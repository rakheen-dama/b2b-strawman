package io.b2mash.b2b.b2bstrawman.deadline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "filing_statuses")
public class FilingStatus {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "deadline_type_slug", nullable = false, length = 50)
  private String deadlineTypeSlug;

  @Column(name = "period_key", nullable = false, length = 20)
  private String periodKey;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "filed_at")
  private Instant filedAt;

  @Column(name = "filed_by")
  private UUID filedBy;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "linked_project_id")
  private UUID linkedProjectId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected FilingStatus() {}

  public FilingStatus(UUID customerId, String deadlineTypeSlug, String periodKey, String status) {
    this.customerId = customerId;
    this.deadlineTypeSlug = deadlineTypeSlug;
    this.periodKey = periodKey;
    this.status = status;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getDeadlineTypeSlug() {
    return deadlineTypeSlug;
  }

  public String getPeriodKey() {
    return periodKey;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getFiledAt() {
    return filedAt;
  }

  public void setFiledAt(Instant filedAt) {
    this.filedAt = filedAt;
  }

  public UUID getFiledBy() {
    return filedBy;
  }

  public void setFiledBy(UUID filedBy) {
    this.filedBy = filedBy;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public UUID getLinkedProjectId() {
    return linkedProjectId;
  }

  public void setLinkedProjectId(UUID linkedProjectId) {
    this.linkedProjectId = linkedProjectId;
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
