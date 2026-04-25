package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.clause.ClausePackSeeder;
import io.b2mash.b2b.b2bstrawman.compliance.CompliancePackSeeder;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldPackSeeder;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestPackSeeder;
import io.b2mash.b2b.b2bstrawman.integration.payment.MockPaymentIntegrationSeeder;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.packs.PackCatalogService;
import io.b2mash.b2b.b2bstrawman.packs.PackInstallService;
import io.b2mash.b2b.b2bstrawman.packs.PackType;
import io.b2mash.b2b.b2bstrawman.reporting.StandardReportPackSeeder;
import io.b2mash.b2b.b2bstrawman.seeder.ProjectTemplatePackSeeder;
import io.b2mash.b2b.b2bstrawman.seeder.RatePackSeeder;
import io.b2mash.b2b.b2bstrawman.seeder.SchedulePackSeeder;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileReconciliationSeeder;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.LegalTariffSeeder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

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
  private final PackCatalogService packCatalogService;
  private final PackInstallService packInstallService;
  private final OrgSettingsRepository orgSettingsRepository;
  private final ClausePackSeeder clausePackSeeder;
  private final CompliancePackSeeder compliancePackSeeder;
  private final StandardReportPackSeeder standardReportPackSeeder;
  private final RequestPackSeeder requestPackSeeder;
  private final RatePackSeeder ratePackSeeder;
  private final ProjectTemplatePackSeeder projectTemplatePackSeeder;
  private final SchedulePackSeeder schedulePackSeeder;
  private final LegalTariffSeeder legalTariffSeeder;
  private final VerticalProfileReconciliationSeeder verticalProfileReconciliationSeeder;
  private final MockPaymentIntegrationSeeder mockPaymentIntegrationSeeder;
  private final TransactionTemplate transactionTemplate;

  public PackReconciliationRunner(
      OrgSchemaMappingRepository mappingRepository,
      FieldPackSeeder fieldPackSeeder,
      PackCatalogService packCatalogService,
      PackInstallService packInstallService,
      OrgSettingsRepository orgSettingsRepository,
      ClausePackSeeder clausePackSeeder,
      CompliancePackSeeder compliancePackSeeder,
      StandardReportPackSeeder standardReportPackSeeder,
      RequestPackSeeder requestPackSeeder,
      RatePackSeeder ratePackSeeder,
      ProjectTemplatePackSeeder projectTemplatePackSeeder,
      SchedulePackSeeder schedulePackSeeder,
      LegalTariffSeeder legalTariffSeeder,
      VerticalProfileReconciliationSeeder verticalProfileReconciliationSeeder,
      MockPaymentIntegrationSeeder mockPaymentIntegrationSeeder,
      TransactionTemplate transactionTemplate) {
    this.mappingRepository = mappingRepository;
    this.fieldPackSeeder = fieldPackSeeder;
    this.packCatalogService = packCatalogService;
    this.packInstallService = packInstallService;
    this.orgSettingsRepository = orgSettingsRepository;
    this.clausePackSeeder = clausePackSeeder;
    this.compliancePackSeeder = compliancePackSeeder;
    this.standardReportPackSeeder = standardReportPackSeeder;
    this.requestPackSeeder = requestPackSeeder;
    this.ratePackSeeder = ratePackSeeder;
    this.projectTemplatePackSeeder = projectTemplatePackSeeder;
    this.schedulePackSeeder = schedulePackSeeder;
    this.legalTariffSeeder = legalTariffSeeder;
    this.verticalProfileReconciliationSeeder = verticalProfileReconciliationSeeder;
    this.mockPaymentIntegrationSeeder = mockPaymentIntegrationSeeder;
    this.transactionTemplate = transactionTemplate;
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
        installPacksViaPipeline(schemaName, PackType.DOCUMENT_TEMPLATE);
        clausePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
        compliancePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
        standardReportPackSeeder.seedForTenant(schemaName, clerkOrgId);
        requestPackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
        installPacksViaPipeline(schemaName, PackType.AUTOMATION_TEMPLATE);
        ratePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
        projectTemplatePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
        schedulePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
        legalTariffSeeder.seedForTenant(schemaName, clerkOrgId);
        // GAP-L-44 + GAP-L-27 — merge enabled_modules and reconcile taxDefaults/tax_label from
        // the vertical profile JSON into the tenant row. Idempotent; runs for every tenant.
        verticalProfileReconciliationSeeder.reconcile(schemaName, clerkOrgId);
        // GAP-L-64 — auto-seed dev-only mock PSP adapter for legal-za tenants when no PAYMENT
        // integration is configured. No-op in prod profile or when a PSP is already wired.
        mockPaymentIntegrationSeeder.seedForTenant(schemaName, clerkOrgId);

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

  private void installPacksViaPipeline(String schemaName, PackType packType) {
    // Always install universal packs (verticalProfile == null in metadata)
    List<String> universalPackIds = packCatalogService.getUniversalPackIds(packType);
    for (String packId : universalPackIds) {
      // internalInstall binds its own tenant scope internally
      packInstallService.internalInstall(packId, schemaName);
    }

    // Resolve the tenant's vertical profile (requires tenant scope for DB access)
    String verticalProfile =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
            .call(
                () ->
                    transactionTemplate.execute(
                        tx ->
                            orgSettingsRepository
                                .findForCurrentTenant()
                                .map(OrgSettings::getVerticalProfile)
                                .orElse(null)));

    // Install profile-specific packs only when a profile is set
    if (verticalProfile != null) {
      List<String> profilePackIds =
          packCatalogService.getPackIdsForProfile(verticalProfile, packType);
      for (String packId : profilePackIds) {
        packInstallService.internalInstall(packId, schemaName);
      }
    }
  }
}
