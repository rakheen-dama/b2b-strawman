package io.b2mash.b2b.b2bstrawman.checklist;

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
@Table(name = "checklist_instances")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class ChecklistInstance implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id")
  private String tenantId;

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

  public ChecklistInstance(UUID templateId, UUID customerId, String status) {
    this.templateId = templateId;
    this.customerId = customerId;
    this.status = status;
    this.startedAt = Instant.now();
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Marks this checklist instance as completed. */
  public void complete(UUID completedBy, Instant completedAt) {
    this.status = "COMPLETED";
    this.completedBy = completedBy;
    this.completedAt = completedAt;
    this.updatedAt = Instant.now();
  }

  /** Reopens a completed instance back to IN_PROGRESS. */
  public void reopen() {
    this.status = "IN_PROGRESS";
    this.completedAt = null;
    this.completedBy = null;
    this.updatedAt = Instant.now();
  }

  /** Cancels this checklist instance. */
  public void cancel() {
    this.status = "CANCELLED";
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
