package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One investment held in a trust account as at a date (Phase 92 649C, EQ-5, architecture §16.1.2,
 * §16.3.9): the record's side — the principal once its placement row is posted, the net of its
 * posted interest entries, the amount withdrawn — beside its posted rows' balance on the account
 * holding it. EQ-5 holds when {@code balance = placed + interest − withdrawn}. A row of {@link
 * TrustLedgerQueryRepository#investmentTies}.
 */
public interface InvestmentTieRow {

  UUID getInvestmentId();

  String getInstitution();

  String getAccountNumber();

  BigDecimal getPlaced();

  BigDecimal getInterest();

  BigDecimal getWithdrawn();

  BigDecimal getBalance();
}
