"use client";

import { Suspense, useEffect, useState, useTransition } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AlertTriangle, Check, Clock, ClipboardList, Hand, Plus, Repeat, RotateCcw, Undo2 } from "lucide-react";
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
  completeTask,
  reopenTask,
  fetchTasks,
} from "@/app/(app)/org/[slug]/projects/[id]/task-actions";
import { cn } from "@/lib/utils";
import { PRIORITY_BADGE, STATUS_BADGE } from "@/components/tasks/task-badge-config";
import { ViewSelectorClient } from "@/components/views/ViewSelectorClient";
import { describeRecurrence } from "@/lib/recurrence";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { toast } from "sonner";
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

// --- Multi-select filter types (207.2) ---

const ALL_STATUSES: TaskStatus[] = ["OPEN", "IN_PROGRESS", "DONE", "CANCELLED"];
const DEFAULT_STATUSES: TaskStatus[] = ["OPEN", "IN_PROGRESS"];

const FILTER_CHIPS: { key: TaskStatus | "all" | "my" | "recurring"; label: string }[] = [
  { key: "all", label: "All" },
  { key: "OPEN", label: "Open" },
  { key: "IN_PROGRESS", label: "In Progress" },
  { key: "DONE", label: "Done" },
  { key: "CANCELLED", label: "Cancelled" },
  { key: "my", label: "My Tasks" },
  { key: "recurring", label: "Recurring" },
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
  const [activeStatuses, setActiveStatuses] = useState<Set<TaskStatus>>(new Set(DEFAULT_STATUSES));
  const [myTasksActive, setMyTasksActive] = useState(false);
  const [recurringActive, setRecurringActive] = useState(false);
  const [tasks, setTasks] = useState<Task[]>(
    initialTasks.filter((t) => DEFAULT_STATUSES.includes(t.status))
  );
  const [isPending, startTransition] = useTransition();
  const [actionTaskId, setActionTaskId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Sync state when parent Server Component re-renders with fresh data.
  // Apply current filter so the displayed list stays consistent with active chips.
  useEffect(() => {
    if (myTasksActive) {
      setTasks(
        currentMemberId
          ? initialTasks.filter((t) => t.assigneeId === currentMemberId)
          : []
      );
    } else {
      setTasks(initialTasks.filter((t) => activeStatuses.has(t.status)));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only re-filter when initialTasks changes from server
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

  // --- Multi-select filter handler (207.2) ---

  function handleChipClick(key: TaskStatus | "all" | "my" | "recurring") {
    setError(null);

    if (key === "all") {
      setMyTasksActive(false);
      setRecurringActive(false);
      setActiveStatuses(new Set(ALL_STATUSES));
      fetchWithFilters(new Set(ALL_STATUSES), false, false);
    } else if (key === "my") {
      const next = !myTasksActive;
      setMyTasksActive(next);
      setRecurringActive(false);
      fetchWithFilters(activeStatuses, next, false);
    } else if (key === "recurring") {
      const next = !recurringActive;
      setRecurringActive(next);
      setMyTasksActive(false);
      fetchWithFilters(activeStatuses, false, next);
    } else {
      setMyTasksActive(false);
      setRecurringActive(false);
      const next = new Set(activeStatuses);
      if (next.has(key)) {
        // Don't allow deselecting the last status
        if (next.size > 1) {
          next.delete(key);
        }
      } else {
        next.add(key);
      }
      setActiveStatuses(next);
      fetchWithFilters(next, false, false);
    }
  }

  function fetchWithFilters(statuses: Set<TaskStatus>, myTasks: boolean, recurring?: boolean) {
    const currentViewId = searchParams.get("view");

    startTransition(async () => {
      try {
        const filters: { status?: string; assigneeId?: string; viewId?: string; recurring?: boolean } = {};
        if (myTasks) {
          if (!currentMemberId) {
            setTasks([]);
            return;
          }
          filters.assigneeId = currentMemberId;
        } else {
          filters.status = Array.from(statuses).join(",");
        }
        if (currentViewId) {
          filters.viewId = currentViewId;
        }
        if (recurring) {
          filters.recurring = true;
        }
        const fetched = await fetchTasks(projectId, filters);
        setTasks(fetched);
      } catch {
        setError("Failed to fetch tasks.");
      }
    });
  }

  function buildCurrentFilters(): { status?: string; assigneeId?: string; viewId?: string; recurring?: boolean } {
    const filters: { status?: string; assigneeId?: string; viewId?: string; recurring?: boolean } = {};
    if (myTasksActive && currentMemberId) {
      filters.assigneeId = currentMemberId;
    } else {
      filters.status = Array.from(activeStatuses).join(",");
    }
    const currentViewId = searchParams.get("view");
    if (currentViewId) {
      filters.viewId = currentViewId;
    }
    if (recurringActive) {
      filters.recurring = true;
    }
    return filters;
  }

  // --- Chip active state helpers ---

  function isChipActive(key: TaskStatus | "all" | "my" | "recurring"): boolean {
    if (key === "all") return activeStatuses.size === ALL_STATUSES.length && !myTasksActive && !recurringActive;
    if (key === "my") return myTasksActive;
    if (key === "recurring") return recurringActive;
    return activeStatuses.has(key) && !myTasksActive && !recurringActive;
  }

  // Check if we're in the initial "no filter applied" state for empty state display
  const isDefaultFilter = !myTasksActive && activeStatuses.size === DEFAULT_STATUSES.length &&
    DEFAULT_STATUSES.every(s => activeStatuses.has(s));

  // --- Claim handler (40.6 + 40.9) ---

  function handleClaim(taskId: string) {
    setError(null);
    setActionTaskId(taskId);

    startTransition(async () => {
      try {
        const result = await claimTask(slug, taskId, projectId);
        if (!result.success) {
          setError(result.error ?? "Failed to claim task.");
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

  // --- Complete handler (207A + 225.6) ---

  function handleComplete(taskId: string) {
    setError(null);
    setActionTaskId(taskId);

    startTransition(async () => {
      try {
        const result = await completeTask(slug, taskId, projectId);
        if (!result.success) {
          setError(result.error ?? "Failed to complete task.");
          router.refresh();
        } else {
          // Show toast for recurring tasks (225.6)
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
          } else if (result.nextInstance === null) {
            // nextInstance is explicitly null — check if the completed task was recurring
            const completedTask = tasks.find((t) => t.id === taskId);
            if (completedTask?.isRecurring) {
              toast.success("Task completed", {
                description: "Recurrence has ended",
              });
            }
          }
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

  // --- Reopen handler (207A) ---

  function handleReopen(taskId: string) {
    setError(null);
    setActionTaskId(taskId);

    startTransition(async () => {
      try {
        const result = await reopenTask(slug, taskId, projectId);
        if (!result.success) {
          setError(result.error ?? "Failed to reopen task.");
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

  // 207.2: Multi-select status filter bar
  const filterBar = (
    <div className="flex flex-wrap gap-2" role="group" aria-label="Task filters">
      {FILTER_CHIPS.map((chip) => (
        <button
          key={chip.key}
          type="button"
          onClick={() => handleChipClick(chip.key)}
          disabled={isPending}
          aria-pressed={isChipActive(chip.key)}
          className={cn(
            "rounded-full px-3 py-1 text-sm font-medium transition-colors",
            isChipActive(chip.key)
              ? "bg-slate-900 text-slate-50 dark:bg-slate-100 dark:text-slate-900"
              : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700",
            isPending && "opacity-50",
          )}
        >
          {chip.label}
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

  if (tasks.length === 0 && isDefaultFilter) {
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
                const isTerminal = task.status === "DONE" || task.status === "CANCELLED";

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
                    {/* 207.4: Visual styling for terminal states */}
                    <TableCell>
                      <button
                        type="button"
                        className="flex min-w-0 items-center gap-1.5 cursor-pointer text-left"
                        onClick={() => openTask(task.id)}
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
                              <TooltipProvider>
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
                              </TooltipProvider>
                            )}
                          </span>
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
                    {/* 40.6 + 45.5 + 207A: Action buttons */}
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
                            <Button
                              size="xs"
                              variant="outline"
                              onClick={(e) => {
                                e.stopPropagation();
                              }}
                            >
                              <Clock className="size-3" />
                              Log Time
                            </Button>
                          </LogTimeDialog>
                        )}
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
                              handleComplete(task.id);
                            }}
                          >
                            <Check className="size-3" />
                            Done
                          </Button>
                        )}
                        {isTerminal && canManage && (
                          <Button
                            size="xs"
                            variant="outline"
                            disabled={isActioning}
                            onClick={(e) => {
                              e.stopPropagation();
                              handleReopen(task.id);
                            }}
                          >
                            <RotateCcw className="size-3" />
                            Reopen
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
