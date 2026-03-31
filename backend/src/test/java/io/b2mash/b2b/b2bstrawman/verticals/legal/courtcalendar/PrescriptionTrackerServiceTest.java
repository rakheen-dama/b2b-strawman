package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService.CreatePrescriptionTrackerRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService.InterruptRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService.PrescriptionTrackerFilters;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService.UpdatePrescriptionTrackerRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrescriptionTrackerServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_presc_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private PrescriptionTrackerService prescriptionTrackerService;
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
        provisioningService
            .provisionTenant(ORG_ID, "Prescription Tracker Service Test Org", null)
            .schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_presc_svc_owner", "presc_svc@test.com", "Presc Svc Owner", "owner"));

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
                              "Prescription Test Corp", "presc@test.com", memberId));
                  customerId = customer.getId();

                  var project =
                      new Project("MVA Claim - Smith", "Motor vehicle accident", memberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();
                }));
  }

  @Test
  void create_calculatesDateAndSavesWithRunningStatus() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreatePrescriptionTrackerRequest(
                          projectId,
                          LocalDate.of(2023, 6, 15),
                          "GENERAL_3Y",
                          null,
                          "Motor vehicle accident claim");

                  var response = prescriptionTrackerService.create(request, memberId);

                  assertThat(response.status()).isEqualTo("RUNNING");
                  assertThat(response.prescriptionType()).isEqualTo("GENERAL_3Y");
                  assertThat(response.causeOfActionDate()).isEqualTo(LocalDate.of(2023, 6, 15));
                  assertThat(response.prescriptionDate()).isEqualTo(LocalDate.of(2026, 6, 15));
                  assertThat(response.customerId()).isEqualTo(customerId);
                  assertThat(response.projectName()).isEqualTo("MVA Claim - Smith");
                  assertThat(response.customerName()).isEqualTo("Prescription Test Corp");
                  assertThat(response.notes()).isEqualTo("Motor vehicle accident claim");
                  assertThat(response.id()).isNotNull();
                }));
  }

  @Test
  void update_recalculatesPrescriptionDate() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var createRequest =
                      new CreatePrescriptionTrackerRequest(
                          projectId,
                          LocalDate.of(2020, 1, 1),
                          "GENERAL_3Y",
                          null,
                          "Original claim");

                  var created = prescriptionTrackerService.create(createRequest, memberId);
                  assertThat(created.prescriptionDate()).isEqualTo(LocalDate.of(2023, 1, 1));

                  var updateRequest =
                      new UpdatePrescriptionTrackerRequest(
                          LocalDate.of(2020, 1, 1), "DEBT_6Y", null, "Changed to debt claim");

                  var updated = prescriptionTrackerService.update(created.id(), updateRequest);

                  assertThat(updated.prescriptionType()).isEqualTo("DEBT_6Y");
                  assertThat(updated.prescriptionDate()).isEqualTo(LocalDate.of(2026, 1, 1));
                  assertThat(updated.notes()).isEqualTo("Changed to debt claim");
                }));
  }

  @Test
  void interrupt_setsInterruptedStatus() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var createRequest =
                      new CreatePrescriptionTrackerRequest(
                          projectId,
                          LocalDate.of(2023, 3, 1),
                          "DELICT_3Y",
                          null,
                          "Delictual claim");

                  var created = prescriptionTrackerService.create(createRequest, memberId);

                  var interruptRequest =
                      new InterruptRequest(
                          LocalDate.of(2025, 1, 15), "Service of combined summons");

                  var interrupted =
                      prescriptionTrackerService.interrupt(created.id(), interruptRequest);

                  assertThat(interrupted.status()).isEqualTo("INTERRUPTED");
                  assertThat(interrupted.interruptionDate()).isEqualTo(LocalDate.of(2025, 1, 15));
                  assertThat(interrupted.interruptionReason())
                      .isEqualTo("Service of combined summons");
                }));
  }

  @Test
  void interrupt_failsOnTerminalState() {
    final UUID[] trackerId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var createRequest =
                      new CreatePrescriptionTrackerRequest(
                          projectId,
                          LocalDate.of(2023, 5, 1),
                          "CONTRACT_3Y",
                          null,
                          "Already interrupted");

                  var created = prescriptionTrackerService.create(createRequest, memberId);
                  prescriptionTrackerService.interrupt(
                      created.id(),
                      new InterruptRequest(LocalDate.of(2025, 6, 1), "First interruption"));
                  trackerId[0] = created.id();
                }));

    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        prescriptionTrackerService.interrupt(
                            trackerId[0],
                            new InterruptRequest(
                                LocalDate.of(2025, 7, 1), "Second interruption attempt")))
                .isInstanceOf(InvalidStateException.class));
  }

  @Test
  void list_filtersByStatus() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create a RUNNING tracker
                  prescriptionTrackerService.create(
                      new CreatePrescriptionTrackerRequest(
                          projectId,
                          LocalDate.of(2024, 1, 1),
                          "GENERAL_3Y",
                          null,
                          "Running tracker"),
                      memberId);

                  var filters = new PrescriptionTrackerFilters("RUNNING", null, null);
                  var page = prescriptionTrackerService.list(filters, Pageable.ofSize(20));

                  assertThat(page.getContent()).isNotEmpty();
                  assertThat(page.getContent())
                      .allSatisfy(t -> assertThat(t.status()).isEqualTo("RUNNING"));
                }));
  }

  @Test
  void create_customType_calculatesWithCustomYears() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreatePrescriptionTrackerRequest(
                          projectId,
                          LocalDate.of(2022, 8, 20),
                          "CUSTOM",
                          10,
                          "Custom prescription period");

                  var response = prescriptionTrackerService.create(request, memberId);

                  assertThat(response.prescriptionType()).isEqualTo("CUSTOM");
                  assertThat(response.customYears()).isEqualTo(10);
                  assertThat(response.prescriptionDate()).isEqualTo(LocalDate.of(2032, 8, 20));
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
