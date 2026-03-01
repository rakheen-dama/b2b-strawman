"use client";

import { useEffect, useReducer, useState, useTransition } from "react";
import { Ban, Check, Circle, Loader2, MoreHorizontal, Repeat, RotateCcw, X, XCircle } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
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
import { TagInput } from "@/components/tags/TagInput";
import { TaskSubItems } from "@/components/tasks/task-sub-items";
import { CustomFieldSection } from "@/components/field-definitions/CustomFieldSection";
import {
  fetchTask,
  updateTask,
  completeTask,
  cancelTask,
  reopenTask,
} from "@/app/(app)/org/[slug]/projects/[id]/task-actions";
import { fetchTimeEntries } from "@/app/(app)/org/[slug]/projects/[id]/time-entry-actions";
import { formatDate } from "@/lib/format";
import { PRIORITY_BADGE, STATUS_BADGE } from "@/components/tasks/task-badge-config";
import { describeRecurrence, parseRecurrenceRule, formatRecurrenceRule } from "@/lib/recurrence";
import type { RecurrenceFrequency } from "@/lib/recurrence";
import { toast } from "sonner";
import type {
  Task,
  TaskStatus,
  TimeEntry,
  TagResponse,
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
} from "@/lib/types";

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
  projectId: string | null;
  slug: string;
  canManage: boolean;
  currentMemberId: string;
  orgRole: string;
  members: { id: string; name: string; email: string }[];
  allTags?: TagResponse[];
  fieldDefinitions?: FieldDefinitionResponse[];
  fieldGroups?: FieldGroupResponse[];
  groupMembers?: Record<string, FieldGroupMemberResponse[]>;
}

// --- Recurrence Editor (225.2) ---

function RecurrenceEditor({
  task,
  slug,
  projectId,
  onUpdate,
}: {
  task: Task;
  slug: string;
  projectId: string;
  onUpdate: (task: Task) => void;
}) {
  const parsed = parseRecurrenceRule(task.recurrenceRule);
  const [frequency, setFrequency] = useState<RecurrenceFrequency | "NONE">(parsed?.frequency ?? "NONE");
  const [recurrenceInterval, setRecurrenceInterval] = useState(parsed?.interval ?? 1);
  const [endDate, setEndDate] = useState(task.recurrenceEndDate ?? "");
  const [, startTransition] = useTransition();

  function handleSave() {
    const effectiveFrequency = frequency === "NONE" ? null : frequency;
    const rule = effectiveFrequency ? formatRecurrenceRule(effectiveFrequency, recurrenceInterval) : null;
    const newEndDate = effectiveFrequency && endDate ? endDate : null;

    // Optimistic update
    onUpdate({
      ...task,
      recurrenceRule: rule,
      recurrenceEndDate: newEndDate,
      isRecurring: !!rule,
    });

    startTransition(async () => {
      try {
        const result = await updateTask(slug, task.id, projectId, {
          title: task.title,
          description: task.description ?? undefined,
          priority: task.priority,
          status: task.status,
          type: task.type ?? undefined,
          dueDate: task.dueDate ?? undefined,
          assigneeId: task.assigneeId ?? undefined,
          recurrenceRule: rule ?? undefined,
          recurrenceEndDate: newEndDate ?? undefined,
        });

        if (!result.success) {
          onUpdate(task);
        }
      } catch {
        onUpdate(task);
      }
    });
  }

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="text-xs text-slate-500 dark:text-slate-400">
            Frequency
          </label>
          <Select value={frequency} onValueChange={(v) => setFrequency(v as RecurrenceFrequency | "NONE")}>
            <SelectTrigger className="mt-1 h-8 w-full text-xs">
              <SelectValue placeholder="None" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="NONE">None</SelectItem>
              <SelectItem value="DAILY">Daily</SelectItem>
              <SelectItem value="WEEKLY">Weekly</SelectItem>
              <SelectItem value="MONTHLY">Monthly</SelectItem>
              <SelectItem value="YEARLY">Yearly</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {frequency !== "NONE" && (
          <div>
            <label htmlFor="detail-recurrence-interval" className="text-xs text-slate-500 dark:text-slate-400">
              Interval
            </label>
            <Input
              id="detail-recurrence-interval"
              type="number"
              min={1}
              value={recurrenceInterval}
              onChange={(e) => setRecurrenceInterval(Math.max(1, parseInt(e.target.value, 10) || 1))}
              className="mt-1 h-8 text-xs"
            />
          </div>
        )}
      </div>
      {frequency && (
        <div>
          <label htmlFor="detail-recurrence-end" className="text-xs text-slate-500 dark:text-slate-400">
            End Date (optional)
          </label>
          <Input
            id="detail-recurrence-end"
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            className="mt-1 h-8 text-xs"
          />
        </div>
      )}
      <Button size="sm" variant="outline" onClick={handleSave}>
        Save Recurrence
      </Button>
    </div>
  );
}

