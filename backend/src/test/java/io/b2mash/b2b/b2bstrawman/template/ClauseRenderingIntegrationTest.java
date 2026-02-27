package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.clause.Clause;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClauseRenderingIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_clause_render_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private PdfRenderingService pdfRenderingService;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID testProjectId;
  private UUID templateWithPlaceholderId;
  private UUID templateWithoutPlaceholderId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Clause Render Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_clause_owner", "clause_owner@test.com", "Clause Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      new Project(
                          "Clause Test Project", "A project for clause rendering", memberIdOwner);
                  project = projectRepository.save(project);
                  testProjectId = project.getId();

                  // Template WITH ${clauses} placeholder
                  var templateWith =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Template With Clauses",
                          "template-with-clauses",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          "<h1 th:text=\"${project.name}\">Name</h1>\n<div th:utext=\"${clauses}\"></div>");
                  templateWith = documentTemplateRepository.save(templateWith);
                  templateWithPlaceholderId = templateWith.getId();

                  // Template WITHOUT ${clauses} placeholder
                  var templateWithout =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Template Without Clauses",
                          "template-without-clauses",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          "<h1 th:text=\"${project.name}\">Name</h1>");
                  templateWithout = documentTemplateRepository.save(templateWithout);
                  templateWithoutPlaceholderId = templateWithout.getId();
                }));
  }

  @Test
  void generatePdf_withClauses_injectsClauseHtml() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clauses = buildTestClauses();
                  var result =
                      pdfRenderingService.generatePdf(
                          templateWithPlaceholderId, testProjectId, memberIdOwner, clauses);

                  assertThat(result.htmlPreview()).contains("clause-block");
                  assertThat(result.htmlPreview()).contains("data-clause-slug=\"confidentiality\"");
                  assertThat(result.htmlPreview())
                      .contains("The parties agree to keep confidential");
                  assertThat(result.pdfBytes()).isNotEmpty();
                }));
  }

  @Test
  void generatePdf_withoutClauses_worksNormally() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var result =
                      pdfRenderingService.generatePdf(
                          templateWithPlaceholderId, testProjectId, memberIdOwner, List.of());

                  assertThat(result.htmlPreview()).contains("Clause Test Project");
                  assertThat(result.htmlPreview()).doesNotContain("clause-block");
                  assertThat(result.pdfBytes()).isNotEmpty();
                }));
  }

  @Test
  void generatePdf_templateWithoutPlaceholder_appendsFallbackSection() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clauses = buildTestClauses();
                  var result =
                      pdfRenderingService.generatePdf(
                          templateWithoutPlaceholderId, testProjectId, memberIdOwner, clauses);

                  assertThat(result.htmlPreview()).contains("clauses-section");
                  assertThat(result.htmlPreview()).contains("Terms and Conditions");
                  assertThat(result.htmlPreview()).contains("clause-block");
                }));
  }

  @Test
  void previewHtml_withClauses_rendersClauseContent() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clauses = buildTestClauses();
                  String html =
                      pdfRenderingService.previewHtml(
                          templateWithPlaceholderId, testProjectId, memberIdOwner, clauses);

                  assertThat(html).contains("data-clause-slug=\"confidentiality\"");
                  assertThat(html).contains("The parties agree to keep confidential");
                }));
  }

  @Test
  void renderFragment_processesThymeleafExpressions() {
    String fragment = "<p th:text=\"${name}\">placeholder</p>";
    var context = Map.<String, Object>of("name", "Test Value");

    String result = pdfRenderingService.renderFragment(fragment, context);

    assertThat(result).contains("Test Value");
    assertThat(result).doesNotContain("placeholder");
    // Should NOT contain html/body wrapper
    assertThat(result).doesNotContain("<html>");
    assertThat(result).doesNotContain("<body>");
  }

  @Test
  void generatePdf_clauseWithDangerousContent_throwsSecurityException() {
    runInTenant(
        () -> {
          var dangerousClause =
              new Clause(
                  "Dangerous", "dangerous", "<p>${#ctx.getBean('dataSource')}</p>", "General");

          assertThatThrownBy(
                  () ->
                      pdfRenderingService.generatePdf(
                          templateWithPlaceholderId,
                          testProjectId,
                          memberIdOwner,
                          List.of(dangerousClause)))
              .isInstanceOf(TemplateSecurityException.class);
        });
  }

  // --- Helpers ---

  private List<Clause> buildTestClauses() {
    return List.of(
        new Clause(
            "Confidentiality",
            "confidentiality",
            "<p>The parties agree to keep confidential all information.</p>",
            "General"),
        new Clause(
            "Termination",
            "termination",
            "<p>Either party may terminate with 30 days notice.</p>",
            "Legal"));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
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
