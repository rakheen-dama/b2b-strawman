package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountType;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * The transaction-type registry (Phase 92, ADR-378, architecture §16.2.1): everything any consumer
 * needs to know about a type, in one place. The column {@code trust_transactions.transaction_type}
 * stays {@code varchar(20)} and the entity field stays a {@code String} (R5, ADR-238); callers use
 * {@link #of(String)}. {@code TrustTransactionTypeSchemaTest} pins {@code chk_trust_txn_type} to
 * these constants: the 25 types. 646A carried the seven kept types; 646B added the eighteen
 * non-client, investment and inter-account types; no slice before 647A posts them.
 */
public enum TrustTransactionType {
  DEPOSIT(
      1,
      TrustLedger.CLIENT,
      1,
      null,
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
      null,
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
      null,
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
      null,
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
      null,
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
      null,
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
      null,
      Approval.WITH_ORIGINAL,
      Reversal.NONE,
      null,
      true,
      "trust_reversal",
      "Reversal",
      "Reversal"),
  FUND_INTEREST(
      1,
      TrustLedger.NON_CLIENT,
      1,
      TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES,
      Approval.NONE,
      Reversal.IMMEDIATE,
      Origin.BANK,
      false,
      "trust_fund_interest",
      "Interest received for the Legal Practitioners' Fidelity Fund",
      "Interest received for the Legal Practitioners' Fidelity Fund"),
  FUND_SWEEP(
      -1,
      TrustLedger.NON_CLIENT,
      -1,
      TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES,
      Approval.NONE,
      Reversal.IMMEDIATE,
      Origin.BANK,
      false,
      "trust_fund_sweep",
      "Interest paid to the Legal Practitioners' Fidelity Fund",
      "Interest paid to the Legal Practitioners' Fidelity Fund"),
  BANK_CHARGE(
      -1,
      TrustLedger.NON_CLIENT,
      -1,
      TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES,
      Approval.NONE,
      Reversal.IMMEDIATE,
      Origin.BANK,
      false,
      "trust_bank_charge",
      "Bank charge",
      "Bank charge"),
  CHARGE_REFUND(
      1,
      TrustLedger.NON_CLIENT,
      1,
      TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES,
      Approval.NONE,
      Reversal.IMMEDIATE,
      Origin.BANK,
      false,
      "trust_charge_refund",
      "Bank charge refunded from the business account",
      "Bank charge refunded from the business account"),
  UNIDENTIFIED_DEPOSIT(
      1,
      TrustLedger.NON_CLIENT,
      1,
      TrustCreditorAccountType.UNIDENTIFIED_DEPOSITS,
      Approval.NONE,
      Reversal.OWNER,
      Origin.BANK,
      false,
      "trust_unidentified_deposit",
      "Unidentified deposit",
      "Unidentified deposit"),
  UNIDENT_ALLOC_OUT(
      0,
      TrustLedger.NON_CLIENT,
      -1,
      TrustCreditorAccountType.UNIDENTIFIED_DEPOSITS,
      Approval.REQUIRED,
      Reversal.PAIR,
      Origin.FIRM,
      false,
      "trust_unident_alloc_out",
      "Unidentified deposit allocated",
      "Unidentified deposit allocated"),
  UNIDENT_ALLOC_IN(
      0,
      TrustLedger.CLIENT,
      1,
      null,
      Approval.WITH_PAIR,
      Reversal.VIA_PAIR,
      Origin.FIRM,
      true,
      "trust_unident_alloc_in",
      "Deposit identified and allocated",
      "Deposit identified and allocated"),
  UNIDENT_FUND_PAY(
      -1,
      TrustLedger.NON_CLIENT,
      -1,
      TrustCreditorAccountType.UNIDENTIFIED_DEPOSITS,
      Approval.REQUIRED,
      Reversal.IMMEDIATE,
      Origin.FIRM,
      false,
      "trust_unident_fund_pay",
      "Unidentified deposit paid to the Fund",
      "Unidentified deposit paid to the Fund"),
  INV_PLACE(
      -1,
      TrustLedger.CLIENT,
      -1,
      null,
      Approval.REQUIRED,
      Reversal.NONE,
      Origin.FIRM,
      true,
      "trust_inv_place",
      "Placed in section 86(4) investment",
      "Placed in section 86(4) investment"),
  INV_RECEIVE(
      1,
      TrustLedger.CLIENT_INVESTMENT,
      1,
      null,
      Approval.WITH_PAIR,
      Reversal.NONE,
      Origin.FIRM,
      false,
      "trust_inv_receive",
      "Investment capital received",
      "Investment capital received"),
  INV_INTEREST(
      1,
      TrustLedger.CLIENT_INVESTMENT,
      1,
      null,
      Approval.NONE,
      Reversal.OWNER,
      Origin.FIRM,
      true,
      "trust_inv_interest",
      "Section 86(4) investment interest",
      "Section 86(4) investment interest"),
  FUND_INV_SHARE(
      1,
      TrustLedger.NON_CLIENT,
      1,
      TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES,
      Approval.NONE,
      Reversal.OWNER,
      Origin.FIRM,
      false,
      "trust_fund_inv_share",
      "Fund share of section 86(4) interest (5%)",
      "Fund share of section 86(4) interest (5%)"),
  INV_WITHDRAW(
      -1,
      TrustLedger.CLIENT_INVESTMENT,
      -1,
      null,
      Approval.NONE,
      Reversal.NONE,
      Origin.FIRM,
      false,
      "trust_inv_withdraw",
      "Investment withdrawn",
      "Investment withdrawn"),
  INV_RETURN(
      1,
      TrustLedger.CLIENT,
      1,
      null,
      Approval.WITH_PAIR,
      Reversal.NONE,
      Origin.FIRM,
      true,
      "trust_inv_return",
      "Investment returned to trust",
      "Investment returned to trust"),
  FIRM_INV_PLACE(
      -1,
      TrustLedger.INTER_ACCOUNT,
      -1,
      null,
      Approval.REQUIRED,
      Reversal.NONE,
      Origin.FIRM,
      false,
      "trust_firm_inv_place",
      "Placed in section 86(3) investment account",
      "Placed in section 86(3) investment account"),
  FIRM_INV_RECEIVE(
      1,
      TrustLedger.INTER_ACCOUNT,
      1,
      null,
      Approval.WITH_PAIR,
      Reversal.NONE,
      Origin.FIRM,
      false,
      "trust_firm_inv_receive",
      "Section 86(3) investment capital received",
      "Section 86(3) investment capital received"),
  FIRM_INV_WITHDRAW(
      -1,
      TrustLedger.INTER_ACCOUNT,
      -1,
      null,
      Approval.NONE,
      Reversal.NONE,
      Origin.FIRM,
      false,
      "trust_firm_inv_withdraw",
      "Section 86(3) investment withdrawn",
      "Section 86(3) investment withdrawn"),
  FIRM_INV_RETURN(
      1,
      TrustLedger.INTER_ACCOUNT,
      1,
      null,
      Approval.WITH_PAIR,
      Reversal.NONE,
      Origin.FIRM,
      false,
      "trust_firm_inv_return",
      "Section 86(3) investment returned to trust",
      "Section 86(3) investment returned to trust");

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
  private final TrustCreditorAccountType creditorAccountType;
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
      TrustCreditorAccountType creditorAccountType,
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
    this.creditorAccountType = creditorAccountType;
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

  /**
   * For a {@link TrustLedger#NON_CLIENT} type: which creditor account of the trust account the row
   * moves (ADR-376); null for every other ledger.
   */
  public TrustCreditorAccountType creditorAccountType() {
    return creditorAccountType;
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