// --- Status Select ---

const STATUS_OPTIONS: { value: TaskStatus; label: string; icon: typeof Circle }[] = [
  { value: "OPEN", label: "Open", icon: Circle },
  { value: "IN_PROGRESS", label: "In Progress", icon: Loader2 },
  { value: "DONE", label: "Done", icon: Check },
  { value: "CANCELLED", label: "Cancelled", icon: XCircle },
];

function StatusSelect({
  value,
  onChange,
}: {
  value: TaskStatus;
  onChange: (status: TaskStatus) => void;
}) {
  const current = STATUS_OPTIONS.find((o) => o.value === value);
  const CurrentIcon = current?.icon ?? Circle;

  return (
    <Select value={value} onValueChange={(v) => onChange(v as TaskStatus)}>
      <SelectTrigger className="h-7 w-auto gap-1.5 rounded-full border-slate-200 px-2.5 text-xs font-medium dark:border-slate-700">
        <CurrentIcon className="size-3" />
        <SelectValue />
      </SelectTrigger>
      <SelectContent position="popper" sideOffset={4}>
        {STATUS_OPTIONS.map((opt) => {
          const Icon = opt.icon;
          return (
            <SelectItem key={opt.value} value={opt.value}>
              <span className="flex items-center gap-1.5">
                <Icon className="size-3" />
                {opt.label}
              </span>
            </SelectItem>
          );
        })}
      </SelectContent>
    </Select>
  );
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
  allTags = [],
  fieldDefinitions = [],
  fieldGroups = [],
  groupMembers = {},
}: TaskDetailSheetProps) {
  const [state, dispatch] = useReducer(sheetReducer, initialState);
  const { task, timeEntries, loadingTask, loadingEntries, error } = state;
  const [, startTransition] = useTransition();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  // Fetch task and time entries when taskId changes
  useEffect(() => {
    if (!taskId) return;

    let cancelled = false;
    dispatch({ type: "FETCH_START" });

    fetchTask(taskId)
      .then((data) => {
        if (cancelled) return;
        // Guard: ensure the task belongs to this project (skip when projectId is null — cross-project context)
        if (projectId !== null && data.projectId !== projectId) {
          dispatch({ type: "TASK_ERROR", error: "Task not found." });
          return;
        }
        dispatch({ type: "TASK_LOADED", task: data });
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
  }, [taskId, projectId]);

  // When projectId is null (cross-project context like My Work), derive from the loaded task
  const effectiveProjectId = projectId ?? task?.projectId ?? "";

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
      const result = await updateTask(slug, task.id, effectiveProjectId, {
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

  // Handle status change — optimistic update + server action
  function handleStatusChange(newStatus: TaskStatus) {
    if (!task || newStatus === task.status) return;

    const prevStatus = task.status;
    dispatch({ type: "UPDATE_TASK", task: { ...task, status: newStatus } });

    startTransition(async () => {
      const result = await updateTask(slug, task.id, effectiveProjectId, {
        title: task.title,
        description: task.description ?? undefined,
        priority: task.priority,
        status: newStatus,
        type: task.type ?? undefined,
        dueDate: task.dueDate ?? undefined,
        assigneeId: task.assigneeId ?? undefined,
      });

      if (!result.success) {
        dispatch({ type: "UPDATE_TASK", task: { ...task, status: prevStatus } });
      }
    });
  }

  const isOwnTask = task?.assigneeId === currentMemberId;
  const canChangeStatus = canManage || isOwnTask;
  const isTerminal = task?.status === "DONE" || task?.status === "CANCELLED";
  const canMarkDone = task?.status === "IN_PROGRESS" && (isOwnTask || canManage);

  const [actionError, setActionError] = useState<string | null>(null);

  // Handle lifecycle actions — call server action then re-fetch task
  function handleLifecycleAction(action: (slug: string, taskId: string, projectId: string) => Promise<{ success: boolean; error?: string; nextInstance?: Task | null }>) {
    if (!task) return;

    setActionError(null);
    startTransition(async () => {
      const result = await action(slug, task.id, effectiveProjectId);
      if (result.success) {
        // Show toast for recurring tasks when completing (225.6)
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
        // Re-fetch to get updated metadata (completedAt, completedByName, etc.)
        try {
          const updated = await fetchTask(task.id);
          dispatch({ type: "TASK_LOADED", task: updated });
        } catch {
          // Silently handle re-fetch failure — the action succeeded
        }
      } else {
        setActionError(result.error ?? "Action failed. Please try again.");
      }
    });
  }

  const isOpen = taskId !== null;
  const priorityBadge = task ? PRIORITY_BADGE[task.priority] : null;
  const statusBadge = task ? STATUS_BADGE[task.status] : null;

  const taskTags = task?.tags ?? [];
  const taskAppliedFieldGroups = task?.appliedFieldGroups ?? [];
  const hasCustomFields = taskAppliedFieldGroups.length > 0;

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
        onPointerDownOutside={(e) => {
          // Prevent Sheet from closing when clicking portaled elements (Select, Combobox)
          // that render outside the Sheet DOM. Only the overlay backdrop (data-slot="sheet-overlay")
          // should close the sheet.
          const target = e.target as HTMLElement | null;
          if (!target?.closest("[data-slot='sheet-overlay']")) {
            e.preventDefault();
          }
        }}
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
                  {canChangeStatus ? (
                    <StatusSelect
                      value={task.status}
                      onChange={handleStatusChange}
                    />
                  ) : (
                    statusBadge && (
                      <Badge variant={statusBadge.variant}>
                        {statusBadge.label}
                      </Badge>
                    )
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

                {/* Lifecycle action buttons */}
                {canChangeStatus && (
                  <div className="mt-2 flex items-center gap-2">
                    {canMarkDone && (
                      <Button
                        size="sm"
                        variant="soft"
                        onClick={() => handleLifecycleAction(completeTask)}
                      >
                        <Check className="mr-1 size-3" />
                        Mark Done
                      </Button>
                    )}
                    {isTerminal && canChangeStatus && (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => handleLifecycleAction(reopenTask)}
                      >
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
                {/* Recurrence info (225.5) */}
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
                          // Navigate to parent task by updating URL
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

            {/* Editable Recurrence (225.2) */}
            {canChangeStatus && !isTerminal && (
              <div className="border-b border-slate-200 px-6 py-4 dark:border-slate-800">
                <h3 className="mb-2 text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Edit Recurrence
                </h3>
                <RecurrenceEditor
                  task={task}
                  slug={slug}
                  projectId={effectiveProjectId}
                  onUpdate={(updated) => dispatch({ type: "UPDATE_TASK", task: updated })}
                />
              </div>
            )}

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

            {/* Tags */}
            <div className="border-b border-slate-200 px-6 py-4 dark:border-slate-800">
              <h3 className="mb-2 text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                Tags
              </h3>
              <TagInput
                entityType="TASK"
                entityId={task.id}
                tags={taskTags}
                allTags={allTags}
                editable={canManage}
                canInlineCreate={isAdmin}
                slug={slug}
              />
            </div>

            {/* Custom Fields */}
            {hasCustomFields && (
              <div className="border-b border-slate-200 px-6 py-4 dark:border-slate-800">
                <h3 className="mb-2 text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Custom Fields
                </h3>
                <CustomFieldSection
                  entityType="TASK"
                  entityId={task.id}
                  customFields={task.customFields ?? {}}
                  appliedFieldGroups={taskAppliedFieldGroups}
                  editable={canManage}
                  slug={slug}
                  fieldDefinitions={fieldDefinitions}
                  fieldGroups={fieldGroups}
                  groupMembers={groupMembers}
                />
              </div>
            )}

            {/* Sub-Items */}
            <TaskSubItems
              taskId={task.id}
              slug={slug}
              projectId={effectiveProjectId}
              canManage={canManage}
            />

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
                      projectId={effectiveProjectId}
                      currentMemberId={currentMemberId}
                      orgRole={orgRole}
                      canManage={canManage}
                    />
                  )}
                </TabsContent>

                <TabsContent value="comments" className="mt-4">
                  <CommentSectionClient
                    projectId={effectiveProjectId}
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
