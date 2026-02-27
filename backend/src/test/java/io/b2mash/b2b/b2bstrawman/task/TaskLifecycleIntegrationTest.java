package io.b2mash.b2b.b2bstrawman.task;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskLifecycleIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_lifecycle_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String projectId;
  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;

  @BeforeAll
  void provisionTenantsAndProject() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Lifecycle Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(ORG_ID, "user_lc_owner", "lc_owner@test.com", "Lifecycle Owner", "owner");
    memberIdAdmin =
        syncMember(ORG_ID, "user_lc_admin", "lc_admin@test.com", "Lifecycle Admin", "admin");
    memberIdMember =
        syncMember(ORG_ID, "user_lc_member", "lc_member@test.com", "Lifecycle Member", "member");

    // Create a project (owner is auto-assigned as lead)
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Lifecycle Test Project", "description": "For lifecycle tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    // Add admin and member to the project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberIdAdmin)))
        .andExpect(status().isCreated());

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

  // --- Complete endpoint tests ---

  @Test
  void shouldCompleteTaskAsAssignee() throws Exception {
    String taskId = createTask("Complete as assignee");

    // Claim the task as member
    mockMvc
        .perform(post("/api/tasks/" + taskId + "/claim").with(memberJwt()))
        .andExpect(status().isOk());

    // Complete as the assignee (member)
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/complete").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.completedAt", notNullValue()))
        .andExpect(jsonPath("$.completedBy").value(memberIdMember))
        .andExpect(jsonPath("$.completedByName").value("Lifecycle Member"));
  }

  @Test
  void shouldCompleteTaskAsAdmin() throws Exception {
    String taskId = createTask("Complete as admin");

    // Claim the task as member
    mockMvc
        .perform(post("/api/tasks/" + taskId + "/claim").with(memberJwt()))
        .andExpect(status().isOk());

    // Admin completes the task
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/complete").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.completedAt", notNullValue()))
        .andExpect(jsonPath("$.completedBy").value(memberIdAdmin));
  }

  @Test
  void shouldRejectCompleteForNonAssigneeMember() throws Exception {
    String taskId = createTask("Reject non-assignee complete");

    // Claim the task as admin
    mockMvc
        .perform(post("/api/tasks/" + taskId + "/claim").with(adminJwt()))
        .andExpect(status().isOk());

    // Regular member (not assignee) tries to complete — should be 403
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/complete").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectCompleteForOpenTask() throws Exception {
    String taskId = createTask("Reject complete on open");

    // Try to complete task in OPEN status — should be 400 (invalid state transition)
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/complete").with(ownerJwt()))
        .andExpect(status().isBadRequest());
  }

  // --- Cancel endpoint tests ---

  @Test
  void shouldCancelTaskAsAdmin() throws Exception {
    String taskId = createTask("Cancel as admin");

    // Admin cancels the task
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/cancel").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.cancelledAt", notNullValue()));
  }

  @Test
  void shouldRejectCancelForRegularMember() throws Exception {
    String taskId = createTask("Reject cancel by member");

    // Regular member tries to cancel — should be 403
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/cancel").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // --- Reopen endpoint tests ---

  @Test
  void shouldReopenCompletedTaskAsAdmin() throws Exception {
    String taskId = createTask("Reopen completed task");

    // Claim then complete the task
    mockMvc
        .perform(post("/api/tasks/" + taskId + "/claim").with(memberJwt()))
        .andExpect(status().isOk());
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/complete").with(memberJwt()))
        .andExpect(status().isOk());

    // Admin reopens
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/reopen").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.completedAt", nullValue()))
        .andExpect(jsonPath("$.completedBy", nullValue()));
  }

  @Test
  void shouldReopenCancelledTaskAsAssignee() throws Exception {
    String taskId = createTask("Reopen cancelled task");

    // Claim the task as member, then admin cancels it
    mockMvc
        .perform(post("/api/tasks/" + taskId + "/claim").with(memberJwt()))
        .andExpect(status().isOk());
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/cancel").with(adminJwt()))
        .andExpect(status().isOk());

    // Assignee (member) reopens — the assignee is cleared by cancel via reopen, but
    // the task entity stores assigneeId through cancel. Let's check with admin instead
    // since cancel doesn't clear assigneeId but reopen permission checks assigneeId.
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/reopen").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.cancelledAt", nullValue()));
  }

  @Test
  void shouldRejectReopenForOpenTask() throws Exception {
    String taskId = createTask("Reject reopen open task");

    // Try to reopen an already OPEN task — should be 400
    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/reopen").with(ownerJwt()))
        .andExpect(status().isBadRequest());
  }

  // --- Status filter tests ---

  @Test
  void shouldFilterByMultipleStatuses() throws Exception {
    String taskId1 = createTask("Filter multi-status open");
    String taskId2 = createTask("Filter multi-status in-progress");
    String taskId3 = createTask("Filter multi-status cancelled");

    // Claim task2 to make it IN_PROGRESS
    mockMvc
        .perform(post("/api/tasks/" + taskId2 + "/claim").with(memberJwt()))
        .andExpect(status().isOk());

    // Cancel task3
    mockMvc
        .perform(patch("/api/tasks/" + taskId3 + "/cancel").with(adminJwt()))
        .andExpect(status().isOk());

    // Filter for OPEN,IN_PROGRESS — should return task1 and task2 (not task3)
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/tasks")
                .with(ownerJwt())
                .param("status", "OPEN,IN_PROGRESS"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')]", taskId1).exists())
        .andExpect(jsonPath("$[?(@.id == '%s')]", taskId2).exists())
        .andExpect(jsonPath("$[?(@.id == '%s')]", taskId3).doesNotExist());
  }

  @Test
  void shouldDefaultToNonTerminalStatuses() throws Exception {
    String taskIdOpen = createTask("Default filter open");
    String taskIdDone = createTask("Default filter done");

    // Claim then complete taskIdDone
    mockMvc
        .perform(post("/api/tasks/" + taskIdDone + "/claim").with(memberJwt()))
        .andExpect(status().isOk());
    mockMvc
        .perform(patch("/api/tasks/" + taskIdDone + "/complete").with(memberJwt()))
        .andExpect(status().isOk());

    // Default (no status param) should exclude DONE tasks
    mockMvc
        .perform(get("/api/projects/" + projectId + "/tasks").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')]", taskIdOpen).exists())
        .andExpect(jsonPath("$[?(@.id == '%s')]", taskIdDone).doesNotExist());
  }

  @Test
  void shouldReturnCompletedFieldsInTaskResponse() throws Exception {
    String taskId = createTask("Completed fields test");

    // Claim and complete
    mockMvc
        .perform(post("/api/tasks/" + taskId + "/claim").with(memberJwt()))
        .andExpect(status().isOk());

    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/complete").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completedAt", notNullValue()))
        .andExpect(jsonPath("$.completedBy").value(memberIdMember))
        .andExpect(jsonPath("$.completedByName").value("Lifecycle Member"))
        .andExpect(jsonPath("$.cancelledAt", nullValue()));

    // Verify GET also returns the fields
    mockMvc
        .perform(get("/api/tasks/" + taskId).with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completedAt", notNullValue()))
        .andExpect(jsonPath("$.completedBy").value(memberIdMember))
        .andExpect(jsonPath("$.completedByName").value("Lifecycle Member"))
        .andExpect(jsonPath("$.cancelledAt", nullValue()));
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

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_lc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_lc_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_lc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
