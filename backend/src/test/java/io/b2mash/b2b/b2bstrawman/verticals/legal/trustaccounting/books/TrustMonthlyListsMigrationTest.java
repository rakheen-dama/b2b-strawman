package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

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
 * Phase 92 slice 650A (task 650.1, architecture §16.2.10 and the first block of §16.7.9, ADR-382):
 * tenant {@code V25} adds {@code trust_monthly_lists} — the Rule 54.15 list of one trust account at
 * one month end, frozen when that month's reconciliation completes. A list's period end is the last
 * day of a calendar month and its difference is zero (both CHECKs); one list per account and month,
 * and per reconciliation; three foreign keys, none cascading. Nothing is back-filled for a
 * reconciliation completed before the migration.
 *
 * <p>A scratch tenant schema is migrated to V24, seeded with a member, a trust account and a
 * COMPLETED reconciliation to a month end, then migrated to V25 — pinned so a later migration
 * cannot change these expectations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustMonthlyListsMigrationTest {

  /** The monthly-list migration (pinned, not "latest"). */
  private static final String MIGRATION_VERSION = "25";

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V25__phase92_trust_monthly_lists.sql";

  private static final String CHECK_VIOLATION = "23514";
  private static final String UNIQUE_VIOLATION = "23505";
  private static final String FK_VIOLATION = "23503";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_trust_monthly_lists_scratch");

  /** Placeholders — none is a prefix of another. */
  private static final Map<String, String> IDS = new LinkedHashMap<>();

  static {
    IDS.put("%owner", "00000000-0000-0000-0000-000000000001");
    IDS.put("%account", "00000000-0000-0000-0000-0000000000c1");
    IDS.put("%completed", "00000000-0000-0000-0000-0000000000f1");
    IDS.put("%second", "00000000-0000-0000-0000-0000000000f2");
    IDS.put("%third", "00000000-0000-0000-0000-0000000000f3");
  }

  private final DataSource migrationDataSource;
  private List<String> listsAfterMigration;

  @Autowired
  TrustMonthlyListsMigrationTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV24() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("24")).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%owner', 'user_tmlm', 'tmlm@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date, investment_basis)
        VALUES ('%account', 'Trust General', 'FNB', '250655', '62000000025', 'GENERAL', true,
                false, 'ACTIVE', DATE '2025-01-01', NULL);
        INSERT INTO "%s".trust_reconciliations (id, trust_account_id, period_end, bank_balance,
            cashbook_balance, client_ledger_total, adjusted_bank_balance, is_balanced, status,
            completed_by, completed_at)
        VALUES ('%completed', '%account', DATE '2026-01-31', 100.00, 100.00, 100.00, 100.00, true,
                'COMPLETED', '%owner', now()),
               ('%second', '%account', DATE '2026-02-28', 100.00, 100.00, 100.00, 100.00, true,
                'COMPLETED', '%owner', now()),
               ('%third', '%account', DATE '2026-04-30', 100.00, 100.00, 100.00, 100.00, true,
                'COMPLETED', '%owner', now());
        """);
    tenantFlyway().target(MigrationVersion.fromVersion(MIGRATION_VERSION)).load().migrate();
    listsAfterMigration = texts("SELECT id FROM \"%s\".trust_monthly_lists");
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void nothingIsBackFilled_forAReconciliationCompletedBeforeTheMigration() {
    assertThat(listsAfterMigration).isEmpty();
  }

  @Test
  void theTableHasItsColumns_typesAndNullability() throws Exception {
    assertThat(
            texts(
                "SELECT column_name || '|' || data_type || '|' || is_nullable || '|'"
                    + " || COALESCE(numeric_precision::text || ',' || numeric_scale::text,"
                    + " character_maximum_length::text, '-')"
                    + " FROM information_schema.columns WHERE table_schema = '%s'"
                    + " AND table_name = 'trust_monthly_lists' ORDER BY ordinal_position"))
        .containsExactly(
            "id|uuid|NO|-",
            "trust_account_id|uuid|NO|-",
            "period_end|date|NO|-",
            "reconciliation_id|uuid|NO|-",
            "bank_balance|numeric|NO|15,2",
            "outstanding_deposits|numeric|NO|15,2",
            "outstanding_payments|numeric|NO|15,2",
            "adjusted_bank_balance|numeric|NO|15,2",
            "cashbook_balance|numeric|NO|15,2",
            "client_ledger_total|numeric|NO|15,2",
            "investment_creditor_total|numeric|NO|15,2",
            "fund_ledger_balance|numeric|NO|15,2",
            "unidentified_deposit_total|numeric|NO|15,2",
            "inter_account_balance|numeric|NO|15,2",
            "creditor_total|numeric|NO|15,2",
            "difference|numeric|NO|15,2",
            "lines|jsonb|NO|-",
            "integrity_flags|jsonb|NO|-",
            "locked_by|uuid|NO|-",
            "locked_at|timestamp with time zone|NO|-");
  }

  @Test
  void theConstraintsExist_andNoneCascades() throws Exception {
    assertThat(constraintDef("trust_monthly_lists_pkey")).isEqualTo("PRIMARY KEY (id)");
    assertThat(constraintDef("uq_trust_monthly_list_account_period"))
        .isEqualTo("UNIQUE (trust_account_id, period_end)");
    assertThat(constraintDef("uq_trust_monthly_list_reconciliation"))
        .isEqualTo("UNIQUE (reconciliation_id)");
    assertThat(constraintDef("chk_trust_monthly_list_month_end"))
        .contains("period_end")
        .contains("date_trunc('month'")
        .contains("'1 mon -1 days'");
    assertThat(constraintDef("chk_trust_monthly_list_balanced")).contains("difference = ");
    assertThat(constraintDef("trust_monthly_lists_trust_account_id_fkey"))
        .contains("FOREIGN KEY (trust_account_id)")
        .contains("trust_accounts(id)");
    assertThat(constraintDef("trust_monthly_lists_reconciliation_id_fkey"))
        .contains("FOREIGN KEY (reconciliation_id)")
        .contains("trust_reconciliations(id)");
    assertThat(constraintDef("trust_monthly_lists_locked_by_fkey"))
        .contains("FOREIGN KEY (locked_by)")
        .contains("members(id)");
    assertThat(
            texts(
                "SELECT c.conname FROM pg_constraint c JOIN pg_namespace n ON n.oid ="
                    + " c.connamespace JOIN pg_class t ON t.oid = c.conrelid WHERE n.nspname ="
                    + " '%s' AND t.relname = 'trust_monthly_lists' AND c.confdeltype = 'c'"))
        .isEmpty();
  }

  @Test
  void theChecksRefuseTheBadShapes_andAcceptTheGoodOnes() throws Exception {
    // Not a month end
    assertRefused(list("%completed", "2026-03-30", "0.00"), CHECK_VIOLATION, "month_end");
    assertRefused(list("%completed", "2026-02-27", "0.00"), CHECK_VIOLATION, "month_end");
    // Not balanced
    assertRefused(list("%completed", "2026-01-31", "0.01"), CHECK_VIOLATION, "balanced");
    // Month ends, leap year included
    exec(list("%completed", "2026-02-28", "0.00"));
    exec(list("%second", "2028-02-29", "0.00"));
    // One list per account and month, and per reconciliation
    assertRefused(list("%third", "2026-02-28", "0.00"), UNIQUE_VIOLATION, "account_period");
    assertRefused(list("%completed", "2026-12-31", "0.00"), UNIQUE_VIOLATION, "reconciliation");
    // The three foreign keys (on an unused reconciliation and month)
    assertRefused(
        list("%third", "2027-01-31", "0.00").replace("'%owner'", "gen_random_uuid()"),
        FK_VIOLATION,
        "locked_by");
    assertRefused(
        list("%third", "2027-01-31", "0.00").replace("'%account', DATE", "gen_random_uuid(), DATE"),
        FK_VIOLATION,
        "trust_account_id");
    assertRefused(
        list("%third", "2027-01-31", "0.00").replace("'%third'", "gen_random_uuid()"),
        FK_VIOLATION,
        "reconciliation_id");
    exec("DELETE FROM \"%s\".trust_monthly_lists");
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

  /** A list row for {@code reconciliation} of the seeded account on {@code periodEnd}. */
  private static String list(String reconciliation, String periodEnd, String difference) {
    return """
        INSERT INTO "%%s".trust_monthly_lists (trust_account_id, period_end, reconciliation_id,
            bank_balance, outstanding_deposits, outstanding_payments, adjusted_bank_balance,
            cashbook_balance, client_ledger_total, investment_creditor_total, fund_ledger_balance,
            unidentified_deposit_total, inter_account_balance, creditor_total, difference, lines,
            locked_by, locked_at)
        VALUES ('%%account', DATE '%2$s', '%1$s', 100.00, 0.00, 0.00, 100.00, 100.00, 100.00,
                0.00, 0.00, 0.00, 0.00, 100.00, %3$s, '[]'::jsonb, '%%owner', now())
        """
        .formatted(reconciliation, periodEnd, difference);
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
                + " AND table_name = 'trust_monthly_lists' ORDER BY column_name"));
    rows.addAll(
        texts(
            "SELECT c.conname || '|' || pg_get_constraintdef(c.oid) FROM pg_constraint c"
                + " JOIN pg_namespace n ON n.oid = c.connamespace JOIN pg_class t"
                + " ON t.oid = c.conrelid WHERE n.nspname = '%s'"
                + " AND t.relname = 'trust_monthly_lists' ORDER BY c.conname"));
    rows.addAll(
        texts(
            "SELECT indexname || '|' || indexdef FROM pg_indexes WHERE schemaname = '%s'"
                + " AND tablename = 'trust_monthly_lists' ORDER BY indexname"));
    rows.addAll(texts("SELECT id FROM \"%s\".trust_monthly_lists ORDER BY id"));
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
