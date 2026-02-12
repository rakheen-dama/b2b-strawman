package io.b2mash.b2b.b2bstrawman.notification;

import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.DocumentUploadedEvent;
import io.b2mash.b2b.b2bstrawman.event.MemberAddedToProjectEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskAssignedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskClaimedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to domain events and creates notifications for relevant recipients. All handler methods
 * run AFTER_COMMIT in a new transaction, ensuring:
 *
 * <ol>
 *   <li>Notifications are only created for committed domain changes.
 *   <li>Notification failures do not affect the domain transaction.
 * </ol>
 *
 * <p>Each handler binds the tenant ScopedValue from the event's tenantId, enabling Hibernate
 * {@code @Filter} and RLS to work correctly in the new transaction.
 */
@Component
public class NotificationEventHandler {

  private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

  private final NotificationService notificationService;

  public NotificationEventHandler(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommentCreated(CommentCreatedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        () -> {
          try {
            notificationService.handleCommentCreated(event);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for comment.created event={}", event.entityId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTaskAssigned(TaskAssignedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        () -> {
          try {
            notificationService.handleTaskAssigned(event);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for task.assigned event={}", event.entityId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTaskClaimed(TaskClaimedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        () -> {
          try {
            notificationService.handleTaskClaimed(event);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for task.claimed event={}", event.entityId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTaskStatusChanged(TaskStatusChangedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        () -> {
          try {
            notificationService.handleTaskStatusChanged(event);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for task.status_changed event={}",
                event.entityId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDocumentUploaded(DocumentUploadedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        () -> {
          try {
            notificationService.handleDocumentUploaded(event);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for document.uploaded event={}",
                event.entityId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMemberAddedToProject(MemberAddedToProjectEvent event) {
    handleInTenantScope(
        event.tenantId(),
        () -> {
          try {
            notificationService.handleMemberAddedToProject(event);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for project_member.added event={}",
                event.entityId(),
                e);
          }
        });
  }

  /**
   * Binds the tenant ScopedValue so that Hibernate {@code @Filter} and RLS work correctly in the
   * handler's new transaction.
   */
  private void handleInTenantScope(String tenantId, Runnable action) {
    if (tenantId != null) {
      ScopedValue.where(RequestScopes.TENANT_ID, tenantId).run(action);
    } else {
      action.run();
    }
  }
}
