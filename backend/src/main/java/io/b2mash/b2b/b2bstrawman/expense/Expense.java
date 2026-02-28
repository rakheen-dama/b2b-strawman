package io.b2mash.b2b.b2bstrawman.expense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "expenses")
public class Expense {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "task_id")
  private UUID taskId;

  @Column(name = "member_id", nullable = false)
  private UUID memberId;

  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "description", nullable = false, length = 500)
  private String description;

  @Column(name = "amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "category", nullable = false, length = 30)
  private String category;

  @Column(name = "receipt_document_id")
  private UUID receiptDocumentId;

  @Column(name = "billable", nullable = false)
  private boolean billable;

  @Column(name = "invoice_id")
  private UUID invoiceId;

  @Column(name = "markup_percent", precision = 5, scale = 2)
  private BigDecimal markupPercent;

  @Column(name = "notes", length = 1000)
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Expense() {}

  public Expense(
      UUID projectId,
      UUID memberId,
      LocalDate date,
      String description,
      BigDecimal amount,
      String currency,
      String category) {
    this.projectId = projectId;
    this.memberId = memberId;
    this.date = date;
    this.description = description;
    this.amount = amount;
    this.currency = currency;
    this.category = category;
    this.billable = true;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /**
   * Returns the billing status derived from the billable flag and invoice association.
   *
   * @return "BILLED" if invoiced, "NON_BILLABLE" if not billable, "UNBILLED" otherwise
   */
  public String getBillingStatus() {
    if (invoiceId != null) {
      return "BILLED";
    }
    if (!billable) {
      return "NON_BILLABLE";
    }
    return "UNBILLED";
  }

  /**
   * Computes the billable amount using per-expense markup only (no org context). Returns the amount
   * with markup applied if markupPercent is set, otherwise the raw amount. Returns zero if
   * non-billable.
   */
  public BigDecimal getBillableAmount() {
    if (!billable) {
      return BigDecimal.ZERO;
    }
    if (markupPercent != null) {
      return amount
          .multiply(BigDecimal.ONE.add(markupPercent.divide(HUNDRED, 4, RoundingMode.HALF_UP)))
          .setScale(2, RoundingMode.HALF_UP);
    }
    return amount;
  }

  /**
   * Computes the billable amount using effective markup: per-expense override, then org default
   * fallback, then zero. Returns zero if non-billable.
   *
   * @param orgDefaultMarkupPercent the organization-level default markup percentage (nullable)
   */
  public BigDecimal computeBillableAmount(BigDecimal orgDefaultMarkupPercent) {
    if (!billable) {
      return BigDecimal.ZERO;
    }
    BigDecimal effectiveMarkup =
        markupPercent != null
            ? markupPercent
            : (orgDefaultMarkupPercent != null ? orgDefaultMarkupPercent : BigDecimal.ZERO);
    return amount
        .multiply(BigDecimal.ONE.add(effectiveMarkup.divide(HUNDRED, 4, RoundingMode.HALF_UP)))
        .setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * Updates all mutable fields of the expense. Cannot be called on a billed expense.
   *
   * @throws IllegalStateException if the expense is billed
   */
  public void update(
      LocalDate date,
      String description,
      BigDecimal amount,
      String currency,
      String category,
      UUID taskId,
      UUID receiptDocumentId,
      BigDecimal markupPercent,
      boolean billable,
      String notes) {
    if (invoiceId != null) {
      throw new IllegalStateException("Cannot update a billed expense");
    }
    this.date = date;
    this.description = description;
    this.amount = amount;
    this.currency = currency;
    this.category = category;
    this.taskId = taskId;
    this.receiptDocumentId = receiptDocumentId;
    this.markupPercent = markupPercent;
    this.billable = billable;
    this.notes = notes;
    this.updatedAt = Instant.now();
  }

  /**
   * Writes off the expense by marking it as non-billable.
   *
   * @throws IllegalStateException if the expense is billed or already non-billable
   */
  public void writeOff() {
    if (invoiceId != null) {
      throw new IllegalStateException("Cannot write off a billed expense");
    }
    if (!billable) {
      throw new IllegalStateException("Expense is already non-billable");
    }
    this.billable = false;
    this.updatedAt = Instant.now();
  }

  /**
   * Restores a written-off expense back to billable.
   *
   * @throws IllegalStateException if the expense is already billable or billed
   */
  public void restore() {
    if (billable) {
      throw new IllegalStateException("Expense is already billable");
    }
    if (invoiceId != null) {
      throw new IllegalStateException("Cannot restore a billed expense");
    }
    this.billable = true;
    this.updatedAt = Instant.now();
  }

  // Getters

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getTaskId() {
    return taskId;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public LocalDate getDate() {
    return date;
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

  public String getCategory() {
    return category;
  }

  public UUID getReceiptDocumentId() {
    return receiptDocumentId;
  }

  public boolean isBillable() {
    return billable;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public BigDecimal getMarkupPercent() {
    return markupPercent;
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

  // Setters for fields managed externally

  public void setInvoiceId(UUID invoiceId) {
    this.invoiceId = invoiceId;
  }

  public void setTaskId(UUID taskId) {
    this.taskId = taskId;
  }

  public void setReceiptDocumentId(UUID receiptDocumentId) {
    this.receiptDocumentId = receiptDocumentId;
  }

  public void setMarkupPercent(BigDecimal markupPercent) {
    this.markupPercent = markupPercent;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }
}
