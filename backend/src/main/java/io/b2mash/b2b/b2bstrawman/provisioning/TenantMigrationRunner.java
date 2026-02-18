package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
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
    var allMappings = mappingRepository.findAll();
    if (allMappings.isEmpty()) {
      log.info("No tenant schemas found — skipping per-tenant migrations");
      return;
    }

    log.info("Running tenant migrations for {} schemas", allMappings.size());
    for (var mapping : allMappings) {
      try {
        migrateSchema(mapping.getSchemaName());
      } catch (Exception e) {
        log.error("Failed to migrate schema {}", mapping.getSchemaName(), e);
      }
    }
    log.info("Tenant migration runner completed");
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
