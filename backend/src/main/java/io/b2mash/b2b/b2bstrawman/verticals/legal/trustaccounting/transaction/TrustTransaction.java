package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
}
