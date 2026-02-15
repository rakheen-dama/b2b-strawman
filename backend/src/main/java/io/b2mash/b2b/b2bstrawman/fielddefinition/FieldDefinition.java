package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAware;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAwareEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ParamDef;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "field_definitions")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class FieldDefinition implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 20)
  private EntityType entityType;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "slug", nullable = false, length = 100)
  private String slug;

  @Enumerated(EnumType.STRING)
  @Column(name = "field_type", nullable = false, length = 20)
  private FieldType fieldType;

  @Column(name = "description")
  private String description;

  @Column(name = "required", nullable = false)
  private boolean required;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "default_value", columnDefinition = "jsonb")
  private Map<String, Object> defaultValue;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "options", columnDefinition = "jsonb")
  private List<Map<String, String>> options;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "validation", columnDefinition = "jsonb")
  private Map<String, Object> validation;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "pack_id", length = 100)
  private String packId;

  @Column(name = "pack_field_key", length = 100)
  private String packFieldKey;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected FieldDefinition() {}

  public FieldDefinition(EntityType entityType, String name, String slug, FieldType fieldType) {
    this.entityType = entityType;
    this.name = name;
    this.slug = slug;
    this.fieldType = fieldType;
    this.required = false;
    this.sortOrder = 0;
    this.active = true;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /**
   * Generates a slug from a human-readable name. Converts to lowercase, replaces spaces and hyphens
   * with underscores, strips non-alphanumeric characters (except underscores), and validates
   * against the pattern ^[a-z][a-z0-9_]*$.
   */
  public static String generateSlug(String name) {
    if (name == null || name.isBlank()) {
      throw new InvalidStateException("Invalid name", "Name must not be blank");
    }
    String slug = name.toLowerCase().replaceAll("[\\s-]+", "_").replaceAll("[^a-z0-9_]", "");

    if (slug.isEmpty() || !Character.isLetter(slug.charAt(0))) {
      throw new InvalidStateException(
          "Invalid slug", "Generated slug must start with a letter: " + slug);
    }

    if (!slug.matches("^[a-z][a-z0-9_]*$")) {
      throw new InvalidStateException("Invalid slug", "Slug must match ^[a-z][a-z0-9_]*$: " + slug);
    }

    return slug;
  }

  /** Updates mutable metadata fields. */
  public void updateMetadata(
      String name, String description, boolean required, Map<String, Object> validation) {
    this.name = name;
    this.description = description;
    this.required = required;
    this.validation = validation;
    this.updatedAt = Instant.now();
  }

  /** Soft-deletes this field definition by setting active to false. */
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

  public EntityType getEntityType() {
    return entityType;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public FieldType getFieldType() {
    return fieldType;
  }

  public String getDescription() {
    return description;
  }

  public boolean isRequired() {
    return required;
  }

  public Map<String, Object> getDefaultValue() {
    return defaultValue;
  }

  public List<Map<String, String>> getOptions() {
    return options;
  }

  public Map<String, Object> getValidation() {
    return validation;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public String getPackId() {
    return packId;
  }

  public String getPackFieldKey() {
    return packFieldKey;
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

  public void setDescription(String description) {
    this.description = description;
  }

  public void setDefaultValue(Map<String, Object> defaultValue) {
    this.defaultValue = defaultValue;
  }

  public void setOptions(List<Map<String, String>> options) {
    this.options = options;
  }

  public void setValidation(Map<String, Object> validation) {
    this.validation = validation;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
