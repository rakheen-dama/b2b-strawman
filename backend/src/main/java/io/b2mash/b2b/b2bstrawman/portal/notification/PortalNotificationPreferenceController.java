package io.b2mash.b2b.b2bstrawman.portal.notification;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal-contact-facing controller for notification preferences (Epic 498C, ADR-258). Exposes a
 * simple GET/PUT pair at {@code /portal/notification-preferences} — auth is handled by {@code
 * CustomerAuthFilter}, which binds {@link RequestScopes#PORTAL_CONTACT_ID} for any {@code
 * /portal/**} request carrying a valid portal JWT.
 *
 * <p>The response bundles the five per-contact toggles from {@link PortalNotificationPreference}
 * together with the firm-level {@code portalDigestCadence} — the portal UI renders that cadence as
 * a read-only "Your firm sends weekly digests" hint so the contact knows the firm-side context.
 */
@RestController
@RequestMapping("/portal/notification-preferences")
public class PortalNotificationPreferenceController {

  private final PortalNotificationPreferenceService preferenceService;

  public PortalNotificationPreferenceController(
      PortalNotificationPreferenceService preferenceService) {
    this.preferenceService = preferenceService;
  }

  @GetMapping
  public ResponseEntity<PortalNotificationPreferencesResponse> get() {
    return ResponseEntity.ok(
        preferenceService.getPreferencesResponse(RequestScopes.requirePortalContactId()));
  }

  @PutMapping
  public ResponseEntity<PortalNotificationPreferencesResponse> update(
      @Valid @RequestBody UpdateRequest request) {
    return ResponseEntity.ok(
        preferenceService.updateAndGetResponse(
            RequestScopes.requirePortalContactId(),
            new PortalNotificationPreferenceService.PortalNotificationPreferenceUpdate(
                request.digestEnabled(),
                request.trustActivityEnabled(),
                request.retainerUpdatesEnabled(),
                request.deadlineRemindersEnabled(),
                request.actionRequiredEnabled())));
  }

  public record UpdateRequest(
      @NotNull Boolean digestEnabled,
      @NotNull Boolean trustActivityEnabled,
      @NotNull Boolean retainerUpdatesEnabled,
      @NotNull Boolean deadlineRemindersEnabled,
      @NotNull Boolean actionRequiredEnabled) {}
}
