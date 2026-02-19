"use client";

import { useCallback, useState } from "react";
import { CheckCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { NotificationItem } from "@/components/notifications/notification-item";
import {
  fetchNotifications,
  markAllNotificationsRead,
} from "@/lib/actions/notifications";
import type { Notification } from "@/lib/actions/notifications";

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
  const [notifications, setNotifications] =
    useState<Notification[]>(initialNotifications);
  const [filter, setFilter] = useState<"all" | "unread">("all");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(initialTotalPages);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [isMarkingAll, setIsMarkingAll] = useState(false);

  const unreadOnly = filter === "unread";

  const reload = useCallback(
    async (filterValue: "all" | "unread") => {
      try {
        const data = await fetchNotifications(filterValue === "unread", 0);
        setNotifications(data.content);
        setTotalPages(data.page.totalPages);
        setPage(0);
      } catch {
        // Keep existing state on error
      }
    },
    []
  );

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
        setNotifications((prev) =>
          prev.map((n) => ({ ...n, isRead: true }))
        );
      }
    } finally {
      setIsMarkingAll(false);
    }
  }

  function handleItemRead() {
    reload(filter);
  }

  const hasUnread = notifications.some((n) => !n.isRead);
  const hasMore = page + 1 < totalPages;

  return (
    <div className="space-y-6">
      {/* Controls */}
      <div className="flex items-center justify-between">
        {/* Filter toggle */}
        <div className="flex gap-1 rounded-lg border border-slate-200 p-0.5 dark:border-slate-800">
          <button
            type="button"
            onClick={() => handleFilterChange("all")}
            className={`rounded-md px-3 py-1 text-sm font-medium transition-colors ${
              filter === "all"
                ? "bg-slate-100 text-slate-900 dark:bg-slate-800 dark:text-slate-100"
                : "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
            }`}
          >
            All
          </button>
          <button
            type="button"
            onClick={() => handleFilterChange("unread")}
            className={`rounded-md px-3 py-1 text-sm font-medium transition-colors ${
              filter === "unread"
                ? "bg-slate-100 text-slate-900 dark:bg-slate-800 dark:text-slate-100"
                : "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
            }`}
          >
            Unread
          </button>
        </div>

        {/* Mark all as read */}
        {hasUnread && (
          <Button
            variant="ghost"
            size="sm"
            onClick={handleMarkAllRead}
            disabled={isMarkingAll}
          >
            <CheckCheck className="size-4" />
            {isMarkingAll ? "Marking..." : "Mark all as read"}
          </Button>
        )}
      </div>

      {/* Notification list */}
      <div className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white dark:divide-slate-800 dark:border-slate-800 dark:bg-slate-950">
        {notifications.length === 0 && (
          <p className="px-4 py-12 text-center text-sm text-slate-500 dark:text-slate-400">
            No notifications
          </p>
        )}

        {notifications.map((notification) => (
          <NotificationItem
            key={notification.id}
            notification={notification}
            orgSlug={orgSlug}
            onRead={handleItemRead}
          />
        ))}
      </div>

      {/* Load more */}
      {hasMore && (
        <div className="flex justify-center">
          <Button
            variant="ghost"
            onClick={handleLoadMore}
            disabled={isLoadingMore}
          >
            {isLoadingMore ? "Loading..." : "Load more"}
          </Button>
        </div>
      )}
    </div>
  );
}
