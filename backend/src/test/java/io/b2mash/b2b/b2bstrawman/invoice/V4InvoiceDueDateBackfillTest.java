package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
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
import org.springframework.test.context.ActiveProfiles;

/**
 * Tenant {@code V4__phase88_demo_ready} §2 (Phase 88 U3, ADR-347) adds {@code
 * org_settings.default_payment_terms_days} (NOT NULL DEFAULT 30, CHECK 0..365) and backfills
 * APPROVED/SENT fee notes that never had a due date: {@code COALESCE(issue_date, created_at UTC
 * date) + terms}. A scratch schema is migrated to V3, seeded with an org_settings row (every real
 * tenant has one — provisioning creates it) and five invoices, then migrated to V4. A second
 * scratch schema has no org_settings row at all, pinning the COALESCE-outside-the-subquery fallback
 * to 30 terms.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V4InvoiceDueDateBackfillTest {

  // Never provisioned or mapped — only names the scratch schemas.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_v4_invoice_due_date_scratch");
  private static final String NO_SETTINGS_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_v4_invoice_due_date_no_settings_scratch");

  private static final String MEMBER = "00000000-0000-0000-0000-0000000002a1";
  private static final String CLIENT = "00000000-0000-0000-0000-0000000002b1";
  private static final String APPROVED_WITH_ISSUE =
      "00000000-0000-0000-0000-0000000002c1"; // issue 2026-03-10
  private static final String SENT_NO_ISSUE =
      "00000000-0000-0000-0000-0000000002c2"; // created 2026-01-10 23:30Z
  private static final String DRAFT_NO_DUE = "00000000-0000-0000-0000-0000000002c3";
  private static final String SENT_TYPED_DUE =
      "00000000-0000-0000-0000-0000000002c4"; // due 2026-12-31
  private static final String PAID_NO_DUE = "00000000-0000-0000-0000-0000000002c5";
  private static final String NO_SETTINGS_SENT =
      "00000000-0000-0000-0000-0000000002d1"; // issue 2026-05-01, schema without org_settings
  private static final String RERUN_SENT =
      "00000000-0000-0000-0000-0000000002e1"; // inserted after V4, issue 2026-06-01

  private final DataSource migrationDataSource;
  private final Map<String, LocalDate> dueById = new HashMap<>();
  private final Map<String, LocalDate> issueById = new HashMap<>();
  private Map<String, String> xminBefore;
  private Map<String, String> xminAfter;
  private Integer termsAfterMigration;
  private LocalDate noSettingsDue;

  @Autowired
  V4InvoiceDueDateBackfillTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemasFromV3() throws Exception {
    // --- schema with an org_settings row (the shape of every real tenant) ---
    exec(SCRATCH_SCHEMA, "DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec(SCRATCH_SCHEMA, "CREATE SCHEMA \"%s\"");
    flyway(SCRATCH_SCHEMA).target(MigrationVersion.fromVersion("3")).load().migrate();
    seedMemberAndClient(SCRATCH_SCHEMA);
    exec(SCRATCH_SCHEMA, "INSERT INTO \"%s\".org_settings DEFAULT VALUES");
    exec(
        SCRATCH_SCHEMA,
        """
        INSERT INTO "%s".invoices
          (id, customer_id, status, currency, customer_name, org_name, created_by,
           invoice_number, issue_date, due_date, created_at)
        VALUES
          ('00000000-0000-0000-0000-0000000002c1', '00000000-0000-0000-0000-0000000002b1',
           'APPROVED', 'ZAR', 'V4 Client', 'V4 Org', '00000000-0000-0000-0000-0000000002a1',
           'V4-0001', DATE '2026-03-10', NULL, TIMESTAMPTZ '2026-03-10 08:00:00+00'),
          ('00000000-0000-0000-0000-0000000002c2', '00000000-0000-0000-0000-0000000002b1',
           'SENT', 'ZAR', 'V4 Client', 'V4 Org', '00000000-0000-0000-0000-0000000002a1',
           'V4-0002', NULL, NULL, TIMESTAMPTZ '2026-01-10 23:30:00+00'),
          ('00000000-0000-0000-0000-0000000002c3', '00000000-0000-0000-0000-0000000002b1',
           'DRAFT', 'ZAR', 'V4 Client', 'V4 Org', '00000000-0000-0000-0000-0000000002a1',
           NULL, NULL, NULL, TIMESTAMPTZ '2026-03-01 08:00:00+00'),
          ('00000000-0000-0000-0000-0000000002c4', '00000000-0000-0000-0000-0000000002b1',
           'SENT', 'ZAR', 'V4 Client', 'V4 Org', '00000000-0000-0000-0000-0000000002a1',
           'V4-0003', DATE '2026-03-01', DATE '2026-12-31', TIMESTAMPTZ '2026-03-01 08:00:00+00'),
          ('00000000-0000-0000-0000-0000000002c5', '00000000-0000-0000-0000-0000000002b1',
           'PAID', 'ZAR', 'V4 Client', 'V4 Org', '00000000-0000-0000-0000-0000000002a1',
           'V4-0004', DATE '2026-03-01', NULL, TIMESTAMPTZ '2026-03-01 08:00:00+00')
        """);

    xminBefore = rowVersions();
    flyway(SCRATCH_SCHEMA).target(MigrationVersion.fromVersion("4")).load().migrate();
    xminAfter = rowVersions();

    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      try (ResultSet rs =
          s.executeQuery(
              "SELECT id::text, issue_date, due_date FROM \"%s\".invoices"
                  .formatted(SCRATCH_SCHEMA))) {
        while (rs.next()) {
          issueById.put(rs.getString(1), rs.getObject(2, LocalDate.class));
          dueById.put(rs.getString(1), rs.getObject(3, LocalDate.class));
        }
      }
      try (ResultSet rs =
          s.executeQuery(
              "SELECT default_payment_terms_days FROM \"%s\".org_settings"
                  .formatted(SCRATCH_SCHEMA))) {
        rs.next();
        termsAfterMigration = rs.getInt(1);
      }
    }

    // --- schema WITHOUT an org_settings row: terms fall back to 30 (COALESCE outside) ---
    exec(NO_SETTINGS_SCHEMA, "DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec(NO_SETTINGS_SCHEMA, "CREATE SCHEMA \"%s\"");
    flyway(NO_SETTINGS_SCHEMA).target(MigrationVersion.fromVersion("3")).load().migrate();
    seedMemberAndClient(NO_SETTINGS_SCHEMA);
    exec(
        NO_SETTINGS_SCHEMA,
        """
        INSERT INTO "%s".invoices
          (id, customer_id, status, currency, customer_name, org_name, created_by,
           invoice_number, issue_date, due_date)
        VALUES
          ('00000000-0000-0000-0000-0000000002d1', '00000000-0000-0000-0000-0000000002b1',
           'SENT', 'ZAR', 'V4 Client', 'V4 Org', '00000000-0000-0000-0000-0000000002a1',
           'V4-0101', DATE '2026-05-01', NULL)
        """);
    flyway(NO_SETTINGS_SCHEMA).target(MigrationVersion.fromVersion("4")).load().migrate();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT due_date FROM \"%s\".invoices WHERE id = '%s'"
                    .formatted(NO_SETTINGS_SCHEMA, NO_SETTINGS_SENT))) {
      rs.next();
      noSettingsDue = rs.getObject(1, LocalDate.class);
    }
  }

  @AfterAll
  void dropScratchSchemas() throws Exception {
    exec(SCRATCH_SCHEMA, "DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec(NO_SETTINGS_SCHEMA, "DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void approvedRowWithIssueDateGetsIssuePlus30() {
    assertThat(dueById.get(APPROVED_WITH_ISSUE)).isEqualTo(LocalDate.of(2026, 4, 9));
  }

  @Test
  void sentRowWithoutIssueDateUsesCreatedAtUtcDate() {
    // created_at 2026-01-10 23:30Z → UTC date 2026-01-10 (SAST would give 01-11 → 02-10).
    assertThat(dueById.get(SENT_NO_ISSUE)).isEqualTo(LocalDate.of(2026, 2, 9));
  }

  @Test
  void backfillDoesNotSetIssueDate() {
    assertThat(issueById).containsKey(SENT_NO_ISSUE);
    assertThat(issueById.get(SENT_NO_ISSUE)).isNull();
  }

  @Test
  void draftRowIsUntouched() {
    assertThat(dueById).containsKey(DRAFT_NO_DUE);
    assertThat(dueById.get(DRAFT_NO_DUE)).isNull();
    assertThat(xminAfter.get(DRAFT_NO_DUE)).isNotNull().isEqualTo(xminBefore.get(DRAFT_NO_DUE));
  }

  @Test
  void typedDueDateIsUntouched() {
    assertThat(dueById.get(SENT_TYPED_DUE)).isEqualTo(LocalDate.of(2026, 12, 31));
    assertThat(xminAfter.get(SENT_TYPED_DUE)).isNotNull().isEqualTo(xminBefore.get(SENT_TYPED_DUE));
  }

  @Test
  void paidRowIsNotBackfilled() {
    assertThat(dueById).containsKey(PAID_NO_DUE);
    assertThat(dueById.get(PAID_NO_DUE)).isNull();
    assertThat(xminAfter.get(PAID_NO_DUE)).isNotNull().isEqualTo(xminBefore.get(PAID_NO_DUE));
  }

  @Test
  void schemaWithoutOrgSettingsRowStillBackfillsWith30() {
    assertThat(noSettingsDue).isEqualTo(LocalDate.of(2026, 5, 31));
  }

  @Test
  void existingOrgSettingsRowGetsTermsOf30() {
    assertThat(termsAfterMigration).isEqualTo(30);
  }

  @Test
  void paymentTermsColumnIsNotNullIntegerDefault30() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = 'org_settings'
                  AND column_name = 'default_payment_terms_days'
                """
                    .formatted(SCRATCH_SCHEMA))) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("integer");
      assertThat(rs.getString(2)).isEqualTo("NO");
      assertThat(rs.getString(3)).isEqualTo("30");
    }
  }

  @Test
  void checkConstraintExistsAndRejects366() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT count(*), max(pg_get_constraintdef(c.oid))
                FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE c.conname = 'chk_org_settings_payment_terms' AND c.contype = 'c'
                  AND n.nspname = '%s'
                """
                    .formatted(SCRATCH_SCHEMA))) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(1);
      assertThat(rs.getString(2)).contains("0").contains("365");
    }
    assertThatThrownBy(
            () ->
                exec(
                    SCRATCH_SCHEMA,
                    "UPDATE \"%s\".org_settings SET default_payment_terms_days = 366"))
        .isInstanceOf(SQLException.class)
        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
  }

  /**
   * Re-running the whole V4 body leaves every migrated invoice row untouched (xmin unchanged), and
   * the backfill reads the firm's terms from org_settings: a NULL-due SENT row inserted after V4,
   * with terms set to 14, gets issue + 14.
   */
  @Test
  void reRunningTheV4BodyIsANoOpForMigratedRowsAndUsesFirmTerms() throws Exception {
    exec(SCRATCH_SCHEMA, "UPDATE \"%s\".org_settings SET default_payment_terms_days = 14");
    try {
      exec(
          SCRATCH_SCHEMA,
          """
          INSERT INTO "%s".invoices
            (id, customer_id, status, currency, customer_name, org_name, created_by,
             invoice_number, issue_date, due_date)
          VALUES
            ('00000000-0000-0000-0000-0000000002e1', '00000000-0000-0000-0000-0000000002b1',
             'SENT', 'ZAR', 'V4 Client', 'V4 Org', '00000000-0000-0000-0000-0000000002a1',
             'V4-0201', DATE '2026-06-01', NULL)
          """);
      String v4 =
          new String(
              getClass()
                  .getResourceAsStream("/db/migration/tenant/V4__phase88_demo_ready.sql")
                  .readAllBytes(),
              StandardCharsets.UTF_8);
      try (Connection c = migrationDataSource.getConnection();
          Statement s = c.createStatement()) {
        try {
          s.execute("SET search_path TO \"%s\"".formatted(SCRATCH_SCHEMA));
          s.execute(v4);
        } finally {
          s.execute("RESET search_path"); // pooled connection — leave it as found
        }
      }

      var versions = rowVersions();
      for (var id : xminAfter.keySet()) {
        assertThat(versions.get(id)).as("xmin of %s", id).isEqualTo(xminAfter.get(id));
      }
      try (Connection c = migrationDataSource.getConnection();
          Statement s = c.createStatement();
          ResultSet rs =
              s.executeQuery(
                  "SELECT due_date FROM \"%s\".invoices WHERE id = '%s'"
                      .formatted(SCRATCH_SCHEMA, RERUN_SENT))) {
        rs.next();
        assertThat(rs.getObject(1, LocalDate.class)).isEqualTo(LocalDate.of(2026, 6, 15));
      }
    } finally {
      exec(SCRATCH_SCHEMA, "UPDATE \"%s\".org_settings SET default_payment_terms_days = 30");
    }
  }

  private void seedMemberAndClient(String schema) throws Exception {
    exec(
        schema,
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '00000000-0000-0000-0000-0000000002a1'::uuid, 'user_v4_due_a1',
               'v4_due_a1@test.com', r.id
        FROM "%s".org_roles r
        WHERE r.slug = 'member'
        """);
    exec(
        schema,
        """
        INSERT INTO "%s".customers (id, name, email, created_by)
        VALUES ('00000000-0000-0000-0000-0000000002b1', 'V4 Client', 'v4_due_client@test.com',
                '00000000-0000-0000-0000-0000000002a1')
        """);
  }

  private Map<String, String> rowVersions() throws Exception {
    var versions = new HashMap<String, String>();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT id::text, xmin::text FROM \"%s\".invoices".formatted(SCRATCH_SCHEMA))) {
      while (rs.next()) {
        versions.put(rs.getString(1), rs.getString(2));
      }
    }
    return versions;
  }

  private void exec(String schema, String sqlWithSchemaPlaceholders) throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute(sqlWithSchemaPlaceholders.replace("%s", schema));
    }
  }

  private FluentConfiguration flyway(String schema) {
    // Same locations/config as TenantProvisioningService.runTenantMigrations.
    return Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(schema)
        .baselineOnMigrate(false);
  }
}
