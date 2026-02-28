import Link from "next/link";
import { CheckCircle2, Circle, Clock, AlertCircle } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { formatDuration, isOverdue, formatLocalDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { MyWorkTaskItem } from "@/lib/types";

const STATUS_ICON: Record<string, typeof Circle> = {
  TODO: Circle,
  IN_PROGRESS: Clock,
  DONE: CheckCircle2,
};

const PRIORITY_VARIANT: Record<string, "destructive" | "warning" | "neutral"> =
  {
    HIGH: "destructive",
    MEDIUM: "warning",
    LOW: "neutral",
  };

interface TaskGroupProps {
  title: string;
  tasks: MyWorkTaskItem[];
  orgSlug: string;
  emptyMessage?: string;
}

export function TaskGroup({
  title,
  tasks,
  orgSlug,
  emptyMessage = "No tasks",
}: TaskGroupProps) {
  // Group tasks by project
  const byProject = new Map<string, MyWorkTaskItem[]>();
  for (const task of tasks) {
    const existing = byProject.get(task.projectId) ?? [];
    existing.push(task);
    byProject.set(task.projectId, existing);
  }

  return (
    <div>
      <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-slate-500">
        {title}
        <span className="ml-1.5 font-mono tabular-nums text-slate-400">
          ({tasks.length})
        </span>
      </h2>

      {tasks.length === 0 ? (
        <p className="py-6 text-center text-sm text-slate-500 italic">
          {emptyMessage}
        </p>
      ) : (
        <div className="space-y-4">
          {Array.from(byProject.entries()).map(([projectId, projectTasks]) => {
            const projectName = projectTasks[0].projectName;
            return (
              <div key={projectId}>
                <Link
                  href={`/org/${orgSlug}/projects/${projectId}`}
                  className="mb-2 inline-flex items-center rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300"
                >
                  {projectName}
                </Link>
                <div className="space-y-1">
                  {projectTasks.map((task) => {
                    const StatusIcon = STATUS_ICON[task.status] ?? Circle;
                    const overdue =
                      task.dueDate !== null && isOverdue(task.dueDate);

                    return (
                      <Link
                        key={task.id}
                        href={`/org/${orgSlug}/projects/${task.projectId}?tab=tasks`}
                        className="flex items-center gap-3 rounded-md px-3 py-2.5 transition-colors hover:bg-slate-50 dark:hover:bg-slate-900"
                      >
                        <StatusIcon
                          className={cn(
                            "size-4 shrink-0",
                            task.status === "DONE"
                              ? "text-emerald-500"
                              : task.status === "IN_PROGRESS"
                                ? "text-teal-500"
                                : "text-slate-400",
                          )}
                        />
                        <div className="min-w-0 flex-1">
                          <span className="truncate text-sm font-medium text-slate-900 dark:text-slate-100">
                            {task.title}
                          </span>
                        </div>
                        <div className="flex shrink-0 items-center gap-2">
                          {task.priority && (
                            <Badge
                              variant={
                                PRIORITY_VARIANT[task.priority] ?? "neutral"
                              }
                            >
                              {task.priority}
                            </Badge>
                          )}
                          {task.dueDate && (
                            <span
                              className={cn(
                                "text-xs tabular-nums",
                                overdue
                                  ? "font-medium text-red-600"
                                  : "text-slate-500",
                              )}
                            >
                              {overdue && (
                                <AlertCircle className="mr-0.5 inline size-3" />
                              )}
                              {formatLocalDate(task.dueDate)}
                            </span>
                          )}
                          {task.totalTimeMinutes > 0 && (
                            <span className="font-mono text-xs tabular-nums text-slate-500">
                              {formatDuration(task.totalTimeMinutes)}
                            </span>
                          )}
                        </div>
                      </Link>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
