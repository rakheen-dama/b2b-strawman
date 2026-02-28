"use client";

import {
  CheckSquare,
  FileText,
  MessageSquare,
  UserPlus,
  BellRing,
  type LucideIcon,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { formatRelativeDate } from "@/lib/format";
import { markNotificationRead } from "@/lib/actions/notifications";
import type { Notification } from "@/lib/actions/notifications";
import { cn } from "@/lib/utils";

interface NotificationItemProps {
  notification: Notification;
  orgSlug: string;
  onRead?: () => void;
}

const NOTIFICATION_ICON_MAP: Record<string, LucideIcon> = {
  TASK: CheckSquare,
  COMMENT: MessageSquare,
  DOCUMENT: FileText,
  MEMBER: UserPlus,
};

function getDeepLinkUrl(
  orgSlug: string,
  notification: Notification,
): string | null {
  const { referenceProjectId, referenceEntityType } = notification;
  if (!referenceProjectId) return null;

  const base = `/org/${orgSlug}/projects/${referenceProjectId}`;
  switch (referenceEntityType) {
    case "TASK":
      return `${base}?tab=tasks`;
    case "DOCUMENT":
      return `${base}?tab=documents`;
    default:
      return base;
  }
}

export function NotificationItem({
  notification,
  orgSlug,
  onRead,
}: NotificationItemProps) {
  const router = useRouter();
  const prefix = notification.type.split("_")[0];
  const Icon = NOTIFICATION_ICON_MAP[prefix] ?? BellRing;

  async function handleClick() {
    if (!notification.isRead) {
      markNotificationRead(notification.id)
        .then(() => onRead?.())
        .catch(() => {});
    }

    const url = getDeepLinkUrl(orgSlug, notification);
    if (url) {
      router.push(url);
    }
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      className={cn(
        "flex w-full items-start gap-3 px-4 py-3 text-left transition-colors hover:bg-slate-50",
        !notification.isRead && "bg-teal-50/50",
      )}
    >
      <Icon className="mt-0.5 size-4 shrink-0 text-slate-400" />

      <div className="min-w-0 flex-1">
        <p
          className={cn(
            "text-sm",
            notification.isRead
              ? "text-slate-600"
              : "font-medium text-slate-900",
          )}
        >
          {notification.title}
        </p>
        <p className="mt-0.5 text-xs text-slate-400">
          {formatRelativeDate(notification.createdAt)}
        </p>
      </div>

      {!notification.isRead && (
        <span
          className="mt-2 size-2 shrink-0 rounded-full bg-teal-500"
          aria-label="Unread"
        />
      )}
    </button>
  );
}
