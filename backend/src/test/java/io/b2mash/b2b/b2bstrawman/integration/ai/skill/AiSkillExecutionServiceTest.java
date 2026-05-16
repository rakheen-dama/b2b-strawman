package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

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
  void executeSkill_providerThrows_recordsFailedExecution() {
    runInTenant(
        () -> {
          var failingSkill = new FailingSkill();
          var context = new SkillContext(UUID.randomUUID(), "CUSTOMER", "Failure test", Map.of());
          var request = new SkillExecutionRequest(failingSkill, context, ownerMemberId, List.of());

          SkillExecutionResult result = executionService.executeSkill(request);
          AiExecution execution = result.execution();

          assertThat(execution.getStatus()).isEqualTo("FAILED");
          assertThat(execution.getErrorMessage()).contains("Simulated AI failure");
          assertThat(execution.getDurationMs()).isNotNull();
          assertThat(execution.getCostCents()).isEqualTo(0);
        });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

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
    public List<AiExecutionGate> createGates(AiExecution execution, String outputContent) {
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

  /** A skill that throws during prompt assembly to test the failure-recording path. */
  static class FailingSkill implements AiSkill {

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
    public List<AiExecutionGate> createGates(AiExecution execution, String outputContent) {
      return List.of();
    }

    @Override
    public boolean requiresVision() {
      return false;
    }
  }
}
