import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchNotificationPreferences } from "@/lib/actions/notifications";
import type { PreferencesResponse } from "@/lib/actions/notifications";
import { NotificationPreferencesForm } from "@/components/notifications/notification-preferences-form";

export default async function NotificationPreferencesPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let initialPreferences: PreferencesResponse = { preferences: [] };
  try {
    initialPreferences = await fetchNotificationPreferences();
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8">
      {/* Back link */}
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-olive-600 hover:text-olive-900 dark:text-olive-400 dark:hover:text-olive-100"
      >
        <ChevronLeft className="size-4" />
        Settings
      </Link>

      {/* Page header */}
      <div>
        <h1 className="font-display text-3xl text-olive-950 dark:text-olive-50">
          Notification Preferences
        </h1>
        <p className="mt-1 text-sm text-olive-600 dark:text-olive-400">
          Choose which notifications you receive and how they are delivered.
        </p>
      </div>

      <NotificationPreferencesForm
        initialPreferences={initialPreferences.preferences}
      />
    </div>
  );
}
