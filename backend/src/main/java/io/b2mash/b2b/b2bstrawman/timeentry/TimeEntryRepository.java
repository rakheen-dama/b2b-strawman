package io.b2mash.b2b.b2bstrawman.timeentry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT te FROM TimeEntry te WHERE te.id = :id")
  Optional<TimeEntry> findOneById(@Param("id") UUID id);

  @Query(
      "SELECT te FROM TimeEntry te WHERE te.taskId = :taskId ORDER BY te.date DESC, te.createdAt"
          + " DESC")
  List<TimeEntry> findByTaskId(@Param("taskId") UUID taskId);

  @Query(
      """
      SELECT te FROM TimeEntry te
      WHERE te.memberId = :memberId
        AND te.date >= :from
        AND te.date <= :to
      ORDER BY te.date DESC, te.createdAt DESC
      """)
  List<TimeEntry> findByMemberIdAndDateBetween(
      @Param("memberId") UUID memberId, @Param("from") LocalDate from, @Param("to") LocalDate to);

  // --- Project time summary aggregation queries (Epic 46A) ---
  // Native SQL because JPQL lacks conditional SUM (CASE WHEN) and multi-table GROUP BY.
  // RLS policy handles tenant isolation for native queries via set_config('app.current_tenant').

  /**
   * Aggregates total billable/non-billable minutes, contributor count, and entry count for a
   * project. Date range parameters are optional — when null, all-time data is returned.
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT
        COALESCE(SUM(CASE WHEN te.billable = true THEN te.duration_minutes ELSE 0 END), 0) AS billableMinutes,
        COALESCE(SUM(CASE WHEN te.billable = false THEN te.duration_minutes ELSE 0 END), 0) AS nonBillableMinutes,
        COALESCE(SUM(te.duration_minutes), 0) AS totalMinutes,
        COUNT(DISTINCT te.member_id) AS contributorCount,
        COUNT(te.id) AS entryCount
      FROM time_entries te
        JOIN tasks t ON te.task_id = t.id
      WHERE t.project_id = :projectId
        AND (CAST(:fromDate AS DATE) IS NULL OR te.date >= CAST(:fromDate AS DATE))
        AND (CAST(:toDate AS DATE) IS NULL OR te.date <= CAST(:toDate AS DATE))
      """)
  ProjectTimeSummaryProjection projectTimeSummary(
      @Param("projectId") UUID projectId,
      @Param("fromDate") LocalDate from,
      @Param("toDate") LocalDate to);

  /**
   * Aggregates billable/non-billable minutes per member for a project. Date range parameters are
   * optional.
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT
        te.member_id AS memberId,
        m.name AS memberName,
        COALESCE(SUM(CASE WHEN te.billable = true THEN te.duration_minutes ELSE 0 END), 0) AS billableMinutes,
        COALESCE(SUM(CASE WHEN te.billable = false THEN te.duration_minutes ELSE 0 END), 0) AS nonBillableMinutes,
        COALESCE(SUM(te.duration_minutes), 0) AS totalMinutes
      FROM time_entries te
        JOIN tasks t ON te.task_id = t.id
        JOIN members m ON te.member_id = m.id
      WHERE t.project_id = :projectId
        AND (CAST(:fromDate AS DATE) IS NULL OR te.date >= CAST(:fromDate AS DATE))
        AND (CAST(:toDate AS DATE) IS NULL OR te.date <= CAST(:toDate AS DATE))
      GROUP BY te.member_id, m.name
      ORDER BY totalMinutes DESC
      """)
  List<MemberTimeSummaryProjection> projectTimeSummaryByMember(
      @Param("projectId") UUID projectId,
      @Param("fromDate") LocalDate from,
      @Param("toDate") LocalDate to);

  /**
   * Aggregates billable minutes, total minutes, and entry count per task for a project. Date range
   * parameters are optional.
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT
        t.id AS taskId,
        t.title AS taskTitle,
        COALESCE(SUM(CASE WHEN te.billable = true THEN te.duration_minutes ELSE 0 END), 0) AS billableMinutes,
        COALESCE(SUM(te.duration_minutes), 0) AS totalMinutes,
        COUNT(te.id) AS entryCount
      FROM time_entries te
        JOIN tasks t ON te.task_id = t.id
      WHERE t.project_id = :projectId
        AND (CAST(:fromDate AS DATE) IS NULL OR te.date >= CAST(:fromDate AS DATE))
        AND (CAST(:toDate AS DATE) IS NULL OR te.date <= CAST(:toDate AS DATE))
      GROUP BY t.id, t.title
      ORDER BY totalMinutes DESC
      """)
  List<TaskTimeSummaryProjection> projectTimeSummaryByTask(
      @Param("projectId") UUID projectId,
      @Param("fromDate") LocalDate from,
      @Param("toDate") LocalDate to);
}
