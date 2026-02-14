package io.b2mash.b2b.b2bstrawman.dashboard;

/**
 * Input data for the project health calculator.
 *
 * @param totalTasks total number of tasks in the project
 * @param doneTasks number of tasks with status DONE
 * @param overdueTasks number of non-DONE tasks past their due date
 * @param budgetConsumedPercent percentage of budget consumed, null if no budget configured
 * @param alertThresholdPct budget alert threshold from ProjectBudget, default 80
 * @param completionPercent derived: doneTasks / totalTasks * 100
 * @param daysSinceLastActivity days since the most recent AuditEvent for this project
 */
public record ProjectHealthInput(
    int totalTasks,
    int doneTasks,
    int overdueTasks,
    Double budgetConsumedPercent,
    int alertThresholdPct,
    double completionPercent,
    int daysSinceLastActivity) {}
