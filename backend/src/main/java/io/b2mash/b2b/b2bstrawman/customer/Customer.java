package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "customers")
public class Customer {

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

  @Enumerated(EnumType.STRING)
  @Column(name = "customer_type", nullable = false, length = 20)
  private CustomerType customerType;

  @Enumerated(EnumType.STRING)
  @Column(name = "lifecycle_status", nullable = false, length = 20)
  private LifecycleStatus lifecycleStatus;

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
    this.customerType = CustomerType.INDIVIDUAL;
    this.lifecycleStatus = LifecycleStatus.PROSPECT;
  }

  public Customer(
      String name,
      String email,
      String phone,
      String idNumber,
      String notes,
      UUID createdBy,
      CustomerType customerType) {
    this(name, email, phone, idNumber, notes, createdBy);
    this.customerType = customerType != null ? customerType : CustomerType.INDIVIDUAL;
  }

  public Customer(
      String name,
      String email,
      String phone,
      String idNumber,
      String notes,
      UUID createdBy,
      CustomerType customerType,
      LifecycleStatus lifecycleStatus) {
    this(name, email, phone, idNumber, notes, createdBy, customerType);
    this.lifecycleStatus = lifecycleStatus != null ? lifecycleStatus : LifecycleStatus.PROSPECT;
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

  public void unarchive() {
    this.status = "ACTIVE";
    this.updatedAt = Instant.now();
  }

  /**
   * Transitions the lifecycle status, recording the actor and timestamp.
   *
   * @throws InvalidStateException if the transition is not allowed
   */
  public void transitionLifecycleStatus(LifecycleStatus targetStatus, UUID actorId) {
    if (!this.lifecycleStatus.canTransitionTo(targetStatus)) {
      throw new InvalidStateException(
          "Invalid lifecycle transition",
          "Cannot transition from " + this.lifecycleStatus + " to " + targetStatus);
    }
    this.lifecycleStatus = targetStatus;
    this.lifecycleStatusChangedAt = Instant.now();
    this.lifecycleStatusChangedBy = actorId;
    if (targetStatus == LifecycleStatus.OFFBOARDED) {
      this.offboardedAt = Instant.now();
    }
    this.updatedAt = Instant.now();
  }

  /** Replaces PII fields with anonymized values. */
  public void anonymize(String replacementName) {
    this.name = replacementName;
    this.email = "anon-" + this.id + "@anonymized.invalid";
    this.phone = null;
    this.idNumber = null;
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

  public CustomerType getCustomerType() {
    return customerType;
  }

  public LifecycleStatus getLifecycleStatus() {
    return lifecycleStatus;
  }

  /**
   * Sets lifecycle status directly, bypassing transition validation. Used by system operations like
   * archive/unarchive alignment and auto-dormancy.
   *
   * @param lifecycleStatus the target status
   * @param changedBy the actor UUID, or null for system-initiated changes
   */
  public void setLifecycleStatus(LifecycleStatus lifecycleStatus, UUID changedBy) {
    this.lifecycleStatus = lifecycleStatus;
    this.lifecycleStatusChangedAt = Instant.now();
    this.lifecycleStatusChangedBy = changedBy;
    this.updatedAt = Instant.now();
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

  public void setOffboardedAt(Instant offboardedAt) {
    this.offboardedAt = offboardedAt;
    this.updatedAt = Instant.now();
  }
}
