package io.b2mash.b2b.b2bstrawman.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "invoices")
public class Invoice {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "invoice_number", length = 20)
  private String invoiceNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private InvoiceStatus status;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "issue_date")
  private LocalDate issueDate;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
  private BigDecimal subtotal;

  @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal taxAmount;

  @Column(name = "total", nullable = false, precision = 14, scale = 2)
  private BigDecimal total;

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

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "custom_fields", columnDefinition = "jsonb")
  private Map<String, Object> customFields = new HashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "applied_field_groups", columnDefinition = "jsonb")
  private List<UUID> appliedFieldGroups = new ArrayList<>();

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
    this.status = InvoiceStatus.DRAFT;
    this.subtotal = BigDecimal.ZERO;
    this.taxAmount = BigDecimal.ZERO;
    this.total = BigDecimal.ZERO;
    this.customerName = customerName;
    this.customerEmail = customerEmail;
    this.customerAddress = customerAddress;
    this.orgName = orgName;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void updateDraft(
      LocalDate dueDate, String notes, String paymentTerms, BigDecimal taxAmount) {
    if (this.status != InvoiceStatus.DRAFT) {
      throw new IllegalStateException("Only draft invoices can be edited");
    }
    this.dueDate = dueDate;
    this.notes = notes;
    this.paymentTerms = paymentTerms;
    this.taxAmount = taxAmount != null ? taxAmount : BigDecimal.ZERO;
    this.total = this.subtotal.add(this.taxAmount);
    this.updatedAt = Instant.now();
  }

  public void recalculateTotals(BigDecimal subtotal) {
    this.subtotal = subtotal;
    this.total = this.subtotal.add(this.taxAmount);
    this.updatedAt = Instant.now();
  }

  public void approve(String invoiceNumber, UUID approvedBy) {
    if (this.status != InvoiceStatus.DRAFT) {
      throw new IllegalStateException("Only draft invoices can be approved");
    }
    this.status = InvoiceStatus.APPROVED;
    this.invoiceNumber = invoiceNumber;
    this.approvedBy = approvedBy;
    if (this.issueDate == null) {
      this.issueDate = LocalDate.now();
    }
    this.updatedAt = Instant.now();
  }

  public void markSent() {
    if (this.status != InvoiceStatus.APPROVED) {
      throw new IllegalStateException("Only approved invoices can be sent");
    }
    this.status = InvoiceStatus.SENT;
    this.updatedAt = Instant.now();
  }

  public void recordPayment(String paymentReference) {
    if (this.status != InvoiceStatus.SENT) {
      throw new IllegalStateException("Only sent invoices can be paid");
    }
    this.status = InvoiceStatus.PAID;
    this.paidAt = Instant.now();
    this.paymentReference = paymentReference;
    this.updatedAt = Instant.now();
  }

  public void voidInvoice() {
    if (this.status != InvoiceStatus.APPROVED && this.status != InvoiceStatus.SENT) {
      throw new IllegalStateException("Only approved or sent invoices can be voided");
    }
    this.status = InvoiceStatus.VOID;
    this.updatedAt = Instant.now();
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
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

  public LocalDate getDueDate() {
    return dueDate;
  }

  public BigDecimal getSubtotal() {
    return subtotal;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public String getNotes() {
    return notes;
  }

  public String getPaymentTerms() {
    return paymentTerms;
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Map<String, Object> getCustomFields() {
    return customFields;
  }

  public void setCustomFields(Map<String, Object> customFields) {
    this.customFields = customFields;
    this.updatedAt = Instant.now();
  }

  public List<UUID> getAppliedFieldGroups() {
    return appliedFieldGroups;
  }

  public void setAppliedFieldGroups(List<UUID> appliedFieldGroups) {
    this.appliedFieldGroups = appliedFieldGroups;
    this.updatedAt = Instant.now();
  }
}
