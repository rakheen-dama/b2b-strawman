import { HelpTip } from "@/components/help-tip";
import { fetchNotificationPreferences } from "@/lib/actions/notifications";
import type { PreferencesResponse } from "@/lib/actions/notifications";
import { NotificationPreferencesForm } from "@/components/notifications/notification-preferences-form";

/**
 * Connections › Notifications tab (Phase 87 S9, ADR-341) — the former notification preferences
 * settings page's loader.
 */
export async function renderNotificationsTab() {
  let initialPreferences: PreferencesResponse = { preferences: [] };
  try {
    initialPreferences = await fetchNotificationPreferences();
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8">
      {/* Page header */}
      <div>
        <h1 className="font-display flex items-center gap-2 text-3xl text-slate-950 dark:text-slate-50">
          Notification Preferences
          <HelpTip code="notifications.preferences" />
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Choose which notifications you receive and how they are delivered.
        </p>
      </div>

      <NotificationPreferencesForm initialPreferences={initialPreferences.preferences} />
    </div>
  );
}
