package io.b2mash.b2b.b2bstrawman.timeentry;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for Project Time Summary endpoints (Epic 46A). Verifies aggregation
 * correctness, access control, date filtering, and tenant isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectTimeSummaryIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_pts_test";
  private static final String ORG_B_ID = "org_pts_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String projectId;
  private String emptyProjectId;
  private String memberIdOwner;
  private String memberIdMember;
  private String memberIdMember2;
  private String taskId1;
  private String taskId2;
  private String taskId3;

  // Tenant B for isolation tests
  private String projectBId;
  private String taskBId;

  @BeforeAll
  void provisionTenantsAndSeedData() throws Exception {
    // Provision tenant A (Pro plan)
    provisioningService.provisionTenant(ORG_ID, "PTS Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Provision tenant B (Pro plan)
    provisioningService.provisionTenant(ORG_B_ID, "PTS Test Org B");
    planSyncService.syncPlan(ORG_B_ID, "pro-plan");

    // Sync members for tenant A
    memberIdOwner =
        syncMember(ORG_ID, "user_pts_owner", "pts_owner@test.com", "PTS Owner", "owner");
    memberIdMember =
        syncMember(ORG_ID, "user_pts_member", "pts_member@test.com", "PTS Member", "member");
    memberIdMember2 =
        syncMember(ORG_ID, "user_pts_member2", "pts_member2@test.com", "PTS Member2", "member");

    // Sync member for tenant B
    syncMember(ORG_B_ID, "user_pts_tenant_b", "pts_tenantb@test.com", "PTS Tenant B", "owner");

    // Create project in tenant A
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "PTS Summary Project", "description": "For time summary tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    // Create empty project (no time entries)
    var emptyProjectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Empty Project", "description": "No time entries"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    emptyProjectId = extractIdFromLocation(emptyProjectResult);

    // Add members to both projects
    addMemberToProject(projectId, memberIdMember);
    addMemberToProject(projectId, memberIdMember2);
    addMemberToProject(emptyProjectId, memberIdMember);

    // Create 3 tasks in the project
    taskId1 = createTask("Task Alpha");
    taskId2 = createTask("Task Beta");
    taskId3 = createTask("Task Gamma");

    // Seed time entries across members, tasks, dates, and billable states:
    // Owner: Task1, 2026-02-03, 60min, billable
    createTimeEntry(ownerJwt(), taskId1, "2026-02-03", 60, true, "Owner on Alpha");
    // Owner: Task1, 2026-02-04, 90min, non-billable
    createTimeEntry(ownerJwt(), taskId1, "2026-02-04", 90, false, "Owner on Alpha day2");
    // Member: Task2, 2026-02-03, 45min, billable
    createTimeEntry(memberJwt(), taskId2, "2026-02-03", 45, true, "Member on Beta");
    // Member: Task2, 2026-02-05, 30min, billable
    createTimeEntry(memberJwt(), taskId2, "2026-02-05", 30, true, "Member on Beta day2");
    // Member2: Task3, 2026-02-03, 120min, non-billable
    createTimeEntry(member2Jwt(), taskId3, "2026-02-03", 120, false, "Member2 on Gamma");
    // Member2: Task1, 2026-02-06, 15min, billable
    createTimeEntry(member2Jwt(), taskId1, "2026-02-06", 15, true, "Member2 on Alpha");

    // Setup tenant B project with time entry
    var projectBResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(tenantBOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Tenant B Project", "description": "B project"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectBId = extractIdFromLocation(projectBResult);

    var taskBResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectBId + "/tasks")
                    .with(tenantBOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Tenant B Task"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    taskBId = extractIdFromLocation(taskBResult);

    createTimeEntry(tenantBOwnerJwt(), taskBId, "2026-02-03", 200, true, "Tenant B entry");
  }

  // --- Task 46.5: Project summary integration tests ---

  @Test
  void projectTimeSummaryReturnsCorrectTotals() throws Exception {
    // Total: 60+90+45+30+120+15 = 360 min
    // Billable: 60+45+30+15 = 150 min
    // Non-billable: 90+120 = 210 min
    // Contributors: 3 (owner, member, member2)
    // Entries: 6
    mockMvc
        .perform(get("/api/projects/" + projectId + "/time-summary").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(projectId))
        .andExpect(jsonPath("$.billableMinutes").value(150))
        .andExpect(jsonPath("$.nonBillableMinutes").value(210))
        .andExpect(jsonPath("$.totalMinutes").value(360))
        .andExpect(jsonPath("$.contributorCount").value(3))
        .andExpect(jsonPath("$.entryCount").value(6));
  }

  @Test
  void emptyProjectReturnsZeroes() throws Exception {
    mockMvc
        .perform(get("/api/projects/" + emptyProjectId + "/time-summary").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billableMinutes").value(0))
        .andExpect(jsonPath("$.nonBillableMinutes").value(0))
        .andExpect(jsonPath("$.totalMinutes").value(0))
        .andExpect(jsonPath("$.contributorCount").value(0))
        .andExpect(jsonPath("$.entryCount").value(0));
  }

  @Test
  void byMemberBreakdownReturnsPerMemberRows() throws Exception {
    mockMvc
        .perform(get("/api/projects/" + projectId + "/time-summary/by-member").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        // Each member should have their aggregated totals
        .andExpect(jsonPath("$[?(@.memberName == 'PTS Owner')].totalMinutes").value(150))
        .andExpect(jsonPath("$[?(@.memberName == 'PTS Owner')].billableMinutes").value(60))
        .andExpect(jsonPath("$[?(@.memberName == 'PTS Owner')].nonBillableMinutes").value(90))
        .andExpect(jsonPath("$[?(@.memberName == 'PTS Member')].totalMinutes").value(75))
        .andExpect(jsonPath("$[?(@.memberName == 'PTS Member')].billableMinutes").value(75))
        .andExpect(jsonPath("$[?(@.memberName == 'PTS Member2')].totalMinutes").value(135))
        .andExpect(jsonPath("$[?(@.memberName == 'PTS Member2')].billableMinutes").value(15))
        .andExpect(jsonPath("$[?(@.memberName == 'PTS Member2')].nonBillableMinutes").value(120));
  }

  @Test
  void byMemberRestrictedToLeadContributorGets403() throws Exception {
    // memberJwt() is a contributor (org:member with contributor project role)
    mockMvc
        .perform(get("/api/projects/" + projectId + "/time-summary/by-member").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanAccessByMember() throws Exception {
    // Sync an admin and add to project
    var memberIdAdmin =
        syncMember(ORG_ID, "user_pts_admin", "pts_admin@test.com", "PTS Admin", "admin");

    mockMvc
        .perform(get("/api/projects/" + projectId + "/time-summary/by-member").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)));
  }

  @Test
  void byTaskBreakdownReturnsPerTaskRows() throws Exception {
    mockMvc
        .perform(get("/api/projects/" + projectId + "/time-summary/by-task").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        // Task Alpha: 60+90+15 = 165, billable=60+15=75, entries=3
        .andExpect(jsonPath("$[?(@.taskTitle == 'Task Alpha')].totalMinutes").value(165))
        .andExpect(jsonPath("$[?(@.taskTitle == 'Task Alpha')].billableMinutes").value(75))
        .andExpect(jsonPath("$[?(@.taskTitle == 'Task Alpha')].entryCount").value(3))
        // Task Beta: 45+30 = 75, billable=75, entries=2
        .andExpect(jsonPath("$[?(@.taskTitle == 'Task Beta')].totalMinutes").value(75))
        .andExpect(jsonPath("$[?(@.taskTitle == 'Task Beta')].billableMinutes").value(75))
        .andExpect(jsonPath("$[?(@.taskTitle == 'Task Beta')].entryCount").value(2))
        // Task Gamma: 120, billable=0, entries=1
        .andExpect(jsonPath("$[?(@.taskTitle == 'Task Gamma')].totalMinutes").value(120))
        .andExpect(jsonPath("$[?(@.taskTitle == 'Task Gamma')].billableMinutes").value(0))
        .andExpect(jsonPath("$[?(@.taskTitle == 'Task Gamma')].entryCount").value(1));
  }

  @Test
  void nonProjectMemberGets404() throws Exception {
    // Tenant B owner cannot see tenant A's project summary
    mockMvc
        .perform(get("/api/projects/" + projectId + "/time-summary").with(tenantBOwnerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void multipleEntriesAggregateCorrectly() throws Exception {
    // Already verified through projectTimeSummaryReturnsCorrectTotals
    // Additional check: verify billable/non-billable separation in summary
    mockMvc
        .perform(get("/api/projects/" + projectId + "/time-summary").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.billableMinutes")
                .value(org.hamcrest.Matchers.greaterThan(0))) // billable entries exist
        .andExpect(
            jsonPath("$.nonBillableMinutes")
                .value(org.hamcrest.Matchers.greaterThan(0))); // non-billable entries exist
  }

  @Test
  void memberCanViewProjectTotals() throws Exception {
    // Contributors (MEMBER+) can view project totals
    mockMvc
        .perform(get("/api/projects/" + projectId + "/time-summary").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalMinutes").value(360));
  }

  @Test
  void memberCanViewByTaskBreakdown() throws Exception {
    // Contributors can view per-task breakdown
    mockMvc
        .perform(get("/api/projects/" + projectId + "/time-summary/by-task").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)));
  }

  // --- Task 46.6: Date range filtering tests ---

  @Test
  void dateRangeFiltersExcludeEntriesOutsideRange() throws Exception {
    // Only entries on 2026-02-03: owner 60min billable + member 45min billable + member2 120min
    // non-billable
    // = 225 total, 105 billable, 120 non-billable, 3 contributors, 3 entries
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/time-summary")
                .param("from", "2026-02-03")
                .param("to", "2026-02-03")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalMinutes").value(225))
        .andExpect(jsonPath("$.billableMinutes").value(105))
        .andExpect(jsonPath("$.nonBillableMinutes").value(120))
        .andExpect(jsonPath("$.contributorCount").value(3))
        .andExpect(jsonPath("$.entryCount").value(3));
  }

  @Test
  void nullDateRangeReturnsAllTimeTotals() throws Exception {
    // No from/to params should return everything
    mockMvc
        .perform(get("/api/projects/" + projectId + "/time-summary").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalMinutes").value(360))
        .andExpect(jsonPath("$.entryCount").value(6));
  }

  @Test
  void dateRangeOnByTaskEndpoint() throws Exception {
    // 2026-02-04 to 2026-02-06: owner 90min on task1, member 30min on task2, member2 15min on
    // task1
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/time-summary/by-task")
                .param("from", "2026-02-04")
                .param("to", "2026-02-06")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2))) // Only task1 and task2 have entries in this range
        .andExpect(jsonPath("$[?(@.taskTitle == 'Task Alpha')].totalMinutes").value(105))
        .andExpect(jsonPath("$[?(@.taskTitle == 'Task Beta')].totalMinutes").value(30));
  }

  // --- Task 46.7: Tenant isolation tests ---

  @Test
  void timeEntriesInTenantADoNotAppearInTenantBSummary() throws Exception {
    // Tenant B's project summary should only include tenant B's entries
    mockMvc
        .perform(get("/api/projects/" + projectBId + "/time-summary").with(tenantBOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalMinutes").value(200))
        .andExpect(jsonPath("$.billableMinutes").value(200))
        .andExpect(jsonPath("$.entryCount").value(1))
        .andExpect(jsonPath("$.contributorCount").value(1));
  }

  @Test
  void tenantACannotAccessTenantBProjectSummary() throws Exception {
    mockMvc
        .perform(get("/api/projects/" + projectBId + "/time-summary").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  // --- Helpers ---

  private void createTimeEntry(
      JwtRequestPostProcessor jwt,
      String taskId,
      String date,
      int durationMinutes,
      boolean billable,
      String description)
      throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "%s",
                      "durationMinutes": %d,
                      "billable": %s,
                      "description": "%s"
                    }
                    """
                        .formatted(date, durationMinutes, billable, description)))
        .andExpect(status().isCreated());
  }

  private String createTask(String title) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s", "priority": "MEDIUM"}
                        """
                            .formatted(title)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractIdFromLocation(result);
  }

  private void addMemberToProject(String projId, String memId) throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memId)))
        .andExpect(status().isCreated());
  }

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
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

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pts_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pts_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pts_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor member2Jwt() {
    return jwt()
        .jwt(j -> j.subject("user_pts_member2").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor tenantBOwnerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pts_tenant_b").claim("o", Map.of("id", ORG_B_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
