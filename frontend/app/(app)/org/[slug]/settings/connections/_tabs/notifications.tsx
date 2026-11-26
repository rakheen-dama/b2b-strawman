import { PageHeader } from "@b2mash/ui/page-header";
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
      {/* K-124: the h1 is the tab label ("Notifications"). */}
      <PageHeader
        title={
          <span className="inline-flex items-center gap-2">
            Notifications
            <HelpTip code="notifications.preferences" />
          </span>
        }
        description="Choose which notifications you receive and how they are delivered."
      />

      <NotificationPreferencesForm initialPreferences={initialPreferences.preferences} />
    </div>
  );
}
