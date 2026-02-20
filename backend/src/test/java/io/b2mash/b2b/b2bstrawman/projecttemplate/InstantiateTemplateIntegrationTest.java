package io.b2mash.b2b.b2bstrawman.projecttemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.InstantiateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagRepository;
import io.b2mash.b2b.b2bstrawman.tag.Tag;
import io.b2mash.b2b.b2bstrawman.tag.TagRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import java.util.UUID;
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
class InstantiateTemplateIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_instantiate_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectTemplateService templateService;
  @Autowired private ProjectTemplateRepository templateRepository;
  @Autowired private TemplateTaskRepository templateTaskRepository;
  @Autowired private TemplateTagRepository templateTagRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private ProjectMemberRepository projectMemberRepository;
  @Autowired private EntityTagRepository entityTagRepository;

  private String tenantSchema;
  private UUID memberId;
  private UUID leadMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Instantiate Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_inst_owner", "inst_owner@test.com", "Owner", "owner"));
    leadMemberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_inst_lead", "inst_lead@test.com", "Lead", "member"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void instantiate_withNameProvided_usesExplicitName() {
    runInTenant(
        () -> {
          var template =
              transactionTemplate.execute(
                  tx ->
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Token Template",
                              "{customer} - {month}",
                              null,
                              true,
                              "MANUAL",
                              null,
                              memberId)));

          var request = new InstantiateTemplateRequest("My Explicit Name", null, null, null);
          var project = templateService.instantiateTemplate(template.getId(), request, memberId);

          assertThat(project.getName()).isEqualTo("My Explicit Name");
        });
  }

  @Test
  void instantiate_withoutName_resolvesNameTokens() {
    runInTenant(
        () -> {
          var customer =
              transactionTemplate.execute(
                  tx ->
                      customerRepository.saveAndFlush(
                          new Customer("Acme Corp", "acme@test.com", null, null, null, memberId)));

          var template =
              transactionTemplate.execute(
                  tx ->
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Token Template 2",
                              "{customer} - Monthly",
                              null,
                              true,
                              "MANUAL",
                              null,
                              memberId)));

          var request = new InstantiateTemplateRequest(null, customer.getId(), null, null);
          var project = templateService.instantiateTemplate(template.getId(), request, memberId);

          assertThat(project.getName()).contains("Acme Corp");
        });
  }

  @Test
  void instantiate_withCustomer_linksCustomerProject() {
    runInTenant(
        () -> {
          var customer =
              transactionTemplate.execute(
                  tx ->
                      customerRepository.saveAndFlush(
                          new Customer(
                              "Linked Customer", "linked@test.com", null, null, null, memberId)));
          var template =
              transactionTemplate.execute(
                  tx ->
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Link Test Template",
                              "{customer}",
                              null,
                              true,
                              "MANUAL",
                              null,
                              memberId)));

          var request = new InstantiateTemplateRequest(null, customer.getId(), null, null);
          var project = templateService.instantiateTemplate(template.getId(), request, memberId);

          var links = customerProjectRepository.findByProjectId(project.getId());
          assertThat(links).hasSize(1);
          assertThat(links.get(0).getCustomerId()).isEqualTo(customer.getId());
        });
  }

  @Test
  void instantiate_withoutCustomer_noCustomerProject() {
    runInTenant(
        () -> {
          var template =
              transactionTemplate.execute(
                  tx ->
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "No Customer Template",
                              "{customer}",
                              null,
                              true,
                              "MANUAL",
                              null,
                              memberId)));

          var request = new InstantiateTemplateRequest("No Customer Project", null, null, null);
          var project = templateService.instantiateTemplate(template.getId(), request, memberId);

          var links = customerProjectRepository.findByProjectId(project.getId());
          assertThat(links).isEmpty();
        });
  }

  @Test
  void instantiate_withProjectLead_createsProjectMember() {
    runInTenant(
        () -> {
          var template =
              transactionTemplate.execute(
                  tx ->
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Lead Template",
                              "{customer}",
                              null,
                              true,
                              "MANUAL",
                              null,
                              memberId)));

          var request = new InstantiateTemplateRequest("Lead Project", null, leadMemberId, null);
          var project = templateService.instantiateTemplate(template.getId(), request, memberId);

          var members = projectMemberRepository.findByProjectId(project.getId());
          assertThat(members).hasSize(1);
          assertThat(members.get(0).getMemberId()).isEqualTo(leadMemberId);
          assertThat(members.get(0).getProjectRole()).isEqualTo("LEAD");
        });
  }

  @Test
  void instantiate_tasksCreated_titlesMatchTemplateTaskNames() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Task Template", "{customer}", null, true, "MANUAL", null, memberId));
                  templateTaskRepository.saveAndFlush(
                      new TemplateTask(
                          template.getId(), "Collect Docs", null, null, 0, true, "UNASSIGNED"));
                  templateTaskRepository.saveAndFlush(
                      new TemplateTask(
                          template.getId(), "Review Files", null, null, 1, true, "UNASSIGNED"));

                  var request = new InstantiateTemplateRequest("Task Project", null, null, null);
                  var project =
                      templateService.instantiateTemplate(template.getId(), request, memberId);

                  var tasks = taskRepository.findByProjectId(project.getId());
                  assertThat(tasks).hasSize(2);
                  var titles = tasks.stream().map(t -> t.getTitle()).toList();
                  assertThat(titles).containsExactlyInAnyOrder("Collect Docs", "Review Files");
                }));
  }

  @Test
  void instantiate_tagsApplied_entityTagsCreated() {
    runInTenant(
        () -> {
          var tag =
              transactionTemplate.execute(
                  tx -> tagRepository.saveAndFlush(new Tag("inst-tag", "#FF0000")));

          var templateId =
              transactionTemplate.execute(
                  tx -> {
                    var template =
                        templateRepository.saveAndFlush(
                            new ProjectTemplate(
                                "Tag Template",
                                "{customer}",
                                null,
                                true,
                                "MANUAL",
                                null,
                                memberId));
                    templateTagRepository.save(template.getId(), tag.getId());
                    return template.getId();
                  });

          var request = new InstantiateTemplateRequest("Tagged Project", null, null, null);
          var project = templateService.instantiateTemplate(templateId, request, memberId);

          var entityTags =
              entityTagRepository.findByEntityTypeAndEntityId("PROJECT", project.getId());
          assertThat(entityTags).hasSize(1);
          assertThat(entityTags.get(0).getTagId()).isEqualTo(tag.getId());
        });
  }

  @Test
  void instantiate_projectLeadRole_assigneeIdSet() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Assignee Template",
                              "{customer}",
                              null,
                              true,
                              "MANUAL",
                              null,
                              memberId));
                  templateTaskRepository.saveAndFlush(
                      new TemplateTask(
                          template.getId(), "Lead Task", null, null, 0, true, "PROJECT_LEAD"));

                  var request =
                      new InstantiateTemplateRequest("Assignee Project", null, leadMemberId, null);
                  var project =
                      templateService.instantiateTemplate(template.getId(), request, memberId);

                  var tasks = taskRepository.findByProjectId(project.getId());
                  assertThat(tasks).hasSize(1);
                  assertThat(tasks.get(0).getAssigneeId()).isEqualTo(leadMemberId);
                  assertThat(tasks.get(0).getStatus())
                      .isEqualTo("OPEN"); // stays OPEN, not IN_PROGRESS
                }));
  }

  @Test
  void instantiate_inactiveTemplate_throwsInvalidState() {
    runInTenant(
        () -> {
          // Setup: create and deactivate template in its own transaction
          var templateId =
              transactionTemplate.execute(
                  tx -> {
                    var template =
                        templateRepository.saveAndFlush(
                            new ProjectTemplate(
                                "Inactive Template",
                                "{customer}",
                                null,
                                true,
                                "MANUAL",
                                null,
                                memberId));
                    template.deactivate();
                    templateRepository.saveAndFlush(template);
                    return template.getId();
                  });

          // Assert: call outside TransactionTemplate so rollback-only doesn't propagate
          var request = new InstantiateTemplateRequest("Should Fail", null, null, null);
          assertThatThrownBy(
                  () -> templateService.instantiateTemplate(templateId, request, memberId))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void instantiate_unknownTemplate_throwsNotFound() {
    runInTenant(
        () -> {
          var request = new InstantiateTemplateRequest("Should Fail", null, null, null);
          assertThatThrownBy(
                  () -> templateService.instantiateTemplate(UUID.randomUUID(), request, memberId))
              .isInstanceOf(ResourceNotFoundException.class);
        });
  }

  @Test
  void instantiate_anyMemberRole_assigneeNull() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Any Member Template",
                              "{customer}",
                              null,
                              true,
                              "MANUAL",
                              null,
                              memberId));
                  templateTaskRepository.saveAndFlush(
                      new TemplateTask(
                          template.getId(), "Open Task", null, null, 0, true, "ANY_MEMBER"));

                  var request =
                      new InstantiateTemplateRequest("Open Task Project", null, leadMemberId, null);
                  var project =
                      templateService.instantiateTemplate(template.getId(), request, memberId);

                  var tasks = taskRepository.findByProjectId(project.getId());
                  assertThat(tasks).hasSize(1);
                  assertThat(tasks.get(0).getAssigneeId()).isNull();
                }));
  }

  @Test
  void instantiate_descriptionOverride_usesRequestDescription() {
    runInTenant(
        () -> {
          var template =
              transactionTemplate.execute(
                  tx ->
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Desc Template",
                              "{customer}",
                              "Template Desc",
                              true,
                              "MANUAL",
                              null,
                              memberId)));

          var request = new InstantiateTemplateRequest("Desc Project", null, null, "Override Desc");
          var project = templateService.instantiateTemplate(template.getId(), request, memberId);

          assertThat(project.getDescription()).isEqualTo("Override Desc");
        });
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
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
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }
}
