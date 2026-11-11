package io.b2mash.b2b.b2bstrawman.dashboard;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * G89-F5: the matter Overview "Hours logged" tile reads {@code GET /api/matters/{id}/member-hours},
 * which {@code DashboardService} caches per tenant + matter + range for a minute. Logging, editing
 * or deleting time must invalidate the matter's cached reads at once, not after the TTL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardMemberHoursCacheIntegrationTest {

  private static final String ORG_ID = "org_member_hours_cache";
  private static final String OWNER = "user_mhc_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private final LocalDate today = LocalDate.now();
  private JwtRequestPostProcessor owner;
  private String taskId;
  private String matterId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Member Hours Cache Org", null);
    TestMemberHelper.syncMember(mockMvc, ORG_ID, OWNER, "mhc_owner@test.com", "MHC Owner", "owner");
    owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    matterId = TestEntityHelper.createProject(mockMvc, owner, "Member Hours Cache Matter");
    taskId = TestEntityHelper.createTask(mockMvc, owner, matterId, "Hours cache task");
  }

  @Test
  void memberHours_reflectsCreateUpdateAndDeleteImmediately() throws Exception {
    // Warm the cache with the empty matter
    memberHours().andExpect(jsonPath("$", hasSize(0)));

    var created =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/time-entries")
                    .with(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"date": "%s", "durationMinutes": 90, "billable": true,
                         "description": "Consultation"}
                        """
                            .formatted(today.minusDays(1))))
            .andExpect(status().isCreated())
            .andReturn();
    String entryId = TestEntityHelper.extractId(created);
    memberHours()
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].totalHours").value(closeTo(1.5, 0.001)));

    mockMvc
        .perform(
            put("/api/time-entries/" + entryId)
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"durationMinutes": 120}
                    """))
        .andExpect(status().isOk());
    memberHours().andExpect(jsonPath("$[0].totalHours").value(closeTo(2.0, 0.001)));

    mockMvc
        .perform(delete("/api/time-entries/" + entryId).with(owner))
        .andExpect(status().isNoContent());
    memberHours().andExpect(jsonPath("$", hasSize(0)));
  }

  private ResultActions memberHours() throws Exception {
    return mockMvc
        .perform(
            get("/api/matters/" + matterId + "/member-hours")
                .param("from", today.minusDays(30).toString())
                .param("to", today.toString())
                .with(owner))
        .andExpect(status().isOk());
  }
}
