package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs on application startup to ensure all existing tenant schemas have compliance packs seeded.
 * Tenants provisioned before Phase 14 will have NULL compliance_pack_status — this runner fills
 * that gap. The underlying {@link CompliancePackSeeder} is idempotent, so running on every boot is
 * safe.
 *
 * <p>Ordered after TenantMigrationRunner ({@code @Order(50)}) to ensure schemas are fully migrated
 * before seeding. Note: the underlying seeder checks packId only, not version — pack upgrades are
 * not yet supported (future work).
 */
@Component
@Order(100)
public class CompliancePackReseedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(CompliancePackReseedRunner.class);

  private final OrgSchemaMappingRepository mappingRepository;
  private final CompliancePackSeeder compliancePackSeeder;

  public CompliancePackReseedRunner(
      OrgSchemaMappingRepository mappingRepository, CompliancePackSeeder compliancePackSeeder) {
    this.mappingRepository = mappingRepository;
    this.compliancePackSeeder = compliancePackSeeder;
  }

  @Override
  public void run(ApplicationArguments args) {
    var allMappings = mappingRepository.findAll();
    if (allMappings.isEmpty()) {
      log.info("No tenant schemas found — skipping compliance pack reseeding");
      return;
    }

    log.info("Reseeding compliance packs for {} tenants", allMappings.size());
    int succeeded = 0;
    int failed = 0;

    for (var mapping : allMappings) {
      try {
        compliancePackSeeder.seedPacksForTenant(mapping.getSchemaName(), mapping.getClerkOrgId());
        succeeded++;
        log.info(
            "Compliance packs reseeded for tenant {} (org {})",
            mapping.getSchemaName(),
            mapping.getClerkOrgId());
      } catch (Exception e) {
        failed++;
        log.error(
            "Failed to reseed compliance packs for tenant {} (org {})",
            mapping.getSchemaName(),
            mapping.getClerkOrgId(),
            e);
      }
    }

    log.info("Compliance pack reseeding completed: {} succeeded, {} failed", succeeded, failed);
  }
}
