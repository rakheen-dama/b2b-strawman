package io.b2mash.b2b.b2bstrawman.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A document attached to a comment (Phase 88 U24, A13, tenant V9 §7). Child table keyed by
 * (comment_id, document_id); the document stays a first-class row in the matter's Documents list.
 * Both FKs cascade on delete: deleting the comment removes only the link, deleting the document
 * removes the chip.
 */
@Entity
@Table(name = "comment_attachments")
@IdClass(CommentAttachment.Key.class)
public class CommentAttachment {

  @Id
  @Column(name = "comment_id", nullable = false, updatable = false)
  private UUID commentId;

  @Id
  @Column(name = "document_id", nullable = false, updatable = false)
  private UUID documentId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CommentAttachment() {}

  public CommentAttachment(UUID commentId, UUID documentId, int sortOrder) {
    this.commentId = commentId;
    this.documentId = documentId;
    this.sortOrder = sortOrder;
    this.createdAt = Instant.now();
  }

  public UUID getCommentId() {
    return commentId;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Composite key for {@link IdClass}. Field names must match the entity's {@code @Id} fields. */
  public static final class Key implements Serializable {
    private UUID commentId;
    private UUID documentId;

    public Key() {}

    public Key(UUID commentId, UUID documentId) {
      this.commentId = commentId;
      this.documentId = documentId;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k
          && Objects.equals(commentId, k.commentId)
          && Objects.equals(documentId, k.documentId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(commentId, documentId);
    }
  }
}
