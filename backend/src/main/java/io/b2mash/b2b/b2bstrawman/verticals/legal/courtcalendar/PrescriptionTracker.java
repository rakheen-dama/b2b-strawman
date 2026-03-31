package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

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
@Table(name = "prescription_trackers")
public class PrescriptionTracker {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "cause_of_action_date", nullable = false)
  private LocalDate causeOfActionDate;

  @Column(name = "prescription_type", nullable = false, length = 30)
  private String prescriptionType;

  @Column(name = "custom_years")
  private Integer customYears;

  @Column(name = "prescription_date", nullable = false)
  private LocalDate prescriptionDate;

  @Column(name = "interruption_date")
  private LocalDate interruptionDate;

  @Column(name = "interruption_reason", length = 200)
  private String interruptionReason;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PrescriptionTracker() {}

  public PrescriptionTracker(
      UUID projectId,
      UUID customerId,
      LocalDate causeOfActionDate,
      String prescriptionType,
      Integer customYears,
      LocalDate prescriptionDate,
      String notes,
      UUID createdBy) {
    this.projectId = projectId;
    this.customerId = customerId;
    this.causeOfActionDate = causeOfActionDate;
    this.prescriptionType = prescriptionType;
    this.customYears = customYears;
    this.prescriptionDate = prescriptionDate;
    this.notes = notes;
    this.status = "RUNNING";
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

  public LocalDate getCauseOfActionDate() {
    return causeOfActionDate;
  }

  public void setCauseOfActionDate(LocalDate causeOfActionDate) {
    this.causeOfActionDate = causeOfActionDate;
  }

  public String getPrescriptionType() {
    return prescriptionType;
  }

  public void setPrescriptionType(String prescriptionType) {
    this.prescriptionType = prescriptionType;
  }

  public Integer getCustomYears() {
    return customYears;
  }

  public void setCustomYears(Integer customYears) {
    this.customYears = customYears;
  }

  public LocalDate getPrescriptionDate() {
    return prescriptionDate;
  }

  public void setPrescriptionDate(LocalDate prescriptionDate) {
    this.prescriptionDate = prescriptionDate;
  }

  public LocalDate getInterruptionDate() {
    return interruptionDate;
  }

  public void setInterruptionDate(LocalDate interruptionDate) {
    this.interruptionDate = interruptionDate;
  }

  public String getInterruptionReason() {
    return interruptionReason;
  }

  public void setInterruptionReason(String interruptionReason) {
    this.interruptionReason = interruptionReason;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
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
