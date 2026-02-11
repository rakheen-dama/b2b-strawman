package io.b2mash.b2b.b2bstrawman.task;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class TaskIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_task_test";
  private static final String ORG_B_ID = "org_task_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String projectId;
  private String projectBId;
  private String memberIdOwner;
  private String memberIdMember;

  @BeforeAll
  void provisionTenantsAndProject() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Task Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    provisioningService.provisionTenant(ORG_B_ID, "Task Test Org B");
    planSyncService.syncPlan(ORG_B_ID, "pro-plan");

    memberIdOwner =
        syncMember(ORG_ID, "user_task_owner", "task_owner@test.com", "Task Owner", "owner");
    syncMember(ORG_ID, "user_task_admin", "task_admin@test.com", "Task Admin", "admin");
    memberIdMember =
        syncMember(ORG_ID, "user_task_member", "task_member@test.com", "Task Member", "member");
    syncMember(ORG_B_ID, "user_task_tenant_b", "task_tenantb@test.com", "Tenant B User", "owner");

    // Create a project in tenant A (owner is auto-assigned as lead)
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Task Test Project", "description": "For task tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    // Add the member to the project
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

    // Create a project in tenant B
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
  }

  // --- CRUD happy path ---

  @Test
  void shouldCreateTask() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "First Task",
                      "description": "Do something important",
                      "priority": "HIGH",
                      "type": "Feature",
                      "dueDate": "2026-03-15"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("First Task"))
        .andExpect(jsonPath("$.description").value("Do something important"))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.priority").value("HIGH"))
        .andExpect(jsonPath("$.type").value("Feature"))
        .andExpect(jsonPath("$.dueDate").value("2026-03-15"))
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.projectId").value(projectId))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  void shouldCreateTaskWithMinimalFields() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Minimal Task"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Minimal Task"))
        .andExpect(jsonPath("$.priority").value("MEDIUM"))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.description").isEmpty())
        .andExpect(jsonPath("$.type").isEmpty())
        .andExpect(jsonPath("$.dueDate").isEmpty());
  }

  @Test
  void shouldListTasksForProject() throws Exception {
    // Create a task first
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "List Test Task"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/projects/" + projectId + "/tasks").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
  }

  @Test
  void shouldGetTaskById() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Get By Id Task", "priority": "LOW"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var taskId = extractIdFromLocation(createResult);

    mockMvc
        .perform(get("/api/tasks/" + taskId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Get By Id Task"))
        .andExpect(jsonPath("$.priority").value("LOW"));
  }

  @Test
  void shouldUpdateTask() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Before Update Task"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var taskId = extractIdFromLocation(createResult);

    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "After Update Task",
                      "description": "Updated description",
                      "priority": "HIGH",
                      "status": "IN_PROGRESS",
                      "type": "Bug",
                      "dueDate": "2026-06-01"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("After Update Task"))
        .andExpect(jsonPath("$.description").value("Updated description"))
        .andExpect(jsonPath("$.priority").value("HIGH"))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.type").value("Bug"))
        .andExpect(jsonPath("$.dueDate").value("2026-06-01"));
  }

  @Test
  void shouldDeleteTask() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "To Delete Task"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var taskId = extractIdFromLocation(createResult);

    mockMvc
        .perform(delete("/api/tasks/" + taskId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify it's gone
    mockMvc.perform(get("/api/tasks/" + taskId).with(ownerJwt())).andExpect(status().isNotFound());
  }

  // --- RBAC ---

  @Test
  void projectMemberCanCreateTask() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Member Created Task"}
                    """))
        .andExpect(status().isCreated());
  }

  @Test
  void nonProjectMemberCannotViewTasks() throws Exception {
    // user_task_tenant_b is in a different org and not a member of this project
    mockMvc
        .perform(get("/api/projects/" + projectId + "/tasks").with(tenantBOwnerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void contributorCannotDeleteTask() throws Exception {
    // Member (contributor) creates a task
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(memberJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Member Cannot Delete"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var taskId = extractIdFromLocation(createResult);

    // Member (contributor) cannot delete — requires lead/admin/owner (edit access)
    mockMvc
        .perform(delete("/api/tasks/" + taskId).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void leadCanDeleteTask() throws Exception {
    // Owner is the project lead
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(memberJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Lead Will Delete"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var taskId = extractIdFromLocation(createResult);

    // Owner (project lead) can delete
    mockMvc
        .perform(delete("/api/tasks/" + taskId).with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  // --- Query filter by status ---

  @Test
  void shouldFilterTasksByStatus() throws Exception {
    // Create an OPEN task
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Filter Status Task"}
                    """))
        .andExpect(status().isCreated());

    // Filter by OPEN status — should include our task
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/tasks").param("status", "OPEN").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$[*].status", everyItem(org.hamcrest.Matchers.is("OPEN"))));
  }

  // --- Query filter by priority ---

  @Test
  void shouldFilterTasksByPriority() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "High Priority Task", "priority": "HIGH"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/tasks").param("priority", "HIGH").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$[*].priority", everyItem(org.hamcrest.Matchers.is("HIGH"))));
  }

  // --- Tenant isolation ---

  @Test
  void tasksAreIsolatedBetweenTenants() throws Exception {
    // Create task in tenant A
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Tenant A Only Task"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var taskId = extractIdFromLocation(createResult);

    // Visible from tenant A
    mockMvc
        .perform(get("/api/tasks/" + taskId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Tenant A Only Task"));

    // NOT visible from tenant B
    mockMvc
        .perform(get("/api/tasks/" + taskId).with(tenantBOwnerJwt()))
        .andExpect(status().isNotFound());
  }

  // --- Validation ---

  @Test
  void shouldReject400WhenTitleIsMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"description": "No title"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn404ForNonexistentTask() throws Exception {
    mockMvc
        .perform(get("/api/tasks/00000000-0000-0000-0000-000000000000").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  // --- Helpers ---

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
        .jwt(j -> j.subject("user_task_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_task_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_task_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor tenantBOwnerJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_task_tenant_b").claim("o", Map.of("id", ORG_B_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
