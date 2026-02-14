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
class ProjectProfitabilityTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_proj_profit_test";

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
  private UUID projectIdEmpty;
  private UUID taskId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Project Profit Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_projprofit_owner",
                "projprofit_owner@test.com",
                "Profit Owner",
                "owner"));
    memberIdMember =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_projprofit_member",
                "projprofit_member@test.com",
                "Profit Member",
                "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              // Create project with time entries
              var project =
                  projectService.createProject("Profit Test Project", "Test", memberIdOwner);
              projectId = project.getId();

              // Create empty project (no time entries)
              var emptyProject =
                  projectService.createProject("Empty Project", "No data", memberIdOwner);
              projectIdEmpty = emptyProject.getId();

              var task =
                  taskService.createTask(
                      projectId,
                      "Profit Task",
                      "Task for profitability testing",
                      "MEDIUM",
                      "TASK",
                      null,
                      memberIdOwner,
                      "owner");
              taskId = task.getId();

              // Create billing rate: $100/hr USD (member default)
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

              // Create cost rate: $50/hr USD for owner
              costRateService.createCostRate(
                  memberIdOwner,
                  "USD",
                  new BigDecimal("50.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");

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
            });
    // Billable hours: 2.5 (120 + 30 = 150 min / 60)
    // Non-billable hours: 1.0 (60 min / 60)
    // Total hours: 3.5 (210 min / 60)
    // Billable value: 2.5 * 100 = $250.00
    // Cost value: 3.5 * 50 = $175.00 (all entries, billable + non-billable)
    // Margin: 250 - 175 = $75.00
    // Margin%: 75 / 250 * 100 = 30.00%
  }

  @Test
  @Order(1)
  void projectWithAllBillableFieldsReturnsCorrectRevenue() throws Exception {
    mockMvc
        .perform(get("/api/projects/{projectId}/profitability", projectId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(projectId.toString()))
        .andExpect(jsonPath("$.projectName").value("Profit Test Project"))
        .andExpect(jsonPath("$.currencies").isArray())
        .andExpect(jsonPath("$.currencies.length()").value(1))
        .andExpect(jsonPath("$.currencies[0].currency").value("USD"))
        .andExpect(jsonPath("$.currencies[0].totalBillableHours").value(2.5))
        .andExpect(jsonPath("$.currencies[0].totalHours").value(3.5))
        .andExpect(jsonPath("$.currencies[0].billableValue").isNumber());
  }

  @Test
  @Order(2)
  void projectWithMixedBillableNonBillableShowsCorrectHours() throws Exception {
    mockMvc
        .perform(get("/api/projects/{projectId}/profitability", projectId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencies[0].totalBillableHours").value(2.5))
        .andExpect(jsonPath("$.currencies[0].totalNonBillableHours").value(1.0))
        .andExpect(jsonPath("$.currencies[0].totalHours").value(3.5));
  }

  @Test
  @Order(3)
  void dateRangeFilteringReturnsSubset() throws Exception {
    // Only January entries: 2h billable + 1h non-billable = 3h total
    mockMvc
        .perform(
            get("/api/projects/{projectId}/profitability", projectId)
                .param("from", "2025-01-01")
                .param("to", "2025-01-31")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencies[0].totalBillableHours").value(2.0))
        .andExpect(jsonPath("$.currencies[0].totalNonBillableHours").value(1.0))
        .andExpect(jsonPath("$.currencies[0].totalHours").value(3.0));
  }

  @Test
  @Order(4)
  void marginCalculationWhenBothCurrenciesMatch() throws Exception {
    // billableValue: 2.5 * 100 = 250
    // costValue: 3.5 * 50 = 175
    // margin: 250 - 175 = 75
    // marginPercent: 75 / 250 * 100 = 30.00
    mockMvc
        .perform(get("/api/projects/{projectId}/profitability", projectId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencies[0].costValue").isNumber())
        .andExpect(jsonPath("$.currencies[0].margin").isNumber())
        .andExpect(jsonPath("$.currencies[0].marginPercent").value(30.00));
  }

  @Test
  @Order(5)
  void emptyProjectReturnsEmptyCurrencies() throws Exception {
    mockMvc
        .perform(get("/api/projects/{projectId}/profitability", projectIdEmpty).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(projectIdEmpty.toString()))
        .andExpect(jsonPath("$.currencies").isArray())
        .andExpect(jsonPath("$.currencies.length()").value(0));
  }

  @Test
  @Order(6)
  void nonProjectMemberRejectedWith404() throws Exception {
    // memberIdMember is not a member of the project — should get 404 (security-by-obscurity)
    mockMvc
        .perform(get("/api/projects/{projectId}/profitability", projectId).with(memberJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(7)
  void noDateParamsReturnsAllTimeData() throws Exception {
    // Without from/to, all 3 entries should be included
    mockMvc
        .perform(get("/api/projects/{projectId}/profitability", projectId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencies[0].totalHours").value(3.5));
  }

  @Test
  @Order(8)
  void unauthenticatedRequestReturns401() throws Exception {
    mockMvc
        .perform(get("/api/projects/{projectId}/profitability", projectId))
        .andExpect(status().isUnauthorized());
  }

  // --- Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_projprofit_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_projprofit_member")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")))
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
