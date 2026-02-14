package io.b2mash.b2b.b2bstrawman.budget;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectBudgetService {

  private static final Logger log = LoggerFactory.getLogger(ProjectBudgetService.class);

  private final ProjectBudgetRepository projectBudgetRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final ProjectAccessService projectAccessService;
  private final AuditService auditService;

  public ProjectBudgetService(
      ProjectBudgetRepository projectBudgetRepository,
      TimeEntryRepository timeEntryRepository,
      ProjectAccessService projectAccessService,
      AuditService auditService) {
    this.projectBudgetRepository = projectBudgetRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.projectAccessService = projectAccessService;
    this.auditService = auditService;
  }

  /**
   * Returns the budget with full computed status for a project. Includes hours/amount consumed,
   * remaining, percentages, and status enums.
   *
   * @throws ResourceNotFoundException if no budget is set for the project or project not accessible
   */
  @Transactional(readOnly = true)
  public BudgetStatus getBudgetWithStatus(UUID projectId, UUID memberId, String orgRole) {
    projectAccessService.requireViewAccess(projectId, memberId, orgRole);

    var budget =
        projectBudgetRepository
            .findByProjectId(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("ProjectBudget", projectId));

    return computeStatus(budget);
  }

  /**
   * Returns a lightweight budget status for dashboard use. Same computation but callers typically
   * only need the percentage and status fields.
   *
   * @throws ResourceNotFoundException if no budget is set for the project or project not accessible
   */
  @Transactional(readOnly = true)
  public BudgetStatus getBudgetStatusOnly(UUID projectId, UUID memberId, String orgRole) {
    // Same computation — the controller will map to a lighter response shape
    return getBudgetWithStatus(projectId, memberId, orgRole);
  }

  /**
   * Creates or updates the budget for a project. Resets thresholdNotified when budget values
   * change. Requires edit access (lead/admin/owner).
   */
  @Transactional
  public BudgetStatus upsertBudget(
      UUID projectId,
      BigDecimal budgetHours,
      BigDecimal budgetAmount,
      String budgetCurrency,
      Integer alertThresholdPct,
      String notes,
      UUID memberId,
      String orgRole) {
    projectAccessService.requireEditAccess(projectId, memberId, orgRole);

    // Validation
    if (budgetHours == null && budgetAmount == null) {
      throw new InvalidStateException(
          "Invalid budget", "At least one of budgetHours or budgetAmount must be provided");
    }
    if (budgetAmount != null && (budgetCurrency == null || budgetCurrency.isBlank())) {
      throw new InvalidStateException(
          "Invalid budget", "budgetCurrency is required when budgetAmount is set");
    }

    int threshold = (alertThresholdPct != null) ? alertThresholdPct : 80;
    if (threshold < 50 || threshold > 100) {
      throw new InvalidStateException(
          "Invalid budget", "alertThresholdPct must be between 50 and 100");
    }

    var existing = projectBudgetRepository.findByProjectId(projectId);

    ProjectBudget budget;
    String eventType;
    if (existing.isPresent()) {
      budget = existing.get();
      budget.updateBudget(budgetHours, budgetAmount, budgetCurrency, threshold, notes);
      eventType = "budget.updated";
    } else {
      budget =
          new ProjectBudget(projectId, budgetHours, budgetAmount, budgetCurrency, threshold, notes);
      eventType = "budget.created";
    }

    budget = projectBudgetRepository.save(budget);
    log.info("{} budget {} for project {}", eventType, budget.getId(), projectId);

    var details = new LinkedHashMap<String, Object>();
    details.put("project_id", projectId.toString());
    if (budgetHours != null) {
      details.put("budget_hours", budgetHours.toString());
    }
    if (budgetAmount != null) {
      details.put("budget_amount", budgetAmount.toString());
      details.put("budget_currency", budgetCurrency);
    }
    details.put("alert_threshold_pct", threshold);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType(eventType)
            .entityType("project_budget")
            .entityId(budget.getId())
            .details(details)
            .build());

    return computeStatus(budget);
  }

  /**
   * Deletes the budget for a project. Requires edit access (lead/admin/owner).
   *
   * @throws ResourceNotFoundException if no budget is set for the project
   */
  @Transactional
  public void deleteBudget(UUID projectId, UUID memberId, String orgRole) {
    projectAccessService.requireEditAccess(projectId, memberId, orgRole);

    var budget =
        projectBudgetRepository
            .findByProjectId(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("ProjectBudget", projectId));

    projectBudgetRepository.delete(budget);
    log.info("Deleted budget {} for project {}", budget.getId(), projectId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("budget.deleted")
            .entityType("project_budget")
            .entityId(budget.getId())
            .details(Map.of("project_id", projectId.toString()))
            .build());
  }

  private BudgetStatus computeStatus(ProjectBudget budget) {
    BigDecimal hoursConsumed =
        timeEntryRepository.budgetHoursConsumed(budget.getProjectId()).getHoursConsumed();

    BigDecimal amountConsumed = BigDecimal.ZERO;
    if (budget.getBudgetAmount() != null && budget.getBudgetCurrency() != null) {
      amountConsumed =
          timeEntryRepository
              .budgetAmountConsumed(budget.getProjectId(), budget.getBudgetCurrency())
              .getAmountConsumed();
    }

    return BudgetStatus.compute(budget, hoursConsumed, amountConsumed);
  }
}
