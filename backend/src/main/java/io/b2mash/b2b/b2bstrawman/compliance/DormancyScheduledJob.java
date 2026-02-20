package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import java.util.List;
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

  private final OrgSchemaMappingRepository mappingRepository;
  private final CustomerLifecycleService lifecycleService;
  private final NotificationService notificationService;
  private final MemberRepository memberRepository;

  public DormancyScheduledJob(
      OrgSchemaMappingRepository mappingRepository,
      CustomerLifecycleService lifecycleService,
      NotificationService notificationService,
      MemberRepository memberRepository) {
    this.mappingRepository = mappingRepository;
    this.lifecycleService = lifecycleService;
    this.notificationService = notificationService;
    this.memberRepository = memberRepository;
  }

  @Scheduled(cron = "0 0 2 * * *")
  public void executeDormancyCheck() {
    log.info("Auto-dormancy scheduled job started");
    var mappings = mappingRepository.findAll();
    int totalTransitioned = 0;

    for (var mapping : mappings) {
      try {
        int transitioned =
            ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
                .where(RequestScopes.ORG_ID, mapping.getClerkOrgId())
                .call(() -> processTenant());
        totalTransitioned += transitioned;
      } catch (Exception e) {
        log.error("Auto-dormancy: failed to process tenant schema {}", mapping.getSchemaName(), e);
      }
    }

    log.info(
        "Auto-dormancy scheduled job completed: {} tenants processed, {} total customers transitioned",
        mappings.size(),
        totalTransitioned);
  }

  private int processTenant() {
    int transitioned = lifecycleService.executeDormancyTransitions();

    if (transitioned > 0) {
      notifyAdmins(transitioned);
    }

    return transitioned;
  }

  private void notifyAdmins(int transitioned) {
    try {
      var admins = memberRepository.findByOrgRoleIn(List.of("admin", "owner"));
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
