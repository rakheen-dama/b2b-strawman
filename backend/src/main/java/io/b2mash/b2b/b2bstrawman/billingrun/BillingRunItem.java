package io.b2mash.b2b.b2bstrawman.billingrun;

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
import java.util.UUID;

@Entity
@Table(name = "billing_run_items")
public class BillingRunItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "billing_run_id", nullable = false)
  private UUID billingRunId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private BillingRunItemStatus status;

  @Column(name = "invoice_id")
  private UUID invoiceId;

  @Column(name = "unbilled_time_amount", precision = 14, scale = 2)
  private BigDecimal unbilledTimeAmount;

  @Column(name = "unbilled_expense_amount", precision = 14, scale = 2)
  private BigDecimal unbilledExpenseAmount;

  @Column(name = "unbilled_time_count")
  private Integer unbilledTimeCount;

  @Column(name = "unbilled_expense_count")
  private Integer unbilledExpenseCount;

  @Column(name = "failure_reason", length = 1000)
  private String failureReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected BillingRunItem() {}

  public BillingRunItem(UUID billingRunId, UUID customerId) {
    this.billingRunId = billingRunId;
    this.customerId = customerId;
    this.status = BillingRunItemStatus.PENDING;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getBillingRunId() {
    return billingRunId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public BillingRunItemStatus getStatus() {
    return status;
  }

  public void setStatus(BillingRunItemStatus status) {
    this.status = status;
    this.updatedAt = Instant.now();
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public void setInvoiceId(UUID invoiceId) {
    this.invoiceId = invoiceId;
    this.updatedAt = Instant.now();
  }

  public BigDecimal getUnbilledTimeAmount() {
    return unbilledTimeAmount;
  }

  public void setUnbilledTimeAmount(BigDecimal unbilledTimeAmount) {
    this.unbilledTimeAmount = unbilledTimeAmount;
    this.updatedAt = Instant.now();
  }

  public BigDecimal getUnbilledExpenseAmount() {
    return unbilledExpenseAmount;
  }

  public void setUnbilledExpenseAmount(BigDecimal unbilledExpenseAmount) {
    this.unbilledExpenseAmount = unbilledExpenseAmount;
    this.updatedAt = Instant.now();
  }

  public Integer getUnbilledTimeCount() {
    return unbilledTimeCount;
  }

  public void setUnbilledTimeCount(Integer unbilledTimeCount) {
    this.unbilledTimeCount = unbilledTimeCount;
    this.updatedAt = Instant.now();
  }

  public Integer getUnbilledExpenseCount() {
    return unbilledExpenseCount;
  }

  public void setUnbilledExpenseCount(Integer unbilledExpenseCount) {
    this.unbilledExpenseCount = unbilledExpenseCount;
    this.updatedAt = Instant.now();
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
    this.updatedAt = Instant.now();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
