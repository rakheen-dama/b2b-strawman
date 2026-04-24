package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.billing.SubscriptionService;
import io.b2mash.b2b.b2bstrawman.clause.ClausePackSeeder;
import io.b2mash.b2b.b2bstrawman.compliance.CompliancePackSeeder;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldPackSeeder;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestPackSeeder;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
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
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileRegistry;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.LegalTariffSeeder;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class TenantProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);

  private static final Map<String, String> COUNTRY_TO_CURRENCY =
      Map.ofEntries(
          Map.entry("South Africa", "ZAR"),
          Map.entry("ZA", "ZAR"),
          Map.entry("Kenya", "KES"),
          Map.entry("KE", "KES"),
          Map.entry("Nigeria", "NGN"),
          Map.entry("NG", "NGN"),
          Map.entry("United Kingdom", "GBP"),
          Map.entry("GB", "GBP"),
          Map.entry("United States", "USD"),
          Map.entry("US", "USD"),
          Map.entry("Canada", "CAD"),
          Map.entry("CA", "CAD"),
          Map.entry("Australia", "AUD"),
          Map.entry("AU", "AUD"),
          Map.entry("India", "INR"),
          Map.entry("IN", "INR"),
          Map.entry("Ghana", "GHS"),
          Map.entry("GH", "GHS"),
          Map.entry("Tanzania", "TZS"),
          Map.entry("TZ", "TZS"));

  private final OrganizationRepository organizationRepository;
  private final OrgSchemaMappingRepository mappingRepository;
  private final DataSource migrationDataSource;
  private final SubscriptionService subscriptionService;
  private final FieldPackSeeder fieldPackSeeder;
  private final PackCatalogService packCatalogService;
  private final PackInstallService packInstallService;
  private final ClausePackSeeder clausePackSeeder;
  private final CompliancePackSeeder compliancePackSeeder;
  private final StandardReportPackSeeder standardReportPackSeeder;
  private final RequestPackSeeder requestPackSeeder;
  private final RatePackSeeder ratePackSeeder;
  private final ProjectTemplatePackSeeder projectTemplatePackSeeder;
  private final SchedulePackSeeder schedulePackSeeder;
  private final LegalTariffSeeder legalTariffSeeder;
  private final VerticalProfileReconciliationSeeder verticalProfileReconciliationSeeder;
  private final TenantTransactionHelper tenantTransactionHelper;
  private final OrgSettingsRepository orgSettingsRepository;
  private final VerticalProfileRegistry verticalProfileRegistry;

  public TenantProvisioningService(
      OrganizationRepository organizationRepository,
      OrgSchemaMappingRepository mappingRepository,
      @Qualifier("migrationDataSource") DataSource migrationDataSource,
      SubscriptionService subscriptionService,
      FieldPackSeeder fieldPackSeeder,
      PackCatalogService packCatalogService,
      PackInstallService packInstallService,
      ClausePackSeeder clausePackSeeder,
      CompliancePackSeeder compliancePackSeeder,
      StandardReportPackSeeder standardReportPackSeeder,
      RequestPackSeeder requestPackSeeder,
      RatePackSeeder ratePackSeeder,
      ProjectTemplatePackSeeder projectTemplatePackSeeder,
      SchedulePackSeeder schedulePackSeeder,
      LegalTariffSeeder legalTariffSeeder,
      VerticalProfileReconciliationSeeder verticalProfileReconciliationSeeder,
      TenantTransactionHelper tenantTransactionHelper,
      OrgSettingsRepository orgSettingsRepository,
      VerticalProfileRegistry verticalProfileRegistry) {
    this.organizationRepository = organizationRepository;
    this.mappingRepository = mappingRepository;
    this.migrationDataSource = migrationDataSource;
    this.subscriptionService = subscriptionService;
    this.fieldPackSeeder = fieldPackSeeder;
    this.packCatalogService = packCatalogService;
    this.packInstallService = packInstallService;
    this.clausePackSeeder = clausePackSeeder;
    this.compliancePackSeeder = compliancePackSeeder;
    this.standardReportPackSeeder = standardReportPackSeeder;
    this.requestPackSeeder = requestPackSeeder;
    this.ratePackSeeder = ratePackSeeder;
    this.projectTemplatePackSeeder = projectTemplatePackSeeder;
    this.schedulePackSeeder = schedulePackSeeder;
    this.legalTariffSeeder = legalTariffSeeder;
    this.verticalProfileReconciliationSeeder = verticalProfileReconciliationSeeder;
    this.tenantTransactionHelper = tenantTransactionHelper;
    this.orgSettingsRepository = orgSettingsRepository;
    this.verticalProfileRegistry = verticalProfileRegistry;
  }

  @Retryable(
      retryFor = ProvisioningException.class,
      noRetryFor = IllegalArgumentException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2))
  public ProvisioningResult provisionTenant(
      String clerkOrgId, String orgName, String verticalProfile) {
    return provisionTenant(clerkOrgId, orgName, verticalProfile, null);
  }

  @Retryable(
      retryFor = ProvisioningException.class,
      noRetryFor = IllegalArgumentException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2))
  public ProvisioningResult provisionTenant(
      String clerkOrgId, String orgName, String verticalProfile, @Nullable String country) {
    // Idempotency check: already fully provisioned?
    var existingMapping = mappingRepository.findByClerkOrgId(clerkOrgId);
    if (existingMapping.isPresent()) {
      log.info("Tenant already provisioned for org {}", clerkOrgId);
      return ProvisioningResult.alreadyProvisioned(existingMapping.get().getSchemaName());
    }

    // Create or find organization record (default tier is STARTER)
    var org =
        organizationRepository
            .findByClerkOrgId(clerkOrgId)
            .orElseGet(() -> organizationRepository.save(new Organization(clerkOrgId, orgName)));

    org.markInProgress();
    organizationRepository.save(org);
    organizationRepository.flush();

    try {
      String schemaName = SchemaNameGenerator.generateSchemaName(clerkOrgId);
      log.info("Provisioning tenant schema {} for org {}", schemaName, clerkOrgId);

      // Each step is idempotent — safe to retry after partial failure.
      // Subscription is created before the mapping so that if it fails,
      // retries won't see the mapping and short-circuit via "alreadyProvisioned".
      // Mapping is created LAST so TenantFilter only resolves to this
      // schema once all tables and the subscription exist (prevents race).
      createSchema(schemaName);
      runTenantMigrations(schemaName);
      String defaultCurrency = resolveCurrency(country, verticalProfile);
      if (verticalProfile != null) {
        setVerticalProfile(schemaName, clerkOrgId, verticalProfile, defaultCurrency);
      } else if (!"USD".equals(defaultCurrency)) {
        setDefaultCurrency(schemaName, clerkOrgId, defaultCurrency);
      }
      fieldPackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
      installPacksViaPipeline(schemaName, verticalProfile, PackType.DOCUMENT_TEMPLATE);
      clausePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
      compliancePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
      standardReportPackSeeder.seedForTenant(schemaName, clerkOrgId);
      requestPackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
      installPacksViaPipeline(schemaName, verticalProfile, PackType.AUTOMATION_TEMPLATE);
      ratePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
      projectTemplatePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
      schedulePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
      legalTariffSeeder.seedForTenant(schemaName, clerkOrgId);
      // GAP-L-44 + GAP-L-27 — apply profile enabled_modules and taxDefaults to the fresh tenant
      // so the first user never sees a bare "Standard" rate or missing vertical modules.
      verticalProfileReconciliationSeeder.reconcile(schemaName, clerkOrgId);
      subscriptionService.createSubscription(org.getId());
      createMapping(clerkOrgId, schemaName);

      org.markCompleted();
      organizationRepository.save(org);

      log.info("Successfully provisioned tenant {} for org {}", schemaName, clerkOrgId);
      return ProvisioningResult.success(schemaName);
    } catch (Exception e) {
      log.error("Failed to provision tenant for org {}", clerkOrgId, e);
      org.markFailed();
      organizationRepository.save(org);
      throw new ProvisioningException("Provisioning failed for org " + clerkOrgId, e);
    }
  }

  void runTenantMigrations(String schemaName) {
    Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(schemaName)
        .baselineOnMigrate(true)
        .load()
        .migrate();
    log.info("Ran tenant migrations for schema {}", schemaName);
  }

  private void createSchema(String schemaName) throws SQLException {
    validateSchemaName(schemaName);
    try (var conn = migrationDataSource.getConnection();
        var stmt = conn.createStatement()) {
      // Schema name is validated to match ^tenant_[0-9a-f]{12}$ — safe to concatenate
      stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");
      log.info("Created schema {}", schemaName);
    }
  }

  private void createMapping(String clerkOrgId, String schemaName) {
    if (mappingRepository.findByClerkOrgId(clerkOrgId).isEmpty()) {
      mappingRepository.save(new OrgSchemaMapping(clerkOrgId, schemaName));
      log.info("Created mapping {} -> {}", clerkOrgId, schemaName);
    }
  }

  private void validateSchemaName(String schemaName) {
    if (!schemaName.matches("^tenant_[0-9a-f]{12}$")) {
      throw new IllegalArgumentException("Invalid schema name: " + schemaName);
    }
  }

  private void setVerticalProfile(
      String schemaName, String orgId, String verticalProfile, String defaultCurrency) {
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId,
        tenantId -> {
          var settings =
              orgSettingsRepository
                  .findForCurrentTenant()
                  .orElseGet(
                      () -> {
                        var newSettings = new OrgSettings(defaultCurrency);
                        return orgSettingsRepository.save(newSettings);
                      });
          settings.setVerticalProfile(verticalProfile);

          var profileOpt = verticalProfileRegistry.getProfile(verticalProfile);
          if (profileOpt.isPresent()) {
            var profile = profileOpt.get();
            settings.setEnabledModules(profile.enabledModules());
            settings.setTerminologyNamespace(profile.terminologyNamespace());
            if (profile.currency() != null) {
              settings.updateCurrency(profile.currency());
            }
          } else {
            log.warn(
                "Vertical profile '{}' not found in registry, skipping modules/terminology",
                verticalProfile);
          }

          orgSettingsRepository.save(settings);
        });
  }

  private void setDefaultCurrency(String schemaName, String orgId, String currency) {
    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId,
        tenantId -> {
          var settings =
              orgSettingsRepository
                  .findForCurrentTenant()
                  .orElseGet(
                      () -> {
                        var newSettings = new OrgSettings(currency);
                        return orgSettingsRepository.save(newSettings);
                      });
          settings.updateCurrency(currency);
          orgSettingsRepository.save(settings);
        });
  }

  String resolveCurrency(@Nullable String country, @Nullable String verticalProfile) {
    // Profile currency takes priority (checked later in setVerticalProfile)
    // Country-derived currency is the fallback for the initial OrgSettings default
    if (country != null) {
      String countryCurrency = COUNTRY_TO_CURRENCY.get(country);
      if (countryCurrency != null) {
        return countryCurrency;
      }
    }
    return "USD";
  }

  private void installPacksViaPipeline(
      String schemaName, String verticalProfile, PackType packType) {
    // Always install universal packs (verticalProfile == null in metadata)
    List<String> universalPackIds = packCatalogService.getUniversalPackIds(packType);
    for (String packId : universalPackIds) {
      packInstallService.internalInstall(packId, schemaName);
    }

    // Install profile-specific packs only when a profile is set
    if (verticalProfile != null) {
      List<String> profilePackIds =
          packCatalogService.getPackIdsForProfile(verticalProfile, packType);
      for (String packId : profilePackIds) {
        packInstallService.internalInstall(packId, schemaName);
      }
    }
  }

  public record ProvisioningResult(boolean success, String schemaName, boolean alreadyProvisioned) {

    public static ProvisioningResult success(String schemaName) {
      return new ProvisioningResult(true, schemaName, false);
    }

    public static ProvisioningResult alreadyProvisioned(String schemaName) {
      return new ProvisioningResult(true, schemaName, true);
    }
  }
}
