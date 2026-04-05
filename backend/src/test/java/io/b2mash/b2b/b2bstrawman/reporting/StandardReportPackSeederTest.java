package io.b2mash.b2b.b2bstrawman.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StandardReportPackSeederTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_rps_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private StandardReportPackSeeder standardReportPackSeeder;
  @Autowired private ReportDefinitionRepository reportDefinitionRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Report Pack Seeder Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_rps_owner", "rps_owner@test.com", "RPS Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void seedCreatesThreeReportDefinitions() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var definitions =
                      reportDefinitionRepository.findAllByOrderByCategoryAscSortOrderAsc();
                  assertThat(definitions).hasSize(3);
                }));
  }

  @Test
  void seedCreatesCorrectSlugs() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var definitions =
                      reportDefinitionRepository.findAllByOrderByCategoryAscSortOrderAsc();
                  var slugs = definitions.stream().map(ReportDefinition::getSlug).toList();
                  assertThat(slugs)
                      .containsExactlyInAnyOrder(
                          "timesheet", "invoice-aging", "project-profitability");
                }));
  }

  @Test
  void seedCreatesCorrectCategories() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var definitions =
                      reportDefinitionRepository.findAllByOrderByCategoryAscSortOrderAsc();
                  var categories = definitions.stream().map(ReportDefinition::getCategory).toList();
                  assertThat(categories)
                      .containsExactlyInAnyOrder("TIME_ATTENDANCE", "FINANCIAL", "PROJECT");
                }));
  }

  @Test
  void idempotencyDoesNotDuplicate() {
    // Call seeder again (already seeded during provisioning)
    standardReportPackSeeder.seedForTenant(tenantSchema, ORG_ID);

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var definitions =
                      reportDefinitionRepository.findAllByOrderByCategoryAscSortOrderAsc();
                  assertThat(definitions).hasSize(3);
                }));
  }

  @Test
  void reportPackStatusRecordedInOrgSettings() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant();
                  assertThat(settings).isPresent();
                  assertThat(settings.get().getReportPackStatus()).isNotNull();
                  assertThat(settings.get().getReportPackStatus())
                      .anyMatch(
                          entry ->
                              StandardReportPackSeeder.PACK_ID.equals(entry.get("packId"))
                                  && Integer.valueOf(StandardReportPackSeeder.PACK_VERSION)
                                      .equals(entry.get("version")));
                }));
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }
}
