package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.event.InformationRequestCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.InformationRequestDraftCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.RequestItemSubmittedEvent;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.notification.Notification;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional helper for information request notifications. Each method runs in a REQUIRES_NEW
 * transaction so it can persist notifications when called from @TransactionalEventListener
 * (AFTER_COMMIT) handlers, which execute outside the original transaction.
 */
@Service
public class InformationRequestNotificationHelper {

  private static final Logger log =
      LoggerFactory.getLogger(InformationRequestNotificationHelper.class);

  private final NotificationService notificationService;
  private final InformationRequestRepository requestRepository;
  private final ProjectMemberRepository projectMemberRepository;

  public InformationRequestNotificationHelper(
      NotificationService notificationService,
      InformationRequestRepository requestRepository,
      ProjectMemberRepository projectMemberRepository) {
    this.notificationService = notificationService;
    this.requestRepository = requestRepository;
    this.projectMemberRepository = projectMemberRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleItemSubmitted(RequestItemSubmittedEvent event) {
    var request = requestRepository.findById(event.requestId()).orElse(null);
    if (request == null || request.getCreatedBy() == null) {
      log.warn(
          "Request not found or no creator for item-submitted notification: {}", event.requestId());
      return List.of();
    }
    String itemName = event.details().getOrDefault("item_name", "an item").toString();
    String requestNumber = event.details().getOrDefault("request_number", "").toString();
    var title = "Client submitted \"%s\" for %s".formatted(itemName, requestNumber);

    var notification =
        notificationService.createIfEnabled(
            request.getCreatedBy(),
            "INFORMATION_REQUEST_ITEM_SUBMITTED",
            title,
            null,
            "information_request",
            event.requestId(),
            event.projectId());
    return notification != null ? List.of(notification) : List.of();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleRequestCompleted(InformationRequestCompletedEvent event) {
    var request = requestRepository.findById(event.requestId()).orElse(null);
    if (request == null || request.getCreatedBy() == null) {
      log.warn(
          "Request not found or no creator for completion notification: {}", event.requestId());
      return List.of();
    }
    var title = "%s completed — all items accepted".formatted(request.getRequestNumber());

    var notification =
        notificationService.createIfEnabled(
            request.getCreatedBy(),
            "INFORMATION_REQUEST_COMPLETED",
            title,
            null,
            "information_request",
            event.requestId(),
            event.projectId());
    return notification != null ? List.of(notification) : List.of();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Notification> handleDraftCreated(InformationRequestDraftCreatedEvent event) {
    if (event.projectId() == null) {
      return List.of();
    }
    var members = projectMemberRepository.findByProjectId(event.projectId());
    var recipients = new HashSet<UUID>();
    for (var member : members) {
      recipients.add(member.getMemberId());
    }
    if (event.actorMemberId() != null) {
      recipients.remove(event.actorMemberId());
    }

    String requestNumber = event.details().getOrDefault("request_number", "").toString();
    var title = "Draft information request %s auto-created from template".formatted(requestNumber);

    var created = new ArrayList<Notification>();
    for (var recipientId : recipients) {
      var notification =
          notificationService.createIfEnabled(
              recipientId,
              "INFORMATION_REQUEST_DRAFT_CREATED",
              title,
              null,
              "information_request",
              event.entityId(),
              event.projectId());
      if (notification != null) {
        created.add(notification);
      }
    }
    return created;
  }
}
