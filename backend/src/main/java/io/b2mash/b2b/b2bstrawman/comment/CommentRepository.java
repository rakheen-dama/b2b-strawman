package io.b2mash.b2b.b2bstrawman.comment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT c FROM Comment c WHERE c.id = :id")
  Optional<Comment> findOneById(@Param("id") UUID id);

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
}
