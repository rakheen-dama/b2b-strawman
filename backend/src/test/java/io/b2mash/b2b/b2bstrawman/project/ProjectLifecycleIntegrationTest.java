package io.b2mash.b2b.b2bstrawman.project;

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
class ProjectLifecycleIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_proj_lifecycle_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;

  @BeforeAll
  void provisionTenantAndMembers() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Project Lifecycle Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(ORG_ID, "user_plc_owner", "plc_owner@test.com", "PLC Owner", "owner");
    memberIdAdmin =
        syncMember(ORG_ID, "user_plc_admin", "plc_admin@test.com", "PLC Admin", "admin");
    memberIdMember =
        syncMember(ORG_ID, "user_plc_member", "plc_member@test.com", "PLC Member", "member");
  }

  @Test
  void shouldCompleteProjectWhenAllTasksDone() throws Exception {
    String projectId = createProject("Complete All Tasks Done");
    addMemberToProject(projectId, memberIdMember);

    String taskId = createTaskInProject(projectId, "Task to complete");
    claimAndCompleteTask(taskId);

    mockMvc
        .perform(patch("/api/projects/" + projectId + "/complete").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.completedAt", notNullValue()))
        .andExpect(jsonPath("$.completedBy").value(memberIdOwner));
  }

  @Test
  void shouldRejectCompleteWithOpenTasks() throws Exception {
    String projectId = createProject("Complete Open Tasks");
    addMemberToProject(projectId, memberIdMember);
    createTaskInProject(projectId, "Open task");

    mockMvc
        .perform(patch("/api/projects/" + projectId + "/complete").with(ownerJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectCompleteWithUnbilledTimeWithoutAck() throws Exception {
    String projectId = createProject("Unbilled No Ack");
    addMemberToProject(projectId, memberIdMember);

    String taskId = createTaskInProject(projectId, "Task with time");
    claimAndCompleteTask(taskId);

    // Create a billable time entry
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"date": "2026-02-28", "durationMinutes": 60, "billable": true, "description": "Billable work"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(patch("/api/projects/" + projectId + "/complete").with(ownerJwt()))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldCompleteProjectWithUnbilledTimeWhenAcknowledged() throws Exception {
    String projectId = createProject("Unbilled With Ack");
    addMemberToProject(projectId, memberIdMember);

    String taskId = createTaskInProject(projectId, "Task with time ack");
    claimAndCompleteTask(taskId);

    // Create a billable time entry
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"date": "2026-02-28", "durationMinutes": 60, "billable": true, "description": "Billable work"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"acknowledgeUnbilledTime": true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  @Test
  void shouldArchiveFromActive() throws Exception {
    String projectId = createProject("Archive From Active");

    mockMvc
        .perform(patch("/api/projects/" + projectId + "/archive").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"))
        .andExpect(jsonPath("$.archivedAt", notNullValue()));
  }

  @Test
  void shouldArchiveFromCompleted() throws Exception {
    String projectId = createProject("Archive From Completed");

    // Complete the project (no tasks, so no open task guardrail)
    mockMvc
        .perform(patch("/api/projects/" + projectId + "/complete").with(ownerJwt()))
        .andExpect(status().isOk());

    mockMvc
        .perform(patch("/api/projects/" + projectId + "/archive").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
  }

  @Test
  void shouldArchiveWithOpenTasksSucceeds() throws Exception {
    String projectId = createProject("Archive With Open Tasks");
    addMemberToProject(projectId, memberIdMember);
    createTaskInProject(projectId, "Open task");

    mockMvc
        .perform(patch("/api/projects/" + projectId + "/archive").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
  }

  @Test
  void shouldReopenFromCompleted() throws Exception {
    String projectId = createProject("Reopen From Completed");

    mockMvc
        .perform(patch("/api/projects/" + projectId + "/complete").with(ownerJwt()))
        .andExpect(status().isOk());

    mockMvc
        .perform(patch("/api/projects/" + projectId + "/reopen").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.completedAt", nullValue()));
  }

  @Test
  void shouldReopenFromArchived() throws Exception {
    String projectId = createProject("Reopen From Archived");

    mockMvc
        .perform(patch("/api/projects/" + projectId + "/archive").with(ownerJwt()))
        .andExpect(status().isOk());

    mockMvc
        .perform(patch("/api/projects/" + projectId + "/reopen").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.archivedAt", nullValue()));
  }

  @Test
  void shouldListProjectsDefaultActiveOnly() throws Exception {
    String activeProjectId = createProject("Active Default Filter");
    String archivedProjectId = createProject("Archived Default Filter");

    mockMvc
        .perform(patch("/api/projects/" + archivedProjectId + "/archive").with(ownerJwt()))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/projects").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')]", activeProjectId).exists())
        .andExpect(jsonPath("$[?(@.id == '%s')]", archivedProjectId).doesNotExist());
  }

  @Test
  void shouldListProjectsWithStatusFilter() throws Exception {
    String activeProjectId = createProject("Active Status Filter");
    String completedProjectId = createProject("Completed Status Filter");

    mockMvc
        .perform(patch("/api/projects/" + completedProjectId + "/complete").with(ownerJwt()))
        .andExpect(status().isOk());

    // Filter for COMPLETED only
    mockMvc
        .perform(get("/api/projects").with(ownerJwt()).param("status", "COMPLETED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')]", completedProjectId).exists())
        .andExpect(jsonPath("$[?(@.id == '%s')]", activeProjectId).doesNotExist());

    // Filter for ACTIVE,COMPLETED — should return both
    mockMvc
        .perform(get("/api/projects").with(ownerJwt()).param("status", "ACTIVE,COMPLETED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')]", activeProjectId).exists())
        .andExpect(jsonPath("$[?(@.id == '%s')]", completedProjectId).exists());
  }

  @Test
  void shouldRejectCreateTaskOnArchivedProject() throws Exception {
    String projectId = createProject("Archived No Task");

    mockMvc
        .perform(patch("/api/projects/" + projectId + "/archive").with(ownerJwt()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Should fail"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectCreateTimeEntryOnArchivedProject() throws Exception {
    String projectId = createProject("Archived No Time Entry");
    addMemberToProject(projectId, memberIdMember);

    String taskId = createTaskInProject(projectId, "Task before archive");

    mockMvc
        .perform(patch("/api/projects/" + projectId + "/archive").with(ownerJwt()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"date": "2026-02-28", "durationMinutes": 30, "billable": false, "description": "Should fail"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectMemberFromCompletingProject() throws Exception {
    String projectId = createProject("Member Forbidden Complete");

    mockMvc
        .perform(patch("/api/projects/" + projectId + "/complete").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectReopenOnActiveProject() throws Exception {
    String projectId = createProject("Reopen Active Fails");

    mockMvc
        .perform(patch("/api/projects/" + projectId + "/reopen").with(ownerJwt()))
        .andExpect(status().isBadRequest());
  }

  // --- Helper Methods ---

  private String createProject(String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "description": "Lifecycle test project"}
                        """
                            .formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractIdFromLocation(result);
  }

  private String createTaskInProject(String projectId, String title) throws Exception {
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

  private void addMemberToProject(String projectId, String memberId) throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberId)))
        .andExpect(status().isCreated());
  }

  private void claimAndCompleteTask(String taskId) throws Exception {
    mockMvc
        .perform(post("/api/tasks/" + taskId + "/claim").with(memberJwt()))
        .andExpect(status().isOk());

    mockMvc
        .perform(patch("/api/tasks/" + taskId + "/complete").with(memberJwt()))
        .andExpect(status().isOk());
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
        .jwt(j -> j.subject("user_plc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_plc_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_plc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
