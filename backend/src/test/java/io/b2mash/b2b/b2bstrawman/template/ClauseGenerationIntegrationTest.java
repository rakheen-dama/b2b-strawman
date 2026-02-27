package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import io.b2mash.b2b.b2bstrawman.clause.TemplateClause;
import io.b2mash.b2b.b2bstrawman.clause.TemplateClauseRepository;
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
class ClauseGenerationIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_clause_gen_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private TemplateClauseRepository templateClauseRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String memberIdOwner;
  private UUID testProjectId;
  private UUID testTemplateId;
  private UUID clauseId1;
  private UUID clauseId2;
  private UUID requiredClauseId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Clause Gen Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(
            ORG_ID, "user_clausegen_owner", "clausegen_owner@test.com", "ClauseGen Owner", "owner");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create project
                  var project =
                      new Project(
                          "Clause Gen Project",
                          "For clause generation tests",
                          UUID.fromString(memberIdOwner));
                  project = projectRepository.save(project);
                  testProjectId = project.getId();

                  // Create template with clause placeholder
                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Clause Gen Template",
                          "clause-gen-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          "<h1 th:text=\"${project.name}\">Name</h1>"
                              + "<div th:utext=\"${clauseBlock}\"></div>"
                              + "<p>Footer</p>");
                  template = documentTemplateRepository.save(template);
                  testTemplateId = template.getId();

                  // Create clauses
                  var clause1 =
                      new Clause(
                          "Test Confidentiality",
                          "test-gen-confidentiality",
                          "<p>The parties agree to keep information confidential.</p>",
                          "General");
                  clause1 = clauseRepository.save(clause1);
                  clauseId1 = clause1.getId();

                  var clause2 =
                      new Clause(
                          "Liability Limitation",
                          "liability-limitation",
                          "<p>Liability is limited to the contract value.</p>",
                          "Legal");
                  clause2 = clauseRepository.save(clause2);
                  clauseId2 = clause2.getId();

                  var requiredClause =
                      new Clause(
                          "Governing Law",
                          "governing-law",
                          "<p>This agreement is governed by South African law.</p>",
                          "Legal");
                  requiredClause = clauseRepository.save(requiredClause);
                  requiredClauseId = requiredClause.getId();

                  // Associate clauses with template (clause1 optional, requiredClause required)
                  templateClauseRepository.save(
                      new TemplateClause(testTemplateId, clauseId1, 0, false));
                  templateClauseRepository.save(
                      new TemplateClause(testTemplateId, requiredClauseId, 1, true));
                }));
  }

  @Test
  void shouldGenerateWithExplicitClauses() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + testTemplateId + "/generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityId": "%s",
                      "saveToDocuments": false,
                      "acknowledgeWarnings": false,
                      "clauses": [
                        {"clauseId": "%s", "sortOrder": 0},
                        {"clauseId": "%s", "sortOrder": 1},
                        {"clauseId": "%s", "sortOrder": 2}
                      ]
                    }
                    """
                        .formatted(testProjectId, clauseId1, clauseId2, requiredClauseId)))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"));
  }

  @Test
  void shouldGenerateWithDefaultClauses() throws Exception {
    // Omit clauses field — template has associations, so defaults should be used
    mockMvc
        .perform(
            post("/api/templates/" + testTemplateId + "/generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityId": "%s",
                      "saveToDocuments": false,
                      "acknowledgeWarnings": false
                    }
                    """
                        .formatted(testProjectId)))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"));
  }

  @Test
  void shouldGenerateWithoutClausesBackwardCompatible() throws Exception {
    // Create a template with NO clause associations
    final UUID[] noClauseTemplateId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "No Clause Template",
                          "no-clause-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          "<h1 th:text=\"${project.name}\">Name</h1><p>Simple</p>");
                  template = documentTemplateRepository.save(template);
                  noClauseTemplateId[0] = template.getId();
                }));

    mockMvc
        .perform(
            post("/api/templates/" + noClauseTemplateId[0] + "/generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityId": "%s",
                      "saveToDocuments": false,
                      "acknowledgeWarnings": false
                    }
                    """
                        .formatted(testProjectId)))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"));
  }

  @Test
  void shouldReturn422WhenRequiredClauseMissing() throws Exception {
    // Provide only the optional clause, omit the required one
    mockMvc
        .perform(
            post("/api/templates/" + testTemplateId + "/generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityId": "%s",
                      "saveToDocuments": false,
                      "acknowledgeWarnings": false,
                      "clauses": [
                        {"clauseId": "%s", "sortOrder": 0}
                      ]
                    }
                    """
                        .formatted(testProjectId, clauseId1)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void shouldReturn400WhenInvalidClauseId() throws Exception {
    var fakeId = UUID.randomUUID();
    mockMvc
        .perform(
            post("/api/templates/" + testTemplateId + "/generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityId": "%s",
                      "saveToDocuments": false,
                      "acknowledgeWarnings": false,
                      "clauses": [
                        {"clauseId": "%s", "sortOrder": 0},
                        {"clauseId": "%s", "sortOrder": 1}
                      ]
                    }
                    """
                        .formatted(testProjectId, requiredClauseId, fakeId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldPopulateClauseSnapshots() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + testTemplateId + "/generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityId": "%s",
                      "saveToDocuments": true,
                      "acknowledgeWarnings": false,
                      "clauses": [
                        {"clauseId": "%s", "sortOrder": 0},
                        {"clauseId": "%s", "sortOrder": 1}
                      ]
                    }
                    """
                        .formatted(testProjectId, clauseId1, requiredClauseId)))
        .andExpect(status().isCreated());

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
                  assertThat(doc.getClauseSnapshots()).isNotNull();
                  assertThat(doc.getClauseSnapshots()).hasSize(2);

                  var firstSnapshot = doc.getClauseSnapshots().get(0);
                  assertThat(firstSnapshot).containsKey("clauseId");
                  assertThat(firstSnapshot).containsKey("slug");
                  assertThat(firstSnapshot).containsKey("title");
                  assertThat(firstSnapshot).containsKey("sortOrder");
                }));
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_clausegen_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
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
