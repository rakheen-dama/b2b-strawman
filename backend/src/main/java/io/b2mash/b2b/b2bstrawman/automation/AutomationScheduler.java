package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
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
  private final AutomationEventListener automationEventListener;
  private final TransactionTemplate transactionTemplate;
  private final ObjectMapper objectMapper;
  private final JobEnqueuer jobEnqueuer;

  public AutomationScheduler(
      ActionExecutionRepository actionExecutionRepository,
      AutomationExecutionRepository executionRepository,
      AutomationRuleRepository ruleRepository,
      AutomationActionRepository actionRepository,
      AutomationActionExecutor automationActionExecutor,
      AutomationEventListener automationEventListener,
      TransactionTemplate transactionTemplate,
      ObjectMapper objectMapper,
      JobEnqueuer jobEnqueuer) {
    this.actionExecutionRepository = actionExecutionRepository;
    this.executionRepository = executionRepository;
    this.ruleRepository = ruleRepository;
    this.actionRepository = actionRepository;
    this.automationActionExecutor = automationActionExecutor;
    this.automationEventListener = automationEventListener;
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

  int processScheduledTenant() {
    // Phase 1: short transaction to evaluate which rules should fire and update lastRunAt.
    // This prevents holding a long-running transaction open during LLM execution.
    record DueRule(AutomationRule rule, Instant now) {}
    List<DueRule> dueRules;
    {
      var result =
          transactionTemplate.execute(
              tx -> {
                var rules = ruleRepository.findByEnabledAndTriggerType(true, TriggerType.SCHEDULED);
                if (rules.isEmpty()) return List.<DueRule>of();

                Instant now = Instant.now();
                var due = new java.util.ArrayList<DueRule>();

                for (var rule : rules) {
                  try {
                    if (shouldFire(rule, now)) {
                      // Update lastRunAt inside the short tx to prevent re-firing
                      rule.setLastRunAt(now);
                      ruleRepository.save(rule);
                      due.add(new DueRule(rule, now));
                    }
                  } catch (Exception e) {
                    log.error(
                        "Failed to evaluate scheduled rule {} ({}): {}",
                        rule.getId(),
                        rule.getName(),
                        e.getMessage(),
                        e);
                  }
                }
                return due;
              });
      dueRules = result != null ? result : List.of();
    }

    // Phase 2: execute each due rule outside the transaction (LLM calls may be long-running)
    int fired = 0;
    for (var due : dueRules) {
      try {
        fireScheduledRule(due.rule(), due.now());
        fired++;
      } catch (Exception e) {
        log.error(
            "Failed to fire scheduled rule {} ({}): {}",
            due.rule().getId(),
            due.rule().getName(),
            e.getMessage(),
            e);
      }
    }
    return fired;
  }

  private boolean shouldFire(AutomationRule rule, Instant now) {
    Map<String, Object> triggerConfig = rule.getTriggerConfig();
    if (triggerConfig == null) return false;

    Object cronObj = triggerConfig.get("cronExpression");
    if (cronObj == null) return false;
    String cronExpr = cronObj.toString();

    try {
      var cron = CronExpression.parse(cronExpr);
      Instant baseTime = rule.getLastRunAt() != null ? rule.getLastRunAt() : rule.getCreatedAt();
      // Deliberate UTC-only cron evaluation: all cron expressions are stored and evaluated in UTC.
      // Per-tenant timezone support is deferred (ADR-271). If added later, the ZoneId should come
      // from the tenant's configured timezone and be applied here instead of ZoneOffset.UTC.
      var next = cron.next(baseTime.atZone(java.time.ZoneOffset.UTC).toLocalDateTime());
      if (next == null) return false;
      Instant nextFire = next.toInstant(java.time.ZoneOffset.UTC);
      return !nextFire.isAfter(now);
    } catch (IllegalArgumentException e) {
      log.warn(
          "Invalid cron expression '{}' for rule {} ({}): {}",
          cronExpr,
          rule.getId(),
          rule.getName(),
          e.getMessage());
      return false;
    }
  }

  private void fireScheduledRule(AutomationRule rule, Instant now) {
    // Build minimal context for the scheduled execution
    Map<String, Map<String, Object>> context = new LinkedHashMap<>();
    context.put(
        "rule",
        Map.of(
            "id", rule.getId().toString(),
            "name", rule.getName(),
            "createdBy", rule.getCreatedBy().toString()));

    // lastRunAt was already updated in the short decision-transaction (processScheduledTenant
    // phase 1) to prevent re-firing. The actual execution happens outside any transaction so
    // that long-running LLM calls do not hold the connection open.
    automationEventListener.fireRule(rule, context);

    log.info("Fired scheduled rule {} ({}) at {}", rule.getId(), rule.getName(), now);
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
