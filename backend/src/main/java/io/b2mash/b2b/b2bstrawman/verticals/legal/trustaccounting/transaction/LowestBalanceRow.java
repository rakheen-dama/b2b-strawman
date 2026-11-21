package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The lowest a derived trust balance stands from a date on (FU-5), and the earliest date it stands
 * there: a row of {@link TrustLedgerQueryRepository#lowestClientBalanceFrom} and {@link
 * TrustLedgerQueryRepository#lowestMatterCellBalanceFrom}.
 */
public interface LowestBalanceRow {

  LocalDate getAsAt();

  BigDecimal getBalance();
}
