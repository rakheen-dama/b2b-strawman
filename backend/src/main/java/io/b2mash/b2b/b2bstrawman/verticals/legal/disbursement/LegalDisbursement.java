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
 * A legal disbursement — an out-of-pocket cost incurred on behalf of a client (sheriff fees, deeds
 * office charges, counsel fees, etc.) that is tracked against a project (matter) and later billed
 * to the client.
 *
 * <p>Two lifecycle axes:
 *
 * <ul>
 *   <li>Approval: {@code DRAFT} → {@code PENDING_APPROVAL} → {@code APPROVED} | {@code REJECTED}
 *   <li>Billing: {@code UNBILLED} → {@code BILLED} | {@code WRITTEN_OFF} (→ {@code UNBILLED} via
 *       {@link #restore()})
 * </ul>
 *
 * <p>Status fields are stored as varchar strings (see ADR-238). Use the {@code
 * DisbursementApprovalStatus}, {@code DisbursementBillingStatus}, {@code DisbursementCategory},
 * {@code DisbursementPaymentSource} and {@code VatTreatment} enums at the boundary.
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

  @Column(name = "category", nullable = false, length = 30)
  private String category;

  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @Column(name = "vat_treatment", nullable = false, length = 30)
  private String vatTreatment;

  @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal vatAmount;

  @Column(name = "payment_source", nullable = false, length = 20)
  private String paymentSource;

  @Column(name = "trust_transaction_id")
  private UUID trustTransactionId;

  @Column(name = "incurred_date", nullable = false)
  private LocalDate incurredDate;

  @Column(name = "supplier_name", nullable = false, length = 200)
  private String supplierName;

  @Column(name = "supplier_reference", length = 100)
  private String supplierReference;

  @Column(name = "receipt_document_id")
  private UUID receiptDocumentId;

  @Column(name = "approval_status", nullable = false, length = 20)
  private String approvalStatus;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "approval_notes", columnDefinition = "TEXT")
  private String approvalNotes;

  @Column(name = "billing_status", nullable = false, length = 20)
  private String billingStatus;

  @Column(name = "invoice_line_id")
  private UUID invoiceLineId;

  @Column(name = "write_off_reason", columnDefinition = "TEXT")
  private String writeOffReason;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected LegalDisbursement() {}

  /**
   * Creates a new disbursement in {@code DRAFT} approval status and {@code UNBILLED} billing
   * status.
   *
   * @param projectId the project / matter the disbursement is incurred against (required)
   * @param customerId the customer the project belongs to (required, denormalised for queries)
   * @param category the category code (required, from {@link DisbursementCategory})
   * @param description a human-readable description (required)
   * @param amount the total amount of the disbursement (required, must be &gt; 0)
   * @param vatTreatment the VAT treatment code (required, from {@link VatTreatment})
   * @param vatAmount the VAT component (required, defaults to {@code BigDecimal.ZERO} if null)
   * @param paymentSource the payment source code (required, from {@link DisbursementPaymentSource})
   * @param trustTransactionId the linked trust transaction ID — required when {@code paymentSource
   *     == TRUST_ACCOUNT}, must be {@code null} otherwise (enforced at DB layer)
   * @param incurredDate the date the disbursement was incurred (required)
   * @param supplierName the supplier name (required)
   * @param supplierReference optional supplier reference
   * @param receiptDocumentId optional document ID of the uploaded receipt
   * @param createdBy the member ID that created the record (required)
   */
  public LegalDisbursement(
      UUID projectId,
      UUID customerId,
      String category,
      String description,
      BigDecimal amount,
      String vatTreatment,
      BigDecimal vatAmount,
      String paymentSource,
      UUID trustTransactionId,
      LocalDate incurredDate,
      String supplierName,
      String supplierReference,
      UUID receiptDocumentId,
      UUID createdBy) {
    this.projectId = projectId;
    this.customerId = customerId;
    this.category = category;
    this.description = description;
    this.amount = amount;
    this.vatTreatment = vatTreatment;
    this.vatAmount = vatAmount != null ? vatAmount : BigDecimal.ZERO;
    this.paymentSource = paymentSource;
    this.trustTransactionId = trustTransactionId;
    this.incurredDate = incurredDate;
    this.supplierName = supplierName;
    this.supplierReference = supplierReference;
    this.receiptDocumentId = receiptDocumentId;
    this.createdBy = createdBy;
    this.approvalStatus = DisbursementApprovalStatus.DRAFT.name();
    this.billingStatus = DisbursementBillingStatus.UNBILLED.name();
  }

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    if (this.createdAt == null) {
      this.createdAt = now;
    }
    if (this.updatedAt == null) {
      this.updatedAt = now;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  // --- State-transition methods ---

  /**
   * Submits a {@code DRAFT} disbursement for approval.
   *
   * @throws IllegalStateException if not currently in {@code DRAFT}
   */
  public void submitForApproval() {
    if (!DisbursementApprovalStatus.DRAFT.name().equals(approvalStatus)) {
      throw new IllegalStateException(
          "Cannot submit for approval: disbursement is not in DRAFT (current=%s)"
              .formatted(approvalStatus));
    }
    this.approvalStatus = DisbursementApprovalStatus.PENDING_APPROVAL.name();
    this.updatedAt = Instant.now();
  }

  /**
   * Approves a {@code PENDING_APPROVAL} disbursement.
   *
   * @param approverId the member ID approving the request (required)
   * @param notes optional approval notes
   * @throws IllegalArgumentException if {@code approverId} is null
   * @throws IllegalStateException if not currently in {@code PENDING_APPROVAL}
   */
  public void approve(UUID approverId, String notes) {
    if (approverId == null) {
      throw new IllegalArgumentException("Approver ID must not be null");
    }
    if (!DisbursementApprovalStatus.PENDING_APPROVAL.name().equals(approvalStatus)) {
      throw new IllegalStateException(
          "Cannot approve: disbursement is not in PENDING_APPROVAL (current=%s)"
              .formatted(approvalStatus));
    }
    this.approvalStatus = DisbursementApprovalStatus.APPROVED.name();
    this.approvedBy = approverId;
    this.approvedAt = Instant.now();
    this.approvalNotes = notes;
    this.updatedAt = Instant.now();
  }

  /**
   * Rejects a {@code PENDING_APPROVAL} disbursement.
   *
   * @param approverId the member ID rejecting the request (required)
   * @param notes optional rejection notes
   * @throws IllegalArgumentException if {@code approverId} is null
   * @throws IllegalStateException if not currently in {@code PENDING_APPROVAL}
   */
  public void reject(UUID approverId, String notes) {
    if (approverId == null) {
      throw new IllegalArgumentException("Approver ID must not be null");
    }
    if (!DisbursementApprovalStatus.PENDING_APPROVAL.name().equals(approvalStatus)) {
      throw new IllegalStateException(
          "Cannot reject: disbursement is not in PENDING_APPROVAL (current=%s)"
              .formatted(approvalStatus));
    }
    this.approvalStatus = DisbursementApprovalStatus.REJECTED.name();
    this.approvedBy = approverId;
    this.approvedAt = Instant.now();
    this.approvalNotes = notes;
    this.updatedAt = Instant.now();
  }

  /**
   * Marks this disbursement as billed by associating it with an invoice line.
   *
   * @param invoiceLineId the invoice line ID (required)
   * @throws IllegalArgumentException if {@code invoiceLineId} is null
   * @throws IllegalStateException if not {@code APPROVED} or already billed / written off
   */
  public void markBilled(UUID invoiceLineId) {
    if (invoiceLineId == null) {
      throw new IllegalArgumentException("Invoice line ID must not be null");
    }
    if (!DisbursementApprovalStatus.APPROVED.name().equals(approvalStatus)) {
      throw new IllegalStateException(
          "Cannot bill: disbursement is not APPROVED (current=%s)".formatted(approvalStatus));
    }
    if (!DisbursementBillingStatus.UNBILLED.name().equals(billingStatus)) {
      throw new IllegalStateException(
          "Cannot bill: disbursement is not UNBILLED (current=%s)".formatted(billingStatus));
    }
    this.billingStatus = DisbursementBillingStatus.BILLED.name();
    this.invoiceLineId = invoiceLineId;
    this.updatedAt = Instant.now();
  }

  /**
   * Writes off this disbursement as uncollectable.
   *
   * @param reason human-readable reason (required, non-blank)
   * @throws IllegalArgumentException if {@code reason} is null or blank
   * @throws IllegalStateException if not currently {@code UNBILLED}
   */
  public void writeOff(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Write-off reason must not be blank");
    }
    if (!DisbursementBillingStatus.UNBILLED.name().equals(billingStatus)) {
      throw new IllegalStateException(
          "Cannot write off: disbursement is not UNBILLED (current=%s)".formatted(billingStatus));
    }
    this.billingStatus = DisbursementBillingStatus.WRITTEN_OFF.name();
    this.writeOffReason = reason;
    this.updatedAt = Instant.now();
  }

  /**
   * Restores a {@code WRITTEN_OFF} disbursement back to {@code UNBILLED} and clears the write-off
   * reason.
   *
   * @throws IllegalStateException if not currently {@code WRITTEN_OFF}
   */
  public void restore() {
    if (!DisbursementBillingStatus.WRITTEN_OFF.name().equals(billingStatus)) {
      throw new IllegalStateException(
          "Cannot restore: disbursement is not WRITTEN_OFF (current=%s)".formatted(billingStatus));
    }
    this.billingStatus = DisbursementBillingStatus.UNBILLED.name();
    this.writeOffReason = null;
    this.updatedAt = Instant.now();
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

  public String getCategory() {
    return category;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getVatTreatment() {
    return vatTreatment;
  }

  public BigDecimal getVatAmount() {
    return vatAmount;
  }

  public String getPaymentSource() {
    return paymentSource;
  }

  public UUID getTrustTransactionId() {
    return trustTransactionId;
  }

  public LocalDate getIncurredDate() {
    return incurredDate;
  }

  public String getSupplierName() {
    return supplierName;
  }

  public String getSupplierReference() {
    return supplierReference;
  }

  public UUID getReceiptDocumentId() {
    return receiptDocumentId;
  }

  public String getApprovalStatus() {
    return approvalStatus;
  }

  public UUID getApprovedBy() {
    return approvedBy;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public String getApprovalNotes() {
    return approvalNotes;
  }

  public String getBillingStatus() {
    return billingStatus;
  }

  public UUID getInvoiceLineId() {
    return invoiceLineId;
  }

  public String getWriteOffReason() {
    return writeOffReason;
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

  // --- Setters for externally managed fields (used in 486B service layer) ---

  public void setDescription(String description) {
    this.description = description;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public void setVatTreatment(String vatTreatment) {
    this.vatTreatment = vatTreatment;
  }

  public void setVatAmount(BigDecimal vatAmount) {
    this.vatAmount = vatAmount;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public void setSupplierName(String supplierName) {
    this.supplierName = supplierName;
  }

  public void setSupplierReference(String supplierReference) {
    this.supplierReference = supplierReference;
  }

  public void setIncurredDate(LocalDate incurredDate) {
    this.incurredDate = incurredDate;
  }

  public void setReceiptDocumentId(UUID receiptDocumentId) {
    this.receiptDocumentId = receiptDocumentId;
  }
}
