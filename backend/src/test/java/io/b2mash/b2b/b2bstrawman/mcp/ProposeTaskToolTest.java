package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGateService;
import io.b2mash.b2b.b2bstrawman.mcp.dto.FileCorrespondenceToolResponse;
import io.b2mash.b2b.b2bstrawman.mcp.dto.ProposeTaskToolResponse;
import io.b2mash.b2b.b2bstrawman.mcp.tool.CorrespondenceWriteTools;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Epic 585B — {@code propose_task} end-to-end + dedupe tests (ADR-322). Drives the {@link
 * CorrespondenceWriteTools} bean directly with {@code RequestScopes} bound, mirroring {@code
 * AttachDocumentToolTest}.
 *
 * <p>The contract under test: {@code propose_task} creates ONLY a PENDING gate (no Task before
 * approval); a second proposal for the same correspondence returns the existing gate ({@code
 * duplicate=true}); approving the gate in-product (AI_REVIEW) creates the Task; a user with
 * MCP_ACCESS but not MCP_WRITE is rejected with {@code forbidden}; and the {@code
 * mcp.write.task_proposed} audit is emitted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProposeTaskToolTest {

  private static final String ORG_ID = "org_mcp_585_propose";
  private static final String GATE_TYPE = "CREATE_TASK_FROM_CORRESPONDENCE";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private McpEnablementService enablementService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CorrespondenceWriteTools tools;
  @Autowired private AiExecutionGateService gateService;
  @Autowired private TaskRepository taskRepository;
  @Autowired private AuditEventRepository auditEventRepository;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID matterId;
  private UUID clientId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP 585 Propose Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    matterId = UUID.fromString(TestEntityHelper.createProject(mockMvc, owner, "Propose Matter"));
    clientId =
        UUID.fromString(
            TestEntityHelper.createCustomer(mockMvc, owner, "Propose Client", "pc@test.com"));

    enableMcp();
  }

  private void enableMcp() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> enablementService.enable("popia-egress-v1")));
  }

  private <T> T withCapabilities(Set<String> capabilities, java.util.concurrent.Callable<T> body) {
    @SuppressWarnings("unchecked")
    T[] holder = (T[]) new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, capabilities)
        .run(
            () -> {
              try {
                holder[0] = body.call();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    return holder[0];
  }

  private <T> T asWriter(java.util.concurrent.Callable<T> body) {
    return withCapabilities(Set.of("MCP_ACCESS", "MCP_WRITE"), body);
  }

  private <T> T asReadOnly(java.util.concurrent.Callable<T> body) {
    return withCapabilities(Set.of("MCP_ACCESS"), body);
  }

  private <T> T inReviewScope(java.util.concurrent.Callable<T> body) {
    return withCapabilities(Set.of("AI_REVIEW", "AI_MANAGE"), body);
  }

  private <T> T inTenantTx(java.util.function.Supplier<T> body) {
    @SuppressWarnings("unchecked")
    T[] holder = (T[]) new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(() -> holder[0] = transactionTemplate.execute(tx -> body.get()));
    return holder[0];
  }

  private List<AuditEvent> readEvents(String prefix) {
    @SuppressWarnings("unchecked")
    List<AuditEvent>[] holder = new List[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                holder[0] =
                    transactionTemplate.execute(
                        tx ->
                            auditEventRepository
                                .findByFilter(
                                    null, null, null, prefix, null, null, PageRequest.of(0, 200))
                                .getContent()));
    return holder[0];
  }

  private UUID fileCorrespondence(String messageId) {
    var resp =
        asWriter(
            () ->
                (FileCorrespondenceToolResponse)
                    tools.fileCorrespondence(
                        matterId,
                        clientId,
                        messageId,
                        "Subject",
                        "Body",
                        null,
                        "from@acme.co.za",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
    return resp.correspondenceId();
  }

  // ── tests ──────────────────────────────────────────────────────────────────

  @Test
  void proposeCreatesPendingGateAndNoTaskBeforeApproval() {
    UUID correspondenceId = fileCorrespondence("<propose-pending-1@mail.test>");
    long before = inTenantTx(() -> taskRepository.countByProjectId(matterId));

    var resp =
        asWriter(
            () ->
                (ProposeTaskToolResponse)
                    tools.proposeTask(
                        matterId,
                        correspondenceId,
                        "Review the contract",
                        "Please review",
                        null,
                        null));

    assertThat(resp.gateId()).isNotNull();
    assertThat(resp.status()).isEqualTo("PENDING");
    assertThat(resp.duplicate()).isFalse();

    // No Task is created by propose_task — only a PENDING gate.
    long after = inTenantTx(() -> taskRepository.countByProjectId(matterId));
    assertThat(after).isEqualTo(before);

    var open =
        inReviewScope(
            () -> gateService.findPendingGateForCorrespondence(correspondenceId, GATE_TYPE));
    assertThat(open).contains(resp.gateId());
  }

  @Test
  void secondProposeForSameCorrespondenceReturnsExistingGate() {
    UUID correspondenceId = fileCorrespondence("<propose-dedupe-1@mail.test>");

    var first =
        asWriter(
            () ->
                (ProposeTaskToolResponse)
                    tools.proposeTask(matterId, correspondenceId, "First", null, null, null));
    var second =
        asWriter(
            () ->
                (ProposeTaskToolResponse)
                    tools.proposeTask(matterId, correspondenceId, "Second", null, null, null));

    assertThat(second.duplicate()).isTrue();
    assertThat(second.gateId()).isEqualTo(first.gateId());
    assertThat(second.status()).isEqualTo("PENDING");
  }

  @Test
  void approvingProposedGateCreatesTask() {
    UUID correspondenceId = fileCorrespondence("<propose-approve-1@mail.test>");

    var resp =
        asWriter(
            () ->
                (ProposeTaskToolResponse)
                    tools.proposeTask(
                        matterId,
                        correspondenceId,
                        "Draft the deed",
                        "From the email",
                        null,
                        null));
    UUID gateId = resp.gateId();

    long before = inTenantTx(() -> taskRepository.countByProjectId(matterId));
    inReviewScope(() -> gateService.approve(gateId, ownerMemberId, "Approved"));
    long after = inTenantTx(() -> taskRepository.countByProjectId(matterId));
    assertThat(after).isEqualTo(before + 1);

    var created =
        inTenantTx(
            () ->
                taskRepository.findByProjectId(matterId).stream()
                    .filter(t -> "Draft the deed".equals(t.getTitle()))
                    .findFirst()
                    .orElseThrow());
    assertThat(created.getDescription()).contains("[From correspondence " + correspondenceId + "]");
    assertThat(created.getAssigneeId()).isNull();
  }

  @Test
  void readOnlyUserIsRejectedWithForbidden() {
    UUID correspondenceId = fileCorrespondence("<propose-forbidden-1@mail.test>");

    var result =
        asReadOnly(
            () ->
                tools.proposeTask(
                    matterId, correspondenceId, "Should be blocked", null, null, null));

    assertThat(result).isInstanceOf(CallToolResult.class);
    assertThat(((CallToolResult) result).isError()).isTrue();

    // No gate was created for the blocked correspondence.
    var open =
        inReviewScope(
            () -> gateService.findPendingGateForCorrespondence(correspondenceId, GATE_TYPE));
    assertThat(open).isEmpty();
  }

  @Test
  void emitsTaskProposedAudit() {
    UUID correspondenceId = fileCorrespondence("<propose-audit-1@mail.test>");

    var resp =
        asWriter(
            () ->
                (ProposeTaskToolResponse)
                    tools.proposeTask(
                        matterId, correspondenceId, "Audited task", null, null, null));
    UUID gateId = resp.gateId();

    var events =
        readEvents("mcp.write.task_proposed").stream()
            .filter(e -> gateId.equals(e.getEntityId()))
            .toList();
    assertThat(events).hasSize(1);
    var event = events.get(0);
    assertThat(event.getEntityType()).isEqualTo("ai_execution_gate");
    assertThat(event.getDetails()).containsEntry("tool", "propose_task");

    @SuppressWarnings("unchecked")
    List<String> entityRefs = (List<String>) event.getDetails().get("entityRefs");
    assertThat(entityRefs)
        .contains(gateId.toString(), correspondenceId.toString(), matterId.toString());
  }
}
