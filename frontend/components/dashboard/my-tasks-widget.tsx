import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { formatLocalDate } from "@/lib/format";
import type { MyWorkTaskItem, MyWorkTasksResponse } from "@/lib/types";
import {
  WIDGET_LINK_CLASS,
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
    <Card data-testid="my-tasks-widget" className="gap-4">
      <CardHeader>
        <CardTitle className="text-sm font-medium">My tasks</CardTitle>
        <CardAction>
          <Link href={`/org/${slug}/my-work`} className={WIDGET_LINK_CLASS}>
            Open my work
          </Link>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        {data === null ? (
          <WidgetErrorLine subject="Your tasks" />
        ) : open.length === 0 ? (
          <p className="text-sm text-slate-400">
            Nothing assigned to you. Tasks assigned to you on a matter appear here.
          </p>
        ) : (
          <>
            <div>
              <WidgetHeadline testId="my-tasks-headline">{open.length}</WidgetHeadline>
              <p className="text-xs text-slate-500">
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
                      className="focus-visible:ring-ring flex items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none hover:bg-slate-50 focus-visible:ring-2 dark:hover:bg-slate-900"
                    >
                      <span className="min-w-0 flex-1">
                        <span className="block truncate">{task.title}</span>
                        <span className="block truncate text-[11px] text-slate-500">
                          {task.matterName}
                        </span>
                      </span>
                      {overdue && <Badge variant="destructive">Overdue</Badge>}
                      <span className="w-24 text-right font-mono text-xs text-slate-600 tabular-nums dark:text-slate-400">
                        {task.dueDate ? formatLocalDate(task.dueDate) : "No due date"}
                      </span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </>
        )}
      </CardContent>
    </Card>
  );
}
