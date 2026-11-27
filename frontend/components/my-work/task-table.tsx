"use client";

import { useMemo, type MouseEvent } from "react";
import { AlertTriangle } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { daysBetween, todayInSA } from "@b2mash/shared/date";
import { DataTable } from "@/components/data-table/data-table";
import type {
  DataTableCallbackPagination,
  DataTableColumn,
} from "@/components/data-table/define-columns";
import { formatDateInSA, formatDuration } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { MyWorkTaskItem, TaskPriority, TaskStatus } from "@/lib/types";

/**
 * My Work's task tables on the DataTable kit (Phase 91 E8A, K-09). Shared by the urgency groups,
 * Available Tasks and the assigned list so the three do not triplicate the columns.
 *
 * K-09: the tables sit in a 668 px column at 1440 and 419 px at 1024 (rail expanded; OBS-before),
 * so columns are shown by the table frame's own width (the kit frame is the `@container`), never by
 * kit `priority` (which starts at 67rem and would hide them at every width, D-73/D-85). Matter,
 * Title and Due Date always show.
 */
const FROM_34REM = "hidden @min-[34rem]:table-cell"; // Priority: frame ≥ 544 px
const FROM_40REM = "hidden @min-[40rem]:table-cell"; // Status: frame ≥ 640 px (shown at 1440)
const FROM_46REM = "hidden @min-[46rem]:table-cell"; // Logged: frame ≥ 736 px

/** The matter badge truncates, with its full name on hover (the no-wrap badge set 1 019 px). */
const MATTER_BADGE_CLASS = "block max-w-28 truncate @min-[40rem]:max-w-40";

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

/** Due before South African today — identical on the SSR server (UTC) and in the browser. */
export function isTaskOverdue(dueDate: string | null, status: string): boolean {
  if (!dueDate || status === "DONE" || status === "CANCELLED") return false;
  return dueDate.slice(0, 10) < todayInSA();
}

/**
 * Which columns a list shows: `"full"` (urgency groups + assigned list) = Matter · Title · Priority
 * · Status · Due Date · Logged; `"available"` = Matter · Title · Priority · Due Date (as today).
 */
export type TaskColumnSet = "full" | "available";

/**
 * Red date + warning icon, as today: `"per-task"` (available + assigned: `isTaskOverdue`),
 * `"group"` (the urgency Overdue group: every row, plus the "{n}d overdue" badge), `"never"` (the
 * other urgency groups).
 */
export type TaskOverdueTone = "per-task" | "group" | "never";

/** The title button's data attribute (`data-task-open`): the delegated row click reads the id. */
const OPEN_ATTR = "data-task-open";

/** Clicks on these (or inside them) keep their own behaviour (the kit's `INTERACTIVE_SELECTOR`). */
const INTERACTIVE_SELECTOR =
  'a, button, input, select, textarea, label, [role="menu"], [role="menuitem"], [role="menuitemcheckbox"], [role="menuitemradio"], [data-row-click="ignore"]';

const getTaskId = (task: MyWorkTaskItem) => task.id;

