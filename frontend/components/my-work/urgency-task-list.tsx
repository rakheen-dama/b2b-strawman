"use client";

import { CheckCircle, AlertTriangle, Clock, CalendarDays } from "lucide-react";
import { useRouter } from "next/navigation";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/empty-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDuration, formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { MyWorkTaskItem, TaskPriority, TaskStatus } from "@/lib/types";

// --- Priority badge config ---

const PRIORITY_BADGE: Record<
  TaskPriority,
  { label: string; variant: "destructive" | "warning" | "neutral" }
> = {
  HIGH: { label: "High", variant: "destructive" },
  MEDIUM: { label: "Medium", variant: "warning" },
  LOW: { label: "Low", variant: "neutral" },
};

const STATUS_BADGE: Record<
  TaskStatus,
  { label: string; variant: "success" | "warning" | "neutral" | "destructive" }
> = {
  OPEN: { label: "Open", variant: "neutral" },
  IN_PROGRESS: { label: "In Progress", variant: "warning" },
  DONE: { label: "Done", variant: "success" },
  CANCELLED: { label: "Cancelled", variant: "destructive" },
};

// --- Urgency grouping ---

interface UrgencyGroups {
  overdue: MyWorkTaskItem[];
  dueThisWeek: MyWorkTaskItem[];
  upcoming: MyWorkTaskItem[];
  noDueDate: MyWorkTaskItem[];
}

export function groupByUrgency(tasks: MyWorkTaskItem[]): UrgencyGroups {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  // Calculate end of current week (Sunday)
  const day = today.getDay();
  const diff = day === 0 ? 0 : 7 - day;
  const endOfWeek = new Date(today);
  endOfWeek.setDate(today.getDate() + diff);
  endOfWeek.setHours(23, 59, 59, 999);

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
    const [y, m, d] = task.dueDate.split("-").map(Number);
    const due = new Date(y, m - 1, d);
    if (due < today) {
      overdue.push(task);
    } else if (due <= endOfWeek) {
      dueThisWeek.push(task);
    } else {
      upcoming.push(task);
    }
  }

  // Sort overdue by most overdue first (earliest due date first)
  overdue.sort((a, b) => {
    const [ay, am, ad] = a.dueDate!.split("-").map(Number);
    const [by, bm, bd] = b.dueDate!.split("-").map(Number);
    const aDate = new Date(ay, am - 1, ad);
    const bDate = new Date(by, bm - 1, bd);
    return aDate.getTime() - bDate.getTime();
  });

  return { overdue, dueThisWeek, upcoming, noDueDate };
}

function getOverdueDays(dueDate: string): number {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const [y, m, d] = dueDate.split("-").map(Number);
  const due = new Date(y, m - 1, d);
  const diffMs = today.getTime() - due.getTime();
  return Math.ceil(diffMs / (1000 * 60 * 60 * 24));
}

// --- Task table for a group ---

interface TaskGroupTableProps {
  tasks: MyWorkTaskItem[];
  slug: string;
  isOverdue?: boolean;
}

function TaskGroupTable({ tasks, slug, isOverdue }: TaskGroupTableProps) {
  const router = useRouter();

  return (
    <div className="rounded-lg border border-slate-200 dark:border-slate-800">
      <Table>
        <TableHeader>
          <TableRow className="border-slate-200 hover:bg-transparent dark:border-slate-800">
            <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Project
            </TableHead>
            <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Title
            </TableHead>
            <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Priority
            </TableHead>
            <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Status
            </TableHead>
            <TableHead className="hidden text-xs uppercase tracking-wide text-slate-600 sm:table-cell dark:text-slate-400">
              Due Date
            </TableHead>
            <TableHead className="hidden text-xs uppercase tracking-wide text-slate-600 sm:table-cell dark:text-slate-400">
              Logged
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {tasks.map((task) => {
            const priorityBadge =
              PRIORITY_BADGE[task.priority as TaskPriority] ??
              PRIORITY_BADGE.MEDIUM;
            const statusBadge =
              STATUS_BADGE[task.status as TaskStatus] ?? STATUS_BADGE.OPEN;

            return (
              <TableRow
                key={task.id}
                className={cn(
                  "cursor-pointer border-slate-100 transition-colors hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900",
                  isOverdue && "bg-red-50/30 dark:bg-red-950/10"
                )}
                onClick={() =>
                  router.push(`/org/${slug}/projects/${task.projectId}`)
                }
              >
                <TableCell>
                  <Badge variant="member">{task.projectName}</Badge>
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <p className="truncate text-sm font-medium text-slate-950 dark:text-slate-50">
                      {task.title}
                    </p>
                    {isOverdue && task.dueDate && (
                      <Badge variant="destructive" className="shrink-0">
                        {getOverdueDays(task.dueDate)}d overdue
                      </Badge>
                    )}
                  </div>
                </TableCell>
                <TableCell>
                  <Badge variant={priorityBadge.variant}>
                    {priorityBadge.label}
                  </Badge>
                </TableCell>
                <TableCell>
                  <Badge variant={statusBadge.variant}>
                    {statusBadge.label}
                  </Badge>
                </TableCell>
                <TableCell className="hidden sm:table-cell">
                  <span
                    className={cn(
                      "inline-flex items-center gap-1 text-sm",
                      isOverdue
                        ? "font-medium text-red-600 dark:text-red-400"
                        : "text-slate-600 dark:text-slate-400"
                    )}
                  >
                    {isOverdue && (
                      <AlertTriangle className="size-3.5 shrink-0" />
                    )}
                    {task.dueDate ? formatDate(task.dueDate) : "\u2014"}
                  </span>
                </TableCell>
                <TableCell className="hidden sm:table-cell">
                  <span className="text-sm text-slate-600 dark:text-slate-400">
                    {task.totalTimeMinutes > 0
                      ? formatDuration(task.totalTimeMinutes)
                      : "\u2014"}
                  </span>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
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
      <h3
        className={cn(
          "text-sm font-semibold",
          colorClasses[variant]
        )}
      >
        {label}
      </h3>
      <Badge variant={variant}>{count}</Badge>
    </div>
  );
}

// --- Main Component ---

interface UrgencyTaskListProps {
  tasks: MyWorkTaskItem[];
  slug: string;
}

export function UrgencyTaskList({ tasks, slug }: UrgencyTaskListProps) {
  if (tasks.length === 0) {
    return (
      <div className="space-y-4">
        <div className="flex items-center gap-2">
          <h2 className="font-semibold text-slate-900 dark:text-slate-100">
            My Tasks
          </h2>
        </div>
        <EmptyState
          icon={CheckCircle}
          title="No tasks assigned"
          description="Tasks assigned to you will appear here"
        />
      </div>
    );
  }

  const groups = groupByUrgency(tasks);
  const hasOverdue = groups.overdue.length > 0;
  const hasDueThisWeek = groups.dueThisWeek.length > 0;
  const hasUpcoming = groups.upcoming.length > 0;
  const hasNoDueDate = groups.noDueDate.length > 0;

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <h2 className="font-semibold text-slate-900 dark:text-slate-100">
          My Tasks
        </h2>
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
            <TaskGroupTable
              tasks={groups.overdue}
              slug={slug}
              isOverdue
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
            <TaskGroupTable tasks={groups.dueThisWeek} slug={slug} />
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
            <TaskGroupTable tasks={groups.upcoming} slug={slug} />
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
            <TaskGroupTable tasks={groups.noDueDate} slug={slug} />
          </div>
        )}
      </div>
    </div>
  );
}
