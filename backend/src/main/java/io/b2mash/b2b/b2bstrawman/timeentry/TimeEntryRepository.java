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
      WHERE te.taskId = :taskId
        AND (:billable IS NULL OR te.billable = :billable)
      ORDER BY te.date DESC, te.createdAt DESC
      """)
  List<TimeEntry> findByTaskIdAndBillable(
      @Param("taskId") UUID taskId, @Param("billable") Boolean billable);

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

  @Query(
      """
      SELECT te FROM TimeEntry te, Task t
      WHERE te.taskId = t.id
        AND (:projectId IS NULL OR t.projectId = :projectId)
        AND (:memberId IS NULL OR te.memberId = :memberId)
        AND (CAST(:fromDate AS date) IS NULL OR te.date >= :fromDate)
        AND (CAST(:toDate AS date) IS NULL OR te.date <= :toDate)
      """)
  List<TimeEntry> findByFilters(
      @Param("projectId") UUID projectId,
      @Param("memberId") UUID memberId,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);

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

  // --- My Work: member time summary queries (Epic 48A) ---

  /**
   * Aggregates total billable/non-billable minutes for a member across all projects. Date range
   * parameters are optional — when null, all-time data is returned. RLS handles tenant isolation.
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT
        COALESCE(SUM(CASE WHEN te.billable = true THEN te.duration_minutes ELSE 0 END), 0) AS billableMinutes,
        COALESCE(SUM(CASE WHEN te.billable = false THEN te.duration_minutes ELSE 0 END), 0) AS nonBillableMinutes,
        COALESCE(SUM(te.duration_minutes), 0) AS totalMinutes
      FROM time_entries te
      WHERE te.member_id = CAST(:memberId AS UUID)
        AND (CAST(:fromDate AS DATE) IS NULL OR te.date >= CAST(:fromDate AS DATE))
        AND (CAST(:toDate AS DATE) IS NULL OR te.date <= CAST(:toDate AS DATE))
      """)
  MyWorkMemberTimeSummaryProjection memberTimeSummary(
      @Param("memberId") UUID memberId,
      @Param("fromDate") LocalDate from,
      @Param("toDate") LocalDate to);

  /**
   * Aggregates billable/non-billable minutes per project for a member. Joins through tasks to get
   * project_id and projects to get project name. Date range parameters are optional. RLS handles
   * tenant isolation.
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT
        t.project_id AS projectId,
        p.name AS projectName,
        COALESCE(SUM(CASE WHEN te.billable = true THEN te.duration_minutes ELSE 0 END), 0) AS billableMinutes,
        COALESCE(SUM(CASE WHEN te.billable = false THEN te.duration_minutes ELSE 0 END), 0) AS nonBillableMinutes,
        COALESCE(SUM(te.duration_minutes), 0) AS totalMinutes
      FROM time_entries te
        JOIN tasks t ON te.task_id = t.id
        JOIN projects p ON t.project_id = p.id
      WHERE te.member_id = CAST(:memberId AS UUID)
        AND (CAST(:fromDate AS DATE) IS NULL OR te.date >= CAST(:fromDate AS DATE))
        AND (CAST(:toDate AS DATE) IS NULL OR te.date <= CAST(:toDate AS DATE))
      GROUP BY t.project_id, p.name
      ORDER BY totalMinutes DESC
      """)
  List<MyWorkProjectTimeSummaryProjection> memberTimeSummaryByProject(
      @Param("memberId") UUID memberId,
      @Param("fromDate") LocalDate from,
      @Param("toDate") LocalDate to);

  /**
   * Sums total duration per task for a batch of task IDs. Used to enrich My Work task items with
   * total logged time without N+1 queries. RLS handles tenant isolation.
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT
        te.task_id AS taskId,
        COALESCE(SUM(te.duration_minutes), 0) AS totalMinutes
      FROM time_entries te
      WHERE te.task_id IN (:taskIds)
      GROUP BY te.task_id
      """)
  List<TaskDurationProjection> sumDurationByTaskIds(@Param("taskIds") List<UUID> taskIds);

  // --- Budget consumption aggregation queries (Epic 71A) ---

  /**
   * Total hours consumed for a project: SUM of all time entries (billable + non-billable) converted
   * from minutes to hours. RLS handles tenant isolation for native queries.
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT COALESCE(SUM(te.duration_minutes), 0) / 60.0 AS hoursConsumed
      FROM time_entries te
      JOIN tasks t ON te.task_id = t.id
      WHERE t.project_id = :projectId
      """)
  BudgetHoursProjection budgetHoursConsumed(@Param("projectId") UUID projectId);

  /**
   * Total amount consumed for a project: SUM of (billing_rate_snapshot * duration_minutes / 60) for
   * billable entries matching the budget currency. RLS handles tenant isolation for native queries.
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT COALESCE(SUM(
          CAST(te.billing_rate_snapshot AS DECIMAL(14,2))
          * te.duration_minutes / 60.0
      ), 0) AS amountConsumed
      FROM time_entries te
      JOIN tasks t ON te.task_id = t.id
      WHERE t.project_id = :projectId
        AND te.billable = true
        AND te.billing_rate_currency = :budgetCurrency
        AND te.billing_rate_snapshot IS NOT NULL
      """)
  BudgetAmountProjection budgetAmountConsumed(
      @Param("projectId") UUID projectId, @Param("budgetCurrency") String budgetCurrency);
}
