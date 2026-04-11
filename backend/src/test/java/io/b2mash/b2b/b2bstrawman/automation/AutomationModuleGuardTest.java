package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutomationModuleGuardTest {

  private static final String ORG_ID = "org_automation_guard_test";
  private static final UUID ACTOR_MEMBER_ID = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private AutomationExecutionRepository executionRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schemaName;

  @BeforeAll
  void setup() throws Exception {
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "Automation Guard Test Org", null).schemaName();
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_auto_owner", "auto@test.com", "Owner", "owner");
    // Disable seeded template rules so they don't interfere with the execution-engine test.
    disableSeededRules();
  }

  private void disableSeededRules() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx ->
                        ruleRepository.findAllByOrderByCreatedAtDesc().stream()
                            .filter(r -> r.getSource() == RuleSource.TEMPLATE && r.isEnabled())
                            .forEach(
                                r -> {
                                  r.toggle();
                                  ruleRepository.save(r);
                                })));
  }

  @BeforeEach
  void disableAllHorizontalModules() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabledModules": []}
                    """))
        .andExpect(status().isOk());
  }

  private void enableAutomationBuilder() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabledModules": ["automation_builder"]}
                    """))
        .andExpect(status().isOk());
  }

  private static final String CREATE_RULE_BODY =
      """
      {
        "name": "Test Rule",
        "description": "test",
        "triggerType": "TASK_STATUS_CHANGED",
        "triggerConfig": {},
        "conditions": null,
        "actions": []
      }
      """;

  @Test
  void postAutomationRules_returns403_whenAutomationBuilderDisabled() throws Exception {
    mockMvc
        .perform(
            post("/api/automation-rules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_RULE_BODY))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(jsonPath("$.moduleId").value("automation_builder"));
  }

  @Test
  void postAutomationRules_returns201_whenAutomationBuilderEnabled() throws Exception {
    enableAutomationBuilder();
    mockMvc
        .perform(
            post("/api/automation-rules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_RULE_BODY))
        .andExpect(status().isCreated());
  }

  @Test
  void getAutomationExecutions_returns403_whenAutomationBuilderDisabled() throws Exception {
    mockMvc
        .perform(
            get("/api/automation-executions")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"));
  }

  @Test
  void getAutomationTemplates_returns403_whenAutomationBuilderDisabled() throws Exception {
    mockMvc
        .perform(
            get("/api/automation-templates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"));
  }

  @Test
  void executionEngine_continuesRunning_whenAutomationBuilderDisabled() {
    // Module remains disabled (set by @BeforeEach)
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Insert a rule directly via repository (the API would 403)
              UUID[] ruleIdHolder = new UUID[1];
              transactionTemplate.executeWithoutResult(
                  tx -> {
                    var rule =
                        new AutomationRule(
                            "Engine Isolation Rule",
                            "Verifies engine fires regardless of module state",
                            TriggerType.TASK_STATUS_CHANGED,
                            Map.of(),
                            null,
                            RuleSource.CUSTOM,
                            null,
                            ACTOR_MEMBER_ID);
                    var saved = ruleRepository.save(rule);
                    ruleIdHolder[0] = saved.getId();
                  });

              // Publish a matching event
              var event =
                  new TaskStatusChangedEvent(
                      "TASK_STATUS_CHANGED",
                      "Task",
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      ACTOR_MEMBER_ID,
                      "Test Actor",
                      schemaName,
                      ORG_ID,
                      Instant.now(),
                      Map.of(),
                      "OPEN",
                      "DONE",
                      null,
                      "Test Task",
                      null);
              eventPublisher.publishEvent(event);

              // Verify the engine fired despite the module being disabled
              var executions =
                  executionRepository.findByRuleIdOrderByStartedAtDesc(ruleIdHolder[0]);
              assertThat(executions).isNotEmpty();
            });
  }

  @Test
  void getAutomationRules_returns200_whenAutomationBuilderEnabled() throws Exception {
    enableAutomationBuilder();
    mockMvc
        .perform(
            get("/api/automation-rules").with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner")))
        .andExpect(status().isOk());
  }
}
