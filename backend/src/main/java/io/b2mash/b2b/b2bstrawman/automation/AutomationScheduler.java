package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import java.time.Instant;
import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Polls for due delayed action executions every 15 minutes. For each tenant schema, queries for
 * SCHEDULED action executions with a scheduledFor timestamp in the past, validates the parent rule
 * is still enabled, deserializes the stored context, and delegates execution to the appropriate
 * action executor.
 */
@Component
public class AutomationScheduler {

  private static final Logger log = LoggerFactory.getLogger(AutomationScheduler.class);
  private static final long POLL_INTERVAL_MS = 900_000; // 15 minutes
  private static final long CRON_POLL_INTERVAL_MS = 60_000; // 60 seconds

  private final ActionExecutionRepository actionExecutionRepository;
  private final AutomationExecutionRepository executionRepository;
  private final AutomationRuleRepository ruleRepository;
  private final AutomationActionRepository actionRepository;
  private final AutomationActionExecutor automationActionExecutor;
  private final AutomationPollTriggersHandler pollTriggersHandler;
  private final TransactionTemplate transactionTemplate;
  private final ObjectMapper objectMapper;
  private final JobEnqueuer jobEnqueuer;

  public AutomationScheduler(
      ActionExecutionRepository actionExecutionRepository,
      AutomationExecutionRepository executionRepository,
      AutomationRuleRepository ruleRepository,
      AutomationActionRepository actionRepository,
      AutomationActionExecutor automationActionExecutor,
      AutomationPollTriggersHandler pollTriggersHandler,
      TransactionTemplate transactionTemplate,
      ObjectMapper objectMapper,
      JobEnqueuer jobEnqueuer) {
    this.actionExecutionRepository = actionExecutionRepository;
    this.executionRepository = executionRepository;
    this.ruleRepository = ruleRepository;
    this.actionRepository = actionRepository;
    this.automationActionExecutor = automationActionExecutor;
    this.pollTriggersHandler = pollTriggersHandler;
    this.transactionTemplate = transactionTemplate;
    this.objectMapper = objectMapper;
    this.jobEnqueuer = jobEnqueuer;
  }

  @SchedulerLock(name = "automation_poll_delayed_actions", lockAtLeastFor = "7m")
  @Scheduled(fixedDelay = POLL_INTERVAL_MS)
  public void pollDelayedActions() {
    log.debug("Automation scheduler started");
    jobEnqueuer.fanOutToAllTenants("automation_poll_delayed", null);
  }

  /**
   * Cron pass: fires SCHEDULED trigger rules whose next fire time has arrived. Runs every 60s,
   * per-tenant. Missed-run policy: fire-once-on-resume, no flood-backfill (ADR-271).
   */
  @SchedulerLock(name = "automation_poll_scheduled_triggers", lockAtLeastFor = "30s")
  @Scheduled(fixedDelay = CRON_POLL_INTERVAL_MS)
  public void pollScheduledTriggers() {
    log.debug("Scheduled trigger cron pass started");
    jobEnqueuer.fanOutToAllTenants("automation_poll_triggers", null);
  }

  /**
   * Delegates the per-tenant scheduled-trigger cron pass to {@link AutomationPollTriggersHandler},
   * which is the single source of truth (OBS-505). Previously this class carried a duplicate copy
   * of the decision/fire logic that was never reached in production — the live path is {@link
   * #pollScheduledTriggers()} → {@code fanOutToAllTenants("automation_poll_triggers")} → {@code
   * AutomationPollTriggersHandler}. The duplicate has been collapsed so the cron poll and the
   * direct test entry point exercise identical code, including the per-active-project AI-summary
   * fan-out.
   */
  int processScheduledTenant() {
    return pollTriggersHandler.processScheduledTenant();
  }

