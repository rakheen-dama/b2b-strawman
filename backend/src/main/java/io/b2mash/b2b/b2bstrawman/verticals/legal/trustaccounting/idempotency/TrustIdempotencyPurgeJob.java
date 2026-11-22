package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantScopedRunner;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily retention of trust {@code Idempotency-Key}s (648B F-11): in every tenant, deletes the keys
 * created more than {@link TrustIdempotencyService#RETENTION} ago, whatever their state. A retry
 * after that is a new request.
 */
@Component
public class TrustIdempotencyPurgeJob {

  private static final Logger log = LoggerFactory.getLogger(TrustIdempotencyPurgeJob.class);

  private final TenantScopedRunner tenantScopedRunner;
  private final TrustIdempotencyService idempotencyService;

  public TrustIdempotencyPurgeJob(
      TenantScopedRunner tenantScopedRunner, TrustIdempotencyService idempotencyService) {
    this.tenantScopedRunner = tenantScopedRunner;
    this.idempotencyService = idempotencyService;
  }

  @SchedulerLock(name = "trust_idempotency_purge", lockAtLeastFor = "5m")
  @Scheduled(cron = "0 15 3 * * *", zone = KaziTime.ZONE_ID)
  public void purgeExpiredKeys() {
    int[] deleted = {0};
    int tenants =
        tenantScopedRunner.forEachTenant(
            (tenantId, orgId) -> deleted[0] += idempotencyService.purgeExpired());
    log.info(
        "Trust idempotency purge completed: {} tenants processed, {} keys deleted",
        tenants,
        deleted[0]);
  }
}
