package io.b2mash.b2b.b2bstrawman.dashboard;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardProjectIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_dashboard_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectService projectService;
  @Autowired private TaskService taskService;
  @Autowired private ProjectMemberService projectMemberService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdMember;
  private UUID memberIdNonMember;

  // Project with mixed tasks (some done, some open, some overdue)
  private UUID projectWithTasks;

  // Project with all tasks done (healthy)
  private UUID projectAllDone;

  // Project with no tasks (unknown)
  private UUID projectNoTasks;

  // Project with overdue tasks (at risk)
  private UUID projectOverdue;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Dashboard Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_dash_owner", "dash_owner@test.com", "Dashboard Owner", "owner"));
    memberIdMember =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_dash_member", "dash_member@test.com", "Dashboard Member", "member"));
    memberIdNonMember =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_dash_nonmember",
                "dash_nonmember@test.com",
                "Dashboard NonMember",
                "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              // --- Project with mixed tasks ---
              var p1 = projectService.createProject("Mixed Tasks Project", "Test", memberIdOwner);
              projectWithTasks = p1.getId();

              // Add member to this project
              projectMemberService.addMember(projectWithTasks, memberIdMember, memberIdOwner);

              // Create tasks: 2 OPEN, 1 IN_PROGRESS, 1 IN_REVIEW, 1 DONE, 1 overdue
              taskService.createTask(
                  projectWithTasks,
                  "Open Task 1",
                  null,
                  "MEDIUM",
                  "TASK",
                  null,
                  memberIdOwner,
                  "owner");
              taskService.createTask(
                  projectWithTasks,
                  "Open Task 2",
                  null,
                  "MEDIUM",
                  "TASK",
                  null,
                  memberIdOwner,
                  "owner");

              var ipTask =
                  taskService.createTask(
                      projectWithTasks,
                      "In Progress Task",
                      null,
                      "HIGH",
                      "TASK",
                      null,
                      memberIdOwner,
                      "owner");
              taskService.updateTask(
                  ipTask.getId(),
                  "In Progress Task",
                  null,
                  "HIGH",
                  "IN_PROGRESS",
                  "TASK",
                  null,
                  null,
                  memberIdOwner,
                  "owner");

              var irTask =
                  taskService.createTask(
                      projectWithTasks,
                      "In Review Task",
                      null,
                      "MEDIUM",
                      "TASK",
                      null,
                      memberIdOwner,
                      "owner");
              taskService.updateTask(
                  irTask.getId(),
                  "In Review Task",
                  null,
                  "MEDIUM",
                  "IN_REVIEW",
                  "TASK",
                  null,
                  null,
                  memberIdOwner,
                  "owner");

              var doneTask =
                  taskService.createTask(
                      projectWithTasks,
                      "Done Task",
                      null,
                      "LOW",
                      "TASK",
                      null,
                      memberIdOwner,
                      "owner");
              taskService.updateTask(
                  doneTask.getId(),
                  "Done Task",
                  null,
                  "LOW",
                  "DONE",
                  "TASK",
                  null,
                  null,
                  memberIdOwner,
                  "owner");

              // Overdue task: OPEN with due date in the past
              var overdueTask =
                  taskService.createTask(
                      projectWithTasks,
                      "Overdue Task",
                      null,
                      "HIGH",
                      "TASK",
                      LocalDate.now().minusDays(5),
                      memberIdOwner,
                      "owner");

              // --- Project with all tasks done (healthy) ---
              var p2 = projectService.createProject("All Done Project", "Test", memberIdOwner);
              projectAllDone = p2.getId();

              var done1 =
                  taskService.createTask(
                      projectAllDone,
                      "Done 1",
                      null,
                      "MEDIUM",
                      "TASK",
                      null,
                      memberIdOwner,
                      "owner");
              taskService.updateTask(
                  done1.getId(),
                  "Done 1",
                  null,
                  "MEDIUM",
                  "DONE",
                  "TASK",
                  null,
                  null,
                  memberIdOwner,
                  "owner");

              var done2 =
                  taskService.createTask(
                      projectAllDone,
                      "Done 2",
                      null,
                      "MEDIUM",
                      "TASK",
                      null,
                      memberIdOwner,
                      "owner");
              taskService.updateTask(
                  done2.getId(),
                  "Done 2",
                  null,
                  "MEDIUM",
                  "DONE",
                  "TASK",
                  null,
                  null,
                  memberIdOwner,
                  "owner");

              // --- Project with no tasks (unknown) ---
              var p3 = projectService.createProject("No Tasks Project", "Test", memberIdOwner);
              projectNoTasks = p3.getId();

              // --- Project with high overdue ratio (at risk / critical) ---
              var p4 = projectService.createProject("Overdue Project", "Test", memberIdOwner);
              projectOverdue = p4.getId();

              // 3 overdue tasks out of 4 total (75% overdue -> CRITICAL)
              taskService.createTask(
                  projectOverdue,
                  "Overdue 1",
                  null,
                  "HIGH",
                  "TASK",
                  LocalDate.now().minusDays(10),
                  memberIdOwner,
                  "owner");
              taskService.createTask(
                  projectOverdue,
                  "Overdue 2",
                  null,
                  "HIGH",
                  "TASK",
                  LocalDate.now().minusDays(7),
                  memberIdOwner,
                  "owner");
              taskService.createTask(
                  projectOverdue,
                  "Overdue 3",
                  null,
                  "HIGH",
                  "TASK",
                  LocalDate.now().minusDays(3),
                  memberIdOwner,
                  "owner");
              var notOverdue =
                  taskService.createTask(
                      projectOverdue,
                      "Not Overdue",
                      null,
                      "LOW",
                      "TASK",
                      LocalDate.now().plusDays(30),
                      memberIdOwner,
                      "owner");
              taskService.updateTask(
                  notOverdue.getId(),
                  "Not Overdue",
                  null,
                  "LOW",
                  "DONE",
                  "TASK",
                  null,
                  null,
                  memberIdOwner,
                  "owner");
            });
  }

  // --- Project Health Tests ---

  @Test
  void healthReturnsHealthyForProjectWithAllTasksDone() throws Exception {
    mockMvc
        .perform(get("/api/projects/{projectId}/health", projectAllDone).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.healthStatus").value("HEALTHY"))
        .andExpect(jsonPath("$.metrics.totalTasks").value(2))
        .andExpect(jsonPath("$.metrics.tasksDone").value(2))
        .andExpect(jsonPath("$.metrics.completionPercent").value(100.0));
  }

  @Test
  void healthReturnsAtRiskOrCriticalWithOverdueTasks() throws Exception {
    mockMvc
        .perform(get("/api/projects/{projectId}/health", projectOverdue).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.healthStatus")
                .value(
                    org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("AT_RISK"), org.hamcrest.Matchers.is("CRITICAL"))))
        .andExpect(jsonPath("$.metrics.tasksOverdue").value(3));
  }

  @Test
  void healthReturnsUnknownForProjectWithNoTasks() throws Exception {
    mockMvc
        .perform(get("/api/projects/{projectId}/health", projectNoTasks).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.healthStatus").value("UNKNOWN"))
        .andExpect(jsonPath("$.healthReasons[0]").value("No tasks created yet"))
        .andExpect(jsonPath("$.metrics.totalTasks").value(0));
  }

  @Test
  void healthIncludesReasonsArray() throws Exception {
    mockMvc
        .perform(get("/api/projects/{projectId}/health", projectOverdue).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.healthReasons").isArray())
        .andExpect(jsonPath("$.healthReasons").isNotEmpty());
  }

  // --- Task Summary Tests ---

  @Test
  void taskSummaryReturnsCorrectCountsByStatus() throws Exception {
    mockMvc
        .perform(get("/api/projects/{projectId}/task-summary", projectWithTasks).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.todo").value(3)) // 2 OPEN + 1 overdue (still OPEN)
        .andExpect(jsonPath("$.inProgress").value(1))
        .andExpect(jsonPath("$.inReview").value(1))
        .andExpect(jsonPath("$.done").value(1))
        .andExpect(jsonPath("$.total").value(6));
  }

  @Test
  void taskSummaryIncludesOverdueCount() throws Exception {
    mockMvc
        .perform(get("/api/projects/{projectId}/task-summary", projectWithTasks).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overdueCount").value(1));
  }

  // --- Access Control Tests ---

  @Test
  void nonMemberCannotAccessProjectHealth() throws Exception {
    // nonMember is not a project member on any of the projects
    mockMvc
        .perform(get("/api/projects/{projectId}/health", projectWithTasks).with(nonMemberJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void adminCanAccessAnyProjectHealth() throws Exception {
    // Admin can access any project even without explicit membership
    mockMvc
        .perform(get("/api/projects/{projectId}/health", projectWithTasks).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.healthStatus").exists());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_dash_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor nonMemberJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_dash_nonmember").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    // Sync a separate admin member to avoid confusion
    return jwt()
        .jwt(j -> j.subject("user_dash_member").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  // --- Member Sync Helper ---

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
