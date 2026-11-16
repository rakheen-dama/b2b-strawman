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
 * Phase 92 slice 649B (task 649.5, architecture §16.7.3, ADR-378): tenant {@code V17} turns the
 * old-model reversals into contra entries. A scratch tenant schema is migrated to V16, seeded with
 * V16-shaped rows (effects explicit): reversed originals and their reversals at zero effect (a
 * payment and a transfer pair), a rejected and a pending credit reversal, an approved credit
 * reversal that never took effect (an H-a row) and a posted reversal with no original. Figures are
 * captured, then the schema is migrated to V17 — pinned (T17), so a later slice's migration cannot
 * change these expectations. Net balances are unchanged; as-at history is restored.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustReversalContraMigrationTest {

  /** The contra-reversal migration (T17: pinned, not "latest"). */
  private static final String CONTRA_VERSION = "17";

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V17__phase92_trust_reversal_contra.sql";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_trust_reversal_contra_scratch");

  /** Placeholders, longest first: a key that prefixes another must be replaced after it. */
  private static final Map<String, String> IDS = new LinkedHashMap<>();

  static {
    IDS.put("%owner", "00000000-0000-0000-0000-000000000001");
    IDS.put("%clientA", "00000000-0000-0000-0000-0000000000a1");
    IDS.put("%clientB", "00000000-0000-0000-0000-0000000000a2");
    IDS.put("%clientC", "00000000-0000-0000-0000-0000000000a3");
    IDS.put("%matter", "00000000-0000-0000-0000-0000000000b1");
    IDS.put("%account2", "00000000-0000-0000-0000-0000000000c2");
    IDS.put("%account1", "00000000-0000-0000-0000-0000000000c1");
    IDS.put("%depARevPend", "00000000-0000-0000-0000-0000000000d9");
    IDS.put("%depBRevRej", "00000000-0000-0000-0000-0000000000d8");
    IDS.put("%depA", "00000000-0000-0000-0000-0000000000d1");
    IDS.put("%depB", "00000000-0000-0000-0000-0000000000d2");
    IDS.put("%payRevR", "00000000-0000-0000-0000-0000000000d4");
    IDS.put("%payRev", "00000000-0000-0000-0000-0000000000d3");
    IDS.put("%trOutR", "00000000-0000-0000-0000-0000000000d7");
    IDS.put("%trOut", "00000000-0000-0000-0000-0000000000d5");
    IDS.put("%trInR", "00000000-0000-0000-0000-0000000000da");
    IDS.put("%trIn", "00000000-0000-0000-0000-0000000000d6");
    IDS.put("%depCR", "00000000-0000-0000-0000-0000000000e2");
    IDS.put("%depC", "00000000-0000-0000-0000-0000000000e1");
    IDS.put("%orphan", "00000000-0000-0000-0000-0000000000e3");
  }

  /** The cashbook of an account as at a date ({@code DATE '…'} literal), over posted rows. */
  private static final String CASHBOOK_SQL =
      """
      SELECT COALESCE(SUM(t.cashbook_effect), 0) FROM "%s".trust_transactions t
      WHERE t.trust_account_id = '%acc' AND t.posted_at IS NOT NULL AND t.transaction_date <= %asAt
      """;

  /** Each client's ledger on an account as at a date. */
  private static final String CLIENTS_SQL =
      """
      SELECT t.customer_id::text, COALESCE(SUM(t.ledger_effect), 0)
      FROM "%s".trust_transactions t
      WHERE t.trust_account_id = '%acc' AND t.ledger = 'CLIENT' AND t.posted_at IS NOT NULL
        AND t.transaction_date <= %asAt
      GROUP BY t.customer_id
      """;

  private static final String TODAY = "DATE '2099-12-31'";

  private final DataSource migrationDataSource;

  private BigDecimal cashbookBefore;
  private final Map<String, BigDecimal> clientsBefore = new HashMap<>();
  private BigDecimal cashbookOn10MarchBefore;
  private final Map<String, BigDecimal> clientsOn10MarchBefore = new HashMap<>();

  @Autowired
  TrustReversalContraMigrationTest(
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV16() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("16")).load().migrate();
    seedV16Shapes();

    cashbookBefore = cashbook("%account1", TODAY);
    clientsBefore.putAll(clients("%account1", TODAY));
    cashbookOn10MarchBefore = cashbook("%account1", "DATE '2025-03-10'");
    clientsOn10MarchBefore.putAll(clients("%account1", "DATE '2025-03-10'"));

    tenantFlyway().target(MigrationVersion.fromVersion(CONTRA_VERSION)).load().migrate();
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  /** The V16 shapes, with raw SQL and explicit effect columns (what 646A wrote). */
  private void seedV16Shapes() throws Exception {
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%owner', 'user_trc', 'trc@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".customers (id, name, email, customer_type, lifecycle_status, created_by)
        VALUES ('%clientA', 'Client A', 'a@trc.test', 'INDIVIDUAL', 'ACTIVE', '%owner'),
               ('%clientB', 'Client B', 'b@trc.test', 'INDIVIDUAL', 'ACTIVE', '%owner'),
               ('%clientC', 'Client C', 'c@trc.test', 'INDIVIDUAL', 'ACTIVE', '%owner');
        INSERT INTO "%s".projects (id, name, created_by) VALUES ('%matter', 'Matter', '%owner');
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date)
        VALUES ('%account1', 'Trust 1', 'FNB', '250655', '62000000011', 'GENERAL', true, false,
                'ACTIVE', DATE '2025-01-01'),
               ('%account2', 'Trust 2', 'FNB', '250655', '62000000012', 'GENERAL', false, false,
                'ACTIVE', DATE '2025-01-01');
        """);

    // Account 1 — deposits, posted with their effects
    txn(
        "%depA",
        "%account1",
        "DEPOSIT",
        "10000.00",
        "%clientA",
        "RECORDED",
        "10000",
        "10000",
        true,
        "2025-03-01");
    txn(
        "%depB",
        "%account1",
        "DEPOSIT",
        "3000.00",
        "%clientB",
        "RECORDED",
        "3000",
        "3000",
        true,
        "2025-03-02");
    // A debit reversal made under the old model: the original REVERSED and zeroed, the REVERSAL
    // posted at zero effect, dated later.
    txn(
        "%payRev",
        "%account1", "PAYMENT", "2000.00", "%clientA", "REVERSED", "0", "0", true, "2025-03-05");
    txn(
        "%payRevR",
        "%account1", "REVERSAL", "2000.00", "%clientA", "RECORDED", "0", "0", true, "2025-03-20");
    link("%payRevR", "%payRev");
    // A transfer A → B reversed under the old model (both legs)
    txn(
        "%trOut",
        "%account1",
        "TRANSFER_OUT",
        "1500.00",
        "%clientA",
        "REVERSED",
        "0",
        "0",
        true,
        "2025-03-06");
    txn(
        "%trIn",
        "%account1",
        "TRANSFER_IN",
        "1500.00",
        "%clientB",
        "REVERSED",
        "0",
        "0",
        true,
        "2025-03-06");
    txn(
        "%trOutR",
        "%account1", "REVERSAL", "1500.00", "%clientA", "RECORDED", "0", "0", true, "2025-03-21");
    txn(
        "%trInR",
        "%account1", "REVERSAL", "1500.00", "%clientB", "RECORDED", "0", "0", true, "2025-03-21");
    link("%trOutR", "%trOut");
    link("%trInR", "%trIn");
    // A rejected and a pending credit reversal (unposted): untouched
    txn(
        "%depBRevRej",
        "%account1", "REVERSAL", "3000.00", "%clientB", "REJECTED", "0", "0", false, "2025-03-22");
    exec("UPDATE \"%s\".trust_transactions SET reversal_of = '%depB' WHERE id = '%depBRevRej'");
    txn(
        "%depARevPend",
        "%account1",
        "REVERSAL",
        "10000.00",
        "%clientA",
        "AWAITING_APPROVAL",
        "0",
        "0",
        false,
        "2025-03-23");
    exec("UPDATE \"%s\".trust_transactions SET reversal_of = '%depA' WHERE id = '%depARevPend'");

    // Account 2 — an H-a row: the credit reversal was approved (posted at zero effect) but the
    // original deposit was never marked; and a posted REVERSAL with no original.
    txn(
        "%depC",
        "%account2",
        "DEPOSIT",
        "5000.00",
        "%clientC",
        "APPROVED",
        "5000",
        "5000",
        true,
        "2025-04-01");
    txn(
        "%depCR",
        "%account2", "REVERSAL", "5000.00", "%clientC", "APPROVED", "0", "0", true, "2025-04-10");
    exec("UPDATE \"%s\".trust_transactions SET reversal_of = '%depC' WHERE id = '%depCR'");
    txn(
        "%orphan",
        "%account2", "REVERSAL", "700.00", "%clientC", "RECORDED", "0", "0", true, "2025-04-12");
  }

  /** One V16-shape trust transaction on the client ledger. */
  private void txn(
      String id,
      String account,
      String type,
      String amount,
      String customer,
      String status,
      String cashbookEffect,
      String ledgerEffect,
      boolean posted,
      String date)
      throws Exception {
    exec(
        """
        INSERT INTO "%s".trust_transactions (id, trust_account_id, transaction_type, amount,
                                             customer_id, project_id, reference,
                                             transaction_date, status, recorded_by, created_at,
                                             ledger, cashbook_effect, ledger_effect, posted_at)
        VALUES ('%id', '%acc', '%type', %amount, '%customer', '%matter', 'REF-%type',
                DATE '%date', '%status', '%owner', TIMESTAMPTZ '%date 10:00:00+02',
                'CLIENT', %cashbook, %ledgerEffect, %postedAt)
        """
            .replace("%id", id)
            .replace("%acc", account)
            .replace("%type", type)
            .replace("%amount", amount)
            .replace("%customer", customer)
            .replace("%status", status)
            .replace("%cashbook", cashbookEffect)
            .replace("%ledgerEffect", ledgerEffect)
            .replace("%postedAt", posted ? "TIMESTAMPTZ '%date 10:00:00+02'" : "NULL")
            .replace("%date", date));
  }

  /** The old model's links: the reversal points at its original, the original at its reversal. */
  private void link(String reversal, String original) throws Exception {
    exec(
        ("UPDATE \"%s\".trust_transactions SET reversal_of = '%o' WHERE id = '%r';"
                + " UPDATE \"%s\".trust_transactions SET reversed_by_id = '%r' WHERE id = '%o';")
            .replace("%o", original)
            .replace("%r", reversal));
  }

  // --- 1. net balances are unchanged ---

  @Test
  void netBalances_areUnchanged() throws Exception {
    assertThat(cashbookBefore).isEqualByComparingTo("13000.00");
    assertThat(cashbook("%account1", TODAY)).isEqualByComparingTo(cashbookBefore);
    var clients = clients("%account1", TODAY);
    assertThat(clients).hasSameSizeAs(clientsBefore);
    clientsBefore.forEach(
        (client, before) ->
            assertThat(clients.get(client)).as("client %s", client).isEqualByComparingTo(before));
    assertThat(clients.get(id("%clientA"))).isEqualByComparingTo("10000.00");
    assertThat(clients.get(id("%clientB"))).isEqualByComparingTo("3000.00");
  }

  // --- 2. as-at history is restored ---

  @Test
  void asAtHistory_isRestored() throws Exception {
    // Before V17 the reversed originals had dropped out of every earlier as-at balance.
    assertThat(cashbookOn10MarchBefore).isEqualByComparingTo("13000.00");
    assertThat(clientsOn10MarchBefore.get(id("%clientA"))).isEqualByComparingTo("10000.00");
    assertThat(clientsOn10MarchBefore.get(id("%clientB"))).isEqualByComparingTo("3000.00");

    // Between the originals and their reversals: the payment and the transfer count again.
    assertThat(cashbook("%account1", "DATE '2025-03-10'")).isEqualByComparingTo("11000.00");
    var on10March = clients("%account1", "DATE '2025-03-10'");
    assertThat(on10March.get(id("%clientA"))).isEqualByComparingTo("6500.00");
    assertThat(on10March.get(id("%clientB"))).isEqualByComparingTo("4500.00");
    // The payment's contra (20 March) lands before the transfer's (21 March).
    assertThat(cashbook("%account1", "DATE '2025-03-20'")).isEqualByComparingTo("13000.00");
    var on20March = clients("%account1", "DATE '2025-03-20'");
    assertThat(on20March.get(id("%clientA"))).isEqualByComparingTo("8500.00");
    assertThat(on20March.get(id("%clientB"))).isEqualByComparingTo("4500.00");
    var on21March = clients("%account1", "DATE '2025-03-21'");
    assertThat(on21March.get(id("%clientA"))).isEqualByComparingTo("10000.00");
    assertThat(on21March.get(id("%clientB"))).isEqualByComparingTo("3000.00");
  }

  // --- 3. effects: originals keep theirs, contras carry the negation ---

  @Test
  void reversedOriginals_keepTheirEffects_andContrasCarryTheNegation() throws Exception {
    assertEffects("%payRev", "REVERSED", "-2000", "-2000", true);
    assertEffects("%payRevR", "RECORDED", "2000", "2000", true);
    assertEffects("%trOut", "REVERSED", "0", "-1500", true);
    assertEffects("%trIn", "REVERSED", "0", "1500", true);
    assertEffects("%trOutR", "RECORDED", "0", "1500", true);
    assertEffects("%trInR", "RECORDED", "0", "-1500", true);
    assertEffects("%depA", "RECORDED", "10000", "10000", true);
    assertEffects("%depB", "RECORDED", "3000", "3000", true);
    assertThat(
            count(
                "SELECT count(*) FROM \"%s\".trust_transactions WHERE trust_account_id ="
                    + " '%account1' AND ledger <> 'CLIENT'"))
        .isZero();
  }

  @Test
  void pendingAndRejectedReversals_areUntouched() throws Exception {
    assertEffects("%depBRevRej", "REJECTED", "0", "0", false);
    assertEffects("%depARevPend", "AWAITING_APPROVAL", "0", "0", false);
    assertThat(
            text("SELECT reversed_by_id::text FROM \"%s\".trust_transactions WHERE id = '%depA'"))
        .isEqualTo("NULL");
    assertThat(
            text("SELECT reversed_by_id::text FROM \"%s\".trust_transactions WHERE id = '%depB'"))
        .isEqualTo("NULL");
  }

  // --- 4. the H-a row takes effect; the orphan is unposted ---

  @Test
  void anApprovedCreditReversalThatNeverTookEffect_nowMarksItsOriginal() throws Exception {
    assertEffects("%depC", "REVERSED", "5000", "5000", true);
    assertThat(
            text("SELECT reversed_by_id::text FROM \"%s\".trust_transactions WHERE id = '%depC'"))
        .isEqualTo(id("%depCR"));
    assertEffects("%depCR", "APPROVED", "-5000", "-5000", true);
    assertThat(cashbook("%account2", TODAY)).isEqualByComparingTo("0.00");
    assertThat(clients("%account2", TODAY).get(id("%clientC"))).isEqualByComparingTo("0.00");
    // The deposit still counts before its reversal's date.
    assertThat(cashbook("%account2", "DATE '2025-04-05'")).isEqualByComparingTo("5000.00");
  }

  @Test
  void aPostedReversalWithNoOriginal_isUnposted() throws Exception {
    assertEffects("%orphan", "REJECTED", "0", "0", false);
    assertThat(text("SELECT rejection_reason FROM \"%s\".trust_transactions WHERE id = '%orphan'"))
        .isEqualTo("Phase 92 migration: reversal with no original");
  }

  // --- 5. EQ-6 in SQL: every posted REVERSAL is the negation of its original ---

  @Test
  void everyPostedReversal_isTheNegationOfItsOriginal() throws Exception {
    assertThat(
            count(
                """
                SELECT count(*) FROM "%s".trust_transactions r
                LEFT JOIN "%s".trust_transactions o ON o.id = r.reversal_of
                WHERE r.transaction_type = 'REVERSAL' AND r.posted_at IS NOT NULL
                  AND (o.id IS NULL OR r.ledger <> o.ledger
                       OR r.cashbook_effect <> -o.cashbook_effect
                       OR r.ledger_effect <> -o.ledger_effect
                       OR o.status <> 'REVERSED' OR o.reversed_by_id <> r.id)
                """))
        .isZero();
    assertThat(
            count(
                "SELECT count(*) FROM \"%s\".trust_transactions WHERE transaction_type ="
                    + " 'REVERSAL' AND posted_at IS NOT NULL"))
        .isEqualTo(4);
  }

  // --- 6. the tight row-shape CHECK ---

  @Test
  void postingCheck_isTight() throws Exception {
    assertThat(constraintDef("chk_trust_txn_posting"))
        .contains("posted_at")
        .contains("abs(ledger_effect) = amount")
        .doesNotContain("ARRAY[(0)");
  }

  @Test
  void aPostedRowThatMovesNothing_isRefused() {
    assertThatThrownBy(
            () ->
                exec(
                    """
                    INSERT INTO "%s".trust_transactions (trust_account_id, transaction_type,
                        amount, customer_id, reference, transaction_date, status, recorded_by,
                        ledger, cashbook_effect, ledger_effect, posted_at)
                    VALUES ('%account1', 'REVERSAL', 1.00, '%clientA', 'REF-ZERO',
                            DATE '2025-03-24', 'RECORDED', '%owner', 'CLIENT', 0, 0, now())
                    """))
        .isInstanceOfSatisfying(
            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
  }

  // --- 7. idempotent ---

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

  /** Status, links and effects of every row — what a second run must leave untouched. */
  private List<String> fingerprint() throws Exception {
    return texts(
        "SELECT id || ':' || status || ':' || ledger || ':' || cashbook_effect || ':'"
            + " || ledger_effect || ':' || COALESCE(posted_at::text, 'NULL') || ':'"
            + " || COALESCE(reversed_by_id::text, 'NULL') || ':'"
            + " || COALESCE(rejection_reason, 'NULL')"
            + " FROM \"%s\".trust_transactions ORDER BY id");
  }

  private void assertEffects(
      String id, String status, String cashbook, String ledger, boolean posted) throws Exception {
    var row =
        texts(
            "SELECT status, cashbook_effect::text, ledger_effect::text,"
                + " (posted_at IS NOT NULL)::text FROM \"%s\".trust_transactions WHERE id = '"
                + id
                + "'");
    assertThat(row).as("row %s", id).hasSize(1);
    var parts = row.getFirst().split("\\|");
    assertThat(parts[0]).as("%s status", id).isEqualTo(status);
    assertThat(new BigDecimal(parts[1]))
        .as("%s cashbook_effect", id)
        .isEqualByComparingTo(cashbook);
    assertThat(new BigDecimal(parts[2])).as("%s ledger_effect", id).isEqualByComparingTo(ledger);
    assertThat(Boolean.parseBoolean(parts[3])).as("%s posted", id).isEqualTo(posted);
  }

  private BigDecimal cashbook(String account, String asAt) throws Exception {
    return new BigDecimal(text(CASHBOOK_SQL.replace("%acc", account).replace("%asAt", asAt)));
  }

  private Map<String, BigDecimal> clients(String account, String asAt) throws Exception {
    var result = new HashMap<String, BigDecimal>();
    for (var row : texts(CLIENTS_SQL.replace("%acc", account).replace("%asAt", asAt))) {
      var parts = row.split("\\|");
      result.put(parts[0], new BigDecimal(parts[1]));
    }
    return result;
  }

  private String constraintDef(String name) throws Exception {
    return text(
        "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c JOIN pg_namespace n"
            + " ON n.oid = c.connamespace WHERE c.conname = '"
            + name
            + "' AND n.nspname = '%s'");
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

  private static String id(String placeholder) {
    return IDS.get(placeholder);
  }

  /** Replaces the id placeholders (longest first), then {@code %s} with the scratch schema. */
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
