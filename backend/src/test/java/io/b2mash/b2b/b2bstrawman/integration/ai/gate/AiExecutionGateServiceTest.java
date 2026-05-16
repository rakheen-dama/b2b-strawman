package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiExecutionGateServiceTest {

  private static final String ORG_ID = "org_ai_gate_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiExecutionGateService gateService;
  @Autowired private AiExecutionGateRepository gateRepository;
  @Autowired private AiExecutionRepository executionRepository;

  @MockitoBean private ChecklistInstanceService checklistInstanceService;
  @MockitoBean private ConflictCheckService conflictCheckService;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "AI Gate Service Test Org", null);
    var ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_gate_svc_owner", "gate_svc_owner@test.com", "Owner", "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void approve_transitionsGateToApprovedAndExecutesAction() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_REVIEW", "AI_MANAGE"))
        .run(
            () -> {
              var execution = createExecution();
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

              var approved = gateService.approve(gate.getId(), ownerMemberId, "Looks good");

              assertThat(approved.getStatus()).isEqualTo("APPROVED");
              assertThat(approved.getReviewedBy()).isEqualTo(ownerMemberId);
              assertThat(approved.getReviewNotes()).isEqualTo("Looks good");
              assertThat(approved.getReviewedAt()).isNotNull();
            });
  }

  @Test
  void approve_onNonPendingGate_throwsInvalidState() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_REVIEW", "AI_MANAGE"))
        .run(
            () -> {
              var execution = createExecution();
              var gate =
                  new AiExecutionGate(
                      execution,
                      "SELECT_MATTER_TEMPLATE",
                      Map.of(
                          "template_id",
                          UUID.randomUUID().toString(),
                          "customisation_notes",
                          "Standard"),
                      "Template is suitable",
                      Instant.now().plus(Duration.ofHours(72)));
              gate = gateRepository.save(gate);

              // Approve first
              gateService.approve(gate.getId(), ownerMemberId, null);

              // Try to approve again — should fail
              UUID gateId = gate.getId();
              assertThatThrownBy(() -> gateService.approve(gateId, ownerMemberId, "Again"))
                  .isInstanceOf(InvalidStateException.class);
            });
  }

  @Test
  void reject_transitionsToRejectedWithNotes() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_REVIEW", "AI_MANAGE"))
        .run(
            () -> {
              var execution = createExecution();
              var gate =
                  new AiExecutionGate(
                      execution,
                      "CONFIRM_CONFLICT_SCREEN",
                      Map.of(
                          "conflict_check_id",
                          UUID.randomUUID().toString(),
                          "clearance_notes",
                          "No conflict found"),
                      "No conflicting matter detected",
                      Instant.now().plus(Duration.ofHours(72)));
              gate = gateRepository.save(gate);

              var rejected =
                  gateService.reject(gate.getId(), ownerMemberId, "Conflict actually exists");

              assertThat(rejected.getStatus()).isEqualTo("REJECTED");
              assertThat(rejected.getReviewedBy()).isEqualTo(ownerMemberId);
              assertThat(rejected.getReviewNotes()).isEqualTo("Conflict actually exists");
              assertThat(rejected.getReviewedAt()).isNotNull();
            });
  }

  @Test
  void expireStaleGates_expiresPastDueGates() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_REVIEW", "AI_MANAGE"))
        .run(
            () -> {
              var execution = createExecution();
              var gate =
                  new AiExecutionGate(
                      execution,
                      "MARK_KYC_COMPLETE",
                      Map.of(
                          "checklist_item_ids",
                          List.of(UUID.randomUUID().toString()),
                          "completion_notes",
                          "Expired test"),
                      "Should be expired",
                      Instant.now().minus(Duration.ofHours(1))); // already expired
              gate = gateRepository.save(gate);
              UUID gateId = gate.getId();

              // Run the expiry sweep manually
              gateService.expireStaleGates();

              var expired = gateRepository.findById(gateId).orElseThrow();
              assertThat(expired.getStatus()).isEqualTo("EXPIRED");
            });
  }

  @Test
  void expireStaleGates_doesNotAffectUnexpiredGates() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_REVIEW", "AI_MANAGE"))
        .run(
            () -> {
              var execution = createExecution();
              var gate =
                  new AiExecutionGate(
                      execution,
                      "MARK_KYC_COMPLETE",
                      Map.of(
                          "checklist_item_ids",
                          List.of(UUID.randomUUID().toString()),
                          "completion_notes",
                          "Not yet expired"),
                      "Should remain pending",
                      Instant.now().plus(Duration.ofHours(72))); // future expiry
              gate = gateRepository.save(gate);
              UUID gateId = gate.getId();

              // Run the expiry sweep manually
              gateService.expireStaleGates();

              var stillPending = gateRepository.findById(gateId).orElseThrow();
              assertThat(stillPending.getStatus()).isEqualTo("PENDING");
            });
  }

  private AiExecution createExecution() {
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
    return executionRepository.save(execution);
  }
}
