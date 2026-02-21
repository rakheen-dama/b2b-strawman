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
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
class ReportRenderingServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_rrs_test";

  @Autowired private ReportRenderingService reportRenderingService;
  @Autowired private ReportExecutionService reportExecutionService;
  @Autowired private ReportDefinitionRepository reportDefinitionRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Report Rendering Service Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberId =
        UUID.fromString(syncMember("user_rrs_owner", "rrs_owner@test.com", "RRS Owner", "owner"));

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
                      var project = new Project("RRS Test Project", "Test project", memberId);
                      project = projectRepository.save(project);

                      var task =
                          new Task(
                              project.getId(), "RRS Task", null, "MEDIUM", "TASK", null, memberId);
                      task = taskRepository.save(task);

                      timeEntryRepository.save(
                          new TimeEntry(
                              task.getId(),
                              memberId,
                              LocalDate.of(2025, 4, 1),
                              120,
                              true,
                              null,
                              "RRS test work"));
                    }));
  }

  @Test
  void renderHtmlReturnsNonEmptyHtmlContainingReportName() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var definition = reportDefinitionRepository.findBySlug("timesheet").orElseThrow();
              var params = new HashMap<String, Object>();
              params.put("dateFrom", "2025-04-01");
              params.put("dateTo", "2025-04-30");
              params.put("groupBy", "member");

              var result = reportExecutionService.executeForExport("timesheet", params);
              String html = reportRenderingService.renderHtml(definition, result, params);

              assertThat(html).isNotEmpty();
              assertThat(html).contains("Timesheet Report");
            });
  }

  @Test
  void renderHtmlContainsBrandingColorWhenOrgSettingsHasBrandColor() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Find existing or create OrgSettings, then set brand color
                      var settings =
                          orgSettingsRepository
                              .findForCurrentTenant()
                              .orElseGet(() -> orgSettingsRepository.save(new OrgSettings("ZAR")));
                      settings.setBrandColor("#ff5733");
                      orgSettingsRepository.save(settings);
                    }));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var definition = reportDefinitionRepository.findBySlug("timesheet").orElseThrow();
              var params = new HashMap<String, Object>();
              params.put("dateFrom", "2025-04-01");
              params.put("dateTo", "2025-04-30");
              params.put("groupBy", "member");

              var result = reportExecutionService.executeForExport("timesheet", params);
              String html = reportRenderingService.renderHtml(definition, result, params);

              assertThat(html).contains("#ff5733");
            });
  }

  @Test
  void renderHtmlContainsFooterTextWhenOrgSettingsHasFooterText() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsRepository.findForCurrentTenant().orElse(null);
                      if (settings != null) {
                        settings.setDocumentFooterText("Confidential - RRS Test Org");
                        orgSettingsRepository.save(settings);
                      }
                    }));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var definition = reportDefinitionRepository.findBySlug("timesheet").orElseThrow();
              var params = new HashMap<String, Object>();
              params.put("dateFrom", "2025-04-01");
              params.put("dateTo", "2025-04-30");
              params.put("groupBy", "member");

              var result = reportExecutionService.executeForExport("timesheet", params);
              String html = reportRenderingService.renderHtml(definition, result, params);

              assertThat(html).contains("Confidential - RRS Test Org");
            });
  }

  @Test
  void generateFilenameWithDateRangeParameters() {
    var params = new HashMap<String, Object>();
    params.put("dateFrom", "2025-04-01");
    params.put("dateTo", "2025-04-30");

    String filename = reportRenderingService.generateFilename("timesheet", params, "pdf");
    assertThat(filename).isEqualTo("timesheet-2025-04-01-to-2025-04-30.pdf");
  }

  @Test
  void generateFilenameWithAsOfDateParameter() {
    var params = new HashMap<String, Object>();
    params.put("asOfDate", "2025-04-30");

    String filename = reportRenderingService.generateFilename("invoice-aging", params, "pdf");
    assertThat(filename).isEqualTo("invoice-aging-2025-04-30.pdf");
  }

  @Test
  void generateFilenameWithNoDateParameters() {
    var params = new HashMap<String, Object>();
    params.put("groupBy", "member");

    String filename = reportRenderingService.generateFilename("timesheet", params, "pdf");
    assertThat(filename).isEqualTo("timesheet.pdf");
  }

  // --- CSV tests ---

  @Test
  void writeCsvContainsMetadataHeadersAndColumnHeaders() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var definition = reportDefinitionRepository.findBySlug("timesheet").orElseThrow();
              var params = new HashMap<String, Object>();
              params.put("dateFrom", "2025-04-01");
              params.put("dateTo", "2025-04-30");
              params.put("groupBy", "member");

              var result = reportExecutionService.executeForExport("timesheet", params);
              var outputStream = new ByteArrayOutputStream();
              try {
                reportRenderingService.writeCsv(definition, result, params, outputStream);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }

              String csv = outputStream.toString();
              String[] lines = csv.split("\n");

              // Metadata lines
              assertThat(lines[0]).startsWith("# Timesheet Report");
              assertThat(lines[1]).startsWith("# Generated:");
              assertThat(lines[2]).startsWith("# Parameters:");

              // Column header line (line index 3)
              assertThat(lines[3]).isNotEmpty();
            });
  }

  @Test
  void writeCsvContainsDataRows() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var definition = reportDefinitionRepository.findBySlug("timesheet").orElseThrow();
              var params = new HashMap<String, Object>();
              params.put("dateFrom", "2025-04-01");
              params.put("dateTo", "2025-04-30");
              params.put("groupBy", "member");

              var result = reportExecutionService.executeForExport("timesheet", params);
              var outputStream = new ByteArrayOutputStream();
              try {
                reportRenderingService.writeCsv(definition, result, params, outputStream);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }

              String csv = outputStream.toString();
              String[] lines = csv.split("\n");

              // 3 metadata + 1 header + at least 1 data row
              assertThat(lines.length).isGreaterThanOrEqualTo(5);
            });
  }

  @Test
  void writeCsvFormatsParametersInMetadataLine() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var definition = reportDefinitionRepository.findBySlug("timesheet").orElseThrow();
              var params = new HashMap<String, Object>();
              params.put("dateFrom", "2025-04-01");
              params.put("dateTo", "2025-04-30");

              var result = reportExecutionService.executeForExport("timesheet", params);
              var outputStream = new ByteArrayOutputStream();
              try {
                reportRenderingService.writeCsv(definition, result, params, outputStream);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }

              String csv = outputStream.toString();
              String[] lines = csv.split("\n");

              // Parameters line should contain key=value pairs
              assertThat(lines[2]).contains("dateFrom=2025-04-01");
              assertThat(lines[2]).contains("dateTo=2025-04-30");
            });
  }

  @Test
  void writeCsvWithEmptyResultProducesHeadersOnly() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var definition = reportDefinitionRepository.findBySlug("timesheet").orElseThrow();
              var params = new HashMap<String, Object>();
              params.put("dateFrom", "2099-01-01");
              params.put("dateTo", "2099-01-31");
              params.put("groupBy", "member");

              var result = reportExecutionService.executeForExport("timesheet", params);
              var outputStream = new ByteArrayOutputStream();
              try {
                reportRenderingService.writeCsv(definition, result, params, outputStream);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }

              String csv = outputStream.toString();
              String[] lines = csv.split("\n");

              // 3 metadata + 1 header = 4 lines, no data rows
              assertThat(lines.length).isEqualTo(4);
            });
  }

  @Test
  void writeCsvEscapesValuesWithCommasAndQuotes() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var definition = reportDefinitionRepository.findBySlug("timesheet").orElseThrow();

              // Get the actual column keys from the definition
              @SuppressWarnings("unchecked")
              var columns =
                  (List<Map<String, String>>) definition.getColumnDefinitions().get("columns");
              assertThat(columns).isNotEmpty();
              String firstKey = columns.getFirst().get("key");

              // Build a synthetic result with a value that needs CSV escaping
              var row = new HashMap<String, Object>();
              row.put(firstKey, "Doe, John \"Jr\"");

              var syntheticResult = new ReportResult(List.of(row), Map.of());
              var params = new HashMap<String, Object>();

              var outputStream = new ByteArrayOutputStream();
              try {
                reportRenderingService.writeCsv(definition, syntheticResult, params, outputStream);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }

              String csv = outputStream.toString();
              // Value with comma and quotes should be escaped per RFC 4180
              assertThat(csv).contains("\"Doe, John \"\"Jr\"\"\"");
            });
  }

  // --- Helpers ---

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
