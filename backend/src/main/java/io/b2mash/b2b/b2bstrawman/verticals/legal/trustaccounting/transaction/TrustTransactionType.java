package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * The transaction-type registry (Phase 92, ADR-378, architecture §16.2.1): everything any consumer
 * needs to know about a type, in one place. The column {@code trust_transactions.transaction_type}
 * stays {@code varchar(20)} and the entity field stays a {@code String} (R5, ADR-238); callers use
 * {@link #of(String)}. {@code TrustTransactionTypeSchemaTest} pins {@code chk_trust_txn_type} to
 * these constants. 646A carries the seven kept types; 646B adds the eighteen new ones.
 */
public enum TrustTransactionType {
  DEPOSIT(
      1,
      TrustLedger.CLIENT,
      1,
      Approval.NONE,
      Reversal.NEEDS_APPROVAL,
      Origin.FIRM,
      true,
      "trust_deposit",
      "Deposit",
      "Deposit"),
  PAYMENT(
      -1,
      TrustLedger.CLIENT,
      -1,
      Approval.REQUIRED,
      Reversal.IMMEDIATE,
      Origin.FIRM,
      true,
      "trust_payment",
      "Payment",
      "Payment"),
  FEE_TRANSFER(
      -1,
      TrustLedger.CLIENT,
      -1,
      Approval.REQUIRED,
      Reversal.IMMEDIATE,
      Origin.FIRM,
      true,
      "trust_fee_transfer",
      "Fee transfer",
      "Fee transfer"),
  REFUND(
      -1,
      TrustLedger.CLIENT,
      -1,
      Approval.REQUIRED,
      Reversal.IMMEDIATE,
      Origin.FIRM,
      true,
      "trust_refund",
      "Refund",
      "Refund"),
  TRANSFER_OUT(
      0,
      TrustLedger.CLIENT,
      -1,
      Approval.NONE,
      Reversal.PAIR,
      Origin.FIRM,
      true,
      "trust_transfer",
      "Transfer out",
      "Transfer out"),
  TRANSFER_IN(
      0,
      TrustLedger.CLIENT,
      1,
      Approval.NONE,
      Reversal.VIA_PAIR,
      Origin.FIRM,
      true,
      "trust_transfer",
      "Transfer in",
      "Transfer in"),
  /**
   * No effect of its own (§16.10.3): a contra entry (649B, ADR-378) carrying the negation of its
   * original's effect on its original's ledger, on its own date; the original stays posted and is
   * marked {@code REVERSED}. Client-visible since 649B: the portal replays the reversed original
   * and its contra together (each with its own effect), so the client sees both movements.
   */
  REVERSAL(
      0,
      null,
      0,
      Approval.WITH_ORIGINAL,
      Reversal.NONE,
      null,
      true,
      "trust_reversal",
      "Reversal",
      "Reversal");

  /** How a row of the type is posted. */
  public enum Approval {
    /** Posted when recorded. */
    NONE,
    /** Saved AWAITING_APPROVAL; posted on final approval (maker-checker). */
    REQUIRED,
    /** Posted with its pair (later slices). */
    WITH_PAIR,
    /** Approved with the original it reverses. */
    WITH_ORIGINAL
  }

  /** How a posted row of the type is reversed. */
  public enum Reversal {
    /** The contra entry posts at once. */
    IMMEDIATE,
    /** The contra entry is saved AWAITING_APPROVAL and posts on final approval. */
    NEEDS_APPROVAL,
    /** Reversed together with its paired leg (TRANSFER_OUT reverses its TRANSFER_IN). */
    PAIR,
    /** Reversed only through its pair (a TRANSFER_IN is reversed via its TRANSFER_OUT). */
    VIA_PAIR,
    /**
     * Reversed only through its owner's own action (unmatching its statement line, reversing its
     * interest entry — 647A / 648B); the generic reverse endpoint refuses it.
     */
    OWNER,
    /** Cannot be reversed. */
    NONE
  }

  /** Who originates a row of the type. */
  public enum Origin {
    FIRM,
    BANK
  }

  private final int cashbookSign;
  private final TrustLedger ledger;
  private final int ledgerSign;
  private final Approval approval;
  private final Reversal reversal;
  private final Origin origin;
  private final boolean clientVisible;
  private final String auditKey;
  private final String label;
  private final String clientLabel;

  TrustTransactionType(
      int cashbookSign,
      TrustLedger ledger,
      int ledgerSign,
      Approval approval,
      Reversal reversal,
      Origin origin,
      boolean clientVisible,
      String auditKey,
      String label,
      String clientLabel) {
    this.cashbookSign = cashbookSign;
    this.ledger = ledger;
    this.ledgerSign = ledgerSign;
    this.approval = approval;
    this.reversal = reversal;
    this.origin = origin;
    this.clientVisible = clientVisible;
    this.auditKey = auditKey;
    this.label = label;
    this.clientLabel = clientLabel;
  }

  /** The type for a stored code; an unknown code is refused (400). */
  public static TrustTransactionType of(String code) {
    return find(code)
        .orElseThrow(
            () ->
                new InvalidStateException(
                    "Unknown trust transaction type", "Unknown trust transaction type: " + code));
  }

  /** Lenient lookup for readers that may meet a legacy or unknown code (T15). */
  public static Optional<TrustTransactionType> find(String code) {
    if (code == null) {
      return Optional.empty();
    }
    for (var type : values()) {
      if (type.name().equals(code)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  /**
   * Effects of a row of this type for {@code amount}: {@code cashbookSign × amount} on the
   * cashbook, {@code ledgerSign × amount} on {@link #ledger()}. REVERSAL has none of its own.
   */
  public TrustLedgerEffect effectFor(BigDecimal amount) {
    if (this == REVERSAL) {
      throw new IllegalStateException("REVERSAL takes its effect from its original");
    }
    return new TrustLedgerEffect(
        ledger,
        amount.multiply(BigDecimal.valueOf(cashbookSign)),
        amount.multiply(BigDecimal.valueOf(ledgerSign)));
  }

  public int cashbookSign() {
    return cashbookSign;
  }

  /** The ledger a row of this type moves; null for REVERSAL (its original's ledger). */
  public TrustLedger ledger() {
    return ledger;
  }

  public int ledgerSign() {
    return ledgerSign;
  }

  public Approval approval() {
    return approval;
  }

  public Reversal reversal() {
    return reversal;
  }

  /** Null for REVERSAL (it inherits its original's origin). */
  public Origin origin() {
    return origin;
  }

  public boolean clientVisible() {
    return clientVisible;
  }

  public String auditKey() {
    return auditKey;
  }

  public String label() {
    return label;
  }

  public String clientLabel() {
    return clientLabel;
  }
}
