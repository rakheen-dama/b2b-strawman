package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
      "KAZI_SHARD_SHARD2_PASSWORD=postgres",
      "KAZI_SHARD_SHARD3_USERNAME=postgres",
      "KAZI_SHARD_SHARD3_PASSWORD=postgres"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShardMigrationDataSourceTest {

  private static final String SHARD2_ID = "shard2"; // has a dedicated migration URL
  private static final String SHARD3_ID = "shard3"; // no migration URL — exercises the fallback

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
    // shard3 deliberately has NO _MIGRATION_URL, to exercise the runtime-pool fallback.
    registry.add("KAZI_SHARD_SHARD3_URL", SecondaryEmbeddedPostgres::getJdbcUrl);
  }

  @BeforeAll
  void registerShards() {
    if (shardConfigRepository.findById(SHARD2_ID).isEmpty()) {
      shardConfigRepository.saveAndFlush(new ShardConfig(SHARD2_ID, "Migration DS Shard"));
    }
    if (shardConfigRepository.findById(SHARD3_ID).isEmpty()) {
      shardConfigRepository.saveAndFlush(new ShardConfig(SHARD3_ID, "No-Migration-URL Shard"));
    }
    shardRegistry.refresh();
  }

  @AfterAll
  void cleanUp() {
    shardConfigRepository.findById(SHARD2_ID).ifPresent(shardConfigRepository::delete);
    shardConfigRepository.findById(SHARD3_ID).ifPresent(shardConfigRepository::delete);
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
  void secondaryShardWithoutMigrationUrl_fallsBackToRuntimeDataSource() {
    // shard3 is a secondary shard with no _MIGRATION_URL — must fall back to its runtime pool
    // (correct for shards that connect directly without PgBouncer).
    assertThat(shardRegistry.getMigrationDataSource(SHARD3_ID))
        .as("migration DataSource falls back to the runtime pool when no migration URL is set")
        .isSameAs(shardRegistry.getDataSource(SHARD3_ID));
  }

  @Test
  void refresh_whenShardMisconfigured_throwsAndLeavesLiveRegistryIntact() {
    var before = shardRegistry.getActiveShardIds();
    // An active shard with no connection env vars makes createSecondaryDataSource throw
    // mid-refresh,
    // before the atomic swap. The live registry must remain the previous good set and stay usable —
    // and the pools opened during the aborted refresh must not leak (CodeRabbit D3 finding).
    shardConfigRepository.saveAndFlush(new ShardConfig("shard_bad", "Misconfigured shard"));
    try {
      assertThatThrownBy(shardRegistry::refresh).isInstanceOf(IllegalStateException.class);

      assertThat(shardRegistry.getActiveShardIds())
          .as("a failed refresh must not mutate the live shard set")
          .containsExactlyInAnyOrderElementsOf(before);
      assertThat(shardRegistry.getDataSource(SHARD2_ID))
          .as("previously registered shards remain usable after a failed refresh")
          .isNotNull();
    } finally {
      shardConfigRepository.findById("shard_bad").ifPresent(shardConfigRepository::delete);
      shardConfigRepository.flush();
      shardRegistry.refresh();
    }
  }
}
