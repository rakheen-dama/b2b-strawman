package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.AutomationActionResponse;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.AutomationExecutionResponse;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.AutomationRuleResponse;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.CreateActionRequest;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.CreateRuleRequest;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.TestRuleResponse;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.UpdateActionRequest;
import io.b2mash.b2b.b2bstrawman.automation.dto.AutomationDtos.UpdateRuleRequest;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AutomationRuleService {

  private final AutomationRuleRepository ruleRepository;
  private final AutomationActionRepository actionRepository;
  private final AutomationExecutionRepository executionRepository;
  private final ActionExecutionRepository actionExecutionRepository;
  private final ConditionEvaluator conditionEvaluator;
  private final AuditService auditService;
  private final EntityManager entityManager;

  public AutomationRuleService(
      AutomationRuleRepository ruleRepository,
      AutomationActionRepository actionRepository,
      AutomationExecutionRepository executionRepository,
      ActionExecutionRepository actionExecutionRepository,
      ConditionEvaluator conditionEvaluator,
      AuditService auditService,
      EntityManager entityManager) {
    this.ruleRepository = ruleRepository;
    this.actionRepository = actionRepository;
    this.executionRepository = executionRepository;
    this.actionExecutionRepository = actionExecutionRepository;
    this.conditionEvaluator = conditionEvaluator;
    this.auditService = auditService;
    this.entityManager = entityManager;
  }

  public AutomationRuleResponse createRule(CreateRuleRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    var rule =
        new AutomationRule(
            request.name(),
            request.description(),
            request.triggerType(),
            request.triggerConfig(),
            request.conditions(),
            RuleSource.CUSTOM,
            null,
            memberId);
    rule = ruleRepository.save(rule);

    var details = new HashMap<String, Object>();
    details.put("rule_name", rule.getName());
    details.put("trigger_type", rule.getTriggerType().name());
    details.put("source", rule.getSource().name());
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("automation_rule.created")
            .entityType("AutomationRule")
            .entityId(rule.getId())
            .details(details)
            .build());

    return toRuleResponse(rule, List.of());
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
    return rules.stream()
        .map(
            rule -> {
              var actions = actionRepository.findByRuleIdOrderBySortOrder(rule.getId());
              return toRuleResponse(rule, actions);
            })
        .toList();
  }

  public AutomationRuleResponse updateRule(UUID id, UpdateRuleRequest request) {
    var rule =
        ruleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Rule", id));
    rule.update(
        request.name(),
        request.description(),
        request.triggerType(),
        request.triggerConfig(),
        request.conditions());
    rule = ruleRepository.save(rule);

    var details = new HashMap<String, Object>();
    details.put("rule_name", rule.getName());
    details.put("trigger_type", rule.getTriggerType().name());
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("automation_rule.updated")
            .entityType("AutomationRule")
            .entityId(rule.getId())
            .details(details)
            .build());

    var actions = actionRepository.findByRuleIdOrderBySortOrder(id);
    return toRuleResponse(rule, actions);
  }

  public void deleteRule(UUID id) {
    var rule =
        ruleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Rule", id));

    // Cancel scheduled action executions before deleting
    var actions = actionRepository.findByRuleIdOrderBySortOrder(id);
    List<UUID> actionIds = actions.stream().map(AutomationAction::getId).toList();
    if (!actionIds.isEmpty()) {
      var scheduledExecutions =
          actionExecutionRepository.findByActionIdInAndStatus(
              actionIds, ActionExecutionStatus.SCHEDULED);
      for (var actionExecution : scheduledExecutions) {
        actionExecution.cancel();
        actionExecutionRepository.save(actionExecution);
      }
    }

    var details = new HashMap<String, Object>();
    details.put("rule_name", rule.getName());
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("automation_rule.deleted")
            .entityType("AutomationRule")
            .entityId(rule.getId())
            .details(details)
            .build());

    // Flush audit event before @Modifying query clears persistence context
    entityManager.flush();
    actionRepository.deleteByRuleId(id);
    ruleRepository.deleteById(id);
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

  public AutomationRuleResponse duplicateRule(UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    var original =
        ruleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Rule", id));
    var originalActions = actionRepository.findByRuleIdOrderBySortOrder(id);

    var copy =
        new AutomationRule(
            original.getName() + " (Copy)",
            original.getDescription(),
            original.getTriggerType(),
            original.getTriggerConfig(),
            original.getConditions(),
            RuleSource.CUSTOM,
            null,
            memberId);
    copy = ruleRepository.save(copy);

    List<AutomationAction> copiedActions = new ArrayList<>();
    for (var action : originalActions) {
      var actionCopy =
          new AutomationAction(
              copy.getId(),
              action.getSortOrder(),
              action.getActionType(),
              action.getActionConfig(),
              action.getDelayDuration(),
              action.getDelayUnit());
      copiedActions.add(actionRepository.save(actionCopy));
    }

    var details = new HashMap<String, Object>();
    details.put("rule_name", copy.getName());
    details.put("source_rule_id", original.getId().toString());
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("automation_rule.created")
            .entityType("AutomationRule")
            .entityId(copy.getId())
            .details(details)
            .build());

    return toRuleResponse(copy, copiedActions);
  }

  @Transactional(readOnly = true)
  public TestRuleResponse testRule(UUID id, Map<String, Object> sampleEventData) {
    var rule =
        ruleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Rule", id));

    // Convert flat Map<String, Object> to Map<String, Map<String, Object>> for ConditionEvaluator
    Map<String, Map<String, Object>> context = new HashMap<>();
    if (sampleEventData != null) {
      for (var entry : sampleEventData.entrySet()) {
        if (entry.getValue() instanceof Map<?, ?> nestedMap) {
          @SuppressWarnings("unchecked")
          Map<String, Object> typedMap = (Map<String, Object>) nestedMap;
          context.put(entry.getKey(), typedMap);
        }
      }
    }

    boolean conditionsMet = conditionEvaluator.evaluate(rule.getConditions(), context);

    List<String> evaluationDetails = new ArrayList<>();
    if (rule.getConditions() == null || rule.getConditions().isEmpty()) {
      evaluationDetails.add("No conditions defined — rule matches unconditionally");
    } else {
      for (var condition : rule.getConditions()) {
        String field = (String) condition.get("field");
        String operator = (String) condition.get("operator");
        Object value = condition.get("value");
        boolean matched = conditionEvaluator.evaluate(List.of(condition), context);
        evaluationDetails.add(
            "Condition [%s %s %s]: %s"
                .formatted(field, operator, value, matched ? "MATCHED" : "NOT MATCHED"));
      }
    }

    var actions = actionRepository.findByRuleIdOrderBySortOrder(id);
    if (conditionsMet && !actions.isEmpty()) {
      evaluationDetails.add(
          "%d action(s) would fire: %s"
              .formatted(
                  actions.size(), actions.stream().map(a -> a.getActionType().name()).toList()));
    }

    return new TestRuleResponse(conditionsMet, evaluationDetails);
  }

  // --- Action CRUD ---

  public AutomationActionResponse addAction(UUID ruleId, CreateActionRequest request) {
    ruleRepository
        .findById(ruleId)
        .orElseThrow(() -> new ResourceNotFoundException("Rule", ruleId));
    var action =
        new AutomationAction(
            ruleId,
            request.sortOrder(),
            request.actionType(),
            request.actionConfig(),
            request.delayDuration(),
            request.delayUnit());
    action = actionRepository.save(action);
    return toActionResponse(action);
  }

  public AutomationActionResponse updateAction(
      UUID ruleId, UUID actionId, UpdateActionRequest request) {
    ruleRepository
        .findById(ruleId)
        .orElseThrow(() -> new ResourceNotFoundException("Rule", ruleId));
    var action =
        actionRepository
            .findById(actionId)
            .orElseThrow(() -> new ResourceNotFoundException("Action", actionId));
    if (!action.getRuleId().equals(ruleId)) {
      throw new ResourceNotFoundException("Action", actionId);
    }
    action.update(
        request.actionType(),
        request.actionConfig(),
        request.sortOrder(),
        request.delayDuration(),
        request.delayUnit());
    action = actionRepository.save(action);
    return toActionResponse(action);
  }

  public void removeAction(UUID ruleId, UUID actionId) {
    ruleRepository
        .findById(ruleId)
        .orElseThrow(() -> new ResourceNotFoundException("Rule", ruleId));
    var action =
        actionRepository
            .findById(actionId)
            .orElseThrow(() -> new ResourceNotFoundException("Action", actionId));
    if (!action.getRuleId().equals(ruleId)) {
      throw new ResourceNotFoundException("Action", actionId);
    }
    actionRepository.delete(action);
  }

  public List<AutomationActionResponse> reorderActions(UUID ruleId, List<UUID> actionIds) {
    ruleRepository
        .findById(ruleId)
        .orElseThrow(() -> new ResourceNotFoundException("Rule", ruleId));
    var actions = actionRepository.findByRuleIdOrderBySortOrder(ruleId);
    Map<UUID, AutomationAction> actionMap = new HashMap<>();
    for (var action : actions) {
      actionMap.put(action.getId(), action);
    }
    // Set all to negative temporary values first to avoid unique constraint violations
    int offset = -1000;
    for (var action : actions) {
      action.setSortOrder(offset--);
    }
    actionRepository.saveAllAndFlush(actions);
    // Now set final sort orders
    for (int i = 0; i < actionIds.size(); i++) {
      var action = actionMap.get(actionIds.get(i));
      if (action != null) {
        action.setSortOrder(i);
      }
    }
    actionRepository.saveAllAndFlush(actions);
    return actionRepository.findByRuleIdOrderBySortOrder(ruleId).stream()
        .map(this::toActionResponse)
        .toList();
  }

  // --- Execution queries ---

  @Transactional(readOnly = true)
  public List<AutomationExecutionResponse> listExecutions(UUID ruleId, ExecutionStatus status) {
    List<AutomationExecution> executions;
    if (ruleId != null && status != null) {
      executions = executionRepository.findByRuleIdAndStatus(ruleId, status);
    } else if (ruleId != null) {
      executions = executionRepository.findByRuleIdOrderByStartedAtDesc(ruleId);
    } else if (status != null) {
      executions = executionRepository.findByStatus(status);
    } else {
      executions = executionRepository.findAllByOrderByCreatedAtDesc();
    }
    return executions.stream().map(this::toExecutionResponse).toList();
  }

  @Transactional(readOnly = true)
  public AutomationExecutionResponse getExecution(UUID id) {
    var execution =
        executionRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Execution", id));
    return toExecutionResponse(execution);
  }

  @Transactional(readOnly = true)
  public List<AutomationExecutionResponse> listExecutionsForRule(UUID ruleId) {
    ruleRepository
        .findById(ruleId)
        .orElseThrow(() -> new ResourceNotFoundException("Rule", ruleId));
    return executionRepository.findByRuleIdOrderByStartedAtDesc(ruleId).stream()
        .map(this::toExecutionResponse)
        .toList();
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

  private AutomationExecutionResponse toExecutionResponse(AutomationExecution execution) {
    String ruleName =
        ruleRepository
            .findById(execution.getRuleId())
            .map(AutomationRule::getName)
            .orElse("(deleted rule)");

    var actionExecutions = actionExecutionRepository.findByExecutionId(execution.getId());
    var actionExecutionResponses =
        actionExecutions.stream().map(this::toActionExecutionResponse).toList();

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
      toActionExecutionResponse(ActionExecution actionExecution) {
    ActionType actionType = null;
    if (actionExecution.getActionId() != null) {
      actionType =
          actionRepository
              .findById(actionExecution.getActionId())
              .map(AutomationAction::getActionType)
              .orElse(null);
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
