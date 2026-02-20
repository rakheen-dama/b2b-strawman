"use client";

import { useEffect, useReducer, useTransition } from "react";
import { X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetClose,
  SheetDescription,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";
import { AssigneeSelector } from "@/components/tasks/assignee-selector";
import { TimeEntryList } from "@/components/tasks/time-entry-list";
import { CommentSectionClient } from "@/components/comments/comment-section-client";
import {
  fetchTask,
  updateTask,
} from "@/app/(app)/org/[slug]/projects/[id]/task-actions";
import { fetchTimeEntries } from "@/app/(app)/org/[slug]/projects/[id]/time-entry-actions";
import { formatDate } from "@/lib/format";
import type { Task, TaskPriority, TaskStatus, TimeEntry } from "@/lib/types";

// --- Badge config (mirrors task-list-panel.tsx) ---

const PRIORITY_BADGE: Record<
  TaskPriority,
  { label: string; variant: "destructive" | "warning" | "neutral" }
> = {
  HIGH: { label: "High", variant: "destructive" },
  MEDIUM: { label: "Medium", variant: "warning" },
  LOW: { label: "Low", variant: "neutral" },
};

const STATUS_BADGE: Record<
  TaskStatus,
  { label: string; variant: "success" | "warning" | "neutral" | "destructive" }
> = {
  OPEN: { label: "Open", variant: "neutral" },
  IN_PROGRESS: { label: "In Progress", variant: "warning" },
  DONE: { label: "Done", variant: "success" },
  CANCELLED: { label: "Cancelled", variant: "destructive" },
};

// --- State ---

interface SheetState {
  task: Task | null;
  timeEntries: TimeEntry[];
  loadingTask: boolean;
  loadingEntries: boolean;
  error: string | null;
}

type SheetAction =
  | { type: "FETCH_START" }
  | { type: "TASK_LOADED"; task: Task }
  | { type: "TASK_ERROR"; error: string }
  | { type: "ENTRIES_LOADED"; entries: TimeEntry[] }
  | { type: "ENTRIES_ERROR" }
  | { type: "UPDATE_TASK"; task: Task };

const initialState: SheetState = {
  task: null,
  timeEntries: [],
  loadingTask: false,
  loadingEntries: false,
  error: null,
};

function sheetReducer(state: SheetState, action: SheetAction): SheetState {
  switch (action.type) {
    case "FETCH_START":
      return { ...initialState, loadingTask: true, loadingEntries: true };
    case "TASK_LOADED":
      return { ...state, task: action.task, loadingTask: false, error: null };
    case "TASK_ERROR":
      return { ...state, loadingTask: false, error: action.error };
    case "ENTRIES_LOADED":
      return { ...state, timeEntries: action.entries, loadingEntries: false };
    case "ENTRIES_ERROR":
      return { ...state, timeEntries: [], loadingEntries: false };
    case "UPDATE_TASK":
      return { ...state, task: action.task };
    default:
      return state;
  }
}

// --- Props ---

interface TaskDetailSheetProps {
  taskId: string | null;
  onClose: () => void;
  projectId: string;
  slug: string;
  canManage: boolean;
  currentMemberId: string;
  orgRole: string;
  members: { id: string; name: string; email: string }[];
}

// --- Component ---

export function TaskDetailSheet({
  taskId,
  onClose,
  projectId,
  slug,
  canManage,
  currentMemberId,
  orgRole,
  members,
}: TaskDetailSheetProps) {
  const [state, dispatch] = useReducer(sheetReducer, initialState);
  const { task, timeEntries, loadingTask, loadingEntries, error } = state;
  const [, startTransition] = useTransition();

  // Fetch task and time entries when taskId changes
  useEffect(() => {
    if (!taskId) return;

    let cancelled = false;
    dispatch({ type: "FETCH_START" });

    fetchTask(taskId)
      .then((data) => {
        if (!cancelled) dispatch({ type: "TASK_LOADED", task: data });
      })
      .catch(() => {
        if (!cancelled)
          dispatch({ type: "TASK_ERROR", error: "Failed to load task." });
      });

    fetchTimeEntries(taskId)
      .then((entries) => {
        if (!cancelled) dispatch({ type: "ENTRIES_LOADED", entries });
      })
      .catch(() => {
        if (!cancelled) dispatch({ type: "ENTRIES_ERROR" });
      });

    return () => {
      cancelled = true;
    };
  }, [taskId]);

  // Handle assignee change — optimistic update + server action
  function handleAssigneeChange(newAssigneeId: string | null) {
    if (!task) return;

    // Optimistic update
    const prevAssigneeId = task.assigneeId;
    const prevAssigneeName = task.assigneeName;
    const newAssigneeName =
      newAssigneeId != null
        ? (members.find((m) => m.id === newAssigneeId)?.name ?? null)
        : null;

    dispatch({
      type: "UPDATE_TASK",
      task: { ...task, assigneeId: newAssigneeId, assigneeName: newAssigneeName },
    });

    startTransition(async () => {
      const result = await updateTask(slug, task.id, projectId, {
        title: task.title,
        description: task.description ?? undefined,
        priority: task.priority,
        status: task.status,
        type: task.type ?? undefined,
        dueDate: task.dueDate ?? undefined,
        assigneeId: newAssigneeId ?? undefined,
      });

      if (!result.success) {
        // Revert on failure
        dispatch({
          type: "UPDATE_TASK",
          task: { ...task, assigneeId: prevAssigneeId, assigneeName: prevAssigneeName },
        });
      }
    });
  }

  const isOpen = taskId !== null;
  const priorityBadge = task ? PRIORITY_BADGE[task.priority] : null;
  const statusBadge = task ? STATUS_BADGE[task.status] : null;

  return (
    <Sheet
      open={isOpen}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <SheetContent
        side="right"
        className="flex w-full flex-col gap-0 overflow-y-auto p-0 sm:max-w-xl"
        showCloseButton={false}
      >
        {/* Accessibility: required by Radix Dialog */}
        <SheetTitle className="sr-only">Task Detail</SheetTitle>
        <SheetDescription className="sr-only">
          Task detail panel showing metadata, time entries, and comments.
        </SheetDescription>

        {loadingTask && (
          <div className="flex flex-1 items-center justify-center p-8">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Loading task...
            </p>
          </div>
        )}

        {error && !loadingTask && (
          <div className="flex flex-1 items-center justify-center p-8">
            <p
              className="text-sm text-red-600 dark:text-red-400"
              role="alert"
            >
              {error}
            </p>
          </div>
        )}

        {task && !loadingTask && (
          <>
            {/* Header */}
            <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-4 dark:border-slate-800">
              <div className="min-w-0 flex-1">
                <h2 className="text-base font-semibold leading-snug text-slate-950 dark:text-slate-50">
                  {task.title}
                </h2>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  {statusBadge && (
                    <Badge variant={statusBadge.variant}>
                      {statusBadge.label}
                    </Badge>
                  )}
                  {priorityBadge && (
                    <Badge variant={priorityBadge.variant}>
                      {priorityBadge.label}
                    </Badge>
                  )}
                  {task.type && (
                    <span className="text-xs text-slate-500 dark:text-slate-400">
                      {task.type}
                    </span>
                  )}
                </div>
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

            {/* Metadata row */}
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
                      onAssigneeChange={handleAssigneeChange}
                      disabled={!canManage}
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
              </dl>
            </div>

            {/* Description */}
            {task.description && (
              <div className="border-b border-slate-200 px-6 py-4 dark:border-slate-800">
                <h3 className="mb-2 text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Description
                </h3>
                <p className="whitespace-pre-wrap text-sm text-slate-700 dark:text-slate-300">
                  {task.description}
                </p>
              </div>
            )}

            {/* Tabbed content */}
            <div className="flex-1 px-6 py-4">
              <Tabs defaultValue="time-entries">
                <TabsList>
                  <TabsTrigger value="time-entries">Time Entries</TabsTrigger>
                  <TabsTrigger value="comments">Comments</TabsTrigger>
                </TabsList>

                <TabsContent value="time-entries" className="mt-4">
                  {loadingEntries ? (
                    <p className="text-sm text-slate-500 dark:text-slate-400">
                      Loading time entries...
                    </p>
                  ) : (
                    <TimeEntryList
                      entries={timeEntries}
                      slug={slug}
                      projectId={projectId}
                      currentMemberId={currentMemberId}
                      orgRole={orgRole}
                      canManage={canManage}
                    />
                  )}
                </TabsContent>

                <TabsContent value="comments" className="mt-4">
                  <CommentSectionClient
                    projectId={projectId}
                    entityType="TASK"
                    entityId={task.id}
                    orgSlug={slug}
                    currentMemberId={currentMemberId}
                    canManageVisibility={canManage}
                  />
                </TabsContent>
              </Tabs>
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
