"use client";

import { Bell } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * Placeholder notification bell for the top bar.
 * Will be connected to the notification polling hook in a later task.
 */
export function NotificationBell() {
  return (
    <Button
      variant="ghost"
      size="sm"
      className="relative text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
      aria-label="Notifications"
    >
      <Bell className="h-4 w-4" />
    </Button>
  );
}
