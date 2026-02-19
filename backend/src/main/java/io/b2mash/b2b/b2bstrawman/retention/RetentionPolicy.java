package io.b2mash.b2b.b2bstrawman.retention;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "retention_policies")
public class RetentionPolicy {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "record_type", nullable = false, length = 20)
  private String recordType;

  @Column(name = "retention_days", nullable = false)
  private int retentionDays;

  @Column(name = "trigger_event", nullable = false, length = 30)
  private String triggerEvent;

  @Column(name = "action", nullable = false, length = 20)
  private String action;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected RetentionPolicy() {}

  public RetentionPolicy(String recordType, int retentionDays, String triggerEvent, String action) {
    this.recordType = recordType;
    this.retentionDays = retentionDays;
    this.triggerEvent = triggerEvent;
    this.action = action;
    this.active = true;
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void update(int retentionDays, String action) {
    this.retentionDays = retentionDays;
    this.action = action;
    this.updatedAt = Instant.now();
  }

  public void deactivate() {
    this.active = false;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getRecordType() {
    return recordType;
  }

  public int getRetentionDays() {
    return retentionDays;
  }

  public String getTriggerEvent() {
    return triggerEvent;
  }

  public String getAction() {
    return action;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
