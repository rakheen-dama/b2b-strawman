package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskFieldPackSeedingIntegrationTest {

  private static final String ORG_ID = "org_task_pack_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private FieldGroupRepository fieldGroupRepository;
  @Autowired private FieldGroupMemberRepository fieldGroupMemberRepository;
  @Autowired private FieldPackSeeder fieldPackSeeder;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Task Pack Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void task_pack_seeds_group_with_auto_apply() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var taskGroups =
                      fieldGroupRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.TASK);
                  var taskPackGroups =
                      taskGroups.stream().filter(g -> "common-task".equals(g.getPackId())).toList();

                  assertThat(taskPackGroups).hasSize(1);
                  var group = taskPackGroups.getFirst();
                  assertThat(group.getSlug()).isEqualTo("task_info");
                  assertThat(group.getName()).isEqualTo("Task Info");
                  assertThat(group.isAutoApply()).isTrue();
                  assertThat(group.getEntityType()).isEqualTo(EntityType.TASK);
                }));
  }

  @Test
  void task_pack_seeds_category_field_only() {
    // Post-Epic-462: common-task only seeds `category`. `priority` and `estimated_hours` were
    // removed (priority is covered by Project promoted column; estimated_hours is reserved for a
    // future structural column on Task).
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var taskFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.TASK);
                  var taskPackFields =
                      taskFields.stream().filter(f -> "common-task".equals(f.getPackId())).toList();

                  assertThat(taskPackFields).hasSize(1);

                  var slugs = taskPackFields.stream().map(FieldDefinition::getSlug).toList();
                  assertThat(slugs).containsExactly("category");

                  var category = taskPackFields.getFirst();
                  assertThat(category.getFieldType()).isEqualTo(FieldType.TEXT);

                  // Verify group membership (1 member in the task_info group)
                  var taskGroup =
                      fieldGroupRepository
                          .findByEntityTypeAndSlug(EntityType.TASK, "task_info")
                          .orElseThrow();
                  var members =
                      fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(
                          taskGroup.getId());
                  assertThat(members).hasSize(1);
                }));
  }

  @Test
  void task_pack_idempotent() {
    // Call seeder a second time — must produce no duplicates
    fieldPackSeeder.seedPacksForTenant(tenantSchema, ORG_ID);

    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var taskGroups =
                      fieldGroupRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.TASK);
                  var taskPackGroups =
                      taskGroups.stream().filter(g -> "common-task".equals(g.getPackId())).toList();
                  assertThat(taskPackGroups).hasSize(1);

                  var taskFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.TASK);
                  var taskPackFields =
                      taskFields.stream().filter(f -> "common-task".equals(f.getPackId())).toList();
                  assertThat(taskPackFields).hasSize(1);
                }));
  }

  private void runInTenant(String schema, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
