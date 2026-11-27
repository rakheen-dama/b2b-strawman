"use client";

import { useMemo } from "react";
import { CheckCircle, AlertTriangle, Clock, CalendarDays } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { EmptyState } from "@/components/empty-state";
import { cn } from "@/lib/utils";
import type { MyWorkTaskItem } from "@/lib/types";
import { todayInSA } from "@b2mash/shared/date";
import { addDays, parseIsoDate } from "@/app/(app)/org/[slug]/diary/diary-types";
import { MyWorkTaskTable } from "./task-table";

// --- Urgency grouping ---

interface UrgencyGroups {
  overdue: MyWorkTaskItem[];
  dueThisWeek: MyWorkTaskItem[];
  upcoming: MyWorkTaskItem[];
  noDueDate: MyWorkTaskItem[];
}

export function groupByUrgency(
  tasks: MyWorkTaskItem[],
  today: string = todayInSA()
): UrgencyGroups {
  // `today` is South African today (yyyy-MM-dd), so the SSR render (UTC) and the browser group
  // identically (G88-F49). yyyy-MM-dd strings compare lexically.

  // End of the current week (Sunday)
  const day = parseIsoDate(today).getDay();
  const endOfWeek = addDays(today, day === 0 ? 0 : 7 - day);

  const overdue: MyWorkTaskItem[] = [];
  const dueThisWeek: MyWorkTaskItem[] = [];
  const upcoming: MyWorkTaskItem[] = [];
  const noDueDate: MyWorkTaskItem[] = [];

  for (const task of tasks) {
    if (!task.dueDate) {
      noDueDate.push(task);
      continue;
    }
    if (task.status === "DONE" || task.status === "CANCELLED") {
      upcoming.push(task);
      continue;
    }
    const due = task.dueDate.slice(0, 10);
    if (due < today) {
      overdue.push(task);
    } else if (due <= endOfWeek) {
      dueThisWeek.push(task);
    } else {
      upcoming.push(task);
    }
  }

  // Sort overdue by most overdue first (earliest due date first)
  overdue.sort((a, b) => a.dueDate!.localeCompare(b.dueDate!));

  return { overdue, dueThisWeek, upcoming, noDueDate };
}

// --- Section header ---

interface GroupHeaderProps {
  icon: React.ReactNode;
  label: string;
  count: number;
  variant: "destructive" | "warning" | "neutral";
}

function GroupHeader({ icon, label, count, variant }: GroupHeaderProps) {
  const colorClasses = {
    destructive: "text-red-700 dark:text-red-400",
    warning: "text-amber-700 dark:text-amber-400",
    neutral: "text-slate-700 dark:text-slate-300",
  };

  return (
    <div className="flex items-center gap-2">
      <span className={colorClasses[variant]}>{icon}</span>
      <h3 className={cn("text-sm font-semibold", colorClasses[variant])}>{label}</h3>
      <Badge variant={variant}>{count}</Badge>
    </div>
  );
}

// --- Main Component ---

/** The Overdue group's row tint (the kit has no row-class hook: wrapper variant, D-84 DA-6). */
const OVERDUE_ROW_TINT = "[&_tbody_tr]:bg-red-50/30 dark:[&_tbody_tr]:bg-red-950/10";

interface UrgencyTaskListProps {
  tasks: MyWorkTaskItem[];
  slug: string;
  onTaskClick?: (taskId: string) => void;
}

export function UrgencyTaskList({ tasks, slug, onTaskClick }: UrgencyTaskListProps) {
  // Memoised: each group array is a DataTable `data` prop (a new identity resets the table). Keyed
  // on the SA date too, so a render after midnight regroups rather than keeping yesterday's groups.
  const today = todayInSA();
  const groups = useMemo(() => groupByUrgency(tasks, today), [tasks, today]);

  if (tasks.length === 0) {
    return (
      <div className="space-y-4">
        <div className="flex items-center gap-2">
          <h2 className="text-foreground font-semibold">My Tasks</h2>
        </div>
        <EmptyState
          variant="compact"
          icon={CheckCircle}
          title="No tasks assigned"
          description="Tasks assigned to you will appear here"
        />
      </div>
    );
  }

  const hasOverdue = groups.overdue.length > 0;
  const hasDueThisWeek = groups.dueThisWeek.length > 0;
  const hasUpcoming = groups.upcoming.length > 0;
  const hasNoDueDate = groups.noDueDate.length > 0;

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <h2 className="text-foreground font-semibold">My Tasks</h2>
        <Badge variant="neutral">{tasks.length}</Badge>
      </div>

      <div className="space-y-6">
        {hasOverdue && (
          <div className="space-y-2">
            <GroupHeader
              icon={<AlertTriangle className="size-4" />}
              label="Overdue"
              count={groups.overdue.length}
              variant="destructive"
            />
            <MyWorkTaskTable
              tasks={groups.overdue}
              slug={slug}
              columnSet="full"
              overdueTone="group"
              onTaskClick={onTaskClick}
              className={OVERDUE_ROW_TINT}
            />
          </div>
        )}

        {hasDueThisWeek && (
          <div className="space-y-2">
            <GroupHeader
              icon={<Clock className="size-4" />}
              label="Due This Week"
              count={groups.dueThisWeek.length}
              variant="warning"
            />
            <MyWorkTaskTable
              tasks={groups.dueThisWeek}
              slug={slug}
              columnSet="full"
              overdueTone="never"
              onTaskClick={onTaskClick}
            />
          </div>
        )}

        {hasUpcoming && (
          <div className="space-y-2">
            <GroupHeader
              icon={<CalendarDays className="size-4" />}
              label="Upcoming"
              count={groups.upcoming.length}
              variant="neutral"
            />
            <MyWorkTaskTable
              tasks={groups.upcoming}
              slug={slug}
              columnSet="full"
              overdueTone="never"
              onTaskClick={onTaskClick}
            />
          </div>
        )}

        {hasNoDueDate && (
          <div className="space-y-2">
            <GroupHeader
              icon={<CalendarDays className="size-4" />}
              label="No Due Date"
              count={groups.noDueDate.length}
              variant="neutral"
            />
            <MyWorkTaskTable
              tasks={groups.noDueDate}
              slug={slug}
              columnSet="full"
              overdueTone="never"
              onTaskClick={onTaskClick}
            />
          </div>
        )}
      </div>
    </div>
  );
}
