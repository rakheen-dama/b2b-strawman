package io.b2mash.b2b.b2bstrawman.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimesheetReportQueryTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_tsq_test";

  @Autowired private TimesheetReportQuery timesheetReportQuery;
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId1;
  private UUID memberId2;
  private UUID projectId1;
  private UUID projectId2;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Timesheet Query Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberId1 =
        UUID.fromString(syncMember("user_tsq_owner", "tsq_owner@test.com", "Alice Smith", "owner"));
    memberId2 =
        UUID.fromString(
            syncMember("user_tsq_member", "tsq_member@test.com", "Bob Jones", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId1)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Project 1
                      var p1 = new Project("Project Alpha", "First project", memberId1);
                      p1 = projectRepository.save(p1);
                      projectId1 = p1.getId();

                      // Project 2
                      var p2 = new Project("Project Beta", "Second project", memberId1);
                      p2 = projectRepository.save(p2);
                      projectId2 = p2.getId();

                      // Tasks
                      var task1 =
                          new Task(
                              projectId1, "Design API", null, "MEDIUM", "TASK", null, memberId1);
                      task1 = taskRepository.save(task1);

                      var task2 =
                          new Task(projectId2, "Build UI", null, "MEDIUM", "TASK", null, memberId1);
                      task2 = taskRepository.save(task2);

                      // Time entries for member 1 on project 1 (billable)
                      timeEntryRepository.save(
                          new TimeEntry(
                              task1.getId(),
                              memberId1,
                              LocalDate.of(2025, 1, 15),
                              120,
                              true,
                              null,
                              "API design"));

                      // Time entries for member 1 on project 1 (non-billable)
                      timeEntryRepository.save(
                          new TimeEntry(
                              task1.getId(),
                              memberId1,
                              LocalDate.of(2025, 1, 16),
                              60,
                              false,
                              null,
                              "Internal meeting"));

                      // Time entries for member 2 on project 1
                      timeEntryRepository.save(
                          new TimeEntry(
                              task1.getId(),
                              memberId2,
                              LocalDate.of(2025, 1, 15),
                              90,
                              true,
                              null,
                              "API review"));

                      // Time entries for member 1 on project 2
                      timeEntryRepository.save(
                          new TimeEntry(
                              task2.getId(),
                              memberId1,
                              LocalDate.of(2025, 1, 17),
                              180,
                              true,
                              null,
                              "UI work"));

                      // Time entries for member 2 on project 2
                      timeEntryRepository.save(
                          new TimeEntry(
                              task2.getId(),
                              memberId2,
                              LocalDate.of(2025, 1, 17),
                              45,
                              false,
                              null,
                              "UI meeting"));
                    }));
  }

  @Test
  void groupByMemberReturnsMemberRows() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          params.put("groupBy", "member");

          var result = timesheetReportQuery.execute(params, PageRequest.of(0, 50));

          assertThat(result.rows()).hasSize(2);
          // Alice has more hours (120+60+180 = 360 min = 6h), Bob has (90+45 = 135 min = 2.25h)
          assertThat(result.rows().getFirst().get("groupLabel")).isEqualTo("Alice Smith");
          assertThat(((Number) result.rows().getFirst().get("totalHours")).doubleValue())
              .isEqualTo(6.0);
        });
  }

  @Test
  void groupByProjectReturnsProjectRows() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          params.put("groupBy", "project");

          var result = timesheetReportQuery.execute(params, PageRequest.of(0, 50));

          assertThat(result.rows()).hasSize(2);
          var labels = result.rows().stream().map(r -> r.get("groupLabel")).toList();
          assertThat(labels).containsExactlyInAnyOrder("Project Alpha", "Project Beta");
        });
  }

  @Test
  void groupByDateReturnsDateRows() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          params.put("groupBy", "date");

          var result = timesheetReportQuery.execute(params, PageRequest.of(0, 50));

          assertThat(result.rows()).hasSize(3); // Jan 15, 16, 17
          // Ordered by date
          assertThat(result.rows().getFirst().get("groupLabel")).isEqualTo("2025-01-15");
          assertThat(result.rows().getLast().get("groupLabel")).isEqualTo("2025-01-17");
        });
  }

  @Test
  void dateFilterNarrowsResults() {
    runInTenant(
        () -> {
          // Only Jan 15
          var params = dateRangeParams("2025-01-15", "2025-01-15");
          params.put("groupBy", "member");

          var result = timesheetReportQuery.execute(params, PageRequest.of(0, 50));

          assertThat(result.rows()).hasSize(2); // Both members have entries on Jan 15
          var totalHours =
              result.rows().stream()
                  .mapToDouble(r -> ((Number) r.get("totalHours")).doubleValue())
                  .sum();
          // Alice: 120 min = 2h, Bob: 90 min = 1.5h
          assertThat(totalHours).isEqualTo(3.5);
        });
  }

  @Test
  void projectIdFilterNarrowsResults() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          params.put("groupBy", "member");
          params.put("projectId", projectId1.toString());

          var result = timesheetReportQuery.execute(params, PageRequest.of(0, 50));

          // Only project 1 entries
          assertThat(result.rows()).hasSize(2); // Both members have entries on project 1
          var totalHours =
              result.rows().stream()
                  .mapToDouble(r -> ((Number) r.get("totalHours")).doubleValue())
                  .sum();
          // Alice: 120+60 = 180 min = 3h, Bob: 90 min = 1.5h
          assertThat(totalHours).isEqualTo(4.5);
        });
  }

  @Test
  void memberIdFilterNarrowsResults() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          params.put("groupBy", "member");
          params.put("memberId", memberId2.toString());

          var result = timesheetReportQuery.execute(params, PageRequest.of(0, 50));

          assertThat(result.rows()).hasSize(1);
          assertThat(result.rows().getFirst().get("groupLabel")).isEqualTo("Bob Jones");
        });
  }

  @Test
  void emptyDateRangeReturnsNoRows() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2024-01-01", "2024-01-31");
          params.put("groupBy", "member");

          var result = timesheetReportQuery.execute(params, PageRequest.of(0, 50));

          assertThat(result.rows()).isEmpty();
          assertThat(((Number) result.summary().get("totalHours")).doubleValue()).isEqualTo(0.0);
        });
  }

  @Test
  void billableNonBillableSplitIsCorrect() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          params.put("groupBy", "member");
          params.put("memberId", memberId1.toString());

          var result = timesheetReportQuery.execute(params, PageRequest.of(0, 50));

          assertThat(result.rows()).hasSize(1);
          var row = result.rows().getFirst();
          // Alice: billable = 120+180 = 300 min = 5h, non-billable = 60 min = 1h
          assertThat(((Number) row.get("billableHours")).doubleValue()).isEqualTo(5.0);
          assertThat(((Number) row.get("nonBillableHours")).doubleValue()).isEqualTo(1.0);
          assertThat(((Number) row.get("totalHours")).doubleValue()).isEqualTo(6.0);
        });
  }

  @Test
  void defaultGroupByIsMemberWhenAbsent() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          // No groupBy specified

          var result = timesheetReportQuery.execute(params, PageRequest.of(0, 50));

          // Should default to member grouping (same as groupBy=member)
          assertThat(result.rows()).hasSize(2);
          // First row should be Alice (most hours)
          assertThat(result.rows().getFirst().get("groupLabel")).isEqualTo("Alice Smith");
        });
  }

  @Test
  void summaryAggregatesAllRows() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          params.put("groupBy", "member");

          var result = timesheetReportQuery.execute(params, PageRequest.of(0, 50));

          var summary = result.summary();
          // Total: (120+60+90+180+45) = 495 min = 8.25h
          assertThat(((Number) summary.get("totalHours")).doubleValue()).isEqualTo(8.25);
          // Billable: (120+90+180) = 390 min = 6.5h
          assertThat(((Number) summary.get("billableHours")).doubleValue()).isEqualTo(6.5);
          // Non-billable: (60+45) = 105 min = 1.75h
          assertThat(((Number) summary.get("nonBillableHours")).doubleValue()).isEqualTo(1.75);
          assertThat(((Number) summary.get("entryCount")).longValue()).isEqualTo(5L);
        });
  }

  @Test
  void paginationSlicesResults() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          params.put("groupBy", "date");

          // Page 0, size 2 of 3 rows
          var result = timesheetReportQuery.execute(params, PageRequest.of(0, 2));

          assertThat(result.rows()).hasSize(2);
          assertThat(result.totalElements()).isEqualTo(3);
          assertThat(result.totalPages()).isEqualTo(2);

          // Page 1
          var result2 = timesheetReportQuery.execute(params, PageRequest.of(1, 2));
          assertThat(result2.rows()).hasSize(1);
        });
  }

  @Test
  void executeAllReturnsAllRowsWithoutPagination() {
    runInTenant(
        () -> {
          var params = dateRangeParams("2025-01-01", "2025-01-31");
          params.put("groupBy", "date");

          var result = timesheetReportQuery.executeAll(params);

          assertThat(result.rows()).hasSize(3);
          assertThat(result.totalElements()).isEqualTo(3);
          assertThat(result.totalPages()).isEqualTo(1);
        });
  }

  // --- Helpers ---

  private Map<String, Object> dateRangeParams(String from, String to) {
    var params = new HashMap<String, Object>();
    params.put("dateFrom", from);
    params.put("dateTo", to);
    return params;
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId1)
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
