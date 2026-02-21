package io.b2mash.b2b.b2bstrawman.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V35MigrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_v35_migration_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ReportDefinitionRepository reportDefinitionRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "V35 Migration Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember(ORG_ID, "user_v35_owner", "v35_owner@test.com", "V35 Owner", "owner");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void saveAndRetrieveReportDefinition() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var definition =
                      new ReportDefinition(
                          "Test Report",
                          "test-report",
                          "TEST_CATEGORY",
                          Map.of(
                              "parameters",
                              List.of(
                                  Map.of(
                                      "name",
                                      "dateFrom",
                                      "type",
                                      "date",
                                      "label",
                                      "From Date",
                                      "required",
                                      true))),
                          Map.of(
                              "columns",
                              List.of(
                                  Map.of("key", "col1", "label", "Column 1", "type", "string"))),
                          "<html><body>Test template</body></html>");
                  definition = reportDefinitionRepository.saveAndFlush(definition);

                  var found = reportDefinitionRepository.findById(definition.getId()).orElseThrow();
                  assertThat(found.getName()).isEqualTo("Test Report");
                  assertThat(found.getSlug()).isEqualTo("test-report");
                  assertThat(found.getCategory()).isEqualTo("TEST_CATEGORY");
                  assertThat(found.getTemplateBody())
                      .isEqualTo("<html><body>Test template</body></html>");
                  assertThat(found.isSystem()).isTrue();
                  assertThat(found.getSortOrder()).isZero();
                  assertThat(found.getCreatedAt()).isNotNull();
                  assertThat(found.getUpdatedAt()).isNotNull();

                  // Verify JSONB round-trip
                  assertThat(found.getParameterSchema()).containsKey("parameters");
                  @SuppressWarnings("unchecked")
                  var params =
                      (List<Map<String, Object>>) found.getParameterSchema().get("parameters");
                  assertThat(params).hasSize(1);
                  assertThat(params.getFirst().get("name")).isEqualTo("dateFrom");

                  assertThat(found.getColumnDefinitions()).containsKey("columns");
                  @SuppressWarnings("unchecked")
                  var cols =
                      (List<Map<String, Object>>) found.getColumnDefinitions().get("columns");
                  assertThat(cols).hasSize(1);
                  assertThat(cols.getFirst().get("key")).isEqualTo("col1");
                }));
  }

  @Test
  void findBySlugReturnsCorrectDefinition() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var definition =
                      new ReportDefinition(
                          "Slug Lookup Report",
                          "slug-lookup-test",
                          "TEST_CATEGORY",
                          Map.of("parameters", List.of()),
                          Map.of("columns", List.of()),
                          "<html>slug test</html>");
                  reportDefinitionRepository.saveAndFlush(definition);

                  var found = reportDefinitionRepository.findBySlug("slug-lookup-test");
                  assertThat(found).isPresent();
                  assertThat(found.get().getName()).isEqualTo("Slug Lookup Report");

                  var notFound = reportDefinitionRepository.findBySlug("nonexistent");
                  assertThat(notFound).isEmpty();
                }));
  }

  @Test
  void slugUniquenessConstraintEnforced() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var definition1 =
                      new ReportDefinition(
                          "First Report",
                          "unique-slug-test",
                          "TEST_CATEGORY",
                          Map.of("parameters", List.of()),
                          Map.of("columns", List.of()),
                          "<html>first</html>");
                  reportDefinitionRepository.saveAndFlush(definition1);
                }));

    assertThatThrownBy(
            () ->
                runInTenant(
                    () ->
                        transactionTemplate.executeWithoutResult(
                            tx -> {
                              var definition2 =
                                  new ReportDefinition(
                                      "Duplicate Slug Report",
                                      "unique-slug-test",
                                      "OTHER_CATEGORY",
                                      Map.of("parameters", List.of()),
                                      Map.of("columns", List.of()),
                                      "<html>duplicate</html>");
                              reportDefinitionRepository.saveAndFlush(definition2);
                            })))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void reportPackStatusRoundTripsOnOrgSettings() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant();
                  assertThat(settings).isPresent();
                  // report_pack_status already has standard-reports from provisioning seeder
                  assertThat(settings.get().getReportPackStatus()).isNotNull();
                  int initialSize = settings.get().getReportPackStatus().size();
                  assertThat(settings.get().getReportPackStatus())
                      .anyMatch(entry -> "standard-reports".equals(entry.get("packId")));

                  // Record an additional pack application and verify round-trip
                  settings.get().recordReportPackApplication("custom-reports", 1);
                  orgSettingsRepository.saveAndFlush(settings.get());

                  var updated = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  assertThat(updated.getReportPackStatus()).isNotNull();
                  assertThat(updated.getReportPackStatus()).hasSize(initialSize + 1);
                  assertThat(updated.getReportPackStatus())
                      .anyMatch(entry -> "custom-reports".equals(entry.get("packId")));
                }));
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
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
