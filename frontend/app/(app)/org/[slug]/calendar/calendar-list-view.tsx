"use client";

import Link from "next/link";
import { ClipboardList, FolderOpen } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { CalendarItem } from "./calendar-actions";

interface CalendarListViewProps {
  items: CalendarItem[];
  slug: string;
}

function getStatusVariant(
  status: string
): "neutral" | "warning" | "success" | "secondary" {
  switch (status) {
    case "OPEN":
      return "neutral";
    case "IN_PROGRESS":
      return "warning";
    case "DONE":
      return "success";
    case "CANCELLED":
    case "ARCHIVED":
      return "secondary";
    case "ACTIVE":
      return "success";
    default:
      return "neutral";
  }
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

function getItemLink(item: CalendarItem, slug: string): string {
  if (item.itemType === "TASK") {
    return `/org/${slug}/projects/${item.projectId}?taskId=${item.id}`;
  }
  return `/org/${slug}/projects/${item.projectId}`;
}

export function CalendarListView({ items, slug }: CalendarListViewProps) {
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

  if (sorted.length === 0) {
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
      {Array.from(weekGroups.entries()).map(([weekKey, group]) => (
        <div key={weekKey}>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
            {group.header}
          </h3>
          <div className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white dark:divide-slate-800 dark:border-slate-700 dark:bg-slate-900">
            {group.items.map((item) => (
              <Link
                key={item.id}
                href={getItemLink(item, slug)}
                className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-slate-50 dark:hover:bg-slate-800/50"
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
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
