package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantScopedRunner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cancels and purges take-on batches not committed within 30 days of creation (§17.3.14, X4,
 * FD-24): daily at 03:00 SA time, every tenant. Each batch is claimed {@code FOR UPDATE SKIP
 * LOCKED} in its own transaction and its status re-checked under the lock, so a batch a commit
 * request has just claimed (or that another worker holds) is never purged (X5). Runs as SYSTEM (no
 * member bound); audited {@code takeon.batch.cancelled} with {@code reason_code: ABANDONED}. No
 * job-queue type (C-12).
 */
@Component
public class TakeOnAbandonedBatchPurgeJob {

  static final Duration RETENTION = Duration.ofDays(30);

  private static final Logger log = LoggerFactory.getLogger(TakeOnAbandonedBatchPurgeJob.class);

  private final TenantScopedRunner tenantScopedRunner;
  private final TakeOnBatchRepository batches;
  private final TakeOnBatchService batchService;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;
  private final TakeOnStorageCleanup storageCleanup;

  public TakeOnAbandonedBatchPurgeJob(
      TenantScopedRunner tenantScopedRunner,
      TakeOnBatchRepository batches,
      TakeOnBatchService batchService,
      PlatformTransactionManager transactionManager,
      Clock clock,
      TakeOnStorageCleanup storageCleanup) {
    this.tenantScopedRunner = tenantScopedRunner;
    this.batches = batches;
    this.batchService = batchService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.clock = clock;
    this.storageCleanup = storageCleanup;
  }

  @SchedulerLock(name = "take_on_abandoned_batch_purge", lockAtLeastFor = "5m")
  @Scheduled(cron = "0 0 3 * * *", zone = KaziTime.ZONE_ID)
  public void purgeAbandoned() {
    Instant cutoff = clock.instant().minus(RETENTION);
    int[] purged = {0};
    int tenants =
        tenantScopedRunner.forEachTenant(
            (tenantId, orgId) -> purged[0] += purgeAbandonedInTenant(cutoff));
    log.info(
        "Take-on abandoned-batch purge: {} batches purged across {} tenants", purged[0], tenants);
  }

  /**
   * Purges the current tenant's open batches created before {@code cutoff}, then retries the S3
   * delete of every purged file still holding its key (a delete that failed after an earlier cancel
   * or purge); the caller binds the tenant. Returns the number of batches purged. Public for tests
   * (the test profile switches scheduling off).
   */
  public int purgeAbandonedInTenant(Instant cutoff) {
    int purged = 0;
    for (UUID id : batches.findAbandonedIds(cutoff)) {
      try {
        Boolean done =
            transactionTemplate.execute(
                status ->
                    batches
                        .findByIdForUpdateSkipLocked(id)
                        .filter(b -> b.getStatus().isOpen() && b.getCreatedAt().isBefore(cutoff))
                        .map(
                            b -> {
                              batchService.cancelAndPurge(b.getId(), null, "ABANDONED");
                              return true;
                            })
                        .orElse(false));
        if (Boolean.TRUE.equals(done)) {
          purged++;
        }
      } catch (RuntimeException e) {
        // one batch failing must not stop the others; it is retried on the next run
        log.warn("Take-on abandoned-batch purge failed for batch {}: {}", id, e.getMessage());
      }
    }
    try {
      storageCleanup.retryPurgedDeletes();
    } catch (RuntimeException e) {
      log.warn("Take-on purged-file delete retry failed: {}", e.getMessage());
    }
    return purged;
  }
}
