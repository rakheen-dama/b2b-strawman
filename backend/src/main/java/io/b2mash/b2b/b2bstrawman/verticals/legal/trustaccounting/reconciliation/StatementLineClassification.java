package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import java.util.Optional;

/**
 * What a statement line is recorded as (Phase 92, ADR-380, architecture §16.3.5). Each value names
 * the line sign it accepts and the trust transaction type it posts. 647B adds {@code
 * UNIDENTIFIED_DEPOSIT} and 648B {@code INVESTMENT_INTEREST}, each with its own logic (the DB CHECK
 * {@code chk_bank_line_classification} already admits all six — V20).
 */
public enum StatementLineClassification {
  FUND_INTEREST(1, TrustTransactionType.FUND_INTEREST),
  FUND_SWEEP(-1, TrustTransactionType.FUND_SWEEP),
  BANK_CHARGE(-1, TrustTransactionType.BANK_CHARGE),
  CHARGE_REFUND(1, TrustTransactionType.CHARGE_REFUND);

  /** +1: recorded from a credit line; -1: from a debit line. */
  private final int lineSign;

  private final TrustTransactionType transactionType;

  StatementLineClassification(int lineSign, TrustTransactionType transactionType) {
    this.lineSign = lineSign;
    this.transactionType = transactionType;
  }

  /** +1 for a credit line, -1 for a debit line. */
  public int lineSign() {
    return lineSign;
  }

  /** The trust transaction type the classification posts. */
  public TrustTransactionType transactionType() {
    return transactionType;
  }

  /** The display label — the posted type's registry label (one source of words). */
  public String label() {
    return transactionType.label();
  }

  /** Lenient lookup for a stored code: a code this slice does not know returns empty. */
  public static Optional<StatementLineClassification> find(String code) {
    if (code == null) {
      return Optional.empty();
    }
    for (var classification : values()) {
      if (classification.name().equals(code)) {
        return Optional.of(classification);
      }
    }
    return Optional.empty();
  }
}
