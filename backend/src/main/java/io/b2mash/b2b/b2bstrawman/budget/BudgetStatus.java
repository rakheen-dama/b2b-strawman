package io.b2mash.b2b.b2bstrawman.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Computed budget status record combining budget configuration with actual consumption from time
 * entries. Status is derived from percentage consumed vs the alert threshold.
 */
public record BudgetStatus(
    UUID projectId,
    BigDecimal budgetHours,
    BigDecimal budgetAmount,
    String budgetCurrency,
    int alertThresholdPct,
    String notes,
    BigDecimal hoursConsumed,
    BigDecimal hoursRemaining,
    BigDecimal hoursConsumedPct,
    BigDecimal amountConsumed,
    BigDecimal amountRemaining,
    BigDecimal amountConsumedPct,
    BudgetStatusEnum hoursStatus,
    BudgetStatusEnum amountStatus,
    BudgetStatusEnum overallStatus) {

  public enum BudgetStatusEnum {
    ON_TRACK,
    AT_RISK,
    OVER_BUDGET;

    /**
     * Determines status from a percentage value and alert threshold.
     *
     * @param pct the consumption percentage (0-100+)
     * @param thresholdPct the alert threshold percentage (50-100)
     * @return ON_TRACK if below threshold, AT_RISK if at/above threshold but below 100, OVER_BUDGET
     *     if at/above 100
     */
    public static BudgetStatusEnum fromPct(BigDecimal pct, int thresholdPct) {
      if (pct.compareTo(BigDecimal.valueOf(100)) >= 0) return OVER_BUDGET;
      if (pct.compareTo(BigDecimal.valueOf(thresholdPct)) >= 0) return AT_RISK;
      return ON_TRACK;
    }

    /** Returns the worse (higher severity) of two statuses. Null-safe: returns the non-null one. */
    public static BudgetStatusEnum worse(BudgetStatusEnum a, BudgetStatusEnum b) {
      if (a == null) return b;
      if (b == null) return a;
      return a.ordinal() > b.ordinal() ? a : b;
    }
  }

  /**
   * Computes a full BudgetStatus from a ProjectBudget and actual consumption values.
   *
   * @param budget the budget configuration
   * @param hoursConsumed total hours consumed (all time entries, billable + non-billable)
   * @param amountConsumed total amount consumed (billable entries with matching currency)
   * @return computed status with derived percentages and statuses
   */
  public static BudgetStatus compute(
      ProjectBudget budget, BigDecimal hoursConsumed, BigDecimal amountConsumed) {
    int threshold = budget.getAlertThresholdPct();

    // Hours
    BigDecimal hoursRemaining = null;
    BigDecimal hoursConsumedPct = null;
    BudgetStatusEnum hoursStatus = null;
    if (budget.getBudgetHours() != null && budget.getBudgetHours().signum() > 0) {
      hoursRemaining = budget.getBudgetHours().subtract(hoursConsumed);
      hoursConsumedPct =
          hoursConsumed
              .divide(budget.getBudgetHours(), 4, RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(100))
              .setScale(2, RoundingMode.HALF_UP);
      hoursStatus = BudgetStatusEnum.fromPct(hoursConsumedPct, threshold);
    }

    // Amount
    BigDecimal amountRemaining = null;
    BigDecimal amountConsumedPct = null;
    BudgetStatusEnum amountStatus = null;
    if (budget.getBudgetAmount() != null && budget.getBudgetAmount().signum() > 0) {
      amountRemaining = budget.getBudgetAmount().subtract(amountConsumed);
      amountConsumedPct =
          amountConsumed
              .divide(budget.getBudgetAmount(), 4, RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(100))
              .setScale(2, RoundingMode.HALF_UP);
      amountStatus = BudgetStatusEnum.fromPct(amountConsumedPct, threshold);
    }

    BudgetStatusEnum overallStatus = BudgetStatusEnum.worse(hoursStatus, amountStatus);

    return new BudgetStatus(
        budget.getProjectId(),
        budget.getBudgetHours(),
        budget.getBudgetAmount(),
        budget.getBudgetCurrency(),
        threshold,
        budget.getNotes(),
        hoursConsumed,
        hoursRemaining,
        hoursConsumedPct,
        amountConsumed,
        amountRemaining,
        amountConsumedPct,
        hoursStatus,
        amountStatus,
        overallStatus);
  }
}
