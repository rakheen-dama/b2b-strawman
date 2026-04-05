package io.b2mash.b2b.b2bstrawman.mywork;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
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
 * Cross-tenant isolation tests for My Work endpoints. Verifies that My Work queries respect tenant
 * boundaries across dedicated schemas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyWorkIsolationTest {
  private static final String ORG_PRO_A_ID = "org_mw_iso_pro_a";
  private static final String ORG_PRO_B_ID = "org_mw_iso_pro_b";
  private static final String ORG_STARTER_A_ID = "org_mw_iso_starter_a";
  private static final String ORG_STARTER_B_ID = "org_mw_iso_starter_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    // Pro tenants (dedicated schema)
    provisioningService.provisionTenant(ORG_PRO_A_ID, "Pro Iso A", null);
    provisioningService.provisionTenant(ORG_PRO_B_ID, "Pro Iso B", null);

    // Starter tenants (shared schema)
    provisioningService.provisionTenant(ORG_STARTER_A_ID, "Starter Iso A", null);
    provisioningService.provisionTenant(ORG_STARTER_B_ID, "Starter Iso B", null);

    // Pro A: sync member, create project + task + time entry
    TestMemberHelper.syncMember(
        mockMvc, ORG_PRO_A_ID, "user_mw_iso_pro_a", "mw_iso_pro_a@test.com", "Pro A User", "owner");
    var proAProject =
        createProject(
            TestJwtFactory.ownerJwt(ORG_PRO_A_ID, "user_mw_iso_pro_a"),
            "Pro A Project",
            "Pro A desc");
    var proATask =
        createTask(
            TestJwtFactory.ownerJwt(ORG_PRO_A_ID, "user_mw_iso_pro_a"),
            proAProject,
            "Pro A Task",
            "HIGH");
    mockMvc
        .perform(
            post("/api/tasks/" + proATask + "/claim")
                .with(TestJwtFactory.ownerJwt(ORG_PRO_A_ID, "user_mw_iso_pro_a")))
        .andExpect(status().isOk());
    createTimeEntry(
        TestJwtFactory.ownerJwt(ORG_PRO_A_ID, "user_mw_iso_pro_a"),
        proATask,
        "2026-02-10",
        60,
        true,
        "Pro A work");

    // Pro B: sync member, create project + task + time entry
    TestMemberHelper.syncMember(
        mockMvc, ORG_PRO_B_ID, "user_mw_iso_pro_b", "mw_iso_pro_b@test.com", "Pro B User", "owner");
    var proBProject =
        createProject(
            TestJwtFactory.ownerJwt(ORG_PRO_B_ID, "user_mw_iso_pro_b"),
            "Pro B Project",
            "Pro B desc");
    var proBTask =
        createTask(
            TestJwtFactory.ownerJwt(ORG_PRO_B_ID, "user_mw_iso_pro_b"),
            proBProject,
            "Pro B Task",
            "HIGH");
    mockMvc
        .perform(
            post("/api/tasks/" + proBTask + "/claim")
                .with(TestJwtFactory.ownerJwt(ORG_PRO_B_ID, "user_mw_iso_pro_b")))
        .andExpect(status().isOk());
    createTimeEntry(
        TestJwtFactory.ownerJwt(ORG_PRO_B_ID, "user_mw_iso_pro_b"),
        proBTask,
        "2026-02-10",
        120,
        true,
        "Pro B work");

    // Starter A: sync member, create project + task + time entry
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_STARTER_A_ID,
        "user_mw_iso_starter_a",
        "mw_iso_starter_a@test.com",
        "Starter A User",
        "owner");
    var starterAProject =
        createProject(
            TestJwtFactory.ownerJwt(ORG_STARTER_A_ID, "user_mw_iso_starter_a"),
            "Starter A Project",
            "Starter A desc");
    var starterATask =
        createTask(
            TestJwtFactory.ownerJwt(ORG_STARTER_A_ID, "user_mw_iso_starter_a"),
            starterAProject,
            "Starter A Task",
            "HIGH");
    mockMvc
        .perform(
            post("/api/tasks/" + starterATask + "/claim")
                .with(TestJwtFactory.ownerJwt(ORG_STARTER_A_ID, "user_mw_iso_starter_a")))
        .andExpect(status().isOk());
    createTimeEntry(
        TestJwtFactory.ownerJwt(ORG_STARTER_A_ID, "user_mw_iso_starter_a"),
        starterATask,
        "2026-02-10",
        90,
        true,
        "Starter A work");

    // Starter B: sync member, create project + task + time entry
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_STARTER_B_ID,
        "user_mw_iso_starter_b",
        "mw_iso_starter_b@test.com",
        "Starter B User",
        "owner");
    var starterBProject =
        createProject(
            TestJwtFactory.ownerJwt(ORG_STARTER_B_ID, "user_mw_iso_starter_b"),
            "Starter B Project",
            "Starter B desc");
    var starterBTask =
        createTask(
            TestJwtFactory.ownerJwt(ORG_STARTER_B_ID, "user_mw_iso_starter_b"),
            starterBProject,
            "Starter B Task",
            "HIGH");
    mockMvc
        .perform(
            post("/api/tasks/" + starterBTask + "/claim")
                .with(TestJwtFactory.ownerJwt(ORG_STARTER_B_ID, "user_mw_iso_starter_b")))
        .andExpect(status().isOk());
    createTimeEntry(
        TestJwtFactory.ownerJwt(ORG_STARTER_B_ID, "user_mw_iso_starter_b"),
        starterBTask,
        "2026-02-10",
        45,
        false,
        "Starter B work");
  }

  @Test
  void proTenantASeesOnlyOwnTasks() throws Exception {
    mockMvc
        .perform(
            get("/api/my-work/tasks")
                .with(TestJwtFactory.ownerJwt(ORG_PRO_A_ID, "user_mw_iso_pro_a")))
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
                .with(TestJwtFactory.ownerJwt(ORG_PRO_B_ID, "user_mw_iso_pro_b"))
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
        .perform(
            get("/api/my-work/tasks")
                .with(TestJwtFactory.ownerJwt(ORG_STARTER_A_ID, "user_mw_iso_starter_a")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.assigned[*].title", everyItem(org.hamcrest.Matchers.is("Starter A Task"))))
        .andExpect(jsonPath("$.assigned[*].title", everyItem(not("Starter B Task"))));

    // Starter A sees only their own time summary
    mockMvc
        .perform(
            get("/api/my-work/time-summary")
                .with(TestJwtFactory.ownerJwt(ORG_STARTER_A_ID, "user_mw_iso_starter_a"))
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
                .with(TestJwtFactory.ownerJwt(ORG_STARTER_B_ID, "user_mw_iso_starter_b"))
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
    return TestEntityHelper.extractIdFromLocation(result);
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
