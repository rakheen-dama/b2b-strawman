package io.b2mash.b2b.b2bstrawman.automation.dto;

import io.b2mash.b2b.b2bstrawman.automation.ActionExecutionStatus;
import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.DelayUnit;
import io.b2mash.b2b.b2bstrawman.automation.ExecutionStatus;
import io.b2mash.b2b.b2bstrawman.automation.RuleSource;
import io.b2mash.b2b.b2bstrawman.automation.TriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AutomationDtos {

  private AutomationDtos() {}

  public record CreateRuleRequest(
      @NotBlank String name,
      String description,
      @NotNull TriggerType triggerType,
      @NotNull Map<String, Object> triggerConfig,
      List<Map<String, Object>> conditions,
      List<CreateActionRequest> actions) {}

  public record UpdateRuleRequest(
      @NotBlank String name,
      String description,
      @NotNull TriggerType triggerType,
      @NotNull Map<String, Object> triggerConfig,
      List<Map<String, Object>> conditions,
      List<CreateActionRequest> actions) {}

  public record CreateActionRequest(
      @NotNull ActionType actionType,
      @NotNull Map<String, Object> actionConfig,
      int sortOrder,
      Integer delayDuration,
      DelayUnit delayUnit) {}

  public record UpdateActionRequest(
      @NotNull ActionType actionType,
      @NotNull Map<String, Object> actionConfig,
      int sortOrder,
      Integer delayDuration,
      DelayUnit delayUnit) {}

  public record ReorderActionsRequest(@NotNull List<UUID> actionIds) {}

  public record AutomationRuleResponse(
      UUID id,
      String name,
      String description,
      boolean enabled,
      TriggerType triggerType,
      Map<String, Object> triggerConfig,
      List<Map<String, Object>> conditions,
      RuleSource source,
      String templateSlug,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt,
      List<AutomationActionResponse> actions) {}

  public record AutomationActionResponse(
      UUID id,
      UUID ruleId,
      int sortOrder,
      ActionType actionType,
      Map<String, Object> actionConfig,
      Integer delayDuration,
      DelayUnit delayUnit,
      Instant createdAt,
      Instant updatedAt) {}

  public record AutomationExecutionResponse(
      UUID id,
      UUID ruleId,
      String ruleName,
      String triggerEventType,
      Map<String, Object> triggerEventData,
      boolean conditionsMet,
      ExecutionStatus status,
      Instant startedAt,
      Instant completedAt,
      String errorMessage,
      Instant createdAt,
      List<ActionExecutionResponse> actionExecutions) {}

  public record ActionExecutionResponse(
      UUID id,
      UUID actionId,
      ActionType actionType,
      ActionExecutionStatus status,
      Instant scheduledFor,
      Instant executedAt,
      Map<String, Object> resultData,
      String errorMessage,
      Instant createdAt) {}

  public record TestRuleRequest(Map<String, Object> sampleEventData) {}

  public record TestRuleResponse(boolean conditionsMet, List<String> evaluationDetails) {}
}
