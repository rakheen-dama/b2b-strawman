package io.b2mash.b2b.b2bstrawman.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
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
import org.springframework.test.context.ActiveProfiles;

/**
 * Tenant {@code V7__phase88_letterhead} (Phase 88 §4 U8, ADR-353): sixteen nullable {@code
 * letterhead_*} columns on {@code org_settings} backing {@link LetterheadSettings}. A scratch
 * schema is migrated to V6, seeded with one {@code org_settings} row, then migrated to V7.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V7LetterheadColumnsTest {

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_v7_letterhead_columns_scratch");

  private static final String SETTINGS_ROW = "00000000-0000-0000-0000-0000000007a1";

  /** (column, data_type, character_maximum_length) — §13.7.1 §4, ordered by column name. */
  private static final List<String> EXPECTED_COLUMNS =
      List.of(
          "letterhead_address_line1|character varying|255",
          "letterhead_address_line2|character varying|255",
          "letterhead_address_line3|character varying|255",
          "letterhead_bank_account_number|character varying|50",
          "letterhead_bank_account_type|character varying|30",
          "letterhead_bank_branch_code|character varying|20",
          "letterhead_bank_name|character varying|100",
          "letterhead_city|character varying|100",
          "letterhead_country|character varying|100",
          "letterhead_email|character varying|255",
          "letterhead_payment_reference_note|text|null",
          "letterhead_phone|character varying|50",
          "letterhead_postal_code|character varying|20",
          "letterhead_signatory_designation|character varying|255",
          "letterhead_signatory_name|character varying|255",
          "letterhead_website|character varying|255");

  private final DataSource migrationDataSource;
  private String xminBefore;
  private String xminAfter;

  @Autowired
  V7LetterheadColumnsTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV6() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    flyway().target(MigrationVersion.fromVersion("6")).load().migrate();
    // Every NOT NULL column of org_settings has a DEFAULT (V1 baseline / V4 §2).
    exec("INSERT INTO \"%s\".org_settings (id) VALUES ('" + SETTINGS_ROW + "')");

    xminBefore = rowVersion();
    flyway().target(MigrationVersion.fromVersion("7")).load().migrate();
    xminAfter = rowVersion();
  }

  @AfterAll
  void dropScratchSchema() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void sixteenLetterheadColumnsArePresentAndNullable() throws Exception {
    List<String> actual = new ArrayList<>();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT column_name, data_type, character_maximum_length, is_nullable
                FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = 'org_settings'
                  AND column_name LIKE 'letterhead\\_%%'
                ORDER BY column_name
                """
                    .formatted(SCRATCH_SCHEMA))) {
      while (rs.next()) {
        assertThat(rs.getString(4)).as("%s nullable", rs.getString(1)).isEqualTo("YES");
        actual.add(rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getObject(3));
      }
    }
    assertThat(actual).containsExactlyElementsOf(EXPECTED_COLUMNS);
  }

  @Test
  void existingRowIsUntouchedAndReadsNull() throws Exception {
    assertThat(xminAfter).isNotNull().isEqualTo(xminBefore);
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT %s FROM \"%s\".org_settings WHERE id = '%s'"
                    .formatted(columnList(), SCRATCH_SCHEMA, SETTINGS_ROW))) {
      assertThat(rs.next()).isTrue();
      for (int i = 1; i <= EXPECTED_COLUMNS.size(); i++) {
        assertThat(rs.getObject(i)).as(EXPECTED_COLUMNS.get(i - 1)).isNull();
      }
    }
  }

  @Test
  void scratchHistoryEndsAtV7() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT max(version::int), count(*) FILTER (WHERE NOT success)"
                    + " FROM \"%s\".flyway_schema_history WHERE version IS NOT NULL"
                        .formatted(SCRATCH_SCHEMA))) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(7);
      assertThat(rs.getInt(2)).isZero();
    }
  }

  /** Re-running the whole V7 body adds no column and rewrites no row. */
  @Test
  void reRunningTheV7BodyIsANoOp() throws Exception {
    String v7 =
        new String(
            getClass()
                .getResourceAsStream("/db/migration/tenant/V7__phase88_letterhead.sql")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    String before = rowVersion();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      try {
        s.execute("SET search_path TO \"%s\"".formatted(SCRATCH_SCHEMA));
        s.execute(v7);
      } finally {
        s.execute("RESET search_path"); // pooled connection — leave it as found
      }
    }

    assertThat(rowVersion()).isEqualTo(before);
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = 'org_settings'
                  AND column_name LIKE 'letterhead\\_%%'
                """
                    .formatted(SCRATCH_SCHEMA))) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(EXPECTED_COLUMNS.size());
    }
  }

  private static String columnList() {
    return String.join(", ", EXPECTED_COLUMNS.stream().map(c -> c.split("\\|")[0]).toList());
  }

  private String rowVersion() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT xmin::text FROM \"%s\".org_settings WHERE id = '%s'"
                    .formatted(SCRATCH_SCHEMA, SETTINGS_ROW))) {
      return rs.next() ? rs.getString(1) : null;
    }
  }

  private void exec(String sqlWithSchemaPlaceholders) throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute(sqlWithSchemaPlaceholders.replace("%s", SCRATCH_SCHEMA));
    }
  }

  private FluentConfiguration flyway() {
    // Same locations/config as TenantProvisioningService.runTenantMigrations.
    return Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(SCRATCH_SCHEMA)
        .baselineOnMigrate(false);
  }
}
