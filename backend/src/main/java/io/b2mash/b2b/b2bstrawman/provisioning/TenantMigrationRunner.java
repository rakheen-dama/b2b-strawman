package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ShardRegistry;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
public class TenantMigrationRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(TenantMigrationRunner.class);

  private final OrgSchemaMappingRepository mappingRepository;
  private final DataSource migrationDataSource;
  private final ShardRegistry shardRegistry;

  public TenantMigrationRunner(
      OrgSchemaMappingRepository mappingRepository,
      @Qualifier("migrationDataSource") DataSource migrationDataSource,
      ObjectProvider<ShardRegistry> shardRegistryProvider) {
    this.mappingRepository = mappingRepository;
    this.migrationDataSource = migrationDataSource;
    this.shardRegistry = shardRegistryProvider.getIfAvailable();
  }

  @Override
  public void run(ApplicationArguments args) {
    if (shardRegistry != null) {
      runShardAwareMigrations();
    } else {
      runLegacyMigrations();
    }
  }

  private void runShardAwareMigrations() {
    var activeShardIds = shardRegistry.getActiveShardIds();
    int totalSchemas = 0;

    for (String shardId : activeShardIds) {
      var dataSource = shardRegistry.getDataSource(shardId);
      var shardMappings = mappingRepository.findByShardId(shardId);

      for (var mapping : shardMappings) {
        try {
          migrateSchema(mapping.getSchemaName(), dataSource);
        } catch (Exception e) {
          log.error("Failed to migrate schema {} on shard {}", mapping.getSchemaName(), shardId, e);
        }
      }

      log.info("Migrated shard {} -- {} schemas", shardId, shardMappings.size());
      totalSchemas += shardMappings.size();
    }

    log.info(
        "Shard-aware tenant migration completed: {} shards, {} schemas",
        activeShardIds.size(),
        totalSchemas);
  }

  private void runLegacyMigrations() {
    var allMappings = mappingRepository.findAll();
    if (allMappings.isEmpty()) {
      log.info("No tenant schemas found — skipping per-tenant migrations");
      return;
    }

    log.info("Running tenant migrations for {} schemas", allMappings.size());
    for (var mapping : allMappings) {
      try {
        migrateSchema(mapping.getSchemaName(), migrationDataSource);
      } catch (Exception e) {
        log.error("Failed to migrate schema {}", mapping.getSchemaName(), e);
      }
    }
    log.info("Tenant migration runner completed");
  }

  private void migrateSchema(String schemaName, DataSource dataSource) {
    var result =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/tenant")
            .schemas(schemaName)
            .baselineOnMigrate(true)
            .load()
            .migrate();
    log.info("Migrated schema {} — {} migrations applied", schemaName, result.migrationsExecuted);
  }
}
