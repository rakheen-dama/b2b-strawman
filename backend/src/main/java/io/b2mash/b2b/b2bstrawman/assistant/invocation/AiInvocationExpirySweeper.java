package io.b2mash.b2b.b2bstrawman.assistant.invocation;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobQueueProperties;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantScopedRunner;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Daily scheduled sweeper for AI specialist invocations. Performs two passes per tenant:
 *
 * <ol>
 *   <li><b>Expiry:</b> PENDING_APPROVAL rows older than 14 days (default) are marked EXPIRED, with
 *       an {@code ai.specialist.expired} audit event emitted.
 *   <li><b>Retention:</b> Terminal-state rows older than 365 days (default) have their {@code
 *       proposed_output} and {@code applied_output} JSONB nulled for POPIA §14 alignment. Status is
 *       preserved as audit shadow.
 * </ol>
 */
@Component
public class AiInvocationExpirySweeper {

  private static final Logger log = LoggerFactory.getLogger(AiInvocationExpirySweeper.class);

  /** Default expiry period for PENDING_APPROVAL invocations. */
  private static final int DEFAULT_EXPIRY_DAYS = 14;

  /** Default retention period for terminal-state invocations. */
  private static final int DEFAULT_RETENTION_DAYS = 365;

  /** Terminal statuses eligible for retention nulling. */
  private static final Set<InvocationStatus> TERMINAL_STATUSES =
      Set.of(
          InvocationStatus.APPROVED,
          InvocationStatus.REJECTED,
          InvocationStatus.AUTO_APPLIED,
          InvocationStatus.FAILED,
          InvocationStatus.EXPIRED);

  private final TenantScopedRunner tenantScopedRunner;
  private final AiSpecialistInvocationRepository repository;
  private final AuditService auditService;
  private final TransactionTemplate transactionTemplate;
  private final JobEnqueuer jobEnqueuer;
  private final JobQueueProperties jobQueueProperties;

  public AiInvocationExpirySweeper(
      TenantScopedRunner tenantScopedRunner,
      AiSpecialistInvocationRepository repository,
      AuditService auditService,
      TransactionTemplate transactionTemplate,
      JobEnqueuer jobEnqueuer,
      JobQueueProperties jobQueueProperties) {
    this.tenantScopedRunner = tenantScopedRunner;
    this.repository = repository;
    this.auditService = auditService;
    this.transactionTemplate = transactionTemplate;
    this.jobEnqueuer = jobEnqueuer;
    this.jobQueueProperties = jobQueueProperties;
  }

  /** Runs daily at 03:00 UTC. */
  @SchedulerLock(name = "ai_invocation_expiry_sweep", lockAtLeastFor = "5m")
  @Scheduled(cron = "0 0 3 * * *")
  public void sweep() {
    log.info("AiInvocationExpirySweeper: starting daily sweep");

    if (jobQueueProperties.isDualMode("ai_invocation_expiry")) {
      int[] totalExpired = {0};
      int[] totalRetained = {0};

      tenantScopedRunner.forEachTenant(
          (tenantId, orgId) -> {
            var result = sweepForTenant();
            if (result != null) {
              totalExpired[0] += result[0];
              totalRetained[0] += result[1];
            }
          });

      log.info(
          "AiInvocationExpirySweeper: completed. Expired: {}, Retention-nulled: {}",
          totalExpired[0],
          totalRetained[0]);
    }

    jobEnqueuer.fanOutToAllTenants("ai_invocation_expiry", null);
  }

  int[] sweepForTenant() {
    return transactionTemplate.execute(
        tx -> {
          int expired = expirePendingApprovals();
          int retained = nullRetentionAgedOutputs();
          return new int[] {expired, retained};
        });
  }

  /** Step 1: Expire PENDING_APPROVAL rows older than the expiry threshold. */
  private int expirePendingApprovals() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(DEFAULT_EXPIRY_DAYS));
    var stale =
        repository.findByStatusAndCreatedAtBefore(InvocationStatus.PENDING_APPROVAL, cutoff);

    int count = 0;
    for (var inv : stale) {
      try {
        inv.markExpired();
        repository.save(inv);
        count++;

        auditService.log(
            AuditEventBuilder.builder()
                .eventType("ai.specialist.expired")
                .entityType(inv.getContextEntityType())
                .entityId(inv.getContextEntityId())
                .actorType("SYSTEM")
                .source("SCHEDULER")
                .details(
                    Map.of(
                        "specialistId", inv.getSpecialistId(),
                        "invocationId", inv.getId().toString(),
                        "reason",
                            "PENDING_APPROVAL expired after " + DEFAULT_EXPIRY_DAYS + " days"))
                .build());

        log.debug("Expired PENDING_APPROVAL invocation {}", inv.getId());
      } catch (Exception e) {
        log.warn("Failed to expire invocation {}: {}", inv.getId(), e.getMessage());
      }
    }
    return count;
  }

  /** Step 2: Null JSONB outputs on terminal-state rows older than the retention threshold. */
  private int nullRetentionAgedOutputs() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(DEFAULT_RETENTION_DAYS));
    int count = 0;

    for (var status : TERMINAL_STATUSES) {
      var aged = repository.findByStatusAndCreatedAtBefore(status, cutoff);
      for (var inv : aged) {
        try {
          // Only null if there's actually output to clear
          if (inv.getProposedOutput() != null || inv.getAppliedOutput() != null) {
            inv.nullOutputsForRetention();
            repository.save(inv);
            count++;
            log.debug("Nulled retention-aged outputs for invocation {}", inv.getId());
          }
        } catch (Exception e) {
          log.warn("Failed to null outputs for invocation {}: {}", inv.getId(), e.getMessage());
        }
      }
    }
    return count;
  }
}
