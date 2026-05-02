package io.b2mash.b2b.b2bstrawman.acceptance;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantScopedRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that runs hourly to expire overdue acceptance requests across all tenant schemas.
 * Follows the DormancyScheduledJob pattern.
 */
@Component
public class AcceptanceExpiryProcessor {

  private static final Logger log = LoggerFactory.getLogger(AcceptanceExpiryProcessor.class);

  private final TenantScopedRunner tenantScopedRunner;
  private final AcceptanceService acceptanceService;

  public AcceptanceExpiryProcessor(
      TenantScopedRunner tenantScopedRunner, AcceptanceService acceptanceService) {
    this.tenantScopedRunner = tenantScopedRunner;
    this.acceptanceService = acceptanceService;
  }

  @Scheduled(fixedDelay = 3600000)
  public void processExpired() {
    log.info("Acceptance expiry processor started");
    int[] totalExpired = {0};
    int tenantsProcessed =
        tenantScopedRunner.forEachTenant(
            (tenantId, orgId) -> totalExpired[0] += acceptanceService.processExpiredForTenant());

    if (totalExpired[0] > 0) {
      log.info(
          "Expiry processor completed: {} requests expired after processing {} tenants",
          totalExpired[0],
          tenantsProcessed);
    } else {
      log.info("Expiry processor completed: no requests expired");
    }
  }
}
