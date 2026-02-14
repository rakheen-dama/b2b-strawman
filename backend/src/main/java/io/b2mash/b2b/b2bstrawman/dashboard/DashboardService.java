package io.b2mash.b2b.b2bstrawman.dashboard;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.audit.CrossProjectActivityProjection;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudget;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudgetRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.CrossProjectActivityItem;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.KpiResponse;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.KpiValues;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.ProjectHealth;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.ProjectHealthDetail;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.ProjectHealthMetrics;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.ProjectHoursEntry;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TaskSummary;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TeamWorkloadEntry;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TrendPoint;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectWithRole;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TeamWorkloadProjection;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for computing project and org-level dashboard data including health scoring, task
 * summaries, and company KPIs. Uses Caffeine caches: project-scoped (1-min TTL) and org-scoped
 * (3-min TTL).
 */
@Service
public class DashboardService {

  private final TaskRepository taskRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final AuditEventRepository auditEventRepository;
  private final ProjectBudgetRepository projectBudgetRepository;
  private final ProjectRepository projectRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final CustomerRepository customerRepository;

  private final Cache<String, Object> projectCache =
      Caffeine.newBuilder().maximumSize(5_000).expireAfterWrite(Duration.ofMinutes(1)).build();

  private final Cache<String, Object> orgCache =
      Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(Duration.ofMinutes(3)).build();

  public DashboardService(
      TaskRepository taskRepository,
      TimeEntryRepository timeEntryRepository,
      AuditEventRepository auditEventRepository,
      ProjectBudgetRepository projectBudgetRepository,
      ProjectRepository projectRepository,
      CustomerProjectRepository customerProjectRepository,
      CustomerRepository customerRepository) {
    this.taskRepository = taskRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.auditEventRepository = auditEventRepository;
    this.projectBudgetRepository = projectBudgetRepository;
    this.projectRepository = projectRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.customerRepository = customerRepository;
  }

  // --- Project-scoped endpoints (Epic 75B) ---

  /**
   * Computes the health status for a project, gathering task counts, budget data, and last activity
   * information. Results are cached for 1 minute per tenant+project.
   *
   * @param projectId the project to compute health for
   * @param tenantId the tenant schema for cache key isolation
   * @return the project health detail with status, reasons, and metrics
   */
  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public ProjectHealthDetail getProjectHealth(UUID projectId, String tenantId) {
    String key = tenantId + ":project:" + projectId + ":health";
    ProjectHealthDetail cached = (ProjectHealthDetail) projectCache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }

