package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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
  private final TriggerConfigMatcher triggerConfigMatcher;

  public AutomationEventListener(
      AutomationRuleRepository ruleRepository,
      AutomationExecutionRepository executionRepository,
      TriggerConfigMatcher triggerConfigMatcher) {
    this.ruleRepository = ruleRepository;
    this.executionRepository = executionRepository;
    this.triggerConfigMatcher = triggerConfigMatcher;
  }

  @EventListener
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
      log.info(
          "Skipping automation evaluation -- event originated from execution {}",
          event.details().get("automationExecutionId"));
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

    // Placeholder condition evaluation: conditions always met for now (281B will implement)
    boolean conditionsMet = true;

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

    // Placeholder: action execution will be wired in Epic 282A
    if (conditionsMet) {
      log.debug(
          "Conditions met for rule {} — action execution deferred to Epic 282A", rule.getId());
    }
  }

  private boolean isCycleDetected(DomainEvent event) {
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
