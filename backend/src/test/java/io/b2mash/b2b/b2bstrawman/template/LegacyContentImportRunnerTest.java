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
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
class LegacyContentImportRunnerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_legacy_import_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentTemplateRepository templateRepository;
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private LegacyContentImportRunner runner;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Legacy Import Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_legacy_owner", "legacy_owner@test.com", "Legacy Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  @SuppressWarnings("unchecked")
  void converts_simple_legacy_html_in_templates_and_clauses() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Insert a template with legacyHtml content
                  var legacyContent = buildLegacyHtmlDoc("<p>Hello <strong>world</strong></p>");
                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Legacy Test Template",
                          "legacy-test-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          legacyContent);
                  templateRepository.save(template);

                  // Insert a clause with legacyHtml body
                  var legacyBody = buildLegacyHtmlDoc("<p>Clause <em>content</em></p>");
                  var clause = new Clause("Legacy Clause", "legacy-clause", legacyBody, "general");
                  clauseRepository.save(clause);
                }));

    // Run the import synchronously
    runner.runImport();

    // Verify template was converted
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      templateRepository.findBySlug("legacy-test-template").orElseThrow();
                  var content = template.getContent();
                  var contentList = (List<Map<String, Object>>) content.get("content");

                  // Should not contain legacyHtml nodes anymore
                  assertThat(contentList).noneMatch(n -> "legacyHtml".equals(n.get("type")));
                  // Should contain paragraph nodes
                  assertThat(contentList).anyMatch(n -> "paragraph".equals(n.get("type")));

                  // Verify clause was converted
                  var clause = clauseRepository.findBySlug("legacy-clause").orElseThrow();
                  var body = clause.getBody();
                  var bodyContent = (List<Map<String, Object>>) body.get("content");

                  assertThat(bodyContent).noneMatch(n -> "legacyHtml".equals(n.get("type")));
                  assertThat(bodyContent).anyMatch(n -> "paragraph".equals(n.get("type")));
                }));
  }

  @Test
  @SuppressWarnings("unchecked")
  void skips_complex_legacy_html_nodes() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Insert a template with complex legacyHtml (no complexity attr)
                  var complexContent =
                      buildComplexLegacyHtmlDoc("<div class=\"custom\">Complex</div>");
                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Complex Legacy Template",
                          "complex-legacy-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          complexContent);
                  templateRepository.save(template);
                }));

    runner.runImport();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      templateRepository.findBySlug("complex-legacy-template").orElseThrow();
                  var content = template.getContent();
                  var contentList = (List<Map<String, Object>>) content.get("content");

                  // Complex nodes should remain untouched
                  assertThat(contentList).anyMatch(n -> "legacyHtml".equals(n.get("type")));
                }));
  }

  @Test
  @SuppressWarnings("unchecked")
  void idempotent_second_run_is_no_op() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var legacyContent = buildLegacyHtmlDoc("<p>Idempotent test</p>");
                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.CUSTOMER,
                          "Idempotent Template",
                          "idempotent-template",
                          TemplateCategory.COVER_LETTER,
                          legacyContent);
                  templateRepository.save(template);
                }));

    // First run
    runner.runImport();

    // Capture state after first run
    final Map<String, Object>[] afterFirstRun = new Map[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template = templateRepository.findBySlug("idempotent-template").orElseThrow();
                  afterFirstRun[0] = template.getContent();
                }));

    // Second run
    runner.runImport();

    // Content should be identical after second run
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template = templateRepository.findBySlug("idempotent-template").orElseThrow();
                  assertThat(template.getContent()).isEqualTo(afterFirstRun[0]);
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }

  /** Build a doc node with a simple legacyHtml child (complexity=simple). */
  private Map<String, Object> buildLegacyHtmlDoc(String html) {
    var attrs = new LinkedHashMap<String, Object>();
    attrs.put("html", html);
    attrs.put("complexity", "simple");

    var legacyNode = new LinkedHashMap<String, Object>();
    legacyNode.put("type", "legacyHtml");
    legacyNode.put("attrs", attrs);

    var doc = new LinkedHashMap<String, Object>();
    doc.put("type", "doc");
    doc.put("content", List.of(legacyNode));
    return doc;
  }

  /** Build a doc node with a complex legacyHtml child (no complexity attr). */
  private Map<String, Object> buildComplexLegacyHtmlDoc(String html) {
    var attrs = new LinkedHashMap<String, Object>();
    attrs.put("html", html);

    var legacyNode = new LinkedHashMap<String, Object>();
    legacyNode.put("type", "legacyHtml");
    legacyNode.put("attrs", attrs);

    var doc = new LinkedHashMap<String, Object>();
    doc.put("type", "doc");
    doc.put("content", List.of(legacyNode));
    return doc;
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
