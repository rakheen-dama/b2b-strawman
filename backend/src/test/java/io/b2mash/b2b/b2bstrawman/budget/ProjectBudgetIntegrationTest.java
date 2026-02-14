package io.b2mash.b2b.b2bstrawman.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateService;
import io.b2mash.b2b.b2bstrawman.budget.BudgetStatus.BudgetStatusEnum;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
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
class ProjectBudgetIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_budget_test";
  private static final String ORG_ID_B = "org_budget_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectBudgetService budgetService;
  @Autowired private ProjectService projectService;
  @Autowired private TaskService taskService;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private BillingRateService billingRateService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdMember;
  private UUID projectId;
  private UUID taskId;

  // Tenant B for isolation tests
  private String tenantSchemaB;
  private UUID memberIdOwnerB;
  private UUID projectIdB;

  @BeforeAll
  void setup() throws Exception {
    // --- Tenant A ---
    provisioningService.provisionTenant(ORG_ID, "Budget Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_budget_owner", "budget_owner@test.com", "Budget Owner", "owner"));
    memberIdMember =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_budget_member", "budget_member@test.com", "Budget Member", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create project, task, billing rate, and time entries within tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var project =
                  projectService.createProject("Budget Test Project", "Test", memberIdOwner);
              projectId = project.getId();

              var task =
                  taskService.createTask(
                      projectId,
                      "Budget Test Task",
                      "Task for budget testing",
                      "MEDIUM",
                      "TASK",
                      null,
                      memberIdOwner,
                      "owner");
              taskId = task.getId();

              // Create billing rate for owner: $100/hour USD
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

              // Create time entries:
              // Entry 1: 120 min (2 hours), billable
              timeEntryService.createTimeEntry(
                  taskId,
                  LocalDate.of(2025, 1, 15),
                  120,
                  true,
                  null,
                  "Billable work",
                  memberIdOwner,
                  "owner");

              // Entry 2: 60 min (1 hour), non-billable
              timeEntryService.createTimeEntry(
                  taskId,
                  LocalDate.of(2025, 1, 16),
                  60,
                  false,
                  null,
                  "Non-billable work",
                  memberIdOwner,
                  "owner");

              // Entry 3: 30 min (0.5 hours), billable
              timeEntryService.createTimeEntry(
                  taskId,
                  LocalDate.of(2025, 1, 17),
                  30,
                  true,
                  null,
                  "More billable work",
                  memberIdOwner,
                  "owner");
            });
    // Total hours: 3.5 (120 + 60 + 30 = 210 min / 60 = 3.5)
    // Billable hours: 2.5 (120 + 30 = 150 min / 60 = 2.5)
    // Amount consumed (USD billable): 2.5 * $100 = $250

    // --- Tenant B (for isolation tests) ---
    provisioningService.provisionTenant(ORG_ID_B, "Budget Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");

    memberIdOwnerB =
        UUID.fromString(
            syncMember(
                ORG_ID_B,
                "user_budget_owner_b",
                "budget_owner_b@test.com",
                "Budget Owner B",
                "owner"));

    tenantSchemaB =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).orElseThrow().getSchemaName();

    // Create a project in tenant B
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaB)
        .where(RequestScopes.ORG_ID, ORG_ID_B)
        .where(RequestScopes.MEMBER_ID, memberIdOwnerB)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var project =
                  projectService.createProject("Budget Test Project B", "Test B", memberIdOwnerB);
              projectIdB = project.getId();
            });
  }

  // --- Service-Level CRUD Tests (Order 1-10) ---

  @Test
  @Order(1)
  void createBudget_hoursOnly() {
    var status =
        runInTenantAs(
            memberIdOwner,
            "owner",
            () ->
                budgetService.upsertBudget(
                    projectId,
                    new BigDecimal("100.00"),
                    null,
                    null,
                    80,
                    "Hours only budget",
                    memberIdOwner,
                    "owner"));

    assertThat(status.projectId()).isEqualTo(projectId);
    assertThat(status.budgetHours()).isEqualByComparingTo(new BigDecimal("100.00"));
    assertThat(status.budgetAmount()).isNull();
    assertThat(status.hoursConsumed()).isEqualByComparingTo(new BigDecimal("3.5"));
    assertThat(status.hoursStatus()).isEqualTo(BudgetStatusEnum.ON_TRACK);
    assertThat(status.overallStatus()).isEqualTo(BudgetStatusEnum.ON_TRACK);
    assertThat(status.amountStatus()).isNull();
  }

  @Test
  @Order(2)
  void upsertBudget_updateToHoursAndAmount() {
    var status =
        runInTenantAs(
            memberIdOwner,
            "owner",
            () ->
                budgetService.upsertBudget(
                    projectId,
                    new BigDecimal("100.00"),
                    new BigDecimal("10000.00"),
                    "USD",
                    80,
                    "Both hours and amount",
                    memberIdOwner,
                    "owner"));

    assertThat(status.budgetHours()).isEqualByComparingTo(new BigDecimal("100.00"));
    assertThat(status.budgetAmount()).isEqualByComparingTo(new BigDecimal("10000.00"));
    assertThat(status.budgetCurrency()).isEqualTo("USD");
    assertThat(status.hoursConsumed()).isEqualByComparingTo(new BigDecimal("3.5"));
    // Amount consumed = 2.5h * $100/h = $250
    assertThat(status.amountConsumed()).isEqualByComparingTo(new BigDecimal("250.00"));
    assertThat(status.hoursStatus()).isEqualTo(BudgetStatusEnum.ON_TRACK);
    assertThat(status.amountStatus()).isEqualTo(BudgetStatusEnum.ON_TRACK);
  }

  @Test
  @Order(3)
  void getBudgetWithStatus_returnsComputedValues() {
    var status =
        runInTenantAs(
            memberIdOwner,
            "owner",
            () -> budgetService.getBudgetWithStatus(projectId, memberIdOwner, "owner"));

    // 3.5 / 100 * 100 = 3.50%
    assertThat(status.hoursConsumedPct()).isEqualByComparingTo(new BigDecimal("3.50"));
    assertThat(status.hoursRemaining()).isEqualByComparingTo(new BigDecimal("96.5"));
    // 250 / 10000 * 100 = 2.50%
    assertThat(status.amountConsumedPct()).isEqualByComparingTo(new BigDecimal("2.50"));
    assertThat(status.amountRemaining()).isEqualByComparingTo(new BigDecimal("9750.00"));
    assertThat(status.notes()).isEqualTo("Both hours and amount");
  }

  @Test
  @Order(4)
  void amountConsumed_onlyCountsMatchingCurrency() {
    // Upsert with EUR currency — no entries have EUR billing rate, so amount should be 0
    var status =
        runInTenantAs(
            memberIdOwner,
            "owner",
            () ->
                budgetService.upsertBudget(
                    projectId,
                    new BigDecimal("100.00"),
                    new BigDecimal("10000.00"),
                    "EUR",
                    80,
                    "EUR budget",
                    memberIdOwner,
                    "owner"));

    assertThat(status.amountConsumed()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(status.amountConsumedPct()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(status.amountStatus()).isEqualTo(BudgetStatusEnum.ON_TRACK);
    // Hours still consumed normally
    assertThat(status.hoursConsumed()).isEqualByComparingTo(new BigDecimal("3.5"));
  }

  @Test
  @Order(5)
  void status_onTrack_whenUnderThreshold() {
    // Set budget to 100 hours with threshold 80% — 3.5/100 = 3.5% which is well under 80%
    var status =
        runInTenantAs(
            memberIdOwner,
            "owner",
            () ->
                budgetService.upsertBudget(
                    projectId,
                    new BigDecimal("100.00"),
                    null,
                    null,
                    80,
                    null,
                    memberIdOwner,
                    "owner"));

    assertThat(status.hoursStatus()).isEqualTo(BudgetStatusEnum.ON_TRACK);
    assertThat(status.overallStatus()).isEqualTo(BudgetStatusEnum.ON_TRACK);
  }

  @Test
  @Order(6)
  void status_atRisk_whenOverThresholdButUnder100() {
    // Set budget to 4 hours with threshold 80% — 3.5/4 = 87.5% which is > 80% but < 100%
    var status =
        runInTenantAs(
            memberIdOwner,
            "owner",
            () ->
                budgetService.upsertBudget(
                    projectId,
                    new BigDecimal("4.00"),
                    null,
                    null,
                    80,
                    null,
                    memberIdOwner,
                    "owner"));

    assertThat(status.hoursConsumedPct()).isEqualByComparingTo(new BigDecimal("87.50"));
    assertThat(status.hoursStatus()).isEqualTo(BudgetStatusEnum.AT_RISK);
    assertThat(status.overallStatus()).isEqualTo(BudgetStatusEnum.AT_RISK);
  }

  @Test
  @Order(7)
  void status_overBudget_whenAt100PctOrMore() {
    // Set budget to 3 hours — 3.5/3 = 116.67% which is >= 100%
    var status =
        runInTenantAs(
            memberIdOwner,
            "owner",
            () ->
                budgetService.upsertBudget(
                    projectId,
                    new BigDecimal("3.00"),
                    null,
                    null,
                    80,
                    null,
                    memberIdOwner,
                    "owner"));

    assertThat(status.hoursConsumedPct()).isGreaterThan(new BigDecimal("100"));
    assertThat(status.hoursStatus()).isEqualTo(BudgetStatusEnum.OVER_BUDGET);
    assertThat(status.overallStatus()).isEqualTo(BudgetStatusEnum.OVER_BUDGET);
    // Remaining should be negative
    assertThat(status.hoursRemaining()).isNegative();
  }

  @Test
  @Order(8)
  void upsertBudget_amountOnly() {
    var status =
        runInTenantAs(
            memberIdOwner,
            "owner",
            () ->
                budgetService.upsertBudget(
                    projectId,
                    null,
                    new BigDecimal("500.00"),
                    "USD",
                    80,
                    "Amount only",
                    memberIdOwner,
                    "owner"));

    assertThat(status.budgetHours()).isNull();
    assertThat(status.budgetAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
    // 250/500 = 50%, which is < 80% threshold
    assertThat(status.amountConsumedPct()).isEqualByComparingTo(new BigDecimal("50.00"));
    assertThat(status.amountStatus()).isEqualTo(BudgetStatusEnum.ON_TRACK);
    assertThat(status.hoursStatus()).isNull();
    assertThat(status.hoursConsumed()).isEqualByComparingTo(new BigDecimal("3.5"));
    assertThat(status.overallStatus()).isEqualTo(BudgetStatusEnum.ON_TRACK);
  }

  @Test
  @Order(9)
  void overallStatus_isWorseOfBoth() {
    // Set hours budget to 3 (OVER_BUDGET) and amount budget to 10000 (ON_TRACK)
    var status =
        runInTenantAs(
            memberIdOwner,
            "owner",
            () ->
                budgetService.upsertBudget(
                    projectId,
                    new BigDecimal("3.00"),
                    new BigDecimal("10000.00"),
                    "USD",
                    80,
                    null,
                    memberIdOwner,
                    "owner"));

    assertThat(status.hoursStatus()).isEqualTo(BudgetStatusEnum.OVER_BUDGET);
    assertThat(status.amountStatus()).isEqualTo(BudgetStatusEnum.ON_TRACK);
    assertThat(status.overallStatus()).isEqualTo(BudgetStatusEnum.OVER_BUDGET);
  }

  @Test
  @Order(10)
  void deleteBudget_thenGetReturns404() {
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          budgetService.deleteBudget(projectId, memberIdOwner, "owner");
          return null;
        });

    assertThatThrownBy(
            () ->
                runInTenantAs(
                    memberIdOwner,
                    "owner",
                    () -> budgetService.getBudgetWithStatus(projectId, memberIdOwner, "owner")))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // --- Cross-Tenant Isolation Tests (Order 11-12) ---

  @Test
  @Order(11)
  void tenantB_cannotSeeTenantA_budgetViaHttp() throws Exception {
    // Ensure budget exists in tenant A via HTTP
    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/budget")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "budgetHours": 100.00,
                      "notes": "Isolation test"
                    }
                    """))
        .andExpect(status().isOk());

    // Tenant B queries tenant A's project budget via HTTP — should get 404
    mockMvc
        .perform(get("/api/projects/" + projectId + "/budget").with(ownerJwtTenantB()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(12)
  void tenantB_cannotQueryTenantA_budgetStatus() throws Exception {
    // Tenant B queries tenant A's project budget status endpoint — should get 404
    mockMvc
        .perform(get("/api/projects/" + projectId + "/budget/status").with(ownerJwtTenantB()))
        .andExpect(status().isNotFound());
  }

  // --- MockMvc HTTP-Layer Tests (Order 13-17) ---

  @Test
  @Order(13)
  void httpPut_budget_upsertReturns200() throws Exception {
    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/budget")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "budgetHours": 200.00,
                      "budgetAmount": 50000.00,
                      "budgetCurrency": "USD",
                      "alertThresholdPct": 90,
                      "notes": "Updated via HTTP"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.budgetHours").value(200.00))
        .andExpect(jsonPath("$.budgetAmount").value(50000.00))
        .andExpect(jsonPath("$.budgetCurrency").value("USD"))
        .andExpect(jsonPath("$.alertThresholdPct").value(90))
        .andExpect(jsonPath("$.notes").value("Updated via HTTP"))
        .andExpect(jsonPath("$.hoursConsumed").isNumber())
        .andExpect(jsonPath("$.overallStatus").isString());
  }

  @Test
  @Order(14)
  void httpGet_budget_returns200WithStatus() throws Exception {
    mockMvc
        .perform(get("/api/projects/" + projectId + "/budget").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(projectId.toString()))
        .andExpect(jsonPath("$.budgetHours").value(200.00))
        .andExpect(jsonPath("$.hoursConsumed").isNumber())
        .andExpect(jsonPath("$.overallStatus").isString());
  }

  @Test
  @Order(15)
  void httpDelete_budget_returns204() throws Exception {
    mockMvc
        .perform(delete("/api/projects/" + projectId + "/budget").with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify it was deleted — GET should now return 404
    mockMvc
        .perform(get("/api/projects/" + projectId + "/budget").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(16)
  void httpGet_budget_withoutAuth_returns401() throws Exception {
    mockMvc
        .perform(get("/api/projects/" + projectId + "/budget"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(17)
  void httpPut_budget_withInvalidBody_returns400() throws Exception {
    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/budget")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "budgetHours": -10.00
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  // --- Helpers ---

  private <T> T runInTenantAs(
      UUID actorId, String role, java.util.concurrent.Callable<T> callable) {
    return runInTenantAs(tenantSchema, ORG_ID, actorId, role, callable);
  }

  private <T> T runInTenantAs(
      String schema,
      String orgId,
      UUID actorId,
      String role,
      java.util.concurrent.Callable<T> callable) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, schema)
          .where(RequestScopes.ORG_ID, orgId)
          .where(RequestScopes.MEMBER_ID, actorId)
          .where(RequestScopes.ORG_ROLE, role)
          .call(() -> callable.call());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_budget_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor ownerJwtTenantB() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_budget_owner_b").claim("o", Map.of("id", ORG_ID_B, "rol", "owner")))
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
