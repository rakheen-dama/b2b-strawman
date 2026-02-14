package io.b2mash.b2b.b2bstrawman.dashboard.dto;

/**
 * Response DTO for the task summary endpoint. Contains counts by status and overdue count.
 *
 * @param todo number of tasks with status OPEN
 * @param inProgress number of tasks with status IN_PROGRESS
 * @param inReview number of tasks with status IN_REVIEW
 * @param done number of tasks with status DONE
 * @param total total number of tasks
 * @param overdueCount number of non-DONE tasks past their due date
 */
public record TaskSummary(
    int todo, int inProgress, int inReview, int done, int total, int overdueCount) {}
