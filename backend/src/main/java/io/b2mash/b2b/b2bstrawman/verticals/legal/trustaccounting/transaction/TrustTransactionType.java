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
 * these constants: the 36 types. 646A carried the seven kept types; 646B added the eighteen
 * non-client, investment and inter-account types; 657A added the eleven take-on types ({@link
 * Origin#TAKE_ON}, posted only by {@code TrustPostingService.recordOpening}).
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
      "Deposit",
      Clearance.BANK),
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
      "Payment",
      Clearance.BANK),
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
      "Fee transfer",
      Clearance.BANK),
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
      "Refund",
      Clearance.BANK),
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
      "Transfer out",
      Clearance.BANK),
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
      "Transfer in",
      Clearance.BANK),
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
      "Reversal",
      Clearance.BANK),
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
      "Interest received for the Legal Practitioners' Fidelity Fund",
      Clearance.BANK),
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
      "Interest paid to the Legal Practitioners' Fidelity Fund",
      Clearance.BANK),
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
      "Bank charge",
      Clearance.BANK),
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
      "Bank charge refunded from the business account",
      Clearance.BANK),
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
      "Unidentified deposit",
      Clearance.BANK),
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
      "Unidentified deposit allocated",
      Clearance.BANK),
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
      "Deposit identified and allocated",
      Clearance.BANK),
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
      "Unidentified deposit paid to the Fund",
      Clearance.BANK),
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
      "Placed in section 86(4) investment",
      Clearance.BANK),
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
      "Investment capital received",
      Clearance.BANK),
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
      "Section 86(4) investment interest",
      Clearance.BANK),
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
      "Fund share of section 86(4) interest (5%)",
      Clearance.BANK),
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
      "Investment withdrawn",
      Clearance.BANK),
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
      "Investment returned to trust",
      Clearance.BANK),
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
      "Placed in section 86(3) investment",
      "Placed in section 86(3) investment",
      Clearance.BANK),
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
      "Section 86(3) investment capital received",
      Clearance.BANK),
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
      "Section 86(3) investment withdrawn",
      Clearance.BANK),
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
      "Section 86(3) investment returned to trust",
      Clearance.BANK),

  // --- Phase 93 / 657A (ADR-386, architecture §17.2.8): the opening entries of a trust take-on.
  // Created only by TrustPostingService.recordOpening (Origin.TAKE_ON), posted APPROVED under the
  // take-on's trust sign-off (Approval.AT_SIGN_OFF), dated at the cutover date. A cleared row
  // (Clearance.AT_CUTOVER) is never outstanding and never matched; an item outstanding at cutover
  // (TAKEON_PAY_UNPRES, TAKEON_DEP_TRANSIT) is an ordinary matchable row on the client's matter,
  // paired with a cleared offset, and reverses like the payment / deposit it stands for (FD-25).
  TAKEON_CLIENT(
      1,
      TrustLedger.CLIENT,
      1,
      null,
      Approval.AT_SIGN_OFF,
      Reversal.NONE,
      Origin.TAKE_ON,
      true,
      "trust_takeon_client",
      "Balance brought forward — take-on",
      "Balance brought forward — take-on",
      Clearance.AT_CUTOVER),
  TAKEON_FUND(
      1,
      TrustLedger.NON_CLIENT,
      1,
      TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES,
      Approval.AT_SIGN_OFF,
      Reversal.NONE,
      Origin.TAKE_ON,
      false,
      "trust_takeon_fund",
      "Fund ledger brought forward — take-on",
      "Fund ledger brought forward — take-on",
      Clearance.AT_CUTOVER),
  /**
   * The Fund ledger in debit at cutover (ADR-381, FD-12): amounts are positive, signs are fixed.
   */
  TAKEON_FUND_DEBIT(
      -1,
      TrustLedger.NON_CLIENT,
      -1,
      TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES,
      Approval.AT_SIGN_OFF,
      Reversal.NONE,
      Origin.TAKE_ON,
      false,
      "trust_takeon_fund_debit",
      "Fund ledger brought forward in debit — take-on",
      "Fund ledger brought forward in debit — take-on",
      Clearance.AT_CUTOVER),
  TAKEON_UNIDENTIFIED(
      1,
      TrustLedger.NON_CLIENT,
      1,
      TrustCreditorAccountType.UNIDENTIFIED_DEPOSITS,
      Approval.AT_SIGN_OFF,
      Reversal.NONE,
      Origin.TAKE_ON,
      false,
      "trust_takeon_unidentified",
      "Unidentified deposit brought forward — take-on",
      "Unidentified deposit brought forward — take-on",
      Clearance.AT_CUTOVER),
  TAKEON_INVESTMENT(
      1,
      TrustLedger.CLIENT_INVESTMENT,
      1,
      null,
      Approval.AT_SIGN_OFF,
      Reversal.NONE,
      Origin.TAKE_ON,
      false,
      "trust_takeon_investment",
      "Section 86(4) investment brought forward — take-on",
      "Section 86(4) investment brought forward — take-on",
      Clearance.AT_CUTOVER),
  TAKEON_FIRM_INV_OUT(
      -1,
      TrustLedger.INTER_ACCOUNT,
      -1,
      null,
      Approval.AT_SIGN_OFF,
      Reversal.NONE,
      Origin.TAKE_ON,
      false,
      "trust_takeon_firm_inv_out",
      "Placed in section 86(3) investment — brought forward",
      "Placed in section 86(3) investment — brought forward",
      Clearance.AT_CUTOVER),
  TAKEON_FIRM_INV_IN(
      1,
      TrustLedger.INTER_ACCOUNT,
      1,
      null,
      Approval.AT_SIGN_OFF,
      Reversal.NONE,
      Origin.TAKE_ON,
      false,
      "trust_takeon_firm_inv_in",
      "Section 86(3) investment brought forward — take-on",
      "Section 86(3) investment brought forward — take-on",
      Clearance.AT_CUTOVER),
  /** A cheque or EFT not yet presented at cutover: outstanding, reverses like PAYMENT. */
  TAKEON_PAY_UNPRES(
      -1,
      TrustLedger.CLIENT,
      -1,
      null,
      Approval.AT_SIGN_OFF,
      Reversal.IMMEDIATE,
      Origin.TAKE_ON,
      false,
      "trust_takeon_pay_unpres",
      "Payment not yet presented at take-on",
      "Payment not yet presented at take-on",
      Clearance.BANK),
  TAKEON_PAY_OFFSET(
      1,
      TrustLedger.CLIENT,
      1,
      null,
      Approval.AT_SIGN_OFF,
      Reversal.NONE,
      Origin.TAKE_ON,
      false,
      "trust_takeon_pay_offset",
      "Take-on offset — payment not yet presented",
      "Take-on offset — payment not yet presented",
      Clearance.AT_CUTOVER),
  /** A deposit not yet cleared at cutover: outstanding, reverses like DEPOSIT (needs approval). */
  TAKEON_DEP_TRANSIT(
      1,
      TrustLedger.CLIENT,
      1,
      null,
      Approval.AT_SIGN_OFF,
      Reversal.NEEDS_APPROVAL,
      Origin.TAKE_ON,
      false,
      "trust_takeon_dep_transit",
      "Deposit not yet cleared at take-on",
      "Deposit not yet cleared at take-on",
      Clearance.BANK),
  TAKEON_DEP_OFFSET(
      -1,
      TrustLedger.CLIENT,
      -1,
      null,
      Approval.AT_SIGN_OFF,
      Reversal.NONE,
      Origin.TAKE_ON,
      false,
      "trust_takeon_dep_offset",
      "Take-on offset — deposit not yet cleared",
      "Take-on offset — deposit not yet cleared",
      Clearance.AT_CUTOVER);

  /** How a row of the type is posted. */
  public enum Approval {
    /** Posted when recorded. */
    NONE,
    /** Saved AWAITING_APPROVAL; posted on final approval (maker-checker). */
    REQUIRED,
    /** Posted with its pair (later slices). */
    WITH_PAIR,
    /** Approved with the original it reverses. */
    WITH_ORIGINAL,
    /**
     * Phase 93 (ADR-386, FD-25): posted {@code APPROVED} by {@code
     * TrustPostingService.recordOpening} only, with the take-on's trust signer as approver and the
     * sign-off time as {@code approved_at} — the sign-off is the Rule 54.19 authorisation of the
     * whole position. Every other posting path refuses it; never a pair.
     */
    AT_SIGN_OFF
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
    BANK,
    /**
     * Phase 93 (ADR-386): created only by a trust take-on commit, through {@code
     * TrustPostingService.recordOpening}; {@code record} refuses it.
     */
    TAKE_ON
  }

  /**
   * Phase 93 (ADR-386, §17.2.8): whether a posted row of the type waits for a bank statement line.
   * {@code TrustPostingService} writes it onto the row as {@code cleared_at_cutover}, exactly as it
   * writes the effects, so no SQL needs to know a type's clearance (the ADR-378 principle).
   */
  public enum Clearance {
    /** Outstanding until matched to a statement line dated on or before the as-at date. */
    BANK,
    /** Cleared at the cutover date: never outstanding, never matched to a line. */
    AT_CUTOVER
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
  private final Clearance clearance;

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
      String clientLabel,
      Clearance clearance) {
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
    this.clearance = clearance;
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
   * Which way a row of this type moves its ledger — the sign of {@link #ledgerSign()}; NEUTRAL for
   * REVERSAL (a reversal row takes its original's opposite, {@link
   * TrustTransactionDirection#ofRow}).
   */
  public TrustTransactionDirection direction() {
    return TrustTransactionDirection.of(this);
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

  /**
   * A row of a trust investment (648A, §16.3.7) — derived from the ledger, not listed: every type
   * on an investment's own ledger ({@link TrustLedger#CLIENT_INVESTMENT} for section 86(4), {@link
   * TrustLedger#INTER_ACCOUNT} for section 86(3) — the ledgers {@code chk_trust_txn_ledger_target}
   * ties to {@code investment_id}), and a {@code CLIENT}-ledger type paired with one of them (the
   * placement {@code INV_PLACE} and the return {@code INV_RETURN}). Such a row is never reversed on
   * the generic paths: the investment is withdrawn instead. {@code
   * TrustTransactionTypeRegistryTest} pins the set.
   */
  public boolean isInvestmentRow() {
    return ledger == TrustLedger.CLIENT_INVESTMENT
        || ledger == TrustLedger.INTER_ACCOUNT
        || this == INV_PLACE
        || this == INV_RETURN;
  }

  public Reversal reversal() {
    return reversal;
  }

  /** Null for REVERSAL (it inherits its original's origin). */
  public Origin origin() {
    return origin;
  }

  /**
   * A row of the type is only ever created from a bank statement line, linked to it at birth
   * (652H): the five {@link Origin#BANK} types ({@code StatementLinePostingService.recordFromLine})
   * and {@link #FUND_INV_SHARE} (posted only for a {@code GROSS} interest entry, which is always
   * recorded from the investment account's statement line — {@code TrustInvestmentService}). Once
   * such a row is reversed and unlinked (its line unmatched), it is never matched to a bank line
   * again: it must not re-match the line it was recorded from (R-1). {@link #INV_INTEREST} is not
   * one — a bank advice records it with no line. The frontend's manual-match pane mirrors this set.
   */
  public boolean recordedOnlyFromStatementLine() {
    return origin == Origin.BANK || this == FUND_INV_SHARE;
  }

  /**
   * A row of the type is created only by a trust take-on commit ({@code
   * TrustPostingService.recordOpening}, Phase 93 657A) — the type DTO's {@code takeOnOnly}; every
   * type picker leaves it out.
   */
  public boolean recordedOnlyAtTakeOn() {
    return origin == Origin.TAKE_ON;
  }

  /** Whether a posted row waits for the bank ({@link Clearance#BANK}) or was cleared at cutover. */
  public Clearance clearance() {
    return clearance;
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
