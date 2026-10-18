package io.b2mash.b2b.b2bstrawman.automation;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Controller test for the surviving automation-rule endpoints (ADR-334): {@code GET
 * /api/automation-rules}, {@code GET /api/automation-rules/{id}}, {@code POST
 * /api/automation-rules/{id}/toggle} and the execution reads. The rule builder's write endpoints
 * were removed in Phase 86 (Epic 604A), so fixtures are seeded directly via the repositories (rules
 * are otherwise installed by packs). The tenant also holds pack-installed rules, so list assertions
 * check containment of the seeded ids, never an exact count.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutomationRuleControllerTest {
  private static final String ORG_ID = "org_auto_ctrl";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private AutomationActionRepository actionRepository;
  @Autowired private AutomationExecutionRepository executionRepository;
  @Autowired private ActionExecutionRepository actionExecutionRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;

  private String memberIdOwner;
  private String tenantSchema;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Automation Controller Test Org", null);
    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_auto_owner", "auto_owner@test.com", "Auto Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_auto_member", "auto_member@test.com", "Auto Member", "member");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    customRoleMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_auto_315b_custom",
                "auto_custom@test.com",
                "Auto Custom User",
                "member"));
    noCapMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_auto_315b_nocap",
                "auto_nocap@test.com",
                "Auto NoCap User",
                "member"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberIdOwner))
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var withCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Automation Manager", "Can manage automations", Set.of("AUTOMATIONS")));
              var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
              customMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withCapRole.id()).orElseThrow());
              memberRepository.save(customMember);

              var withoutCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Team Lead Auto", "Can manage teams", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withoutCapRole.id()).orElseThrow());
              memberRepository.save(noCapMember);
            });
  }

  // --- Fixture helpers (rules have no create API; packs install them in production) ---

  private UUID seedRule(String name, TriggerType triggerType, boolean enabled) {
    UUID[] holder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var rule =
                          new AutomationRule(
                              name,
                              "seeded by AutomationRuleControllerTest",
                              triggerType,
                              Map.of(),
                              null,
                              RuleSource.CUSTOM,
                              null,
                              UUID.fromString(memberIdOwner));
                      if (!enabled) {
                        rule.toggle();
                      }
                      holder[0] = ruleRepository.save(rule).getId();
                    }));
    return holder[0];
  }

  private void seedAction(UUID ruleId, ActionType actionType, int sortOrder) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx ->
                        actionRepository.save(
                            new AutomationAction(
                                ruleId,
                                sortOrder,
                                actionType,
                                Map.of("message", "Task done"),
                                null,
                                null))));
  }

  @Test
  void shouldGetRuleWithActions() throws Exception {
    UUID ruleId = seedRule("Get Test Rule", TriggerType.TASK_STATUS_CHANGED, true);
    seedAction(ruleId, ActionType.SEND_NOTIFICATION, 0);

    mockMvc
        .perform(
            get("/api/automation-rules/" + ruleId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Get Test Rule"))
        .andExpect(jsonPath("$.source").value("CUSTOM"))
        .andExpect(jsonPath("$.triggerType").value("TASK_STATUS_CHANGED"))
        .andExpect(jsonPath("$.actions", hasSize(1)))
        .andExpect(jsonPath("$.actions[0].actionType").value("SEND_NOTIFICATION"));
  }

  @Test
  void shouldReturn404ForUnknownRule() throws Exception {
    mockMvc
        .perform(
            get("/api/automation-rules/" + UUID.randomUUID())
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner")))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldListRulesFilteredByEnabled() throws Exception {
    UUID enabledId = seedRule("Enabled Filter Rule", TriggerType.PROJECT_STATUS_CHANGED, true);
    UUID disabledId = seedRule("Disabled Filter Rule", TriggerType.PROJECT_STATUS_CHANGED, false);

    mockMvc
        .perform(
            get("/api/automation-rules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner"))
                .param("enabled", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem(enabledId.toString())))
        .andExpect(jsonPath("$[*].id", not(hasItem(disabledId.toString()))));

    mockMvc
        .perform(
            get("/api/automation-rules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner"))
                .param("enabled", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem(disabledId.toString())))
        .andExpect(jsonPath("$[*].id", not(hasItem(enabledId.toString()))));
  }

  @Test
  void shouldListRulesFilteredByTriggerType() throws Exception {
    UUID taskRuleId = seedRule("Task Trigger Rule", TriggerType.TASK_STATUS_CHANGED, true);
    UUID projectRuleId = seedRule("Project Trigger Rule", TriggerType.PROJECT_STATUS_CHANGED, true);

    mockMvc
        .perform(
            get("/api/automation-rules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner"))
                .param("triggerType", "TASK_STATUS_CHANGED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem(taskRuleId.toString())))
        .andExpect(jsonPath("$[*].id", not(hasItem(projectRuleId.toString()))));
  }

  @Test
  void shouldToggleRuleEnabledState() throws Exception {
    UUID ruleId = seedRule("Toggle Me", TriggerType.TASK_STATUS_CHANGED, true);

    // Toggle off
    mockMvc
        .perform(
            post("/api/automation-rules/" + ruleId + "/toggle")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));

    // Toggle on
    mockMvc
        .perform(
            post("/api/automation-rules/" + ruleId + "/toggle")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));
  }

  @Test
  void shouldListExecutionsForRule() throws Exception {
    UUID ruleId = seedRule("Exec List Rule", TriggerType.TASK_STATUS_CHANGED, true);

    // Insert an execution directly via ScopedValue
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var execution =
                  new AutomationExecution(
                      ruleId,
                      "TASK_STATUS_CHANGED",
                      Map.of("task", Map.of("status", "DONE")),
                      true,
                      ExecutionStatus.ACTIONS_COMPLETED);
              executionRepository.save(execution);
            });

    mockMvc
        .perform(
            get("/api/automation-rules/" + ruleId + "/executions")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].ruleId").value(ruleId.toString()));
  }

  @Test
  void shouldGetExecutionDetail() throws Exception {
    UUID ruleId = seedRule("Exec Detail Rule", TriggerType.TASK_STATUS_CHANGED, true);

    // Insert execution and action execution directly
    UUID[] executionIdHolder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var execution =
                  new AutomationExecution(
                      ruleId,
                      "TASK_STATUS_CHANGED",
                      Map.of("task", Map.of("status", "DONE")),
                      true,
                      ExecutionStatus.ACTIONS_COMPLETED);
              execution = executionRepository.save(execution);
              executionIdHolder[0] = execution.getId();

              var actionExec =
                  new ActionExecution(
                      execution.getId(), null, ActionExecutionStatus.COMPLETED, Instant.now());
              actionExec.complete(Map.of("result", "ok"));
              actionExecutionRepository.save(actionExec);
            });

    mockMvc
        .perform(
            get("/api/automation-executions/" + executionIdHolder[0])
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(executionIdHolder[0].toString()))
        .andExpect(jsonPath("$.ruleName").value("Exec Detail Rule"))
        .andExpect(jsonPath("$.actionExecutions").isArray());
  }

  @Test
  void shouldForbidMemberFromAutomationEndpoints() throws Exception {
    UUID ruleId = seedRule("Member Forbidden Rule", TriggerType.TASK_STATUS_CHANGED, true);

    mockMvc
        .perform(
            get("/api/automation-rules").with(TestJwtFactory.memberJwt(ORG_ID, "user_auto_member")))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/automation-rules/" + ruleId + "/toggle")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_auto_member")))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/automation-executions")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_auto_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldCreateAuditEventsOnRuleToggle() throws Exception {
    UUID ruleId = seedRule("Audit Rule", TriggerType.TASK_STATUS_CHANGED, true);

    // Check audit events in the tenant schema — validate schema name to prevent SQL injection
    String safeSchema = sanitizeSchemaName(tenantSchema);

    // Toggle off → automation_rule.disabled
    mockMvc
        .perform(
            post("/api/automation-rules/" + ruleId + "/toggle")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner")))
        .andExpect(status().isOk());

    Integer disabledCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM "
                + safeSchema
                + ".audit_events WHERE entity_id = ?::uuid AND event_type = 'automation_rule.disabled'",
            Integer.class,
            ruleId.toString());
    assertTrue(
        disabledCount != null && disabledCount > 0,
        "Expected at least one audit event for rule disable");

    // Toggle on → automation_rule.enabled
    mockMvc
        .perform(
            post("/api/automation-rules/" + ruleId + "/toggle")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_auto_owner")))
        .andExpect(status().isOk());

    Integer enabledCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM "
                + safeSchema
                + ".audit_events WHERE entity_id = ?::uuid AND event_type = 'automation_rule.enabled'",
            Integer.class,
            ruleId.toString());
    assertTrue(
        enabledCount != null && enabledCount > 0,
        "Expected at least one audit event for rule enable");
  }

  // --- Capability Tests (added in Epic 315B) ---

  @Test
  void customRoleWithCapability_accessesAutomationEndpoint_returns200() throws Exception {
    UUID ruleId = seedRule("Cap Test Rule", TriggerType.TASK_STATUS_CHANGED, true);
    mockMvc
        .perform(
            post("/api/automation-rules/" + ruleId + "/toggle")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_auto_315b_custom")))
        .andExpect(status().isOk());
  }

  @Test
  void customRoleWithoutCapability_accessesAutomationEndpoint_returns403() throws Exception {
    UUID ruleId = seedRule("NoCap Rule", TriggerType.TASK_STATUS_CHANGED, true);
    mockMvc
        .perform(
            post("/api/automation-rules/" + ruleId + "/toggle")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_auto_315b_nocap")))
        .andExpect(status().isForbidden());
  }

  /**
   * Validates that a schema name contains only safe characters (alphanumeric, underscores, hyphens)
   * to prevent SQL injection when used in SET search_path.
   */
  private static String sanitizeSchemaName(String schema) {
    if (schema == null || !schema.matches("^[a-zA-Z0-9_\\-]+$")) {
      throw new IllegalArgumentException("Invalid schema name: " + schema);
    }
    return schema;
  }
}
