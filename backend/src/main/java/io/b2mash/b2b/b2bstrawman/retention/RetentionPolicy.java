package io.b2mash.b2b.b2bstrawman.retention;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAware;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAwareEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "retention_policies")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class RetentionPolicy implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "record_type", nullable = false, length = 30)
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
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Activates this retention policy. */
  public void activate() {
    this.active = true;
    this.updatedAt = Instant.now();
  }

  /** Deactivates this retention policy. */
  public void deactivate() {
    this.active = false;
    this.updatedAt = Instant.now();
  }

  // --- TenantAware ---

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  // --- Getters ---

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

  // --- Setters for mutable fields ---

  public void setRetentionDays(int retentionDays) {
    this.retentionDays = retentionDays;
    this.updatedAt = Instant.now();
  }

  public void setAction(String action) {
    this.action = action;
    this.updatedAt = Instant.now();
  }
}
