package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceService;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService;
import java.time.Duration;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiExecutionGateControllerTest {

  private static final String ORG_ID = "org_ai_gate_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiExecutionGateRepository gateRepository;
  @Autowired private AiExecutionRepository executionRepository;

  @MockitoBean private ChecklistInstanceService checklistInstanceService;
  @MockitoBean private ConflictCheckService conflictCheckService;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID gateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "AI Gate Controller Test Org", null);
    var ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_gate_ctrl_owner", "gate_ctrl_owner@test.com", "Owner", "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_gate_ctrl_member", "gate_ctrl_member@test.com", "Member", "member");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create a gate for testing
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_REVIEW", "AI_MANAGE"))
        .run(
            () -> {
              var execution =
                  new AiExecution(
                      "fica-verification",
                      "customer",
                      UUID.randomUUID(),
                      ownerMemberId,
                      "claude-sonnet-4-6",
                      1);
              execution.markCompleted(
                  new AiCompletionResponse(
                      "output", "claude-sonnet-4-6", 2000, 800, 1500, 0, "end_turn", 5000L),
                  4250L);
              execution = executionRepository.save(execution);

              var gate =
                  new AiExecutionGate(
                      execution,
                      "MARK_KYC_COMPLETE",
                      Map.of(
                          "checklist_item_ids",
                          List.of(UUID.randomUUID().toString()),
                          "completion_notes",
                          "AI verified"),
                      "Items are satisfied by uploaded documents",
                      Instant.now().plus(Duration.ofHours(72)));
              gate = gateRepository.save(gate);
              gateId = gate.getId();
            });
  }

  @Test
  void listGates_requiresAiReviewCapability() throws Exception {
    mockMvc
        .perform(
            get("/api/ai/gates").with(TestJwtFactory.memberJwt(ORG_ID, "user_gate_ctrl_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void listGates_returnsGatesForAuthorizedUser() throws Exception {
    mockMvc
        .perform(get("/api/ai/gates").with(TestJwtFactory.ownerJwt(ORG_ID, "user_gate_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].gateType").value("MARK_KYC_COMPLETE"))
        .andExpect(jsonPath("$.content[0].status").value("PENDING"));
  }

  @Test
  void getGate_returnsGateDetail() throws Exception {
    mockMvc
        .perform(
            get("/api/ai/gates/" + gateId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gate_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(gateId.toString()))
        .andExpect(jsonPath("$.gateType").value("MARK_KYC_COMPLETE"))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.aiReasoning").value("Items are satisfied by uploaded documents"))
        .andExpect(jsonPath("$.proposedAction").isMap());
  }

  @Test
  void approveGate_transitionsToApproved() throws Exception {
    // Create a new gate for this test to avoid interfering with other tests
    UUID newGateId = createPendingGate();

    mockMvc
        .perform(
            post("/api/ai/gates/" + newGateId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gate_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "Verified correct"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.reviewNotes").value("Verified correct"));
  }

  @Test
  void rejectGate_transitionsToRejected() throws Exception {
    UUID newGateId = createPendingGate();

    mockMvc
        .perform(
            post("/api/ai/gates/" + newGateId + "/reject")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gate_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "Not appropriate"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.reviewNotes").value("Not appropriate"));
  }

  @Test
  void getGate_returnsNotFoundForMissingGate() throws Exception {
    mockMvc
        .perform(
            get("/api/ai/gates/" + UUID.randomUUID())
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gate_ctrl_owner")))
        .andExpect(status().isNotFound());
  }

  private UUID createPendingGate() {
    UUID[] result = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_REVIEW", "AI_MANAGE"))
        .run(
            () -> {
              var execution =
                  new AiExecution(
                      "fica-verification",
                      "customer",
                      UUID.randomUUID(),
                      ownerMemberId,
                      "claude-sonnet-4-6",
                      1);
              execution.markCompleted(
                  new AiCompletionResponse(
                      "output", "claude-sonnet-4-6", 2000, 800, 1500, 0, "end_turn", 5000L),
                  4250L);
              execution = executionRepository.save(execution);

              var gate =
                  new AiExecutionGate(
                      execution,
                      "MARK_KYC_COMPLETE",
                      Map.of(
                          "checklist_item_ids",
                          List.of(UUID.randomUUID().toString()),
                          "completion_notes",
                          "AI verified"),
                      "Items are satisfied by uploaded documents",
                      Instant.now().plus(Duration.ofHours(72)));
              gate = gateRepository.save(gate);
              result[0] = gate.getId();
            });
    return result[0];
  }
}
