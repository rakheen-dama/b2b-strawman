package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import static io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.DEPOSIT;
import static io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.FEE_TRANSFER;
import static io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.INV_PLACE;
import static io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.INV_RETURN;
import static io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.PAYMENT;
import static io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.REFUND;
import static io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.TRANSFER_IN;
import static io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.TRANSFER_OUT;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.CustomerTypeTotalRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A client's posted trust totals on one account, one per transaction type (G89-F13). Each total is
 * the type's posted amount (a positive figure for the UI); its sign in the balance is the type's
 * {@link TrustTransactionType#ledgerSign()} — the registry, not this class, says which totals add
 * and which subtract. Derived from {@code TrustLedgerQueryRepository.clientTotalsByType} (posted
 * {@code CLIENT}-ledger effects, a reversal netted under the type it reverses), so {@link
 * #derivedBalance()} equals {@code TrustLedgerQueryRepository.clientBalance} for the same date.
 *
 * <p>648E (ADR-379 L7): a section 86(4) placement ({@code INV_PLACE}) and its return ({@code
 * INV_RETURN}) are totals of their own, {@code investmentPlacements} and {@code investmentReturns}
 * — not transfers. The identity is deposits + transfers in + investment returns − payments − fee
 * transfers − refunds − transfers out − investment placements = the client's general balance; what
 * is invested sits beside it ({@code ClientLedgerCardResponse.investedBalance}), and a future net
 * interest entry (648B, {@code CLIENT_INVESTMENT} ledger) changes only the invested balance and,
 * once returned, {@code investmentReturns}.
 *
 * <p>Phase 93 (657A, ADR-386, §17.3.15): {@code broughtForward} is a <b>signed</b> net — the
 * client's take-on rows on the account: the brought-forward balance ({@code TAKEON_CLIENT}) and
 * each item outstanding at cutover with its offset ({@code TAKEON_DEP_TRANSIT} + / {@code
 * TAKEON_DEP_OFFSET} −, {@code TAKEON_PAY_UNPRES} − / {@code TAKEON_PAY_OFFSET} +), which net to
 * zero while the item stands. A later reversal of a cutover item nets under its own type, so the
 * cancelled cheque comes back into {@code broughtForward}. {@link #derivedBalance()} adds it as-is,
 * so it still equals {@code clientBalance}.
 */
public record LedgerTotals(
    BigDecimal deposits,
    BigDecimal transfersIn,
    BigDecimal payments,
    BigDecimal feeTransfers,
    BigDecimal refunds,
    BigDecimal transfersOut,
    BigDecimal investmentPlacements,
    BigDecimal investmentReturns,
    BigDecimal broughtForward) {

  public static final LedgerTotals ZERO =
      new LedgerTotals(
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO);

  /**
   * Σ total × {@code ledgerSign()} over the types (deposits, transfers in and investment returns
   * add, the rest subtract), plus the signed brought-forward net (657A).
   */
  public BigDecimal derivedBalance() {
    return signed(DEPOSIT, deposits)
        .add(signed(TRANSFER_IN, transfersIn))
        .add(signed(PAYMENT, payments))
        .add(signed(FEE_TRANSFER, feeTransfers))
        .add(signed(REFUND, refunds))
        .add(signed(TRANSFER_OUT, transfersOut))
        .add(signed(INV_PLACE, investmentPlacements))
        .add(signed(INV_RETURN, investmentReturns))
        .add(broughtForward);
  }

  /**
   * Adds {@code amount} (a positive posted amount) to the type's total. REVERSAL has no total of
   * its own (its effect is netted under the type it reverses); an unknown code (lenient reader,
   * T15) leaves the totals unchanged. The switch is exhaustive, so a new registry type must be
   * placed here before it compiles.
   */
  public LedgerTotals plus(String transactionType, BigDecimal amount) {
    var type = TrustTransactionType.find(transactionType);
    if (type.isEmpty() || amount == null) {
      return this;
    }
    return switch (type.get()) {
      // An unidentified deposit identified and allocated to the client (647D) is, to the client,
      // their deposit received later: it adds to deposits (both types have ledgerSign +1).
      case DEPOSIT, UNIDENT_ALLOC_IN -> withDeposits(deposits.add(amount));
      case TRANSFER_IN -> withTransfersIn(transfersIn.add(amount));
      case PAYMENT -> withPayments(payments.add(amount));
      case FEE_TRANSFER -> withFeeTransfers(feeTransfers.add(amount));
      case REFUND -> withRefunds(refunds.add(amount));
      case TRANSFER_OUT -> withTransfersOut(transfersOut.add(amount));
      // 648E: money moved from the client's general balance to their section 86(4) investment
      // (approved INV_PLACE) and back (INV_RETURN) — the client's total in trust does not move.
      case INV_PLACE -> withInvestmentPlacements(investmentPlacements.add(amount));
      case INV_RETURN -> withInvestmentReturns(investmentReturns.add(amount));
      // 657A: the take-on, one signed brought-forward figure — never a deposit or a payment
      case TAKEON_CLIENT, TAKEON_PAY_OFFSET, TAKEON_DEP_TRANSIT ->
          withBroughtForward(broughtForward.add(amount));
      case TAKEON_PAY_UNPRES, TAKEON_DEP_OFFSET ->
          withBroughtForward(broughtForward.subtract(amount));
      case REVERSAL -> this;
      // Never on the CLIENT ledger: clientTotalsByType filters ledger = 'CLIENT' (a reversal row
      // carries its original's ledger), so these cannot reach the totals.
      case FUND_INTEREST,
          FUND_SWEEP,
          BANK_CHARGE,
          CHARGE_REFUND,
          UNIDENTIFIED_DEPOSIT,
          UNIDENT_ALLOC_OUT,
          UNIDENT_FUND_PAY,
          INV_RECEIVE,
          INV_INTEREST,
          FUND_INV_SHARE,
          INV_WITHDRAW,
          FIRM_INV_PLACE,
          FIRM_INV_RECEIVE,
          FIRM_INV_WITHDRAW,
          FIRM_INV_RETURN,
          TAKEON_FUND,
          TAKEON_FUND_DEBIT,
          TAKEON_UNIDENTIFIED,
          TAKEON_INVESTMENT,
          TAKEON_FIRM_INV_OUT,
          TAKEON_FIRM_INV_IN ->
          this;
    };
  }

  // One component changed per helper, so no switch arm re-lists the nine positional arguments.

  private LedgerTotals withDeposits(BigDecimal value) {
    return new LedgerTotals(
        value,
        transfersIn,
        payments,
        feeTransfers,
        refunds,
        transfersOut,
        investmentPlacements,
        investmentReturns,
        broughtForward);
  }

  private LedgerTotals withTransfersIn(BigDecimal value) {
    return new LedgerTotals(
        deposits,
        value,
        payments,
        feeTransfers,
        refunds,
        transfersOut,
        investmentPlacements,
        investmentReturns,
        broughtForward);
  }

  private LedgerTotals withPayments(BigDecimal value) {
    return new LedgerTotals(
        deposits,
        transfersIn,
        value,
        feeTransfers,
        refunds,
        transfersOut,
        investmentPlacements,
        investmentReturns,
        broughtForward);
  }

  private LedgerTotals withFeeTransfers(BigDecimal value) {
    return new LedgerTotals(
        deposits,
        transfersIn,
        payments,
        value,
        refunds,
        transfersOut,
        investmentPlacements,
        investmentReturns,
        broughtForward);
  }

  private LedgerTotals withRefunds(BigDecimal value) {
    return new LedgerTotals(
        deposits,
        transfersIn,
        payments,
        feeTransfers,
        value,
        transfersOut,
        investmentPlacements,
        investmentReturns,
        broughtForward);
  }

  private LedgerTotals withTransfersOut(BigDecimal value) {
    return new LedgerTotals(
        deposits,
        transfersIn,
        payments,
        feeTransfers,
        refunds,
        value,
        investmentPlacements,
        investmentReturns,
        broughtForward);
  }

  private LedgerTotals withInvestmentPlacements(BigDecimal value) {
    return new LedgerTotals(
        deposits,
        transfersIn,
        payments,
        feeTransfers,
        refunds,
        transfersOut,
        value,
        investmentReturns,
        broughtForward);
  }

  private LedgerTotals withInvestmentReturns(BigDecimal value) {
    return new LedgerTotals(
        deposits,
        transfersIn,
        payments,
        feeTransfers,
        refunds,
        transfersOut,
        investmentPlacements,
        value,
        broughtForward);
  }

  private LedgerTotals withBroughtForward(BigDecimal value) {
    return new LedgerTotals(
        deposits,
        transfersIn,
        payments,
        feeTransfers,
        refunds,
        transfersOut,
        investmentPlacements,
        investmentReturns,
        value);
  }

  /**
   * Folds per-type rows into one {@code LedgerTotals} per client (absent client = {@link #ZERO}). A
   * row's {@code total} is a signed {@code ledger_effect} sum; × the type's {@code ledgerSign()}
   * turns it into the type's posted amount.
   */
  public static Map<UUID, LedgerTotals> byCustomer(List<CustomerTypeTotalRow> rows) {
    var totals = new HashMap<UUID, LedgerTotals>();
    for (var row : rows) {
      var amount =
          TrustTransactionType.find(row.getTransactionType())
              .map(type -> signed(type, row.getTotal()))
              .orElse(BigDecimal.ZERO);
      totals.merge(
          row.getCustomerId(), ZERO.plus(row.getTransactionType(), amount), LedgerTotals::add);
    }
    return totals;
  }

  /**
   * {@code value × type.ledgerSign()} — the sign is ±1, so this also maps a net back to an amount.
   */
  private static BigDecimal signed(TrustTransactionType type, BigDecimal value) {
    return value.multiply(BigDecimal.valueOf(type.ledgerSign()));
  }

  private LedgerTotals add(LedgerTotals other) {
    return new LedgerTotals(
        deposits.add(other.deposits),
        transfersIn.add(other.transfersIn),
        payments.add(other.payments),
        feeTransfers.add(other.feeTransfers),
        refunds.add(other.refunds),
        transfersOut.add(other.transfersOut),
        investmentPlacements.add(other.investmentPlacements),
        investmentReturns.add(other.investmentReturns),
        broughtForward.add(other.broughtForward));
  }
}
