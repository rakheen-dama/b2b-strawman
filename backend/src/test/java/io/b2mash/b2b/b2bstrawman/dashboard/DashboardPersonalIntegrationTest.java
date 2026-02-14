package io.b2mash.b2b.b2bstrawman.dashboard;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
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
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
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

/**
 * Integration tests for the personal dashboard and project member-hours endpoints (Epic 79A). Uses
 * a dedicated org to avoid collisions with other dashboard tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardPersonalIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_personal_dash_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectService projectService;
  @Autowired private TaskService taskService;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private ProjectMemberService projectMemberService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID adminMemberId;
  private UUID member1Id;
  private UUID nonMemberId; // member not on any project

  private UUID projectAId;
  private UUID projectBId;

  // Tasks in project A
  private UUID taskA1Id; // assigned to member1, billable time
  private UUID taskA2Id; // assigned to member1, overdue
  private UUID taskA3Id; // assigned to member1, upcoming deadline

  // Tasks in project B
  private UUID taskB1Id; // assigned to member1, non-billable time

  private final LocalDate today = LocalDate.now();
  private final LocalDate thirtyDaysAgo = today.minusDays(30);

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Personal Dashboard Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    adminMemberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_pdash_admin", "pdash_admin@test.com", "Admin User", "admin"));
    member1Id =
        UUID.fromString(
            syncMember(ORG_ID, "user_pdash_member1", "pdash_m1@test.com", "Member One", "member"));
    nonMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_pdash_nonmember", "pdash_nm@test.com", "Non Member", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, adminMemberId)
        .where(RequestScopes.ORG_ROLE, "admin")
        .run(this::seedData);
  }

  private void seedData() {
    // --- Create 2 projects ---
    var projectA =
        projectService.createProject("Personal Project Alpha", "First project", adminMemberId);
    projectAId = projectA.getId();

    var projectB =
        projectService.createProject("Personal Project Beta", "Second project", adminMemberId);
    projectBId = projectB.getId();

    // --- Add member1 to both projects ---
    projectMemberService.addMember(projectAId, member1Id, adminMemberId);
    projectMemberService.addMember(projectBId, member1Id, adminMemberId);

    // --- Create tasks in project A ---
    // Task A1: assigned to member1, no due date
    var taskA1 =
        taskService.createTask(
            projectAId, "Task A1", null, "MEDIUM", "TASK", null, adminMemberId, "admin");
    taskA1Id = taskA1.getId();
    taskService.updateTask(
        taskA1Id,
        "Task A1",
        null,
        "MEDIUM",
        "IN_PROGRESS",
        "TASK",
        null,
        member1Id,
        adminMemberId,
        "admin");

    // Task A2: assigned to member1, overdue (due date in the past)
    var taskA2 =
        taskService.createTask(
            projectAId,
            "Task A2 Overdue",
            null,
            "HIGH",
            "TASK",
            today.minusDays(5),
            adminMemberId,
            "admin");
    taskA2Id = taskA2.getId();
    taskService.updateTask(
        taskA2Id,
        "Task A2 Overdue",
        null,
        "HIGH",
        "IN_PROGRESS",
        "TASK",
        today.minusDays(5),
        member1Id,
        adminMemberId,
        "admin");

    // Task A3: assigned to member1, upcoming deadline
    var taskA3 =
        taskService.createTask(
            projectAId,
            "Task A3 Upcoming",
            null,
            "MEDIUM",
            "TASK",
            today.plusDays(3),
            adminMemberId,
            "admin");
    taskA3Id = taskA3.getId();
    taskService.updateTask(
        taskA3Id,
        "Task A3 Upcoming",
        null,
        "MEDIUM",
        "OPEN",
        "TASK",
        today.plusDays(3),
        member1Id,
        adminMemberId,
        "admin");

    // --- Create task in project B ---
    var taskB1 =
        taskService.createTask(
            projectBId, "Task B1", null, "LOW", "TASK", today.plusDays(7), adminMemberId, "admin");
    taskB1Id = taskB1.getId();
    taskService.updateTask(
        taskB1Id,
        "Task B1",
        null,
        "LOW",
        "IN_PROGRESS",
        "TASK",
        today.plusDays(7),
        member1Id,
        adminMemberId,
        "admin");

    // --- Log time for member1 ---
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, member1Id)
        .where(RequestScopes.ORG_ROLE, "member")
        .run(
            () -> {
              // Project A: 120 min billable
              timeEntryService.createTimeEntry(
                  taskA1Id,
                  today.minusDays(3),
                  120,
                  true,
                  null,
                  "Member1 billable on A",
                  member1Id,
                  "member");

              // Project A: 60 min non-billable
              timeEntryService.createTimeEntry(
                  taskA1Id,
                  today.minusDays(2),
                  60,
                  false,
                  null,
                  "Member1 non-billable on A",
                  member1Id,
                  "member");

              // Project B: 90 min billable
              timeEntryService.createTimeEntry(
                  taskB1Id,
                  today.minusDays(1),
                  90,
                  true,
                  null,
                  "Member1 billable on B",
                  member1Id,
                  "member");
            });

    // --- Log time for admin on project A ---
    timeEntryService.createTimeEntry(
        taskA1Id, today.minusDays(4), 45, true, null, "Admin work on A", adminMemberId, "admin");
  }

  // --- Personal Dashboard Tests ---

  @Test
  void personalDashboardReturnsUtilization() throws Exception {
    // member1 total: 120+60+90 = 270 min = 4.5 hours; billable: 120+90 = 210 min = 3.5 hours
    mockMvc
        .perform(
            get("/api/dashboard/personal")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(member1Jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.utilization.totalHours").value(closeTo(4.5, 0.01)))
        .andExpect(jsonPath("$.utilization.billableHours").value(closeTo(3.5, 0.01)))
        .andExpect(jsonPath("$.utilization.billablePercent").isNumber());
  }

  @Test
  void personalDashboardReturnsProjectBreakdown() throws Exception {
    // member1 has time on 2 projects: Alpha (180 min) and Beta (90 min)
    mockMvc
        .perform(
            get("/api/dashboard/personal")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(member1Jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectBreakdown").isArray())
        .andExpect(jsonPath("$.projectBreakdown", hasSize(2)))
        .andExpect(jsonPath("$.projectBreakdown[0].projectName").value("Personal Project Alpha"))
        .andExpect(jsonPath("$.projectBreakdown[0].hours").value(closeTo(3.0, 0.01)))
        .andExpect(jsonPath("$.projectBreakdown[0].percent").isNumber())
        .andExpect(jsonPath("$.projectBreakdown[1].projectName").value("Personal Project Beta"))
        .andExpect(jsonPath("$.projectBreakdown[1].hours").value(closeTo(1.5, 0.01)));
  }

  @Test
  void personalDashboardReturnsOverdueTaskCount() throws Exception {
    // member1 has 1 overdue task (Task A2)
    mockMvc
        .perform(
            get("/api/dashboard/personal")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(member1Jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overdueTaskCount").value(1));
  }

  @Test
  void personalDashboardReturnsUpcomingDeadlines() throws Exception {
    // member1 has 2 upcoming tasks with due dates: A3 (today+3) and B1 (today+7)
    mockMvc
        .perform(
            get("/api/dashboard/personal")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(member1Jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upcomingDeadlines").isArray())
        .andExpect(jsonPath("$.upcomingDeadlines", hasSize(2)))
        .andExpect(jsonPath("$.upcomingDeadlines[0].taskName").value("Task A3 Upcoming"))
        .andExpect(jsonPath("$.upcomingDeadlines[0].projectName").value("Personal Project Alpha"))
        .andExpect(jsonPath("$.upcomingDeadlines[0].dueDate").isString())
        .andExpect(jsonPath("$.upcomingDeadlines[1].taskName").value("Task B1"))
        .andExpect(jsonPath("$.upcomingDeadlines[1].projectName").value("Personal Project Beta"));
  }

  @Test
  void personalDashboardReturnsTrend() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/personal")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(member1Jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trend").isArray())
        .andExpect(jsonPath("$.trend", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$.trend[0].period").isString())
        .andExpect(jsonPath("$.trend[0].value").isNumber());
  }

  // --- Member-Hours Tests ---

  @Test
  void memberHoursReturnsCorrectBreakdown() throws Exception {
    // Project A has admin (45 min billable) and member1 (180 min total: 120 billable + 60
    // non-billable)
    mockMvc
        .perform(
            get("/api/projects/" + projectAId + "/member-hours")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(2)))
        // Sorted by totalHours DESC: member1 (3.0h) before admin (0.75h)
        .andExpect(jsonPath("$[0].memberName").value("Member One"))
        .andExpect(jsonPath("$[0].totalHours").value(closeTo(3.0, 0.01)))
        .andExpect(jsonPath("$[0].billableHours").value(closeTo(2.0, 0.01)))
        .andExpect(jsonPath("$[1].memberName").value("Admin User"))
        .andExpect(jsonPath("$[1].totalHours").value(closeTo(0.75, 0.01)))
        .andExpect(jsonPath("$[1].billableHours").value(closeTo(0.75, 0.01)));
  }

  @Test
  void memberHoursRequiresProjectViewAccess() throws Exception {
    // nonMember is not on project A, should get 404 (security-by-obscurity)
    mockMvc
        .perform(
            get("/api/projects/" + projectAId + "/member-hours")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(nonMemberJwt()))
        .andExpect(status().isNotFound());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pdash_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor member1Jwt() {
    return jwt()
        .jwt(j -> j.subject("user_pdash_member1").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor nonMemberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_pdash_nonmember").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
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
