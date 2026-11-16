package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

/**
 * Which way a trust transaction moves its own creditor ledger (Phase 92, 646E, arch §16.4.1): the
 * sign {@code TrustPostingService} writes to {@code ledger_effect}. {@link #IN} credits the ledger,
 * {@link #OUT} debits it. It is independent of status, so an unposted row (an awaiting payment, a
 * pending contra) still shows the way it will move.
 *
 * <p>Ledger sign, not cashbook sign: a {@code TRANSFER_IN} is an inflow on the client's ledger
 * though the cashbook does not move — the meaning the screens have always given the arrow.
 */
public enum TrustTransactionDirection {
  IN,
  OUT,
  /**
   * No direction of its own: {@code REVERSAL} at type level (it takes its original's opposite), or
   * a row whose type or original cannot be resolved.
   */
  NEUTRAL;

  /** The type-level direction: the registry's ledger sign; {@code REVERSAL} is NEUTRAL. */
  public static TrustTransactionDirection of(TrustTransactionType type) {
    if (type == null || type.ledgerSign() == 0) {
      return NEUTRAL;
    }
    return type.ledgerSign() > 0 ? IN : OUT;
  }

  /**
   * The direction of one row. A {@code REVERSAL} is the opposite of the type it reverses ({@code
   * reversedType}, the original's stored code) — the sign of the contra's own {@code ledger_effect}
   * once posted ({@code TrustPostingService.contraOf}); NEUTRAL when the original cannot be
   * resolved. Every other row takes its type's direction. Unknown codes never throw.
   */
  public static TrustTransactionDirection ofRow(String transactionType, String reversedType) {
    var type = TrustTransactionType.find(transactionType);
    if (type.isEmpty()) {
      return NEUTRAL;
    }
    if (type.get() == TrustTransactionType.REVERSAL) {
      return TrustTransactionType.find(reversedType)
          .filter(original -> original != TrustTransactionType.REVERSAL)
          .map(original -> of(original).opposite())
          .orElse(NEUTRAL);
    }
    return of(type.get());
  }

  /** IN ↔ OUT; NEUTRAL stays NEUTRAL. */
  public TrustTransactionDirection opposite() {
    return switch (this) {
      case IN -> OUT;
      case OUT -> IN;
      case NEUTRAL -> NEUTRAL;
    };
  }
}
