package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalDeadlineResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDeadlineView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalDeadlineViewRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * D-127: the portal "Diary dates" page sends no {@code from}/{@code to} (PR #1897), so the default
 * window applies. It used to start at SA today for every status filter, which made the
 * <b>Overdue</b> filter always empty (an overdue deadline is due before today) and hid every past
 * item from <b>Completed</b> and <b>Cancelled</b>. Rule: with no {@code from}, those three filters
 * have no lower bound; {@code to} still defaults to today+60. "All", UPCOMING and DUE_SOON keep
 * today..today+60, and an explicit {@code from} is always honoured.
 *
 * <p>Rows are seeded straight into the portal read model under a random customer id, relative to
 * day D = the Clock bean's "today" (SA time); the service is built with a clock fixed at midday SA
 * time on D (the {@code PortalDeadlineStatusStalenessIntegrationTest} pattern).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalDeadlineStatusFilterLookbackIntegrationTest {

  private static final String ORG_ID = "org_portal_deadline_lookback_d127";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PortalDeadlineViewRepository deadlineRepo;
  @Autowired private VerticalModuleGuard moduleGuard;
  @Autowired private Clock clock;

  private final UUID customerId = UUID.randomUUID();
  private String tenantSchema;
  private LocalDate d;

  // Seeded rows (id per row), named by effective status and due date relative to D.
  private final UUID overdueLongAgo = UUID.randomUUID(); // D-400, unresolved
  private final UUID overduePast = UUID.randomUUID(); // D-30, unresolved
  private final UUID overdueYesterday = UUID.randomUUID(); // D-1, unresolved
  private final UUID completedPast = UUID.randomUUID(); // D-20, COMPLETED
  private final UUID cancelledPast = UUID.randomUUID(); // D-10, CANCELLED
  private final UUID dueSoon = UUID.randomUUID(); // D+3
  private final UUID completedFuture = UUID.randomUUID(); // D+5, COMPLETED
  private final UUID upcoming = UUID.randomUUID(); // D+20
  private final UUID cancelledFuture = UUID.randomUUID(); // D+30, CANCELLED
  private final UUID cancelledBeyond = UUID.randomUUID(); // D+90, CANCELLED (past the +60 default)
  private final UUID upcomingBeyond = UUID.randomUUID(); // D+90

  @BeforeAll
  void setup() {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Deadline Lookback Firm", "legal-za")
            .schemaName();
    d = LocalDate.now(clock);
    // Stored statuses on unresolved rows are deliberately stale; only COMPLETED/CANCELLED count.
    seed(overdueLongAgo, d.minusDays(400), "UPCOMING");
    seed(overduePast, d.minusDays(30), "UPCOMING");
    seed(overdueYesterday, d.minusDays(1), "DUE_SOON");
    seed(completedPast, d.minusDays(20), "COMPLETED");
    seed(cancelledPast, d.minusDays(10), "CANCELLED");
    seed(dueSoon, d.plusDays(3), "UPCOMING");
    seed(completedFuture, d.plusDays(5), "COMPLETED");
    seed(upcoming, d.plusDays(20), "UPCOMING");
    seed(cancelledFuture, d.plusDays(30), "CANCELLED");
    seed(cancelledBeyond, d.plusDays(90), "CANCELLED");
    seed(upcomingBeyond, d.plusDays(90), "UPCOMING");
  }

  @Test
  void pastLookingFiltersHaveNoLowerBoundWhenNoFromIsSent() {
    SoftAssertions soft = new SoftAssertions();
    soft.assertThat(ids(list(null, null, "OVERDUE")))
        .as("[B1] OVERDUE, no from: every unresolved past row, oldest first")
        .containsExactly(overdueLongAgo, overduePast, overdueYesterday);
    soft.assertThat(ids(list(null, null, "COMPLETED")))
        .as("[B2] COMPLETED, no from: past and future completed rows up to D+60")
        .containsExactly(completedPast, completedFuture);
    soft.assertThat(ids(list(null, null, "CANCELLED")))
        .as("[B3] CANCELLED, no from: past and future cancelled rows up to D+60 (not D+90)")
        .containsExactly(cancelledPast, cancelledFuture);
    soft.assertThat(ids(list(null, null, "overdue")))
        .as("[B4] lower-case status is normalised before the lookback rule")
        .containsExactly(overdueLongAgo, overduePast, overdueYesterday);
    soft.assertThat(list(null, null, "OVERDUE"))
        .as("[B5] every OVERDUE row reads OVERDUE")
        .allSatisfy(r -> assertThat(r.status()).isEqualTo("OVERDUE"));
    soft.assertAll();
  }

  @Test
  void allUpcomingAndDueSoonKeepTheTodayToSixtyDayWindow() {
    SoftAssertions soft = new SoftAssertions();
    soft.assertThat(ids(list(null, null, null)))
        .as("[C1] All: D..D+60 only, ordered by due date")
        .containsExactly(dueSoon, completedFuture, upcoming, cancelledFuture);
    soft.assertThat(ids(list(null, null, "UPCOMING")))
        .as("[C2] UPCOMING: D..D+60")
        .containsExactly(upcoming);
    soft.assertThat(ids(list(null, null, "DUE_SOON")))
        .as("[C3] DUE_SOON: D..D+60")
        .containsExactly(dueSoon);
    soft.assertAll();
  }

  @Test
  void anExplicitFromIsAlwaysHonoured() {
    SoftAssertions soft = new SoftAssertions();
    soft.assertThat(ids(list(d.minusDays(30), null, "OVERDUE")))
        .as("[D1] OVERDUE from=D-30")
        .containsExactly(overduePast, overdueYesterday);
    soft.assertThat(ids(list(d, null, "COMPLETED")))
        .as("[D2] COMPLETED from=D")
        .containsExactly(completedFuture);
    soft.assertThat(ids(list(d.minusDays(15), d.plusDays(100), "CANCELLED")))
        .as("[D3] CANCELLED from=D-15 to=D+100")
        .containsExactly(cancelledPast, cancelledFuture, cancelledBeyond);
    soft.assertThat(ids(list(d.minusDays(400), null, null)))
        .as("[D4] All from=D-400: explicit from widens the default view")
        .containsExactly(
            overdueLongAgo,
            overduePast,
            completedPast,
            cancelledPast,
            overdueYesterday,
            dueSoon,
            completedFuture,
            upcoming,
            cancelledFuture);
    soft.assertAll();
  }

  @Test
  void dateRangeValidationHandlesTheOpenLowerBound() {
    // No from + a past-looking filter: there is no lower bound to compare, so no 400.
    assertThat(ids(list(null, d.minusDays(25), "OVERDUE")))
        .as("[E1] OVERDUE, no from, to=D-25")
        .containsExactly(overdueLongAgo, overduePast);
    // The default lower bound (today) still applies to "All", so to < today is a 400 as before.
    assertThatThrownBy(() -> list(null, d.minusDays(1), null))
        .isInstanceOf(InvalidStateException.class);
    // An explicit from after to is always a 400.
    assertThatThrownBy(() -> list(d, d.minusDays(1), "OVERDUE"))
        .isInstanceOf(InvalidStateException.class);
  }

  // ── helpers ──

  private void seed(UUID id, LocalDate dueDate, String storedStatus) {
    deadlineRepo.upsert(
        new PortalDeadlineView(
            id,
            "COURT_DATE",
            customerId,
            UUID.randomUUID(),
            "COURT_DATE",
            "Hearing " + dueDate,
            dueDate,
            storedStatus,
            null,
            null));
  }

  /** A {@link PortalDeadlineService} whose "today" is D (midday SA time). */
  private PortalDeadlineService service() {
    Clock fixed =
        Clock.fixed(d.atTime(LocalTime.NOON).atZone(KaziTime.ZONE).toInstant(), KaziTime.ZONE);
    return new PortalDeadlineService(deadlineRepo, moduleGuard, fixed);
  }

  private List<PortalDeadlineResponse> list(LocalDate from, LocalDate to, String status) {
    return inCustomer(() -> service().listForContact(from, to, status));
  }

  private static List<UUID> ids(List<PortalDeadlineResponse> rows) {
    return rows.stream().map(PortalDeadlineResponse::id).toList();
  }

  /** Portal-contact scope: tenant + org for the module gate, customer for the row scoping. */
  private <T> T inCustomer(Callable<T> action) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .where(RequestScopes.ORG_ID, ORG_ID)
          .where(RequestScopes.CUSTOMER_ID, customerId)
          .call(action::call);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
