package io.b2mash.b2b.b2bstrawman.tag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entity_tags")
public class EntityTag {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tag_id", nullable = false)
  private UUID tagId;

  @Column(name = "entity_type", nullable = false, length = 20)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected EntityTag() {}

  public EntityTag(UUID tagId, String entityType, UUID entityId) {
    this.tagId = tagId;
    this.entityType = entityType;
    this.entityId = entityId;
    this.createdAt = Instant.now();
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getTagId() {
    return tagId;
  }

  public String getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
