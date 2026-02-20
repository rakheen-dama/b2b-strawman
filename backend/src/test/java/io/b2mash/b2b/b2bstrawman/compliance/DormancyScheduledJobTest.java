package io.b2mash.b2b.b2bstrawman.compliance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DormancyScheduledJobTest {

  @Mock private OrgSchemaMappingRepository mappingRepository;
  @Mock private CustomerLifecycleService lifecycleService;
  @Mock private NotificationService notificationService;
  @Mock private MemberRepository memberRepository;

  private DormancyScheduledJob job;

  @BeforeEach
  void setUp() {
    job =
        new DormancyScheduledJob(
            mappingRepository, lifecycleService, notificationService, memberRepository);
  }

  @Test
  void executeDormancyCheck_noTenants_completesCleanly() {
    when(mappingRepository.findAll()).thenReturn(List.of());

    job.executeDormancyCheck();

    verify(lifecycleService, never()).executeDormancyTransitions();
  }

  @Test
  void executeDormancyCheck_withTransitions_notifiesAdmins() {
    var mapping = new OrgSchemaMapping("org_test", "tenant_abc123");
    when(mappingRepository.findAll()).thenReturn(List.of(mapping));
    when(lifecycleService.executeDormancyTransitions()).thenReturn(2);

    var admin = new Member("user_admin", "admin@test.com", "Admin", null, "admin");
    when(memberRepository.findByOrgRoleIn(List.of("admin", "owner"))).thenReturn(List.of(admin));

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
    var mapping = new OrgSchemaMapping("org_test2", "tenant_def456");
    when(mappingRepository.findAll()).thenReturn(List.of(mapping));
    when(lifecycleService.executeDormancyTransitions()).thenReturn(0);

    job.executeDormancyCheck();

    verify(notificationService, never())
        .createNotification(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void executeDormancyCheck_singleTransition_usesSingularTitle() {
    var mapping = new OrgSchemaMapping("org_test3", "tenant_ghi789");
    when(mappingRepository.findAll()).thenReturn(List.of(mapping));
    when(lifecycleService.executeDormancyTransitions()).thenReturn(1);

    var admin = new Member("user_admin2", "admin2@test.com", "Admin2", null, "owner");
    when(memberRepository.findByOrgRoleIn(List.of("admin", "owner"))).thenReturn(List.of(admin));

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
