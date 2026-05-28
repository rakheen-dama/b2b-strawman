package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobQueueProperties;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantScopedRunner;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that runs daily at 2 AM to detect and transition inactive customers to DORMANT
 * status. Iterates all tenant schemas and executes dormancy checks within each tenant context.
 */
@Component
public class DormancyScheduledJob {

  private static final Logger log = LoggerFactory.getLogger(DormancyScheduledJob.class);

  private final TenantScopedRunner tenantScopedRunner;
  private final CustomerLifecycleService lifecycleService;
  private final NotificationService notificationService;
  private final MemberRepository memberRepository;
  private final JobEnqueuer jobEnqueuer;
  private final JobQueueProperties jobQueueProperties;

  public DormancyScheduledJob(
      TenantScopedRunner tenantScopedRunner,
      CustomerLifecycleService lifecycleService,
      NotificationService notificationService,
      MemberRepository memberRepository,
      JobEnqueuer jobEnqueuer,
      JobQueueProperties jobQueueProperties) {
    this.tenantScopedRunner = tenantScopedRunner;
    this.lifecycleService = lifecycleService;
    this.notificationService = notificationService;
    this.memberRepository = memberRepository;
    this.jobEnqueuer = jobEnqueuer;
    this.jobQueueProperties = jobQueueProperties;
  }

  @SchedulerLock(name = "dormancy_execute_dormancy_check", lockAtLeastFor = "5m")
  @Scheduled(cron = "0 0 2 * * *")
  public void executeDormancyCheck() {
    log.info("Auto-dormancy scheduled job started");

    if (jobQueueProperties.isDualMode("dormancy_check")) {
      int[] totalTransitioned = {0};
      int tenantsProcessed =
          tenantScopedRunner.forEachTenant(
              (tenantId, orgId) -> totalTransitioned[0] += processTenant());

      log.info(
          "Auto-dormancy scheduled job completed: {} tenants processed, {} total customers transitioned",
          tenantsProcessed,
          totalTransitioned[0]);
    }

    jobEnqueuer.fanOutToAllTenants("dormancy_check", null);
  }

  int processTenant() {
    int transitioned = lifecycleService.executeDormancyTransitions();

    if (transitioned > 0) {
      notifyAdmins(transitioned);
    }

    return transitioned;
  }

  private void notifyAdmins(int transitioned) {
    try {
      var admins = memberRepository.findByRoleSlugsIn(List.of("admin", "owner"));
      String title =
          transitioned == 1
              ? "1 customer marked as dormant"
              : transitioned + " customers marked as dormant";
      String body =
          transitioned + " customer(s) were automatically marked as dormant due to inactivity.";

      for (Member admin : admins) {
        notificationService.createNotification(
            admin.getId(), "CUSTOMER_DORMANCY", title, body, null, null, null);
      }
    } catch (Exception e) {
      log.error("Auto-dormancy: failed to send admin notifications", e);
    }
  }
}
