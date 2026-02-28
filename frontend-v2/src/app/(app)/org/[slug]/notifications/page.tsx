import { PageHeader } from "@/components/layout/page-header";
import { NotificationsPageClient } from "@/components/notifications/notifications-page-client";
import { fetchNotifications } from "@/lib/actions/notifications";
import { getAuthContext } from "@/lib/auth";
import type { Notification } from "@/lib/actions/notifications";

export default async function NotificationsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  await getAuthContext();

  let notifications: Notification[] = [];
  let totalPages = 0;

  try {
    const data = await fetchNotifications(false, 0);
    notifications = data.content;
    totalPages = data.page.totalPages;
  } catch {
    // Non-fatal
  }

  return (
    <div className="space-y-8">
      <PageHeader
        title="Notifications"
        description="View and manage your notifications."
      />

      <NotificationsPageClient
        initialNotifications={notifications}
        initialTotalPages={totalPages}
        orgSlug={slug}
      />
    </div>
  );
}
