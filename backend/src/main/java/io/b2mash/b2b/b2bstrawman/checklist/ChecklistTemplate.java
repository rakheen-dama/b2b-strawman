package io.b2mash.b2b.b2bstrawman.checklist;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAware;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAwareEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "checklist_templates")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class ChecklistTemplate implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "slug", nullable = false, length = 200)
  private String slug;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "customer_type", nullable = false, length = 20)
  private String customerType;

  @Column(name = "source", nullable = false, length = 20)
  private String source;

  @Column(name = "pack_id", length = 100)
  private String packId;

  @Column(name = "pack_template_key", length = 200)
  private String packTemplateKey;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "auto_instantiate", nullable = false)
  private boolean autoInstantiate;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ChecklistTemplate() {}

  public ChecklistTemplate(
      String name, String slug, String description, String customerType, String source) {
    this.name = name;
    this.slug = slug;
    this.description = description;
    this.customerType = customerType;
    this.source = source;
    this.active = true;
    this.autoInstantiate = true;
    this.sortOrder = 0;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  @PreUpdate
  void onPreUpdate() {
    this.updatedAt = Instant.now();
  }

  /** Activates this checklist template, making it available for instantiation. */
  public void activate() {
    this.active = true;
  }

  /** Deactivates this checklist template, preventing new instantiations. */
  public void deactivate() {
    this.active = false;
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

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public String getDescription() {
    return description;
  }

  public String getCustomerType() {
    return customerType;
  }

  public String getSource() {
    return source;
  }

  public String getPackId() {
    return packId;
  }

  public String getPackTemplateKey() {
    return packTemplateKey;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isAutoInstantiate() {
    return autoInstantiate;
  }

  public int getSortOrder() {
    return sortOrder;
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
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setAutoInstantiate(boolean autoInstantiate) {
    this.autoInstantiate = autoInstantiate;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public void setPackId(String packId) {
    this.packId = packId;
  }

  public void setPackTemplateKey(String packTemplateKey) {
    this.packTemplateKey = packTemplateKey;
  }
}
