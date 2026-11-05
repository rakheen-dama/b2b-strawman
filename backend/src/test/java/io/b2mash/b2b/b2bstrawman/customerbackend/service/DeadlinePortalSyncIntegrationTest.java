package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDeadlineView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalDeadlineViewRepository;
import io.b2mash.b2b.b2bstrawman.event.CourtDateCancelledEvent;
import io.b2mash.b2b.b2bstrawman.event.CourtDateScheduledEvent;
import io.b2mash.b2b.b2bstrawman.event.DomainEvent;
import io.b2mash.b2b.b2bstrawman.event.PrescriptionDeadlineClearedEvent;
import io.b2mash.b2b.b2bstrawman.event.PrescriptionDeadlineSetEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.CancelRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.CreateCourtDateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.OutcomeRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.PostponeRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.UpdateCourtDateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService.CreatePrescriptionTrackerRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService.InterruptRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService.UpdatePrescriptionTrackerRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * §5b row 19 (Phase 89 633B, ADR-363): court dates and prescription trackers created through the
 * firm services must reach {@code portal.portal_deadline_view} for the matter's owning client only
 * (co-clients see nothing — decision row 5), with the sanitised copy only: firm-only strings
 * (judge, description, outcome, cancel / postpone reason, notes, interruption reason) never reach
 * the row. Cancel / postpone-original / interrupt delete the row (B4). Every scenario drives the
 * real services; their own transactions commit, so the AFTER_COMMIT listeners run before the call
 * returns. Assertions are scoped to the ids each test created.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RecordApplicationEvents
class DeadlinePortalSyncIntegrationTest {

  private static final String ORG_ID = "org_deadline_portal_sync_633b";
  private static final String COURT = "Johannesburg High Court";
  private static final String JUDGE = "Judge Mokgoro";
  private static final String DESCRIPTION = "Privileged: client admitted liability";
  private static final String NOTES = "Cause: slip at Sandton City";
  private static final String OUTCOME = "Judgment for plaintiff, costs reserved";
  private static final String CANCEL_REASON = "Client settled privately";
  private static final String POSTPONE_REASON = "Court roll full, judge recused";
  private static final String INTERRUPTION_REASON = "Summons served 2026-10-01";
  private static final String MEMBER_NAME = "Deadline Sync Owner";
  private static final List<String> FIRM_ONLY =
      List.of(
          JUDGE, DESCRIPTION, NOTES, OUTCOME, CANCEL_REASON, POSTPONE_REASON, INTERRUPTION_REASON);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CourtCalendarService courtCalendarService;
  @Autowired private PrescriptionTrackerService prescriptionTrackerService;
  @Autowired private PortalDeadlineViewRepository deadlineRepo;
  @Autowired private ApplicationEvents events;

