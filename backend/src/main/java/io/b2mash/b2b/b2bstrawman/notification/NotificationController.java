package io.b2mash.b2b.b2bstrawman.notification;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Page<NotificationResponse>> listNotifications(
      @RequestParam(defaultValue = "false") boolean unreadOnly,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    UUID memberId = RequestScopes.requireMemberId();
    var pageable =
        PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
    var notifications = notificationService.listNotifications(memberId, unreadOnly, pageable);

    return ResponseEntity.ok(notifications.map(NotificationResponse::from));
  }

  @GetMapping("/unread-count")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<UnreadCountResponse> getUnreadCount() {
    UUID memberId = RequestScopes.requireMemberId();
    long count = notificationService.getUnreadCount(memberId);
    return ResponseEntity.ok(new UnreadCountResponse(count));
  }

  @PutMapping("/{id}/read")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    notificationService.markAsRead(id, memberId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/read-all")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> markAllAsRead() {
    UUID memberId = RequestScopes.requireMemberId();
    notificationService.markAllAsRead(memberId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> dismissNotification(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    notificationService.dismissNotification(id, memberId);
    return ResponseEntity.noContent().build();
  }

  // --- DTOs ---

  public record NotificationResponse(
      UUID id,
      String type,
      String title,
      String body,
      String referenceEntityType,
      UUID referenceEntityId,
      UUID referenceProjectId,
      boolean isRead,
      Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
      return new NotificationResponse(
          notification.getId(),
          notification.getType(),
          notification.getTitle(),
          notification.getBody(),
          notification.getReferenceEntityType(),
          notification.getReferenceEntityId(),
          notification.getReferenceProjectId(),
          notification.isRead(),
          notification.getCreatedAt());
    }
  }

  public record UnreadCountResponse(long count) {}
}
