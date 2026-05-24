package io.b2mash.b2b.b2bstrawman.integration.ai.skill.contractreview;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
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
class AiReviewReportGeneratorTest {

  private static final String ORG_ID = "org_report_gen_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiReviewReportGenerator reportGenerator;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private AiExecutionRepository aiExecutionRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private StorageService storageService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Report Gen Test Org", null);
    String ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_rg_owner", "rg_owner@test.com", "Report Gen Owner", "owner");
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

  private ContractReviewOutput buildTestOutput() {
    return new ContractReviewOutput(
        new ContractReviewOutput.DocumentClassification(
            "COMMERCIAL_CONTRACT",
            "Service Level Agreement",
            List.of("Acme Solutions (Pty) Ltd", "TechCorp Holdings (Pty) Ltd")),
        "This is a test executive summary for the contract review.",
        List.of(
            new ContractReviewOutput.Finding(
                "HIGH",
                "Liability",
                "Clause 12.3",
                "Unlimited liability exclusion",
                "Description of the finding",
                "Risk explanation",
                "Recommendation text",
                "CPA s48, s49"),
            new ContractReviewOutput.Finding(
                "MEDIUM",
                "Termination",
                "Clause 15.1",
                "Auto-renewal without notice",
                "Description of the termination finding",
                "Termination risk",
                "Extend notice period",
                "CPA s14"),
            new ContractReviewOutput.Finding(
                "LOW",
                "Data Protection",
                "Clause 18",
                "Insufficient data processing",
                "Data protection description",
                "Regulatory risk",
                "Include POPIA addendum",
                "POPIA s19-22")),
        List.of(
            new ContractReviewOutput.MissingProtection(
                "Force majeure clause",
                "No force majeure provision",
                "Include a force majeure clause",
                "MEDIUM")),
        "The agreement presents a MODERATE risk profile.",
        List.of(
            new ContractReviewOutput.RecommendedAction("Revise liability clause", "Highest risk")));
  }

  private Project createTestProject() {
    var project = new Project("Test Matter", "Test matter for report gen", ownerMemberId);
    return projectRepository.saveAndFlush(project);
  }

  private AiExecution createTestExecution() {
    var execution =
        new AiExecution(
            "contract-review", "DOCUMENT", UUID.randomUUID(), ownerMemberId, "claude-sonnet", 1);
    return aiExecutionRepository.saveAndFlush(execution);
  }

  @Test
  void generateReviewReport_createsDocument_withTiptapContent() {
    runInTenant(
        () -> {
          var project = createTestProject();
          var execution = createTestExecution();
          var output = buildTestOutput();

          Document doc =
              reportGenerator.generateReviewReport(
                  output, project.getId(), execution.getId(), ownerMemberId);

          assertThat(doc).isNotNull();
          assertThat(doc.getId()).isNotNull();
          assertThat(doc.getFileName()).startsWith("Contract Review Report - ");
          assertThat(doc.getFileName()).endsWith(".json");
          assertThat(doc.getContentType()).isEqualTo("application/json");
          assertThat(doc.getStatus()).isEqualTo(Document.Status.UPLOADED);
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
  void generateReviewReport_setsAiProvenance() {
    runInTenant(
        () -> {
          var project = createTestProject();
          var execution = createTestExecution();
          var output = buildTestOutput();

          Document doc =
              reportGenerator.generateReviewReport(
                  output, project.getId(), execution.getId(), ownerMemberId);

          assertThat(doc.getSource()).isEqualTo(Document.Source.AI_GENERATED);
          assertThat(doc.getAiExecutionId()).isEqualTo(execution.getId());

          // Verify persisted state
          Document reloaded = documentRepository.findById(doc.getId()).orElseThrow();
          assertThat(reloaded.getSource()).isEqualTo(Document.Source.AI_GENERATED);
          assertThat(reloaded.getAiExecutionId()).isEqualTo(execution.getId());
        });
  }

  @Test
  void generateReviewReport_contentIncludesExecutiveSummary() {
    runInTenant(
        () -> {
          var project = createTestProject();
          var execution = createTestExecution();
          var output = buildTestOutput();

          Document doc =
              reportGenerator.generateReviewReport(
                  output, project.getId(), execution.getId(), ownerMemberId);

          byte[] storedBytes = storageService.download(doc.getS3Key());
          String content = new String(storedBytes, StandardCharsets.UTF_8);

          assertThat(content).contains("This is a test executive summary for the contract review.");
          assertThat(content).contains("Executive Summary");
          assertThat(content).contains("Contract Review Report");
        });
  }

  @Test
  @SuppressWarnings("unchecked")
  void generateReviewReport_findingsGroupedBySeverity() {
    runInTenant(
        () -> {
          var project = createTestProject();
          var execution = createTestExecution();
          var output = buildTestOutput();

          Document doc =
              reportGenerator.generateReviewReport(
                  output, project.getId(), execution.getId(), ownerMemberId);

          byte[] storedBytes = storageService.download(doc.getS3Key());
          Map<String, Object> tiptap = objectMapper.readValue(storedBytes, Map.class);

          List<Map<String, Object>> nodes = (List<Map<String, Object>>) tiptap.get("content");

          // Extract all heading texts to verify severity ordering
          List<String> headingTexts =
              nodes.stream()
                  .filter(n -> "heading".equals(n.get("type")))
                  .filter(
                      n -> {
                        var attrs = (Map<String, Object>) n.get("attrs");
                        return attrs != null && Integer.valueOf(3).equals(attrs.get("level"));
                      })
                  .map(
                      n -> {
                        var nodeContent = (List<Map<String, Object>>) n.get("content");
                        return (String) nodeContent.getFirst().get("text");
                      })
                  .toList();

          // Verify severity groups appear in correct order: HIGH, MEDIUM, LOW
          assertThat(headingTexts)
              .containsExactly("HIGH Risk Findings", "MEDIUM Risk Findings", "LOW Risk Findings");
        });
  }
}
