package io.b2mash.b2b.b2bstrawman.projecttemplate;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.CreateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.SaveFromProjectRequest;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.TemplateTaskRequest;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.UpdateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.schedule.RecurringSchedule;
import io.b2mash.b2b.b2bstrawman.schedule.RecurringScheduleRepository;
import io.b2mash.b2b.b2bstrawman.tag.Tag;
import io.b2mash.b2b.b2bstrawman.tag.TagRepository;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
class ProjectTemplateServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_template_svc_test";

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
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private ProjectMemberRepository projectMemberRepository;
  @Autowired private RecurringScheduleRepository scheduleRepository;
  @Autowired private CustomerRepository customerRepository;

  private String tenantSchema;
  private UUID memberId;
  private UUID secondMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Template Service Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_svc_owner", "svc_owner@test.com", "Svc Owner", "owner"));
    secondMemberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_svc_member", "svc_member@test.com", "Svc Member", "member"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void createTemplate_withTasksAndTags_persistsAll() {
    runInTenant(
        () -> {
          var tag =
              transactionTemplate.execute(
                  tx -> tagRepository.saveAndFlush(new Tag("bookkeeping", "#3B82F6")));

          var request =
              new CreateTemplateRequest(
                  "Monthly Bookkeeping",
                  "Monthly Bookkeeping - {customer} - {month} {year}",
                  "Standard monthly template",
                  true,
                  List.of(
                      new TemplateTaskRequest(
                          "Collect docs", null, BigDecimal.valueOf(2), 0, true, "ANY_MEMBER", null),
                      new TemplateTaskRequest(
                          "Process", null, BigDecimal.valueOf(4), 1, true, "ANY_MEMBER", null)),
                  List.of(tag.getId()));

          var response = templateService.create(request, memberId);

          assertThat(response.id()).isNotNull();
          assertThat(response.name()).isEqualTo("Monthly Bookkeeping");
          assertThat(response.source()).isEqualTo("MANUAL");
          assertThat(response.active()).isTrue();
          assertThat(response.taskCount()).isEqualTo(2);
          assertThat(response.tagCount()).isEqualTo(1);
          assertThat(response.tasks()).hasSize(2);
          assertThat(response.tasks().get(0).name()).isEqualTo("Collect docs");
          assertThat(response.tasks().get(1).name()).isEqualTo("Process");
        });
  }

  @Test
  void createTemplate_noTasksOrTags_succeeds() {
    runInTenant(
        () -> {
          var request =
              new CreateTemplateRequest(
                  "Empty Template", "{customer}", null, false, List.of(), List.of());

          var response = templateService.create(request, memberId);

          assertThat(response.id()).isNotNull();
          assertThat(response.name()).isEqualTo("Empty Template");
          assertThat(response.taskCount()).isEqualTo(0);
          assertThat(response.tagCount()).isEqualTo(0);
          assertThat(response.tasks()).isEmpty();
          assertThat(response.tags()).isEmpty();
        });
  }

  @Test
  void updateTemplate_replacesTasksAndTags() {
    runInTenant(
        () -> {
          var createReq =
              new CreateTemplateRequest(
                  "Update Test",
                  "{customer}",
                  null,
                  false,
                  List.of(
                      new TemplateTaskRequest(
                          "Old Task", null, null, 0, false, "UNASSIGNED", null)),
                  List.of());
          var created = templateService.create(createReq, memberId);

          var updateReq =
              new UpdateTemplateRequest(
                  "Update Test Updated",
                  "{customer} Updated",
                  "New desc",
                  true,
                  List.of(
                      new TemplateTaskRequest(
                          "New Task 1", null, null, 0, true, "ANY_MEMBER", null),
                      new TemplateTaskRequest(
                          "New Task 2", null, null, 1, true, "PROJECT_LEAD", null)),
                  List.of());
          var updated = templateService.update(created.id(), updateReq);

          assertThat(updated.name()).isEqualTo("Update Test Updated");
          assertThat(updated.description()).isEqualTo("New desc");
          assertThat(updated.billableDefault()).isTrue();
          assertThat(updated.tasks()).hasSize(2);
          assertThat(updated.tasks().stream().map(t -> t.name()))
              .containsExactly("New Task 1", "New Task 2");
        });
  }

  @Test
  void deleteTemplate_noSchedules_succeeds() {
    runInTenant(
        () -> {
          var request =
              new CreateTemplateRequest(
                  "To Delete", "{customer}", null, false, List.of(), List.of());
          var created = templateService.create(request, memberId);

          templateService.delete(created.id());

          assertThatThrownBy(() -> templateService.get(created.id()))
              .isInstanceOf(ResourceNotFoundException.class);
        });
  }

  @Test
  void deleteTemplate_withActiveSchedule_throws409() {
    runInTenant(
        () -> {
          // Setup: create template + schedule in one transaction
          UUID templateId =
              transactionTemplate.execute(
                  tx -> {
                    var template =
                        templateRepository.saveAndFlush(
                            new ProjectTemplate(
                                "Protected Template",
                                "{customer}",
                                null,
                                false,
                                "MANUAL",
                                null,
                                memberId));

                    var customer =
                        customerRepository.saveAndFlush(
                            createActiveCustomer("Schedule Customer", "cust@test.com", memberId));

                    scheduleRepository.saveAndFlush(
                        new RecurringSchedule(
                            template.getId(),
                            customer.getId(),
                            null,
                            "MONTHLY",
                            LocalDate.now(),
                            null,
                            0,
                            null,
                            memberId));

                    return template.getId();
                  });

          // Assert: delete should throw (in its own transaction via @Transactional)
          assertThatThrownBy(() -> templateService.delete(templateId))
              .isInstanceOf(ResourceConflictException.class);
        });
  }

  @Test
  void duplicateTemplate_createsIndependentCopy() {
    runInTenant(
        () -> {
          var tag =
              transactionTemplate.execute(
                  tx -> tagRepository.saveAndFlush(new Tag("dup-tag", "#10B981")));

          var request =
              new CreateTemplateRequest(
                  "Original",
                  "{customer}",
                  "Desc",
                  true,
                  List.of(
                      new TemplateTaskRequest("Task A", null, null, 0, true, "ANY_MEMBER", null)),
                  List.of(tag.getId()));
          var original = templateService.create(request, memberId);

          var copy = templateService.duplicate(original.id(), memberId);

          assertThat(copy.name()).isEqualTo("Original (Copy)");
          assertThat(copy.source()).isEqualTo("MANUAL");
          assertThat(copy.id()).isNotEqualTo(original.id());
          assertThat(copy.tasks()).hasSize(1);
          assertThat(copy.tasks().get(0).name()).isEqualTo("Task A");
          assertThat(copy.tags()).hasSize(1);
          assertThat(copy.tags().get(0).id()).isEqualTo(tag.getId());

          // Verify source unchanged
          var sourceCheck = templateService.get(original.id());
          assertThat(sourceCheck.name()).isEqualTo("Original");
        });
  }

  @Test
  void saveFromProject_withTaskSelection_ordersCorrectly() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      projectRepository.saveAndFlush(
                          new Project("Order Test Project", null, memberId));

                  var task1 =
                      taskRepository.saveAndFlush(
                          new Task(
                              project.getId(), "First Task", null, "MEDIUM", null, null, memberId));
                  var task2 =
                      taskRepository.saveAndFlush(
                          new Task(
                              project.getId(),
                              "Second Task",
                              null,
                              "MEDIUM",
                              null,
                              null,
                              memberId));
                  var task3 =
                      taskRepository.saveAndFlush(
                          new Task(
                              project.getId(), "Third Task", null, "MEDIUM", null, null, memberId));

                  // Request tasks in reverse order
                  var request =
                      new SaveFromProjectRequest(
                          "From Project Template",
                          "{customer}",
                          null,
                          List.of(task3.getId(), task1.getId(), task2.getId()),
                          List.of(),
                          null);

                  var response =
                      templateService.saveFromProject(project.getId(), request, memberId, "owner");

                  assertThat(response.source()).isEqualTo("FROM_PROJECT");
                  assertThat(response.sourceProjectId()).isEqualTo(project.getId());
                  assertThat(response.tasks()).hasSize(3);
                  assertThat(response.tasks().get(0).name()).isEqualTo("Third Task");
                  assertThat(response.tasks().get(1).name()).isEqualTo("First Task");
                  assertThat(response.tasks().get(2).name()).isEqualTo("Second Task");
                }));
  }

  @Test
  void saveFromProject_withRoleMapping_assignsRoles() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      projectRepository.saveAndFlush(
                          new Project("Role Test Project", null, memberId));

                  var task1 =
                      taskRepository.saveAndFlush(
                          new Task(
                              project.getId(),
                              "Task With Role",
                              null,
                              "MEDIUM",
                              null,
                              null,
                              memberId));
                  var task2 =
                      taskRepository.saveAndFlush(
                          new Task(
                              project.getId(),
                              "Task Without Role",
                              null,
                              "MEDIUM",
                              null,
                              null,
                              memberId));

                  var roleMap = Map.of(task1.getId(), "PROJECT_LEAD");
                  var request =
                      new SaveFromProjectRequest(
                          "Role Template",
                          "{customer}",
                          null,
                          List.of(task1.getId(), task2.getId()),
                          List.of(),
                          roleMap);

                  var response =
                      templateService.saveFromProject(project.getId(), request, memberId, "admin");

                  assertThat(response.tasks().get(0).assigneeRole()).isEqualTo("PROJECT_LEAD");
                  assertThat(response.tasks().get(1).assigneeRole()).isEqualTo("UNASSIGNED");
                }));
  }

  @Test
  void saveFromProject_withInvalidTaskId_skipsUnknownTasks() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      projectRepository.saveAndFlush(
                          new Project("Skip Test Project", null, memberId));

                  var task1 =
                      taskRepository.saveAndFlush(
                          new Task(
                              project.getId(), "Valid Task", null, "MEDIUM", null, null, memberId));

                  var request =
                      new SaveFromProjectRequest(
                          "Skip Template",
                          "{customer}",
                          null,
                          List.of(task1.getId(), UUID.randomUUID(), UUID.randomUUID()),
                          List.of(),
                          null);

                  var response =
                      templateService.saveFromProject(project.getId(), request, memberId, "owner");

                  // Only the valid task should be included
                  assertThat(response.tasks()).hasSize(1);
                  assertThat(response.tasks().get(0).name()).isEqualTo("Valid Task");
                }));
  }

  @Test
  void listActive_returnsOnlyActiveTemplates() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var t1 =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Active A List", "{c}", null, false, "MANUAL", null, memberId));
                  var t2 =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Active B List", "{c}", null, false, "MANUAL", null, memberId));
                  var t3 =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Inactive C List", "{c}", null, false, "MANUAL", null, memberId));
                  t3.deactivate();
                  templateRepository.saveAndFlush(t3);

                  var activeList = templateService.listActive();
                  var activeIds = activeList.stream().map(r -> r.id()).toList();

                  assertThat(activeIds).contains(t1.getId(), t2.getId());
                  assertThat(activeIds).doesNotContain(t3.getId());
                }));
  }

  @Test
  void get_existingTemplate_returnsWithTasksAndTags() {
    runInTenant(
        () -> {
          var tag =
              transactionTemplate.execute(
                  tx -> tagRepository.saveAndFlush(new Tag("get-tag", "#FF0000")));

          var request =
              new CreateTemplateRequest(
                  "Get Test Template",
                  "{customer}",
                  "Test desc",
                  true,
                  List.of(
                      new TemplateTaskRequest(
                          "Get Task", "desc", BigDecimal.valueOf(1), 0, true, "UNASSIGNED", null)),
                  List.of(tag.getId()));
          var created = templateService.create(request, memberId);

          var retrieved = templateService.get(created.id());

          assertThat(retrieved.name()).isEqualTo("Get Test Template");
          assertThat(retrieved.description()).isEqualTo("Test desc");
          assertThat(retrieved.tasks()).hasSize(1);
          assertThat(retrieved.tasks().get(0).name()).isEqualTo("Get Task");
          assertThat(retrieved.tasks().get(0).description()).isEqualTo("desc");
          assertThat(retrieved.tags()).hasSize(1);
          assertThat(retrieved.tags().get(0).id()).isEqualTo(tag.getId());
        });
  }

  @Test
  void saveFromProject_byProjectLead_succeeds() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      projectRepository.saveAndFlush(
                          new Project("Lead Test Project", null, memberId));

                  var task1 =
                      taskRepository.saveAndFlush(
                          new Task(
                              project.getId(), "Lead Task", null, "MEDIUM", null, null, memberId));

                  // Add secondMemberId as project lead
                  projectMemberRepository.saveAndFlush(
                      new ProjectMember(project.getId(), secondMemberId, "lead", memberId));

                  var request =
                      new SaveFromProjectRequest(
                          "Lead Template",
                          "{customer}",
                          null,
                          List.of(task1.getId()),
                          List.of(),
                          null);

                  // Call as member (not admin/owner) who is project lead
                  var response =
                      templateService.saveFromProject(
                          project.getId(), request, secondMemberId, "member");

                  assertThat(response.name()).isEqualTo("Lead Template");
                  assertThat(response.tasks()).hasSize(1);
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private void runInTenantAs(UUID asMemberId, String asRole, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, asMemberId)
        .where(RequestScopes.ORG_ROLE, asRole)
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
