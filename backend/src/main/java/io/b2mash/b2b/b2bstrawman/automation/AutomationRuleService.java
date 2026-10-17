package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.AutomationActionResponse;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.AutomationExecutionResponse;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.AutomationRuleResponse;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read and enable/disable operations over automation rules and their executions (ADR-334: the
 * engine is headless and the admin list is always on — no module guard). Rules are installed by
 * packs ({@code AutomationPackInstaller}); the rule builder's create/update/delete/duplicate/test
 * and action-CRUD methods were removed with their endpoints in Phase 86 (Epic 604A).
 */
@Service
@Transactional
public class AutomationRuleService {

  private final AutomationRuleRepository ruleRepository;
  private final AutomationActionRepository actionRepository;
  private final AutomationExecutionRepository executionRepository;
  private final ActionExecutionRepository actionExecutionRepository;
  private final AuditService auditService;

  public AutomationRuleService(
      AutomationRuleRepository ruleRepository,
      AutomationActionRepository actionRepository,
      AutomationExecutionRepository executionRepository,
      ActionExecutionRepository actionExecutionRepository,
      AuditService auditService) {
    this.ruleRepository = ruleRepository;
    this.actionRepository = actionRepository;
    this.executionRepository = executionRepository;
    this.actionExecutionRepository = actionExecutionRepository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public AutomationRuleResponse getRule(UUID id) {
    var rule =
        ruleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Rule", id));
    var actions = actionRepository.findByRuleIdOrderBySortOrder(id);
    return toRuleResponse(rule, actions);
  }

  @Transactional(readOnly = true)
  public List<AutomationRuleResponse> listRules(Boolean enabled, TriggerType triggerType) {
    List<AutomationRule> rules;
    if (enabled != null && triggerType != null) {
      rules = ruleRepository.findByEnabledAndTriggerType(enabled, triggerType);
    } else if (enabled != null) {
      rules = ruleRepository.findByEnabled(enabled);
    } else if (triggerType != null) {
      rules = ruleRepository.findByTriggerType(triggerType);
    } else {
      rules = ruleRepository.findAllByOrderByCreatedAtDesc();
    }
    // Batch-fetch all actions for the returned rules to avoid N+1
    List<UUID> ruleIds = rules.stream().map(AutomationRule::getId).toList();
    Map<UUID, List<AutomationAction>> actionsByRuleId = batchFetchActionsByRuleIds(ruleIds);
    return rules.stream()
        .map(rule -> toRuleResponse(rule, actionsByRuleId.getOrDefault(rule.getId(), List.of())))
        .toList();
  }

  public AutomationRuleResponse toggleRule(UUID id) {
    var rule =
        ruleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Rule", id));
    rule.toggle();
    rule = ruleRepository.save(rule);

    String eventType = rule.isEnabled() ? "automation_rule.enabled" : "automation_rule.disabled";
    var details = new HashMap<String, Object>();
    details.put("rule_name", rule.getName());
    auditService.log(
        AuditEventBuilder.builder()
            .eventType(eventType)
            .entityType("AutomationRule")
            .entityId(rule.getId())
            .details(details)
            .build());

    var actions = actionRepository.findByRuleIdOrderBySortOrder(id);
    return toRuleResponse(rule, actions);
  }

  // --- Execution queries ---

  @Transactional(readOnly = true)
  public Page<AutomationExecutionResponse> listExecutions(
      UUID ruleId, ExecutionStatus status, Pageable pageable) {
    Page<AutomationExecution> executions;
    if (ruleId != null && status != null) {
      executions = executionRepository.findByRuleIdAndStatus(ruleId, status, pageable);
    } else if (ruleId != null) {
      executions = executionRepository.findByRuleId(ruleId, pageable);
    } else if (status != null) {
      executions = executionRepository.findByStatus(status, pageable);
    } else {
      executions = executionRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
    return toExecutionResponsePage(executions);
  }

  @Transactional(readOnly = true)
  public AutomationExecutionResponse getExecution(UUID id) {
    var execution =
        executionRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Execution", id));
    return toSingleExecutionResponse(execution);
  }

  @Transactional(readOnly = true)
  public Page<AutomationExecutionResponse> listExecutionsForRule(UUID ruleId, Pageable pageable) {
    ruleRepository
        .findById(ruleId)
        .orElseThrow(() -> new ResourceNotFoundException("Rule", ruleId));
    var executions = executionRepository.findByRuleId(ruleId, pageable);
    return toExecutionResponsePage(executions);
  }

  // --- Response mapping ---

  private AutomationRuleResponse toRuleResponse(
      AutomationRule rule, List<AutomationAction> actions) {
    return new AutomationRuleResponse(
        rule.getId(),
        rule.getName(),
        rule.getDescription(),
        rule.isEnabled(),
        rule.getTriggerType(),
        rule.getTriggerConfig(),
        rule.getConditions(),
        rule.getSource(),
        rule.getTemplateSlug(),
        rule.getCreatedBy(),
        rule.getCreatedAt(),
        rule.getUpdatedAt(),
        actions.stream().map(this::toActionResponse).toList());
  }

  private AutomationActionResponse toActionResponse(AutomationAction action) {
    return new AutomationActionResponse(
        action.getId(),
        action.getRuleId(),
        action.getSortOrder(),
        action.getActionType(),
        action.getActionConfig(),
        action.getDelayDuration(),
        action.getDelayUnit(),
        action.getCreatedAt(),
        action.getUpdatedAt());
  }

  /**
   * Batch-fetches actions grouped by rule ID for a list of rule IDs. Avoids N+1 queries when
   * mapping multiple rules to responses.
   */
  private Map<UUID, List<AutomationAction>> batchFetchActionsByRuleIds(List<UUID> ruleIds) {
    if (ruleIds.isEmpty()) {
      return Map.of();
    }
    return actionRepository.findByRuleIdInOrderBySortOrder(ruleIds).stream()
        .collect(Collectors.groupingBy(AutomationAction::getRuleId));
  }

  /**
   * Converts a page of executions to responses with batch-fetched rules and action executions.
   * Avoids N+1 queries for rule names and action execution lookups.
   */
  private Page<AutomationExecutionResponse> toExecutionResponsePage(
      Page<AutomationExecution> executions) {
    List<AutomationExecution> content = executions.getContent();
    if (content.isEmpty()) {
      return executions.map(e -> null); // empty page
    }

    // Batch-fetch rule names
    List<UUID> ruleIds = content.stream().map(AutomationExecution::getRuleId).distinct().toList();
    Map<UUID, String> ruleNameMap =
        ruleRepository.findAllById(ruleIds).stream()
            .collect(Collectors.toMap(AutomationRule::getId, AutomationRule::getName));

    // Batch-fetch action executions
    List<UUID> executionIds = content.stream().map(AutomationExecution::getId).toList();
    Map<UUID, List<ActionExecution>> actionExecsByExecutionId =
        actionExecutionRepository.findByExecutionIdIn(executionIds).stream()
            .collect(Collectors.groupingBy(ActionExecution::getExecutionId));

    // Batch-fetch action types for all action executions
    List<UUID> actionIds =
        actionExecsByExecutionId.values().stream()
            .flatMap(List::stream)
            .map(ActionExecution::getActionId)
            .filter(id -> id != null)
            .distinct()
            .toList();
    Map<UUID, ActionType> actionTypeMap =
        actionRepository.findAllById(actionIds).stream()
            .collect(Collectors.toMap(AutomationAction::getId, AutomationAction::getActionType));

    return executions.map(
        execution ->
            toExecutionResponse(
                execution,
                ruleNameMap.getOrDefault(execution.getRuleId(), "(deleted rule)"),
                actionExecsByExecutionId.getOrDefault(execution.getId(), List.of()),
                actionTypeMap));
  }

  /** Maps a single execution to its response (used for getExecution by ID). */
  private AutomationExecutionResponse toSingleExecutionResponse(AutomationExecution execution) {
    String ruleName =
        ruleRepository
            .findById(execution.getRuleId())
            .map(AutomationRule::getName)
            .orElse("(deleted rule)");

    var actionExecutions = actionExecutionRepository.findByExecutionId(execution.getId());

    List<UUID> actionIds =
        actionExecutions.stream()
            .map(ActionExecution::getActionId)
            .filter(id -> id != null)
            .distinct()
            .toList();
    Map<UUID, ActionType> actionTypeMap =
        actionRepository.findAllById(actionIds).stream()
            .collect(Collectors.toMap(AutomationAction::getId, AutomationAction::getActionType));

    return toExecutionResponse(execution, ruleName, actionExecutions, actionTypeMap);
  }

  private AutomationExecutionResponse toExecutionResponse(
      AutomationExecution execution,
      String ruleName,
      List<ActionExecution> actionExecutions,
      Map<UUID, ActionType> actionTypeMap) {
    var actionExecutionResponses =
        actionExecutions.stream().map(ae -> toActionExecutionResponse(ae, actionTypeMap)).toList();

    return new AutomationExecutionResponse(
        execution.getId(),
        execution.getRuleId(),
        ruleName,
        execution.getTriggerEventType(),
        execution.getTriggerEventData(),
        execution.isConditionsMet(),
        execution.getStatus(),
        execution.getStartedAt(),
        execution.getCompletedAt(),
        execution.getErrorMessage(),
        execution.getCreatedAt(),
        actionExecutionResponses);
  }

  private io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.ActionExecutionResponse
      toActionExecutionResponse(
          ActionExecution actionExecution, Map<UUID, ActionType> actionTypeMap) {
    ActionType actionType = null;
    if (actionExecution.getActionId() != null) {
      actionType = actionTypeMap.get(actionExecution.getActionId());
    }
    return new io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.ActionExecutionResponse(
        actionExecution.getId(),
        actionExecution.getActionId(),
        actionType,
        actionExecution.getStatus(),
        actionExecution.getScheduledFor(),
        actionExecution.getExecutedAt(),
        actionExecution.getResultData(),
        actionExecution.getErrorMessage(),
        actionExecution.getCreatedAt());
  }
}
