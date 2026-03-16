package io.b2mash.b2b.b2bstrawman.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Deduplication log for field-date approaching notifications. Each row records that a specific
 * (entity, field, threshold) combination has already been notified, preventing the field date
 * scanner from firing duplicate alerts.
 */
@Entity
@Table(name = "field_date_notification_log")
public class FieldDateNotificationLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "entity_type", nullable = false, length = 20)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "field_name", nullable = false, length = 100)
  private String fieldName;

  @Column(name = "days_until", nullable = false)
  private int daysUntil;

  @Column(name = "fired_at", nullable = false)
  private Instant firedAt;

  protected FieldDateNotificationLog() {}

  public FieldDateNotificationLog(
      String entityType, UUID entityId, String fieldName, int daysUntil) {
    this.entityType = entityType;
    this.entityId = entityId;
    this.fieldName = fieldName;
    this.daysUntil = daysUntil;
    this.firedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public String getFieldName() {
    return fieldName;
  }

  public int getDaysUntil() {
    return daysUntil;
  }

  public Instant getFiredAt() {
    return firedAt;
  }
}
