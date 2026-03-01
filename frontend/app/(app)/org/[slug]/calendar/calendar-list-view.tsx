"use client";

import Link from "next/link";
import { ClipboardList, FolderOpen } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { CalendarItem } from "./calendar-types";
import { getStatusVariant, getItemLink, getDueDateColor } from "./calendar-types";

interface CalendarListViewProps {
  items: CalendarItem[];
  overdueItems?: CalendarItem[];
  slug: string;
}

function getPriorityColor(priority: string | null): string {
  switch (priority) {
    case "URGENT":
      return "text-red-600 dark:text-red-400";
    case "HIGH":
      return "text-orange-600 dark:text-orange-400";
    case "MEDIUM":
      return "text-yellow-600 dark:text-yellow-400";
    case "LOW":
      return "text-slate-500 dark:text-slate-400";
    default:
      return "";
  }
}

function getWeekRange(dateStr: string): { start: Date; end: Date } {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  const dayOfWeek = date.getDay(); // 0=Sun
  const monday = new Date(date);
  monday.setDate(date.getDate() - ((dayOfWeek + 6) % 7)); // Go back to Monday
  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);
  return { start: monday, end: sunday };
}

function formatWeekHeader(start: Date, end: Date): string {
  const opts: Intl.DateTimeFormatOptions = { month: "short", day: "numeric" };
  const startStr = start.toLocaleDateString("en-US", opts);
  const endStr = end.toLocaleDateString("en-US", opts);
  return `Week of ${startStr} - ${endStr}`;
}

function getWeekKey(dateStr: string): string {
  const { start } = getWeekRange(dateStr);
  return start.toISOString().split("T")[0];
}

export function CalendarListView({
  items,
  overdueItems,
  slug,
}: CalendarListViewProps) {
  // Sort by dueDate
  const sorted = [...items].sort((a, b) =>
    a.dueDate.localeCompare(b.dueDate)
  );

  // Group by week
  const weekGroups = new Map<
    string,
    { header: string; items: CalendarItem[] }
  >();

  for (const item of sorted) {
    const weekKey = getWeekKey(item.dueDate);
    if (!weekGroups.has(weekKey)) {
      const { start, end } = getWeekRange(item.dueDate);
      weekGroups.set(weekKey, {
        header: formatWeekHeader(start, end),
        items: [],
      });
    }
    weekGroups.get(weekKey)!.items.push(item);
  }

  const hasOverdue = overdueItems && overdueItems.length > 0;

  if (sorted.length === 0 && !hasOverdue) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-slate-300 py-12 dark:border-slate-700">
        <p className="text-sm text-slate-500 dark:text-slate-400">
          No items with due dates this month
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Overdue Section */}
      {hasOverdue && (
        <div>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wider text-red-600 dark:text-red-400">
            Overdue
          </h3>
          <div className="divide-y divide-red-100 rounded-lg border border-red-200 bg-red-50 dark:divide-red-900/30 dark:border-red-800 dark:bg-red-950/10">
            {overdueItems.map((item) => (
              <Link
                key={item.id}
                href={getItemLink(item, slug)}
                className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-red-100/50 dark:hover:bg-red-900/20"
              >
                {item.itemType === "TASK" ? (
                  <ClipboardList className="size-4 shrink-0 text-red-400" />
                ) : (
                  <FolderOpen className="size-4 shrink-0 text-red-500" />
                )}
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-red-900 dark:text-red-100">
                    {item.name}
                  </p>
                  <p className="truncate text-xs text-red-600/70 dark:text-red-400/70">
                    {item.projectName}
                  </p>
                </div>
                <span className="shrink-0 font-mono text-xs tabular-nums text-red-600 dark:text-red-400">
                  {item.dueDate}
                </span>
                <Badge
                  variant={getStatusVariant(item.status)}
                  className="shrink-0"
                >
                  {item.status}
                </Badge>
              </Link>
            ))}
          </div>
        </div>
      )}

      {/* Week Groups */}
      {Array.from(weekGroups.entries()).map(([weekKey, group]) => (
        <div key={weekKey}>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
            {group.header}
          </h3>
          <div className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white dark:divide-slate-800 dark:border-slate-700 dark:bg-slate-900">
            {group.items.map((item) => {
              const urgency = getDueDateColor(item.dueDate, item.status);
              return (
                <Link
                  key={item.id}
                  href={getItemLink(item, slug)}
                  className={cn(
                    "flex items-center gap-3 px-4 py-3 transition-colors hover:bg-slate-50 dark:hover:bg-slate-800/50",
                    urgency.row
                  )}
                >
                  {/* Icon */}
                  {item.itemType === "TASK" ? (
                    <ClipboardList className="size-4 shrink-0 text-slate-400" />
                  ) : (
                    <FolderOpen className="size-4 shrink-0 text-teal-500" />
                  )}

                  {/* Name + project */}
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-slate-900 dark:text-slate-100">
                      {item.name}
                    </p>
                    <p className="truncate text-xs text-slate-500">
                      {item.projectName}
                    </p>
                  </div>

                  {/* Due date */}
                  <span className="shrink-0 font-mono text-xs tabular-nums text-slate-500">
                    {item.dueDate}
                  </span>

                  {/* Status badge */}
                  <Badge
                    variant={getStatusVariant(item.status)}
                    className="shrink-0"
                  >
                    {item.status}
                  </Badge>

                  {/* Priority (tasks only) */}
                  {item.priority && (
                    <span
                      className={cn(
                        "shrink-0 text-xs font-medium",
                        getPriorityColor(item.priority)
                      )}
                    >
                      {item.priority}
                    </span>
                  )}
                </Link>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}
