package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of {@link TrustTransactionRepository#findMonthlyBalanceMovements}: the net cashbook and
 * client-ledger movement of one SA calendar month on one trust account. {@code monthStart} is the
 * first day of the month, or null for the opening row (everything dated before the window).
 */
public interface TrustMonthlyMovementProjection {

  LocalDate getMonthStart();

  BigDecimal getCashbookNet();

  BigDecimal getLedgerNet();
}
