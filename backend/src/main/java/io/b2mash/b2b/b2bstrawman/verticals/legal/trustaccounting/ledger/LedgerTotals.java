package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.CustomerTypeTotalRow;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A client's posted trust totals on one account, grouped by transaction type (G89-F13). Derived
 * from the ledger itself ({@code TrustTransactionRepository.sumPostedByCustomerAndType}:
 * RECORDED/APPROVED, no REVERSAL), so the totals always reconcile: {@link #derivedBalance()} equals
 * the derived client balance. Each total's sign in that sum is its type's {@code
 * TrustTransactionType.ledgerSign()}.
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

  /** Deposits + Transfers in − Payments − Fee transfers − Refunds − Transfers out. */
  public BigDecimal derivedBalance() {
    return deposits
        .add(transfersIn)
        .subtract(payments)
        .subtract(feeTransfers)
        .subtract(refunds)
        .subtract(transfersOut);
  }

  /**
   * Adds one type's posted total. A type that does not belong to these totals (REVERSAL, which the
   * query excludes, or an unknown code) leaves the totals unchanged.
   */
  public LedgerTotals plus(String transactionType, BigDecimal amount) {
    var value = amount != null ? amount : BigDecimal.ZERO;
    return switch (transactionType) {
      case "DEPOSIT" ->
          new LedgerTotals(
              deposits.add(value), transfersIn, payments, feeTransfers, refunds, transfersOut);
      case "TRANSFER_IN" ->
          new LedgerTotals(
              deposits, transfersIn.add(value), payments, feeTransfers, refunds, transfersOut);
      case "PAYMENT" ->
          new LedgerTotals(
              deposits, transfersIn, payments.add(value), feeTransfers, refunds, transfersOut);
      case "FEE_TRANSFER" ->
          new LedgerTotals(
              deposits, transfersIn, payments, feeTransfers.add(value), refunds, transfersOut);
      case "REFUND" ->
          new LedgerTotals(
              deposits, transfersIn, payments, feeTransfers, refunds.add(value), transfersOut);
      case "TRANSFER_OUT" ->
          new LedgerTotals(
              deposits, transfersIn, payments, feeTransfers, refunds, transfersOut.add(value));
      default -> this;
    };
  }

  /**
   * Folds per-type rows into one {@code LedgerTotals} per client (absent client = {@link #ZERO}).
   */
  public static Map<UUID, LedgerTotals> byCustomer(List<CustomerTypeTotalRow> rows) {
    var totals = new HashMap<UUID, LedgerTotals>();
    for (var row : rows) {
      totals.merge(
          row.getCustomerId(),
          ZERO.plus(row.getTransactionType(), row.getTotal()),
          LedgerTotals::add);
    }
    return totals;
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
