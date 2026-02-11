package io.b2mash.b2b.b2bstrawman.mywork;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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
 * Cross-tenant and shared-schema isolation tests for My Work endpoints. Verifies that My Work
 * queries respect tenant boundaries for both Pro (dedicated schema) and Starter (shared schema +
 * RLS) tiers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyWorkIsolationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_PRO_A_ID = "org_mw_iso_pro_a";
  private static final String ORG_PRO_B_ID = "org_mw_iso_pro_b";
  private static final String ORG_STARTER_A_ID = "org_mw_iso_starter_a";
  private static final String ORG_STARTER_B_ID = "org_mw_iso_starter_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    // Pro tenants (dedicated schema)
    provisioningService.provisionTenant(ORG_PRO_A_ID, "Pro Iso A");
    planSyncService.syncPlan(ORG_PRO_A_ID, "pro-plan");
    provisioningService.provisionTenant(ORG_PRO_B_ID, "Pro Iso B");
    planSyncService.syncPlan(ORG_PRO_B_ID, "pro-plan");

    // Starter tenants (shared schema)
    provisioningService.provisionTenant(ORG_STARTER_A_ID, "Starter Iso A");
    provisioningService.provisionTenant(ORG_STARTER_B_ID, "Starter Iso B");

    // Pro A: sync member, create project + task + time entry
    syncMember(ORG_PRO_A_ID, "user_mw_iso_pro_a", "mw_iso_pro_a@test.com", "Pro A User", "owner");
    var proAProject = createProject(proAJwt(), "Pro A Project", "Pro A desc");
    var proATask = createTask(proAJwt(), proAProject, "Pro A Task", "HIGH");
    mockMvc
        .perform(post("/api/tasks/" + proATask + "/claim").with(proAJwt()))
        .andExpect(status().isOk());
    createTimeEntry(proAJwt(), proATask, "2026-02-10", 60, true, "Pro A work");

    // Pro B: sync member, create project + task + time entry
    syncMember(ORG_PRO_B_ID, "user_mw_iso_pro_b", "mw_iso_pro_b@test.com", "Pro B User", "owner");
    var proBProject = createProject(proBJwt(), "Pro B Project", "Pro B desc");
    var proBTask = createTask(proBJwt(), proBProject, "Pro B Task", "HIGH");
    mockMvc
        .perform(post("/api/tasks/" + proBTask + "/claim").with(proBJwt()))
        .andExpect(status().isOk());
    createTimeEntry(proBJwt(), proBTask, "2026-02-10", 120, true, "Pro B work");

    // Starter A: sync member, create project + task + time entry
    syncMember(
        ORG_STARTER_A_ID,
        "user_mw_iso_starter_a",
        "mw_iso_starter_a@test.com",
        "Starter A User",
        "owner");
    var starterAProject = createProject(starterAJwt(), "Starter A Project", "Starter A desc");
    var starterATask = createTask(starterAJwt(), starterAProject, "Starter A Task", "HIGH");
    mockMvc
        .perform(post("/api/tasks/" + starterATask + "/claim").with(starterAJwt()))
        .andExpect(status().isOk());
    createTimeEntry(starterAJwt(), starterATask, "2026-02-10", 90, true, "Starter A work");

    // Starter B: sync member, create project + task + time entry
    syncMember(
        ORG_STARTER_B_ID,
        "user_mw_iso_starter_b",
        "mw_iso_starter_b@test.com",
        "Starter B User",
        "owner");
    var starterBProject = createProject(starterBJwt(), "Starter B Project", "Starter B desc");
    var starterBTask = createTask(starterBJwt(), starterBProject, "Starter B Task", "HIGH");
    mockMvc
        .perform(post("/api/tasks/" + starterBTask + "/claim").with(starterBJwt()))
        .andExpect(status().isOk());
    createTimeEntry(starterBJwt(), starterBTask, "2026-02-10", 45, false, "Starter B work");
  }

  @Test
  void proTenantASeesOnlyOwnTasks() throws Exception {
    mockMvc
        .perform(get("/api/my-work/tasks").with(proAJwt()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.assigned[*].title", everyItem(org.hamcrest.Matchers.is("Pro A Task"))))
        .andExpect(jsonPath("$.assigned[*].title", everyItem(not("Pro B Task"))));
  }

  @Test
  void proTenantBSeesOnlyOwnTimeSummary() throws Exception {
    mockMvc
        .perform(
            get("/api/my-work/time-summary")
                .with(proBJwt())
                .param("from", "2026-02-01")
                .param("to", "2026-02-28"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalMinutes").value(120))
        .andExpect(jsonPath("$.byProject", hasSize(1)))
        .andExpect(jsonPath("$.byProject[0].projectName").value("Pro B Project"));
  }

  @Test
  void starterTenantIsolationForMyWork() throws Exception {
    // Starter A sees only their own tasks
    mockMvc
        .perform(get("/api/my-work/tasks").with(starterAJwt()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.assigned[*].title", everyItem(org.hamcrest.Matchers.is("Starter A Task"))))
        .andExpect(jsonPath("$.assigned[*].title", everyItem(not("Starter B Task"))));

    // Starter A sees only their own time summary
    mockMvc
        .perform(
            get("/api/my-work/time-summary")
                .with(starterAJwt())
                .param("from", "2026-02-01")
                .param("to", "2026-02-28"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalMinutes").value(90))
        .andExpect(jsonPath("$.billableMinutes").value(90))
        .andExpect(jsonPath("$.byProject", hasSize(1)))
        .andExpect(jsonPath("$.byProject[0].projectName").value("Starter A Project"));

    // Starter B sees only their own time summary
    mockMvc
        .perform(
            get("/api/my-work/time-summary")
                .with(starterBJwt())
                .param("from", "2026-02-01")
                .param("to", "2026-02-28"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalMinutes").value(45))
        .andExpect(jsonPath("$.nonBillableMinutes").value(45))
        .andExpect(jsonPath("$.byProject", hasSize(1)))
        .andExpect(jsonPath("$.byProject[0].projectName").value("Starter B Project"));
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
    return extractIdFromLocation(result);
  }

  private String createTask(
      JwtRequestPostProcessor jwt, String projectId, String title, String priority)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s", "priority": "%s"}
                        """
                            .formatted(title, priority)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractIdFromLocation(result);
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

  private JwtRequestPostProcessor proAJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_mw_iso_pro_a")
                    .claim("o", Map.of("id", ORG_PRO_A_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor proBJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_mw_iso_pro_b")
                    .claim("o", Map.of("id", ORG_PRO_B_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor starterAJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_mw_iso_starter_a")
                    .claim("o", Map.of("id", ORG_STARTER_A_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor starterBJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_mw_iso_starter_b")
                    .claim("o", Map.of("id", ORG_STARTER_B_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
