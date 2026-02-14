package io.b2mash.b2b.b2bstrawman.dashboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic rule-based project health scoring calculator.
 *
 * <p>Evaluates six rules against project metrics and returns the worst (highest severity) status
 * along with all triggered reasons. Pure utility class with no Spring dependencies.
 *
 * <p>Rules:
 *
 * <ol>
 *   <li>Budget overrun (>= 100%) -> CRITICAL
 *   <li>Budget at risk (>= alert threshold, low completion) -> AT_RISK
 *   <li>High overdue ratio (> 30%) -> CRITICAL
 *   <li>Moderate overdue ratio (> 10%) -> AT_RISK
 *   <li>Stale project (> 14 days inactive) -> AT_RISK
 *   <li>No tasks -> UNKNOWN (terminal, overrides everything)
 * </ol>
 */
public final class ProjectHealthCalculator {

  static final double OVERDUE_CRITICAL_THRESHOLD = 0.3;
  static final double OVERDUE_AT_RISK_THRESHOLD = 0.1;
  static final int INACTIVITY_DAYS_THRESHOLD = 14;
  static final int DEFAULT_BUDGET_ALERT_THRESHOLD = 80;

  private ProjectHealthCalculator() {}

  /**
   * Calculates the health status and reasons for a project.
   *
   * @param input the project health metrics
   * @return the computed health result with status and reasons
   */
  public static ProjectHealthResult calculate(ProjectHealthInput input) {
    HealthStatus status = HealthStatus.HEALTHY;
    List<String> reasons = new ArrayList<>();

    // Rule 1: Budget overrun (CRITICAL)
    if (input.budgetConsumedPercent() != null && input.budgetConsumedPercent() >= 100) {
      status = escalate(status, HealthStatus.CRITICAL);
      reasons.add("Over budget");
    }

    // Rule 2: Budget at risk — high consumption with low task completion
    if (input.budgetConsumedPercent() != null
        && input.budgetConsumedPercent() >= input.alertThresholdPct()
        && input.completionPercent() < input.budgetConsumedPercent() - 10) {
      status = escalate(status, HealthStatus.AT_RISK);
      reasons.add(
          "Budget %d%% consumed but only %d%% of tasks complete"
              .formatted(
                  Math.round(input.budgetConsumedPercent()),
                  Math.round(input.completionPercent())));
    }

    // Rule 3: High overdue ratio (CRITICAL)
    if (input.totalTasks() > 0) {
      double overdueRatio = (double) input.overdueTasks() / input.totalTasks();
      if (overdueRatio > OVERDUE_CRITICAL_THRESHOLD) {
        status = escalate(status, HealthStatus.CRITICAL);
        reasons.add("%d of %d tasks overdue".formatted(input.overdueTasks(), input.totalTasks()));
      }
      // Rule 4: Moderate overdue ratio (AT_RISK) — mutually exclusive with Rule 3
      else if (overdueRatio > OVERDUE_AT_RISK_THRESHOLD) {
        status = escalate(status, HealthStatus.AT_RISK);
        reasons.add("%d overdue tasks".formatted(input.overdueTasks()));
      }
    }

    // Rule 5: Stale project (AT_RISK)
    if (input.totalTasks() > 0 && input.daysSinceLastActivity() > INACTIVITY_DAYS_THRESHOLD) {
      status = escalate(status, HealthStatus.AT_RISK);
      reasons.add("No activity in %d days".formatted(input.daysSinceLastActivity()));
    }

    // Rule 6: No tasks (UNKNOWN) — terminal, overrides everything
    if (input.totalTasks() == 0) {
      status = HealthStatus.UNKNOWN;
      reasons = List.of("No tasks created yet");
    }

    return new ProjectHealthResult(status, List.copyOf(reasons));
  }

  /**
   * Returns the status with higher severity. Used to escalate from current status when a rule
   * fires.
   */
  private static HealthStatus escalate(HealthStatus current, HealthStatus candidate) {
    return current.severity() >= candidate.severity() ? current : candidate;
  }
}
