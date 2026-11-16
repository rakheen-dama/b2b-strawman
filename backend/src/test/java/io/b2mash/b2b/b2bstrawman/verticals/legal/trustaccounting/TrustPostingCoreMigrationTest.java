package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Phase 92 slice 646A (task 646.4, architecture §16.7.2, ADR-378): tenant {@code V16} gives every
 * posted trust transaction its own effects (HEAD semantics), deletes the calculated interest run's
 * postings and their links, adds {@code chk_trust_txn_posting}, cuts {@code chk_trust_txn_type} to
 * the seven kept types and turns {@code client_ledger_cards} into a lock anchor. A scratch tenant
 * schema is migrated to V15, seeded with the old shapes (a posted interest run with allocation,
 * allocation line, a reversed interest credit and a bank line matched to the LPFF row; approved,
 * awaiting, rejected, reversed and transfer rows; card-less seeder rows and an old-shape card), the
 * HEAD figures are captured with the old sign tables, then migrated to V16 — pinned (T17), so a
 * later slice's migration cannot change these expectations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustPostingCoreMigrationTest {

  /** The posting-core migration (T17: pinned, not "latest"). */
  private static final String POSTING_CORE_VERSION = "16";

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V16__phase92_trust_posting_core.sql";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_trust_posting_core_scratch");

  private static final Map<String, String> IDS = new LinkedHashMap<>();

  static {
    IDS.put("%owner", "00000000-0000-0000-0000-000000000001");
    IDS.put("%clientA", "00000000-0000-0000-0000-0000000000a1");
    IDS.put("%clientB", "00000000-0000-0000-0000-0000000000a2");
    IDS.put("%matter", "00000000-0000-0000-0000-0000000000b1");
    IDS.put("%account", "00000000-0000-0000-0000-0000000000c1");
    IDS.put("%rate", "00000000-0000-0000-0000-0000000000c2");
    IDS.put("%depA", "00000000-0000-0000-0000-0000000000d1");
    IDS.put("%depB", "00000000-0000-0000-0000-0000000000d2");
    IDS.put("%payApproved", "00000000-0000-0000-0000-0000000000d3");
    IDS.put("%payAwaiting", "00000000-0000-0000-0000-0000000000d4");
    IDS.put("%refundRejected", "00000000-0000-0000-0000-0000000000d5");
    IDS.put("%payReversed", "00000000-0000-0000-0000-0000000000d6");
    IDS.put("%payReversal", "00000000-0000-0000-0000-0000000000d7");
    IDS.put("%transferOut", "00000000-0000-0000-0000-0000000000d8");
    IDS.put("%transferIn", "00000000-0000-0000-0000-0000000000d9");
    IDS.put("%interestCredit", "00000000-0000-0000-0000-0000000000e1");
    IDS.put("%interestLpff", "00000000-0000-0000-0000-0000000000e2");
    IDS.put("%interestReversal", "00000000-0000-0000-0000-0000000000e3");
    IDS.put("%run", "00000000-0000-0000-0000-0000000000f1");
    IDS.put("%allocation", "00000000-0000-0000-0000-0000000000f2");
    IDS.put("%line", "00000000-0000-0000-0000-0000000000f3");
    IDS.put("%statement", "00000000-0000-0000-0000-0000000000f4");
    IDS.put("%bankLine", "00000000-0000-0000-0000-0000000000f5");
  }

  /**
   * HEAD cashbook sign table (the deleted {@code calculateCashbookBalance}) over the rows V16 keeps
   * — the interest rows are deleted by design, so they are left out of the comparison.
   */
  private static final String HEAD_CASHBOOK_SQL =
      """
      SELECT COALESCE(SUM(CASE
          WHEN t.transaction_type = 'DEPOSIT' THEN t.amount
          WHEN t.transaction_type IN ('PAYMENT', 'FEE_TRANSFER', 'REFUND') THEN -t.amount
          ELSE 0 END), 0)
      FROM "%s".trust_transactions t
      WHERE t.trust_account_id = '%account' AND t.status IN ('RECORDED', 'APPROVED')
        AND t.transaction_type NOT IN ('INTEREST_CREDIT', 'INTEREST_LPFF')
      """;

  /** HEAD client sign table per client, over the rows V16 keeps. */
  private static final String HEAD_CLIENT_SQL =
      """
      SELECT t.customer_id::text, COALESCE(SUM(CASE
          WHEN t.transaction_type IN ('DEPOSIT', 'TRANSFER_IN') THEN t.amount
          WHEN t.transaction_type IN ('PAYMENT', 'TRANSFER_OUT', 'FEE_TRANSFER', 'REFUND')
            THEN -t.amount
          ELSE 0 END), 0)
      FROM "%s".trust_transactions t
      WHERE t.trust_account_id = '%account' AND t.customer_id IS NOT NULL
        AND t.status IN ('RECORDED', 'APPROVED')
        AND t.transaction_type NOT IN ('INTEREST_CREDIT', 'INTEREST_LPFF')
      GROUP BY t.customer_id
      """;

  private static final String DERIVED_CASHBOOK_SQL =
      """
      SELECT COALESCE(SUM(t.cashbook_effect), 0) FROM "%s".trust_transactions t
      WHERE t.trust_account_id = '%account' AND t.posted_at IS NOT NULL
      """;

  private static final String DERIVED_CLIENT_SQL =
      """
      SELECT t.customer_id::text, COALESCE(SUM(t.ledger_effect), 0)
      FROM "%s".trust_transactions t
      WHERE t.trust_account_id = '%account' AND t.customer_id IS NOT NULL
        AND t.ledger = 'CLIENT' AND t.posted_at IS NOT NULL
      GROUP BY t.customer_id
      """;

  private final DataSource migrationDataSource;

  private BigDecimal headCashbook;
  private final Map<String, BigDecimal> headClients = new HashMap<>();

  @Autowired
  TrustPostingCoreMigrationTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV15() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("15")).load().migrate();
    seedOldShapes();

    headCashbook = money(HEAD_CASHBOOK_SQL);
    headClients.putAll(moneyByKey(HEAD_CLIENT_SQL));

    tenantFlyway().target(MigrationVersion.fromVersion(POSTING_CORE_VERSION)).load().migrate();
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  /** The old (V15) shapes, with raw SQL — column names from the V1 baseline and V12. */
  private void seedOldShapes() throws Exception {
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%owner', 'user_tpc', 'tpc@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".customers (id, name, email, customer_type, lifecycle_status, created_by)
        VALUES ('%clientA', 'Client A', 'a@tpc.test', 'INDIVIDUAL', 'ACTIVE', '%owner'),
               ('%clientB', 'Client B', 'b@tpc.test', 'INDIVIDUAL', 'ACTIVE', '%owner');
        INSERT INTO "%s".projects (id, name, created_by) VALUES ('%matter', 'Matter', '%owner');
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date)
        VALUES ('%account', 'Trust', 'FNB', '250655', '62000000001', 'GENERAL', true, false,
                'ACTIVE', DATE '2025-01-01');
        INSERT INTO "%s".lpff_rates (id, trust_account_id, effective_from, rate_percent,
                                     lpff_share_percent)
        VALUES ('%rate', '%account', DATE '2025-01-01', 0.0500, 0.7500);
        """);
    // Deposits as the demo seeder wrote them: RECORDED, no client_ledger_cards row.
    txn("%depA", "DEPOSIT", "10000.00", "'%clientA'", "RECORDED", "NULL");
    txn("%depB", "DEPOSIT", "5000.00", "'%clientB'", "RECORDED", "NULL");
    txn("%payApproved", "PAYMENT", "2000.00", "'%clientA'", "APPROVED", "TIMESTAMPTZ '2025-03-02'");
    txn("%payAwaiting", "PAYMENT", "500.00", "'%clientA'", "AWAITING_APPROVAL", "NULL");
    txn("%refundRejected", "REFUND", "300.00", "'%clientB'", "REJECTED", "NULL");
    // A debit reversal pair: the original REVERSED, the REVERSAL RECORDED
    txn("%payReversed", "PAYMENT", "1000.00", "'%clientA'", "REVERSED", "TIMESTAMPTZ '2025-03-03'");
    txn("%payReversal", "REVERSAL", "1000.00", "'%clientA'", "RECORDED", "NULL");
    exec(
        """
        UPDATE "%s".trust_transactions SET reversal_of = '%payReversed' WHERE id = '%payReversal';
        UPDATE "%s".trust_transactions SET reversed_by_id = '%payReversal' WHERE id = '%payReversed';
        """);
    // A transfer pair A → B
    txn("%transferOut", "TRANSFER_OUT", "1500.00", "'%clientA'", "RECORDED", "NULL");
    txn("%transferIn", "TRANSFER_IN", "1500.00", "'%clientB'", "RECORDED", "NULL");
    // A posted interest run: the credit (reversed), the LPFF share, allocation + line, bank match
    txn("%interestCredit", "INTEREST_CREDIT", "12.34", "'%clientA'", "REVERSED", "NULL");
    txn("%interestLpff", "INTEREST_LPFF", "5.00", "NULL", "RECORDED", "NULL");
    txn("%interestReversal", "REVERSAL", "12.34", "'%clientA'", "RECORDED", "NULL");
    exec(
        """
        UPDATE "%s".trust_transactions SET reversal_of = '%interestCredit'
         WHERE id = '%interestReversal';
        UPDATE "%s".trust_transactions SET reversed_by_id = '%interestReversal'
         WHERE id = '%interestCredit';
        INSERT INTO "%s".interest_runs (id, trust_account_id, period_start, period_end, lpff_rate_id,
                                        total_interest, total_lpff_share, total_client_share,
                                        status, approved_by, posted_at, created_by)
        VALUES ('%run', '%account', DATE '2025-03-01', DATE '2025-03-31', '%rate',
                17.34, 5.00, 12.34, 'POSTED', '%owner', now(), '%owner');
        INSERT INTO "%s".interest_allocations (id, interest_run_id, customer_id,
                                               average_daily_balance, days_in_period,
                                               gross_interest, lpff_share, client_share,
                                               trust_transaction_id)
        VALUES ('%allocation', '%run', '%clientA', 6500.00, 31, 17.34, 5.00, 12.34,
                '%interestCredit');
        INSERT INTO "%s".interest_allocation_lines (id, interest_allocation_id, project_id,
                                                    balance_days, weight, client_share,
                                                    trust_transaction_id)
        VALUES ('%line', '%allocation', '%matter', 201500.00, 1, 12.34, '%interestCredit');
        INSERT INTO "%s".bank_statements (id, trust_account_id, period_start, period_end,
                                          opening_balance, closing_balance, file_key, file_name,
                                          format, line_count, matched_count, imported_by)
        VALUES ('%statement', '%account', DATE '2025-03-01', DATE '2025-03-31', 0, 13005.00,
                'k/stmt.csv', 'stmt.csv', 'CSV', 1, 1, '%owner');
        INSERT INTO "%s".bank_statement_lines (id, bank_statement_id, line_number,
                                               transaction_date, description, amount,
                                               match_status, trust_transaction_id,
                                               match_confidence)
        VALUES ('%bankLine', '%statement', 1, DATE '2025-03-31', 'LPFF interest', 5.00,
                'MANUALLY_MATCHED', '%interestLpff', 1.00);
        UPDATE "%s".trust_transactions SET bank_statement_line_id = '%bankLine'
         WHERE id = '%interestLpff';
        """);
    // The old card shape: client A has a stored, non-zero balance; client B has no card.
    exec(
        """
        INSERT INTO "%s".client_ledger_cards (trust_account_id, customer_id, balance,
                                              total_deposits, total_payments,
                                              last_transaction_date)
        VALUES ('%account', '%clientA', 999.00, 10000.00, 2000.00, DATE '2025-03-05');
        """);
  }

  /** One old-shape trust transaction (no effect columns exist yet), dated in March 2025. */
  private void txn(
      String id, String type, String amount, String customer, String status, String approvedAt)
      throws Exception {
    exec(
        """
        INSERT INTO "%s".trust_transactions (id, trust_account_id, transaction_type, amount,
                                             customer_id, project_id, reference,
                                             transaction_date, status, approved_by, approved_at,
                                             recorded_by, created_at)
        VALUES ('%id', '%account', '%type', %amount, %customer, %project, 'REF-%type',
                DATE '2025-03-05', '%status', %approvedBy, %approvedAt, '%owner',
                TIMESTAMPTZ '2025-03-05 10:00:00+02')
        """
            .replace("%id", id)
            .replace("%type", type)
            .replace("%amount", amount)
            .replace("%customer", customer)
            .replace("%project", "NULL".equals(customer) ? "NULL" : "'%matter'")
            .replace("%status", status)
            .replace("%approvedBy", "NULL".equals(approvedAt) ? "NULL" : "'%owner'")
            .replace("%approvedAt", approvedAt));
  }

  // --- 1. interest postings and their links are gone ---

  @Test
  void interestRowsAndTheirReversals_areDeleted() throws Exception {
    assertThat(
            count(
                "SELECT count(*) FROM \"%s\".trust_transactions"
                    + " WHERE transaction_type IN ('INTEREST_CREDIT', 'INTEREST_LPFF')"))
        .isZero();
    assertThat(
            count("SELECT count(*) FROM \"%s\".trust_transactions WHERE id = '%interestReversal'"))
        .as("the reversal of the interest credit")
        .isZero();
    // The other nine rows survive.
    assertThat(count("SELECT count(*) FROM \"%s\".trust_transactions")).isEqualTo(9);
  }

  @Test
  void interestLinks_areNulled_andThePostedRunReturnsToApproved() throws Exception {
    assertThat(
            text(
                "SELECT COALESCE(trust_transaction_id::text, 'NULL')"
                    + " FROM \"%s\".interest_allocations WHERE id = '%allocation'"))
        .isEqualTo("NULL");
    assertThat(
            text(
                "SELECT COALESCE(trust_transaction_id::text, 'NULL')"
                    + " FROM \"%s\".interest_allocation_lines WHERE id = '%line'"))
        .isEqualTo("NULL");
    assertThat(
            text(
                "SELECT status || '/' || COALESCE(posted_at::text, 'NULL')"
                    + " FROM \"%s\".interest_runs WHERE id = '%run'"))
        .isEqualTo("APPROVED/NULL");
    assertThat(
            text(
                "SELECT match_status || '/' || COALESCE(trust_transaction_id::text, 'NULL') || '/'"
                    + " || COALESCE(match_confidence::text, 'NULL')"
                    + " FROM \"%s\".bank_statement_lines WHERE id = '%bankLine'"))
        .isEqualTo("UNMATCHED/NULL/NULL");
    assertThat(count("SELECT matched_count FROM \"%s\".bank_statements WHERE id = '%statement'"))
        .isZero();
  }

  // --- 2. effects backfilled with HEAD semantics ---

  @Test
  void everyKeptRow_carriesItsHeadEffects() throws Exception {
    assertEffects("%depA", "10000.00", "10000.00", true);
    assertEffects("%depB", "5000.00", "5000.00", true);
    assertEffects("%payApproved", "-2000.00", "-2000.00", true);
    assertEffects("%payAwaiting", "0", "0", false);
    assertEffects("%refundRejected", "0", "0", false);
    // HEAD semantics (649B changes them): a REVERSED original and its REVERSAL count 0
    assertEffects("%payReversed", "0", "0", true);
    assertEffects("%payReversal", "0", "0", true);
    // Transfers never reach the bank
    assertEffects("%transferOut", "0", "-1500.00", true);
    assertEffects("%transferIn", "0", "1500.00", true);
    assertThat(count("SELECT count(*) FROM \"%s\".trust_transactions WHERE ledger <> 'CLIENT'"))
        .isZero();
    // posted_at = the final approval when there is one, else when the row was created
    assertThat(
            text(
                "SELECT (posted_at = approved_at)::text FROM \"%s\".trust_transactions"
                    + " WHERE id = '%payApproved'"))
        .isEqualTo("true");
    assertThat(
            text(
                "SELECT (posted_at = created_at)::text FROM \"%s\".trust_transactions"
                    + " WHERE id = '%depA'"))
        .isEqualTo("true");
  }

  @Test
  void derivedFigures_equalTheHeadFigures() throws Exception {
    assertThat(headCashbook).as("HEAD cashbook (sanity)").isEqualByComparingTo("13000.00");
    assertThat(money(DERIVED_CASHBOOK_SQL))
        .as("derived cashbook")
        .isEqualByComparingTo(headCashbook);

    var derived = moneyByKey(DERIVED_CLIENT_SQL);
    assertThat(derived.keySet()).isEqualTo(headClients.keySet());
    headClients.forEach(
        (client, head) ->
            assertThat(derived.get(client))
                .as("derived balance of client %s", client)
                .isEqualByComparingTo(head));
    assertThat(headClients.get(IDS.get("%clientA"))).isEqualByComparingTo("6500.00");
    assertThat(headClients.get(IDS.get("%clientB"))).isEqualByComparingTo("6500.00");
  }

  // --- 3. the card becomes a lock anchor ---

  @Test
  void everyClientWithRows_hasALockAnchor() throws Exception {
    assertThat(
            count(
                "SELECT count(*) FROM \"%s\".client_ledger_cards WHERE trust_account_id ="
                    + " '%account' AND customer_id IN ('%clientA', '%clientB')"))
        .isEqualTo(2);
  }

  @Test
  void cardColumnsAndBalanceIndex_areGone() throws Exception {
    assertThat(
            texts(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = '%s'"
                    + " AND table_name = 'client_ledger_cards'"))
        .containsExactlyInAnyOrder(
            "id", "trust_account_id", "customer_id", "created_at", "updated_at");
    assertThat(
            count(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = '%s'"
                    + " AND indexname = 'idx_client_ledger_balance'"))
        .isZero();
    assertThat(
            count(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = '%s'"
                    + " AND indexname = 'idx_trust_txn_ledger_sum'"))
        .isEqualTo(1);
  }

  // --- 4. constraints ---

  @Test
  void postingCheck_andTheSevenNameTypeCheck_exist() throws Exception {
    assertThat(constraintDef("chk_trust_txn_posting")).contains("posted_at");
    var typeCheck = constraintDef("chk_trust_txn_type");
    assertThat(typeCheck)
        .contains(
            "'DEPOSIT'",
            "'PAYMENT'",
            "'TRANSFER_IN'",
            "'TRANSFER_OUT'",
            "'FEE_TRANSFER'",
            "'REFUND'",
            "'REVERSAL'")
        .doesNotContain("INTEREST_CREDIT")
        .doesNotContain("INTEREST_LPFF");
  }

  @Test
  void aPostedStatusWithoutPostedAt_isRefused() {
    assertThatThrownBy(
            () ->
                exec(
                    """
                    INSERT INTO "%s".trust_transactions (trust_account_id, transaction_type,
                        amount, customer_id, reference, transaction_date, status, recorded_by)
                    VALUES ('%account', 'DEPOSIT', 1.00, '%clientA', 'REF-UNPOSTED',
                            DATE '2025-03-06', 'RECORDED', '%owner')
                    """))
        .isInstanceOfSatisfying(
            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
  }

  // --- 5. idempotent ---

  @Test
  void runningTheMigrationBodyTwice_isANoOp() throws Exception {
    var before = fingerprint();
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
    assertThat(fingerprint()).isEqualTo(before);
  }

  // --- helpers ---

  /** Counts, sums and the effect of every row — what a second run must leave untouched. */
  private List<String> fingerprint() throws Exception {
    var rows =
        new ArrayList<>(
            texts(
                "SELECT id || ':' || status || ':' || ledger || ':' || cashbook_effect || ':'"
                    + " || ledger_effect || ':' || COALESCE(posted_at::text, 'NULL')"
                    + " FROM \"%s\".trust_transactions ORDER BY id"));
    rows.add("anchors=" + count("SELECT count(*) FROM \"%s\".client_ledger_cards"));
    rows.add("runs=" + text("SELECT string_agg(status, ',') FROM \"%s\".interest_runs"));
    rows.add(
        "matched="
            + count("SELECT matched_count FROM \"%s\".bank_statements WHERE id = '%statement'"));
    return rows;
  }

  private void assertEffects(String id, String cashbook, String ledger, boolean posted)
      throws Exception {
    var row =
        texts(
            "SELECT cashbook_effect::text, ledger_effect::text, (posted_at IS NOT NULL)::text"
                + " FROM \"%s\".trust_transactions WHERE id = '"
                + id
                + "'");
    assertThat(row).as("row %s", id).hasSize(1);
    var parts = row.getFirst().split("\\|");
    assertThat(new BigDecimal(parts[0]))
        .as("%s cashbook_effect", id)
        .isEqualByComparingTo(cashbook);
    assertThat(new BigDecimal(parts[1])).as("%s ledger_effect", id).isEqualByComparingTo(ledger);
    assertThat(Boolean.parseBoolean(parts[2])).as("%s posted", id).isEqualTo(posted);
  }

  private String constraintDef(String name) throws Exception {
    return text(
        "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c JOIN pg_namespace n"
            + " ON n.oid = c.connamespace WHERE c.conname = '"
            + name
            + "' AND n.nspname = '%s'");
  }

  private BigDecimal money(String sql) throws Exception {
    return new BigDecimal(text(sql));
  }

  private Map<String, BigDecimal> moneyByKey(String sql) throws Exception {
    var result = new HashMap<String, BigDecimal>();
    for (var row : texts(sql)) {
      var parts = row.split("\\|");
      result.put(parts[0], new BigDecimal(parts[1]));
    }
    return result;
  }

  private long count(String sql) throws Exception {
    return Long.parseLong(text(sql));
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
