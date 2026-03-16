package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.event.CustomerStatusChangedEvent;
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
class CustomerStatusChangedTriggerWiringTest {

  private static final String ORG_ID = "org_csc_trigger_test";
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
        provisioningService.provisionTenant(ORG_ID, "CSC Trigger Test Org", null).schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");
  }

  @Test
  void customerStatusChangedEvent_triggersAutomationRule() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var rule =
                  createRule(
                      "Customer status watcher",
                      TriggerType.CUSTOMER_STATUS_CHANGED,
                      Map.of(),
                      RuleSource.CUSTOM);
              ruleRepository.save(rule);

              UUID customerId = UUID.randomUUID();

              var event =
                  new CustomerStatusChangedEvent(
                      "customer.status.changed",
                      "customer",
                      customerId,
                      null,
                      ACTOR_MEMBER_ID,
                      ACTOR_NAME,
                      schemaName,
                      ORG_ID,
                      Instant.now(),
                      Map.of(
                          "old_status", "PROSPECT",
                          "new_status", "ONBOARDING",
                          "customer_name", "Test Customer"));

              eventPublisher.publishEvent(event);

              var executions = executionRepository.findByRuleIdOrderByStartedAtDesc(rule.getId());
              assertThat(executions).hasSize(1);
              assertThat(executions.getFirst().getTriggerEventType())
                  .isEqualTo("CustomerStatusChangedEvent");
              assertThat(executions.getFirst().getStatus())
                  .isEqualTo(ExecutionStatus.ACTIONS_COMPLETED);

              Map<String, Object> snapshot = executions.getFirst().getTriggerEventData();
              assertThat(snapshot).containsEntry("entityId", customerId.toString());
              assertThat(snapshot).containsEntry("eventType", "customer.status.changed");
              assertThat(snapshot).containsEntry("entityType", "customer");
            });
  }

  @Test
  void triggerTypeMapping_customerStatusChangedEvent_returnsCUSTOMER_STATUS_CHANGED() {
    var event =
        new CustomerStatusChangedEvent(
            "customer.status.changed",
            "customer",
            UUID.randomUUID(),
            null,
            ACTOR_MEMBER_ID,
            ACTOR_NAME,
            "test",
            "org",
            Instant.now(),
            Map.of("old_status", "ACTIVE", "new_status", "DORMANT", "customer_name", "Test"));
    assertThat(TriggerTypeMapping.getTriggerType(event))
        .isEqualTo(TriggerType.CUSTOMER_STATUS_CHANGED);
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
