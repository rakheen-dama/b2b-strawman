package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import static io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.DEPOSIT;
import static io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.FEE_TRANSFER;
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
 */
public record LedgerTotals(
    BigDecimal deposits,
    BigDecimal transfersIn,
    BigDecimal payments,
    BigDecimal feeTransfers,
    BigDecimal refunds,
    BigDecimal transfersOut) {

  public static final LedgerTotals ZERO =
      new LedgerTotals(
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO);

  /**
   * Σ total × {@code ledgerSign()} over the types (deposits and transfers in add, the rest
   * subtract).
   */
  public BigDecimal derivedBalance() {
    return signed(DEPOSIT, deposits)
        .add(signed(TRANSFER_IN, transfersIn))
        .add(signed(PAYMENT, payments))
        .add(signed(FEE_TRANSFER, feeTransfers))
        .add(signed(REFUND, refunds))
        .add(signed(TRANSFER_OUT, transfersOut));
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
      case DEPOSIT, UNIDENT_ALLOC_IN ->
          new LedgerTotals(
              deposits.add(amount), transfersIn, payments, feeTransfers, refunds, transfersOut);
      // To the client's general ledger a section 86(4) placement is money transferred out to their
      // investment, and its return a transfer back in (648A): INV_PLACE has TRANSFER_OUT's
      // ledgerSign (−1) and INV_RETURN TRANSFER_IN's (+1), so derivedBalance() still equals
      // clientBalance. 648E adds the separate "Invested (section 86(4))" figure.
      case TRANSFER_IN, INV_RETURN ->
          new LedgerTotals(
              deposits, transfersIn.add(amount), payments, feeTransfers, refunds, transfersOut);
      case PAYMENT ->
          new LedgerTotals(
              deposits, transfersIn, payments.add(amount), feeTransfers, refunds, transfersOut);
      case FEE_TRANSFER ->
          new LedgerTotals(
              deposits, transfersIn, payments, feeTransfers.add(amount), refunds, transfersOut);
      case REFUND ->
          new LedgerTotals(
              deposits, transfersIn, payments, feeTransfers, refunds.add(amount), transfersOut);
      case TRANSFER_OUT, INV_PLACE ->
          new LedgerTotals(
              deposits, transfersIn, payments, feeTransfers, refunds, transfersOut.add(amount));
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
          FIRM_INV_RETURN ->
          this;
    };
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
        transfersOut.add(other.transfersOut));
  }
}
