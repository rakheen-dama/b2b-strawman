package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

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
 * Phase 92 slice 648B (task 648.8, architecture §16.2.8 and the tenant part of §16.7.8, ADR-379):
 * tenant {@code V24} adds {@code trust_investment_interest} — one row per section 86(4) interest
 * credit with its gross, the Fund's 5% and the client's 95% — and drops the hand-typed {@code
 * trust_investments.interest_earned} (its CHECK {@code chk_trust_invest_earned} goes with it). The
 * 5% is deliberately not a CHECK (OL-5: a constant and a R0,01 tolerance in the service).
 *
 * <p>A scratch tenant schema is migrated to V23, seeded with a section 86(4) investment (its {@code
 * interest_earned} hand-typed at 10.00) and its posted rows, then migrated to V24 — pinned so a
 * later migration cannot change these expectations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustInvestmentInterestMigrationTest {

  /** The investment-interest migration (pinned, not "latest"). */
  private static final String MIGRATION_VERSION = "24";

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V24__phase92_investment_interest.sql";

  private static final String CHECK_VIOLATION = "23514";
  private static final String FK_VIOLATION = "23503";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_investment_interest_scratch");

  /** Placeholders — none is a prefix of another. */
  private static final Map<String, String> IDS = new LinkedHashMap<>();

  static {
    IDS.put("%owner", "00000000-0000-0000-0000-000000000001");
    IDS.put("%client", "00000000-0000-0000-0000-0000000000a1");
    IDS.put("%matter", "00000000-0000-0000-0000-0000000000b1");
    IDS.put("%general", "00000000-0000-0000-0000-0000000000c1");
    IDS.put("%investAcct", "00000000-0000-0000-0000-0000000000c2");
    IDS.put("%deposit", "00000000-0000-0000-0000-0000000000d1");
    IDS.put("%place", "00000000-0000-0000-0000-0000000000d2");
    IDS.put("%receive", "00000000-0000-0000-0000-0000000000d3");
    IDS.put("%netRow", "00000000-0000-0000-0000-0000000000d4");
    IDS.put("%investment", "00000000-0000-0000-0000-0000000000e1");
  }

  private final DataSource migrationDataSource;
  private List<String> trustRowsBefore;
  private List<String> balancesBefore;
  private List<String> entriesAfterMigration;

  @Autowired
  TrustInvestmentInterestMigrationTest(
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV23() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("23")).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%owner', 'user_tiim', 'tiim@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".customers (id, name, email, customer_type, lifecycle_status, created_by)
        VALUES ('%client', 'Client A', 'a@tiim.test', 'INDIVIDUAL', 'ACTIVE', '%owner');
        INSERT INTO "%s".projects (id, name, created_by) VALUES ('%matter', 'Matter', '%owner');
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date, investment_basis)
        VALUES ('%general', 'Trust General', 'FNB', '250655', '62000000024', 'GENERAL', true,
                false, 'ACTIVE', DATE '2025-01-01', NULL),
               ('%investAcct', 'Trust Investment', 'Investec', '580105', '71000000024',
                'INVESTMENT', false, false, 'ACTIVE', DATE '2025-01-01', 'CLIENT_INSTRUCTION');
        INSERT INTO "%s".trust_investments (id, trust_account_id, investment_account_id,
            customer_id, project_id, institution, account_number, principal, interest_rate,
            deposit_date, interest_earned, status, investment_basis)
        VALUES ('%investment', '%general', '%investAcct', '%client', '%matter', 'Investec', '1',
                1000.00, 0.0500, DATE '2025-02-02', 10.00, 'ACTIVE', 'CLIENT_INSTRUCTION');
        INSERT INTO "%s".trust_transactions (id, trust_account_id, transaction_type, amount,
            customer_id, project_id, reference, transaction_date, status, recorded_by, ledger,
            cashbook_effect, ledger_effect, posted_at, investment_id)
        VALUES ('%deposit', '%general', 'DEPOSIT', 5000.00, '%client', '%matter', 'DEP-1',
                DATE '2025-02-01', 'RECORDED', '%owner', 'CLIENT', 5000.00, 5000.00, now(), NULL),
               ('%place', '%general', 'INV_PLACE', 1000.00, '%client', '%matter', 'INV-1',
                DATE '2025-02-02', 'APPROVED', '%owner', 'CLIENT', -1000.00, -1000.00, now(),
                '%investment'),
               ('%receive', '%investAcct', 'INV_RECEIVE', 1000.00, '%client', '%matter', 'INV-1',
                DATE '2025-02-02', 'APPROVED', '%owner', 'CLIENT_INVESTMENT', 1000.00, 1000.00,
                now(), '%investment'),
               ('%netRow', '%investAcct', 'INV_INTEREST', 25.00, '%client', '%matter', 'INT-1',
                DATE '2025-02-28', 'RECORDED', '%owner', 'CLIENT_INVESTMENT', 25.00, 25.00, now(),
                '%investment');
        UPDATE "%s".trust_investments SET deposit_transaction_id = '%place'
         WHERE id = '%investment';
        """);
    trustRowsBefore = trustRows();
    balancesBefore = balances();
    tenantFlyway().target(MigrationVersion.fromVersion(MIGRATION_VERSION)).load().migrate();
    entriesAfterMigration = texts("SELECT id FROM \"%s\".trust_investment_interest");
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void interestEarnedIsDropped_withItsCheck_andNoRowOrBalanceMoves() throws Exception {
    assertThat(
            texts(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = '%s'"
                    + " AND table_name = 'trust_investments' AND column_name = 'interest_earned'"))
        .isEmpty();
    assertThat(texts(constraintSql("chk_trust_invest_earned"))).isEmpty();
    // The investment and every trust row stay as they were; no entry is invented for them
    assertThat(texts("SELECT id, status FROM \"%s\".trust_investments"))
        .containsExactly(IDS.get("%investment") + "|ACTIVE");
    assertThat(trustRows()).isEqualTo(trustRowsBefore).hasSize(4);
    assertThat(balances()).isEqualTo(balancesBefore);
    assertThat(entriesAfterMigration).isEmpty();
  }

  @Test
  void theTableHasItsColumns_typesAndNullability() throws Exception {
    assertThat(
            texts(
                "SELECT column_name || '|' || data_type || '|' || is_nullable || '|'"
                    + " || COALESCE(numeric_precision::text || ',' || numeric_scale::text,"
                    + " character_maximum_length::text, '-')"
                    + " FROM information_schema.columns WHERE table_schema = '%s'"
                    + " AND table_name = 'trust_investment_interest' ORDER BY ordinal_position"))
        .containsExactly(
            "id|uuid|NO|-",
            "investment_id|uuid|NO|-",
            "entry_date|date|NO|-",
            "gross_amount|numeric|NO|15,2",
            "fund_share_amount|numeric|NO|15,2",
            "net_amount|numeric|NO|15,2",
            "presentation|character varying|NO|10",
            "net_transaction_id|uuid|NO|-",
            "fund_share_transaction_id|uuid|YES|-",
            "bank_statement_line_id|uuid|YES|-",
            "client_tax_reference|character varying|YES|100",
            "status|character varying|NO|20",
            "recorded_by|uuid|NO|-",
            "created_at|timestamp with time zone|NO|-");
  }

  @Test
  void theChecksForeignKeysAndIndexExist() throws Exception {
    assertThat(constraintDef("chk_invest_interest_amounts"))
        .contains("gross_amount > ")
        .contains("fund_share_amount >= ")
        .contains("net_amount > ")
        .contains("gross_amount = (fund_share_amount + net_amount)");
    assertThat(constraintDef("chk_invest_interest_presentation")).contains("'GROSS'", "'NET'");
    assertThat(constraintDef("chk_invest_interest_fund_txn"))
        .contains("'GROSS'")
        .contains("fund_share_transaction_id IS NOT NULL");
    assertThat(constraintDef("chk_invest_interest_status")).contains("'POSTED'", "'REVERSED'");
    assertThat(constraintDef("trust_investment_interest_investment_fkey"))
        .contains("FOREIGN KEY (investment_id)")
        .contains("trust_investments(id)");
    assertThat(constraintDef("trust_investment_interest_net_txn_fkey"))
        .contains("FOREIGN KEY (net_transaction_id)")
        .contains("trust_transactions(id)");
    assertThat(constraintDef("trust_investment_interest_fund_txn_fkey"))
        .contains("FOREIGN KEY (fund_share_transaction_id)")
        .contains("trust_transactions(id)");
    assertThat(constraintDef("trust_investment_interest_line_fkey"))
        .contains("FOREIGN KEY (bank_statement_line_id)")
        .contains("bank_statement_lines(id)");
    assertThat(constraintDef("trust_investment_interest_recorded_by_fkey"))
        .contains("FOREIGN KEY (recorded_by)")
        .contains("members(id)");
    assertThat(
            text(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = '%s'"
                    + " AND indexname = 'idx_invest_interest_investment'"))
        .contains("(investment_id, entry_date)");
    // No ON DELETE CASCADE (house rule)
    assertThat(
            texts(
                "SELECT c.conname FROM pg_constraint c JOIN pg_namespace n ON n.oid ="
                    + " c.connamespace JOIN pg_class t ON t.oid = c.conrelid WHERE n.nspname ="
                    + " '%s' AND t.relname = 'trust_investment_interest' AND c.confdeltype = 'c'"))
        .isEmpty();
  }

  @Test
  void theChecksRefuseTheBadShapes_andAcceptTheGoodOnes() throws Exception {
    // gross must equal fund + net
    assertCheckRefused(entry("100.00", "5.00", "94.00", "NET", "NULL", "POSTED"));
    // the amounts' signs
    assertCheckRefused(entry("0.00", "0.00", "0.00", "NET", "NULL", "POSTED"));
    assertCheckRefused(entry("100.00", "-5.00", "105.00", "NET", "NULL", "POSTED"));
    assertCheckRefused(entry("5.00", "5.00", "0.00", "NET", "NULL", "POSTED"));
    // GROSS needs its Fund-share row; NET has none
    assertCheckRefused(entry("100.00", "5.00", "95.00", "GROSS", "NULL", "POSTED"));
    assertCheckRefused(entry("100.00", "5.00", "95.00", "NET", "'%deposit'", "POSTED"));
    // unknown presentation / status
    assertCheckRefused(entry("100.00", "5.00", "95.00", "HALF", "NULL", "POSTED"));
    assertCheckRefused(entry("100.00", "5.00", "95.00", "NET", "NULL", "VOID"));
    // the 5% is NOT a CHECK: a share the service would refuse is a valid row shape
    exec(entry("100.00", "7.00", "93.00", "NET", "NULL", "POSTED"));
    exec(entry("400.00", "20.00", "380.00", "GROSS", "'%deposit'", "REVERSED"));
    exec(entry("0.05", "0.00", "0.05", "NET", "NULL", "POSTED"));
    // an entry names a real investment
    assertThatThrownBy(
            () ->
                exec(
                    entry("100.00", "5.00", "95.00", "NET", "NULL", "POSTED")
                        .replace("'%investment'", "gen_random_uuid()")))
        .isInstanceOfSatisfying(
            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(FK_VIOLATION));
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

  /** An entry for the seeded investment, its INV_INTEREST row and the given SQL literals. */
  private static String entry(
      String gross,
      String fund,
      String net,
      String presentation,
      String fundTransaction,
      String status) {
    return """
        INSERT INTO "%%s".trust_investment_interest (investment_id, entry_date, gross_amount,
            fund_share_amount, net_amount, presentation, net_transaction_id,
            fund_share_transaction_id, status, recorded_by)
        VALUES ('%%investment', DATE '2025-02-28', %1$s, %2$s, %3$s, '%4$s', '%%netRow', %5$s,
                '%6$s', '%%owner')
        """
        .formatted(gross, fund, net, presentation, fundTransaction, status);
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

  /** Refused by a CHECK (SQLState 23514) on {@code trust_investment_interest}. */
  private void assertCheckRefused(String sql) {
    assertThatThrownBy(() -> exec(sql))
        .as("refused: %s", sql)
        .isInstanceOfSatisfying(
            SQLException.class,
            e -> {
              assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLATION);
              assertThat(e.getMessage()).contains("chk_invest_interest_");
            });
  }

  /** The seeded trust rows, every column the migration must not change. */
  private List<String> trustRows() throws Exception {
    return texts(
        "SELECT id, transaction_type, amount, customer_id, project_id, reference, status, ledger,"
            + " cashbook_effect, ledger_effect, COALESCE(investment_id::text, '-')"
            + " FROM \"%s\".trust_transactions ORDER BY id");
  }

  /** Each account's cashbook and its ledgers' totals. */
  private List<String> balances() throws Exception {
    return texts(
        "SELECT trust_account_id, SUM(cashbook_effect),"
            + " SUM(ledger_effect) FILTER (WHERE ledger = 'CLIENT'),"
            + " SUM(ledger_effect) FILTER (WHERE ledger = 'CLIENT_INVESTMENT')"
            + " FROM \"%s\".trust_transactions WHERE posted_at IS NOT NULL"
            + " GROUP BY trust_account_id ORDER BY trust_account_id");
  }

  /** The tables' columns, constraints and indexes — what a second run must not change. */
  private List<String> fingerprint() throws Exception {
    var rows = new ArrayList<String>();
    for (var table : List.of("trust_investment_interest", "trust_investments")) {
      rows.addAll(
          texts(
              "SELECT column_name || '|' || data_type || '|' || is_nullable"
                  + " FROM information_schema.columns WHERE table_schema = '%s'"
                  + " AND table_name = '"
                  + table
                  + "' ORDER BY column_name"));
      rows.addAll(
          texts(
              "SELECT c.conname || '|' || pg_get_constraintdef(c.oid) FROM pg_constraint c"
                  + " JOIN pg_namespace n ON n.oid = c.connamespace JOIN pg_class t"
                  + " ON t.oid = c.conrelid WHERE n.nspname = '%s' AND t.relname = '"
                  + table
                  + "' ORDER BY c.conname"));
      rows.addAll(
          texts(
              "SELECT indexname || '|' || indexdef FROM pg_indexes WHERE schemaname = '%s'"
                  + " AND tablename = '"
                  + table
                  + "' ORDER BY indexname"));
    }
    rows.addAll(texts("SELECT id FROM \"%s\".trust_investment_interest ORDER BY id"));
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
