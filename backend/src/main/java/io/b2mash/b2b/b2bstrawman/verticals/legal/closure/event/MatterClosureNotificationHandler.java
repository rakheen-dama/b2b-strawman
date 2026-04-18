package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.event;

import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fans out in-app notifications to org owners and admins when a matter is closed or reopened (Phase
 * 67, Epic 489B, task 489.14).
 *
 * <p>Uses {@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT} — the event
 * is published inside {@code MatterClosureService}'s transaction, but the listener must only fire
 * after that transaction successfully commits so we never deliver "Matter closed" to recipients
 * when the close itself rolled back. {@code NotificationService.notifyAdminsAndOwners} runs in
 * {@code REQUIRES_NEW}, so notification failures cannot affect the (already committed) close. The
 * listener executes on the publisher thread, which retains the {@code TENANT_ID} {@code
 * ScopedValue} binding required by {@code NotificationService} for schema routing.
 */
@Component
public class MatterClosureNotificationHandler {

  private static final Logger log = LoggerFactory.getLogger(MatterClosureNotificationHandler.class);

  private final NotificationService notificationService;

  public MatterClosureNotificationHandler(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMatterClosed(MatterClosedEvent event) {
    try {
      String title = "Matter closed" + (event.override() ? " (override used)" : "");
      String body =
          "Matter has been closed (reason: " + event.reason() + "). Retention clock started.";
      notificationService.notifyAdminsAndOwners(
          "MATTER_CLOSED", title, body, "project", event.projectId());
    } catch (Exception e) {
      log.warn("Failed to send MATTER_CLOSED notifications for project={}", event.projectId(), e);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMatterReopened(MatterReopenedEvent event) {
    try {
      // NOTE: retention soft-cancel is not yet persisted (TODO 489C). The reopen clears the
      // project's closed_at, but the canonical retention anchor (retentionClockStartedAt) and
      // the derived retention window are preserved — see MatterClosureService.reopen and
      // RetentionElapsedException. Messaging intentionally omits any "soft-cancel" claim.
      notificationService.notifyAdminsAndOwners(
          "MATTER_REOPENED",
          "Matter reopened",
          "Matter reopened. Retention window unchanged.",
          "project",
          event.projectId());
    } catch (Exception e) {
      log.warn("Failed to send MATTER_REOPENED notifications for project={}", event.projectId(), e);
    }
  }
}
