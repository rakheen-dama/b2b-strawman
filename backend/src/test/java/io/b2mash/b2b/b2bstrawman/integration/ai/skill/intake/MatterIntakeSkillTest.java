package io.b2mash.b2b.b2bstrawman.integration.ai.skill.intake;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.AiSkillExecutionService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillContext;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillExecutionResult;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplate;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
class MatterIntakeSkillTest {

  private static final String ORG_ID = "org_matter_intake_test";
  private final AtomicInteger counter = new AtomicInteger(0);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiSkillExecutionService executionService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectTemplateRepository projectTemplateRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private MatterIntakeSkill matterIntakeSkill;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Matter Intake Test Org", null);
    String ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_intake_owner", "intake_owner@test.com", "Intake Owner", "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void intakeSkill_producesCompletedExecution_withCorrectSkillId() {
    runInTenant(
        () -> {
          UUID customerId = createCustomerWithTemplate();
          var context =
              new SkillContext(
                  customerId,
                  "CUSTOMER",
                  "New RAF litigation matter for motor vehicle accident on N1 highway",
                  Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("matter-intake", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          assertThat(execution).isNotNull();
          assertThat(execution.getStatus()).isEqualTo("COMPLETED");
          assertThat(execution.getSkillId()).isEqualTo("matter-intake");
          assertThat(execution.getEntityType()).isEqualTo("CUSTOMER");
        });
  }

  @Test
  void intakeSkill_outputIsParseable_asMatterIntakeOutput() {
    runInTenant(
        () -> {
          UUID customerId = createCustomerWithTemplate();
          var context =
              new SkillContext(
                  customerId,
                  "CUSTOMER",
                  "RAF claim for client injured in Johannesburg motor vehicle collision",
                  Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("matter-intake", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          assertThat(execution.getOutputContent()).isNotNull();
          MatterIntakeOutput output = parseOutput(execution.getOutputContent());
          assertThat(output.matterClassification().recommendedType()).isEqualTo("LITIGATION");
          assertThat(output.matterClassification().confidence()).isEqualTo(0.92);
        });
  }

  @Test
  void intakeSkill_templateRecommendation_createsSELECT_MATTER_TEMPLATEGate() {
    runInTenant(
        () -> {
          UUID customerId = createCustomerWithTemplate();
          var context =
              new SkillContext(
                  customerId,
                  "CUSTOMER",
                  "Personal injury claim from road accident — need template selection",
                  Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("matter-intake", context, ownerMemberId, List.of());
          List<AiExecutionGate> gates = result.gates();

          List<AiExecutionGate> templateGates =
              gates.stream().filter(g -> "SELECT_MATTER_TEMPLATE".equals(g.getGateType())).toList();
          assertThat(templateGates).hasSize(1);
          assertThat(templateGates.getFirst().getStatus()).isEqualTo("PENDING");
        });
  }

  @Test
  void intakeSkill_potentialConflict_createsCONFIRM_CONFLICT_SCREENGate() {
    runInTenant(
        () -> {
          UUID customerId = createCustomerWithTemplate();
          var context =
              new SkillContext(
                  customerId,
                  "CUSTOMER",
                  "New matter intake — checking for conflicts with existing RAF matters",
                  Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("matter-intake", context, ownerMemberId, List.of());
          List<AiExecutionGate> gates = result.gates();

          // Canned response has POTENTIAL_CONFLICT status — should create a gate
          List<AiExecutionGate> conflictGates =
              gates.stream()
                  .filter(g -> "CONFIRM_CONFLICT_SCREEN".equals(g.getGateType()))
                  .toList();
          assertThat(conflictGates).hasSize(1);
          assertThat(conflictGates.getFirst().getStatus()).isEqualTo("PENDING");
        });
  }

  @Test
  void intakeSkill_conflictDetected_doesNotCreateGate() {
    runInTenant(
        () -> {
          UUID customerId = createCustomerWithTemplate();
          var context =
              new SkillContext(
                  customerId,
                  "CUSTOMER",
                  "Matter intake with conflict detected — should not create gate",
                  Map.of());

          // Execute the skill to get a real execution entity
          SkillExecutionResult result =
              executionService.executeSkill("matter-intake", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          // Test createGates directly with CONFLICT_DETECTED JSON
          String conflictDetectedJson =
              """
              {
                "matterClassification": {"recommendedType": "LITIGATION", "confidence": 0.9, "reasoning": "test"},
                "templateRecommendation": null,
                "requiredDocuments": [],
                "feeEstimate": {"tariffBasis": "LSSA", "estimatedRangeMinCents": 500000, "estimatedRangeMaxCents": 1000000, "reasoning": "test", "assumptions": []},
                "conflictScreening": {"status": "CONFLICT_DETECTED", "matches": [{"existingMatterName": "Smith v Jones", "customerName": "Jones", "matchType": "DIRECT_PARTY", "reasoning": "Same party"}]},
                "riskFlags": ["CONFLICT"]
              }
              """;

          List<AiExecutionGate> gates =
              matterIntakeSkill.createGates(
                  execution,
                  conflictDetectedJson,
                  new SkillContext(UUID.randomUUID(), "CUSTOMER", "test", Map.of()));
          assertThat(
                  gates.stream().noneMatch(g -> "CONFIRM_CONFLICT_SCREEN".equals(g.getGateType())))
              .isTrue();
        });
  }

  /**
   * Regression for AIVERIFY-001: live Claude wraps its JSON output in a ```json … ``` markdown
   * fence. createGates must tolerate the fence (via the shared LlmJsonParser) and still build
   * gates, rather than failing on the leading backtick as it did in V3.
   */
  @Test
  void intakeSkill_fencedJsonOutput_isParsed_andCreatesGates() {
    runInTenant(
        () -> {
          UUID customerId = createCustomerWithTemplate();
          var context =
              new SkillContext(
                  customerId,
                  "CUSTOMER",
                  "RAF litigation matter — verifying fenced JSON tolerance",
                  Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("matter-intake", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          // Valid matter-intake JSON wrapped in a markdown code fence, exactly as Claude returns
          // it.
          String fencedJson =
              """
              Here is the matter intake analysis:
              ```json
              {
                "matterClassification": {"recommendedType": "LITIGATION", "confidence": 0.91, "reasoning": "RAF claim"},
                "templateRecommendation": {"templateId": "00000000-0000-0000-0000-000000000099", "templateName": "RAF", "reasoning": "fits", "customisationNotes": "add SAPS task"},
                "requiredDocuments": [],
                "feeEstimate": {"tariffBasis": "LSSA", "estimatedRangeMinCents": 500000, "estimatedRangeMaxCents": 1000000, "reasoning": "test", "assumptions": []},
                "conflictScreening": {"status": "CLEAR", "matches": []},
                "riskFlags": []
              }
              ```
              Let me know if you need anything further.
              """;

          List<AiExecutionGate> gates =
              matterIntakeSkill.createGates(
                  execution,
                  fencedJson,
                  new SkillContext(customerId, "CUSTOMER", "test", Map.of()));

          // Both gates created: template recommendation (has templateId) and CLEAR conflict screen.
          assertThat(gates.stream().anyMatch(g -> "SELECT_MATTER_TEMPLATE".equals(g.getGateType())))
              .isTrue();
          assertThat(
                  gates.stream().anyMatch(g -> "CONFIRM_CONFLICT_SCREEN".equals(g.getGateType())))
              .isTrue();
        });
  }

  @Test
  void intakeSkill_preflightCheck_rejectsShortDescription() {
    runInTenant(
        () -> {
          UUID customerId = createCustomerWithTemplate();
          // Description under 20 characters
          var context = new SkillContext(customerId, "CUSTOMER", "Short desc", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("matter-intake", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          assertThat(execution.getStatus()).isEqualTo("FAILED");
          assertThat(execution.getErrorMessage()).contains("20 characters");
        });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_EXECUTE", "AI_REVIEW", "AI_MANAGE"))
        .run(() -> transactionTemplate.executeWithoutResult(status -> action.run()));
  }

  private UUID createCustomerWithTemplate() {
    int idx = counter.incrementAndGet();
    var customer =
        TestCustomerFactory.createActiveCustomer(
            "Intake Test Client " + idx, "intake-client-" + idx + "@test.com", ownerMemberId);
    customer = customerRepository.saveAndFlush(customer);

    // Ensure at least one active project template exists
    var template =
        new ProjectTemplate(
            "RAF Litigation Template " + idx,
            "{customer} v RAF",
            "Standard template for Road Accident Fund personal injury litigation",
            true,
            "MANUAL",
            null,
            ownerMemberId);
    projectTemplateRepository.saveAndFlush(template);

    return customer.getId();
  }

  private MatterIntakeOutput parseOutput(String json) {
    try {
      return objectMapper.readValue(json, MatterIntakeOutput.class);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse matter intake output: " + e.getMessage(), e);
    }
  }
}
