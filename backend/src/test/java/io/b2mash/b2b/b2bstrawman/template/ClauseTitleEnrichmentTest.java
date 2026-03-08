package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.LinkedHashMap;
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

/**
 * Tests that clause titles embedded in template clauseBlock nodes are enriched with current library
 * titles at read time (GAP-9).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClauseTitleEnrichmentTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_clause_enrich_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String memberIdOwner;
  private UUID templateId;
  private UUID clauseId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Clause Enrich Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(
            ORG_ID,
            "user_clause_enrich_owner",
            "clause_enrich_owner@test.com",
            "Clause Enrich Owner",
            "owner");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create a clause with original title
                  var clause =
                      new Clause(
                          "Original Title",
                          "clause-enrich-test",
                          Map.of("type", "doc", "content", List.of()),
                          "General");
                  clause = clauseRepository.save(clause);
                  clauseId = clause.getId();

                  // Create a template with a clauseBlock referencing that clause
                  // The clauseBlock attrs snapshot the title at insertion time
                  var clauseBlockAttrs = new LinkedHashMap<String, Object>();
                  clauseBlockAttrs.put("clauseId", clauseId.toString());
                  clauseBlockAttrs.put("title", "Original Title");
                  clauseBlockAttrs.put("required", false);

                  var clauseBlockNode = new LinkedHashMap<String, Object>();
                  clauseBlockNode.put("type", "clauseBlock");
                  clauseBlockNode.put("attrs", clauseBlockAttrs);
                  clauseBlockNode.put("content", List.of());

                  var content = new LinkedHashMap<String, Object>();
                  content.put("type", "doc");
                  content.put("content", List.of(clauseBlockNode));

                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Enrich Test Template",
                          "enrich-test-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          content);
                  template = documentTemplateRepository.save(template);
                  templateId = template.getId();
                }));

    // Now update the clause title (simulating a library edit after template insertion)
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clause = clauseRepository.findById(clauseId).orElseThrow();
                  clause.update(
                      "Updated Title",
                      clause.getSlug(),
                      clause.getDescription(),
                      clause.getBody(),
                      clause.getCategory());
                  clauseRepository.save(clause);
                }));
  }

  @Test
  void shouldEnrichClauseTitleWithCurrentLibraryTitle() throws Exception {
    var result =
        mockMvc
            .perform(get("/api/templates/" + templateId).with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    String json = result.getResponse().getContentAsString();

    // The clauseBlock title in the response should reflect the updated library title,
    // not the stale snapshotted title
    String enrichedTitle = JsonPath.read(json, "$.content.content[0].attrs.title");
    assertThat(enrichedTitle).isEqualTo("Updated Title");
  }

  @Test
  void shouldNotMutateStoredContent() throws Exception {
    // Fetch via API (triggers enrichment)
    mockMvc
        .perform(get("/api/templates/" + templateId).with(ownerJwt()))
        .andExpect(status().isOk());

    // Verify the stored content still has the old snapshotted title
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template = documentTemplateRepository.findById(templateId).orElseThrow();
                  @SuppressWarnings("unchecked")
                  var content = template.getContent();
                  @SuppressWarnings("unchecked")
                  var nodes = (List<Map<String, Object>>) content.get("content");
                  @SuppressWarnings("unchecked")
                  var attrs = (Map<String, Object>) nodes.getFirst().get("attrs");
                  // The stored title should still be the original snapshotted value
                  assertThat(attrs.get("title")).isEqualTo("Original Title");
                }));
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_clause_enrich_owner")
                    .claim("o", Map.of("id", ORG_ID, "rol", "owner")))
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
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/internal/members/sync")
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
