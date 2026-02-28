"use client";

import {
  CheckSquare,
  FileText,
  MessageSquare,
  Clock,
  Users,
  Activity,
  type LucideIcon,
} from "lucide-react";

import { WidgetCard } from "@/components/layout/widget-grid";
import { formatRelativeDate } from "@/lib/format";
import type { CrossProjectActivityItem } from "@/lib/dashboard-types";

interface RecentActivityWidgetProps {
  items: CrossProjectActivityItem[] | null;
  orgSlug: string;
}

const ENTITY_ICON_MAP: Record<string, LucideIcon> = {
  task: CheckSquare,
  document: FileText,
  comment: MessageSquare,
  time_entry: Clock,
  project_member: Users,
};

function getEventIcon(eventType: string): LucideIcon {
  const prefix = eventType.split(".")[0];
  return ENTITY_ICON_MAP[prefix] ?? Activity;
}

function getInitials(name: string | null | undefined): string {
  if (!name) return "?";
  return name
    .split(" ")
    .map((part) => part[0])
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase();
}

export function RecentActivityWidget({
  items,
  orgSlug,
}: RecentActivityWidgetProps) {
  // suppress unused var lint — orgSlug reserved for future "View all" link
  void orgSlug;

  if (items === null) {
    return (
      <WidgetCard title="Recent Activity">
        <p className="py-4 text-center text-sm text-slate-500 italic">
          Unable to load activity.
        </p>
      </WidgetCard>
    );
  }

  if (items.length === 0) {
    return (
      <WidgetCard title="Recent Activity">
        <p className="py-4 text-center text-sm text-slate-500 italic">
          No recent activity across your projects.
        </p>
      </WidgetCard>
    );
  }

  return (
    <WidgetCard title="Recent Activity">
      <div className="space-y-1">
        {items.map((item) => {
          const Icon = getEventIcon(item.eventType);
          return (
            <div
              key={item.eventId}
              className="flex items-start gap-3 rounded-md px-3 py-2.5"
            >
              <span className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-medium text-slate-700 dark:bg-slate-800 dark:text-slate-200">
                {getInitials(item.actorName)}
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <Icon className="size-3.5 shrink-0 text-slate-400" />
                  <p className="truncate text-sm text-slate-700 dark:text-slate-300">
                    {item.description}
                  </p>
                </div>
                <div className="mt-0.5 flex items-center gap-2">
                  <span className="inline-flex items-center rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                    {item.projectName}
                  </span>
                  <span className="text-xs text-slate-500">
                    {formatRelativeDate(item.occurredAt)}
                  </span>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </WidgetCard>
  );
}
