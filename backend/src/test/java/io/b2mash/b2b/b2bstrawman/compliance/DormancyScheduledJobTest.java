package io.b2mash.b2b.b2bstrawman.compliance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobQueueProperties;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantScopedRunner;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DormancyScheduledJobTest {

  @Mock private TenantScopedRunner tenantScopedRunner;
  @Mock private CustomerLifecycleService lifecycleService;
  @Mock private NotificationService notificationService;
  @Mock private MemberRepository memberRepository;
  @Mock private JobEnqueuer jobEnqueuer;
  @Mock private JobQueueProperties jobQueueProperties;

  private DormancyScheduledJob job;

  @BeforeEach
  void setUp() {
    when(jobQueueProperties.isDualMode("dormancy_check")).thenReturn(true);
    job =
        new DormancyScheduledJob(
            tenantScopedRunner,
            lifecycleService,
            notificationService,
            memberRepository,
            jobEnqueuer,
            jobQueueProperties);
  }

  /** Stubs forEachTenant to invoke the action once with synthetic tenant data. */
  private void stubForEachTenantOnce(String tenantId, String orgId) {
    when(tenantScopedRunner.forEachTenant(any()))
        .thenAnswer(
            invocation -> {
              BiConsumer<String, String> action = invocation.getArgument(0);
              action.accept(tenantId, orgId);
              return 1;
            });
  }

  @Test
  void executeDormancyCheck_noTenants_completesCleanly() {
    when(tenantScopedRunner.forEachTenant(any())).thenReturn(0);

    job.executeDormancyCheck();

    verify(lifecycleService, never()).executeDormancyTransitions();
  }

  @Test
  void executeDormancyCheck_withTransitions_notifiesAdmins() {
    stubForEachTenantOnce("tenant_abc123", "org_test");
    when(lifecycleService.executeDormancyTransitions()).thenReturn(2);

    var adminRole = new OrgRole("Admin", "admin", "Admin role", true);
    var admin = new Member("user_admin", "admin@test.com", "Admin", null, adminRole);
    when(memberRepository.findByRoleSlugsIn(List.of("admin", "owner"))).thenReturn(List.of(admin));

    job.executeDormancyCheck();

    verify(lifecycleService).executeDormancyTransitions();
    verify(notificationService)
        .createNotification(
            any(),
            eq("CUSTOMER_DORMANCY"),
            eq("2 customers marked as dormant"),
            any(),
            any(),
            any(),
            any());
  }

  @Test
  void executeDormancyCheck_zeroTransitions_skipsNotification() {
    stubForEachTenantOnce("tenant_def456", "org_test2");
    when(lifecycleService.executeDormancyTransitions()).thenReturn(0);

    job.executeDormancyCheck();

    verify(notificationService, never())
        .createNotification(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void executeDormancyCheck_singleTransition_usesSingularTitle() {
    stubForEachTenantOnce("tenant_ghi789", "org_test3");
    when(lifecycleService.executeDormancyTransitions()).thenReturn(1);

    var ownerRole = new OrgRole("Owner", "owner", "Owner role", true);
    var admin = new Member("user_admin2", "admin2@test.com", "Admin2", null, ownerRole);
    when(memberRepository.findByRoleSlugsIn(List.of("admin", "owner"))).thenReturn(List.of(admin));

    job.executeDormancyCheck();

    verify(notificationService)
        .createNotification(
            any(),
            eq("CUSTOMER_DORMANCY"),
            eq("1 customer marked as dormant"),
            any(),
            any(),
            any(),
            any());
  }
}
