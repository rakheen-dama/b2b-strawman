package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.worktype.WorkTypes;
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
import java.time.ZoneOffset;
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

  /**
   * Optimistic-lock version (Phase 67, Epic 489B). Prevents concurrent matter closures (and any
   * other concurrent project-state transition) from both succeeding and duplicating downstream
   * effects (closure log rows, notifications, audit events). Hibernate increments this on every
   * flush; concurrent writers get {@code OptimisticLockException} which maps to HTTP 409.
   *
   * <p>Deliberately left {@code null} on new instances so Spring Data JPA's {@code isNew()} check
   * (which treats a null {@code @Version} as "new entity → persist") still fires a fresh INSERT. A
   * non-null default would flip every {@code save()} into a MERGE, breaking id generation for
   * brand-new projects. The DB column has {@code NOT NULL DEFAULT 0} so the first flush stamps 0.
   */
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

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
   * Retention clock anchor (ADR-249, ADR-354). Stamped with the close instant by {@link
   * #closeMatter(Instant, int)} on EVERY close — a re-close after a reopen is a fresh anchor. Kept
   * across {@link #reopenMatter()} for audit (the next close overwrites it). {@link
   * #complete(UUID)} never touches it: completion is not closure.
   */
  @Column(name = "retention_clock_started_at")
  private Instant retentionClockStartedAt;

  /**
   * Per-matter retention end (Phase 88 U17, ADR-354): the UTC date of the close plus the firm's
   * retention years, stamped by {@link #closeMatter(Instant, int)} on every close (a re-close
   * re-stamps it) and cleared by {@link #reopenMatter()}. Null for every matter that is not CLOSED.
   * Persisted so the obligation is frozen at close time — a later change to the firm's years does
   * not move it — and read as-is by {@code MatterResponse}, with no read-time fallback.
   */
  @Column(name = "retention_ends_on")
  private LocalDate retentionEndsOn;

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

  /** Hibernate-managed optimistic-lock version. Null before first flush; monotonic after. */
  public Long getVersion() {
    return version;
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

  public LocalDate getRetentionEndsOn() {
    return retentionEndsOn;
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

  /** Stores the canonical form ({@link WorkTypes#normalizeForStorage}); blank clears it. */
  public void setWorkType(String workType) {
    this.workType = WorkTypes.normalizeForStorage(workType);
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
    this.workType = workType != null ? WorkTypes.normalizeForStorage(workType) : this.workType;
    this.updatedAt = Instant.now();
  }

  /**
   * Marks project COMPLETED. Records completedAt and completedBy. Only valid from ACTIVE.
   * Completion does not start the retention clock — only closing the matter does (ADR-249,
   * ADR-354).
   */
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
   * Marks the project CLOSED (Phase 67, ADR-248). Valid from ACTIVE or COMPLETED. Stamps {@code
   * closedAt} and {@code retentionClockStartedAt} with {@code now} on EVERY close and persists
   * {@code retentionEndsOn} = the UTC calendar date of {@code now} + {@code retentionYears}
   * (calendar-year arithmetic, so a leap-day close lands on 28 February). A re-close after a reopen
   * resets all three — a fresh anchor (ADR-249, ADR-354). The acting member is recorded on the
   * closure log by {@code MatterClosureService}, not here.
   *
   * @throws IllegalArgumentException if {@code retentionYears} is less than 1
   */
  public void closeMatter(Instant now, int retentionYears) {
    if (retentionYears < 1) {
      throw new IllegalArgumentException(
          "retentionYears must be at least 1 (got " + retentionYears + ")");
    }
    requireTransition(ProjectStatus.CLOSED, "close");
    this.status = ProjectStatus.CLOSED;
    this.closedAt = now;
    this.retentionClockStartedAt = now;
    this.retentionEndsOn = now.atZone(ZoneOffset.UTC).toLocalDate().plusYears(retentionYears);
    this.updatedAt = Instant.now();
  }

  /**
   * Reopens a CLOSED matter back to ACTIVE (Phase 67, ADR-248). Clears {@code closedAt} and {@code
   * retentionEndsOn} — an open matter has no scheduled retention end — but keeps {@code
   * retentionClockStartedAt} for audit; the next close overwrites it (ADR-354).
   */
  public void reopenMatter() {
    requireTransition(ProjectStatus.ACTIVE, "reopen");
    this.status = ProjectStatus.ACTIVE;
    this.closedAt = null;
    this.retentionEndsOn = null;
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
