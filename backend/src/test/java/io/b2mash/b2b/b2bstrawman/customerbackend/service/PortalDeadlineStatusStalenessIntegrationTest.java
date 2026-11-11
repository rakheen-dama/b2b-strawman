package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalDeadlineResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalDeadlineViewRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.CreateCourtDateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService.CreatePrescriptionTrackerRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G89-F71: the portal deadline {@code status} (UPCOMING / DUE_SOON / OVERDUE) used to be derived
 * once, at sync time, and nothing re-derived it as the calendar moved. It is now derived at read
 * time ({@code PortalDeadlineStatus}). A court date (and a prescription) synced while more than 7
 * days out should read DUE_SOON once inside the 7-day window and OVERDUE once past — on the
 * client-facing badge and through the {@code status} filter.
 *
 * <p>Day D is the Clock bean's "today" (SA time). Both deadlines are created at D+10 through the
 * real firm services, so the real AFTER_COMMIT listeners project the portal rows. "Advancing the
 * clock" is done by reading through a {@link PortalDeadlineService} built by hand with a fixed
 * clock at D+5 / D+11 (the {@code PortalDeadlineBackfillRunnerTest} pattern — no {@code MockitoBean
 * Clock}, no extra Spring context cache key). The source deadlines are never touched after
 * creation.
 *
 * <p>The assertions state the correct behaviour. They failed 8/8 before the fix (the red test).
 * SoftAssertions so every stale read is reported in one run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalDeadlineStatusStalenessIntegrationTest {

  private static final String ORG_ID = "org_portal_deadline_staleness_f71";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CourtCalendarService courtCalendarService;
  @Autowired private PrescriptionTrackerService prescriptionTrackerService;
  @Autowired private PortalDeadlineViewRepository deadlineRepo;
  @Autowired private VerticalModuleGuard moduleGuard;
  @Autowired private Clock clock;

  private String tenantSchema;
  private UUID memberId;
  private UUID ownerId;
  private UUID matterId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Deadline Staleness Firm", "legal-za")
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_deadline_staleness_f71",
                "deadline_staleness_f71@test.com",
                "Deadline Staleness Owner",
                "owner"));
    var tx = new TransactionTemplate(transactionManager);
    inTenant(
        () ->
            tx.execute(
                s -> {
                  ownerId =
                      customerRepository
                          .saveAndFlush(
                              createActiveCustomer(
                                  "Lerato Mokoena", "lerato-f71@test.com", memberId))
                          .getId();
                  var project = new Project("Mokoena v. RAF", "Personal injury claim", memberId);
                  project.setCustomerId(ownerId);
                  matterId = projectRepository.saveAndFlush(project).getId();
                  customerProjectRepository.save(new CustomerProject(ownerId, matterId, memberId));
                  return null;
                }));
  }

  @Test
  void statusFollowsTheCalendarWithoutAnySourceChange() {
    LocalDate d = LocalDate.now(clock);
    LocalDate due = d.plusDays(10);

    // ── Day D: schedule a court date and a prescription, both due D+10, via the real services.
    UUID courtId =
        inTenant(
            () ->
                courtCalendarService
                    .createCourtDate(
                        new CreateCourtDateRequest(
                            matterId,
                            "HEARING",
                            due,
                            LocalTime.of(10, 0),
                            "Johannesburg High Court",
                            "2026/F71",
                            null,
                            null,
                            7),
                        memberId)
                    .id());
    UUID trackerId =
        inTenant(
            () ->
                prescriptionTrackerService
                    .create(
                        new CreatePrescriptionTrackerRequest(
                            matterId, due.minusYears(3), "GENERAL_3Y", null, null),
                        memberId)
                    .id());

    // Preconditions (hard): at D both rows exist, are due D+10 and read UPCOMING.
    List<PortalDeadlineResponse> atD = list(d, null, null, null);
    PortalDeadlineResponse courtAtD = find(atD, "COURT_DATE", courtId);
    PortalDeadlineResponse trackerAtD = find(atD, "PRESCRIPTION_TRACKER", trackerId);
    assertThat(courtAtD).as("court row at D").isNotNull();
    assertThat(trackerAtD).as("prescription row at D").isNotNull();
    assertThat(courtAtD.dueDate()).isEqualTo(due);
    // The prescription's due date is what the GENERAL_3Y rule computes from the cause-of-action
    // date. It equals `due` except when `due` is 29 Feb: minusYears(3) lands on 28 Feb and the
    // rule gives 28 Feb (D+9), which still reads UPCOMING at D, DUE_SOON at D+5, OVERDUE at D+11.
    assertThat(trackerAtD.dueDate()).isEqualTo(due.minusYears(3).plusYears(3));
    assertThat(courtAtD.status()).as("court status at D (10 days out)").isEqualTo("UPCOMING");
    assertThat(trackerAtD.status()).as("prescription status at D").isEqualTo("UPCOMING");

    SoftAssertions soft = new SoftAssertions();

    // ── Day D+5: 5 days left — must read DUE_SOON, and the DUE_SOON filter must return it.
    LocalDate d5 = d.plusDays(5);
    List<PortalDeadlineResponse> atD5 = list(d5, null, null, null);
    soft.assertThat(statusOf(atD5, "COURT_DATE", courtId))
        .as("[A1] court status at D+5 (5 days left)")
        .isEqualTo("DUE_SOON");
    soft.assertThat(statusOf(atD5, "PRESCRIPTION_TRACKER", trackerId))
        .as("[A2] prescription status at D+5 (5 days left)")
        .isEqualTo("DUE_SOON");
    soft.assertThat(ids(list(d5, null, null, "DUE_SOON")))
        .as("[A3] status=DUE_SOON filter at D+5")
        .contains(courtId, trackerId);
    soft.assertThat(ids(list(d5, null, null, "UPCOMING")))
        .as("[A4] status=UPCOMING filter at D+5 must NOT contain the 5-days-left rows")
        .doesNotContain(courtId, trackerId);

    // ── Day D+11: one day past due — must read OVERDUE. The default window starts at today,
    // so pass from=D explicitly to see the past-due rows at all.
    LocalDate d11 = d.plusDays(11);
    List<PortalDeadlineResponse> atD11 = list(d11, d, null, null);
    soft.assertThat(statusOf(atD11, "COURT_DATE", courtId))
        .as("[A5] court status at D+11 (1 day past due)")
        .isEqualTo("OVERDUE");
    soft.assertThat(statusOf(atD11, "PRESCRIPTION_TRACKER", trackerId))
        .as("[A6] prescription status at D+11 (1 day past)")
        .isEqualTo("OVERDUE");
    soft.assertThat(ids(list(d11, d, null, "OVERDUE")))
        .as("[A7] status=OVERDUE filter at D+11 (from=D)")
        .contains(courtId, trackerId);
    soft.assertThat(inCustomer(() -> serviceAt(d11).getForContact("COURT_DATE", courtId)).status())
        .as("[A8] getForContact (detail panel badge) court status at D+11")
        .isEqualTo("OVERDUE");

    soft.assertAll();
  }

  // ── helpers ──

  /** A {@link PortalDeadlineService} whose "today" is {@code day} (midday SA time). */
  private PortalDeadlineService serviceAt(LocalDate day) {
    Clock fixed =
        Clock.fixed(day.atTime(LocalTime.NOON).atZone(KaziTime.ZONE).toInstant(), KaziTime.ZONE);
    return new PortalDeadlineService(deadlineRepo, moduleGuard, fixed);
  }

  private List<PortalDeadlineResponse> list(
      LocalDate today, LocalDate from, LocalDate to, String status) {
    return inCustomer(() -> serviceAt(today).listForContact(from, to, status));
  }

  private static PortalDeadlineResponse find(
      List<PortalDeadlineResponse> rows, String sourceEntity, UUID id) {
    return rows.stream()
        .filter(r -> r.sourceEntity().equals(sourceEntity) && r.id().equals(id))
        .findFirst()
        .orElse(null);
  }

  private static String statusOf(List<PortalDeadlineResponse> rows, String sourceEntity, UUID id) {
    PortalDeadlineResponse row = find(rows, sourceEntity, id);
    return row == null ? "<row missing>" : row.status();
  }

  private static List<UUID> ids(List<PortalDeadlineResponse> rows) {
    return rows.stream().map(PortalDeadlineResponse::id).toList();
  }

  /** Portal-contact scope: tenant + org for the module gate, customer for the row scoping. */
  private <T> T inCustomer(Callable<T> action) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .where(RequestScopes.ORG_ID, ORG_ID)
          .where(RequestScopes.CUSTOMER_ID, ownerId)
          .call(action::call);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private <T> T inTenant(Callable<T> action) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .where(RequestScopes.ORG_ID, ORG_ID)
          .where(RequestScopes.MEMBER_ID, memberId)
          .where(RequestScopes.ORG_ROLE, "owner")
          .call(action::call);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
