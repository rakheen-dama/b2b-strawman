package io.b2mash.b2b.b2bstrawman.task;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {
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

  // --- Cross-project queries for My Work (Epic 48A) ---

  /**
   * Finds tasks assigned to a specific member with active statuses (OPEN, IN_PROGRESS). Ordered by
   * due date ascending (nulls last) then created date descending. Scoped to the current tenant
   * schema via search_path.
   */
  @Query(
      """
      SELECT t FROM Task t
      WHERE t.assigneeId = :memberId
        AND t.status IN ('OPEN', 'IN_PROGRESS')
      ORDER BY t.dueDate ASC NULLS LAST, t.createdAt DESC
      """)
  List<Task> findAssignedToMember(@Param("memberId") UUID memberId);

  /**
   * Finds unassigned OPEN tasks in projects where the member is a ProjectMember. Ordered by
   * priority descending then created date descending. Scoped to the current tenant schema via
   * search_path.
   */
  @Query(
      """
      SELECT t FROM Task t
      WHERE t.assigneeId IS NULL
        AND t.status = 'OPEN'
        AND t.projectId IN (
          SELECT pm.projectId FROM ProjectMember pm WHERE pm.memberId = :memberId
        )
      ORDER BY t.createdAt DESC
      """)
  List<Task> findUnassignedInMemberProjects(@Param("memberId") UUID memberId);

  // --- Dashboard aggregation queries (Epic 75B) ---

  /** Counts all tasks in a project within the current tenant schema. */
  @Query("SELECT COUNT(t) FROM Task t WHERE t.projectId = :projectId")
  long countByProjectId(@Param("projectId") UUID projectId);

  /** Counts tasks in a project matching a specific status. */
  @Query("SELECT COUNT(t) FROM Task t WHERE t.projectId = :projectId AND t.status = :status")
  long countByProjectIdAndStatus(
      @Param("projectId") UUID projectId, @Param("status") String status);

  /** Counts non-DONE tasks in a project that are past their due date. */
  @Query(
      "SELECT COUNT(t) FROM Task t WHERE t.projectId = :projectId AND t.status <> 'DONE' AND t.dueDate < :today")
  long countOverdueByProjectId(@Param("projectId") UUID projectId, @Param("today") LocalDate today);

  /** Counts all non-DONE tasks across the org that are past their due date. */
  @Query("SELECT COUNT(t) FROM Task t WHERE t.status <> 'DONE' AND t.dueDate < :today")
  long countOrgOverdue(@Param("today") LocalDate today);

  /**
   * Single aggregation query returning all task summary counts for a project. Returns Object[] with
   * positions: [0]=todo, [1]=inProgress, [2]=inReview, [3]=done, [4]=total, [5]=overdueCount.
   */
  @Query(
      """
      SELECT
          COALESCE(SUM(CASE WHEN t.status = 'OPEN' THEN 1 ELSE 0 END), 0),
          COALESCE(SUM(CASE WHEN t.status = 'IN_PROGRESS' THEN 1 ELSE 0 END), 0),
          COALESCE(SUM(CASE WHEN t.status = 'IN_REVIEW' THEN 1 ELSE 0 END), 0),
          COALESCE(SUM(CASE WHEN t.status = 'DONE' THEN 1 ELSE 0 END), 0),
          COUNT(t),
          COALESCE(SUM(CASE WHEN t.status <> 'DONE' AND t.dueDate < :today THEN 1 ELSE 0 END), 0)
      FROM Task t
      WHERE t.projectId = :projectId
      """)
  List<Object[]> getTaskSummaryByProjectId(
      @Param("projectId") UUID projectId, @Param("today") LocalDate today);

  // --- Personal dashboard queries (Epic 79A) ---

  /**
   * Finds upcoming tasks assigned to a member with a due date on or after today and non-DONE
   * status. Ordered by due date ascending. Limit is applied in Java via stream().limit().
   */
  @Query(
      """
      SELECT t FROM Task t
      WHERE t.assigneeId = :memberId
        AND t.status <> 'DONE'
        AND t.dueDate >= :today
      ORDER BY t.dueDate ASC
      """)
  List<Task> findUpcomingByAssignee(
      @Param("memberId") UUID memberId, @Param("today") LocalDate today);

  /** Counts non-DONE tasks assigned to a specific member that are past their due date. */
  @Query(
      "SELECT COUNT(t) FROM Task t WHERE t.assigneeId = :memberId AND t.status <> 'DONE' AND t.dueDate < :today")
  long countOverdueByAssignee(@Param("memberId") UUID memberId, @Param("today") LocalDate today);
}
