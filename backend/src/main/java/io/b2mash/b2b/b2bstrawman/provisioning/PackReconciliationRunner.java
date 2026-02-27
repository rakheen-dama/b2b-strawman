package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.clause.ClausePackSeeder;
import io.b2mash.b2b.b2bstrawman.compliance.CompliancePackSeeder;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldPackSeeder;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.reporting.StandardReportPackSeeder;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackSeeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs on application startup to ensure all existing tenant schemas have all pack seeders applied.
 * This covers tenants provisioned before new packs were introduced. All underlying seeders are
 * idempotent, so running on every boot is safe.
 *
 * <p>Ordered after TenantMigrationRunner ({@code @Order(50)}) to ensure schemas are fully migrated
 * before seeding.
 */
@Component
@Order(100)
public class PackReconciliationRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(PackReconciliationRunner.class);

  private final OrgSchemaMappingRepository mappingRepository;
  private final FieldPackSeeder fieldPackSeeder;
  private final TemplatePackSeeder templatePackSeeder;
  private final ClausePackSeeder clausePackSeeder;
  private final CompliancePackSeeder compliancePackSeeder;
  private final StandardReportPackSeeder standardReportPackSeeder;

  public PackReconciliationRunner(
      OrgSchemaMappingRepository mappingRepository,
      FieldPackSeeder fieldPackSeeder,
      TemplatePackSeeder templatePackSeeder,
      ClausePackSeeder clausePackSeeder,
      CompliancePackSeeder compliancePackSeeder,
      StandardReportPackSeeder standardReportPackSeeder) {
    this.mappingRepository = mappingRepository;
    this.fieldPackSeeder = fieldPackSeeder;
    this.templatePackSeeder = templatePackSeeder;
    this.clausePackSeeder = clausePackSeeder;
    this.compliancePackSeeder = compliancePackSeeder;
    this.standardReportPackSeeder = standardReportPackSeeder;
  }

  @Override
  public void run(ApplicationArguments args) {
    var allMappings = mappingRepository.findAll();
    if (allMappings.isEmpty()) {
      log.info("No tenant schemas found — skipping pack reconciliation");
      return;
    }

    log.info("Running pack reconciliation for {} tenants", allMappings.size());
    int succeeded = 0;
    int failed = 0;

    for (var mapping : allMappings) {
      try {
        var schemaName = mapping.getSchemaName();
        var clerkOrgId = mapping.getClerkOrgId();

        fieldPackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
        templatePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
        clausePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
        compliancePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
        standardReportPackSeeder.seedForTenant(schemaName, clerkOrgId);

        succeeded++;
      } catch (Exception e) {
        failed++;
        log.error(
            "Failed to reconcile packs for tenant {} (org {})",
            mapping.getSchemaName(),
            mapping.getClerkOrgId(),
            e);
      }
    }

    log.info(
        "Pack reconciliation: checked {} tenants, {} succeeded, {} failed",
        allMappings.size(),
        succeeded,
        failed);
  }
}
