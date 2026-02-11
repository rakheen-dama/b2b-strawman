package io.b2mash.b2b.b2bstrawman.task;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
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
 * Integration tests verifying task row-level isolation for Starter-tier orgs sharing the {@code
 * tenant_shared} schema. Two Starter orgs are provisioned with projects and tasks, and every
 * operation is validated for correct isolation via Hibernate @Filter and tenant_id population.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskStarterTenantIntegrationTest {

  private static final String ORG_A_ID = "org_task_starter_a";
  private static final String ORG_B_ID = "org_task_starter_b";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String projectAId;
  private String projectBId;

  @BeforeAll
  void provisionStarterOrgsAndProjects() throws Exception {
    // Provision as Starter tier (no planSyncService call — default is Starter)
    provisioningService.provisionTenant(ORG_A_ID, "Task Starter Org A");
    provisioningService.provisionTenant(ORG_B_ID, "Task Starter Org B");

    syncMember(
        ORG_A_ID, "user_task_sa_owner", "task_sa_owner@test.com", "Task Starter A Owner", "owner");
    syncMember(
        ORG_B_ID, "user_task_sb_owner", "task_sb_owner@test.com", "Task Starter B Owner", "owner");

    // Create a project in each Starter org
    var projectAResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(orgAOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Starter A Task Project", "description": "For task isolation tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectAId = extractIdFromLocation(projectAResult);

    var projectBResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(orgBOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Starter B Task Project", "description": "For task isolation tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectBId = extractIdFromLocation(projectBResult);
  }

  @Test
  void starterOrgTasksAreIsolated() throws Exception {
    // Create a task in Starter Org A
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectAId + "/tasks")
                    .with(orgAOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Starter A Only Task", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var taskId = extractIdFromLocation(createResult);

    // Visible from Org A
    mockMvc
        .perform(get("/api/tasks/" + taskId).with(orgAOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Starter A Only Task"));

    // NOT visible from Org B (404 — RLS isolation via tenant_id filter)
    mockMvc
        .perform(get("/api/tasks/" + taskId).with(orgBOwnerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void starterOrgTaskListingIsIsolated() throws Exception {
    // Create task in Org A
    mockMvc
        .perform(
            post("/api/projects/" + projectAId + "/tasks")
                .with(orgAOwnerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Starter A List Isolation Task"}
                    """))
        .andExpect(status().isCreated());

    // Org A can list its tasks
    mockMvc
        .perform(get("/api/projects/" + projectAId + "/tasks").with(orgAOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));

    // Org B cannot see Org A's project (security-by-obscurity → 404)
    mockMvc
        .perform(get("/api/projects/" + projectAId + "/tasks").with(orgBOwnerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void bothStarterOrgsCanCreateTasksIndependently() throws Exception {
    // Create task in Org A
    var createA =
        mockMvc
            .perform(
                post("/api/projects/" + projectAId + "/tasks")
                    .with(orgAOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Independent Task A", "priority": "LOW"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var taskAId = extractIdFromLocation(createA);

    // Create task in Org B
    var createB =
        mockMvc
            .perform(
                post("/api/projects/" + projectBId + "/tasks")
                    .with(orgBOwnerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Independent Task B", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var taskBId = extractIdFromLocation(createB);

    // Both tasks are visible to their respective orgs
    mockMvc
        .perform(get("/api/tasks/" + taskAId).with(orgAOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Independent Task A"));

    mockMvc
        .perform(get("/api/tasks/" + taskBId).with(orgBOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Independent Task B"));

    // Cross-org access denied
    mockMvc
        .perform(get("/api/tasks/" + taskAId).with(orgBOwnerJwt()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/tasks/" + taskBId).with(orgAOwnerJwt()))
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

  private JwtRequestPostProcessor orgAOwnerJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_task_sa_owner").claim("o", Map.of("id", ORG_A_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor orgBOwnerJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_task_sb_owner").claim("o", Map.of("id", ORG_B_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
