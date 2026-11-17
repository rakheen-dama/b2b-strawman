package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One bank credit whose owner is not yet known (Phase 92, ADR-376, §16.2.5): opened when a
 * statement line is recorded as {@code UNIDENTIFIED_DEPOSIT}, held on the trust account's
 * unidentified-deposits ledger until it is allocated to a client and matter or paid to the Fund
 * (647D — and back to HELD when that is undone), or reversed when its line is unmatched (647B).
 * Every ledger row that moves the item names it ({@code
 * trust_transactions.unidentified_deposit_id}), so whether it was open as at a date is derived from
 * those rows (EQ-4); {@link #getStatus()} describes the present only.
 */
@Entity
@Table(name = "unidentified_deposits")
public class UnidentifiedDeposit {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trust_account_id", nullable = false)
  private UUID trustAccountId;

  /** The trust account's UNIDENTIFIED_DEPOSITS creditor account — the same id as its rows. */
  @Column(name = "creditor_account_id", nullable = false)
  private UUID creditorAccountId;

  /** The UNIDENTIFIED_DEPOSIT row that opened it; null only inside the creating transaction. */
  @Column(name = "deposit_transaction_id")
  private UUID depositTransactionId;

  @Column(name = "bank_statement_line_id", nullable = false)
  private UUID bankStatementLineId;

  @Column(name = "received_date", nullable = false)
  private LocalDate receivedDate;

  @Column(name = "bank_description", nullable = false, length = 500)
  private String bankDescription;

  @Column(name = "bank_reference", length = 200)
  private String bankReference;

  @Column(name = "amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private UnidentifiedDepositStatus status;

  // --- Resolution (647D): the pending or approved allocation / payment to the Fund.

  @Column(name = "resolution_transaction_id")
  private UUID resolutionTransactionId;

  @Column(name = "allocation_credit_transaction_id")
  private UUID allocationCreditTransactionId;

  @Column(name = "allocated_customer_id")
  private UUID allocatedCustomerId;

  @Column(name = "allocated_project_id")
  private UUID allocatedProjectId;

  /** The date of the row that took the item out of HELD (chk_unidentified_deposit_resolved). */
  @Column(name = "resolved_date")
  private LocalDate resolvedDate;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UnidentifiedDeposit() {}

  /** A HELD item for one bank credit; its opening row is attached right after it posts. */
  public UnidentifiedDeposit(
      UUID trustAccountId,
      UUID creditorAccountId,
      UUID bankStatementLineId,
      LocalDate receivedDate,
      String bankDescription,
      String bankReference,
      BigDecimal amount,
      String notes) {
    this.trustAccountId = trustAccountId;
    this.creditorAccountId = creditorAccountId;
    this.bankStatementLineId = bankStatementLineId;
    this.receivedDate = receivedDate;
    this.bankDescription = bankDescription;
    this.bankReference = bankReference;
    this.amount = amount;
    this.notes = notes;
    this.status = UnidentifiedDepositStatus.HELD;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  /** The opening UNIDENTIFIED_DEPOSIT row (set once, in the creating transaction). */
  public void attachDepositTransaction(UUID transactionId) {
    if (this.depositTransactionId != null) {
      throw new IllegalStateException(
          "Unidentified deposit " + id + " already has its opening transaction");
    }
    this.depositTransactionId = transactionId;
  }

  /** Whether the item may be taken out of HELD: HELD with no pending or approved resolution. */
  public boolean isHeldWithoutResolution() {
    return status == UnidentifiedDepositStatus.HELD && resolutionTransactionId == null;
  }

  /** The line was unmatched: REVERSED, resolved on the contra's date (= the line's date). */
  public void markReversed(LocalDate contraDate) {
    if (!isHeldWithoutResolution()) {
      throw new IllegalStateException(
          "Unidentified deposit " + id + " is not held without a resolution");
    }
    this.status = UnidentifiedDepositStatus.REVERSED;
    this.resolvedDate = contraDate;
  }

  /**
   * A resolution was requested (647D): the pending {@code UNIDENT_ALLOC_OUT} or {@code
   * UNIDENT_FUND_PAY} row, and for an allocation its client and matter (both null for a payment to
   * the Fund). The item stays HELD until the row posts.
   */
  public void requestResolution(UUID resolutionTransactionId, UUID customerId, UUID projectId) {
    if (!isHeldWithoutResolution()) {
      throw new IllegalStateException(
          "Unidentified deposit " + id + " is not held without a resolution");
    }
    this.resolutionTransactionId = resolutionTransactionId;
    this.allocatedCustomerId = customerId;
    this.allocatedProjectId = projectId;
  }

  /** The allocation posted: ALLOCATED on {@code date} (chk_unidentified_deposit_allocated). */
  public void markAllocated(UUID allocationCreditTransactionId, LocalDate date) {
    if (status != UnidentifiedDepositStatus.HELD
        || resolutionTransactionId == null
        || allocatedCustomerId == null
        || allocatedProjectId == null) {
      throw new IllegalStateException(
          "Unidentified deposit " + id + " has no pending allocation to complete");
    }
    this.allocationCreditTransactionId = allocationCreditTransactionId;
    this.status = UnidentifiedDepositStatus.ALLOCATED;
    this.resolvedDate = date;
  }

  /** The payment to the Fund posted: PAID_TO_FUND on {@code date}. */
  public void markPaidToFund(LocalDate date) {
    if (status != UnidentifiedDepositStatus.HELD
        || resolutionTransactionId == null
        || allocatedCustomerId != null
        || allocatedProjectId != null) {
      throw new IllegalStateException(
          "Unidentified deposit " + id + " has no pending payment to the Fund to complete");
    }
    this.status = UnidentifiedDepositStatus.PAID_TO_FUND;
    this.resolvedDate = date;
  }

  /** The resolution was rejected: HELD, the resolution and the allocated ids cleared. */
  public void clearResolution() {
    if (status != UnidentifiedDepositStatus.HELD || resolutionTransactionId == null) {
      throw new IllegalStateException("Unidentified deposit " + id + " has no pending resolution");
    }
    this.resolutionTransactionId = null;
    this.allocatedCustomerId = null;
    this.allocatedProjectId = null;
  }

  /**
   * The resolution was reversed (an allocation undone, a payment to the Fund returned): HELD again,
   * with {@code resolved_date}, the resolution, credit and allocated ids cleared. Whether the item
   * was open at a past date is read from its rows, never from these columns (EQ-4).
   */
  public void reopen() {
    if (status != UnidentifiedDepositStatus.ALLOCATED
        && status != UnidentifiedDepositStatus.PAID_TO_FUND) {
      throw new IllegalStateException(
          "Unidentified deposit " + id + " is " + status + ", not allocated or paid to the Fund");
    }
    this.status = UnidentifiedDepositStatus.HELD;
    this.resolvedDate = null;
    this.resolutionTransactionId = null;
    this.allocationCreditTransactionId = null;
    this.allocatedCustomerId = null;
    this.allocatedProjectId = null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTrustAccountId() {
    return trustAccountId;
  }

  public UUID getCreditorAccountId() {
    return creditorAccountId;
  }

  public UUID getDepositTransactionId() {
    return depositTransactionId;
  }

  public UUID getBankStatementLineId() {
    return bankStatementLineId;
  }

  public LocalDate getReceivedDate() {
    return receivedDate;
  }

  public String getBankDescription() {
    return bankDescription;
  }

  public String getBankReference() {
    return bankReference;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public UnidentifiedDepositStatus getStatus() {
    return status;
  }

  public UUID getResolutionTransactionId() {
    return resolutionTransactionId;
  }

  public UUID getAllocationCreditTransactionId() {
    return allocationCreditTransactionId;
  }

  public UUID getAllocatedCustomerId() {
    return allocatedCustomerId;
  }

  public UUID getAllocatedProjectId() {
    return allocatedProjectId;
  }

  public LocalDate getResolvedDate() {
    return resolvedDate;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
