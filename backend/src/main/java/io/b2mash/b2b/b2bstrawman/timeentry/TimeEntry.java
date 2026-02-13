package io.b2mash.b2b.b2bstrawman.timeentry;

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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "time_entries")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class TimeEntry implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "task_id", nullable = false)
  private UUID taskId;

  @Column(name = "member_id", nullable = false)
  private UUID memberId;

  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  @Column(name = "billable", nullable = false)
  private boolean billable;

  @Deprecated
  @Column(name = "rate_cents")
  private Integer rateCents;

  @Column(name = "billing_rate_snapshot", precision = 12, scale = 2)
  private BigDecimal billingRateSnapshot;

  @Column(name = "billing_rate_currency", length = 3)
  private String billingRateCurrency;

  @Column(name = "cost_rate_snapshot", precision = 12, scale = 2)
  private BigDecimal costRateSnapshot;

  @Column(name = "cost_rate_currency", length = 3)
  private String costRateCurrency;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TimeEntry() {}

  public TimeEntry(
      UUID taskId,
      UUID memberId,
      LocalDate date,
      int durationMinutes,
      boolean billable,
      Integer rateCents,
      String description) {
    this.taskId = taskId;
    this.memberId = memberId;
    this.date = date;
    this.durationMinutes = durationMinutes;
    this.billable = billable;
    this.rateCents = rateCents;
    this.description = description;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
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

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public boolean isBillable() {
    return billable;
  }

  @Deprecated
  public Integer getRateCents() {
    return rateCents;
  }

  public String getDescription() {
    return description;
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

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public void setDurationMinutes(int durationMinutes) {
    this.durationMinutes = durationMinutes;
  }

  public void setBillable(boolean billable) {
    this.billable = billable;
  }

  @Deprecated
  public void setRateCents(Integer rateCents) {
    this.rateCents = rateCents;
  }

  public BigDecimal getBillingRateSnapshot() {
    return billingRateSnapshot;
  }

  public String getBillingRateCurrency() {
    return billingRateCurrency;
  }

  public BigDecimal getCostRateSnapshot() {
    return costRateSnapshot;
  }

  public String getCostRateCurrency() {
    return costRateCurrency;
  }

  public void snapshotBillingRate(BigDecimal rate, String currency) {
    this.billingRateSnapshot = rate;
    this.billingRateCurrency = currency;
  }

  public void snapshotCostRate(BigDecimal rate, String currency) {
    this.costRateSnapshot = rate;
    this.costRateCurrency = currency;
  }

  /**
   * Computed billable value: (durationMinutes / 60) * billingRateSnapshot. Returns null if not
   * billable or no billing rate snapshot.
   */
  public BigDecimal getBillableValue() {
    if (!billable || billingRateSnapshot == null) {
      return null;
    }
    return BigDecimal.valueOf(durationMinutes)
        .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP)
        .multiply(billingRateSnapshot);
  }

  /**
   * Computed cost value: (durationMinutes / 60) * costRateSnapshot. Returns null if no cost rate
   * snapshot. Includes both billable and non-billable entries.
   */
  public BigDecimal getCostValue() {
    if (costRateSnapshot == null) {
      return null;
    }
    return BigDecimal.valueOf(durationMinutes)
        .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP)
        .multiply(costRateSnapshot);
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
