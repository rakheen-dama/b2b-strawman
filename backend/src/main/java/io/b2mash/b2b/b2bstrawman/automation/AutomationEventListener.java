package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.automation.config.ActionFailure;
import io.b2mash.b2b.b2bstrawman.event.DomainEvent;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central dispatch point for the automation engine. Subscribes to all {@link DomainEvent} instances
 * via Spring's {@link EventListener} and routes them through trigger matching, condition
 * evaluation, and action execution.
 *
 * <p>This listener runs within the same transaction as the event publisher. All processing is
 * wrapped in try-catch to prevent automation failures from rolling back the triggering transaction.
 */
@Component
public class AutomationEventListener {

  private static final Logger log = LoggerFactory.getLogger(AutomationEventListener.class);

  private final AutomationRuleRepository ruleRepository;
  private final AutomationExecutionRepository executionRepository;
  private final AutomationActionRepository actionRepository;
  private final TriggerConfigMatcher triggerConfigMatcher;
  private final ConditionEvaluator conditionEvaluator;
  private final AutomationActionExecutor automationActionExecutor;
  private final NotificationService notificationService;

  public AutomationEventListener(
      AutomationRuleRepository ruleRepository,
      AutomationExecutionRepository executionRepository,
      AutomationActionRepository actionRepository,
      TriggerConfigMatcher triggerConfigMatcher,
      ConditionEvaluator conditionEvaluator,
      AutomationActionExecutor automationActionExecutor,
      NotificationService notificationService) {
    this.ruleRepository = ruleRepository;
    this.executionRepository = executionRepository;
    this.actionRepository = actionRepository;
    this.triggerConfigMatcher = triggerConfigMatcher;
    this.conditionEvaluator = conditionEvaluator;
    this.automationActionExecutor = automationActionExecutor;
    this.notificationService = notificationService;
  }

  @EventListener
  @Transactional
  public void onDomainEvent(DomainEvent event) {
    try {
      processEvent(event);
    } catch (Exception e) {
      log.error(
          "Automation event listener failed for event type {}: {}",
          event.getClass().getSimpleName(),
          e.getMessage(),
          e);
    }
  }

  private void processEvent(DomainEvent event) {
    // Step 1: Map event to trigger type
    TriggerType triggerType = TriggerTypeMapping.getTriggerType(event);
    if (triggerType == null) {
      log.debug(
          "No trigger type mapping for event class {}, skipping", event.getClass().getSimpleName());
      return;
    }

    // Step 2: Cycle detection placeholder — skip if event originated from automation
    if (isCycleDetected(event)) {
      Object execId =
          event.automationExecutionId() != null
              ? event.automationExecutionId()
              : (event.details() != null ? event.details().get("automationExecutionId") : null);
      log.info("Skipping automation evaluation -- event originated from execution {}", execId);
      return;
    }

    // Step 3: Query enabled rules for this trigger type
    var rules = ruleRepository.findByEnabledAndTriggerType(true, triggerType);
    if (rules.isEmpty()) {
      log.debug("No enabled rules for trigger type {}", triggerType);
      return;
    }

    log.debug(
        "Evaluating {} rule(s) for trigger type {} from event {}",
        rules.size(),
        triggerType,
        event.getClass().getSimpleName());

    // Step 4: Evaluate each rule independently
    for (var rule : rules) {
      try {
        evaluateRule(rule, event);
      } catch (Exception e) {
        log.error(
            "Failed to evaluate automation rule {} ({}): {}",
            rule.getId(),
            rule.getName(),
            e.getMessage(),
            e);
      }
    }
  }

