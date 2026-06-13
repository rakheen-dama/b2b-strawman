package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.automation.config.ActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionFailure;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionResult;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionSuccess;
import io.b2mash.b2b.b2bstrawman.automation.config.AutomationConfigDeserializer;
import io.b2mash.b2b.b2bstrawman.automation.executor.ActionExecutor;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dispatcher that routes automation actions to the appropriate {@link ActionExecutor} by {@link
 * ActionType}. Deserializes JSONB action config and records {@link ActionExecution} results.
 *
 * <p>Each side-effecting action runs inside its OWN physical transaction ({@code REQUIRES_NEW}, see
 * {@link #actionTransactionTemplate}). This is the load-bearing isolation guarantee for the
 * automation engine: an action that throws — e.g. an {@code INVOKE_AI_SPECIALIST} action whose
 * runner hits an {@code @Transactional} integration guard with no AI provider configured — can only
 * mark its <em>own</em> transaction rollback-only. The business transaction that triggered the
 * domain event (e.g. {@code InformationRequestService.acceptItem} completing a request) is
 * suspended for the action and therefore can never be poisoned into an {@code
 * UnexpectedRollbackException}. Catching the throwable in the executors alone is insufficient: a
 * nested {@code @Transactional} proxy sets the rollback-only flag on whatever transaction is
 * current <em>before</em> the catch runs, so without the {@code REQUIRES_NEW} boundary the
 * triggering transaction would still abort at commit. Recording the failure ({@link
 * ActionExecution} status + {@code AUTOMATION_ACTION_FAILED} notification) happens in the outer
 * automation transaction, which is left clean, so the soft-failure audit trail survives. (OBS-505)
 */
@Component
public class AutomationActionExecutor {

  private static final Logger log = LoggerFactory.getLogger(AutomationActionExecutor.class);

  private final Map<ActionType, ActionExecutor> executors;
  private final AutomationConfigDeserializer configDeserializer;
  private final ActionExecutionRepository actionExecutionRepository;
  private final TransactionTemplate actionTransactionTemplate;

  public AutomationActionExecutor(
      List<ActionExecutor> executorList,
      AutomationConfigDeserializer configDeserializer,
      ActionExecutionRepository actionExecutionRepository,
      PlatformTransactionManager transactionManager) {
    this.executors =
        executorList.stream()
            .collect(Collectors.toMap(ActionExecutor::supportedType, Function.identity()));
    this.configDeserializer = configDeserializer;
    this.actionExecutionRepository = actionExecutionRepository;
    this.actionTransactionTemplate = new TransactionTemplate(transactionManager);
    this.actionTransactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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

      // Run the action in its own REQUIRES_NEW transaction so a throwing/rollback-marking action
      // (e.g. an INVOKE_AI_SPECIALIST runner hitting a @Transactional guard with no AI provider)
      // can only roll back ITS OWN work — never the suspended business transaction that triggered
      // the domain event. See the class javadoc. (OBS-505)
      ActionResult result = executeInNewTransaction(action, executor, config, context, executionId);

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

  /**
   * Executes a single action inside its own {@code REQUIRES_NEW} transaction. Any failure — whether
   * the executor returns an {@link ActionFailure}, throws, or marks its transaction rollback-only
   * (surfacing as {@code UnexpectedRollbackException} at commit) — is contained to this nested
   * transaction and converted into an {@link ActionFailure}. It never propagates to the caller's
   * transaction, so the triggering business transaction is left clean. The automation
   * execution/notification bookkeeping that follows runs in that outer transaction and commits.
   */
  private ActionResult executeInNewTransaction(
      AutomationAction action,
      ActionExecutor executor,
      ActionConfig config,
      Map<String, Map<String, Object>> context,
      UUID executionId) {
    try {
      // Bind the automation execution ID so domain events published by services
      // during action execution carry this ID for cycle detection (ADR-146).
      return actionTransactionTemplate.execute(
          status ->
              ScopedValue.where(RequestScopes.AUTOMATION_EXECUTION_ID, executionId)
                  .call(() -> executor.execute(config, context, executionId)));
    } catch (Exception e) {
      log.warn(
          "Action {} (type {}) failed in its isolated transaction: {}",
          action.getId(),
          action.getActionType(),
          e.getMessage());
      return new ActionFailure(
          "Action failed in isolated transaction: " + e.getMessage(), e.toString());
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
