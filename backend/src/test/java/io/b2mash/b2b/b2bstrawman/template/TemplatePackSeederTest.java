package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemplatePackSeederTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_tps_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TemplatePackSeeder templatePackSeeder;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Template Pack Seeder Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember(ORG_ID, "user_tps_owner", "tps_owner@test.com", "TPS Owner", "owner");

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
                  var platformTemplates =
                      templates.stream()
                          .filter(t -> t.getSource() == TemplateSource.PLATFORM)
                          .toList();
                  assertThat(platformTemplates).hasSize(3);
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
                  var platformTemplates =
                      templates.stream()
                          .filter(t -> t.getSource() == TemplateSource.PLATFORM)
                          .toList();
                  assertThat(platformTemplates).hasSize(3);
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
                  var platformTemplates =
                      templates.stream()
                          .filter(t -> t.getSource() == TemplateSource.PLATFORM)
                          .toList();
                  assertThat(platformTemplates).allMatch(t -> "common".equals(t.getPackId()));
                  assertThat(platformTemplates).allMatch(t -> t.getPackTemplateKey() != null);

                  var keys =
                      platformTemplates.stream().map(DocumentTemplate::getPackTemplateKey).toList();
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
                            assertThat(t.getContentJson()).isNotNull();
                            assertThat(t.getContentJson()).containsEntry("type", "doc");
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
                            Map<String, Object> json = t.getContentJson();
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
