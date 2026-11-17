package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;

/**
 * Every book-side term of EQ-2 for one trust account as at a date (Phase 92 646C, architecture
 * §16.3.3, ADR-377): the cashbook and the six creditor terms. A row of {@link
 * TrustLedgerQueryRepository#position}.
 */
public interface TrustPositionRow {

  BigDecimal getCashbook();

  BigDecimal getClientLedgers();

  BigDecimal getInvestmentCreditors();

  BigDecimal getFundLedger();

  BigDecimal getUnidentified();

  BigDecimal getOtherNonClient();

  BigDecimal getInterAccount();
}
