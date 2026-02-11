"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronDown, ChevronRight, Inbox, AlertTriangle } from "lucide-react";
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
import { formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { MyWorkTaskItem, TaskPriority } from "@/lib/types";

// --- Priority badge config ---

const PRIORITY_BADGE: Record<
  TaskPriority,
  { label: string; variant: "destructive" | "warning" | "neutral" }
> = {
  HIGH: { label: "High", variant: "destructive" },
  MEDIUM: { label: "Medium", variant: "warning" },
  LOW: { label: "Low", variant: "neutral" },
};

// --- Overdue helper ---

function isOverdue(dueDate: string | null, status: string): boolean {
  if (!dueDate || status === "DONE" || status === "CANCELLED") return false;
  const due = new Date(dueDate);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return due < today;
}

// --- Component ---

interface AvailableTaskListProps {
  tasks: MyWorkTaskItem[];
  slug: string;
}

export function AvailableTaskList({ tasks, slug }: AvailableTaskListProps) {
  const router = useRouter();
  const [isOpen, setIsOpen] = useState(tasks.length > 0);

  return (
    <div className="space-y-4">
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-2"
      >
        {isOpen ? (
          <ChevronDown className="size-4 text-olive-500" />
        ) : (
          <ChevronRight className="size-4 text-olive-500" />
        )}
        <h2 className="font-semibold text-olive-900 dark:text-olive-100">
          Available Tasks
        </h2>
        <Badge variant="neutral">{tasks.length}</Badge>
      </button>

      {isOpen && (
        <>
          {tasks.length === 0 ? (
            <EmptyState
              icon={Inbox}
              title="No available tasks"
              description="Unassigned tasks in your projects will appear here"
            />
          ) : (
            <div className="rounded-lg border border-olive-200 dark:border-olive-800">
              <Table>
                <TableHeader>
                  <TableRow className="border-olive-200 hover:bg-transparent dark:border-olive-800">
                    <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                      Project
                    </TableHead>
                    <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                      Title
                    </TableHead>
                    <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                      Priority
                    </TableHead>
                    <TableHead className="hidden text-xs uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                      Due Date
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {tasks.map((task) => {
                    const priorityBadge =
                      PRIORITY_BADGE[task.priority as TaskPriority] ??
                      PRIORITY_BADGE.MEDIUM;
                    const overdue = isOverdue(task.dueDate, task.status);

                    return (
                      <TableRow
                        key={task.id}
                        className="cursor-pointer border-olive-100 transition-colors hover:bg-olive-50 dark:border-olive-800/50 dark:hover:bg-olive-900"
                        onClick={() =>
                          router.push(`/org/${slug}/projects/${task.projectId}`)
                        }
                      >
                        <TableCell>
                          <Badge variant="member">{task.projectName}</Badge>
                        </TableCell>
                        <TableCell>
                          <p className="truncate text-sm font-medium text-olive-950 dark:text-olive-50">
                            {task.title}
                          </p>
                        </TableCell>
                        <TableCell>
                          <Badge variant={priorityBadge.variant}>
                            {priorityBadge.label}
                          </Badge>
                        </TableCell>
                        <TableCell className="hidden sm:table-cell">
                          <span
                            className={cn(
                              "inline-flex items-center gap-1 text-sm",
                              overdue
                                ? "font-medium text-red-600 dark:text-red-400"
                                : "text-olive-600 dark:text-olive-400"
                            )}
                          >
                            {overdue && (
                              <AlertTriangle className="size-3.5 shrink-0" />
                            )}
                            {task.dueDate
                              ? formatDate(task.dueDate)
                              : "\u2014"}
                          </span>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </>
      )}
    </div>
  );
}
