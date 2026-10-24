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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

  /**
   * Running total of posted payments, capped at {@code total} (Phase 88 U15, ADR-347 A3).
   * Initialised in Java: Hibernate inserts the field value, not the column DEFAULT, so null would
   * violate NOT NULL.
   */
  @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
  private BigDecimal amountPaid = BigDecimal.ZERO;

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

  @Column(name = "payment_session_id", length = 255)
  private String paymentSessionId;

  @Column(name = "payment_url", length = 1024)
  private String paymentUrl;

  @Column(name = "payment_destination", nullable = false, length = 50)
  private String paymentDestination;

  @Column(name = "billing_run_id")
  private UUID billingRunId;

  @Column(name = "po_number", length = 100)
  private String poNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "tax_type", length = 20)
  private TaxType taxType;

  @Column(name = "billing_period_start")
  private LocalDate billingPeriodStart;

  @Column(name = "billing_period_end")
  private LocalDate billingPeriodEnd;

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
    this.paymentDestination = "OPERATING";
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void updateDraft(
      LocalDate dueDate, String notes, String paymentTerms, BigDecimal taxAmount) {
    updateDraft(dueDate, notes, paymentTerms, taxAmount, null, null, null, null);
  }

  public void updateDraft(
      LocalDate dueDate,
      String notes,
      String paymentTerms,
      BigDecimal taxAmount,
      String poNumber,
      TaxType taxType,
      LocalDate billingPeriodStart,
      LocalDate billingPeriodEnd) {
    if (this.status != InvoiceStatus.DRAFT) {
      throw new IllegalStateException("Only draft invoices can be edited");
    }
    this.dueDate = dueDate;
    this.notes = notes;
    this.paymentTerms = paymentTerms;
    this.taxAmount = taxAmount != null ? taxAmount : BigDecimal.ZERO;
    this.total = this.subtotal.add(this.taxAmount);
    this.poNumber = poNumber != null ? poNumber : this.poNumber;
    this.taxType = taxType != null ? taxType : this.taxType;
    this.billingPeriodStart =
        billingPeriodStart != null ? billingPeriodStart : this.billingPeriodStart;
    this.billingPeriodEnd = billingPeriodEnd != null ? billingPeriodEnd : this.billingPeriodEnd;
    this.updatedAt = Instant.now();
  }

  public void recalculateTotals(
      BigDecimal computedSubtotal,
      boolean hasPerLineTax,
      BigDecimal perLineTaxSum,
      boolean taxInclusive) {
    this.subtotal = computedSubtotal;

    if (hasPerLineTax) {
      this.taxAmount = perLineTaxSum;
    }
    // else: taxAmount stays as manually set value (backward compatibility)

    if (taxInclusive && hasPerLineTax) {
      this.total = this.subtotal;
    } else {
      this.total = this.subtotal.add(this.taxAmount);
    }
    this.updatedAt = Instant.now();
  }

  /**
   * DRAFT → APPROVED: allocates the number, stamps {@code issueDate} (today when unset) and — when
   * the draft carries no due date — {@code dueDate = issueDate + defaultTermsDays} from the firm's
   * payment terms (Phase 88 U3, ADR-347). A due date typed on the draft is never overwritten.
   */
  public void approve(String invoiceNumber, UUID approvedBy, int defaultTermsDays) {
    if (this.status != InvoiceStatus.DRAFT) {
      throw new IllegalStateException("Only draft invoices can be approved");
    }
    this.status = InvoiceStatus.APPROVED;
    this.invoiceNumber = invoiceNumber;
    this.approvedBy = approvedBy;
    if (this.issueDate == null) {
      this.issueDate = LocalDate.now();
    }
    if (this.dueDate == null) {
      this.dueDate = this.issueDate.plusDays(defaultTermsDays);
    }
    this.updatedAt = Instant.now();
  }

  /**
   * Applies default due date and payment terms to an APPROVED invoice that is missing them. Used by
   * batch send to fill in defaults before sending. Since ADR-347 every fee note approved through
   * {@link #approve} already carries a due date, so {@code defaultDueDate} is a no-op for those; a
   * null {@code paymentTerms} is still filled.
   */
  public void applyDefaults(LocalDate defaultDueDate, String defaultPaymentTerms) {
    if (this.status != InvoiceStatus.APPROVED) {
      throw new IllegalStateException("Defaults can only be applied to approved invoices");
    }
    boolean changed = false;
    if (this.dueDate == null && defaultDueDate != null) {
      this.dueDate = defaultDueDate;
      changed = true;
    }
    if (this.paymentTerms == null && defaultPaymentTerms != null) {
      this.paymentTerms = defaultPaymentTerms;
      changed = true;
    }
    if (changed) {
      this.updatedAt = Instant.now();
    }
  }

  public void markSent() {
    if (this.status != InvoiceStatus.APPROVED) {
      throw new IllegalStateException("Only approved invoices can be sent");
    }
    this.status = InvoiceStatus.SENT;
    this.updatedAt = Instant.now();
  }

  /**
   * Statuses that can take a payment (ADR-347) — the single definition, also read by the service.
   */
  static final Set<InvoiceStatus> PAYABLE =
      Set.of(InvoiceStatus.SENT, InvoiceStatus.PARTIALLY_PAID);

  /** Statuses a payment can be reversed from (ADR-347 rationale 4) — also read by the service. */
  static final Set<InvoiceStatus> REVERSIBLE =
      Set.of(InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID);

  /**
   * Guard-and-throw: e.g. {@code IllegalStateException("Only sent or partially paid fee notes can
   * take a payment")}. Services translate it to a 409 "Invalid status transition".
   */
  void requireStatus(Set<InvoiceStatus> allowed, String action) {
    if (!allowed.contains(this.status)) {
      String names =
          allowed.stream()
              .sorted()
              .map(s -> s.name().toLowerCase(Locale.ROOT).replace('_', ' '))
              .collect(Collectors.joining(" or "));
      throw new IllegalStateException("Only " + names + " fee notes can " + action);
    }
  }

  /**
   * Applies a posted payment (Phase 88 U15, ADR-347): {@code amountPaid = min(amountPaid + amount,
   * total)}; PAID (with {@code paidAt}) once the balance is zero, else PARTIALLY_PAID. The payment
   * event itself always records the real amount — the cap only protects {@code
   * chk_invoices_amount_paid} when an external source overpays.
   */
  public void applyPayment(BigDecimal amount, String paymentReference) {
    requireStatus(PAYABLE, "take a payment");
    this.amountPaid = this.amountPaid.add(amount).min(this.total);
    if (paymentReference != null) {
      this.paymentReference = paymentReference;
    }
    if (getBalanceDue().signum() == 0) {
      this.status = InvoiceStatus.PAID;
      this.paidAt = Instant.now();
    } else {
      this.status = InvoiceStatus.PARTIALLY_PAID;
    }
    this.updatedAt = Instant.now();
  }

  /**
   * Settles the remaining balance in full — the interim path for the external callers until 622C
   * re-points them to {@code postPayment} with their real amounts, plus seeders and test fixtures.
   */
  public void recordPayment(String paymentReference) {
    applyPayment(getBalanceDue(), paymentReference);
  }

  /**
   * Recomputes the balance after a payment event is reversed (ADR-347 rationale 4): {@code
   * amountPaid = LEAST(total, completedSum)} — never decremented in place. Zero → SENT (reference
   * and {@code paidAt} cleared); still fully paid → PAID ({@code paidAt} kept); otherwise
   * PARTIALLY_PAID ({@code paidAt} cleared).
   */
  public void reapplyPayments(BigDecimal completedSum) {
    requireStatus(REVERSIBLE, "have a payment reversed");
    this.amountPaid =
        completedSum.max(BigDecimal.ZERO).min(this.total).setScale(2, RoundingMode.HALF_UP);
    if (this.amountPaid.signum() == 0) {
      this.status = InvoiceStatus.SENT;
      this.paidAt = null;
      this.paymentReference = null;
    } else if (this.amountPaid.compareTo(this.total) == 0) {
      this.status = InvoiceStatus.PAID; // paidAt kept — still settled
    } else {
      this.status = InvoiceStatus.PARTIALLY_PAID;
      this.paidAt = null;
    }
    this.updatedAt = Instant.now();
  }

  public void voidInvoice() {
    if (this.status == InvoiceStatus.PARTIALLY_PAID) {
      throw new IllegalStateException(
          "A partially paid fee note cannot be voided — reverse its payments first");
    }
    if (this.status != InvoiceStatus.APPROVED && this.status != InvoiceStatus.SENT) {
      throw new IllegalStateException("Only approved or sent invoices can be voided");
    }
    this.status = InvoiceStatus.VOID;
    this.updatedAt = Instant.now();
  }

  /** Voids a draft invoice — used when cancelling a billing run. */
  public void voidDraft() {
    if (this.status != InvoiceStatus.DRAFT) {
      throw new IllegalStateException("Only draft invoices can be voided via voidDraft");
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

  public BigDecimal getAmountPaid() {
    return amountPaid;
  }

  /** {@code total − amountPaid} — derived, never stored (ADR-347 A3). */
  public BigDecimal getBalanceDue() {
    return total.subtract(amountPaid);
  }

  public String getCustomerName() {
    return customerName;
  }

  public void setCustomerName(String customerName) {
    this.customerName = customerName;
    this.updatedAt = Instant.now();
  }

  public String getCustomerEmail() {
    return customerEmail;
  }

  public void setCustomerEmail(String customerEmail) {
    this.customerEmail = customerEmail;
    this.updatedAt = Instant.now();
  }

  public String getCustomerAddress() {
    return customerAddress;
  }

  public void setCustomerAddress(String customerAddress) {
    this.customerAddress = customerAddress;
    this.updatedAt = Instant.now();
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

  public String getPaymentSessionId() {
    return paymentSessionId;
  }

  public void setPaymentSessionId(String paymentSessionId) {
    this.paymentSessionId = paymentSessionId;
    this.updatedAt = Instant.now();
  }

  public String getPaymentUrl() {
    return paymentUrl;
  }

  public void setPaymentUrl(String paymentUrl) {
    this.paymentUrl = paymentUrl;
    this.updatedAt = Instant.now();
  }

  public String getPaymentDestination() {
    return paymentDestination;
  }

  public void setPaymentDestination(String paymentDestination) {
    this.paymentDestination = paymentDestination;
    this.updatedAt = Instant.now();
  }

  public UUID getBillingRunId() {
    return billingRunId;
  }

  public void setBillingRunId(UUID billingRunId) {
    this.billingRunId = billingRunId;
    this.updatedAt = Instant.now();
  }

  public String getPoNumber() {
    return poNumber;
  }

  public void setPoNumber(String poNumber) {
    this.poNumber = poNumber;
    this.updatedAt = Instant.now();
  }

  public TaxType getTaxType() {
    return taxType;
  }

  public void setTaxType(TaxType taxType) {
    this.taxType = taxType;
    this.updatedAt = Instant.now();
  }

  public LocalDate getBillingPeriodStart() {
    return billingPeriodStart;
  }

  public void setBillingPeriodStart(LocalDate billingPeriodStart) {
    this.billingPeriodStart = billingPeriodStart;
    this.updatedAt = Instant.now();
  }

  public LocalDate getBillingPeriodEnd() {
    return billingPeriodEnd;
  }

  public void setBillingPeriodEnd(LocalDate billingPeriodEnd) {
    this.billingPeriodEnd = billingPeriodEnd;
    this.updatedAt = Instant.now();
  }
}
