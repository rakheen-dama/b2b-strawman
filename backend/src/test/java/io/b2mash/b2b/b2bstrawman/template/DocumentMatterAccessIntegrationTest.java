package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Matter access on the document paths that skipped it (D19):
 *
 * <ul>
 *   <li>G89-F53 — {@code POST /api/templates/{id}/generate}, {@code /preview} and {@code
 *       /generate-docx} rendered any matter for a member not on it.
 *   <li>G89-F54 — the Statement of Account list / get / generate were gated by capability only.
 *   <li>G89-F55 — the statement {@code pdfUrl} pointed at a route that does not exist.
 *   <li>G89-F56 — AI contract review read any document and sent its text to the LLM.
 * </ul>
 *
 * <p>M is on the matter, N is not; both hold the same custom role, so every difference is the
 * matter membership. A member who cannot see the matter gets 404 (security-by-obscurity).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentMatterAccessIntegrationTest {

  private static final String ORG_ID = "org_doc_matter_access_test";
  private static final String OWNER = "user_dma_owner";
  private static final String M = "user_dma_m";
  private static final String N = "user_dma_n";
  private static final String PERIOD =
      """
      {"periodStart":"2026-01-01","periodEnd":"2026-03-31"}""";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private AiExecutionRepository aiExecutionRepository;
  @Autowired private StorageService storageService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String schema;
  private UUID ownerId;
  private UUID matterId;
  private String tiptapTemplateId;
  private String docxTemplateId;
  private String statementId;
  private UUID contractDocumentId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Matter Access Attorneys", "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "dma_owner@test.com", "DMA Owner", "owner"));
    UUID mId =
        UUID.fromString(
            TestMemberHelper.syncMember(mockMvc, ORG_ID, M, "dma_m@test.com", "DMA M", "member"));
    UUID nId =
        UUID.fromString(
            TestMemberHelper.syncMember(mockMvc, ORG_ID, N, "dma_n@test.com", "DMA N", "member"));
    String role =
        createCustomRole(
            "Matter Clerk",
            "VIEW_LEGAL",
            "TEAM_OVERSIGHT",
            "GENERATE_STATEMENT_OF_ACCOUNT",
            "AI_EXECUTE");
    assignRole(mId, role);
    assignRole(nId, role);

    UUID clientId =
        inTenantTx(
            () ->
                customerRepository
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "Dlamini Trading", "dlamini@test.com", ownerId))
                    .getId());
    var created =
        mockMvc
            .perform(
                post("/api/matters")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Dlamini supply dispute","clientId":"%s","workType":"LITIGATION"}"""
                            .formatted(clientId)))
            .andExpect(status().isCreated())
            .andReturn();
    matterId = UUID.fromString(TestEntityHelper.extractId(created));
    mockMvc
        .perform(
            post("/api/matters/" + matterId + "/members")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":\"%s\"}".formatted(mId)))
        .andExpect(status().isCreated());

    tiptapTemplateId = createTiptapMatterTemplate();
    docxTemplateId = uploadDocxMatterTemplate();
    statementId =
        JsonPath.read(
                mockMvc
                    .perform(
                        post("/api/matters/" + matterId + "/statements")
                            .with(owner())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PERIOD))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(),
                "$.id")
            .toString();
    contractDocumentId = inTenantTx(this::createContractDocument);
  }

  // ---- G89-F53: generic generate / preview / generate-docx --------------------------------

  @Test
  void generate_memberNotOnMatter_notFound_memberOnMatter_ok() throws Exception {
    String body =
        "{\"entityId\":\"%s\",\"saveToDocuments\":false,\"acknowledgeWarnings\":true}"
            .formatted(matterId);
    mockMvc
        .perform(
            post("/api/templates/" + tiptapTemplateId + "/generate")
                .with(member(N))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/templates/" + tiptapTemplateId + "/generate")
                .with(member(M))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void preview_memberNotOnMatter_notFound_memberOnMatter_ok() throws Exception {
    String body = "{\"entityId\":\"%s\"}".formatted(matterId);
    mockMvc
        .perform(
            post("/api/templates/" + tiptapTemplateId + "/preview")
                .with(member(N))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/templates/" + tiptapTemplateId + "/preview")
                .with(member(M))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void generateDocx_memberNotOnMatter_notFound_memberOnMatter_ok() throws Exception {
    String body = "{\"entityId\":\"%s\"}".formatted(matterId);
    mockMvc
        .perform(
            post("/api/templates/" + docxTemplateId + "/generate-docx")
                .with(member(N))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/templates/" + docxTemplateId + "/generate-docx")
                .with(member(M))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  // ---- G89-F54: statements list / get / generate ------------------------------------------

  @Test
  void statements_memberNotOnMatter_notFound() throws Exception {
    mockMvc
        .perform(get("/api/matters/" + matterId + "/statements").with(member(N)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/matters/" + matterId + "/statements/" + statementId).with(member(N)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/matters/" + matterId + "/statements")
                .with(member(N))
                .contentType(MediaType.APPLICATION_JSON)
                .content(PERIOD))
        .andExpect(status().isNotFound());
  }

  @Test
  void statements_memberOnMatter_ok() throws Exception {
    mockMvc
        .perform(get("/api/matters/" + matterId + "/statements").with(member(M)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(statementId)).exists());
    mockMvc
        .perform(get("/api/matters/" + matterId + "/statements/" + statementId).with(member(M)))
        .andExpect(status().isOk());
  }

  // ---- G89-F55: the statement pdfUrl resolves -----------------------------------------------

  @Test
  void statementPdfUrl_resolvesToADownloadForAnAuthorisedMember() throws Exception {
    String pdfUrl =
        JsonPath.read(
            mockMvc
                .perform(
                    get("/api/matters/" + matterId + "/statements/" + statementId).with(member(M)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.pdfUrl");
    assertThat(pdfUrl).isEqualTo("/api/generated-documents/" + statementId + "/download");
    // Followed by the owner: the owner's statement prints the trust section, so it downloads only
    // for a member holding VIEW_TRUST (G89-F52) — M, without it, is refused (403).
    mockMvc.perform(get(pdfUrl).with(owner())).andExpect(status().isFound());
    mockMvc.perform(get(pdfUrl).with(member(M))).andExpect(status().isForbidden());

    String listedPdfUrl =
        JsonPath.<List<String>>read(
                mockMvc
                    .perform(get("/api/matters/" + matterId + "/statements").with(member(M)))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(),
                "$.content[?(@.id == '%s')].pdfUrl".formatted(statementId))
            .getFirst();
    assertThat(listedPdfUrl).isEqualTo(pdfUrl);
  }

  // ---- G89-F56: AI contract review reads only a document the member may read --------------

  @Test
  void contractReview_documentOnAMatterTheMemberIsNotOn_deniedAndNeverSent() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/ai/skills/contract-review")
                    .with(member(N))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contractReviewBody()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // The skill's error contract: a pre-flight failure is a FAILED execution, recorded before the
    // LLM call — no output, no cost, no gate, and a duration of zero (the provider never ran).
    assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("FAILED");
    assertThat(JsonPath.<Object>read(body, "$.output")).isNull();
    assertThat(JsonPath.<List<?>>read(body, "$.gates")).isEmpty();
    assertThat(((Number) JsonPath.read(body, "$.costCents")).longValue()).isZero();
    assertThat(((Number) JsonPath.read(body, "$.durationMs")).longValue()).isZero();

    UUID executionId = UUID.fromString(JsonPath.read(body, "$.executionId"));
    var execution = inTenantTx(() -> aiExecutionRepository.findById(executionId).orElseThrow());
    assertThat(execution.getInputTokens()).isZero();
    assertThat(execution.getErrorMessage()).isNotBlank();
  }

  @Test
  void contractReview_memberOnMatter_completes() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/skills/contract-review")
                .with(member(M))
                .contentType(MediaType.APPLICATION_JSON)
                .content(contractReviewBody()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  // ---- helpers ------------------------------------------------------------------------------

  private String contractReviewBody() {
    return "{\"documentId\":\"%s\",\"matterId\":\"%s\"}".formatted(contractDocumentId, matterId);
  }

  private static JwtRequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private static JwtRequestPostProcessor member(String subject) {
    return TestJwtFactory.memberJwt(ORG_ID, subject);
  }

  private String createTiptapMatterTemplate() throws Exception {
    Map<String, Object> content =
        TestDocumentBuilder.doc()
            .heading(1, "Matter summary")
            .paragraph("Summary of the matter")
            .build();
    var body = new LinkedHashMap<String, Object>();
    body.put("name", "Matter Access Summary");
    body.put("category", "PROJECT_SUMMARY");
    body.put("primaryEntityType", "PROJECT");
    body.put("content", content);
    var result =
        mockMvc
            .perform(
                post("/api/templates")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String uploadDocxMatterTemplate() throws Exception {
    byte[] docx;
    try (XWPFDocument doc = new XWPFDocument()) {
      doc.createParagraph().createRun().setText("Matter {{project.name}}");
      var out = new ByteArrayOutputStream();
      doc.write(out);
      docx = out.toByteArray();
    }
    var file =
        new MockMultipartFile(
            "file",
            "matter-access.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            docx);
    var result =
        mockMvc
            .perform(
                multipart("/api/templates/docx/upload")
                    .file(file)
                    .param("name", "Matter Access DOCX")
                    .param("category", "ENGAGEMENT_LETTER")
                    .param("entityType", "PROJECT")
                    .with(owner()))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  /** A Tiptap-JSON contract on the matter, with its bytes in storage (text-extractable). */
  private UUID createContractDocument() {
    var doc =
        new Document(
            Document.Scope.PROJECT,
            matterId,
            null,
            "supply-agreement.json",
            "application/json",
            512L,
            ownerId,
            Document.Visibility.INTERNAL);
    doc.assignS3Key("org/" + ORG_ID + "/documents/supply-agreement.json");
    doc.confirmUpload();
    doc = documentRepository.saveAndFlush(doc);
    storageService.upload(
        doc.getS3Key(),
        """
        {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text",\
        "text":"SUPPLY AGREEMENT between Dlamini Trading and a supplier. Clause 4 - Payment."}]}]}"""
            .getBytes(StandardCharsets.UTF_8),
        "application/json");
    return doc.getId();
  }

  private String createCustomRole(String name, String... capabilities) throws Exception {
    String caps = objectMapper.writeValueAsString(List.of(capabilities));
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":%s}"""
                            .formatted(name, caps)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(UUID memberId, String roleId) throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }

  private <T> T inTenantTx(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_EXECUTE"))
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }
}
