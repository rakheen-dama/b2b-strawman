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

@Entity
@Table(name = "recurring_schedules")
public class RecurringSchedule {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "template_id", nullable = false)
  private UUID templateId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "name_override", length = 300)
  private String nameOverride;

  // Valid values: WEEKLY, FORTNIGHTLY, MONTHLY, QUARTERLY, SEMI_ANNUALLY, ANNUALLY
  @Column(name = "frequency", nullable = false, length = 20)
  private String frequency;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "lead_time_days", nullable = false)
  private int leadTimeDays;

  // Valid values: ACTIVE, PAUSED, COMPLETED
  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "next_execution_date")
  private LocalDate nextExecutionDate;

  @Column(name = "last_executed_at")
  private Instant lastExecutedAt;

  @Column(name = "execution_count", nullable = false)
  private int executionCount;

  @Column(name = "project_lead_member_id")
  private UUID projectLeadMemberId;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected RecurringSchedule() {}

  public RecurringSchedule(
      UUID templateId,
      UUID customerId,
      String nameOverride,
      String frequency,
      LocalDate startDate,
      LocalDate endDate,
      int leadTimeDays,
      UUID projectLeadMemberId,
      UUID createdBy) {
    this.templateId = templateId;
    this.customerId = customerId;
    this.nameOverride = nameOverride;
    this.frequency = frequency;
    this.startDate = startDate;
    this.endDate = endDate;
    this.leadTimeDays = leadTimeDays;
    this.status = "ACTIVE";
    this.executionCount = 0;
    this.projectLeadMemberId = projectLeadMemberId;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /**
   * Records a successful schedule execution. Increments execution count and sets last executed
   * timestamp.
   */
  public void recordExecution(Instant executedAt) {
    this.executionCount++;
    this.lastExecutedAt = executedAt;
    this.updatedAt = Instant.now();
  }

  public void setNextExecutionDate(LocalDate nextExecutionDate) {
    this.nextExecutionDate = nextExecutionDate;
    this.updatedAt = Instant.now();
  }

  public void setStatus(String status) {
    this.status = status;
    this.updatedAt = Instant.now();
  }

  public void updateMutableFields(
      String nameOverride, LocalDate endDate, int leadTimeDays, UUID projectLeadMemberId) {
    this.nameOverride = nameOverride;
    this.endDate = endDate;
    this.leadTimeDays = leadTimeDays;
    this.projectLeadMemberId = projectLeadMemberId;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTemplateId() {
    return templateId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getNameOverride() {
    return nameOverride;
  }

  public String getFrequency() {
    return frequency;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public int getLeadTimeDays() {
    return leadTimeDays;
  }

  public String getStatus() {
    return status;
  }

  public LocalDate getNextExecutionDate() {
    return nextExecutionDate;
  }

  public Instant getLastExecutedAt() {
    return lastExecutedAt;
  }

  public int getExecutionCount() {
    return executionCount;
  }

  public UUID getProjectLeadMemberId() {
    return projectLeadMemberId;
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
}
