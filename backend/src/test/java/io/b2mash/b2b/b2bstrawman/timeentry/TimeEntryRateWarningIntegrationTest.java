package io.b2mash.b2b.b2bstrawman.timeentry;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
class TimeEntryRateWarningIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_rate_warning_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String projectId;
  private String taskId;
  private String memberIdOwner;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Rate Warning Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner = syncMember(ORG_ID, "user_rw_owner", "rw_owner@test.com", "RW Owner", "owner");

    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Rate Warning Project", "description": "test"}"""))
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
                        {"title": "Rate Warning Task", "priority": "HIGH"}"""))
            .andExpect(status().isCreated())
            .andReturn();
    taskId = extractIdFromLocation(taskResult);
  }

  @Test
  void billable_time_entry_without_rate_includes_warning() throws Exception {
    // No billing rate configured — create billable entry
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-03-01",
                      "durationMinutes": 60,
                      "billable": true,
                      "description": "No rate test"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.billable").value(true))
        .andExpect(jsonPath("$.billingRateSnapshot").value(nullValue()))
        .andExpect(
            jsonPath("$.rateWarning")
                .value(
                    "No rate card found. This time entry will generate a zero-amount invoice line"
                        + " item."));
  }

  @Test
  void billable_time_entry_with_rate_has_no_warning() throws Exception {
    // Create a billing rate for this member
    mockMvc
        .perform(
            post("/api/billing-rates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "currency": "USD",
                      "hourlyRate": 150.00,
                      "effectiveFrom": "2026-01-01"
                    }
                    """
                        .formatted(memberIdOwner)))
        .andExpect(status().isCreated());

    // Create billable entry — should have rate snapshot and no warning
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-03-02",
                      "durationMinutes": 90,
                      "billable": true,
                      "description": "With rate test"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.billable").value(true))
        .andExpect(jsonPath("$.billingRateSnapshot").isNotEmpty())
        .andExpect(jsonPath("$.rateWarning").value(nullValue()));
  }

  @Test
  void non_billable_time_entry_has_no_warning() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-03-03",
                      "durationMinutes": 45,
                      "billable": false,
                      "description": "Non-billable test"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.billable").value(false))
        .andExpect(jsonPath("$.rateWarning").value(nullValue()));
  }

  // --- Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_rw_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
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
                        {"clerkOrgId": "%s", "clerkUserId": "%s", "email": "%s", "name": "%s", "avatarUrl": null, "orgRole": "%s"}
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
}
