package io.b2mash.b2b.b2bstrawman.dashboard;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudget;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudgetRepository;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.ProjectHealthDetail;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.ProjectHealthMetrics;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TaskSummary;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for computing project dashboard data including health scoring and task summaries. Uses a
 * Caffeine cache with 1-minute TTL for project-scoped data.
 */
@Service
public class DashboardService {

  private final TaskRepository taskRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final AuditEventRepository auditEventRepository;
  private final ProjectBudgetRepository projectBudgetRepository;

  private final Cache<String, Object> projectCache =
      Caffeine.newBuilder().maximumSize(5_000).expireAfterWrite(Duration.ofMinutes(1)).build();

  public DashboardService(
      TaskRepository taskRepository,
      TimeEntryRepository timeEntryRepository,
      AuditEventRepository auditEventRepository,
      ProjectBudgetRepository projectBudgetRepository) {
    this.taskRepository = taskRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.auditEventRepository = auditEventRepository;
    this.projectBudgetRepository = projectBudgetRepository;
  }

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
    Object[] row = rows.getFirst();
    return new TaskSummary(
        ((Number) row[0]).intValue(),
        ((Number) row[1]).intValue(),
        ((Number) row[2]).intValue(),
        ((Number) row[3]).intValue(),
        ((Number) row[4]).intValue(),
        ((Number) row[5]).intValue());
  }

  private int computeDaysSinceLastActivity(UUID projectId) {
    Optional<Instant> lastActivity = auditEventRepository.findMostRecentByProject(projectId);
    if (lastActivity.isEmpty()) {
      return 0;
    }
    return (int) Duration.between(lastActivity.get(), Instant.now()).toDays();
  }
}
