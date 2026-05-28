package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobQueueProperties;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingProvider;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingSyncResult;
import io.b2mash.b2b.b2bstrawman.integration.accounting.CustomerSyncRequest;
import io.b2mash.b2b.b2bstrawman.integration.accounting.InvoiceSyncRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantScopedRunner;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scheduled worker that drains pending accounting sync entries across all tenants. Processes
 * entries with exponential back-off and dead-letter after 5 failed attempts.
 */
@Service
public class AccountingSyncWorker {

  private static final Logger log = LoggerFactory.getLogger(AccountingSyncWorker.class);

  private static final int BATCH_SIZE = 25;
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration RATE_LIMIT_DELAY = Duration.ofSeconds(60);

  private static final Duration[] BACKOFF_SCHEDULE = {
    Duration.ofMinutes(1), // attempt 1
    Duration.ofMinutes(5), // attempt 2
    Duration.ofMinutes(15), // attempt 3
    Duration.ofHours(1), // attempt 4
    Duration.ofHours(6) // attempt 5
  };

  private final TenantScopedRunner tenantScopedRunner;
  private final AccountingSyncEntryRepository syncEntryRepository;
  private final IntegrationRegistry integrationRegistry;
  private final AccountingSyncService syncService;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionTemplate transactionTemplate;
  private final JobEnqueuer jobEnqueuer;
  private final JobQueueProperties jobQueueProperties;

  public AccountingSyncWorker(
      TenantScopedRunner tenantScopedRunner,
      AccountingSyncEntryRepository syncEntryRepository,
      IntegrationRegistry integrationRegistry,
      AccountingSyncService syncService,
      ApplicationEventPublisher eventPublisher,
      TransactionTemplate transactionTemplate,
      JobEnqueuer jobEnqueuer,
      JobQueueProperties jobQueueProperties) {
    this.tenantScopedRunner = tenantScopedRunner;
    this.syncEntryRepository = syncEntryRepository;
    this.integrationRegistry = integrationRegistry;
    this.syncService = syncService;
    this.eventPublisher = eventPublisher;
    this.transactionTemplate = transactionTemplate;
    this.jobEnqueuer = jobEnqueuer;
    this.jobQueueProperties = jobQueueProperties;
  }

  @SchedulerLock(name = "accounting_sync_drain_pending_entries", lockAtLeastFor = "15s")
  @Scheduled(fixedDelay = 30_000)
  public void drainPendingEntries() {
    log.debug("AccountingSyncWorker: starting drain cycle");

    if (jobQueueProperties.isDualMode("accounting_sync_drain")) {
      int[] totalProcessed = {0};

      tenantScopedRunner.forEachTenant(
          (tenantId, orgId) -> {
            int processed = drainForTenant();
            totalProcessed[0] += processed;
          });

      if (totalProcessed[0] > 0) {
        log.info("AccountingSyncWorker: drain cycle complete. Processed: {}", totalProcessed[0]);
      }
    }

    jobEnqueuer.fanOutToAllTenants("accounting_sync_drain", null);
  }

  int drainForTenant() {
    var entries =
        syncEntryRepository.findDrainableEntries(Instant.now(), PageRequest.of(0, BATCH_SIZE));
    int processed = 0;

    for (AccountingSyncEntry entry : entries) {
      Boolean success = transactionTemplate.execute(tx -> processEntry(entry));
      if (Boolean.TRUE.equals(success)) {
        processed++;
      } else if (success == null) {
        // Rate limit hit — stop processing for this tenant
        break;
      }
    }

    return processed;
  }

