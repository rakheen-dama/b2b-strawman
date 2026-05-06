package io.b2mash.b2b.b2bstrawman.assistant.invocation;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantScopedRunner;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs once at application startup to reap stale RUNNING invocations left behind by a JVM crash or
 * unclean shutdown. Any RUNNING row older than 2x the default timeout (120s) is marked FAILED with
 * error message "REAPED_AFTER_RESTART".
 *
 * <p>Distinct from {@link AiInvocationExpirySweeper} which handles daily PENDING_APPROVAL expiry
 * and JSONB retention.
 */
@Component
public class AiInvocationReaper {

  private static final Logger log = LoggerFactory.getLogger(AiInvocationReaper.class);
  private static final Duration STALE_THRESHOLD = Duration.ofSeconds(120); // 2 * 60s

  private final TenantScopedRunner tenantScopedRunner;
  private final AiSpecialistInvocationRepository repository;
  private final TransactionTemplate transactionTemplate;

  public AiInvocationReaper(
      TenantScopedRunner tenantScopedRunner,
      AiSpecialistInvocationRepository repository,
      TransactionTemplate transactionTemplate) {
    this.tenantScopedRunner = tenantScopedRunner;
    this.repository = repository;
    this.transactionTemplate = transactionTemplate;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void reapStaleInvocations() {
    log.info("AiInvocationReaper: scanning for stale RUNNING invocations");
    int[] totalReaped = {0};

    tenantScopedRunner.forEachTenant(
        (tenantId, orgId) -> {
          Integer reaped = reapForTenant();
          if (reaped != null && reaped > 0) {
            totalReaped[0] += reaped;
          }
        });

    if (totalReaped[0] > 0) {
      log.info("AiInvocationReaper: reaped {} stale RUNNING invocations", totalReaped[0]);
    } else {
      log.info("AiInvocationReaper: no stale RUNNING invocations found");
    }
  }

  private Integer reapForTenant() {
    return transactionTemplate.execute(
        tx -> {
          Instant cutoff = Instant.now().minus(STALE_THRESHOLD);
          var stale = repository.findByStatusAndCreatedAtBefore(InvocationStatus.RUNNING, cutoff);
          if (stale.isEmpty()) return 0;

          int count = 0;
          for (var inv : stale) {
            try {
              inv.markFailed("REAPED_AFTER_RESTART");
              repository.save(inv);
              count++;
              log.debug(
                  "Reaped stale invocation {} (specialist={}, created={})",
                  inv.getId(),
                  inv.getSpecialistId(),
                  inv.getCreatedAt());
            } catch (Exception e) {
              log.warn("Failed to reap invocation {}: {}", inv.getId(), e.getMessage());
            }
          }
          return count;
        });
  }
}
