package io.b2mash.b2b.b2bstrawman.dashboard.dto;

/**
 * Metrics sub-record within ProjectHealthDetail, containing the raw data points used for health
 * scoring.
 *
 * @param tasksDone number of tasks with status DONE
 * @param tasksInProgress number of tasks with status IN_PROGRESS
 * @param tasksTodo number of tasks with status OPEN
 * @param tasksOverdue number of non-DONE tasks past their due date
 * @param totalTasks total number of tasks in the project
 * @param completionPercent percentage of tasks completed (doneTasks / totalTasks * 100)
 * @param budgetConsumedPercent percentage of budget consumed, null if no budget configured
 * @param hoursThisPeriod total hours logged in the current period
 * @param daysSinceLastActivity days since the most recent audit event for this project
 */
public record ProjectHealthMetrics(
    int tasksDone,
    int tasksInProgress,
    int tasksTodo,
    int tasksOverdue,
    int totalTasks,
    double completionPercent,
    Double budgetConsumedPercent,
    double hoursThisPeriod,
    int daysSinceLastActivity) {}
