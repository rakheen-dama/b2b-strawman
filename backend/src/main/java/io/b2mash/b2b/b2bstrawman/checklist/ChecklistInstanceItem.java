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
@Table(name = "checklist_instance_items")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class ChecklistInstanceItem implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "instance_id", nullable = false)
  private UUID instanceId;

  @Column(name = "template_item_id", nullable = false)
  private UUID templateItemId;

  @Column(name = "name", nullable = false, length = 300)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "required", nullable = false)
  private boolean required;

  @Column(name = "requires_document", nullable = false)
  private boolean requiresDocument;

  @Column(name = "required_document_label", length = 200)
  private String requiredDocumentLabel;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "completed_by")
  private UUID completedBy;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "document_id")
  private UUID documentId;

  @Column(name = "depends_on_item_id")
  private UUID dependsOnItemId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ChecklistInstanceItem() {}

  public ChecklistInstanceItem(
      UUID instanceId, UUID templateItemId, String name, int sortOrder, boolean required) {
    this.instanceId = instanceId;
    this.templateItemId = templateItemId;
    this.name = name;
    this.sortOrder = sortOrder;
    this.required = required;
    this.requiresDocument = false;
    this.status = "PENDING";
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Marks this item as completed with optional notes and document reference. */
  public void complete(UUID completedBy, Instant completedAt, String notes, UUID documentId) {
    this.status = "COMPLETED";
    this.completedBy = completedBy;
    this.completedAt = completedAt;
    this.notes = notes;
    this.documentId = documentId;
    this.updatedAt = Instant.now();
  }

  /** Skips this item with a reason. Only non-required items should be skipped. */
  public void skip(String reason) {
    this.status = "SKIPPED";
    this.notes = reason;
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

  public UUID getInstanceId() {
    return instanceId;
  }

  public UUID getTemplateItemId() {
    return templateItemId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isRequired() {
    return required;
  }

  public boolean isRequiresDocument() {
    return requiresDocument;
  }

  public String getRequiredDocumentLabel() {
    return requiredDocumentLabel;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public UUID getCompletedBy() {
    return completedBy;
  }

  public String getNotes() {
    return notes;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public UUID getDependsOnItemId() {
    return dependsOnItemId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // --- Setters for mutable fields ---

  public void setDescription(String description) {
    this.description = description;
  }

  public void setRequiresDocument(boolean requiresDocument) {
    this.requiresDocument = requiresDocument;
  }

  public void setRequiredDocumentLabel(String requiredDocumentLabel) {
    this.requiredDocumentLabel = requiredDocumentLabel;
  }

  public void setDependsOnItemId(UUID dependsOnItemId) {
    this.dependsOnItemId = dependsOnItemId;
  }
}
