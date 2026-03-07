package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.automation.config.ActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionFailure;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionResult;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionSuccess;
import io.b2mash.b2b.b2bstrawman.automation.config.AutomationConfigDeserializer;
import io.b2mash.b2b.b2bstrawman.automation.executor.ActionExecutor;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dispatcher that routes automation actions to the appropriate {@link ActionExecutor} by {@link
 * ActionType}. Deserializes JSONB action config and records {@link ActionExecution} results.
 */
@Component
public class AutomationActionExecutor {

  private static final Logger log = LoggerFactory.getLogger(AutomationActionExecutor.class);

  private final Map<ActionType, ActionExecutor> executors;
  private final AutomationConfigDeserializer configDeserializer;
  private final ActionExecutionRepository actionExecutionRepository;

  public AutomationActionExecutor(
      List<ActionExecutor> executorList,
      AutomationConfigDeserializer configDeserializer,
      ActionExecutionRepository actionExecutionRepository) {
    this.executors =
        executorList.stream()
            .collect(Collectors.toMap(ActionExecutor::supportedType, Function.identity()));
    this.configDeserializer = configDeserializer;
    this.actionExecutionRepository = actionExecutionRepository;
  }

  /**
   * Executes a single automation action: deserializes config, delegates to the appropriate
   * executor, and records an {@link ActionExecution} with the result.
   *
   * @param action the automation action definition
   * @param executionId the parent automation execution ID
   * @param context the automation context map
   * @return the action result
   */
  public ActionResult execute(
      AutomationAction action, UUID executionId, Map<String, Map<String, Object>> context) {

    // Check if this is a delayed action
    if (action.getDelayDuration() != null && action.getDelayUnit() != null) {
      return createScheduledExecution(action, executionId, context);
    }

    return executeImmediate(action, executionId, context);
  }

  /**
   * Executes an action immediately with the provided executor. Also used by the scheduler to
   * execute delayed actions when their scheduled time arrives.
   */
  public ActionResult executeImmediate(
      AutomationAction action, UUID executionId, Map<String, Map<String, Object>> context) {

    var actionExecution =
        new ActionExecution(executionId, action.getId(), ActionExecutionStatus.PENDING, null);
    actionExecutionRepository.save(actionExecution);

    return executeWithRecord(action, executionId, context, actionExecution);
  }

  /**
   * Executes a previously scheduled action execution. Used by the scheduler to execute delayed
   * actions with their pre-existing ActionExecution record.
   */
  public ActionResult executeScheduled(
      AutomationAction action,
      UUID executionId,
      Map<String, Map<String, Object>> context,
      ActionExecution actionExecution) {
    return executeWithRecord(action, executionId, context, actionExecution);
  }

  private ActionResult executeWithRecord(
      AutomationAction action,
      UUID executionId,
      Map<String, Map<String, Object>> context,
      ActionExecution actionExecution) {

    try {
      ActionExecutor executor = executors.get(action.getActionType());
      if (executor == null) {
        String message = "No executor for action type: " + action.getActionType();
        log.warn(message);
        actionExecution.fail(message, null);
        actionExecutionRepository.save(actionExecution);
        return new ActionFailure(message, null);
      }

      ActionConfig config =
          configDeserializer.deserializeActionConfig(
              action.getActionType(), action.getActionConfig());

      ActionResult result = executor.execute(config, context, executionId);

      if (result.isSuccess()) {
        var success = (ActionSuccess) result;
        actionExecution.complete(success.resultData());
      } else {
        var failure = (ActionFailure) result;
        actionExecution.fail(failure.errorMessage(), failure.errorDetail());
      }
      actionExecutionRepository.save(actionExecution);

      return result;
    } catch (Exception e) {
      log.error(
          "Unexpected error executing action {} (type {}): {}",
          action.getId(),
          action.getActionType(),
          e.getMessage(),
          e);
      actionExecution.fail("Unexpected error: " + e.getMessage(), e.toString());
      actionExecutionRepository.save(actionExecution);
      return new ActionFailure("Unexpected error: " + e.getMessage(), e.toString());
    }
  }

  private ActionResult createScheduledExecution(
      AutomationAction action, UUID executionId, Map<String, Map<String, Object>> context) {
    Instant scheduledFor = calculateScheduledFor(action.getDelayDuration(), action.getDelayUnit());

    var actionExecution =
        new ActionExecution(
            executionId, action.getId(), ActionExecutionStatus.SCHEDULED, scheduledFor);

    // Store context for later deserialization by the scheduler
    Map<String, Object> scheduledData = new LinkedHashMap<>();
    scheduledData.put("_context", context);
    actionExecution.storeContext(scheduledData);

    actionExecutionRepository.save(actionExecution);

    log.info(
        "Scheduled action {} (type {}) for execution at {}",
        action.getId(),
        action.getActionType(),
        scheduledFor);

    return new ActionSuccess(Map.of("scheduled", true, "scheduledFor", scheduledFor.toString()));
  }

  private Instant calculateScheduledFor(Integer delayDuration, DelayUnit delayUnit) {
    Instant now = Instant.now();
    return switch (delayUnit) {
      case MINUTES -> now.plus(Duration.ofMinutes(delayDuration));
      case HOURS -> now.plus(Duration.ofHours(delayDuration));
      case DAYS -> now.plus(Duration.ofDays(delayDuration));
    };
  }
}
