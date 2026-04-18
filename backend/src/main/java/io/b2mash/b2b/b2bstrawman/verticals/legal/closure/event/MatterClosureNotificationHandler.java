package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.event;

import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Fans out in-app notifications to org owners and admins when a matter is closed or reopened (Phase
 * 67, Epic 489B, task 489.14).
 *
 * <p>Uses plain {@link EventListener} (synchronous, publisher's transaction/thread) — the event is
 * published inside {@code MatterClosureService}'s transaction, which still holds the {@code
 * TENANT_ID} {@code ScopedValue} binding needed by {@code NotificationService} to route to the
 * correct schema. Failures inside the handler are swallowed and logged; notification failure must
 * never roll back the closure transaction.
 */
@Component
public class MatterClosureNotificationHandler {

  private static final Logger log = LoggerFactory.getLogger(MatterClosureNotificationHandler.class);

  private final NotificationService notificationService;

  public MatterClosureNotificationHandler(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @EventListener
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

  @EventListener
  public void onMatterReopened(MatterReopenedEvent event) {
    try {
      notificationService.notifyAdminsAndOwners(
          "MATTER_REOPENED",
          "Matter reopened",
          "Matter reopened. Retention window soft-cancelled.",
          "project",
          event.projectId());
    } catch (Exception e) {
      log.warn("Failed to send MATTER_REOPENED notifications for project={}", event.projectId(), e);
    }
  }
}
