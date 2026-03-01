package io.b2mash.b2b.b2bstrawman.notification;

import io.b2mash.b2b.b2bstrawman.event.AcceptanceRequestAcceptedEvent;
import io.b2mash.b2b.b2bstrawman.event.BudgetThresholdEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.DocumentGeneratedEvent;
import io.b2mash.b2b.b2bstrawman.event.DocumentUploadedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceApprovedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceVoidedEvent;
import io.b2mash.b2b.b2bstrawman.event.MemberAddedToProjectEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectArchivedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProposalSentEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskAssignedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskCancelledEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskClaimedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskRecurrenceCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.channel.NotificationDispatcher;
import io.b2mash.b2b.b2bstrawman.schedule.event.RecurringProjectCreatedEvent;
import io.b2mash.b2b.b2bstrawman.schedule.event.ScheduleCompletedEvent;
import io.b2mash.b2b.b2bstrawman.schedule.event.ScheduleSkippedEvent;
import java.util.List;
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
 * <p>Each handler binds the tenant ScopedValue from the event's tenantId so that the dedicated
 * schema is selected correctly via {@code search_path} in the new transaction.
 */
@Component
public class NotificationEventHandler {

  private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

  private final NotificationService notificationService;
  private final NotificationDispatcher notificationDispatcher;

  public NotificationEventHandler(
      NotificationService notificationService, NotificationDispatcher notificationDispatcher) {
    this.notificationService = notificationService;
    this.notificationDispatcher = notificationDispatcher;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommentCreated(CommentCreatedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleCommentCreated(event);
            dispatchAll(notifications);
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
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleTaskAssigned(event);
            dispatchAll(notifications);
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
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleTaskClaimed(event);
            dispatchAll(notifications);
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
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleTaskStatusChanged(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for task.status_changed event={}",
                event.entityId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTaskCancelled(TaskCancelledEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleTaskCancelled(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for task.cancelled event={}", event.entityId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTaskRecurrenceCreated(TaskRecurrenceCreatedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleTaskRecurrenceCreated(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for task.recurrence_created event={}",
                event.entityId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDocumentUploaded(DocumentUploadedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleDocumentUploaded(event);
            dispatchAll(notifications);
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
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleMemberAddedToProject(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for project_member.added event={}",
                event.entityId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onBudgetThreshold(BudgetThresholdEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleBudgetThreshold(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for budget.threshold_reached event={}",
                event.entityId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDocumentGenerated(DocumentGeneratedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleDocumentGenerated(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for document.generated event={}",
                event.entityId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoiceApproved(InvoiceApprovedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleInvoiceApproved(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for invoice.approved event={}",
                event.entityId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoiceSent(InvoiceSentEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleInvoiceSent(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for invoice.sent event={}", event.entityId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoicePaid(InvoicePaidEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleInvoicePaid(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for invoice.paid event={}", event.entityId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoiceVoided(InvoiceVoidedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleInvoiceVoided(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for invoice.voided event={}", event.entityId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRecurringProjectCreated(RecurringProjectCreatedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleRecurringProjectCreated(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for recurring_project.created schedule={}",
                event.scheduleId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onScheduleSkipped(ScheduleSkippedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleScheduleSkipped(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for schedule_execution.skipped schedule={}",
                event.scheduleId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onScheduleCompleted(ScheduleCompletedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleScheduleCompleted(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for schedule.completed schedule={}",
                event.scheduleId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onAcceptanceRequestAccepted(AcceptanceRequestAcceptedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleAcceptanceRequestAccepted(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for acceptance_request.accepted event={}",
                event.entityId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProjectCompleted(ProjectCompletedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleProjectCompleted(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for project.completed event={}",
                event.entityId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProjectArchived(ProjectArchivedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var notifications = notificationService.handleProjectArchived(event);
            dispatchAll(notifications);
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for project.archived event={}",
                event.entityId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProposalSent(ProposalSentEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            String title =
                "Proposal %s has been sent"
                    .formatted(event.details().getOrDefault("proposal_number", ""));
            notificationService.notifyAdminsAndOwners(
                "PROPOSAL_SENT", title, null, "PROPOSAL", event.entityId());
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for proposal.sent event={}", event.entityId(), e);
          }
        });
  }

  /**
   * Dispatches all created notifications through multi-channel delivery. Email is passed as null
   * because email resolution is not yet implemented.
   */
  private void dispatchAll(List<Notification> notifications) {
    for (var notification : notifications) {
      notificationDispatcher.dispatch(notification, null);
    }
  }

  /**
   * Binds tenant and org ScopedValues so that the correct dedicated schema is selected via {@code
   * search_path} in the handler's new transaction.
   */
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
