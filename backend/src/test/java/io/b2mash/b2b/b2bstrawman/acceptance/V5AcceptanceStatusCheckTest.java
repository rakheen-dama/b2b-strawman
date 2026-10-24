package io.b2mash.b2b.b2bstrawman.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * Tenant {@code V5__phase88_acceptance_decline} (Phase 88 §6, U23): {@code
 * acceptance_requests.declined_at} / {@code decline_reason} (nullable) and a new {@code
 * chk_acceptance_requests_status} over the seven statuses, guarded on {@code current_schema()}. A
 * scratch schema is migrated to V4, seeded with a SENT row, then migrated to V5.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V5AcceptanceStatusCheckTest {

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_v5_acceptance_status_check_scratch");

  private static final String SENT_ROW = "00000000-0000-0000-0000-0000000005a1";

  private final DataSource migrationDataSource;
  private String xminBefore;
  private String xminAfter;

  @Autowired
  V5AcceptanceStatusCheckTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV4() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    flyway().target(MigrationVersion.fromVersion("4")).load().migrate();
    // acceptance_requests has no foreign keys, so random ids are fine for the references.
    exec(
        """
        INSERT INTO "%s".acceptance_requests
          (id, generated_document_id, portal_contact_id, customer_id, status, request_token,
           expires_at, sent_by_member_id, reminder_count, created_at, updated_at)
        VALUES
          ('00000000-0000-0000-0000-0000000005a1', gen_random_uuid(), gen_random_uuid(),
           gen_random_uuid(), 'SENT', 'v5-scratch-token', now() + interval '30 days',
           gen_random_uuid(), 0, now(), now())
        """);

    xminBefore = rowVersion();
    flyway().target(MigrationVersion.fromVersion("5")).load().migrate();
    xminAfter = rowVersion();
  }

  @AfterAll
  void dropScratchSchema() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void declineColumnsAreNullableTimestamptzAndText() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = 'acceptance_requests'
                  AND column_name IN ('declined_at', 'decline_reason')
                ORDER BY column_name
                """
                    .formatted(SCRATCH_SCHEMA))) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("decline_reason");
      assertThat(rs.getString(2)).isEqualTo("text");
      assertThat(rs.getString(3)).isEqualTo("YES");
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("declined_at");
      assertThat(rs.getString(2)).isEqualTo("timestamp with time zone");
      assertThat(rs.getString(3)).isEqualTo("YES");
      assertThat(rs.next()).isFalse();
    }
  }

  @Test
  void existingRowIsUntouched() throws Exception {
    assertThat(xminAfter).isNotNull().isEqualTo(xminBefore);
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT status, declined_at, decline_reason FROM \"%s\".acceptance_requests WHERE id = '%s'"
                    .formatted(SCRATCH_SCHEMA, SENT_ROW))) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("SENT");
      assertThat(rs.getObject(2)).isNull();
      assertThat(rs.getObject(3)).isNull();
    }
  }

  @Test
  void statusCheckCoversTheSevenStatuses() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT count(*), max(pg_get_constraintdef(c.oid))
                FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE c.conname = 'chk_acceptance_requests_status' AND c.contype = 'c'
                  AND n.nspname = '%s'
                """
                    .formatted(SCRATCH_SCHEMA))) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(1);
      String def = rs.getString(2);
      for (var status : AcceptanceStatus.values()) {
        assertThat(def).as("CHECK covers %s", status).contains("'" + status.name() + "'");
      }
    }
  }

  @Test
  void declinedStatusIsAcceptedAndBogusIsRejected() throws Exception {
    try {
      exec(
          "UPDATE \"%s\".acceptance_requests SET status = 'DECLINED', declined_at = now(),"
              + " decline_reason = 'Price too high' WHERE id = '"
              + SENT_ROW
              + "'");
      try (Connection c = migrationDataSource.getConnection();
          Statement s = c.createStatement();
          ResultSet rs =
              s.executeQuery(
                  "SELECT status FROM \"%s\".acceptance_requests WHERE id = '%s'"
                      .formatted(SCRATCH_SCHEMA, SENT_ROW))) {
        rs.next();
        assertThat(rs.getString(1)).isEqualTo("DECLINED");
      }
      assertThatThrownBy(
              () ->
                  exec(
                      "UPDATE \"%s\".acceptance_requests SET status = 'BOGUS' WHERE id = '"
                          + SENT_ROW
                          + "'"))
          .isInstanceOf(SQLException.class)
          .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
    } finally {
      exec(
          "UPDATE \"%s\".acceptance_requests SET status = 'SENT', declined_at = NULL,"
              + " decline_reason = NULL WHERE id = '"
              + SENT_ROW
              + "'");
    }
  }

  /** Re-running the whole V5 body rewrites no row and duplicates no constraint. */
  @Test
  void reRunningTheV5BodyIsANoOp() throws Exception {
    String v5 =
        new String(
            getClass()
                .getResourceAsStream("/db/migration/tenant/V5__phase88_acceptance_decline.sql")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    // Another test updates (and restores) the row, so compare against the version just before.
    String before = rowVersion();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      try {
        s.execute("SET search_path TO \"%s\"".formatted(SCRATCH_SCHEMA));
        s.execute(v5);
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
                SELECT count(*)
                FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = '%s' AND c.conname = 'chk_acceptance_requests_status'
                """
                    .formatted(SCRATCH_SCHEMA))) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(1);
    }
  }

  private String rowVersion() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT xmin::text FROM \"%s\".acceptance_requests WHERE id = '%s'"
                    .formatted(SCRATCH_SCHEMA, SENT_ROW))) {
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
