package io.b2mash.b2b.b2bstrawman.reporting;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportingControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_rc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;

  @Autowired
  private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Reporting Controller Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    var ownerMemberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_rc_owner", "rc_owner@test.com", "RC Owner", "owner"));

    var tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create test data for execute endpoint tests
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var project = new Project("RC Test Project", "Test project", ownerMemberId);
                      project = projectRepository.save(project);

                      var task =
                          new Task(
                              project.getId(),
                              "RC Task",
                              null,
                              "MEDIUM",
                              "TASK",
                              null,
                              ownerMemberId);
                      task = taskRepository.save(task);

                      timeEntryRepository.save(
                          new TimeEntry(
                              task.getId(),
                              ownerMemberId,
                              LocalDate.of(2025, 6, 1),
                              120,
                              true,
                              null,
                              "RC test work"));
                      timeEntryRepository.save(
                          new TimeEntry(
                              task.getId(),
                              ownerMemberId,
                              LocalDate.of(2025, 6, 2),
                              60,
                              true,
                              null,
                              "RC test work 2"));
                    }));
  }

  @Test
  void listReportDefinitionsReturnsThreeCategories() throws Exception {
    mockMvc
        .perform(get("/api/report-definitions").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories").isArray())
        .andExpect(jsonPath("$.categories.length()").value(3));
  }

  @Test
  void listReportDefinitionsHasCorrectCategoryLabels() throws Exception {
    mockMvc
        .perform(get("/api/report-definitions").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[?(@.category == 'FINANCIAL')].label").value("Financial"))
        .andExpect(
            jsonPath("$.categories[?(@.category == 'TIME_ATTENDANCE')].label")
                .value("Time & Attendance"))
        .andExpect(jsonPath("$.categories[?(@.category == 'PROJECT')].label").value("Project"));
  }

  @Test
  void getReportDefinitionBySlugReturnsDetail() throws Exception {
    mockMvc
        .perform(get("/api/report-definitions/timesheet").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value("timesheet"))
        .andExpect(jsonPath("$.name").value("Timesheet Report"))
        .andExpect(jsonPath("$.category").value("TIME_ATTENDANCE"))
        .andExpect(jsonPath("$.parameterSchema").isMap())
        .andExpect(jsonPath("$.columnDefinitions").isMap())
        .andExpect(jsonPath("$.isSystem").value(true))
        .andExpect(jsonPath("$.templateBody").doesNotExist());
  }

  @Test
  void getReportDefinitionUnknownSlugReturns404() throws Exception {
    mockMvc
        .perform(get("/api/report-definitions/nonexistent").with(memberJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void listReportDefinitionsRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/report-definitions")).andExpect(status().isUnauthorized());
  }

  // --- Execute endpoint tests ---

  @Test
  void executeReportWithValidSlugReturns200() throws Exception {
    mockMvc
        .perform(
            post("/api/report-definitions/timesheet/execute")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "parameters": {
                        "dateFrom": "2025-06-01",
                        "dateTo": "2025-06-30",
                        "groupBy": "member"
                      },
                      "page": 0,
                      "size": 50
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reportName").value("Timesheet Report"))
        .andExpect(jsonPath("$.rows").isArray())
        .andExpect(jsonPath("$.summary").isMap())
        .andExpect(jsonPath("$.columns").isArray())
        .andExpect(jsonPath("$.pagination.page").value(0))
        .andExpect(jsonPath("$.pagination.size").value(50));
  }

  @Test
  void executeReportWithUnknownSlugReturns404() throws Exception {
    mockMvc
        .perform(
            post("/api/report-definitions/nonexistent-report/execute")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "parameters": {
                        "dateFrom": "2025-01-01",
                        "dateTo": "2025-01-31"
                      },
                      "page": 0,
                      "size": 50
                    }
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void executeReportWithoutAuthenticationReturns401() throws Exception {
    mockMvc
        .perform(
            post("/api/report-definitions/timesheet/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "parameters": {
                        "dateFrom": "2025-01-01",
                        "dateTo": "2025-01-31"
                      },
                      "page": 0,
                      "size": 50
                    }
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void executeReportWithPaginationReturnsCorrectPage() throws Exception {
    mockMvc
        .perform(
            post("/api/report-definitions/timesheet/execute")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "parameters": {
                        "dateFrom": "2025-06-01",
                        "dateTo": "2025-06-30",
                        "groupBy": "date"
                      },
                      "page": 0,
                      "size": 1
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pagination.page").value(0))
        .andExpect(jsonPath("$.pagination.size").value(1))
        .andExpect(jsonPath("$.pagination.totalElements").value(2))
        .andExpect(jsonPath("$.pagination.totalPages").value(2))
        .andExpect(jsonPath("$.rows.length()").value(1));
  }

  // --- Helpers ---

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_rc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
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
