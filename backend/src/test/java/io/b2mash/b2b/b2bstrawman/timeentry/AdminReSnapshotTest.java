package io.b2mash.b2b.b2bstrawman.timeentry;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class AdminReSnapshotTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_resnap_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private BillingRateService billingRateService;
  @Autowired private CostRateService costRateService;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdMember;
  private String projectId1;
  private String projectId2;
  private String taskId1;
  private String taskId2;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "ReSnapshot Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_resnap_owner", "resnap_owner@test.com", "ReSnap Owner", "owner"));
    memberIdMember =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_resnap_member", "resnap_member@test.com", "ReSnap Member", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create project 1
    var projectResult1 =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "ReSnap Project 1", "description": "For re-snapshot tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId1 = extractIdFromLocation(projectResult1);

    // Create project 2
    var projectResult2 =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "ReSnap Project 2", "description": "Second project"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId2 = extractIdFromLocation(projectResult2);

    // Create tasks in each project
    var taskResult1 =
        mockMvc
            .perform(
                post("/api/projects/" + projectId1 + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "ReSnap Task 1", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    taskId1 = extractIdFromLocation(taskResult1);

    var taskResult2 =
        mockMvc
            .perform(
                post("/api/projects/" + projectId2 + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "ReSnap Task 2", "priority": "MEDIUM"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    taskId2 = extractIdFromLocation(taskResult2);

    // Create time entries WITHOUT rates first (no rates configured yet)
    mockMvc
        .perform(
            post("/api/tasks/" + taskId1 + "/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2024-06-15",
                      "durationMinutes": 60,
                      "billable": true,
                      "description": "Entry in project 1"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.billingRateSnapshot").doesNotExist());

    mockMvc
        .perform(
            post("/api/tasks/" + taskId2 + "/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2024-06-15",
                      "durationMinutes": 90,
                      "billable": true,
                      "description": "Entry in project 2"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.billingRateSnapshot").doesNotExist());

    // Now configure rates for the owner (after entries were created)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              billingRateService.createRate(
                  memberIdOwner,
                  null,
                  null,
                  "USD",
                  new BigDecimal("200.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");
              costRateService.createCostRate(
                  memberIdOwner,
                  "USD",
                  new BigDecimal("100.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");
            });
  }

  @Test
  @Order(1)
  void reSnapshot_updatesEntriesWithNullSnapshots() throws Exception {
    // Re-snapshot all entries for this member — should update entries that had null snapshots
    mockMvc
        .perform(
            post("/api/admin/time-entries/re-snapshot")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberIdOwner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entriesProcessed").value(2))
        .andExpect(jsonPath("$.entriesUpdated").value(2))
        .andExpect(jsonPath("$.entriesSkipped").value(0));
  }

  @Test
  @Order(2)
  void reSnapshot_skipsEntriesWhereRateMatches() throws Exception {
    // Re-snapshot again — same rates, so all should be skipped
    mockMvc
        .perform(
            post("/api/admin/time-entries/re-snapshot")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberIdOwner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entriesProcessed").value(2))
        .andExpect(jsonPath("$.entriesUpdated").value(0))
        .andExpect(jsonPath("$.entriesSkipped").value(2));
  }

  @Test
  @Order(3)
  void reSnapshot_withProjectFilter_onlyAffectsThatProject() throws Exception {
    // Re-snapshot filtered to project 1 only — should only process project 1's entry
    mockMvc
        .perform(
            post("/api/admin/time-entries/re-snapshot")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"projectId": "%s", "memberId": "%s"}
                    """
                        .formatted(projectId1, memberIdOwner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entriesProcessed").value(1))
        .andExpect(jsonPath("$.entriesSkipped").value(1));
  }

  @Test
  @Order(4)
  void reSnapshot_byNonAdmin_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/time-entries/re-snapshot")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isForbidden());
  }

  // --- JWT helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_resnap_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_resnap_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
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
