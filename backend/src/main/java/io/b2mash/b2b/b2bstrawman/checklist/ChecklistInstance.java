package io.b2mash.b2b.b2bstrawman.checklist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checklist_instances")
public class ChecklistInstance {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "template_id", nullable = false)
  private UUID templateId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "completed_by")
  private UUID completedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ChecklistInstance() {}

  public ChecklistInstance(UUID templateId, UUID customerId, Instant startedAt) {
    this.templateId = templateId;
    this.customerId = customerId;
    this.startedAt = startedAt;
    this.status = "IN_PROGRESS";
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void complete(UUID actorId) {
    this.status = "COMPLETED";
    this.completedAt = Instant.now();
    this.completedBy = actorId;
    this.updatedAt = Instant.now();
  }

  public void cancel() {
    this.status = "CANCELLED";
    this.updatedAt = Instant.now();
  }

  public void revertToInProgress() {
    if (!"COMPLETED".equals(this.status)) {
      throw new IllegalStateException("Cannot revert to IN_PROGRESS from " + status);
    }
    this.status = "IN_PROGRESS";
    this.completedAt = null;
    this.completedBy = null;
    this.updatedAt = Instant.now();
  }

  // Getters
  public UUID getId() {
    return id;
  }

  public UUID getTemplateId() {
    return templateId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getStatus() {
    return status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public UUID getCompletedBy() {
    return completedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
