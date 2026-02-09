package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantFilter;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TenantUpgradeService {

  private static final Logger log = LoggerFactory.getLogger(TenantUpgradeService.class);
  private static final String SHARED_SCHEMA = "tenant_shared";

  private final OrganizationRepository organizationRepository;
  private final OrgSchemaMappingRepository mappingRepository;
  private final DataSource migrationDataSource;
  private final JdbcTemplate migrationJdbc;
  private final TransactionTemplate migrationTxTemplate;
  private final TenantFilter tenantFilter;

  public TenantUpgradeService(
      OrganizationRepository organizationRepository,
      OrgSchemaMappingRepository mappingRepository,
      @Qualifier("migrationDataSource") DataSource migrationDataSource,
      TenantFilter tenantFilter) {
    this.organizationRepository = organizationRepository;
    this.mappingRepository = mappingRepository;
    this.migrationDataSource = migrationDataSource;
    this.migrationJdbc = new JdbcTemplate(migrationDataSource);
    this.tenantFilter = tenantFilter;

    var txManager = new DataSourceTransactionManager(migrationDataSource);
    this.migrationTxTemplate = new TransactionTemplate(txManager);
    this.migrationTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Upgrades a Starter org from tenant_shared to a dedicated tenant_<hash> schema. Performs schema
   * creation, Flyway migrations, data copy, atomic cutover, and cache invalidation. All steps are
   * idempotent — safe to retry after partial failure.
   */
  public void upgrade(String clerkOrgId) {
    var org =
        organizationRepository
            .findByClerkOrgId(clerkOrgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", clerkOrgId));

    if (org.getTier() != Tier.PRO) {
      throw new InvalidStateException(
          "Invalid tier for upgrade", "Organization " + clerkOrgId + " is not on the Pro tier");
    }

    // Already on a dedicated schema — idempotent no-op
    var currentMapping = mappingRepository.findByClerkOrgId(clerkOrgId);
    if (currentMapping.isPresent() && !SHARED_SCHEMA.equals(currentMapping.get().getSchemaName())) {
      log.info(
          "Organization {} already on dedicated schema {}, skipping",
          clerkOrgId,
          currentMapping.get().getSchemaName());
      return;
    }

    log.info("Starting Starter → Pro upgrade for org {}", clerkOrgId);

    // Step 1: Mark upgrade in progress
    org.markInProgress();
    organizationRepository.save(org);
    organizationRepository.flush();

    try {
      // Step 2: Generate dedicated schema name
      String newSchema = SchemaNameGenerator.generateSchemaName(clerkOrgId);
      log.info("Target schema for org {}: {}", clerkOrgId, newSchema);

      // Step 3: Create schema (idempotent)
      createSchema(newSchema);

      // Step 4: Run Flyway V1-V8 migrations
      runTenantMigrations(newSchema);

      // Step 5: Copy data from shared to dedicated
      copyData(clerkOrgId, newSchema);

      // Step 6: Atomic cutover — update mapping + delete shared data
      atomicCutover(clerkOrgId, newSchema);

      // Step 7: Invalidate TenantFilter cache
      tenantFilter.evictSchema(clerkOrgId);

      // Step 8: Mark completed
      org.markCompleted();
      organizationRepository.save(org);

      log.info("Successfully upgraded org {} to dedicated schema {}", clerkOrgId, newSchema);
    } catch (Exception e) {
      log.error("Upgrade failed for org {}", clerkOrgId, e);
      org.markFailed();
      organizationRepository.save(org);
      throw new ProvisioningException("Tier upgrade failed for org " + clerkOrgId, e);
    }
  }

  private void createSchema(String schemaName) throws SQLException {
    validateSchemaName(schemaName);
    try (var conn = migrationDataSource.getConnection();
        var stmt = conn.createStatement()) {
      stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");
      log.info("Created schema {}", schemaName);
    }
  }

  private void runTenantMigrations(String schemaName) {
    var result =
        Flyway.configure()
            .dataSource(migrationDataSource)
            .locations("classpath:db/migration/tenant")
            .schemas(schemaName)
            .baselineOnMigrate(true)
            .load()
            .migrate();
    log.info("Migrated schema {} — {} migrations applied", schemaName, result.migrationsExecuted);
  }

  /** Copies data from tenant_shared to the dedicated schema, excluding tenant_id. */
  private void copyData(String clerkOrgId, String newSchema) {
    validateSchemaName(newSchema);

    // Copy order respects FK constraints: members → projects → documents + project_members
    int members =
        migrationJdbc.update(
            "INSERT INTO \""
                + newSchema
                + "\".members"
                + " (id, clerk_user_id, email, name, avatar_url, org_role, created_at, updated_at)"
                + " SELECT id, clerk_user_id, email, name, avatar_url, org_role, created_at,"
                + " updated_at"
                + " FROM tenant_shared.members WHERE tenant_id = ?"
                + " ON CONFLICT (id) DO NOTHING",
            clerkOrgId);

    int projects =
        migrationJdbc.update(
            "INSERT INTO \""
                + newSchema
                + "\".projects"
                + " (id, name, description, created_by, created_at, updated_at)"
                + " SELECT id, name, description, created_by, created_at, updated_at"
                + " FROM tenant_shared.projects WHERE tenant_id = ?"
                + " ON CONFLICT (id) DO NOTHING",
            clerkOrgId);

    int documents =
        migrationJdbc.update(
            "INSERT INTO \""
                + newSchema
                + "\".documents"
                + " (id, project_id, file_name, content_type, size, s3_key, status, uploaded_by,"
                + " uploaded_at, created_at)"
                + " SELECT id, project_id, file_name, content_type, size, s3_key, status,"
                + " uploaded_by, uploaded_at, created_at"
                + " FROM tenant_shared.documents WHERE tenant_id = ?"
                + " ON CONFLICT (id) DO NOTHING",
            clerkOrgId);

    int projectMembers =
        migrationJdbc.update(
            "INSERT INTO \""
                + newSchema
                + "\".project_members"
                + " (id, project_id, member_id, project_role, added_by, created_at)"
                + " SELECT id, project_id, member_id, project_role, added_by, created_at"
                + " FROM tenant_shared.project_members WHERE tenant_id = ?"
                + " ON CONFLICT (id) DO NOTHING",
            clerkOrgId);

    log.info(
        "Copied data for org {}: {} members, {} projects, {} documents, {} project_members",
        clerkOrgId,
        members,
        projects,
        documents,
        projectMembers);
  }

  /**
   * Atomically updates the schema mapping and deletes shared data in a single transaction. Delete
   * order is reverse of copy (FK constraints): project_members → documents → projects → members.
   */
  private void atomicCutover(String clerkOrgId, String newSchema) {
    migrationTxTemplate.executeWithoutResult(
        status -> {
          // Update mapping to point to dedicated schema
          int updated =
              migrationJdbc.update(
                  "UPDATE public.org_schema_mapping SET schema_name = ? WHERE clerk_org_id = ?",
                  newSchema,
                  clerkOrgId);
          if (updated == 0) {
            throw new IllegalStateException("No org_schema_mapping found for org " + clerkOrgId);
          }

          // Delete shared data in reverse FK order
          int pm =
              migrationJdbc.update(
                  "DELETE FROM tenant_shared.project_members WHERE tenant_id = ?", clerkOrgId);
          int docs =
              migrationJdbc.update(
                  "DELETE FROM tenant_shared.documents WHERE tenant_id = ?", clerkOrgId);
          int proj =
              migrationJdbc.update(
                  "DELETE FROM tenant_shared.projects WHERE tenant_id = ?", clerkOrgId);
          int mem =
              migrationJdbc.update(
                  "DELETE FROM tenant_shared.members WHERE tenant_id = ?", clerkOrgId);

          log.info(
              "Atomic cutover for org {} → {}: deleted {} project_members, {} documents,"
                  + " {} projects, {} members from shared",
              clerkOrgId,
              newSchema,
              pm,
              docs,
              proj,
              mem);
        });
  }

  private void validateSchemaName(String schemaName) {
    if (!schemaName.matches("^tenant_[0-9a-f]{12}$")) {
      throw new IllegalArgumentException("Invalid schema name: " + schemaName);
    }
  }
}
