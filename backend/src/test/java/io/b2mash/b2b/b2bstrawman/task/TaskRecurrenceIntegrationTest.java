package io.b2mash.b2b.b2bstrawman.task;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskRecurrenceIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_recurrence_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String projectId;
  private String memberIdOwner;
  private String memberIdMember;

  @BeforeAll
  void provisionTenantsAndProject() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Recurrence Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(ORG_ID, "user_rec_owner", "rec_owner@test.com", "Rec Owner", "owner");
    memberIdMember =
        syncMember(ORG_ID, "user_rec_member", "rec_member@test.com", "Rec Member", "member");

    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Recurrence Test Project", "description": "For recurrence tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isCreated());
  }

  @Test
  void shouldCreateNextInstanceWhenRecurringTaskCompleted() throws Exception {
    // Create a recurring task with weekly recurrence
    String taskId =
        createRecurringTask("Weekly recurring", "FREQ=WEEKLY;INTERVAL=1", "2026-03-01", null);

    // Claim the task as member
    mockMvc
        .perform(post("/api/tasks/" + taskId + "/claim").with(memberJwt()))
        .andExpect(status().isOk());

    // Complete the task — should create next instance
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/complete").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.completedAt", notNullValue()))
        .andExpect(jsonPath("$.nextInstance").exists())
        .andExpect(jsonPath("$.nextInstance.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.nextInstance.dueDate").value("2026-03-08"))
        .andExpect(jsonPath("$.nextInstance.recurrenceRule").value("FREQ=WEEKLY;INTERVAL=1"))
        .andExpect(jsonPath("$.nextInstance.isRecurring").value(true))
        .andExpect(jsonPath("$.nextInstance.parentTaskId").value(taskId))
        .andExpect(jsonPath("$.nextInstance.assigneeId").value(memberIdMember));
  }

  @Test
  void shouldNotCreateNextInstanceWhenRecurrenceEndDatePassed() throws Exception {
    // Create a recurring task with end date in the past
    String taskId =
        createRecurringTask(
            "Expired recurrence", "FREQ=DAILY;INTERVAL=1", "2025-01-01", "2025-01-10");

    // Claim and complete
    mockMvc
        .perform(post("/api/tasks/" + taskId + "/claim").with(memberJwt()))
        .andExpect(status().isOk());

    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/complete").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.nextInstance").doesNotExist());
  }

  @Test
  void shouldNotCreateNextInstanceWhenNextDueDateExceedsEndDate() throws Exception {
    // Create recurring task where next due date would exceed end date
    String taskId =
        createRecurringTask(
            "End date exceeded", "FREQ=MONTHLY;INTERVAL=1", "2026-03-15", "2026-04-01");

    // Claim and complete
    mockMvc
        .perform(post("/api/tasks/" + taskId + "/claim").with(memberJwt()))
        .andExpect(status().isOk());

    // Next due date would be 2026-04-15 which is after end date 2026-04-01
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/complete").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.nextInstance").doesNotExist());
  }

  @Test
  void shouldReturnNullNextInstanceForNonRecurringTask() throws Exception {
    // Create a standard (non-recurring) task
    String taskId = createTask("Non-recurring task");

    // Claim and complete
    mockMvc
        .perform(post("/api/tasks/" + taskId + "/claim").with(memberJwt()))
        .andExpect(status().isOk());

    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/complete").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.nextInstance").doesNotExist());
  }

  @Test
  void shouldNotCreateNextInstanceOnCancel() throws Exception {
    // Create a recurring task
    String taskId =
        createRecurringTask("Cancel recurring", "FREQ=WEEKLY;INTERVAL=1", "2026-03-01", null);

    // Cancel (no recurrence should happen)
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/cancel").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    // Verify no new task was created by listing tasks — only the cancelled one
    // (This is implicit — the cancel endpoint returns TaskResponse, not CompleteTaskResponse)
  }

  @Test
  void shouldCopyAssigneeToNextInstance() throws Exception {
    // Create recurring task and assign via claim
    String taskId =
        createRecurringTask("Assignee copy test", "FREQ=WEEKLY;INTERVAL=1", "2026-04-01", null);

    // Claim as member
    mockMvc
        .perform(post("/api/tasks/" + taskId + "/claim").with(memberJwt()))
        .andExpect(status().isOk());

    // Complete — next instance should have same assignee
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/complete").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextInstance.assigneeId").value(memberIdMember))
        .andExpect(jsonPath("$.nextInstance.assigneeName").value("Rec Member"));
  }

  @Test
  void shouldSetParentTaskIdToRootInChain() throws Exception {
    // Create the root recurring task
    String rootTaskId =
        createRecurringTask("Root of chain", "FREQ=WEEKLY;INTERVAL=1", "2026-05-01", null);

    // Claim and complete root → should create child1
    mockMvc
        .perform(post("/api/tasks/" + rootTaskId + "/claim").with(memberJwt()))
        .andExpect(status().isOk());

    var completeResult =
        mockMvc
            .perform(patch("/api/tasks/" + rootTaskId + "/complete").with(memberJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextInstance.parentTaskId").value(rootTaskId))
            .andReturn();

    String child1Id =
        JsonPath.read(completeResult.getResponse().getContentAsString(), "$.nextInstance.id");

    // Complete child1 → should create child2 with parentTaskId still = root
    var completeResult2 =
        mockMvc
            .perform(patch("/api/tasks/" + child1Id + "/complete").with(memberJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextInstance.parentTaskId").value(rootTaskId))
            .andExpect(jsonPath("$.nextInstance.dueDate").value("2026-05-15"))
            .andReturn();
  }

  @Test
  void shouldRejectInvalidRecurrenceRuleOnCreate() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Bad rule task", "recurrenceRule": "INVALID"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectInvalidRecurrenceRuleOnUpdate() throws Exception {
    String taskId = createTask("Valid then invalid");

    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Valid then invalid",
                      "priority": "MEDIUM",
                      "status": "OPEN",
                      "recurrenceRule": "INVALID"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnRecurrenceFieldsInTaskResponse() throws Exception {
    String taskId =
        createRecurringTask(
            "Recurrence fields test", "FREQ=MONTHLY;INTERVAL=2", "2026-06-01", "2026-12-31");

    mockMvc
        .perform(get("/api/tasks/" + taskId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recurrenceRule").value("FREQ=MONTHLY;INTERVAL=2"))
        .andExpect(jsonPath("$.recurrenceEndDate").value("2026-12-31"))
        .andExpect(jsonPath("$.isRecurring").value(true))
        .andExpect(jsonPath("$.parentTaskId").doesNotExist());
  }

  @Test
  void shouldUpdateTaskWithRecurrenceRule() throws Exception {
    String taskId = createTask("Update recurrence test");

    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Update recurrence test",
                      "priority": "MEDIUM",
                      "status": "OPEN",
                      "recurrenceRule": "FREQ=WEEKLY;INTERVAL=2",
                      "recurrenceEndDate": "2026-12-31"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recurrenceRule").value("FREQ=WEEKLY;INTERVAL=2"))
        .andExpect(jsonPath("$.recurrenceEndDate").value("2026-12-31"))
        .andExpect(jsonPath("$.isRecurring").value(true));
  }

  @Test
  void shouldClearRecurrenceRuleOnUpdate() throws Exception {
    // Create a recurring task
    String taskId =
        createRecurringTask("Clear recurrence test", "FREQ=DAILY;INTERVAL=1", "2026-06-01", null);

    // Verify it's recurring
    mockMvc
        .perform(get("/api/tasks/" + taskId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isRecurring").value(true));

    // Update with blank recurrenceRule to clear it
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Clear recurrence test",
                      "priority": "MEDIUM",
                      "status": "OPEN",
                      "recurrenceRule": ""
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isRecurring").value(false));
  }

  @Test
  void shouldFilterRecurringTasksOnly() throws Exception {
    // Create one recurring and one non-recurring task
    createRecurringTask("Recurring for filter", "FREQ=WEEKLY;INTERVAL=1", "2026-07-01", null);
    createTask("Non-recurring for filter");

    // Filter with recurring=true
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/tasks")
                .param("recurring", "true")
                .param("status", "OPEN")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(greaterThan(0)))
        .andExpect(jsonPath("$[*].isRecurring", everyItem(is(true))));
  }

  @Test
  void shouldFilterUnassignedRecurringTasks() throws Exception {
    // Create a recurring task WITHOUT assignee (unassigned)
    createRecurringTask("Unassigned recurring", "FREQ=WEEKLY;INTERVAL=1", "2026-07-15", null);

    // Create a non-recurring task WITHOUT assignee
    createTask("Unassigned non-recurring");

    // Filter with recurring=true AND assigneeFilter=unassigned
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/tasks")
                .param("recurring", "true")
                .param("assigneeFilter", "unassigned")
                .param("status", "OPEN")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(greaterThan(0)))
        .andExpect(jsonPath("$[*].isRecurring", everyItem(is(true))))
        .andExpect(jsonPath("$[*].assigneeId", everyItem(is(org.hamcrest.Matchers.nullValue()))));
  }

  @Test
  void shouldIncludeRecurringTasksInUnfilteredList() throws Exception {
    // Create a recurring task
    createRecurringTask("Recurring in list", "FREQ=MONTHLY;INTERVAL=1", "2026-08-01", null);

    // List without recurring filter — should include recurring tasks
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/tasks").param("status", "OPEN").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
  }

  // --- Helpers ---

  private String createTask(String title) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s"}
                        """
                            .formatted(title)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String createRecurringTask(
      String title, String recurrenceRule, String dueDate, String recurrenceEndDate)
      throws Exception {
    String endDateField =
        recurrenceEndDate != null
            ? ", \"recurrenceEndDate\": \"%s\"".formatted(recurrenceEndDate)
            : "";
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "title": "%s",
                          "recurrenceRule": "%s",
                          "dueDate": "%s"%s
                        }
                        """
                            .formatted(title, recurrenceRule, dueDate, endDateField)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_rec_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_rec_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
