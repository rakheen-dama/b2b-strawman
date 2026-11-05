package io.b2mash.b2b.b2bstrawman.customerbackend;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.ActiveProfiles;

/**
 * Global {@code V3__portal_backfill_markers} (Phase 89 633A, §14.7.2, rulings §5): one shared
 * marker table keyed by {@code (backfill_name, tenant_schema)} for the portal read-model backfill
 * runners. A scratch DATABASE (global V1 hard-codes {@code portal.} and unqualified {@code public}
 * tables) is migrated to V2, then to V3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GlobalV3BackfillMarkersTest {

  private static final String SCRATCH_GLOBAL_DB = "global_v3_backfill_markers_scratch";

  private final DataSource migrationDataSource;
  private DataSource scratch;
  private boolean tableAbsentAtV2;

  @Autowired
  GlobalV3BackfillMarkersTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchGlobalFromV2() throws Exception {
    String serverUrl;
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute("DROP DATABASE IF EXISTS " + SCRATCH_GLOBAL_DB + " WITH (FORCE)");
      s.execute("CREATE DATABASE " + SCRATCH_GLOBAL_DB);
      serverUrl = c.getMetaData().getURL();
    }
    String scratchUrl =
        serverUrl.replaceFirst("^(jdbc:postgresql://[^/]+/)[^?]*", "$1" + SCRATCH_GLOBAL_DB);
    try (Connection c = migrationDataSource.getConnection()) {
      scratch =
          new SimpleDriverDataSource(
              new org.postgresql.Driver(), scratchUrl, c.getMetaData().getUserName(), "postgres");
    }
    flyway().target(MigrationVersion.fromVersion("2")).load().migrate();
    tableAbsentAtV2 =
        "NULL"
            .equals(
                single(
                    "SELECT coalesce(to_regclass('portal.portal_backfill_markers')::text, 'NULL')"));
    flyway().target(MigrationVersion.fromVersion("3")).load().migrate();
  }

  @AfterAll
  void dropScratchDatabase() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute("DROP DATABASE IF EXISTS " + SCRATCH_GLOBAL_DB + " WITH (FORCE)");
    }
  }

  @Test
  void tableIsAbsentAtV2AndPresentAtV3() throws Exception {
    assertThat(tableAbsentAtV2).isTrue();
    assertThat(single("SELECT to_regclass('portal.portal_backfill_markers')::text"))
        .isEqualTo("portal.portal_backfill_markers");
  }

  @Test
  void columnsMatchTheSpec() throws Exception {
    assertThat(
            rows(
                """
                SELECT column_name || '|' || data_type || '|'
                       || coalesce(character_maximum_length::text, '') || '|' || is_nullable
                       || '|' || coalesce(column_default, '')
                FROM information_schema.columns
                WHERE table_schema = 'portal' AND table_name = 'portal_backfill_markers'
                ORDER BY ordinal_position
                """))
        .containsExactly(
            "backfill_name|character varying|64|NO|",
            "tenant_schema|character varying|63|NO|",
            "backfilled_at|timestamp with time zone||NO|now()",
            "row_count|integer||NO|");
  }

  @Test
  void primaryKeyIsBackfillNameThenTenantSchemaAndNoOtherIndex() throws Exception {
    assertThat(
            single(
                """
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname = 'portal_backfill_markers_pkey' AND contype = 'p'
                  AND conrelid = 'portal.portal_backfill_markers'::regclass
                """))
        .isEqualTo("PRIMARY KEY (backfill_name, tenant_schema)");
    assertThat(
            single(
                "SELECT count(*)::text FROM pg_indexes"
                    + " WHERE schemaname = 'portal' AND tablename = 'portal_backfill_markers'"))
        .isEqualTo("1");
  }

  @Test
  void oneMarkerPerBackfillNameAndSchema() throws Exception {
    exec("DELETE FROM portal.portal_backfill_markers WHERE tenant_schema = 'tenant_v3scratch01'");
    String insert =
        "INSERT INTO portal.portal_backfill_markers (backfill_name, tenant_schema, row_count)"
            + " VALUES ('%s', 'tenant_v3scratch01', %d)"
            + " ON CONFLICT (backfill_name, tenant_schema) DO NOTHING";
    exec(insert.formatted("portal_task_backfill", 9));
    exec(insert.formatted("portal_task_backfill", 4));
    exec(insert.formatted("portal_deadline_backfill", 0));

    assertThat(
            rows(
                "SELECT backfill_name || '|' || row_count || '|' || (backfilled_at IS NOT NULL)"
                    + " FROM portal.portal_backfill_markers"
                    + " WHERE tenant_schema = 'tenant_v3scratch01' ORDER BY backfill_name"))
        .containsExactly("portal_deadline_backfill|0|true", "portal_task_backfill|9|true");
  }

  @Test
  void reRunningTheV3BodyAndMigratingAgainAreNoOps() throws Exception {
    String v3 =
        new String(
            getClass()
                .getResourceAsStream("/db/migration/global/V3__portal_backfill_markers.sql")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    exec(v3);

    assertThat(
            flyway().target(MigrationVersion.fromVersion("3")).load().migrate().migrationsExecuted)
        .isZero();
    assertThat(
            single(
                "SELECT max(version::int) || '|' || count(*) || '|' || bool_and(success)"
                    + " FROM public.flyway_schema_history"))
        .isEqualTo("3|3|true");
  }

  // Same config as FlywayConfig.globalFlyway, on the scratch database.
  private FluentConfiguration flyway() {
    return Flyway.configure()
        .dataSource(scratch)
        .locations("classpath:db/migration/global")
        .schemas("public")
        .baselineOnMigrate(false);
  }

  private void exec(String sql) throws Exception {
    try (Connection c = scratch.getConnection();
        Statement s = c.createStatement()) {
      s.execute(sql);
    }
  }

  private String single(String sql) throws Exception {
    List<String> out = rows(sql);
    assertThat(out).hasSize(1);
    return out.getFirst();
  }

  private List<String> rows(String sql) throws Exception {
    List<String> out = new ArrayList<>();
    try (Connection c = scratch.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(sql)) {
      while (rs.next()) {
        out.add(rs.getString(1));
      }
    }
    return out;
  }
}
