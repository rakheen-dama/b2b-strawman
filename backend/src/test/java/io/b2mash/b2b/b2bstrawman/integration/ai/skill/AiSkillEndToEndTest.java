package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.compliance.ComplianceAuditFindingRepository;
import io.b2mash.b2b.b2bstrawman.compliance.ComplianceAuditReport;
import io.b2mash.b2b.b2bstrawman.compliance.ComplianceAuditReportRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
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
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.nio.charset.StandardCharsets;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-to-end integration tests for the three new AI skill endpoints (contract-review, drafting,
 * compliance-audit) and their gate approval flows. Tests the full HTTP -> execution -> gate ->
 * approve -> downstream effect cycle using MockMvc and StubAiProvider.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiSkillEndToEndTest {

  private static final String ORG_ID = "org_ai_skill_e2e_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private StorageService storageService;
  @Autowired private ComplianceAuditReportRepository complianceAuditReportRepository;
  @Autowired private ComplianceAuditFindingRepository complianceAuditFindingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID projectId;
  private UUID documentId;
  private UUID templateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "AI Skill E2E Test Org", null);

    String ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_e2e_owner", "e2e_owner@test.com", "E2E Owner", "owner");
    ownerMemberId = UUID.fromString(ownerStr);

    // Sync a regular member (no AI capabilities)
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_e2e_member", "e2e_member@test.com", "E2E Member", "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create test fixtures within tenant context
    runInTenant(this::createTestFixtures);
  }

  // ── Contract Review Tests ─────────────────────────────────────────────────

  @Test
  @Order(1)
  void contractReview_createsExecution_withGate() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/ai/skills/contract-review")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_e2e_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"documentId":"%s","projectId":"%s"}
                        """
                            .formatted(documentId, projectId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.gates").isArray())
            .andExpect(jsonPath("$.gates[0].gateType").value("CREATE_REVIEW_REPORT"))
            .andExpect(jsonPath("$.gates[0].status").value("PENDING"))
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(JsonPath.<String>read(body, "$.executionId")).isNotNull();
  }

  @Test
  @Order(2)
  void contractReview_gateApproval_createsDocument() throws Exception {
    // Step 1: Execute the skill to get a gate
    var skillResult =
        mockMvc
            .perform(
                post("/api/ai/skills/contract-review")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_e2e_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"documentId":"%s","projectId":"%s"}
                        """
                            .formatted(documentId, projectId)))
            .andExpect(status().isOk())
            .andReturn();

    String gateId = JsonPath.read(skillResult.getResponse().getContentAsString(), "$.gates[0].id");

    // Step 2: Approve the gate
    mockMvc
        .perform(
            post("/api/ai/gates/" + gateId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_e2e_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes":"Reviewed and approved"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    // Step 3: Verify a new AI-generated document was created in the project
    runInTenant(
        () -> {
          List<Document> projectDocs = documentRepository.findProjectScopedByProjectId(projectId);
          List<Document> aiDocs =
              projectDocs.stream()
                  .filter(d -> Document.Source.AI_GENERATED.equals(d.getSource()))
                  .toList();
          assertThat(aiDocs).isNotEmpty();
          assertThat(aiDocs.getFirst().getFileName()).contains("Contract Review Report");
        });
  }

  // ── Drafting Tests ────────────────────────────────────────────────────────

  @Test
  @Order(3)
  void drafting_createsExecution_withGate() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/ai/skills/drafting")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_e2e_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"templateId":"%s","projectId":"%s"}
                        """
                            .formatted(templateId, projectId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.gates").isArray())
            .andExpect(jsonPath("$.gates[0].gateType").value("CREATE_DRAFT_DOCUMENT"))
            .andExpect(jsonPath("$.gates[0].status").value("PENDING"))
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(JsonPath.<String>read(body, "$.executionId")).isNotNull();
  }

  @Test
  @Order(4)
  void drafting_gateApproval_createsDraftDocument() throws Exception {
    // Step 1: Execute the skill to get a gate
    var skillResult =
        mockMvc
            .perform(
                post("/api/ai/skills/drafting")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_e2e_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"templateId":"%s","projectId":"%s"}
                        """
                            .formatted(templateId, projectId)))
            .andExpect(status().isOk())
            .andReturn();

    String gateId = JsonPath.read(skillResult.getResponse().getContentAsString(), "$.gates[0].id");

    // Step 2: Approve the gate
    mockMvc
        .perform(
            post("/api/ai/gates/" + gateId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_e2e_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes":"Draft looks good"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    // Step 3: Verify a new AI-generated draft document was created
    runInTenant(
        () -> {
          List<Document> projectDocs = documentRepository.findProjectScopedByProjectId(projectId);
          List<Document> aiDraftDocs =
              projectDocs.stream()
                  .filter(d -> Document.Source.AI_GENERATED.equals(d.getSource()))
                  .filter(d -> d.getFileName().contains("Engagement Letter"))
                  .toList();
          assertThat(aiDraftDocs).isNotEmpty();
        });
  }

  // ── Compliance Audit Tests ────────────────────────────────────────────────

  @Test
  @Order(5)
  void complianceAudit_createsExecution_withGate() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/ai/skills/compliance-audit")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_e2e_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.gates").isArray())
            .andExpect(jsonPath("$.gates[0].gateType").value("PUBLISH_COMPLIANCE_REPORT"))
            .andExpect(jsonPath("$.gates[0].status").value("PENDING"))
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(JsonPath.<String>read(body, "$.executionId")).isNotNull();
  }

  @Test
  @Order(6)
  void complianceAudit_gateApproval_publishesReport() throws Exception {
    // Step 1: Execute the skill to get a gate
    var skillResult =
        mockMvc
            .perform(
                post("/api/ai/skills/compliance-audit")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_e2e_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn();

    String gateId = JsonPath.read(skillResult.getResponse().getContentAsString(), "$.gates[0].id");

    // Step 2: Approve the gate
    mockMvc
        .perform(
            post("/api/ai/gates/" + gateId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_e2e_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes":"Audit findings acknowledged"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    // Step 3: Verify a compliance audit report was published with findings
    runInTenant(
        () -> {
          var reports =
              complianceAuditReportRepository.findByStatusOrderByCreatedAtDesc(
                  "PUBLISHED", Pageable.unpaged());
          assertThat(reports.getContent()).isNotEmpty();
          ComplianceAuditReport report = reports.getContent().getFirst();
          assertThat(report.getOverallGrade()).isEqualTo("B");

          var findings =
              complianceAuditFindingRepository.findByReportIdOrderBySeverity(
                  report.getId(), Pageable.unpaged());
          assertThat(findings.getContent()).isNotEmpty();
        });
  }

  // ── Rejection Test ────────────────────────────────────────────────────────

  @Test
  @Order(7)
  void contractReviewGateRejection_leavesNoDownstreamEffects() throws Exception {
    // Record baseline document count
    int[] baselineCount = new int[1];
    runInTenant(
        () -> {
          baselineCount[0] = documentRepository.findProjectScopedByProjectId(projectId).size();
        });

    // Step 1: Execute contract review skill to get a gate
    var skillResult =
        mockMvc
            .perform(
                post("/api/ai/skills/contract-review")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_e2e_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"documentId":"%s","projectId":"%s"}
                        """
                            .formatted(documentId, projectId)))
            .andExpect(status().isOk())
            .andReturn();

    String gateId = JsonPath.read(skillResult.getResponse().getContentAsString(), "$.gates[0].id");

    // Step 2: Reject the gate
    mockMvc
        .perform(
            post("/api/ai/gates/" + gateId + "/reject")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_e2e_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes":"Not acceptable"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));

    // Step 3: Verify no new documents were created (count unchanged)
    runInTenant(
        () -> {
          int afterCount = documentRepository.findProjectScopedByProjectId(projectId).size();
          assertThat(afterCount).isEqualTo(baselineCount[0]);
        });
  }

  @Test
  @Order(10)
  void complianceAuditGateRejection_leavesNoDownstreamEffects() throws Exception {
    // Record baseline report count
    long[] baselineReportCount = new long[1];
    runInTenant(
        () -> {
          baselineReportCount[0] =
              complianceAuditReportRepository
                  .findByStatusOrderByCreatedAtDesc("PUBLISHED", Pageable.unpaged())
                  .getTotalElements();
        });

    // Step 1: Execute compliance audit skill to get a gate
    var skillResult =
        mockMvc
            .perform(
                post("/api/ai/skills/compliance-audit")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_e2e_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn();

    String gateId = JsonPath.read(skillResult.getResponse().getContentAsString(), "$.gates[0].id");

    // Step 2: Reject the gate
    mockMvc
        .perform(
            post("/api/ai/gates/" + gateId + "/reject")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_e2e_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes":"Audit findings rejected"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));

    // Step 3: Verify no new compliance audit report was published (count unchanged)
    runInTenant(
        () -> {
          long afterCount =
              complianceAuditReportRepository
                  .findByStatusOrderByCreatedAtDesc("PUBLISHED", Pageable.unpaged())
                  .getTotalElements();
          assertThat(afterCount).isEqualTo(baselineReportCount[0]);
        });
  }

  // ── Authorization Tests ───────────────────────────────────────────────────

  @Test
  @Order(8)
  void skillEndpoints_return403_forUnprovisionedOrg() throws Exception {
    // Use a JWT from an org that hasn't been provisioned — no tenant schema = 403
    // Contract review
    mockMvc
        .perform(
            post("/api/ai/skills/contract-review")
                .with(TestJwtFactory.ownerJwt("org_not_provisioned_e2e", "user_unknown"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"documentId":"%s","projectId":"%s"}
                    """
                        .formatted(UUID.randomUUID(), UUID.randomUUID())))
        .andExpect(status().isForbidden());

    // Drafting
    mockMvc
        .perform(
            post("/api/ai/skills/drafting")
                .with(TestJwtFactory.ownerJwt("org_not_provisioned_e2e", "user_unknown"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"templateId":"%s","projectId":"%s"}
                    """
                        .formatted(UUID.randomUUID(), UUID.randomUUID())))
        .andExpect(status().isForbidden());

    // Compliance audit
    mockMvc
        .perform(
            post("/api/ai/skills/compliance-audit")
                .with(TestJwtFactory.ownerJwt("org_not_provisioned_e2e", "user_unknown"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(9)
  void gateApproval_returns403_forMemberWithoutAiReview() throws Exception {
    // First, create a gate as owner
    var skillResult =
        mockMvc
            .perform(
                post("/api/ai/skills/compliance-audit")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_e2e_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn();

    String gateId = JsonPath.read(skillResult.getResponse().getContentAsString(), "$.gates[0].id");

    // Try to approve as member (no AI_REVIEW capability)
    mockMvc
        .perform(
            post("/api/ai/gates/" + gateId + "/approve")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_e2e_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes":"Attempting approval"}
                    """))
        .andExpect(status().isForbidden());
  }

  // ── Test Fixture Setup ────────────────────────────────────────────────────

  private void createTestFixtures() {
    // Create a project
    var project =
        new Project("E2E Test Matter", "End-to-end test matter for AI skills", ownerMemberId);
    project.setWorkType("commercial");
    project = projectRepository.saveAndFlush(project);
    projectId = project.getId();

    // Create a document in the project (for contract review)
    var doc =
        new Document(
            Document.Scope.PROJECT,
            projectId,
            null,
            "test-contract.json",
            "application/json",
            2048L,
            ownerMemberId,
            Document.Visibility.INTERNAL);
    doc.assignS3Key("org/" + ORG_ID + "/documents/test-contract.json");
    doc.confirmUpload();
    doc = documentRepository.saveAndFlush(doc);
    documentId = doc.getId();

    // Upload content to InMemoryStorageService
    byte[] contractContent =
        ("""
        {"type":"doc","content":[
          {"type":"paragraph","content":[{"type":"text","text":"SERVICE LEVEL AGREEMENT"}]},
          {"type":"paragraph","content":[{"type":"text","text":"This Service Level Agreement is entered into between Acme Solutions (Pty) Ltd and TechCorp Holdings (Pty) Ltd."}]},
          {"type":"paragraph","content":[{"type":"text","text":"Clause 12.3 - Limitation of Liability. The service provider shall not be liable for any indirect, consequential, or special damages."}]}
        ]}""")
            .getBytes(StandardCharsets.UTF_8);
    storageService.upload(doc.getS3Key(), contractContent, "application/json");

    // Create a document template (for drafting)
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
            "e2e-engagement-letter-template",
            TemplateCategory.ENGAGEMENT_LETTER,
            templateContent);
    template = documentTemplateRepository.saveAndFlush(template);
    templateId = template.getId();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_EXECUTE", "AI_REVIEW", "AI_MANAGE"))
        .run(() -> transactionTemplate.executeWithoutResult(status -> action.run()));
  }
}
