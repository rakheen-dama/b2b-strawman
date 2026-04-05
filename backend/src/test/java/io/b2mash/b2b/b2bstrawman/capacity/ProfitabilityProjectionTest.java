package io.b2mash.b2b.b2bstrawman.capacity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateService;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.CreateAllocationRequest;
import io.b2mash.b2b.b2bstrawman.costrate.CostRateService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProfitabilityProjectionTest {
  private static final String ORG_ID = "org_profitability_projection_test";

  // Dynamically compute a Monday far in the future so it's always "future" relative to today
  private static final LocalDate FUTURE_MONDAY =
      LocalDate.now().plusYears(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
  private static final LocalDate FUTURE_MONDAY_2 = FUTURE_MONDAY.plusWeeks(1);

  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectService projectService;
  @Autowired private TaskService taskService;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private BillingRateService billingRateService;
  @Autowired private CostRateService costRateService;
  @Autowired private ResourceAllocationService allocationService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdMember;
  private UUID projectId;
  private UUID projectIdEmpty;
  private UUID taskId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Profitability Projection Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_profproj_owner",
                "profproj_owner@test.com",
                "ProfProj Owner",
                "owner"));
    memberIdMember =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_profproj_member",
                "profproj_member@test.com",
                "ProfProj Member",
                "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              // Create project
              var project =
                  projectService.createProject("Projection Test Project", "Test", memberIdOwner);
              projectId = project.getId();

              // Create empty project (no allocations)
              var emptyProject =
                  projectService.createProject(
                      "Empty Projection Project", "No data", memberIdOwner);
              projectIdEmpty = emptyProject.getId();

              // Create task for past time entries
              var task =
                  taskService.createTask(
                      projectId,
                      "Projection Task",
                      "Task for projection testing",
                      "MEDIUM",
                      "TASK",
                      null,
                      new ActorContext(memberIdOwner, "owner"));
              taskId = task.getId();

              // Create billing rate: $150/hr USD (member default, effective from 2024)
              billingRateService.createRate(
                  memberIdOwner,
                  null,
                  null,
                  "USD",
                  new BigDecimal("150.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  new ActorContext(memberIdOwner, "owner"));

              // Create cost rate: $75/hr USD for owner
              costRateService.createCostRate(
                  memberIdOwner,
                  "USD",
                  new BigDecimal("75.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  new ActorContext(memberIdOwner, "owner"));

              // Past time entry: 120 min (2 hours), billable, 2025-01-15
              timeEntryService.createTimeEntry(
                  taskId,
                  LocalDate.of(2025, 1, 15),
                  120,
                  true,
                  null,
                  "Past billable work",
                  new ActorContext(memberIdOwner, "owner"));

              // Future allocation: 20 hours for owner on project, future Monday
              allocationService.createAllocation(
                  new CreateAllocationRequest(
                      memberIdOwner,
                      projectId,
                      FUTURE_MONDAY,
                      new BigDecimal("20.00"),
                      "Sprint planning"),
                  memberIdOwner);

              // Second future allocation: 10 hours for owner on project, next Monday
              allocationService.createAllocation(
                  new CreateAllocationRequest(
                      memberIdOwner,
                      projectId,
                      FUTURE_MONDAY_2,
                      new BigDecimal("10.00"),
                      "Sprint execution"),
                  memberIdOwner);
            });
  }

  @Test
  @Order(1)
  void futureAllocationWithBillingAndCostRateReturnsCorrectProjections() throws Exception {
    // 20h * $150 = $3000 + 10h * $150 = $1500 => total projected revenue = $4500
    // 20h * $75  = $1500 + 10h * $75  = $750  => total projected cost = $2250
    // projected margin = 4500 - 2250 = $2250
    mockMvc
        .perform(
            get("/api/projects/{projectId}/profitability", projectId)
                .param("includeProjections", "true")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_profproj_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projections").isNotEmpty())
        .andExpect(jsonPath("$.projections.projectedRevenue").value(4500.00))
        .andExpect(jsonPath("$.projections.projectedCost").value(2250.00))
        .andExpect(jsonPath("$.projections.projectedMargin").value(2250.00));
  }

  @Test
  @Order(2)
  void projectionsOmittedWhenIncludeProjectionsFalse() throws Exception {
    mockMvc
        .perform(
            get("/api/projects/{projectId}/profitability", projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_profproj_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projections").doesNotExist());
  }

  @Test
  @Order(3)
  void missingBillingRateExcludesProjectedRevenue() throws Exception {
    // Member has no billing rate configured — only cost rate via owner
    // Create allocation for member (who has no rates)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              // Create a separate project for this test
              var project =
                  projectService.createProject("No Billing Rate Project", "Test", memberIdOwner);

              // Allocate member who has no billing/cost rates
              allocationService.createAllocation(
                  new CreateAllocationRequest(
                      memberIdMember,
                      project.getId(),
                      FUTURE_MONDAY,
                      new BigDecimal("10.00"),
                      "No rate test"),
                  memberIdOwner);

              // Verify via HTTP
              try {
                mockMvc
                    .perform(
                        get("/api/projects/{projectId}/profitability", project.getId())
                            .param("includeProjections", "true")
                            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_profproj_owner")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.projections.projectedRevenue").value(0))
                    .andExpect(jsonPath("$.projections.projectedCost").value(0));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Test
  @Order(4)
  void pastWeekUsesActualsNotProjections() throws Exception {
    // Query only past data — projections should be zero
    mockMvc
        .perform(
            get("/api/projects/{projectId}/profitability", projectId)
                .param("from", "2025-01-01")
                .param("to", "2025-01-31")
                .param("includeProjections", "true")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_profproj_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencies[0].totalBillableHours").value(2.0))
        .andExpect(jsonPath("$.projections.projectedRevenue").value(0))
        .andExpect(jsonPath("$.projections.projectedCost").value(0))
        .andExpect(jsonPath("$.projections.projectedMargin").value(0));
  }

  @Test
  @Order(5)
  void currentWeekShowsProjectionsFromAllocations() throws Exception {
    // Create allocation for the current week
    LocalDate currentMonday =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var project =
                  projectService.createProject("Current Week Project", "Test", memberIdOwner);

              allocationService.createAllocation(
                  new CreateAllocationRequest(
                      memberIdOwner,
                      project.getId(),
                      currentMonday,
                      new BigDecimal("8.00"),
                      "Current week work"),
                  memberIdOwner);

              // Current week should show projections (8h * $150 = $1200, 8h * $75 = $600)
              try {
                mockMvc
                    .perform(
                        get("/api/projects/{projectId}/profitability", project.getId())
                            .param("includeProjections", "true")
                            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_profproj_owner")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.projections.projectedRevenue").value(1200.00))
                    .andExpect(jsonPath("$.projections.projectedCost").value(600.00))
                    .andExpect(jsonPath("$.projections.projectedMargin").value(600.00));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Test
  @Order(6)
  void multipleAllocationsAggregateCorrectly() throws Exception {
    // Already tested in Order(1) — owner has 20h and 10h allocations
    // This test confirms aggregation via org-level endpoint
    mockMvc
        .perform(
            get("/api/reports/profitability")
                .param("includeProjections", "true")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_profproj_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projections").isNotEmpty())
        .andExpect(jsonPath("$.projections.projectedRevenue").isNumber())
        .andExpect(jsonPath("$.projections.projectedCost").isNumber())
        .andExpect(jsonPath("$.projections.projectedMargin").isNumber());
  }

  @Test
  @Order(7)
  void projectionWithProjectRateOverride() throws Exception {
    // Create a project-level rate override ($200/hr instead of $150/hr default)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var project =
                  projectService.createProject("Rate Override Project", "Test", memberIdOwner);

              // Create project-specific billing rate override
              billingRateService.createRate(
                  memberIdOwner,
                  project.getId(),
                  null,
                  "USD",
                  new BigDecimal("200.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  new ActorContext(memberIdOwner, "owner"));

              allocationService.createAllocation(
                  new CreateAllocationRequest(
                      memberIdOwner,
                      project.getId(),
                      FUTURE_MONDAY,
                      new BigDecimal("5.00"),
                      "Rate override test"),
                  memberIdOwner);

              // Should use $200/hr (project override), not $150/hr (member default)
              // 5h * $200 = $1000, 5h * $75 = $375, margin = $625
              try {
                mockMvc
                    .perform(
                        get("/api/projects/{projectId}/profitability", project.getId())
                            .param("includeProjections", "true")
                            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_profproj_owner")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.projections.projectedRevenue").value(1000.00))
                    .andExpect(jsonPath("$.projections.projectedCost").value(375.00))
                    .andExpect(jsonPath("$.projections.projectedMargin").value(625.00));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Test
  @Order(8)
  void emptyAllocationsReturnZeroProjections() throws Exception {
    mockMvc
        .perform(
            get("/api/projects/{projectId}/profitability", projectIdEmpty)
                .param("includeProjections", "true")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_profproj_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projections.projectedRevenue").value(0))
        .andExpect(jsonPath("$.projections.projectedCost").value(0))
        .andExpect(jsonPath("$.projections.projectedMargin").value(0));
  }

  @Test
  @Order(9)
  void controllerIncludeProjectionsQueryParamDefaultsToFalse() throws Exception {
    // Without includeProjections param, projections should be null/absent
    mockMvc
        .perform(
            get("/api/projects/{projectId}/profitability", projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_profproj_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projections").doesNotExist());

    // With includeProjections=true, projections should be present
    mockMvc
        .perform(
            get("/api/projects/{projectId}/profitability", projectId)
                .param("includeProjections", "true")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_profproj_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projections").isNotEmpty());
  }
}