function buildColumns(
  set: TaskColumnSet,
  tone: TaskOverdueTone,
  onTaskClick: ((taskId: string) => void) | undefined
): DataTableColumn<MyWorkTaskItem>[] {
  const matter: DataTableColumn<MyWorkTaskItem> = {
    id: "matter",
    meta: { label: "Matter" },
    cell: ({ row }) => (
      <Badge variant="member" className={MATTER_BADGE_CLASS} title={row.original.matterName}>
        {row.original.matterName}
      </Badge>
    ),
  };

  // The flexible column: takes the spare width and truncates (`w-full max-w-0` + an 8rem floor).
  const title: DataTableColumn<MyWorkTaskItem> = {
    id: "title",
    meta: {
      label: "Title",
      headerClassName: "w-full max-w-0",
      cellClassName: "w-full max-w-0",
      minWidth: "8rem",
    },
    cell: ({ row }) => {
      const task = row.original;
      const titleClass = "text-foreground min-w-0 truncate text-sm font-medium";
      return (
        <div className="flex min-w-0 items-center gap-2">
          {onTaskClick ? (
            <button
              type="button"
              data-task-open={task.id}
              onClick={() => onTaskClick(task.id)}
              title={task.title}
              className={cn(
                titleClass,
                "focus-visible:ring-ring rounded-sm text-left outline-none focus-visible:ring-2"
              )}
            >
              {task.title}
            </button>
          ) : (
            <span className={titleClass} title={task.title}>
              {task.title}
            </span>
          )}
          {tone === "group" && task.dueDate && (
            <Badge variant="destructive" className="shrink-0">
              {daysBetween(task.dueDate.slice(0, 10), todayInSA())}d overdue
            </Badge>
          )}
        </div>
      );
    },
  };

  const priority: DataTableColumn<MyWorkTaskItem> = {
    id: "priority",
    meta: { label: "Priority", headerClassName: FROM_34REM, cellClassName: FROM_34REM },
    cell: ({ row }) => {
      const badge = PRIORITY_BADGE[row.original.priority as TaskPriority] ?? PRIORITY_BADGE.MEDIUM;
      return <Badge variant={badge.variant}>{badge.label}</Badge>;
    },
  };

  const status: DataTableColumn<MyWorkTaskItem> = {
    id: "status",
    meta: { label: "Status", headerClassName: FROM_40REM, cellClassName: FROM_40REM },
    cell: ({ row }) => {
      const badge = STATUS_BADGE[row.original.status as TaskStatus] ?? STATUS_BADGE.OPEN;
      return <Badge variant={badge.variant}>{badge.label}</Badge>;
    },
  };

  const dueDate: DataTableColumn<MyWorkTaskItem> = {
    id: "dueDate",
    meta: { label: "Due Date" },
    cell: ({ row }) => {
      const task = row.original;
      const overdue =
        tone === "group" || (tone === "per-task" && isTaskOverdue(task.dueDate, task.status));
      return (
        <span
          className={cn(
            "inline-flex items-center gap-1 text-sm",
            overdue ? "text-destructive font-medium" : "text-muted-foreground"
          )}
        >
          {overdue && <AlertTriangle aria-hidden="true" className="size-3.5 shrink-0" />}
          {task.dueDate ? formatDateInSA(task.dueDate) : "—"}
        </span>
      );
    },
  };

  const logged: DataTableColumn<MyWorkTaskItem> = {
    id: "logged",
    meta: { label: "Logged", headerClassName: FROM_46REM, cellClassName: FROM_46REM },
    cell: ({ row }) => (
      <span className="text-muted-foreground text-sm">
        {row.original.totalTimeMinutes > 0 ? formatDuration(row.original.totalTimeMinutes) : "—"}
      </span>
    ),
  };

  return set === "available"
    ? [matter, title, priority, dueDate]
    : [matter, title, priority, status, dueDate, logged];
}

interface MyWorkTaskTableProps {
  /** Pass a stable (memoised) array: a new identity resets the kit's table state. */
  tasks: MyWorkTaskItem[];
  slug: string;
  columns: TaskColumnSet;
  overdueTone: TaskOverdueTone;
  /** Opens the task Sheet; absent → a row click opens the task's matter (kit `rowHref`). */
  onTaskClick?: (taskId: string) => void;
  /** Callback pager (K-97); the kit shows it only when `pageCount > 1`. */
  pagination?: DataTableCallbackPagination;
  className?: string;
}

/**
 * One task table. Row activation: without `onTaskClick` the kit's `rowHref` opens the matter. With
 * it, the kit has no callback twin of `rowHref` (found.md, kit follow-up), so the title is a real
 * `<button>` (keyboard path) and a click anywhere else on the row is delegated to it here, with the
 * kit's own guards: left button, inside a body row, not on an interactive element, no text selected.
 */
export function MyWorkTaskTable({
  tasks,
  slug,
  columns: set,
  overdueTone,
  onTaskClick,
  pagination,
  className,
}: MyWorkTaskTableProps) {
  const openable = onTaskClick !== undefined;
  const columns = useMemo(
    () => buildColumns(set, overdueTone, onTaskClick),
    [set, overdueTone, onTaskClick]
  );

  function handleClick(event: MouseEvent<HTMLDivElement>) {
    if (!onTaskClick || event.defaultPrevented || event.button !== 0) return;
    const target = event.target;
    if (!(target instanceof Element) || !event.currentTarget.contains(target)) return;
    if (target.closest(INTERACTIVE_SELECTOR)) return;
    if (window.getSelection()?.toString()) return;
    const opener = target.closest("tbody tr")?.querySelector(`[${OPEN_ATTR}]`);
    const taskId = opener?.getAttribute(OPEN_ATTR);
    if (taskId) onTaskClick(taskId);
  }

  const rowHref = openable
    ? undefined
    : (task: MyWorkTaskItem) => `/org/${slug}/matters/${task.matterId}`;
  const shared = {
    columns,
    data: tasks,
    getRowId: getTaskId,
    rowHref,
    stickyHeader: false,
    empty: null,
  };

  return (
    // Delegated row click (see above): the title button is the keyboard path.
    <div className={cn(openable && "[&_tbody_tr]:cursor-pointer", className)} onClick={handleClick}>
      {pagination ? (
        <DataTable {...shared} pagination={pagination} />
      ) : (
        // No "Columns" menu: no new chrome (the pager variant turns it off by itself).
        <DataTable {...shared} columnVisibility={false} />
      )}
    </div>
  );
}
