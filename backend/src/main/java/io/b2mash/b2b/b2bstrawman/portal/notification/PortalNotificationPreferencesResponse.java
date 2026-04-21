package io.b2mash.b2b.b2bstrawman.portal.notification;

/**
 * Response DTO returned by {@link PortalNotificationPreferenceController} for both GET and PUT
 * /portal/notification-preferences. Bundles the five per-contact toggles with the firm-level portal
 * digest cadence so the portal UI can render a read-only "Your firm sends weekly digests" hint.
 */
public record PortalNotificationPreferencesResponse(
    boolean digestEnabled,
    boolean trustActivityEnabled,
    boolean retainerUpdatesEnabled,
    boolean deadlineRemindersEnabled,
    boolean actionRequiredEnabled,
    String firmDigestCadence) {}
