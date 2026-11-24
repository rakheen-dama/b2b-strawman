package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService.TrustPosition;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustOpeningPlan.Contribution;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The commit's in-transaction proof (Phase 93 657C, §17.3.9): after every opening row is posted and
 * flushed, the same queries the first reconciliation will use — {@code TrustBooksService.position}
 * and {@code TrustLedgerQueryRepository.outstanding} at the cutover date — must return, to the
 * cent, what the batch was validated with; and the integrity check must raise no EQ-4 ({@code
 * UNIDENTIFIED_ITEMS_MISMATCH}) or EQ-5 ({@code INVESTMENT_BALANCE_MISMATCH}) flag. Any difference
 * is a programming error: a {@link TakeOnCommitRefusal} ({@code SELF_CHECK_FAILED}) rolls the whole
 * take-on back. The names of the terms that differed are logged — never an amount.
 *
 * <p>Expected figures. The banking account {@code G} holds nothing at or before the cutover date
 * (the account checks, re-run under lock), so its position must equal the batch inputs (CB, OD, OP,
 * the Fund) and the plan's creditor terms. An investment account may already hold another banking
 * account's take-on at the same date (a shared account, D-187), so its expected position is its
 * position read under the lock before posting plus this plan's contribution.
 *
 * <p>Joins the commit transaction (the services it calls are {@code readOnly} and join it; their
 * isolation is the caller's). Not final, with a public constructor: one integration test builds the
 * commit by hand with a self-check that fails (no mock bean, no new context key).
 */
@Component
public class TrustOpeningSelfCheck {

  private static final Logger log = LoggerFactory.getLogger(TrustOpeningSelfCheck.class);

  private static final Set<String> EQUATION_FLAGS =
      Set.of(
          TrustIntegrityService.UNIDENTIFIED_ITEMS_MISMATCH,
          TrustIntegrityService.INVESTMENT_BALANCE_MISMATCH);

  /** One account's books at the cutover date: the position and the outstanding items. */
  public record Books(
      TrustPosition position, BigDecimal outstandingDeposits, BigDecimal outstandingPayments) {}

  /** What the books said once the take-on was posted — the report's "Kazi" side. */
  public record Figures(Books banking, BigDecimal invested) {}

  private final TrustBooksService books;
  private final TrustLedgerQueryRepository ledgerQueries;
  private final TrustIntegrityService integrity;

  public TrustOpeningSelfCheck(
      TrustBooksService books,
      TrustLedgerQueryRepository ledgerQueries,
      TrustIntegrityService integrity) {
    this.books = books;
    this.ledgerQueries = ledgerQueries;
    this.integrity = integrity;
  }

  /** {@code account}'s books at {@code asAt}, read in the caller's transaction. */
  public Books read(UUID accountId, java.time.LocalDate asAt) {
    var outstanding = ledgerQueries.outstanding(accountId, asAt);
    return new Books(
        books.position(accountId, asAt),
        zero(outstanding.getOutstandingDeposits()),
        zero(outstanding.getOutstandingPayments()));
  }

  /**
   * Asserts the posted take-on ties ({@code before}: each investment account's books read under the
   * lock before posting). Returns the figures read, for the report.
   */
  public Figures assertTies(TrustOpeningPlan plan, TakeOnBatch batch, Map<UUID, Books> before) {
    var differing = new ArrayList<String>();
    var c = plan.cutoverDate();

    // G: the batch inputs, and the plan's creditor terms
    UUID g = plan.bankingAccountId();
    Books banking = read(g, c);
    Contribution planned = plan.contribution(g);
    var p = banking.position();
    tie(differing, "G.cashbook", p.cashbook(), batch.getCashbookBalance());
    tie(differing, "G.plan.cashbook", planned.cashbook(), batch.getCashbookBalance());
    tie(
        differing,
        "G.outstandingDeposits",
        banking.outstandingDeposits(),
        batch.getOutstandingDepositsTotal());
    tie(
        differing,
        "G.plan.outstandingDeposits",
        planned.outstandingDeposits(),
        batch.getOutstandingDepositsTotal());
    tie(
        differing,
        "G.outstandingPayments",
        banking.outstandingPayments(),
        batch.getOutstandingPaymentsTotal());
    tie(
        differing,
        "G.plan.outstandingPayments",
        planned.outstandingPayments(),
        batch.getOutstandingPaymentsTotal());
    tie(differing, "G.fundLedger", p.fundLedger(), batch.getFundLedgerBalance());
    tieTerms(differing, "G", p, planned, null);
    tie(differing, "G.difference", p.difference(), BigDecimal.ZERO);

    // each investment account: before + this plan's contribution
    BigDecimal invested = BigDecimal.ZERO;
    for (UUID i : plan.investmentAccountIds()) {
      Books after = read(i, c);
      Books was = before.get(i);
      Contribution added = plan.contribution(i);
      tieTerms(differing, "I", after.position(), added, was.position());
      tie(
          differing,
          "I.outstandingDeposits",
          after.outstandingDeposits(),
          was.outstandingDeposits());
      tie(
          differing,
          "I.outstandingPayments",
          after.outstandingPayments(),
          was.outstandingPayments());
      tie(differing, "I.difference", after.position().difference(), BigDecimal.ZERO);
      invested = invested.add(after.position().cashbook().subtract(was.position().cashbook()));
    }
    tie(differing, "investedTotal", invested, batch.getInvestedTotal());

    // EQ-4 and EQ-5 on every account
    for (UUID accountId : plan.accountIds()) {
      integrity.check(accountId, c, null).flags().stream()
          .filter(f -> EQUATION_FLAGS.contains(f.code()))
          .forEach(f -> differing.add("integrity." + f.code()));
    }

    if (!differing.isEmpty()) {
      log.warn(
          "Trust take-on batch {} self-check failed; terms differing: {}",
          batch.getId(),
          differing);
      throw TakeOnCommitRefusal.selfCheckFailed();
    }
    return new Figures(banking, invested);
  }

  /** The six EQ-2 creditor terms and the cashbook: {@code was + added} (was null: zero). */
  private static void tieTerms(
      List<String> differing,
      String account,
      TrustPosition actual,
      Contribution added,
      TrustPosition was) {
    tie(
        differing,
        account + ".cashbook",
        actual.cashbook(),
        plus(was == null ? null : was.cashbook(), added.cashbook()));
    tie(
        differing,
        account + ".clientLedgers",
        actual.clientLedgers(),
        plus(was == null ? null : was.clientLedgers(), added.clientLedgers()));
    tie(
        differing,
        account + ".investmentCreditors",
        actual.investmentCreditors(),
        plus(was == null ? null : was.investmentCreditors(), added.investmentCreditors()));
    tie(
        differing,
        account + ".fundLedger",
        actual.fundLedger(),
        plus(was == null ? null : was.fundLedger(), added.fundLedger()));
    tie(
        differing,
        account + ".unidentified",
        actual.unidentifiedDeposits(),
        plus(was == null ? null : was.unidentifiedDeposits(), added.unidentified()));
    tie(
        differing,
        account + ".interAccount",
        actual.interAccount(),
        plus(was == null ? null : was.interAccount(), added.interAccount()));
    tie(
        differing,
        account + ".otherNonClient",
        actual.otherNonClient(),
        was == null ? BigDecimal.ZERO : zero(was.otherNonClient()));
  }

  private static BigDecimal plus(BigDecimal was, BigDecimal added) {
    return zero(was).add(zero(added));
  }

  private static void tie(
      List<String> differing, String term, BigDecimal actual, BigDecimal expected) {
    if (zero(actual).compareTo(zero(expected)) != 0) {
      differing.add(term);
    }
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
