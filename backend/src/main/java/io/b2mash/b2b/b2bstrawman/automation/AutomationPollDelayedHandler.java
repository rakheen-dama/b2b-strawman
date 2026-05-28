package io.b2mash.b2b.b2bstrawman.automation;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Job handler for delayed automation action execution. Queries for SCHEDULED action executions with
 * a scheduledFor timestamp in the past, validates the parent rule is still enabled, deserializes
 * the stored context, and delegates execution to the action executor.
 *
 * <p>Extracted from {@link AutomationScheduler#pollDelayedActions()}.
 */
@Component
public class AutomationPollDelayedHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(AutomationPollDelayedHandler.class);

  private final ActionExecutionRepository actionExecutionRepository;
  private final AutomationExecutionRepository executionRepository;
  private final AutomationRuleRepository ruleRepository;
  private final AutomationActionRepository actionRepository;
  private final AutomationActionExecutor automationActionExecutor;
  private final TransactionTemplate transactionTemplate;
  private final ObjectMapper objectMapper;

  public AutomationPollDelayedHandler(
      ActionExecutionRepository actionExecutionRepository,
      AutomationExecutionRepository executionRepository,
      AutomationRuleRepository ruleRepository,
      AutomationActionRepository actionRepository,
      AutomationActionExecutor automationActionExecutor,
      TransactionTemplate transactionTemplate,
      ObjectMapper objectMapper) {
    this.actionExecutionRepository = actionExecutionRepository;
    this.executionRepository = executionRepository;
    this.ruleRepository = ruleRepository;
    this.actionRepository = actionRepository;
    this.automationActionExecutor = automationActionExecutor;
    this.transactionTemplate = transactionTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public String jobType() {
    return "automation_poll_delayed";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    int processed = processTenant();
    if (processed > 0) {
      log.info("AutomationPollDelayedHandler: processed {} delayed actions", processed);
    }
  }

  private int processTenant() {
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

    Map<String, Map<String, Object>> context = deserializeContext(actionExecution);
    if (context == null) {
      actionExecution.fail("Failed to deserialize stored context", null);
      actionExecutionRepository.save(actionExecution);
      return;
    }

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
