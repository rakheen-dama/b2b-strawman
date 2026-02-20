"use client";

import { Suspense, useEffect, useState, useTransition } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AlertTriangle, Check, Clock, ClipboardList, Hand, Plus, Undo2 } from "lucide-react";
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
import { LogTimeDialog } from "@/components/tasks/log-time-dialog";
import { TaskDetailSheet } from "@/components/tasks/task-detail-sheet";
import { formatDate } from "@/lib/format";
import {
  claimTask,
  releaseTask,
  updateTask,
  fetchTasks,
} from "@/app/(app)/org/[slug]/projects/[id]/task-actions";
import { cn } from "@/lib/utils";
import { PRIORITY_BADGE, STATUS_BADGE } from "@/components/tasks/task-badge-config";
import { ViewSelectorClient } from "@/components/views/ViewSelectorClient";
import type {
  Task,
  TaskStatus,
  RetainerSummaryResponse,
  TagResponse,
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
  SavedViewResponse,
  CreateSavedViewRequest,
} from "@/lib/types";

// --- Filter types (40.7) ---

type FilterKey = "all" | "OPEN" | "IN_PROGRESS" | "DONE" | "my";

const FILTER_OPTIONS: { key: FilterKey; label: string }[] = [
  { key: "all", label: "All" },
  { key: "OPEN", label: "Open" },
  { key: "IN_PROGRESS", label: "In Progress" },
  { key: "DONE", label: "Done" },
  { key: "my", label: "My Tasks" },
];

// --- Overdue helper (40.8) ---

function isOverdue(dueDate: string | null, status: TaskStatus): boolean {
  if (!dueDate || status === "DONE" || status === "CANCELLED") return false;
  const due = new Date(dueDate);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return due < today;
}

// --- Component ---

interface TaskListPanelProps {
  tasks: Task[];
  slug: string;
  projectId: string;
  canManage: boolean;
  currentMemberId: string | null;
  orgRole?: string | null;
  retainerSummary?: RetainerSummaryResponse | null;
  members?: { id: string; name: string; email: string }[];
  allTags?: TagResponse[];
  fieldDefinitions?: FieldDefinitionResponse[];
  fieldGroups?: FieldGroupResponse[];
  groupMembers?: Record<string, FieldGroupMemberResponse[]>;
  savedViews?: SavedViewResponse[];
  onSave?: (req: CreateSavedViewRequest) => Promise<{ success: boolean; error?: string }>;
}

