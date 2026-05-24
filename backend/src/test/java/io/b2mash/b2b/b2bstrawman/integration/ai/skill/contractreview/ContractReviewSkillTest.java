package io.b2mash.b2b.b2bstrawman.integration.ai.skill.contractreview;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.AiSkillExecutionService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillContext;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillExecutionResult;
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
class ContractReviewSkillTest {

  private static final String ORG_ID = "org_contract_review_test";
  private final AtomicInteger counter = new AtomicInteger(0);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiSkillExecutionService executionService;
  @Autowired private StorageService storageService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Contract Review Test Org", null);
    String ownerStr =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_cr_owner",
            "cr_owner@test.com",
            "Contract Review Owner",
            "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void contractReviewSkill_producesCompletedExecution_withCorrectSkillId() {
    runInTenant(
        () -> {
          UUID documentId = createUploadedDocument("application/json");
          var context = new SkillContext(documentId, "DOCUMENT", "Contract review test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("contract-review", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          assertThat(execution).isNotNull();
          assertThat(execution.getStatus()).isEqualTo("COMPLETED");
          assertThat(execution.getSkillId()).isEqualTo("contract-review");
          assertThat(execution.getEntityType()).isEqualTo("DOCUMENT");
        });
  }

  @Test
  void contractReviewSkill_outputIsParseable_asContractReviewOutput() {
    runInTenant(
        () -> {
          UUID documentId = createUploadedDocument("application/json");
          var context =
              new SkillContext(
                  documentId, "DOCUMENT", "Contract review output parse test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("contract-review", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          assertThat(execution.getOutputContent()).isNotNull();
          ContractReviewOutput output = parseOutput(execution.getOutputContent());
          assertThat(output.documentClassification()).isNotNull();
          assertThat(output.documentClassification().type()).isEqualTo("COMMERCIAL_CONTRACT");
          assertThat(output.executiveSummary()).contains("Service Level Agreement");
          assertThat(output.findings()).hasSize(3);
          assertThat(output.missingProtections()).hasSize(1);
          assertThat(output.recommendedActions()).hasSize(1);
        });
  }

  @Test
  void contractReviewSkill_createsOneGate_withTypeCreateReviewReport() {
    runInTenant(
        () -> {
          UUID documentId = createUploadedDocument("application/json");
          var context =
              new SkillContext(documentId, "DOCUMENT", "Contract review gate test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("contract-review", context, ownerMemberId, List.of());
          List<AiExecutionGate> gates = result.gates();

          assertThat(gates).hasSize(1);
          assertThat(gates.getFirst().getGateType()).isEqualTo("CREATE_REVIEW_REPORT");
          assertThat(gates.getFirst().getStatus()).isEqualTo("PENDING");
          assertThat(gates.getFirst().getAiReasoning()).contains("Service Level Agreement");
        });
  }

  @Test
  void contractReviewSkill_failsGracefully_whenDocumentHasUnsupportedType() {
    runInTenant(
        () -> {
          UUID documentId = createUploadedDocument("image/png");

          var context = new SkillContext(documentId, "DOCUMENT", "Unsupported type test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("contract-review", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          assertThat(execution.getStatus()).isEqualTo("FAILED");
          assertThat(execution.getErrorMessage()).contains("Cannot extract text");
        });
  }

  @Test
  void contractReviewSkill_failsGracefully_whenDocumentDoesNotExist() {
    runInTenant(
        () -> {
          UUID nonExistentId = UUID.randomUUID();
          var context =
              new SkillContext(nonExistentId, "DOCUMENT", "Missing document test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("contract-review", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          assertThat(execution.getStatus()).isEqualTo("FAILED");
          assertThat(execution.getErrorMessage()).contains("Document");
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

  /**
   * Creates an uploaded document with the given content type. For extractable types
   * (application/json), stores a Tiptap JSON document with contract text. For unsupported types
   * (e.g. image/png), stores dummy bytes to trigger the extraction failure path.
   */
  private UUID createUploadedDocument(String contentType) {
    int idx = counter.incrementAndGet();

    // Create a project to satisfy the PROJECT scope FK constraint
    var project =
        new Project(
            "Contract Review Matter " + idx, "Test matter for contract review", ownerMemberId);
    project.setWorkType("commercial");
    project = projectRepository.saveAndFlush(project);

    String fileName =
        "application/json".equals(contentType)
            ? "contract-" + idx + ".json"
            : "contract-" + idx + ".png";

    var doc =
        new Document(
            Document.Scope.PROJECT,
            project.getId(),
            null,
            fileName,
            contentType,
            2048L,
            ownerMemberId,
            Document.Visibility.INTERNAL);
    doc.assignS3Key("org/" + ORG_ID + "/documents/" + fileName);
    doc.confirmUpload();
    doc = documentRepository.saveAndFlush(doc);

    // Put bytes into InMemoryStorageService — use Tiptap JSON for extractable documents
    byte[] content;
    if ("application/json".equals(contentType)) {
      content =
          ("""
          {"type":"doc","content":[
            {"type":"paragraph","content":[{"type":"text","text":"SERVICE LEVEL AGREEMENT"}]},
            {"type":"paragraph","content":[{"type":"text","text":"This Service Level Agreement is entered into between Acme Solutions (Pty) Ltd and TechCorp Holdings (Pty) Ltd for the provision of IT managed services."}]},
            {"type":"paragraph","content":[{"type":"text","text":"Clause 12.3 - Limitation of Liability. The service provider shall not be liable for any indirect, consequential, or special damages arising from this agreement."}]},
            {"type":"paragraph","content":[{"type":"text","text":"Clause 15.1 - Term and Renewal. This agreement is for a period of 24 months and shall automatically renew unless either party gives 30 days written notice."}]},
            {"type":"paragraph","content":[{"type":"text","text":"Clause 18 - Confidentiality and Data Protection. Each party shall maintain the confidentiality of information received."}]}
          ]}""")
              .getBytes(StandardCharsets.UTF_8);
    } else {
      content = "dummy binary content".getBytes(StandardCharsets.UTF_8);
    }
    storageService.upload(doc.getS3Key(), content, contentType);

    return doc.getId();
  }

  private ContractReviewOutput parseOutput(String json) {
    try {
      return objectMapper.readValue(json, ContractReviewOutput.class);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse contract review output: " + e.getMessage(), e);
    }
  }
}
