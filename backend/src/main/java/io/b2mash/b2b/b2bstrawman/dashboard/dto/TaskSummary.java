package io.b2mash.b2b.b2bstrawman.dashboard.dto;

/**
 * Response DTO for the task summary endpoint. Contains counts by status and overdue count.
 *
 * @param todo number of tasks with status OPEN
 * @param inProgress number of tasks with status IN_PROGRESS
 * @param done number of tasks with status DONE
 * @param cancelled number of tasks with status CANCELLED
 * @param total total number of tasks
 * @param overdueCount number of non-DONE tasks past their due date
 */
public record TaskSummary(
    int todo, int inProgress, int done, int cancelled, int total, int overdueCount) {}
