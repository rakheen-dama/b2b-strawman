package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class PdfRenderingServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_pdf_render_test";

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
  private UUID testTemplateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "PDF Render Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_pdf_owner", "pdf_owner@test.com", "PDF Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create test project and template within tenant
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      new Project(
                          "Rendering Test Project", "A project for PDF tests", memberIdOwner);
                  project = projectRepository.save(project);
                  testProjectId = project.getId();

                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Test PDF Template",
                          "test-pdf-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          "<h1 th:text=\"${project.name}\">Project Name</h1><p th:text=\"${generatedAt}\">Date</p>");
                  template.setCss("h1 { color: blue; }");
                  template = documentTemplateRepository.save(template);
                  testTemplateId = template.getId();
                }));
  }

  @Test
  void generatesPdfWithNonZeroBytes() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var result =
                      pdfRenderingService.generatePdf(testTemplateId, testProjectId, memberIdOwner);

                  assertThat(result).isNotNull();
                  assertThat(result.pdfBytes()).isNotEmpty();
                  // PDF magic bytes: %PDF-
                  assertThat(result.pdfBytes()[0]).isEqualTo((byte) '%');
                  assertThat(result.pdfBytes()[1]).isEqualTo((byte) 'P');
                  assertThat(result.pdfBytes()[2]).isEqualTo((byte) 'D');
                  assertThat(result.pdfBytes()[3]).isEqualTo((byte) 'F');
                }));
  }

  @Test
  void htmlPreviewContainsEntityData() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var result =
                      pdfRenderingService.generatePdf(testTemplateId, testProjectId, memberIdOwner);

                  assertThat(result.htmlPreview()).contains("Rendering Test Project");
                  assertThat(result.htmlPreview()).contains("<!DOCTYPE html>");
                  assertThat(result.htmlPreview()).contains("<style>");
                }));
  }

  @Test
  void cssMergingIncludesBothDefaultAndCustom() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var result =
                      pdfRenderingService.generatePdf(testTemplateId, testProjectId, memberIdOwner);

                  // Default CSS includes @page rule
                  assertThat(result.htmlPreview()).contains("@page");
                  // Custom CSS from template
                  assertThat(result.htmlPreview()).contains("color: blue;");
                }));
  }

  @Test
  void filenameMatchesExpectedPattern() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var result =
                      pdfRenderingService.generatePdf(testTemplateId, testProjectId, memberIdOwner);

                  // Pattern: {slug}-{entity-name-slugified}-{yyyy-MM-dd}.pdf
                  assertThat(result.fileName()).startsWith("test-pdf-template-");
                  assertThat(result.fileName()).contains("rendering-test-project");
                  assertThat(result.fileName()).endsWith(LocalDate.now() + ".pdf");
                }));
  }

  @Test
  void renderThymeleafProcessesVariables() {
    var context = java.util.Map.<String, Object>of("name", "Test Value", "count", 42);
    String result =
        pdfRenderingService.renderThymeleaf("<p th:text=\"${name}\">placeholder</p>", context);
    assertThat(result).contains("Test Value");
    assertThat(result).doesNotContain("placeholder");
  }

  // --- Helpers ---

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
