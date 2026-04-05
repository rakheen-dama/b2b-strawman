package io.b2mash.b2b.b2bstrawman.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportExecutionServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_res_test";

  @Autowired private ReportExecutionService reportExecutionService;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Report Execution Service Test Org", null);

    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_res_owner", "res_owner@test.com", "RES Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create test data
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var project = new Project("RES Test Project", "Test project", memberId);
                      project = projectRepository.save(project);

                      var task =
                          new Task(
                              project.getId(), "RES Task", null, "MEDIUM", "TASK", null, memberId);
                      task = taskRepository.save(task);

                      timeEntryRepository.save(
                          new TimeEntry(
                              task.getId(),
                              memberId,
                              LocalDate.of(2025, 3, 1),
                              120,
                              true,
                              null,
                              "Test work"));
                    }));
  }

  @Test
  void executeWithValidSlugDispatchesToTimesheetQuery() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var params = new HashMap<String, Object>();
              params.put("dateFrom", "2025-03-01");
              params.put("dateTo", "2025-03-31");
              params.put("groupBy", "member");

              var response =
                  reportExecutionService.execute("timesheet", params, PageRequest.of(0, 50));

              assertThat(response.reportName()).isEqualTo("Timesheet Report");
              assertThat(response.rows()).isNotEmpty();
              assertThat(response.columns()).isNotEmpty();
              assertThat(response.pagination()).isNotNull();
            });
  }

  @Test
  void executeWithUnknownSlugThrowsResourceNotFoundException() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var params = new HashMap<String, Object>();
              params.put("dateFrom", "2025-03-01");
              params.put("dateTo", "2025-03-31");

              assertThatThrownBy(
                      () ->
                          reportExecutionService.execute(
                              "nonexistent-report", params, PageRequest.of(0, 50)))
                  .isInstanceOf(ResourceNotFoundException.class);
            });
  }

  @Test
  void executeFiresReportGeneratedAuditEvent() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var params = new HashMap<String, Object>();
              params.put("dateFrom", "2025-03-01");
              params.put("dateTo", "2025-03-31");
              params.put("groupBy", "member");

              reportExecutionService.execute("timesheet", params, PageRequest.of(0, 50));

              // Verify audit event
              var auditPage =
                  auditEventRepository.findByFilter(
                      "REPORT", null, null, "REPORT_GENERATED", null, null, PageRequest.of(0, 10));
              assertThat(auditPage.getContent()).isNotEmpty();
              var latestEvent = auditPage.getContent().getFirst();
              assertThat(latestEvent.getEventType()).isEqualTo("REPORT_GENERATED");
              assertThat(latestEvent.getEntityType()).isEqualTo("REPORT");
            });
  }

  @Test
  void paginationParametersAreHonored() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var params = new HashMap<String, Object>();
              params.put("dateFrom", "2025-03-01");
              params.put("dateTo", "2025-03-31");
              params.put("groupBy", "member");

              var response =
                  reportExecutionService.execute("timesheet", params, PageRequest.of(0, 1));

              assertThat(response.pagination().page()).isEqualTo(0);
              assertThat(response.pagination().size()).isEqualTo(1);
            });
  }
}
