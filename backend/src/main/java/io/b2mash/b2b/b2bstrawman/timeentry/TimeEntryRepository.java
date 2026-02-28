package io.b2mash.b2b.b2bstrawman.timeentry;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {
  @Query(
      """
      SELECT te FROM TimeEntry te, Task t
      WHERE te.taskId = t.id
        AND t.projectId IN :projectIds
        AND te.billable = true
      ORDER BY te.date DESC, te.createdAt DESC
      """)
  List<TimeEntry> findBillableByProjectIdIn(@Param("projectIds") List<UUID> projectIds);

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
      WHERE te.taskId = :taskId
        AND (
          (:billingStatus = 'UNBILLED' AND te.billable = true AND te.invoiceId IS NULL)
          OR (:billingStatus = 'BILLED' AND te.invoiceId IS NOT NULL)
          OR (:billingStatus = 'NON_BILLABLE' AND te.billable = false)
        )
      ORDER BY te.date DESC, te.createdAt DESC
      """)
  List<TimeEntry> findByTaskIdAndBillingStatus(
      @Param("taskId") UUID taskId, @Param("billingStatus") String billingStatus);

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
  // Tenant isolation is provided by the dedicated schema (search_path set on connection checkout).

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
   * parameters are optional — when null, all-time data is returned. Tenant isolation is provided by
   * the dedicated schema (search_path).
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
   * project_id and projects to get project name. Date range parameters are optional. Tenant
   * isolation is provided by the dedicated schema (search_path).
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
   * total logged time without N+1 queries. Tenant isolation is provided by the dedicated schema
   * (search_path).
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
   * from minutes to hours. Tenant isolation is provided by the dedicated schema (search_path).
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

  // --- Org-level aggregation queries (Epic 76A) ---

  /**
   * Org-level hours summary for a date range. Returns total and billable minutes. Tenant isolation
   * is provided by the dedicated schema (search_path).
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT
          COALESCE(SUM(te.duration_minutes), 0) AS totalMinutes,
          COALESCE(SUM(CASE WHEN te.billable THEN te.duration_minutes ELSE 0 END), 0) AS billableMinutes
      FROM time_entries te
      JOIN tasks t ON te.task_id = t.id
      WHERE te.date >= CAST(:fromDate AS DATE) AND te.date <= CAST(:toDate AS DATE)
      """)
  OrgHoursSummaryProjection findOrgHoursSummary(
      @Param("fromDate") LocalDate from, @Param("toDate") LocalDate to);

  /**
   * Hours grouped by period for trend computation. Uses date_trunc for grouping. Tenant isolation
   * is provided by the dedicated schema (search_path).
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT
          TO_CHAR(date_trunc(:granularity, te.date), :format) AS period,
          COALESCE(SUM(te.duration_minutes), 0) AS totalMinutes,
          date_trunc(:granularity, te.date) AS periodStart
      FROM time_entries te
      JOIN tasks t ON te.task_id = t.id
      WHERE te.date >= CAST(:fromDate AS DATE) AND te.date <= CAST(:toDate AS DATE)
      GROUP BY periodStart, period
      ORDER BY periodStart
      """)
  List<TrendProjection> findHoursTrend(
      @Param("fromDate") LocalDate from,
      @Param("toDate") LocalDate to,
      @Param("granularity") String granularity,
      @Param("format") String format);

  // --- Team workload aggregation query (Epic 76B) ---

  /**
   * Team workload query: aggregates hours per member per project for a date range. Returns flat
   * rows to be post-processed (grouped by member, capped at 5 projects). Tenant isolation is
   * provided by the dedicated schema (search_path).
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT
          m.id AS memberId,
          m.name AS memberName,
          t.project_id AS projectId,
          p.name AS projectName,
          COALESCE(SUM(te.duration_minutes), 0) AS totalMinutes,
          COALESCE(SUM(CASE WHEN te.billable THEN te.duration_minutes ELSE 0 END), 0) AS billableMinutes
      FROM time_entries te
      JOIN tasks t ON te.task_id = t.id
      JOIN members m ON te.member_id = m.id
      JOIN projects p ON t.project_id = p.id
      WHERE te.date >= CAST(:fromDate AS DATE) AND te.date <= CAST(:toDate AS DATE)
      GROUP BY m.id, m.name, t.project_id, p.name
      ORDER BY SUM(te.duration_minutes) DESC
      """)
  List<TeamWorkloadProjection> findTeamWorkload(
      @Param("fromDate") LocalDate from, @Param("toDate") LocalDate to);

  // --- Personal dashboard trend query (Epic 79A) ---

  /**
   * Member-scoped hours trend for personal dashboard sparkline. Same grouping logic as
   * findHoursTrend but filtered to a single member. Tenant isolation is provided by the dedicated
   * schema (search_path).
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT
          TO_CHAR(date_trunc(:granularity, te.date), :format) AS period,
          COALESCE(SUM(te.duration_minutes), 0) AS totalMinutes,
          date_trunc(:granularity, te.date) AS periodStart
      FROM time_entries te
      JOIN tasks t ON te.task_id = t.id
      WHERE te.member_id = CAST(:memberId AS UUID)
        AND te.date >= CAST(:fromDate AS DATE) AND te.date <= CAST(:toDate AS DATE)
      GROUP BY periodStart, period
      ORDER BY periodStart
      """)
  List<TrendProjection> findMemberHoursTrend(
      @Param("memberId") UUID memberId,
      @Param("fromDate") LocalDate from,
      @Param("toDate") LocalDate to,
      @Param("granularity") String granularity,
      @Param("format") String format);

  /**
   * Total amount consumed for a project: SUM of (billing_rate_snapshot * duration_minutes / 60) for
   * billable entries matching the budget currency. Tenant isolation is provided by the dedicated
   * schema (search_path).
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

  // --- Delete protection guard queries (Epic 206A) ---

  /** Counts all time entries for tasks in a project. Used by delete protection guard. */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT COUNT(te.id)
      FROM time_entries te
      JOIN tasks t ON te.task_id = t.id
      WHERE t.project_id = :projectId
      """)
  long countByProjectId(@Param("projectId") UUID projectId);

  /** Counts all time entries for a specific task. Used by delete protection guard. */
  @Query("SELECT COUNT(te) FROM TimeEntry te WHERE te.taskId = :taskId")
  long countByTaskId(@Param("taskId") UUID taskId);

  // --- Project lifecycle guardrail queries (Epic 204A) ---

  /**
   * Counts unbilled time entries and total unbilled hours for a project. Unbilled = billable AND
   * invoice_id IS NULL.
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT
        COUNT(te.id) AS entryCount,
        COALESCE(SUM(te.duration_minutes), 0) / 60.0 AS totalHours
      FROM time_entries te
      JOIN tasks t ON te.task_id = t.id
      WHERE t.project_id = :projectId
        AND te.billable = true
        AND te.invoice_id IS NULL
      """)
  UnbilledTimeSummaryProjection countUnbilledByProjectId(@Param("projectId") UUID projectId);
}
