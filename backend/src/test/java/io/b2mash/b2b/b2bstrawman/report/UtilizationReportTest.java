package io.b2mash.b2b.b2bstrawman.report;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateService;
import io.b2mash.b2b.b2bstrawman.costrate.CostRateService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UtilizationReportTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_util_report_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectService projectService;
  @Autowired private TaskService taskService;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private BillingRateService billingRateService;
  @Autowired private CostRateService costRateService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdMember;
  private UUID projectId;
  private UUID taskId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Utilization Report Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_util_owner", "util_owner@test.com", "Util Owner", "owner"));
    memberIdMember =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_util_member", "util_member@test.com", "Util Member", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var project =
                  projectService.createProject("Util Test Project", "Test", memberIdOwner);
              projectId = project.getId();

              var task =
                  taskService.createTask(
                      projectId,
                      "Util Task",
                      "Task for utilization testing",
                      "MEDIUM",
                      "TASK",
                      null,
                      memberIdOwner,
                      "owner");
              taskId = task.getId();

              // Billing rate: $100/hr USD for owner (member default)
              billingRateService.createRate(
                  memberIdOwner,
                  null,
                  null,
                  "USD",
                  new BigDecimal("100.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");

              // Cost rate: $50/hr USD for owner
              costRateService.createCostRate(
                  memberIdOwner,
                  "USD",
                  new BigDecimal("50.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");

              // Owner entries:
              // Entry 1: 120 min (2 hours), billable, 2025-01-15
              timeEntryService.createTimeEntry(
                  taskId,
                  LocalDate.of(2025, 1, 15),
                  120,
                  true,
                  null,
                  "Billable work",
                  memberIdOwner,
                  "owner");

              // Entry 2: 60 min (1 hour), non-billable, 2025-01-16
              timeEntryService.createTimeEntry(
                  taskId,
                  LocalDate.of(2025, 1, 16),
                  60,
                  false,
                  null,
                  "Non-billable work",
                  memberIdOwner,
                  "owner");

              // Entry 3: 30 min (0.5 hours), billable, 2025-02-10
              timeEntryService.createTimeEntry(
                  taskId,
                  LocalDate.of(2025, 2, 10),
                  30,
                  true,
                  null,
                  "More billable work",
                  memberIdOwner,
                  "owner");

              // Billing rate: $80/hr USD for member (created by owner)
              billingRateService.createRate(
                  memberIdMember,
                  null,
                  null,
                  "USD",
                  new BigDecimal("80.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");

              // Cost rate: $40/hr USD for member (created by owner)
              costRateService.createCostRate(
                  memberIdMember,
                  "USD",
                  new BigDecimal("40.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");

              // Member entry: 90 min (1.5 hours), billable, 2025-01-20
              // Uses memberIdMember as entry owner, owner role for access check
              timeEntryService.createTimeEntry(
                  taskId,
                  LocalDate.of(2025, 1, 20),
                  90,
                  true,
                  null,
                  "Member billable work",
                  memberIdMember,
                  "owner");
            });

    // Owner: billable 2.5h, non-billable 1h, total 3.5h, utilization = 2.5/3.5*100 = 71.43%
    // Member: billable 1.5h, total 1.5h, utilization = 100.00%
  }

  @Test
  @Order(1)
  void adminQueriesAllMembersUtilization() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/utilization")
                .param("from", "2025-01-01")
                .param("to", "2025-12-31")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2025-01-01"))
        .andExpect(jsonPath("$.to").value("2025-12-31"))
        .andExpect(jsonPath("$.members").isArray())
        .andExpect(jsonPath("$.members.length()").value(2));
  }

  @Test
  @Order(2)
  void utilizationPercentageCalculatedCorrectly() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/utilization")
                .param("from", "2025-01-01")
                .param("to", "2025-12-31")
                .param("memberId", memberIdOwner.toString())
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members.length()").value(1))
        .andExpect(jsonPath("$.members[0].memberId").value(memberIdOwner.toString()))
        .andExpect(jsonPath("$.members[0].memberName").value("Util Owner"))
        .andExpect(jsonPath("$.members[0].totalHours").value(3.5))
        .andExpect(jsonPath("$.members[0].billableHours").value(2.5))
        .andExpect(jsonPath("$.members[0].nonBillableHours").value(1.0))
        .andExpect(jsonPath("$.members[0].utilizationPercent").value(71.43))
        .andExpect(jsonPath("$.members[0].currencies").isArray())
        .andExpect(jsonPath("$.members[0].currencies.length()").value(1))
        .andExpect(jsonPath("$.members[0].currencies[0].currency").value("USD"))
        .andExpect(jsonPath("$.members[0].currencies[0].billableValue").isNumber())
        .andExpect(jsonPath("$.members[0].currencies[0].costValue").isNumber());
  }

  @Test
  @Order(3)
  void memberQueriesOwnUtilizationOnly() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/utilization")
                .param("from", "2025-01-01")
                .param("to", "2025-12-31")
                .with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members.length()").value(1))
        .andExpect(jsonPath("$.members[0].memberId").value(memberIdMember.toString()))
        .andExpect(jsonPath("$.members[0].memberName").value("Util Member"))
        .andExpect(jsonPath("$.members[0].billableHours").value(1.5))
        .andExpect(jsonPath("$.members[0].utilizationPercent").value(100.00));
  }

  @Test
  @Order(4)
  void memberQueryingAnotherMemberGetsForbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/utilization")
                .param("from", "2025-01-01")
                .param("to", "2025-12-31")
                .param("memberId", memberIdOwner.toString())
                .with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(5)
  void dateRangeFilteringReturnsCorrectSubset() throws Exception {
    // Only January 2025 entries: owner has 120min + 60min, member has 90min
    mockMvc
        .perform(
            get("/api/reports/utilization")
                .param("from", "2025-01-01")
                .param("to", "2025-01-31")
                .param("memberId", memberIdOwner.toString())
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members.length()").value(1))
        .andExpect(jsonPath("$.members[0].totalHours").value(3.0))
        .andExpect(jsonPath("$.members[0].billableHours").value(2.0))
        .andExpect(jsonPath("$.members[0].nonBillableHours").value(1.0));
  }

  @Test
  @Order(6)
  void memberWithNoEntriesReturnsEmptyList() throws Exception {
    // Query a date range with no entries
    mockMvc
        .perform(
            get("/api/reports/utilization")
                .param("from", "2020-01-01")
                .param("to", "2020-12-31")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members").isArray())
        .andExpect(jsonPath("$.members.length()").value(0));
  }

  // --- Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_util_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_util_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
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
}
