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
import { Button } from "@b2mash/ui/button";
import { EmptyState } from "@/components/empty-state";
import { RelativeDate } from "@/components/ui/relative-date";
import { WidgetCard } from "@/components/dashboard/widget-parts";
import type { CrossMatterActivityItem } from "@/lib/dashboard-types";

interface RecentActivityWidgetProps {
  items: CrossMatterActivityItem[] | null;
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
  // One shell for all three states (640B): same title style and testid whether loaded or not.
  if (items === null) {
    return (
      <WidgetCard data-testid="recent-activity-widget" title="Recent Activity">
        <p className="text-muted-foreground text-sm italic">
          Unable to load activity. Please try again.
        </p>
      </WidgetCard>
    );
  }

  if (items.length === 0) {
    return (
      <WidgetCard data-testid="recent-activity-widget" title="Recent Activity">
        <EmptyState
          variant="compact"
          icon={Activity}
          title="No recent activity"
          description="Activity will appear as your team works on matters."
        />
      </WidgetCard>
    );
  }

  return (
    <WidgetCard
      data-testid="recent-activity-widget"
      title="Recent Activity"
      footer={
        <Button
          variant="ghost"
          size="sm"
          className="text-muted-foreground h-7 text-xs"
          onClick={() => {
            /* Future: link to full activity view */
          }}
        >
          View all activity &rarr;
        </Button>
      }
    >
      {items.map((item, idx) => {
        const Icon = getEventIcon(item.eventType);
        return (
          <div
            key={item.eventId}
            className={cn(
              "flex items-center gap-2 rounded-md px-2 py-1.5",
              idx % 2 === 1 && "bg-muted/50"
            )}
          >
            <span className="bg-muted text-foreground flex size-6 shrink-0 items-center justify-center rounded-full text-[10px] font-medium">
              {getInitials(item.actorName)}
            </span>

            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5">
                <Icon className="text-muted-foreground size-3 shrink-0" />
                <p className="text-foreground truncate text-xs">{item.description}</p>
              </div>
            </div>
            <span className="text-muted-foreground shrink-0 text-[10px]">
              <RelativeDate iso={item.occurredAt} />
            </span>
          </div>
        );
      })}
    </WidgetCard>
  );
}
