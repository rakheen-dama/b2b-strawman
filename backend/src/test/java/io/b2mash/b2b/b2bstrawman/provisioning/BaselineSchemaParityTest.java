package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 86 migration-squash proof (ADR-331, architecture §11.7(e)) and the permanent proof of
 * {@code V1__baseline.sql}. Migrates a scratch schema with the tenant Flyway configuration used by
 * {@link TenantProvisioningService} but {@code .target("1")}, dumps it and the global {@code
 * public}/{@code portal} schemas (tables, columns, constraints, indexes, triggers, functions,
 * extensions, migration-seeded rows) in a normalised, ordinal-free form and compares them
 * byte-for-byte with snapshots committed BEFORE the series was squashed (Epic 605A). The only
 * permitted difference between the old series and {@code V1__baseline.sql} is the number of files.
 *
 * <p>Pinning the tenant comparison to V1 keeps the snapshots frozen while feature migrations (V2+)
 * are added: those get their own targeted tests and never re-record these snapshots (see {@code
 * backend/db/BASELINE.md} §5; the target-1 conversion landed with tenant V2). The scratch schema's
 * history must be exactly one successful SQL row, version 1. A tenant provisioned through the real
 * {@link TenantProvisioningService} path must have one successful row per tenant migration file on
 * the classpath, so V2+ still apply on provisioning.
 *
 * <p>The global comparison is pinned the same way (Phase 88 624A, before global V2 landed): the
 * global series is migrated into a scratch DATABASE with {@code .target("1")} — a scratch schema
 * cannot work because global V1 hard-codes {@code portal.} and the unqualified {@code public}
 * tables — and that database's {@code public}/{@code portal} are compared with {@code
 * global-baseline.snapshot}. Its history must be exactly one successful SQL row, version 1. The
 * live global history (migrated at startup by {@code FlywayConfig.globalFlyway}) must have one
 * successful row per global migration file on the classpath, so V2+ still apply at startup.
 *
 * <p>The snapshots pin the shape a tenant gets on an EMPTY database. The pre-squash series was not
 * deterministic across tenants: V7/V8/V10/V16/V27/V29/V127 guarded nine objects with unscoped
 * catalog lookups ({@code pg_constraint WHERE conname = …}, {@code information_schema.columns WHERE
 * table_name = …}), so only the first tenant in a database received them. The squashed baseline has
 * no such guards and gives every tenant the full shape.
 *
 * <p>Do NOT re-record the snapshots for a feature migration. {@code BASELINE_SNAPSHOT_RECORD=true}
 * exists only to repair the recorder itself, never to make a migration pass.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaselineSchemaParityTest {

  private static final String ORG_ID = "org_baseline_schema_parity";
  // Never provisioned or mapped — only names the scratch schema migrated to V1.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_baseline_schema_parity_v1_scratch");
  // Never used by the application — only holds the global series migrated to V1. A database (not a
  // schema) because global V1 creates/qualifies `portal.` and writes unqualified `public` tables.
  private static final String SCRATCH_GLOBAL_DB = "baseline_parity_global_v1_scratch";
  private static final String TENANT_TOKEN = "<tenant>";
  // PG16 information_schema synthesises NOT NULL "constraints" named
  // <nspoid>_<reloid>_<attnum>_not_null.
  private static final String NOT_NULL_PSEUDO = "^[0-9]+_[0-9]+_[0-9]+_not_null$";
  private static final Path SNAPSHOT_DIR = Path.of("src/test/resources/schema");
  // Recorder repair only — never re-record for a feature migration (BASELINE.md §5).
  private static final boolean RECORD =
      "true".equalsIgnoreCase(System.getenv("BASELINE_SNAPSHOT_RECORD"));

  private final TenantProvisioningService provisioningService;
  private final DataSource migrationDataSource;
  private String provisionedSchema;
  private DataSource scratchGlobalDataSource;

  @Autowired
  BaselineSchemaParityTest(
      TenantProvisioningService provisioningService,
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.provisioningService = provisioningService;
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void setUp() throws Exception {
    provisionedSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Baseline Schema Parity Org", null)
            .schemaName();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute("DROP SCHEMA IF EXISTS \"" + SCRATCH_SCHEMA + "\" CASCADE");
      // Created up front (as provisioning does) so Flyway records no SCHEMA-creation row.
      s.execute("CREATE SCHEMA \"" + SCRATCH_SCHEMA + "\"");
    }
    // Same locations/config as TenantProvisioningService.runTenantMigrations, pinned to V1.
    Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(SCRATCH_SCHEMA)
        .baselineOnMigrate(false)
        .target(MigrationVersion.fromVersion("1"))
        .load()
        .migrate();

    // Global series: a scratch DATABASE on the same server, same config as
    // FlywayConfig.globalFlyway, pinned to V1. CREATE/DROP DATABASE need autocommit (Hikari
    // default) and cannot run in a transaction.
    String serverUrl;
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute("DROP DATABASE IF EXISTS " + SCRATCH_GLOBAL_DB + " WITH (FORCE)");
      s.execute("CREATE DATABASE " + SCRATCH_GLOBAL_DB);
      serverUrl = c.getMetaData().getURL();
    }
    // jdbc:postgresql://host:port/<db>[?params] → same server, scratch database
    String scratchUrl =
        serverUrl.replaceFirst("^(jdbc:postgresql://[^/]+/)[^?]*", "$1" + SCRATCH_GLOBAL_DB);
    try (Connection c = migrationDataSource.getConnection()) {
      scratchGlobalDataSource =
          new SimpleDriverDataSource(
              new org.postgresql.Driver(), scratchUrl, c.getMetaData().getUserName(), "postgres");
    }
    Flyway.configure()
        .dataSource(scratchGlobalDataSource)
        .locations("classpath:db/migration/global")
        .schemas("public")
        .baselineOnMigrate(false)
        .target(MigrationVersion.fromVersion("1"))
        .load()
        .migrate();
  }

  @AfterAll
  void dropScratchSchema() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute("DROP SCHEMA IF EXISTS \"" + SCRATCH_SCHEMA + "\" CASCADE");
      s.execute("DROP DATABASE IF EXISTS " + SCRATCH_GLOBAL_DB + " WITH (FORCE)");
    }
  }

  @Test
  void tenantSchemaAtV1MatchesCommittedSnapshot() throws Exception {
    compare(
        "tenant-baseline.snapshot", dump(migrationDataSource, new String[] {SCRATCH_SCHEMA}, true));
  }

  @Test
  void globalSchemasAtV1MatchCommittedSnapshot() throws Exception {
    compare(
        "global-baseline.snapshot",
        dump(scratchGlobalDataSource, new String[] {"public", "portal"}, false));
  }

  @Test
  void scratchGlobalHistoryIsExactlyBaselineV1() throws Exception {
    try (Connection c = scratchGlobalDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT count(*), count(*) FILTER (WHERE success),"
                    + " count(*) FILTER (WHERE type <> 'SQL'), min(version), max(version)"
                    + " FROM public.flyway_schema_history")) {
      rs.next();
      assertThat(rs.getInt(1)).as("scratch global history rows").isEqualTo(1);
      assertThat(rs.getInt(2)).as("scratch global successful rows").isEqualTo(1);
      assertThat(rs.getInt(3)).as("scratch global BASELINE/SCHEMA rows").isZero();
      assertThat(rs.getString(4)).as("scratch global lowest version").isEqualTo("1");
      assertThat(rs.getString(5)).as("scratch global highest version").isEqualTo("1");
    }
  }

  @Test
  void scratchTenantHistoryIsExactlyBaselineV1() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT count(*), count(*) FILTER (WHERE success),"
                    + " count(*) FILTER (WHERE type <> 'SQL'), min(version), max(version) FROM \""
                    + SCRATCH_SCHEMA
                    + "\".flyway_schema_history")) {
      rs.next();
      assertThat(rs.getInt(1)).as("scratch history rows").isEqualTo(1);
      assertThat(rs.getInt(2)).as("scratch successful rows").isEqualTo(1);
      assertThat(rs.getInt(3)).as("scratch BASELINE/SCHEMA rows").isZero();
      assertThat(rs.getString(4)).as("scratch lowest version").isEqualTo("1");
      assertThat(rs.getString(5)).as("scratch highest version").isEqualTo("1");
    }
  }

  @Test
  void provisionedTenantHistoryHasOneSuccessfulRowPerMigrationFile() throws Exception {
    assertHistory("\"" + provisionedSchema + "\".flyway_schema_history", "tenant");
  }

  @Test
  void globalHistoryHasOneSuccessfulRowPerMigrationFile() throws Exception {
    assertHistory("public.flyway_schema_history", "global");
  }

  private void assertHistory(String historyTable, String series) throws Exception {
    int files =
        new PathMatchingResourcePatternResolver()
            .getResources("classpath*:db/migration/" + series + "/V*__*.sql")
            .length;
    assertThat(files).as("%s migration files on the classpath", series).isPositive();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT count(*), count(*) FILTER (WHERE success), "
                    + "count(*) FILTER (WHERE type <> 'SQL'), min(version) FROM "
                    + historyTable)) {
      rs.next();
      assertThat(rs.getInt(1)).as("%s history rows == migration files", series).isEqualTo(files);
      assertThat(rs.getInt(2)).as("%s successful rows", series).isEqualTo(files);
      assertThat(rs.getInt(3)).as("%s BASELINE/SCHEMA rows", series).isZero();
      assertThat(rs.getString(4)).as("%s lowest version", series).isEqualTo("1");
    }
  }

  private String dump(DataSource dataSource, String[] schemas, boolean tenant) throws Exception {
    List<String> out = new ArrayList<>();
    try (Connection c = dataSource.getConnection()) {
      try (Statement s = c.createStatement()) {
        s.execute("SET search_path TO pg_catalog"); // deterministic qualification (T8)
      }
      try {
        Array in = c.createArrayOf("text", schemas);
        String notFlyway = " AND table_name::text <> 'flyway_schema_history'";
        section(
            out,
            c,
            in,
            "tables",
            "SELECT table_schema, table_name, table_type FROM information_schema.tables"
                + " WHERE table_schema::text = ANY(?)"
                + notFlyway);
        section(
            out,
            c,
            in,
            "columns",
            "SELECT table_schema, table_name, column_name, data_type, udt_name,"
                + " character_maximum_length, numeric_precision, numeric_scale, datetime_precision,"
                + " is_nullable, column_default FROM information_schema.columns"
                + " WHERE table_schema::text = ANY(?)"
                + notFlyway);
        section(
            out,
            c,
            in,
            "table_constraints",
            "SELECT table_schema, table_name, constraint_name, constraint_type, is_deferrable,"
                + " initially_deferred FROM information_schema.table_constraints"
                + " WHERE table_schema::text = ANY(?)"
                + notFlyway
                + " AND constraint_name::text !~ '"
                + NOT_NULL_PSEUDO
                + "'");
        section(
            out,
            c,
            in,
            "check_constraints",
            "SELECT tc.table_schema, tc.table_name, cc.constraint_name, cc.check_clause"
                + " FROM information_schema.check_constraints cc"
                + " JOIN information_schema.table_constraints tc"
                + "   ON tc.constraint_schema = cc.constraint_schema"
                + "  AND tc.constraint_name = cc.constraint_name AND tc.constraint_type = 'CHECK'"
                + " WHERE cc.constraint_schema::text = ANY(?)"
                + " AND cc.constraint_name::text !~ '"
                + NOT_NULL_PSEUDO
                + "'");
        section(
            out,
            c,
            in,
            "key_column_usage",
            "SELECT table_schema, table_name, constraint_name, column_name, ordinal_position,"
                + " position_in_unique_constraint FROM information_schema.key_column_usage"
                + " WHERE table_schema::text = ANY(?)"
                + notFlyway);
        section(
            out,
            c,
            in,
            "constraint_defs", // FK ON DELETE/UPDATE actions etc. (T9)
            "SELECT n.nspname, cl.relname, co.conname, co.contype, pg_get_constraintdef(co.oid, true)"
                + " FROM pg_constraint co JOIN pg_namespace n ON n.oid = co.connamespace"
                + " JOIN pg_class cl ON cl.oid = co.conrelid"
                + " WHERE n.nspname::text = ANY(?) AND cl.relname <> 'flyway_schema_history'");
        section(
            out,
            c,
            in,
            "indexes",
            "SELECT schemaname, tablename, indexname, indexdef FROM pg_indexes"
                + " WHERE schemaname::text = ANY(?) AND tablename::text <> 'flyway_schema_history'");
        section(
            out,
            c,
            in,
            "triggers",
            "SELECT event_object_schema, event_object_table, trigger_name, event_manipulation,"
                + " action_timing, action_orientation, action_statement"
                + " FROM information_schema.triggers WHERE event_object_schema::text = ANY(?)");
        section(
            out,
            c,
            in,
            "functions", // excludes pg_trgm's extension-owned functions
            "SELECT n.nspname, p.proname, pg_get_function_identity_arguments(p.oid),"
                + " pg_get_function_result(p.oid), l.lanname, md5(p.prosrc)"
                + " FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace"
                + " JOIN pg_language l ON l.oid = p.prolang WHERE n.nspname::text = ANY(?)"
                + " AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.classid = 'pg_proc'::regclass"
                + " AND d.objid = p.oid AND d.deptype = 'e')");
        if (tenant) {
          String t = "\"" + SCRATCH_SCHEMA + "\".";
          section(
              out,
              c,
              null,
              "seed:org_roles",
              "SELECT slug, name, description, is_system FROM " + t + "org_roles WHERE is_system");
          section(
              out,
              c,
              null,
              "seed:org_role_capabilities",
              "SELECT r.slug, rc.capability FROM "
                  + t
                  + "org_role_capabilities rc JOIN "
                  + t
                  + "org_roles r ON r.id = rc.org_role_id WHERE r.is_system");
          section(
              out,
              c,
              null,
              "seed:tax_rates",
              "SELECT name, rate, is_default, is_exempt, active, sort_order FROM "
                  + t
                  + "tax_rates");
          section(
              out,
              c,
              null,
              "seed:accounting_tax_code_mapping",
              "SELECT provider_id, kazi_tax_mode, external_tax_code, display_label, is_default FROM "
                  + t
                  + "accounting_tax_code_mapping");
          section(
              out,
              c,
              null,
              "seed:request_counters",
              "SELECT next_number FROM " + t + "request_counters");
        } else {
          section(
              out,
              c,
              null,
              "extensions",
              "SELECT e.extname, n.nspname FROM pg_extension e"
                  + " JOIN pg_namespace n ON n.oid = e.extnamespace WHERE e.extname <> 'plpgsql'");
          section(
              out,
              c,
              null,
              "seed:shard_config",
              "SELECT shard_id, display_name FROM public.shard_config WHERE shard_id = 'primary'");
          section(
              out,
              c,
              null,
              "seed:shedlock", // locked_by/lock_until mutate at runtime
              "SELECT name FROM public.shedlock WHERE name = 'stale_job_recovery'");
        }
      } finally {
        try (Statement s = c.createStatement()) {
          s.execute("RESET search_path"); // pooled connection — leave it as found
        }
      }
    }
    return String.join("\n", out) + "\n";
  }

  private void section(List<String> out, Connection c, Array in, String name, String sql)
      throws Exception {
    List<String> rows = new ArrayList<>();
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      if (in != null) {
        ps.setArray(1, in);
      }
      try (ResultSet rs = ps.executeQuery()) {
        int cols = rs.getMetaData().getColumnCount();
        while (rs.next()) {
          StringBuilder sb = new StringBuilder();
          for (int i = 1; i <= cols; i++) {
            if (i > 1) {
              sb.append(" | ");
            }
            sb.append(rs.getString(i));
          }
          rows.add(normalise(sb.toString()));
        }
      }
    }
    rows.sort(null); // Java ordering — independent of DB collation (T8)
    out.add("## " + name + " (" + rows.size() + ")");
    out.addAll(rows);
  }

  private String normalise(String line) {
    String l = line.replace("\r", "").replace("\n", "\\n");
    return l.replace(SCRATCH_SCHEMA, TENANT_TOKEN);
  }

  private void compare(String fileName, String actual) throws Exception {
    if (RECORD) {
      Files.createDirectories(SNAPSHOT_DIR);
      Files.writeString(SNAPSHOT_DIR.resolve(fileName), actual, StandardCharsets.UTF_8);
      fail("Recorded %s (BASELINE_SNAPSHOT_RECORD=true) — re-run without it to verify.", fileName);
    }
    ClassPathResource resource = new ClassPathResource("schema/" + fileName);
    assertThat(resource.exists()).as("committed snapshot schema/%s", fileName).isTrue();
    String expected = resource.getContentAsString(StandardCharsets.UTF_8);
    if (!expected.equals(actual)) {
      Set<String> missing = new TreeSet<>(expected.lines().toList());
      missing.removeAll(actual.lines().toList());
      Set<String> unexpected = new TreeSet<>(actual.lines().toList());
      unexpected.removeAll(expected.lines().toList());
      fail(
          "%s differs from the committed pre-squash snapshot.%n--- missing (in snapshot, not in"
              + " schema) ---%n%s%n--- unexpected (in schema, not in snapshot) ---%n%s",
          fileName, String.join("\n", missing), String.join("\n", unexpected));
    }
  }
}
