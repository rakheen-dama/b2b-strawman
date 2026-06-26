package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Epic 585A plumbing tests (ADR-322) — the gate-over-MCP machinery below the {@code propose_task}
 * tool: the synthetic zero-cost {@link AiExecution}, the public {@link
 * AiExecutionGateService#createGate}, the open-gate JSONB lookup ({@link
 * AiExecutionGateService#findPendingGateForCorrespondence}), and the new {@code
 * CREATE_TASK_FROM_CORRESPONDENCE} executor arm.
 *
 * <p>"PASS means observed": the approval path drives a REAL {@code TaskService.createTask} against
 * a real project and the created Task row is re-read from the repository — not mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GateOverMcpPlumbingTest {

  private static final String ORG_ID = "org_ai_gate_mcp_plumbing";
  private static final String GATE_TYPE = "CREATE_TASK_FROM_CORRESPONDENCE";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiExecutionService aiExecutionService;
  @Autowired private AiExecutionGateService gateService;
  @Autowired private AiExecutionGateRepository gateRepository;
  @Autowired private AiExecutionRepository executionRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private AuditEventRepository auditEventRepository;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "AI Gate MCP Plumbing Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    projectId = UUID.fromString(TestEntityHelper.createProject(mockMvc, owner, "Plumbing Matter"));
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private <T> T inReviewScope(java.util.concurrent.Callable<T> body) {
    @SuppressWarnings("unchecked")
    T[] holder = (T[]) new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_REVIEW", "AI_MANAGE"))
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

  private Map<String, Object> payload(UUID correspondenceId, String title, LocalDate dueDate) {
    Map<String, Object> p = new HashMap<>();
    p.put("correspondence_id", correspondenceId.toString());
    p.put("project_id", projectId.toString());
    p.put("title", title);
    p.put("description", "Body of the email");
    p.put("due_date", dueDate == null ? null : dueDate.toString());
    p.put("assignee_id", null);
    return p;
  }

  // ── tests ──────────────────────────────────────────────────────────────────

  @Test
  void syntheticExecutionPersistsExternallyExecutedZeroCost() {
    UUID correspondenceId = UUID.randomUUID();
    AiExecution saved =
        inReviewScope(
            () -> aiExecutionService.recordSyntheticMcpExecution(ownerMemberId, correspondenceId));

    AiExecution reread =
        inTenantTx(() -> executionRepository.findById(saved.getId()).orElseThrow());
    assertThat(reread.getStatus()).isEqualTo("EXTERNALLY_EXECUTED");
    assertThat(reread.getCostCents()).isZero();
    assertThat(reread.getInputTokens()).isZero();
    assertThat(reread.getOutputTokens()).isZero();
    assertThat(reread.getInvokedBy()).isEqualTo(ownerMemberId);
    assertThat(reread.getEntityId()).isEqualTo(correspondenceId);
    assertThat(reread.getModel()).isEqualTo("byoc");
  }

  @Test
  void createGateProducesPendingGateWithNonNullExecution() {
    UUID correspondenceId = UUID.randomUUID();
    AiExecutionGate gate =
        inReviewScope(
            () -> {
              var synthetic =
                  aiExecutionService.recordSyntheticMcpExecution(ownerMemberId, correspondenceId);
              return gateService.createGate(
                  synthetic,
                  GATE_TYPE,
                  payload(correspondenceId, "Review NDA", null),
                  "Proposed from filed email",
                  Instant.now().plus(Duration.ofHours(72)));
            });

    AiExecutionGate reread = inTenantTx(() -> gateRepository.findById(gate.getId()).orElseThrow());
    assertThat(reread.getStatus()).isEqualTo("PENDING");
    assertThat(reread.getGateType()).isEqualTo(GATE_TYPE);
    assertThat(reread.getExecution()).isNotNull();
    assertThat(reread.getExecution().getId()).isNotNull();

    // createGate emits the mandatory ai.gate.created audit row (585A.2) on the audit plane with the
    // lowercase/snake entityType.
    var created =
        readEvents("ai.gate.created").stream()
            .filter(e -> gate.getId().equals(e.getEntityId()))
            .toList();
    assertThat(created).hasSize(1);
    assertThat(created.get(0).getEntityType()).isEqualTo("ai_execution_gate");
    assertThat(created.get(0).getDetails()).containsEntry("gateType", GATE_TYPE);
  }

  @Test
  void findPendingGateForCorrespondenceLocatesOpenGateByJsonbId() {
    UUID correspondenceId = UUID.randomUUID();
    AiExecutionGate gate =
        inReviewScope(
            () -> {
              var synthetic =
                  aiExecutionService.recordSyntheticMcpExecution(ownerMemberId, correspondenceId);
              return gateService.createGate(
                  synthetic,
                  GATE_TYPE,
                  payload(correspondenceId, "Pay invoice", null),
                  "Proposed from filed email",
                  Instant.now().plus(Duration.ofHours(72)));
            });

    var found =
        inReviewScope(
            () -> gateService.findPendingGateForCorrespondence(correspondenceId, GATE_TYPE));
    assertThat(found).contains(gate.getId());

    // A different correspondence id finds nothing.
    var none =
        inReviewScope(
            () -> gateService.findPendingGateForCorrespondence(UUID.randomUUID(), GATE_TYPE));
    assertThat(none).isEmpty();
  }

  @Test
  void approvingGateCreatesTaskWithCorrespondenceBackLink() {
    UUID correspondenceId = UUID.randomUUID();
    LocalDate dueDate = LocalDate.now().plusDays(14);

    AiExecutionGate gate =
        inReviewScope(
            () -> {
              var synthetic =
                  aiExecutionService.recordSyntheticMcpExecution(ownerMemberId, correspondenceId);
              return gateService.createGate(
                  synthetic,
                  GATE_TYPE,
                  payload(correspondenceId, "File answering affidavit", dueDate),
                  "Proposed from filed email",
                  Instant.now().plus(Duration.ofHours(72)));
            });

    long before = inTenantTx(() -> taskRepository.countByProjectId(projectId));

    inReviewScope(() -> gateService.approve(gate.getId(), ownerMemberId, "Looks good"));

    long after = inTenantTx(() -> taskRepository.countByProjectId(projectId));
    assertThat(after).isEqualTo(before + 1);

    Task created =
        inTenantTx(
            () ->
                taskRepository.findByProjectId(projectId).stream()
                    .filter(t -> "File answering affidavit".equals(t.getTitle()))
                    .findFirst()
                    .orElseThrow());
    // Task created unassigned in v1 (executor passes a null org-role so assigneeId is dropped).
    assertThat(created.getAssigneeId()).isNull();
    // Back-link lives in the description (custom fields strip unknown keys).
    assertThat(created.getDescription()).contains("[From correspondence " + correspondenceId + "]");
  }

  @Test
  void approvedGateTransitionsToApproved() {
    UUID correspondenceId = UUID.randomUUID();
    AiExecutionGate gate =
        inReviewScope(
            () -> {
              var synthetic =
                  aiExecutionService.recordSyntheticMcpExecution(ownerMemberId, correspondenceId);
              return gateService.createGate(
                  synthetic,
                  GATE_TYPE,
                  payload(correspondenceId, "Call client", null),
                  "Proposed from filed email",
                  Instant.now().plus(Duration.ofHours(72)));
            });

    var approved = inReviewScope(() -> gateService.approve(gate.getId(), ownerMemberId, null));
    assertThat(approved.getStatus()).isEqualTo("APPROVED");
  }
}
