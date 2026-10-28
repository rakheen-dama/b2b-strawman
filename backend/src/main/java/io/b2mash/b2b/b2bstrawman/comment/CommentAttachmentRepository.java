package io.b2mash.b2b.b2bstrawman.comment;

import io.b2mash.b2b.b2bstrawman.document.Document;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentAttachmentRepository
    extends JpaRepository<CommentAttachment, CommentAttachment.Key> {

  List<CommentAttachment> findByCommentIdOrderBySortOrderAsc(UUID commentId);

  /** A document hangs off one comment at most. */
  boolean existsByDocumentId(UUID documentId);

  /**
   * The attachment chips for a page of comments in one query (link joined to its document), ordered
   * by each comment's sort order. Callers filter by visibility where needed.
   */
  @Query(
      """
      SELECT ca.commentId AS commentId, d.id AS documentId, d.fileName AS fileName,
             d.contentType AS contentType, d.size AS fileSize, d.visibility AS visibility,
             d.status AS status, d.scope AS scope, d.projectId AS projectId
      FROM CommentAttachment ca JOIN Document d ON d.id = ca.documentId
      WHERE ca.commentId IN :commentIds
      ORDER BY ca.sortOrder ASC
      """)
  List<AttachmentChip> findChipsByCommentIds(@Param("commentIds") Collection<UUID> commentIds);

  /**
   * One attachment chip: the link's comment plus the document fields a chip shows or filters on.
   */
  interface AttachmentChip {
    UUID getCommentId();

    UUID getDocumentId();

    String getFileName();

    String getContentType();

    long getFileSize();

    String getVisibility();

    Document.Status getStatus();

    String getScope();

    UUID getProjectId();
  }
}
