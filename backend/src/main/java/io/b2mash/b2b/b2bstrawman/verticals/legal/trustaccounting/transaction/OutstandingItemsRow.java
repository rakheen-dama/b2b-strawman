package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;

/**
 * The outstanding items of one trust account as at a date (Phase 92 646C, architecture §16.3.3,
 * ADR-377): the cashbook receipts and payments the bank had not shown by that date. Both are
 * non-negative. A row of {@link TrustLedgerQueryRepository#outstanding}.
 */
public interface OutstandingItemsRow {

  BigDecimal getOutstandingDeposits();

  BigDecimal getOutstandingPayments();
}
