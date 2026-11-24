package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The trust position's arithmetic at cutover (Phase 93 657B, §17.3.9) — pure and in memory, from
 * the rows just validated and the batch's reconciled-position inputs; nothing is read from the
 * books (X2). EQ-1(C), the items tie, EQ-2(C), the investments tie and the deficit test, each side
 * and each difference returned for the wizard's indicator ({@code summary.equation}, §17.4.4), and
 * the batch-level hard blocks in display order: the deficit first, then the control totals.
 *
 * <p>The nested records are the wire shape of a trust batch's {@code summary} (pinned in {@code
 * TakeOnWireContractTest}). Amounts are plain two-decimal strings ({@code "418500.00"}), never JSON
 * numbers: the summary is stored as jsonb, and a decimal read back from it would become a double.
 */
public final class TrustPositionEquation {

  private TrustPositionEquation() {}

  private static final JsonMapper JSON = JsonMapper.builder().build();

  // --- Wire records (TakeOnBatchResponse.summary of a TRUST_POSITION batch) ---

  /** One batch-level message: an account check, a block or a flag, with its X3 text. */
  public record TrustPositionMessage(String code, String message) {}

  /** EQ-1(C): bank + outstanding deposits − outstanding payments = cashbook. */
  public record TrustEq1(
      String bankBalance,
      String plusOutstandingDeposits,
      String lessOutstandingPayments,
      String adjustedBank,
      String cashbook,
      String difference,
      boolean holds) {}

  /** The listed outstanding items agree with the reconciled position's totals. */
  public record TrustItemsTie(
      String listedDeposits,
      String listedPayments,
      String outstandingDeposits,
      String outstandingPayments,
      String difference,
      boolean holds) {}

  /** EQ-2(C): cashbook = the trust creditors on the banking account. */
  public record TrustEq2(
      String clientLedgers,
      String fundLedger,
      String unidentified,
      String placedInSection86_3,
      String creditors,
      String cashbook,
      String difference,
      boolean holds) {}

  /** The investments listed agree with the total invested per the institutions' confirmations. */
  public record TrustInvestmentsTie(
      String perConfirmations, String listed, String difference, boolean holds) {}

  /** The deficit test: the firm's trust creditors against the trust money held. */
  public record TrustDeficit(
      String trustCreditors, String moneyHeld, String difference, boolean inDeficit) {}

  public record TrustEquationSummary(
      TrustEq1 eq1,
      TrustItemsTie itemsTie,
      TrustEq2 eq2,
      TrustInvestmentsTie investments,
      TrustDeficit deficit) {}

  /**
   * A trust batch's {@code summary}: the §17.3.9 account checks that failed (shown above the table;
   * none → the rows and the equation were checked), the batch-level hard blocks (deficit first),
   * the batch-level warnings with their text, the flag codes, and the equation ({@code null} when
   * an account check failed).
   */
  public record TrustPositionSummary(
      List<TrustPositionMessage> accountErrors,
      List<TrustPositionMessage> blocks,
      List<TrustPositionMessage> warnings,
      List<String> flags,
      TrustEquationSummary equation) {

    /** The jsonb shape stored in {@code take_on_batches.summary}. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap() {
      return JSON.convertValue(this, Map.class);
    }

    /**
     * The typed reader of a trust batch's stored {@code summary} (the inverse of {@link #toMap});
     * empty when the batch has none (never validated).
     */
    public static Optional<TrustPositionSummary> from(Map<String, ?> summary) {
      return summary == null
          ? Optional.empty()
          : Optional.of(JSON.convertValue(summary, TrustPositionSummary.class));
    }

    /** EQ-1(C) held; false when no equation was computed (an account check failed). */
    public boolean eq1Holds() {
      return equation != null && equation.eq1() != null && equation.eq1().holds();
    }

    /** EQ-2(C) held; false when no equation was computed (an account check failed). */
    public boolean eq2Holds() {
      return equation != null && equation.eq2() != null && equation.eq2().holds();
    }
  }

  // --- Inputs and result (not wire) ---

  /** The batch's reconciled position at cutover (the previous system's figures). */
  public record Inputs(
      BigDecimal bankBalance,
      BigDecimal outstandingDeposits,
      BigDecimal outstandingPayments,
      BigDecimal cashbook,
      BigDecimal fundLedger,
      BigDecimal investedTotal) {}

  /** The sums over the validated rows (every row whose amount typed — D-184). */
  public record Sums(
      BigDecimal clients,
      BigDecimal unidentified,
      BigDecimal section86_3,
      BigDecimal section86_4,
      BigDecimal fundAmounts,
      BigDecimal confirmations,
      BigDecimal listedDeposits,
      BigDecimal listedPayments) {

    public static Sums zero() {
      return new Sums(
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO);
    }
  }

