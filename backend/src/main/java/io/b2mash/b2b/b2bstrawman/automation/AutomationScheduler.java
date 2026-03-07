package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.Map;
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

  private final OrgSchemaMappingRepository mappingRepository;
  private final ActionExecutionRepository actionExecutionRepository;
  private final AutomationExecutionRepository executionRepository;
  private final AutomationRuleRepository ruleRepository;
  private final AutomationActionRepository actionRepository;
  private final AutomationActionExecutor automationActionExecutor;
  private final TransactionTemplate transactionTemplate;
  private final ObjectMapper objectMapper;

  public AutomationScheduler(
      OrgSchemaMappingRepository mappingRepository,
      ActionExecutionRepository actionExecutionRepository,
      AutomationExecutionRepository executionRepository,
      AutomationRuleRepository ruleRepository,
      AutomationActionRepository actionRepository,
      AutomationActionExecutor automationActionExecutor,
      TransactionTemplate transactionTemplate,
      ObjectMapper objectMapper) {
    this.mappingRepository = mappingRepository;
    this.actionExecutionRepository = actionExecutionRepository;
    this.executionRepository = executionRepository;
    this.ruleRepository = ruleRepository;
    this.actionRepository = actionRepository;
    this.automationActionExecutor = automationActionExecutor;
    this.transactionTemplate = transactionTemplate;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedRate = POLL_INTERVAL_MS)
  public void pollDelayedActions() {
    log.debug("Automation scheduler started");
    var mappings = mappingRepository.findAll();
    int processed = 0;

    for (var mapping : mappings) {
      try {
        int count =
            ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
                .where(RequestScopes.ORG_ID, mapping.getExternalOrgId())
                .call(() -> processTenant());
        processed += count;
      } catch (Exception e) {
        log.error("Failed to process delayed actions for schema {}", mapping.getSchemaName(), e);
      }
    }

    log.info("Automation scheduler completed: {} delayed actions processed", processed);
  }

  private int processTenant() {
    var dueActions =
        transactionTemplate.execute(
            tx ->
                actionExecutionRepository.findByStatusAndScheduledForBefore(
                    ActionExecutionStatus.SCHEDULED, Instant.now()));

    if (dueActions == null || dueActions.isEmpty()) {
      return 0;
    }

    int processed = 0;
    for (var actionExecution : dueActions) {
      try {
        transactionTemplate.executeWithoutResult(tx -> executeDelayedAction(actionExecution));
        processed++;
      } catch (Exception e) {
        log.error(
            "Failed to execute delayed action execution {}: {}",
            actionExecution.getId(),
            e.getMessage(),
            e);
      }
    }
    return processed;
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
