"use client";

import { useCallback, useState } from "react";
import { Bell, CheckCheck } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Card } from "@b2mash/ui/card";
import { EmptyState } from "@/components/empty-state";
import { createMessages } from "@/lib/messages";
import { NotificationItem } from "@/components/notifications/notification-item";
import { fetchNotifications, markAllNotificationsRead } from "@/lib/actions/notifications";
import type { Notification } from "@/lib/actions/notifications";
import { cn } from "@/lib/utils";

const FILTER_BUTTON = "rounded-md px-3 py-1 text-sm font-medium transition-colors";
const FILTER_ACTIVE = "bg-muted text-foreground";
const FILTER_INACTIVE = "text-muted-foreground hover:text-foreground";

interface NotificationsPageClientProps {
  initialNotifications: Notification[];
  initialTotalPages: number;
  orgSlug: string;
}

export function NotificationsPageClient({
  initialNotifications,
  initialTotalPages,
  orgSlug,
}: NotificationsPageClientProps) {
  const [notifications, setNotifications] = useState<Notification[]>(initialNotifications);
  const [filter, setFilter] = useState<"all" | "unread">("all");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(initialTotalPages);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [isMarkingAll, setIsMarkingAll] = useState(false);

  const unreadOnly = filter === "unread";

  const reload = useCallback(async (filterValue: "all" | "unread") => {
    try {
      const data = await fetchNotifications(filterValue === "unread", 0);
      setNotifications(data.content);
      setTotalPages(data.page.totalPages);
      setPage(0);
    } catch {
      // Keep existing state on error
    }
  }, []);

  async function handleFilterChange(newFilter: "all" | "unread") {
    setFilter(newFilter);
    await reload(newFilter);
  }

  async function handleLoadMore() {
    setIsLoadingMore(true);
    try {
      const nextPage = page + 1;
      const data = await fetchNotifications(unreadOnly, nextPage);
      setNotifications((prev) => [...prev, ...data.content]);
      setTotalPages(data.page.totalPages);
      setPage(nextPage);
    } catch {
      // Keep existing state on error
    } finally {
      setIsLoadingMore(false);
    }
  }

  async function handleMarkAllRead() {
    setIsMarkingAll(true);
    try {
      const result = await markAllNotificationsRead();
      if (result.success) {
        setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      }
    } finally {
      setIsMarkingAll(false);
    }
  }

  function handleItemRead() {
    reload(filter);
  }

  const { t } = createMessages("empty-states");
  const hasUnread = notifications.some((n) => !n.isRead);
  const hasMore = page + 1 < totalPages;

  return (
    <div className="space-y-6">
      {/* Controls */}
      <div className="flex items-center justify-between">
        {/* Filter toggle */}
        <div className="flex gap-1 rounded-lg border p-0.5">
          <button
            type="button"
            aria-pressed={filter === "all"}
            onClick={() => handleFilterChange("all")}
            className={cn(FILTER_BUTTON, filter === "all" ? FILTER_ACTIVE : FILTER_INACTIVE)}
          >
            All
          </button>
          <button
            type="button"
            aria-pressed={filter === "unread"}
            onClick={() => handleFilterChange("unread")}
            className={cn(FILTER_BUTTON, filter === "unread" ? FILTER_ACTIVE : FILTER_INACTIVE)}
          >
            Unread
          </button>
        </div>

        {/* Mark all as read */}
        {hasUnread && (
          <Button variant="ghost" size="sm" onClick={handleMarkAllRead} disabled={isMarkingAll}>
            <CheckCheck className="size-4" />
            {isMarkingAll ? "Marking..." : "Mark all as read"}
          </Button>
        )}
      </div>

      {/* Notification list */}
      {notifications.length === 0 ? (
        <EmptyState
          variant="default"
          icon={Bell}
          title={t("notifications.page.heading")}
          description={t("notifications.page.description")}
        />
      ) : (
        <Card className="gap-0 py-0">
          <div className="divide-border divide-y">
            {notifications.map((notification) => (
              <NotificationItem
                key={notification.id}
                notification={notification}
                orgSlug={orgSlug}
                onRead={handleItemRead}
              />
            ))}
          </div>
        </Card>
      )}

      {/* Load more */}
      {hasMore && (
        <div className="flex justify-center">
          <Button variant="ghost" onClick={handleLoadMore} disabled={isLoadingMore}>
            {isLoadingMore ? "Loading..." : "Load more"}
          </Button>
        </div>
      )}
    </div>
  );
}
