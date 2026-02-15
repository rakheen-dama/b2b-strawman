"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Bell } from "lucide-react";
import { Button } from "@/components/ui/button";
import { NotificationDropdown } from "@/components/notifications/notification-dropdown";
import { useNotificationPolling } from "@/hooks/use-notification-polling";
import { cn } from "@/lib/utils";

interface NotificationBellProps {
  orgSlug: string;
}

export function NotificationBell({ orgSlug }: NotificationBellProps) {
  const { unreadCount, refetch } = useNotificationPolling();
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const handleClose = useCallback(() => setIsOpen(false), []);

  // Close dropdown when clicking outside
  useEffect(() => {
    if (!isOpen) return;

    function handleClickOutside(event: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    }

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [isOpen]);

  // Close on Escape
  useEffect(() => {
    if (!isOpen) return;

    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setIsOpen(false);
      }
    }

    document.addEventListener("keydown", handleEscape);
    return () => document.removeEventListener("keydown", handleEscape);
  }, [isOpen]);

  const displayCount = unreadCount > 99 ? "99+" : String(unreadCount);

  return (
    <div ref={containerRef} className="relative">
      <Button
        variant="ghost"
        size="icon-sm"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-label="Notifications"
        aria-expanded={isOpen}
        className="relative"
      >
        <Bell className="size-4" />
        {unreadCount > 0 && (
          <span
            className={cn(
              "absolute -top-0.5 -right-0.5 flex items-center justify-center rounded-full bg-teal-600 text-[10px] font-medium text-white",
              unreadCount > 99
                ? "h-4 min-w-5 px-1"
                : "size-4"
            )}
          >
            {displayCount}
          </span>
        )}
      </Button>

      {isOpen && (
        <div role="dialog" aria-label="Notifications" className="absolute right-0 top-full z-50 mt-1 rounded-md border border-slate-200 bg-white shadow-lg dark:border-slate-800 dark:bg-slate-950">
          <NotificationDropdown
            orgSlug={orgSlug}
            onClose={handleClose}
            onCountChange={refetch}
          />
        </div>
      )}
    </div>
  );
}
