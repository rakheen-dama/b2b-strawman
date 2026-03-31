package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "court_dates")
public class CourtDate {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "date_type", nullable = false, length = 30)
  private String dateType;

  @Column(name = "scheduled_date", nullable = false)
  private LocalDate scheduledDate;

  @Column(name = "scheduled_time")
  private LocalTime scheduledTime;

  @Column(name = "court_name", nullable = false, length = 200)
  private String courtName;

  @Column(name = "court_reference", length = 100)
  private String courtReference;

  @Column(name = "judge_magistrate", length = 200)
  private String judgeMagistrate;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "outcome", columnDefinition = "TEXT")
  private String outcome;

  @Column(name = "reminder_days", nullable = false)
  private int reminderDays;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CourtDate() {}

  public CourtDate(
      UUID projectId,
      UUID customerId,
      String dateType,
      LocalDate scheduledDate,
      LocalTime scheduledTime,
      String courtName,
      String courtReference,
      String judgeMagistrate,
      String description,
      int reminderDays,
      UUID createdBy) {
    this.projectId = projectId;
    this.customerId = customerId;
    this.dateType = dateType;
    this.scheduledDate = scheduledDate;
    this.scheduledTime = scheduledTime;
    this.courtName = courtName;
    this.courtReference = courtReference;
    this.judgeMagistrate = judgeMagistrate;
    this.description = description;
    this.status = "SCHEDULED";
    this.reminderDays = reminderDays;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getDateType() {
    return dateType;
  }

  public void setDateType(String dateType) {
    this.dateType = dateType;
  }

  public LocalDate getScheduledDate() {
    return scheduledDate;
  }

  public void setScheduledDate(LocalDate scheduledDate) {
    this.scheduledDate = scheduledDate;
  }

  public LocalTime getScheduledTime() {
    return scheduledTime;
  }

  public void setScheduledTime(LocalTime scheduledTime) {
    this.scheduledTime = scheduledTime;
  }

  public String getCourtName() {
    return courtName;
  }

  public void setCourtName(String courtName) {
    this.courtName = courtName;
  }

  public String getCourtReference() {
    return courtReference;
  }

  public void setCourtReference(String courtReference) {
    this.courtReference = courtReference;
  }

  public String getJudgeMagistrate() {
    return judgeMagistrate;
  }

  public void setJudgeMagistrate(String judgeMagistrate) {
    this.judgeMagistrate = judgeMagistrate;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String outcome) {
    this.outcome = outcome;
  }

  public int getReminderDays() {
    return reminderDays;
  }

  public void setReminderDays(int reminderDays) {
    this.reminderDays = reminderDays;
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

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
