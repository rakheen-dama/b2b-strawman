package io.b2mash.b2b.b2bstrawman.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT t FROM Task t WHERE t.id = :id")
  Optional<Task> findOneById(@Param("id") UUID id);

  @Query("SELECT t FROM Task t WHERE t.projectId = :projectId ORDER BY t.createdAt DESC")
  List<Task> findByProjectId(@Param("projectId") UUID projectId);

  @Query(
      """
      SELECT t FROM Task t WHERE t.projectId = :projectId
        AND (:status IS NULL OR t.status = :status)
        AND (:assigneeId IS NULL OR t.assigneeId = :assigneeId)
        AND (:priority IS NULL OR t.priority = :priority)
      ORDER BY t.createdAt DESC
      """)
  List<Task> findByProjectIdWithFilters(
      @Param("projectId") UUID projectId,
      @Param("status") String status,
      @Param("assigneeId") UUID assigneeId,
      @Param("priority") String priority);

  @Query(
      """
      SELECT t FROM Task t WHERE t.projectId = :projectId
        AND t.assigneeId IS NULL
        AND (:status IS NULL OR t.status = :status)
        AND (:priority IS NULL OR t.priority = :priority)
      ORDER BY t.createdAt DESC
      """)
  List<Task> findByProjectIdUnassigned(
      @Param("projectId") UUID projectId,
      @Param("status") String status,
      @Param("priority") String priority);
}
