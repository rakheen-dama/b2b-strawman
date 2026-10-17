package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.List;
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

/**
 * Replacement-path test for Epic 604A (Phase 86, ADR-334): the {@code automation_builder} module
 * and the rule builder's write endpoints are gone, and the automation admin surface is always on.
 * With no horizontal module enabled, an owner can list rules, read a rule, toggle it, list
 * executions and list templates (200 — previously 403 "Module not enabled"); the removed builder
 * write endpoints answer 405 (a GET mapping remains on the same path) or 404 (unmapped); and the
 * headless execution engine still fires.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutomationWithoutBuilderModuleTest {

  private static final String ORG_ID = "org_automation_no_builder_test";
  private static final UUID ACTOR_MEMBER_ID = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private AutomationExecutionRepository executionRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schemaName;
  private UUID toggleRuleId;

  @BeforeAll
  void setup() throws Exception {
    schemaName =
        provisioningService
            .provisionTenant(ORG_ID, "Automation Without Builder Test Org", null)
            .schemaName();
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_auto_owner", "auto@test.com", "Owner", "owner");
    // Disable seeded template rules so they don't interfere with the execution-engine test.
    disableSeededRules();
    toggleRuleId = createDisabledRule("Toggle Target Rule");
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

  /** Creates a disabled CUSTOM rule directly via the repository (rules have no create API). */
  private UUID createDisabledRule(String name) {
    UUID[] holder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var rule =
                          new AutomationRule(
                              name,
                              "always-on toggle target",
                              TriggerType.TASK_STATUS_CHANGED,
                              Map.of("toStatus", "__never__"),
                              null,
                              RuleSource.CUSTOM,
                              null,
                              ACTOR_MEMBER_ID);
                      rule.toggle();
                      holder[0] = ruleRepository.save(rule).getId();
                    }));
    return holder[0];
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

  @Test
  void automationReadEndpoints_return200_withNoModuleEnabled() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner");
    mockMvc
        .perform(get("/api/automation-rules").with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem(toggleRuleId.toString())));
    mockMvc
        .perform(get("/api/automation-rules/{id}", toggleRuleId).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Toggle Target Rule"));
    mockMvc.perform(get("/api/automation-executions").with(owner)).andExpect(status().isOk());
    mockMvc
        .perform(get("/api/automation-rules/{id}/executions", toggleRuleId).with(owner))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/automation-templates").with(owner)).andExpect(status().isOk());
  }

  @Test
  void toggle_returns200_withNoModuleEnabled() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner");
    UUID ruleId = createDisabledRule("Toggle Twice Rule");
    mockMvc
        .perform(post("/api/automation-rules/{id}/toggle", ruleId).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));
    mockMvc
        .perform(post("/api/automation-rules/{id}/toggle", ruleId).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  void builderWriteEndpoints_areGone() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner");
    String body =
        """
        {"name": "x", "triggerType": "TASK_STATUS_CHANGED", "triggerConfig": {}, "actions": []}
        """;
    // A GET mapping survives on these paths, so Spring answers 405 Method Not Allowed.
    mockMvc
        .perform(
            post("/api/automation-rules")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isMethodNotAllowed());
    mockMvc
        .perform(
            put("/api/automation-rules/{id}", toggleRuleId)
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isMethodNotAllowed());
    mockMvc
        .perform(delete("/api/automation-rules/{id}", toggleRuleId).with(owner))
        .andExpect(status().isMethodNotAllowed());
    // No mapping at all on these paths.
    for (String suffix : List.of("duplicate", "test", "actions")) {
      mockMvc
          .perform(
              post("/api/automation-rules/{id}/" + suffix, toggleRuleId)
                  .with(owner)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isNotFound());
    }
    // The rule is untouched by the rejected writes.
    mockMvc
        .perform(get("/api/automation-rules/{id}", toggleRuleId).with(owner))
        .andExpect(status().isOk());
  }

  @Test
  void executionEngine_continuesRunning_withNoHorizontalModulesEnabled() {
    // No horizontal modules enabled (set by @BeforeEach)
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Insert a rule directly via repository (rules are pack-installed; no create API)
              UUID[] ruleIdHolder = new UUID[1];
              transactionTemplate.executeWithoutResult(
                  tx -> {
                    var rule =
                        new AutomationRule(
                            "Engine Isolation Rule",
                            "Verifies engine fires with no horizontal modules enabled",
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

              // Verify the engine fired
              var executions =
                  executionRepository.findByRuleIdOrderByStartedAtDesc(ruleIdHolder[0]);
              assertThat(executions).isNotEmpty();
            });
  }
}
