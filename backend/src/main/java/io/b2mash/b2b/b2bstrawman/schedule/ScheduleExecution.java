package io.b2mash.b2b.b2bstrawman.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Execution history for a recurring schedule. Each row represents one project created by the
 * scheduler. Immutable after creation — no update methods.
 *
 * <p>The (schedule_id, period_start) unique constraint on the table is the idempotency key.
 */
@Entity
@Table(name = "schedule_executions")
public class ScheduleExecution {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "schedule_id", nullable = false)
  private UUID scheduleId;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "executed_at", nullable = false)
  private Instant executedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ScheduleExecution() {}

  public ScheduleExecution(
      UUID scheduleId,
      UUID projectId,
      LocalDate periodStart,
      LocalDate periodEnd,
      Instant executedAt) {
    this.scheduleId = scheduleId;
    this.projectId = projectId;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.executedAt = executedAt;
    this.createdAt = Instant.now();
  }

  // Only getters — no setters, no update methods (immutable log entry)
  public UUID getId() {
    return id;
  }

  public UUID getScheduleId() {
    return scheduleId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public LocalDate getPeriodStart() {
    return periodStart;
  }

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
