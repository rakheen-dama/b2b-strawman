package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourtDateReminderJobTest {
  private static final String ORG_ID = "org_reminder_job_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CourtDateRepository courtDateRepository;
  @Autowired private PrescriptionTrackerRepository prescriptionTrackerRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CourtDateReminderJob reminderJob;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Reminder Job Test Org", null).schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_reminder_job_owner",
                "reminder_job@test.com",
                "Reminder Job Owner",
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
                              "Reminder Test Corp", "reminder@test.com", memberId));
                  customerId = customer.getId();

                  var project =
                      new Project("Reminder Matter", "Test matter for reminders", memberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();
                }));
  }

  @Test
  void execute_createsNotificationForUpcomingCourtDate() {
    // Create a court date within reminder window (3 days from now, 7 day reminder)
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var courtDate =
                      new CourtDate(
                          projectId,
                          customerId,
                          "HEARING",
                          LocalDate.now().plusDays(3),
                          LocalTime.of(10, 0),
                          "Test Court",
                          null,
                          null,
                          "Upcoming hearing",
                          7,
                          memberId);
                  courtDateRepository.saveAndFlush(courtDate);
                }));

    // Execute the reminder job
    reminderJob.execute();

    // Verify notification was created
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var notifications =
                      notificationRepository.findByRecipientMemberId(
                          memberId, org.springframework.data.domain.Pageable.ofSize(50));
                  assertThat(notifications.getContent())
                      .anyMatch(n -> "COURT_DATE_REMINDER".equals(n.getType()));
                }));
  }

  @Test
  void execute_skipsDuplicateNotification() {
    // Create a court date within reminder window
    final UUID[] courtDateId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var courtDate =
                      new CourtDate(
                          projectId,
                          customerId,
                          "TRIAL",
                          LocalDate.now().plusDays(5),
                          null,
                          "Dedup Test Court",
                          null,
                          null,
                          "Dedup test",
                          7,
                          memberId);
                  courtDate = courtDateRepository.saveAndFlush(courtDate);
                  courtDateId[0] = courtDate.getId();
                }));

    // Execute twice
    reminderJob.execute();
    reminderJob.execute();

    // Count notifications for this specific court date
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var notifications =
                      notificationRepository.findByRecipientMemberId(
                          memberId, org.springframework.data.domain.Pageable.ofSize(100));
                  long courtDateNotifications =
                      notifications.getContent().stream()
                          .filter(
                              n ->
                                  "COURT_DATE_REMINDER".equals(n.getType())
                                      && courtDateId[0].equals(n.getReferenceEntityId()))
                          .count();
                  // Should be exactly 1 due to idempotency
                  assertThat(courtDateNotifications).isEqualTo(1);
                }));
  }

  @Test
  void execute_updatesPrescriptionTrackerStatusToWarned() {
    // Create a prescription tracker expiring within 90 days
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var tracker =
                      new PrescriptionTracker(
                          projectId,
                          customerId,
                          LocalDate.now().minusYears(3).plusDays(60),
                          "GENERAL_3Y",
                          null,
                          LocalDate.now().plusDays(60),
                          "Test prescription",
                          memberId);
                  prescriptionTrackerRepository.saveAndFlush(tracker);
                }));

    // Execute the reminder job
    reminderJob.execute();

    // Verify tracker was updated to WARNED
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var trackers =
                      prescriptionTrackerRepository.findByProjectIdOrderByPrescriptionDateAsc(
                          projectId);
                  var warned =
                      trackers.stream().filter(t -> "WARNED".equals(t.getStatus())).toList();
                  assertThat(warned).isNotEmpty();
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
}
