package io.b2mash.b2b.b2bstrawman.takeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 93 / 657C (task 657.19, architecture §17.2.7, §17.7.6): tenant {@code
 * V36__phase93_take_on_reports} (guide V34; V35 taken by 656A — next free, D-196). A scratch schema
 * is walked to V35 with an owner member, a trust account and a trust take-on batch, then migrated:
 * the three tables have their columns, one report per batch, one file per format, the format and
 * attempts CHECKs, every foreign key refuses a dangling id and never cascades, and re-running the
 * body changes nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnReportsSchemaTest {

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V36__phase93_take_on_reports.sql";
  private static final String PREVIOUS_VERSION = "35";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String S =
      SchemaNameGenerator.generateSchemaName("org_takeon_reports_scratch");

  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID BATCH = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

  private static final List<String> CONSTRAINTS =
      List.of(
          "take_on_reports_pkey",
          "uq_take_on_reports_batch",
          "take_on_reports_batch_fkey",
          "take_on_reports_account_fkey",
          "take_on_report_files_pkey",
          "uq_take_on_report_files_format",
          "chk_take_on_report_file_format",
          "take_on_report_files_report_fkey",
          "take_on_report_renders_pkey",
          "chk_take_on_report_render_attempts",
          "take_on_report_renders_report_fkey");

  @Autowired private JdbcTemplate jdbc;

  @Autowired
  @Qualifier("migrationDataSource")
  private DataSource migrationDataSource;

  @BeforeAll
  void walkToV35() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"" + S + "\" CASCADE");
    exec("CREATE SCHEMA \"" + S + "\"");
    tenantFlyway().target(MigrationVersion.fromVersion(PREVIOUS_VERSION)).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%o', 'user_tors', 'tors@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date)
        VALUES ('%a', 'Reports schema trust', 'FNB', '250655', '62099990036', 'GENERAL', false,
                false, 'ACTIVE', DATE '2025-01-01');
        INSERT INTO "%s".take_on_batches
          (id, dataset, status, trust_account_id, cutover_date, created_by, created_at, updated_at)
        VALUES ('%b', 'TRUST_POSITION', 'UPLOADED', '%a', DATE '2026-10-31', '%o', now(), now());
        """
            .replace("%s", S)
            .replace("%o", OWNER.toString())
            .replace("%a", ACCOUNT.toString())
            .replace("%b", BATCH.toString()));
    tenantFlyway().load().migrate();
  }

  @AfterEach
  void clearRows() {
    for (String table :
        List.of("take_on_report_renders", "take_on_report_files", "take_on_reports")) {
      jdbc.update("DELETE FROM \"%s\".%s".formatted(S, table));
    }
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"" + S + "\" CASCADE");
  }

  // 1
  @Test
  void migrationAppliesOnAWalkedSchema_withTheThreeTablesAndTheirColumns() {
    // Applied (not "the latest": a later migration must not break this pin).
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".flyway_schema_history WHERE success AND version = ?"
                    .formatted(S),
                Integer.class,
                "36"))
        .isEqualTo(1);
    assertThat(columns("take_on_reports"))
        .containsExactly(
            "id uuid NO",
            "batch_id uuid NO",
            "trust_account_id uuid NO",
            "cutover_date date NO",
            "snapshot jsonb NO",
            "snapshot_sha256 character varying(64) NO",
            "generated_by uuid NO",
            "generated_at timestamp with time zone NO");
    assertThat(columns("take_on_report_files"))
        .containsExactly(
            "id uuid NO",
            "report_id uuid NO",
            "format character varying(10) NO",
            "s3_key character varying(500) NO",
            "sha256 character varying(64) NO",
            "file_size bigint NO",
            "rendered_at timestamp with time zone NO");
    assertThat(columns("take_on_report_renders"))
        .containsExactly(
            "report_id uuid NO",
            "attempts integer NO",
            "last_error_code character varying(50) YES",
            "last_attempt_at timestamp with time zone YES",
            "failed_at timestamp with time zone YES");
    assertThat(
            jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND indexname = ?",
                String.class,
                S,
                "idx_take_on_reports_account"))
        .contains("(trust_account_id, cutover_date DESC)");
  }

  // 2
  @Test
  void oneReportPerBatch() {
    insertReport(UUID.randomUUID(), BATCH, ACCOUNT);
    assertRejected(
        () -> insertReport(UUID.randomUUID(), BATCH, ACCOUNT), "uq_take_on_reports_batch");
  }

  // 3
  @Test
  void oneFilePerFormat_andOnlyPdfOrCsv() {
    UUID report = insertReport(UUID.randomUUID(), BATCH, ACCOUNT);
    assertThat(insertFile(report, "PDF")).isEqualTo(1);
    assertThat(insertFile(report, "CSV")).isEqualTo(1);
    assertRejected(() -> insertFile(report, "PDF"), "uq_take_on_report_files_format");
    assertRejected(() -> insertFile(report, "DOCX"), "chk_take_on_report_file_format");
  }

  // 4
  @Test
  void renderAttemptsAreNeverNegative_andDefaultToZero() {
    UUID report = insertReport(UUID.randomUUID(), BATCH, ACCOUNT);
    jdbc.update(
        "INSERT INTO \"%s\".take_on_report_renders (report_id) VALUES (?)".formatted(S), report);
    assertThat(
            jdbc.queryForObject(
                "SELECT attempts FROM \"%s\".take_on_report_renders WHERE report_id = ?"
                    .formatted(S),
                Integer.class,
                report))
        .isZero();
    jdbc.update("DELETE FROM \"%s\".take_on_report_renders".formatted(S));
    assertRejected(
        () ->
            jdbc.update(
                "INSERT INTO \"%s\".take_on_report_renders (report_id, attempts) VALUES (?, -1)"
                    .formatted(S),
                report),
        "chk_take_on_report_render_attempts");
  }

  // 5
  @Test
  void everyForeignKeyRefusesADanglingId_andNeverCascades() {
    assertRejected(
        () -> insertReport(UUID.randomUUID(), UUID.randomUUID(), ACCOUNT),
        "take_on_reports_batch_fkey");
    assertRejected(
        () -> insertReport(UUID.randomUUID(), BATCH, UUID.randomUUID()),
        "take_on_reports_account_fkey");
    assertRejected(() -> insertFile(UUID.randomUUID(), "PDF"), "take_on_report_files_report_fkey");
    assertRejected(
        () ->
            jdbc.update(
                "INSERT INTO \"%s\".take_on_report_renders (report_id) VALUES (?)".formatted(S),
                UUID.randomUUID()),
        "take_on_report_renders_report_fkey");
    for (String fk :
        List.of(
            "take_on_reports_batch_fkey",
            "take_on_reports_account_fkey",
            "take_on_report_files_report_fkey",
            "take_on_report_renders_report_fkey")) {
      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT c.confdeltype::text FROM pg_constraint c
                  JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = ? AND n.nspname = ?
                  """,
                  String.class,
                  fk,
                  S))
          .as("%s never cascades", fk)
          .isEqualTo("a");
    }
  }

  // 6
  @Test
  void rerunningTheMigrationBody_changesNothing() throws Exception {
    String checkBefore = constraintDef("chk_take_on_report_file_format");
    rerunMigrationBody();
    assertThat(constraintDef("chk_take_on_report_file_format")).isEqualTo(checkBefore);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = ? AND c.conname = ANY (?)
                """,
                Integer.class,
                S,
                CONSTRAINTS.toArray(String[]::new)))
        .isEqualTo(CONSTRAINTS.size());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = ? AND indexname = ?",
                Integer.class,
                S,
                "idx_take_on_reports_account"))
        .isEqualTo(1);
  }

  // --- helpers ---

  private List<String> columns(String table) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT column_name, data_type, udt_name, is_nullable, character_maximum_length
            FROM information_schema.columns
            WHERE table_schema = ? AND table_name = ?
            ORDER BY ordinal_position
            """,
            S,
            table);
    return rows.stream()
        .map(
            c -> {
              String type =
                  "USER-DEFINED".equals(c.get("data_type"))
                      ? (String) c.get("udt_name")
                      : (String) c.get("data_type");
              if (c.get("character_maximum_length") != null) {
                type += "(" + c.get("character_maximum_length") + ")";
              }
              return c.get("column_name") + " " + type + " " + c.get("is_nullable");
            })
        .toList();
  }

  private UUID insertReport(UUID id, UUID batch, UUID account) {
    jdbc.update(
        ("INSERT INTO \"%s\".take_on_reports (id, batch_id, trust_account_id, cutover_date,"
                + " snapshot, snapshot_sha256, generated_by, generated_at)"
                + " VALUES (?, ?, ?, DATE '2026-10-31', '{}'::jsonb, ?, ?, now())")
            .formatted(S),
        id,
        batch,
        account,
        "0".repeat(64),
        OWNER);
    return id;
  }

  private int insertFile(UUID report, String format) {
    return jdbc.update(
        ("INSERT INTO \"%s\".take_on_report_files (id, report_id, format, s3_key, sha256,"
                + " file_size, rendered_at) VALUES (?, ?, ?, 'take-on/x', ?, 10, now())")
            .formatted(S),
        UUID.randomUUID(),
        report,
        format,
        "0".repeat(64));
  }

  private String constraintDef(String name) {
    return jdbc.queryForObject(
        """
        SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c
        JOIN pg_namespace n ON n.oid = c.connamespace
        WHERE c.conname = ? AND n.nspname = ?
        """,
        String.class,
        name,
        S);
  }

  private static void assertRejected(Runnable write, String constraint) {
    assertThatThrownBy(write::run)
        .as("rejected by %s", constraint)
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(constraint);
  }

  private void rerunMigrationBody() throws Exception {
    var body =
        new String(
            new ClassPathResource(MIGRATION_FILE).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    try (Connection c = migrationDataSource.getConnection()) {
      c.setAutoCommit(false);
      try (Statement s = c.createStatement()) {
        // SET LOCAL: the search_path reverts at commit, so the pooled connection is unaffected.
        s.execute("SET LOCAL search_path TO \"" + S + "\"");
        s.execute(body);
        c.commit();
      } catch (SQLException e) {
        c.rollback();
        throw e;
      } finally {
        c.setAutoCommit(true);
      }
    }
  }

  private void exec(String sql) throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute(sql);
    }
  }

  private FluentConfiguration tenantFlyway() {
    // Same locations/config as TenantProvisioningService.runTenantMigrations.
    return Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(S)
        .baselineOnMigrate(false);
  }
}
