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
@Table(name = "checklist_template_items")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class ChecklistTemplateItem implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "template_id", nullable = false)
  private UUID templateId;

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

  @Column(name = "depends_on_item_id")
  private UUID dependsOnItemId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ChecklistTemplateItem() {}

  public ChecklistTemplateItem(
      UUID templateId,
      String name,
      String description,
      int sortOrder,
      boolean required,
      boolean requiresDocument) {
    this.templateId = templateId;
    this.name = name;
    this.description = description;
    this.sortOrder = sortOrder;
    this.required = required;
    this.requiresDocument = requiresDocument;
    this.createdAt = Instant.now();
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

  public void setName(String name) {
    this.name = name;
    this.updatedAt = Instant.now();
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public void setRequired(boolean required) {
    this.required = required;
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
