"use client";

import { useState, useTransition } from "react";
import { Ban, Check, MoreHorizontal, RotateCcw, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { SheetClose } from "@/components/ui/sheet";
import { TaskStatusSelect } from "@/components/tasks/task-status-select";
import { PRIORITY_BADGE, STATUS_BADGE } from "@/components/tasks/task-badge-config";
import { formatDate } from "@/lib/format";
import {
  completeTask,
  cancelTask,
  reopenTask,
  fetchTask,
} from "@/app/(app)/org/[slug]/projects/[id]/task-actions";
import { toast } from "sonner";
import type { Task, TaskStatus } from "@/lib/types";

interface TaskDetailHeaderProps {
  task: Task;
  slug: string;
  projectId: string;
  canChangeStatus: boolean;
  isAdmin: boolean;
  onClose: () => void;
  onStatusChange: (status: TaskStatus) => void;
  onTaskUpdate: (task: Task) => void;
}

export function TaskDetailHeader({
  task,
  slug,
  projectId,
  canChangeStatus,
  isAdmin,
  onClose,
  onStatusChange,
  onTaskUpdate,
}: TaskDetailHeaderProps) {
  const [, startTransition] = useTransition();
  const [actionError, setActionError] = useState<string | null>(null);

  const isTerminal = task.status === "DONE" || task.status === "CANCELLED";
  const canMarkDone = task.status === "IN_PROGRESS" && canChangeStatus;

  const priorityBadge = PRIORITY_BADGE[task.priority];
  const statusBadge = STATUS_BADGE[task.status];

  function handleLifecycleAction(
    action: (
      slug: string,
      taskId: string,
      projectId: string
    ) => Promise<{
      success: boolean;
      error?: string;
      nextInstance?: Task | null;
    }>
  ) {
    setActionError(null);
    startTransition(async () => {
      const result = await action(slug, task.id, projectId);
      if (result.success) {
        if ("nextInstance" in result) {
          if (result.nextInstance) {
            const dueLabel = result.nextInstance.dueDate
              ? new Date(result.nextInstance.dueDate).toLocaleDateString(undefined, {
                  year: "numeric",
                  month: "short",
                  day: "numeric",
                })
              : "no due date";
            toast.success("Task completed", {
              description: `Next instance due ${dueLabel}`,
            });
          } else if (task.isRecurring) {
            toast.success("Task completed", {
              description: "Recurrence has ended",
            });
          }
        }
        try {
          const updated = await fetchTask(task.id);
          onTaskUpdate(updated);
        } catch {
          // Silently handle re-fetch failure
        }
      } else {
        setActionError(result.error ?? "Action failed. Please try again.");
      }
    });
  }

  return (
    <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-4 dark:border-slate-800">
      <div className="min-w-0 flex-1">
        <h2 className="text-base leading-snug font-semibold text-slate-950 dark:text-slate-50">
          {task.title}
        </h2>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          {canChangeStatus ? (
            <TaskStatusSelect value={task.status} onChange={onStatusChange} />
          ) : (
            statusBadge && <Badge variant={statusBadge.variant}>{statusBadge.label}</Badge>
          )}
          {priorityBadge && <Badge variant={priorityBadge.variant}>{priorityBadge.label}</Badge>}
          {task.type && (
            <span className="text-xs text-slate-500 dark:text-slate-400">{task.type}</span>
          )}
        </div>

        {/* Lifecycle action buttons */}
        {canChangeStatus && (
          <div className="mt-2 flex items-center gap-2">
            {canMarkDone && (
              <Button size="sm" variant="soft" onClick={() => handleLifecycleAction(completeTask)}>
                <Check className="mr-1 size-3" />
                Mark Done
              </Button>
            )}
            {isTerminal && canChangeStatus && (
              <Button size="sm" variant="outline" onClick={() => handleLifecycleAction(reopenTask)}>
                <RotateCcw className="mr-1 size-3" />
                Reopen
              </Button>
            )}
            {!isTerminal && isAdmin && (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="icon" className="size-7">
                    <MoreHorizontal className="size-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start">
                  <DropdownMenuItem
                    variant="destructive"
                    onClick={() => handleLifecycleAction(cancelTask)}
                  >
                    <Ban className="mr-2 size-4" />
                    Cancel Task
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            )}
          </div>
        )}

        {/* Action error feedback */}
        {actionError && (
          <p className="mt-2 text-xs text-red-600 dark:text-red-400" role="alert">
            {actionError}
          </p>
        )}

        {/* Completion / cancellation metadata */}
        {task.status === "DONE" && task.completedAt && (
          <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
            Completed{task.completedByName ? ` by ${task.completedByName}` : ""} on{" "}
            {formatDate(task.completedAt)}
          </p>
        )}
        {task.status === "CANCELLED" && task.cancelledAt && (
          <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
            Cancelled on {formatDate(task.cancelledAt)}
          </p>
        )}
      </div>
      <SheetClose asChild>
        <Button
          variant="ghost"
          size="icon"
          className="shrink-0"
          onClick={onClose}
          aria-label="Close task detail"
        >
          <X className="size-4" />
        </Button>
      </SheetClose>
    </div>
  );
}
