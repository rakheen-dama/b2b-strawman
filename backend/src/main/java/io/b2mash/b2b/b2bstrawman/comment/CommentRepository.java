package io.b2mash.b2b.b2bstrawman.comment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
  /**
   * List comments on a specific entity within a project, ordered by creation time (oldest first).
   */
  @Query(
      """
      SELECT c FROM Comment c
      WHERE c.entityType = :entityType
        AND c.entityId = :entityId
        AND c.projectId = :projectId
      ORDER BY c.createdAt ASC
      """)
  Page<Comment> findByTargetAndProject(
      @Param("entityType") String entityType,
      @Param("entityId") UUID entityId,
      @Param("projectId") UUID projectId,
      Pageable pageable);

  /** Find all distinct commenter member IDs on an entity (for notification fan-out). */
  @Query(
      """
      SELECT DISTINCT c.authorMemberId FROM Comment c
      WHERE c.entityType = :entityType
        AND c.entityId = :entityId
      """)
  List<UUID> findDistinctAuthorsByEntity(
      @Param("entityType") String entityType, @Param("entityId") UUID entityId);

  /** Find portal-visible (SHARED) comments for a customer via their documents. */
  @Query(
      """
      SELECT c FROM Comment c
      JOIN Document d ON c.entityId = d.id AND c.entityType = 'DOCUMENT'
      WHERE d.customerId = :customerId
        AND c.visibility = 'EXTERNAL'
      ORDER BY c.createdAt ASC
      """)
  List<Comment> findPortalVisibleByCustomerId(@Param("customerId") UUID customerId);
}
