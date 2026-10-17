package io.b2mash.b2b.b2bstrawman.projecttemplate;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.tag.Tag;
import io.b2mash.b2b.b2bstrawman.tag.TagRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.util.UUID;
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
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V30MigrationTest {
  private static final String ORG_ID = "org_v30_migration_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectTemplateRepository projectTemplateRepository;
  @Autowired private TemplateTaskRepository templateTaskRepository;
  @Autowired private TemplateTagRepository templateTagRepository;
  @Autowired private TagRepository tagRepository;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "V30 Migration Test Org", null);

    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_v30_owner", "v30_owner@test.com", "V30 Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void saveAndRetrieveProjectTemplate() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ProjectTemplate(
                          "Monthly Audit",
                          "{customer} - Monthly Audit {month}",
                          "Standard monthly audit template",
                          true,
                          "MANUAL",
                          null,
                          memberId);
                  template = projectTemplateRepository.saveAndFlush(template);

                  var found = projectTemplateRepository.findById(template.getId()).orElseThrow();
                  assertThat(found.getName()).isEqualTo("Monthly Audit");
                  assertThat(found.getNamePattern())
                      .isEqualTo("{customer} - Monthly Audit {month}");
                  assertThat(found.isActive()).isTrue();
                  assertThat(found.isBillableDefault()).isTrue();
                  assertThat(found.getSource()).isEqualTo("MANUAL");
                  assertThat(found.getCreatedBy()).isEqualTo(memberId);
                }));
  }

  @Test
  void saveAndRetrieveTemplateTask() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ProjectTemplate(
                          "Task Test Template",
                          "{customer} - Task Test",
                          null,
                          false,
                          "MANUAL",
                          null,
                          memberId);
                  template = projectTemplateRepository.saveAndFlush(template);

                  var task =
                      new TemplateTask(
                          template.getId(),
                          "Review documents",
                          "Review all submitted documents",
                          new BigDecimal("2.50"),
                          1,
                          true,
                          "PROJECT_LEAD");
                  task = templateTaskRepository.saveAndFlush(task);

                  var found = templateTaskRepository.findById(task.getId()).orElseThrow();
                  assertThat(found.getTemplateId()).isEqualTo(template.getId());
                  assertThat(found.getName()).isEqualTo("Review documents");
                  assertThat(found.getEstimatedHours())
                      .isEqualByComparingTo(new BigDecimal("2.50"));
                  assertThat(found.getSortOrder()).isEqualTo(1);
                  assertThat(found.isBillable()).isTrue();
                  assertThat(found.getAssigneeRole()).isEqualTo("PROJECT_LEAD");
                }));
  }

  @Test
  void saveAndRetrieveTemplateTag() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ProjectTemplate(
                          "Tag Test Template",
                          "{customer} - Tag Test",
                          null,
                          true,
                          "MANUAL",
                          null,
                          memberId);
                  template = projectTemplateRepository.saveAndFlush(template);

                  var tag = new Tag("audit", "#3B82F6");
                  tag = tagRepository.saveAndFlush(tag);

                  templateTagRepository.save(template.getId(), tag.getId());

                  var tagIds = templateTagRepository.findTagIdsByTemplateId(template.getId());
                  assertThat(tagIds).containsExactly(tag.getId());
                }));
  }

  @Test
  void cascadeDeleteRemovesTasksAndTags() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ProjectTemplate(
                          "Cascade Delete Template",
                          "{customer} - Cascade",
                          null,
                          true,
                          "MANUAL",
                          null,
                          memberId);
                  template = projectTemplateRepository.saveAndFlush(template);

                  var task =
                      new TemplateTask(
                          template.getId(), "Cascade task", null, null, 1, true, "UNASSIGNED");
                  templateTaskRepository.saveAndFlush(task);

                  var tag = new Tag("cascade-tag", "#EF4444");
                  tag = tagRepository.saveAndFlush(tag);
                  templateTagRepository.save(template.getId(), tag.getId());

                  // Verify data exists
                  assertThat(
                          templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId()))
                      .hasSize(1);
                  assertThat(templateTagRepository.findTagIdsByTemplateId(template.getId()))
                      .hasSize(1);

                  // Delete the template — CASCADE should remove tasks and tags
                  projectTemplateRepository.delete(template);
                  projectTemplateRepository.flush();

                  assertThat(
                          templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId()))
                      .isEmpty();
                  assertThat(templateTagRepository.findTagIdsByTemplateId(template.getId()))
                      .isEmpty();
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
