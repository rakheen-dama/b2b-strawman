package io.b2mash.b2b.b2bstrawman.automation.executor;

import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionResult;
import java.util.Map;
import java.util.UUID;

/**
 * Strategy interface for executing a specific type of automation action. Implementations are
 * collected by the {@link io.b2mash.b2b.b2bstrawman.automation.AutomationActionExecutor} dispatcher
 * and routed by {@link ActionType}.
 */
public interface ActionExecutor {

  /** Returns the action type this executor handles. */
  ActionType supportedType();

  /**
   * Executes the action with the given config and context.
   *
   * @param config the deserialized action configuration
   * @param context the automation context map (entity key -> field map)
   * @param automationExecutionId the parent execution ID for cycle detection propagation
   * @return the result of the execution
   */
  ActionResult execute(
      ActionConfig config, Map<String, Map<String, Object>> context, UUID automationExecutionId);
}
