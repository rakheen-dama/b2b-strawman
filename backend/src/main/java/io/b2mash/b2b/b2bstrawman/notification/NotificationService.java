package io.b2mash.b2b.b2bstrawman.notification;

import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.DocumentUploadedEvent;
import io.b2mash.b2b.b2bstrawman.event.MemberAddedToProjectEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskAssignedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskClaimedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.UUID;
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

  public NotificationService(
      NotificationRepository notificationRepository,
      NotificationPreferenceRepository notificationPreferenceRepository) {
    this.notificationRepository = notificationRepository;
    this.notificationPreferenceRepository = notificationPreferenceRepository;
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

  // --- Stub handle*() methods for NotificationEventHandler (61C will implement real logic) ---

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleCommentCreated(CommentCreatedEvent event) {
    log.debug(
        "Notification fan-out stub: {} eventType={} entity={}",
        event.getClass().getSimpleName(),
        event.eventType(),
        event.entityId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleTaskAssigned(TaskAssignedEvent event) {
    log.debug(
        "Notification fan-out stub: {} eventType={} entity={}",
        event.getClass().getSimpleName(),
        event.eventType(),
        event.entityId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleTaskClaimed(TaskClaimedEvent event) {
    log.debug(
        "Notification fan-out stub: {} eventType={} entity={}",
        event.getClass().getSimpleName(),
        event.eventType(),
        event.entityId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleTaskStatusChanged(TaskStatusChangedEvent event) {
    log.debug(
        "Notification fan-out stub: {} eventType={} entity={}",
        event.getClass().getSimpleName(),
        event.eventType(),
        event.entityId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleDocumentUploaded(DocumentUploadedEvent event) {
    log.debug(
        "Notification fan-out stub: {} eventType={} entity={}",
        event.getClass().getSimpleName(),
        event.eventType(),
        event.entityId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleMemberAddedToProject(MemberAddedToProjectEvent event) {
    log.debug(
        "Notification fan-out stub: {} eventType={} entity={}",
        event.getClass().getSimpleName(),
        event.eventType(),
        event.entityId());
  }
}
