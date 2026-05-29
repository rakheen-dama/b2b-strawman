package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.infrastructure.testutil.SecondaryEmbeddedPostgres;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
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
 * Cross-shard data isolation test. Provisions tenants on different shards and verifies that a
 * tenant schema on shard 1 does not exist on shard 2, and vice versa. This proves that the two
 * embedded Postgres instances maintain completely separate data.
 *
 * <p>Tests at the DataSource/JDBC level since the web layer (TenantFilter, MemberSyncService) does
 * not yet bind SHARD_ID for cross-shard requests.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "kazi.sharding.enabled=true",
      "kazi.job-queue.enabled=true",
      "kazi.job-queue.auto-start=false"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShardIsolationTest {

  private static final String SHARD1_ORG_ID = "org_iso_shard1";
  private static final String SHARD2_ORG_ID = "org_iso_shard2";
  private static final String SHARD2_ID = "shard2";
  private static final String SHARD2_SCHEMA = "tenant_a1b2c3d4e5f6";

  private String shard1Schema;

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private ShardConfigRepository shardConfigRepository;
  @Autowired private ShardRegistry shardRegistry;
  @Autowired private OrgSchemaMappingRepository mappingRepository;

  @DynamicPropertySource
  static void registerSecondaryShardProperties(DynamicPropertyRegistry registry) {
    registry.add("KAZI_SHARD_SHARD2_URL", SecondaryEmbeddedPostgres::getJdbcUrl);
    registry.add("KAZI_SHARD_SHARD2_USERNAME", () -> "postgres");
    registry.add("KAZI_SHARD_SHARD2_PASSWORD", () -> "postgres");
  }

  @BeforeAll
  void setUpShardsAndTenants() throws Exception {
    // Register shard2
    if (shardConfigRepository.findById(SHARD2_ID).isEmpty()) {
      shardConfigRepository.saveAndFlush(new ShardConfig(SHARD2_ID, "Isolation Shard"));
    }
    shardRegistry.refresh();

    // Create pg_trgm extension on the secondary Postgres
    DataSource shard2Ds = shardRegistry.getDataSource(SHARD2_ID);
    try (Connection conn = shard2Ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
    }

    // Provision tenant on shard1 (primary) — full provisioning works on primary
    var shard1Result =
        provisioningService.provisionTenant(SHARD1_ORG_ID, "Isolation Shard1 Org", null);
    shard1Schema = shard1Result.schemaName();

    // Manually provision tenant on shard2
    if (mappingRepository.findByExternalOrgId(SHARD2_ORG_ID).isEmpty()) {
      try (Connection conn = shard2Ds.getConnection();
          Statement stmt = conn.createStatement()) {
        stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + SHARD2_SCHEMA + "\"");
      }
      Flyway.configure()
          .dataSource(shard2Ds)
          .locations("classpath:db/migration/tenant")
          .schemas(SHARD2_SCHEMA)
          .baselineOnMigrate(true)
          .load()
          .migrate();
      mappingRepository.saveAndFlush(new OrgSchemaMapping(SHARD2_ORG_ID, SHARD2_SCHEMA, SHARD2_ID));
    }
  }

  @AfterAll
  void cleanUpShard2Data() {
    // Remove shard2 data from the shared embedded Postgres so other test contexts that enable
    // sharding don't encounter an active shard2 config row (which would require shard2 env vars).
    mappingRepository.findByExternalOrgId(SHARD2_ORG_ID).ifPresent(mappingRepository::delete);
    shardConfigRepository.findById(SHARD2_ID).ifPresent(shardConfigRepository::delete);
    shardConfigRepository.flush();
    shardRegistry.refresh();
  }

  @Test
  void tenantOnShard1_cannotAccessShard2Data() throws Exception {
    // Verify shard1's tenant schema exists on the primary Postgres
    DataSource primaryDs = shardRegistry.getPrimaryDataSource();
    try (Connection conn = primaryDs.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = '"
                    + shard1Schema
                    + "' AND table_name = 'projects'")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1))
          .as("Shard1 should have the projects table in its tenant schema")
          .isEqualTo(1);
    }

    // Verify shard2's tenant schema does NOT exist on the primary Postgres
    try (Connection conn = primaryDs.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT COUNT(*) FROM information_schema.schemata "
                    + "WHERE schema_name = '"
                    + SHARD2_SCHEMA
                    + "'")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1))
          .as("Shard2's schema should NOT exist on the primary Postgres")
          .isEqualTo(0);
    }

    // Verify shard2's tenant schema exists on the secondary Postgres
    DataSource shard2Ds = shardRegistry.getDataSource(SHARD2_ID);
    try (Connection conn = shard2Ds.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = '"
                    + SHARD2_SCHEMA
                    + "' AND table_name = 'projects'")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1))
          .as("Shard2 should have the projects table in its tenant schema")
          .isEqualTo(1);
    }
  }

  @Test
  void wrongShardCompositeId_resultsInDataNotFound() throws Exception {
    // Shard1's tenant schema should NOT exist on shard2's Postgres
    DataSource shard2Ds = shardRegistry.getDataSource(SHARD2_ID);
    try (Connection conn = shard2Ds.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '"
                    + shard1Schema
                    + "'")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1)).as("Shard1's tenant schema should not exist on shard2").isEqualTo(0);
    }

    // Shard2's tenant schema should NOT exist on shard1's (primary) Postgres
    DataSource primaryDs = shardRegistry.getPrimaryDataSource();
    try (Connection conn = primaryDs.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '"
                    + SHARD2_SCHEMA
                    + "'")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1))
          .as("Shard2's tenant schema should not exist on primary")
          .isEqualTo(0);
    }
  }
}
