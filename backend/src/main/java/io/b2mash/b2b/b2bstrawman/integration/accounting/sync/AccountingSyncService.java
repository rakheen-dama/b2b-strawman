package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingPaymentSource;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingProvider;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnectionRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.XeroConnectionStatus;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Core orchestration service for accounting sync: enqueue, retry, summary, and payment polling. */
@Service
public class AccountingSyncService {

  private static final Logger log = LoggerFactory.getLogger(AccountingSyncService.class);

  private final AccountingSyncEntryRepository syncEntryRepository;
  private final AccountingXeroConnectionRepository xeroConnectionRepository;
  private final IntegrationRegistry integrationRegistry;
  private final ApplicationEventPublisher eventPublisher;

  public AccountingSyncService(
      AccountingSyncEntryRepository syncEntryRepository,
      AccountingXeroConnectionRepository xeroConnectionRepository,
      IntegrationRegistry integrationRegistry,
      ApplicationEventPublisher eventPublisher) {
    this.syncEntryRepository = syncEntryRepository;
    this.xeroConnectionRepository = xeroConnectionRepository;
    this.integrationRegistry = integrationRegistry;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Enqueue an invoice push to Xero. Idempotent: if an active entry already exists for the invoice,
   * this is a no-op. If no CONNECTED Xero connection exists, returns silently.
   */
  @Transactional
  public void enqueueInvoicePush(UUID invoiceId, SyncTrigger trigger) {
    if (!hasConnectedXeroConnection()) {
      log.debug("No connected Xero connection; skipping invoice push for {}", invoiceId);
      return;
    }

    var existing = syncEntryRepository.findActiveEntryForEntity(SyncEntityType.INVOICE, invoiceId);
    if (existing.isPresent()) {
      log.debug("Active sync entry already exists for invoice {}; skipping enqueue", invoiceId);
      return;
    }

    var entry =
        new AccountingSyncEntry(
            SyncEntityType.INVOICE,
            invoiceId,
            "xero",
            SyncDirection.PUSH,
            trigger,
            "KAZI-INV-" + invoiceId);
    syncEntryRepository.save(entry);
    log.info("Enqueued invoice push: invoiceId={}, trigger={}", invoiceId, trigger);
  }

  /**
   * Enqueue a customer push to Xero. Idempotent: if an active entry already exists for the
   * customer, this is a no-op. If no CONNECTED Xero connection exists, returns silently.
   */
  @Transactional
  public void enqueueCustomerPush(UUID customerId, SyncTrigger trigger) {
    if (!hasConnectedXeroConnection()) {
      log.debug("No connected Xero connection; skipping customer push for {}", customerId);
      return;
    }

    var existing =
        syncEntryRepository.findActiveEntryForEntity(SyncEntityType.CUSTOMER, customerId);
    if (existing.isPresent()) {
      log.debug("Active sync entry already exists for customer {}; skipping enqueue", customerId);
      return;
    }

    var entry =
        new AccountingSyncEntry(
            SyncEntityType.CUSTOMER,
            customerId,
            "xero",
            SyncDirection.PUSH,
            trigger,
            "KAZI-CUST-" + customerId);
    syncEntryRepository.save(entry);
    log.info("Enqueued customer push: customerId={}, trigger={}", customerId, trigger);
  }

  /** Reset a dead-lettered entry for manual retry. Resets state to PENDING with attempt count 0. */
  @Transactional
  public void retryFromDeadLetter(UUID syncEntryId) {
    var entry =
        syncEntryRepository
            .findOneById(syncEntryId)
            .orElseThrow(() -> new ResourceNotFoundException("AccountingSyncEntry", syncEntryId));
    entry.resetForRetry();
    syncEntryRepository.save(entry);
    log.info("Reset sync entry {} from DEAD_LETTER to PENDING for retry", syncEntryId);
  }

  /** Returns a summary of sync entry counts grouped by state. */
  @Transactional(readOnly = true)
  public Map<SyncState, Long> getSyncSummary() {
    var results = syncEntryRepository.countByState();
    Map<SyncState, Long> summary = new EnumMap<>(SyncState.class);
    for (Object[] row : results) {
      var state = (SyncState) row[0];
      var count = (Long) row[1];
      summary.put(state, count);
    }
    return summary;
  }

  /**
   * Poll payments for a Xero connection. Skeleton implementation — full matching logic lands in
   * 522A.
   */
  @Transactional
  public void pollPaymentsForConnection(UUID connectionId) {
    var connection =
        xeroConnectionRepository
            .findOneById(connectionId)
            .orElseThrow(
                () -> new ResourceNotFoundException("AccountingXeroConnection", connectionId));

    var provider =
        integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);

    if (provider instanceof AccountingPaymentSource paymentSource) {
      var since =
          connection.getLastPollAt() != null
              ? connection.getLastPollAt()
              : connection.getConnectedAt();
      var payments = paymentSource.getPaymentsModifiedSince(since);
      log.info(
          "Polled {} payments for connection {} since {}", payments.size(), connectionId, since);
      // Full payment matching logic deferred to 522A
    }

    connection.recordPoll();
    xeroConnectionRepository.save(connection);
  }

  /**
   * Trust boundary guard for sync entries. Currently always permits — 521A will wire real
   * validation logic (amount thresholds, entity type checks, org-level controls).
   */
  public TrustBoundaryDecision checkTrustBoundary(AccountingSyncEntry entry) {
    return TrustBoundaryDecision.permit();
  }

  private boolean hasConnectedXeroConnection() {
    return xeroConnectionRepository.existsByStatus(XeroConnectionStatus.CONNECTED);
  }
}
