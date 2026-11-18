package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 92 slice 650B (task 650.7, architecture §16.2.9 and the second block of §16.7.9, ADR-384):
 * tenant {@code V26} adds {@code trust_interest_memos} — the Rule 54.28 gross interest, gross
 * charges and VAT of one trust account for one month that never reached the account. A memo's month
 * is its first day and its amounts are zero or more with the VAT never above the charges (both
 * CHECKs); one memo per account and month; two foreign keys, none cascading. Nothing is
 * back-filled.
 *
 * <p>A scratch tenant schema is migrated to V25, seeded with a member and a trust account, then
 * migrated to V26 — pinned so a later migration cannot change these expectations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustInterestMemosMigrationTest {

  /** The memo migration (pinned, not "latest"). */
  private static final String MIGRATION_VERSION = "26";

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V26__phase92_trust_interest_memos.sql";

  private static final String CHECK_VIOLATION = "23514";
  private static final String UNIQUE_VIOLATION = "23505";
  private static final String FK_VIOLATION = "23503";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_trust_interest_memos_scratch");

  /** Placeholders — none is a prefix of another. */
  private static final Map<String, String> IDS = new LinkedHashMap<>();

  static {
    IDS.put("%owner", "00000000-0000-0000-0000-000000000001");
    IDS.put("%account", "00000000-0000-0000-0000-0000000000c1");
  }

  private final DataSource migrationDataSource;
  private List<String> memosAfterMigration;

  @Autowired
  TrustInterestMemosMigrationTest(
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV25() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("25")).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%owner', 'user_timm', 'timm@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date, investment_basis)
        VALUES ('%account', 'Trust General', 'FNB', '250655', '62000000026', 'GENERAL', true,
                false, 'ACTIVE', DATE '2025-01-01', NULL);
        """);
    tenantFlyway().target(MigrationVersion.fromVersion(MIGRATION_VERSION)).load().migrate();
    memosAfterMigration = texts("SELECT id FROM \"%s\".trust_interest_memos");
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void nothingIsBackFilled() {
    assertThat(memosAfterMigration).isEmpty();
  }

  @Test
  void theTableHasItsColumns_typesAndNullability() throws Exception {
    assertThat(
            texts(
                "SELECT column_name || '|' || data_type || '|' || is_nullable || '|'"
                    + " || COALESCE(numeric_precision::text || ',' || numeric_scale::text,"
                    + " character_maximum_length::text, '-')"
                    + " FROM information_schema.columns WHERE table_schema = '%s'"
                    + " AND table_name = 'trust_interest_memos' ORDER BY ordinal_position"))
        .containsExactly(
            "id|uuid|NO|-",
            "trust_account_id|uuid|NO|-",
            "period_month|date|NO|-",
            "gross_interest|numeric|NO|15,2",
            "gross_charges|numeric|NO|15,2",
            "charges_vat|numeric|NO|15,2",
            "source_note|character varying|NO|500",
            "recorded_by|uuid|NO|-",
            "created_at|timestamp with time zone|NO|-",
            "updated_at|timestamp with time zone|NO|-");
  }

  @Test
  void theConstraintsExist_andNoneCascades() throws Exception {
    assertThat(constraintDef("trust_interest_memos_pkey")).isEqualTo("PRIMARY KEY (id)");
    assertThat(constraintDef("uq_trust_interest_memo_account_month"))
        .isEqualTo("UNIQUE (trust_account_id, period_month)");
    assertThat(constraintDef("chk_trust_interest_memo_month"))
        .contains("EXTRACT(day FROM period_month)")
        .contains("1");
    assertThat(constraintDef("chk_trust_interest_memo_amounts"))
        .contains("gross_interest >= ")
        .contains("gross_charges >= ")
        .contains("charges_vat >= ")
        .contains("charges_vat <= gross_charges");
    assertThat(constraintDef("trust_interest_memos_trust_account_id_fkey"))
        .contains("FOREIGN KEY (trust_account_id)")
        .contains("trust_accounts(id)");
    assertThat(constraintDef("trust_interest_memos_recorded_by_fkey"))
        .contains("FOREIGN KEY (recorded_by)")
        .contains("members(id)");
    assertThat(
            texts(
                "SELECT c.conname FROM pg_constraint c JOIN pg_namespace n ON n.oid ="
                    + " c.connamespace JOIN pg_class t ON t.oid = c.conrelid WHERE n.nspname ="
                    + " '%s' AND t.relname = 'trust_interest_memos' AND c.confdeltype = 'c'"))
        .isEmpty();
  }

  @Test
  void theChecksRefuseTheBadShapes_andAcceptTheGoodOnes() throws Exception {
    // Not the first day of the month
    assertRefused(memo("2026-03-02", "10.00", "5.00", "1.00"), CHECK_VIOLATION, "month");
    // A negative amount; the VAT above the charges it is included in
    assertRefused(memo("2026-03-01", "-0.01", "5.00", "1.00"), CHECK_VIOLATION, "amounts");
    assertRefused(memo("2026-03-01", "10.00", "-5.00", "0.00"), CHECK_VIOLATION, "amounts");
    assertRefused(memo("2026-03-01", "10.00", "5.00", "5.01"), CHECK_VIOLATION, "amounts");
    // The VAT equal to the charges, and an all-zero memo, are accepted
    exec(memo("2026-03-01", "10.00", "5.00", "5.00"));
    exec(memo("2026-04-01", "0.00", "0.00", "0.00"));
    // One memo per account and month
    assertRefused(memo("2026-03-01", "1.00", "1.00", "0.00"), UNIQUE_VIOLATION, "account_month");
    // The two foreign keys (on an unused month)
    assertRefused(
        memo("2026-05-01", "1.00", "1.00", "0.00").replace("'%owner'", "gen_random_uuid()"),
        FK_VIOLATION,
        "recorded_by");
    assertRefused(
        memo("2026-05-01", "1.00", "1.00", "0.00")
            .replace("'%account', DATE", "gen_random_uuid(), DATE"),
        FK_VIOLATION,
        "trust_account_id");
    // Defaults: the amounts are zero and the timestamps set when not named
    exec(
        """
        INSERT INTO "%s".trust_interest_memos (trust_account_id, period_month, source_note,
            recorded_by)
        VALUES ('%account', DATE '2026-06-01', 'Defaults', '%owner')
        """);
    assertThat(
            text(
                "SELECT gross_interest || '|' || gross_charges || '|' || charges_vat || '|'"
                    + " || (created_at IS NOT NULL) || '|' || (updated_at IS NOT NULL)"
                    + " FROM \"%s\".trust_interest_memos WHERE period_month = DATE '2026-06-01'"))
        .isEqualTo("0.00|0.00|0.00|true|true");
    exec("DELETE FROM \"%s\".trust_interest_memos");
  }

  @Test
  void runningTheMigrationBodyTwice_isANoOp() throws Exception {
    var before = fingerprint();
    for (int run = 0; run < 2; run++) {
      runBody();
    }
    assertThat(fingerprint()).isEqualTo(before);
  }

  // --- helpers ---

  /** A memo of the seeded account for the month starting {@code periodMonth}. */
  private static String memo(
      String periodMonth, String grossInterest, String grossCharges, String chargesVat) {
    return """
        INSERT INTO "%%s".trust_interest_memos (trust_account_id, period_month, gross_interest,
            gross_charges, charges_vat, source_note, recorded_by)
        VALUES ('%%account', DATE '%1$s', %2$s, %3$s, %4$s, 'FNB advice', '%%owner')
        """
        .formatted(periodMonth, grossInterest, grossCharges, chargesVat);
  }

  private void assertRefused(String sql, String sqlState, String constraintFragment) {
    assertThatThrownBy(() -> exec(sql))
        .as("refused: %s", sql)
        .isInstanceOfSatisfying(
            SQLException.class,
            e -> {
              assertThat(e.getSQLState()).isEqualTo(sqlState);
              assertThat(e.getMessage()).contains(constraintFragment);
            });
  }

  private void runBody() throws Exception {
    var body =
        new String(
            new ClassPathResource(MIGRATION_FILE).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    try (Connection c = migrationDataSource.getConnection()) {
      c.setAutoCommit(false);
      try (Statement s = c.createStatement()) {
        // SET LOCAL: the search_path reverts at commit, so the pooled connection is unaffected.
        s.execute("SET LOCAL search_path TO \"" + SCRATCH_SCHEMA + "\"");
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

  private static String constraintSql(String name) {
    return "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c"
        + " JOIN pg_namespace n ON n.oid = c.connamespace WHERE n.nspname = '%s'"
        + " AND c.conname = '"
        + name
        + "'";
  }

  private String constraintDef(String name) throws Exception {
    return text(constraintSql(name));
  }

  /** The table's columns, constraints and indexes, and its rows — what a second run must keep. */
  private List<String> fingerprint() throws Exception {
    var rows = new ArrayList<String>();
    rows.addAll(
        texts(
            "SELECT column_name || '|' || data_type || '|' || is_nullable"
                + " FROM information_schema.columns WHERE table_schema = '%s'"
                + " AND table_name = 'trust_interest_memos' ORDER BY column_name"));
    rows.addAll(
        texts(
            "SELECT c.conname || '|' || pg_get_constraintdef(c.oid) FROM pg_constraint c"
                + " JOIN pg_namespace n ON n.oid = c.connamespace JOIN pg_class t"
                + " ON t.oid = c.conrelid WHERE n.nspname = '%s'"
                + " AND t.relname = 'trust_interest_memos' ORDER BY c.conname"));
    rows.addAll(
        texts(
            "SELECT indexname || '|' || indexdef FROM pg_indexes WHERE schemaname = '%s'"
                + " AND tablename = 'trust_interest_memos' ORDER BY indexname"));
    rows.addAll(texts("SELECT id FROM \"%s\".trust_interest_memos ORDER BY id"));
    return rows;
  }

  private String text(String sql) throws Exception {
    var rows = texts(sql);
    assertThat(rows).as("one row from %s", sql).hasSize(1);
    return rows.getFirst();
  }

  /** Every row of {@code sql}, its columns joined with {@code |} (NULL as "NULL"). */
  private List<String> texts(String sql) throws Exception {
    var rows = new ArrayList<String>();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(resolve(sql))) {
      int columns = rs.getMetaData().getColumnCount();
      while (rs.next()) {
        var values = new ArrayList<String>();
        for (int i = 1; i <= columns; i++) {
          var value = rs.getString(i);
          values.add(value == null ? "NULL" : value);
        }
        rows.add(String.join("|", values));
      }
    }
    return rows;
  }

  private void exec(String sql) throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute(resolve(sql));
    }
  }

  /** Replaces the id placeholders, then {@code %s} with the scratch schema. */
  private static String resolve(String sql) {
    var resolved = sql;
    for (var entry : IDS.entrySet()) {
      resolved = resolved.replace(entry.getKey(), entry.getValue());
    }
    return resolved.replace("%s", SCRATCH_SCHEMA);
  }

  private FluentConfiguration tenantFlyway() {
    // Same locations/config as TenantProvisioningService.runTenantMigrations.
    return Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(SCRATCH_SCHEMA)
        .baselineOnMigrate(false);
  }
}
