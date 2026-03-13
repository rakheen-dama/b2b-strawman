"use client";

import { AlertTriangle, Check, Clock, Hand, Repeat, RotateCcw, Undo2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { LogTimeDialog } from "@/components/tasks/log-time-dialog";
import {
  TableCell,
  TableRow,
} from "@/components/ui/table";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { formatDate } from "@/lib/format";
import { PRIORITY_BADGE, STATUS_BADGE } from "@/components/tasks/task-badge-config";
import { describeRecurrence } from "@/lib/recurrence";
import type { Task, RetainerSummaryResponse } from "@/lib/types";

function isOverdue(dueDate: string | null, status: string): boolean {
  if (!dueDate || status === "DONE" || status === "CANCELLED") return false;
  const due = new Date(dueDate);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return due < today;
}

interface TaskListTableRowProps {
  task: Task;
  slug: string;
  projectId: string;
  currentMemberId: string | null;
  canManage: boolean;
  retainerSummary?: RetainerSummaryResponse | null;
  isActioning: boolean;
  onOpenTask: (id: string) => void;
  onClaim: (id: string) => void;
  onRelease: (id: string) => void;
  onComplete: (id: string) => void;
  onReopen: (id: string) => void;
}

export function TaskListTableRow({
  task,
  slug,
  projectId,
  currentMemberId,
  canManage,
  retainerSummary,
  isActioning,
  onOpenTask,
  onClaim,
  onRelease,
  onComplete,
  onReopen,
}: TaskListTableRowProps) {
  const priorityBadge = PRIORITY_BADGE[task.priority];
  const statusBadge = STATUS_BADGE[task.status];
  const overdue = isOverdue(task.dueDate, task.status);
  const isTerminal = task.status === "DONE" || task.status === "CANCELLED";

  const canClaim = task.status === "OPEN" && !task.assigneeId;
  const isOwnTask =
    task.status === "IN_PROGRESS" &&
    task.assigneeId != null &&
    currentMemberId != null &&
    task.assigneeId === currentMemberId;
  const canMarkDone = task.status === "IN_PROGRESS" && (isOwnTask || canManage);

  return (
    <TableRow className="border-slate-100 transition-colors hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900">
      <TableCell>
        <Badge variant={priorityBadge.variant}>{priorityBadge.label}</Badge>
      </TableCell>
      <TableCell>
        <button
          type="button"
          className="flex min-w-0 items-center gap-1.5 cursor-pointer text-left"
          onClick={() => onOpenTask(task.id)}
          aria-label={`Open task detail for ${task.title}`}
        >
          <div className="min-w-0">
            <span className="flex items-center gap-1.5">
              <p className={cn(
                "truncate text-sm font-medium dark:text-slate-50",
                task.status === "DONE"
                  ? "line-through text-muted-foreground"
                  : task.status === "CANCELLED"
                    ? "text-muted-foreground"
                    : "text-slate-950 hover:text-teal-600",
              )}>
                {task.title}
              </p>
              {task.isRecurring && (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span className="inline-flex shrink-0 text-teal-500" aria-label="Recurring task">
                      <Repeat className="size-3.5" />
                    </span>
                  </TooltipTrigger>
                  <TooltipContent>
                    <p>{describeRecurrence(task.recurrenceRule)}</p>
                  </TooltipContent>
                </Tooltip>
              )}
            </span>
            {task.type && (
              <p className="truncate text-xs text-slate-500 dark:text-slate-500">{task.type}</p>
            )}
          </div>
        </button>
      </TableCell>
      <TableCell>
        <Badge variant={statusBadge.variant}>{statusBadge.label}</Badge>
      </TableCell>
      <TableCell className="hidden sm:table-cell">
        <span className="text-sm text-slate-600 dark:text-slate-400">
          {task.assigneeName ?? "Unassigned"}
        </span>
      </TableCell>
      <TableCell className="hidden sm:table-cell">
        <span className={cn(
          "inline-flex items-center gap-1 text-sm",
          overdue ? "font-medium text-red-600 dark:text-red-400" : "text-slate-600 dark:text-slate-400",
        )}>
          {overdue && <AlertTriangle className="size-3.5 shrink-0" />}
          {task.dueDate ? formatDate(task.dueDate) : "\u2014"}
        </span>
      </TableCell>
      <TableCell>
        <div className="flex items-center gap-1.5">
          {!isTerminal && (
            <LogTimeDialog
              slug={slug}
              projectId={projectId}
              taskId={task.id}
              memberId={currentMemberId}
              retainerSummary={retainerSummary}
            >
              <Button size="xs" variant="outline" onClick={(e) => e.stopPropagation()}>
                <Clock className="size-3" />
                Log Time
              </Button>
            </LogTimeDialog>
          )}
          {canClaim && (
            <Button size="xs" variant="default" disabled={isActioning} onClick={() => onClaim(task.id)}>
              <Hand className="size-3" />
              {isActioning ? "Claiming..." : "Claim"}
            </Button>
          )}
          {isOwnTask && (
            <Button size="xs" variant="ghost" disabled={isActioning} onClick={() => onRelease(task.id)}>
              <Undo2 className="size-3" />
              Release
            </Button>
          )}
          {canMarkDone && (
            <Button size="xs" variant="soft" disabled={isActioning} onClick={(e) => { e.stopPropagation(); onComplete(task.id); }}>
              <Check className="size-3" />
              Done
            </Button>
          )}
          {isTerminal && canManage && (
            <Button size="xs" variant="outline" disabled={isActioning} onClick={(e) => { e.stopPropagation(); onReopen(task.id); }}>
              <RotateCcw className="size-3" />
              Reopen
            </Button>
          )}
        </div>
      </TableCell>
    </TableRow>
  );
}
