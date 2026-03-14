"use client";

import { Repeat } from "lucide-react";
import { AssigneeSelector } from "@/components/tasks/assignee-selector";
import { formatDate } from "@/lib/format";
import { describeRecurrence } from "@/lib/recurrence";
import type { Task } from "@/lib/types";

interface TaskDetailMetadataProps {
  task: Task;
  members: { id: string; name: string; email: string }[];
  canManage: boolean;
  isTerminal: boolean;
  onAssigneeChange: (assigneeId: string | null) => void;
}

export function TaskDetailMetadata({
  task,
  members,
  canManage,
  isTerminal,
  onAssigneeChange,
}: TaskDetailMetadataProps) {
  return (
    <div className="border-b border-slate-200 px-6 py-4 dark:border-slate-800">
      <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Assignee
          </dt>
          <dd className="mt-1">
            <AssigneeSelector
              members={members}
              currentAssigneeId={task.assigneeId}
              onAssigneeChange={onAssigneeChange}
              disabled={!canManage || isTerminal}
            />
          </dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Due Date
          </dt>
          <dd className="mt-1 text-sm text-slate-700 dark:text-slate-300">
            {task.dueDate ? formatDate(task.dueDate) : "\u2014"}
          </dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Created By
          </dt>
          <dd className="mt-1 text-sm text-slate-700 dark:text-slate-300">
            {task.createdByName ?? "\u2014"}
          </dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Created
          </dt>
          <dd className="mt-1 text-sm text-slate-700 dark:text-slate-300">
            {formatDate(task.createdAt)}
          </dd>
        </div>
        {/* Recurrence info */}
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
            <span className="inline-flex items-center gap-1">
              <Repeat className="size-3" />
              Recurrence
            </span>
          </dt>
          <dd className="mt-1 text-sm text-slate-700 dark:text-slate-300">
            {describeRecurrence(task.recurrenceRule)}
            {task.recurrenceEndDate && (
              <span className="ml-1 text-xs text-slate-500">
                (until {formatDate(task.recurrenceEndDate)})
              </span>
            )}
          </dd>
        </div>
        {task.parentTaskId && (
          <div>
            <dt className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Parent Task
            </dt>
            <dd className="mt-1">
              <button
                type="button"
                className="text-sm text-teal-600 hover:text-teal-700 hover:underline dark:text-teal-400"
                onClick={() => {
                  if (task.parentTaskId) {
                    const params = new URLSearchParams(window.location.search);
                    params.set("taskId", task.parentTaskId);
                    window.history.pushState(null, "", `?${params.toString()}`);
                    window.dispatchEvent(new PopStateEvent("popstate"));
                  }
                }}
              >
                View parent task
              </button>
            </dd>
          </div>
        )}
      </dl>
    </div>
  );
}
