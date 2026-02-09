package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TenantMigrationRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(TenantMigrationRunner.class);
  static final String SHARED_SCHEMA = "tenant_shared";

  private final OrgSchemaMappingRepository mappingRepository;
  private final DataSource migrationDataSource;

  public TenantMigrationRunner(
      OrgSchemaMappingRepository mappingRepository,
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.mappingRepository = mappingRepository;
    this.migrationDataSource = migrationDataSource;
  }

  @Override
  public void run(ApplicationArguments args) {
    bootstrapSharedSchema();

    var allMappings = mappingRepository.findAll();
    if (allMappings.isEmpty()) {
      log.info("No tenant schemas found — skipping per-tenant migrations");
      return;
    }

    log.info("Running tenant migrations for {} schemas", allMappings.size());
    for (var mapping : allMappings) {
      if (SHARED_SCHEMA.equals(mapping.getSchemaName())) {
        continue; // Already migrated above
      }
      try {
        migrateSchema(mapping.getSchemaName());
      } catch (Exception e) {
        log.error("Failed to migrate schema {}", mapping.getSchemaName(), e);
      }
    }
    log.info("Tenant migration runner completed");
  }

  /**
   * Bootstraps the shared schema used by Starter-tier organizations. Creates the schema if it
   * doesn't exist and runs all tenant migrations against it. Idempotent — Flyway tracks applied
   * versions, so subsequent startups are no-ops.
   */
  private void bootstrapSharedSchema() {
    try (Connection conn = migrationDataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE SCHEMA IF NOT EXISTS " + SHARED_SCHEMA);
      log.info("Ensured shared schema '{}' exists", SHARED_SCHEMA);
    } catch (SQLException e) {
      log.error("Failed to create shared schema '{}'", SHARED_SCHEMA, e);
      throw new RuntimeException("Failed to bootstrap shared schema", e);
    }

    migrateSchema(SHARED_SCHEMA);
  }

  private void migrateSchema(String schemaName) {
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
}
