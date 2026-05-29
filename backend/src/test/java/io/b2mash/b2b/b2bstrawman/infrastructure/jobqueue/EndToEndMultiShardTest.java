package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.infrastructure.testutil.SecondaryEmbeddedPostgres;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ShardConfig;
import io.b2mash.b2b.b2bstrawman.multitenancy.ShardConfigRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ShardRegistry;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
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
 * End-to-end multi-shard integration test. Registers a secondary shard (backed by a separate
 * embedded Postgres instance), manually provisions a tenant schema on it, enqueues a job targeting
 * that tenant, and verifies the shard routing infrastructure works correctly.
 *
 * <p>Tests operate at the infrastructure layer (ShardRegistry, DataSource routing, job queue)
 * rather than through MockMvc, because the web layer's MemberSyncService and TenantFilter do not
 * yet bind SHARD_ID for cross-shard member sync — that is a future epic. The infrastructure-level
 * routing tested here is the foundation those higher-layer changes will build on.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "kazi.sharding.enabled=true",
      "kazi.job-queue.enabled=true",
      "kazi.job-queue.auto-start=false",
      "KAZI_SHARD_SHARD2_USERNAME=postgres",
      "KAZI_SHARD_SHARD2_PASSWORD=postgres"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndMultiShardTest {

  private static final String SHARD2_ORG_ID = "org_e2e_shard2";
  private static final String SHARD2_ID = "shard2";
  private static final String SHARD2_SCHEMA = "tenant_e2e000000002";

  private final ShardConfigRepository shardConfigRepository;
  private final ShardRegistry shardRegistry;
  private final OrgSchemaMappingRepository mappingRepository;
  private final JobQueueRepository jobQueueRepository;
  private final JobEnqueuer jobEnqueuer;
  private final JobWorker worker;

  @Autowired
  EndToEndMultiShardTest(
      ShardConfigRepository shardConfigRepository,
      ShardRegistry shardRegistry,
      OrgSchemaMappingRepository mappingRepository,
      JobQueueRepository jobQueueRepository,
      JobEnqueuer jobEnqueuer,
      JobWorker worker) {
    this.shardConfigRepository = shardConfigRepository;
    this.shardRegistry = shardRegistry;
    this.mappingRepository = mappingRepository;
    this.jobQueueRepository = jobQueueRepository;
    this.jobEnqueuer = jobEnqueuer;
    this.worker = worker;
  }

  @DynamicPropertySource
  static void registerSecondaryShardProperties(DynamicPropertyRegistry registry) {
    // SecondaryEmbeddedPostgres JDBC URL is runtime-generated (random port),
    // so @DynamicPropertySource is the correct pattern here. Static credentials
    // are in @TestPropertySource above.
    registry.add("KAZI_SHARD_SHARD2_URL", SecondaryEmbeddedPostgres::getJdbcUrl);
  }

  @BeforeAll
  void setUpSecondaryShardAndTenant() throws Exception {
    // Register shard2 in the shard_config table (if not already present)
    if (shardConfigRepository.findById(SHARD2_ID).isEmpty()) {
      shardConfigRepository.saveAndFlush(new ShardConfig(SHARD2_ID, "Secondary Shard"));
    }

    // Refresh the ShardRegistry so it picks up shard2's DataSource
    shardRegistry.refresh();

    // Create pg_trgm extension on the secondary Postgres — required by tenant Flyway migrations
    // (V83__create_legal_foundation_tables.sql uses gin_trgm_ops). The primary DB has this from
    // global migration V16, but the secondary embedded Postgres starts empty.
    DataSource shard2Ds = shardRegistry.getDataSource(SHARD2_ID);
    try (Connection conn = shard2Ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
    }

    // Manually provision a tenant schema on shard2.
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
  void cleanUpShard2Data() throws Exception {
    // Drop the marker table created by ShardRoutingTestJobHandler so the test is repeatable within
    // the same JVM (the secondary Postgres is a JVM-scoped singleton). Must happen before refresh()
    // deregisters shard2's DataSource.
    try (Connection conn = shardRegistry.getDataSource(SHARD2_ID).getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "DROP TABLE IF EXISTS \""
              + SHARD2_SCHEMA
              + "\"."
              + ShardRoutingTestJobHandler.MARKER_TABLE);
    }

    // Remove shard2 data from the shared embedded Postgres so other test contexts that enable
    // sharding don't encounter an active shard2 config row (which would require shard2 env vars).
    mappingRepository.findByExternalOrgId(SHARD2_ORG_ID).ifPresent(mappingRepository::delete);
    shardConfigRepository.findById(SHARD2_ID).ifPresent(shardConfigRepository::delete);
    shardConfigRepository.flush();
    shardRegistry.refresh();
  }

  @Test
  void provisionTenantOnShard2_enqueueJob_correctShardReference() {
    // Verify the mapping was created pointing to shard2
    var mapping = mappingRepository.findByExternalOrgId(SHARD2_ORG_ID);
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getShardId()).isEqualTo(SHARD2_ID);

    // Enqueue a job targeting the shard2 tenant
    String tenantId = mapping.get().getSchemaName();
    var job = new JobQueue("E2E_SHARD2_JOB", tenantId, SHARD2_ORG_ID, SHARD2_ID, null, 3);
    jobQueueRepository.saveAndFlush(job);

    // Verify the job was persisted with correct shard reference
    var persisted = jobQueueRepository.findById(job.getId());
    assertThat(persisted).isPresent();
    assertThat(persisted.get().getShardId()).isEqualTo(SHARD2_ID);
    assertThat(persisted.get().getTenantId()).isEqualTo(tenantId);
    assertThat(persisted.get().getStatus()).isEqualTo(JobStatus.PENDING);

    // Clean up
    jobQueueRepository.delete(job);
    jobQueueRepository.flush();
  }

  /**
   * D2 regression: {@code fanOutToAllTenants} must stamp each job with the tenant's actual shard id
   * from its {@code OrgSchemaMapping}, not a hardcoded "primary".
   */
  @Test
  void fanOutToAllTenants_stampsShardIdFromMapping() {
    jobQueueRepository.deleteAllInBatch();

    jobEnqueuer.fanOutToAllTenants(ShardRoutingTestJobHandler.JOB_TYPE, null);

    var shard2Job =
        jobQueueRepository.findAll().stream()
            .filter(j -> j.getJobType().equals(ShardRoutingTestJobHandler.JOB_TYPE))
            .filter(j -> j.getTenantId().equals(SHARD2_SCHEMA))
            .findFirst();

    assertThat(shard2Job).as("fan-out should create a job for the shard2 tenant").isPresent();
    assertThat(shard2Job.get().getShardId())
        .as("job for a shard2 tenant must carry shardId=shard2, not the default 'primary'")
        .isEqualTo(SHARD2_ID);

    jobQueueRepository.deleteAllInBatch();
  }

  /**
   * D1 regression: the worker must bind {@code SHARD_ID} before invoking the handler, so a job for
   * a secondary-shard tenant executes against that shard's physical database — not primary. The
   * handler writes a marker row via Hibernate; we assert it lands in shard2 and that shard2's
   * schema does not exist on primary at all.
   */
  @Test
  void jobForShard2Tenant_executesAgainstShard2Database() throws Exception {
    jobQueueRepository.deleteAllInBatch();
    DataSource shard2Ds = shardRegistry.getDataSource(SHARD2_ID);
    assertThat(countMarkerRows(shard2Ds, SHARD2_SCHEMA))
        .as("no marker rows before the job runs")
        .isZero();

    // Enqueue (committed) a job for the shard2 tenant, then drive it through the worker.
    var job =
        new JobQueue(
            ShardRoutingTestJobHandler.JOB_TYPE, SHARD2_SCHEMA, SHARD2_ORG_ID, SHARD2_ID, null, 0);
    jobQueueRepository.saveAndFlush(job);

    try {
      worker.start();
      await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofMillis(250))
          .untilAsserted(
              () -> {
                var current = jobQueueRepository.findById(job.getId()).orElseThrow();
                assertThat(current.getStatus()).isEqualTo(JobStatus.COMPLETED);
              });
    } finally {
      worker.stop();
    }

    // The marker must land in the shard2 database...
    assertThat(countMarkerRows(shard2Ds, SHARD2_SCHEMA))
        .as("handler write must land in the shard2 tenant schema")
        .isEqualTo(1);

    // ...and shard2's tenant schema must not exist on primary at all.
    DataSource primaryDs = shardRegistry.getPrimaryDataSource();
    try (Connection conn = primaryDs.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '"
                    + SHARD2_SCHEMA
                    + "'")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1)).as("shard2 tenant schema must not exist on primary").isZero();
    }

    jobQueueRepository.deleteAllInBatch();
  }

  private long countMarkerRows(DataSource ds, String schema) throws Exception {
    String qualifiedTable = "\"" + schema + "\"." + ShardRoutingTestJobHandler.MARKER_TABLE;
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      // Guard: the marker table is created lazily by the handler, so it may not exist yet.
      try (ResultSet exists =
          stmt.executeQuery("SELECT to_regclass('" + qualifiedTable + "') IS NOT NULL")) {
        assertThat(exists.next()).isTrue();
        if (!exists.getBoolean(1)) {
          return 0L;
        }
      }
      try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + qualifiedTable)) {
        assertThat(rs.next()).isTrue();
        return rs.getLong(1);
      }
    }
  }

  @Test
  void shard2DataSource_routesToSeparatePostgres() throws Exception {
    // Verify both shards are active in the registry
    assertThat(shardRegistry.getActiveShardIds()).contains("primary", SHARD2_ID);

    // Verify shard2's DataSource connects to the secondary Postgres
    DataSource shard2Ds = shardRegistry.getDataSource(SHARD2_ID);
    try (Connection conn = shard2Ds.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT 1 AS probe")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt("probe")).isEqualTo(1);
    }
  }

  @Test
  void flyway_migrationsRunOnShard2() throws Exception {
    // Verify that Flyway created the tenant schema with tables on shard2
    DataSource shard2Ds = shardRegistry.getDataSource(SHARD2_ID);
    try (Connection conn = shard2Ds.getConnection();
        Statement stmt = conn.createStatement()) {
      // Set search_path to the tenant schema
      stmt.execute("SET search_path TO \"" + SHARD2_SCHEMA + "\"");

      // Verify a core tenant table exists (projects is created by an early migration)
      try (ResultSet rs =
          stmt.executeQuery(
              "SELECT COUNT(*) FROM information_schema.tables "
                  + "WHERE table_schema = '"
                  + SHARD2_SCHEMA
                  + "' AND table_name = 'projects'")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(1);
      }
    }

    // Verify the mapping reflects shard2
    var mapping = mappingRepository.findByExternalOrgId(SHARD2_ORG_ID);
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getShardId()).isEqualTo(SHARD2_ID);
    assertThat(mapping.get().getSchemaName()).isEqualTo(SHARD2_SCHEMA);
  }
}
