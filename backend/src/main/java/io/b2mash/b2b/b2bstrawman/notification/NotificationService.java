package io.b2mash.b2b.b2bstrawman.notification;

import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.BudgetThresholdEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.DocumentGeneratedEvent;
import io.b2mash.b2b.b2bstrawman.event.DocumentUploadedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceApprovedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceVoidedEvent;
import io.b2mash.b2b.b2bstrawman.event.MemberAddedToProjectEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskAssignedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskClaimedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.schedule.event.RecurringProjectCreatedEvent;
import io.b2mash.b2b.b2bstrawman.schedule.event.ScheduleCompletedEvent;
import io.b2mash.b2b.b2bstrawman.schedule.event.ScheduleSkippedEvent;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  private final NotificationRepository notificationRepository;
  private final NotificationPreferenceRepository notificationPreferenceRepository;
  private final CommentRepository commentRepository;
  private final TaskRepository taskRepository;
  private final DocumentRepository documentRepository;
  private final ProjectMemberRepository projectMemberRepository;
  private final MemberRepository memberRepository;

  public NotificationService(
      NotificationRepository notificationRepository,
      NotificationPreferenceRepository notificationPreferenceRepository,
      CommentRepository commentRepository,
      TaskRepository taskRepository,
      DocumentRepository documentRepository,
      ProjectMemberRepository projectMemberRepository,
      MemberRepository memberRepository) {
    this.notificationRepository = notificationRepository;
    this.notificationPreferenceRepository = notificationPreferenceRepository;
    this.commentRepository = commentRepository;
    this.taskRepository = taskRepository;
    this.documentRepository = documentRepository;
    this.projectMemberRepository = projectMemberRepository;
    this.memberRepository = memberRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Notification createNotification(
      UUID recipientMemberId,
      String type,
      String title,
      String body,
      String refEntityType,
      UUID refEntityId,
      UUID refProjectId) {
    var notification =
        new Notification(
            recipientMemberId, type, title, body, refEntityType, refEntityId, refProjectId);
    return notificationRepository.save(notification);
  }

  @Transactional(readOnly = true)
  public Page<Notification> listNotifications(
      UUID memberId, boolean unreadOnly, Pageable pageable) {
    if (unreadOnly) {
      return notificationRepository.findUnreadByRecipientMemberId(memberId, pageable);
    }
    return notificationRepository.findByRecipientMemberId(memberId, pageable);
  }

  @Transactional(readOnly = true)
  public long getUnreadCount(UUID memberId) {
    return notificationRepository.countUnreadByRecipientMemberId(memberId);
  }

  @Transactional
  public void markAsRead(UUID notificationId, UUID memberId) {
    var notification =
        notificationRepository
            .findById(notificationId)
            .filter(n -> n.getRecipientMemberId().equals(memberId))
            .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
    notification.markAsRead();
  }

  @Transactional
  public void markAllAsRead(UUID memberId) {
    notificationRepository.markAllAsRead(memberId);
  }

  @Transactional
  public void dismissNotification(UUID notificationId, UUID memberId) {
    var notification =
        notificationRepository
            .findById(notificationId)
            .filter(n -> n.getRecipientMemberId().equals(memberId))
            .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
    notificationRepository.delete(notification);
  }

  // --- Notification types ---

  public static final List<String> NOTIFICATION_TYPES =
      List.of(
          "TASK_ASSIGNED",
          "TASK_CLAIMED",
          "TASK_UPDATED",
          "COMMENT_ADDED",
          "DOCUMENT_SHARED",
          "MEMBER_INVITED",
          "BUDGET_ALERT",
          "INVOICE_APPROVED",
          "INVOICE_SENT",
          "INVOICE_PAID",
          "INVOICE_VOIDED",
          "DOCUMENT_GENERATED",
          "RECURRING_PROJECT_CREATED",
          "SCHEDULE_SKIPPED",
          "SCHEDULE_COMPLETED",
          "RETAINER_PERIOD_READY_TO_CLOSE",
          "RETAINER_PERIOD_CLOSED",
          "RETAINER_APPROACHING_CAPACITY",
          "RETAINER_FULLY_CONSUMED",
          "RETAINER_TERMINATED",
          "PAYMENT_FAILED",
          "PAYMENT_LINK_EXPIRED");

  // --- Preference methods ---

  /** Returns preferences for all notification types, merging stored rows with defaults. */
  @Transactional(readOnly = true)
  public List<PreferenceView> getPreferences(UUID memberId) {
    Map<String, NotificationPreference> stored =
        notificationPreferenceRepository.findByMemberId(memberId).stream()
            .collect(
                Collectors.toMap(NotificationPreference::getNotificationType, Function.identity()));

    return NOTIFICATION_TYPES.stream()
        .map(
            type -> {
              var pref = stored.get(type);
              if (pref != null) {
                return new PreferenceView(type, pref.isInAppEnabled(), pref.isEmailEnabled());
              }
              // Default: inAppEnabled=true, emailEnabled=false
              return new PreferenceView(type, true, false);
            })
        .toList();
  }

  /** Upserts preferences for the given types and returns the full merged preference list. */
  @Transactional
  public List<PreferenceView> updatePreferences(UUID memberId, List<PreferenceUpdate> updates) {
    for (var update : updates) {
      var existing =
          notificationPreferenceRepository.findByMemberIdAndNotificationType(
              memberId, update.notificationType());
      if (existing.isPresent()) {
        var pref = existing.get();
        pref.setInAppEnabled(update.inAppEnabled());
        pref.setEmailEnabled(update.emailEnabled());
      } else {
        notificationPreferenceRepository.save(
            new NotificationPreference(
                memberId, update.notificationType(), update.inAppEnabled(), update.emailEnabled()));
      }
    }
    return getPreferences(memberId);
  }

  /** Read-only view of a notification preference. */
  public record PreferenceView(
      String notificationType, boolean inAppEnabled, boolean emailEnabled) {}

  /** Input record for updating a single preference. */
  public record PreferenceUpdate(
      String notificationType, boolean inAppEnabled, boolean emailEnabled) {}

  // --- Fan-out handler methods (called by NotificationEventHandler) ---

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleCommentCreated(CommentCreatedEvent event) {
    var recipients = new HashSet<UUID>();
    String title;

    if ("TASK".equals(event.targetEntityType())) {
      var taskOpt = taskRepository.findById(event.targetEntityId());
      if (taskOpt.isEmpty()) {
        log.warn("Task not found for comment notification: {}", event.targetEntityId());
        return List.of();
      }
      var task = taskOpt.get();
      if (task.getAssigneeId() != null) {
        recipients.add(task.getAssigneeId());
      }
      title = "%s commented on task \"%s\"".formatted(event.actorName(), task.getTitle());
    } else if ("DOCUMENT".equals(event.targetEntityType())) {
      var docOpt = documentRepository.findById(event.targetEntityId());
      if (docOpt.isEmpty()) {
        log.warn("Document not found for comment notification: {}", event.targetEntityId());
        return List.of();
      }
      var doc = docOpt.get();
      recipients.add(doc.getUploadedBy());
      title = "%s commented on document \"%s\"".formatted(event.actorName(), doc.getFileName());
    } else {
      log.warn("Unknown target entity type for comment: {}", event.targetEntityType());
      return List.of();
    }

    // Add prior commenters on this entity
    var priorCommenters =
        commentRepository.findDistinctAuthorsByEntity(
            event.targetEntityType(), event.targetEntityId());
    recipients.addAll(priorCommenters);

    // Exclude the comment author
    recipients.remove(event.actorMemberId());

    var created = new ArrayList<Notification>();
    for (var recipientId : recipients) {
      var notification =
          createIfEnabled(
              recipientId,
              "COMMENT_ADDED",
              title,
              null,
              event.targetEntityType(),
              event.targetEntityId(),
              event.projectId());
      if (notification != null) {
        created.add(notification);
      }
    }
    return created;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleTaskAssigned(TaskAssignedEvent event) {
    if (event.assigneeMemberId() == null) {
      return List.of();
    }
    // Do not notify the actor about their own action
    if (event.assigneeMemberId().equals(event.actorMemberId())) {
      return List.of();
    }

    var title = "%s assigned you to task \"%s\"".formatted(event.actorName(), event.taskTitle());

    var notification =
        createIfEnabled(
            event.assigneeMemberId(),
            "TASK_ASSIGNED",
            title,
            null,
            "TASK",
            event.entityId(),
            event.projectId());
    return notification != null ? List.of(notification) : List.of();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleTaskClaimed(TaskClaimedEvent event) {
    var recipients = new HashSet<UUID>();

    // Previous assignee (if any)
    if (event.previousAssigneeId() != null) {
      recipients.add(event.previousAssigneeId());
    }

    // Project leads
    var leads = projectMemberRepository.findByProjectIdAndProjectRole(event.projectId(), "LEAD");
    for (var lead : leads) {
      recipients.add(lead.getMemberId());
    }

    // Exclude actor
    recipients.remove(event.actorMemberId());

    var title = "%s claimed task \"%s\"".formatted(event.actorName(), event.taskTitle());

    var created = new ArrayList<Notification>();
    for (var recipientId : recipients) {
      var notification =
          createIfEnabled(
              recipientId,
              "TASK_CLAIMED",
              title,
              null,
              "TASK",
              event.entityId(),
              event.projectId());
      if (notification != null) {
        created.add(notification);
      }
    }
    return created;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleTaskStatusChanged(TaskStatusChangedEvent event) {
    if (event.assigneeMemberId() == null) {
      return List.of();
    }
    // Do not notify the actor about their own action
    if (event.assigneeMemberId().equals(event.actorMemberId())) {
      return List.of();
    }

    var title =
        "%s changed task \"%s\" to %s"
            .formatted(event.actorName(), event.taskTitle(), event.newStatus());

    var notification =
        createIfEnabled(
            event.assigneeMemberId(),
            "TASK_UPDATED",
            title,
            null,
            "TASK",
            event.entityId(),
            event.projectId());
    return notification != null ? List.of(notification) : List.of();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleDocumentUploaded(DocumentUploadedEvent event) {
    var members = projectMemberRepository.findByProjectId(event.projectId());

    var title = "%s uploaded \"%s\"".formatted(event.actorName(), event.documentName());

    var created = new ArrayList<Notification>();
    for (var member : members) {
      // Exclude uploader
      if (member.getMemberId().equals(event.actorMemberId())) {
        continue;
      }
      var notification =
          createIfEnabled(
              member.getMemberId(),
              "DOCUMENT_SHARED",
              title,
              null,
              "DOCUMENT",
              event.entityId(),
              event.projectId());
      if (notification != null) {
        created.add(notification);
      }
    }
    return created;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleMemberAddedToProject(MemberAddedToProjectEvent event) {
    var title = "You were added to project \"%s\"".formatted(event.projectName());

    var notification =
        createIfEnabled(
            event.addedMemberId(),
            "MEMBER_INVITED",
            title,
            null,
            "PROJECT",
            event.projectId(),
            event.projectId());
    return notification != null ? List.of(notification) : List.of();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleBudgetThreshold(BudgetThresholdEvent event) {
    var recipients = new HashSet<UUID>();

    // Project leads
    var leads = projectMemberRepository.findByProjectIdAndProjectRole(event.projectId(), "LEAD");
    for (var lead : leads) {
      recipients.add(lead.getMemberId());
    }

    // Org admins and owners
    var adminsAndOwners = memberRepository.findByOrgRoleIn(List.of("admin", "owner"));
    for (var member : adminsAndOwners) {
      recipients.add(member.getId());
    }

    // Exclude actor
    recipients.remove(event.actorMemberId());

    var projectName = (String) event.details().get("project_name");
    var dimension = (String) event.details().get("dimension");
    var consumedPct = event.details().get("consumed_pct");

    var title =
        "Project \"%s\" has reached %s%% of its %s budget"
            .formatted(projectName, consumedPct, dimension);

    var created = new ArrayList<Notification>();
    for (var recipientId : recipients) {
      var notification =
          createIfEnabled(
              recipientId,
              "BUDGET_ALERT",
              title,
              null,
              "PROJECT",
              event.projectId(),
              event.projectId());
      if (notification != null) {
        created.add(notification);
      }
    }
    return created;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleInvoiceApproved(InvoiceApprovedEvent event) {
    // Recipients: invoice creator (if different from approver)
    var recipients = new HashSet<UUID>();
    if (event.createdByMemberId() != null) recipients.add(event.createdByMemberId());
    recipients.remove(event.actorMemberId());
    var title =
        "Invoice %s for %s has been approved"
            .formatted(event.invoiceNumber(), event.customerName());
    return createNotificationsForRecipients(
        recipients, "INVOICE_APPROVED", title, event.entityId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleInvoiceSent(InvoiceSentEvent event) {
    // Recipients: org admins + owners
    var recipients = new HashSet<UUID>();
    for (var m : memberRepository.findByOrgRoleIn(List.of("admin", "owner"))) {
      recipients.add(m.getId());
    }
    recipients.remove(event.actorMemberId());
    var title =
        "Invoice %s for %s has been sent".formatted(event.invoiceNumber(), event.customerName());
    return createNotificationsForRecipients(recipients, "INVOICE_SENT", title, event.entityId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleInvoicePaid(InvoicePaidEvent event) {
    // Recipients: creator + admins/owners
    var recipients = new HashSet<UUID>();
    if (event.createdByMemberId() != null) recipients.add(event.createdByMemberId());
    for (var m : memberRepository.findByOrgRoleIn(List.of("admin", "owner"))) {
      recipients.add(m.getId());
    }
    recipients.remove(event.actorMemberId());
    var title =
        "Invoice %s for %s has been paid".formatted(event.invoiceNumber(), event.customerName());
    return createNotificationsForRecipients(recipients, "INVOICE_PAID", title, event.entityId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleInvoiceVoided(InvoiceVoidedEvent event) {
    // Recipients: creator + approver + admins/owners
    var recipients = new HashSet<UUID>();
    if (event.createdByMemberId() != null) recipients.add(event.createdByMemberId());
    if (event.approvedByMemberId() != null) recipients.add(event.approvedByMemberId());
    for (var m : memberRepository.findByOrgRoleIn(List.of("admin", "owner"))) {
      recipients.add(m.getId());
    }
    recipients.remove(event.actorMemberId());
    var title =
        "Invoice %s for %s has been voided".formatted(event.invoiceNumber(), event.customerName());
    return createNotificationsForRecipients(recipients, "INVOICE_VOIDED", title, event.entityId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleDocumentGenerated(DocumentGeneratedEvent event) {
    var recipients = new HashSet<UUID>();

    if (event.primaryEntityType() == TemplateEntityType.PROJECT && event.projectId() != null) {
      // Notify project leads
      var leads = projectMemberRepository.findByProjectIdAndProjectRole(event.projectId(), "LEAD");
      for (var lead : leads) {
        recipients.add(lead.getMemberId());
      }
    } else {
      // CUSTOMER or INVOICE-scoped: notify org admins/owners
      var adminsAndOwners = memberRepository.findByOrgRoleIn(List.of("admin", "owner"));
      for (var member : adminsAndOwners) {
        recipients.add(member.getId());
      }
    }

    // Exclude the actor
    recipients.remove(event.actorMemberId());

    if (recipients.isEmpty()) {
      log.warn(
          "No recipients found for DOCUMENT_GENERATED notification: entity={}", event.entityId());
      return List.of();
    }

    var title = "%s generated document \"%s\"".formatted(event.actorName(), event.fileName());

    var created = new ArrayList<Notification>();
    for (var recipientId : recipients) {
      var notification =
          createIfEnabled(
              recipientId,
              "DOCUMENT_GENERATED",
              title,
              null,
              "GENERATED_DOCUMENT",
              event.entityId(),
              event.projectId());
      if (notification != null) {
        created.add(notification);
      }
    }
    return created;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleRecurringProjectCreated(RecurringProjectCreatedEvent event) {
    var recipients = new HashSet<UUID>();

    // Notify project lead if set
    if (event.projectLeadMemberId() != null) {
      recipients.add(event.projectLeadMemberId());
    }

    // Notify org admins and owners
    for (var m : memberRepository.findByOrgRoleIn(List.of("admin", "owner"))) {
      recipients.add(m.getId());
    }

    // Exclude actor (null for scheduler — Set.remove(null) is safe)
    recipients.remove(event.actorMemberId());

    var title =
        "Recurring project \"%s\" created for %s"
            .formatted(event.projectName(), event.customerName());

    var created = new ArrayList<Notification>();
    for (var recipientId : recipients) {
      var notification =
          createIfEnabled(
              recipientId,
              "RECURRING_PROJECT_CREATED",
              title,
              null,
              "PROJECT",
              event.projectId(),
              event.projectId());
      if (notification != null) {
        created.add(notification);
      }
    }
    return created;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleScheduleSkipped(ScheduleSkippedEvent event) {
    var recipients = new HashSet<UUID>();

    // Notify org admins and owners
    for (var m : memberRepository.findByOrgRoleIn(List.of("admin", "owner"))) {
      recipients.add(m.getId());
    }

    var title = "Schedule skipped for %s — %s".formatted(event.customerName(), event.reason());

    var created = new ArrayList<Notification>();
    for (var recipientId : recipients) {
      var notification =
          createIfEnabled(
              recipientId,
              "SCHEDULE_SKIPPED",
              title,
              null,
              "RECURRING_SCHEDULE",
              event.scheduleId(),
              null);
      if (notification != null) {
        created.add(notification);
      }
    }
    return created;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleScheduleCompleted(ScheduleCompletedEvent event) {
    var recipients = new HashSet<UUID>();

    // Notify org admins and owners
    for (var m : memberRepository.findByOrgRoleIn(List.of("admin", "owner"))) {
      recipients.add(m.getId());
    }

    var title =
        "Schedule for %s (%s) completed after %d executions"
            .formatted(event.customerName(), event.templateName(), event.executionCount());

    var created = new ArrayList<Notification>();
    for (var recipientId : recipients) {
      var notification =
          createIfEnabled(
              recipientId,
              "SCHEDULE_COMPLETED",
              title,
              null,
              "RECURRING_SCHEDULE",
              event.scheduleId(),
              null);
      if (notification != null) {
        created.add(notification);
      }
    }
    return created;
  }

  // --- Admin/owner fan-out helper (used by retainer services) ---

  /**
   * Creates a notification for all org admins and owners. Used by retainer services for
   * agreement-level notifications.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void notifyAdminsAndOwners(
      String type, String title, String body, String entityType, UUID entityId) {
    var adminsAndOwners = memberRepository.findByOrgRoleIn(List.of("admin", "owner"));
    for (var member : adminsAndOwners) {
      createNotification(member.getId(), type, title, body, entityType, entityId, null);
    }
  }

  // --- Private helpers ---

  /**
   * Checks if in-app notifications are enabled for a recipient and notification type. Opt-out
   * model: no preference row means notifications are enabled by default.
   */
  private boolean isInAppEnabled(UUID recipientMemberId, String notificationType) {
    return notificationPreferenceRepository
        .findByMemberIdAndNotificationType(recipientMemberId, notificationType)
        .map(NotificationPreference::isInAppEnabled)
        .orElse(true);
  }

  /**
   * Creates a notification for the recipient only if their in-app preference is enabled.
   *
   * @return the saved notification, or {@code null} if the preference was disabled
   */
  private List<Notification> createNotificationsForRecipients(
      java.util.Set<UUID> recipients, String type, String title, UUID entityId) {
    var created = new ArrayList<Notification>();
    for (var recipientId : recipients) {
      var notification = createIfEnabled(recipientId, type, title, null, "INVOICE", entityId, null);
      if (notification != null) created.add(notification);
    }
    return created;
  }

  Notification createIfEnabled(
      UUID recipientMemberId,
      String notificationType,
      String title,
      String body,
      String refEntityType,
      UUID refEntityId,
      UUID refProjectId) {
    if (isInAppEnabled(recipientMemberId, notificationType)) {
      var notification =
          new Notification(
              recipientMemberId,
              notificationType,
              title,
              body,
              refEntityType,
              refEntityId,
              refProjectId);
      return notificationRepository.save(notification);
    }
    return null;
  }
}
