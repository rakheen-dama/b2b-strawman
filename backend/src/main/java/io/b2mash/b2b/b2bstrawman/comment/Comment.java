package io.b2mash.b2b.b2bstrawman.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class Comment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "entity_type", nullable = false, length = 20)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "author_member_id", nullable = false)
  private UUID authorMemberId;

  @NotBlank
  @Column(name = "body", nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "visibility", nullable = false, length = 20)
  private String visibility;

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "source", nullable = false, length = 20)
  private String source = "INTERNAL";

  protected Comment() {}

  public Comment(
      String entityType,
      UUID entityId,
      UUID projectId,
      UUID authorMemberId,
      String body,
      String visibility) {
    this.entityType = entityType;
    this.entityId = entityId;
    this.projectId = projectId;
    this.authorMemberId = authorMemberId;
    this.body = body;
    this.visibility = visibility;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public Comment(
      String entityType,
      UUID entityId,
      UUID projectId,
      UUID authorMemberId,
      String body,
      String visibility,
      String source) {
    this.entityType = entityType;
    this.entityId = entityId;
    this.projectId = projectId;
    this.authorMemberId = authorMemberId;
    this.body = body;
    this.visibility = visibility;
    this.source = source;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void updateBody(String body) {
    this.body = body;
    this.updatedAt = Instant.now();
  }

  public void updateVisibility(String visibility) {
    this.visibility = visibility;
    this.updatedAt = Instant.now();
  }

  /** Replaces body with the given replacement text (used for data anonymization). */
  public void redact(String replacement) {
    this.body = replacement;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getAuthorMemberId() {
    return authorMemberId;
  }

  public String getBody() {
    return body;
  }

  public String getVisibility() {
    return visibility;
  }

  public UUID getParentId() {
    return parentId;
  }

  public void setParentId(UUID parentId) {
    this.parentId = parentId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public String getSource() {
    return source;
  }
}
