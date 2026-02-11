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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimeEntryIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_timeentry_test";
  private static final String ORG_B_ID = "org_timeentry_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String projectId;
  private String taskId;
  private String memberIdOwner;
  private String memberIdMember;
  private String projectBId;
  private String taskBId;

  @BeforeAll
  void provisionTenantsAndSeedData() throws Exception {
    // Provision tenant A with Pro plan
    provisioningService.provisionTenant(ORG_ID, "TimeEntry Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Provision tenant B with Pro plan
    provisioningService.provisionTenant(ORG_B_ID, "TimeEntry Test Org B");
    planSyncService.syncPlan(ORG_B_ID, "pro-plan");

    // Sync members for tenant A
    memberIdOwner = syncMember(ORG_ID, "user_te_owner", "te_owner@test.com", "TE Owner", "owner");
    memberIdMember =
        syncMember(ORG_ID, "user_te_member", "te_member@test.com", "TE Member", "member");

    // Sync member for tenant B
    syncMember(ORG_B_ID, "user_te_tenant_b", "te_tenantb@test.com", "Tenant B User", "owner");

    // Create a project in tenant A (owner is auto-assigned as lead)
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "TE Test Project", "description": "For time entry tests"}
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

    // Create a task in the project
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Time Entry Test Task", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    taskId = extractIdFromLocation(taskResult);

    // Create a project and task in tenant B
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
  }

  // --- Create time entry ---

  @Test
  void shouldCreateTimeEntry() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-10",
                      "durationMinutes": 90,
                      "billable": true,
                      "description": "Worked on feature"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.taskId").value(taskId))
        .andExpect(jsonPath("$.memberId").value(memberIdOwner))
        .andExpect(jsonPath("$.memberName").value("TE Owner"))
        .andExpect(jsonPath("$.date").value("2026-02-10"))
        .andExpect(jsonPath("$.durationMinutes").value(90))
        .andExpect(jsonPath("$.billable").value(true))
        .andExpect(jsonPath("$.description").value("Worked on feature"))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  void shouldCreateTimeEntryWithValidRateCents() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-10",
                      "durationMinutes": 60,
                      "billable": true,
                      "rateCents": 15000,
                      "description": "With rate"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rateCents").value(15000));
  }

  // --- Validation ---

  @Test
  void shouldReject400WhenDateIsMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "durationMinutes": 60,
                      "billable": true
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenDurationIsZero() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-10",
                      "durationMinutes": 0,
                      "billable": true
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenDurationIsNegative() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-10",
                      "durationMinutes": -10,
                      "billable": true
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenRateCentsIsNegative() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-10",
                      "durationMinutes": 60,
                      "billable": true,
                      "rateCents": -100
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  // --- List time entries ---

  @Test
  void shouldListTimeEntriesForTask() throws Exception {
    // Create two time entries
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-09",
                      "durationMinutes": 30,
                      "billable": false,
                      "description": "Entry A"
                    }
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-08",
                      "durationMinutes": 45,
                      "billable": true,
                      "description": "Entry B"
                    }
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/tasks/" + taskId + "/time-entries").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        // At least our 2 entries (plus any from other tests sharing this task)
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
  }

  @Test
  void shouldReturnEmptyListForTaskWithNoTimeEntries() throws Exception {
    // Create a new task with no time entries
    var newTaskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Empty Time Entry Task"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var emptyTaskId = extractIdFromLocation(newTaskResult);

    mockMvc
        .perform(get("/api/tasks/" + emptyTaskId + "/time-entries").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  // --- Access control ---

  @Test
  void nonProjectMemberCannotCreateTimeEntry() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(tenantBOwnerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-10",
                      "durationMinutes": 60,
                      "billable": true
                    }
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void nonProjectMemberCannotListTimeEntries() throws Exception {
    mockMvc
        .perform(get("/api/tasks/" + taskId + "/time-entries").with(tenantBOwnerJwt()))
        .andExpect(status().isNotFound());
  }

  // --- Tenant isolation ---

  @Test
  void timeEntriesAreIsolatedBetweenTenants() throws Exception {
    // Create a time entry in tenant B
    mockMvc
        .perform(
            post("/api/tasks/" + taskBId + "/time-entries")
                .with(tenantBOwnerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-10",
                      "durationMinutes": 120,
                      "billable": true,
                      "description": "Tenant B entry"
                    }
                    """))
        .andExpect(status().isCreated());

    // Tenant A cannot see tenant B's task or time entries
    mockMvc
        .perform(get("/api/tasks/" + taskBId + "/time-entries").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  // --- Task not found ---

  @Test
  void shouldReturn404ForNonexistentTask() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/00000000-0000-0000-0000-000000000000/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-10",
                      "durationMinutes": 60,
                      "billable": true
                    }
                    """))
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

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_te_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_te_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor tenantBOwnerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_te_tenant_b").claim("o", Map.of("id", ORG_B_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
