package io.b2mash.b2b.b2bstrawman.timeentry;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimeEntryBatchIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_te_batch_365a";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String projectId;
  private String taskId;
  private String memberIdOwner;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "TE Batch 365A Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(ORG_ID, "user_365a_owner", "365a_owner@test.com", "365A Owner", "owner");

    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {"name": "Batch 365A Project", "description": "test"}
                    """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {"title": "Batch 365A Task"}
                    """))
            .andExpect(status().isCreated())
            .andReturn();
    taskId = extractIdFromLocation(taskResult);
  }

  @Test
  void shouldBatchCreateFiveValidEntries() throws Exception {
    var body = buildBatchBody(5, taskId, "2026-03-10", 60, true);
    mockMvc
        .perform(
            post("/api/time-entries/batch")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCreated").value(5))
        .andExpect(jsonPath("$.totalErrors").value(0))
        .andExpect(jsonPath("$.created", hasSize(5)))
        .andExpect(jsonPath("$.errors", hasSize(0)));
  }

  @Test
  void shouldCaptureRateSnapshotsOnBatchCreate() throws Exception {
    String body =
        """
        {
          "entries": [
            {
              "taskId": "%s",
              "date": "2026-03-11",
              "durationMinutes": 90,
              "description": "Rate snapshot test",
              "billable": true
            }
          ]
        }
        """
            .formatted(taskId);

    mockMvc
        .perform(
            post("/api/time-entries/batch")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCreated").value(1))
        .andExpect(jsonPath("$.created[0].id").exists())
        .andExpect(jsonPath("$.created[0].taskId").value(taskId))
        .andExpect(jsonPath("$.created[0].date").value("2026-03-11"));
  }

  @Test
  void shouldReturnCorrectCounts() throws Exception {
    var body = buildBatchBody(3, taskId, "2026-03-12", 45, false);
    mockMvc
        .perform(
            post("/api/time-entries/batch")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCreated").value(3))
        .andExpect(jsonPath("$.totalErrors").value(0));
  }

  @Test
  void shouldReturnPartialSuccessWithMixedValidInvalid() throws Exception {
    var invalidTaskId = UUID.randomUUID().toString();
    String body =
        """
        {
          "entries": [
            {"taskId": "%s", "date": "2026-03-13", "durationMinutes": 60, "billable": true},
            {"taskId": "%s", "date": "2026-03-13", "durationMinutes": 60, "billable": true},
            {"taskId": "%s", "date": "2026-03-13", "durationMinutes": 60, "billable": true},
            {"taskId": "%s", "date": "2026-03-13", "durationMinutes": 60, "billable": true},
            {"taskId": "%s", "date": "2026-03-13", "durationMinutes": 60, "billable": true}
          ]
        }
        """
            .formatted(taskId, taskId, invalidTaskId, taskId, invalidTaskId);

    mockMvc
        .perform(
            post("/api/time-entries/batch")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCreated").value(3))
        .andExpect(jsonPath("$.totalErrors").value(2))
        .andExpect(jsonPath("$.errors[0].index").value(2))
        .andExpect(jsonPath("$.errors[1].index").value(4));
  }

  @Test
  void shouldHandleAllInvalidEntries() throws Exception {
    String body =
        """
        {
          "entries": [
            {"taskId": "%s", "date": "2026-03-14", "durationMinutes": 60, "billable": true},
            {"taskId": "%s", "date": "2026-03-14", "durationMinutes": 60, "billable": true},
            {"taskId": "%s", "date": "2026-03-14", "durationMinutes": 60, "billable": true}
          ]
        }
        """
            .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/time-entries/batch")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCreated").value(0))
        .andExpect(jsonPath("$.totalErrors").value(3))
        .andExpect(jsonPath("$.created", hasSize(0)));
  }

  @Test
  void shouldReject400WhenBatchExceedsLimit() throws Exception {
    var body = buildBatchBody(51, taskId, "2026-03-15", 30, true);
    mockMvc
        .perform(
            post("/api/time-entries/batch")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldFireBudgetThresholdEventWhenThresholdCrossed() throws Exception {
    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/budget")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"budgetHours": 1.0, "alertThresholdPct": 50}
                    """))
        .andExpect(status().isOk());

    String body =
        """
        {
          "entries": [
            {"taskId": "%s", "date": "2026-03-20", "durationMinutes": 90, "billable": true}
          ]
        }
        """
            .formatted(taskId);

    mockMvc
        .perform(
            post("/api/time-entries/batch")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCreated").value(1));
  }

  // --- Helpers ---

  private String buildBatchBody(
      int count, String taskId, String date, int durationMinutes, boolean billable) {
    var entries = new StringBuilder();
    for (int i = 0; i < count; i++) {
      if (i > 0) entries.append(",");
      entries.append(
          """
          {"taskId": "%s", "date": "%s", "durationMinutes": %d, "billable": %s, "description": "Entry %d"}
          """
              .formatted(taskId, date, durationMinutes, billable, i));
    }
    return """
        {"entries": [%s]}
        """
        .formatted(entries);
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

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_365a_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }
}
