package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourtCalendarControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_court_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Court Controller Test Org", null).schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_court_ctrl_owner",
                "court_ctrl@test.com",
                "Court Ctrl Owner",
                "owner"));
    syncMember(
        ORG_ID,
        "user_court_ctrl_member",
        "court_ctrl_member@test.com",
        "Court Ctrl Member",
        "member");

    // Enable the court_calendar module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("court_calendar"));
                      orgSettingsRepository.save(settings);
                    }));

    // Create test customer and project
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          customerRepository.saveAndFlush(
                              createActiveCustomer(
                                  "Ctrl Test Corp", "ctrl_test@test.com", memberId));
                      customerId = customer.getId();

                      var project = new Project("Ctrl Test Matter", "Controller test", memberId);
                      project.setCustomerId(customerId);
                      project = projectRepository.saveAndFlush(project);
                      projectId = project.getId();
                    }));
  }

  @Test
  void postCourtDate_returns201WithCreatedDate() throws Exception {
    mockMvc
        .perform(
            post("/api/court-dates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "projectId": "%s",
                      "dateType": "HEARING",
                      "scheduledDate": "2026-05-15",
                      "scheduledTime": "10:00",
                      "courtName": "Johannesburg High Court",
                      "courtReference": "2026/12345",
                      "judgeMagistrate": "Judge Mogoeng",
                      "description": "Application for summary judgment",
                      "reminderDays": 7
                    }
                    """
                        .formatted(projectId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.dateType").value("HEARING"))
        .andExpect(jsonPath("$.courtName").value("Johannesburg High Court"))
        .andExpect(jsonPath("$.projectId").value(projectId.toString()))
        .andExpect(jsonPath("$.customerId").value(customerId.toString()))
        .andExpect(jsonPath("$.projectName").value("Ctrl Test Matter"))
        .andExpect(jsonPath("$.customerName").value("Ctrl Test Corp"));
  }

  @Test
  void getCourtDates_returns200WithPaginatedResults() throws Exception {
    // Create a court date first
    mockMvc
        .perform(
            post("/api/court-dates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "projectId": "%s",
                      "dateType": "TRIAL",
                      "scheduledDate": "2026-06-01",
                      "courtName": "Cape Town High Court"
                    }
                    """
                        .formatted(projectId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/court-dates").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page.totalElements").isNumber());
  }

  @Test
  void getCourtDateById_returns200WithDetail() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/court-dates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "projectId": "%s",
                          "dateType": "MOTION",
                          "scheduledDate": "2026-07-10",
                          "courtName": "Pretoria High Court",
                          "courtReference": "2026/99999"
                        }
                        """
                            .formatted(projectId)))
            .andExpect(status().isCreated())
            .andReturn();

    String id =
        com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(get("/api/court-dates/" + id).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.courtReference").value("2026/99999"));
  }

  @Test
  void putCourtDate_updatesFields() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/court-dates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "projectId": "%s",
                          "dateType": "HEARING",
                          "scheduledDate": "2026-08-01",
                          "courtName": "Original Court"
                        }
                        """
                            .formatted(projectId)))
            .andExpect(status().isCreated())
            .andReturn();

    String id =
        com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            put("/api/court-dates/" + id)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "dateType": "TRIAL",
                      "scheduledDate": "2026-08-15",
                      "courtName": "Updated Court",
                      "judgeMagistrate": "Judge New"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dateType").value("TRIAL"))
        .andExpect(jsonPath("$.courtName").value("Updated Court"))
        .andExpect(jsonPath("$.judgeMagistrate").value("Judge New"));
  }

  @Test
  void postPostpone_returnsPostponedStatus() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/court-dates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "projectId": "%s",
                          "dateType": "HEARING",
                          "scheduledDate": "2026-09-01",
                          "courtName": "Postpone Test Court"
                        }
                        """
                            .formatted(projectId)))
            .andExpect(status().isCreated())
            .andReturn();

    String id =
        com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/court-dates/" + id + "/postpone")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "newDate": "2026-10-15",
                      "reason": "Judge unavailable"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("POSTPONED"))
        .andExpect(jsonPath("$.scheduledDate").value("2026-10-15"));
  }

  @Test
  void postOutcome_returnsHeardStatus() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/court-dates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "projectId": "%s",
                          "dateType": "TRIAL",
                          "scheduledDate": "2026-10-01",
                          "courtName": "Outcome Test Court"
                        }
                        """
                            .formatted(projectId)))
            .andExpect(status().isCreated())
            .andReturn();

    String id =
        com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/court-dates/" + id + "/outcome")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "outcome": "Judgment in favor of plaintiff"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("HEARD"))
        .andExpect(jsonPath("$.outcome").value("Judgment in favor of plaintiff"));
  }

  @Test
  void putCourtDate_heardStatus_returns400() throws Exception {
    // Create a court date
    var createResult =
        mockMvc
            .perform(
                post("/api/court-dates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "projectId": "%s",
                          "dateType": "HEARING",
                          "scheduledDate": "2026-12-01",
                          "courtName": "Terminal State Court"
                        }
                        """
                            .formatted(projectId)))
            .andExpect(status().isCreated())
            .andReturn();

    String id =
        com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Record outcome to transition to HEARD
    mockMvc
        .perform(
            post("/api/court-dates/" + id + "/outcome")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "outcome": "Judgment granted"
                    }
                    """))
        .andExpect(status().isOk());

    // Attempt update on HEARD court date — should return 400
    mockMvc
        .perform(
            put("/api/court-dates/" + id)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "dateType": "TRIAL",
                      "scheduledDate": "2026-12-15",
                      "courtName": "Should Not Work"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postCourtDate_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/court-dates")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "projectId": "%s",
                      "dateType": "HEARING",
                      "scheduledDate": "2026-11-01",
                      "courtName": "Forbidden Court"
                    }
                    """
                        .formatted(projectId)))
        .andExpect(status().isForbidden());
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
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_court_ctrl_owner")
                    .claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_court_ctrl_member")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }
}
