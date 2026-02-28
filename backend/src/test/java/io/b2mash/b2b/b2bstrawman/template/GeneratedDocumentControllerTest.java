package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class GeneratedDocumentControllerTest {

  private static final Map<String, Object> CONTENT = Map.of("type", "doc", "content", List.of());

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_gen_doc_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String memberIdOwner;
  private String memberIdMember;
  private UUID testProjectId;
  private UUID testTemplateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "GenDoc Controller Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(
            ORG_ID,
            "user_gendocctrl_owner",
            "gendocctrl_owner@test.com",
            "GenDocCtrl Owner",
            "owner");

    memberIdMember =
        syncMember(
            ORG_ID,
            "user_gendocctrl_member",
            "gendocctrl_member@test.com",
            "GenDocCtrl Member",
            "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      new Project(
                          "GenDocCtrl Test Project",
                          "For controller tests",
                          UUID.fromString(memberIdOwner));
                  project = projectRepository.save(project);
                  testProjectId = project.getId();

                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "GenDocCtrl Template",
                          "gendocctrl-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          CONTENT);
                  template = documentTemplateRepository.save(template);
                  testTemplateId = template.getId();
                }));

    // Generate a document so we have data for list/download/delete tests
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
        .andExpect(status().isCreated());
  }

  @Test
  void shouldListGeneratedDocumentsByEntity() throws Exception {
    mockMvc
        .perform(
            get("/api/generated-documents")
                .with(ownerJwt())
                .param("entityType", "PROJECT")
                .param("entityId", testProjectId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].templateName").value("GenDocCtrl Template"))
        .andExpect(jsonPath("$[0].fileName").exists())
        .andExpect(jsonPath("$[0].generatedByName").value("GenDocCtrl Owner"));
  }

  @Test
  void shouldDownloadGeneratedDocument() throws Exception {
    // First get the generated doc ID from list
    var listResult =
        mockMvc
            .perform(
                get("/api/generated-documents")
                    .with(ownerJwt())
                    .param("entityType", "PROJECT")
                    .param("entityId", testProjectId.toString()))
            .andExpect(status().isOk())
            .andReturn();

    String generatedDocId = JsonPath.read(listResult.getResponse().getContentAsString(), "$[0].id");

    // Download returns 302 redirect to presigned URL
    mockMvc
        .perform(get("/api/generated-documents/" + generatedDocId + "/download").with(ownerJwt()))
        .andExpect(status().is(302));
  }

  @Test
  void adminCanDeleteGeneratedDocument() throws Exception {
    // Generate a new document to delete
    var genResult =
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

    // Get the generated doc from the list (last one created)
    var listResult =
        mockMvc
            .perform(
                get("/api/generated-documents")
                    .with(ownerJwt())
                    .param("entityType", "PROJECT")
                    .param("entityId", testProjectId.toString()))
            .andExpect(status().isOk())
            .andReturn();

    String content = listResult.getResponse().getContentAsString();
    List<String> ids = JsonPath.read(content, "$[*].id");
    String latestDocId = ids.getFirst();

    mockMvc
        .perform(delete("/api/generated-documents/" + latestDocId).with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void memberCannotDeleteGeneratedDocument() throws Exception {
    // Generate a new document
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

    var listResult =
        mockMvc
            .perform(
                get("/api/generated-documents")
                    .with(ownerJwt())
                    .param("entityType", "PROJECT")
                    .param("entityId", testProjectId.toString()))
            .andExpect(status().isOk())
            .andReturn();

    String content = listResult.getResponse().getContentAsString();
    List<String> ids = JsonPath.read(content, "$[*].id");
    String latestDocId = ids.getFirst();

    mockMvc
        .perform(delete("/api/generated-documents/" + latestDocId).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldIncludeTemplateNameAndGeneratedByInList() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/generated-documents")
                    .with(ownerJwt())
                    .param("entityType", "PROJECT")
                    .param("entityId", testProjectId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].templateName").isNotEmpty())
            .andExpect(jsonPath("$[0].generatedByName").isNotEmpty())
            .andExpect(jsonPath("$[0].fileSize").isNumber())
            .andExpect(jsonPath("$[0].primaryEntityType").value("PROJECT"))
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    String templateName = JsonPath.read(responseBody, "$[0].templateName");
    assertThat(templateName).isEqualTo("GenDocCtrl Template");
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_gendocctrl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_gendocctrl_member")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
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
