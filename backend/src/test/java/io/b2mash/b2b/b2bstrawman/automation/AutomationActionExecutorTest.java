package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionFailure;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionSuccess;
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
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.time.Instant;
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
class AutomationActionExecutorTest {

  private static final String ORG_ID = "org_automation_action_test";
  private static final String ACTOR_NAME = "Test Actor";

  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private AutomationActionRepository actionRepository;
  @Autowired private AutomationExecutionRepository executionRepository;
  @Autowired private ActionExecutionRepository actionExecutionRepository;
  @Autowired private AutomationActionExecutor automationActionExecutor;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private ProjectMemberRepository projectMemberRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;

  private String schemaName;
  private UUID actorMemberId;
  private UUID projectId;

  @BeforeAll
  void provisionTenant() {
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "Automation Action Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Create a member
              var member =
                  new Member(
                      "clerk_action_test",
                      "action@test.com",
                      "Action Actor",
                      null,
                      orgRoleRepository.findBySlug("owner").orElseThrow());
              memberRepository.save(member);
              actorMemberId = member.getId();

              // Create a customer
              var customer =
                  TestCustomerFactory.createActiveCustomer(
                      "Test Customer", "customer@test.com", actorMemberId);
              customerRepository.save(customer);

              // Create a project
              var project = new Project("Automation Test Project", null, actorMemberId);
              project.setCustomerId(customer.getId());
              projectRepository.save(project);
              projectId = project.getId();

              // Add member to project as LEAD
              var pm = new ProjectMember(projectId, actorMemberId, "LEAD", actorMemberId);
              projectMemberRepository.save(pm);
            });
  }

  @Test
  void createTaskAction_happyPath() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Create task rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.CREATE_TASK,
                      Map.of(
                          "taskName",
                          "Follow-up: {{task.name}}",
                          "taskDescription",
                          "Auto-created follow-up",
                          "assignTo",
                          "TRIGGER_ACTOR"),
                      null,
                      null);
              actionRepository.save(action);

              var context = buildTaskContext();
              UUID executionId = createExecution(rule.getId());
              var result = automationActionExecutor.execute(action, executionId, context);

              assertThat(result).isInstanceOf(ActionSuccess.class);
              var success = (ActionSuccess) result;
              assertThat(success.resultData()).containsKey("createdTaskId");

              // Verify ActionExecution was recorded
              var actionExecutions = actionExecutionRepository.findByExecutionId(executionId);
              assertThat(actionExecutions).isNotEmpty();
              assertThat(actionExecutions.getFirst().getStatus())
                  .isEqualTo(ActionExecutionStatus.COMPLETED);
            });
  }

  @Test
  void createTaskAction_variableSubstitutionInName() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Variable sub rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.CREATE_TASK,
                      Map.of(
                          "taskName",
                          "Review: {{task.name}} by {{actor.name}}",
                          "taskDescription",
                          "Task {{task.status}}",
                          "assignTo",
                          "UNASSIGNED"),
                      null,
                      null);
              actionRepository.save(action);

              var context = buildTaskContext();
              UUID executionId = createExecution(rule.getId());
              var result = automationActionExecutor.execute(action, executionId, context);

              assertThat(result).isInstanceOf(ActionSuccess.class);
              // Verify the task was created with resolved name
              String taskId = ((ActionSuccess) result).resultData().get("createdTaskId").toString();
              var task = taskRepository.findById(UUID.fromString(taskId)).orElseThrow();
              assertThat(task.getTitle()).isEqualTo("Review: Test Task by " + ACTOR_NAME);
              assertThat(task.getDescription()).isEqualTo("Task COMPLETED");
            });
  }

  @Test
  void sendNotificationAction_happyPath() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Notification rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "recipientType",
                          "TRIGGER_ACTOR",
                          "title",
                          "Task {{task.name}} completed",
                          "message",
                          "The task has been completed by {{actor.name}}"),
                      null,
                      null);
              actionRepository.save(action);

              long notifCountBefore = notificationRepository.count();
              var context = buildTaskContext();
              UUID executionId = createExecution(rule.getId());
              var result = automationActionExecutor.execute(action, executionId, context);

              assertThat(result).isInstanceOf(ActionSuccess.class);
              assertThat(notificationRepository.count()).isGreaterThan(notifCountBefore);
            });
  }

  @Test
  void sendNotificationAction_allAdmins() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Admin notif rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "recipientType",
                          "ALL_ADMINS",
                          "title",
                          "Admin alert",
                          "message",
                          "Something happened"),
                      null,
                      null);
              actionRepository.save(action);

              var context = buildTaskContext();
              UUID executionId = createExecution(rule.getId());
              var result = automationActionExecutor.execute(action, executionId, context);

              assertThat(result).isInstanceOf(ActionSuccess.class);
              var success = (ActionSuccess) result;
              assertThat(success.resultData()).containsKey("notificationsSent");
            });
  }

  @Test
  void unknownActionType_returnsFailure() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Unknown action rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              // UPDATE_STATUS with invalid config (missing targetEntityType)
              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.UPDATE_STATUS,
                      Map.of("targetStatus", "COMPLETED"),
                      null,
                      null);
              actionRepository.save(action);

              var context = buildTaskContext();
              UUID executionId = createExecution(rule.getId());
              var result = automationActionExecutor.execute(action, executionId, context);

              assertThat(result).isInstanceOf(ActionFailure.class);
              var failure = (ActionFailure) result;
              assertThat(failure.errorMessage()).contains("Missing targetEntityType");
            });
  }

  @Test
  void actionExecutionPersistedWithCorrectStatus_completed() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Persistence test rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "recipientType",
                          "TRIGGER_ACTOR",
                          "title",
                          "Test",
                          "message",
                          "Test message"),
                      null,
                      null);
              actionRepository.save(action);

              UUID executionId = createExecution(rule.getId());
              var context = buildTaskContext();
              automationActionExecutor.execute(action, executionId, context);

              var actionExecs = actionExecutionRepository.findByExecutionId(executionId);
              assertThat(actionExecs).hasSize(1);
              assertThat(actionExecs.getFirst().getStatus())
                  .isEqualTo(ActionExecutionStatus.COMPLETED);
              assertThat(actionExecs.getFirst().getResultData()).isNotNull();
              assertThat(actionExecs.getFirst().getExecutedAt()).isNotNull();
            });
  }

  @Test
  void actionExecutionPersistedWithCorrectStatus_failed() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Failure persistence rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.UPDATE_STATUS,
                      Map.of("targetStatus", "DONE"),
                      null,
                      null);
              actionRepository.save(action);

              UUID executionId = createExecution(rule.getId());
              var context = buildTaskContext();
              automationActionExecutor.execute(action, executionId, context);

              var actionExecs = actionExecutionRepository.findByExecutionId(executionId);
              assertThat(actionExecs).hasSize(1);
              assertThat(actionExecs.getFirst().getStatus())
                  .isEqualTo(ActionExecutionStatus.FAILED);
              assertThat(actionExecs.getFirst().getErrorMessage()).isNotNull();
            });
  }

  @Test
  void endToEnd_eventTriggersRuleWithActions() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("E2E action rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "recipientType",
                          "TRIGGER_ACTOR",
                          "title",
                          "Task completed: {{task.name}}",
                          "message",
                          "Done"),
                      null,
                      null);
              actionRepository.save(action);

              // Publish event to trigger the full pipeline
              var event =
                  new TaskStatusChangedEvent(
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
                      "OPEN",
                      "COMPLETED",
                      null,
                      "E2E Test Task",
                      null);

              eventPublisher.publishEvent(event);

              // Verify execution was completed
              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).isNotEmpty();
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_COMPLETED);
            });
  }

  @Test
  void endToEnd_failedActionSetsExecutionToActionsFailed() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("E2E failed action rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              // UPDATE_STATUS with invalid config (missing targetEntityType) -> will fail
              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.UPDATE_STATUS,
                      Map.of("targetStatus", "DONE"),
                      null,
                      null);
              actionRepository.save(action);

              var event =
                  new TaskStatusChangedEvent(
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
                      "OPEN",
                      "COMPLETED",
                      null,
                      "Failed Action Task",
                      null);

              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).isNotEmpty();
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_FAILED);
              assertThat(executions.getFirst().getErrorMessage()).isNotNull();
            });
  }

  // --- Helper methods ---

  private AutomationRule createRule(String name, TriggerType triggerType) {
    return new AutomationRule(
        name,
        "Test rule: " + name,
        triggerType,
        Map.of(),
        null,
        RuleSource.CUSTOM,
        null,
        actorMemberId);
  }

  private UUID createExecution(UUID ruleId) {
    var execution =
        new AutomationExecution(
            ruleId,
            "TaskStatusChangedEvent",
            Map.of("eventType", "test"),
            true,
            ExecutionStatus.TRIGGERED);
    executionRepository.save(execution);
    return execution.getId();
  }

  private Map<String, Map<String, Object>> buildTaskContext() {
    var context = new LinkedHashMap<String, Map<String, Object>>();

    var task = new LinkedHashMap<String, Object>();
    task.put("id", UUID.randomUUID().toString());
    task.put("name", "Test Task");
    task.put("status", "COMPLETED");
    task.put("previousStatus", "OPEN");
    task.put("projectId", projectId.toString());
    context.put("task", task);

    var project = new LinkedHashMap<String, Object>();
    project.put("id", projectId.toString());
    project.put("name", "Automation Test Project");
    context.put("project", project);

    var actor = new LinkedHashMap<String, Object>();
    actor.put("id", actorMemberId.toString());
    actor.put("name", ACTOR_NAME);
    context.put("actor", actor);

    var rule = new LinkedHashMap<String, Object>();
    rule.put("id", UUID.randomUUID().toString());
    rule.put("name", "Test Rule");
    context.put("rule", rule);

    return context;
  }
}