  private void evaluateRule(AutomationRule rule, DomainEvent event) {
    // Validate trigger config against event data
    if (!triggerConfigMatcher.matches(rule, event)) {
      log.debug("Trigger config does not match for rule {} ({})", rule.getId(), rule.getName());
      return;
    }

    // Build event data snapshot
    Map<String, Object> triggerEventData = buildEventSnapshot(event);
    String triggerEventType = event.getClass().getSimpleName();

    // Build context and evaluate conditions
    TriggerType triggerType = rule.getTriggerType();
    Map<String, Map<String, Object>> context = AutomationContext.build(triggerType, event, rule);
    boolean conditionsMet = conditionEvaluator.evaluate(rule.getConditions(), context);

    // Create execution record
    ExecutionStatus status =
        conditionsMet ? ExecutionStatus.TRIGGERED : ExecutionStatus.CONDITIONS_NOT_MET;

    var execution =
        new AutomationExecution(
            rule.getId(), triggerEventType, triggerEventData, conditionsMet, status);
    executionRepository.save(execution);

    log.info(
        "Created automation execution for rule {} ({}) with status {}",
        rule.getId(),
        rule.getName(),
        status);

    if (conditionsMet) {
      executeActions(rule, execution, context);
    }
  }

  private void executeActions(
      AutomationRule rule,
      AutomationExecution execution,
      Map<String, Map<String, Object>> context) {
    List<AutomationAction> actions = actionRepository.findByRuleIdOrderBySortOrder(rule.getId());
    if (actions.isEmpty()) {
      log.debug("No actions configured for rule {} ({})", rule.getId(), rule.getName());
      execution.complete();
      executionRepository.save(execution);
      return;
    }

    boolean allSucceeded = true;
    List<String> failureMessages = new ArrayList<>();

    for (var action : actions) {
      var result = automationActionExecutor.execute(action, execution.getId(), context);
      if (!result.isSuccess()) {
        allSucceeded = false;
        var failure = (ActionFailure) result;
        failureMessages.add(action.getActionType() + ": " + failure.errorMessage());
        log.warn(
            "Action {} (type {}) failed for rule {} ({}): {}",
            action.getId(),
            action.getActionType(),
            rule.getId(),
            rule.getName(),
            failure.errorMessage());
        // Continue executing subsequent actions — no short-circuit
      }
    }

    if (allSucceeded) {
      execution.complete();
    } else {
      execution.fail(String.join("; ", failureMessages));
      sendFailureNotification(rule, failureMessages);
    }
    executionRepository.save(execution);
  }

  private void sendFailureNotification(AutomationRule rule, List<String> failureMessages) {
    try {
      String title = "Automation action failed: " + rule.getName();
      String body = "The following action(s) failed: " + String.join("; ", failureMessages);

      notificationService.notifyAdminsAndOwners(
          "AUTOMATION_ACTION_FAILED", title, body, "AutomationRule", rule.getId());

      log.info(
          "Sent AUTOMATION_ACTION_FAILED notification to admins/owners for rule {} ({})",
          rule.getId(),
          rule.getName());
    } catch (Exception e) {
      log.error(
          "Failed to send automation failure notification for rule {} ({}): {}",
          rule.getId(),
          rule.getName(),
          e.getMessage(),
          e);
    }
  }

  private boolean isCycleDetected(DomainEvent event) {
    // Primary check: use the DomainEvent interface method (ADR-146)
    if (event.automationExecutionId() != null) {
      return true;
    }
    // Backward compatibility: check details map for legacy events
    Map<String, Object> details = event.details();
    if (details == null) {
      return false;
    }
    return details.get("automationExecutionId") != null;
  }

  private Map<String, Object> buildEventSnapshot(DomainEvent event) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("eventType", event.eventType());
    snapshot.put("entityType", event.entityType());
    snapshot.put("entityId", event.entityId() != null ? event.entityId().toString() : null);
    snapshot.put("projectId", event.projectId() != null ? event.projectId().toString() : null);
    snapshot.put("actorName", event.actorName());
    snapshot.put("occurredAt", event.occurredAt().toString());
    snapshot.put("details", event.details());
    return snapshot;
  }
}
