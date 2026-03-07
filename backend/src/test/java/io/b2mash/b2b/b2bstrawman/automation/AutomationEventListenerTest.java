package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.event.BudgetThresholdEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.DocumentUploadedEvent;
import io.b2mash.b2b.b2bstrawman.event.InformationRequestCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceVoidedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectArchivedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectReopenedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.event.TimeEntryChangedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
class AutomationEventListenerTest {

  private static final String ORG_ID = "org_automation_listener_test";
  private static final UUID ACTOR_MEMBER_ID = UUID.randomUUID();
  private static final String ACTOR_NAME = "Test Actor";

  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private AutomationExecutionRepository executionRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private ApplicationEventPublisher eventPublisher;

  private String schemaName;

  @BeforeAll
  void provisionTenant() {
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "Automation Listener Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");
  }

  @Test
  void eventWithNoMatchingRules_noExecutionRecordsCreated() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Use INFORMATION_REQUEST_COMPLETED trigger type — no rules created for it in tests
              var event =
                  new InformationRequestCompletedEvent(
                      "INFO_REQUEST_COMPLETED",
                      "InformationRequest",
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      ACTOR_MEMBER_ID,
                      ACTOR_NAME,
                      schemaName,
                      ORG_ID,
                      Instant.now(),
                      Map.of(),
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      UUID.randomUUID());
              long countBefore = executionRepository.count();

              eventPublisher.publishEvent(event);

              assertThat(executionRepository.count()).isEqualTo(countBefore);
            });
  }

  @Test
  void eventWithMatchingRule_executionRecordCreated() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "Match any task status",
                      TriggerType.TASK_STATUS_CHANGED,
                      Map.of(),
                      RuleSource.CUSTOM);
              ruleRepository.save(rule);

              var event = taskStatusChangedEvent("OPEN", "COMPLETED");
              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).hasSize(1);
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_COMPLETED);
              assertThat(executions.getFirst().isConditionsMet()).isTrue();
              assertThat(executions.getFirst().getTriggerEventType())
                  .isEqualTo("TaskStatusChangedEvent");
            });
  }

  @Test
  void triggerConfigMatch_specificStatus() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "Match COMPLETED",
                      TriggerType.TASK_STATUS_CHANGED,
                      Map.of("toStatus", "COMPLETED"),
                      RuleSource.CUSTOM);
              ruleRepository.save(rule);

              var event = taskStatusChangedEvent("OPEN", "COMPLETED");
              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).hasSize(1);
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_COMPLETED);
            });
  }

  @Test
  void triggerConfigNoMatch_noExecutionRecord() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "Match IN_PROGRESS only",
                      TriggerType.TASK_STATUS_CHANGED,
                      Map.of("toStatus", "IN_PROGRESS"),
                      RuleSource.CUSTOM);
              ruleRepository.save(rule);

              // Event has newStatus=COMPLETED, rule wants IN_PROGRESS — should not match
              var event = taskStatusChangedEvent("OPEN", "COMPLETED");
              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).isEmpty();
            });
  }

  @Test
  void multipleRulesForSameTriggerType_bothEvaluatedIndependently() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule1 =
                  createRule(
                      "Rule A - any status",
                      TriggerType.TASK_STATUS_CHANGED,
                      Map.of(),
                      RuleSource.CUSTOM);
              var rule2 =
                  createRule(
                      "Rule B - any status",
                      TriggerType.TASK_STATUS_CHANGED,
                      Map.of(),
                      RuleSource.CUSTOM);
              ruleRepository.save(rule1);
              ruleRepository.save(rule2);

              var event = taskStatusChangedEvent("OPEN", "COMPLETED");
              eventPublisher.publishEvent(event);

              var exec1 = executionRepository.findByRuleIdOrderByStartedAtDesc(rule1.getId());
              var exec2 = executionRepository.findByRuleIdOrderByStartedAtDesc(rule2.getId());
              assertThat(exec1).hasSize(1);
              assertThat(exec2).hasSize(1);
            });
  }

  @Test
  void disabledRuleNotMatched() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "Disabled rule",
                      TriggerType.TASK_STATUS_CHANGED,
                      Map.of(),
                      RuleSource.CUSTOM);
              rule.toggle(); // disable it
              ruleRepository.save(rule);

              var event = taskStatusChangedEvent("OPEN", "COMPLETED");
              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).isEmpty();
            });
  }

  @Test
  void unmappedEventTypeIgnored() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // CommentCreatedEvent has no trigger type mapping
              long countBefore = executionRepository.count();

              var event =
                  new CommentCreatedEvent(
                      "COMMENT_CREATED",
                      "Comment",
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      ACTOR_MEMBER_ID,
                      ACTOR_NAME,
                      schemaName,
                      ORG_ID,
                      Instant.now(),
                      Map.of(),
                      "TASK",
                      UUID.randomUUID(),
                      "INTERNAL");

              eventPublisher.publishEvent(event);

              assertThat(executionRepository.count()).isEqualTo(countBefore);
            });
  }

  @Test
  void triggerConfigAnyStatus_matchesAnyStatusChange() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // StatusChangeTriggerConfig with null toStatus/fromStatus = any
              var rule =
                  createRule(
                      "Any status change",
                      TriggerType.TASK_STATUS_CHANGED,
                      new LinkedHashMap<>(),
                      RuleSource.CUSTOM);
              ruleRepository.save(rule);

              var event = taskStatusChangedEvent("OPEN", "CANCELLED");
              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).hasSize(1);
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_COMPLETED);
            });
  }

  @Test
  void executionRecordHasCorrectTriggerEventDataSnapshot() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "Snapshot test",
                      TriggerType.TASK_STATUS_CHANGED,
                      Map.of(),
                      RuleSource.CUSTOM);
              ruleRepository.save(rule);

              UUID entityId = UUID.randomUUID();
              UUID projectId = UUID.randomUUID();
              var event =
                  new TaskStatusChangedEvent(
                      "TASK_STATUS_CHANGED",
                      "Task",
                      entityId,
                      projectId,
                      ACTOR_MEMBER_ID,
                      ACTOR_NAME,
                      schemaName,
                      ORG_ID,
                      Instant.now(),
                      Map.of("key", "value"),
                      "OPEN",
                      "COMPLETED",
                      null,
                      "Test Task");

              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).hasSize(1);

              Map<String, Object> snapshot = executions.getFirst().getTriggerEventData();
              assertThat(snapshot).containsEntry("eventType", "TASK_STATUS_CHANGED");
              assertThat(snapshot).containsEntry("entityType", "Task");
              assertThat(snapshot).containsEntry("entityId", entityId.toString());
              assertThat(snapshot).containsEntry("projectId", projectId.toString());
              assertThat(snapshot).containsEntry("actorName", ACTOR_NAME);
              assertThat(snapshot).containsKey("occurredAt");
            });
  }

  @Test
  void listenerErrorDoesNotPropagateToCallerOnRuleFailure() {
    // This test verifies that even if something goes wrong during rule processing,
    // the per-rule try-catch prevents exceptions from escaping to the event publisher.
    // We use BUDGET_THRESHOLD_REACHED with a non-numeric thresholdPercent so that
    // Jackson's convertValue genuinely fails when deserializing BudgetThresholdTriggerConfig.
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var badRule =
                  createRule(
                      "Bad config rule",
                      TriggerType.BUDGET_THRESHOLD_REACHED,
                      Map.of("thresholdPercent", "not_a_number"),
                      RuleSource.CUSTOM);
              ruleRepository.save(badRule);

              // Also add a good rule to verify it still gets processed
              var goodRule =
                  createRule(
                      "Good rule",
                      TriggerType.BUDGET_THRESHOLD_REACHED,
                      Map.of("thresholdPercent", 50),
                      RuleSource.CUSTOM);
              ruleRepository.save(goodRule);

              var details = new LinkedHashMap<String, Object>();
              details.put("project_name", "Test Project");
              details.put("dimension", "HOURS");
              details.put("consumed_pct", 90);

              var event =
                  new BudgetThresholdEvent(
                      "BUDGET_THRESHOLD",
                      "Budget",
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      ACTOR_MEMBER_ID,
                      ACTOR_NAME,
                      schemaName,
                      ORG_ID,
                      Instant.now(),
                      details);

              // Should NOT throw — errors are caught internally per-rule
              eventPublisher.publishEvent(event);

              // Good rule should still produce an execution despite bad rule failing
              var goodExecList =
                  executionRepository.findByRuleIdOrderByStartedAtDesc(goodRule.getId());
              assertThat(goodExecList).hasSize(1);
            });
  }

  @Test
  void budgetThresholdTriggerMatchesWhenAboveThreshold() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "Budget 80%",
                      TriggerType.BUDGET_THRESHOLD_REACHED,
                      Map.of("thresholdPercent", 80),
                      RuleSource.CUSTOM);
              ruleRepository.save(rule);

              var details = new LinkedHashMap<String, Object>();
              details.put("project_name", "Test Project");
              details.put("dimension", "HOURS");
              details.put("consumed_pct", 90);

              var event =
                  new BudgetThresholdEvent(
                      "BUDGET_THRESHOLD",
                      "Budget",
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      ACTOR_MEMBER_ID,
                      ACTOR_NAME,
                      schemaName,
                      ORG_ID,
                      Instant.now(),
                      details);

              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).hasSize(1);
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_COMPLETED);
            });
  }

  @Test
  void budgetThresholdTriggerDoesNotMatchWhenBelowThreshold() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "Budget 80% threshold",
                      TriggerType.BUDGET_THRESHOLD_REACHED,
                      Map.of("thresholdPercent", 80),
                      RuleSource.CUSTOM);
              ruleRepository.save(rule);

              var details = new LinkedHashMap<String, Object>();
              details.put("project_name", "Test Project");
              details.put("dimension", "HOURS");
              details.put("consumed_pct", 50);

              var event =
                  new BudgetThresholdEvent(
                      "BUDGET_THRESHOLD",
                      "Budget",
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      ACTOR_MEMBER_ID,
                      ACTOR_NAME,
                      schemaName,
                      ORG_ID,
                      Instant.now(),
                      details);

              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).isEmpty();
            });
  }

  @Test
  void triggerTypeMappings_allMappedEventsResolveCorrectly() {
    // TaskStatusChangedEvent -> TASK_STATUS_CHANGED
    assertThat(TriggerTypeMapping.getTriggerType(taskStatusChangedEvent("OPEN", "COMPLETED")))
        .isEqualTo(TriggerType.TASK_STATUS_CHANGED);

    // ProjectCompletedEvent -> PROJECT_STATUS_CHANGED
    assertThat(
            TriggerTypeMapping.getTriggerType(
                new ProjectCompletedEvent(
                    "PROJECT_COMPLETED",
                    "Project",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ACTOR_MEMBER_ID,
                    ACTOR_NAME,
                    "test",
                    "org",
                    Instant.now(),
                    Map.of(),
                    ACTOR_MEMBER_ID,
                    "Test Project",
                    false)))
        .isEqualTo(TriggerType.PROJECT_STATUS_CHANGED);

    // ProjectArchivedEvent -> PROJECT_STATUS_CHANGED
    assertThat(
            TriggerTypeMapping.getTriggerType(
                new ProjectArchivedEvent(
                    "PROJECT_ARCHIVED",
                    "Project",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ACTOR_MEMBER_ID,
                    ACTOR_NAME,
                    "test",
                    "org",
                    Instant.now(),
                    Map.of(),
                    ACTOR_MEMBER_ID,
                    "Test Project")))
        .isEqualTo(TriggerType.PROJECT_STATUS_CHANGED);

    // ProjectReopenedEvent -> PROJECT_STATUS_CHANGED
    assertThat(
            TriggerTypeMapping.getTriggerType(
                new ProjectReopenedEvent(
                    "PROJECT_REOPENED",
                    "Project",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ACTOR_MEMBER_ID,
                    ACTOR_NAME,
                    "test",
                    "org",
                    Instant.now(),
                    Map.of(),
                    ACTOR_MEMBER_ID,
                    "Test Project",
                    "COMPLETED")))
        .isEqualTo(TriggerType.PROJECT_STATUS_CHANGED);

    // BudgetThresholdEvent -> BUDGET_THRESHOLD_REACHED
    assertThat(
            TriggerTypeMapping.getTriggerType(
                new BudgetThresholdEvent(
                    "BUDGET_THRESHOLD",
                    "Budget",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ACTOR_MEMBER_ID,
                    ACTOR_NAME,
                    "test",
                    "org",
                    Instant.now(),
                    Map.of())))
        .isEqualTo(TriggerType.BUDGET_THRESHOLD_REACHED);

    // TimeEntryChangedEvent with CREATED action -> TIME_ENTRY_CREATED
    assertThat(
            TriggerTypeMapping.getTriggerType(
                new TimeEntryChangedEvent(
                    "TIME_ENTRY_CHANGED",
                    "TimeEntry",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "CREATED",
                    ACTOR_MEMBER_ID,
                    ACTOR_NAME,
                    "test",
                    "org",
                    Instant.now(),
                    Map.of())))
        .isEqualTo(TriggerType.TIME_ENTRY_CREATED);

    // TimeEntryChangedEvent with UPDATED action -> null (only CREATED is mapped)
    assertThat(
            TriggerTypeMapping.getTriggerType(
                new TimeEntryChangedEvent(
                    "TIME_ENTRY_CHANGED",
                    "TimeEntry",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "UPDATED",
                    ACTOR_MEMBER_ID,
                    ACTOR_NAME,
                    "test",
                    "org",
                    Instant.now(),
                    Map.of())))
        .isNull();

    // TimeEntryChangedEvent with DELETED action -> null
    assertThat(
            TriggerTypeMapping.getTriggerType(
                new TimeEntryChangedEvent(
                    "TIME_ENTRY_CHANGED",
                    "TimeEntry",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "DELETED",
                    ACTOR_MEMBER_ID,
                    ACTOR_NAME,
                    "test",
                    "org",
                    Instant.now(),
                    Map.of())))
        .isNull();

    // DocumentUploadedEvent -> DOCUMENT_ACCEPTED
    assertThat(
            TriggerTypeMapping.getTriggerType(
                new DocumentUploadedEvent(
                    "DOCUMENT_UPLOADED",
                    "Document",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ACTOR_MEMBER_ID,
                    ACTOR_NAME,
                    "test",
                    "org",
                    Instant.now(),
                    Map.of(),
                    "test.pdf")))
        .isEqualTo(TriggerType.DOCUMENT_ACCEPTED);

    // InvoiceSentEvent -> INVOICE_STATUS_CHANGED
    assertThat(
            TriggerTypeMapping.getTriggerType(
                new InvoiceSentEvent(
                    "INVOICE_SENT",
                    "Invoice",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ACTOR_MEMBER_ID,
                    ACTOR_NAME,
                    "test",
                    "org",
                    Instant.now(),
                    Map.of(),
                    ACTOR_MEMBER_ID,
                    "INV-001",
                    "Customer A")))
        .isEqualTo(TriggerType.INVOICE_STATUS_CHANGED);

    // InvoicePaidEvent -> INVOICE_STATUS_CHANGED
    assertThat(
            TriggerTypeMapping.getTriggerType(
                new InvoicePaidEvent(
                    "INVOICE_PAID",
                    "Invoice",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ACTOR_MEMBER_ID,
                    ACTOR_NAME,
                    "test",
                    "org",
                    Instant.now(),
                    Map.of(),
                    ACTOR_MEMBER_ID,
                    "INV-002",
                    "Customer B",
                    "REF-123")))
        .isEqualTo(TriggerType.INVOICE_STATUS_CHANGED);

    // InvoiceVoidedEvent -> INVOICE_STATUS_CHANGED
    assertThat(
            TriggerTypeMapping.getTriggerType(
                new InvoiceVoidedEvent(
                    "INVOICE_VOIDED",
                    "Invoice",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ACTOR_MEMBER_ID,
                    ACTOR_NAME,
                    "test",
                    "org",
                    Instant.now(),
                    Map.of(),
                    ACTOR_MEMBER_ID,
                    "INV-003",
                    "Customer C",
                    ACTOR_MEMBER_ID)))
        .isEqualTo(TriggerType.INVOICE_STATUS_CHANGED);

    // Unmapped event -> null
    assertThat(
            TriggerTypeMapping.getTriggerType(
                new CommentCreatedEvent(
                    "COMMENT_CREATED",
                    "Comment",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ACTOR_MEMBER_ID,
                    ACTOR_NAME,
                    "test",
                    "org",
                    Instant.now(),
                    Map.of(),
                    "TASK",
                    UUID.randomUUID(),
                    "INTERNAL")))
        .isNull();
  }

  @Test
  void cycleDetection_eventWithAutomationExecutionIdIsSkipped() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "Cycle test rule",
                      TriggerType.TASK_STATUS_CHANGED,
                      Map.of(),
                      RuleSource.CUSTOM);
              ruleRepository.save(rule);

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
                      "COMPLETED",
                      null,
                      "Cycle Task");

              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).isEmpty();
            });
  }

  // --- Helper methods ---

  private AutomationRule createRule(
      String name, TriggerType triggerType, Map<String, Object> triggerConfig, RuleSource source) {
    return new AutomationRule(
        name,
        "Test rule: " + name,
        triggerType,
        triggerConfig,
        null,
        source,
        null,
        ACTOR_MEMBER_ID);
  }

  private TaskStatusChangedEvent taskStatusChangedEvent(String oldStatus, String newStatus) {
    return new TaskStatusChangedEvent(
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
        oldStatus,
        newStatus,
        null,
        "Test Task");
  }
}
