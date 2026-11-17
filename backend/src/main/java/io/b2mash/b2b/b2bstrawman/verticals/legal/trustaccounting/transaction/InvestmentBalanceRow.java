package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One investment's balance — a row of {@link TrustLedgerQueryRepository#investmentBalances} (648A).
 */
public interface InvestmentBalanceRow {

  UUID getInvestmentId();

  BigDecimal getBalance();
}
