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

import { cn } from "@/lib/utils";
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";
import { RelativeDate } from "@/components/ui/relative-date";
import { useTerminology } from "@/lib/terminology";
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

export function RecentActivityWidget({ items, orgSlug: _orgSlug }: RecentActivityWidgetProps) {
  const { t } = useTerminology();

  if (items === null) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Recent Activity</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground text-sm italic">
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
          <EmptyState
            icon={Activity}
            title="No recent activity"
            description={`Activity will appear as your team works on ${t("projects")}.`}
          />
        </CardContent>
      </Card>
    );
  }

  return (
    <Card data-testid="recent-activity-widget">
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium">Recent Activity</CardTitle>
      </CardHeader>
      <CardContent className="space-y-0 pt-0">
        {items.map((item, idx) => {
          const Icon = getEventIcon(item.eventType);
          return (
            <div
              key={item.eventId}
              className={cn(
                "flex items-center gap-2 rounded-md px-2 py-1.5",
                idx % 2 === 1 && "bg-slate-50/50 dark:bg-slate-900/50"
              )}
            >
              <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-slate-200 text-[10px] font-medium text-slate-700 dark:bg-slate-700 dark:text-slate-200">
                {getInitials(item.actorName)}
              </span>

              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <Icon className="size-3 shrink-0 text-slate-400 dark:text-slate-500" />
                  <p className="truncate text-xs text-slate-700 dark:text-slate-300">
                    {item.description}
                  </p>
                </div>
              </div>
              <span className="shrink-0 text-[10px] text-slate-400 dark:text-slate-500">
                <RelativeDate iso={item.occurredAt} />
              </span>
            </div>
          );
        })}
      </CardContent>
      <CardFooter className="pt-0">
        <Button
          variant="ghost"
          size="sm"
          className="h-7 text-xs text-slate-500"
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
