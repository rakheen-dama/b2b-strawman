package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
class TemplateValidationIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_template_validation_test";

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
  private UUID templateWithRequiredFieldsId;
  private UUID templateWithMissingFieldId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Template Validation Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner = syncMember(ORG_ID, "user_tv_owner", "tv_owner@test.com", "TV Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      new Project(
                          "Validation Test Project",
                          "For validation tests",
                          UUID.fromString(memberIdOwner));
                  project = projectRepository.save(project);
                  testProjectId = project.getId();

                  // Template that requires project.name (will be present)
                  var t1 =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Template With All Fields",
                          "template-with-all-fields",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          "<h1 th:text=\"${project.name}\">Name</h1>");
                  t1.setRequiredContextFields(
                      List.of(Map.of("entity", "project", "field", "name")));
                  t1 = documentTemplateRepository.save(t1);
                  templateWithRequiredFieldsId = t1.getId();

                  // Template that requires project.nonexistent_field (will be absent)
                  var t2 =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Template With Missing Field",
                          "template-with-missing-field",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          "<h1>Test</h1>");
                  t2.setRequiredContextFields(
                      List.of(Map.of("entity", "project", "field", "nonexistent_field")));
                  t2 = documentTemplateRepository.save(t2);
                  templateWithMissingFieldId = t2.getId();
                }));
  }

  @Test
  void validate_template_with_all_required_fields_present() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + templateWithRequiredFieldsId + "/preview")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityId\": \"%s\"}".formatted(testProjectId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validationResult.allPresent").value(true));
  }

  @Test
  void validate_template_with_missing_field() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + templateWithMissingFieldId + "/preview")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityId\": \"%s\"}".formatted(testProjectId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validationResult.allPresent").value(false))
        .andExpect(jsonPath("$.validationResult.fields[0].present").value(false));
  }

  @Test
  void preview_returns_validation_result() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + templateWithRequiredFieldsId + "/preview")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityId\": \"%s\"}".formatted(testProjectId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.html").exists())
        .andExpect(jsonPath("$.validationResult").exists());
  }

  @Test
  void generate_returns_422_when_fields_missing_and_not_acknowledged() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + templateWithMissingFieldId + "/generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"entityId\": \"%s\", \"saveToDocuments\": false, \"acknowledgeWarnings\": false}"
                        .formatted(testProjectId)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void generate_succeeds_when_acknowledged() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + templateWithMissingFieldId + "/generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"entityId\": \"%s\", \"saveToDocuments\": false, \"acknowledgeWarnings\": true}"
                        .formatted(testProjectId)))
        .andExpect(status().isOk());

    // Verify warnings were saved on GeneratedDocument
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var docs =
                      generatedDocumentRepository
                          .findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
                              TemplateEntityType.PROJECT, testProjectId);
                  var withWarnings =
                      docs.stream()
                          .filter(d -> d.getWarnings() != null && !d.getWarnings().isEmpty())
                          .findFirst();
                  assertThat(withWarnings).isPresent();
                }));
  }

  @Test
  void generate_succeeds_when_all_fields_present() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + templateWithRequiredFieldsId + "/generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"entityId\": \"%s\", \"saveToDocuments\": false, \"acknowledgeWarnings\": false}"
                        .formatted(testProjectId)))
        .andExpect(status().isOk());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_tv_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
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
