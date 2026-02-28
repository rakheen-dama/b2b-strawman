package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentGenerationIntegrationTest {

  private static final Map<String, Object> CONTENT = Map.of("type", "doc", "content", List.of());

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_doc_gen_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String memberIdOwner;
  private UUID testProjectId;
  private UUID testTemplateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Doc Gen Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(ORG_ID, "user_docgen_owner", "docgen_owner@test.com", "DocGen Owner", "owner");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      new Project(
                          "Gen Test Project",
                          "For generation tests",
                          UUID.fromString(memberIdOwner));
                  project = projectRepository.save(project);
                  testProjectId = project.getId();

                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Gen Template",
                          "gen-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          CONTENT);
                  template = documentTemplateRepository.save(template);
                  testTemplateId = template.getId();
                }));
  }

  @Test
  void shouldGenerateAndDownloadPdf() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/templates/" + testTemplateId + "/generate")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"entityId": "%s", "saveToDocuments": false, "acknowledgeWarnings": false}
                        """
                            .formatted(testProjectId)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"gen-template-")))
            .andReturn();

    byte[] pdfBytes = result.getResponse().getContentAsByteArray();
    assertThat(pdfBytes).isNotEmpty();
    assertThat(pdfBytes[0]).isEqualTo((byte) '%');
    assertThat(pdfBytes[1]).isEqualTo((byte) 'P');
    assertThat(pdfBytes[2]).isEqualTo((byte) 'D');
    assertThat(pdfBytes[3]).isEqualTo((byte) 'F');
  }

  @Test
  void shouldGenerateAndSaveToDocuments() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + testTemplateId + "/generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityId": "%s", "saveToDocuments": true, "acknowledgeWarnings": false}
                    """
                        .formatted(testProjectId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.fileName").exists())
        .andExpect(jsonPath("$.fileSize").isNumber())
        .andExpect(jsonPath("$.documentId").exists())
        .andExpect(jsonPath("$.generatedAt").exists());
  }

  @Test
  void shouldCreateGeneratedDocumentWithS3Key() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + testTemplateId + "/generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityId": "%s", "saveToDocuments": false, "acknowledgeWarnings": false}
                    """
                        .formatted(testProjectId)))
        .andExpect(status().isOk());

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var docs =
                      generatedDocumentRepository
                          .findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
                              TemplateEntityType.PROJECT, testProjectId);
                  assertThat(docs).isNotEmpty();
                  var doc = docs.getFirst();
                  assertThat(doc.getS3Key()).startsWith("org/" + tenantSchema + "/generated/");
                  assertThat(doc.getS3Key()).endsWith(".pdf");
                }));
  }

  @Test
  void shouldPopulateContextSnapshot() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + testTemplateId + "/generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityId": "%s", "saveToDocuments": false, "acknowledgeWarnings": false}
                    """
                        .formatted(testProjectId)))
        .andExpect(status().isOk());

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var docs =
                      generatedDocumentRepository
                          .findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
                              TemplateEntityType.PROJECT, testProjectId);
                  assertThat(docs).isNotEmpty();
                  var doc = docs.getFirst();
                  assertThat(doc.getContextSnapshot()).isNotNull();
                  assertThat(doc.getContextSnapshot()).containsKey("template_name");
                  assertThat(doc.getContextSnapshot()).containsKey("entity_type");
                }));
  }

  @Test
  void shouldGenerateCorrectFilenameFormat() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/templates/" + testTemplateId + "/generate")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"entityId": "%s", "saveToDocuments": false, "acknowledgeWarnings": false}
                        """
                            .formatted(testProjectId)))
            .andExpect(status().isOk())
            .andReturn();

    String contentDisposition = result.getResponse().getHeader("Content-Disposition");
    assertThat(contentDisposition).contains("gen-template-");
    String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    assertThat(contentDisposition).contains(today);
    assertThat(contentDisposition).endsWith(".pdf\"");
  }

  @Test
  void shouldReturn401WithoutAuth() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + testTemplateId + "/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityId": "%s", "saveToDocuments": false, "acknowledgeWarnings": false}
                    """
                        .formatted(testProjectId)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturn404ForMissingTemplate() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + UUID.randomUUID() + "/generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityId": "%s", "saveToDocuments": false, "acknowledgeWarnings": false}
                    """
                        .formatted(testProjectId)))
        .andExpect(status().isNotFound());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_docgen_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberIdOwner))
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
