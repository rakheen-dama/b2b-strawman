package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.event.ProposalSentEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
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
class ProposalSentTriggerWiringTest {

  private static final String ORG_ID = "org_proposal_trigger_test";
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
        provisioningService.provisionTenant(ORG_ID, "Proposal Trigger Test Org", null).schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");
  }

  @Test
  void proposalSentEvent_triggersAutomationRule() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "Proposal follow-up", TriggerType.PROPOSAL_SENT, Map.of(), RuleSource.CUSTOM);
              ruleRepository.save(rule);

              UUID proposalId = UUID.randomUUID();
              UUID projectId = UUID.randomUUID();
              String customerId = UUID.randomUUID().toString();

              var event =
                  new ProposalSentEvent(
                      "proposal.sent",
                      "proposal",
                      proposalId,
                      projectId,
                      ACTOR_MEMBER_ID,
                      ACTOR_NAME,
                      schemaName,
                      ORG_ID,
                      Instant.now(),
                      Map.of(
                          "customer_id", customerId,
                          "customer_name", "Test Customer",
                          "project_name", "Test Project"));

              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).hasSize(1);
              assertThat(executions.getFirst().getTriggerEventType())
                  .isEqualTo("ProposalSentEvent");
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_COMPLETED);

              Map<String, Object> snapshot = executions.getFirst().getTriggerEventData();
              assertThat(snapshot).containsEntry("entityId", proposalId.toString());
              assertThat(snapshot).containsEntry("eventType", "proposal.sent");
              assertThat(snapshot).containsEntry("entityType", "proposal");
            });
  }

  @Test
  void triggerTypeMapping_proposalSentEvent_returnsPROPOSAL_SENT() {
    var event =
        new ProposalSentEvent(
            "proposal.sent",
            "proposal",
            UUID.randomUUID(),
            null,
            ACTOR_MEMBER_ID,
            ACTOR_NAME,
            "test",
            "org",
            Instant.now(),
            Map.of());
    assertThat(TriggerTypeMapping.getTriggerType(event)).isEqualTo(TriggerType.PROPOSAL_SENT);
  }

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
}
