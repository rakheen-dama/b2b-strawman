package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplate;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.TemplateTask;
import io.b2mash.b2b.b2bstrawman.projecttemplate.TemplateTaskRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.seeder.ProjectTemplatePackDefinition;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for the {@code consulting-za} project template pack: asserts the pack JSON is
 * discovered by {@link io.b2mash.b2b.b2bstrawman.seeder.ProjectTemplatePackSeeder} and applied to a
 * tenant provisioned with the {@code consulting-za} vertical profile. Verifies 5 agency templates
 * seed with the expected task counts, that all tasks are billable, and that template-level {@code
 * matterType} values (representing the {@code campaign_type} custom-field default) round-trip from
 * the JSON resource.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsultingZaProjectTemplatePackTest {

  private static final String ORG_ID = "org_cz_pt_pack_test";

  private static final Map<String, Integer> EXPECTED_TASK_COUNTS =
      Map.of(
          "Website Design & Build", 9,
          "Social Media Management Retainer", 6,
          "Brand Identity", 9,
          "SEO Campaign", 7,
          "Content Marketing Retainer", 6);

  private static final Map<String, String> EXPECTED_MATTER_TYPES =
      Map.of(
          "Website Design & Build", "WEBSITE_BUILD",
          "Social Media Management Retainer", "SOCIAL_MEDIA_RETAINER",
          "Brand Identity", "BRAND_IDENTITY",
          "SEO Campaign", "SEO_CAMPAIGN",
          "Content Marketing Retainer", "CONTENT_MARKETING");

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectTemplateRepository projectTemplateRepository;
  @Autowired private TemplateTaskRepository templateTaskRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(
        ORG_ID, "Consulting ZA Project Template Pack Test Org", "consulting-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void seedsFiveConsultingTemplates() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var seederTemplates = seederTemplates();
                  assertThat(seederTemplates).hasSize(5);
                  var names = seederTemplates.stream().map(ProjectTemplate::getName).toList();
                  assertThat(names)
                      .containsExactlyInAnyOrder(
                          "Website Design & Build",
                          "Social Media Management Retainer",
                          "Brand Identity",
                          "SEO Campaign",
                          "Content Marketing Retainer");
                }));
  }

  @Test
  void eachTemplateHasExpectedTaskCount() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  for (var template : seederTemplates()) {
                    Integer expected = EXPECTED_TASK_COUNTS.get(template.getName());
                    assertThat(expected)
                        .as("Template '%s' is not in the expected-counts map", template.getName())
                        .isNotNull();
                    var tasks =
                        templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId());
                    assertThat(tasks)
                        .as("Template '%s' should have %d tasks", template.getName(), expected)
                        .hasSize(expected);
                  }
                }));
  }

  @Test
  void sortOrdersStartAtOneAndAreContiguous() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  for (var template : seederTemplates()) {
                    var tasks =
                        templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId());
                    var sortOrders = tasks.stream().map(TemplateTask::getSortOrder).toList();
                    var expected =
                        java.util.stream.IntStream.rangeClosed(1, tasks.size()).boxed().toList();
                    assertThat(sortOrders)
                        .as("Sort orders for '%s'", template.getName())
                        .containsExactlyElementsOf(expected);
                  }
                }));
  }

  @Test
  void allTasksAreBillable() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  for (var template : seederTemplates()) {
                    var tasks =
                        templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId());
                    assertThat(tasks)
                        .as("All tasks in '%s' should be billable", template.getName())
                        .allSatisfy(task -> assertThat(task.isBillable()).isTrue());
                  }
                }));
  }

  @Test
  void assigneeRolesAreValid() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  for (var template : seederTemplates()) {
                    var tasks =
                        templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId());
                    assertThat(tasks)
                        .allSatisfy(
                            task ->
                                assertThat(task.getAssigneeRole())
                                    .as(
                                        "Task '%s' in '%s' should have a valid assignee role",
                                        task.getName(), template.getName())
                                    .isIn("PROJECT_LEAD", "ANY_MEMBER", "UNASSIGNED"));
                  }
                }));
  }

  /**
   * Task 481.8 — verifies the "use template" custom-field default is declared in the pack JSON.
   * Since {@code ProjectTemplatePackSeeder.applyPack()} does not currently persist {@code
   * matterType} (nor propagate it to {@code Project.customFields} at instantiation), this test
   * asserts the declarative round-trip: each template's {@code matterType} in the JSON matches the
   * {@code campaign_type} enum value documented in Epic 480. Future wiring that reads {@code
   * TemplateEntry.matterType()} at instantiation will populate {@code customFields.campaign_type}
   * from these exact values.
   */
  @Test
  void templateMatterTypesMatchEpic480CampaignTypeEnum() throws Exception {
    ProjectTemplatePackDefinition pack;
    try (InputStream in =
        new ClassPathResource("project-template-packs/consulting-za.json").getInputStream()) {
      pack = objectMapper.readValue(in, ProjectTemplatePackDefinition.class);
    }

    assertThat(pack.packId()).isEqualTo("consulting-za-project-templates");
    assertThat(pack.verticalProfile()).isEqualTo("consulting-za");
    assertThat(pack.templates()).hasSize(5);

    for (var entry : pack.templates()) {
      String expectedMatterType = EXPECTED_MATTER_TYPES.get(entry.name());
      assertThat(expectedMatterType)
          .as("Template '%s' is not in the expected-matter-types map", entry.name())
          .isNotNull();
      assertThat(entry.matterType())
          .as("Template '%s' matterType should match Epic 480 campaign_type enum", entry.name())
          .isEqualTo(expectedMatterType);
    }
  }

  private List<ProjectTemplate> seederTemplates() {
    return projectTemplateRepository.findAllByOrderByNameAsc().stream()
        .filter(t -> "SEEDER".equals(t.getSource()))
        .filter(t -> EXPECTED_TASK_COUNTS.containsKey(t.getName()))
        .toList();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
