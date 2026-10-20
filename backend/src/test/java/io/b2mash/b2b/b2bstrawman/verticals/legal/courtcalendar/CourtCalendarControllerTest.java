package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
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
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
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
class CourtCalendarControllerTest {
  private static final String ORG_ID = "org_court_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
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
  private UUID otherCustomerId;
  private UUID otherProjectId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Court Controller Test Org", null).schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_court_ctrl_owner",
                "court_ctrl@test.com",
                "Court Ctrl Owner",
                "owner"));
    TestMemberHelper.syncMember(
        mockMvc,
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

                      // Second client + matter so the list filters have a row to exclude.
                      var otherCustomer =
                          customerRepository.saveAndFlush(
                              createActiveCustomer(
                                  "Ctrl Filter Other Corp",
                                  "ctrl_filter_other@test.com",
                                  memberId));
                      otherCustomerId = otherCustomer.getId();

                      var otherProject =
                          new Project("Ctrl Filter Other Matter", "Filter test", memberId);
                      otherProject.setCustomerId(otherCustomerId);
                      otherProjectId = projectRepository.saveAndFlush(otherProject).getId();
                    }));
  }

  @Test
  void postCourtDate_returns201WithCreatedDate() throws Exception {
    mockMvc
        .perform(
            post("/api/court-dates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "matterId": "%s",
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
        .andExpect(jsonPath("$.matterId").value(projectId.toString()))
        .andExpect(jsonPath("$.clientId").value(customerId.toString()))
        .andExpect(jsonPath("$.matterName").value("Ctrl Test Matter"))
        .andExpect(jsonPath("$.clientName").value("Ctrl Test Corp"));
  }

  @Test
  void getCourtDates_returns200WithPaginatedResults() throws Exception {
    // Create a court date first
    mockMvc
        .perform(
            post("/api/court-dates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "matterId": "%s",
                      "dateType": "TRIAL",
                      "scheduledDate": "2026-06-01",
                      "courtName": "Cape Town High Court"
                    }
                    """
                        .formatted(projectId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/court-dates").with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner")))
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "matterId": "%s",
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
        .perform(
            get("/api/court-dates/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner")))
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "matterId": "%s",
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner"))
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
  void postPostpone_returnsNewScheduledEntry() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/court-dates")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "matterId": "%s",
                          "dateType": "HEARING",
                          "scheduledDate": "2026-09-01",
                          "courtName": "Postpone Test Court"
                        }
                        """
                            .formatted(projectId)))
            .andExpect(status().isCreated())
            .andReturn();

    String originalId =
        com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Postpone returns the new SCHEDULED entry
    var postponeResult =
        mockMvc
            .perform(
                post("/api/court-dates/" + originalId + "/postpone")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "newDate": "2026-10-15",
                          "reason": "Judge unavailable"
                        }
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.scheduledDate").value("2026-10-15"))
            .andExpect(jsonPath("$.description").value("Rescheduled from 2026-09-01"))
            .andReturn();

    // New entry should have a different ID
    String newId =
        com.jayway.jsonpath.JsonPath.read(
            postponeResult.getResponse().getContentAsString(), "$.id");
    assertThat(newId).isNotEqualTo(originalId);

    // Original entry should be POSTPONED with original date preserved
    mockMvc
        .perform(
            get("/api/court-dates/" + originalId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("POSTPONED"))
        .andExpect(jsonPath("$.scheduledDate").value("2026-09-01"))
        .andExpect(jsonPath("$.outcome").value("Postponed: Judge unavailable"));
  }

  @Test
  void postOutcome_returnsHeardStatus() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/court-dates")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "matterId": "%s",
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner"))
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "matterId": "%s",
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner"))
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
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_court_ctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "matterId": "%s",
                      "dateType": "HEARING",
                      "scheduledDate": "2026-11-01",
                      "courtName": "Forbidden Court"
                    }
                    """
                        .formatted(projectId)))
        .andExpect(status().isForbidden());
  }

  // --- List filters: ?matterId= / ?clientId= must bind (a stale param name is silently ignored
  // by Spring and returns the unfiltered list, so each assertion checks the other row is absent)
  // ---

  @Test
  void getCourtDates_filteredByMatterIdAndClientId_excludesOtherMattersRows() throws Exception {
    String ownId = createCourtDate(projectId, "Filter Own Court");
    String otherId = createCourtDate(otherProjectId, "Filter Other Court");

    List<String> byMatter = listIds("/api/court-dates", "matterId", otherProjectId);
    assertThat(byMatter).contains(otherId).doesNotContain(ownId);

    List<String> byClient = listIds("/api/court-dates", "clientId", customerId);
    assertThat(byClient).contains(ownId).doesNotContain(otherId);
  }

  @Test
  void getPrescriptionTrackers_filteredByMatterIdAndClientId_excludesOtherMattersRows()
      throws Exception {
    String ownId = createPrescriptionTracker(projectId);
    String otherId = createPrescriptionTracker(otherProjectId);

    List<String> byMatter = listIds("/api/prescription-trackers", "matterId", otherProjectId);
    assertThat(byMatter).contains(otherId).doesNotContain(ownId);

    List<String> byClient = listIds("/api/prescription-trackers", "clientId", customerId);
    assertThat(byClient).contains(ownId).doesNotContain(otherId);
  }

  private String createCourtDate(UUID matterId, String courtName) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/court-dates")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "matterId": "%s",
                          "dateType": "HEARING",
                          "scheduledDate": "2026-11-20",
                          "courtName": "%s"
                        }
                        """
                            .formatted(matterId, courtName)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String createPrescriptionTracker(UUID matterId) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/prescription-trackers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "matterId": "%s",
                          "causeOfActionDate": "2026-01-15",
                          "prescriptionType": "GENERAL_3Y"
                        }
                        """
                            .formatted(matterId)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private List<String> listIds(String path, String paramName, UUID paramValue) throws Exception {
    var result =
        mockMvc
            .perform(
                get(path)
                    .param(paramName, paramValue.toString())
                    .param("size", "200")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_court_ctrl_owner")))
            .andExpect(status().isOk())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.content[*].id");
  }
}
