package io.b2mash.b2b.b2bstrawman.acceptance;

import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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

  private final OrgSchemaMappingRepository mappingRepository;
  private final AcceptanceService acceptanceService;

  public AcceptanceExpiryProcessor(
      OrgSchemaMappingRepository mappingRepository, AcceptanceService acceptanceService) {
    this.mappingRepository = mappingRepository;
    this.acceptanceService = acceptanceService;
  }

  @Scheduled(fixedDelay = 3600000)
  public void processExpired() {
    log.info("Acceptance expiry processor started");
    var mappings = mappingRepository.findAll();
    int totalExpired = 0;

    for (var mapping : mappings) {
      try {
        int expired =
            ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
                .where(RequestScopes.ORG_ID, mapping.getClerkOrgId())
                .call(() -> acceptanceService.processExpiredForTenant());
        totalExpired += expired;
      } catch (Exception e) {
        log.error("Expiry processor failed for schema {}", mapping.getSchemaName(), e);
      }
    }

    if (totalExpired > 0) {
      log.info(
          "Expiry processor completed: {} requests expired across {} tenants",
          totalExpired,
          mappings.size());
    } else {
      log.info("Expiry processor completed: no requests expired");
    }
  }
}
