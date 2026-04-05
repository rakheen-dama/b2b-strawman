package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
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
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplate;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskStatus;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutomationActionExecutorExtendedTest {

  private static final String ORG_ID = "org_automation_extended_test";
  private static final String ACTOR_NAME = "Extended Test Actor";

  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private AutomationActionRepository actionRepository;
  @Autowired private AutomationExecutionRepository executionRepository;
  @Autowired private ActionExecutionRepository actionExecutionRepository;
  @Autowired private AutomationActionExecutor automationActionExecutor;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private ProjectMemberRepository projectMemberRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private ProjectTemplateRepository projectTemplateRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;

  private String schemaName;
  private UUID actorMemberId;
  private UUID projectId;
  private UUID taskId;
  private UUID templateId;
  private UUID secondMemberId;

  @BeforeAll
  void provisionTenant() {
    schemaName =
        provisioningService
            .provisionTenant(ORG_ID, "Automation Extended Test Org", null)
            .schemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Create actor member (owner)
              var member =
                  new Member(
                      "clerk_ext_test",
                      "ext@test.com",
                      "Extended Actor",
                      null,
                      orgRoleRepository.findBySlug("owner").orElseThrow());
              memberRepository.save(member);
              actorMemberId = member.getId();

              // Create a second member for assignment tests
              var member2 =
                  new Member(
                      "clerk_ext_test2",
                      "ext2@test.com",
                      "Second Member",
                      null,
                      orgRoleRepository.findBySlug("member").orElseThrow());
              memberRepository.save(member2);
              secondMemberId = member2.getId();

              // Create a customer
              var customer =
                  TestCustomerFactory.createActiveCustomer(
                      "Ext Test Customer", "extcust@test.com", actorMemberId);
              customerRepository.save(customer);

              // Create a project
              var project = new Project("Extended Test Project", null, actorMemberId);
              project.setCustomerId(customer.getId());
              projectRepository.save(project);
              projectId = project.getId();

              // Add actor member to project as LEAD
              var pm = new ProjectMember(projectId, actorMemberId, "LEAD", actorMemberId);
              projectMemberRepository.save(pm);

              // Create a task in OPEN status
              var task =
                  new Task(
                      projectId, "Test Task", "Description", "MEDIUM", null, null, actorMemberId);
              taskRepository.save(task);
              taskId = task.getId();

              // Create a project template for CreateProject tests
              var template =
                  new ProjectTemplate(
                      "Test Template",
                      "{{name}}",
                      "Template desc",
                      true,
                      "MANUAL",
                      null,
                      actorMemberId);
              projectTemplateRepository.save(template);
              templateId = template.getId();
            });
  }

  @BeforeEach
  void cleanupAutomationData() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              actionExecutionRepository.deleteAll();
              executionRepository.deleteAll();
              actionRepository.deleteAll();
              ruleRepository.deleteAll();
              notificationRepository.deleteAll();
            });
  }

  // --- 282.9: UpdateStatusActionExecutor Tests ---

  @Test
  void updateTaskStatus_happyPath() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Create a fresh task in OPEN status
              var freshTask =
                  new Task(
                      projectId, "Status Update Task", "Desc", "MEDIUM", null, null, actorMemberId);
              taskRepository.save(freshTask);

              var rule = createRule("Update task status rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.UPDATE_STATUS,
                      Map.of("targetEntityType", "TASK", "newStatus", "IN_PROGRESS"),
                      null,
                      null);
              actionRepository.save(action);

              var context = buildTaskContextWithId(freshTask.getId());
              // Set current status to OPEN for transition validation
              context.get("task").put("status", "OPEN");
              UUID executionId = createExecution(rule.getId());
              var result = automationActionExecutor.execute(action, executionId, context);

              assertThat(result).isInstanceOf(ActionSuccess.class);
              var success = (ActionSuccess) result;
              assertThat(success.resultData()).containsKey("updatedEntityId");

              // Verify the task status was actually updated
              var updated = taskRepository.findById(freshTask.getId()).orElseThrow();
              assertThat(updated.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
            });
  }

  @Test
  void updateTaskStatus_invalidTransition_returnsFailure() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Invalid transition rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.UPDATE_STATUS,
                      Map.of("targetEntityType", "TASK", "newStatus", "DONE"),
                      null,
                      null);
              actionRepository.save(action);

              // Context has task with status OPEN — OPEN->DONE is not a valid transition
              var context = buildTaskContextWithId(taskId);
              context.get("task").put("status", "OPEN");
              UUID executionId = createExecution(rule.getId());
              var result = automationActionExecutor.execute(action, executionId, context);

              assertThat(result).isInstanceOf(ActionFailure.class);
              var failure = (ActionFailure) result;
              assertThat(failure.errorMessage()).contains("Invalid task status transition");
            });
  }

  @Test
  void updateCustomerStatus_returnsNotYetSupported() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Customer status rule", TriggerType.CUSTOMER_STATUS_CHANGED);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.UPDATE_STATUS,
                      Map.of("targetEntityType", "CUSTOMER", "newStatus", "ACTIVE"),
                      null,
                      null);
              actionRepository.save(action);

              var context = buildTaskContext();
              UUID executionId = createExecution(rule.getId());
              var result = automationActionExecutor.execute(action, executionId, context);

              assertThat(result).isInstanceOf(ActionFailure.class);
              var failure = (ActionFailure) result;
              assertThat(failure.errorMessage())
                  .contains("Customer status update not yet supported");
            });
  }

  // --- 282.10: CreateProjectActionExecutor Tests ---

  @Test
  void createProjectFromTemplate_happyPath() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Create project rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.CREATE_PROJECT,
                      Map.of(
                          "projectTemplateId",
                          templateId.toString(),
                          "projectName",
                          "Auto: {{task.name}}",
                          "linkToCustomer",
                          false),
                      null,
                      null);
              actionRepository.save(action);

              var context = buildTaskContext();
              UUID executionId = createExecution(rule.getId());
              var result = automationActionExecutor.execute(action, executionId, context);

              assertThat(result).isInstanceOf(ActionSuccess.class);
              var success = (ActionSuccess) result;
              assertThat(success.resultData()).containsKey("createdProjectId");

              // Verify project was created
              UUID createdId =
                  UUID.fromString(success.resultData().get("createdProjectId").toString());
              var project = projectRepository.findById(createdId).orElseThrow();
              assertThat(project.getName()).isEqualTo("Auto: Test Task");
            });
  }

  // --- 282.11: AssignMemberActionExecutor Tests ---

  @Test
  void assignMemberToProject_happyPath() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Create a new project for this test to avoid conflicts
              var newProject = new Project("Assign Test Project", null, actorMemberId);
              projectRepository.save(newProject);

              var rule = createRule("Assign member rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.ASSIGN_MEMBER,
                      Map.of(
                          "assignTo",
                          "SPECIFIC_MEMBER",
                          "specificMemberId",
                          secondMemberId.toString(),
                          "role",
                          "member"),
                      null,
                      null);
              actionRepository.save(action);

              var context = buildTaskContext();
              // Override project context to use the new project
              context.get("project").put("id", newProject.getId().toString());
              context.get("project").put("name", "Assign Test Project");

              UUID executionId = createExecution(rule.getId());
              var result = automationActionExecutor.execute(action, executionId, context);

              assertThat(result).isInstanceOf(ActionSuccess.class);
              var success = (ActionSuccess) result;
              assertThat(success.resultData()).containsKey("assignedMemberId");
              assertThat(success.resultData().get("assignedMemberId"))
                  .isEqualTo(secondMemberId.toString());

              // Verify member was actually assigned
              var members = projectMemberRepository.findByProjectId(newProject.getId());
              assertThat(members).anyMatch(m -> m.getMemberId().equals(secondMemberId));
            });
  }

  // --- 282.12: Non-short-circuit error handling ---

  @Test
  void nonShortCircuit_actionOneFails_actionTwoStillExecutes() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Non-short-circuit rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              // Action 1: UPDATE_STATUS for CUSTOMER (will fail — not supported)
              var action1 =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.UPDATE_STATUS,
                      Map.of("targetEntityType", "CUSTOMER", "newStatus", "ACTIVE"),
                      null,
                      null);
              actionRepository.save(action1);

              // Action 2: SEND_NOTIFICATION (should still execute)
              var action2 =
                  new AutomationAction(
                      rule.getId(),
                      2,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "recipientType",
                          "TRIGGER_ACTOR",
                          "title",
                          "Still executed",
                          "message",
                          "This should run despite action 1 failing"),
                      null,
                      null);
              actionRepository.save(action2);

              // Fire the event through the full pipeline
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
                      "DONE",
                      null,
                      "Non-Short-Circuit Task",
                      null);

              long notifCountBefore = notificationRepository.count();
              eventPublisher.publishEvent(event);

              // Verify execution status = ACTIONS_FAILED (because action 1 failed)
              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).isNotEmpty();
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_FAILED);

              // Verify action 2 still ran (notification was sent)
              // We check for > notifCountBefore because the notification from action 2 + failure
              // notifications should increase the count
              assertThat(notificationRepository.count()).isGreaterThan(notifCountBefore);

              // Verify both action executions were recorded
              var actionExecs =
                  actionExecutionRepository.findByExecutionId(executions.getFirst().getId());
              assertThat(actionExecs).hasSize(2);
            });
  }

  @Test
  void threeActionSequence_middleFails_othersSucceed() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Three action middle fail", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              // Action 1: SEND_NOTIFICATION (succeeds)
              var action1 =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "recipientType",
                          "TRIGGER_ACTOR",
                          "title",
                          "First action",
                          "message",
                          "Succeeds"),
                      null,
                      null);
              actionRepository.save(action1);

              // Action 2: UPDATE_STATUS for CUSTOMER (fails)
              var action2 =
                  new AutomationAction(
                      rule.getId(),
                      2,
                      ActionType.UPDATE_STATUS,
                      Map.of("targetEntityType", "CUSTOMER", "newStatus", "ACTIVE"),
                      null,
                      null);
              actionRepository.save(action2);

              // Action 3: SEND_NOTIFICATION (succeeds)
              var action3 =
                  new AutomationAction(
                      rule.getId(),
                      3,
                      ActionType.SEND_NOTIFICATION,
                      Map.of(
                          "recipientType",
                          "TRIGGER_ACTOR",
                          "title",
                          "Third action",
                          "message",
                          "Also succeeds"),
                      null,
                      null);
              actionRepository.save(action3);

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
                      "IN_PROGRESS",
                      null,
                      "Three Action Task",
                      null);

              eventPublisher.publishEvent(event);

              // Overall status is ACTIONS_FAILED because action 2 failed
              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).isNotEmpty();
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_FAILED);
              assertThat(executions.getFirst().getErrorMessage())
                  .contains("Customer status update not yet supported");

              // All 3 action executions should be recorded
              var actionExecs =
                  actionExecutionRepository.findByExecutionId(executions.getFirst().getId());
              assertThat(actionExecs).hasSize(3);

              // Actions 1 and 3 succeeded, action 2 failed
              long completed =
                  actionExecs.stream()
                      .filter(ae -> ae.getStatus() == ActionExecutionStatus.COMPLETED)
                      .count();
              long failed =
                  actionExecs.stream()
                      .filter(ae -> ae.getStatus() == ActionExecutionStatus.FAILED)
                      .count();
              assertThat(completed).isEqualTo(2);
              assertThat(failed).isEqualTo(1);
            });
  }

  // --- 282.13: Failure notification ---

  @Test
  void failureNotification_sentToAdmins() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Failure notif rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              // Single failing action
              var action =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.UPDATE_STATUS,
                      Map.of("targetEntityType", "CUSTOMER", "newStatus", "ACTIVE"),
                      null,
                      null);
              actionRepository.save(action);

              long notifCountBefore = notificationRepository.count();

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
                      "DONE",
                      null,
                      "Failure Notif Task",
                      null);

              eventPublisher.publishEvent(event);

              // Should have sent AUTOMATION_ACTION_FAILED notifications to admins/owners
              long notifCountAfter = notificationRepository.count();
              assertThat(notifCountAfter).isGreaterThan(notifCountBefore);

              // Verify the notification content
              var notifications = notificationRepository.findAll();
              var failureNotif =
                  notifications.stream()
                      .filter(n -> "AUTOMATION_ACTION_FAILED".equals(n.getType()))
                      .findFirst();
              assertThat(failureNotif).isPresent();
              assertThat(failureNotif.get().getTitle())
                  .contains("Automation action failed: Failure notif rule");
            });
  }

  @Test
  void executionStatus_reflectsWorstCase() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule = createRule("Worst case rule", TriggerType.TASK_STATUS_CHANGED);
              ruleRepository.save(rule);

              // Two actions: one succeeds, one fails
              var action1 =
                  new AutomationAction(
                      rule.getId(),
                      1,
                      ActionType.SEND_NOTIFICATION,
                      Map.of("recipientType", "TRIGGER_ACTOR", "title", "OK", "message", "Fine"),
                      null,
                      null);
              actionRepository.save(action1);

              var action2 =
                  new AutomationAction(
                      rule.getId(),
                      2,
                      ActionType.UPDATE_STATUS,
                      Map.of("targetEntityType", "CUSTOMER", "newStatus", "DORMANT"),
                      null,
                      null);
              actionRepository.save(action2);

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
                      "IN_PROGRESS",
                      null,
                      "Worst Case Task",
                      null);

              eventPublisher.publishEvent(event);

              // Even though action 1 succeeded, overall = ACTIONS_FAILED
              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).isNotEmpty();
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_FAILED);
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
    task.put("status", "DONE");
    task.put("previousStatus", "OPEN");
    task.put("projectId", projectId.toString());
    context.put("task", task);

    var project = new LinkedHashMap<String, Object>();
    project.put("id", projectId.toString());
    project.put("name", "Extended Test Project");
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

  private Map<String, Map<String, Object>> buildTaskContextWithId(UUID specificTaskId) {
    var context = buildTaskContext();
    context.get("task").put("id", specificTaskId.toString());
    return context;
  }
}
