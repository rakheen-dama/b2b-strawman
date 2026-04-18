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

  /**
   * Timestamp of the matter's CLOSED transition (Phase 67, ADR-248). Nullable — only set while the
   * project is in status CLOSED. Cleared by {@link #reopenMatter()}.
   */
  @Column(name = "closed_at")
  private Instant closedAt;

  /**
   * Retention clock anchor (ADR-249 minimal slice). Stamped exactly once on the first transition to
   * COMPLETED and never overwritten — re-completing a reopened matter preserves the earliest anchor
   * so the retention sweep evaluates against the original trigger. When Phase 67 (GAP-L-07)
   * introduces a distinct CLOSED state, the stamp moves from {@code complete(...)} to {@code
   * close(...)}; this column does not change.
   */
  @Column(name = "retention_clock_started_at")
  private Instant retentionClockStartedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "custom_fields", columnDefinition = "jsonb")
  private Map<String, Object> customFields = new HashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "applied_field_groups", columnDefinition = "jsonb")
  private List<UUID> appliedFieldGroups;

  @Column(name = "reference_number", length = 100)
  private String referenceNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "priority", length = 20)
  private ProjectPriority priority;

  @Column(name = "work_type", length = 50)
  private String workType;

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

  public Instant getClosedAt() {
    return closedAt;
  }

  public Instant getRetentionClockStartedAt() {
    return retentionClockStartedAt;
  }

  public String getReferenceNumber() {
    return referenceNumber;
  }

  public void setReferenceNumber(String referenceNumber) {
    this.referenceNumber = referenceNumber;
    this.updatedAt = Instant.now();
  }

  public ProjectPriority getPriority() {
    return priority;
  }

  public void setPriority(ProjectPriority priority) {
    this.priority = priority;
    this.updatedAt = Instant.now();
  }

  public String getWorkType() {
    return workType;
  }

  public void setWorkType(String workType) {
    this.workType = workType;
    this.updatedAt = Instant.now();
  }

  public void update(
      String name,
      String description,
      UUID customerId,
      LocalDate dueDate,
      String referenceNumber,
      ProjectPriority priority,
      String workType) {
    this.name = name;
    this.description = description;
    this.customerId = customerId != null ? customerId : this.customerId;
    this.dueDate = dueDate != null ? dueDate : this.dueDate;
    this.referenceNumber = referenceNumber != null ? referenceNumber : this.referenceNumber;
    this.priority = priority != null ? priority : this.priority;
    this.workType = workType != null ? workType : this.workType;
    this.updatedAt = Instant.now();
  }

  /**
   * Marks project COMPLETED. Records completedAt, completedBy, and (on first transition only)
   * retentionClockStartedAt. Only valid from ACTIVE. Re-completing a reopened project preserves the
   * earliest retention anchor — ADR-249 (minimal slice; final home is close() in Phase 67).
   */
  public void complete(UUID memberId) {
    requireTransition(ProjectStatus.COMPLETED, "complete");
    this.status = ProjectStatus.COMPLETED;
    this.completedAt = Instant.now();
    this.completedBy = memberId;
    // Retention clock starts on the FIRST transition to COMPLETED and is never re-stamped
    // by subsequent re-completions. When Phase 67 introduces CLOSED, move this assignment to
    // close(...) — see ADR-249.
    if (this.retentionClockStartedAt == null) {
      this.retentionClockStartedAt = this.completedAt;
    }
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
    if (this.status == ProjectStatus.CLOSED) {
      reopenMatter();
      return;
    }
    requireTransition(ProjectStatus.ACTIVE, "reopen");
    this.status = ProjectStatus.ACTIVE;
    this.completedAt = null;
    this.completedBy = null;
    this.archivedAt = null;
    this.archivedBy = null;
    this.updatedAt = Instant.now();
  }

  /**
   * Marks the project CLOSED (Phase 67, ADR-248). Valid from ACTIVE or COMPLETED. Records {@code
   * closedAt} and, on the first transition only, stamps {@code retentionClockStartedAt} (ADR-249 —
   * the canonical anchor now lives here rather than in {@link #complete(UUID)}, per the field's
   * javadoc).
   *
   * <p>489A-minimal signature: takes only the acting member id. The full {@code closeMatter(UUID,
   * ClosureRequest)} service hook lands in 489B.
   */
  public void closeMatter(UUID memberId) {
    requireTransition(ProjectStatus.CLOSED, "close");
    this.status = ProjectStatus.CLOSED;
    this.closedAt = Instant.now();
    if (this.retentionClockStartedAt == null) {
      this.retentionClockStartedAt = this.closedAt;
    }
    this.updatedAt = Instant.now();
  }

  /**
   * Reopens a CLOSED matter back to ACTIVE (Phase 67, ADR-248). Clears {@code closedAt} but
   * deliberately preserves {@code retentionClockStartedAt} — the canonical anchor is the earliest
   * closure event, and the soft-cancel of retention lives on the RetentionPolicy row (ADR-249;
   * wired in 489B).
   */
  public void reopenMatter() {
    requireTransition(ProjectStatus.ACTIVE, "reopen");
    this.status = ProjectStatus.ACTIVE;
    this.closedAt = null;
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
