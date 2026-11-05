package io.b2mash.b2b.b2bstrawman.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "recipient_member_id", nullable = false)
  private UUID recipientMemberId;

  @Column(name = "type", nullable = false, length = 50)
  private String type;

  @Column(name = "title", nullable = false, length = 500)
  private String title;

  @Column(name = "body", columnDefinition = "TEXT")
  private String body;

  @Column(name = "reference_entity_type", length = 20)
  private String referenceEntityType;

  @Column(name = "reference_entity_id")
  private UUID referenceEntityId;

  @Column(name = "reference_project_id")
  private UUID referenceProjectId;

  /**
   * The comment a COMMENT_ADDED notification is about (G88-M6, tenant V11), so the bell can open
   * the comment itself. No FK: a deleted comment leaves the notification and its link degrades to
   * the entity-level tab. NULL for every other notification type and for pre-V11 rows.
   */
  @Column(name = "reference_comment_id")
  private UUID referenceCommentId;

  @Column(name = "is_read", nullable = false)
  private boolean isRead;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Notification() {}

  public Notification(
      UUID recipientMemberId,
      String type,
      String title,
      String body,
      String referenceEntityType,
      UUID referenceEntityId,
      UUID referenceProjectId) {
    this(
        recipientMemberId,
        type,
        title,
        body,
        referenceEntityType,
        referenceEntityId,
        referenceProjectId,
        null);
  }

  public Notification(
      UUID recipientMemberId,
      String type,
      String title,
      String body,
      String referenceEntityType,
      UUID referenceEntityId,
      UUID referenceProjectId,
      UUID referenceCommentId) {
    this.recipientMemberId = recipientMemberId;
    this.type = type;
    this.title = title;
    this.body = body;
    this.referenceEntityType = referenceEntityType;
    this.referenceEntityId = referenceEntityId;
    this.referenceProjectId = referenceProjectId;
    this.referenceCommentId = referenceCommentId;
    this.isRead = false;
    this.createdAt = Instant.now();
  }

  public void markAsRead() {
    this.isRead = true;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRecipientMemberId() {
    return recipientMemberId;
  }

  public String getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public String getReferenceEntityType() {
    return referenceEntityType;
  }

  public UUID getReferenceEntityId() {
    return referenceEntityId;
  }

  public UUID getReferenceProjectId() {
    return referenceProjectId;
  }

  public UUID getReferenceCommentId() {
    return referenceCommentId;
  }

  public boolean isRead() {
    return isRead;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
