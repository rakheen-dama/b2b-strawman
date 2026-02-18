package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
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
@Table(name = "data_subject_requests")
public class DataSubjectRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "request_type", nullable = false, length = 20)
  private String requestType;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "rejection_reason", columnDefinition = "TEXT")
  private String rejectionReason;

  @Column(name = "deadline", nullable = false)
  private LocalDate deadline;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(name = "requested_by", nullable = false)
  private UUID requestedBy;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "completed_by")
  private UUID completedBy;

  @Column(name = "export_file_key", length = 1000)
  private String exportFileKey;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected DataSubjectRequest() {}

  public DataSubjectRequest(
      UUID customerId,
      String requestType,
      String description,
      UUID requestedBy,
      LocalDate deadline) {
    this.customerId = customerId;
    this.requestType = requestType;
    this.description = description;
    this.requestedBy = requestedBy;
    this.deadline = deadline;
    this.status = "RECEIVED";
    Instant now = Instant.now();
    this.requestedAt = now;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void startProcessing(UUID actorId) {
    if (!"RECEIVED".equals(this.status)) {
      throw new InvalidStateException(
          "Invalid status transition",
          "Cannot start processing — request is in status " + this.status + ", expected RECEIVED");
    }
    this.status = "IN_PROGRESS";
    this.updatedAt = Instant.now();
  }

  public void complete(UUID actorId) {
    if (!"IN_PROGRESS".equals(this.status)) {
      throw new InvalidStateException(
          "Invalid status transition",
          "Cannot complete — request is in status " + this.status + ", expected IN_PROGRESS");
    }
    this.status = "COMPLETED";
    this.completedAt = Instant.now();
    this.completedBy = actorId;
    this.updatedAt = Instant.now();
  }

  public void reject(String reason, UUID actorId) {
    if (!"IN_PROGRESS".equals(this.status)) {
      throw new InvalidStateException(
          "Invalid status transition",
          "Cannot reject — request is in status " + this.status + ", expected IN_PROGRESS");
    }
    this.status = "REJECTED";
    this.rejectionReason = reason;
    this.completedAt = Instant.now();
    this.completedBy = actorId;
    this.updatedAt = Instant.now();
  }

  // Getters
  public UUID getId() {
    return id;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getRequestType() {
    return requestType;
  }

  public String getStatus() {
    return status;
  }

  public String getDescription() {
    return description;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public LocalDate getDeadline() {
    return deadline;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  public UUID getRequestedBy() {
    return requestedBy;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public UUID getCompletedBy() {
    return completedBy;
  }

  public String getExportFileKey() {
    return exportFileKey;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setExportFileKey(String exportFileKey) {
    this.exportFileKey = exportFileKey;
    this.updatedAt = Instant.now();
  }

  public void setNotes(String notes) {
    this.notes = notes;
    this.updatedAt = Instant.now();
  }
}
