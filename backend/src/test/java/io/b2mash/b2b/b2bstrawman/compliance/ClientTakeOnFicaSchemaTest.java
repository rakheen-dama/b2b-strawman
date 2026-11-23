package io.b2mash.b2b.b2bstrawman.compliance;

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
 * Phase 93 / 655A (task 655.2, architecture §17.2.5, §17.2.11, §17.7.4, ADR-388, ADR-389): tenant
 * {@code V34__phase93_client_take_on_fica} (guide V32/V33 — both taken, next free, C-1). A scratch
 * schema is walked to V33 with an existing client and an {@code org_settings} row, then migrated:
 * the old rows are untouched, existing settings get the 36/24/12 review intervals, and every new
 * CHECK, unique index and foreign key rejects the shape it forbids.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientTakeOnFicaSchemaTest {

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V34__phase93_client_take_on_fica.sql";
  private static final String PREVIOUS_VERSION = "33";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String S =
      SchemaNameGenerator.generateSchemaName("org_client_takeon_fica_scratch");

  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID CLIENT_2 = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
  private static final UUID BATCH = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final UUID ROW = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
  private static final UUID SIGN_OFF = UUID.fromString("00000000-0000-0000-0000-0000000000b4");

  @Autowired private JdbcTemplate jdbc;

  @Autowired
  @Qualifier("migrationDataSource")
  private DataSource migrationDataSource;

  @BeforeAll
  void walkToV33() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"" + S + "\" CASCADE");
    exec("CREATE SCHEMA \"" + S + "\"");
    tenantFlyway().target(MigrationVersion.fromVersion(PREVIOUS_VERSION)).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%o', 'user_ctf', 'ctf@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".customers (id, name, email, created_by)
        VALUES ('%c', 'Existing client', 'existing@test.com', '%o'),
               ('%c2', 'Second client', 'second@test.com', '%o');
        INSERT INTO "%s".org_settings DEFAULT VALUES;
        INSERT INTO "%s".take_on_batches (id, dataset, status, validation_hash, created_by,
            created_at, updated_at)
        VALUES ('%b', 'CLIENTS', 'VALIDATED', repeat('a', 64), '%o', now(), now());
        INSERT INTO "%s".take_on_sources (id, batch_id, section, file_name, content_type,
            file_format, file_size, file_sha256, parse_options, uploaded_by, uploaded_at)
        VALUES ('%src', '%b', 'MAIN', 'c.csv', 'text/csv', 'CSV', 10, repeat('b', 64), '{}',
                '%o', now());
        INSERT INTO "%s".take_on_rows (id, batch_id, source_id, dataset, section, row_number,
            raw_values, status, created_at, updated_at)
        VALUES ('%r', '%b', '%src', 'CLIENTS', 'MAIN', 2, '{}', 'VALIDATED', now(), now());
        INSERT INTO "%s".take_on_sign_offs (id, batch_id, kind, member_id, member_name, role_name,
            statement_version, statement_text, validation_hash, signed_at)
        VALUES ('%so', '%b', 'FICA_ATTESTATION', '%o', 'Owner', 'Owner', 'FICA-ATTEST-v1', 'I confirm',
                repeat('a', 64), now());
        """
            .replace("%src", SOURCE.toString())
            .replace("%so", SIGN_OFF.toString())
            .replace("%s", S)
            .replace("%o", OWNER.toString())
            .replace("%c2", CLIENT_2.toString())
            .replace("%c", CLIENT.toString())
            .replace("%b", BATCH.toString())
            .replace("%r", ROW.toString()));
    tenantFlyway().load().migrate();
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"" + S + "\" CASCADE");
  }

  // 1
  @Test
  void migrationAppliesOnAWalkedSchema_andLeavesOldRowsAlone() {
    // Applied (not "the latest": a later migration must not break this pin — 655A, V34).
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".flyway_schema_history WHERE success AND version = ?"
                    .formatted(S),
                Integer.class,
                "34"))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                ("SELECT count(*) FROM \"%s\".customers WHERE id IN (?, ?)"
                        + " AND take_on_batch_id IS NULL AND taken_on_at IS NULL")
                    .formatted(S),
                Integer.class,
                CLIENT,
                CLIENT_2))
        .isEqualTo(2);
  }

  // 2
  @Test
  void existingOrgSettings_getTheDefaultReviewIntervals() {
    Map<String, Object> row =
        jdbc.queryForMap(
            ("SELECT fica_review_months_low, fica_review_months_medium, fica_review_months_high"
                    + " FROM \"%s\".org_settings LIMIT 1")
                .formatted(S));
    assertThat(row)
        .containsEntry("fica_review_months_low", 36)
        .containsEntry("fica_review_months_medium", 24)
        .containsEntry("fica_review_months_high", 12);
  }

  // 3
  @Test
  void reviewIntervals_mustBeOneToOneHundredAndTwentyMonths() {
    for (String column :
        List.of("fica_review_months_low", "fica_review_months_medium", "fica_review_months_high")) {
      for (int bad : new int[] {0, 121}) {
        assertRejected(
            () -> jdbc.update("UPDATE \"%s\".org_settings SET %s = ?".formatted(S, column), bad),
            "chk_org_settings_fica_review_months");
      }
    }
    assertThat(
            jdbc.update(
                ("UPDATE \"%s\".org_settings SET fica_review_months_low = 120,"
                        + " fica_review_months_high = 1")
                    .formatted(S)))
        .isEqualTo(1);
    jdbc.update(
        ("UPDATE \"%s\".org_settings SET fica_review_months_low = 36,"
                + " fica_review_months_high = 12")
            .formatted(S));
  }

  // 4
  @Test
  void customerTakeOn_batchAndInstantTogether() {
    assertRejected(
        () ->
            jdbc.update(
                "UPDATE \"%s\".customers SET take_on_batch_id = ? WHERE id = ?".formatted(S),
                BATCH,
                CLIENT_2),
        "chk_customer_taken_on");
    assertRejected(
        () ->
            jdbc.update(
                "UPDATE \"%s\".customers SET taken_on_at = now() WHERE id = ?".formatted(S),
                CLIENT_2),
        "chk_customer_taken_on");
    assertRejected(
        () ->
            jdbc.update(
                ("UPDATE \"%s\".customers SET take_on_batch_id = ?, taken_on_at = now()"
                        + " WHERE id = ?")
                    .formatted(S),
                UUID.randomUUID(),
                CLIENT_2),
        "customers_take_on_batch_fkey");
  }

  // 5
  @Test
  void attestationChecks_rejectEachForbiddenShape() {
    assertThat(insertAttestation(CLIENT, Map.of())).isEqualTo(1); // the valid shape
    jdbc.update(
        ("UPDATE \"%s\".client_fica_attestations SET status = 'SUPERSEDED', superseded_at = now()"
                + " WHERE customer_id = ?")
            .formatted(S),
        CLIENT);
    assertRejected(
        () -> insertAttestation(CLIENT, Map.of("status", "'GONE'")), "chk_fica_att_status");
    assertRejected(
        () -> insertAttestation(CLIENT, Map.of("superseded_at", "now()")),
        "chk_fica_att_superseded");
    assertRejected(
        () -> insertAttestation(CLIENT, Map.of("risk_rating", "'EXTREME'")), "chk_fica_att_risk");
    assertRejected(
        () -> insertAttestation(CLIENT, Map.of("fica_client_type", "'ALIEN'")),
        "chk_fica_att_client_type");
    assertRejected(
        () -> insertAttestation(CLIENT, Map.of("beneficial_ownership_status", "'MAYBE'")),
        "chk_fica_att_bo");
    assertRejected(
        () -> insertAttestation(CLIENT, Map.of("screening_status", "'PENDING'")),
        "chk_fica_att_screening");
    assertRejected(
        () -> insertAttestation(CLIENT, Map.of("screening_date", "NULL")),
        "chk_fica_att_screening_date");
    assertThat(
            insertAttestation(
                CLIENT,
                Map.of(
                    "screening_status", "'NOT_SCREENED'",
                    "screening_date", "NULL",
                    "status", "'SUPERSEDED'",
                    "superseded_at", "now()")))
        .as("a client not screened needs no screening date")
        .isEqualTo(1);
    assertRejected(
        () -> insertAttestation(CLIENT, Map.of("source_of_funds_status", "'SOMETIMES'")),
        "chk_fica_att_sof");
    assertRejected(
        () -> insertAttestation(CLIENT, Map.of("risk_rating", "'UNRATED'")), "chk_fica_att_review");
    assertRejected(
        () -> insertAttestation(CLIENT, Map.of("next_review_date", "NULL")), "chk_fica_att_review");
    assertThat(
            insertAttestation(
                CLIENT,
                Map.of(
                    "risk_rating", "'UNRATED'",
                    "next_review_date", "NULL",
                    "status", "'SUPERSEDED'",
                    "superseded_at", "now()")))
        .as("an unrated client has no review date")
        .isEqualTo(1);
  }

  // 6
  @Test
  void oneActiveAttestationPerClient_supersededOnesUnlimited() {
    assertThat(insertAttestation(CLIENT_2, Map.of())).isEqualTo(1);
    assertRejected(() -> insertAttestation(CLIENT_2, Map.of()), "uq_fica_att_active_customer");
    for (int i = 0; i < 2; i++) {
      assertThat(
              insertAttestation(
                  CLIENT_2, Map.of("status", "'SUPERSEDED'", "superseded_at", "now()")))
          .isEqualTo(1);
    }
    String reviewIndex =
        jdbc.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND indexname = ?",
            String.class,
            S,
            "idx_fica_att_review");
    assertThat(reviewIndex).contains("NULLS FIRST").contains("'ACTIVE'");
  }

  // 7
  @Test
  void attestationReferences_areForeignKeysThatNeverCascade() {
    var missing = "'" + UUID.randomUUID() + "'";
    assertRejected(
        () ->
            insertAttestation(
                UUID.randomUUID(), Map.of("status", "'SUPERSEDED'", "superseded_at", "now()")),
        "client_fica_attestations_customer_fkey");
    assertRejected(
        () -> insertAttestation(CLIENT, Map.of("take_on_batch_id", missing)),
        "client_fica_attestations_batch_fkey");
    assertRejected(
        () -> insertAttestation(CLIENT, Map.of("take_on_row_id", missing)),
        "client_fica_attestations_row_fkey");
    assertRejected(
        () -> insertAttestation(CLIENT, Map.of("sign_off_id", missing)),
        "client_fica_attestations_sign_off_fkey");
    assertRejected(
        () -> insertAttestation(CLIENT, Map.of("attested_by", missing)),
        "client_fica_attestations_member_fkey");
    for (String fk :
        List.of(
            "customers_take_on_batch_fkey",
            "client_fica_attestations_customer_fkey",
            "client_fica_attestations_batch_fkey",
            "client_fica_attestations_row_fkey",
            "client_fica_attestations_sign_off_fkey",
            "client_fica_attestations_member_fkey")) {
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

  // 8
  @Test
  void rerunningTheMigrationBody_changesNothing() throws Exception {
    String reviewBefore = constraintDef("chk_org_settings_fica_review_months");
    String takenOnBefore = constraintDef("chk_customer_taken_on");
    rerunMigrationBody();
    assertThat(constraintDef("chk_org_settings_fica_review_months")).isEqualTo(reviewBefore);
    assertThat(constraintDef("chk_customer_taken_on")).isEqualTo(takenOnBefore);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = ? AND c.conname IN ('customers_take_on_batch_fkey',
                  'chk_customer_taken_on', 'chk_org_settings_fica_review_months')
                """,
                Integer.class,
                S))
        .isEqualTo(3);
  }

  // --- helpers ---

  /**
   * Inserts one attestation of {@code customer} — a valid ACTIVE shape (MEDIUM, screened clear with
   * a date, a review date) with {@code overrides} (column → SQL literal) applied.
   */
  private int insertAttestation(UUID customer, Map<String, String> overrides) {
    var values = new java.util.LinkedHashMap<String, String>();
    values.put("id", "gen_random_uuid()");
    values.put("customer_id", "'" + customer + "'");
    values.put("status", "'ACTIVE'");
    values.put("take_on_batch_id", "'" + BATCH + "'");
    values.put("take_on_row_id", "'" + ROW + "'");
    values.put("sign_off_id", "'" + SIGN_OFF + "'");
    values.put("cdd_completed_date", "DATE '2024-02-01'");
    values.put("risk_rating", "'MEDIUM'");
    values.put("fica_client_type", "'NATURAL_PERSON'");
    values.put("beneficial_ownership_status", "'NOT_APPLICABLE'");
    values.put("screening_status", "'SCREENED_CLEAR'");
    values.put("screening_date", "DATE '2024-02-01'");
    values.put("cdd_records_location", "'Archive box 1'");
    values.put("next_review_date", "DATE '2026-02-01'");
    values.put("attested_by", "'" + OWNER + "'");
    values.put("attested_at", "now()");
    values.put("created_at", "now()");
    values.put("superseded_at", "NULL");
    values.putAll(overrides);
    return jdbc.update(
        "INSERT INTO \"%s\".client_fica_attestations (%s) VALUES (%s)"
            .formatted(S, String.join(", ", values.keySet()), String.join(", ", values.values())));
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
