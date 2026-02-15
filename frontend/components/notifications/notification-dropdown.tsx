"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { CheckCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { NotificationItem } from "@/components/notifications/notification-item";
import {
  fetchNotifications,
  markAllNotificationsRead,
} from "@/lib/actions/notifications";
import type { Notification } from "@/lib/actions/notifications";

interface NotificationDropdownProps {
  orgSlug: string;
  onClose: () => void;
  onCountChange: () => void;
}

export function NotificationDropdown({
  orgSlug,
  onClose,
  onCountChange,
}: NotificationDropdownProps) {
  const [notifications, setNotifications] = useState<Notification[] | null>(
    null
  );
  const [error, setError] = useState<string | null>(null);
  const [isMarkingAll, setIsMarkingAll] = useState(false);

  const loadNotifications = useCallback(async () => {
    try {
      const data = await fetchNotifications(false, 0);
      setNotifications(data.content);
      setError(null);
    } catch {
      setNotifications([]);
      setError("Failed to load notifications.");
    }
  }, []);

  useEffect(() => {
    loadNotifications();
  }, [loadNotifications]);

  async function handleMarkAllRead() {
    setIsMarkingAll(true);
    try {
      const result = await markAllNotificationsRead();
      if (result.success) {
        // Update local state to mark all as read
        setNotifications(
          (prev) => prev?.map((n) => ({ ...n, isRead: true })) ?? null
        );
        onCountChange();
      }
    } finally {
      setIsMarkingAll(false);
    }
  }

  function handleItemRead() {
    onCountChange();
    loadNotifications();
  }

  const isLoading = notifications === null;
  const hasUnread = notifications?.some((n) => !n.isRead) ?? false;

  return (
    <div className="w-80">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-slate-200 px-3 py-2 dark:border-slate-800">
        <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
          Notifications
        </h3>
        {hasUnread && (
          <Button
            variant="ghost"
            size="xs"
            onClick={handleMarkAllRead}
            disabled={isMarkingAll}
          >
            <CheckCheck className="size-3" />
            {isMarkingAll ? "Marking..." : "Mark all read"}
          </Button>
        )}
      </div>

      {/* Content */}
      <div className="max-h-80 overflow-y-auto">
        {isLoading && (
          <div className="space-y-2 p-3">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        )}

        {error && (
          <p className="px-3 py-4 text-center text-sm text-red-600">{error}</p>
        )}

        {!isLoading && !error && notifications.length === 0 && (
          <p className="px-3 py-8 text-center text-sm text-slate-500 dark:text-slate-400">
            No notifications
          </p>
        )}

        {!isLoading &&
          !error &&
          notifications.map((notification) => (
            <NotificationItem
              key={notification.id}
              notification={notification}
              orgSlug={orgSlug}
              onRead={handleItemRead}
            />
          ))}
      </div>

      {/* Footer */}
      <div className="border-t border-slate-200 px-3 py-2 dark:border-slate-800">
        {/* Route created in Epic 63B */}
        <Link
          href={`/org/${orgSlug}/notifications`}
          onClick={onClose}
          className="block text-center text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
        >
          View all notifications
        </Link>
      </div>
    </div>
  );
}
