package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event;

import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.Notification;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.notification.channel.NotificationDispatcher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for trust transaction approval events and creates notifications for relevant recipients.
 * Runs AFTER_COMMIT in a new transaction to ensure notifications are only created for committed
 * changes and notification failures do not affect the domain transaction.
 */
@Component
public class TrustNotificationHandler {

  private static final Logger log = LoggerFactory.getLogger(TrustNotificationHandler.class);

  private final NotificationService notificationService;
  private final NotificationDispatcher notificationDispatcher;
  private final MemberRepository memberRepository;

  public TrustNotificationHandler(
      NotificationService notificationService,
      NotificationDispatcher notificationDispatcher,
      MemberRepository memberRepository) {
    this.notificationService = notificationService;
    this.notificationDispatcher = notificationDispatcher;
    this.memberRepository = memberRepository;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTrustTransactionApprovalEvent(TrustTransactionApprovalEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            switch (event.eventType()) {
              case "trust_transaction.awaiting_approval" -> handleAwaitingApproval(event);
              case "trust_transaction.approved" -> handleApproved(event);
              case "trust_transaction.rejected" -> handleRejected(event);
              default -> log.warn("Unknown trust transaction event type: {}", event.eventType());
            }
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for trust transaction event={} txn={}",
                event.eventType(),
                event.transactionId(),
                e);
          }
        });
  }

  private void handleAwaitingApproval(TrustTransactionApprovalEvent event) {
    // Notify members with APPROVE_TRUST_PAYMENT capability (owners)
    var notifications =
        notificationService.notifyAdminsAndOwners(
            "trust_transaction.awaiting_approval",
            "Trust " + formatTransactionType(event.transactionType()) + " requires approval",
            "A "
                + formatTransactionType(event.transactionType())
                + " of R"
                + event.amount()
                + " requires your approval.",
            "trust_transaction",
            event.transactionId());

    dispatchAll(notifications);
  }

  private void handleApproved(TrustTransactionApprovalEvent event) {
    // Notify the recorder that their transaction was approved
    var notification =
        notificationService.createNotification(
            event.recordedBy(),
            "trust_transaction.approved",
            "Trust " + formatTransactionType(event.transactionType()) + " approved",
            "Your "
                + formatTransactionType(event.transactionType())
                + " of R"
                + event.amount()
                + " has been approved.",
            "trust_transaction",
            event.transactionId(),
            null);

    dispatchAll(List.of(notification));
  }

  private void handleRejected(TrustTransactionApprovalEvent event) {
    // Notify the recorder that their transaction was rejected
    var notification =
        notificationService.createNotification(
            event.recordedBy(),
            "trust_transaction.rejected",
            "Trust " + formatTransactionType(event.transactionType()) + " rejected",
            "Your "
                + formatTransactionType(event.transactionType())
                + " of R"
                + event.amount()
                + " has been rejected.",
            "trust_transaction",
            event.transactionId(),
            null);

    dispatchAll(List.of(notification));
  }

  private String formatTransactionType(String transactionType) {
    return transactionType.toLowerCase().replace('_', ' ');
  }

  private void dispatchAll(List<Notification> notifications) {
    for (var notification : notifications) {
      String recipientEmail =
          memberRepository
              .findById(notification.getRecipientMemberId())
              .map(Member::getEmail)
              .orElse(null);
      if (recipientEmail == null) {
        log.debug(
            "No email found for member {} — in-app notification will still be dispatched",
            notification.getRecipientMemberId());
      }
      notificationDispatcher.dispatch(notification, recipientEmail);
    }
  }

  /**
   * Runs the action within a tenant scope. Only binds TENANT_ID and ORG_ID intentionally —
   * notification handlers run outside of a member/request context, so MEMBER_ID, ORG_ROLE, and
   * CAPABILITIES are not bound. The handler only needs tenant scope for database routing.
   */
  private void handleInTenantScope(String tenantId, String orgId, Runnable action) {
    if (tenantId == null) {
      // Trust accounting events should always have a tenant context.
      // Without one, queries would hit the public schema — skip rather than corrupt.
      log.warn("Trust notification event received without tenantId — skipping notification");
      return;
    }

    var carrier = ScopedValue.where(RequestScopes.TENANT_ID, tenantId);
    if (orgId != null) {
      carrier = carrier.where(RequestScopes.ORG_ID, orgId);
    }
    carrier.run(action);
  }
}