  int processTenant() {
    Integer processed =
        transactionTemplate.execute(
            tx -> {
              var dueActions =
                  actionExecutionRepository.findDueScheduledForUpdate(
                      ActionExecutionStatus.SCHEDULED.name(), Instant.now());

              if (dueActions == null || dueActions.isEmpty()) {
                return 0;
              }

              int count = 0;
              for (var actionExecution : dueActions) {
                try {
                  executeDelayedAction(actionExecution);
                  count++;
                } catch (Exception e) {
                  log.error(
                      "Failed to execute delayed action execution {}: {}",
                      actionExecution.getId(),
                      e.getMessage(),
                      e);
                }
              }
              return count;
            });

    return processed != null ? processed : 0;
  }

  private void executeDelayedAction(ActionExecution actionExecution) {
    // Load parent automation execution
    var executionOpt = executionRepository.findById(actionExecution.getExecutionId());
    if (executionOpt.isEmpty()) {
      log.warn(
          "Parent execution {} not found for action execution {}, cancelling",
          actionExecution.getExecutionId(),
          actionExecution.getId());
      actionExecution.cancel();
      actionExecutionRepository.save(actionExecution);
      return;
    }

    var execution = executionOpt.get();

    // Load and validate the rule
    var ruleOpt = ruleRepository.findById(execution.getRuleId());
    if (ruleOpt.isEmpty()) {
      log.info(
          "Rule {} was deleted, cancelling delayed action execution {}",
          execution.getRuleId(),
          actionExecution.getId());
      actionExecution.cancel();
      actionExecutionRepository.save(actionExecution);
      return;
    }

    var rule = ruleOpt.get();
    if (!rule.isEnabled()) {
      log.info(
          "Rule {} ({}) is disabled, cancelling delayed action execution {}",
          rule.getId(),
          rule.getName(),
          actionExecution.getId());
      actionExecution.cancel();
      actionExecutionRepository.save(actionExecution);
      return;
    }

    // Load the action definition (action_id may be null if the action was deleted — ON DELETE SET
    // NULL)
    if (actionExecution.getActionId() == null) {
      log.warn(
          "Action ID is null for action execution {} (action was deleted), marking as failed",
          actionExecution.getId());
      actionExecution.fail("Action definition was deleted", null);
      actionExecutionRepository.save(actionExecution);
      return;
    }
    var actionOpt = actionRepository.findById(actionExecution.getActionId());
    if (actionOpt.isEmpty()) {
      log.warn(
          "Action {} not found for action execution {}, marking as failed",
          actionExecution.getActionId(),
          actionExecution.getId());
      actionExecution.fail("Action definition not found", null);
      actionExecutionRepository.save(actionExecution);
      return;
    }

    var action = actionOpt.get();

    // Deserialize the stored context
    Map<String, Map<String, Object>> context = deserializeContext(actionExecution);
    if (context == null) {
      actionExecution.fail("Failed to deserialize stored context", null);
      actionExecutionRepository.save(actionExecution);
      return;
    }

    // Execute the action using the existing executor infrastructure
    automationActionExecutor.executeScheduled(action, execution.getId(), context, actionExecution);

    log.info(
        "Executed delayed action {} (type {}) for rule {} ({})",
        actionExecution.getId(),
        action.getActionType(),
        rule.getId(),
        rule.getName());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Map<String, Object>> deserializeContext(ActionExecution actionExecution) {
    try {
      Map<String, Object> resultData = actionExecution.getResultData();
      if (resultData == null || !resultData.containsKey("_context")) {
        log.warn(
            "No _context found in resultData for action execution {}", actionExecution.getId());
        return null;
      }

      Object contextObj = resultData.get("_context");
      return objectMapper.convertValue(
          contextObj, new TypeReference<Map<String, Map<String, Object>>>() {});
    } catch (Exception e) {
      log.error(
          "Failed to deserialize context for action execution {}: {}",
          actionExecution.getId(),
          e.getMessage(),
          e);
      return null;
    }
  }
}
