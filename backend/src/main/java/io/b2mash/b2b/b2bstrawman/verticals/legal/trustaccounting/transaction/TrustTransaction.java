package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "trust_transactions")
public class TrustTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trust_account_id", nullable = false)
  private UUID trustAccountId;

  @Column(name = "transaction_type", nullable = false, length = 20)
  private String transactionType;

  @Column(name = "amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "counterparty_customer_id")
  private UUID counterpartyCustomerId;

  @Column(name = "invoice_id")
  private UUID invoiceId;

  @Column(name = "reference", nullable = false, length = 200)
  private String reference;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "transaction_date", nullable = false)
  private LocalDate transactionDate;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "second_approved_by")
  private UUID secondApprovedBy;

  @Column(name = "second_approved_at")
  private Instant secondApprovedAt;

  @Column(name = "rejected_by")
  private UUID rejectedBy;

  @Column(name = "rejected_at")
  private Instant rejectedAt;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(name = "reversal_of")
  private UUID reversalOf;

  @Column(name = "reversed_by_id")
  private UUID reversedById;

  @Column(name = "bank_statement_line_id")
  private UUID bankStatementLineId;

  @Column(name = "recorded_by", nullable = false)
  private UUID recordedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  // --- Posting effects (Phase 92, ADR-378). Written only by TrustPostingService.applyPosting. The
  // initial values matter: Hibernate inserts explicit values, so the column defaults never apply
  // to a JPA insert and an unposted row must carry CLIENT / 0 / 0 / null (chk_trust_txn_posting).

  @Enumerated(EnumType.STRING)
  @Column(name = "ledger", nullable = false, length = 20)
  private TrustLedger ledger = TrustLedger.CLIENT;

  @Column(name = "cashbook_effect", nullable = false, precision = 15, scale = 2)
  private BigDecimal cashbookEffect = BigDecimal.ZERO;

  @Column(name = "ledger_effect", nullable = false, precision = 15, scale = 2)
  private BigDecimal ledgerEffect = BigDecimal.ZERO;

  @Column(name = "posted_at")
  private Instant postedAt;

  // Phase 92 / 646B (ADR-376). 647A added setCreditorAccountId (the first NON_CLIENT rows, recorded
  // from a statement line) and copyLedgerTargetFrom (a contra carries its original's target); 648A
  // added setInvestmentId (every investment row names its investment). creditorAccountId stays
  // null on CLIENT rows (chk_trust_txn_ledger_target).

  /**
   * The non-client creditor account a NON_CLIENT row moves (ADR-376, chk_trust_txn_ledger_target).
   */
  @Column(name = "creditor_account_id")
  private UUID creditorAccountId;

  /** The investment the row belongs to — every INV_*, FIRM_INV_* and FUND_INV_SHARE row (648A). */
  @Column(name = "investment_id")
  private UUID investmentId;

  /**
   * The unidentified deposit a row belongs to (647B, ADR-376): required on UNIDENTIFIED_DEPOSIT,
   * UNIDENT_ALLOC_OUT and UNIDENT_FUND_PAY (chk_trust_txn_unidentified_item), copied onto their
   * REVERSAL rows (copyLedgerTargetFrom) and onto UNIDENT_ALLOC_IN (647D). It is what makes "open
   * as at a date" derivable from the ledger (EQ-4).
   */
  @Column(name = "unidentified_deposit_id")
  private UUID unidentifiedDepositId;

  // --- Bank-charge detail (Phase 92 / 647A, ADR-381): non-null exactly on BANK_CHARGE rows
  // (chk_trust_txn_charge: 0 <= charge_vat_amount < amount).

  /** BANK_CHARGE only: whether the charge is on the Fund's recoverable list. */
  @Column(name = "charge_recoverable")
  private Boolean chargeRecoverable;

  /** BANK_CHARGE only: the VAT included in {@code amount}. */
  @Column(name = "charge_vat_amount", precision = 15, scale = 2)
  private BigDecimal chargeVatAmount;

  // --- Phase 93 / 657A (ADR-386): opening entries of a take-on.

  /**
   * The take-on batch whose commit posted this opening entry; null on every other row, including
   * the REVERSAL of a take-on row (chk_trust_txn_take_on_batch). Set once, before the first save.
   */
  @Column(name = "take_on_batch_id", updatable = false)
  private UUID takeOnBatchId;

  /**
   * Cleared at the cutover date (the registry's {@code Clearance.AT_CUTOVER}): never outstanding,
   * never linked to a bank line (chk_trust_txn_cutover_cleared). Written only by {@link
   * TrustPostingService#recordOpening} through {@link #applyClearance}; false on every other row.
   */
  @Column(name = "cleared_at_cutover", nullable = false, updatable = false)
  private boolean clearedAtCutover;

  protected TrustTransaction() {}

  public TrustTransaction(
      UUID trustAccountId,
      String transactionType,
      BigDecimal amount,
      UUID customerId,
      UUID projectId,
      UUID counterpartyCustomerId,
      String reference,
      String description,
      LocalDate transactionDate,
      String status,
      UUID recordedBy) {
    this.trustAccountId = trustAccountId;
    this.transactionType = transactionType;
    this.amount = amount;
    this.customerId = customerId;
    this.projectId = projectId;
    this.counterpartyCustomerId = counterpartyCustomerId;
    this.reference = reference;
    this.description = description;
    this.transactionDate = transactionDate;
    this.status = status;
    this.recordedBy = recordedBy;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTrustAccountId() {
    return trustAccountId;
  }

  public String getTransactionType() {
    return transactionType;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getCounterpartyCustomerId() {
    return counterpartyCustomerId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public String getReference() {
    return reference;
  }

  public String getDescription() {
    return description;
  }

  public LocalDate getTransactionDate() {
    return transactionDate;
  }

  public String getStatus() {
    return status;
  }

  public UUID getApprovedBy() {
    return approvedBy;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public UUID getSecondApprovedBy() {
    return secondApprovedBy;
  }

  public Instant getSecondApprovedAt() {
    return secondApprovedAt;
  }

  public UUID getRejectedBy() {
    return rejectedBy;
  }

  public Instant getRejectedAt() {
    return rejectedAt;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public UUID getReversalOf() {
    return reversalOf;
  }

  public UUID getReversedById() {
    return reversedById;
  }

  public UUID getBankStatementLineId() {
    return bankStatementLineId;
  }

  public UUID getRecordedBy() {
    return recordedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  /** The creditor ledger this row moves (ADR-378). */
  public TrustLedger getLedger() {
    return ledger;
  }

  /** The non-client creditor account a NON_CLIENT row moves; null on every other ledger. */
  public UUID getCreditorAccountId() {
    return creditorAccountId;
  }

  /** The investment the row belongs to; null when it belongs to none. */
  public UUID getInvestmentId() {
    return investmentId;
  }

  /** The unidentified deposit the row belongs to (647B); null when it belongs to none. */
  public UUID getUnidentifiedDepositId() {
    return unidentifiedDepositId;
  }

  public Boolean getChargeRecoverable() {
    return chargeRecoverable;
  }

  public BigDecimal getChargeVatAmount() {
    return chargeVatAmount;
  }

  /** Signed effect on the cashbook; 0 while unposted (and, in 646A, for reversals). */
  public BigDecimal getCashbookEffect() {
    return cashbookEffect;
  }

  /** Signed effect on {@link #getLedger()}; 0 while unposted (and, in 646A, for reversals). */
  public BigDecimal getLedgerEffect() {
    return ledgerEffect;
  }

  /** When the row was posted; null while AWAITING_APPROVAL or REJECTED. */
  public Instant getPostedAt() {
    return postedAt;
  }

  public UUID getTakeOnBatchId() {
    return takeOnBatchId;
  }

  public boolean isClearedAtCutover() {
    return clearedAtCutover;
  }

  /** Phase 93: names the take-on batch of an opening entry — set once, before the first save. */
  public void setTakeOnBatchId(UUID takeOnBatchId) {
    if (this.takeOnBatchId != null) {
      throw new IllegalStateException(
          "Trust transaction " + id + " already names its take-on batch");
    }
    this.takeOnBatchId = takeOnBatchId;
  }

  /**
   * Writes the registry's clearance onto an opening entry. Package-private and called only by
   * {@link TrustPostingService#recordOpening} (same rule as {@link #applyPosting}).
   */
  void applyClearance(TrustTransactionType.Clearance clearance) {
    boolean cleared = clearance == TrustTransactionType.Clearance.AT_CUTOVER;
    if (cleared && takeOnBatchId == null) {
      throw new IllegalStateException("Only a take-on row is cleared at the cutover date");
    }
    this.clearedAtCutover = cleared;
  }

  /**
   * Writes the posting effects. Package-private and called only by {@link TrustPostingService}
   * (ArchUnit {@code TrustPostingRulesTest}, ADR-378): effects come only from the registry.
   */
  void applyPosting(TrustLedgerEffect effect, Instant postedAt) {
    this.ledger = effect.ledger();
    this.cashbookEffect = effect.cashbookEffect();
    this.ledgerEffect = effect.ledgerEffect();
    this.postedAt = postedAt;
  }

  // --- Mutable setters (status lifecycle + reversal linking) ---

  public void setStatus(String status) {
    this.status = status;
  }

  public void setReversedById(UUID reversedById) {
    this.reversedById = reversedById;
  }

  public void setReversalOf(UUID reversalOf) {
    this.reversalOf = reversalOf;
  }

  public void setApprovedBy(UUID approvedBy) {
    this.approvedBy = approvedBy;
  }

  public void setApprovedAt(Instant approvedAt) {
    this.approvedAt = approvedAt;
  }

  public void setRejectedBy(UUID rejectedBy) {
    this.rejectedBy = rejectedBy;
  }

  public void setRejectedAt(Instant rejectedAt) {
    this.rejectedAt = rejectedAt;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public void setInvoiceId(UUID invoiceId) {
    this.invoiceId = invoiceId;
  }

  public void setSecondApprovedBy(UUID secondApprovedBy) {
    this.secondApprovedBy = secondApprovedBy;
  }

  public void setSecondApprovedAt(Instant secondApprovedAt) {
    this.secondApprovedAt = secondApprovedAt;
  }

  public void setBankStatementLineId(UUID bankStatementLineId) {
    this.bankStatementLineId = bankStatementLineId;
  }

  /**
   * The non-client creditor account a NON_CLIENT row posts to (647A; chk_trust_txn_ledger_target).
   */
  public void setCreditorAccountId(UUID creditorAccountId) {
    this.creditorAccountId = creditorAccountId;
  }

  /**
   * Set once, before the row is first saved (648A placement, receive, withdraw and return rows):
   * required on the {@code CLIENT_INVESTMENT} and {@code INTER_ACCOUNT} rows
   * (chk_trust_txn_ledger_target), and set on the {@code CLIENT} legs too, for EQ-5.
   */
  public void setInvestmentId(UUID investmentId) {
    this.investmentId = investmentId;
  }

  /** Set once, before the row is first saved (647B open; 647D resolution rows). */
  public void setUnidentifiedDepositId(UUID unidentifiedDepositId) {
    this.unidentifiedDepositId = unidentifiedDepositId;
  }

  /** A BANK_CHARGE's detail (647A; chk_trust_txn_charge — both set exactly on BANK_CHARGE rows). */
  public void setChargeDetail(Boolean recoverable, BigDecimal vatAmount) {
    this.chargeRecoverable = recoverable;
    this.chargeVatAmount = vatAmount;
  }

  /**
   * A contra carries its original's ledger target (§16.3.11): creditor account, investment and
   * unidentified deposit (647B — a contra names its item, so EQ-4 derives "open as at a date" from
   * the item's own rows). The client and matter are copied by {@code
   * TrustPostingService.contraDraft}. Charge detail is NOT copied — {@code chk_trust_txn_charge}
   * requires it null on a REVERSAL. Package-private: {@link TrustPostingService} only.
   */
  void copyLedgerTargetFrom(TrustTransaction original) {
    this.creditorAccountId = original.creditorAccountId;
    this.investmentId = original.investmentId;
    this.unidentifiedDepositId = original.unidentifiedDepositId;
  }
}
