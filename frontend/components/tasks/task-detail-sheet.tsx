"use client";

import { useEffect, useReducer, useTransition } from "react";
import { Sheet, SheetContent, SheetDescription, SheetTitle } from "@/components/ui/sheet";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { TimeEntryList } from "@/components/tasks/time-entry-list";
import { CommentSectionClient } from "@/components/comments/comment-section-client";
import { TagInput } from "@/components/tags/TagInput";
import { TaskSubItems } from "@/components/tasks/task-sub-items";
import { CustomFieldSection } from "@/components/field-definitions/CustomFieldSection";
import { TaskDetailHeader } from "@/components/tasks/task-detail-header";
import { TaskDetailMetadata } from "@/components/tasks/task-detail-metadata";
import { RecurrenceEditor } from "@/components/tasks/task-recurrence-editor";
import { toast } from "sonner";
import { fetchTask, updateTask } from "@/app/(app)/org/[slug]/projects/[id]/task-actions";
import { fetchTimeEntries } from "@/app/(app)/org/[slug]/projects/[id]/time-entry-actions";
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
  isAdmin?: boolean;
  members: { id: string; name: string; email: string }[];
  allTags?: TagResponse[];
  fieldDefinitions?: FieldDefinitionResponse[];
  fieldGroups?: FieldGroupResponse[];
  groupMembers?: Record<string, FieldGroupMemberResponse[]>;
}

// --- Component ---

export function TaskDetailSheet({
  taskId,
  onClose,
  projectId,
  slug,
  canManage,
  currentMemberId,
  isAdmin = false,
  members,
  allTags = [],
  fieldDefinitions = [],
  fieldGroups = [],
  groupMembers = {},
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
        if (cancelled) return;
        if (projectId !== null && data.projectId !== projectId) {
          dispatch({ type: "TASK_ERROR", error: "Task not found." });
          return;
        }
        dispatch({ type: "TASK_LOADED", task: data });
      })
      .catch(() => {
        if (!cancelled) dispatch({ type: "TASK_ERROR", error: "Failed to load task." });
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

  const effectiveProjectId = projectId ?? task?.projectId ?? "";

  function handleAssigneeChange(newAssigneeId: string | null) {
    if (!task) return;
    const prevAssigneeId = task.assigneeId;
    const prevAssigneeName = task.assigneeName;
    const newAssigneeName =
      newAssigneeId != null ? (members.find((m) => m.id === newAssigneeId)?.name ?? null) : null;

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
        dispatch({
          type: "UPDATE_TASK",
          task: { ...task, assigneeId: prevAssigneeId, assigneeName: prevAssigneeName },
        });
        toast.error("Failed to update assignee", {
          description: result.error ?? "An unexpected error occurred.",
        });
      }
    });
  }

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
        toast.error("Failed to update status", {
          description: result.error ?? "An unexpected error occurred.",
        });
      }
    });
  }

  const isOwnTask = task?.assigneeId === currentMemberId;
  const canChangeStatus = canManage || isOwnTask;
  const isTerminal = task?.status === "DONE" || task?.status === "CANCELLED";
  const isOpen = taskId !== null;

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
          const target = e.target as HTMLElement | null;
          if (!target?.closest("[data-slot='sheet-overlay']")) {
            e.preventDefault();
          }
        }}
      >
        <SheetTitle className="sr-only">Task Detail</SheetTitle>
        <SheetDescription className="sr-only">
          Task detail panel showing metadata, time entries, and comments.
        </SheetDescription>

        {loadingTask && (
          <div className="flex flex-1 items-center justify-center p-8">
            <p className="text-sm text-slate-500 dark:text-slate-400">Loading task...</p>
          </div>
        )}

        {error && !loadingTask && (
          <div className="flex flex-1 items-center justify-center p-8">
            <p className="text-sm text-red-600 dark:text-red-400" role="alert">
              {error}
            </p>
          </div>
        )}

        {task && !loadingTask && (
          <>
            <TaskDetailHeader
              task={task}
              slug={slug}
              projectId={effectiveProjectId}
              canChangeStatus={canChangeStatus}
              isAdmin={isAdmin}
              onClose={onClose}
              onStatusChange={handleStatusChange}
              onTaskUpdate={(updated) => dispatch({ type: "TASK_LOADED", task: updated })}
            />

            <TaskDetailMetadata
              task={task}
              members={members}
              canManage={canManage}
              isTerminal={isTerminal}
              onAssigneeChange={handleAssigneeChange}
            />

            {/* Editable Recurrence */}
            {canChangeStatus && !isTerminal && (
              <div className="border-b border-slate-200 px-6 py-4 dark:border-slate-800">
                <h3 className="mb-2 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
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
                <h3 className="mb-2 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                  Description
                </h3>
                <p className="text-sm whitespace-pre-wrap text-slate-700 dark:text-slate-300">
                  {task.description}
                </p>
              </div>
            )}

            {/* Tags */}
            <div className="border-b border-slate-200 px-6 py-4 dark:border-slate-800">
              <h3 className="mb-2 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
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
                <h3 className="mb-2 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
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
                      isAdmin={isAdmin}
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
