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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "billing_runs")
public class BillingRun {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", length = 300)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private BillingRunStatus status;

  @Column(name = "period_from", nullable = false)
  private LocalDate periodFrom;

  @Column(name = "period_to", nullable = false)
  private LocalDate periodTo;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "include_expenses", nullable = false)
  private boolean includeExpenses;

  @Column(name = "include_retainers", nullable = false)
  private boolean includeRetainers;

  @Column(name = "total_customers")
  private Integer totalCustomers;

  @Column(name = "total_invoices")
  private Integer totalInvoices;

  @Column(name = "total_amount", precision = 14, scale = 2)
  private BigDecimal totalAmount;

  @Column(name = "total_sent")
  private Integer totalSent;

  @Column(name = "total_failed")
  private Integer totalFailed;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected BillingRun() {}

  public BillingRun(
      String name,
      LocalDate periodFrom,
      LocalDate periodTo,
      String currency,
      boolean includeExpenses,
      boolean includeRetainers,
      UUID createdBy) {
    this.name = name;
    this.periodFrom = periodFrom;
    this.periodTo = periodTo;
    this.currency = currency;
    this.includeExpenses = includeExpenses;
    this.includeRetainers = includeRetainers;
    this.status = BillingRunStatus.PREVIEW;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void startGeneration() {
    if (this.status != BillingRunStatus.PREVIEW) {
      throw new IllegalStateException("Only PREVIEW runs can start generation");
    }
    this.status = BillingRunStatus.IN_PROGRESS;
    this.updatedAt = Instant.now();
  }

  public void complete(int totalInvoices, int totalFailed, BigDecimal totalAmount) {
    this.status = BillingRunStatus.COMPLETED;
    this.totalInvoices = totalInvoices;
    this.totalFailed = totalFailed;
    this.totalAmount = totalAmount;
    this.completedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void cancel() {
    this.status = BillingRunStatus.CANCELLED;
    this.completedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public BillingRunStatus getStatus() {
    return status;
  }

  public LocalDate getPeriodFrom() {
    return periodFrom;
  }

  public LocalDate getPeriodTo() {
    return periodTo;
  }

  public String getCurrency() {
    return currency;
  }

  public boolean isIncludeExpenses() {
    return includeExpenses;
  }

  public boolean isIncludeRetainers() {
    return includeRetainers;
  }

  public Integer getTotalCustomers() {
    return totalCustomers;
  }

  public void setTotalCustomers(Integer totalCustomers) {
    this.totalCustomers = totalCustomers;
    this.updatedAt = Instant.now();
  }

  public Integer getTotalInvoices() {
    return totalInvoices;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public Integer getTotalSent() {
    return totalSent;
  }

  public void setTotalSent(Integer totalSent) {
    this.totalSent = totalSent;
    this.updatedAt = Instant.now();
  }

  public Integer getTotalFailed() {
    return totalFailed;
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

  public Instant getCompletedAt() {
    return completedAt;
  }
}
