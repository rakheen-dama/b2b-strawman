package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.billing.SubscriptionService;
import io.b2mash.b2b.b2bstrawman.clause.ClausePackSeeder;
import io.b2mash.b2b.b2bstrawman.compliance.CompliancePackSeeder;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldPackSeeder;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.reporting.StandardReportPackSeeder;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackSeeder;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class TenantProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);

  private final OrganizationRepository organizationRepository;
  private final OrgSchemaMappingRepository mappingRepository;
  private final DataSource migrationDataSource;
  private final SubscriptionService subscriptionService;
  private final FieldPackSeeder fieldPackSeeder;
  private final TemplatePackSeeder templatePackSeeder;
  private final ClausePackSeeder clausePackSeeder;
  private final CompliancePackSeeder compliancePackSeeder;
  private final StandardReportPackSeeder standardReportPackSeeder;

  public TenantProvisioningService(
      OrganizationRepository organizationRepository,
      OrgSchemaMappingRepository mappingRepository,
      @Qualifier("migrationDataSource") DataSource migrationDataSource,
      SubscriptionService subscriptionService,
      FieldPackSeeder fieldPackSeeder,
      TemplatePackSeeder templatePackSeeder,
      ClausePackSeeder clausePackSeeder,
      CompliancePackSeeder compliancePackSeeder,
      StandardReportPackSeeder standardReportPackSeeder) {
    this.organizationRepository = organizationRepository;
    this.mappingRepository = mappingRepository;
    this.migrationDataSource = migrationDataSource;
    this.subscriptionService = subscriptionService;
    this.fieldPackSeeder = fieldPackSeeder;
    this.templatePackSeeder = templatePackSeeder;
    this.clausePackSeeder = clausePackSeeder;
    this.compliancePackSeeder = compliancePackSeeder;
    this.standardReportPackSeeder = standardReportPackSeeder;
  }

  @Retryable(
      retryFor = ProvisioningException.class,
      noRetryFor = IllegalArgumentException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2))
  public ProvisioningResult provisionTenant(String clerkOrgId, String orgName) {
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
      // Mapping is created LAST so TenantFilter only resolves to this
      // schema once all tables exist (prevents race with first request).
      createSchema(schemaName);
      runTenantMigrations(schemaName);
      fieldPackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
      templatePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
      clausePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
      compliancePackSeeder.seedPacksForTenant(schemaName, clerkOrgId);
      standardReportPackSeeder.seedForTenant(schemaName, clerkOrgId);
      createMapping(clerkOrgId, schemaName);
      String planSlug = org.getPlanSlug() != null ? org.getPlanSlug() : "starter";
      subscriptionService.createSubscription(org.getId(), planSlug);

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

  public record ProvisioningResult(boolean success, String schemaName, boolean alreadyProvisioned) {

    public static ProvisioningResult success(String schemaName) {
      return new ProvisioningResult(true, schemaName, false);
    }

    public static ProvisioningResult alreadyProvisioned(String schemaName) {
      return new ProvisioningResult(true, schemaName, true);
    }
  }
}
