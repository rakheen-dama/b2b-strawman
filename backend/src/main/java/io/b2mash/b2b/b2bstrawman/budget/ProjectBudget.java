package io.b2mash.b2b.b2bstrawman.budget;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAware;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAwareEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "project_budgets")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class ProjectBudget implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false, unique = true)
  private UUID projectId;

  @Column(name = "budget_hours", precision = 10, scale = 2)
  private BigDecimal budgetHours;

  @Column(name = "budget_amount", precision = 14, scale = 2)
  private BigDecimal budgetAmount;

  @Column(name = "budget_currency", length = 3)
  private String budgetCurrency;

  @Column(name = "alert_threshold_pct", nullable = false)
  private int alertThresholdPct = 80;

  @Column(name = "threshold_notified", nullable = false)
  private boolean thresholdNotified = false;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Version private Long version;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ProjectBudget() {}

  public ProjectBudget(
      UUID projectId,
      BigDecimal budgetHours,
      BigDecimal budgetAmount,
      String budgetCurrency,
      int alertThresholdPct,
      String notes) {
    this.projectId = projectId;
    this.budgetHours = budgetHours;
    this.budgetAmount = budgetAmount;
    this.budgetCurrency = budgetCurrency;
    this.alertThresholdPct = alertThresholdPct;
    this.notes = notes;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /**
   * Updates the budget values. Resets {@code thresholdNotified} to false if any budget value
   * changes, so threshold alerts can fire again for the new limits.
   */
  public void updateBudget(
      BigDecimal budgetHours,
      BigDecimal budgetAmount,
      String budgetCurrency,
      int alertThresholdPct,
      String notes) {
    boolean budgetChanged =
        !bigDecimalEquals(this.budgetHours, budgetHours)
            || !bigDecimalEquals(this.budgetAmount, budgetAmount)
            || alertThresholdPct != this.alertThresholdPct;

    this.budgetHours = budgetHours;
    this.budgetAmount = budgetAmount;
    this.budgetCurrency = budgetCurrency;
    this.alertThresholdPct = alertThresholdPct;
    this.notes = notes;
    this.updatedAt = Instant.now();

    if (budgetChanged) {
      this.thresholdNotified = false;
    }
  }

  public void resetThresholdNotified() {
    this.thresholdNotified = false;
  }

  public void markThresholdNotified() {
    this.thresholdNotified = true;
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public BigDecimal getBudgetHours() {
    return budgetHours;
  }

  public BigDecimal getBudgetAmount() {
    return budgetAmount;
  }

  public String getBudgetCurrency() {
    return budgetCurrency;
  }

  public int getAlertThresholdPct() {
    return alertThresholdPct;
  }

  public boolean isThresholdNotified() {
    return thresholdNotified;
  }

  public String getNotes() {
    return notes;
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

  private static boolean bigDecimalEquals(BigDecimal a, BigDecimal b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return a.compareTo(b) == 0;
  }
}
