package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.infrastructure.testutil.SecondaryEmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * D3 regression: when {@code KAZI_SHARD_{ID}_MIGRATION_URL} is configured, the registry must expose
 * a dedicated direct-connection DataSource for DDL (so secondary shards behind PgBouncer in
 * transaction mode don't fail CREATE SCHEMA / Flyway). When it is not configured, the migration
 * DataSource falls back to the runtime pool. See kazi-infra-review-scheduling-sharding.md finding
 * D3.
 *
 * <p>The embedded test Postgres has no PgBouncer, so this verifies the routing/wiring (a distinct
 * direct DataSource is built and is DDL-capable) rather than reproducing the PgBouncer rejection.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "kazi.sharding.enabled=true",
      "KAZI_SHARD_SHARD2_USERNAME=postgres",
      "KAZI_SHARD_SHARD2_PASSWORD=postgres"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShardMigrationDataSourceTest {

  private static final String SHARD2_ID = "shard2";

  private final ShardConfigRepository shardConfigRepository;
  private final ShardRegistry shardRegistry;

  @Autowired
  ShardMigrationDataSourceTest(
      ShardConfigRepository shardConfigRepository, ShardRegistry shardRegistry) {
    this.shardConfigRepository = shardConfigRepository;
    this.shardRegistry = shardRegistry;
  }

  @DynamicPropertySource
  static void registerSecondaryShardProperties(DynamicPropertyRegistry registry) {
    // Both URLs point at the same embedded Postgres (no PgBouncer in tests). The migration URL
    // being present is what triggers the dedicated direct-connection DataSource under test.
    registry.add("KAZI_SHARD_SHARD2_URL", SecondaryEmbeddedPostgres::getJdbcUrl);
    registry.add("KAZI_SHARD_SHARD2_MIGRATION_URL", SecondaryEmbeddedPostgres::getJdbcUrl);
  }

  @BeforeAll
  void registerShard() {
    if (shardConfigRepository.findById(SHARD2_ID).isEmpty()) {
      shardConfigRepository.saveAndFlush(new ShardConfig(SHARD2_ID, "Migration DS Shard"));
    }
    shardRegistry.refresh();
  }

  @AfterAll
  void cleanUp() {
    shardConfigRepository.findById(SHARD2_ID).ifPresent(shardConfigRepository::delete);
    shardConfigRepository.flush();
    shardRegistry.refresh();
  }

  @Test
  void migrationUrlConfigured_returnsDedicatedDirectDataSource() {
    DataSource runtime = shardRegistry.getDataSource(SHARD2_ID);
    DataSource migration = shardRegistry.getMigrationDataSource(SHARD2_ID);

    assertThat(migration)
        .as("a dedicated migration DataSource must be built when the migration URL is set")
        .isNotNull()
        .isNotSameAs(runtime);
  }

  @Test
  void migrationDataSource_isDdlCapable() throws Exception {
    DataSource migration = shardRegistry.getMigrationDataSource(SHARD2_ID);
    try (Connection conn = migration.getConnection();
        Statement stmt = conn.createStatement()) {
      // DDL round-trip through the direct connection.
      stmt.execute("CREATE SCHEMA IF NOT EXISTS d3_migration_probe");
      stmt.execute("DROP SCHEMA IF EXISTS d3_migration_probe");
    }
  }

  @Test
  void noMigrationUrl_fallsBackToRuntimeDataSource() {
    // "primary" has no migration URL configured — must fall back to the runtime DataSource.
    assertThat(shardRegistry.getMigrationDataSource("primary"))
        .as("migration DataSource falls back to the runtime pool when no migration URL is set")
        .isSameAs(shardRegistry.getDataSource("primary"));
  }
}
