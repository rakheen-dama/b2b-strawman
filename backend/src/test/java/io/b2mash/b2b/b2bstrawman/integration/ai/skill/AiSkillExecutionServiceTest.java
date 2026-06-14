package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGateRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.intake.MatterIntakeOutput;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiSkillExecutionServiceTest {

  private static final String ORG_ID = "org_ai_skill_exec_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiSkillExecutionService executionService;
  @Autowired private AiFirmProfileService firmProfileService;
  @Autowired private AiFirmProfileRepository firmProfileRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private AiExecutionPersistenceService persistenceService;
  @Autowired private AiExecutionRepository executionRepository;
  @Autowired private AiExecutionGateRepository gateRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private LlmJsonParser llmJsonParser;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "AI Skill Execution Test Org", null);
    var ownerStr =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_skill_exec_owner",
            "skill_exec_owner@test.com",
            "Skill Exec Owner",
            "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  @Order(1)
  void executeSkill_completesSuccessfully_recordsExecution() {
    runInTenant(
        () -> {
          var skill = new TestSkill();
          var context =
              new SkillContext(UUID.randomUUID(), "CUSTOMER", "Test verification", Map.of());
          var request = new SkillExecutionRequest(skill, context, ownerMemberId, List.of());

          SkillExecutionResult result = executionService.executeSkill(request);
          AiExecution execution = result.execution();

          assertThat(execution).isNotNull();
          assertThat(execution.getId()).isNotNull();
          assertThat(execution.getStatus()).isEqualTo("COMPLETED");
          assertThat(execution.getSkillId()).isEqualTo("test-skill");
          assertThat(execution.getEntityType()).isEqualTo("CUSTOMER");
          assertThat(execution.getModel()).isEqualTo("claude-sonnet-4-6");
          assertThat(execution.getInputTokens()).isEqualTo(2000);
          assertThat(execution.getOutputTokens()).isEqualTo(800);
          assertThat(execution.getOutputContent()).contains("verification_result");
        });
  }

  @Test
  @Order(2)
  void executeSkill_createsGatesFromSkillOutput() {
    runInTenant(
        () -> {
          var skill = new TestSkill();
          var context =
              new SkillContext(UUID.randomUUID(), "CUSTOMER", "Gate creation test", Map.of());
          var request = new SkillExecutionRequest(skill, context, ownerMemberId, List.of());

          SkillExecutionResult result = executionService.executeSkill(request);
          List<AiExecutionGate> gates = result.gates();

          assertThat(gates).hasSize(1);
          assertThat(gates.getFirst().getGateType()).isEqualTo("MARK_KYC_COMPLETE");
          assertThat(gates.getFirst().getStatus()).isEqualTo("PENDING");
          assertThat(gates.getFirst().getAiReasoning())
              .isEqualTo("Test AI reasoning: document verified");
        });
  }

  @Test
  @Order(3)
  void executeSkill_calculatesAndRecordsCost() {
    runInTenant(
        () -> {
          var skill = new TestSkill();
          var context = new SkillContext(UUID.randomUUID(), "CUSTOMER", "Cost test", Map.of());
          var request = new SkillExecutionRequest(skill, context, ownerMemberId, List.of());

          SkillExecutionResult result = executionService.executeSkill(request);
          AiExecution execution = result.execution();

          // Cost calculation: (2000 input * 3.00/M + 1500 cache_read * 0.30/M +
          //   0 cache_creation * 3.75/M) = 0.006 + 0.00045 + 0 = 0.00645 USD input
          // Output: 800 * 15.00/M = 0.012 USD
          // Total USD: 0.01845
          // ZAR cents: 0.01845 * 18.50 * 100 = 34.1325 -> 34 cents (rounded)
          assertThat(execution.getCostCents()).isGreaterThan(0);
        });
  }

  @Test
  @Order(5)
  void executeSkill_budgetExhausted_throwsInvalidStateException() {
    runInTenant(
        () -> {
          // Set a very low budget (1 cent) on the firm profile.
          // Prior tests (Order 1-3) already recorded executions costing ~34 cents each,
          // so the spend already exceeds 1 cent.
          transactionTemplate.executeWithoutResult(
              status -> {
                AiFirmProfile profile = firmProfileService.getOrCreateProfile();
                profile.updateProfile(
                    null,
                    "ZA",
                    "CONSERVATIVE",
                    null,
                    null,
                    null,
                    "claude-sonnet-4-6",
                    1L,
                    false,
                    ownerMemberId);
                firmProfileRepository.save(profile);
              });

          // This call should fail immediately because spend > budget
          var skill = new TestSkill();
          var context = new SkillContext(UUID.randomUUID(), "CUSTOMER", "Over budget", Map.of());
          var request = new SkillExecutionRequest(skill, context, ownerMemberId, List.of());

          assertThatThrownBy(() -> executionService.executeSkill(request))
              .isInstanceOf(InvalidStateException.class)
              .hasMessageContaining("budget exhausted");

          // Clean up: set budget to a very high value so other test classes aren't affected
          transactionTemplate.executeWithoutResult(
              status -> {
                AiFirmProfile profile = firmProfileService.getOrCreateProfile();
                profile.updateProfile(
                    null,
                    "ZA",
                    "CONSERVATIVE",
                    null,
                    null,
                    null,
                    "claude-sonnet-4-6",
                    99_999_999L,
                    false,
                    ownerMemberId);
                firmProfileRepository.save(profile);
              });
        });
  }

  @Test
  @Order(4)
  void executeSkill_promptAssemblyThrows_recordsFailedExecution() {
    runInTenant(
        () -> {
          var failingSkill = new PromptAssemblyFailingSkill();
          var context = new SkillContext(UUID.randomUUID(), "CUSTOMER", "Failure test", Map.of());
          var request = new SkillExecutionRequest(failingSkill, context, ownerMemberId, List.of());

          SkillExecutionResult result = executionService.executeSkill(request);
          AiExecution execution = result.execution();

          assertThat(execution.getStatus()).isEqualTo("FAILED");
          assertThat(execution.getErrorMessage()).contains("Simulated AI failure");
          assertThat(execution.getDurationMs()).isNotNull();
          // Prompt assembly failed before the LLM call — no spend incurred, no cost metered.
          assertThat(execution.getCostCents()).isEqualTo(0);
        });
  }

  /**
   * Provider-call-failure sink: when the LLM HTTP call itself throws (network/timeout), the
   * orchestrator records the IN_PROGRESS execution as FAILED with the elapsed duration and zero
   * cost via {@link AiExecutionPersistenceService#failExecution}. This asserts that sink directly,
   * since the wired StubAiProvider never throws.
   */
  @Test
  @Order(7)
  void failExecution_recordsProviderFailure_withDurationAndZeroCost() {
    runInTenant(
        () -> {
          var skill = new TestSkill();
          var context =
              new SkillContext(UUID.randomUUID(), "CUSTOMER", "Provider failure sink", Map.of());
          var request = new SkillExecutionRequest(skill, context, ownerMemberId, List.of());

          UUID executionId =
              persistenceService.startExecution(request, firmProfileService.getOrCreateProfile());
          SkillExecutionResult result =
              persistenceService.failExecution(executionId, "Connection reset by peer", 1234L);
          AiExecution execution = result.execution();

          assertThat(execution.getStatus()).isEqualTo("FAILED");
          assertThat(execution.getErrorMessage()).isEqualTo("Connection reset by peer");
          assertThat(execution.getDurationMs()).isEqualTo(1234L);
          assertThat(execution.getCostCents()).isEqualTo(0);
          assertThat(result.gates()).isEmpty();
        });
  }

  /**
   * Regression for AIVERIFY-001: when the LLM call succeeds (and is billed) but its output cannot
   * be parsed, the execution must NOT roll back. Instead exactly one execution row is recorded as
   * FAILED with the real cost/tokens preserved, no gates are created, and the {@code
   * ai.specialist.failed} audit event is emitted. Before the fix, the parse exception unwound the
   * whole @Transactional method, leaving 0 rows and silently losing the metered spend.
   */
  @Test
  @Order(6)
  void executeSkill_unparseableOutput_recordsFailedWithCost_noRollback_noGates() {
    runInTenant(
        () -> {
          long executionsBefore = executionRepository.count();
          long auditFailedBefore = countSpecialistFailedAudits();

          // This fixture parses the (deliberately unparseable, code-fenced prose) stub response via
          // the real LlmJsonParser into MatterIntakeOutput — reproducing the V3 parse failure.
          var skill = new UnparseableOutputSkill(llmJsonParser, objectMapper);
          var context =
              new SkillContext(UUID.randomUUID(), "CUSTOMER", "Unparseable output test", Map.of());
          var request = new SkillExecutionRequest(skill, context, ownerMemberId, List.of());

          SkillExecutionResult result = executionService.executeSkill(request);
          AiExecution execution = result.execution();

          // FAILED, not rolled back.
          assertThat(execution.getStatus()).isEqualTo("FAILED");
          assertThat(execution.getErrorMessage()).contains("parse");

          // Cost / tokens metered (the LLM was billed even though parsing failed).
          assertThat(execution.getCostCents()).isGreaterThan(0);
          assertThat(execution.getInputTokens()).isGreaterThan(0);
          assertThat(execution.getOutputTokens()).isGreaterThan(0);

          // No gates.
          assertThat(result.gates()).isEmpty();
          assertThat(gateRepository.findByExecutionId(execution.getId())).isEmpty();

          // Exactly one new execution row persisted (no rollback, no duplicate).
          assertThat(executionRepository.count()).isEqualTo(executionsBefore + 1);
          AiExecution reloaded = executionRepository.findById(execution.getId()).orElseThrow();
          assertThat(reloaded.getStatus()).isEqualTo("FAILED");
          assertThat(reloaded.getCostCents()).isGreaterThan(0);

          // ai.specialist.failed audit event emitted.
          assertThat(countSpecialistFailedAudits()).isEqualTo(auditFailedBefore + 1);
        });
  }

  /**
   * Regression for AIVERIFY-001 (review follow-up): {@code createGates} does more than parse — it
   * can throw {@link ResourceNotFoundException} (a referenced entity was deleted) or an NPE while
   * resolving gate context, AFTER the LLM was billed. Those must also record FAILED-with-cost, not
   * roll back the metered execution. Before broadening the catch from {@code InvalidStateException}
   * to {@code RuntimeException}, this path unwound the transaction and lost the spend.
   */
  @Test
  @Order(7)
  void executeSkill_gateBuildingThrowsNonParseError_recordsFailedWithCost_noRollback() {
    runInTenant(
        () -> {
          long executionsBefore = executionRepository.count();
          long auditFailedBefore = countSpecialistFailedAudits();

          // Parseable stub (reuses test-skill's response) so the LLM call succeeds and is billed;
          // createGates then throws a non-parse runtime exception.
          var skill = new GateBuildingFailingSkill();
          var context =
              new SkillContext(
                  UUID.randomUUID(), "CUSTOMER", "Gate-building failure test", Map.of());
          var request = new SkillExecutionRequest(skill, context, ownerMemberId, List.of());

          SkillExecutionResult result = executionService.executeSkill(request);
          AiExecution execution = result.execution();

          // FAILED, not rolled back, cost preserved.
          assertThat(execution.getStatus()).isEqualTo("FAILED");
          assertThat(execution.getCostCents()).isGreaterThan(0);
          assertThat(result.gates()).isEmpty();
          assertThat(gateRepository.findByExecutionId(execution.getId())).isEmpty();

          // Exactly one new execution row + a specialist.failed audit event.
          assertThat(executionRepository.count()).isEqualTo(executionsBefore + 1);
          AiExecution reloaded = executionRepository.findById(execution.getId()).orElseThrow();
          assertThat(reloaded.getStatus()).isEqualTo("FAILED");
          assertThat(reloaded.getCostCents()).isGreaterThan(0);
          assertThat(countSpecialistFailedAudits()).isEqualTo(auditFailedBefore + 1);
        });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private long countSpecialistFailedAudits() {
    Page<AuditEvent> events =
        auditEventRepository.findByFilter(
            null, null, null, "ai.specialist.failed", null, null, Pageable.unpaged());
    return events.getTotalElements();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_EXECUTE", "AI_REVIEW", "AI_MANAGE"))
        .run(action);
  }

  // ── Test Skill fixtures ───────────────────────────────────────────────────

  static class TestSkill implements AiSkill {

    @Override
    public String skillId() {
      return "test-skill";
    }

    @Override
    public String assembleSystemPrompt(AiFirmProfile profile) {
      return "You are a test skill for FICA verification.";
    }

    @Override
    public String assembleUserPrompt(SkillContext context) {
      return "Verify entity: " + context.entityId();
    }

    @Override
    public List<AiExecutionGate> createGates(
        AiExecution execution, String outputContent, SkillContext context) {
      var gate =
          new AiExecutionGate(
              execution,
              "MARK_KYC_COMPLETE",
              Map.of(
                  "checklist_item_ids",
                  List.of(UUID.randomUUID().toString()),
                  "completion_notes",
                  "AI verified"),
              "Test AI reasoning: document verified",
              Instant.now().plus(Duration.ofHours(72)));
      return List.of(gate);
    }

    @Override
    public boolean requiresVision() {
      return false;
    }
  }

  /**
   * A skill whose stub response (ai/stubs/unparseable-skill/response.json) is code-fenced prose
   * that cannot be bound to its structured output type — reproducing AIVERIFY-001. It routes the
   * parse through the real {@link LlmJsonParser}, so the resulting {@link InvalidStateException}
   * exercises the FAILED-with-cost path in completeExecution.
   */
  static class UnparseableOutputSkill implements AiSkill {

    private final LlmJsonParser llmJsonParser;
    private final ObjectMapper objectMapper;

    UnparseableOutputSkill(LlmJsonParser llmJsonParser, ObjectMapper objectMapper) {
      this.llmJsonParser = llmJsonParser;
      this.objectMapper = objectMapper;
    }

    @Override
    public String skillId() {
      return "unparseable-skill";
    }

    @Override
    public String assembleSystemPrompt(AiFirmProfile profile) {
      return "You are a skill whose canned output is unparseable.";
    }

    @Override
    public String assembleUserPrompt(SkillContext context) {
      return "Produce structured output for: " + context.entityId();
    }

    @Override
    public List<AiExecutionGate> createGates(
        AiExecution execution, String outputContent, SkillContext context) {
      // Parsing the fenced-prose stub into a structured type throws InvalidStateException.
      llmJsonParser.parse(objectMapper, outputContent, MatterIntakeOutput.class);
      return fail(
          "createGates should not be reached — LlmJsonParser should have thrown"
              + " InvalidStateException for the unparseable stub");
    }

    @Override
    public boolean requiresVision() {
      return false;
    }
  }

  /**
   * A skill that parses fine (reuses the parseable {@code test-skill} stub) but throws a non-parse
   * runtime exception from {@code createGates} — reproducing the ContractReviewSkill path where a
   * referenced document/project is missing. Exercises the broadened catch in completeExecution.
   */
  static class GateBuildingFailingSkill implements AiSkill {

    @Override
    public String skillId() {
      return "test-skill";
    }

    @Override
    public String assembleSystemPrompt(AiFirmProfile profile) {
      return "You are a skill that fails while building gates.";
    }

    @Override
    public String assembleUserPrompt(SkillContext context) {
      return "Produce output for: " + context.entityId();
    }

    @Override
    public List<AiExecutionGate> createGates(
        AiExecution execution, String outputContent, SkillContext context) {
      throw new ResourceNotFoundException("Document", UUID.randomUUID());
    }

    @Override
    public boolean requiresVision() {
      return false;
    }
  }

  /** A skill that throws during prompt assembly to test the failure-recording path. */
  static class PromptAssemblyFailingSkill implements AiSkill {

    @Override
    public String skillId() {
      return "failing-skill";
    }

    @Override
    public String assembleSystemPrompt(AiFirmProfile profile) {
      throw new RuntimeException("Simulated AI failure");
    }

    @Override
    public String assembleUserPrompt(SkillContext context) {
      return "This should not be reached";
    }

    @Override
    public List<AiExecutionGate> createGates(
        AiExecution execution, String outputContent, SkillContext context) {
      return List.of();
    }

    @Override
    public boolean requiresVision() {
      return false;
    }
  }
}
