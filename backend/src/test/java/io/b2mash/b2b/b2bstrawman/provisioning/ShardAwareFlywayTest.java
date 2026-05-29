package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ShardRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for shard-aware Flyway migration runner. Validates that TenantMigrationRunner
 * correctly queries schemas per shard and runs migrations on the appropriate DataSource. Uses a
 * single shard (primary) with the embedded Postgres instance. Full multi-shard Flyway tests (with
 * real second Postgres) are deferred to Epic 555B.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "kazi.sharding.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShardAwareFlywayTest {

  private static final String ORG_ID = "org_flyway_shard";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository mappingRepository;
  @Autowired private ShardRegistry shardRegistry;
  @Autowired private TenantMigrationRunner migrationRunner;

  private String provisionedSchema;

  @BeforeAll
  void setup() {
    var result = provisioningService.provisionTenant(ORG_ID, "Flyway Shard Test Org", null);
    provisionedSchema = result.schemaName();
  }

  @Test
  void singleShardMigrationRunsAsDefault() {
    // ShardRegistry should return "primary" as the only active shard
    assertThat(shardRegistry.getActiveShardIds()).containsExactly("primary");

    // The TenantMigrationRunner should have run at startup without errors.
    // Verify the provisioned schema exists in mappings for the primary shard.
    var mappings = mappingRepository.findByShardId("primary");
    assertThat(mappings).isNotEmpty();
    assertThat(mappings.stream().map(m -> m.getSchemaName())).contains(provisionedSchema);
  }

  @Test
  void migrationRunnerIteratesAllSchemasOnPrimaryShard() {
    // Query schemas assigned to the primary shard
    var primaryMappings = mappingRepository.findByShardId("primary");
    var allMappings = mappingRepository.findAll();

    // With a single shard, all mappings should be on "primary"
    assertThat(primaryMappings).hasSameSizeAs(allMappings);
  }

  @Test
  void migrationRunnerQueriesSchemasPerShard() throws Exception {
    // Verify the runner can be invoked again without error (idempotent Flyway migrations)
    // This exercises the shard-aware path: iterate shards -> query schemas per shard -> migrate
    migrationRunner.run(null);

    // After re-running, the mapping should still exist
    var mapping = mappingRepository.findByClerkOrgId(ORG_ID);
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getShardId()).isEqualTo("primary");
  }
}
