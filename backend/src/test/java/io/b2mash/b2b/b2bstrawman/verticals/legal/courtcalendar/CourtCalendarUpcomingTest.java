package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import java.time.LocalDate;
import java.time.LocalTime;
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
class CourtCalendarUpcomingTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_upcoming_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CourtCalendarService courtCalendarService;
  @Autowired private CourtDateRepository courtDateRepository;
  @Autowired private PrescriptionTrackerRepository prescriptionTrackerRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Upcoming Test Org", null).schemaName();
    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_upcoming_owner", "upcoming@test.com", "Upcoming Owner", "owner"));

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
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer(
                              "Upcoming Test Corp", "upcoming@test.com", memberId));
                  customerId = customer.getId();

                  var project =
                      new Project("Upcoming Matter", "Test matter for upcoming", memberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();
                }));
  }

  @Test
  void getUpcoming_returnsCourtDatesWithin30Days() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Court date within 30 days
                  var nearDate =
                      new CourtDate(
                          projectId,
                          customerId,
                          "HEARING",
                          LocalDate.now().plusDays(10),
                          LocalTime.of(10, 0),
                          "Nearby Court",
                          null,
                          null,
                          "Within range",
                          7,
                          memberId);
                  courtDateRepository.saveAndFlush(nearDate);

                  // Court date beyond 30 days
                  var farDate =
                      new CourtDate(
                          projectId,
                          customerId,
                          "TRIAL",
                          LocalDate.now().plusDays(60),
                          null,
                          "Far Court",
                          null,
                          null,
                          "Out of range",
                          7,
                          memberId);
                  courtDateRepository.saveAndFlush(farDate);

                  var response = courtCalendarService.getUpcoming();

                  assertThat(response.courtDates()).isNotEmpty();
                  assertThat(response.courtDates())
                      .allSatisfy(cd -> assertThat(cd.daysUntil()).isLessThanOrEqualTo(30));
                }));
  }

  @Test
  void getUpcoming_returnsPrescriptionWarningsWithin90Days() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Prescription tracker expiring within 90 days
                  var nearTracker =
                      new PrescriptionTracker(
                          projectId,
                          customerId,
                          LocalDate.now().minusYears(3).plusDays(45),
                          "GENERAL_3Y",
                          null,
                          LocalDate.now().plusDays(45),
                          "Within range",
                          memberId);
                  prescriptionTrackerRepository.saveAndFlush(nearTracker);

                  // Prescription tracker expiring beyond 90 days
                  var farTracker =
                      new PrescriptionTracker(
                          projectId,
                          customerId,
                          LocalDate.now().minusYears(3).plusDays(120),
                          "GENERAL_3Y",
                          null,
                          LocalDate.now().plusDays(120),
                          "Out of range",
                          memberId);
                  prescriptionTrackerRepository.saveAndFlush(farTracker);

                  var response = courtCalendarService.getUpcoming();

                  assertThat(response.prescriptionWarnings()).isNotEmpty();
                  assertThat(response.prescriptionWarnings())
                      .allSatisfy(pw -> assertThat(pw.daysUntil()).isLessThanOrEqualTo(90));
                }));
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
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
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }
}
