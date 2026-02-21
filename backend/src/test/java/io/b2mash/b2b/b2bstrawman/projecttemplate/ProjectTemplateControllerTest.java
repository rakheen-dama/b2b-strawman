package io.b2mash.b2b.b2bstrawman.projecttemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.schedule.RecurringSchedule;
import io.b2mash.b2b.b2bstrawman.schedule.RecurringScheduleRepository;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectTemplateControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_template_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private ProjectMemberRepository projectMemberRepository;
  @Autowired private ProjectTemplateRepository templateRepository;
  @Autowired private RecurringScheduleRepository scheduleRepository;
  @Autowired private CustomerRepository customerRepository;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID adminMemberId;
  private UUID memberMemberId;
  private String createdTemplateId;
  private UUID projectId;
  private UUID taskId1;
  private UUID taskId2;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Template Controller Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    ownerMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_tmpl_ctrl_owner", "tmpl_ctrl_owner@test.com", "Owner", "owner"));
    adminMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_tmpl_ctrl_admin", "tmpl_ctrl_admin@test.com", "Admin", "admin"));
    memberMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_tmpl_ctrl_member", "tmpl_ctrl_member@test.com", "Member", "member"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create a project with tasks for saveFromProject tests
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var project =
                          projectRepository.saveAndFlush(
                              new Project("Controller Test Project", null, ownerMemberId));
                      projectId = project.getId();

                      var task1 =
                          taskRepository.saveAndFlush(
                              new Task(
                                  project.getId(),
                                  "Controller Task 1",
                                  null,
                                  "MEDIUM",
                                  null,
                                  null,
                                  ownerMemberId));
                      taskId1 = task1.getId();

                      var task2 =
                          taskRepository.saveAndFlush(
                              new Task(
                                  project.getId(),
                                  "Controller Task 2",
                                  null,
                                  "HIGH",
                                  null,
                                  null,
                                  ownerMemberId));
                      taskId2 = task2.getId();

                      // Add memberMemberId as project lead for permission tests
                      projectMemberRepository.saveAndFlush(
                          new ProjectMember(
                              project.getId(), memberMemberId, "lead", ownerMemberId));
                    }));
  }

  // --- CRUD Tests ---

  @Test
  @Order(1)
  void shouldCreateTemplateWithTasks() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/project-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Monthly Bookkeeping",
                          "namePattern": "{customer} - {month} {year}",
                          "description": "Standard monthly template",
                          "billableDefault": true,
                          "tasks": [
                            {
                              "name": "Collect docs",
                              "description": "Gather all documents",
                              "estimatedHours": 2.0,
                              "sortOrder": 0,
                              "billable": true,
                              "assigneeRole": "ANY_MEMBER"
                            },
                            {
                              "name": "Process entries",
                              "estimatedHours": 4.0,
                              "sortOrder": 1,
                              "billable": true,
                              "assigneeRole": "PROJECT_LEAD"
                            }
                          ],
                          "tagIds": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Monthly Bookkeeping"))
            .andExpect(jsonPath("$.namePattern").value("{customer} - {month} {year}"))
            .andExpect(jsonPath("$.description").value("Standard monthly template"))
            .andExpect(jsonPath("$.billableDefault").value(true))
            .andExpect(jsonPath("$.source").value("MANUAL"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.taskCount").value(2))
            .andExpect(jsonPath("$.tasks.length()").value(2))
            .andExpect(jsonPath("$.tasks[0].name").value("Collect docs"))
            .andExpect(jsonPath("$.tasks[1].name").value("Process entries"))
            .andReturn();

    createdTemplateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(2)
  void shouldListTemplates() throws Exception {
    mockMvc
        .perform(get("/api/project-templates").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  @Order(3)
  void shouldGetTemplateById() throws Exception {
    mockMvc
        .perform(get("/api/project-templates/" + createdTemplateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdTemplateId))
        .andExpect(jsonPath("$.name").value("Monthly Bookkeeping"))
        .andExpect(jsonPath("$.tasks.length()").value(2));
  }

  @Test
  @Order(4)
  void shouldReturn404ForNonExistentTemplate() throws Exception {
    mockMvc
        .perform(get("/api/project-templates/" + UUID.randomUUID()).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(5)
  void shouldReturn400ForMissingRequiredFields() throws Exception {
    mockMvc
        .perform(
            post("/api/project-templates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "description": "Missing name and namePattern"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(6)
  void shouldUpdateTemplate() throws Exception {
    mockMvc
        .perform(
            put("/api/project-templates/" + createdTemplateId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Monthly Bookkeeping Updated",
                      "namePattern": "{customer} - Updated",
                      "description": "Updated description",
                      "billableDefault": false,
                      "tasks": [
                        {
                          "name": "Updated Task 1",
                          "sortOrder": 0,
                          "billable": false,
                          "assigneeRole": "UNASSIGNED"
                        }
                      ],
                      "tagIds": []
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Monthly Bookkeeping Updated"))
        .andExpect(jsonPath("$.description").value("Updated description"))
        .andExpect(jsonPath("$.billableDefault").value(false))
        .andExpect(jsonPath("$.tasks.length()").value(1))
        .andExpect(jsonPath("$.tasks[0].name").value("Updated Task 1"));
  }

  @Test
  @Order(7)
  void shouldDuplicateTemplate() throws Exception {
    mockMvc
        .perform(
            post("/api/project-templates/" + createdTemplateId + "/duplicate").with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value(org.hamcrest.Matchers.endsWith("(Copy)")))
        .andExpect(jsonPath("$.source").value("MANUAL"))
        .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.notNullValue()));
  }

  @Test
  @Order(8)
  void shouldDeleteTemplate() throws Exception {
    // Create a disposable template to delete
    var result =
        mockMvc
            .perform(
                post("/api/project-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "To Delete",
                          "namePattern": "{customer}",
                          "billableDefault": false,
                          "tasks": [],
                          "tagIds": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String deleteId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(delete("/api/project-templates/" + deleteId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify it's gone
    mockMvc
        .perform(get("/api/project-templates/" + deleteId).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(9)
  void shouldReturn409WhenDeletingTemplateWithActiveSchedule() throws Exception {
    // Create template + schedule in tenant context
    final UUID[] templateIdHolder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
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
                                  ownerMemberId));
                      templateIdHolder[0] = template.getId();

                      var customer =
                          customerRepository.saveAndFlush(
                              new Customer(
                                  "Schedule Cust",
                                  "sched_cust@test.com",
                                  null,
                                  null,
                                  null,
                                  ownerMemberId));

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
                              ownerMemberId));
                    }));

    mockMvc
        .perform(delete("/api/project-templates/" + templateIdHolder[0]).with(ownerJwt()))
        .andExpect(status().isConflict());
  }

  // --- Permission Tests ---

  @Test
  @Order(10)
  void memberCannotCreateTemplate() throws Exception {
    mockMvc
        .perform(
            post("/api/project-templates")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Member Template",
                      "namePattern": "{customer}",
                      "billableDefault": false,
                      "tasks": [],
                      "tagIds": []
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(11)
  void memberCannotUpdateTemplate() throws Exception {
    mockMvc
        .perform(
            put("/api/project-templates/" + createdTemplateId)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Hacked Name",
                      "namePattern": "{customer}",
                      "billableDefault": false,
                      "tasks": [],
                      "tagIds": []
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(12)
  void memberCannotDeleteTemplate() throws Exception {
    mockMvc
        .perform(delete("/api/project-templates/" + createdTemplateId).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(13)
  void memberCannotDuplicateTemplate() throws Exception {
    mockMvc
        .perform(
            post("/api/project-templates/" + createdTemplateId + "/duplicate").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(14)
  void memberCanListTemplates() throws Exception {
    mockMvc.perform(get("/api/project-templates").with(memberJwt())).andExpect(status().isOk());
  }

  @Test
  @Order(15)
  void memberCanGetTemplateById() throws Exception {
    mockMvc
        .perform(get("/api/project-templates/" + createdTemplateId).with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdTemplateId));
  }

  // --- Save From Project Tests ---

  @Test
  @Order(16)
  void shouldSaveFromProjectAsAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/project-templates/from-project/" + projectId)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Admin From Project",
                      "namePattern": "{customer} - Admin",
                      "description": "Created by admin",
                      "taskIds": ["%s", "%s"],
                      "tagIds": [],
                      "taskRoles": {"%s": "PROJECT_LEAD"}
                    }
                    """
                        .formatted(taskId1, taskId2, taskId1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Admin From Project"))
        .andExpect(jsonPath("$.source").value("FROM_PROJECT"))
        .andExpect(jsonPath("$.sourceProjectId").value(projectId.toString()))
        .andExpect(jsonPath("$.taskCount").value(2))
        .andExpect(jsonPath("$.tasks[0].name").value("Controller Task 1"))
        .andExpect(jsonPath("$.tasks[0].assigneeRole").value("PROJECT_LEAD"))
        .andExpect(jsonPath("$.tasks[1].name").value("Controller Task 2"))
        .andExpect(jsonPath("$.tasks[1].assigneeRole").value("UNASSIGNED"));
  }

  @Test
  @Order(17)
  void shouldSaveFromProjectAsProjectLead() throws Exception {
    // memberMemberId is a project lead on the test project (set up in @BeforeAll)
    mockMvc
        .perform(
            post("/api/project-templates/from-project/" + projectId)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Lead From Project",
                      "namePattern": "{customer} - Lead",
                      "taskIds": ["%s"],
                      "tagIds": []
                    }
                    """
                        .formatted(taskId1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Lead From Project"))
        .andExpect(jsonPath("$.source").value("FROM_PROJECT"))
        .andExpect(jsonPath("$.taskCount").value(1));
  }

  @Test
  @Order(18)
  void regularMemberCannotSaveFromProjectWithoutLead() throws Exception {
    // Create a different project where memberMemberId is NOT a lead
    final UUID[] otherProjectId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var project =
                          projectRepository.saveAndFlush(
                              new Project("No Lead Project", null, ownerMemberId));
                      otherProjectId[0] = project.getId();
                    }));

    mockMvc
        .perform(
            post("/api/project-templates/from-project/" + otherProjectId[0])
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Unauthorized Template",
                      "namePattern": "{customer}",
                      "taskIds": [],
                      "tagIds": []
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(19)
  void shouldReturn404ForNonExistentProject() throws Exception {
    mockMvc
        .perform(
            post("/api/project-templates/from-project/" + UUID.randomUUID())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "From Nonexistent Project",
                      "namePattern": "{customer}",
                      "taskIds": [],
                      "tagIds": []
                    }
                    """))
        .andExpect(status().isNotFound());
  }

  // --- Instantiate Tests ---

  @Test
  @Order(20)
  void shouldInstantiateTemplateAsOwner() throws Exception {
    mockMvc
        .perform(
            post("/api/project-templates/" + createdTemplateId + "/instantiate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Instantiated Project",
                      "customerId": null,
                      "projectLeadMemberId": null,
                      "description": null
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.name").value("Instantiated Project"));
  }

  @Test
  @Order(21)
  void shouldInstantiateTemplateAsMember() throws Exception {
    mockMvc
        .perform(
            post("/api/project-templates/" + createdTemplateId + "/instantiate")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Member Instantiated Project"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Member Instantiated Project"));
  }

  @Test
  @Order(22)
  void shouldReturn404WhenInstantiatingUnknownTemplate() throws Exception {
    mockMvc
        .perform(
            post("/api/project-templates/" + UUID.randomUUID() + "/instantiate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Shouldn't work"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(23)
  void shouldReturn400WhenInstantiatingInactiveTemplate() throws Exception {
    // Create and deactivate a template in tenant context
    final UUID[] inactiveTemplateId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var t =
                          templateRepository.saveAndFlush(
                              new ProjectTemplate(
                                  "Inactive For Instantiate",
                                  "{customer}",
                                  null,
                                  false,
                                  "MANUAL",
                                  null,
                                  ownerMemberId));
                      t.deactivate();
                      templateRepository.saveAndFlush(t);
                      inactiveTemplateId[0] = t.getId();
                    }));

    mockMvc
        .perform(
            post("/api/project-templates/" + inactiveTemplateId[0] + "/instantiate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Should Be 400"}
                    """))
        .andExpect(status().isBadRequest());
  }

  // --- Sub-Item Tests ---

  @Test
  @Order(30)
  void createTemplate_withSubItems_subItemsArePersisted() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/project-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Sub-Item Template",
                          "namePattern": "Test - {customer}",
                          "billableDefault": true,
                          "tasks": [
                            {
                              "name": "Task 1",
                              "sortOrder": 0,
                              "billable": true,
                              "assigneeRole": "UNASSIGNED",
                              "items": [
                                {"title": "Sub-item A", "sortOrder": 0},
                                {"title": "Sub-item B", "sortOrder": 1}
                              ]
                            },
                            {
                              "name": "Task 2",
                              "sortOrder": 1,
                              "billable": false,
                              "assigneeRole": "UNASSIGNED",
                              "items": [
                                {"title": "Sub-item C", "sortOrder": 0}
                              ]
                            }
                          ],
                          "tagIds": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String templateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // GET the template and verify nested items
    mockMvc
        .perform(get("/api/project-templates/" + templateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tasks.length()").value(2))
        .andExpect(jsonPath("$.tasks[0].items.length()").value(2))
        .andExpect(jsonPath("$.tasks[0].items[0].title").value("Sub-item A"))
        .andExpect(jsonPath("$.tasks[0].items[0].sortOrder").value(0))
        .andExpect(jsonPath("$.tasks[0].items[1].title").value("Sub-item B"))
        .andExpect(jsonPath("$.tasks[0].items[1].sortOrder").value(1))
        .andExpect(jsonPath("$.tasks[1].items.length()").value(1))
        .andExpect(jsonPath("$.tasks[1].items[0].title").value("Sub-item C"))
        .andExpect(jsonPath("$.tasks[1].items[0].sortOrder").value(0));
  }

  @Test
  @Order(31)
  void updateTemplate_withSubItems_subItemsAreReplaced() throws Exception {
    // Create a template with 1 task having 2 sub-items
    var createResult =
        mockMvc
            .perform(
                post("/api/project-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Update Sub-Items Template",
                          "namePattern": "{customer}",
                          "billableDefault": false,
                          "tasks": [
                            {
                              "name": "Original Task",
                              "sortOrder": 0,
                              "billable": true,
                              "assigneeRole": "UNASSIGNED",
                              "items": [
                                {"title": "Old Item 1", "sortOrder": 0},
                                {"title": "Old Item 2", "sortOrder": 1}
                              ]
                            }
                          ],
                          "tagIds": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String templateId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Update: replace the task with a new task having 1 different sub-item
    mockMvc
        .perform(
            put("/api/project-templates/" + templateId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Update Sub-Items Template",
                      "namePattern": "{customer}",
                      "billableDefault": false,
                      "tasks": [
                        {
                          "name": "Replaced Task",
                          "sortOrder": 0,
                          "billable": false,
                          "assigneeRole": "UNASSIGNED",
                          "items": [
                            {"title": "New Item Only", "sortOrder": 0}
                          ]
                        }
                      ],
                      "tagIds": []
                    }
                    """))
        .andExpect(status().isOk());

    // GET and verify old items are gone, new item exists
    mockMvc
        .perform(get("/api/project-templates/" + templateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tasks.length()").value(1))
        .andExpect(jsonPath("$.tasks[0].name").value("Replaced Task"))
        .andExpect(jsonPath("$.tasks[0].items.length()").value(1))
        .andExpect(jsonPath("$.tasks[0].items[0].title").value("New Item Only"));
  }

  @Test
  @Order(32)
  void duplicateTemplate_withSubItems_subItemsAreCopied() throws Exception {
    // Create a template with sub-items
    var createResult =
        mockMvc
            .perform(
                post("/api/project-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Duplicate Sub-Items Template",
                          "namePattern": "{customer}",
                          "billableDefault": true,
                          "tasks": [
                            {
                              "name": "Task With Items",
                              "sortOrder": 0,
                              "billable": true,
                              "assigneeRole": "UNASSIGNED",
                              "items": [
                                {"title": "Item X", "sortOrder": 0},
                                {"title": "Item Y", "sortOrder": 1}
                              ]
                            }
                          ],
                          "tagIds": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String sourceId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Duplicate
    var dupResult =
        mockMvc
            .perform(post("/api/project-templates/" + sourceId + "/duplicate").with(ownerJwt()))
            .andExpect(status().isCreated())
            .andReturn();

    String dupId = JsonPath.read(dupResult.getResponse().getContentAsString(), "$.id");

    // GET the duplicate and verify items are present
    mockMvc
        .perform(get("/api/project-templates/" + dupId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tasks.length()").value(1))
        .andExpect(jsonPath("$.tasks[0].items.length()").value(2))
        .andExpect(jsonPath("$.tasks[0].items[0].title").value("Item X"))
        .andExpect(jsonPath("$.tasks[0].items[1].title").value("Item Y"));
  }

  @Test
  @Order(33)
  void instantiateTemplate_withSubItems_taskItemsAreCreated() throws Exception {
    // Create a template with sub-items
    var createResult =
        mockMvc
            .perform(
                post("/api/project-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Instantiate Sub-Items Template",
                          "namePattern": "{customer}",
                          "billableDefault": true,
                          "tasks": [
                            {
                              "name": "Template Task A",
                              "sortOrder": 0,
                              "billable": true,
                              "assigneeRole": "UNASSIGNED",
                              "items": [
                                {"title": "Step 1", "sortOrder": 0},
                                {"title": "Step 2", "sortOrder": 1}
                              ]
                            },
                            {
                              "name": "Template Task B",
                              "sortOrder": 1,
                              "billable": false,
                              "assigneeRole": "UNASSIGNED",
                              "items": [
                                {"title": "Step 3", "sortOrder": 0}
                              ]
                            }
                          ],
                          "tagIds": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String templateId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Instantiate the template
    var instantiateResult =
        mockMvc
            .perform(
                post("/api/project-templates/" + templateId + "/instantiate")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Instantiated With Items"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String projectIdStr =
        JsonPath.read(instantiateResult.getResponse().getContentAsString(), "$.id");

    // GET the project's tasks
    var tasksResult =
        mockMvc
            .perform(get("/api/projects/" + projectIdStr + "/tasks").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    // Extract task IDs and titles so we can match by name (order may vary)
    List<Map<String, Object>> tasks =
        JsonPath.read(tasksResult.getResponse().getContentAsString(), "$[*]");

    String taskAId = null;
    String taskBId = null;
    for (var task : tasks) {
      if ("Template Task A".equals(task.get("title"))) {
        taskAId = task.get("id").toString();
      } else if ("Template Task B".equals(task.get("title"))) {
        taskBId = task.get("id").toString();
      }
    }
    assertThat(taskAId).as("Task A should exist").isNotNull();
    assertThat(taskBId).as("Task B should exist").isNotNull();

    // Task A should have 2 items (Step 1, Step 2)
    mockMvc
        .perform(get("/api/tasks/" + taskAId + "/items").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].title").value("Step 1"))
        .andExpect(jsonPath("$[0].sortOrder").value(0))
        .andExpect(jsonPath("$[0].completed").value(false))
        .andExpect(jsonPath("$[1].title").value("Step 2"))
        .andExpect(jsonPath("$[1].sortOrder").value(1));

    // Task B should have 1 item (Step 3)
    mockMvc
        .perform(get("/api/tasks/" + taskBId + "/items").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].title").value("Step 3"))
        .andExpect(jsonPath("$[0].sortOrder").value(0))
        .andExpect(jsonPath("$[0].completed").value(false));
  }

  @Test
  @Order(34)
  void saveFromProject_tasksWithItems_itemsCopiedToTemplate() throws Exception {
    // Create task items on the existing project tasks (taskId1, taskId2)
    mockMvc
        .perform(
            post("/api/tasks/" + taskId1 + "/items")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Project Sub-Item 1", "sortOrder": 0}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/tasks/" + taskId1 + "/items")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Project Sub-Item 2", "sortOrder": 1}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/tasks/" + taskId2 + "/items")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Project Sub-Item 3", "sortOrder": 0}
                    """))
        .andExpect(status().isCreated());

    // Save project as template including both tasks
    var saveResult =
        mockMvc
            .perform(
                post("/api/project-templates/from-project/" + projectId)
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "From Project With Items",
                          "namePattern": "{customer} - Items",
                          "taskIds": ["%s", "%s"],
                          "tagIds": []
                        }
                        """
                            .formatted(taskId1, taskId2)))
            .andExpect(status().isCreated())
            .andReturn();

    String savedTemplateId = JsonPath.read(saveResult.getResponse().getContentAsString(), "$.id");

    // GET the template and verify items were copied
    mockMvc
        .perform(get("/api/project-templates/" + savedTemplateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tasks.length()").value(2))
        .andExpect(jsonPath("$.tasks[0].items.length()").value(2))
        .andExpect(jsonPath("$.tasks[0].items[0].title").value("Project Sub-Item 1"))
        .andExpect(jsonPath("$.tasks[0].items[1].title").value("Project Sub-Item 2"))
        .andExpect(jsonPath("$.tasks[1].items.length()").value(1))
        .andExpect(jsonPath("$.tasks[1].items[0].title").value("Project Sub-Item 3"));
  }

  // --- Helper methods ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_tmpl_ctrl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_tmpl_ctrl_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_tmpl_ctrl_member")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
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
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
