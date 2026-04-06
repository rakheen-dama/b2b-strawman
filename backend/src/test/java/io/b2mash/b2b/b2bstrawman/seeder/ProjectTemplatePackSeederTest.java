package io.b2mash.b2b.b2bstrawman.seeder;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.TemplateTaskRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectTemplatePackSeederTest {

  private static final String ORG_ID = "org_project_template_pack_seeder_test";
  private static final String NON_LEGAL_ORG_ID = "org_ptps_non_legal_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private ProjectTemplateRepository projectTemplateRepository;
  @Autowired private TemplateTaskRepository templateTaskRepository;
  @Autowired private ProjectTemplatePackSeeder projectTemplatePackSeeder;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String nonLegalTenantSchema;

  @BeforeAll
  void setup() {
    // Provision with legal-za profile — project template pack should be applied
    provisioningService.provisionTenant(ORG_ID, "Legal Template Pack Test Org", "legal-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Provision with null profile for vertical filtering test
    provisioningService.provisionTenant(NON_LEGAL_ORG_ID, "Non-Legal Template Pack Test Org", null);
    nonLegalTenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(NON_LEGAL_ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  @Order(1)
  void createsTemplatesAndTasksFromLegalPack() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = projectTemplateRepository.findAllByOrderByNameAsc();
                  var seederTemplates =
                      templates.stream().filter(t -> "SEEDER".equals(t.getSource())).toList();
                  // The legal-za pack defines 4 templates
                  assertThat(seederTemplates).hasSize(4);
                  // Each template should have tasks
                  for (var template : seederTemplates) {
                    var tasks =
                        templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId());
                    assertThat(tasks)
                        .as("Template '%s' should have 9 tasks", template.getName())
                        .hasSize(9);
                    // Verify sort orders are 1-9
                    for (int i = 0; i < tasks.size(); i++) {
                      assertThat(tasks.get(i).getSortOrder()).isEqualTo(i + 1);
                    }
                  }
                  // Verify all templates are active
                  assertThat(seederTemplates).allSatisfy(t -> assertThat(t.isActive()).isTrue());
                  // Verify createdBy is the seeder sentinel
                  assertThat(seederTemplates)
                      .allSatisfy(
                          t ->
                              assertThat(t.getCreatedBy())
                                  .isEqualTo(ProjectTemplatePackSeeder.SEEDER_CREATED_BY));
                }));
  }

  @Test
  @Order(2)
  void isIdempotent() {
    // Running the seeder again should not create duplicates
    projectTemplatePackSeeder.seedPacksForTenant(tenantSchema, ORG_ID);
    runInTenant(
        tenantSchema,
        ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = projectTemplateRepository.findAllByOrderByNameAsc();
                  var seederTemplates =
                      templates.stream().filter(t -> "SEEDER".equals(t.getSource())).toList();
                  // Still 4 templates — not 8
                  assertThat(seederTemplates).hasSize(4);
                  // OrgSettings should have the pack recorded
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  assertThat(settings.getProjectTemplatePackStatus()).isNotNull();
                  assertThat(settings.getProjectTemplatePackStatus())
                      .anyMatch(entry -> "legal-za-project-templates".equals(entry.get("packId")));
                }));
  }

  @Test
  @Order(3)
  void skipsPacksForWrongVerticalProfile() {
    runInTenant(
        nonLegalTenantSchema,
        NON_LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = projectTemplateRepository.findAllByOrderByNameAsc();
                  var seederTemplates =
                      templates.stream().filter(t -> "SEEDER".equals(t.getSource())).toList();
                  // Non-legal tenant should NOT have legal templates
                  assertThat(seederTemplates).isEmpty();
                }));
  }

  private void runInTenant(String schema, String orgId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
