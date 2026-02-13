import { fetchNotifications } from "@/lib/actions/notifications";
import { NotificationsPageClient } from "@/components/notifications/notifications-page-client";
import type { NotificationsResponse } from "@/lib/actions/notifications";

export default async function NotificationsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let initialNotifications: NotificationsResponse = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    size: 10,
    number: 0,
  };
  try {
    initialNotifications = await fetchNotifications(false, 0);
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8">
      <h1 className="font-display text-3xl text-olive-950 dark:text-olive-50">
        Notifications
      </h1>

      <NotificationsPageClient
        initialNotifications={initialNotifications.content}
        initialTotalPages={initialNotifications.totalPages}
        orgSlug={slug}
      />
    </div>
  );
}
