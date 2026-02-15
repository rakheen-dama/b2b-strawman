package io.b2mash.b2b.b2bstrawman.customer;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ParamDef;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "customers")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class Customer implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "phone", length = 50)
  private String phone;

  @Column(name = "id_number", length = 100)
  private String idNumber;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "custom_fields", columnDefinition = "jsonb")
  private Map<String, Object> customFields = new HashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "applied_field_groups", columnDefinition = "jsonb")
  private List<UUID> appliedFieldGroups;

  @Column(name = "lifecycle_status", nullable = false, length = 20)
  private String lifecycleStatus = "PROSPECT";

  @Column(name = "lifecycle_status_changed_at")
  private Instant lifecycleStatusChangedAt;

  @Column(name = "lifecycle_status_changed_by")
  private UUID lifecycleStatusChangedBy;

  @Column(name = "offboarded_at")
  private Instant offboardedAt;

  protected Customer() {}

  public Customer(
      String name, String email, String phone, String idNumber, String notes, UUID createdBy) {
    this.name = name;
    this.email = email;
    this.phone = phone;
    this.idNumber = idNumber;
    this.status = "ACTIVE";
    this.notes = notes;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void update(String name, String email, String phone, String idNumber, String notes) {
    this.name = name;
    this.email = email;
    this.phone = phone;
    this.idNumber = idNumber;
    this.notes = notes;
    this.updatedAt = Instant.now();
  }

  public void archive() {
    this.status = "ARCHIVED";
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getIdNumber() {
    return idNumber;
  }

  public String getStatus() {
    return status;
  }

  public String getNotes() {
    return notes;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Map<String, Object> getCustomFields() {
    return customFields;
  }

  public void setCustomFields(Map<String, Object> customFields) {
    this.customFields = customFields;
    this.updatedAt = Instant.now();
  }

  public List<UUID> getAppliedFieldGroups() {
    return appliedFieldGroups;
  }

  public void setAppliedFieldGroups(List<UUID> appliedFieldGroups) {
    this.appliedFieldGroups = appliedFieldGroups;
    this.updatedAt = Instant.now();
  }

  public String getLifecycleStatus() {
    return lifecycleStatus;
  }

  public Instant getLifecycleStatusChangedAt() {
    return lifecycleStatusChangedAt;
  }

  public UUID getLifecycleStatusChangedBy() {
    return lifecycleStatusChangedBy;
  }

  public Instant getOffboardedAt() {
    return offboardedAt;
  }

  /**
   * Transitions the customer to a new lifecycle status, updating all lifecycle fields atomically.
   */
  public void transitionLifecycle(
      String newStatus, UUID changedBy, Instant changedAt, Instant offboardedAt) {
    this.lifecycleStatus = newStatus;
    this.lifecycleStatusChangedAt = changedAt;
    this.lifecycleStatusChangedBy = changedBy;
    this.offboardedAt = offboardedAt;
    this.updatedAt = Instant.now();
  }
}
