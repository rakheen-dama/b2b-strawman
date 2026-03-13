"use client";

import { Suspense, useEffect, useState, useTransition } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ClipboardList, Plus } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";
import {
  Table,
  TableBody,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { CreateTaskDialog } from "@/components/tasks/create-task-dialog";
import { TaskDetailSheet } from "@/components/tasks/task-detail-sheet";
import { TaskListTableRow } from "@/components/tasks/task-list-table-row";
import {
  claimTask,
  releaseTask,
  completeTask,
  reopenTask,
  fetchTasks,
} from "@/app/(app)/org/[slug]/projects/[id]/task-actions";
import { cn } from "@/lib/utils";
import { ViewSelectorClient } from "@/components/views/ViewSelectorClient";
import { toast } from "sonner";
import {
  TooltipProvider,
} from "@/components/ui/tooltip";
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
    initialTasks.filter((t) => DEFAULT_STATUSES.includes(t.status)),
  );
  const [isPending, startTransition] = useTransition();
  const [actionTaskId, setActionTaskId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (myTasksActive) {
      setTasks(currentMemberId ? initialTasks.filter((t) => t.assigneeId === currentMemberId) : []);
    } else {
      setTasks(initialTasks.filter((t) => activeStatuses.has(t.status)));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialTasks]);

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

  function buildCurrentFilters(): { status?: string; assigneeId?: string; viewId?: string; recurring?: boolean } {
    const filters: { status?: string; assigneeId?: string; viewId?: string; recurring?: boolean } = {};
    if (myTasksActive && currentMemberId) filters.assigneeId = currentMemberId;
    else filters.status = Array.from(activeStatuses).join(",");
    const currentViewId = searchParams.get("view");
    if (currentViewId) filters.viewId = currentViewId;
    if (recurringActive) filters.recurring = true;
    return filters;
  }

  function fetchWithFilters(statuses: Set<TaskStatus>, myTasks: boolean, recurring?: boolean) {
    const currentViewId = searchParams.get("view");
    startTransition(async () => {
      try {
        const filters: { status?: string; assigneeId?: string; viewId?: string; recurring?: boolean } = {};
        if (myTasks) {
          if (!currentMemberId) { setTasks([]); return; }
          filters.assigneeId = currentMemberId;
        } else {
          filters.status = Array.from(statuses).join(",");
        }
        if (currentViewId) filters.viewId = currentViewId;
        if (recurring) filters.recurring = true;
        const fetched = await fetchTasks(projectId, filters);
        setTasks(fetched);
      } catch {
        setError("Failed to fetch tasks.");
      }
    });
  }

  function handleChipClick(key: TaskStatus | "all" | "my" | "recurring") {
    setError(null);
    if (key === "all") {
      setMyTasksActive(false); setRecurringActive(false);
      setActiveStatuses(new Set(ALL_STATUSES));
      fetchWithFilters(new Set(ALL_STATUSES), false, false);
    } else if (key === "my") {
      const next = !myTasksActive;
      setMyTasksActive(next); setRecurringActive(false);
      fetchWithFilters(activeStatuses, next, false);
    } else if (key === "recurring") {
      const next = !recurringActive;
      setRecurringActive(next); setMyTasksActive(false);
      fetchWithFilters(activeStatuses, false, next);
    } else {
      setMyTasksActive(false); setRecurringActive(false);
      const next = new Set(activeStatuses);
      if (next.has(key)) { if (next.size > 1) next.delete(key); }
      else next.add(key);
      setActiveStatuses(next);
      fetchWithFilters(next, false, false);
    }
  }

  function isChipActive(key: TaskStatus | "all" | "my" | "recurring"): boolean {
    if (key === "all") return activeStatuses.size === ALL_STATUSES.length && !myTasksActive && !recurringActive;
    if (key === "my") return myTasksActive;
    if (key === "recurring") return recurringActive;
    return activeStatuses.has(key) && !myTasksActive && !recurringActive;
  }

  const isDefaultFilter = !myTasksActive && activeStatuses.size === DEFAULT_STATUSES.length &&
    DEFAULT_STATUSES.every((s) => activeStatuses.has(s));

  // --- Action handlers ---

  function handleTaskAction(
    taskId: string,
    action: (s: string, t: string, p: string) => Promise<{ success: boolean; error?: string; nextInstance?: Task | null }>,
    actionName: string,
  ) {
    setError(null);
    setActionTaskId(taskId);
    startTransition(async () => {
      try {
        const result = await action(slug, taskId, projectId);
        if (!result.success) {
          setError(result.error ?? `Failed to ${actionName} task.`);
          router.refresh();
        } else {
          if (actionName === "complete" && result.nextInstance) {
            const dueLabel = result.nextInstance.dueDate
              ? new Date(result.nextInstance.dueDate).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" })
              : "no due date";
            toast.success("Task completed", { description: `Next instance due ${dueLabel}` });
          } else if (actionName === "complete" && result.nextInstance === null) {
            const completedTask = tasks.find((t) => t.id === taskId);
            if (completedTask?.isRecurring) toast.success("Task completed", { description: "Recurrence has ended" });
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
        <EmptyState icon={ClipboardList} title="No tasks yet" description="Create a task to start tracking work on this project" />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {header}
      {viewSelector}
      {filterBar}
      {error && <p className="text-sm text-red-600 dark:text-red-400" role="alert">{error}</p>}
      {tasks.length === 0 ? (
        <EmptyState icon={ClipboardList} title="No tasks match this filter" description="Try a different filter or clear the selection." />
      ) : (
        <TooltipProvider>
          <div className="rounded-lg border border-slate-200 dark:border-slate-800">
            <Table>
              <TableHeader>
                <TableRow className="border-slate-200 hover:bg-transparent dark:border-slate-800">
                  <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">Priority</TableHead>
                  <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">Title</TableHead>
                  <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">Status</TableHead>
                  <TableHead className="hidden text-xs uppercase tracking-wide text-slate-600 sm:table-cell dark:text-slate-400">Assignee</TableHead>
                  <TableHead className="hidden text-xs uppercase tracking-wide text-slate-600 sm:table-cell dark:text-slate-400">Due Date</TableHead>
                  <TableHead className="text-xs uppercase tracking-wide text-slate-600 dark:text-slate-400">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tasks.map((task) => (
                  <TaskListTableRow
                    key={task.id}
                    task={task}
                    slug={slug}
                    projectId={projectId}
                    currentMemberId={currentMemberId}
                    canManage={canManage}
                    retainerSummary={retainerSummary}
                    isActioning={actionTaskId === task.id && isPending}
                    onOpenTask={openTask}
                    onClaim={(id) => handleTaskAction(id, claimTask, "claim")}
                    onRelease={(id) => handleTaskAction(id, releaseTask, "release")}
                    onComplete={(id) => handleTaskAction(id, completeTask, "complete")}
                    onReopen={(id) => handleTaskAction(id, reopenTask, "reopen")}
                  />
                ))}
              </TableBody>
            </Table>
          </div>
        </TooltipProvider>
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
