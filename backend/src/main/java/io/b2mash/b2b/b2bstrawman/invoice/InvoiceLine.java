package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAware;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAwareEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "invoice_lines")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class InvoiceLine implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "time_entry_id")
  private UUID timeEntryId;

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

  @Column(name = "tenant_id")
  private String tenantId;

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
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Recalculates amount as quantity * unitPrice and updates the updatedAt timestamp. */
  public void recalculateAmount() {
    this.amount = this.quantity.multiply(this.unitPrice).setScale(2, RoundingMode.HALF_UP);
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
