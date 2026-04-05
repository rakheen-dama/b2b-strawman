package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
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
class TemplatePackSeederTest {
  private static final String ORG_ID = "org_tps_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TemplatePackSeeder templatePackSeeder;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Template Pack Seeder Test Org", null);

    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_tps_owner", "tps_owner@test.com", "TPS Owner", "owner");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void seedCreatesThreeTemplates() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                  var commonTemplates =
                      templates.stream().filter(t -> "common".equals(t.getPackId())).toList();
                  assertThat(commonTemplates).hasSize(3);
                }));
  }

  @Test
  void idempotencyDoesNotDuplicate() {
    // Seed again — should not create duplicates
    templatePackSeeder.seedPacksForTenant(tenantSchema, ORG_ID);

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                  var commonTemplates =
                      templates.stream().filter(t -> "common".equals(t.getPackId())).toList();
                  assertThat(commonTemplates).hasSize(3);
                }));
  }

  @Test
  void sourceSetToPlatform() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                  var platformTemplates =
                      templates.stream()
                          .filter(t -> t.getPackId() != null && t.getPackId().equals("common"))
                          .toList();
                  assertThat(platformTemplates)
                      .allMatch(t -> t.getSource() == TemplateSource.PLATFORM);
                }));
  }

  @Test
  void packIdAndKeySet() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                  var commonTemplates =
                      templates.stream().filter(t -> "common".equals(t.getPackId())).toList();
                  assertThat(commonTemplates).allMatch(t -> t.getPackTemplateKey() != null);

                  var keys =
                      commonTemplates.stream().map(DocumentTemplate::getPackTemplateKey).toList();
                  assertThat(keys)
                      .containsExactlyInAnyOrder(
                          "engagement-letter", "project-summary", "invoice-cover-letter");
                }));
  }

  @Test
  void templatePackStatusUpdatedInOrgSettings() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant();
                  assertThat(settings).isPresent();
                  assertThat(settings.get().getTemplatePackStatus()).isNotNull();
                  assertThat(settings.get().getTemplatePackStatus())
                      .anyMatch(entry -> "common".equals(entry.get("packId")));
                }));
  }

  @Test
  void seededContentJsonHasDocRootNode() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                  var platformTemplates =
                      templates.stream()
                          .filter(t -> t.getSource() == TemplateSource.PLATFORM)
                          .toList();
                  assertThat(platformTemplates).isNotEmpty();
                  assertThat(platformTemplates)
                      .allSatisfy(
                          t -> {
                            assertThat(t.getContent()).isNotNull();
                            assertThat(t.getContent()).containsEntry("type", "doc");
                          });
                }));
  }

  @SuppressWarnings("unchecked")
  @Test
  void seededContentJsonHasContentArray() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                  var platformTemplates =
                      templates.stream()
                          .filter(t -> t.getSource() == TemplateSource.PLATFORM)
                          .toList();
                  assertThat(platformTemplates).isNotEmpty();
                  assertThat(platformTemplates)
                      .allSatisfy(
                          t -> {
                            Map<String, Object> json = t.getContent();
                            assertThat(json).containsKey("content");
                            assertThat(json.get("content")).isInstanceOf(List.class);
                            List<Map<String, Object>> content =
                                (List<Map<String, Object>>) json.get("content");
                            assertThat(content).isNotEmpty();
                          });
                }));
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }
}
