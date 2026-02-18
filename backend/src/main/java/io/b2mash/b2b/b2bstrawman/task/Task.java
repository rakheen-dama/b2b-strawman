package io.b2mash.b2b.b2bstrawman.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "priority", nullable = false, length = 20)
  private String priority;

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
    this.status = "OPEN";
    this.priority = priority != null ? priority : "MEDIUM";
    this.type = type;
    this.dueDate = dueDate;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void update(
      String title,
      String description,
      String priority,
      String status,
      String type,
      LocalDate dueDate,
      UUID assigneeId) {
    this.title = title;
    this.description = description;
    this.priority = priority;
    this.status = status;
    this.type = type;
    this.dueDate = dueDate;
    this.assigneeId = assigneeId;
    this.updatedAt = Instant.now();
  }

  /** Claims this task for the given member. Sets status to IN_PROGRESS and assigns the member. */
  public void claim(UUID memberId) {
    this.assigneeId = memberId;
    this.status = "IN_PROGRESS";
    this.updatedAt = Instant.now();
  }

  /** Releases this task. Clears assignee and resets status to OPEN. */
  public void release() {
    this.assigneeId = null;
    this.status = "OPEN";
    this.updatedAt = Instant.now();
  }

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

  public String getStatus() {
    return status;
  }

  public String getPriority() {
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
