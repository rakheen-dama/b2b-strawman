package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiExecutionGateTest {

  private static final String ORG_ID = "org_ai_gate_test";

  @Autowired private AiExecutionGateRepository gateRepository;
  @Autowired private AiExecutionRepository executionRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "AI Gate Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private AiExecution createExecution() {
    var execution =
        new AiExecution(
            "fica-verification",
            "CUSTOMER",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "claude-sonnet-4-6",
            1);
    execution.markCompleted(
        new AiCompletionResponse("{}", "claude-sonnet-4-6", 100, 50, 0, 0, "end_turn", 100L),
        1000L);
    return executionRepository.save(execution);
  }

  @Test
  void persist_withJsonbProposedAction() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var execution = createExecution();
              var proposedAction =
                  Map.<String, Object>of(
                      "checklist_item_ids",
                      List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()),
                      "completion_notes",
                      "All documents verified via AI");

              var gate =
                  new AiExecutionGate(
                      execution,
                      "MARK_KYC_COMPLETE",
                      proposedAction,
                      "Documents match ID requirements per FICA",
                      Instant.now().plus(72, ChronoUnit.HOURS));

              var saved = gateRepository.save(gate);
              assertThat(saved.getId()).isNotNull();

              var loaded = gateRepository.findById(saved.getId()).orElseThrow();
              assertThat(loaded.getGateType()).isEqualTo("MARK_KYC_COMPLETE");
              assertThat(loaded.getStatus()).isEqualTo("PENDING");
              assertThat(loaded.getAiReasoning())
                  .isEqualTo("Documents match ID requirements per FICA");
              assertThat(loaded.getProposedAction()).containsKey("checklist_item_ids");
              assertThat(loaded.getProposedAction()).containsKey("completion_notes");
              assertThat(loaded.getExpiresAt()).isAfter(Instant.now());
            });
  }

  @Test
  void approve_setsStatusAndReviewFields() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var execution = createExecution();
              var gate =
                  new AiExecutionGate(
                      execution,
                      "SELECT_MATTER_TEMPLATE",
                      Map.of("template_id", UUID.randomUUID().toString()),
                      "Template matches matter type",
                      Instant.now().plus(72, ChronoUnit.HOURS));
              gateRepository.save(gate);

              UUID reviewerId = UUID.randomUUID();
              gate.approve(reviewerId, "Looks good");
              var saved = gateRepository.save(gate);

              assertThat(saved.getStatus()).isEqualTo("APPROVED");
              assertThat(saved.getReviewedBy()).isEqualTo(reviewerId);
              assertThat(saved.getReviewedAt()).isNotNull();
              assertThat(saved.getReviewNotes()).isEqualTo("Looks good");
            });
  }

  @Test
  void reject_setsStatusAndReviewFields() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var execution = createExecution();
              var gate =
                  new AiExecutionGate(
                      execution,
                      "CONFIRM_CONFLICT_SCREEN",
                      Map.of("conflict_check_id", UUID.randomUUID().toString()),
                      "No conflicts detected",
                      Instant.now().plus(72, ChronoUnit.HOURS));
              gateRepository.save(gate);

              UUID reviewerId = UUID.randomUUID();
              gate.reject(reviewerId, "Need manual review");
              var saved = gateRepository.save(gate);

              assertThat(saved.getStatus()).isEqualTo("REJECTED");
              assertThat(saved.getReviewedBy()).isEqualTo(reviewerId);
              assertThat(saved.getReviewNotes()).isEqualTo("Need manual review");
            });
  }

  @Test
  void requirePendingStatus_throwsOnNonPending() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var execution = createExecution();
              var gate =
                  new AiExecutionGate(
                      execution,
                      "MARK_KYC_COMPLETE",
                      Map.of("checklist_item_ids", List.of()),
                      "Test reasoning",
                      Instant.now().plus(72, ChronoUnit.HOURS));
              gateRepository.save(gate);

              // Approve first
              gate.approve(UUID.randomUUID(), null);
              gateRepository.save(gate);

              // Now calling requirePendingStatus should throw
              assertThatThrownBy(gate::requirePendingStatus)
                  .isInstanceOf(InvalidStateException.class);
            });
  }
}
