package io.b2mash.b2b.b2bstrawman.integration.ai.skill.drafting;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.AiSkillExecutionService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillContext;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillExecutionResult;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DraftingSkillTest {

  private static final String ORG_ID = "org_drafting_skill_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiSkillExecutionService executionService;
  @Autowired private DraftingSkill draftingSkill;
  @Autowired private AiFirmProfileService firmProfileService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID templateId;
  private UUID projectId;
  private UUID customerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Drafting Skill Test Org", null);
    String ownerStr =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_drafting_owner",
            "drafting_owner@test.com",
            "Drafting Test Owner",
            "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create test data within tenant context
    runInTenant(this::createTestData);
  }

  @Test
  void draftingSkill_assemblesSystemPrompt_withFirmProfileAndSchema() {
    runInTenant(
        () -> {
          AiFirmProfile profile = firmProfileService.getOrCreateProfile();
          String systemPrompt = draftingSkill.assembleSystemPrompt(profile);

          assertThat(systemPrompt).contains("firm-profile");
          assertThat(systemPrompt).contains("legal document drafting assistant");
          assertThat(systemPrompt).contains("variableFills");
          assertThat(systemPrompt).contains("narrativeSections");
          assertThat(systemPrompt).contains("PLAIN LANGUAGE");
        });
  }

  @Test
  void draftingSkill_assemblesUserPrompt_withTemplateAndMatterContext() {
    runInTenant(
        () -> {
          var context =
              new SkillContext(
                  projectId,
                  "PROJECT",
                  "User prompt test",
                  Map.of("templateId", templateId.toString()));

          String userPrompt = draftingSkill.assembleUserPrompt(context);

          assertThat(userPrompt).contains("<template>");
          assertThat(userPrompt).contains("Engagement Letter Template");
          assertThat(userPrompt).contains("<matter>");
          assertThat(userPrompt).contains("Drafting Test Matter");
          assertThat(userPrompt).contains("commercial");
          assertThat(userPrompt).contains("<customer>");
          assertThat(userPrompt).contains("Themba Consulting (Pty) Ltd");
          assertThat(userPrompt).contains("<clause-library>");
          assertThat(userPrompt).contains("Drafting Test Liability Clause");
        });
  }

  @Test
  void draftingSkill_createsOneGate_withTypeCreateDraftDocument() {
    runInTenant(
        () -> {
          var context =
              new SkillContext(
                  projectId,
                  "PROJECT",
                  "Gate creation test",
                  Map.of("templateId", templateId.toString()));

          SkillExecutionResult result =
              executionService.executeSkill("drafting", context, ownerMemberId, List.of());
          List<AiExecutionGate> gates = result.gates();

          assertThat(gates).hasSize(1);
          assertThat(gates.getFirst().getGateType()).isEqualTo("CREATE_DRAFT_DOCUMENT");
          assertThat(gates.getFirst().getStatus()).isEqualTo("PENDING");
          assertThat(gates.getFirst().getAiReasoning()).isNotBlank();
        });
  }

  @Test
  void draftingSkill_outputIsParseable_asDraftingOutput() {
    runInTenant(
        () -> {
          var context =
              new SkillContext(
                  projectId,
                  "PROJECT",
                  "Output parse test",
                  Map.of("templateId", templateId.toString()));

          SkillExecutionResult result =
              executionService.executeSkill("drafting", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          assertThat(execution.getOutputContent()).isNotNull();
          DraftingOutput output = parseOutput(execution.getOutputContent());
          assertThat(output.variableFills()).isNotEmpty();
          assertThat(output.narrativeSections()).isNotEmpty();
          assertThat(output.clauseRecommendations()).isNotEmpty();
          assertThat(output.warnings()).isNotEmpty();
          assertThat(output.recommendedActions()).isNotEmpty();
        });
  }

  @Test
  void draftingSkill_variableFills_haveCorrectConfidenceLevels() {
    runInTenant(
        () -> {
          var context =
              new SkillContext(
                  projectId,
                  "PROJECT",
                  "Confidence level test",
                  Map.of("templateId", templateId.toString()));

          SkillExecutionResult result =
              executionService.executeSkill("drafting", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          DraftingOutput output = parseOutput(execution.getOutputContent());
          List<DraftingOutput.VariableFill> fills = output.variableFills();

          // Verify DATA-sourced fills have HIGH confidence
          var dataFills = fills.stream().filter(f -> "DATA".equals(f.source())).toList();
          assertThat(dataFills).isNotEmpty();
          assertThat(dataFills).allMatch(f -> "HIGH".equals(f.confidence()));

          // Verify INFERRED fills have MEDIUM confidence
          var inferredFills = fills.stream().filter(f -> "INFERRED".equals(f.source())).toList();
          assertThat(inferredFills).isNotEmpty();
          assertThat(inferredFills).allMatch(f -> "MEDIUM".equals(f.confidence()));

          // Verify UNAVAILABLE fills have UNDETERMINED confidence
          var unavailableFills =
              fills.stream().filter(f -> "UNAVAILABLE".equals(f.source())).toList();
          assertThat(unavailableFills).isNotEmpty();
          assertThat(unavailableFills).allMatch(f -> "UNDETERMINED".equals(f.confidence()));
        });
  }

  // -- Helpers --

  private void createTestData() {
    // Create a customer
    Customer customer =
        TestCustomerFactory.createActiveCustomer(
            "Themba Consulting (Pty) Ltd",
            "info@themba-consulting.co.za",
            ownerMemberId,
            io.b2mash.b2b.b2bstrawman.customer.CustomerType.COMPANY);
    customer = customerRepository.saveAndFlush(customer);
    customerId = customer.getId();

    // Create a project linked to the customer
    var project = new Project("Drafting Test Matter", "Commercial advisory matter", ownerMemberId);
    project.setWorkType("commercial");
    project.setReferenceNumber("COM-2026-001");
    project.setCustomerId(customerId);
    project = projectRepository.saveAndFlush(project);
    projectId = project.getId();

    // Create a document template
    Map<String, Object> templateContent =
        Map.of(
            "type",
            "doc",
            "content",
            List.of(
                Map.of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(Map.of("type", "text", "text", "ENGAGEMENT LETTER")))));
    var template =
        new DocumentTemplate(
            TemplateEntityType.PROJECT,
            "Engagement Letter Template",
            "engagement-letter-template",
            TemplateCategory.ENGAGEMENT_LETTER,
            templateContent);
    template.setRequiredContextFields(
        List.of(
            Map.of("entity", "customer", "field", "name"),
            Map.of("entity", "customer", "field", "registrationNumber"),
            Map.of("entity", "project", "field", "referenceNumber")));
    template = documentTemplateRepository.saveAndFlush(template);
    templateId = template.getId();

    // Create a clause in the library (unique slug to avoid conflicts with seeded data)
    var clause =
        new Clause(
            "Drafting Test Liability Clause",
            "drafting-test-liability-clause",
            Map.of(
                "type",
                "doc",
                "content",
                List.of(
                    Map.of(
                        "type",
                        "paragraph",
                        "content",
                        List.of(
                            Map.of(
                                "type",
                                "text",
                                "text",
                                "The liability of the Firm shall be limited..."))))),
            "liability");
    clauseRepository.saveAndFlush(clause);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_EXECUTE", "AI_REVIEW", "AI_MANAGE"))
        .run(() -> transactionTemplate.executeWithoutResult(status -> action.run()));
  }

  private DraftingOutput parseOutput(String json) {
    try {
      return objectMapper.readValue(json, DraftingOutput.class);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse drafting output: " + e.getMessage(), e);
    }
  }
}
