package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrescriptionTrackerServiceTest {
  private static final String ORG_ID = "org_presc_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
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
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_presc_svc_owner",
                "presc_svc@test.com",
                "Presc Svc Owner",
                "owner"));

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
    // Use a cause-of-action date that puts prescription date > 90 days in the future
    var causeDate = LocalDate.now().minusYears(2);
    var expectedPrescriptionDate = causeDate.plusYears(3);

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreatePrescriptionTrackerRequest(
                          projectId, causeDate, "GENERAL_3Y", null, "Motor vehicle accident claim");

                  var response = prescriptionTrackerService.create(request, memberId);

                  assertThat(response.status()).isEqualTo("RUNNING");
                  assertThat(response.prescriptionType()).isEqualTo("GENERAL_3Y");
                  assertThat(response.causeOfActionDate()).isEqualTo(causeDate);
                  assertThat(response.prescriptionDate()).isEqualTo(expectedPrescriptionDate);
                  assertThat(response.customerId()).isEqualTo(customerId);
                  assertThat(response.projectName()).isEqualTo("MVA Claim - Smith");
                  assertThat(response.customerName()).isEqualTo("Prescription Test Corp");
                  assertThat(response.notes()).isEqualTo("Motor vehicle accident claim");
                  assertThat(response.id()).isNotNull();
                }));
  }

  @Test
  void update_recalculatesPrescriptionDate() {
    // Use dates that keep prescription > 90 days in the future
    var causeDate = LocalDate.now().minusYears(2);
    var expectedInitialDate = causeDate.plusYears(3);
    var expectedUpdatedDate = causeDate.plusYears(6);

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var createRequest =
                      new CreatePrescriptionTrackerRequest(
                          projectId, causeDate, "GENERAL_3Y", null, "Original claim");

                  var created = prescriptionTrackerService.create(createRequest, memberId);
                  assertThat(created.prescriptionDate()).isEqualTo(expectedInitialDate);

                  var updateRequest =
                      new UpdatePrescriptionTrackerRequest(
                          causeDate, "DEBT_6Y", null, "Changed to debt claim");

                  var updated = prescriptionTrackerService.update(created.id(), updateRequest);

                  assertThat(updated.prescriptionType()).isEqualTo("DEBT_6Y");
                  assertThat(updated.prescriptionDate()).isEqualTo(expectedUpdatedDate);
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

  @Test
  void getById_returnsExpiredStatus_whenPrescriptionDateIsInThePast() {
    // Cause of action date far enough in past that prescription date is also past
    var causeDate = LocalDate.now().minusYears(4);
    final UUID[] trackerId = new UUID[1];

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreatePrescriptionTrackerRequest(
                          projectId, causeDate, "GENERAL_3Y", null, "Past prescription");
                  var created = prescriptionTrackerService.create(request, memberId);
                  trackerId[0] = created.id();
                  // DB status is RUNNING, but prescription date is in the past
                  assertThat(created.status()).isEqualTo("EXPIRED");
                }));

    runInTenant(
        () -> {
          var fetched = prescriptionTrackerService.getById(trackerId[0]);
          assertThat(fetched.status()).isEqualTo("EXPIRED");
        });
  }

  @Test
  void getById_returnsWarnedStatus_whenPrescriptionDateIsWithin90Days() {
    // Cause of action date so prescription falls within 90 days from today
    var prescriptionTarget = LocalDate.now().plusDays(45);
    var causeDate = prescriptionTarget.minusYears(3);
    final UUID[] trackerId = new UUID[1];

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreatePrescriptionTrackerRequest(
                          projectId, causeDate, "GENERAL_3Y", null, "Approaching prescription");
                  var created = prescriptionTrackerService.create(request, memberId);
                  trackerId[0] = created.id();
                  assertThat(created.status()).isEqualTo("WARNED");
                }));

    runInTenant(
        () -> {
          var fetched = prescriptionTrackerService.getById(trackerId[0]);
          assertThat(fetched.status()).isEqualTo("WARNED");
        });
  }

  @Test
  void getById_preservesInterruptedStatus_evenWhenPrescriptionDateIsPast() {
    var causeDate = LocalDate.now().minusYears(4);
    final UUID[] trackerId = new UUID[1];

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreatePrescriptionTrackerRequest(
                          projectId, causeDate, "GENERAL_3Y", null, "Interrupted tracker");
                  var created = prescriptionTrackerService.create(request, memberId);
                  prescriptionTrackerService.interrupt(
                      created.id(),
                      new InterruptRequest(LocalDate.now().minusMonths(6), "Service of summons"));
                  trackerId[0] = created.id();
                }));

    runInTenant(
        () -> {
          var fetched = prescriptionTrackerService.getById(trackerId[0]);
          // INTERRUPTED is terminal — should NOT be overridden to EXPIRED
          assertThat(fetched.status()).isEqualTo("INTERRUPTED");
        });
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
