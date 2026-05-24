package io.b2mash.b2b.b2bstrawman.integration.ai.skill.drafting;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.nio.charset.StandardCharsets;
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
class AiDraftDocumentGeneratorTest {

  private static final String ORG_ID = "org_draft_gen_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiDraftDocumentGenerator draftGenerator;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private AiExecutionRepository aiExecutionRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private StorageService storageService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Draft Gen Test Org", null);
    String ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_dg_owner", "dg_owner@test.com", "Draft Gen Owner", "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_EXECUTE", "AI_REVIEW", "AI_MANAGE"))
        .run(() -> transactionTemplate.executeWithoutResult(status -> action.run()));
  }

  private DraftingOutput buildTestOutput(UUID clauseId) {
    return new DraftingOutput(
        UUID.randomUUID(), // templateId in output (informational)
        List.of(
            new DraftingOutput.VariableFill(
                "Client Name", "Acme Solutions (Pty) Ltd", "intake_form", "HIGH", null),
            new DraftingOutput.VariableFill(
                "Effective Date", "2026-06-01", "ai_inferred", "MEDIUM", null),
            new DraftingOutput.VariableFill(
                "Hourly Rate", "R2,500.00", "firm_defaults", "HIGH", null)),
        List.of(
            new DraftingOutput.NarrativeSection(
                "Scope of Services",
                "We will provide comprehensive legal advisory services including contract review, "
                    + "regulatory compliance, and dispute resolution.",
                "Tailored to client's tech industry focus"),
            new DraftingOutput.NarrativeSection(
                "Payment Terms",
                "Invoices are issued monthly in arrears. Payment is due within 30 days of invoice "
                    + "date.",
                null)),
        clauseId != null
            ? List.of(
                new DraftingOutput.ClauseRecommendation(
                    clauseId, "Limitation of Liability", "Standard for service agreements"))
            : List.of(),
        List.of("Verify client registration number"),
        List.of(
            new DraftingOutput.RecommendedAction(
                "Review payment terms with client", "Non-standard 30-day cycle")));
  }

  private DocumentTemplate createTestTemplate() {
    String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    Map<String, Object> templateContent =
        Map.of(
            "type",
            "doc",
            "content",
            List.of(
                Map.of(
                    "type",
                    "heading",
                    "attrs",
                    Map.of("level", 1),
                    "content",
                    List.of(Map.of("type", "text", "text", "{{Client Name}}"))),
                Map.of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(
                        Map.of(
                            "type",
                            "text",
                            "text",
                            "This engagement letter is effective from {{Effective Date}}.")))));

    var template =
        new DocumentTemplate(
            TemplateEntityType.PROJECT,
            "Engagement Letter",
            "engagement-letter-" + uniqueSuffix,
            TemplateCategory.ENGAGEMENT_LETTER,
            templateContent);
    return documentTemplateRepository.saveAndFlush(template);
  }

  private Project createTestProject() {
    var project =
        new Project("Test Matter for Drafting", "Draft generation test matter", ownerMemberId);
    return projectRepository.saveAndFlush(project);
  }

  private AiExecution createTestExecution() {
    var execution =
        new AiExecution(
            "template-drafting", "DOCUMENT", UUID.randomUUID(), ownerMemberId, "claude-sonnet", 1);
    return aiExecutionRepository.saveAndFlush(execution);
  }

  private Clause createTestClause() {
    String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    Map<String, Object> clauseBody =
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
                            "The total liability of the service provider shall not exceed "
                                + "the total fees paid under this agreement.")))));

    var clause =
        new Clause(
            "Limitation of Liability",
            "limitation-of-liability-" + uniqueSuffix,
            clauseBody,
            "risk");
    return clauseRepository.saveAndFlush(clause);
  }

  @Test
  void generateDraft_createsDocumentLinkedToProject() {
    runInTenant(
        () -> {
          var template = createTestTemplate();
          var project = createTestProject();
          var execution = createTestExecution();
          var output = buildTestOutput(null);

          Document doc =
              draftGenerator.generateDraft(
                  output, template.getId(), project.getId(), execution.getId(), ownerMemberId);

          assertThat(doc).isNotNull();
          assertThat(doc.getId()).isNotNull();
          assertThat(doc.getProjectId()).isEqualTo(project.getId());
          assertThat(doc.getScope()).isEqualTo(Document.Scope.PROJECT);
          assertThat(doc.getStatus()).isEqualTo(Document.Status.UPLOADED);
          assertThat(doc.getContentType()).isEqualTo("application/json");
          assertThat(doc.getS3Key()).isNotEqualTo("pending");

          // Verify content was uploaded to storage
          byte[] storedBytes = storageService.download(doc.getS3Key());
          assertThat(storedBytes).isNotNull();
          assertThat(storedBytes.length).isGreaterThan(0);

          // Verify it's valid Tiptap JSON
          @SuppressWarnings("unchecked")
          Map<String, Object> tiptap = objectMapper.readValue(storedBytes, Map.class);
          assertThat(tiptap).containsKey("type");
          assertThat(tiptap.get("type")).isEqualTo("doc");
          assertThat(tiptap).containsKey("content");
        });
  }

  @Test
  void generateDraft_setsAiProvenance() {
    runInTenant(
        () -> {
          var template = createTestTemplate();
          var project = createTestProject();
          var execution = createTestExecution();
          var output = buildTestOutput(null);

          Document doc =
              draftGenerator.generateDraft(
                  output, template.getId(), project.getId(), execution.getId(), ownerMemberId);

          assertThat(doc.getSource()).isEqualTo(Document.Source.AI_GENERATED);
          assertThat(doc.getAiExecutionId()).isEqualTo(execution.getId());

          // Verify persisted state
          Document reloaded = documentRepository.findById(doc.getId()).orElseThrow();
          assertThat(reloaded.getSource()).isEqualTo(Document.Source.AI_GENERATED);
          assertThat(reloaded.getAiExecutionId()).isEqualTo(execution.getId());
        });
  }

  @Test
  void generateDraft_appliesVariableFillsToContent() {
    runInTenant(
        () -> {
          var template = createTestTemplate();
          var project = createTestProject();
          var execution = createTestExecution();
          var output = buildTestOutput(null);

          Document doc =
              draftGenerator.generateDraft(
                  output, template.getId(), project.getId(), execution.getId(), ownerMemberId);

          byte[] storedBytes = storageService.download(doc.getS3Key());
          String content = new String(storedBytes, StandardCharsets.UTF_8);

          // Variable fill values should appear in the document content
          assertThat(content).contains("Acme Solutions (Pty) Ltd");
          assertThat(content).contains("2026-06-01");
          assertThat(content).contains("R2,500.00");

          // Narrative section content should also appear
          assertThat(content).contains("Scope of Services");
          assertThat(content).contains("comprehensive legal advisory services");
          assertThat(content).contains("Payment Terms");
        });
  }

  @Test
  void generateDraft_documentNameIncludesTemplateName() {
    runInTenant(
        () -> {
          var template = createTestTemplate();
          var project = createTestProject();
          var execution = createTestExecution();
          var output = buildTestOutput(null);

          Document doc =
              draftGenerator.generateDraft(
                  output, template.getId(), project.getId(), execution.getId(), ownerMemberId);

          assertThat(doc.getFileName()).startsWith("Engagement Letter - ");
          assertThat(doc.getFileName()).endsWith(".json");
          assertThat(doc.getFileName()).contains("Engagement Letter");
        });
  }
}