  /**
   * Process a single sync entry within a transaction. Returns true on success/handled failure,
   * false on unexpected error, null on rate limit (signals stop).
   */
  private Boolean processEntry(AccountingSyncEntry entry) {
    try {
      entry.markInFlight();
      syncEntryRepository.save(entry);

      // Trust boundary guard — currently a no-op pass-through; 521A wires real logic
      TrustBoundaryDecision decision = syncService.checkTrustBoundary(entry);
      if (!decision.allowed()) {
        entry.markBlockedTrustBoundary(decision.reason());
        syncEntryRepository.save(entry);
        log.warn("Sync entry {} blocked by trust boundary: {}", entry.getId(), decision.reason());
        return true;
      }

      var provider =
          integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);

      AccountingSyncResult result = dispatchSync(provider, entry);

      if (result.success()) {
        entry.markCompleted(result.externalReferenceId());
        syncEntryRepository.save(entry);
        eventPublisher.publishEvent(
            new XeroSyncEntryCompletedEvent(
                entry.getId(),
                entry.getEntityType(),
                entry.getEntityId(),
                result.externalReferenceId()));
        log.debug(
            "Sync entry {} completed: externalId={}", entry.getId(), result.externalReferenceId());
        return true;
      }

      // Failure handling
      String errorCode = classifyError(result);
      String errorMessage = result.errorMessage();

      if (isRateLimitError(errorMessage)) {
        // Rate limit: revert to PENDING without consuming retry budget
        entry.markRateLimited(Instant.now().plus(RATE_LIMIT_DELAY));
        syncEntryRepository.save(entry);
        log.warn("Rate limit hit for entry {}; stopping tenant drain", entry.getId());
        return null;
      }

      if (isValidationError(errorMessage)) {
        // Validation error: straight to dead letter
        entry.markDeadLetter("VALIDATION_FAILED", errorMessage);
        syncEntryRepository.save(entry);
        eventPublisher.publishEvent(
            new XeroSyncEntryDeadLetteredEvent(
                entry.getId(), entry.getEntityType(), entry.getEntityId(), "VALIDATION_FAILED"));
        log.warn("Sync entry {} dead-lettered (validation): {}", entry.getId(), errorMessage);
        return true;
      }

      // Transient error: retry with back-off or dead-letter after max attempts
      int nextAttemptNumber = entry.getAttemptCount() + 1;
      if (nextAttemptNumber >= MAX_ATTEMPTS) {
        entry.markDeadLetter(errorCode, errorMessage);
        syncEntryRepository.save(entry);
        eventPublisher.publishEvent(
            new XeroSyncEntryDeadLetteredEvent(
                entry.getId(), entry.getEntityType(), entry.getEntityId(), errorCode));
        log.warn(
            "Sync entry {} dead-lettered after {} attempts: {}",
            entry.getId(),
            MAX_ATTEMPTS,
            errorMessage);
      } else {
        Instant nextAttempt = computeNextAttempt(nextAttemptNumber);
        entry.markFailedRetrying(errorCode, errorMessage, nextAttempt);
        syncEntryRepository.save(entry);
        log.info(
            "Sync entry {} retry scheduled: attempt={}, nextAt={}",
            entry.getId(),
            nextAttemptNumber,
            nextAttempt);
      }
      return true;

    } catch (Exception e) {
      log.error("Unexpected error processing sync entry {}: {}", entry.getId(), e.getMessage(), e);
      try {
        int nextAttemptNumber = entry.getAttemptCount() + 1;
        if (nextAttemptNumber >= MAX_ATTEMPTS) {
          entry.markDeadLetter("UNEXPECTED_ERROR", e.getMessage());
          syncEntryRepository.save(entry);
          eventPublisher.publishEvent(
              new XeroSyncEntryDeadLetteredEvent(
                  entry.getId(), entry.getEntityType(), entry.getEntityId(), "UNEXPECTED_ERROR"));
        } else {
          entry.markFailedRetrying(
              "UNEXPECTED_ERROR", e.getMessage(), computeNextAttempt(nextAttemptNumber));
          syncEntryRepository.save(entry);
        }
      } catch (Exception saveEx) {
        log.error("Failed to persist error state for entry {}", entry.getId(), saveEx);
      }
      return true;
    }
  }

  private AccountingSyncResult dispatchSync(
      AccountingProvider provider, AccountingSyncEntry entry) {
    return switch (entry.getEntityType()) {
      case INVOICE -> {
        // Stub request — real payload mapping via XeroInvoicePayloadMapper in 519A
        var request =
            new InvoiceSyncRequest(
                entry.getExternalReference(),
                "Pending",
                List.of(),
                "NZD",
                null,
                null,
                entry.getExternalReference(),
                null);
        yield provider.syncInvoice(request);
      }
      case CUSTOMER -> {
        // Stub request — real payload mapping in 519A
        var request = new CustomerSyncRequest("Pending", null, null, null, null, null, null, null);
        yield provider.syncCustomer(request);
      }
      case PAYMENT_PULL ->
          // Payment pull entries are not processed by this worker
          new AccountingSyncResult(true, null, null);
    };
  }

  private Instant computeNextAttempt(int attemptNumber) {
    int index = Math.min(attemptNumber - 1, BACKOFF_SCHEDULE.length - 1);
    return Instant.now().plus(BACKOFF_SCHEDULE[index]);
  }

  private String classifyError(AccountingSyncResult result) {
    if (result.errorMessage() == null) {
      return "UNKNOWN";
    }
    if (isRateLimitError(result.errorMessage())) {
      return "RATE_LIMIT";
    }
    if (isValidationError(result.errorMessage())) {
      return "VALIDATION_FAILED";
    }
    return "TRANSIENT";
  }

  private boolean isRateLimitError(String errorMessage) {
    if (errorMessage == null) return false;
    String lower = errorMessage.toLowerCase(Locale.ROOT);
    return lower.contains("rate") && lower.contains("limit");
  }

  private boolean isValidationError(String errorMessage) {
    if (errorMessage == null) return false;
    String lower = errorMessage.toLowerCase(Locale.ROOT);
    return lower.contains("validation")
        || lower.contains("status 400")
        || lower.contains("http 400");
  }

  /** Expose backoff schedule for testing. */
  static Duration[] getBackoffSchedule() {
    return BACKOFF_SCHEDULE.clone();
  }
}