export function TaskListPanel({
  tasks: initialTasks,
  slug,
  projectId,
  canManage,
  currentMemberId,
  orgRole,
  retainerSummary,
  members = [],
  allTags = [],
  fieldDefinitions = [],
  fieldGroups = [],
  groupMembers = {},
  savedViews = [],
  onSave,
}: TaskListPanelProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const selectedTaskId = searchParams.get("taskId");
  const [activeFilter, setActiveFilter] = useState<FilterKey>("all");
  const [tasks, setTasks] = useState<Task[]>(initialTasks);
  const [isPending, startTransition] = useTransition();
  const [actionTaskId, setActionTaskId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Sync state when parent Server Component re-renders with fresh data.
  useEffect(() => {
    setTasks(initialTasks);
  }, [initialTasks]);

  // --- URL state helpers ---

  function openTask(id: string) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("taskId", id);
    router.push(`?${params.toString()}`, { scroll: false });
  }

  function closeTask() {
    const params = new URLSearchParams(searchParams.toString());
    params.delete("taskId");
    router.push(`?${params.toString()}`, { scroll: false });
  }

  // --- Filter handler (40.7) ---

  function handleFilterChange(key: FilterKey) {
    setActiveFilter(key);
    setError(null);

    const currentViewId = searchParams.get("view");

    startTransition(async () => {
      try {
        const filters: { status?: string; assigneeId?: string; viewId?: string } = {};
        if (key === "OPEN" || key === "IN_PROGRESS" || key === "DONE") {
          filters.status = key;
        } else if (key === "my") {
          if (!currentMemberId) {
            setTasks([]);
            return;
          }
          filters.assigneeId = currentMemberId;
        }
        if (currentViewId) {
          filters.viewId = currentViewId;
        }
        const fetched = await fetchTasks(projectId, filters);
        setTasks(fetched);
      } catch {
        setError("Failed to fetch tasks.");
      }
    });
  }

  // --- Claim handler (40.6 + 40.9) ---

  function handleClaim(taskId: string) {
    setError(null);
    setActionTaskId(taskId);

    startTransition(async () => {
      try {
        const result = await claimTask(slug, taskId, projectId);
        if (!result.success) {
          // 40.9: conflict toast + refresh
          setError(result.error ?? "Failed to claim task.");
          router.refresh();
        } else {
          // Re-fetch to reflect new state
          const fetched = await fetchTasks(projectId, buildCurrentFilters());
          setTasks(fetched);
        }
      } catch {
        setError("An unexpected error occurred.");
      } finally {
        setActionTaskId(null);
      }
    });
  }

  // --- Release handler (40.6) ---

  function handleRelease(taskId: string) {
    setError(null);
    setActionTaskId(taskId);

    startTransition(async () => {
      try {
        const result = await releaseTask(slug, taskId, projectId);
        if (!result.success) {
          setError(result.error ?? "Failed to release task.");
          router.refresh();
        } else {
          const fetched = await fetchTasks(projectId, buildCurrentFilters());
          setTasks(fetched);
        }
      } catch {
        setError("An unexpected error occurred.");
      } finally {
        setActionTaskId(null);
      }
    });
  }

  // --- Mark Done handler (40.6) ---

  function handleMarkDone(task: Task) {
    setError(null);
    setActionTaskId(task.id);

    startTransition(async () => {
      try {
        const result = await updateTask(slug, task.id, projectId, {
          title: task.title,
          description: task.description,
          priority: task.priority,
          status: "DONE",
          type: task.type,
          dueDate: task.dueDate,
          assigneeId: task.assigneeId,
        });
        if (!result.success) {
          setError(result.error ?? "Failed to mark task as done.");
          router.refresh();
        } else {
          const fetched = await fetchTasks(projectId, buildCurrentFilters());
          setTasks(fetched);
        }
      } catch {
        setError("An unexpected error occurred.");
      } finally {
        setActionTaskId(null);
      }
    });
  }

  function buildCurrentFilters(): { status?: string; assigneeId?: string; viewId?: string } {
    const filters: { status?: string; assigneeId?: string; viewId?: string } = {};
    if (
      activeFilter === "OPEN" ||
      activeFilter === "IN_PROGRESS" ||
      activeFilter === "DONE"
    ) {
      filters.status = activeFilter;
    } else if (activeFilter === "my" && currentMemberId) {
      filters.assigneeId = currentMemberId;
    }
    const currentViewId = searchParams.get("view");
    if (currentViewId) {
      filters.viewId = currentViewId;
    }
    return filters;
  }

  // --- Render ---

  const header = (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        <h2 className="font-semibold text-slate-900 dark:text-slate-100">Tasks</h2>
        {tasks.length > 0 && <Badge variant="neutral">{tasks.length}</Badge>}
      </div>
      {canManage && (
        <CreateTaskDialog slug={slug} projectId={projectId} members={members} canManage={canManage}>
          <Button size="sm" variant="outline">
            <Plus className="mr-1.5 size-4" />
            New Task
          </Button>
        </CreateTaskDialog>
      )}
    </div>
  );

  // 40.7: Status filter bar
  const filterBar = (
    <div className="flex flex-wrap gap-2" role="group" aria-label="Task filters">
      {FILTER_OPTIONS.map((option) => (
        <button
          key={option.key}
          type="button"
          onClick={() => handleFilterChange(option.key)}
          disabled={isPending}
          className={cn(
            "rounded-full px-3 py-1 text-sm font-medium transition-colors",
            activeFilter === option.key
              ? "bg-slate-900 text-slate-50 dark:bg-slate-100 dark:text-slate-900"
              : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700",
            isPending && "opacity-50",
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );

  const viewSelector = onSave ? (
    <Suspense fallback={null}>
      <ViewSelectorClient
        entityType="TASK"
        views={savedViews}
        canCreate={canManage}
        canCreateShared={orgRole === "org:admin" || orgRole === "org:owner"}
        slug={slug}
        allTags={allTags}
        fieldDefinitions={fieldDefinitions}
        onSave={onSave}
      />
    </Suspense>
  ) : null;

  if (tasks.length === 0 && activeFilter === "all") {
    return (
      <div className="space-y-4">
        {header}
        {viewSelector}
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
      {viewSelector}
      {filterBar}
      {error && (
        <p className="text-sm text-red-600 dark:text-red-400" role="alert">
          {error}
        </p>
      )}
      {tasks.length === 0 ? (
        <EmptyState
          icon={ClipboardList}
          title="No tasks match this filter"
          description="Try a different filter or clear the selection."
        />
      ) : (
        <div className="rounded-lg border border-slate-200 dark:border-slate-800">
          <Table>
            <TableHeader>
              <TableRow className="border-slate-200 hover:bg-transparent dark:border-slate-800">
                <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Priority
                </TableHead>
                <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Title
                </TableHead>
                <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Status
                </TableHead>
                <TableHead className="hidden text-xs uppercase tracking-wide text-slate-600 sm:table-cell dark:text-slate-400">
                  Assignee
                </TableHead>
                <TableHead className="hidden text-xs uppercase tracking-wide text-slate-600 sm:table-cell dark:text-slate-400">
                  Due Date
                </TableHead>
                <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Actions
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {tasks.map((task) => {
                const priorityBadge = PRIORITY_BADGE[task.priority];
                const statusBadge = STATUS_BADGE[task.status];
                const overdue = isOverdue(task.dueDate, task.status);
                const isActioning = actionTaskId === task.id && isPending;

                // 40.6: Determine available actions
                const canClaim =
                  task.status === "OPEN" && !task.assigneeId;
                const isOwnTask =
                  task.status === "IN_PROGRESS" &&
                  task.assigneeId != null &&
                  currentMemberId != null &&
                  task.assigneeId === currentMemberId;
                const canMarkDone =
                  task.status === "IN_PROGRESS" && (isOwnTask || canManage);

                return (
                  <TableRow
                    key={task.id}
                    className="border-slate-100 transition-colors hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900"
                  >
                    {/* 40.8: Priority badge */}
                    <TableCell>
                      <Badge variant={priorityBadge.variant}>
                        {priorityBadge.label}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <button
                        type="button"
                        className="flex min-w-0 items-center gap-1.5 cursor-pointer text-left"
                        onClick={() => openTask(task.id)}
                        aria-label={`Open task detail for ${task.title}`}
                      >
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium text-slate-950 hover:text-teal-600 dark:text-slate-50">
                            {task.title}
                          </p>
                          {task.type && (
                            <p className="truncate text-xs text-slate-500 dark:text-slate-500">
                              {task.type}
                            </p>
                          )}
                        </div>
                      </button>
                    </TableCell>
                    <TableCell>
                      <Badge variant={statusBadge.variant}>
                        {statusBadge.label}
                      </Badge>
                    </TableCell>
                    <TableCell className="hidden sm:table-cell">
                      <span className="text-sm text-slate-600 dark:text-slate-400">
                        {task.assigneeName ?? "Unassigned"}
                      </span>
                    </TableCell>
                    {/* 40.8: Due date with overdue styling */}
                    <TableCell className="hidden sm:table-cell">
                      <span
                        className={cn(
                          "inline-flex items-center gap-1 text-sm",
                          overdue
                            ? "font-medium text-red-600 dark:text-red-400"
                            : "text-slate-600 dark:text-slate-400",
                        )}
                      >
                        {overdue && (
                          <AlertTriangle className="size-3.5 shrink-0" />
                        )}
                        {task.dueDate ? formatDate(task.dueDate) : "\u2014"}
                      </span>
                    </TableCell>
                    {/* 40.6 + 45.5: Action buttons */}
                    <TableCell>
                      <div className="flex items-center gap-1.5">
                        <LogTimeDialog
                          slug={slug}
                          projectId={projectId}
                          taskId={task.id}
                          memberId={currentMemberId}
                          retainerSummary={retainerSummary}
                        >
                          <Button
                            size="xs"
                            variant="outline"
                            onClick={(e) => {
                              // Prevent row click when clicking Log Time button
                              e.stopPropagation();
                            }}
                          >
                            <Clock className="size-3" />
                            Log Time
                          </Button>
                        </LogTimeDialog>
                        {canClaim && (
                          <Button
                            size="xs"
                            variant="default"
                            disabled={isActioning}
                            onClick={() => handleClaim(task.id)}
                          >
                            <Hand className="size-3" />
                            {isActioning ? "Claiming..." : "Claim"}
                          </Button>
                        )}
                        {isOwnTask && (
                          <Button
                            size="xs"
                            variant="ghost"
                            disabled={isActioning}
                            onClick={() => handleRelease(task.id)}
                          >
                            <Undo2 className="size-3" />
                            Release
                          </Button>
                        )}
                        {canMarkDone && (
                          <Button
                            size="xs"
                            variant="soft"
                            disabled={isActioning}
                            onClick={(e) => {
                              e.stopPropagation();
                              handleMarkDone(task);
                            }}
                          >
                            <Check className="size-3" />
                            Done
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}
      <TaskDetailSheet
        taskId={selectedTaskId}
        onClose={closeTask}
        projectId={projectId}
        slug={slug}
        canManage={canManage}
        currentMemberId={currentMemberId ?? ""}
        orgRole={orgRole ?? ""}
        members={members}
        allTags={allTags}
        fieldDefinitions={fieldDefinitions}
        fieldGroups={fieldGroups}
        groupMembers={groupMembers}
      />
    </div>
  );
}
