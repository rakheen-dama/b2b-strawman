package io.b2mash.b2b.b2bstrawman.budget;

import io.b2mash.b2b.b2bstrawman.event.BudgetThresholdEvent;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Checks budget thresholds after time entry mutations and publishes {@link BudgetThresholdEvent}
 * when a threshold is crossed. Designed to be called within the same transaction as the time entry
 * save, so events are only published if the transaction commits.
 */
@Service
public class BudgetCheckService {

  private static final Logger log = LoggerFactory.getLogger(BudgetCheckService.class);

  private final ProjectBudgetRepository projectBudgetRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final ProjectRepository projectRepository;

  public BudgetCheckService(
      ProjectBudgetRepository projectBudgetRepository,
      TimeEntryRepository timeEntryRepository,
      ApplicationEventPublisher applicationEventPublisher,
      ProjectRepository projectRepository) {
    this.projectBudgetRepository = projectBudgetRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.applicationEventPublisher = applicationEventPublisher;
    this.projectRepository = projectRepository;
  }

  /**
   * Checks whether the project's budget threshold has been crossed and publishes a {@link
   * BudgetThresholdEvent} if so. Returns silently if no budget exists or if the threshold was
   * already notified.
   */
  public void checkAndAlert(
      UUID projectId, UUID actorMemberId, String actorName, String tenantId, String orgId) {

    var budgetOpt = projectBudgetRepository.findByProjectId(projectId);
    if (budgetOpt.isEmpty()) {
      return;
    }

    var budget = budgetOpt.get();

    if (budget.isThresholdNotified()) {
      return;
    }

    int threshold = budget.getAlertThresholdPct();

    // Check hours dimension
    BigDecimal hoursConsumedPct = null;
    if (budget.getBudgetHours() != null && budget.getBudgetHours().signum() > 0) {
      var hoursProjection = timeEntryRepository.budgetHoursConsumed(projectId);
      var hoursConsumed = hoursProjection.getHoursConsumed();
      hoursConsumedPct =
          BudgetStatus.compute(budget, hoursConsumed, BigDecimal.ZERO).hoursConsumedPct();
    }

    // Check amount dimension
    BigDecimal amountConsumedPct = null;
    if (budget.getBudgetAmount() != null
        && budget.getBudgetAmount().signum() > 0
        && budget.getBudgetCurrency() != null) {
      var amountProjection =
          timeEntryRepository.budgetAmountConsumed(projectId, budget.getBudgetCurrency());
      var amountConsumed = amountProjection.getAmountConsumed();
      amountConsumedPct =
          BudgetStatus.compute(budget, BigDecimal.ZERO, amountConsumed).amountConsumedPct();
    }

    // Check if either dimension crossed the threshold
    boolean hoursCrossed =
        hoursConsumedPct != null
            && BudgetStatus.BudgetStatusEnum.fromPct(hoursConsumedPct, threshold)
                != BudgetStatus.BudgetStatusEnum.ON_TRACK;
    boolean amountCrossed =
        amountConsumedPct != null
            && BudgetStatus.BudgetStatusEnum.fromPct(amountConsumedPct, threshold)
                != BudgetStatus.BudgetStatusEnum.ON_TRACK;

    if (!hoursCrossed && !amountCrossed) {
      return;
    }

    // Determine which dimension triggered (pick the higher percentage)
    String dimension;
    BigDecimal consumedPct;
    if (hoursCrossed && amountCrossed) {
      if (hoursConsumedPct.compareTo(amountConsumedPct) >= 0) {
        dimension = "hours";
        consumedPct = hoursConsumedPct;
      } else {
        dimension = "amount";
        consumedPct = amountConsumedPct;
      }
    } else if (hoursCrossed) {
      dimension = "hours";
      consumedPct = hoursConsumedPct;
    } else {
      dimension = "amount";
      consumedPct = amountConsumedPct;
    }

    // Mark as notified and save — use optimistic locking to prevent duplicate notifications
    try {
      budget.markThresholdNotified();
      projectBudgetRepository.save(budget);
    } catch (ObjectOptimisticLockingFailureException e) {
      // Another concurrent thread already marked as notified — safe to ignore
      log.debug("Budget threshold already notified by another thread: project={}", projectId);
      return;
    }

    // Look up project name
    var projectName =
        projectRepository.findById(projectId).map(p -> p.getName()).orElse("Unknown Project");

    var details = new LinkedHashMap<String, Object>();
    details.put("project_name", projectName);
    details.put("dimension", dimension);
    details.put("consumed_pct", consumedPct);

    var event =
        new BudgetThresholdEvent(
            "budget.threshold_reached",
            "project_budget",
            budget.getId(),
            projectId,
            actorMemberId,
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            details);

    applicationEventPublisher.publishEvent(event);
    log.info(
        "Budget threshold alert: project={}, dimension={}, consumed={}%",
        projectId, dimension, consumedPct);
  }
}
