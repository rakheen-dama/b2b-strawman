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
@Table(name = "checklist_instance_items")
public class ChecklistInstanceItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

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
      UUID instanceId,
      UUID templateItemId,
      String name,
      String description,
      int sortOrder,
      boolean required,
      boolean requiresDocument,
      String requiredDocumentLabel) {
    this.instanceId = instanceId;
    this.templateItemId = templateItemId;
    this.name = name;
    this.description = description;
    this.sortOrder = sortOrder;
    this.required = required;
    this.requiresDocument = requiresDocument;
    this.requiredDocumentLabel = requiredDocumentLabel;
    this.status = "PENDING";
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void complete(UUID actorId, String notes, UUID documentId) {
    this.status = "COMPLETED";
    this.completedAt = Instant.now();
    this.completedBy = actorId;
    this.notes = notes;
    this.documentId = documentId;
    this.updatedAt = Instant.now();
  }

  public void skip(String reason) {
    this.status = "SKIPPED";
    this.notes = reason;
    this.updatedAt = Instant.now();
  }

  public void reopen() {
    this.status = "PENDING";
    this.completedAt = null;
    this.completedBy = null;
    this.notes = null;
    this.documentId = null;
    this.updatedAt = Instant.now();
  }

  public void block() {
    this.status = "BLOCKED";
    this.updatedAt = Instant.now();
  }

  public void unblock() {
    this.status = "PENDING";
    this.updatedAt = Instant.now();
  }

  public void setDependsOnItemId(UUID dependsOnItemId) {
    this.dependsOnItemId = dependsOnItemId;
  }

  public void setStatus(String status) {
    this.status = status;
    this.updatedAt = Instant.now();
  }

  // Getters
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
}
