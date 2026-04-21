import { portalFetch, portalGet } from "@/lib/api-client";

/**
 * Portal notification preferences (Epic 498C, ADR-258).
 *
 * Five per-contact opt-out toggles plus the read-only firm-level digest cadence
 * — the portal UI renders the cadence as a "Your firm sends weekly digests"
 * hint. All five boolean toggles default to `true` on first read.
 */
export interface NotificationPreferences {
  digestEnabled: boolean;
  trustActivityEnabled: boolean;
  retainerUpdatesEnabled: boolean;
  deadlineRemindersEnabled: boolean;
  actionRequiredEnabled: boolean;
  firmDigestCadence: "WEEKLY" | "BIWEEKLY" | "OFF";
}

export type NotificationPreferencesUpdate = Omit<
  NotificationPreferences,
  "firmDigestCadence"
>;

/** Fetches the current notification preferences for the authenticated portal contact. */
export async function getPreferences(): Promise<NotificationPreferences> {
  return portalGet<NotificationPreferences>("/portal/notification-preferences");
}

/**
 * Persists updated notification preferences. Accepts the five boolean toggles
 * only — `firmDigestCadence` is firm-level and cannot be set from the portal.
 */
export async function updatePreferences(
  dto: NotificationPreferencesUpdate,
): Promise<NotificationPreferences> {
  const response = await portalFetch("/portal/notification-preferences", {
    method: "PUT",
    body: JSON.stringify(dto),
  });
  if (!response.ok) {
    throw new Error("Failed to save preferences. Please try again.");
  }
  return response.json() as Promise<NotificationPreferences>;
}
