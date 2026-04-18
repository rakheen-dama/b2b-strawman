package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Legal disbursement — a third-party pass-through cost incurred on a matter (sheriff fees, deeds
 * office, court fees, counsel, etc.).
 *
 * <p>Sibling entity to {@code Expense} (ADR-247). Status fields are varchar + CHECK constraint in
 * DB; Java-side enums are for service/controller convenience only (ADR-238). Payment source is
 * either OFFICE_ACCOUNT or TRUST_ACCOUNT; trust-linked disbursements carry a bare UUID FK to the
 * {@code TrustTransaction} that drew the funds.
 */
@Entity
@Table(name = "legal_disbursements")
public class LegalDisbursement {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "incurred_date", nullable = false)
  private LocalDate incurredDate;

  @Column(name = "category", nullable = false, length = 30)
  private String category;

  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "vat_treatment", nullable = false, length = 30)
  private String vatTreatment;

  @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal vatAmount;

  @Column(name = "supplier_name", length = 200)
  private String supplierName;

  @Column(name = "receipt_document_id")
  private UUID receiptDocumentId;

  @Column(name = "payment_source", nullable = false, length = 20)
  private String paymentSource;

  @Column(name = "trust_transaction_id")
  private UUID trustTransactionId;

  @Column(name = "approval_status", nullable = false, length = 20)
  private String approvalStatus = DisbursementApprovalStatus.DRAFT.name();

  @Column(name = "approval_notes", columnDefinition = "TEXT")
  private String approvalNotes;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "billing_status", nullable = false, length = 20)
  private String billingStatus = DisbursementBillingStatus.UNBILLED.name();

  @Column(name = "billed_invoice_line_id")
  private UUID billedInvoiceLineId;

  @Column(name = "write_off_reason", columnDefinition = "TEXT")
  private String writeOffReason;

  @Column(name = "written_off_at")
  private Instant writtenOffAt;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected LegalDisbursement() {}

  public LegalDisbursement(
      UUID projectId,
      UUID customerId,
      LocalDate incurredDate,
      String category,
      String description,
      BigDecimal amount,
      String currency,
      String vatTreatment,
      BigDecimal vatAmount,
      String paymentSource,
      UUID createdBy) {
    this.projectId = projectId;
    this.customerId = customerId;
    this.incurredDate = incurredDate;
    this.category = category;
    this.description = description;
    this.amount = amount;
    this.currency = currency;
    this.vatTreatment = vatTreatment;
    this.vatAmount = vatAmount;
    this.paymentSource = paymentSource;
    this.createdBy = createdBy;
  }

  @PrePersist
  protected void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
    if (this.approvalStatus == null) {
      this.approvalStatus = DisbursementApprovalStatus.DRAFT.name();
    }
    if (this.billingStatus == null) {
      this.billingStatus = DisbursementBillingStatus.UNBILLED.name();
    }
    if (this.currency == null) {
      this.currency = "ZAR";
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  // --- State-transition methods (guard legal transitions) ---

  /** DRAFT → PENDING_APPROVAL. */
  public void submitForApproval() {
    if (!DisbursementApprovalStatus.DRAFT.name().equals(this.approvalStatus)) {
      throw new IllegalStateException(
          "Cannot submit for approval from state: " + this.approvalStatus);
    }
    this.approvalStatus = DisbursementApprovalStatus.PENDING_APPROVAL.name();
  }

  /** PENDING_APPROVAL → APPROVED. */
  public void approve(UUID approverId, String notes) {
    if (!DisbursementApprovalStatus.PENDING_APPROVAL.name().equals(this.approvalStatus)) {
      throw new IllegalStateException("Cannot approve from state: " + this.approvalStatus);
    }
    this.approvalStatus = DisbursementApprovalStatus.APPROVED.name();
    this.approvedBy = approverId;
    this.approvedAt = Instant.now();
    this.approvalNotes = notes;
  }

  /** PENDING_APPROVAL → REJECTED. */
  public void reject(UUID approverId, String notes) {
    if (!DisbursementApprovalStatus.PENDING_APPROVAL.name().equals(this.approvalStatus)) {
      throw new IllegalStateException("Cannot reject from state: " + this.approvalStatus);
    }
    this.approvalStatus = DisbursementApprovalStatus.REJECTED.name();
    this.approvedBy = approverId;
    this.approvedAt = Instant.now();
    this.approvalNotes = notes;
  }

  /** UNBILLED + APPROVED → BILLED. */
  public void markBilled(UUID invoiceLineId) {
    if (invoiceLineId == null) {
      throw new IllegalArgumentException("invoiceLineId must not be null");
    }
    if (!DisbursementBillingStatus.UNBILLED.name().equals(this.billingStatus)) {
      throw new IllegalStateException(
          "Cannot mark billed from billing state: " + this.billingStatus);
    }
    if (!DisbursementApprovalStatus.APPROVED.name().equals(this.approvalStatus)) {
      throw new IllegalStateException(
          "Disbursement must be APPROVED before billing, current approval state: "
              + this.approvalStatus);
    }
    this.billingStatus = DisbursementBillingStatus.BILLED.name();
    this.billedInvoiceLineId = invoiceLineId;
  }

  /** UNBILLED → WRITTEN_OFF. */
  public void writeOff(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("write-off reason must not be blank");
    }
    if (!DisbursementBillingStatus.UNBILLED.name().equals(this.billingStatus)) {
      throw new IllegalStateException("Cannot write off from state: " + this.billingStatus);
    }
    this.billingStatus = DisbursementBillingStatus.WRITTEN_OFF.name();
    this.writeOffReason = reason;
    this.writtenOffAt = Instant.now();
  }

  /** WRITTEN_OFF → UNBILLED. */
  public void restore() {
    if (!DisbursementBillingStatus.WRITTEN_OFF.name().equals(this.billingStatus)) {
      throw new IllegalStateException("Cannot restore from state: " + this.billingStatus);
    }
    this.billingStatus = DisbursementBillingStatus.UNBILLED.name();
    this.writeOffReason = null;
    this.writtenOffAt = null;
  }

  /**
   * Updates mutable fields on a DRAFT disbursement. Throws if the disbursement is no longer in
   * DRAFT state (e.g., PENDING_APPROVAL, APPROVED, REJECTED).
   */
  public void update(
      LocalDate incurredDate,
      String category,
      String description,
      BigDecimal amount,
      String currency,
      String vatTreatment,
      BigDecimal vatAmount,
      String supplierName,
      String paymentSource,
      UUID trustTransactionId) {
    if (!DisbursementApprovalStatus.DRAFT.name().equals(this.approvalStatus)) {
      throw new IllegalStateException(
          "Cannot update disbursement from state: " + this.approvalStatus);
    }
    this.incurredDate = incurredDate;
    this.category = category;
    this.description = description;
    this.amount = amount;
    this.currency = currency;
    this.vatTreatment = vatTreatment;
    this.vatAmount = vatAmount;
    this.supplierName = supplierName;
    this.paymentSource = paymentSource;
    this.trustTransactionId = trustTransactionId;
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public LocalDate getIncurredDate() {
    return incurredDate;
  }

  public String getCategory() {
    return category;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public String getVatTreatment() {
    return vatTreatment;
  }

  public BigDecimal getVatAmount() {
    return vatAmount;
  }

  public String getSupplierName() {
    return supplierName;
  }

  public UUID getReceiptDocumentId() {
    return receiptDocumentId;
  }

  public String getPaymentSource() {
    return paymentSource;
  }

  public UUID getTrustTransactionId() {
    return trustTransactionId;
  }

  public String getApprovalStatus() {
    return approvalStatus;
  }

  public String getApprovalNotes() {
    return approvalNotes;
  }

  public UUID getApprovedBy() {
    return approvedBy;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public String getBillingStatus() {
    return billingStatus;
  }

  public UUID getBilledInvoiceLineId() {
    return billedInvoiceLineId;
  }

  public String getWriteOffReason() {
    return writeOffReason;
  }

  public Instant getWrittenOffAt() {
    return writtenOffAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // --- Setters for externally managed optional fields ---

  public void setSupplierName(String supplierName) {
    this.supplierName = supplierName;
  }

  public void setReceiptDocumentId(UUID receiptDocumentId) {
    this.receiptDocumentId = receiptDocumentId;
  }

  public void setTrustTransactionId(UUID trustTransactionId) {
    this.trustTransactionId = trustTransactionId;
  }
}
