"use client";

import { ClipboardList, Plus } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { CreateTaskDialog } from "@/components/tasks/create-task-dialog";
import { formatDate } from "@/lib/format";
import type { Task, TaskPriority, TaskStatus } from "@/lib/types";

const PRIORITY_BADGE: Record<TaskPriority, { label: string; variant: "destructive" | "warning" | "neutral" }> = {
  HIGH: { label: "High", variant: "destructive" },
  MEDIUM: { label: "Medium", variant: "warning" },
  LOW: { label: "Low", variant: "neutral" },
};

const STATUS_BADGE: Record<TaskStatus, { label: string; variant: "success" | "warning" | "neutral" | "destructive" }> = {
  OPEN: { label: "Open", variant: "neutral" },
  IN_PROGRESS: { label: "In Progress", variant: "warning" },
  DONE: { label: "Done", variant: "success" },
  CANCELLED: { label: "Cancelled", variant: "destructive" },
};

interface TaskListPanelProps {
  tasks: Task[];
  slug: string;
  projectId: string;
  canManage: boolean;
}

export function TaskListPanel({ tasks, slug, projectId, canManage }: TaskListPanelProps) {
  const header = (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        <h2 className="font-semibold text-olive-900 dark:text-olive-100">Tasks</h2>
        {tasks.length > 0 && (
          <Badge variant="neutral">{tasks.length}</Badge>
        )}
      </div>
      {canManage && (
        <CreateTaskDialog slug={slug} projectId={projectId}>
          <Button size="sm" variant="outline">
            <Plus className="mr-1.5 size-4" />
            New Task
          </Button>
        </CreateTaskDialog>
      )}
    </div>
  );

  if (tasks.length === 0) {
    return (
      <div className="space-y-4">
        {header}
        <EmptyState
          icon={ClipboardList}
          title="No tasks yet"
          description="Create a task to start tracking work on this project"
        />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {header}
      <div className="rounded-lg border border-olive-200 dark:border-olive-800">
        <Table>
          <TableHeader>
            <TableRow className="border-olive-200 hover:bg-transparent dark:border-olive-800">
              <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                Priority
              </TableHead>
              <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                Title
              </TableHead>
              <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                Status
              </TableHead>
              <TableHead className="hidden text-xs uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                Assignee
              </TableHead>
              <TableHead className="hidden text-xs uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                Due Date
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {tasks.map((task) => {
              const priorityBadge = PRIORITY_BADGE[task.priority];
              const statusBadge = STATUS_BADGE[task.status];

              return (
                <TableRow
                  key={task.id}
                  className="border-olive-100 transition-colors hover:bg-olive-50 dark:border-olive-800/50 dark:hover:bg-olive-900"
                >
                  <TableCell>
                    <Badge variant={priorityBadge.variant}>{priorityBadge.label}</Badge>
                  </TableCell>
                  <TableCell>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-olive-950 dark:text-olive-50">
                        {task.title}
                      </p>
                      {task.type && (
                        <p className="truncate text-xs text-olive-500 dark:text-olive-500">
                          {task.type}
                        </p>
                      )}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusBadge.variant}>{statusBadge.label}</Badge>
                  </TableCell>
                  <TableCell className="hidden sm:table-cell">
                    <span className="text-sm text-olive-600 dark:text-olive-400">
                      {task.assigneeName ?? "Unassigned"}
                    </span>
                  </TableCell>
                  <TableCell className="hidden sm:table-cell">
                    <span className="text-sm text-olive-600 dark:text-olive-400">
                      {task.dueDate ? formatDate(task.dueDate) : "\u2014"}
                    </span>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
