package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;

/**
 * What a posted trust transaction does to the books (ADR-378, §16.2.1): the ledger it moves, its
 * signed effect on the cashbook (what the bank sees) and its signed effect on that ledger. Built
 * only from {@link TrustTransactionType#effectFor(BigDecimal)} or {@link #zero(TrustLedger)} and
 * written to the row only by {@link TrustPostingService}.
 */
public record TrustLedgerEffect(
    TrustLedger ledger, BigDecimal cashbookEffect, BigDecimal ledgerEffect) {

  /** The contra effect (used by the 649B reversal model). */
  public TrustLedgerEffect negated() {
    return new TrustLedgerEffect(ledger, cashbookEffect.negate(), ledgerEffect.negate());
  }

  /** No movement on either side — unposted rows and, in 646A, reversals (HEAD semantics). */
  public static TrustLedgerEffect zero(TrustLedger ledger) {
    return new TrustLedgerEffect(ledger, BigDecimal.ZERO, BigDecimal.ZERO);
  }
}
