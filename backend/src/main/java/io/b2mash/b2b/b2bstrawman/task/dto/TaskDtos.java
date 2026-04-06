package io.b2mash.b2b.b2bstrawman.task.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import io.b2mash.b2b.b2bstrawman.task.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TaskDtos {

  private TaskDtos() {}

  /**
   * Request body for task creation.
   *
   * @param assigneeId Optional. The member to pre-assign the task to at creation time. Only honored
   *     for admin/owner callers; silently ignored for regular members. See {@link
   *     io.b2mash.b2b.b2bstrawman.task.TaskService#createTask} for the permission asymmetry
   *     rationale.
   */
  public record CreateTaskRequest(
      @NotBlank(message = "title is required")
          @Size(max = 500, message = "title must be at most 500 characters")
          String title,
      String description,
      @Size(max = 20, message = "priority must be at most 20 characters") String priority,
      @Size(max = 100, message = "type must be at most 100 characters") String type,
      LocalDate dueDate,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      UUID assigneeId,
      @Size(max = 100, message = "recurrenceRule must be at most 100 characters")
          String recurrenceRule,
      LocalDate recurrenceEndDate) {}

  public record UpdateTaskRequest(
      @NotBlank(message = "title is required")
          @Size(max = 500, message = "title must be at most 500 characters")
          String title,
      String description,
      @NotBlank(message = "priority is required")
          @Size(max = 20, message = "priority must be at most 20 characters")
          String priority,
      @NotBlank(message = "status is required")
          @Size(max = 20, message = "status must be at most 20 characters")
          String status,
      @Size(max = 100, message = "type must be at most 100 characters") String type,
      LocalDate dueDate,
      UUID assigneeId,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      @Size(max = 100, message = "recurrenceRule must be at most 100 characters")
          String recurrenceRule,
      LocalDate recurrenceEndDate) {}

  /**
   * Backward-compatible response for the complete endpoint. The completed task fields are unwrapped
   * to the top level (so response.status, response.title, etc. still work). The optional
   * nextInstance field is a nested TaskResponse for the next recurring instance.
   */
  public record CompleteTaskResponse(
      @JsonUnwrapped TaskResponse completedTask, TaskResponse nextInstance) {}

  public record TaskResponse(
      UUID id,
      UUID projectId,
      String title,
      String description,
      String status,
      String priority,
      String type,
      UUID assigneeId,
      String assigneeName,
      UUID createdBy,
      String createdByName,
      LocalDate dueDate,
      int version,
      Instant createdAt,
      Instant updatedAt,
      Instant completedAt,
      UUID completedBy,
      String completedByName,
      Instant cancelledAt,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      List<TagResponse> tags,
      String recurrenceRule,
      LocalDate recurrenceEndDate,
      UUID parentTaskId,
      boolean isRecurring) {

    public static TaskResponse from(
        Task task, Map<UUID, String> memberNames, List<TagResponse> tags) {
      return new TaskResponse(
          task.getId(),
          task.getProjectId(),
          task.getTitle(),
          task.getDescription(),
          task.getStatus().name(),
          task.getPriority().name(),
          task.getType(),
          task.getAssigneeId(),
          task.getAssigneeId() != null ? memberNames.get(task.getAssigneeId()) : null,
          task.getCreatedBy(),
          memberNames.get(task.getCreatedBy()),
          task.getDueDate(),
          task.getVersion(),
          task.getCreatedAt(),
          task.getUpdatedAt(),
          task.getCompletedAt(),
          task.getCompletedBy(),
          task.getCompletedBy() != null ? memberNames.get(task.getCompletedBy()) : null,
          task.getCancelledAt(),
          task.getCustomFields(),
          task.getAppliedFieldGroups(),
          tags,
          task.getRecurrenceRule(),
          task.getRecurrenceEndDate(),
          task.getParentTaskId(),
          task.isRecurring());
    }
  }
}
