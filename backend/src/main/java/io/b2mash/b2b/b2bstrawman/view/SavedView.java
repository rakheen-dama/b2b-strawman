package io.b2mash.b2b.b2bstrawman.view;

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
@Table(name = "saved_views")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class SavedView implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "entity_type", nullable = false, length = 20)
  private String entityType;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "filters", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> filters = new HashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "columns", columnDefinition = "jsonb")
  private List<String> columns;

  @Column(name = "shared", nullable = false)
  private boolean shared;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SavedView() {}

  public SavedView(
      String entityType,
      String name,
      Map<String, Object> filters,
      List<String> columns,
      boolean shared,
      UUID createdBy,
      int sortOrder) {
    this.entityType = entityType;
    this.name = name;
    this.filters = filters != null ? filters : new HashMap<>();
    this.columns = columns;
    this.shared = shared;
    this.createdBy = createdBy;
    this.sortOrder = sortOrder;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Updates mutable view configuration. */
  public void updateFilters(
      String name, Map<String, Object> filters, List<String> columns, int sortOrder) {
    this.name = name;
    this.filters = filters != null ? filters : new HashMap<>();
    this.columns = columns;
    this.sortOrder = sortOrder;
    this.updatedAt = Instant.now();
  }

  /** Promotes or demotes a view between shared and personal. */
  public void setShared(boolean shared) {
    this.shared = shared;
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

  public String getEntityType() {
    return entityType;
  }

  public String getName() {
    return name;
  }

  public Map<String, Object> getFilters() {
    return filters;
  }

  public List<String> getColumns() {
    return columns;
  }

  public boolean isShared() {
    return shared;
  }

  public UUID getCreatedBy() {
    return createdBy;
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
}
