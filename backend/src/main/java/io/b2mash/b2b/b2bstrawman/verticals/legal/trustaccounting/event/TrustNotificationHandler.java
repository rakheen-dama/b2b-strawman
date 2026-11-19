package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event;

import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.Notification;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.notification.channel.NotificationDispatcher;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionTypeLabels;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for trust accounting domain events and creates notifications for relevant recipients.
 * Runs AFTER_COMMIT in a new transaction to ensure notifications are only created for committed
 * changes and notification failures do not affect the domain transaction.
 *
 * <p>Handles 5 notification flows:
 *
 * <ul>
 *   <li>Transaction awaiting approval -> notify admins/owners
 *   <li>Transaction approved -> notify recorder
 *   <li>Transaction rejected -> notify recorder
 *   <li>Reconciliation completed -> notify admins/owners
 *   <li>Investment maturing -> notify admins/owners
 * </ul>
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
    RequestScopes.runForTenant(
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

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReconciliationCompleted(TrustDomainEvent.ReconciliationCompleted event) {
    RequestScopes.runForTenant(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            handleReconciliationCompleted(event);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for reconciliation.completed reconciliation={}",
                event.reconciliationId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvestmentMaturing(TrustDomainEvent.InvestmentMaturing event) {
    RequestScopes.runForTenant(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            handleInvestmentMaturing(event);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for investment.maturing investment={}",
                event.investmentId(),
                e);
          }
        });
  }

  private void handleAwaitingApproval(TrustTransactionApprovalEvent event) {
    // Notify members with APPROVE_TRUST_PAYMENT capability (owners and admins)
    var notifications =
        notificationService.notifyAdminsAndOwners(
            "TRUST_PAYMENT_AWAITING_APPROVAL",
            "Trust " + formatTransactionType(event.transactionType()) + " requires approval",
            withArticle(formatTransactionType(event.transactionType()))
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
            "TRUST_PAYMENT_APPROVED",
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
            "TRUST_PAYMENT_REJECTED",
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

  private void handleReconciliationCompleted(TrustDomainEvent.ReconciliationCompleted event) {
    var notifications =
        notificationService.notifyAdminsAndOwners(
            "TRUST_RECONCILIATION_COMPLETED",
            "Trust account reconciliation completed",
            "Reconciliation for period ending "
                + event.periodEnd()
                + " has been completed successfully.",
            "trust_reconciliation",
            event.reconciliationId());

    dispatchAll(notifications);
  }

  private void handleInvestmentMaturing(TrustDomainEvent.InvestmentMaturing event) {
    var notifications =
        notificationService.notifyAdminsAndOwners(
            "TRUST_INVESTMENT_MATURING",
            "Trust investment maturing in " + event.daysUntilMaturity() + " days",
            "Investment of R"
                + event.principal()
                + " at "
                + event.institution()
                + " matures on "
                + event.maturityDate()
                + ".",
            "trust_investment",
            event.investmentId());

    dispatchAll(notifications);
  }

  /**
   * The type's firm label, decapitalised for the middle of a title (647D): "payment", "fee
   * transfer", "refund", "reversal" as before, and "unidentified deposit allocated" / "unidentified
   * deposit paid to the Fund" instead of a raw code. An investment placement reads its noun ({@link
   * TrustTransactionTypeLabels#placementNoun}, 648A): "placement in a section 86(4) investment",
   * not "placed in section 86(4) investment".
   */
  static String formatTransactionType(String transactionType) {
    String placement = TrustTransactionTypeLabels.placementNoun(transactionType);
    if (placement != null) {
      return placement;
    }
    String label = TrustTransactionTypeLabels.firmLabel(transactionType);
    if (label == null || label.isEmpty()) {
      return "transaction";
    }
    return Character.toLowerCase(label.charAt(0)) + label.substring(1);
  }

  /** "A payment", "An unidentified deposit allocated" — sentence-initial article. */
  static String withArticle(String noun) {
    return ("aeiou".indexOf(noun.charAt(0)) >= 0 ? "An " : "A ") + noun;
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
}
