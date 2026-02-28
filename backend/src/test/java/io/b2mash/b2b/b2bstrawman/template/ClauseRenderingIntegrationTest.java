package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
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
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID testProjectId;
  private UUID templateWithClauseBlockId;
  private UUID templateWithoutClauseBlockId;
  private Clause confidentialityClause;
  private Clause terminationClause;

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

                  // Create clauses with Tiptap JSON bodies
                  var confBody =
                      TestDocumentBuilder.doc()
                          .paragraph("The parties agree to keep confidential")
                          .build();
                  confidentialityClause =
                      clauseRepository.save(
                          new Clause(
                              "Confidentiality", "cri-confidentiality", confBody, "General"));

                  var termBody =
                      TestDocumentBuilder.doc().paragraph("Either party may terminate").build();
                  terminationClause =
                      clauseRepository.save(
                          new Clause("Termination", "cri-termination", termBody, "Legal"));

                  // Template with clauseBlock nodes referencing the clauses
                  var contentWithClauses =
                      TestDocumentBuilder.doc()
                          .heading(1, "Agreement")
                          .variable("project.name")
                          .clauseBlock(
                              confidentialityClause.getId(),
                              "cri-confidentiality",
                              "Confidentiality",
                              true)
                          .clauseBlock(
                              terminationClause.getId(), "cri-termination", "Termination", false)
                          .build();
                  var templateWith =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Template With Clauses",
                          "template-with-clauses",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          contentWithClauses);
                  templateWith = documentTemplateRepository.save(templateWith);
                  templateWithClauseBlockId = templateWith.getId();

                  // Template without clauseBlock nodes
                  var contentWithout =
                      TestDocumentBuilder.doc()
                          .heading(1, "Simple Document")
                          .variable("project.name")
                          .build();
                  var templateWithout =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Template Without Clauses",
                          "template-without-clauses",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          contentWithout);
                  templateWithout = documentTemplateRepository.save(templateWithout);
                  templateWithoutClauseBlockId = templateWithout.getId();
                }));
  }

  @Test
  void generatePdf_withClauses_injectsClauseHtml() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clauses = List.of(confidentialityClause, terminationClause);
                  var result =
                      pdfRenderingService.generatePdf(
                          templateWithClauseBlockId, testProjectId, memberIdOwner, clauses);

                  assertThat(result.htmlPreview()).contains("clause-block");
                  assertThat(result.htmlPreview())
                      .contains("data-clause-slug=\"cri-confidentiality\"");
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
                          templateWithClauseBlockId, testProjectId, memberIdOwner, List.of());

                  assertThat(result.htmlPreview()).contains("Clause Test Project");
                  // clauseBlock nodes render as comments when no clause found
                  assertThat(result.htmlPreview()).doesNotContain("class=\"clause-block\"");
                  assertThat(result.pdfBytes()).isNotEmpty();
                }));
  }

  @Test
  void previewHtml_withClauses_rendersClauseContent() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clauses = List.of(confidentialityClause, terminationClause);
                  String html =
                      pdfRenderingService.previewHtml(
                          templateWithClauseBlockId, testProjectId, memberIdOwner, clauses);

                  assertThat(html).contains("data-clause-slug=\"cri-confidentiality\"");
                  assertThat(html).contains("The parties agree to keep confidential");
                  assertThat(html).contains("data-clause-slug=\"cri-termination\"");
                  assertThat(html).contains("Either party may terminate");
                }));
  }

  @Test
  void previewHtml_withoutClauseBlock_rendersNormally() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  String html =
                      pdfRenderingService.previewHtml(
                          templateWithoutClauseBlockId, testProjectId, memberIdOwner);

                  assertThat(html).contains("Simple Document");
                  assertThat(html).contains("Clause Test Project");
                  assertThat(html).doesNotContain("clause-block");
                }));
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
