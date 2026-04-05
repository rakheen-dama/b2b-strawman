package io.b2mash.b2b.b2bstrawman.mywork;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the My Work endpoints. Verifies cross-project task aggregation, member time
 * entries, time summaries, filtering, and tenant isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyWorkIntegrationTest {
  private static final String ORG_ID = "org_mywork_test";
  private static final String ORG_B_ID = "org_mywork_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String memberIdOwner;
  private String memberIdMember;
  private String memberIdMemberB;
  private String projectAId;
  private String projectBId;
  private String projectCId; // project where member is NOT a member
  private String assignedTaskAId;
  private String assignedTaskBId;
  private String unassignedTaskAId;
  private String unassignedTaskBId;
  private String closedTaskId;
  private String otherOrgTaskId; // task in a project member is not part of

  @BeforeAll
  void provisionTenantsAndSeedData() throws Exception {
    // Provision tenant A (Pro) and tenant B (Pro)
    provisioningService.provisionTenant(ORG_ID, "MyWork Test Org", null);

    provisioningService.provisionTenant(ORG_B_ID, "MyWork Test Org B", null);

    // Sync members for tenant A
    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_mw_owner", "mw_owner@test.com", "MW Owner", "owner");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_mw_member", "mw_member@test.com", "MW Member", "member");
    // Additional member not added to projects (for isolation tests)
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_mw_outsider", "mw_outsider@test.com", "MW Outsider", "member");

    // Sync member for tenant B
    memberIdMemberB =
        TestMemberHelper.syncMember(
            mockMvc, ORG_B_ID, "user_mw_tenant_b", "mw_tenantb@test.com", "Tenant B User", "owner");

    // Create Project A — owner is auto-lead
    projectAId =
        createProject(
            TestJwtFactory.ownerJwt(ORG_ID, "user_mw_owner"),
            "MW Project A",
            "Project A for my work tests");
    // Add member to Project A
    addMemberToProject(
        TestJwtFactory.ownerJwt(ORG_ID, "user_mw_owner"), projectAId, memberIdMember);

    // Create Project B — owner is auto-lead
    projectBId =
        createProject(
            TestJwtFactory.ownerJwt(ORG_ID, "user_mw_owner"),
            "MW Project B",
            "Project B for my work tests");
    // Add member to Project B
    addMemberToProject(
        TestJwtFactory.ownerJwt(ORG_ID, "user_mw_owner"), projectBId, memberIdMember);

    // Create Project C — member is NOT a project member (only owner is lead)
    projectCId =
        createProject(
            TestJwtFactory.ownerJwt(ORG_ID, "user_mw_owner"),
            "MW Project C",
            "Project C - member excluded");

    // Create tasks in Project A
    assignedTaskAId =
        createTask(
            TestJwtFactory.ownerJwt(ORG_ID, "user_mw_owner"),
            projectAId,
            "Assigned Task A",
            "HIGH",
            null);
    // Claim task for member
    mockMvc
        .perform(
            post("/api/tasks/" + assignedTaskAId + "/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member")))
        .andExpect(status().isOk());

    unassignedTaskAId =
        createTask(
            TestJwtFactory.ownerJwt(ORG_ID, "user_mw_owner"),
            projectAId,
            "Unassigned Task A",
            "MEDIUM",
            null);

    // Create tasks in Project B
    assignedTaskBId =
        createTask(
            TestJwtFactory.ownerJwt(ORG_ID, "user_mw_owner"),
            projectBId,
            "Assigned Task B",
            "LOW",
            "2026-03-01");
    mockMvc
        .perform(
            post("/api/tasks/" + assignedTaskBId + "/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member")))
        .andExpect(status().isOk());

    unassignedTaskBId =
        createTask(
            TestJwtFactory.ownerJwt(ORG_ID, "user_mw_owner"),
            projectBId,
            "Unassigned Task B",
            "HIGH",
            null);

    // Create a DONE task (should not appear in assigned results)
    closedTaskId =
        createTask(
            TestJwtFactory.ownerJwt(ORG_ID, "user_mw_owner"),
            projectAId,
            "Closed Task",
            "LOW",
            null);
    mockMvc
        .perform(
            post("/api/tasks/" + closedTaskId + "/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member")))
        .andExpect(status().isOk());
    // Update task status to DONE
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/tasks/" + closedTaskId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Closed Task", "priority": "LOW", "status": "DONE"}
                    """))
        .andExpect(status().isOk());

    // Create task in Project C (member is NOT a project member)
    otherOrgTaskId =
        createTask(
            TestJwtFactory.ownerJwt(ORG_ID, "user_mw_owner"),
            projectCId,
            "Task In Non-Member Project",
            "HIGH",
            null);

    // Create time entries for member in different projects
    createTimeEntry(
        TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"),
        assignedTaskAId,
        "2026-02-10",
        60,
        true,
        "Work on task A");
    createTimeEntry(
        TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"),
        assignedTaskAId,
        "2026-02-11",
        30,
        false,
        "More work on task A");
    createTimeEntry(
        TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"),
        assignedTaskBId,
        "2026-02-10",
        120,
        true,
        "Work on task B");
    createTimeEntry(
        TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"),
        assignedTaskBId,
        "2026-02-12",
        45,
        true,
        "Continue task B");

    // Create time entry in tenant B
    var tenantBProjectId =
        createProject(
            TestJwtFactory.ownerJwt(ORG_B_ID, "user_mw_tenant_b"),
            "Tenant B Project",
            "Should be isolated");
    var tenantBTaskId =
        createTask(
            TestJwtFactory.ownerJwt(ORG_B_ID, "user_mw_tenant_b"),
            tenantBProjectId,
            "Tenant B Task",
            "HIGH",
            null);
    createTimeEntry(
        TestJwtFactory.ownerJwt(ORG_B_ID, "user_mw_tenant_b"),
        tenantBTaskId,
        "2026-02-10",
        180,
        true,
        "Tenant B work");
  }

  // --- My Assigned Tasks ---

  @Test
  void shouldReturnAssignedTasksForMember() throws Exception {
    mockMvc
        .perform(
            get("/api/my-work/tasks")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"))
                .param("filter", "assigned"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assigned").isArray())
        .andExpect(jsonPath("$.assigned", hasSize(2)))
        .andExpect(jsonPath("$.assigned[*].title").isArray())
        .andExpect(jsonPath("$.assigned[*].projectName").isArray())
        .andExpect(jsonPath("$.unassigned").isArray())
        .andExpect(jsonPath("$.unassigned", hasSize(0)));
  }

  @Test
  void shouldReturnUnassignedTasksInMemberProjects() throws Exception {
    mockMvc
        .perform(
            get("/api/my-work/tasks")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"))
                .param("filter", "unassigned"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assigned", hasSize(0)))
        .andExpect(jsonPath("$.unassigned").isArray())
        .andExpect(jsonPath("$.unassigned", hasSize(greaterThanOrEqualTo(2))))
        .andExpect(jsonPath("$.unassigned[*].status", everyItem(org.hamcrest.Matchers.is("OPEN"))));
  }

  @Test
  void shouldReturnBothAssignedAndUnassignedWithFilterAll() throws Exception {
    mockMvc
        .perform(
            get("/api/my-work/tasks")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"))
                .param("filter", "all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assigned", hasSize(2)))
        .andExpect(jsonPath("$.unassigned", hasSize(greaterThanOrEqualTo(2))));
  }

  @Test
  void shouldReturnBothWhenNoFilterSpecified() throws Exception {
    mockMvc
        .perform(get("/api/my-work/tasks").with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assigned", hasSize(2)))
        .andExpect(jsonPath("$.unassigned", hasSize(greaterThanOrEqualTo(2))));
  }

  @Test
  void shouldFilterByProjectId() throws Exception {
    mockMvc
        .perform(
            get("/api/my-work/tasks")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"))
                .param("projectId", projectAId))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.assigned[*].projectId", everyItem(org.hamcrest.Matchers.is(projectAId))))
        .andExpect(
            jsonPath("$.unassigned[*].projectId", everyItem(org.hamcrest.Matchers.is(projectAId))));
  }

  @Test
  void shouldExcludeTasksInNonMemberProjects() throws Exception {
    // Member is not in Project C — its tasks should not appear
    mockMvc
        .perform(get("/api/my-work/tasks").with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unassigned[*].title", everyItem(not("Task In Non-Member Project"))));
  }

  @Test
  void shouldNotIncludeClosedTasksInAssigned() throws Exception {
    mockMvc
        .perform(
            get("/api/my-work/tasks")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"))
                .param("filter", "assigned"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assigned[*].title", everyItem(not("Closed Task"))));
  }

  @Test
  void shouldIncludeTotalTimeMinutesOnTasks() throws Exception {
    mockMvc
        .perform(
            get("/api/my-work/tasks")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"))
                .param("filter", "assigned"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assigned[0].totalTimeMinutes").isNumber());
  }

  // --- My Time Entries ---

  @Test
  void shouldReturnMyTimeEntries() throws Exception {
    mockMvc
        .perform(
            get("/api/my-work/time-entries")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"))
                .param("from", "2026-02-10")
                .param("to", "2026-02-12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(4)))
        .andExpect(jsonPath("$[0].taskTitle").exists())
        .andExpect(jsonPath("$[0].projectName").exists());
  }

  @Test
  void shouldFilterTimeEntriesByDateRange() throws Exception {
    mockMvc
        .perform(
            get("/api/my-work/time-entries")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"))
                .param("from", "2026-02-10")
                .param("to", "2026-02-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(2))); // Only the 2 entries on Feb 10
  }

  // --- My Time Summary ---

  @Test
  void shouldReturnTimeSummaryTotals() throws Exception {
    mockMvc
        .perform(
            get("/api/my-work/time-summary")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"))
                .param("from", "2026-02-10")
                .param("to", "2026-02-12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberId").value(memberIdMember))
        .andExpect(jsonPath("$.fromDate").value("2026-02-10"))
        .andExpect(jsonPath("$.toDate").value("2026-02-12"))
        .andExpect(jsonPath("$.totalMinutes").value(255)) // 60+30+120+45
        .andExpect(jsonPath("$.billableMinutes").value(225)) // 60+120+45
        .andExpect(jsonPath("$.nonBillableMinutes").value(30));
  }

  @Test
  void shouldReturnTimeSummaryByProjectBreakdown() throws Exception {
    mockMvc
        .perform(
            get("/api/my-work/time-summary")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_member"))
                .param("from", "2026-02-10")
                .param("to", "2026-02-12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.byProject").isArray())
        .andExpect(jsonPath("$.byProject", hasSize(2)))
        .andExpect(jsonPath("$.byProject[*].projectName").isArray())
        .andExpect(jsonPath("$.byProject[*].projectId").isArray());
  }

  @Test
  void shouldReturnEmptyStateWhenNoTasks() throws Exception {
    // Outsider member has no assigned tasks and is in no projects
    mockMvc
        .perform(
            get("/api/my-work/tasks").with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_outsider")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assigned", hasSize(0)))
        .andExpect(jsonPath("$.unassigned", hasSize(0)));
  }

  @Test
  void shouldReturnEmptyTimeEntriesWhenNoneExist() throws Exception {
    mockMvc
        .perform(
            get("/api/my-work/time-entries")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_mw_outsider"))
                .param("from", "2026-02-10")
                .param("to", "2026-02-12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  // --- Helpers ---

  private String createProject(JwtRequestPostProcessor jwt, String name, String description)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "description": "%s"}
                        """
                            .formatted(name, description)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractIdFromLocation(result);
  }

  private void addMemberToProject(JwtRequestPostProcessor jwt, String projectId, String memberId)
      throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberId)))
        .andExpect(status().isCreated());
  }

  private String createTask(
      JwtRequestPostProcessor jwt, String projectId, String title, String priority, String dueDate)
      throws Exception {
    var dueDateField = dueDate != null ? ", \"dueDate\": \"" + dueDate + "\"" : "";
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s", "priority": "%s"%s}
                        """
                            .formatted(title, priority, dueDateField)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractIdFromLocation(result);
  }

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
}
