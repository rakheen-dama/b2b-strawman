package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.tax.TaxRate;
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
import java.util.UUID;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "time_entry_id")
  private UUID timeEntryId;

  @Column(name = "retainer_period_id")
  private UUID retainerPeriodId;

  @Column(name = "expense_id")
  private UUID expenseId;

  @Enumerated(EnumType.STRING)
  @Column(name = "line_type", nullable = false, length = 20)
  private InvoiceLineType lineType;

  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "quantity", nullable = false, precision = 10, scale = 4)
  private BigDecimal quantity;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  // --- Tax fields (denormalized snapshots from TaxRate) ---

  @Column(name = "tax_rate_id")
  private UUID taxRateId;

  @Column(name = "tax_rate_name", length = 100)
  private String taxRateName;

  @Column(name = "tax_rate_percent", precision = 5, scale = 2)
  private BigDecimal taxRatePercent;

  @Column(name = "tax_amount", precision = 14, scale = 2)
  private BigDecimal taxAmount;

  @Column(name = "tax_exempt", nullable = false)
  private boolean taxExempt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected InvoiceLine() {}

  public InvoiceLine(
      UUID invoiceId,
      UUID projectId,
      UUID timeEntryId,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      int sortOrder) {
    this.invoiceId = invoiceId;
    this.projectId = projectId;
    this.timeEntryId = timeEntryId;
    this.description = description;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.amount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    this.sortOrder = sortOrder;
    this.lineType = InvoiceLineType.TIME;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Recalculates amount as quantity * unitPrice and updates the updatedAt timestamp. */
  public void recalculateAmount() {
    this.amount = this.quantity.multiply(this.unitPrice).setScale(2, RoundingMode.HALF_UP);
    this.updatedAt = Instant.now();
  }

  /** Updates line item fields and recalculates amount. */
  public void update(String description, BigDecimal quantity, BigDecimal unitPrice, int sortOrder) {
    this.description = description;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.sortOrder = sortOrder;
    recalculateAmount();
  }

  /**
   * Applies a tax rate to this line, snapshotting the rate's fields and storing the calculated tax
   * amount.
   *
   * @param taxRate the tax rate to apply
   * @param calculatedTaxAmount the pre-calculated tax amount for this line
   */
  public void applyTaxRate(TaxRate taxRate, BigDecimal calculatedTaxAmount) {
    this.taxRateId = taxRate.getId();
    this.taxRateName = taxRate.getName();
    this.taxRatePercent = taxRate.getRate();
    this.taxExempt = taxRate.isExempt();
    this.taxAmount = calculatedTaxAmount;
    this.updatedAt = Instant.now();
  }

  /**
   * Re-copies the tax rate's name, percentage, and exempt flag without changing the tax amount.
   * Used when a tax rate is updated and lines need their snapshots refreshed.
   *
   * @param taxRate the tax rate to refresh from
   */
  public void refreshTaxSnapshot(TaxRate taxRate) {
    this.taxRateName = taxRate.getName();
    this.taxRatePercent = taxRate.getRate();
    this.taxExempt = taxRate.isExempt();
    this.updatedAt = Instant.now();
  }

  /** Clears all tax fields, resetting this line to a no-tax state. */
  public void clearTaxRate() {
    this.taxRateId = null;
    this.taxRateName = null;
    this.taxRatePercent = null;
    this.taxAmount = null;
    this.taxExempt = false;
    this.updatedAt = Instant.now();
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getTimeEntryId() {
    return timeEntryId;
  }

  public UUID getRetainerPeriodId() {
    return retainerPeriodId;
  }

  public void setRetainerPeriodId(UUID retainerPeriodId) {
    this.retainerPeriodId = retainerPeriodId;
  }

  public UUID getExpenseId() {
    return expenseId;
  }

  public void setExpenseId(UUID expenseId) {
    this.expenseId = expenseId;
  }

  public InvoiceLineType getLineType() {
    return lineType;
  }

  public void setLineType(InvoiceLineType lineType) {
    this.lineType = lineType;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public UUID getTaxRateId() {
    return taxRateId;
  }

  public String getTaxRateName() {
    return taxRateName;
  }

  public BigDecimal getTaxRatePercent() {
    return taxRatePercent;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public boolean isTaxExempt() {
    return taxExempt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
