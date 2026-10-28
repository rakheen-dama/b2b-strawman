package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
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
 * Tenant {@code V8__phase88_retention_comment_attachments} §5 (Phase 88 U17, ADR-354): seeds NULL
 * {@code org_settings.legal_matter_retention_years} to 5, adds the nullable {@code
 * projects.retention_ends_on date}, backfills CLOSED matters from {@code COALESCE(closed_at,
 * retention_clock_started_at)} (UTC date) + the firm's years, and adds the partial index {@code
 * idx_projects_retention_ends_on}. A scratch schema is migrated to V7, seeded, then migrated to V8.
 * A second scratch schema has no {@code org_settings} row at all, pinning the
 * COALESCE-outside-the-subquery fallback to 5 years.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V8RetentionAndCommentAttachmentsTest {

  // Never provisioned or mapped — only names the scratch schemas.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_v8_retention_scratch");
  private static final String NO_SETTINGS_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_v8_retention_no_settings_scratch");

  private static final String V8_RESOURCE =
      "/db/migration/tenant/V8__phase88_retention_comment_attachments.sql";

  private static final String SETTINGS_ROW = "00000000-0000-0000-0000-0000000008a1";
  private static final String MEMBER = "00000000-0000-0000-0000-0000000008b1";

  /** closed_at 2026-10-22 09:00Z, clock the same instant. */
  private static final String CLOSED_PLAIN = "00000000-0000-0000-0000-0000000008c1";

  /** Clock stamped by the old complete() on 2026-09-01; closed 2026-10-22 10:00Z. */
  private static final String CLOSED_AFTER_COMPLETE = "00000000-0000-0000-0000-0000000008c2";

  /** closed_at 2026-10-22 23:30Z — already 2026-10-23 in Johannesburg. */
  private static final String CLOSED_LATE_UTC = "00000000-0000-0000-0000-0000000008c3";

  /** closed_at on the 2024 leap day. */
  private static final String CLOSED_LEAP = "00000000-0000-0000-0000-0000000008c4";

  /**
   * CLOSED without closed_at; only the clock is set (midday so session TZ cannot move the date).
   */
  private static final String CLOSED_CLOCK_ONLY = "00000000-0000-0000-0000-0000000008c5";

  /** COMPLETED with a clock stamped by the old complete() — must stay untouched. */
  private static final String COMPLETED_STAMPED = "00000000-0000-0000-0000-0000000008c6";

  private static final String ACTIVE_PLAIN = "00000000-0000-0000-0000-0000000008c7";

  /** Inserted after migration by the re-run test, with the firm's years changed to 7. */
  private static final String RERUN_CLOSED = "00000000-0000-0000-0000-0000000008c8";

  /** Clock-only CLOSED row inserted by the session-time-zone test, at 00:30 UTC. */
  private static final String TZ_CLOCK_ONLY = "00000000-0000-0000-0000-0000000008c9";

  private static final String NO_SETTINGS_CLOSED = "00000000-0000-0000-0000-0000000008d1";

  private final DataSource migrationDataSource;
  private Map<String, String> xminBefore;
  private Map<String, String> xminAfter;
  private Map<String, LocalDate> endsOnById;
  private Integer seededYears;
  private LocalDate noSettingsEndsOn;

  @Autowired
  V8RetentionAndCommentAttachmentsTest(
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemasFromV7() throws Exception {
    exec(SCRATCH_SCHEMA, "DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec(SCRATCH_SCHEMA, "CREATE SCHEMA \"%s\"");
    flyway(SCRATCH_SCHEMA).target(MigrationVersion.fromVersion("7")).load().migrate();
    // Explicit NULL: an INSERT without the column would take the column DEFAULT 5 and prove
    // nothing — every real tenant row was written with an explicit NULL by the entity.
    exec(
        SCRATCH_SCHEMA,
        "INSERT INTO \"%s\".org_settings (id, legal_matter_retention_years) VALUES ('"
            + SETTINGS_ROW
            + "', NULL)");
    seedMember(SCRATCH_SCHEMA);
    exec(
        SCRATCH_SCHEMA,
        """
        INSERT INTO "%s".projects
          (id, name, created_by, status, closed_at, retention_clock_started_at, completed_at)
        VALUES
          ('#CLOSED_PLAIN#', 'V8 closed', '#MEMBER#', 'CLOSED',
           TIMESTAMPTZ '2026-10-22 09:00:00+00', TIMESTAMP '2026-10-22 09:00:00', NULL),
          ('#CLOSED_AFTER_COMPLETE#', 'V8 completed then closed', '#MEMBER#', 'CLOSED',
           TIMESTAMPTZ '2026-10-22 10:00:00+00', TIMESTAMP '2026-09-01 10:00:00',
           TIMESTAMPTZ '2026-09-01 10:00:00+00'),
          ('#CLOSED_LATE_UTC#', 'V8 late evening', '#MEMBER#', 'CLOSED',
           TIMESTAMPTZ '2026-10-22 23:30:00+00', TIMESTAMP '2026-10-22 23:30:00', NULL),
          ('#CLOSED_LEAP#', 'V8 leap day', '#MEMBER#', 'CLOSED',
           TIMESTAMPTZ '2024-02-29 08:00:00+00', TIMESTAMP '2024-02-29 08:00:00', NULL),
          ('#CLOSED_CLOCK_ONLY#', 'V8 clock only', '#MEMBER#', 'CLOSED',
           NULL, TIMESTAMP '2024-06-15 12:00:00', NULL),
          ('#COMPLETED_STAMPED#', 'V8 completed', '#MEMBER#', 'COMPLETED',
           NULL, TIMESTAMP '2026-09-01 10:00:00', TIMESTAMPTZ '2026-09-01 10:00:00+00'),
          ('#ACTIVE_PLAIN#', 'V8 active', '#MEMBER#', 'ACTIVE', NULL, NULL, NULL)
        """
            .replace("#CLOSED_PLAIN#", CLOSED_PLAIN)
            .replace("#CLOSED_AFTER_COMPLETE#", CLOSED_AFTER_COMPLETE)
            .replace("#CLOSED_LATE_UTC#", CLOSED_LATE_UTC)
            .replace("#CLOSED_LEAP#", CLOSED_LEAP)
            .replace("#CLOSED_CLOCK_ONLY#", CLOSED_CLOCK_ONLY)
            .replace("#COMPLETED_STAMPED#", COMPLETED_STAMPED)
            .replace("#ACTIVE_PLAIN#", ACTIVE_PLAIN)
            .replace("#MEMBER#", MEMBER));

    xminBefore = rowVersions(SCRATCH_SCHEMA);
    flyway(SCRATCH_SCHEMA).target(MigrationVersion.fromVersion("8")).load().migrate();
    xminAfter = rowVersions(SCRATCH_SCHEMA);
    endsOnById = retentionEndsOn(SCRATCH_SCHEMA);
    seededYears = settingsYears(SCRATCH_SCHEMA);

    // Second scratch schema: no org_settings row at all.
    exec(NO_SETTINGS_SCHEMA, "DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec(NO_SETTINGS_SCHEMA, "CREATE SCHEMA \"%s\"");
    flyway(NO_SETTINGS_SCHEMA).target(MigrationVersion.fromVersion("7")).load().migrate();
    seedMember(NO_SETTINGS_SCHEMA);
    exec(
        NO_SETTINGS_SCHEMA,
        "INSERT INTO \"%s\".projects (id, name, created_by, status, closed_at)"
            + " VALUES ('"
            + NO_SETTINGS_CLOSED
            + "', 'V8 no settings', '"
            + MEMBER
            + "', 'CLOSED', TIMESTAMPTZ '2026-10-22 09:00:00+00')");
    flyway(NO_SETTINGS_SCHEMA).target(MigrationVersion.fromVersion("8")).load().migrate();
    noSettingsEndsOn = retentionEndsOn(NO_SETTINGS_SCHEMA).get(NO_SETTINGS_CLOSED);
  }

  @AfterAll
  void dropScratchSchemas() throws Exception {
    exec(SCRATCH_SCHEMA, "DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec(NO_SETTINGS_SCHEMA, "DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  // ---------- §5 U17: per-matter retention end ----------

  @Test
  void nullYearsSeededToFive() {
    assertThat(seededYears).isEqualTo(5);
  }

  @Test
  void retentionEndsOnColumnIsNullableDate() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT data_type, is_nullable FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = 'projects'
                  AND column_name = 'retention_ends_on'
                """
                    .formatted(SCRATCH_SCHEMA))) {
      assertThat(rs.next()).as("projects.retention_ends_on exists").isTrue();
      assertThat(rs.getString(1)).isEqualTo("date");
      assertThat(rs.getString(2)).isEqualTo("YES");
    }
  }

  @Test
  void closedMatterBackfilledFromClosedAtPlusFiveYears() {
    assertThat(endsOnById.get(CLOSED_PLAIN)).isEqualTo(LocalDate.of(2031, 10, 22));
  }

  @Test
  void closedAfterCompletion_anchorsOnClosedAt() {
    // The old complete() stamped the clock on 2026-09-01; the close is the anchor (ADR-249).
    assertThat(endsOnById.get(CLOSED_AFTER_COMPLETE)).isEqualTo(LocalDate.of(2031, 10, 22));
  }

  @Test
  void lateEveningCloseUsesUtcDate() {
    assertThat(endsOnById.get(CLOSED_LATE_UTC)).isEqualTo(LocalDate.of(2031, 10, 22));
  }

  @Test
  void leapDayCloseClampsToFebruary28() {
    // Postgres date + interval '5 years' clamps like Java's LocalDate.plusYears.
    assertThat(endsOnById.get(CLOSED_LEAP))
        .isEqualTo(LocalDate.of(2029, 2, 28))
        .isEqualTo(LocalDate.of(2024, 2, 29).plusYears(5));
  }

  @Test
  void closedWithoutClosedAtFallsBackToClock() {
    assertThat(endsOnById.get(CLOSED_CLOCK_ONLY)).isEqualTo(LocalDate.of(2029, 6, 15));
  }

  @Test
  void completedAndActiveMattersUntouched() {
    assertThat(endsOnById.get(COMPLETED_STAMPED)).isNull();
    assertThat(endsOnById.get(ACTIVE_PLAIN)).isNull();
    assertThat(xminAfter.get(COMPLETED_STAMPED))
        .isNotNull()
        .isEqualTo(xminBefore.get(COMPLETED_STAMPED));
    assertThat(xminAfter.get(ACTIVE_PLAIN)).isNotNull().isEqualTo(xminBefore.get(ACTIVE_PLAIN));
  }

  @Test
  void partialIndexPresent() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = '%s'".formatted(SCRATCH_SCHEMA)
                    + " AND indexname = 'idx_projects_retention_ends_on'")) {
      assertThat(rs.next()).as("idx_projects_retention_ends_on exists").isTrue();
      assertThat(rs.getString(1))
          .contains("(retention_ends_on)")
          .contains("WHERE (retention_ends_on IS NOT NULL)");
    }
  }

  @Test
  void schemaWithoutOrgSettingsRowStillBackfillsWithFive() {
    assertThat(noSettingsEndsOn).isEqualTo(LocalDate.of(2031, 10, 22));
  }

  @Test
  void scratchHistoryEndsAtV8() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT max(version::int), count(*) FILTER (WHERE NOT success)"
                    + " FROM \"%s\".flyway_schema_history WHERE version IS NOT NULL"
                        .formatted(SCRATCH_SCHEMA))) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(8);
      assertThat(rs.getInt(2)).isZero();
    }
  }

  /**
   * Re-running the whole V8 body rewrites no already-migrated row; a CLOSED row still missing its
   * end date is backfilled with the firm's CURRENT years (proving the backfill reads the setting).
   */
  @Test
  void reRunningTheV8BodyIsANoOpForMigratedRows() throws Exception {
    String v8 =
        new String(
            getClass().getResourceAsStream(V8_RESOURCE).readAllBytes(), StandardCharsets.UTF_8);
    try {
      exec(SCRATCH_SCHEMA, "UPDATE \"%s\".org_settings SET legal_matter_retention_years = 7");
      exec(
          SCRATCH_SCHEMA,
          "INSERT INTO \"%s\".projects (id, name, created_by, status, closed_at) VALUES ('"
              + RERUN_CLOSED
              + "', 'V8 rerun', '"
              + MEMBER
              + "', 'CLOSED', TIMESTAMPTZ '2026-10-22 09:00:00+00')");
      Map<String, String> before = rowVersions(SCRATCH_SCHEMA);

      try (Connection c = migrationDataSource.getConnection();
          Statement s = c.createStatement()) {
        try {
          s.execute("SET search_path TO \"%s\"".formatted(SCRATCH_SCHEMA));
          s.execute(v8);
        } finally {
          s.execute("RESET search_path"); // pooled connection — leave it as found
        }
      }

      Map<String, String> after = rowVersions(SCRATCH_SCHEMA);
      for (String id :
          new String[] {
            CLOSED_PLAIN,
            CLOSED_AFTER_COMPLETE,
            CLOSED_LATE_UTC,
            CLOSED_LEAP,
            CLOSED_CLOCK_ONLY,
            COMPLETED_STAMPED,
            ACTIVE_PLAIN
          }) {
        assertThat(after.get(id))
            .as("row %s rewritten by the re-run", id)
            .isEqualTo(before.get(id));
      }
      Map<String, LocalDate> endsOn = retentionEndsOn(SCRATCH_SCHEMA);
      assertThat(endsOn.get(CLOSED_PLAIN))
          .as("an already-stamped end date never moves")
          .isEqualTo(LocalDate.of(2031, 10, 22));
      assertThat(endsOn.get(RERUN_CLOSED)).isEqualTo(LocalDate.of(2033, 10, 22));
      assertThat(settingsYears(SCRATCH_SCHEMA)).as("the seed only touches NULL years").isEqualTo(7);
    } finally {
      exec(SCRATCH_SCHEMA, "DELETE FROM \"%s\".projects WHERE id = '" + RERUN_CLOSED + "'");
      exec(SCRATCH_SCHEMA, "UPDATE \"%s\".org_settings SET legal_matter_retention_years = 5");
    }
  }

  /**
   * closed_at is timestamptz but the clock is timestamp without time zone holding a UTC wall-clock
   * (how the application writes it). The backfill must read the clock as UTC whatever the session
   * time zone is: a clock-only row at 00:30 UTC run in a Johannesburg session would otherwise land
   * on the previous day.
   */
  @Test
  void clockOnlyBackfillUsesUtcDayInANonUtcSession() throws Exception {
    String v8 =
        new String(
            getClass().getResourceAsStream(V8_RESOURCE).readAllBytes(), StandardCharsets.UTF_8);
    try {
      exec(
          SCRATCH_SCHEMA,
          "INSERT INTO \"%s\".projects"
              + " (id, name, created_by, status, closed_at, retention_clock_started_at) VALUES ('"
              + TZ_CLOCK_ONLY
              + "', 'V8 clock only near midnight', '"
              + MEMBER
              + "', 'CLOSED', NULL, TIMESTAMP '2024-06-15 00:30:00')");

      try (Connection c = migrationDataSource.getConnection();
          Statement s = c.createStatement()) {
        try {
          s.execute("SET search_path TO \"%s\"".formatted(SCRATCH_SCHEMA));
          s.execute("SET TIME ZONE 'Africa/Johannesburg'");
          s.execute(v8);
        } finally {
          // Pooled connection — leave it as found.
          s.execute("RESET search_path");
          s.execute("RESET TIME ZONE");
        }
      }

      assertThat(retentionEndsOn(SCRATCH_SCHEMA).get(TZ_CLOCK_ONLY))
          .isEqualTo(LocalDate.of(2029, 6, 15));
    } finally {
      exec(SCRATCH_SCHEMA, "DELETE FROM \"%s\".projects WHERE id = '" + TZ_CLOCK_ONLY + "'");
    }
  }

  // ---------- helpers ----------

  private void seedMember(String schema) throws Exception {
    exec(
        schema,
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '#MEMBER#'::uuid, 'user_v8_retention', 'v8_retention@test.com', r.id
        FROM "%s".org_roles r
        WHERE r.slug = 'member'
        """
            .replace("#MEMBER#", MEMBER));
  }

  private Integer settingsYears(String schema) throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT legal_matter_retention_years FROM \"%s\".org_settings WHERE id = '%s'"
                    .formatted(schema, SETTINGS_ROW))) {
      return rs.next() ? (Integer) rs.getObject(1) : null;
    }
  }

  private Map<String, LocalDate> retentionEndsOn(String schema) throws Exception {
    var result = new HashMap<String, LocalDate>();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT id::text, retention_ends_on FROM \"%s\".projects".formatted(schema))) {
      while (rs.next()) {
        result.put(rs.getString(1), rs.getObject(2, LocalDate.class));
      }
    }
    return result;
  }

  private Map<String, String> rowVersions(String schema) throws Exception {
    var versions = new HashMap<String, String>();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery("SELECT id::text, xmin::text FROM \"%s\".projects".formatted(schema))) {
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