    ProjectHealthDetail result = computeProjectHealth(projectId);
    projectCache.put(key, result);
    return result;
  }

  /**
   * Computes task summary counts for a project, broken down by status. Results are cached for 1
   * minute per tenant+project.
   *
   * @param projectId the project to summarize
   * @param tenantId the tenant schema for cache key isolation
   * @return the task summary with counts by status and overdue count
   */
  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public TaskSummary getTaskSummary(UUID projectId, String tenantId) {
    String key = tenantId + ":project:" + projectId + ":task-summary";
    TaskSummary cached = (TaskSummary) projectCache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }

    TaskSummary result = computeTaskSummary(projectId);
    projectCache.put(key, result);
    return result;
  }

  // --- Org-level endpoints (Epic 76A) ---

  /**
   * Returns org-level KPIs with trend and previous-period comparison. Financial fields
   * (billablePercent, averageMarginPercent) are redacted for non-admin/non-owner members. Cache
   * stores the full (non-redacted) version; redaction happens after cache retrieval.
   *
   * @param tenantId the tenant schema for cache key isolation
   * @param orgRole the caller's org role for financial field redaction
   * @param from start date of the KPI period
   * @param to end date of the KPI period
   * @return KPI response with trend and previous period data
   */
  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public KpiResponse getCompanyKpis(String tenantId, String orgRole, LocalDate from, LocalDate to) {
    String key = tenantId + ":kpis:" + from + "_" + to;

    // Always cache the FULL version
    KpiResponse full = (KpiResponse) orgCache.getIfPresent(key);
    if (full == null) {
      KpiValues currentValues = computeKpiValues(from, to);
      List<TrendPoint> trend = computeTrend(from, to);
      KpiValues previousPeriod = computePreviousPeriod(from, to);

      full =
          new KpiResponse(
              currentValues.activeProjectCount(),
              currentValues.totalHoursLogged(),
              currentValues.billablePercent(),
              currentValues.overdueTaskCount(),
              currentValues.averageMarginPercent(),
              trend,
              previousPeriod);

      orgCache.put(key, full);
    }

    // Single redaction point
    return isAdminOrOwner(orgRole) ? full : full.withFinancialsRedacted();
  }

  /**
   * Returns the project health list for accessible projects, sorted by severity (CRITICAL first),
   * then by completion percent ascending (least complete first).
   *
   * @param tenantId the tenant schema for cache key isolation
   * @param memberId the requesting member's ID for project access filtering
   * @param orgRole the caller's org role for visibility (admin/owner sees all projects)
   * @return sorted list of project health summaries
   */
  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public List<ProjectHealth> getProjectHealthList(String tenantId, UUID memberId, String orgRole) {
    String key = tenantId + ":project-health:" + orgRole;
    List<ProjectHealth> cached = (List<ProjectHealth>) orgCache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }

    List<ProjectHealth> result = computeProjectHealthList(memberId, orgRole);
    orgCache.put(key, result);
    return result;
  }

  // --- Org-level endpoints (Epic 76B) ---

  /**
   * Returns team workload data aggregating hours per member across projects for a date range.
   * Admin/owner sees all members; regular members see only their own entry. Results are cached in
   * the org cache.
   *
   * @param tenantId the tenant schema for cache key isolation
   * @param memberId the requesting member's ID for self-filtering
   * @param orgRole the caller's org role for visibility
   * @param from start date of the workload period
   * @param to end date of the workload period
   * @return list of team workload entries with per-project breakdowns
   */
  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public List<TeamWorkloadEntry> getTeamWorkload(
      String tenantId, UUID memberId, String orgRole, LocalDate from, LocalDate to) {
    String cacheKey =
        isAdminOrOwner(orgRole)
            ? tenantId + ":team-workload:all:" + from + "_" + to
            : tenantId + ":team-workload:member:" + memberId + ":" + from + "_" + to;

    List<TeamWorkloadEntry> cached = (List<TeamWorkloadEntry>) orgCache.getIfPresent(cacheKey);
    if (cached != null) {
      return cached;
    }

    List<TeamWorkloadEntry> result = computeTeamWorkload(memberId, orgRole, from, to);
    orgCache.put(cacheKey, result);
    return result;
  }

  /**
   * Returns recent cross-project activity events. Admin/owner sees all events; regular members see
   * only events from projects they belong to. Results are cached in the org cache.
   *
   * @param tenantId the tenant schema for cache key isolation
   * @param memberId the requesting member's ID for project access filtering
   * @param orgRole the caller's org role for visibility
   * @param limit maximum number of events to return
   * @return list of activity items with actor and project names
   */
  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public List<CrossProjectActivityItem> getCrossProjectActivity(
      String tenantId, UUID memberId, String orgRole, int limit) {
    String cacheKey = tenantId + ":activity:" + memberId + ":" + orgRole + ":" + limit;

    List<CrossProjectActivityItem> cached =
        (List<CrossProjectActivityItem>) orgCache.getIfPresent(cacheKey);
    if (cached != null) {
      return cached;
    }

    List<CrossProjectActivityItem> result = computeCrossProjectActivity(memberId, orgRole, limit);
    orgCache.put(cacheKey, result);
    return result;
  }

  // --- Private: project-scoped computation ---

  private ProjectHealthDetail computeProjectHealth(UUID projectId) {
    LocalDate today = LocalDate.now();

    // Task counts
    long totalTasks = taskRepository.countByProjectId(projectId);
    long doneTasks = taskRepository.countByProjectIdAndStatus(projectId, "DONE");
    long inProgressTasks = taskRepository.countByProjectIdAndStatus(projectId, "IN_PROGRESS");
    long todoTasks = taskRepository.countByProjectIdAndStatus(projectId, "OPEN");
    long overdueTasks = taskRepository.countOverdueByProjectId(projectId, today);

    double completionPercent = totalTasks > 0 ? (double) doneTasks / totalTasks * 100 : 0;

    // Budget data
    Double budgetConsumedPercent = null;
    int alertThresholdPct = ProjectHealthCalculator.DEFAULT_BUDGET_ALERT_THRESHOLD;
    Optional<ProjectBudget> budgetOpt = projectBudgetRepository.findByProjectId(projectId);
    if (budgetOpt.isPresent()) {
      ProjectBudget budget = budgetOpt.get();
      alertThresholdPct = budget.getAlertThresholdPct();
      BigDecimal budgetHours = budget.getBudgetHours();
      if (budgetHours != null && budgetHours.compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal consumed = timeEntryRepository.budgetHoursConsumed(projectId).getHoursConsumed();
        budgetConsumedPercent =
            consumed
                .multiply(BigDecimal.valueOf(100))
                .divide(budgetHours, 2, RoundingMode.HALF_UP)
                .doubleValue();
      }
    }

    // Last activity
    int daysSinceLastActivity = computeDaysSinceLastActivity(projectId);

    // Hours this period (all-time for now)
    double hoursThisPeriod =
        timeEntryRepository.projectTimeSummary(projectId, null, null).getTotalMinutes() / 60.0;

    // Build health input and calculate
    var input =
        new ProjectHealthInput(
            (int) totalTasks,
            (int) doneTasks,
            (int) overdueTasks,
            budgetConsumedPercent,
            alertThresholdPct,
            completionPercent,
            daysSinceLastActivity);

    ProjectHealthResult healthResult = ProjectHealthCalculator.calculate(input);

    var metrics =
        new ProjectHealthMetrics(
            (int) doneTasks,
            (int) inProgressTasks,
            (int) todoTasks,
            (int) overdueTasks,
            (int) totalTasks,
            completionPercent,
            budgetConsumedPercent,
            hoursThisPeriod,
            daysSinceLastActivity);

    return new ProjectHealthDetail(healthResult.status(), healthResult.reasons(), metrics);
  }

  private TaskSummary computeTaskSummary(UUID projectId) {
    var rows = taskRepository.getTaskSummaryByProjectId(projectId, LocalDate.now());
    if (rows.isEmpty()) {
      return new TaskSummary(0, 0, 0, 0, 0, 0);
    }
    Object[] row = rows.getFirst();
    return new TaskSummary(
        ((Number) row[0]).intValue(),
        ((Number) row[1]).intValue(),
        ((Number) row[2]).intValue(),
        ((Number) row[3]).intValue(),
        ((Number) row[4]).intValue(),
        ((Number) row[5]).intValue());
  }

  // --- Private: org-level computation ---

  private KpiValues computeKpiValues(LocalDate from, LocalDate to) {
    long activeCount = projectRepository.countActiveProjects();

    var hoursSummary = timeEntryRepository.findOrgHoursSummary(from, to);
    double totalHours = hoursSummary.getTotalMinutes() / 60.0;
    long billableMinutes = hoursSummary.getBillableMinutes();
    long totalMinutes = hoursSummary.getTotalMinutes();
    Double billablePercent =
        totalMinutes > 0 ? (double) billableMinutes / totalMinutes * 100 : null;

    long overdueCount = taskRepository.countOrgOverdue(LocalDate.now());

    // averageMarginPercent: null until Phase 8 rate snapshots are available
    Double averageMarginPercent = null;

    return new KpiValues(
        (int) activeCount, totalHours, billablePercent, (int) overdueCount, averageMarginPercent);
  }

  private List<TrendPoint> computeTrend(LocalDate from, LocalDate to) {
    long daysBetween = ChronoUnit.DAYS.between(from, to);

    String granularity;
    String format;
    if (daysBetween <= 7) {
      granularity = "day";
      format = "YYYY-MM-DD";
    } else if (daysBetween <= 90) {
      granularity = "week";
      format = "IYYY-\"W\"IW";
    } else {
      granularity = "month";
      format = "YYYY-MM";
    }

    // Query the last 6 periods leading up to 'to'
    LocalDate trendFrom = computeTrendStart(to, granularity, 6);
    var results = timeEntryRepository.findHoursTrend(trendFrom, to, granularity, format);

    return results.stream()
        .map(r -> new TrendPoint(r.getPeriod(), r.getTotalMinutes() / 60.0))
        .toList();
  }

  private LocalDate computeTrendStart(LocalDate to, String granularity, int periods) {
    return switch (granularity) {
      case "day" -> to.minusDays(periods);
      case "week" -> to.minusWeeks(periods);
      case "month" -> to.minusMonths(periods);
      default -> to.minusDays(periods);
    };
  }

  private KpiValues computePreviousPeriod(LocalDate from, LocalDate to) {
    long daysBetween = ChronoUnit.DAYS.between(from, to) + 1;
    LocalDate prevFrom = from.minusDays(daysBetween);
    LocalDate prevTo = from.minusDays(1);

    return computeKpiValues(prevFrom, prevTo);
  }

  private List<ProjectHealth> computeProjectHealthList(UUID memberId, String orgRole) {
    // Get accessible projects based on role
    List<ProjectWithRole> projectsWithRoles;
    if (isAdminOrOwner(orgRole)) {
      projectsWithRoles = projectRepository.findAllProjectsWithRole(memberId);
    } else {
      projectsWithRoles = projectRepository.findProjectsForMember(memberId);
    }

    LocalDate today = LocalDate.now();

    return projectsWithRoles.stream()
        .map(
            projectWithRole -> {
              var project = projectWithRole.project();
              UUID projectId = project.getId();

              long totalTasks = taskRepository.countByProjectId(projectId);
              long doneTasks = taskRepository.countByProjectIdAndStatus(projectId, "DONE");
              long overdueTasks = taskRepository.countOverdueByProjectId(projectId, today);
              double completionPercent = totalTasks > 0 ? (double) doneTasks / totalTasks * 100 : 0;

              // Budget data
              Double budgetConsumedPercent = null;
              int alertThresholdPct = ProjectHealthCalculator.DEFAULT_BUDGET_ALERT_THRESHOLD;
              var budgetOpt = projectBudgetRepository.findByProjectId(projectId);
              if (budgetOpt.isPresent()) {
                var budget = budgetOpt.get();
                alertThresholdPct = budget.getAlertThresholdPct();
                var budgetHours = budget.getBudgetHours();
                if (budgetHours != null && budgetHours.compareTo(BigDecimal.ZERO) > 0) {
                  var consumed =
                      timeEntryRepository.budgetHoursConsumed(projectId).getHoursConsumed();
                  budgetConsumedPercent =
                      consumed
                          .multiply(BigDecimal.valueOf(100))
                          .divide(budgetHours, 2, RoundingMode.HALF_UP)
                          .doubleValue();
                }
              }

              int daysSinceLastActivity = computeDaysSinceLastActivity(projectId);

              // Hours logged (all-time for the list view)
              double hoursLogged =
                  timeEntryRepository.projectTimeSummary(projectId, null, null).getTotalMinutes()
                      / 60.0;

              // Calculate health
              var input =
                  new ProjectHealthInput(
                      (int) totalTasks,
                      (int) doneTasks,
                      (int) overdueTasks,
                      budgetConsumedPercent,
                      alertThresholdPct,
                      completionPercent,
                      daysSinceLastActivity);
              var healthResult = ProjectHealthCalculator.calculate(input);

              // Look up customer name
              String customerName = lookupCustomerName(projectId);

              return new ProjectHealth(
                  projectId,
                  project.getName(),
                  customerName,
                  healthResult.status(),
                  healthResult.reasons(),
                  (int) doneTasks,
                  (int) totalTasks,
                  completionPercent,
                  budgetConsumedPercent,
                  hoursLogged);
            })
        .sorted(
            Comparator.comparingInt((ProjectHealth ph) -> ph.healthStatus().severity())
                .reversed()
                .thenComparingDouble(ProjectHealth::completionPercent))
        .toList();
  }

  private List<TeamWorkloadEntry> computeTeamWorkload(
      UUID memberId, String orgRole, LocalDate from, LocalDate to) {
    List<TeamWorkloadProjection> rows = timeEntryRepository.findTeamWorkload(from, to);

    // Group flat rows by member
    Map<UUID, List<TeamWorkloadProjection>> byMember = new LinkedHashMap<>();
    for (var row : rows) {
      byMember.computeIfAbsent(row.getMemberId(), k -> new ArrayList<>()).add(row);
    }

    List<TeamWorkloadEntry> entries = new ArrayList<>();
    for (var entry : byMember.entrySet()) {
      UUID entryMemberId = entry.getKey();
      List<TeamWorkloadProjection> memberRows = entry.getValue();

      // For regular members, skip entries that are not their own
      if (!isAdminOrOwner(orgRole) && !entryMemberId.equals(memberId)) {
        continue;
      }

      String memberName = memberRows.getFirst().getMemberName();

      // Sum total and billable minutes across all projects
      long totalMinutes = 0;
      long billableMinutes = 0;
      for (var row : memberRows) {
        totalMinutes += row.getTotalMinutes();
        billableMinutes += row.getBillableMinutes();
      }

      // Sort projects by hours DESC, cap at 5, aggregate rest as "Other"
      memberRows.sort(Comparator.comparingLong(TeamWorkloadProjection::getTotalMinutes).reversed());

      List<ProjectHoursEntry> projects = new ArrayList<>();
      long otherMinutes = 0;
      for (int i = 0; i < memberRows.size(); i++) {
        var row = memberRows.get(i);
        if (i < 5) {
          projects.add(
              new ProjectHoursEntry(
                  row.getProjectId(), row.getProjectName(), row.getTotalMinutes() / 60.0));
        } else {
          otherMinutes += row.getTotalMinutes();
        }
      }
      if (otherMinutes > 0) {
        projects.add(new ProjectHoursEntry(null, "Other", otherMinutes / 60.0));
      }

      entries.add(
          new TeamWorkloadEntry(
              entryMemberId, memberName, totalMinutes / 60.0, billableMinutes / 60.0, projects));
    }

    return entries;
  }

  private List<CrossProjectActivityItem> computeCrossProjectActivity(
      UUID memberId, String orgRole, int limit) {
    List<CrossProjectActivityProjection> rows;
    if (isAdminOrOwner(orgRole)) {
      rows = auditEventRepository.findCrossProjectActivity(limit);
    } else {
      rows = auditEventRepository.findCrossProjectActivityForMember(memberId, limit);
    }

    return rows.stream()
        .map(
            row ->
                new CrossProjectActivityItem(
                    row.getEventId(),
                    row.getEventType(),
                    buildActivityDescription(row.getEventType(), row.getActorName()),
                    row.getActorName(),
                    row.getProjectId(),
                    row.getProjectName(),
                    row.getOccurredAt()))
        .toList();
  }

  private String buildActivityDescription(String eventType, String actorName) {
    String actor = actorName != null ? actorName : "System";
    if (eventType == null) {
      return actor + " performed an action";
    }
    String[] parts = eventType.split("\\.");
    if (parts.length < 2) {
      return actor + " performed " + eventType;
    }
    String entity = parts[0];
    String action = parts[1];
    return switch (action) {
      case "created" -> actor + " created a " + entity;
      case "updated" -> actor + " updated a " + entity;
      case "deleted" -> actor + " deleted a " + entity;
      case "uploaded" -> actor + " uploaded a " + entity;
      default -> actor + " performed " + eventType;
    };
  }

  private String lookupCustomerName(UUID projectId) {
    return customerProjectRepository
        .findFirstCustomerByProjectId(projectId)
        .flatMap(customerRepository::findOneById)
        .map(c -> c.getName())
        .orElse(null);
  }

  // --- Shared helpers ---

  private boolean isAdminOrOwner(String orgRole) {
    return Roles.ORG_ADMIN.equals(orgRole) || Roles.ORG_OWNER.equals(orgRole);
  }

  private int computeDaysSinceLastActivity(UUID projectId) {
    Optional<Instant> lastActivity = auditEventRepository.findMostRecentByProject(projectId);
    if (lastActivity.isEmpty()) {
      return Integer.MAX_VALUE;
    }
    return (int) Duration.between(lastActivity.get(), Instant.now()).toDays();
  }
}
