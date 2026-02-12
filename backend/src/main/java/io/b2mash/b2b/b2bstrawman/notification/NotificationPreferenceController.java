package io.b2mash.b2b.b2bstrawman.notification;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/preferences")
public class NotificationPreferenceController {

  private final NotificationService notificationService;

  public NotificationPreferenceController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<PreferencesListResponse> listPreferences() {
    UUID memberId = RequestScopes.requireMemberId();
    var preferences = notificationService.getPreferences(memberId);
    return ResponseEntity.ok(
        new PreferencesListResponse(preferences.stream().map(PreferenceResponse::from).toList()));
  }

  @PutMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<PreferencesListResponse> updatePreferences(
      @RequestBody UpdatePreferencesRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    var updates =
        request.preferences().stream()
            .map(
                p ->
                    new NotificationService.PreferenceUpdate(
                        p.notificationType(), p.inAppEnabled(), p.emailEnabled()))
            .toList();
    var preferences = notificationService.updatePreferences(memberId, updates);
    return ResponseEntity.ok(
        new PreferencesListResponse(preferences.stream().map(PreferenceResponse::from).toList()));
  }

  // --- DTOs ---

  public record PreferenceResponse(
      String notificationType, boolean inAppEnabled, boolean emailEnabled) {

    public static PreferenceResponse from(NotificationService.PreferenceView view) {
      return new PreferenceResponse(
          view.notificationType(), view.inAppEnabled(), view.emailEnabled());
    }
  }

  public record PreferencesListResponse(List<PreferenceResponse> preferences) {}

  public record UpdatePreferencesRequest(List<PreferenceUpdate> preferences) {}

  public record PreferenceUpdate(
      String notificationType, boolean inAppEnabled, boolean emailEnabled) {}
}
