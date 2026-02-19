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

  @Test
  void generatesPdfFromFullHtmlTemplate() {
    // Simulate a real seeded template (full HTML document with map-based context)
    String fullTemplate =
        """
        <!DOCTYPE html>
        <html xmlns:th="http://www.thymeleaf.org">
        <head><title>Test</title></head>
        <body>
          <div class="header">
            <img th:if="${org.logoUrl}" th:src="${org.logoUrl}" alt="Logo" style="max-height: 60px;"/>
            <h1 th:text="${org.name}">Org Name</h1>
          </div>
          <div class="content">
            <h2>Engagement Letter</h2>
            <p>Dear <span th:text="${customer.name}">Customer</span>,</p>
            <p>Project: <span th:text="${project.name}">Project</span></p>
          </div>
        </body>
        </html>
        """;

    // Build context with LinkedHashMaps (same as real context builders)
    var org = new java.util.LinkedHashMap<String, Object>();
    org.put("name", "Test Org");
    org.put("logoUrl", null);
    org.put("documentFooterText", null);

    var project = new java.util.LinkedHashMap<String, Object>();
    project.put("name", "Test Project");

    var customer = new java.util.LinkedHashMap<String, Object>();
    customer.put("name", "Test Customer");

    var context = new java.util.HashMap<String, Object>();
    context.put("org", org);
    context.put("project", project);
    context.put("customer", customer);
    context.put("generatedAt", java.time.Instant.now().toString());

    String rendered = pdfRenderingService.renderThymeleaf(fullTemplate, context);
    String fullHtml = pdfRenderingService.wrapHtml(rendered, "h1 { color: blue; }");

    // Verify no double-wrapping
    assertThat(fullHtml.indexOf("<!DOCTYPE")).isEqualTo(fullHtml.lastIndexOf("<!DOCTYPE"));
    assertThat(fullHtml).contains("Test Org");
    assertThat(fullHtml).contains("Test Customer");
    assertThat(fullHtml).contains("Test Project");
    assertThat(fullHtml).doesNotContain("________");

    // Verify PDF generation succeeds
    byte[] pdf = pdfRenderingService.htmlToPdf(fullHtml);
    assertThat(pdf).isNotEmpty();
    assertThat(pdf[0]).isEqualTo((byte) '%');
    assertThat(pdf[1]).isEqualTo((byte) 'P');
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
