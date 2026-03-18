package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutomationSchedulerTest {

  private static final String ORG_ID = "org_automation_scheduler_test";
  private static final String ORG_ID_2 = "org_automation_scheduler_test_2";
  private static final UUID ACTOR_MEMBER_ID = UUID.randomUUID();
  private static final String ACTOR_NAME = "Scheduler Test Actor";

  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private AutomationExecutionRepository executionRepository;
  @Autowired private AutomationActionRepository actionRepository;
  @Autowired private ActionExecutionRepository actionExecutionRepository;
  @Autowired private AutomationScheduler automationScheduler;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private ApplicationEventPublisher eventPublisher;

  @Autowired
  private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

  private String schemaName;
  private String schemaName2;

  @BeforeAll
  void provisionTenants() {
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "Scheduler Test Org", null).schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    disableSeededRules(schemaName, ORG_ID);

    schemaName2 =
        provisioningService.provisionTenant(ORG_ID_2, "Scheduler Test Org 2", null).schemaName();
    planSyncService.syncPlan(ORG_ID_2, "pro-plan");
    disableSeededRules(schemaName2, ORG_ID_2);
  }

  private void disableSeededRules(String schema, String orgId) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      ruleRepository.findAllByOrderByCreatedAtDesc().stream()
                          .filter(r -> r.getSource() == RuleSource.TEMPLATE && r.isEnabled())
                          .forEach(
                              r -> {
                                r.toggle();
                                ruleRepository.save(r);
                              });
                    }));
  }

  // --- Scheduler Tests (283.9) ---

  @Test
  void scheduler_picksUpDueScheduledActionAndExecutes() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Create a rule with a SEND_NOTIFICATION action
              var rule = createRule("Due scheduled action rule");
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "title", "Delayed notification",
                          "message", "This was delayed",
                          "recipientType", "TRIGGER_ACTOR"),
                      null,
                      null);
              actionRepository.save(action);

              // Create an execution
              var execution =
                  new AutomationExecution(
                      rule.getId(),
                      "TaskStatusChangedEvent",
                      Map.of("eventType", "test"),
                      true,
                      ExecutionStatus.TRIGGERED);
              executionRepository.save(execution);

              // Create a SCHEDULED action execution that is due
              var actionExecution =
                  new ActionExecution(
                      execution.getId(),
                      action.getId(),
                      ActionExecutionStatus.SCHEDULED,
                      Instant.now().minus(1, ChronoUnit.HOURS));
              // Store context with actor info
              Map<String, Object> contextData = new LinkedHashMap<>();
              Map<String, Object> actorMap = new LinkedHashMap<>();
              actorMap.put("id", ACTOR_MEMBER_ID.toString());
              actorMap.put("name", ACTOR_NAME);
              contextData.put("_context", Map.of("actor", actorMap));
              actionExecution.storeContext(contextData);
              actionExecutionRepository.save(actionExecution);

              // Run the scheduler
              automationScheduler.pollDelayedActions();

              // Verify the action execution was processed (COMPLETED or FAILED — not SCHEDULED)
              var updated = actionExecutionRepository.findById(actionExecution.getId());
              assertThat(updated).isPresent();
              assertThat(updated.get().getStatus()).isNotEqualTo(ActionExecutionStatus.SCHEDULED);
            });
  }

  @Test
  void scheduler_ignoresActionNotYetDue() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Future scheduled action rule");
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "title", "Future notification",
                          "message", "Not yet",
                          "recipientType", "TRIGGER_ACTOR"),
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

              // Scheduled in the future — should not be picked up
              var actionExecution =
                  new ActionExecution(
                      execution.getId(),
                      action.getId(),
                      ActionExecutionStatus.SCHEDULED,
                      Instant.now().plus(2, ChronoUnit.HOURS));
              actionExecutionRepository.save(actionExecution);

              automationScheduler.pollDelayedActions();

              var updated = actionExecutionRepository.findById(actionExecution.getId());
              assertThat(updated).isPresent();
              assertThat(updated.get().getStatus()).isEqualTo(ActionExecutionStatus.SCHEDULED);
            });
  }

  @Test
  void scheduler_cancelsActionWhenRuleIsDisabled() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Disabled rule for scheduler");
              rule.toggle(); // disable
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "title", "Test",
                          "message", "Test",
                          "recipientType", "TRIGGER_ACTOR"),
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
              contextData.put(
                  "_context", Map.of("actor", Map.of("id", ACTOR_MEMBER_ID.toString())));
              actionExecution.storeContext(contextData);
              actionExecutionRepository.save(actionExecution);

              automationScheduler.pollDelayedActions();

              var updated = actionExecutionRepository.findById(actionExecution.getId());
              assertThat(updated).isPresent();
              assertThat(updated.get().getStatus()).isEqualTo(ActionExecutionStatus.CANCELLED);
            });
  }

  @Test
  void scheduler_handlesDeletedActionDefinition() {
    // When an automation_action is deleted, action_id on the action_execution is SET NULL
    // (per FK ON DELETE SET NULL). The scheduler should mark such actions as FAILED.
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Deleted action test rule");
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "title", "Test",
                          "message", "Test",
                          "recipientType", "TRIGGER_ACTOR"),
                      null,
                      null);
              actionRepository.save(action);
              UUID actionId = action.getId();

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
                      actionId,
                      ActionExecutionStatus.SCHEDULED,
                      Instant.now().minus(1, ChronoUnit.HOURS));
              Map<String, Object> contextData = new LinkedHashMap<>();
              contextData.put(
                  "_context", Map.of("actor", Map.of("id", ACTOR_MEMBER_ID.toString())));
              actionExecution.storeContext(contextData);
              actionExecutionRepository.save(actionExecution);

              // Delete the action (ON DELETE SET NULL sets action_id to null)
              transactionTemplate.executeWithoutResult(
                  tx -> actionRepository.deleteByRuleId(rule.getId()));

              automationScheduler.pollDelayedActions();

              var updated = actionExecutionRepository.findById(actionExecution.getId());
              assertThat(updated).isPresent();
              assertThat(updated.get().getStatus()).isEqualTo(ActionExecutionStatus.FAILED);
            });
  }

  @Test
  void scheduler_handlesExecutionFailureGracefully() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Failure test rule");
              ruleRepository.save(rule);

              // Action with invalid config to trigger failure
              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.UPDATE_STATUS,
                      Map.of("targetStatus", "INVALID"),
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
              contextData.put(
                  "_context", Map.of("actor", Map.of("id", ACTOR_MEMBER_ID.toString())));
              actionExecution.storeContext(contextData);
              actionExecutionRepository.save(actionExecution);

              automationScheduler.pollDelayedActions();

              var updated = actionExecutionRepository.findById(actionExecution.getId());
              assertThat(updated).isPresent();
              assertThat(updated.get().getStatus()).isEqualTo(ActionExecutionStatus.FAILED);
            });
  }

  @Test
  void scheduler_processesMultipleTenantsIndependently() {
    // Create scheduled action in tenant 1
    UUID actionExecId1 =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
            .where(RequestScopes.ORG_ID, ORG_ID)
            .call(
                () -> {
                  var rule = createRule("Multi-tenant rule 1");
                  ruleRepository.save(rule);

                  var action =
                      new AutomationAction(
                          rule.getId(),
                          1,
                          ActionType.SEND_NOTIFICATION,
                          Map.of(
                              "title", "T1 notification",
                              "message", "Tenant 1",
                              "recipientType", "TRIGGER_ACTOR"),
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
                  contextData.put(
                      "_context", Map.of("actor", Map.of("id", ACTOR_MEMBER_ID.toString())));
                  actionExecution.storeContext(contextData);
                  actionExecutionRepository.save(actionExecution);
                  return actionExecution.getId();
                });

    // Create scheduled action in tenant 2
    UUID actionExecId2 =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaName2)
            .where(RequestScopes.ORG_ID, ORG_ID_2)
            .call(
                () -> {
                  var rule = createRule("Multi-tenant rule 2");
                  ruleRepository.save(rule);

                  var action =
                      new AutomationAction(
                          rule.getId(),
                          1,
                          ActionType.SEND_NOTIFICATION,
                          Map.of(
                              "title", "T2 notification",
                              "message", "Tenant 2",
                              "recipientType", "TRIGGER_ACTOR"),
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
                  contextData.put(
                      "_context", Map.of("actor", Map.of("id", ACTOR_MEMBER_ID.toString())));
                  actionExecution.storeContext(contextData);
                  actionExecutionRepository.save(actionExecution);
                  return actionExecution.getId();
                });

    // Run scheduler — processes both tenants
    automationScheduler.pollDelayedActions();

    // Verify both were processed (no longer SCHEDULED)
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var updated = actionExecutionRepository.findById(actionExecId1);
              assertThat(updated).isPresent();
              assertThat(updated.get().getStatus()).isNotEqualTo(ActionExecutionStatus.SCHEDULED);
            });

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName2)
        .where(RequestScopes.ORG_ID, ORG_ID_2)
        .run(
            () -> {
              var updated = actionExecutionRepository.findById(actionExecId2);
              assertThat(updated).isPresent();
              assertThat(updated.get().getStatus()).isNotEqualTo(ActionExecutionStatus.SCHEDULED);
            });
  }

  @Test
  void scheduler_errorInOneTenantDoesNotAffectOthers() {
    // Create a valid scheduled action in tenant 2
    UUID actionExecId =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaName2)
            .where(RequestScopes.ORG_ID, ORG_ID_2)
            .call(
                () -> {
                  var rule = createRule("Isolation test rule");
                  ruleRepository.save(rule);

                  var action =
                      new AutomationAction(
                          rule.getId(),
                          1,
                          ActionType.SEND_NOTIFICATION,
                          Map.of(
                              "title", "Isolation test",
                              "message", "Should still execute",
                              "recipientType", "TRIGGER_ACTOR"),
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
                  contextData.put(
                      "_context", Map.of("actor", Map.of("id", ACTOR_MEMBER_ID.toString())));
                  actionExecution.storeContext(contextData);
                  actionExecutionRepository.save(actionExecution);
                  return actionExecution.getId();
                });

    // Run scheduler — tenant 1 may have errors from previous tests but tenant 2 should still work
    automationScheduler.pollDelayedActions();

    // Verify tenant 2 action was processed
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName2)
        .where(RequestScopes.ORG_ID, ORG_ID_2)
        .run(
            () -> {
              var updated = actionExecutionRepository.findById(actionExecId);
              assertThat(updated).isPresent();
              assertThat(updated.get().getStatus()).isNotEqualTo(ActionExecutionStatus.SCHEDULED);
            });
  }

  @Test
  void delayedAction_contextIsSerializedAndDeserializedCorrectly() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Context serialization rule");
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "title", "Context test",
                          "message", "With full context",
                          "recipientType", "TRIGGER_ACTOR"),
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

              // Create scheduled action with rich context
              UUID projectId = UUID.randomUUID();
              UUID taskId = UUID.randomUUID();
              var actionExecution =
                  new ActionExecution(
                      execution.getId(),
                      action.getId(),
                      ActionExecutionStatus.SCHEDULED,
                      Instant.now().minus(1, ChronoUnit.HOURS));

              Map<String, Object> contextData = new LinkedHashMap<>();
              Map<String, Object> contextMap = new LinkedHashMap<>();
              contextMap.put("actor", Map.of("id", ACTOR_MEMBER_ID.toString(), "name", ACTOR_NAME));
              contextMap.put(
                  "task",
                  Map.of(
                      "id", taskId.toString(),
                      "projectId", projectId.toString(),
                      "title", "Test Task"));
              contextMap.put("project", Map.of("id", projectId.toString()));
              contextData.put("_context", contextMap);
              actionExecution.storeContext(contextData);
              actionExecutionRepository.save(actionExecution);

              automationScheduler.pollDelayedActions();

              // Verify the action was processed (not still SCHEDULED)
              var updated = actionExecutionRepository.findById(actionExecution.getId());
              assertThat(updated).isPresent();
              assertThat(updated.get().getStatus()).isNotEqualTo(ActionExecutionStatus.SCHEDULED);
            });
  }

  // --- Cycle Detection Tests (283.10) ---

  @Test
  void cycleDetection_eventWithAutomationExecutionIdViaInterfaceIsSkipped() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Cycle detection interface test");
              ruleRepository.save(rule);

              UUID automationExecId = UUID.randomUUID();

              // Event with automationExecutionId set via the record field (interface method)
              var event =
                  new TaskStatusChangedEvent(
                      "TASK_STATUS_CHANGED",
                      "Task",
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      ACTOR_MEMBER_ID,
                      ACTOR_NAME,
                      schemaName,
                      ORG_ID,
                      Instant.now(),
                      Map.of(),
                      "OPEN",
                      "DONE",
                      null,
                      "Cycle Interface Task",
                      automationExecId);

              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).isEmpty();
            });
  }

  @Test
  void cycleDetection_eventWithoutAutomationExecutionIdIsProcessed() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("No cycle detection test");
              ruleRepository.save(rule);

              // Event without automationExecutionId — should be processed normally
              var event =
                  new TaskStatusChangedEvent(
                      "TASK_STATUS_CHANGED",
                      "Task",
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      ACTOR_MEMBER_ID,
                      ACTOR_NAME,
                      schemaName,
                      ORG_ID,
                      Instant.now(),
                      Map.of(),
                      "OPEN",
                      "DONE",
                      null,
                      "Normal Task",
                      null);

              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).isNotEmpty();
            });
  }

  @Test
  void cycleDetection_eventWithAutomationExecutionIdInDetailsMapIsSkipped() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Cycle detection details map test");
              ruleRepository.save(rule);

              // Event with automationExecutionId in details map (backward compat)
              var details = new LinkedHashMap<String, Object>();
              details.put("automationExecutionId", UUID.randomUUID().toString());

              var event =
                  new TaskStatusChangedEvent(
                      "TASK_STATUS_CHANGED",
                      "Task",
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      ACTOR_MEMBER_ID,
                      ACTOR_NAME,
                      schemaName,
                      ORG_ID,
                      Instant.now(),
                      details,
                      "OPEN",
                      "DONE",
                      null,
                      "Cycle Details Task",
                      null);

              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).isEmpty();
            });
  }

  @Test
  void cycleDetection_multipleRulesAllSkippedWhenEventHasAutomationExecutionId() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule1 = createRule("Multi-rule cycle test 1");
              var rule2 = createRule("Multi-rule cycle test 2");
              ruleRepository.save(rule1);
              ruleRepository.save(rule2);

              UUID automationExecId = UUID.randomUUID();

              var event =
                  new TaskStatusChangedEvent(
                      "TASK_STATUS_CHANGED",
                      "Task",
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      ACTOR_MEMBER_ID,
                      ACTOR_NAME,
                      schemaName,
                      ORG_ID,
                      Instant.now(),
                      Map.of(),
                      "OPEN",
                      "IN_PROGRESS",
                      null,
                      "Multi-Rule Cycle Task",
                      automationExecId);

              eventPublisher.publishEvent(event);

              var executions1 = executionRepository.findByRuleIdOrderByStartedAtDesc(rule1.getId());
              var executions2 = executionRepository.findByRuleIdOrderByStartedAtDesc(rule2.getId());
              assertThat(executions1).isEmpty();
              assertThat(executions2).isEmpty();
            });
  }

  // --- Helper methods ---

  private AutomationRule createRule(String name) {
    return new AutomationRule(
        name,
        "Test rule: " + name,
        TriggerType.TASK_STATUS_CHANGED,
        Map.of(),
        null,
        RuleSource.CUSTOM,
        null,
        ACTOR_MEMBER_ID);
  }
}
