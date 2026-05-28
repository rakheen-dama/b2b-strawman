package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Integration tests for the SCHEDULED trigger cron pass in {@link AutomationScheduler}. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
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
}
