package io.b2mash.b2b.b2bstrawman.testutil;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.assertj.core.api.SoftAssertions;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Independent oracle for the trust equation (architecture §16.1.2, §16.9.1; ADR-377). It computes
 * every term with <b>its own SQL</b> through {@link JdbcTemplate} against the tenant schema, so a
 * mistake in a production balance query cannot certify itself; {@link #assertServicesAgree} then
 * checks the production services return the oracle's figures.
 *
 * <h2>Rules of use</h2>
 *
 * <ol>
 *   <li><b>A fresh trust account per test method.</b> The equation is per account, and a {@code
 *       PER_CLASS} tenant accumulates its siblings' rows. Create it with {@code isPrimary = false}
 *       (a tenant holds one primary GENERAL account).
 *   <li><b>Every posting test asserts through {@link #assertMoves}</b>, which checks the equation
 *       <i>and</i> the size of each move — a posting that balances by moving nothing would
 *       otherwise pass.
 *   <li><b>{@code assertFirmBalanced(today)} at the end of every harness-backed class</b>
 *       ({@code @AfterAll}). It is an invariant over the tenant, not a count, so it is safe over
 *       shared state. A test that deliberately unbalances an account does so in a separate tenant.
 *   <li>Call every method inside a tenant scope ({@link RequestScopes#TENANT_ID} bound); the schema
 *       is taken from it. {@link #assertServicesAgree} also needs the {@code trust_accounting}
 *       module enabled.
 *   <li>The harness reads <b>committed</b> data on its own connection: do not wrap a posting and
 *       its assertion in one outer transaction. Every {@code TrustTransactionService} method
 *       commits on return.
 *   <li>Post with dates on or before today ({@code LocalDate.now(clock)}). Never {@code
 *       LocalDate.now()} — the test JVM runs in UTC, the {@link Clock} bean in SA time.
 *   <li>Tests express every figure in harness terms ({@link BooksSnapshot}, {@link #clientLedger})
 *       and never read {@code client_ledger_cards} themselves: 646A replaces the internals below
 *       without changing a single expectation except the pins it closes (task 646.9).
 * </ol>
 *
 * <h2>Version 0 (slice 649A) — the HEAD model</h2>
 *
 * <p>Before the effect columns exist (646A), the oracle maps the terms onto the HEAD schema:
 *
 * <ul>
 *   <li>"Posted" = {@code status IN ('RECORDED','APPROVED')} (no {@code posted_at} yet).
 *   <li>{@code cashbook(A,D)} — the HEAD cashbook sign table ({@code
 *       TrustTransactionRepository.calculateCashbookBalance}: DEPOSIT, INTEREST_CREDIT +; PAYMENT,
 *       FEE_TRANSFER, REFUND, INTEREST_LPFF −; transfers and REVERSAL 0) over posted rows dated
 *       {@code <= D}.
 *   <li>{@code clientLedgers(A, today)} — the <b>stored</b> card total, {@code
 *       SUM(client_ledger_cards.balance)} for the account. This is the v0 comparison: cashbook
 *       against the stored cards.
 *   <li>{@code clientLedgers(A, D < today)} — cards have no history, so the HEAD client sign table
 *       (DEPOSIT, TRANSFER_IN, INTEREST_CREDIT +; PAYMENT, TRANSFER_OUT, FEE_TRANSFER, REFUND,
 *       INTEREST_LPFF −) summed over every client's posted rows dated {@code <= D}. Not exercised
 *       by 649A's paths.
 *   <li>{@code investmentCreditors}, {@code fundLedger}, {@code unidentified}, {@code interAccount}
 *       — zero: no such ledgers on HEAD (648A, 647A, 647B, 648A add them).
 *   <li>{@link #clientLedger} — one client's stored card balance, zero when the client has no card.
 *   <li>EQ-4 (itemised unidentified deposits, 647B), EQ-5 (per investment, 648A) and EQ-6 (every
 *       posted row's effects re-derived from the registry, 646A) are no-ops until their columns
 *       exist.
 *   <li>{@link #assertBankAgrees} — statement closing balance + HEAD outstanding deposits − HEAD
 *       outstanding payments (posted, unmatched, dated {@code <= periodEnd}) against {@code
 *       cashbook(A, periodEnd)}. First used by 646C.
 *   <li>{@link #assertServicesAgree} — {@code TrustTransactionService.getCashbookBalance} and
 *       {@code ClientLedgerService.getTotalTrustBalance}; the HEAD services have no as-at date, so
 *       v0 supports {@code asAt == today} only (646C / 649C replace them).
 * </ul>
 */
@Component
@Profile("test")
public final class TrustBooksHarness {

  private static final Pattern TENANT_SCHEMA = Pattern.compile("^tenant_[0-9a-f]{12}$");

  /**
   * HEAD cashbook sign table (TrustTransactionRepository.calculateCashbookBalance), as at a date.
   */
  private static final String CASHBOOK_SQL =
      """
      SELECT COALESCE(SUM(CASE
          WHEN t.transaction_type IN ('DEPOSIT', 'INTEREST_CREDIT') THEN t.amount
          WHEN t.transaction_type IN ('PAYMENT', 'FEE_TRANSFER', 'REFUND', 'INTEREST_LPFF')
            THEN -t.amount
          ELSE 0 END), 0)
      FROM "%s".trust_transactions t
      WHERE t.trust_account_id = ?
        AND t.status IN ('RECORDED', 'APPROVED')
        AND t.transaction_date <= ?
      """;

  /** Stored card total (ClientLedgerCardRepository.calculateTotalTrustBalance). */
  private static final String CARD_TOTAL_SQL =
      """
      SELECT COALESCE(SUM(c.balance), 0)
      FROM "%s".client_ledger_cards c
      WHERE c.trust_account_id = ?
      """;

  /** One client's stored card balance (0 when the client has no card). */
  private static final String CARD_SQL =
      """
      SELECT COALESCE(SUM(c.balance), 0)
      FROM "%s".client_ledger_cards c
      WHERE c.trust_account_id = ? AND c.customer_id = ?
      """;

  /** HEAD client sign table, every client, as at a past date (cards keep no history). */
  private static final String DERIVED_CLIENT_TOTAL_SQL =
      """
      SELECT COALESCE(SUM(CASE
          WHEN t.transaction_type IN ('DEPOSIT', 'TRANSFER_IN', 'INTEREST_CREDIT') THEN t.amount
          WHEN t.transaction_type IN
              ('PAYMENT', 'TRANSFER_OUT', 'FEE_TRANSFER', 'REFUND', 'INTEREST_LPFF')
            THEN -t.amount
          ELSE 0 END), 0)
      FROM "%s".trust_transactions t
      WHERE t.trust_account_id = ?
        AND t.customer_id IS NOT NULL
        AND t.status IN ('RECORDED', 'APPROVED')
        AND t.transaction_date <= ?
      """;

  private static final String ACCOUNT_IDS_SQL = "SELECT id FROM \"%s\".trust_accounts ORDER BY id";

  private static final String STATEMENT_SQL =
      """
      SELECT closing_balance FROM "%s".bank_statements WHERE id = ? AND trust_account_id = ?
      """;

  /** HEAD outstanding items: posted, not matched to a statement line, dated on or before D. */
  private static final String OUTSTANDING_SQL =
      """
      SELECT
        COALESCE(SUM(CASE WHEN t.transaction_type IN ('DEPOSIT', 'INTEREST_CREDIT')
                     THEN t.amount ELSE 0 END), 0) AS deposits,
        COALESCE(SUM(CASE WHEN t.transaction_type IN
                         ('PAYMENT', 'FEE_TRANSFER', 'REFUND', 'INTEREST_LPFF')
                     THEN t.amount ELSE 0 END), 0) AS payments
      FROM "%s".trust_transactions t
      WHERE t.trust_account_id = ?
        AND t.status IN ('RECORDED', 'APPROVED')
        AND t.bank_statement_line_id IS NULL
        AND t.transaction_date <= ?
      """;

  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final TrustTransactionService trustTransactionService;
  private final ClientLedgerService clientLedgerService;

  public TrustBooksHarness(
      JdbcTemplate jdbc,
      Clock clock,
      TrustTransactionService trustTransactionService,
      ClientLedgerService clientLedgerService) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.trustTransactionService = trustTransactionService;
    this.clientLedgerService = clientLedgerService;
  }

  /** Every term as at today. */
  public BooksSnapshot snapshot(UUID trustAccountId) {
    return snapshotAsAt(trustAccountId, today());
  }

  /** Every term as at {@code asAt}. */
  public BooksSnapshot snapshotAsAt(UUID trustAccountId, LocalDate asAt) {
    var schema = schema();
    var cashbook = money(CASHBOOK_SQL.formatted(schema), trustAccountId, asAt);
    var clientLedgers =
        asAt.isBefore(today())
            ? money(DERIVED_CLIENT_TOTAL_SQL.formatted(schema), trustAccountId, asAt)
            : money(CARD_TOTAL_SQL.formatted(schema), trustAccountId);
    return new BooksSnapshot(
        trustAccountId,
        asAt,
        cashbook,
        clientLedgers,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  /** One client's ledger balance on the account, as at today (v0: stored card, 0 if none). */
  public BigDecimal clientLedger(UUID trustAccountId, UUID customerId) {
    return money(CARD_SQL.formatted(schema()), trustAccountId, customerId);
  }

  /**
   * Every equation the books can be checked against without a bank statement: EQ-2, EQ-4, EQ-5,
   * EQ-6 (v0: EQ-2 only — see the class Javadoc), as at today.
   */
  public void assertBalanced(UUID trustAccountId) {
    assertBalancedAsAt(trustAccountId, today());
  }

  public void assertBalancedAsAt(UUID trustAccountId, LocalDate asAt) {
    var s = snapshotAsAt(trustAccountId, asAt);
    assertThat(s.difference())
        .as(
            "EQ-2 on trust account %s as at %s: cashbook %s vs creditors %s (client ledgers %s)",
            trustAccountId, asAt, s.cashbook(), s.creditors(), s.clientLedgers())
        .isEqualByComparingTo(BigDecimal.ZERO);
    // EQ-4 (647B), EQ-5 (648A), EQ-6 (646A): no-ops on the HEAD model.
  }

  /** EQ-3 over every trust account of the tenant (open or closed), plus EQ-2 for each. */
  public void assertFirmBalanced(LocalDate asAt) {
    var schema = schema();
    List<UUID> accountIds = jdbc.queryForList(ACCOUNT_IDS_SQL.formatted(schema), UUID.class);
    var softly = new SoftAssertions();
    var interAccountTotal = BigDecimal.ZERO;
    for (UUID accountId : accountIds) {
      var s = snapshotAsAt(accountId, asAt);
      interAccountTotal = interAccountTotal.add(s.interAccount());
      softly
          .assertThat(s.difference())
          .as(
              "EQ-2 on trust account %s as at %s: cashbook %s vs creditors %s (client ledgers %s)",
              accountId, asAt, s.cashbook(), s.creditors(), s.clientLedgers())
          .isEqualByComparingTo(BigDecimal.ZERO);
    }
    softly
        .assertThat(interAccountTotal)
        .as("EQ-3: Σ interAccount over the firm's %d trust accounts", accountIds.size())
        .isEqualByComparingTo(BigDecimal.ZERO);
    softly.assertAll();
  }

  /** EQ-1 as at the reconciliation's period end, given its statement. */
  public void assertBankAgrees(UUID trustAccountId, UUID bankStatementId, LocalDate periodEnd) {
    var schema = schema();
    var bank = money(STATEMENT_SQL.formatted(schema), bankStatementId, trustAccountId);
    var outstanding =
        jdbc.queryForMap(OUTSTANDING_SQL.formatted(schema), trustAccountId, periodEnd);
    var adjustedBank =
        bank.add((BigDecimal) outstanding.get("deposits"))
            .subtract((BigDecimal) outstanding.get("payments"));
    var cashbook = money(CASHBOOK_SQL.formatted(schema), trustAccountId, periodEnd);
    assertThat(adjustedBank)
        .as(
            "EQ-1 on trust account %s as at %s: adjusted bank (bank %s + outstanding deposits %s"
                + " − outstanding payments %s) vs cashbook %s",
            trustAccountId,
            periodEnd,
            bank,
            outstanding.get("deposits"),
            outstanding.get("payments"),
            cashbook)
        .isEqualByComparingTo(cashbook);
  }

  /** The production services return the oracle's figures (v0: as at today only). */
  public void assertServicesAgree(UUID trustAccountId, LocalDate asAt) {
    assertThat(asAt)
        .as("v0: the HEAD services have no as-at date; assertServicesAgree supports today only")
        .isEqualTo(today());
    var s = snapshotAsAt(trustAccountId, asAt);
    assertThat(trustTransactionService.getCashbookBalance(trustAccountId).balance())
        .as("TrustTransactionService.getCashbookBalance vs oracle cashbook")
        .isEqualByComparingTo(s.cashbook());
    assertThat(clientLedgerService.getTotalTrustBalance(trustAccountId).balance())
        .as("ClientLedgerService.getTotalTrustBalance vs oracle client ledgers")
        .isEqualByComparingTo(s.clientLedgers());
  }

  /**
   * Runs {@code action}, then asserts the account balanced and that each term moved by exactly the
   * stated delta. All failures are reported together.
   */
  public void assertMoves(UUID trustAccountId, Expected expected, Runnable action) {
    var before = snapshot(trustAccountId);
    action.run();
    var after = snapshot(trustAccountId);
    var moved = after.deltaSince(before);
    var softly = new SoftAssertions();
    softly
        .assertThat(after.difference())
        .as(
            "EQ-2 on trust account %s after the action: cashbook %s vs creditors %s (client"
                + " ledgers %s)",
            trustAccountId, after.cashbook(), after.creditors(), after.clientLedgers())
        .isEqualByComparingTo(BigDecimal.ZERO);
    term(softly, "cashbook", moved.cashbook(), expected.cashbook());
    term(softly, "clientLedgers", moved.clientLedgers(), expected.clientLedgers());
    term(
        softly, "investmentCreditors", moved.investmentCreditors(), expected.investmentCreditors());
    term(softly, "fundLedger", moved.fundLedger(), expected.fundLedger());
    term(softly, "unidentified", moved.unidentified(), expected.unidentified());
    term(softly, "interAccount", moved.interAccount(), expected.interAccount());
    softly.assertAll();
  }

  private static void term(
      SoftAssertions softly, String name, BigDecimal moved, BigDecimal expected) {
    softly
        .assertThat(moved)
        .as("%s moved by %s, expected %s", name, moved.toPlainString(), expected.toPlainString())
        .isEqualByComparingTo(expected);
  }

  private BigDecimal money(String sql, Object... args) {
    var value = jdbc.queryForObject(sql, BigDecimal.class, args);
    return value == null ? BigDecimal.ZERO : value;
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  private static String schema() {
    var schema = RequestScopes.requireTenantId();
    if (!TENANT_SCHEMA.matcher(schema).matches()) {
      throw new IllegalStateException("Not a tenant schema: " + schema);
    }
    return schema;
  }

  /** The movement of each term an action must produce ({@code after − before}). */
  public record Expected(
      BigDecimal cashbook,
      BigDecimal clientLedgers,
      BigDecimal investmentCreditors,
      BigDecimal fundLedger,
      BigDecimal unidentified,
      BigDecimal interAccount) {

    /** Nothing moves (a pending, rejected or refused step). */
    public static Expected none() {
      return new Expected(
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO);
    }

    /** Cashbook and the client ledgers move together by {@code signedAmount}. */
    public static Expected cashbookAndClient(String signedAmount) {
      var amount = new BigDecimal(signedAmount);
      return new Expected(
          amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Cashbook and the fund ledger move together by {@code signedAmount} (647A onwards). */
    public static Expected cashbookAndFund(String signedAmount) {
      var amount = new BigDecimal(signedAmount);
      return new Expected(
          amount, BigDecimal.ZERO, BigDecimal.ZERO, amount, BigDecimal.ZERO, BigDecimal.ZERO);
    }
  }
}
