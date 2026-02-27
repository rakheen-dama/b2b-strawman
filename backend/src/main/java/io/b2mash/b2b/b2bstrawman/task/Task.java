package io.b2mash.b2b.b2bstrawman.task;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tasks")
public class Task {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "title", nullable = false, length = 500)
  private String title;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private TaskStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "priority", nullable = false, length = 20)
  private TaskPriority priority;

  @Column(name = "type", length = 100)
  private String type;

  @Column(name = "assignee_id")
  private UUID assigneeId;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Version
  @Column(name = "version", nullable = false)
  private int version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "completed_by")
  private UUID completedBy;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancelled_by")
  private UUID cancelledBy;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "custom_fields", columnDefinition = "jsonb")
  private Map<String, Object> customFields = new HashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "applied_field_groups", columnDefinition = "jsonb")
  private List<UUID> appliedFieldGroups;

  protected Task() {}

  public Task(
      UUID projectId,
      String title,
      String description,
      String priority,
      String type,
      LocalDate dueDate,
      UUID createdBy) {
    this.projectId = projectId;
    this.title = title;
    this.description = description;
    this.status = TaskStatus.OPEN;
    this.priority = priority != null ? TaskPriority.valueOf(priority) : TaskPriority.MEDIUM;
    this.type = type;
    this.dueDate = dueDate;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /**
   * Updates task fields. If the status changes, validates the transition and manages lifecycle
   * timestamps accordingly. The actorId is recorded as completedBy or cancelledBy when
   * transitioning to terminal states.
   */
  public void update(
      String title,
      String description,
      TaskPriority priority,
      TaskStatus newStatus,
      String type,
      LocalDate dueDate,
      UUID assigneeId,
      UUID actorId) {
    this.title = title;
    this.description = description;
    this.priority = priority;
    this.type = type;
    this.dueDate = dueDate;
    this.assigneeId = assigneeId;

    // Handle status transition with lifecycle timestamp bookkeeping
    if (newStatus != this.status) {
      requireTransition(newStatus, "update status");

      // Manage lifecycle timestamps based on the target status
      switch (newStatus) {
        case DONE -> {
          this.completedAt = Instant.now();
          this.completedBy = actorId;
          this.cancelledAt = null;
          this.cancelledBy = null;
        }
        case CANCELLED -> {
          this.cancelledAt = Instant.now();
          this.cancelledBy = actorId;
          this.completedAt = null;
          this.completedBy = null;
        }
        case OPEN, IN_PROGRESS -> {
          this.completedAt = null;
          this.completedBy = null;
          this.cancelledAt = null;
          this.cancelledBy = null;
        }
      }
      this.status = newStatus;
    }

    this.updatedAt = Instant.now();
  }

  // --- Lifecycle transition methods ---

  /**
   * Claims this task for the given member. Validates that the task is OPEN before transitioning to
   * IN_PROGRESS.
   */
  public void claim(UUID memberId) {
    requireTransition(TaskStatus.IN_PROGRESS, "claim");
    this.assigneeId = memberId;
    this.status = TaskStatus.IN_PROGRESS;
    this.updatedAt = Instant.now();
  }

  /** Releases this task. Validates that the task is IN_PROGRESS before transitioning to OPEN. */
  public void release() {
    requireTransition(TaskStatus.OPEN, "release");
    this.assigneeId = null;
    this.status = TaskStatus.OPEN;
    this.updatedAt = Instant.now();
  }

  /**
   * Completes this task. Sets status to DONE and records completion timestamp and actor. Only valid
   * from IN_PROGRESS.
   */
  public void complete(UUID memberId) {
    requireTransition(TaskStatus.DONE, "complete");
    this.status = TaskStatus.DONE;
    this.completedAt = Instant.now();
    this.completedBy = memberId;
    this.updatedAt = Instant.now();
  }

  /**
   * Cancels this task. Sets status to CANCELLED and records cancellation timestamp and actor. Valid
   * from OPEN or IN_PROGRESS.
   */
  public void cancel(UUID cancelledBy) {
    requireTransition(TaskStatus.CANCELLED, "cancel");
    this.status = TaskStatus.CANCELLED;
    this.cancelledAt = Instant.now();
    this.cancelledBy = cancelledBy;
    this.updatedAt = Instant.now();
  }

  /**
   * Reopens this task from a terminal state (DONE or CANCELLED). Clears all lifecycle timestamps.
   */
  public void reopen() {
    requireTransition(TaskStatus.OPEN, "reopen");
    this.status = TaskStatus.OPEN;
    this.completedAt = null;
    this.completedBy = null;
    this.cancelledAt = null;
    this.cancelledBy = null;
    this.updatedAt = Instant.now();
  }

  // --- Private helpers ---

  private void requireTransition(TaskStatus target, String action) {
    if (!this.status.canTransitionTo(target)) {
      throw new InvalidStateException(
          "Invalid task state", "Cannot " + action + " task in status " + this.status);
    }
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public TaskPriority getPriority() {
    return priority;
  }

  public String getType() {
    return type;
  }

  public UUID getAssigneeId() {
    return assigneeId;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public int getVersion() {
    return version;
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

  public UUID getCompletedBy() {
    return completedBy;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public UUID getCancelledBy() {
    return cancelledBy;
  }

  public Map<String, Object> getCustomFields() {
    return customFields;
  }

  public void setCustomFields(Map<String, Object> customFields) {
    this.customFields = customFields;
    this.updatedAt = Instant.now();
  }

  public List<UUID> getAppliedFieldGroups() {
    return appliedFieldGroups;
  }

  public void setAppliedFieldGroups(List<UUID> appliedFieldGroups) {
    this.appliedFieldGroups = appliedFieldGroups;
    this.updatedAt = Instant.now();
  }
}
