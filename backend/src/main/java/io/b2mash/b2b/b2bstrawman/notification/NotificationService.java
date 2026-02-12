package io.b2mash.b2b.b2bstrawman.notification;

import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.DocumentUploadedEvent;
import io.b2mash.b2b.b2bstrawman.event.MemberAddedToProjectEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskAssignedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskClaimedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
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

  public NotificationService(
      NotificationRepository notificationRepository,
      NotificationPreferenceRepository notificationPreferenceRepository,
      CommentRepository commentRepository,
      TaskRepository taskRepository,
      DocumentRepository documentRepository,
      ProjectMemberRepository projectMemberRepository) {
    this.notificationRepository = notificationRepository;
    this.notificationPreferenceRepository = notificationPreferenceRepository;
    this.commentRepository = commentRepository;
    this.taskRepository = taskRepository;
    this.documentRepository = documentRepository;
    this.projectMemberRepository = projectMemberRepository;
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
            .findOneById(notificationId)
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
            .findOneById(notificationId)
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
          "MEMBER_INVITED");

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
  public void handleCommentCreated(CommentCreatedEvent event) {
    var recipients = new HashSet<UUID>();
    String title;

    if ("TASK".equals(event.targetEntityType())) {
      var taskOpt = taskRepository.findOneById(event.targetEntityId());
      if (taskOpt.isEmpty()) {
        log.warn("Task not found for comment notification: {}", event.targetEntityId());
        return;
      }
      var task = taskOpt.get();
      if (task.getAssigneeId() != null) {
        recipients.add(task.getAssigneeId());
      }
      title = "%s commented on task \"%s\"".formatted(event.actorName(), task.getTitle());
    } else if ("DOCUMENT".equals(event.targetEntityType())) {
      var docOpt = documentRepository.findOneById(event.targetEntityId());
      if (docOpt.isEmpty()) {
        log.warn("Document not found for comment notification: {}", event.targetEntityId());
        return;
      }
      var doc = docOpt.get();
      recipients.add(doc.getUploadedBy());
      title = "%s commented on document \"%s\"".formatted(event.actorName(), doc.getFileName());
    } else {
      log.warn("Unknown target entity type for comment: {}", event.targetEntityType());
      return;
    }

    // Add prior commenters on this entity
    var priorCommenters =
        commentRepository.findDistinctAuthorsByEntity(
            event.targetEntityType(), event.targetEntityId());
    recipients.addAll(priorCommenters);

    // Exclude the comment author
    recipients.remove(event.actorMemberId());

    for (var recipientId : recipients) {
      createIfEnabled(
          recipientId,
          "COMMENT_ADDED",
          title,
          null,
          event.targetEntityType(),
          event.targetEntityId(),
          event.projectId());
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleTaskAssigned(TaskAssignedEvent event) {
    if (event.assigneeMemberId() == null) {
      return;
    }
    // Do not notify the actor about their own action
    if (event.assigneeMemberId().equals(event.actorMemberId())) {
      return;
    }

    var title = "%s assigned you to task \"%s\"".formatted(event.actorName(), event.taskTitle());

    createIfEnabled(
        event.assigneeMemberId(),
        "TASK_ASSIGNED",
        title,
        null,
        "TASK",
        event.entityId(),
        event.projectId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleTaskClaimed(TaskClaimedEvent event) {
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

    for (var recipientId : recipients) {
      createIfEnabled(
          recipientId, "TASK_CLAIMED", title, null, "TASK", event.entityId(), event.projectId());
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleTaskStatusChanged(TaskStatusChangedEvent event) {
    if (event.assigneeMemberId() == null) {
      return;
    }
    // Do not notify the actor about their own action
    if (event.assigneeMemberId().equals(event.actorMemberId())) {
      return;
    }

    var title =
        "%s changed task \"%s\" to %s"
            .formatted(event.actorName(), event.taskTitle(), event.newStatus());

    createIfEnabled(
        event.assigneeMemberId(),
        "TASK_UPDATED",
        title,
        null,
        "TASK",
        event.entityId(),
        event.projectId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleDocumentUploaded(DocumentUploadedEvent event) {
    var members = projectMemberRepository.findByProjectId(event.projectId());

    var title = "%s uploaded \"%s\"".formatted(event.actorName(), event.documentName());

    for (var member : members) {
      // Exclude uploader
      if (member.getMemberId().equals(event.actorMemberId())) {
        continue;
      }
      createIfEnabled(
          member.getMemberId(),
          "DOCUMENT_SHARED",
          title,
          null,
          "DOCUMENT",
          event.entityId(),
          event.projectId());
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleMemberAddedToProject(MemberAddedToProjectEvent event) {
    var title = "You were added to project \"%s\"".formatted(event.projectName());

    createIfEnabled(
        event.addedMemberId(),
        "MEMBER_INVITED",
        title,
        null,
        "PROJECT",
        event.projectId(),
        event.projectId());
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

  /** Creates a notification for the recipient only if their in-app preference is enabled. */
  private void createIfEnabled(
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
      notificationRepository.save(notification);
    }
  }
}
