package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.TestDocumentBuilder;
import io.b2mash.b2b.b2bstrawman.testutil.StubAiProvider;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * G89-F58: the AI skills that take a caller-supplied matter ({@code contract-review}'s {@code
 * matterId}, {@code drafting}'s {@code matterId}) loaded it with no matter check, so a member not
 * on the matter sent its name and details to the LLM. G89-F59: contract review without a {@code
 * matterId} returned 500.
 *
 * <p>M is on the matter "Ndaba secret acquisition"; N, with the same custom role, is not. The
 * matter's name is the marker: the stub AI provider records every prompt it receives, and a refused
 * run must never send it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiSkillMatterAccessIntegrationTest {

  private static final String ORG_ID = "org_ai_skill_matter_access_test";
  private static final String OWNER = "user_asma_owner";
  private static final String M = "user_asma_m";
  private static final String N = "user_asma_n";
  private static final String SECRET_MATTER = "Ndaba secret acquisition";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private AiExecutionRepository aiExecutionRepository;
  @Autowired private StorageService storageService;
  @Autowired private StubAiProvider stubAiProvider;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String schema;
  private UUID ownerId;

  /** The secret matter: nobody runs a skill on it legitimately, so its name is a clean marker. */
  private String secretMatterId;

  /** M's own matter, for the positive controls. */
  private String memberMatterId;

  private String templateId;
  private UUID orgDocumentId;
  private UUID matterDocumentId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "AI Matter Access Attorneys", "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "asma_owner@test.com", "ASMA Owner", "owner"));
    String mId =
        TestMemberHelper.syncMember(mockMvc, ORG_ID, M, "asma_m@test.com", "ASMA M", "member");
    String nId =
        TestMemberHelper.syncMember(mockMvc, ORG_ID, N, "asma_n@test.com", "ASMA N", "member");
    String role = createCustomRole("AI Clerk", "VIEW_LEGAL", "AI_EXECUTE");
    assignRole(mId, role);
    assignRole(nId, role);

    secretMatterId = TestEntityHelper.createProject(mockMvc, owner(), SECRET_MATTER);
    memberMatterId = TestEntityHelper.createProject(mockMvc, owner(), "Mokoena supply review");
    mockMvc
        .perform(
            post("/api/matters/" + memberMatterId + "/members")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":\"%s\"}".formatted(mId)))
        .andExpect(status().isCreated());

    templateId = createMatterTemplate();
    // An ORG-scoped document: every member may read it, so only the matter check can refuse N.
    orgDocumentId = inTenantTx(() -> createContract(null, "standard-terms.json"));
    matterDocumentId =
        inTenantTx(() -> createContract(UUID.fromString(memberMatterId), "supply-terms.json"));
  }

  // ---- G89-F58: contract review with a matterId the member cannot view -------------------

  @Test
  void contractReview_matterTheMemberIsNotOn_deniedAndMatterNeverSent() throws Exception {
    String body =
        runSkill(
            N,
            "/api/ai/skills/contract-review",
            "{\"documentId\":\"%s\",\"matterId\":\"%s\"}".formatted(orgDocumentId, secretMatterId));
    assertRefusedBeforeTheLlm(body, "not found");
    assertThat(stubAiProvider.receivedPromptContaining(SECRET_MATTER)).isFalse();
  }

  // ---- G89-F58: drafting for a matter the member cannot view --------------------------------

  @Test
  void drafting_matterTheMemberIsNotOn_deniedAndMatterNeverSent() throws Exception {
    String body =
        runSkill(
            N,
            "/api/ai/skills/drafting",
            "{\"templateId\":\"%s\",\"matterId\":\"%s\"}".formatted(templateId, secretMatterId));
    assertRefusedBeforeTheLlm(body, "not found");
    assertThat(stubAiProvider.receivedPromptContaining(SECRET_MATTER)).isFalse();
  }

  // ---- positive controls: a member on the matter -----------------------------------------------

  @Test
  void contractReview_memberOnMatter_completes() throws Exception {
    String body =
        runSkill(
            M,
            "/api/ai/skills/contract-review",
            "{\"documentId\":\"%s\",\"matterId\":\"%s\"}".formatted(orgDocumentId, memberMatterId));
    assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("COMPLETED");
  }

  @Test
  void drafting_memberOnMatter_completes() throws Exception {
    String body =
        runSkill(
            M,
            "/api/ai/skills/drafting",
            "{\"templateId\":\"%s\",\"matterId\":\"%s\"}".formatted(templateId, memberMatterId));
    assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("COMPLETED");
  }

  // ---- G89-F59: contract review without a matterId ---------------------------------------------

  /** Without a matterId the review attaches to the document's own matter (the skill contract). */
  @Test
  void contractReview_withoutMatterId_matterDocument_completes() throws Exception {
    var response =
        mockMvc
            .perform(
                post("/api/ai/skills/contract-review")
                    .with(member(M))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"documentId\":\"%s\"}".formatted(matterDocumentId)))
            .andReturn()
            .getResponse();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(JsonPath.<String>read(response.getContentAsString(), "$.status"))
        .isEqualTo("COMPLETED");
  }

  /** No matterId and a document on no matter: refused before the LLM, never a 500. */
  @Test
  void contractReview_withoutMatterId_documentOnNoMatter_refusedBeforeTheLlm() throws Exception {
    var response =
        mockMvc
            .perform(
                post("/api/ai/skills/contract-review")
                    .with(member(M))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"documentId\":\"%s\"}".formatted(orgDocumentId)))
            .andReturn()
            .getResponse();
    assertThat(response.getStatus()).isEqualTo(200);
    assertRefusedBeforeTheLlm(response.getContentAsString(), "matter");
  }

  // ---- G89-F62: drafting without a templateId --------------------------------------------------

  /** Map.of rejected the null templateId, so the request was a 500; it is a 400 ProblemDetail. */
  @Test
  void drafting_withoutTemplateId_is400TemplateRequired() throws Exception {
    long callsBefore = stubAiProvider.completionCallCount();
    mockMvc
        .perform(
            post("/api/ai/skills/drafting")
                .with(member(M))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"matterId\":\"%s\"}".formatted(memberMatterId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Template required"))
        .andExpect(jsonPath("$.detail").value("Choose a template to draft from."));
    // This request (memberMatterId) never reached the provider.
    assertThat(stubAiProvider.completionCallCount()).isEqualTo(callsBefore);
  }

  /** The same null-safety for the matter: a 400, never a 500. */
  @Test
  void drafting_withoutMatterId_is400MatterRequired() throws Exception {
    long callsBefore = stubAiProvider.completionCallCount();
    mockMvc
        .perform(
            post("/api/ai/skills/drafting")
                .with(member(M))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\":\"%s\"}".formatted(templateId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Matter required"))
        .andExpect(jsonPath("$.detail").value("Choose the matter to draft for."));
    assertThat(stubAiProvider.completionCallCount()).isEqualTo(callsBefore);
  }

  // ---- helpers --------------------------------------------------------------------------------

  /**
   * The skill error contract (a FAILED execution recorded at prompt assembly) with nothing sent: no
   * output, no cost, a zero duration (the provider never ran), and the refusal itself as the error
   * — not a transaction artefact.
   */
  private void assertRefusedBeforeTheLlm(String body, String errorContains) {
    assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("FAILED");
    assertThat(JsonPath.<Object>read(body, "$.output")).isNull();
    assertThat(JsonPath.<List<?>>read(body, "$.gates")).isEmpty();
    assertThat(((Number) JsonPath.read(body, "$.costCents")).longValue()).isZero();
    assertThat(((Number) JsonPath.read(body, "$.durationMs")).longValue()).isZero();

    UUID executionId = UUID.fromString(JsonPath.read(body, "$.executionId"));
    var execution = inTenantTx(() -> aiExecutionRepository.findById(executionId).orElseThrow());
    assertThat(execution.getErrorMessage()).containsIgnoringCase(errorContains);
  }

  private String runSkill(String subject, String path, String json) throws Exception {
    return mockMvc
        .perform(
            post(path).with(member(subject)).contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static JwtRequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private static JwtRequestPostProcessor member(String subject) {
    return TestJwtFactory.memberJwt(ORG_ID, subject);
  }

  private String createMatterTemplate() throws Exception {
    Map<String, Object> content =
        TestDocumentBuilder.doc().heading(1, "Letter").paragraph("Dear client").build();
    var body = new LinkedHashMap<String, Object>();
    body.put("name", "AI Access Letter");
    body.put("category", "COVER_LETTER");
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

  /** A Tiptap-JSON contract: ORG-scoped when {@code matter} is null, else on that matter. */
  private UUID createContract(UUID matter, String fileName) {
    var doc =
        new Document(
            matter == null ? Document.Scope.ORG : Document.Scope.PROJECT,
            matter,
            null,
            fileName,
            "application/json",
            256L,
            ownerId,
            Document.Visibility.INTERNAL);
    doc.assignS3Key("org/" + ORG_ID + "/documents/" + fileName);
    doc.confirmUpload();
    doc = documentRepository.saveAndFlush(doc);
    storageService.upload(
        doc.getS3Key(),
        """
        {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text",\
        "text":"STANDARD TERMS OF SUPPLY. Clause 3 - Delivery. Clause 7 - Liability."}]}]}"""
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

  private void assignRole(String memberId, String roleId) throws Exception {
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