  private String tenantSchema;
  private UUID memberId;
  private UUID ownerId;
  private UUID coClientId;
  private UUID matterId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Deadline Portal Sync Firm", "legal-za")
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_deadline_sync_633b",
                "deadline_sync_633b@test.com",
                MEMBER_NAME,
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
                                  "Sipho Dlamini", "sipho-633b@test.com", memberId))
                          .getId();
                  coClientId =
                      customerRepository
                          .saveAndFlush(
                              createActiveCustomer(
                                  "Thandi Dlamini", "thandi-633b@test.com", memberId))
                          .getId();
                  var project =
                      new Project("Sipho Dlamini v. Standard Bank", "Delictual claim", memberId);
                  project.setCustomerId(ownerId);
                  matterId = projectRepository.saveAndFlush(project).getId();
                  customerProjectRepository.save(new CustomerProject(ownerId, matterId, memberId));
                  customerProjectRepository.save(
                      new CustomerProject(coClientId, matterId, memberId));
                  return null;
                }));
  }

  @Test
  void createCourtDate_projectsAPortalRowForTheOwningClient() {
    UUID id = createCourtDate("HEARING", today().plusDays(20), COURT, "2026/1234");

    PortalDeadlineView row = courtRow(ownerId, id);
    assertThat(row.deadlineType()).isEqualTo("COURT_DATE");
    assertThat(row.matterId()).isEqualTo(matterId);
    assertThat(row.label()).isEqualTo("Hearing — Johannesburg High Court");
    assertThat(row.descriptionSanitised()).isEqualTo("2026/1234");
    assertThat(row.dueDate()).isEqualTo(today().plusDays(20));
    assertThat(row.status()).isEqualTo("UPCOMING");
    assertNoLeak(row);
  }

  @Test
  void coClientSeesNothing() {
    UUID courtId = createCourtDate("HEARING", today().plusDays(21), COURT, "2026/1234");
    UUID trackerId = createTracker("GENERAL_3Y", today().minusYears(1));

    assertThat(deadlineRepo.findByCustomerIdAndSourceEntityAndId(coClientId, "COURT_DATE", courtId))
        .isEmpty();
    assertThat(
            deadlineRepo.findByCustomerIdAndSourceEntityAndId(
                coClientId, "PRESCRIPTION_TRACKER", trackerId))
        .isEmpty();
    assertThat(deadlineRepo.findByCustomer(coClientId, null, null, null))
        .extracting(PortalDeadlineView::id)
        .doesNotContain(courtId, trackerId);
    assertThat(deadlineRepo.findByCustomer(ownerId, null, null, null))
        .extracting(PortalDeadlineView::id)
        .contains(courtId, trackerId);
  }

  @Test
  void updateDateMovesTheRow() {
    UUID id = createCourtDate("HEARING", today().plusDays(20), COURT, "2026/1234");

    inTenant(
        () ->
            courtCalendarService.updateCourtDate(
                id,
                new UpdateCourtDateRequest(
                    "TRIAL",
                    today().plusDays(30),
                    LocalTime.of(10, 0),
                    "Pretoria High Court",
                    "2026/1234",
                    JUDGE,
                    DESCRIPTION,
                    7)));

    PortalDeadlineView row = courtRow(ownerId, id);
    assertThat(row.dueDate()).isEqualTo(today().plusDays(30));
    assertThat(row.label()).isEqualTo("Trial — Pretoria High Court");
    assertNoLeak(row);
  }

  @Test
  void updateOfFirmOnlyFieldsPublishesNothing() {
    UUID id = createCourtDate("HEARING", today().plusDays(22), COURT, "2026/1234");
    PortalDeadlineView before = courtRow(ownerId, id);

    inTenant(
        () ->
            courtCalendarService.updateCourtDate(
                id,
                new UpdateCourtDateRequest(
                    "HEARING",
                    today().plusDays(22),
                    LocalTime.of(14, 30),
                    COURT,
                    "2026/1234",
                    "Judge Someone Else",
                    "Privileged: new strategy note",
                    3)));

    assertThat(scheduledEventsFor(id)).hasSize(1); // only the create
    PortalDeadlineView after = courtRow(ownerId, id);
    assertThat(after.label()).isEqualTo(before.label());
    assertThat(after.descriptionSanitised()).isEqualTo(before.descriptionSanitised());
    assertThat(after.dueDate()).isEqualTo(before.dueDate());
    assertThat(after.lastSyncedAt()).isEqualTo(before.lastSyncedAt());
  }

  @Test
  void postponeDeletesTheOriginalAndProjectsTheNewRow() {
    UUID originalId = createCourtDate("HEARING", today().plusDays(20), COURT, "2026/1234");

    UUID newId =
        inTenant(
            () ->
                courtCalendarService
                    .postponeCourtDate(
                        originalId, new PostponeRequest(today().plusDays(40), POSTPONE_REASON))
                    .id());

    assertThat(deadlineRepo.findByCustomerIdAndSourceEntityAndId(ownerId, "COURT_DATE", originalId))
        .isEmpty();
    PortalDeadlineView row = courtRow(ownerId, newId);
    assertThat(row.dueDate()).isEqualTo(today().plusDays(40));
    assertThat(row.label()).isEqualTo("Hearing — Johannesburg High Court");
    assertNoLeak(row);
    assertThat(row.descriptionSanitised()).doesNotContain("Rescheduled from");
    assertThat(cancelledEventsFor(originalId))
        .singleElement()
        .extracting(CourtDateCancelledEvent::reason)
        .isEqualTo("POSTPONED");
  }

  @Test
  void cancelDeletesTheRow() {
    UUID id = createCourtDate("TRIAL", today().plusDays(25), COURT, "2026/5555");
    assertThat(deadlineRepo.findByCustomerIdAndSourceEntityAndId(ownerId, "COURT_DATE", id))
        .isPresent();

    inTenant(() -> courtCalendarService.cancelCourtDate(id, new CancelRequest(CANCEL_REASON)));

    assertThat(deadlineRepo.findByCustomerIdAndSourceEntityAndId(ownerId, "COURT_DATE", id))
        .isEmpty();
    CourtDateCancelledEvent event = cancelledEventsFor(id).getFirst();
    assertThat(event.reason()).isEqualTo("CANCELLED");
    assertThat(event.details()).containsOnlyKeys("reason", "project_id");
    assertThat(event.details().values()).doesNotContain(CANCEL_REASON);
  }

  @Test
  void outcomeOnAScheduledRowCompletesIt() {
    UUID id = createCourtDate("HEARING", today().plusDays(5), COURT, "2026/1234");

    inTenant(() -> courtCalendarService.recordOutcome(id, new OutcomeRequest(OUTCOME)));

    PortalDeadlineView row = courtRow(ownerId, id);
    assertThat(row.status()).isEqualTo("COMPLETED");
    assertNoLeak(row);
  }

  @Test
  void outcomeOnAPostponedOriginalPublishesNothing() {
    UUID originalId = createCourtDate("HEARING", today().plusDays(20), COURT, "2026/1234");
    inTenant(
        () ->
            courtCalendarService.postponeCourtDate(
                originalId, new PostponeRequest(today().plusDays(45), POSTPONE_REASON)));

    inTenant(() -> courtCalendarService.recordOutcome(originalId, new OutcomeRequest(OUTCOME)));

    assertThat(scheduledEventsFor(originalId))
        .noneMatch(e -> "HEARD".equals(e.status()))
        .hasSize(1); // only the create
    assertThat(deadlineRepo.findByCustomerIdAndSourceEntityAndId(ownerId, "COURT_DATE", originalId))
        .isEmpty();
  }

  @Test
  void blankCourtReferenceFallsBackToHumanisedCopy() {
    UUID id = createCourtDate("HEARING", today().plusDays(20), COURT, null);

    PortalDeadlineView row = courtRow(ownerId, id);
    assertThat(row.descriptionSanitised())
        .isEqualTo("Court date — Hearing — Johannesburg High Court")
        .doesNotContain("COURT_DATE");
  }

  @Test
  void longCourtNameIsTruncatedToTheColumn() {
    String longCourt = "Gauteng Division of the High Court ".repeat(6).substring(0, 200);

    UUID id = createCourtDate("HEARING", today().plusDays(20), longCourt, "2026/1234");

    PortalDeadlineView row = courtRow(ownerId, id);
    assertThat(row.label()).hasSizeLessThanOrEqualTo(160).startsWith("Hearing — ").endsWith("…");
  }

  @Test
  void trackerCreateProjectsAPrescriptionRow() {
    var response =
        inTenant(
            () ->
                prescriptionTrackerService.create(
                    new CreatePrescriptionTrackerRequest(
                        matterId, today().minusYears(1), "GENERAL_3Y", null, NOTES),
                    memberId));

    PortalDeadlineView row = trackerRow(response.id());
    assertThat(row.deadlineType()).isEqualTo("PRESCRIPTION");
    assertThat(row.matterId()).isEqualTo(matterId);
    assertThat(row.label()).isEqualTo("Prescription deadline");
    assertThat(row.descriptionSanitised()).isEqualTo("General 3 years");
    assertThat(row.dueDate()).isEqualTo(response.prescriptionDate());
    assertThat(row.descriptionSanitised()).doesNotContain(response.causeOfActionDate().toString());
    assertNoLeak(row);
  }

  @Test
  void trackerUpdateMovesTheRow() {
    UUID id = createTracker("GENERAL_3Y", today().minusYears(1));

    var updated =
        inTenant(
            () ->
                prescriptionTrackerService.update(
                    id,
                    new UpdatePrescriptionTrackerRequest(
                        today().minusMonths(1), "DEBT_6Y", null, NOTES)));

    PortalDeadlineView row = trackerRow(id);
    assertThat(row.dueDate()).isEqualTo(updated.prescriptionDate());
    assertThat(row.descriptionSanitised()).isEqualTo("Debt 6 years");
    assertNoLeak(row);
  }

  @Test
  void interruptDeletesTheRow() {
    UUID id = createTracker("GENERAL_3Y", today().minusYears(1));
    assertThat(
            deadlineRepo.findByCustomerIdAndSourceEntityAndId(ownerId, "PRESCRIPTION_TRACKER", id))
        .isPresent();

    inTenant(
        () ->
            prescriptionTrackerService.interrupt(
                id, new InterruptRequest(today(), INTERRUPTION_REASON)));

    assertThat(
            deadlineRepo.findByCustomerIdAndSourceEntityAndId(ownerId, "PRESCRIPTION_TRACKER", id))
        .isEmpty();
    assertThat(events.stream(PrescriptionDeadlineClearedEvent.class))
        .filteredOn(e -> e.entityId().equals(id))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.reason()).isEqualTo("INTERRUPTED");
              assertThat(e.details()).containsOnlyKeys("reason", "project_id");
              assertThat(e.details().values()).doesNotContain(INTERRUPTION_REASON);
            });
  }

  @Test
  void publishedEventsCarryTheActorAndOnlyAllowListedDetails() {
    UUID courtId = createCourtDate("HEARING", today().plusDays(26), COURT, "2026/1234");
    UUID trackerId = createTracker("GENERAL_3Y", today().minusYears(1));
    inTenant(() -> courtCalendarService.cancelCourtDate(courtId, new CancelRequest(CANCEL_REASON)));
    inTenant(
        () ->
            prescriptionTrackerService.interrupt(
                trackerId, new InterruptRequest(today(), INTERRUPTION_REASON)));

    List<DomainEvent> captured =
        events.stream(DomainEvent.class)
            .filter(
                e ->
                    e instanceof CourtDateScheduledEvent
                        || e instanceof CourtDateCancelledEvent
                        || e instanceof PrescriptionDeadlineSetEvent
                        || e instanceof PrescriptionDeadlineClearedEvent)
            .filter(e -> e.entityId().equals(courtId) || e.entityId().equals(trackerId))
            .toList();

    assertThat(captured)
        .hasSize(4)
        .hasAtLeastOneElementOfType(CourtDateScheduledEvent.class)
        .hasAtLeastOneElementOfType(CourtDateCancelledEvent.class)
        .hasAtLeastOneElementOfType(PrescriptionDeadlineSetEvent.class)
        .hasAtLeastOneElementOfType(PrescriptionDeadlineClearedEvent.class);
    for (DomainEvent e : captured) {
      assertThat(e.actorMemberId()).isEqualTo(memberId);
      assertThat(e.actorName()).isEqualTo(MEMBER_NAME).isNotEqualTo("system");
      assertThat(e.tenantId()).isEqualTo(tenantSchema);
      assertThat(e.orgId()).isEqualTo(ORG_ID);
      assertThat(e.projectId()).isEqualTo(matterId);
      assertThat(e.details().get("project_id")).isEqualTo(matterId.toString());
      Set<String> allowed =
          switch (e) {
            case CourtDateScheduledEvent ignored ->
                Set.of(
                    "date_type",
                    "scheduled_date",
                    "court_name",
                    "court_reference",
                    "status",
                    "project_id");
            case PrescriptionDeadlineSetEvent ignored ->
                Set.of("prescription_type", "prescription_date", "project_id");
            default -> Set.of("reason", "project_id");
          };
      assertThat(allowed).containsAll(e.details().keySet());
      assertThat(e.details().values().stream().map(String::valueOf).toList())
          .doesNotContainAnyElementsOf(FIRM_ONLY);
    }
  }

  // ── helpers ──

  private UUID createCourtDate(String type, LocalDate date, String court, String reference) {
    return inTenant(
        () ->
            courtCalendarService
                .createCourtDate(
                    new CreateCourtDateRequest(
                        matterId,
                        type,
                        date,
                        LocalTime.of(10, 0),
                        court,
                        reference,
                        JUDGE,
                        DESCRIPTION,
                        7),
                    memberId)
                .id());
  }

  private UUID createTracker(String type, LocalDate causeOfAction) {
    return inTenant(
        () ->
            prescriptionTrackerService
                .create(
                    new CreatePrescriptionTrackerRequest(
                        matterId, causeOfAction, type, null, NOTES),
                    memberId)
                .id());
  }

  private PortalDeadlineView trackerRow(UUID id) {
    return deadlineRepo
        .findByCustomerIdAndSourceEntityAndId(ownerId, "PRESCRIPTION_TRACKER", id)
        .orElseThrow();
  }

  private List<CourtDateScheduledEvent> scheduledEventsFor(UUID id) {
    return events.stream(CourtDateScheduledEvent.class)
        .filter(e -> e.entityId().equals(id))
        .toList();
  }

  private List<CourtDateCancelledEvent> cancelledEventsFor(UUID id) {
    return events.stream(CourtDateCancelledEvent.class)
        .filter(e -> e.entityId().equals(id))
        .toList();
  }

  /** Firm-only strings must never reach the portal row's label or description. */
  private static void assertNoLeak(PortalDeadlineView row) {
    for (String s : FIRM_ONLY) {
      assertThat(row.label()).doesNotContain(s);
      assertThat(row.descriptionSanitised()).doesNotContain(s);
    }
  }

  private PortalDeadlineView courtRow(UUID customerId, UUID id) {
    return deadlineRepo
        .findByCustomerIdAndSourceEntityAndId(customerId, "COURT_DATE", id)
        .orElseThrow();
  }

  private static LocalDate today() {
    return LocalDate.now(KaziTime.ZONE);
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
