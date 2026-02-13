package io.b2mash.b2b.b2bstrawman.timeentry;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateService;
import io.b2mash.b2b.b2bstrawman.costrate.CostRateService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TimeEntryRateSnapshotTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_timeentry_snapshot_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private BillingRateService billingRateService;
  @Autowired private CostRateService costRateService;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdMember;
  private String taskId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "TimeEntry Snapshot Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_snap_owner", "snap_owner@test.com", "Snapshot Owner", "owner"));
    memberIdMember =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_snap_member", "snap_member@test.com", "Snapshot Member", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create project via MockMvc (owner is auto-assigned as project lead)
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Snapshot Test Project", "description": "For rate snapshot tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String projectId = extractIdFromLocation(projectResult);

    // Add member to project so they have view access
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

    // Create task in project
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Snapshot Test Task", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    taskId = extractIdFromLocation(taskResult);

    // Create billing and cost rates via direct service calls within tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              // Billing rate: $200/hr USD, effective 2024-01-01 to 2025-06-30
              billingRateService.createRate(
                  memberIdOwner,
                  null,
                  null,
                  "USD",
                  new BigDecimal("200.00"),
                  LocalDate.of(2024, 1, 1),
                  LocalDate.of(2025, 6, 30),
                  memberIdOwner,
                  "owner");

              // Cost rate: $120/hr USD, effective 2024-01-01, open-ended
              costRateService.createCostRate(
                  memberIdOwner,
                  "USD",
                  new BigDecimal("120.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");

              // Second billing rate: $250/hr EUR, effective 2025-07-01 to 2025-12-31
              // (for re-snapshot test)
              billingRateService.createRate(
                  memberIdOwner,
                  null,
                  null,
                  "EUR",
                  new BigDecimal("250.00"),
                  LocalDate.of(2025, 7, 1),
                  LocalDate.of(2025, 12, 31),
                  memberIdOwner,
                  "owner");
            });
  }

  @Test
  @Order(1)
  void createTimeEntry_withBillingRate_snapshotsCaptured() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2024-06-15",
                      "durationMinutes": 120,
                      "billable": true,
                      "description": "Work with billing rate"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.billingRateSnapshot").value(200.00))
        .andExpect(jsonPath("$.billingRateCurrency").value("USD"))
        .andExpect(jsonPath("$.costRateSnapshot").value(120.00))
        .andExpect(jsonPath("$.costRateCurrency").value("USD"));
  }

  @Test
  @Order(2)
  void createTimeEntry_noRateConfigured_snapshotsNull() throws Exception {
    // Member has no rates configured — snapshots should be null
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2024-06-15",
                      "durationMinutes": 60,
                      "billable": true,
                      "description": "Work without rate"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.billingRateSnapshot").doesNotExist())
        .andExpect(jsonPath("$.billingRateCurrency").doesNotExist())
        .andExpect(jsonPath("$.costRateSnapshot").doesNotExist())
        .andExpect(jsonPath("$.costRateCurrency").doesNotExist())
        .andExpect(jsonPath("$.billableValue").doesNotExist())
        .andExpect(jsonPath("$.costValue").doesNotExist());
  }

  @Test
  @Order(3)
  void createTimeEntry_withBothRates_bothSnapshotsPopulated() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/time-entries")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "date": "2024-06-15",
                          "durationMinutes": 90,
                          "billable": true,
                          "description": "Both rates present"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.billingRateSnapshot").value(200.00))
            .andExpect(jsonPath("$.billingRateCurrency").value("USD"))
            .andExpect(jsonPath("$.costRateSnapshot").value(120.00))
            .andExpect(jsonPath("$.costRateCurrency").value("USD"))
            .andExpect(jsonPath("$.billableValue").isNumber())
            .andExpect(jsonPath("$.costValue").isNumber())
            .andReturn();

    // Verify computed billableValue: 90/60 * 200 = 300.0000
    double billableValue =
        ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.billableValue"))
            .doubleValue();
    org.assertj.core.api.Assertions.assertThat(billableValue)
        .isCloseTo(300.0, org.assertj.core.data.Offset.offset(0.01));

    // Verify computed costValue: 90/60 * 120 = 180.0000
    double costValue =
        ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.costValue"))
            .doubleValue();
    org.assertj.core.api.Assertions.assertThat(costValue)
        .isCloseTo(180.0, org.assertj.core.data.Offset.offset(0.01));
  }

  @Test
  @Order(4)
  void updateTimeEntry_dateChange_reSnapshotsWithNewRate() throws Exception {
    // Create entry at date 2024-06-15 — should get $200/hr billing rate
    var createResult =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/time-entries")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "date": "2024-06-15",
                          "durationMinutes": 60,
                          "billable": true,
                          "description": "To be re-dated"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.billingRateSnapshot").value(200.00))
            .andExpect(jsonPath("$.billingRateCurrency").value("USD"))
            .andReturn();

    String entryId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.id").toString();

    // Update date to 2025-08-15 — should re-snapshot to $250/hr EUR
    mockMvc
        .perform(
            put("/api/time-entries/" + entryId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2025-08-15"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billingRateSnapshot").value(250.00))
        .andExpect(jsonPath("$.billingRateCurrency").value("EUR"));
  }

  @Test
  @Order(5)
  void updateTimeEntry_descriptionOnly_snapshotUnchanged() throws Exception {
    // Create entry at date 2024-06-15 — should get $200/hr
    var createResult =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/time-entries")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "date": "2024-06-15",
                          "durationMinutes": 60,
                          "billable": true,
                          "description": "Original description"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.billingRateSnapshot").value(200.00))
            .andReturn();

    String entryId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.id").toString();

    // Update description only — snapshot should remain the same
    mockMvc
        .perform(
            put("/api/time-entries/" + entryId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "description": "Updated description"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billingRateSnapshot").value(200.00))
        .andExpect(jsonPath("$.billingRateCurrency").value("USD"))
        .andExpect(jsonPath("$.description").value("Updated description"));
  }

  @Test
  @Order(6)
  void createTimeEntry_billableValue_computedCorrectly() throws Exception {
    // 120 minutes at $200/hr = 120/60 * 200 = $400
    var result =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/time-entries")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "date": "2024-06-15",
                          "durationMinutes": 120,
                          "billable": true,
                          "description": "Computed value test"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.billableValue").isNumber())
            .andReturn();

    double billableValue =
        ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.billableValue"))
            .doubleValue();
    org.assertj.core.api.Assertions.assertThat(billableValue)
        .isCloseTo(400.0, org.assertj.core.data.Offset.offset(0.01));
  }

  @Test
  @Order(7)
  void createTimeEntry_notBillable_nullBillableValueButHasCostValue() throws Exception {
    // Non-billable entry: billableValue should be null, costValue should exist
    var result =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/time-entries")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "date": "2024-06-15",
                          "durationMinutes": 60,
                          "billable": false,
                          "description": "Non-billable with cost"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.billableValue").doesNotExist())
            .andExpect(jsonPath("$.costValue").isNumber())
            .andExpect(jsonPath("$.costRateSnapshot").value(120.00))
            .andExpect(jsonPath("$.costRateCurrency").value("USD"))
            .andReturn();

    // costValue: 60/60 * 120 = 120.0
    double costValue =
        ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.costValue"))
            .doubleValue();
    org.assertj.core.api.Assertions.assertThat(costValue)
        .isCloseTo(120.0, org.assertj.core.data.Offset.offset(0.01));
  }

  // --- JWT helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_snap_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_snap_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  // --- Helpers ---

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

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }
}
