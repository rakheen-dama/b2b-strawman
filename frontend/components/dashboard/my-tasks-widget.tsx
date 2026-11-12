import Link from "next/link";
import { AlertCircle, ListChecks } from "lucide-react";
import { EmptyState } from "@/components/empty-state";
import { formatLocalDate } from "@/lib/format";
import type { MyWorkTaskItem, MyWorkTasksResponse } from "@/lib/types";
import {
  StatusBadge,
  WIDGET_LINK_CLASS,
  WidgetCard,
  WidgetErrorLine,
  WidgetHeadline,
} from "@/components/dashboard/widget-parts";

const MAX_ROWS = 5;

interface MyTasksWidgetProps {
  slug: string;
  /** Server "today" (yyyy-MM-dd) — the overdue cut-off. */
  today: string;
  /** `null` = the read failed. */
  data: MyWorkTasksResponse | null;
}

function isOpen(task: MyWorkTaskItem): boolean {
  return task.status !== "DONE" && task.status !== "CANCELLED";
}

/** Due date ascending, undated last. */
function byDueDate(a: MyWorkTaskItem, b: MyWorkTaskItem): number {
  if (a.dueDate === b.dueDate) return 0;
  if (a.dueDate === null) return 1;
  if (b.dueDate === null) return -1;
  return a.dueDate < b.dueDate ? -1 : 1;
}

/**
 * My tasks (614B, arch §12.6.3, fee-earner row 2): the caller's open assigned tasks, soonest due
 * first, top 5. My work owns the full list.
 */
export function MyTasksWidget({ slug, today, data }: MyTasksWidgetProps) {
  const open = data ? data.assigned.filter(isOpen).sort(byDueDate) : [];
  const overdueCount = open.filter((t) => t.dueDate !== null && t.dueDate < today).length;
  return (
    <WidgetCard
      data-testid="my-tasks-widget"
      title="My tasks"
      action={
        <Link href={`/org/${slug}/my-work`} className={WIDGET_LINK_CLASS}>
          Open my work
        </Link>
      }
      contentProps={{ className: "space-y-3" }}
    >
      {data === null ? (
        <WidgetErrorLine subject="Your tasks" />
      ) : open.length === 0 ? (
        <EmptyState
          variant="compact"
          icon={ListChecks}
          title="Nothing assigned to you."
          description="Tasks assigned to you on a matter appear here."
        />
      ) : (
        <>
          <div>
            <WidgetHeadline testId="my-tasks-headline">{open.length}</WidgetHeadline>
            <p className="text-muted-foreground text-xs">
              open {open.length === 1 ? "task" : "tasks"}
              {overdueCount > 0 && `, ${overdueCount} overdue`}
            </p>
          </div>
          <ul className="space-y-0.5" aria-label="My open tasks">
            {open.slice(0, MAX_ROWS).map((task) => {
              const overdue = task.dueDate !== null && task.dueDate < today;
              return (
                <li key={task.id}>
                  <Link
                    href={`/org/${slug}/matters/${task.matterId}?taskId=${task.id}`}
                    className="hover:bg-muted focus-visible:ring-ring flex items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none focus-visible:ring-2"
                  >
                    <span className="min-w-0 flex-1">
                      <span className="text-foreground block truncate">{task.title}</span>
                      <span className="text-muted-foreground block truncate text-[11px]">
                        {task.matterName}
                      </span>
                    </span>
                    {overdue && (
                      <StatusBadge tone="danger" icon={<AlertCircle />}>
                        Overdue
                      </StatusBadge>
                    )}
                    <span className="text-muted-foreground w-24 text-right font-mono text-xs tabular-nums">
                      {task.dueDate ? formatLocalDate(task.dueDate) : "No due date"}
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </WidgetCard>
  );
}
