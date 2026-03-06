package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "information_requests")
public class InformationRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "request_number", nullable = false, length = 20)
  private String requestNumber;

  @Column(name = "request_template_id")
  private UUID requestTemplateId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "portal_contact_id", nullable = false)
  private UUID portalContactId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private RequestStatus status;

  @Column(name = "reminder_interval_days")
  private Integer reminderIntervalDays;

  @Column(name = "last_reminder_sent_at")
  private Instant lastReminderSentAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected InformationRequest() {}

  public InformationRequest(
      String requestNumber, UUID customerId, UUID portalContactId, UUID createdBy) {
    this.requestNumber = Objects.requireNonNull(requestNumber, "requestNumber must not be null");
    this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
    this.portalContactId =
        Objects.requireNonNull(portalContactId, "portalContactId must not be null");
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
    this.status = RequestStatus.DRAFT;
  }

  @PrePersist
  void onPrePersist() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onPreUpdate() {
    this.updatedAt = Instant.now();
  }

  // ── Lifecycle transitions ──────────────────────────────────────────

  /** DRAFT -> SENT. Sets sentAt. */
  public void send() {
    requireStatus(Set.of(RequestStatus.DRAFT), "send");
    this.status = RequestStatus.SENT;
    this.sentAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** SENT -> IN_PROGRESS. Idempotent — no-op if already IN_PROGRESS. */
  public void markInProgress() {
    if (this.status == RequestStatus.IN_PROGRESS) {
      return;
    }
    requireStatus(Set.of(RequestStatus.SENT), "mark as in progress");
    this.status = RequestStatus.IN_PROGRESS;
    this.updatedAt = Instant.now();
  }

  /** Sets COMPLETED + completedAt. */
  public void complete() {
    requireStatus(Set.of(RequestStatus.SENT, RequestStatus.IN_PROGRESS), "complete");
    this.status = RequestStatus.COMPLETED;
    this.completedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Any state except COMPLETED -> CANCELLED. Sets cancelledAt. */
  public void cancel() {
    if (this.status == RequestStatus.COMPLETED) {
      throw new InvalidStateException(
          "Invalid request state", "Cannot cancel request in status " + this.status);
    }
    this.status = RequestStatus.CANCELLED;
    this.cancelledAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public boolean isEditable() {
    return this.status == RequestStatus.DRAFT;
  }

  public void requireEditable() {
    if (!isEditable()) {
      throw new InvalidStateException(
          "Invalid request state", "Cannot edit request in status " + this.status);
    }
  }

  // ── Getters ────────────────────────────────────────────────────────

  public UUID getId() {
    return id;
  }

  public String getRequestNumber() {
    return requestNumber;
  }

  public UUID getRequestTemplateId() {
    return requestTemplateId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getPortalContactId() {
    return portalContactId;
  }

  public RequestStatus getStatus() {
    return status;
  }

  public Integer getReminderIntervalDays() {
    return reminderIntervalDays;
  }

  public Instant getLastReminderSentAt() {
    return lastReminderSentAt;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
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

  // ── Setters for optional fields ────────────────────────────────────

  public void setRequestTemplateId(UUID requestTemplateId) {
    this.requestTemplateId = requestTemplateId;
  }

  public void setProjectId(UUID projectId) {
    this.projectId = projectId;
  }

  public void setReminderIntervalDays(Integer reminderIntervalDays) {
    this.reminderIntervalDays = reminderIntervalDays;
  }

  public void setLastReminderSentAt(Instant lastReminderSentAt) {
    this.lastReminderSentAt = lastReminderSentAt;
  }

  // ── Private helpers ────────────────────────────────────────────────

  private void requireStatus(Set<RequestStatus> allowedStatuses, String action) {
    if (!allowedStatuses.contains(this.status)) {
      throw new InvalidStateException(
          "Invalid request state", "Cannot " + action + " request in status " + this.status);
    }
  }
}
