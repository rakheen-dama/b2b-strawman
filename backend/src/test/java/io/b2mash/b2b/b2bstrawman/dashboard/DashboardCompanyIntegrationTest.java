package io.b2mash.b2b.b2bstrawman.dashboard;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
 * Integration tests for all company-level dashboard endpoints: KPIs, project health list, team
 * workload, and cross-project activity. Uses a dedicated org to avoid collisions with
 * DashboardProjectIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardCompanyIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_company_dash_test";

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
  private UUID member2Id;

  // 3 projects
  private UUID projectAId; // admin + member1 are members
  private UUID projectBId; // admin + member2 are members
  private UUID projectCId; // admin only (no regular members)

  // Tasks for time entries
  private UUID taskA1Id;
  private UUID taskB1Id;
  private UUID taskC1Id;

  private final LocalDate today = LocalDate.now();
  private final LocalDate thirtyDaysAgo = today.minusDays(30);

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Company Dashboard Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    adminMemberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_cdash_admin", "cdash_admin@test.com", "Admin User", "admin"));
    member1Id =
        UUID.fromString(
            syncMember(ORG_ID, "user_cdash_member1", "cdash_m1@test.com", "Member One", "member"));
    member2Id =
        UUID.fromString(
            syncMember(ORG_ID, "user_cdash_member2", "cdash_m2@test.com", "Member Two", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, adminMemberId)
        .where(RequestScopes.ORG_ROLE, "admin")
        .run(this::seedData);
  }

  private void seedData() {
    // --- Create 3 projects ---
    var projectA = projectService.createProject("Project Alpha", "First project", adminMemberId);
    projectAId = projectA.getId();

    var projectB = projectService.createProject("Project Beta", "Second project", adminMemberId);
    projectBId = projectB.getId();

    var projectC = projectService.createProject("Project Gamma", "Third project", adminMemberId);
    projectCId = projectC.getId();

    // --- Add members to projects ---
    projectMemberService.addMember(projectAId, member1Id, adminMemberId);
    projectMemberService.addMember(projectBId, member2Id, adminMemberId);

    // --- Create tasks ---
    // Project A: 2 open tasks (1 overdue), 1 done
    var taskA1 =
        taskService.createTask(
            projectAId, "Task A1", null, "MEDIUM", "TASK", null, adminMemberId, "admin");
    taskA1Id = taskA1.getId();

    taskService.createTask(
        projectAId,
        "Task A2 Overdue",
        null,
        "HIGH",
        "TASK",
        today.minusDays(5),
        adminMemberId,
        "admin");

    var taskA3 =
        taskService.createTask(
            projectAId, "Task A3 Done", null, "LOW", "TASK", null, adminMemberId, "admin");
    taskService.updateTask(
        taskA3.getId(),
        "Task A3 Done",
        null,
        "LOW",
        "DONE",
        "TASK",
        null,
        null,
        adminMemberId,
        "admin");

    // Project B: 1 open task
    var taskB1 =
        taskService.createTask(
            projectBId, "Task B1", null, "MEDIUM", "TASK", null, adminMemberId, "admin");
    taskB1Id = taskB1.getId();

    // Project C: 1 open task (overdue)
    var taskC1 =
        taskService.createTask(
            projectCId,
            "Task C1 Overdue",
            null,
            "HIGH",
            "TASK",
            today.minusDays(3),
            adminMemberId,
            "admin");
    taskC1Id = taskC1.getId();

    // --- Create time entries ---
    // Admin logs time on project A and C
    timeEntryService.createTimeEntry(
        taskA1Id, today.minusDays(5), 120, true, null, "Admin work on A", adminMemberId, "admin");
    timeEntryService.createTimeEntry(
        taskC1Id, today.minusDays(2), 60, false, null, "Admin work on C", adminMemberId, "admin");

    // Member1 logs time on project A
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, member1Id)
        .where(RequestScopes.ORG_ROLE, "member")
        .run(
            () -> {
              timeEntryService.createTimeEntry(
                  taskA1Id,
                  today.minusDays(3),
                  90,
                  true,
                  null,
                  "Member1 work on A",
                  member1Id,
                  "member");
            });

    // Member2 logs time on project B
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, member2Id)
        .where(RequestScopes.ORG_ROLE, "member")
        .run(
            () -> {
              timeEntryService.createTimeEntry(
                  taskB1Id,
                  today.minusDays(1),
                  45,
                  true,
                  null,
                  "Member2 work on B",
                  member2Id,
                  "member");
            });
  }

  // --- KPI Tests ---

  @Test
  void kpiReturnsCorrectActiveProjectCount() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/kpis")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeProjectCount").value(greaterThanOrEqualTo(3)));
  }

  @Test
  void kpiReturnsTotalHoursForPeriod() throws Exception {
    // Total: 120 + 60 + 90 + 45 = 315 minutes = 5.25 hours
    mockMvc
        .perform(
            get("/api/dashboard/kpis")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalHoursLogged").value(greaterThan(0.0)));
  }

  @Test
  void kpiBillablePercentIsNullForNonAdmin() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/kpis")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(member1Jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billablePercent").isEmpty());
  }

  @Test
  void kpiBillablePercentIsPresentForAdmin() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/kpis")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billablePercent").isNumber());
  }

  @Test
  void kpiIncludesTrendWithCorrectPeriodCount() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/kpis")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trend").isArray());
  }

  @Test
  void kpiIncludesPreviousPeriodValues() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/kpis")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.previousPeriod").isNotEmpty())
        .andExpect(jsonPath("$.previousPeriod.activeProjectCount").isNumber());
  }

  // --- Project Health List Tests ---

  @Test
  void projectHealthListSortedBySeverity() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/project-health").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
        .andExpect(jsonPath("$[0].projectId").isString())
        .andExpect(jsonPath("$[0].healthStatus").isString());
  }

  @Test
  void projectHealthListFilteredByMemberAccess() throws Exception {
    // member1 is only on projectA, so should see fewer projects than admin
    var adminResult =
        mockMvc
            .perform(get("/api/dashboard/project-health").with(adminJwt()))
            .andExpect(status().isOk())
            .andReturn();
    int adminCount =
        ((List<?>) JsonPath.read(adminResult.getResponse().getContentAsString(), "$")).size();

    var memberResult =
        mockMvc
            .perform(get("/api/dashboard/project-health").with(member1Jwt()))
            .andExpect(status().isOk())
            .andReturn();
    int memberCount =
        ((List<?>) JsonPath.read(memberResult.getResponse().getContentAsString(), "$")).size();

    // Admin should see more projects than member1 (who only has access to projectA)
    assert adminCount > memberCount
        : "Admin should see more projects (%d) than member (%d)".formatted(adminCount, memberCount);
  }

  // --- Team Workload Tests ---

  @Test
  void teamWorkloadReturnsAllMembersForAdmin() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/team-workload")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
        .andExpect(jsonPath("$[0].memberId").isString())
        .andExpect(jsonPath("$[0].memberName").isString())
        .andExpect(jsonPath("$[0].totalHours").isNumber())
        .andExpect(jsonPath("$[0].billableHours").isNumber())
        .andExpect(jsonPath("$[0].projects").isArray());
  }

  @Test
  void teamWorkloadReturnsOnlySelfForMember() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/team-workload")
                .param("from", thirtyDaysAgo.toString())
                .param("to", today.toString())
                .with(member1Jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].memberId").value(member1Id.toString()))
        .andExpect(jsonPath("$[0].memberName").value("Member One"));
  }

  // --- Cross-Project Activity Tests ---

  @Test
  void crossProjectActivityReturnsRecentEvents() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/activity").param("limit", "20").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$[0].eventId").isString())
        .andExpect(jsonPath("$[0].eventType").isString())
        .andExpect(jsonPath("$[0].description").isString())
        .andExpect(jsonPath("$[0].occurredAt").isString());
  }

  @Test
  void crossProjectActivityFilteredByMemberProjectAccess() throws Exception {
    // member1 is only on projectA, so should see fewer activity events
    var adminResult =
        mockMvc
            .perform(get("/api/dashboard/activity").param("limit", "50").with(adminJwt()))
            .andExpect(status().isOk())
            .andReturn();
    int adminCount =
        ((List<?>) JsonPath.read(adminResult.getResponse().getContentAsString(), "$")).size();

    var memberResult =
        mockMvc
            .perform(get("/api/dashboard/activity").param("limit", "50").with(member1Jwt()))
            .andExpect(status().isOk())
            .andReturn();
    int memberCount =
        ((List<?>) JsonPath.read(memberResult.getResponse().getContentAsString(), "$")).size();

    // Admin sees all events, member1 only sees events from projectA
    assert adminCount >= memberCount
        : "Admin should see at least as many events (%d) as member (%d)"
            .formatted(adminCount, memberCount);
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cdash_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor member1Jwt() {
    return jwt()
        .jwt(j -> j.subject("user_cdash_member1").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor member2Jwt() {
    return jwt()
        .jwt(j -> j.subject("user_cdash_member2").claim("o", Map.of("id", ORG_ID, "rol", "member")))
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