  /** The equation, and the batch-level blocks in display order. */
  public record Result(
      TrustEquationSummary equation, List<TrustPositionMessage> blocks, boolean fundInDebit) {}

  /** §17.3.9, exactly; every comparison to the cent. */
  public static Result compute(Inputs in, Sums sums) {
    BigDecimal b = cents(in.bankBalance());
    BigDecimal od = cents(in.outstandingDeposits());
    BigDecimal op = cents(in.outstandingPayments());
    BigDecimal cb = cents(in.cashbook());
    BigDecimal f = cents(in.fundLedger());
    BigDecimal invested = cents(in.investedTotal());

    BigDecimal clients = cents(sums.clients());
    BigDecimal unidentified = cents(sums.unidentified());
    BigDecimal s863 = cents(sums.section86_3());
    BigDecimal s864 = cents(sums.section86_4());
    BigDecimal fundAmounts = cents(sums.fundAmounts());
    BigDecimal confirmations = cents(sums.confirmations());
    BigDecimal listedOd = cents(sums.listedDeposits());
    BigDecimal listedOp = cents(sums.listedPayments());

    BigDecimal adjustedBank = b.add(od).subtract(op);
    boolean eq1Holds = adjustedBank.compareTo(cb) == 0;

    BigDecimal itemsDifference = listedOd.subtract(od).abs().add(listedOp.subtract(op).abs());
    boolean depositsTie = listedOd.compareTo(od) == 0;
    boolean paymentsTie = listedOp.compareTo(op) == 0;

    BigDecimal creditors = clients.add(f).add(unidentified).subtract(s863);
    boolean eq2Holds = cb.compareTo(creditors) == 0;

    boolean investmentsHold = confirmations.compareTo(invested) == 0;

    BigDecimal creditorsFirm = clients.add(s864).add(f).add(fundAmounts).add(unidentified);
    BigDecimal moneyHeld = adjustedBank.add(invested);
    boolean inDeficit = creditorsFirm.compareTo(moneyHeld) > 0;

    var equation =
        new TrustEquationSummary(
            new TrustEq1(
                s(b), s(od), s(op), s(adjustedBank), s(cb), s(adjustedBank.subtract(cb)), eq1Holds),
            new TrustItemsTie(
                s(listedOd),
                s(listedOp),
                s(od),
                s(op),
                s(itemsDifference),
                depositsTie && paymentsTie),
            new TrustEq2(
                s(clients),
                s(f),
                s(unidentified),
                s(s863.negate()),
                s(creditors),
                s(cb),
                s(creditors.subtract(cb)),
                eq2Holds),
            new TrustInvestmentsTie(
                s(invested),
                s(confirmations),
                s(confirmations.subtract(invested)),
                investmentsHold),
            new TrustDeficit(
                s(creditorsFirm), s(moneyHeld), s(creditorsFirm.subtract(moneyHeld)), inDeficit));

    var blocks = new ArrayList<TrustPositionMessage>();
    if (inDeficit) {
      blocks.add(
          new TrustPositionMessage(
              TrustPositionMessages.TRUST_DEFICIT,
              TrustPositionMessages.deficit(creditorsFirm, moneyHeld)));
    }
    if (!eq1Holds) {
      blocks.add(
          new TrustPositionMessage(
              TrustPositionMessages.CONTROL_TOTAL_EQ1,
              TrustPositionMessages.eq1(b, od, op, adjustedBank, cb)));
    }
    if (!depositsTie) {
      blocks.add(
          new TrustPositionMessage(
              TrustPositionMessages.CONTROL_TOTAL_DEPOSITS,
              TrustPositionMessages.listedTotal("deposits", listedOd, od)));
    }
    if (!paymentsTie) {
      blocks.add(
          new TrustPositionMessage(
              TrustPositionMessages.CONTROL_TOTAL_PAYMENTS,
              TrustPositionMessages.listedTotal("payments", listedOp, op)));
    }
    if (!eq2Holds) {
      blocks.add(
          new TrustPositionMessage(
              TrustPositionMessages.CONTROL_TOTAL_EQ2, TrustPositionMessages.eq2(creditors, cb)));
    }
    if (!investmentsHold) {
      blocks.add(
          new TrustPositionMessage(
              TrustPositionMessages.CONTROL_TOTAL_INVESTMENTS,
              TrustPositionMessages.investmentsListed(confirmations, invested)));
    }
    return new Result(equation, List.copyOf(blocks), f.signum() < 0);
  }

  /** A wire amount: two decimals, plain ({@code "-100000.00"}). */
  static String s(BigDecimal amount) {
    return cents(amount).toPlainString();
  }

  private static BigDecimal cents(BigDecimal amount) {
    return Objects.requireNonNull(amount, "amount").setScale(2, RoundingMode.HALF_UP);
  }
}
