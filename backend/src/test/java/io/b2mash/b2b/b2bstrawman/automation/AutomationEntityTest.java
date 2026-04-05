package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.automation.config.AssignMemberActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.AssignTo;
import io.b2mash.b2b.b2bstrawman.automation.config.AutomationConfigDeserializer;
import io.b2mash.b2b.b2bstrawman.automation.config.BudgetThresholdTriggerConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.CreateProjectActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.CreateTaskActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.EmptyTriggerConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.SendEmailActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.SendNotificationActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.StatusChangeTriggerConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.UpdateStatusActionConfig;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutomationEntityTest {

  private static final String ORG_ID = "org_automation_entity_test";

  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private AutomationActionRepository actionRepository;
  @Autowired private AutomationExecutionRepository executionRepository;
  @Autowired private ActionExecutionRepository actionExecutionRepository;
  @Autowired private AutomationConfigDeserializer configDeserializer;
  @Autowired private TenantProvisioningService provisioningService;

  private String schemaName;

  @BeforeAll
  void provisionTenant() {
    schemaName =
        provisioningService
            .provisionTenant(ORG_ID, "Automation Entity Test Org", null)
            .schemaName();
  }

  @Test
  void automationRuleJsonbRoundTrip() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var triggerConfig =
                  Map.<String, Object>of("fromStatus", "OPEN", "toStatus", "IN_PROGRESS");
              var conditions =
                  List.of(
                      Map.<String, Object>of(
                          "field", "priority", "operator", "EQUALS", "value", "HIGH"));

              var rule =
                  new AutomationRule(
                      "Test Rule",
                      "A test automation rule",
                      TriggerType.TASK_STATUS_CHANGED,
                      triggerConfig,
                      conditions,
                      RuleSource.CUSTOM,
                      null,
                      UUID.randomUUID());

              var saved = ruleRepository.save(rule);
              ruleRepository.flush();

              var found = ruleRepository.findById(saved.getId()).orElseThrow();
              assertThat(found.getName()).isEqualTo("Test Rule");
              assertThat(found.getDescription()).isEqualTo("A test automation rule");
              assertThat(found.isEnabled()).isTrue();
              assertThat(found.getTriggerType()).isEqualTo(TriggerType.TASK_STATUS_CHANGED);
              assertThat(found.getTriggerConfig()).containsEntry("fromStatus", "OPEN");
              assertThat(found.getTriggerConfig()).containsEntry("toStatus", "IN_PROGRESS");
              assertThat(found.getConditions()).hasSize(1);
              assertThat(found.getConditions().getFirst()).containsEntry("field", "priority");
              assertThat(found.getSource()).isEqualTo(RuleSource.CUSTOM);
              assertThat(found.getCreatedAt()).isNotNull();
              assertThat(found.getUpdatedAt()).isNotNull();
            });
  }

  @Test
  void automationRuleToggle() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var rule =
                  new AutomationRule(
                      "Toggle Rule",
                      null,
                      TriggerType.TIME_ENTRY_CREATED,
                      Map.of(),
                      null,
                      RuleSource.CUSTOM,
                      null,
                      UUID.randomUUID());
              ruleRepository.save(rule);
              assertThat(rule.isEnabled()).isTrue();

              rule.toggle();
              ruleRepository.save(rule);
              ruleRepository.flush();

              var found = ruleRepository.findById(rule.getId()).orElseThrow();
              assertThat(found.isEnabled()).isFalse();
            });
  }

  @Test
  void automationActionJsonbRoundTrip() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var rule =
                  ruleRepository.save(
                      new AutomationRule(
                          "Action Test Rule",
                          null,
                          TriggerType.CUSTOMER_STATUS_CHANGED,
                          Map.of("fromStatus", "PROSPECT", "toStatus", "ACTIVE"),
                          null,
                          RuleSource.CUSTOM,
                          null,
                          UUID.randomUUID()));

              var actionConfig =
                  Map.<String, Object>of(
                      "taskName", "Welcome task",
                      "taskDescription", "Onboard the customer",
                      "assignTo", "PROJECT_OWNER");

              var action =
                  new AutomationAction(
                      rule.getId(), 1, ActionType.CREATE_TASK, actionConfig, null, null);
              actionRepository.save(action);
              actionRepository.flush();

              var found = actionRepository.findById(action.getId()).orElseThrow();
              assertThat(found.getActionType()).isEqualTo(ActionType.CREATE_TASK);
              assertThat(found.getActionConfig()).containsEntry("taskName", "Welcome task");
              assertThat(found.getSortOrder()).isEqualTo(1);
              assertThat(found.getDelayDuration()).isNull();
              assertThat(found.getDelayUnit()).isNull();
            });
  }

  @Test
  void automationActionWithDelay() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var rule =
                  ruleRepository.save(
                      new AutomationRule(
                          "Delay Test Rule",
                          null,
                          TriggerType.INVOICE_STATUS_CHANGED,
                          Map.of("fromStatus", "DRAFT", "toStatus", "SENT"),
                          null,
                          RuleSource.CUSTOM,
                          null,
                          UUID.randomUUID()));

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.SEND_EMAIL,
                      Map.of("recipientType", "CUSTOMER", "subject", "Follow up", "body", "Hi"),
                      2,
                      DelayUnit.DAYS);
              actionRepository.save(action);
              actionRepository.flush();

              var found = actionRepository.findById(action.getId()).orElseThrow();
              assertThat(found.getDelayDuration()).isEqualTo(2);
              assertThat(found.getDelayUnit()).isEqualTo(DelayUnit.DAYS);
            });
  }

  @Test
  void automationExecutionCreationAndStatus() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var rule =
                  ruleRepository.save(
                      new AutomationRule(
                          "Execution Test Rule",
                          null,
                          TriggerType.PROJECT_STATUS_CHANGED,
                          Map.of("fromStatus", "PLANNING", "toStatus", "ACTIVE"),
                          null,
                          RuleSource.CUSTOM,
                          null,
                          UUID.randomUUID()));

              var eventData =
                  Map.<String, Object>of(
                      "projectId", UUID.randomUUID().toString(), "oldStatus", "PLANNING");

              var execution =
                  new AutomationExecution(
                      rule.getId(),
                      "PROJECT_STATUS_CHANGED",
                      eventData,
                      true,
                      ExecutionStatus.TRIGGERED);
              executionRepository.save(execution);
              executionRepository.flush();

              var found = executionRepository.findById(execution.getId()).orElseThrow();
              assertThat(found.getStatus()).isEqualTo(ExecutionStatus.TRIGGERED);
              assertThat(found.isConditionsMet()).isTrue();
              assertThat(found.getTriggerEventData()).containsEntry("oldStatus", "PLANNING");
              assertThat(found.getStartedAt()).isNotNull();
              assertThat(found.getCompletedAt()).isNull();

              execution.complete();
              executionRepository.save(execution);
              executionRepository.flush();

              var completed = executionRepository.findById(execution.getId()).orElseThrow();
              assertThat(completed.getStatus()).isEqualTo(ExecutionStatus.ACTIONS_COMPLETED);
              assertThat(completed.getCompletedAt()).isNotNull();
            });
  }

  @Test
  void actionExecutionScheduledAndStatusTransitions() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var rule =
                  ruleRepository.save(
                      new AutomationRule(
                          "ActionExec Test Rule",
                          null,
                          TriggerType.DOCUMENT_ACCEPTED,
                          Map.of(),
                          null,
                          RuleSource.CUSTOM,
                          null,
                          UUID.randomUUID()));

              var execution =
                  executionRepository.save(
                      new AutomationExecution(
                          rule.getId(),
                          "DOCUMENT_ACCEPTED",
                          Map.of("documentId", "doc-1"),
                          true,
                          ExecutionStatus.TRIGGERED));

              var scheduledFor = Instant.now().plusSeconds(3600);
              var actionExec =
                  new ActionExecution(
                      execution.getId(), null, ActionExecutionStatus.SCHEDULED, scheduledFor);
              actionExecutionRepository.save(actionExec);
              actionExecutionRepository.flush();

              var found = actionExecutionRepository.findById(actionExec.getId()).orElseThrow();
              assertThat(found.getStatus()).isEqualTo(ActionExecutionStatus.SCHEDULED);
              assertThat(found.getScheduledFor()).isNotNull();

              actionExec.complete(Map.of("created", true));
              actionExecutionRepository.save(actionExec);
              actionExecutionRepository.flush();

              var completed = actionExecutionRepository.findById(actionExec.getId()).orElseThrow();
              assertThat(completed.getStatus()).isEqualTo(ActionExecutionStatus.COMPLETED);
              assertThat(completed.getResultData()).containsEntry("created", true);
              assertThat(completed.getExecutedAt()).isNotNull();
            });
  }

  @Test
  void actionExecutionFailure() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var rule =
                  ruleRepository.save(
                      new AutomationRule(
                          "Failure Test Rule",
                          null,
                          TriggerType.TIME_ENTRY_CREATED,
                          Map.of(),
                          null,
                          RuleSource.CUSTOM,
                          null,
                          UUID.randomUUID()));

              var execution =
                  executionRepository.save(
                      new AutomationExecution(
                          rule.getId(),
                          "TIME_ENTRY_CREATED",
                          Map.of("timeEntryId", "te-1"),
                          true,
                          ExecutionStatus.TRIGGERED));

              var actionExec =
                  new ActionExecution(execution.getId(), null, ActionExecutionStatus.PENDING, null);
              actionExecutionRepository.save(actionExec);

              actionExec.fail("Task creation failed", "NullPointerException at line 42");
              actionExecutionRepository.save(actionExec);
              actionExecutionRepository.flush();

              var found = actionExecutionRepository.findById(actionExec.getId()).orElseThrow();
              assertThat(found.getStatus()).isEqualTo(ActionExecutionStatus.FAILED);
              assertThat(found.getErrorMessage()).isEqualTo("Task creation failed");
              assertThat(found.getErrorDetail()).contains("NullPointerException");
            });
  }

  @Test
  void enumsStoredAsStrings() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var rule =
                  ruleRepository.save(
                      new AutomationRule(
                          "Enum Storage Rule",
                          null,
                          TriggerType.BUDGET_THRESHOLD_REACHED,
                          Map.of("thresholdPercent", 80),
                          null,
                          RuleSource.TEMPLATE,
                          "budget-alert",
                          UUID.randomUUID()));
              ruleRepository.flush();

              var found = ruleRepository.findById(rule.getId()).orElseThrow();
              assertThat(found.getTriggerType()).isEqualTo(TriggerType.BUDGET_THRESHOLD_REACHED);
              assertThat(found.getSource()).isEqualTo(RuleSource.TEMPLATE);
              assertThat(found.getTemplateSlug()).isEqualTo("budget-alert");
            });
  }

  @Test
  void findByEnabledAndTriggerType() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var rule1 =
                  ruleRepository.save(
                      new AutomationRule(
                          "Enabled Rule",
                          null,
                          TriggerType.INFORMATION_REQUEST_COMPLETED,
                          Map.of(),
                          null,
                          RuleSource.CUSTOM,
                          null,
                          UUID.randomUUID()));

              var rule2 =
                  new AutomationRule(
                      "Disabled Rule",
                      null,
                      TriggerType.INFORMATION_REQUEST_COMPLETED,
                      Map.of(),
                      null,
                      RuleSource.CUSTOM,
                      null,
                      UUID.randomUUID());
              rule2.toggle(); // disable
              ruleRepository.save(rule2);
              ruleRepository.flush();

              var enabled =
                  ruleRepository.findByEnabledAndTriggerType(
                      true, TriggerType.INFORMATION_REQUEST_COMPLETED);
              assertThat(enabled).extracting(AutomationRule::getName).contains("Enabled Rule");
              assertThat(enabled)
                  .extracting(AutomationRule::getName)
                  .doesNotContain("Disabled Rule");
            });
  }

  @Test
  void configDeserializerTriggerTypes() {
    // StatusChangeTriggerConfig
    var statusConfig =
        configDeserializer.deserializeTriggerConfig(
            TriggerType.TASK_STATUS_CHANGED, Map.of("fromStatus", "OPEN", "toStatus", "CLOSED"));
    assertThat(statusConfig).isInstanceOf(StatusChangeTriggerConfig.class);
    var sc = (StatusChangeTriggerConfig) statusConfig;
    assertThat(sc.fromStatus()).isEqualTo("OPEN");
    assertThat(sc.toStatus()).isEqualTo("CLOSED");

    // BudgetThresholdTriggerConfig
    var budgetConfig =
        configDeserializer.deserializeTriggerConfig(
            TriggerType.BUDGET_THRESHOLD_REACHED, Map.of("thresholdPercent", 90));
    assertThat(budgetConfig).isInstanceOf(BudgetThresholdTriggerConfig.class);
    assertThat(((BudgetThresholdTriggerConfig) budgetConfig).thresholdPercent()).isEqualTo(90);

    // EmptyTriggerConfig
    var emptyConfig =
        configDeserializer.deserializeTriggerConfig(TriggerType.TIME_ENTRY_CREATED, Map.of());
    assertThat(emptyConfig).isInstanceOf(EmptyTriggerConfig.class);
  }

  @Test
  void configDeserializerActionTypes() {
    // CreateTaskActionConfig
    var taskConfig =
        configDeserializer.deserializeActionConfig(
            ActionType.CREATE_TASK,
            Map.of(
                "taskName", "Follow up",
                "taskDescription", "Check in",
                "assignTo", "PROJECT_OWNER",
                "taskStatus", "TODO"));
    assertThat(taskConfig).isInstanceOf(CreateTaskActionConfig.class);
    var tc = (CreateTaskActionConfig) taskConfig;
    assertThat(tc.taskName()).isEqualTo("Follow up");
    assertThat(tc.assignTo()).isEqualTo(AssignTo.PROJECT_OWNER);

    // SendNotificationActionConfig
    var notifConfig =
        configDeserializer.deserializeActionConfig(
            ActionType.SEND_NOTIFICATION,
            Map.of("recipientType", "OWNER", "title", "Alert", "message", "Something happened"));
    assertThat(notifConfig).isInstanceOf(SendNotificationActionConfig.class);

    // SendEmailActionConfig
    var emailConfig =
        configDeserializer.deserializeActionConfig(
            ActionType.SEND_EMAIL,
            Map.of("recipientType", "CUSTOMER", "subject", "Update", "body", "Dear customer"));
    assertThat(emailConfig).isInstanceOf(SendEmailActionConfig.class);

    // UpdateStatusActionConfig
    var statusConfig =
        configDeserializer.deserializeActionConfig(
            ActionType.UPDATE_STATUS, Map.of("targetEntityType", "PROJECT", "newStatus", "ACTIVE"));
    assertThat(statusConfig).isInstanceOf(UpdateStatusActionConfig.class);

    // CreateProjectActionConfig
    var projectConfig =
        configDeserializer.deserializeActionConfig(
            ActionType.CREATE_PROJECT,
            Map.of(
                "projectTemplateId",
                UUID.randomUUID().toString(),
                "projectName",
                "New Project",
                "linkToCustomer",
                true));
    assertThat(projectConfig).isInstanceOf(CreateProjectActionConfig.class);

    // AssignMemberActionConfig
    var assignConfig =
        configDeserializer.deserializeActionConfig(
            ActionType.ASSIGN_MEMBER, Map.of("assignTo", "SPECIFIC_MEMBER", "role", "DEVELOPER"));
    assertThat(assignConfig).isInstanceOf(AssignMemberActionConfig.class);
    assertThat(((AssignMemberActionConfig) assignConfig).assignTo())
        .isEqualTo(AssignTo.SPECIFIC_MEMBER);
  }

  @Test
  void findExecutionsByRuleOrderByStartedAtDesc() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var rule =
                  ruleRepository.save(
                      new AutomationRule(
                          "Exec Order Rule",
                          null,
                          TriggerType.TASK_STATUS_CHANGED,
                          Map.of("fromStatus", "A", "toStatus", "B"),
                          null,
                          RuleSource.CUSTOM,
                          null,
                          UUID.randomUUID()));

              executionRepository.save(
                  new AutomationExecution(
                      rule.getId(),
                      "TASK_STATUS_CHANGED",
                      Map.of("first", true),
                      true,
                      ExecutionStatus.ACTIONS_COMPLETED));
              executionRepository.save(
                  new AutomationExecution(
                      rule.getId(),
                      "TASK_STATUS_CHANGED",
                      Map.of("second", true),
                      true,
                      ExecutionStatus.TRIGGERED));
              executionRepository.flush();

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).hasSizeGreaterThanOrEqualTo(2);
            });
  }
}
