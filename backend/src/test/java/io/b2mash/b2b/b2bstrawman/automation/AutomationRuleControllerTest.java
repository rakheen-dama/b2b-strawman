package io.b2mash.b2b.b2bstrawman.automation;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutomationRuleControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_auto_ctrl";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private AutomationExecutionRepository executionRepository;
  @Autowired private ActionExecutionRepository actionExecutionRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private MemberRepository memberRepository;

  private String memberIdOwner;
  private String tenantSchema;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Automation Controller Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberIdOwner = syncMember("user_auto_owner", "auto_owner@test.com", "Auto Owner", "owner");
    syncMember("user_auto_member", "auto_member@test.com", "Auto Member", "member");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    customRoleMemberId =
        UUID.fromString(
            syncMember(
                "user_auto_315b_custom", "auto_custom@test.com", "Auto Custom User", "member"));
    noCapMemberId =
        UUID.fromString(
            syncMember("user_auto_315b_nocap", "auto_nocap@test.com", "Auto NoCap User", "member"));

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
              customMember.setOrgRoleId(withCapRole.id());
              memberRepository.save(customMember);

              var withoutCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Team Lead Auto", "Can manage teams", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleId(withoutCapRole.id());
              memberRepository.save(noCapMember);
            });
  }

  @Test
  void shouldCreateRule() throws Exception {
    mockMvc
        .perform(
            post("/api/automation-rules")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Test Rule",
                      "description": "A test automation rule",
                      "triggerType": "TASK_STATUS_CHANGED",
                      "triggerConfig": {"from": "IN_PROGRESS", "to": "COMPLETED"},
                      "conditions": [{"field": "task.status", "operator": "EQUALS", "value": "COMPLETED"}]
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.name").value("Test Rule"))
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.source").value("CUSTOM"))
        .andExpect(jsonPath("$.triggerType").value("TASK_STATUS_CHANGED"));
  }

  @Test
  void shouldGetRuleWithActions() throws Exception {
    // Create rule
    var createResult =
        mockMvc
            .perform(
                post("/api/automation-rules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Get Test Rule",
                          "triggerType": "TASK_STATUS_CHANGED",
                          "triggerConfig": {}
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Add action
    mockMvc
        .perform(
            post("/api/automation-rules/" + ruleId + "/actions")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "actionType": "SEND_NOTIFICATION",
                      "actionConfig": {"message": "Task done"},
                      "sortOrder": 0
                    }
                    """))
        .andExpect(status().isCreated());

    // Get rule with actions
    mockMvc
        .perform(get("/api/automation-rules/" + ruleId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Get Test Rule"))
        .andExpect(jsonPath("$.actions", hasSize(1)))
        .andExpect(jsonPath("$.actions[0].actionType").value("SEND_NOTIFICATION"));
  }

  @Test
  void shouldListRulesFilteredByEnabled() throws Exception {
    // Create enabled rule
    mockMvc
        .perform(
            post("/api/automation-rules")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Enabled Filter Rule",
                      "triggerType": "PROJECT_STATUS_CHANGED",
                      "triggerConfig": {}
                    }
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/automation-rules").with(ownerJwt()).param("enabled", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void shouldListRulesFilteredByTriggerType() throws Exception {
    mockMvc
        .perform(
            get("/api/automation-rules")
                .with(ownerJwt())
                .param("triggerType", "TASK_STATUS_CHANGED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void shouldUpdateRule() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/automation-rules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Update Me",
                          "triggerType": "TASK_STATUS_CHANGED",
                          "triggerConfig": {}
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            put("/api/automation-rules/" + ruleId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Updated Name",
                      "triggerType": "PROJECT_STATUS_CHANGED",
                      "triggerConfig": {"status": "ACTIVE"}
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Name"))
        .andExpect(jsonPath("$.triggerType").value("PROJECT_STATUS_CHANGED"));
  }

  @Test
  void shouldDeleteRuleWithCascade() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/automation-rules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Delete Me",
                          "triggerType": "TASK_STATUS_CHANGED",
                          "triggerConfig": {}
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Add action
    mockMvc
        .perform(
            post("/api/automation-rules/" + ruleId + "/actions")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"actionType": "SEND_NOTIFICATION", "actionConfig": {"msg": "test"}, "sortOrder": 0}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(delete("/api/automation-rules/" + ruleId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify deleted
    mockMvc
        .perform(get("/api/automation-rules/" + ruleId).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldToggleRuleEnabledState() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/automation-rules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Toggle Me",
                          "triggerType": "TASK_STATUS_CHANGED",
                          "triggerConfig": {}
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Toggle off
    mockMvc
        .perform(post("/api/automation-rules/" + ruleId + "/toggle").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));

    // Toggle on
    mockMvc
        .perform(post("/api/automation-rules/" + ruleId + "/toggle").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));
  }

  @Test
  void shouldDuplicateRule() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/automation-rules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Original Rule",
                          "triggerType": "TASK_STATUS_CHANGED",
                          "triggerConfig": {"key": "val"}
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Add action to original
    mockMvc
        .perform(
            post("/api/automation-rules/" + ruleId + "/actions")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"actionType": "CREATE_TASK", "actionConfig": {"name": "Auto task"}, "sortOrder": 0}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(post("/api/automation-rules/" + ruleId + "/duplicate").with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Original Rule (Copy)"))
        .andExpect(jsonPath("$.source").value("CUSTOM"))
        .andExpect(jsonPath("$.actions", hasSize(1)))
        .andExpect(jsonPath("$.actions[0].actionType").value("CREATE_TASK"));
  }

  @Test
  void shouldAddAction() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/automation-rules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Action Rule", "triggerType": "TASK_STATUS_CHANGED", "triggerConfig": {}}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/automation-rules/" + ruleId + "/actions")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "actionType": "SEND_EMAIL",
                      "actionConfig": {"to": "test@example.com"},
                      "sortOrder": 0,
                      "delayDuration": 30,
                      "delayUnit": "MINUTES"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.actionType").value("SEND_EMAIL"))
        .andExpect(jsonPath("$.delayDuration").value(30))
        .andExpect(jsonPath("$.delayUnit").value("MINUTES"));
  }

  @Test
  void shouldUpdateAction() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/automation-rules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Update Action Rule", "triggerType": "TASK_STATUS_CHANGED", "triggerConfig": {}}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    var actionResult =
        mockMvc
            .perform(
                post("/api/automation-rules/" + ruleId + "/actions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"actionType": "SEND_NOTIFICATION", "actionConfig": {"msg": "old"}, "sortOrder": 0}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String actionId = JsonPath.read(actionResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            put("/api/automation-rules/" + ruleId + "/actions/" + actionId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"actionType": "SEND_EMAIL", "actionConfig": {"msg": "new"}, "sortOrder": 1}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actionType").value("SEND_EMAIL"))
        .andExpect(jsonPath("$.sortOrder").value(1));
  }

  @Test
  void shouldRemoveAction() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/automation-rules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Remove Action Rule", "triggerType": "TASK_STATUS_CHANGED", "triggerConfig": {}}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    var actionResult =
        mockMvc
            .perform(
                post("/api/automation-rules/" + ruleId + "/actions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"actionType": "SEND_NOTIFICATION", "actionConfig": {}, "sortOrder": 0}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String actionId = JsonPath.read(actionResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            delete("/api/automation-rules/" + ruleId + "/actions/" + actionId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify action removed
    mockMvc
        .perform(get("/api/automation-rules/" + ruleId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actions", hasSize(0)));
  }

  @Test
  void shouldReorderActions() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/automation-rules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Reorder Rule", "triggerType": "TASK_STATUS_CHANGED", "triggerConfig": {}}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    var action1Result =
        mockMvc
            .perform(
                post("/api/automation-rules/" + ruleId + "/actions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"actionType": "SEND_NOTIFICATION", "actionConfig": {}, "sortOrder": 0}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String action1Id = JsonPath.read(action1Result.getResponse().getContentAsString(), "$.id");

    var action2Result =
        mockMvc
            .perform(
                post("/api/automation-rules/" + ruleId + "/actions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"actionType": "CREATE_TASK", "actionConfig": {}, "sortOrder": 1}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String action2Id = JsonPath.read(action2Result.getResponse().getContentAsString(), "$.id");

    // Reorder: action2 first, then action1
    mockMvc
        .perform(
            put("/api/automation-rules/" + ruleId + "/actions/reorder")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"actionIds": ["%s", "%s"]}
                    """
                        .formatted(action2Id, action1Id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(action2Id))
        .andExpect(jsonPath("$[0].sortOrder").value(0))
        .andExpect(jsonPath("$[1].id").value(action1Id))
        .andExpect(jsonPath("$[1].sortOrder").value(1));
  }

  @Test
  void shouldTestRuleWithConditionsMet() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/automation-rules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Test Conditions Met",
                          "triggerType": "TASK_STATUS_CHANGED",
                          "triggerConfig": {},
                          "conditions": [{"field": "task.status", "operator": "EQUALS", "value": "COMPLETED"}]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/automation-rules/" + ruleId + "/test")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"sampleEventData": {"task": {"status": "COMPLETED"}}}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conditionsMet").value(true))
        .andExpect(jsonPath("$.evaluationDetails").isArray());
  }

  @Test
  void shouldTestRuleWithConditionsNotMet() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/automation-rules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Test Conditions Not Met",
                          "triggerType": "TASK_STATUS_CHANGED",
                          "triggerConfig": {},
                          "conditions": [{"field": "task.status", "operator": "EQUALS", "value": "COMPLETED"}]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/automation-rules/" + ruleId + "/test")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"sampleEventData": {"task": {"status": "IN_PROGRESS"}}}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conditionsMet").value(false));
  }

  @Test
  void shouldListExecutionsForRule() throws Exception {
    // Create rule
    var createResult =
        mockMvc
            .perform(
                post("/api/automation-rules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Exec List Rule", "triggerType": "TASK_STATUS_CHANGED", "triggerConfig": {}}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Insert an execution directly via ScopedValue
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var execution =
                  new AutomationExecution(
                      UUID.fromString(ruleId),
                      "TASK_STATUS_CHANGED",
                      Map.of("task", Map.of("status", "COMPLETED")),
                      true,
                      ExecutionStatus.ACTIONS_COMPLETED);
              executionRepository.save(execution);
            });

    mockMvc
        .perform(get("/api/automation-rules/" + ruleId + "/executions").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].ruleId").value(ruleId));
  }

  @Test
  void shouldGetExecutionDetail() throws Exception {
    // Create rule
    var createResult =
        mockMvc
            .perform(
                post("/api/automation-rules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Exec Detail Rule", "triggerType": "TASK_STATUS_CHANGED", "triggerConfig": {}}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Insert execution and action execution directly
    String schema = tenantSchema;
    UUID[] executionIdHolder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () -> {
              var execution =
                  new AutomationExecution(
                      UUID.fromString(ruleId),
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
        .perform(get("/api/automation-executions/" + executionIdHolder[0]).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(executionIdHolder[0].toString()))
        .andExpect(jsonPath("$.ruleName").value("Exec Detail Rule"))
        .andExpect(jsonPath("$.actionExecutions").isArray());
  }

  @Test
  void shouldForbidMemberFromCrud() throws Exception {
    mockMvc
        .perform(
            post("/api/automation-rules")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Forbidden", "triggerType": "TASK_STATUS_CHANGED", "triggerConfig": {}}
                    """))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/automation-rules").with(memberJwt()))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/automation-executions").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldCreateAuditEventsOnRuleCrud() throws Exception {
    // Create rule
    var createResult =
        mockMvc
            .perform(
                post("/api/automation-rules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Audit Rule", "triggerType": "TASK_STATUS_CHANGED", "triggerConfig": {}}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Check audit events in the tenant schema — validate schema name to prevent SQL injection
    String safeSchema = sanitizeSchemaName(tenantSchema);

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM "
                + safeSchema
                + ".audit_events WHERE entity_id = ?::uuid AND event_type = 'automation_rule.created'",
            Integer.class,
            ruleId);
    assertTrue(count != null && count > 0, "Expected at least one audit event for rule creation");

    // Toggle to generate enabled/disabled audit events
    mockMvc
        .perform(post("/api/automation-rules/" + ruleId + "/toggle").with(ownerJwt()))
        .andExpect(status().isOk());

    Integer disabledCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM "
                + safeSchema
                + ".audit_events WHERE entity_id = ?::uuid AND event_type = 'automation_rule.disabled'",
            Integer.class,
            ruleId);
    assertTrue(
        disabledCount != null && disabledCount > 0,
        "Expected at least one audit event for rule disable");

    // Delete to generate deletion audit
    mockMvc
        .perform(delete("/api/automation-rules/" + ruleId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    Integer deletedCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM "
                + safeSchema
                + ".audit_events WHERE entity_id = ?::uuid AND event_type = 'automation_rule.deleted'",
            Integer.class,
            ruleId);
    assertTrue(
        deletedCount != null && deletedCount > 0,
        "Expected at least one audit event for rule deletion");
  }

  // --- Capability Tests (added in Epic 315B) ---

  @Test
  void customRoleWithCapability_accessesAutomationEndpoint_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/automation-rules")
                .with(customRoleJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Cap Test Rule", "triggerType": "TASK_STATUS_CHANGED", "triggerConfig": {}}
                    """))
        .andExpect(status().isCreated());
  }

  @Test
  void customRoleWithoutCapability_accessesAutomationEndpoint_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/automation-rules")
                .with(noCapabilityJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "NoCap Rule", "triggerType": "TASK_STATUS_CHANGED", "triggerConfig": {}}
                    """))
        .andExpect(status().isForbidden());
  }

  // --- Helper methods ---

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        { "clerkOrgId": "%s", "clerkUserId": "%s", "email": "%s",
                          "name": "%s", "avatarUrl": null, "orgRole": "%s" }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_auto_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_auto_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor customRoleJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_auto_315b_custom")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor noCapabilityJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_auto_315b_nocap").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
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
