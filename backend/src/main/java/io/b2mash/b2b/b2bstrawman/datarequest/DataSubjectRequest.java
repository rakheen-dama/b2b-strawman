package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
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
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "data_subject_requests")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class DataSubjectRequest implements TenantAware {

  public static final Set<String> VALID_REQUEST_TYPES =
      Set.of("ACCESS", "DELETION", "CORRECTION", "OBJECTION");

  public static final Set<String> VALID_STATUSES =
      Set.of("RECEIVED", "IN_PROGRESS", "COMPLETED", "REJECTED");

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id")
  private String tenantId;

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

  @Column(name = "export_file_key", length = 500)
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
      LocalDate deadline,
      UUID requestedBy) {
    this.customerId = customerId;
    this.requestType = requestType;
    this.description = description;
    this.deadline = deadline;
    this.requestedBy = requestedBy;
    this.status = "RECEIVED";
    this.requestedAt = Instant.now();
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /**
   * Transitions this request to a new status. Valid transitions: RECEIVED -> IN_PROGRESS,
   * IN_PROGRESS -> COMPLETED, IN_PROGRESS -> REJECTED.
   */
  public void transitionTo(String newStatus) {
    if (!VALID_STATUSES.contains(newStatus)) {
      throw new ResourceConflictException(
          "Invalid status", "Status '" + newStatus + "' is not valid");
    }
    this.status = newStatus;
    this.updatedAt = Instant.now();
  }

  /** Marks this request as completed. */
  public void complete(UUID completedBy, Instant completedAt) {
    this.status = "COMPLETED";
    this.completedBy = completedBy;
    this.completedAt = completedAt;
    this.updatedAt = Instant.now();
  }

  /** Rejects this request with a reason. */
  public void reject(String reason) {
    this.status = "REJECTED";
    this.rejectionReason = reason;
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

  // --- Setters for mutable fields ---

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public void setExportFileKey(String exportFileKey) {
    this.exportFileKey = exportFileKey;
  }
}
