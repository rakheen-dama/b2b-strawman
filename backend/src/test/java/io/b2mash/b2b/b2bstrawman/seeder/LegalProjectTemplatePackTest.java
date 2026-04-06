package io.b2mash.b2b.b2bstrawman.seeder;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplate;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.TemplateTask;
import io.b2mash.b2b.b2bstrawman.projecttemplate.TemplateTaskRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegalProjectTemplatePackTest {

  private static final String ORG_ID = "org_legal_pt_pack_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectTemplateRepository projectTemplateRepository;
  @Autowired private TemplateTaskRepository templateTaskRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Legal PT Pack Test Org", "legal-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void createsFourLegalTemplates() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = projectTemplateRepository.findAllByOrderByNameAsc();
                  var seederTemplates =
                      templates.stream().filter(t -> "SEEDER".equals(t.getSource())).toList();
                  assertThat(seederTemplates).hasSize(4);

                  var names = seederTemplates.stream().map(ProjectTemplate::getName).toList();
                  assertThat(names)
                      .containsExactlyInAnyOrder(
                          "Litigation (Personal Injury / General)",
                          "Deceased Estate Administration",
                          "Collections (Debt Recovery)",
                          "Commercial (Corporate & Contract)");
                }));
  }

  @Test
  void eachTemplateHasNineTasks() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = projectTemplateRepository.findAllByOrderByNameAsc();
                  var seederTemplates =
                      templates.stream().filter(t -> "SEEDER".equals(t.getSource())).toList();
                  for (var template : seederTemplates) {
                    var tasks =
                        templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId());
                    assertThat(tasks)
                        .as("Template '%s' should have 9 tasks", template.getName())
                        .hasSize(9);
                  }
                }));
  }

  @Test
  void sortOrdersAreOneToNine() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = projectTemplateRepository.findAllByOrderByNameAsc();
                  var seederTemplates =
                      templates.stream().filter(t -> "SEEDER".equals(t.getSource())).toList();
                  for (var template : seederTemplates) {
                    var tasks =
                        templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId());
                    var sortOrders = tasks.stream().map(TemplateTask::getSortOrder).toList();
                    assertThat(sortOrders)
                        .as("Sort orders for '%s'", template.getName())
                        .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
                  }
                }));
  }

  @Test
  void assigneeRolesMatchSpec() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = projectTemplateRepository.findAllByOrderByNameAsc();
                  var seederTemplates =
                      templates.stream().filter(t -> "SEEDER".equals(t.getSource())).toList();
                  for (var template : seederTemplates) {
                    var tasks =
                        templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId());
                    assertThat(tasks)
                        .allSatisfy(
                            task -> {
                              assertThat(task.getAssigneeRole())
                                  .as(
                                      "Task '%s' in '%s' should have valid assignee role",
                                      task.getName(), template.getName())
                                  .isIn("PROJECT_LEAD", "ANY_MEMBER", "UNASSIGNED");
                            });
                  }
                }));
  }

  @Test
  void litigationTemplateHasCorrectTasks() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      projectTemplateRepository.findAllByOrderByNameAsc().stream()
                          .filter(t -> "Litigation (Personal Injury / General)".equals(t.getName()))
                          .findFirst()
                          .orElseThrow();
                  var tasks =
                      templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId());
                  assertThat(tasks.get(0).getName())
                      .isEqualTo("Initial consultation & case assessment");
                  assertThat(tasks.get(0).getAssigneeRole()).isEqualTo("PROJECT_LEAD");
                  assertThat(tasks.get(4).getName())
                      .isEqualTo("Discovery -- request & exchange documents");
                  assertThat(tasks.get(4).getAssigneeRole()).isEqualTo("ANY_MEMBER");
                  assertThat(tasks.get(8).getName()).isEqualTo("Execution -- warrant / attachment");
                  assertThat(tasks.get(8).getAssigneeRole()).isEqualTo("ANY_MEMBER");
                }));
  }

  @Test
  void allTasksAreBillable() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = projectTemplateRepository.findAllByOrderByNameAsc();
                  var seederTemplates =
                      templates.stream().filter(t -> "SEEDER".equals(t.getSource())).toList();
                  for (var template : seederTemplates) {
                    var tasks =
                        templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId());
                    assertThat(tasks)
                        .as("All tasks in '%s' should be billable", template.getName())
                        .allSatisfy(task -> assertThat(task.isBillable()).isTrue());
                  }
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
