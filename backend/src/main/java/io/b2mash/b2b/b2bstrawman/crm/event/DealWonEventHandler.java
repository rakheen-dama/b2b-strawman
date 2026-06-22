package io.b2mash.b2b.b2bstrawman.crm.event;

import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.notification.channel.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Post-commit side effects for {@link DealWonEvent} (Phase 80, slice 575A). The DEAL_WON
 * notification to the deal owner runs after the transition transaction commits, re-binding the
 * tenant scope (AFTER_COMMIT listeners run outside the request scope). Failures are logged and
 * swallowed — a notification problem must not affect the already-committed transition.
 */
@Component
public class DealWonEventHandler {

  private static final Logger log = LoggerFactory.getLogger(DealWonEventHandler.class);

  private final NotificationService notificationService;
  private final NotificationDispatcher notificationDispatcher;
  private final MemberRepository memberRepository;

  public DealWonEventHandler(
      NotificationService notificationService,
      NotificationDispatcher notificationDispatcher,
      MemberRepository memberRepository) {
    this.notificationService = notificationService;
    this.notificationDispatcher = notificationDispatcher;
    this.memberRepository = memberRepository;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDealWon(DealWonEvent event) {
    RequestScopes.runForTenantOnShard(
        event.tenantId(),
        event.orgId(),
        event.shardId(),
        () -> {
          try {
            var notification =
                notificationService.createNotification(
                    event.ownerId(),
                    "DEAL_WON",
                    "You won a deal",
                    "A deal you own has been marked as won.",
                    "DEAL",
                    event.dealId(),
                    null);
            String recipientEmail =
                memberRepository.findById(event.ownerId()).map(Member::getEmail).orElse(null);
            notificationDispatcher.dispatch(notification, recipientEmail);
            log.info("Post-commit DEAL_WON notification sent for deal {}", event.dealId());
          } catch (Exception e) {
            log.error("Failed to send DEAL_WON notification for deal {}", event.dealId(), e);
          }
        });
  }
}
