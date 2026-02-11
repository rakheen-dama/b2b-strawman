package io.b2mash.b2b.b2bstrawman.timeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared schema (Starter tier) isolation tests for time entries. Provisions two Starter orgs
 * sharing the tenant_shared schema and verifies that time entries are isolated via
 * Hibernate @Filter and tenant_id population.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimeEntrySharedSchemaIntegrationTest {

  private static final String ORG_A_ID = "org_te_shared_a";
  private static final String ORG_B_ID = "org_te_shared_b";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TimeEntryRepository timeEntryRepository;

  private String projectAId;
  private String projectBId;

  @BeforeAll
  void provisionStarterOrgsAndSeedData() throws Exception {
    // Provision two Starter orgs (no plan sync = Starter tier = shared schema)
    provisioningService.provisionTenant(ORG_A_ID, "TE Shared A");
    provisioningService.provisionTenant(ORG_B_ID, "TE Shared B");

    // Sync members
    syncMember(ORG_A_ID, "user_te_shared_a", "te_shared_a@test.com", "Shared A User", "owner");
    syncMember(ORG_B_ID, "user_te_shared_b", "te_shared_b@test.com", "Shared B User", "owner");

    // Create projects in each org (tasks created per-test for isolation)
    var projectAResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(orgAJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Shared A Project", "description": "Starter A"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectAId = extractIdFromLocation(projectAResult);

    var projectBResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(orgBJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Shared B Project", "description": "Starter B"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectBId = extractIdFromLocation(projectBResult);
  }

  @Test
  void timeEntriesAreIsolatedBetweenStarterOrgs() throws Exception {
    // Create isolated tasks for this test
    var taskAId = createTask(projectAId, orgAJwt(), "Isolation Test A Task");
    var taskBId = createTask(projectBId, orgBJwt(), "Isolation Test B Task");

    // Create time entry in Org A
    mockMvc
        .perform(
            post("/api/tasks/" + taskAId + "/time-entries")
                .with(orgAJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-10",
                      "durationMinutes": 60,
                      "billable": true,
                      "description": "Org A time"
                    }
                    """))
        .andExpect(status().isCreated());

    // Create time entry in Org B
    mockMvc
        .perform(
            post("/api/tasks/" + taskBId + "/time-entries")
                .with(orgBJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-10",
                      "durationMinutes": 45,
                      "billable": false,
                      "description": "Org B time"
                    }
                    """))
        .andExpect(status().isCreated());

    // Org A cannot see Org B's task or time entries
    mockMvc
        .perform(get("/api/tasks/" + taskBId + "/time-entries").with(orgAJwt()))
        .andExpect(status().isNotFound());

    // Org B cannot see Org A's task or time entries
    mockMvc
        .perform(get("/api/tasks/" + taskAId + "/time-entries").with(orgBJwt()))
        .andExpect(status().isNotFound());

    // Org A sees only its own entry
    mockMvc
        .perform(get("/api/tasks/" + taskAId + "/time-entries").with(orgAJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].description").value("Org A time"));
  }

  @Test
  void tenantIdIsPopulatedForSharedSchemaTimeEntries() throws Exception {
    var taskAId = createTask(projectAId, orgAJwt(), "TenantId Check Task");

    // Create a time entry in Org A
    var result =
        mockMvc
            .perform(
                post("/api/tasks/" + taskAId + "/time-entries")
                    .with(orgAJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "date": "2026-02-11",
                          "durationMinutes": 30,
                          "billable": true,
                          "description": "Tenant ID check"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var entryId =
        UUID.fromString(
            JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString());

    // Verify tenant_id is populated via direct repository access in shared schema context
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_shared")
        .where(RequestScopes.ORG_ID, ORG_A_ID)
        .run(
            () -> {
              var entry = timeEntryRepository.findById(entryId);
              assertThat(entry).isPresent();
              assertThat(entry.get().getTenantId()).isEqualTo(ORG_A_ID);
            });
  }

  @Test
  void bothStarterOrgsCanCrudTimeEntriesIndependently() throws Exception {
    var taskAId = createTask(projectAId, orgAJwt(), "Independent A Time Task");
    var taskBId = createTask(projectBId, orgBJwt(), "Independent B Time Task");

    // Create entry in Org A
    mockMvc
        .perform(
            post("/api/tasks/" + taskAId + "/time-entries")
                .with(orgAJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-10",
                      "durationMinutes": 90,
                      "billable": true,
                      "description": "Independent A"
                    }
                    """))
        .andExpect(status().isCreated());

    // Create entry in Org B
    mockMvc
        .perform(
            post("/api/tasks/" + taskBId + "/time-entries")
                .with(orgBJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-10",
                      "durationMinutes": 120,
                      "billable": false,
                      "description": "Independent B"
                    }
                    """))
        .andExpect(status().isCreated());

    // Each org sees only its own entries
    mockMvc
        .perform(get("/api/tasks/" + taskAId + "/time-entries").with(orgAJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].description").value("Independent A"));

    mockMvc
        .perform(get("/api/tasks/" + taskBId + "/time-entries").with(orgBJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].description").value("Independent B"));
  }

  // --- Helpers ---

  private String createTask(String projectId, JwtRequestPostProcessor jwt, String title)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s"}
                        """
                            .formatted(title)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractIdFromLocation(result);
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

  private JwtRequestPostProcessor orgAJwt() {
    return jwt()
        .jwt(j -> j.subject("user_te_shared_a").claim("o", Map.of("id", ORG_A_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor orgBJwt() {
    return jwt()
        .jwt(j -> j.subject("user_te_shared_b").claim("o", Map.of("id", ORG_B_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
