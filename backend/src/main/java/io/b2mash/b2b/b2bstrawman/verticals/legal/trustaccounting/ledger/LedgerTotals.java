package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.CustomerTypeTotalRow;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A client's posted trust totals on one account, by movement (G89-F13). Derived from the ledger
 * itself ({@code TrustTransactionRepository.sumPostedByCustomerAndType}: RECORDED/APPROVED, no
 * REVERSAL), not from the card's running counters — those never counted refunds or transfers and
 * kept a reversed payment — so the totals always reconcile: {@link #derivedBalance()} equals the
 * client card balance.
 */
public record LedgerTotals(
    BigDecimal deposits,
    BigDecimal interestCredited,
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
          BigDecimal.ZERO,
          BigDecimal.ZERO);

  /** Deposits + Interest + Transfers in − Payments − Fee transfers − Refunds − Transfers out. */
  public BigDecimal derivedBalance() {
    return deposits
        .add(interestCredited)
        .add(transfersIn)
        .subtract(payments)
        .subtract(feeTransfers)
        .subtract(refunds)
        .subtract(transfersOut);
  }

  /**
   * Adds one type's posted total. Types that do not belong to a client ledger (INTEREST_LPFF, which
   * carries no client, and REVERSAL, which the query excludes) leave the totals unchanged.
   */
  public LedgerTotals plus(String transactionType, BigDecimal amount) {
    var value = amount != null ? amount : BigDecimal.ZERO;
    return switch (transactionType) {
      case "DEPOSIT" ->
          new LedgerTotals(
              deposits.add(value),
              interestCredited,
              transfersIn,
              payments,
              feeTransfers,
              refunds,
              transfersOut);
      case "INTEREST_CREDIT" ->
          new LedgerTotals(
              deposits,
              interestCredited.add(value),
              transfersIn,
              payments,
              feeTransfers,
              refunds,
              transfersOut);
      case "TRANSFER_IN" ->
          new LedgerTotals(
              deposits,
              interestCredited,
              transfersIn.add(value),
              payments,
              feeTransfers,
              refunds,
              transfersOut);
      case "PAYMENT" ->
          new LedgerTotals(
              deposits,
              interestCredited,
              transfersIn,
              payments.add(value),
              feeTransfers,
              refunds,
              transfersOut);
      case "FEE_TRANSFER" ->
          new LedgerTotals(
              deposits,
              interestCredited,
              transfersIn,
              payments,
              feeTransfers.add(value),
              refunds,
              transfersOut);
      case "REFUND" ->
          new LedgerTotals(
              deposits,
              interestCredited,
              transfersIn,
              payments,
              feeTransfers,
              refunds.add(value),
              transfersOut);
      case "TRANSFER_OUT" ->
          new LedgerTotals(
              deposits,
              interestCredited,
              transfersIn,
              payments,
              feeTransfers,
              refunds,
              transfersOut.add(value));
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
        interestCredited.add(other.interestCredited),
        transfersIn.add(other.transfersIn),
        payments.add(other.payments),
        feeTransfers.add(other.feeTransfers),
        refunds.add(other.refunds),
        transfersOut.add(other.transfersOut));
  }
}
