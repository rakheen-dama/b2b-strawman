package io.b2mash.b2b.b2bstrawman.fielddefinition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "field_groups")
public class FieldGroup {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 20)
  private EntityType entityType;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "slug", nullable = false, length = 100)
  private String slug;

  @Column(name = "description")
  private String description;

  @Column(name = "pack_id", length = 100)
  private String packId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected FieldGroup() {}

  public FieldGroup(EntityType entityType, String name, String slug) {
    this.entityType = entityType;
    this.name = name;
    this.slug = slug;
    this.sortOrder = 0;
    this.active = true;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Updates mutable metadata fields. */
  public void updateMetadata(String name, String description, int sortOrder) {
    this.name = name;
    this.description = description;
    this.sortOrder = sortOrder;
    this.updatedAt = Instant.now();
  }

  /** Soft-deletes this field group by setting active to false. */
  public void deactivate() {
    this.active = false;
    this.updatedAt = Instant.now();
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

  public String getDescription() {
    return description;
  }

  public String getPackId() {
    return packId;
  }

  public int getSortOrder() {
    return sortOrder;
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

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public void setPackId(String packId) {
    this.packId = packId;
  }
}
