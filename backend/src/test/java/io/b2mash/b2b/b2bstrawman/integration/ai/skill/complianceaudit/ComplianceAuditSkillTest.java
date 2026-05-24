package io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.AiSkillExecutionService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillContext;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillExecutionResult;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ComplianceAuditSkillTest {

  private static final String ORG_ID = "org_compliance_audit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiSkillExecutionService executionService;
  @Autowired private ComplianceAuditSkill complianceAuditSkill;
  @Autowired private AiFirmProfileService firmProfileService;
  @Autowired private AiExecutionRepository aiExecutionRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Compliance Audit Test Org", null);
    String ownerStr =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_ca_owner",
            "ca_owner@test.com",
            "Compliance Audit Owner",
            "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  @Order(1)
  void complianceAuditSkill_assemblesSystemPrompt_withRegulatoryFramework() {
    runInTenant(
        () -> {
          AiFirmProfile profile = firmProfileService.getOrCreateProfile();
          String systemPrompt = complianceAuditSkill.assembleSystemPrompt(profile);

          assertThat(systemPrompt).contains("compliance audit assistant");
          assertThat(systemPrompt).contains("FICA");
          assertThat(systemPrompt).contains("POPIA");
          assertThat(systemPrompt).containsIgnoringCase("Attorneys Act");
          assertThat(systemPrompt).containsIgnoringCase("Prescription Act");
          assertThat(systemPrompt).contains("firm-profile");
          assertThat(systemPrompt).contains("auditDate");
        });
  }

  @Test
  @Order(2)
  void complianceAuditSkill_assemblesUserPrompt_withComplianceSnapshotData() {
    runInTenant(
        () -> {
          var context =
              new SkillContext(UUID.randomUUID(), "FIRM", "Compliance audit test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("compliance-audit", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          assertThat(execution).isNotNull();
          assertThat(execution.getStatus()).isEqualTo("COMPLETED");
          assertThat(execution.getSkillId()).isEqualTo("compliance-audit");
          assertThat(execution.getEntityType()).isEqualTo("FIRM");
        });
  }

  @Test
  @Order(3)
  void complianceAuditSkill_createsOneGate_withTypePublishComplianceReport() {
    runInTenant(
        () -> {
          var context = new SkillContext(UUID.randomUUID(), "FIRM", "Gate creation test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("compliance-audit", context, ownerMemberId, List.of());
          List<AiExecutionGate> gates = result.gates();

          assertThat(gates).hasSize(1);
          assertThat(gates.getFirst().getGateType()).isEqualTo("PUBLISH_COMPLIANCE_REPORT");
          assertThat(gates.getFirst().getStatus()).isEqualTo("PENDING");
          assertThat(gates.getFirst().getAiReasoning()).contains("Overall grade: B");
        });
  }

  @Test
  @Order(4)
  void complianceAuditSkill_outputIsParseable_asComplianceAuditOutput() {
    runInTenant(
        () -> {
          var context = new SkillContext(UUID.randomUUID(), "FIRM", "Output parse test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("compliance-audit", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          assertThat(execution.getOutputContent()).isNotNull();
          ComplianceAuditOutput output = parseOutput(execution.getOutputContent());
          assertThat(output.overallGrade()).isEqualTo("B");
          assertThat(output.overallAssessment()).isNotBlank();
          assertThat(output.categoryScores()).containsKey("FICA_CDD");
          assertThat(output.findings()).isNotEmpty();
          assertThat(output.findings().getFirst().severity()).isEqualTo("CRITICAL");
          assertThat(output.recommendations()).isNotEmpty();
        });
  }

  @Test
  @Order(5)
  void complianceAuditSkill_concurrentAuditPrevention_rejectsSecondInvocation() {
    runInTenant(
        () -> {
          // Create a stuck IN_PROGRESS execution to simulate a running audit
          var existingExecution =
              new AiExecution(
                  "compliance-audit",
                  "FIRM",
                  UUID.randomUUID(),
                  ownerMemberId,
                  "claude-sonnet-4-6",
                  1);
          aiExecutionRepository.saveAndFlush(existingExecution);

          // Attempt a second audit — should be rejected because there are now
          // 2 IN_PROGRESS executions (the stuck one + the new one being created)
          var context =
              new SkillContext(UUID.randomUUID(), "FIRM", "Concurrent audit test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("compliance-audit", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          // The execution should have FAILED due to ResourceConflictException
          assertThat(execution.getStatus()).isEqualTo("FAILED");
          assertThat(execution.getErrorMessage()).contains("already in progress");
        });
  }

  // -- Helpers --

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_EXECUTE", "AI_REVIEW", "AI_MANAGE"))
        .run(() -> transactionTemplate.executeWithoutResult(status -> action.run()));
  }

  private ComplianceAuditOutput parseOutput(String json) {
    try {
      return objectMapper.readValue(json, ComplianceAuditOutput.class);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse compliance audit output: " + e.getMessage(), e);
    }
  }
}
