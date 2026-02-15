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

import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardFooter,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
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
  const entityPrefix = eventType.split(".")[0];
  return ENTITY_ICON_MAP[entityPrefix] ?? Activity;
}

function getInitials(name: string): string {
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
  if (items === null) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Recent Activity</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground italic">
            Unable to load activity. Please try again.
          </p>
        </CardContent>
      </Card>
    );
  }

  if (items.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Recent Activity</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground italic">
            No recent activity across your projects.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Recent Activity</CardTitle>
      </CardHeader>
      <CardContent className="space-y-1">
        {items.map((item) => {
          const Icon = getEventIcon(item.eventType);
          return (
            <div
              key={item.eventId}
              className="flex items-start gap-3 rounded-md px-3 py-2.5"
            >
              <span className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full bg-slate-200 text-xs font-medium text-slate-700 dark:bg-slate-700 dark:text-slate-200">
                {getInitials(item.actorName)}
              </span>

              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <Icon className="size-3.5 shrink-0 text-slate-400 dark:text-slate-500" />
                  <p className="truncate text-sm text-slate-700 dark:text-slate-300">
                    {item.description}
                  </p>
                </div>
                <div className="mt-0.5 flex items-center gap-2">
                  <span className="inline-flex items-center rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                    {item.projectName}
                  </span>
                  <span className="text-xs text-slate-500 dark:text-slate-400">
                    {formatRelativeDate(item.occurredAt)}
                  </span>
                </div>
              </div>
            </div>
          );
        })}
      </CardContent>
      <CardFooter>
        <Button
          variant="ghost"
          size="sm"
          className="text-muted-foreground"
          onClick={() => {
            /* Future: link to full activity view */
          }}
        >
          View all activity &rarr;
        </Button>
      </CardFooter>
    </Card>
  );
}
