package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

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
import java.util.UUID;

@Entity
@Table(name = "accounting_sync_entry")
public class AccountingSyncEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 20)
  private SyncEntityType entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "provider_id", nullable = false, length = 20)
  private String providerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 10)
  private SyncDirection direction;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false, length = 30)
  private SyncState state;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "last_error_code", length = 50)
  private String lastErrorCode;

  @Column(name = "last_error_detail", columnDefinition = "TEXT")
  private String lastErrorDetail;

  @Column(name = "external_reference", length = 100)
  private String externalReference;

  @Column(name = "external_id", length = 100)
  private String externalId;

  @Enumerated(EnumType.STRING)
  @Column(name = "\"trigger\"", nullable = false, length = 30)
  private SyncTrigger trigger;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected AccountingSyncEntry() {}

  public AccountingSyncEntry(
      SyncEntityType entityType,
      UUID entityId,
      String providerId,
      SyncDirection direction,
      SyncTrigger trigger,
      String externalReference) {
    this.entityType = entityType;
    this.entityId = entityId;
    this.providerId = providerId;
    this.direction = direction;
    this.state = SyncState.PENDING;
    this.attemptCount = 0;
    this.nextAttemptAt = Instant.now();
    this.trigger = trigger;
    this.externalReference = externalReference;
  }

  @PrePersist
  void onPrePersist() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  @PreUpdate
  void onPreUpdate() {
    this.updatedAt = Instant.now();
  }

  /** Transition to IN_FLIGHT for worker processing. */
  public void markInFlight() {
    this.state = SyncState.IN_FLIGHT;
    this.nextAttemptAt = null;
  }

  /** Mark as successfully completed. */
  public void markCompleted(String externalId) {
    this.state = SyncState.COMPLETED;
    this.externalId = externalId;
    this.completedAt = Instant.now();
    this.nextAttemptAt = null;
    this.lastErrorCode = null;
    this.lastErrorDetail = null;
  }

  /** Mark as failed with retry scheduled. */
  public void markFailedRetrying(String errorCode, String errorDetail, Instant nextAttempt) {
    this.state = SyncState.FAILED_RETRYING;
    this.attemptCount++;
    this.lastErrorCode = errorCode;
    this.lastErrorDetail = errorDetail;
    this.nextAttemptAt = nextAttempt;
  }

  /** Move to dead-letter (no further automatic retry). */
  public void markDeadLetter(String errorCode, String errorDetail) {
    this.state = SyncState.DEAD_LETTER;
    this.lastErrorCode = errorCode;
    this.lastErrorDetail = errorDetail;
    this.nextAttemptAt = null;
  }

  /** Mark as blocked by trust boundary guard. Permanent, no retry. */
  public void markBlockedTrustBoundary(String reason) {
    this.state = SyncState.BLOCKED_TRUST_BOUNDARY;
    this.lastErrorCode = "TRUST_BOUNDARY";
    this.lastErrorDetail = reason;
    this.nextAttemptAt = null;
  }

  /** Reset for manual retry from dead-letter. */
  public void resetForRetry() {
    if (this.state != SyncState.DEAD_LETTER) {
      throw new IllegalStateException("resetForRetry is only valid from DEAD_LETTER");
    }
    this.state = SyncState.PENDING;
    this.attemptCount = 0;
    this.nextAttemptAt = Instant.now();
    this.trigger = SyncTrigger.MANUAL_RETRY;
    this.lastErrorCode = null;
    this.lastErrorDetail = null;
    this.externalId = null;
    this.completedAt = null;
  }

  public UUID getId() {
    return id;
  }

  public SyncEntityType getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public String getProviderId() {
    return providerId;
  }

  public SyncDirection getDirection() {
    return direction;
  }

  public SyncState getState() {
    return state;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public String getLastErrorCode() {
    return lastErrorCode;
  }

  public String getLastErrorDetail() {
    return lastErrorDetail;
  }

  public String getExternalReference() {
    return externalReference;
  }

  public String getExternalId() {
    return externalId;
  }

  public SyncTrigger getTrigger() {
    return trigger;
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
}
