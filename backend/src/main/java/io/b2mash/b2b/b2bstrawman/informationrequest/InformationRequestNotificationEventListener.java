package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.event.InformationRequestCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.InformationRequestDraftCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.RequestItemSubmittedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.Notification;
import io.b2mash.b2b.b2bstrawman.notification.channel.NotificationDispatcher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InformationRequestNotificationEventListener {

  private static final Logger log =
      LoggerFactory.getLogger(InformationRequestNotificationEventListener.class);

  private final NotificationDispatcher notificationDispatcher;
  private final InformationRequestNotificationHelper notificationHelper;

  public InformationRequestNotificationEventListener(
      NotificationDispatcher notificationDispatcher,
      InformationRequestNotificationHelper notificationHelper) {
    this.notificationDispatcher = notificationDispatcher;
    this.notificationHelper = notificationHelper;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onItemSubmitted(RequestItemSubmittedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationHelper.handleItemSubmitted(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notification for item_submitted event={}", event.entityId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRequestCompleted(InformationRequestCompletedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationHelper.handleRequestCompleted(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notification for request_completed event={}",
                event.entityId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDraftCreated(InformationRequestDraftCreatedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationHelper.handleDraftCreated(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notification for draft_created event={}", event.entityId(), e);
          }
        });
  }

  private void dispatchAll(List<Notification> notifications) {
    for (var notification : notifications) {
      notificationDispatcher.dispatch(notification, null);
    }
  }

  private void handleInTenantScope(String tenantId, String orgId, Runnable action) {
    if (tenantId != null) {
      var carrier = ScopedValue.where(RequestScopes.TENANT_ID, tenantId);
      if (orgId != null) {
        carrier = carrier.where(RequestScopes.ORG_ID, orgId);
      }
      carrier.run(action);
    } else {
      action.run();
    }
  }
}
