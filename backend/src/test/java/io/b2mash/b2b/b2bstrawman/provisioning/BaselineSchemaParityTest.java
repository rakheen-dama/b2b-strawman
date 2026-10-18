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
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 86 migration-squash proof (ADR-331, architecture §11.7(e)). Provisions a tenant through the
 * real {@link TenantProvisioningService} path, dumps the tenant schema and the global {@code
 * public}/{@code portal} schemas (tables, columns, constraints, indexes, triggers, functions,
 * extensions, migration-seeded rows) in a normalised, ordinal-free form and compares them
 * byte-for-byte with snapshots committed BEFORE the series was squashed (Epic 605A). The only
 * permitted difference between the old series and {@code V1__baseline.sql} is the number of files.
 * Also asserts one successful {@code flyway_schema_history} row per migration file on the classpath
 * (pre-squash: the full series; post-squash: exactly one row, version 1).
 *
 * <p>The snapshots pin the shape a tenant gets on an EMPTY database. The pre-squash series was not
 * deterministic across tenants: V7/V8/V10/V16/V27/V29/V127 guarded nine objects with unscoped
 * catalog lookups ({@code pg_constraint WHERE conname = …}, {@code information_schema.columns WHERE
 * table_name = …}), so only the first tenant in a database received them. Pre-squash this test is
 * therefore green only when its tenant is the first one provisioned in the JVM; the squashed
 * baseline has no such guards and gives every tenant the full shape.
 *
 * <p>Re-record ONLY when a new feature migration deliberately changes the schema ({@code
 * BASELINE_SNAPSHOT_RECORD=true}); never to make the squash pass.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaselineSchemaParityTest {

  private static final String ORG_ID = "org_baseline_schema_parity";
  private static final String TENANT_TOKEN = "<tenant>";
  // PG16 information_schema synthesises NOT NULL "constraints" named
  // <nspoid>_<reloid>_<attnum>_not_null.
  private static final String NOT_NULL_PSEUDO = "^[0-9]+_[0-9]+_[0-9]+_not_null$";
  private static final Path SNAPSHOT_DIR = Path.of("src/test/resources/schema");
  private static final boolean RECORD =
      "true".equalsIgnoreCase(System.getenv("BASELINE_SNAPSHOT_RECORD"));

  private final TenantProvisioningService provisioningService;
  private final DataSource migrationDataSource;
  private String tenantSchema;

  @Autowired
  BaselineSchemaParityTest(
      TenantProvisioningService provisioningService,
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.provisioningService = provisioningService;
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void provision() {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Baseline Schema Parity Org", null)
            .schemaName();
  }

  @Test
  void tenantSchemaMatchesCommittedSnapshot() throws Exception {
    compare("tenant-baseline.snapshot", dump(new String[] {tenantSchema}, true));
  }

  @Test
  void globalSchemasMatchCommittedSnapshot() throws Exception {
    compare("global-baseline.snapshot", dump(new String[] {"public", "portal"}, false));
  }

  @Test
  void tenantHistoryHasOneSuccessfulRowPerMigrationFile() throws Exception {
    assertHistory("\"" + tenantSchema + "\".flyway_schema_history", "tenant");
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

  private String dump(String[] schemas, boolean tenant) throws Exception {
    List<String> out = new ArrayList<>();
    try (Connection c = migrationDataSource.getConnection()) {
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
          String t = "\"" + tenantSchema + "\".";
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
    return tenantSchema == null ? l : l.replace(tenantSchema, TENANT_TOKEN);
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
