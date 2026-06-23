package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.applier.OutputApplier;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.InboxSummaryPayload;
import io.b2mash.b2b.b2bstrawman.assistant.specialist.NonInteractiveSpecialistRunner;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** Integration tests for the SCHEDULED trigger cron pass in {@link AutomationScheduler}. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  AutomationSchedulerScheduledTriggerIntegrationTest.FakeInboxApplierConfig.class
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutomationSchedulerScheduledTriggerIntegrationTest {

  private static final String ORG_ID = "org_sched_trigger_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AutomationScheduler scheduler;
  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private AutomationActionRepository actionRepository;
  @Autowired private AutomationExecutionRepository executionRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private NotificationRepository notificationRepository;
  @MockitoBean private NonInteractiveSpecialistRunner runner;
  @MockitoSpyBean private AutomationEventListener eventListener;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Sched Trigger Test Org", null);
    var ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_sched_owner", "sched_owner@test.com", "Sched Owner", "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_ASSISTANT_USE", "TEAM_OVERSIGHT"))
        .run(body);
  }

  /**
   * Counts AUTOMATION_ACTION_FAILED notifications for a SPECIFIC rule. Must scope by rule id: this
   * class runs PER_CLASS against a never-reset tenant and {@code processScheduledTenant()} fires
   * every due rule in the tenant, so sibling methods' accumulated (and intentionally-failing) rules
   * bump a tenant-wide failure count mid-test — the flake was {@code expected 5, got 13}. The
   * producer tags each notification with referenceEntityType="AutomationRule" + the rule id (see
   * {@code AutomationEventListener#sendFailureNotification}).
   */
  private long countAutomationActionFailedNotificationsForRule(UUID ruleId) {
    return notificationRepository.findAll().stream()
        .filter(n -> "AUTOMATION_ACTION_FAILED".equals(n.getType()))
        .filter(n -> ruleId.equals(n.getReferenceEntityId()))
        .count();
  }

  @Test
  void scheduledTrigger_firesRuleAtCronTick() {
    runInTenant(
        () -> {
          // Create a SCHEDULED rule with cron "every second" and lastRunAt in the past
          var rule =
              new AutomationRule(
                  "Scheduled Test Rule",
                  "Test cron",
                  TriggerType.SCHEDULED,
                  Map.of("cronExpression", "* * * * * *"), // every second
                  List.of(),
                  RuleSource.CUSTOM,
                  null,
                  ownerMemberId);
          rule = ruleRepository.save(rule);

          // Set lastRunAt to 2 minutes ago so next fire time is in the past
          rule.setLastRunAt(Instant.now().minus(2, ChronoUnit.MINUTES));
          ruleRepository.save(rule);

          // Create a no-op action (SEND_NOTIFICATION)
          var action =
              new AutomationAction(
                  rule.getId(),
                  1,
                  ActionType.SEND_NOTIFICATION,
                  Map.of("message", "Scheduled test"),
                  null,
                  null);
          actionRepository.save(action);

          // Trigger the cron pass
          scheduler.processScheduledTenant();

          // Verify lastRunAt was updated
          var updatedRule = ruleRepository.findById(rule.getId()).orElseThrow();
          assertThat(updatedRule.getLastRunAt()).isNotNull();
          assertThat(updatedRule.getLastRunAt())
              .isAfter(Instant.now().minus(1, ChronoUnit.MINUTES));

          // Verify an execution was created
          var executions = executionRepository.findAll();
          assertThat(executions).isNotEmpty();
          var matchingExecutions =
              executions.stream().filter(e -> e.getRuleId().equals(updatedRule.getId())).toList();
          assertThat(matchingExecutions).isNotEmpty();
        });
  }

  @Test
  void scheduledTrigger_invalidCronExpression_handledGracefully() {
    runInTenant(
        () -> {
          var saved =
              ruleRepository.save(
                  new AutomationRule(
                      "Bad Cron Rule",
                      "Invalid cron",
                      TriggerType.SCHEDULED,
                      Map.of("cronExpression", "not-a-cron"),
                      List.of(),
                      RuleSource.CUSTOM,
                      null,
                      ownerMemberId));

          // Should not throw, just skip the rule
          scheduler.processScheduledTenant();

          // No new executions should be created for this rule
          var newExecs =
              executionRepository.findAll().stream()
                  .filter(e -> e.getRuleId().equals(saved.getId()))
                  .toList();
          assertThat(newExecs).isEmpty();
        });
  }

  @Test
  void scheduledTrigger_missedRunPolicy_fireOnceNoFlood() {
    runInTenant(
        () -> {
          // Create a rule with "every minute" cron and lastRunAt 1 hour ago
          // Should fire exactly once (not 60 times)
          var rule =
              new AutomationRule(
                  "Missed Run Policy Rule",
                  "Test missed run",
                  TriggerType.SCHEDULED,
                  Map.of("cronExpression", "0 * * * * *"), // every minute
                  List.of(),
                  RuleSource.CUSTOM,
                  null,
                  ownerMemberId);
          rule = ruleRepository.save(rule);
          rule.setLastRunAt(Instant.now().minus(1, ChronoUnit.HOURS));
          ruleRepository.save(rule);

          // First poll fires once
          scheduler.processScheduledTenant();

          var updatedRule = ruleRepository.findById(rule.getId()).orElseThrow();
          Instant firstRunAt = updatedRule.getLastRunAt();
          assertThat(firstRunAt).isNotNull();

          // Second poll should not fire again immediately (next fire is ~1min away)
          scheduler.processScheduledTenant();

          var reloadedRule = ruleRepository.findById(rule.getId()).orElseThrow();
          // lastRunAt should be same as after first fire (no second fire within the same second)
          assertThat(reloadedRule.getLastRunAt()).isEqualTo(firstRunAt);
        });
  }

  /**
   * OBS-505: a SCHEDULED INVOKE_AI_SPECIALIST rule with a {@code {{project.id}}} contextRef must
   * fan out one specialist invocation per ACTIVE project (with that project's id), skip non-active
   * projects, and produce zero AUTOMATION_ACTION_FAILED notifications. Before the fan-out fix the
   * scheduled path fired once with an entity-less context, so {@code {{project.id}}} never resolved
   * → ActionFailure → AUTOMATION_ACTION_FAILED.
   */
  @Test
  void scheduledTrigger_projectScopedAiSpecialist_fansOutPerActiveProject() {
    var payload =
        new InboxSummaryPayload(
            UUID.randomUUID(),
            "2026-01-01T00:00:00Z",
            "2026-01-08T00:00:00Z",
            "Weekly summary",
            null);
    Mockito.when(
            runner.run(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(payload);
    Mockito.when(runner.promptVersion(Mockito.anyString())).thenReturn("v1.0");
    Mockito.clearInvocations(runner);

    runInTenant(
        () -> {
          // Seed 2 ACTIVE + 1 non-active (COMPLETED) project
          var active1 =
              projectRepository.save(new Project("Active Matter A", "desc", ownerMemberId));
          var active2 =
              projectRepository.save(new Project("Active Matter B", "desc", ownerMemberId));
          var inactive = new Project("Closed Matter C", "desc", ownerMemberId);
          inactive.complete(ownerMemberId); // -> COMPLETED, not ACTIVE
          inactive = projectRepository.save(inactive);

          // SCHEDULED rule: weekly per-matter AI summary, project-scoped contextRef, ACTIVE filter
          var rule =
              new AutomationRule(
                  "Weekly matter activity summary",
                  "Per-matter weekly AI summary",
                  TriggerType.SCHEDULED,
                  Map.of("cronExpression", "* * * * * *"),
                  List.of(
                      Map.of("field", "project.status", "operator", "EQUALS", "value", "ACTIVE")),
                  RuleSource.TEMPLATE,
                  "weekly-matter-activity-summary",
                  ownerMemberId);
          rule = ruleRepository.save(rule);
          rule.setLastRunAt(Instant.now().minus(2, ChronoUnit.MINUTES));
          rule = ruleRepository.save(rule);

          var action =
              new AutomationAction(
                  rule.getId(),
                  0,
                  ActionType.INVOKE_AI_SPECIALIST,
                  Map.of(
                      "specialistId", "inbox-za",
                      "contextRef", Map.of("entityType", "project", "entityId", "{{project.id}}"),
                      "initialPrompt", "Summarise the past week.",
                      "lookback", "P7D",
                      "mode", "DIRECT",
                      "timeoutSeconds", 120),
                  null,
                  null);
          actionRepository.save(action);

          // Fire the cron tick
          scheduler.processScheduledTenant();

          // The specialist runner is invoked once per ACTIVE project (project-scoped fan-out) and
          // captures each active project's id. Assert on the SPECIFIC ids we seeded, not an exact
          // global invocation count: this PER_CLASS tenant is never reset, so sibling test methods
          // accumulate extra ACTIVE projects and SCHEDULED rules that also fan out — an exact
          // `times(activeProjectCount)` is a moving target (the flake: expected 5, got 13).
          // Containment is robust to that accumulation while still proving the contract — both
          // seeded ACTIVE projects fired and the non-active (COMPLETED) project never did.
          var entityIdCaptor = ArgumentCaptor.forClass(UUID.class);
          Mockito.verify(runner, Mockito.atLeast(2))
              .run(
                  Mockito.eq("inbox-za"),
                  Mockito.eq("project"),
                  entityIdCaptor.capture(),
                  Mockito.any(),
                  Mockito.any(),
                  Mockito.any());

          assertThat(entityIdCaptor.getAllValues())
              .contains(active1.getId(), active2.getId())
              .doesNotContain(inactive.getId());

          // This rule's fan-out (runner mocked to succeed) must produce no failure notifications.
          // Scope to the rule id, not a tenant-wide count — see the helper javadoc (the count-bleed
          // flake: processScheduledTenant() also fires sibling accumulated rules in this
          // never-reset
          // PER_CLASS tenant, so a global count was a moving target — expected 5, got 13).
          assertThat(countAutomationActionFailedNotificationsForRule(rule.getId())).isZero();
        });
  }

  /**
   * OBS-505 (CodeRabbit): per-project failure isolation. When {@code fireRule(...)} throws for one
   * active project during the fan-out, the remaining active projects must still be processed and
   * the cron tick must complete without propagating the exception (otherwise one bad matter starves
   * all others for the whole cron window, and since lastRunAt already advanced there is no retry
   * until the next tick — a week away for the weekly per-matter rule).
   *
   * <p>Fail-first evidence: against the pre-fix code (no per-project try/catch inside {@code
   * fireScheduledRulePerActiveProject}) the thrown exception aborts the loop, so the healthy
   * project's {@code fireRule} is never reached when it is iterated after the failing one, and
   * {@code processScheduledTenant()} would either propagate or never invoke the second project —
   * the {@code times(1)} verification on the healthy context fails. With the fix the loop swallows,
   * logs, continues, and the healthy project is still fired.
   */
  @Test
  void scheduledTrigger_oneProjectFails_othersStillFireAndTickCompletes() {
    runInTenant(
        () -> {
          var failing =
              projectRepository.save(new Project("Failing Matter", "desc", ownerMemberId));
          var healthy =
              projectRepository.save(new Project("Healthy Matter", "desc", ownerMemberId));

          var rule =
              new AutomationRule(
                  "Per-project isolation rule",
                  "Fan-out failure isolation",
                  TriggerType.SCHEDULED,
                  Map.of("cronExpression", "* * * * * *"),
                  List.of(),
                  RuleSource.CUSTOM,
                  null,
                  ownerMemberId);
          rule = ruleRepository.save(rule);
          rule.setLastRunAt(Instant.now().minus(2, ChronoUnit.MINUTES));
          rule = ruleRepository.save(rule);

          var action =
              new AutomationAction(
                  rule.getId(),
                  0,
                  ActionType.SEND_NOTIFICATION,
                  Map.of(
                      "message",
                      "Per-matter ping",
                      "contextRef",
                      Map.of("entityType", "project", "entityId", "{{project.id}}")),
                  null,
                  null);
          actionRepository.save(action);

          // Make fireRule throw for the failing project's context, delegate to the real method
          // (which actually fires) for everyone else. project.id distinguishes the contexts.
          String failingId = failing.getId().toString();
          Mockito.doAnswer(
                  inv -> {
                    Map<String, Map<String, Object>> ctx = inv.getArgument(1);
                    Object pid = ctx.getOrDefault("project", Map.of()).get("id");
                    if (failingId.equals(pid)) {
                      throw new RuntimeException("Simulated per-project failure");
                    }
                    return inv.callRealMethod();
                  })
              .when(eventListener)
              .fireRule(Mockito.any(), Mockito.any());

          UUID ruleId = rule.getId();
          long execsBefore =
              executionRepository.findAll().stream()
                  .filter(e -> e.getRuleId().equals(ruleId))
                  .count();
          // Must NOT propagate, even though one project's fireRule throws.
          scheduler.processScheduledTenant();

          // The healthy project was still fired (real method ran) — the core isolation guarantee:
          // the failing project did NOT abort the loop before the healthy one was reached.
          String healthyId = healthy.getId().toString();
          Mockito.verify(eventListener, Mockito.atLeastOnce())
              .fireRule(
                  Mockito.any(),
                  Mockito.argThat(
                      ctx ->
                          ctx != null
                              && healthyId.equals(
                                  ctx.getOrDefault("project", Map.of()).get("id"))));
          // The failing project was also attempted (proving the failure was real, then swallowed).
          String failingId2 = failing.getId().toString();
          Mockito.verify(eventListener, Mockito.atLeastOnce())
              .fireRule(
                  Mockito.any(),
                  Mockito.argThat(
                      ctx ->
                          ctx != null
                              && failingId2.equals(
                                  ctx.getOrDefault("project", Map.of()).get("id"))));

          long execsAfter =
              executionRepository.findAll().stream()
                  .filter(e -> e.getRuleId().equals(ruleId))
                  .count();
          // At least one execution was persisted for this rule despite a sibling project throwing:
          // proof the per-project catch let the loop continue past the failure (without it the loop
          // aborts at the failing project and the healthy project — verified above — never runs).
          // A rule-scoped delta of >= 1 is asserted rather than an exact `activeProjectCount - 1`,
          // because this PER_CLASS tenant is never reset and accumulates ACTIVE projects across
          // methods, making the exact count a flaky moving target. The precise "failing project did
          // not starve the healthy one" guarantee is already enforced by the two fireRule
          // verifications above.
          assertThat(execsAfter - execsBefore).isGreaterThanOrEqualTo(1L);

          Mockito.reset(eventListener);
        });
  }

  /**
   * OBS-505 regression: a SCHEDULED rule with no project-scoped action (SEND_NOTIFICATION) must
   * still fire exactly once — no fan-out, no behaviour change.
   */
  @Test
  void scheduledTrigger_nonProjectScopedRule_firesExactlyOnce() {
    runInTenant(
        () -> {
          // Seed 2 ACTIVE projects to prove they do NOT cause fan-out for a non-project rule
          projectRepository.save(new Project("Active X", "desc", ownerMemberId));
          projectRepository.save(new Project("Active Y", "desc", ownerMemberId));

          var rule =
              new AutomationRule(
                  "Scheduled Notification Rule",
                  "No project ref",
                  TriggerType.SCHEDULED,
                  Map.of("cronExpression", "* * * * * *"),
                  List.of(),
                  RuleSource.CUSTOM,
                  null,
                  ownerMemberId);
          rule = ruleRepository.save(rule);
          rule.setLastRunAt(Instant.now().minus(2, ChronoUnit.MINUTES));
          rule = ruleRepository.save(rule);

          var action =
              new AutomationAction(
                  rule.getId(),
                  0,
                  ActionType.SEND_NOTIFICATION,
                  Map.of("message", "Scheduled notification"),
                  null,
                  null);
          actionRepository.save(action);

          UUID ruleId = rule.getId();
          long execsBefore =
              executionRepository.findAll().stream()
                  .filter(e -> e.getRuleId().equals(ruleId))
                  .count();

          scheduler.processScheduledTenant();

          long execsAfter =
              executionRepository.findAll().stream()
                  .filter(e -> e.getRuleId().equals(ruleId))
                  .count();
          // Exactly one execution created (fired once, not once-per-project)
          assertThat(execsAfter - execsBefore).isEqualTo(1);
        });
  }

  @TestConfiguration
  static class FakeInboxApplierConfig {
    @Bean("inboxSummaryApplier")
    FakeInboxApplier fakeInboxApplier() {
      return new FakeInboxApplier();
    }
  }

  static class FakeInboxApplier implements OutputApplier<InboxSummaryPayload> {
    private final AtomicInteger applyCount = new AtomicInteger();

    @Override
    public Class<InboxSummaryPayload> payloadType() {
      return InboxSummaryPayload.class;
    }

    @Override
    public void apply(InboxSummaryPayload payload, UUID actorId) {
      applyCount.incrementAndGet();
    }
  }
}
