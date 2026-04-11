# Fix Spec: BUG-AUTO-04 — Task status dropdown allows invalid transitions and fails silently

## Problem

The `TaskStatusSelect` dropdown in the task detail panel presents all four statuses (OPEN, IN_PROGRESS, DONE, CANCELLED) regardless of the current task status. When a user selects an invalid transition (e.g., OPEN -> DONE), the backend rejects it with a 400 `InvalidStateException` because the `TaskStatus` state machine only allows `OPEN -> {IN_PROGRESS, CANCELLED}`. The frontend catches the error and rolls back the optimistic UI update, but never shows a toast or error message to the user -- the dropdown silently reverts, making it appear broken.

The bug report mentions "PUT returns 405 for some callers" but investigation confirms this is actually a **400** (Invalid State) for disallowed transitions, not a 405 (Method Not Allowed). The PUT endpoint at `@PutMapping("/api/tasks/{id}")` exists and routes correctly (TaskController.java:169). The 405 mention may be from a different caller context or misdiagnosis.

## Root Cause (confirmed)

Two interacting issues:

1. **Frontend: `TaskStatusSelect` shows all statuses without filtering by allowed transitions**
   - File: `frontend/components/tasks/task-status-select.tsx` (lines 13-18)
   - The `STATUS_OPTIONS` array always shows all 4 values. There is no prop or logic to filter based on valid transitions from the current status.

2. **Frontend: `handleStatusChange` silently swallows backend errors**
   - File: `frontend/components/tasks/task-detail-sheet.tsx` (lines 187-206)
   - When `updateTask()` returns `{ success: false, error: "..." }`, the handler rolls back the optimistic update (line 203) but never calls `toast.error()` or sets any user-visible error state. The error is effectively swallowed.

**Backend state machine (correct, not a bug):**
- File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskStatus.java` (lines 13-18)
- `OPEN -> {IN_PROGRESS, CANCELLED}` (DONE not reachable)
- `IN_PROGRESS -> {DONE, OPEN, CANCELLED}`
- `DONE -> {OPEN}` (reopen only)
- `CANCELLED -> {OPEN}` (reopen only)

The backend correctly enforces the state machine via `Task.requireTransition()` at `Task.java:251-255`, called from `Task.update()` at `Task.java:147`.

## Fix

### Part 1: Filter dropdown options by allowed transitions (Frontend)

**File:** `frontend/components/tasks/task-status-select.tsx`

1. Add a `currentStatus` or use the existing `value` prop to determine which transitions are valid.
2. Define a frontend-side transition map matching the backend state machine:
   ```typescript
   const ALLOWED_TRANSITIONS: Record<TaskStatus, TaskStatus[]> = {
     OPEN: ["IN_PROGRESS", "CANCELLED"],
     IN_PROGRESS: ["DONE", "OPEN", "CANCELLED"],
     DONE: ["OPEN"],
     CANCELLED: ["OPEN"],
   };
   ```
3. Filter `STATUS_OPTIONS` to only show the current status plus statuses reachable from the current status:
   ```typescript
   const allowedValues = new Set([value, ...ALLOWED_TRANSITIONS[value]]);
   const filteredOptions = STATUS_OPTIONS.filter(o => allowedValues.has(o.value));
   ```

### Part 2: Show error toast on status change failure (Frontend)

**File:** `frontend/components/tasks/task-detail-sheet.tsx`

In `handleStatusChange` (lines 192-205), after the `if (!result.success)` block that rolls back the optimistic update, add a toast notification:

```typescript
if (!result.success) {
  dispatch({ type: "UPDATE_TASK", task: { ...task, status: prevStatus } });
  toast.error("Failed to update status", {
    description: result.error ?? "An unexpected error occurred.",
  });
}
```

Note: `toast` from `sonner` is already imported in `task-detail-header.tsx` but NOT in `task-detail-sheet.tsx`. Add `import { toast } from "sonner";` to the imports.

### Part 3 (optional): Same toast for assignee change failure

**File:** `frontend/components/tasks/task-detail-sheet.tsx`

In `handleAssigneeChange` (lines 178-183), add the same toast pattern for consistency:

```typescript
if (!result.success) {
  dispatch({ type: "UPDATE_TASK", task: { ...task, assigneeId: prevAssigneeId, assigneeName: prevAssigneeName } });
  toast.error("Failed to update assignee", {
    description: result.error ?? "An unexpected error occurred.",
  });
}
```

## Scope

**Frontend only** -- the backend state machine is correct and intentional.

Files to modify:
- `frontend/components/tasks/task-status-select.tsx` -- filter dropdown options
- `frontend/components/tasks/task-detail-sheet.tsx` -- add error toast + toast import

Migration needed: no

## Verification

1. Navigate to http://localhost:3001/mock-login, sign in as Alice.
2. Open a project with tasks. Click on a task in OPEN status to open the detail sheet.
3. Verify the status dropdown only shows: Open, In Progress, Cancelled (not Done).
4. Change status to In Progress. Verify dropdown now shows: Done, Open, Cancelled.
5. Change status to Done via dropdown. Verify it succeeds.
6. If a transition fails for any reason (e.g., permission), verify a toast error appears.
7. Run `pnpm test` in frontend/ -- existing task detail sheet tests should still pass.

## Estimated Effort

S (< 30 min) -- two small frontend changes, no backend changes, no migration.
