package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestModuleHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end integration tests for the Workflow Automations engine. Covers cross-cutting scenarios
 * not tested by individual slice tests: full event-to-execution flow, multi-rule evaluation,
 * condition edge cases, cycle detection, delayed actions, template activation, error recovery, and
 * rule lifecycle effects on scheduled actions.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutomationEndToEndTest {

  private static final String ORG_ID = "org_automation_e2e_test";
  private static final String ACTOR_NAME = "E2E Test Actor";

  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private AutomationActionRepository actionRepository;
  @Autowired private AutomationExecutionRepository executionRepository;
  @Autowired private ActionExecutionRepository actionExecutionRepository;
  @Autowired private AutomationActionExecutor automationActionExecutor;
  @Autowired private AutomationScheduler automationScheduler;
  @Autowired private AutomationRuleService automationRuleService;
  @Autowired private AutomationTemplateService automationTemplateService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private ProjectMemberRepository projectMemberRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private ConditionEvaluator conditionEvaluator;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;

  private String schemaName;
  private UUID actorMemberId;
  private UUID projectId;

  @BeforeAll
  void provisionTenant() {
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "Automation E2E Test Org", null).schemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var member =
                  new Member(
                      "clerk_e2e_test",
                      "e2e@test.com",
                      "E2E Test Actor",
                      null,
                      orgRoleRepository.findBySlug("owner").orElseThrow());
              memberRepository.save(member);
              actorMemberId = member.getId();

              var customer =
                  TestCustomerFactory.createActiveCustomer(
                      "E2E Test Customer", "e2ecustomer@test.com", actorMemberId);
              customerRepository.save(customer);

              var project = new Project("E2E Automation Project", null, actorMemberId);
              project.setCustomerId(customer.getId());
              projectRepository.save(project);
              projectId = project.getId();

              var pm = new ProjectMember(projectId, actorMemberId, "LEAD", actorMemberId);
              projectMemberRepository.save(pm);

              TestModuleHelper.enableModulesInTenant(
                  orgSettingsService, orgSettingsRepository, "automation_builder");
            });
  }

  // --- 288.1 Full end-to-end flow ---

  @Test
  void fullEndToEnd_eventTriggersRuleWithCreateTaskAction() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "E2E full flow",
                      TriggerType.TASK_STATUS_CHANGED,
                      Map.of("toStatus", "DONE"),
                      null);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      0,
                      ActionType.CREATE_TASK,
                      Map.of(
                          "taskName", "Follow-up: {{task.name}}",
                          "taskDescription", "Auto-created from automation",
                          "assignTo", "TRIGGER_ACTOR"),
                      null,
                      null);
              actionRepository.save(action);

              long taskCountBefore = taskRepository.count();

              var event = taskStatusChangedEvent("OPEN", "DONE");
              eventPublisher.publishEvent(event);

              // Verify execution record
              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).hasSize(1);
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_COMPLETED);
              assertThat(executions.getFirst().isConditionsMet()).isTrue();

              // Verify action execution record
              var actionExecs =
                  actionExecutionRepository.findByExecutionId(executions.getFirst().getId());
              assertThat(actionExecs).hasSize(1);
              assertThat(actionExecs.getFirst().getStatus())
                  .isEqualTo(ActionExecutionStatus.COMPLETED);
              assertThat(actionExecs.getFirst().getResultData()).containsKey("createdTaskId");

              // Verify task was actually created with expected content
              assertThat(taskRepository.count()).isGreaterThan(taskCountBefore);
              var createdTaskId =
                  UUID.fromString(
                      actionExecs.getFirst().getResultData().get("createdTaskId").toString());
              var createdTask = taskRepository.findById(createdTaskId);
              assertThat(createdTask).isPresent();
              assertThat(createdTask.get().getTitle()).contains("Follow-up:");
            });
  }

  // --- 288.2 Multi-rule test ---

  @Test
  void multiRule_sameEventTriggersTwoRulesIndependently() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule1 =
                  createRule("Multi-rule A", TriggerType.TASK_STATUS_CHANGED, Map.of(), null);
              var rule2 =
                  createRule("Multi-rule B", TriggerType.TASK_STATUS_CHANGED, Map.of(), null);
              ruleRepository.save(rule1);
              ruleRepository.save(rule2);

              // Add a SEND_NOTIFICATION action to each rule
              var action1 =
                  new AutomationAction(
                      rule1.getId(),
                      0,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "recipientType", "TRIGGER_ACTOR",
                          "title", "Rule A fired",
                          "message", "From rule A"),
                      null,
                      null);
              var action2 =
                  new AutomationAction(
                      rule2.getId(),
                      0,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "recipientType", "TRIGGER_ACTOR",
                          "title", "Rule B fired",
                          "message", "From rule B"),
                      null,
                      null);
              actionRepository.save(action1);
              actionRepository.save(action2);

              var event = taskStatusChangedEvent("OPEN", "IN_PROGRESS");
              eventPublisher.publishEvent(event);

              var exec1 = executionRepository.findByRuleIdOrderByStartedAtDesc(rule1.getId());
              var exec2 = executionRepository.findByRuleIdOrderByStartedAtDesc(rule2.getId());
              assertThat(exec1).hasSize(1);
              assertThat(exec2).hasSize(1);
              assertThat(exec1.getFirst().getStatus()).isEqualTo(ExecutionStatus.ACTIONS_COMPLETED);
              assertThat(exec2.getFirst().getStatus()).isEqualTo(ExecutionStatus.ACTIONS_COMPLETED);
            });
  }

  // --- 288.3 Condition edge cases ---

  @Test
  void conditionEdgeCases_isNullOnNullField() {
    var context = buildMinimalContext();
    context.get("task").put("assigneeId", null);

    var conditions =
        List.<Map<String, Object>>of(Map.of("field", "task.assigneeId", "operator", "IS_NULL"));
    assertThat(conditionEvaluator.evaluate(conditions, context)).isTrue();
  }

  @Test
  void conditionEdgeCases_equalsOnNullField() {
    var context = buildMinimalContext();
    // "task.missing" does not exist in context -> resolves to null
    var conditions =
        List.<Map<String, Object>>of(
            Map.of("field", "task.missing", "operator", "EQUALS", "value", "something"));
    assertThat(conditionEvaluator.evaluate(conditions, context)).isFalse();
  }

  @Test
  void conditionEdgeCases_greaterThanOnNonNumeric() {
    var context = buildMinimalContext();
    context.get("task").put("status", "DONE");

    var conditions =
        List.<Map<String, Object>>of(
            Map.of("field", "task.status", "operator", "GREATER_THAN", "value", 5));
    assertThat(conditionEvaluator.evaluate(conditions, context)).isFalse();
  }

  @Test
  void conditionEdgeCases_inWithMatchingValue() {
    var context = buildMinimalContext();
    context.get("task").put("status", "DONE");

    var conditions =
        List.<Map<String, Object>>of(
            Map.of(
                "field",
                "task.status",
                "operator",
                "IN",
                "value",
                List.of("OPEN", "DONE", "CANCELLED")));
    assertThat(conditionEvaluator.evaluate(conditions, context)).isTrue();
  }

  @Test
  void conditionEdgeCases_unknownFieldPath() {
    var context = buildMinimalContext();

    var conditions =
        List.<Map<String, Object>>of(
            Map.of("field", "nonexistent.field", "operator", "EQUALS", "value", "test"));
    assertThat(conditionEvaluator.evaluate(conditions, context)).isFalse();
  }

  @Test
  void conditionEdgeCases_emptyConditionsList() {
    var context = buildMinimalContext();

    assertThat(conditionEvaluator.evaluate(List.of(), context)).isTrue();
    assertThat(conditionEvaluator.evaluate(null, context)).isTrue();
  }

  // --- 288.4 Cycle detection end-to-end ---

  @Test
  void cycleDetection_createTaskActionDoesNotRetriggerRule() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "Cycle detection rule",
                      TriggerType.TASK_STATUS_CHANGED,
                      Map.of("toStatus", "DONE"),
                      null);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      0,
                      ActionType.CREATE_TASK,
                      Map.of(
                          "taskName", "Cycle test follow-up",
                          "taskDescription", "Should not cause a cycle",
                          "assignTo", "TRIGGER_ACTOR"),
                      null,
                      null);
              actionRepository.save(action);

              var event = taskStatusChangedEvent("OPEN", "DONE");
              eventPublisher.publishEvent(event);

              // Only 1 execution should exist — CREATE_TASK does not publish
              // TaskStatusChangedEvent, so no cycle occurs
              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).hasSize(1);
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_COMPLETED);
            });
  }

  // --- 288.5 Delayed action end-to-end ---

  @Test
  void delayedAction_scheduledAndThenExecutedByScheduler() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "Delayed action rule", TriggerType.TASK_STATUS_CHANGED, Map.of(), null);
              ruleRepository.save(rule);

              // Action with 1-minute delay
              var action =
                  new AutomationAction(
                      rule.getId(),
                      0,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "recipientType", "TRIGGER_ACTOR",
                          "title", "Delayed notification",
                          "message", "This was delayed"),
                      1,
                      DelayUnit.MINUTES);
              actionRepository.save(action);

              var event = taskStatusChangedEvent("OPEN", "DONE");
              eventPublisher.publishEvent(event);

              // Verify execution was created
              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).hasSize(1);

              // Verify action execution is SCHEDULED with a future scheduledFor
              var actionExecs =
                  actionExecutionRepository.findByExecutionId(executions.getFirst().getId());
              assertThat(actionExecs).hasSize(1);
              assertThat(actionExecs.getFirst().getStatus())
                  .isEqualTo(ActionExecutionStatus.SCHEDULED);
              assertThat(actionExecs.getFirst().getScheduledFor()).isNotNull();
              assertThat(actionExecs.getFirst().getScheduledFor())
                  .isAfter(Instant.now().minusSeconds(5))
                  .isBefore(Instant.now().plus(2, ChronoUnit.MINUTES));

              // To test execution by the scheduler, create a separate SCHEDULED action
              // execution with scheduledFor in the past (ActionExecution has no setter)
              var pastExecution =
                  new AutomationExecution(
                      rule.getId(),
                      "TaskStatusChangedEvent",
                      Map.of("eventType", "delayed-test"),
                      true,
                      ExecutionStatus.TRIGGERED);
              executionRepository.save(pastExecution);

              var pastActionExec =
                  new ActionExecution(
                      pastExecution.getId(),
                      action.getId(),
                      ActionExecutionStatus.SCHEDULED,
                      Instant.now().minus(1, ChronoUnit.HOURS));
              Map<String, Object> contextData = new LinkedHashMap<>();
              contextData.put("_context", Map.of("actor", Map.of("id", actorMemberId.toString())));
              pastActionExec.storeContext(contextData);
              actionExecutionRepository.save(pastActionExec);

              // Run the scheduler
              automationScheduler.pollDelayedActions();

              // Verify the past-due action was executed
              var updated = actionExecutionRepository.findById(pastActionExec.getId());
              assertThat(updated).isPresent();
              assertThat(updated.get().getStatus()).isEqualTo(ActionExecutionStatus.COMPLETED);
            });
  }

  // --- 288.6 Template activation + execution ---

  @Test
  void templateActivation_taskCompletionChainFiresOnEvent() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, actorMemberId)
        .run(
            () -> {
              // Activate the task-completion-chain template
              var ruleResponse =
                  automationTemplateService.activateTemplate("task-completion-chain");
              assertThat(ruleResponse).isNotNull();
              assertThat(ruleResponse.source()).isEqualTo(RuleSource.TEMPLATE);
              assertThat(ruleResponse.templateSlug()).isEqualTo("task-completion-chain");

              UUID ruleId = ruleResponse.id();
              long taskCountBefore = taskRepository.count();

              // Publish matching event (toStatus=DONE — matches TaskStatus.DONE enum value)
              var event = taskStatusChangedEvent("IN_PROGRESS", "DONE");
              eventPublisher.publishEvent(event);

              // Verify the rule fired
              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(ruleId);
              assertThat(executions).isNotEmpty();
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_COMPLETED);

              // Verify follow-up task was created
              assertThat(taskRepository.count()).isGreaterThan(taskCountBefore);
            });
  }

  // --- 288.7 Error recovery ---

  @Test
  void errorRecovery_failedActionDoesNotBlockSubsequentActions() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "Error recovery rule", TriggerType.TASK_STATUS_CHANGED, Map.of(), null);
              ruleRepository.save(rule);

              // Action 0: SEND_NOTIFICATION (will succeed)
              var action0 =
                  new AutomationAction(
                      rule.getId(),
                      0,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "recipientType", "TRIGGER_ACTOR",
                          "title", "Before failure",
                          "message", "This should succeed"),
                      null,
                      null);
              // Action 1: UPDATE_STATUS missing targetEntityType (will fail)
              var action1 =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.UPDATE_STATUS,
                      Map.of("targetStatus", "DONE"),
                      null,
                      null);
              // Action 2: SEND_NOTIFICATION (will succeed)
              var action2 =
                  new AutomationAction(
                      rule.getId(),
                      2,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "recipientType", "TRIGGER_ACTOR",
                          "title", "After failure",
                          "message", "This should also succeed"),
                      null,
                      null);
              actionRepository.save(action0);
              actionRepository.save(action1);
              actionRepository.save(action2);

              var event = taskStatusChangedEvent("OPEN", "DONE");
              eventPublisher.publishEvent(event);

              // Overall execution should be ACTIONS_FAILED
              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).hasSize(1);
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_FAILED);
              assertThat(executions.getFirst().getErrorMessage()).isNotNull();

              // Verify individual action execution statuses
              var actionExecs =
                  actionExecutionRepository.findByExecutionId(executions.getFirst().getId());
              assertThat(actionExecs).hasSize(3);

              // Sort by action sortOrder using a single batch lookup
              var actionIds = actionExecs.stream().map(ActionExecution::getActionId).toList();
              var actionSortOrders =
                  actionRepository.findAllById(actionIds).stream()
                      .collect(
                          java.util.stream.Collectors.toMap(
                              AutomationAction::getId, AutomationAction::getSortOrder));
              var sortedExecs =
                  actionExecs.stream()
                      .sorted(
                          java.util.Comparator.comparingInt(
                              ae -> actionSortOrders.getOrDefault(ae.getActionId(), 0)))
                      .toList();

              assertThat(sortedExecs.get(0).getStatus()).isEqualTo(ActionExecutionStatus.COMPLETED);
              assertThat(sortedExecs.get(1).getStatus()).isEqualTo(ActionExecutionStatus.FAILED);
              assertThat(sortedExecs.get(1).getErrorMessage()).contains("Missing targetEntityType");
              assertThat(sortedExecs.get(2).getStatus()).isEqualTo(ActionExecutionStatus.COMPLETED);

              // Verify AUTOMATION_ACTION_FAILED notification was sent
              assertThat(
                      notificationRepository.existsByTypeAndReferenceEntityId(
                          "AUTOMATION_ACTION_FAILED", rule.getId()))
                  .isTrue();
            });
  }

  // --- 288.8 Rule lifecycle effects on scheduled actions ---

  @Test
  void ruleLifecycle_deleteRuleCancelsScheduledActions() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, actorMemberId)
        .run(
            () -> {
              var rule =
                  createRule("Delete rule test", TriggerType.TASK_STATUS_CHANGED, Map.of(), null);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      0,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "recipientType", "TRIGGER_ACTOR",
                          "title", "Test",
                          "message", "Scheduled"),
                      null,
                      null);
              actionRepository.save(action);

              var execution =
                  new AutomationExecution(
                      rule.getId(),
                      "TaskStatusChangedEvent",
                      Map.of("eventType", "test"),
                      true,
                      ExecutionStatus.TRIGGERED);
              executionRepository.save(execution);

              var actionExecution =
                  new ActionExecution(
                      execution.getId(),
                      action.getId(),
                      ActionExecutionStatus.SCHEDULED,
                      Instant.now().plus(1, ChronoUnit.HOURS));
              actionExecutionRepository.save(actionExecution);
              UUID actionExecId = actionExecution.getId();
              UUID ruleId = rule.getId();

              // Verify the SCHEDULED action exists before deletion
              assertThat(actionExecutionRepository.findById(actionExecId)).isPresent();
              assertThat(actionExecutionRepository.findById(actionExecId).get().getStatus())
                  .isEqualTo(ActionExecutionStatus.SCHEDULED);

              // Delete the rule — cancels scheduled actions, then cascade-deletes
              // the execution records along with rule and action definitions
              automationRuleService.deleteRule(ruleId);

              // Verify rule is gone
              assertThat(ruleRepository.findById(ruleId)).isEmpty();

              // Action execution records are cascade-deleted (rule -> execution ->
              // action_execution)
              assertThat(actionExecutionRepository.findById(actionExecId)).isEmpty();
            });
  }

  @Test
  void ruleLifecycle_disabledRuleScheduledActionsCancelledByScheduler() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "Disable rule scheduler test",
                      TriggerType.TASK_STATUS_CHANGED,
                      Map.of(),
                      null);
              rule.toggle(); // disable
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      0,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "recipientType", "TRIGGER_ACTOR",
                          "title", "Test",
                          "message", "Should be cancelled"),
                      null,
                      null);
              actionRepository.save(action);

              var execution =
                  new AutomationExecution(
                      rule.getId(),
                      "TaskStatusChangedEvent",
                      Map.of("eventType", "test"),
                      true,
                      ExecutionStatus.TRIGGERED);
              executionRepository.save(execution);

              var actionExecution =
                  new ActionExecution(
                      execution.getId(),
                      action.getId(),
                      ActionExecutionStatus.SCHEDULED,
                      Instant.now().minus(1, ChronoUnit.HOURS));
              Map<String, Object> contextData = new LinkedHashMap<>();
              contextData.put("_context", Map.of("actor", Map.of("id", actorMemberId.toString())));
              actionExecution.storeContext(contextData);
              actionExecutionRepository.save(actionExecution);

              // Run scheduler — should cancel since rule is disabled
              automationScheduler.pollDelayedActions();

              var updated = actionExecutionRepository.findById(actionExecution.getId());
              assertThat(updated).isPresent();
              assertThat(updated.get().getStatus()).isEqualTo(ActionExecutionStatus.CANCELLED);
            });
  }

  @Test
  void ruleLifecycle_toggleOffThenOnNewEventsProcessed() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule("Toggle off/on test", TriggerType.TASK_STATUS_CHANGED, Map.of(), null);
              ruleRepository.save(rule);

              // Disable the rule
              rule.toggle();
              ruleRepository.save(rule);

              // Publish event — should NOT trigger (rule is disabled)
              var event1 = taskStatusChangedEvent("OPEN", "IN_PROGRESS");
              eventPublisher.publishEvent(event1);

              var execDisabled = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(execDisabled).isEmpty();

              // Re-enable the rule
              rule.toggle();
              ruleRepository.save(rule);

              // Publish event — should trigger again
              var event2 = taskStatusChangedEvent("IN_PROGRESS", "DONE");
              eventPublisher.publishEvent(event2);

              var execEnabled = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(execEnabled).hasSize(1);
              assertThat(execEnabled.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_COMPLETED);
            });
  }

  // --- Helper methods ---

  private AutomationRule createRule(
      String name,
      TriggerType triggerType,
      Map<String, Object> triggerConfig,
      List<Map<String, Object>> conditions) {
    return new AutomationRule(
        name,
        "E2E test rule: " + name,
        triggerType,
        triggerConfig,
        conditions,
        RuleSource.CUSTOM,
        null,
        actorMemberId);
  }

  private TaskStatusChangedEvent taskStatusChangedEvent(String oldStatus, String newStatus) {
    return new TaskStatusChangedEvent(
        "TASK_STATUS_CHANGED",
        "Task",
        UUID.randomUUID(),
        projectId,
        actorMemberId,
        ACTOR_NAME,
        schemaName,
        ORG_ID,
        Instant.now(),
        Map.of(),
        oldStatus,
        newStatus,
        null,
        "E2E Test Task",
        null);
  }

  private Map<String, Map<String, Object>> buildMinimalContext() {
    var context = new LinkedHashMap<String, Map<String, Object>>();

    var task = new LinkedHashMap<String, Object>();
    task.put("id", UUID.randomUUID().toString());
    task.put("name", "Test Task");
    task.put("status", "DONE");
    task.put("previousStatus", "OPEN");
    task.put("projectId", UUID.randomUUID().toString());
    context.put("task", task);

    var project = new LinkedHashMap<String, Object>();
    project.put("id", UUID.randomUUID().toString());
    project.put("name", "Test Project");
    context.put("project", project);

    var actor = new LinkedHashMap<String, Object>();
    actor.put("id", UUID.randomUUID().toString());
    actor.put("name", ACTOR_NAME);
    context.put("actor", actor);

    return context;
  }
}
