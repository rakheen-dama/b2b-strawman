package io.b2mash.b2b.b2bstrawman.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.activity.ActivityItem;
import io.b2mash.b2b.b2bstrawman.activity.ActivityService;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.audit.CrossProjectActivityProjection;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudgetRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.CrossMatterActivityItem;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestService;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code DashboardService.getCrossProjectActivity} caches already-formatted (and amount-redacted)
 * sentences in the org cache. A custom role can gain or lose {@code FINANCIAL_VISIBILITY} / {@code
 * VIEW_TRUST} while the member id and role slug stay the same, so the effective visibility must be
 * part of the cache key — otherwise a member who just lost a capability is served the unredacted
 * sentences cached for them a moment earlier.
 */
class DashboardActivityCacheTest {

  private static final String TENANT = "tenant_activity_cache";
  private static final int LIMIT = 10;
  private static final UUID EVENT_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();
  private static final ActorContext MEMBER = new ActorContext(MEMBER_ID, Roles.ORG_MEMBER);

  private final AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
  private final ActivityService activityService = mock(ActivityService.class);
  private DashboardService dashboardService;

  @BeforeEach
  void setUp() {
    dashboardService =
        new DashboardService(
            mock(TaskRepository.class),
            mock(TimeEntryRepository.class),
            auditEventRepository,
            mock(ProjectBudgetRepository.class),
            mock(ProjectRepository.class),
            mock(CustomerProjectRepository.class),
            mock(CustomerRepository.class),
            mock(ProjectAccessService.class),
            mock(OrgSettingsService.class),
            mock(VerticalModuleGuard.class),
            mock(InformationRequestService.class),
            mock(TrustAccountService.class),
            mock(TrustTransactionService.class),
            mock(ClientLedgerService.class),
            mock(TrustReconciliationRepository.class),
            mock(DisbursementRepository.class),
            activityService,
            Clock.systemUTC());

    var row = mock(CrossProjectActivityProjection.class);
    when(row.getEventId()).thenReturn(EVENT_ID);
    when(row.getEventType()).thenReturn("trust_transaction.deposit_recorded");
    when(row.getProjectId()).thenReturn(UUID.randomUUID());
    when(row.getProjectName()).thenReturn("Estate late Dlamini");
    when(row.getOccurredAt()).thenReturn(Instant.parse("2026-10-20T08:00:00Z"));
    when(auditEventRepository.findCrossProjectActivityForMember(MEMBER_ID, LIMIT))
        .thenReturn(List.of(row));
    when(auditEventRepository.findAllById(anyIterable())).thenReturn(List.of());
    // Stand-in for the real formatter: the sentence depends on the capabilities bound at call time.
    when(activityService.formatEvents(any(), eq(MEMBER)))
        .thenAnswer(
            invocation -> {
              String message =
                  RequestScopes.hasCapability("VIEW_TRUST")
                      ? "Alice recorded a trust deposit of R50 000,00 (DEP-1)"
                      : "Alice recorded a trust deposit";
              return List.of(
                  new ActivityItem(
                      EVENT_ID,
                      message,
                      "Alice",
                      null,
                      "trust_transaction",
                      EVENT_ID,
                      null,
                      "trust_transaction.deposit_recorded",
                      Instant.parse("2026-10-20T08:00:00Z")));
            });
  }

  @Test
  void sameMemberAndRoleWithDifferentCapabilitiesDoNotShareACacheEntry() {
    assertThat(homeAs(Set.of("FINANCIAL_VISIBILITY", "VIEW_TRUST")))
        .isEqualTo("Alice recorded a trust deposit of R50 000,00 (DEP-1)");

    // Same member, same role slug — the custom role just lost VIEW_TRUST.
    assertThat(homeAs(Set.of("FINANCIAL_VISIBILITY"))).isEqualTo("Alice recorded a trust deposit");
    // ...and FINANCIAL_VISIBILITY too.
    assertThat(homeAs(Set.of())).isEqualTo("Alice recorded a trust deposit");

    verify(auditEventRepository, times(3)).findCrossProjectActivityForMember(MEMBER_ID, LIMIT);
  }

  @Test
  void sameMemberRoleAndCapabilitiesReuseTheCacheEntry() {
    homeAs(Set.of("FINANCIAL_VISIBILITY", "VIEW_TRUST"));
    assertThat(homeAs(Set.of("FINANCIAL_VISIBILITY", "VIEW_TRUST")))
        .isEqualTo("Alice recorded a trust deposit of R50 000,00 (DEP-1)");

    verify(auditEventRepository, times(1)).findCrossProjectActivityForMember(MEMBER_ID, LIMIT);
  }

  private String homeAs(Set<String> capabilities) {
    List<CrossMatterActivityItem> items =
        ScopedValue.where(RequestScopes.CAPABILITIES, capabilities)
            .call(() -> dashboardService.getCrossProjectActivity(TENANT, MEMBER, LIMIT));
    assertThat(items).hasSize(1);
    return items.getFirst().description();
  }
}
