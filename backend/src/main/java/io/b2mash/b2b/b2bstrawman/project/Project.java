package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "projects")
public class Project {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ProjectStatus status;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "completed_by")
  private UUID completedBy;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "archived_by")
  private UUID archivedBy;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "custom_fields", columnDefinition = "jsonb")
  private Map<String, Object> customFields = new HashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "applied_field_groups", columnDefinition = "jsonb")
  private List<UUID> appliedFieldGroups;

  protected Project() {}

  public Project(String name, String description, UUID createdBy) {
    this.name = name;
    this.description = description;
    this.createdBy = createdBy;
    this.status = ProjectStatus.ACTIVE;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public ProjectStatus getStatus() {
    return status;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public void setCustomerId(UUID customerId) {
    this.customerId = customerId;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public void setDueDate(LocalDate dueDate) {
    this.dueDate = dueDate;
  }

  public UUID getCreatedBy() {
    return createdBy;
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

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public UUID getArchivedBy() {
    return archivedBy;
  }

  public void update(String name, String description, UUID customerId, LocalDate dueDate) {
    this.name = name;
    this.description = description;
    this.customerId = customerId != null ? customerId : this.customerId;
    this.dueDate = dueDate != null ? dueDate : this.dueDate;
    this.updatedAt = Instant.now();
  }

  /** Marks project COMPLETED. Records completedAt and completedBy. Only valid from ACTIVE. */
  public void complete(UUID memberId) {
    requireTransition(ProjectStatus.COMPLETED, "complete");
    this.status = ProjectStatus.COMPLETED;
    this.completedAt = Instant.now();
    this.completedBy = memberId;
    this.updatedAt = Instant.now();
  }

  /** Archives this project. Records archivedAt and archivedBy. Valid from ACTIVE or COMPLETED. */
  public void archive(UUID memberId) {
    requireTransition(ProjectStatus.ARCHIVED, "archive");
    this.status = ProjectStatus.ARCHIVED;
    this.archivedAt = Instant.now();
    this.archivedBy = memberId;
    this.updatedAt = Instant.now();
  }

  /** Reopens a COMPLETED or ARCHIVED project back to ACTIVE. Clears lifecycle timestamps. */
  public void reopen() {
    requireTransition(ProjectStatus.ACTIVE, "reopen");
    this.status = ProjectStatus.ACTIVE;
    this.completedAt = null;
    this.completedBy = null;
    this.archivedAt = null;
    this.archivedBy = null;
    this.updatedAt = Instant.now();
  }

  /** Returns true if this project is read-only (ARCHIVED). Read-only projects block mutations. */
  public boolean isReadOnly() {
    return this.status == ProjectStatus.ARCHIVED;
  }

  private void requireTransition(ProjectStatus target, String action) {
    if (!this.status.canTransitionTo(target)) {
      throw new InvalidStateException(
          "Invalid project state", "Cannot " + action + " project in status " + this.status);
    }
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
