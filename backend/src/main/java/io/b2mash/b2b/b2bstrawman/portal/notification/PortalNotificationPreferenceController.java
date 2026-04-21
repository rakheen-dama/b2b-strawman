package io.b2mash.b2b.b2bstrawman.portal.notification;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import jakarta.validation.Valid;
import java.util.UUID;
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
  private final OrgSettingsService orgSettingsService;

  public PortalNotificationPreferenceController(
      PortalNotificationPreferenceService preferenceService,
      OrgSettingsService orgSettingsService) {
    this.preferenceService = preferenceService;
    this.orgSettingsService = orgSettingsService;
  }

  @GetMapping
  public ResponseEntity<PreferencesResponse> get() {
    return ResponseEntity.ok(buildResponse(requireContactId()));
  }

  @PutMapping
  public ResponseEntity<PreferencesResponse> update(@Valid @RequestBody UpdateRequest request) {
    UUID contactId = requireContactId();
    preferenceService.update(
        contactId,
        new PortalNotificationPreferenceService.PortalNotificationPreferenceUpdate(
            request.digestEnabled(),
            request.trustActivityEnabled(),
            request.retainerUpdatesEnabled(),
            request.deadlineRemindersEnabled(),
            request.actionRequiredEnabled()));
    return ResponseEntity.ok(buildResponse(contactId));
  }

  private UUID requireContactId() {
    if (!RequestScopes.PORTAL_CONTACT_ID.isBound()) {
      throw new ResourceNotFoundException("PortalContact", "(unbound)");
    }
    return RequestScopes.PORTAL_CONTACT_ID.get();
  }

  private PreferencesResponse buildResponse(UUID contactId) {
    var pref = preferenceService.getOrCreate(contactId);
    var firmCadence = orgSettingsService.getPortalDigestCadence();
    return new PreferencesResponse(
        pref.isDigestEnabled(),
        pref.isTrustActivityEnabled(),
        pref.isRetainerUpdatesEnabled(),
        pref.isDeadlineRemindersEnabled(),
        pref.isActionRequiredEnabled(),
        firmCadence.name());
  }

  public record PreferencesResponse(
      boolean digestEnabled,
      boolean trustActivityEnabled,
      boolean retainerUpdatesEnabled,
      boolean deadlineRemindersEnabled,
      boolean actionRequiredEnabled,
      String firmDigestCadence) {}

  public record UpdateRequest(
      boolean digestEnabled,
      boolean trustActivityEnabled,
      boolean retainerUpdatesEnabled,
      boolean deadlineRemindersEnabled,
      boolean actionRequiredEnabled) {}
}
