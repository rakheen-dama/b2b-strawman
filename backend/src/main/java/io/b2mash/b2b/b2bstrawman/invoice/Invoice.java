package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAware;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAwareEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Invoice entity representing a billable invoice for time entries and manual line items.
 *
 * <p>Lifecycle: DRAFT (editable, no number) → APPROVED (numbered, locked) → SENT (to customer) →
 * PAID (payment received). Can transition to VOID from APPROVED or SENT.
 *
 * <p>Invoice numbers are assigned sequentially per tenant when the invoice is approved (transitions
 * from DRAFT to APPROVED). Format: "INV-0001", "INV-0002", etc.
 */
@Entity
@Table(name = "invoices")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class Invoice implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "invoice_number", length = 50)
  private String invoiceNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private InvoiceStatus status = InvoiceStatus.DRAFT;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "issue_date")
  private LocalDate issueDate;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(name = "subtotal", precision = 14, scale = 2, nullable = false)
  private BigDecimal subtotal = BigDecimal.ZERO;

  @Column(name = "tax_amount", precision = 14, scale = 2, nullable = false)
  private BigDecimal taxAmount = BigDecimal.ZERO;

  @Column(name = "total", precision = 14, scale = 2, nullable = false)
  private BigDecimal total = BigDecimal.ZERO;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "payment_terms", length = 100)
  private String paymentTerms;

  @Column(name = "payment_reference", length = 255)
  private String paymentReference;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "customer_name", nullable = false, length = 255)
  private String customerName;

  @Column(name = "customer_email", length = 255)
  private String customerEmail;

  @Column(name = "customer_address", columnDefinition = "TEXT")
  private String customerAddress;

  @Column(name = "org_name", nullable = false, length = 255)
  private String orgName;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Invoice() {}

  public Invoice(
      UUID customerId,
      String currency,
      String customerName,
      String customerEmail,
      String customerAddress,
      String orgName,
      UUID createdBy) {
    this.customerId = customerId;
    this.currency = currency;
    this.customerName = customerName;
    this.customerEmail = customerEmail;
    this.customerAddress = customerAddress;
    this.orgName = orgName;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /**
   * Approves the invoice, transitioning from DRAFT to APPROVED. Assigns invoice number via
   * InvoiceNumberService (called by service layer). Sets issue date to today if not already set.
   *
   * @param approvedBy the member ID who approved the invoice
   * @throws InvalidStateException if the invoice is not in DRAFT status
   */
  public void approve(UUID approvedBy) {
    if (!status.canTransitionTo(InvoiceStatus.APPROVED)) {
      throw new InvalidStateException(
          "Invalid invoice status",
          "Cannot approve invoice in status " + status + ". Must be DRAFT.");
    }
    this.status = InvoiceStatus.APPROVED;
    this.approvedBy = approvedBy;
    if (this.issueDate == null) {
      this.issueDate = LocalDate.now();
    }
    this.updatedAt = Instant.now();
  }

  /**
   * Marks the invoice as sent to the customer, transitioning from APPROVED to SENT. Sets issue date
   * to today if not already set (fallback).
   *
   * @throws InvalidStateException if the invoice is not in APPROVED status
   */
  public void markSent() {
    if (!status.canTransitionTo(InvoiceStatus.SENT)) {
      throw new InvalidStateException(
          "Invalid invoice status",
          "Cannot mark invoice as sent in status " + status + ". Must be APPROVED.");
    }
    this.status = InvoiceStatus.SENT;
    if (this.issueDate == null) {
      this.issueDate = LocalDate.now();
    }
    this.updatedAt = Instant.now();
  }

  /**
   * Records payment for the invoice, transitioning from SENT to PAID. Captures payment timestamp
   * and optional payment reference.
   *
   * @param paymentReference optional payment reference (e.g., transaction ID, check number)
   * @throws InvalidStateException if the invoice is not in SENT status
   */
  public void recordPayment(String paymentReference) {
    if (!status.canTransitionTo(InvoiceStatus.PAID)) {
      throw new InvalidStateException(
          "Invalid invoice status",
          "Cannot record payment for invoice in status " + status + ". Must be SENT.");
    }
    this.status = InvoiceStatus.PAID;
    this.paidAt = Instant.now();
    this.paymentReference = paymentReference;
    this.updatedAt = Instant.now();
  }

  /**
   * Voids the invoice, transitioning from APPROVED or SENT to VOID. Voided invoices free up time
   * entries for re-invoicing.
   *
   * @throws InvalidStateException if the invoice is not in APPROVED or SENT status
   */
  public void voidInvoice() {
    if (!status.canTransitionTo(InvoiceStatus.VOID)) {
      throw new InvalidStateException(
          "Invalid invoice status",
          "Cannot void invoice in status "
              + status
              + ". Must be APPROVED or SENT (not PAID or already VOID).");
    }
    this.status = InvoiceStatus.VOID;
    this.updatedAt = Instant.now();
  }

  /**
   * Recalculates the invoice totals based on the provided line items. Sets subtotal to the sum of
   * all line amounts, and total to subtotal + taxAmount.
   *
   * @param lines the invoice lines (must be all lines for this invoice)
   */
  public void recalculateTotals(List<InvoiceLine> lines) {
    this.subtotal =
        lines.stream()
            .map(InvoiceLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    this.total = this.subtotal.add(this.taxAmount).setScale(2, RoundingMode.HALF_UP);
    this.updatedAt = Instant.now();
  }

  /**
   * Returns true if the invoice can be edited (only DRAFT invoices are editable).
   *
   * @return true if status is DRAFT, false otherwise
   */
  public boolean canEdit() {
    return status == InvoiceStatus.DRAFT;
  }

  // --- Getters and Setters for editable fields ---

  public UUID getId() {
    return id;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public void setInvoiceNumber(String invoiceNumber) {
    this.invoiceNumber = invoiceNumber;
  }

  public InvoiceStatus getStatus() {
    return status;
  }

  public String getCurrency() {
    return currency;
  }

  public LocalDate getIssueDate() {
    return issueDate;
  }

  public void setIssueDate(LocalDate issueDate) {
    this.issueDate = issueDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public void setDueDate(LocalDate dueDate) {
    this.dueDate = dueDate;
    this.updatedAt = Instant.now();
  }

  public BigDecimal getSubtotal() {
    return subtotal;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public void setTaxAmount(BigDecimal taxAmount) {
    this.taxAmount = taxAmount;
    this.total = this.subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
    this.updatedAt = Instant.now();
  }

  public BigDecimal getTotal() {
    return total;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
    this.updatedAt = Instant.now();
  }

  public String getPaymentTerms() {
    return paymentTerms;
  }

  public void setPaymentTerms(String paymentTerms) {
    this.paymentTerms = paymentTerms;
    this.updatedAt = Instant.now();
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public String getCustomerName() {
    return customerName;
  }

  public String getCustomerEmail() {
    return customerEmail;
  }

  public String getCustomerAddress() {
    return customerAddress;
  }

  public String getOrgName() {
    return orgName;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public UUID getApprovedBy() {
    return approvedBy;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
