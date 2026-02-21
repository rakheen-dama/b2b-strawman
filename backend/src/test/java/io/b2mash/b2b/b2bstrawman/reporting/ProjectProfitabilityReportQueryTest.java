package io.b2mash.b2b.b2bstrawman.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectProfitabilityReportQueryTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_ppq_test";

  @Autowired private ProjectProfitabilityReportQuery profitabilityReportQuery;
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID projectId1;
  private UUID projectId2;
  private UUID projectId3;
  private UUID customerId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Profitability Query Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberId =
        UUID.fromString(syncMember("user_ppq_owner", "ppq_owner@test.com", "John Doe", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Create customer
                      var customer =
                          TestCustomerFactory.createActiveCustomer(
                              "Alpha Client", "alpha@test.com", memberId);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      // Project 1: has both revenue and cost
                      var p1 = new Project("Project One", "First project", memberId);
                      p1 = projectRepository.save(p1);
                      projectId1 = p1.getId();

                      // Link project 1 to customer
                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId1, memberId));

                      // Project 2: has revenue but no cost
                      var p2 = new Project("Project Two", "Second project", memberId);
                      p2 = projectRepository.save(p2);
                      projectId2 = p2.getId();

                      // Project 3: has cost but no revenue (all non-billable)
                      var p3 = new Project("Project Three", "Third project", memberId);
                      p3 = projectRepository.save(p3);
                      projectId3 = p3.getId();

                      // Tasks
                      var task1 =
                          new Task(projectId1, "Task A", null, "MEDIUM", "TASK", null, memberId);
                      task1 = taskRepository.save(task1);

                      var task2 =
                          new Task(projectId2, "Task B", null, "MEDIUM", "TASK", null, memberId);
                      task2 = taskRepository.save(task2);

                      var task3 =
                          new Task(projectId3, "Task C", null, "MEDIUM", "TASK", null, memberId);
                      task3 = taskRepository.save(task3);

                      // Project 1: 2 hours billable @ R200/hr, cost @ R100/hr
                      var te1 =
                          new TimeEntry(
                              task1.getId(),
                              memberId,
                              LocalDate.of(2025, 1, 15),
                              120, // 2 hours
                              true,
                              null,
                              "Billable work on P1");
                      te1.snapshotBillingRate(new BigDecimal("200.00"), "ZAR");
                      te1.snapshotCostRate(new BigDecimal("100.00"), "ZAR");
                      timeEntryRepository.save(te1);

                      // Project 1: 1 hour billable @ R200/hr, cost @ R100/hr
                      var te2 =
                          new TimeEntry(
                              task1.getId(),
                              memberId,
                              LocalDate.of(2025, 1, 16),
                              60, // 1 hour
                              true,
                              null,
                              "More billable work on P1");
                      te2.snapshotBillingRate(new BigDecimal("200.00"), "ZAR");
                      te2.snapshotCostRate(new BigDecimal("100.00"), "ZAR");
                      timeEntryRepository.save(te2);

                      // Project 2: 3 hours billable @ R150/hr, no cost rate
                      var te3 =
                          new TimeEntry(
                              task2.getId(),
                              memberId,
                              LocalDate.of(2025, 1, 15),
                              180, // 3 hours
                              true,
                              null,
                              "Billable work on P2");
                      te3.snapshotBillingRate(new BigDecimal("150.00"), "ZAR");
                      // No cost rate snapshot
                      timeEntryRepository.save(te3);

                      // Project 3: 2 hours non-billable, cost @ R80/hr
                      var te4 =
                          new TimeEntry(
                              task3.getId(),
                              memberId,
                              LocalDate.of(2025, 1, 15),
                              120, // 2 hours
                              false,
                              null,
                              "Non-billable work on P3");
                      // Non-billable: billing rate snapshot set but won't count as revenue
                      // (the query filters on te.billable)
                      te4.snapshotCostRate(new BigDecimal("80.00"), "ZAR");
                      timeEntryRepository.save(te4);
                    }));
  }

  @Test
  void marginComputedCorrectly() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          var result = profitabilityReportQuery.executeAll(params);

          // Project 1: revenue = 3h * R200 = R600, cost = 3h * R100 = R300, margin = R300
          var p1Row =
              result.rows().stream()
                  .filter(r -> projectId1.equals(r.get("projectId")))
                  .findFirst()
                  .orElseThrow();
          assertThat(compareBd(p1Row.get("revenue"), "600.00")).isTrue();
          assertThat(compareBd(p1Row.get("cost"), "300.00")).isTrue();
          assertThat(compareBd(p1Row.get("margin"), "300.00")).isTrue();
        });
  }

  @Test
  void marginPercentComputedCorrectly() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          var result = profitabilityReportQuery.executeAll(params);

          // Project 1: marginPercent = (300 / 600) * 100 = 50.00%
          var p1Row =
              result.rows().stream()
                  .filter(r -> projectId1.equals(r.get("projectId")))
                  .findFirst()
                  .orElseThrow();
          assertThat(compareBd(p1Row.get("marginPercent"), "50.00")).isTrue();
        });
  }

  @Test
  void summaryTotalsCorrect() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          var result = profitabilityReportQuery.executeAll(params);

          var summary = result.summary();
          // Total revenue = P1(600) + P2(450) = 1050
          // Total cost = P1(300) + P3(160) = 460
          // Total margin = 1050 - 460 = 590
          assertThat(compareBd(summary.get("totalRevenue"), "1050.00")).isTrue();
          assertThat(compareBd(summary.get("totalCost"), "460.00")).isTrue();
          assertThat(compareBd(summary.get("totalMargin"), "590.00")).isTrue();
        });
  }

  @Test
  void projectIdFilterWorks() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          params.put("projectId", projectId1.toString());

          var result = profitabilityReportQuery.executeAll(params);

          assertThat(result.rows()).hasSize(1);
          assertThat(result.rows().getFirst().get("projectName")).isEqualTo("Project One");
        });
  }

  @Test
  void customerIdFilterWorks() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          params.put("customerId", customerId.toString());

          var result = profitabilityReportQuery.executeAll(params);

          // Only Project 1 is linked to the customer
          assertThat(result.rows()).hasSize(1);
          assertThat(result.rows().getFirst().get("projectName")).isEqualTo("Project One");
        });
  }

  @Test
  void projectWithNoRevenueShowsZeroMargin() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          var result = profitabilityReportQuery.executeAll(params);

          // Project 3 has cost but no revenue (non-billable entries only)
          var p3Row =
              result.rows().stream()
                  .filter(r -> projectId3.equals(r.get("projectId")))
                  .findFirst()
                  .orElseThrow();
          assertThat(compareBd(p3Row.get("revenue"), "0")).isTrue();
          assertThat(compareBd(p3Row.get("cost"), "160.00")).isTrue();
          assertThat(compareBd(p3Row.get("margin"), "-160.00")).isTrue();
          // marginPercent should be 0 when revenue is 0
          assertThat(compareBd(p3Row.get("marginPercent"), "0")).isTrue();
        });
  }

  @Test
  void projectWithNoCostShowsFullMargin() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          var result = profitabilityReportQuery.executeAll(params);

          // Project 2 has revenue but no cost
          var p2Row =
              result.rows().stream()
                  .filter(r -> projectId2.equals(r.get("projectId")))
                  .findFirst()
                  .orElseThrow();
          assertThat(compareBd(p2Row.get("revenue"), "450.00")).isTrue();
          assertThat(compareBd(p2Row.get("cost"), "0")).isTrue();
          assertThat(compareBd(p2Row.get("margin"), "450.00")).isTrue();
          assertThat(compareBd(p2Row.get("marginPercent"), "100.00")).isTrue();
        });
  }

  // --- Helpers ---

  private Map<String, Object> dateRangeParams(String from, String to) {
    var params = new HashMap<String, Object>();
    params.put("dateFrom", from);
    params.put("dateTo", to);
    return params;
  }

  private boolean compareBd(Object value, String expected) {
    if (value instanceof BigDecimal bd) {
      return bd.setScale(2, RoundingMode.HALF_UP)
              .compareTo(new BigDecimal(expected).setScale(2, RoundingMode.HALF_UP))
          == 0;
    }
    return false;
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> transactionTemplate.executeWithoutResult(tx -> action.run()));
  }

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
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
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
