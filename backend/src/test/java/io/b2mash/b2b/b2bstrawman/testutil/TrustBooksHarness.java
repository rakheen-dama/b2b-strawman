package io.b2mash.b2b.b2bstrawman.testutil;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedger;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerEffect;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
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
 * <h2>Version 4 (647B) — EQ-4: unidentified deposits are itemised</h2>
 *
 * <p>{@link #assertBalancedAsAt} and {@link #assertFirmBalanced} check EQ-4 for every account
 * (ADR-376, §16.1.2), from the ledger rows alone — never from the item's status columns, except
 * (d):
 *
 * <ol type="a">
 *   <li>every posted row on an {@code UNIDENTIFIED_DEPOSITS} creditor account dated {@code <= D}
 *       names its item ({@code unidentified_deposit_id}, reversals included);
 *   <li>each item's balance as at D ({@code SUM(ledger_effect)} of its own posted {@code
 *       NON_CLIENT} rows dated {@code <= D}) is 0 or its amount — an item is never part-open;
 *   <li>Σ amount of the items open as at D (balance = amount) equals the unidentified term;
 *   <li>as at today, an item is {@code HELD} exactly when it is open.
 * </ol>
 *
 * <p>{@link Expected#cashbookAndUnidentified} is the move of a deposit recorded as unidentified.
 * EQ-5 (648A) is still a no-op.
 *
 * <h2>Version 3 (646C) — every term as at a date; outstanding items by line date</h2>
 *
 * <ul>
 *   <li>{@code fundLedger}, {@code unidentified} — {@code SUM(ledger_effect)} over posted {@code
 *       NON_CLIENT} rows whose creditor account is of type {@code FUND_INTEREST_AND_CHARGES} /
 *       {@code UNIDENTIFIED_DEPOSITS}, dated {@code <= D} (were zero in v2). A third creditor type
 *       has no oracle term, so a row on one unbalances EQ-2 here (test it in its own tenant).
 *   <li>{@link #assertBankAgrees} — outstanding items are dated by the <b>statement line's</b> date
 *       (a match to a line after D is still outstanding as at D); a reversed row and its posted
 *       contra, neither on a line, cancel (ADR-377, §16.3.3). It also checks {@code
 *       TrustBooksService.bankPosition}.
 *   <li>{@link #assertServicesAgree} — {@code TrustBooksService.position} against every oracle term
 *       at <b>any</b> date; the two undated legacy services are still checked when the date is
 *       today.
 * </ul>
 *
 * <h2>Version 2 (649B) — effect columns; EQ-6 with contra reversals</h2>
 *
 * <p>Every posted row carries its own effects (ADR-378), so every term is a sum over them, as at
 * any date — v0's split between the stored card (today) and a derived sum (the past) is gone:
 *
 * <ul>
 *   <li>"Posted" = {@code posted_at IS NOT NULL} (§16.1.2).
 *   <li>{@code cashbook(A,D)} — {@code SUM(cashbook_effect)} over posted rows dated {@code <= D}.
 *   <li>{@code clientLedgers(A,D)}, {@code investmentCreditors(A,D)}, {@code interAccount(A,D)} —
 *       {@code SUM(ledger_effect)} over posted rows of the ledger {@code CLIENT}, {@code
 *       CLIENT_INVESTMENT}, {@code INTER_ACCOUNT}, dated {@code <= D} (the last two are always 0 in
 *       646A — no type posts to them yet).
 *   <li>{@link #clientLedger} / {@link #clientLedgerAsAt} — one client's {@code SUM(ledger_effect)}
 *       on the {@code CLIENT} ledger, as at today / a date.
 *   <li>EQ-6 (in {@link #assertBalancedAsAt} and {@link #assertFirmBalanced}) — every posted row's
 *       (ledger, cashbook effect, ledger effect) re-derived from the registry: {@code
 *       TrustTransactionType.of(type).effectFor(amount)} for every row, a {@code REVERSED} original
 *       included (it keeps its own effects), and for a {@code REVERSAL} contra the negation of its
 *       original's: {@code TrustTransactionType.of(originalType).effectFor(originalAmount)
 *       .negated()} (649B, ADR-378; the 646A interim rule — both at {@code (CLIENT, 0, 0)} — is
 *       gone). A posted {@code REVERSAL} with no original fails EQ-6. EQ-4 is implemented in v4
 *       (647B); EQ-5 (648A) is a no-op until its columns exist.
 *   <li>{@link #assertBankAgrees} — statement closing balance + outstanding deposits − outstanding
 *       payments (posted, unmatched, dated {@code <= periodEnd}; split on the sign of {@code
 *       cashbook_effect}) against {@code cashbook(A, periodEnd)} — v2 dated a match by match time;
 *       v3 dates it by the line's date.
 *   <li>{@link #assertServicesAgree} — {@code TrustTransactionService.getCashbookBalance} and
 *       {@code ClientLedgerService.getTotalTrustBalance} against the oracle. Since 646A both are
 *       derived by production SQL and the oracle by its own, so it is a real check (v3 adds {@code
 *       TrustBooksService.position} at any date).
 * </ul>
 */
@Component
@Profile("test")
public final class TrustBooksHarness {

  private static final Pattern TENANT_SCHEMA = Pattern.compile("^tenant_[0-9a-f]{12}$");

  /** The cashbook as at a date: every posted row's cashbook effect. */
  private static final String CASHBOOK_SQL =
      """
      SELECT COALESCE(SUM(t.cashbook_effect), 0) FROM "%s".trust_transactions t
      WHERE t.trust_account_id = ? AND t.posted_at IS NOT NULL AND t.transaction_date <= ?
      """;

  /** One ledger's total as at a date ({@code CLIENT}, {@code CLIENT_INVESTMENT}, …). */
  private static final String LEDGER_TOTAL_SQL =
      """
      SELECT COALESCE(SUM(t.ledger_effect), 0) FROM "%s".trust_transactions t
      WHERE t.trust_account_id = ? AND t.ledger = ? AND t.posted_at IS NOT NULL
        AND t.transaction_date <= ?
      """;

  /** One client's ledger as at a date. */
  private static final String CLIENT_SQL =
      """
      SELECT COALESCE(SUM(t.ledger_effect), 0) FROM "%s".trust_transactions t
      WHERE t.trust_account_id = ? AND t.customer_id = ? AND t.ledger = 'CLIENT'
        AND t.posted_at IS NOT NULL AND t.transaction_date <= ?
      """;

  /** EQ-6: every posted row with its stored effects (and, for a contra, its original's type). */
  private static final String POSTED_ROWS_SQL =
      """
      SELECT t.id, t.transaction_type, t.amount, t.status, t.ledger, t.cashbook_effect,
             t.ledger_effect, o.transaction_type AS original_type, o.amount AS original_amount
      FROM "%s".trust_transactions t
      LEFT JOIN "%s".trust_transactions o ON o.id = t.reversal_of
      WHERE t.trust_account_id = ? AND t.posted_at IS NOT NULL AND t.transaction_date <= ?
      """;

  private static final String ACCOUNT_IDS_SQL = "SELECT id FROM \"%s\".trust_accounts ORDER BY id";

  private static final String STATEMENT_SQL =
      """
      SELECT closing_balance FROM "%s".bank_statements WHERE id = ? AND trust_account_id = ?
      """;

  /**
   * Outstanding items as at D (646C, ADR-377): posted rows moving the cashbook, dated on or before
   * D, not on a statement line dated on or before D — a match is dated by the <b>line's</b> date. A
   * reversed row and its posted contra, neither on a line, cancel (the entry never reached the
   * bank). Binds: account, D, D, D.
   */
  private static final String OUTSTANDING_SQL =
      """
      SELECT COALESCE(SUM(t.cashbook_effect) FILTER (WHERE t.cashbook_effect > 0), 0)
               AS deposits,
             COALESCE(-SUM(t.cashbook_effect) FILTER (WHERE t.cashbook_effect < 0), 0)
               AS payments
      FROM "%1$s".trust_transactions t
      LEFT JOIN "%1$s".bank_statement_lines l ON l.id = t.bank_statement_line_id
      WHERE t.trust_account_id = ? AND t.posted_at IS NOT NULL AND t.cashbook_effect <> 0
        AND t.transaction_date <= ?
        AND (t.bank_statement_line_id IS NULL OR l.transaction_date > ?)
        AND NOT (t.bank_statement_line_id IS NULL AND t.reversed_by_id IS NOT NULL
                 AND EXISTS (SELECT 1 FROM "%1$s".trust_transactions c
                              WHERE c.id = t.reversed_by_id AND c.posted_at IS NOT NULL
                                AND c.bank_statement_line_id IS NULL
                                AND c.transaction_date <= ?))
        AND NOT (t.bank_statement_line_id IS NULL AND t.reversal_of IS NOT NULL
                 AND EXISTS (SELECT 1 FROM "%1$s".trust_transactions o
                              WHERE o.id = t.reversal_of AND o.bank_statement_line_id IS NULL))
      """;

  /** One non-client creditor ledger's total as at a date, by the creditor account's type. */
  private static final String NON_CLIENT_TOTAL_SQL =
      """
      SELECT COALESCE(SUM(t.ledger_effect), 0) FROM "%1$s".trust_transactions t
      JOIN "%1$s".trust_creditor_accounts ca ON ca.id = t.creditor_account_id
      WHERE t.trust_account_id = ? AND t.ledger = 'NON_CLIENT' AND ca.account_type = ?
        AND t.posted_at IS NOT NULL AND t.transaction_date <= ?
      """;

  /** EQ-4 (a): posted unidentified-ledger rows (reversals included) that name no item. */
  private static final String UNIDENTIFIED_ROWS_WITHOUT_ITEM_SQL =
      """
      SELECT count(*) FROM "%1$s".trust_transactions t
      JOIN "%1$s".trust_creditor_accounts ca ON ca.id = t.creditor_account_id
      WHERE t.trust_account_id = ? AND t.ledger = 'NON_CLIENT'
        AND ca.account_type = 'UNIDENTIFIED_DEPOSITS' AND t.posted_at IS NOT NULL
        AND t.transaction_date <= ? AND t.unidentified_deposit_id IS NULL
      """;

  /**
   * EQ-4 (b–d): each item of the account with its amount, status and its balance as at D from its
   * own posted rows. Binds D, account.
   */
  private static final String ITEM_BALANCES_SQL =
      """
      SELECT d.id, d.amount, d.status,
             COALESCE((SELECT SUM(t.ledger_effect) FROM "%1$s".trust_transactions t
                        WHERE t.unidentified_deposit_id = d.id AND t.ledger = 'NON_CLIENT'
                          AND t.posted_at IS NOT NULL AND t.transaction_date <= ?), 0) AS balance
      FROM "%1$s".unidentified_deposits d WHERE d.trust_account_id = ?
      """;

  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final TrustTransactionService trustTransactionService;
  private final ClientLedgerService clientLedgerService;
  private final TrustBooksService trustBooksService;

  public TrustBooksHarness(
      JdbcTemplate jdbc,
      Clock clock,
      TrustTransactionService trustTransactionService,
      ClientLedgerService clientLedgerService,
      TrustBooksService trustBooksService) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.trustTransactionService = trustTransactionService;
    this.clientLedgerService = clientLedgerService;
    this.trustBooksService = trustBooksService;
  }

  /** Every term as at today. */
  public BooksSnapshot snapshot(UUID trustAccountId) {
    return snapshotAsAt(trustAccountId, today());
  }

  /** Every term as at {@code asAt}. */
  public BooksSnapshot snapshotAsAt(UUID trustAccountId, LocalDate asAt) {
    var schema = schema();
    var cashbook = money(CASHBOOK_SQL.formatted(schema), trustAccountId, asAt);
    var ledgerTotal = LEDGER_TOTAL_SQL.formatted(schema);
    var clientLedgers = money(ledgerTotal, trustAccountId, TrustLedger.CLIENT.name(), asAt);
    var investmentCreditors =
        money(ledgerTotal, trustAccountId, TrustLedger.CLIENT_INVESTMENT.name(), asAt);
    var interAccount = money(ledgerTotal, trustAccountId, TrustLedger.INTER_ACCOUNT.name(), asAt);
    var nonClientTotal = NON_CLIENT_TOTAL_SQL.formatted(schema);
    var fundLedger = money(nonClientTotal, trustAccountId, "FUND_INTEREST_AND_CHARGES", asAt);
    var unidentified = money(nonClientTotal, trustAccountId, "UNIDENTIFIED_DEPOSITS", asAt);
    return new BooksSnapshot(
        trustAccountId,
        asAt,
        cashbook,
        clientLedgers,
        investmentCreditors,
        fundLedger,
        unidentified,
        interAccount);
  }

  /** One client's ledger balance on the account, as at today (0 when it has no posted row). */
  public BigDecimal clientLedger(UUID trustAccountId, UUID customerId) {
    return clientLedgerAsAt(trustAccountId, customerId, today());
  }

  /** One client's ledger balance on the account, as at {@code asAt}. */
  public BigDecimal clientLedgerAsAt(UUID trustAccountId, UUID customerId, LocalDate asAt) {
    return money(CLIENT_SQL.formatted(schema()), trustAccountId, customerId, asAt);
  }

  /**
   * Every equation the books can be checked against without a bank statement: EQ-2, EQ-4, EQ-5,
   * EQ-6 (646A: EQ-2 and EQ-6 — see the class Javadoc), as at today.
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
    var softly = new SoftAssertions();
    assertEffectsDerived(softly, trustAccountId, asAt);
    assertItemised(softly, trustAccountId, asAt, s.unidentified());
    softly.assertAll();
    // EQ-5 (648A): a no-op until its columns exist.
  }

  /** EQ-3 over every trust account of the tenant (open or closed), plus EQ-2 and EQ-6 for each. */
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
      assertEffectsDerived(softly, accountId, asAt);
      assertItemised(softly, accountId, asAt, s.unidentified());
    }
    softly
        .assertThat(interAccountTotal)
        .as("EQ-3: Σ interAccount over the firm's %d trust accounts", accountIds.size())
        .isEqualByComparingTo(BigDecimal.ZERO);
    softly.assertAll();
  }

  /**
   * EQ-6: every posted row's stored (ledger, cashbook effect, ledger effect) equals the registry's
   * effect for its type and amount — a {@code REVERSED} original included — and a {@code REVERSAL}
   * contra's equals the negation of its original's (649B, ADR-378).
   */
  private void assertEffectsDerived(SoftAssertions softly, UUID trustAccountId, LocalDate asAt) {
    var schema = schema();
    var rows = jdbc.queryForList(POSTED_ROWS_SQL.formatted(schema, schema), trustAccountId, asAt);
    for (var row : rows) {
      var type = (String) row.get("transaction_type");
      var amount = (BigDecimal) row.get("amount");
      var what =
          "EQ-6 on trust account %s, %s %s %s (%s)"
              .formatted(trustAccountId, row.get("status"), type, amount, row.get("id"));
      TrustLedgerEffect expected;
      if ("REVERSAL".equals(type)) {
        var originalType = (String) row.get("original_type");
        softly.assertThat(originalType).as(what + " original").isNotNull();
        if (originalType == null) {
          continue;
        }
        expected =
            TrustTransactionType.of(originalType)
                .effectFor((BigDecimal) row.get("original_amount"))
                .negated();
      } else {
        expected = TrustTransactionType.of(type).effectFor(amount);
      }
      softly.assertThat(row.get("ledger")).as(what + " ledger").isEqualTo(expected.ledger().name());
      softly
          .assertThat((BigDecimal) row.get("cashbook_effect"))
          .as(what + " cashbook effect")
          .isEqualByComparingTo(expected.cashbookEffect());
      softly
          .assertThat((BigDecimal) row.get("ledger_effect"))
          .as(what + " ledger effect")
          .isEqualByComparingTo(expected.ledgerEffect());
    }
  }

  /**
   * EQ-4 (647B, ADR-376): unidentified deposits are itemised — see the class Javadoc (a)–(d). The
   * unidentified term is the oracle's own ({@link #snapshotAsAt}).
   */
  private void assertItemised(
      SoftAssertions softly, UUID trustAccountId, LocalDate asAt, BigDecimal unidentifiedTerm) {
    var schema = schema();
    Long unnamed =
        jdbc.queryForObject(
            UNIDENTIFIED_ROWS_WITHOUT_ITEM_SQL.formatted(schema), Long.class, trustAccountId, asAt);
    softly
        .assertThat(unnamed)
        .as(
            "EQ-4 on trust account %s as at %s: unidentified-ledger rows naming no item",
            trustAccountId, asAt)
        .isZero();
    var items = jdbc.queryForList(ITEM_BALANCES_SQL.formatted(schema), asAt, trustAccountId);
    boolean today = asAt.equals(today());
    var openTotal = BigDecimal.ZERO;
    for (var item : items) {
      var amount = (BigDecimal) item.get("amount");
      var balance = (BigDecimal) item.get("balance");
      var what =
          "EQ-4 on trust account %s as at %s, item %s (%s, %s): balance %s"
              .formatted(
                  trustAccountId,
                  asAt,
                  item.get("id"),
                  item.get("status"),
                  amount.toPlainString(),
                  balance.toPlainString());
      boolean open = balance.compareTo(amount) == 0;
      softly
          .assertThat(open || balance.signum() == 0)
          .as(what + " — must be 0 or the amount")
          .isTrue();
      if (open) {
        openTotal = openTotal.add(amount);
      }
      if (today) {
        softly
            .assertThat("HELD".equals(item.get("status")))
            .as(what + " — HELD exactly when open as at today")
            .isEqualTo(open);
      }
    }
    softly
        .assertThat(openTotal)
        .as(
            "EQ-4 on trust account %s as at %s: Σ open items vs the unidentified term %s",
            trustAccountId, asAt, unidentifiedTerm)
        .isEqualByComparingTo(unidentifiedTerm);
  }

  /**
   * EQ-1 as at the reconciliation's period end, given its statement: the statement's closing
   * balance + outstanding deposits − outstanding payments (dated by line date, v3) against {@code
   * cashbook(A, periodEnd)}; and {@code TrustBooksService.bankPosition} returns the same figures.
   */
  public void assertBankAgrees(UUID trustAccountId, UUID bankStatementId, LocalDate periodEnd) {
    var schema = schema();
    var bank = money(STATEMENT_SQL.formatted(schema), bankStatementId, trustAccountId);
    var outstanding =
        jdbc.queryForMap(
            OUTSTANDING_SQL.formatted(schema), trustAccountId, periodEnd, periodEnd, periodEnd);
    var deposits = (BigDecimal) outstanding.get("deposits");
    var payments = (BigDecimal) outstanding.get("payments");
    var adjustedBank = bank.add(deposits).subtract(payments);
    var cashbook = money(CASHBOOK_SQL.formatted(schema), trustAccountId, periodEnd);
    assertThat(adjustedBank)
        .as(
            "EQ-1 on trust account %s as at %s: adjusted bank (bank %s + outstanding deposits %s"
                + " − outstanding payments %s) vs cashbook %s",
            trustAccountId, periodEnd, bank, deposits, payments, cashbook)
        .isEqualByComparingTo(cashbook);
    var service = trustBooksService.bankPosition(trustAccountId, bankStatementId, periodEnd);
    var softly = new SoftAssertions();
    softly
        .assertThat(service.bankBalance())
        .as("bankPosition.bankBalance")
        .isEqualByComparingTo(bank);
    softly
        .assertThat(service.outstandingDeposits())
        .as("bankPosition.outstandingDeposits")
        .isEqualByComparingTo(deposits);
    softly
        .assertThat(service.outstandingPayments())
        .as("bankPosition.outstandingPayments")
        .isEqualByComparingTo(payments);
    softly
        .assertThat(service.adjustedBankBalance())
        .as("bankPosition.adjustedBankBalance")
        .isEqualByComparingTo(adjustedBank);
    softly.assertAll();
  }

  /**
   * The production services return the oracle's figures as at {@code asAt}: {@code
   * TrustBooksService.position} at any date, term by term ({@code otherNonClient} must be zero —
   * the oracle has no such term); and, when {@code asAt} is today, the two undated legacy reads
   * ({@code getCashbookBalance}, {@code getTotalTrustBalance}).
   */
  public void assertServicesAgree(UUID trustAccountId, LocalDate asAt) {
    var s = snapshotAsAt(trustAccountId, asAt);
    var p = trustBooksService.position(trustAccountId, asAt);
    var softly = new SoftAssertions();
    softly.assertThat(p.cashbook()).as("position.cashbook").isEqualByComparingTo(s.cashbook());
    softly
        .assertThat(p.clientLedgers())
        .as("position.clientLedgers")
        .isEqualByComparingTo(s.clientLedgers());
    softly
        .assertThat(p.investmentCreditors())
        .as("position.investmentCreditors")
        .isEqualByComparingTo(s.investmentCreditors());
    softly
        .assertThat(p.fundLedger())
        .as("position.fundLedger")
        .isEqualByComparingTo(s.fundLedger());
    softly
        .assertThat(p.unidentifiedDeposits())
        .as("position.unidentifiedDeposits")
        .isEqualByComparingTo(s.unidentified());
    softly
        .assertThat(p.interAccount())
        .as("position.interAccount")
        .isEqualByComparingTo(s.interAccount());
    softly
        .assertThat(p.otherNonClient())
        .as("position.otherNonClient")
        .isEqualByComparingTo(BigDecimal.ZERO);
    softly
        .assertThat(p.creditorTotal())
        .as("position.creditorTotal")
        .isEqualByComparingTo(s.creditors());
    softly
        .assertThat(p.difference())
        .as("position.difference")
        .isEqualByComparingTo(s.difference());
    if (asAt.equals(today())) {
      softly
          .assertThat(trustTransactionService.getCashbookBalance(trustAccountId).balance())
          .as("TrustTransactionService.getCashbookBalance vs oracle cashbook")
          .isEqualByComparingTo(s.cashbook());
      softly
          .assertThat(clientLedgerService.getTotalTrustBalance(trustAccountId).balance())
          .as("ClientLedgerService.getTotalTrustBalance vs oracle client ledgers")
          .isEqualByComparingTo(s.clientLedgers());
    }
    softly.assertAll();
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

    /**
     * Cashbook and the unidentified-deposits ledger move together by {@code signedAmount} (647B).
     */
    public static Expected cashbookAndUnidentified(String signedAmount) {
      var amount = new BigDecimal(signedAmount);
      return new Expected(
          amount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, amount, BigDecimal.ZERO);
    }
  }
}
