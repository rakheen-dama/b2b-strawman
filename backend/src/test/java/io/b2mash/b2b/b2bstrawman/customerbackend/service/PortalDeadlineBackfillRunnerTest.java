package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDeadlineView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalBackfillMarkerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalDeadlineViewRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDateRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTracker;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * §5b row 19 existing tenants (Phase 89 633B, ADR-363, R6.4, ADR-358): court dates and prescription
 * trackers created before the live listeners existed have no {@code portal.portal_deadline_view}
 * row; {@link PortalDeadlineBackfillRunner} projects the live future ones once per tenant and
 * records a {@code portal_deadline_backfill} marker. Rows are seeded through the repositories (no
 * events — a pre-633B tenant). The SUT is built by hand with a fixed {@link Clock} in 2030 so
 * "past" rows are past only by the injected clock (no new Spring context). Assertions are scoped to
 * this class's own tenant and the ids it seeded.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalDeadlineBackfillRunnerTest {

  private static final String ORG_ID = "org_portal_deadline_backfill";
  private static final LocalDate FIXED_TODAY = LocalDate.of(2030, 1, 15);
  private static final String COURT = "Johannesburg High Court";
  private static final String JUDGE = "Judge Mokgoro";
  private static final String DESCRIPTION = "Privileged: client admitted liability";
  private static final String NOTES = "Cause: slip at Sandton City";
  private static final List<String> FIRM_ONLY = List.of(JUDGE, DESCRIPTION, NOTES);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CourtDateRepository courtDateRepository;
  @Autowired private PrescriptionTrackerRepository prescriptionTrackerRepository;
  @Autowired private DeadlinePortalSyncService syncService;
  @Autowired private PortalDeadlineViewRepository deadlineRepo;
  @Autowired private PortalBackfillMarkerRepository markerRepository;
  @Autowired private PortalDeadlineBackfillRunner springRunner;

  @Autowired
  @Qualifier("portalTransactionManager")
  private PlatformTransactionManager portalTransactionManager;

  @Autowired
  @Qualifier("portalJdbcClient")
  private JdbcClient portalJdbc;

  private OrgSchemaMapping mapping;
  private UUID memberId;
  private UUID ownerId;
  private UUID coClientId;
  private UUID matterId;
  private PortalDeadlineBackfillService fixedClockService;
  private PortalDeadlineBackfillRunner fixedClockRunner;

  // seeded ids
  private UUID futureCourt;
  private UUID todayCourt;
  private UUID pastCourt;
  private UUID postponedCourt;
  private UUID cancelledCourt;
  private UUID heardCourt;
  private UUID runningTracker;
  private UUID warnedTracker;
  private UUID interruptedTracker;
  private UUID pastTracker;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Deadline Backfill Firm", "legal-za");
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_portal_deadline_backfill",
                "portal_deadline_backfill@test.com",
                "Deadline Backfill Owner",
                "owner"));
    mapping = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow();
    fixedClockService =
        new PortalDeadlineBackfillService(
            courtDateRepository,
            prescriptionTrackerRepository,
            syncService,
            deadlineRepo,
            portalTransactionManager,
            Clock.fixed(FIXED_TODAY.atStartOfDay(KaziTime.ZONE).toInstant(), KaziTime.ZONE));
    fixedClockRunner =
        new PortalDeadlineBackfillRunner(
            orgSchemaMappingRepository, markerRepository, fixedClockService);

    inTenantTx(
        () -> {
          ownerId =
              customerRepository
                  .saveAndFlush(
                      createActiveCustomer(
                          "Backfill Owner", "dl-backfill-owner@test.com", memberId))
                  .getId();
          coClientId =
              customerRepository
                  .saveAndFlush(
                      createActiveCustomer("Backfill Co", "dl-backfill-co@test.com", memberId))
                  .getId();
          var project = new Project("Ndlovu v RAF", "Backfill matter", memberId);
          project.setCustomerId(ownerId);
          matterId = projectRepository.saveAndFlush(project).getId();
          customerProjectRepository.save(new CustomerProject(ownerId, matterId, memberId));
          customerProjectRepository.save(new CustomerProject(coClientId, matterId, memberId));

          futureCourt = court("SCHEDULED", LocalDate.of(2030, 2, 1));
          todayCourt = court("SCHEDULED", FIXED_TODAY);
          pastCourt = court("SCHEDULED", LocalDate.of(2030, 1, 10));
          postponedCourt = court("POSTPONED", LocalDate.of(2030, 3, 1));
          cancelledCourt = court("CANCELLED", LocalDate.of(2030, 3, 1));
          heardCourt = court("HEARD", LocalDate.of(2030, 3, 1));
          runningTracker = tracker("RUNNING", LocalDate.of(2031, 6, 1));
          warnedTracker = tracker("WARNED", LocalDate.of(2030, 2, 15));
          interruptedTracker = tracker("INTERRUPTED", LocalDate.of(2031, 6, 1));
          pastTracker = tracker("RUNNING", LocalDate.of(2029, 12, 1));
          return null;
        });
  }

  /** Simulates a tenant that predates 633B: no projected deadline rows, no marker. */
  @BeforeEach
  void resetTenant() {
    portalJdbc
        .sql("DELETE FROM portal.portal_backfill_markers WHERE tenant_schema = ?")
        .params(mapping.getSchemaName())
        .update();
    portalJdbc
        .sql("DELETE FROM portal.portal_deadline_view WHERE customer_id IN (?, ?)")
        .params(ownerId, coClientId)
        .update();
  }

  @Test
  void backfillProjectsOnlyLiveFutureRowsAndIsSkippedOnTheSecondRun() {
    int first = fixedClockRunner.backfillTenant(mapping);

    assertThat(first).isEqualTo(4);
    assertThat(ownerRowIds())
        .containsExactlyInAnyOrder(futureCourt, todayCourt, runningTracker, warnedTracker)
        .doesNotContain(
            pastCourt, postponedCourt, cancelledCourt, heardCourt, interruptedTracker, pastTracker);
    Map<String, Object> markerAfterFirst = marker();
    assertThat(((Number) markerAfterFirst.get("row_count")).intValue()).isEqualTo(4);

    PortalDeadlineView court = ownerRow("COURT_DATE", futureCourt);
    assertThat(court.deadlineType()).isEqualTo("COURT_DATE");
    assertThat(court.label()).isEqualTo("Hearing — Johannesburg High Court");
    assertThat(court.descriptionSanitised()).isEqualTo("2026/1234");
    assertThat(court.matterId()).isEqualTo(matterId);
    assertThat(court.status()).isEqualTo("UPCOMING");
    assertNoLeak(court);

    PortalDeadlineView prescription = ownerRow("PRESCRIPTION_TRACKER", runningTracker);
    assertThat(prescription.deadlineType()).isEqualTo("PRESCRIPTION");
    assertThat(prescription.label()).isEqualTo("Prescription deadline");
    assertThat(prescription.descriptionSanitised()).isEqualTo("General 3 years");
    assertThat(prescription.dueDate()).isEqualTo(LocalDate.of(2031, 6, 1));
    assertNoLeak(prescription);

    int second = fixedClockRunner.backfillTenant(mapping);

    assertThat(second).isEqualTo(-1); // marker present → skipped
    assertThat(marker()).isEqualTo(markerAfterFirst);
    assertThat(ownerRowIds()).hasSize(4);
  }

  @Test
  void aCoClientGetsNoBackfilledRow() {
    fixedClockRunner.backfillTenant(mapping);

    assertThat(deadlineRepo.findByCustomer(coClientId, null, null, null, LocalDate.now()))
        .extracting(PortalDeadlineView::id)
        .doesNotContain(futureCourt, todayCourt, runningTracker, warnedTracker);
    assertThat(ownerRowIds()).contains(futureCourt, runningTracker);
  }

  @Test
  void reconcileRemovesARowWhoseCourtDateWasCancelledMidRun() {
    // Race (D7, 633A precedent): the backfill read X and Y, then X was cancelled and Y heard (their
    // AFTER_COMMIT listeners ran before our upsert), then the backfill wrote stale rows for both.
    UUID[] ids = new UUID[2];
    inTenantTx(
        () -> {
          ids[0] = court("SCHEDULED", LocalDate.of(2030, 4, 1));
          ids[1] = court("SCHEDULED", LocalDate.of(2030, 4, 2));
          return null;
        });
    var written =
        List.of(
            DeadlinePortalSyncService.courtDateSource(
                ids[0], ownerId, matterId, "HEARING", LocalDate.of(2030, 4, 1), COURT, null, null),
            DeadlinePortalSyncService.courtDateSource(
                ids[1], ownerId, matterId, "HEARING", LocalDate.of(2030, 4, 2), COURT, null, null));
    written.forEach(syncService::upsertFromDeadline);
    inTenantTx(
        () -> {
          var x = courtDateRepository.findById(ids[0]).orElseThrow();
          x.setStatus("CANCELLED");
          courtDateRepository.save(x);
          var y = courtDateRepository.findById(ids[1]).orElseThrow();
          y.setStatus("HEARD");
          courtDateRepository.save(y);
          return null;
        });

    int removed =
        fixedClockService.reconcile(ORG_ID, mapping.getSchemaName(), mapping.getShardId(), written);

    assertThat(removed).isEqualTo(1);
    assertThat(deadlineRepo.findByCustomerIdAndSourceEntityAndId(ownerId, "COURT_DATE", ids[0]))
        .isEmpty();
    assertThat(ownerRow("COURT_DATE", ids[1]).status()).isEqualTo("COMPLETED");
  }

  @Test
  void reconcileRemovesARowWhoseTrackerExpiredMidRun() {
    // Race: the backfill read X (RUNNING) and Y (WARNED); the reminder job then flipped X to
    // EXPIRED (it publishes no event) before reconcile. Reconcile must drop X (the backfill read's
    // dead-status rule) and keep re-syncing the still-live Y.
    UUID[] ids = new UUID[2];
    inTenantTx(
        () -> {
          ids[0] = tracker("RUNNING", LocalDate.of(2030, 5, 1));
          ids[1] = tracker("WARNED", LocalDate.of(2030, 5, 2));
          return null;
        });
    try {
      var written =
          List.of(
              DeadlinePortalSyncService.prescriptionSource(
                  ids[0], ownerId, matterId, "GENERAL_3Y", LocalDate.of(2030, 5, 1)),
              DeadlinePortalSyncService.prescriptionSource(
                  ids[1], ownerId, matterId, "GENERAL_3Y", LocalDate.of(2030, 5, 2)));
      written.forEach(syncService::upsertFromDeadline);
      inTenantTx(
          () -> {
            var x = prescriptionTrackerRepository.findById(ids[0]).orElseThrow();
            x.setStatus("EXPIRED");
            prescriptionTrackerRepository.save(x);
            return null;
          });

      int removed =
          fixedClockService.reconcile(
              ORG_ID, mapping.getSchemaName(), mapping.getShardId(), written);

      assertThat(removed).isEqualTo(1);
      assertThat(
              deadlineRepo.findByCustomerIdAndSourceEntityAndId(
                  ownerId, "PRESCRIPTION_TRACKER", ids[0]))
          .isEmpty();
      assertThat(ownerRow("PRESCRIPTION_TRACKER", ids[1]).dueDate())
          .isEqualTo(LocalDate.of(2030, 5, 2));
    } finally {
      // Y is still live and future-dated: drop both so the count-based backfill tests in this
      // PER_CLASS tenant are order-independent.
      inTenantTx(
          () -> {
            prescriptionTrackerRepository.deleteAllById(List.of(ids[0], ids[1]));
            return null;
          });
    }
  }

  @Test
  void runSmokeCheckBackfillsAnUnmarkedTenant() {
    UUID soon = inTenantTx(() -> court("SCHEDULED", LocalDate.now(KaziTime.ZONE).plusDays(10)));

    springRunner.run(null);

    assertThat(ownerRowIds()).contains(soon, futureCourt, runningTracker);
    assertThat(((Number) marker().get("row_count")).intValue()).isPositive();
  }

  // ── helpers ──

  /** Must run inside the tenant scope + tenant transaction. */
  private UUID court(String status, LocalDate date) {
    var cd =
        new CourtDate(
            matterId,
            ownerId,
            "HEARING",
            date,
            null,
            COURT,
            "2026/1234",
            JUDGE,
            DESCRIPTION,
            7,
            memberId);
    cd.setStatus(status);
    return courtDateRepository.save(cd).getId();
  }

  /** Must run inside the tenant scope + tenant transaction. */
  private UUID tracker(String status, LocalDate prescriptionDate) {
    var t =
        new PrescriptionTracker(
            matterId,
            ownerId,
            prescriptionDate.minusYears(3),
            "GENERAL_3Y",
            null,
            prescriptionDate,
            NOTES,
            memberId);
    t.setStatus(status);
    return prescriptionTrackerRepository.save(t).getId();
  }

  private List<UUID> ownerRowIds() {
    return deadlineRepo.findByCustomer(ownerId, null, null, null, LocalDate.now()).stream()
        .map(PortalDeadlineView::id)
        .toList();
  }

  private PortalDeadlineView ownerRow(String source, UUID id) {
    return deadlineRepo.findByCustomerIdAndSourceEntityAndId(ownerId, source, id).orElseThrow();
  }

  private static void assertNoLeak(PortalDeadlineView row) {
    for (String s : FIRM_ONLY) {
      assertThat(row.label()).doesNotContain(s);
      assertThat(row.descriptionSanitised()).doesNotContain(s);
    }
  }

  private Map<String, Object> marker() {
    return portalJdbc
        .sql(
            "SELECT row_count, backfilled_at FROM portal.portal_backfill_markers"
                + " WHERE backfill_name = ? AND tenant_schema = ?")
        .params(PortalDeadlineBackfillRunner.NAME, mapping.getSchemaName())
        .query()
        .singleRow();
  }

  private <T> T inTenantTx(Callable<T> action) {
    var tx = new TransactionTemplate(transactionManager);
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
          .where(RequestScopes.ORG_ID, ORG_ID)
          .where(RequestScopes.MEMBER_ID, memberId)
          .call(
              () ->
                  tx.execute(
                      s -> {
                        try {
                          return action.call();
                        } catch (RuntimeException e) {
                          throw e;
                        } catch (Exception e) {
                          throw new RuntimeException(e);
                        }
                      }));
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
