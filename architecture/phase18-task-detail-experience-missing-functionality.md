# Gap Fix A — Task Detail Experience

Resolves the four task UX gaps identified in QA gap analysis (Group A: findings #2, #3, #5, #6). Introduces a slide-over Sheet panel as the primary task detail surface, replacing the inline row expansion. Wires tags, custom fields, saved views, and assignee selection to tasks.

**ADR**: [ADR-080](../adr/ADR-080-task-detail-sheet-panel.md) — Task Detail Surface: Slide-over Sheet Panel

**MIGRATION**: None. All backend schema support already exists.

**Dependencies on prior phases**: Phase 4 (Task entity), Phase 5 (TimeEntry, task-time linking), Phase 6.5 (Comments, CommentSectionClient), Phase 8 (BillingRate for time entries), Phase 11 (TagInput, CustomFieldSection, ViewSelectorClient, SavedView).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 129 | Assignee Selection & Backend Prep | Both | -- | S | 129A | Pending |
| 130 | Task Detail Sheet Core | Frontend | 129 | L | 130A, 130B | Pending |
| 131 | Tags, Custom Fields & Saved Views | Frontend | 130 | M | 131A, 131B | Pending |
| 132 | My Work Enhancements | Frontend | 131 | S | 132A | Pending |

---

## Dependency Graph

```
[E129A Backend: assigneeId on create + AssigneeSelector component]
                    |
[E130A TaskDetailSheet core — metadata, description, assignee, time entries, comments]
                    |
[E130B Remove inline expansion, wire sheet into TaskListPanel with URL state]
                    |
         +----------+-----------+
         |                      |
         v                      v
[E131A Tags + Custom Fields   [E132A My Work:
 in TaskDetailSheet]           ViewSelectorClient
         |                     + sheet opening]
[E131B ViewSelectorClient
 on TaskListPanel header]
```

**Parallel opportunities**:
- After 130B: Epics 131 and 132 are independent tracks. 131A/131B and 132A can run in parallel.

---

## Epic 129 — Assignee Selection & Backend Prep

### Slice 129A — AssigneeSelector component + CreateTaskRequest assigneeId

**Backend changes (2 files):**

| File | Change |
|------|--------|
| `backend/.../task/CreateTaskRequest.java` | Add optional `UUID assigneeId` field |
| `backend/.../task/TaskService.java` | In `createTask()`: if `assigneeId` is provided AND caller is lead/admin (check `RequestScopes.ORG_ROLE`), set `task.setAssigneeId(assigneeId)` and set status to `IN_PROGRESS`. If caller is regular member, ignore `assigneeId` silently (don't error — the frontend won't show it to them). |

**Frontend changes (2 files):**

| File | Change |
|------|--------|
| `frontend/components/tasks/assignee-selector.tsx` | **New file.** `"use client"` component wrapping shadcn `Command` (combobox). Props: `members: {id, name, email}[]`, `currentAssigneeId: string \| null`, `onAssigneeChange: (id: string \| null) => void`, `disabled?: boolean`. Shows member list with search, plus "Unassigned" option at top. Use explicit `value` prop on `CommandItem` (per MEMORY.md cmdk lesson): `value={\`${name} ${email}\`}`. Render as a `Popover` trigger button showing current assignee name or "Unassigned" placeholder. |
| `frontend/components/tasks/create-task-dialog.tsx` | Add `AssigneeSelector` below priority field, only rendered when `canManage` prop is true. Pass selected `assigneeId` in the form submission body. Add `members` and `canManage` to the component's props interface. |

**Tests:**

| Test | Type |
|------|------|
| `TaskService.createTask` with `assigneeId` as admin → task created with assignee, status `IN_PROGRESS` | Backend integration |
| `TaskService.createTask` with `assigneeId` as regular member → task created without assignee, status `OPEN` | Backend integration |
| `AssigneeSelector` renders members, selecting one fires `onAssigneeChange` | Frontend unit |
| `CreateTaskDialog` shows assignee picker when `canManage=true`, hides when `false` | Frontend unit |

---

## Epic 130 — Task Detail Sheet Core

### Slice 130A — TaskDetailSheet component

**New file:** `frontend/components/tasks/task-detail-sheet.tsx`

A `"use client"` component rendering a right-side `Sheet` (from `components/ui/sheet.tsx`) with full task detail.

**Props interface:**

```typescript
interface TaskDetailSheetProps {
  taskId: string | null;                    // null = closed
  onClose: () => void;
  projectId: string;
  slug: string;
  canManage: boolean;
  currentMemberId: string;
  orgRole: string;
  members: { id: string; name: string; email: string }[];
}
```

**Layout (top to bottom):**

1. **Header** — Task title (display, not inline-edit in this slice), status badge, priority badge, type label. Close button (`SheetClose` or `onClose`).
2. **Metadata row** — Assignee (`AssigneeSelector` from 129A), due date (display), created by + created date.
3. **Description** — Read-only `<p>` (editing is already handled by `UpdateTaskRequest` via the existing edit flow — the sheet can show an "Edit" button that opens an edit dialog or inline edit in a future slice).
4. **Tabbed content** — Two tabs: "Time Entries" (`TimeEntryList`) and "Comments" (`CommentSectionClient`). These are the same components currently in the inline expansion — they move here.

**Data fetching:** The sheet fetches task data via a client-side `fetch` to `GET /api/tasks/{taskId}` when `taskId` changes (use `useEffect` + state, or SWR/react-query if available in the project). Time entries are fetched via existing `fetchTimeEntries` pattern.

**Sheet configuration:**
- `side="right"`
- Override width: `className="sm:max-w-xl w-full"` on `SheetContent`
- Body: `overflow-y-auto` for independent scrolling
- Overlay: default `bg-black/50` (matches existing Sheet)

**Assignee changes from the sheet:** When `AssigneeSelector` fires `onAssigneeChange`, call `PUT /api/tasks/{taskId}` with `{ assigneeId }` via a server action or direct fetch. Optimistically update local state.

| File | Change |
|------|--------|
| `frontend/components/tasks/task-detail-sheet.tsx` | **New file** — full component as described above |

**Tests:**

| Test | Type |
|------|------|
| Sheet renders when `taskId` is provided, shows task title/status/priority | Frontend unit |
| Sheet is closed (not rendered) when `taskId` is null | Frontend unit |
| Assignee change in sheet calls update endpoint | Frontend unit |
| Time entries tab shows `TimeEntryList` | Frontend unit |
| Comments tab shows `CommentSectionClient` | Frontend unit |

---

### Slice 130B — Wire sheet into TaskListPanel, remove inline expansion

**Modify:** `frontend/components/tasks/task-list-panel.tsx`

**Remove:**
- `expandedTaskId` state variable
- `handleToggleExpand` function
- The `colSpan={6}` expansion `<TableRow>` with embedded `TimeEntryList` and `CommentSectionClient`
- The `timeEntries` state map and `fetchTimeEntries` inline handler (time entry fetching moves to the sheet)

**Add:**
- Import `TaskDetailSheet` from `./task-detail-sheet`
- Read `taskId` from URL search params: `const searchParams = useSearchParams(); const selectedTaskId = searchParams.get("taskId");`
- On task row click: `router.push(\`?tab=tasks&taskId=${task.id}\`, { scroll: false })` (preserve tab param)
- On sheet close: `router.push(\`?tab=tasks\`, { scroll: false })` (clear taskId param)
- Render `<TaskDetailSheet taskId={selectedTaskId} onClose={handleCloseSheet} ... />` at the end of the component

**Modify:** `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`

- Thread `members` (already fetched), `allTags`, `fieldDefinitions`, `fieldGroups` props down to `TaskListPanel`. These are needed by the sheet for the assignee selector (this slice) and tags/custom fields (next epic).
- Add `members` to `TaskListPanel`'s props interface.

**Visual change:** Task titles in the list should get a hover style (`cursor-pointer`, `hover:text-teal-600`) to indicate clickability, replacing the old expand/collapse button.

**Tests:**

| Test | Type |
|------|------|
| Clicking a task row updates URL with `?taskId=` | Frontend unit |
| Sheet opens with correct task when URL has `?taskId=` | Frontend unit |
| Closing sheet clears `?taskId=` from URL | Frontend unit |
| Inline expansion is fully removed (no colSpan row) | Frontend unit |

---

## Epic 131 — Tags, Custom Fields & Saved Views

### Slice 131A — Tags + Custom Fields in TaskDetailSheet

**Modify:** `frontend/components/tasks/task-detail-sheet.tsx`

Add two new sections between the description and the tabbed content:

1. **Tags section** — `<TagInput entityType="TASK" entityId={taskId} .../>`. Server action: calls `POST /api/tasks/{taskId}/tags`. Props: `allTags` (passed from parent), `currentTags` (from task fetch response — `task.tags` is already in `TaskResponse`).

2. **Custom Fields section** — `<CustomFieldSection entityType="TASK" entityId={taskId} .../>`. Server action: calls `PUT /api/tasks/{taskId}/field-groups` for field group application, and `PUT /api/tasks/{taskId}` with `customFields` map for field value changes. Props: `fieldDefinitions`, `fieldGroups`, `appliedFieldGroups` (from task response), `customFields` (from task response), `canManage`.

Both components follow the exact same integration pattern as the project detail page (`frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`) and customer detail page. Copy the wiring pattern from there.

**Server actions needed:** Check if `task-actions.ts` already has actions for tag/custom-field mutations. If not, add:
- `setTaskTags(taskId: string, tagIds: string[])` — calls `POST /api/tasks/{taskId}/tags`
- `updateTaskCustomFields(taskId: string, customFields: Record<string, unknown>)` — calls `PUT /api/tasks/{taskId}` with `{ customFields }`
- `setTaskFieldGroups(taskId: string, fieldGroupIds: string[])` — calls `PUT /api/tasks/{taskId}/field-groups`

**Pass-through props:** `TaskListPanel` must pass `allTags`, `fieldDefinitions`, `fieldGroups` to `TaskDetailSheet`. These were threaded from the page in 130B.

| File | Change |
|------|--------|
| `frontend/components/tasks/task-detail-sheet.tsx` | Add TagInput + CustomFieldSection sections |
| `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts` | Add server actions for tag/field mutations if not present |

**Tests:**

| Test | Type |
|------|------|
| TagInput renders in sheet with current task tags | Frontend unit |
| CustomFieldSection renders in sheet with current field values | Frontend unit |
| Tag change calls `setTaskTags` server action | Frontend unit |
| Custom field change calls `updateTaskCustomFields` server action | Frontend unit |

---

### Slice 131B — ViewSelectorClient on TaskListPanel

**Modify:** `frontend/components/tasks/task-list-panel.tsx`

Add `ViewSelectorClient` in the panel header area (above the task table, alongside the existing "Create Task" button area).

**Pattern (copy from projects list page):**
1. Fetch saved views for TASK entity type server-side in the project detail page: `GET /api/views?entityType=TASK`
2. Pass `savedViews` as a prop to `TaskListPanel`
3. Render `<ViewSelectorClient entityType="TASK" savedViews={savedViews} ... />` in the header
4. The view ID comes from `?view=` search param (ViewSelectorClient already handles this via `router.push`)
5. The task list endpoint already accepts `?view={viewId}` — the server action that fetches tasks needs to forward this param

**Modify:** `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`
- Fetch saved views for TASK: `const taskViews = await api.get('/api/views?entityType=TASK');`
- Pass to TaskListPanel: `taskViews={taskViews}`

**Modify:** `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts`
- Ensure `fetchTasks` (or equivalent server action) accepts and forwards `viewId` param to the backend

| File | Change |
|------|--------|
| `frontend/components/tasks/task-list-panel.tsx` | Add ViewSelectorClient in header |
| `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` | Fetch TASK saved views, pass as prop |
| `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts` | Forward `view` param in task fetch |

**Tests:**

| Test | Type |
|------|------|
| ViewSelectorClient renders in task list header | Frontend unit |
| Selecting a view updates `?view=` URL param | Frontend unit |
| Task list re-fetches with view filter applied | Frontend integration |

---

## Epic 132 — My Work Enhancements

### Slice 132A — ViewSelectorClient + Task Sheet on My Work

**Modify:** `frontend/app/(app)/org/[slug]/my-work/page.tsx`

Two changes:

1. **ViewSelectorClient** — Add `ViewSelectorClient entityType="TASK"` in the page header (below `MyWorkHeader`). Fetch saved views server-side: `GET /api/views?entityType=TASK`. The `?view={viewId}` param needs to be forwarded to the `/api/my-work/tasks` endpoint.

   **Backend check:** Verify `MyWorkController.getMyTasks()` accepts a `@RequestParam(required = false) UUID view` parameter. If not, add it — delegate to `ViewFilterService` the same way `TaskController.listTasks` does. This is the only potential backend change in this epic.

2. **Task sheet opening** — When a user clicks a task in the assigned/unassigned lists, open `TaskDetailSheet` with that task's ID. The My Work page doesn't have project-scoped members/tags/fields readily available (it's cross-project), so:
   - The sheet should fetch its own tag/field data based on the task's `projectId` (returned in the task response)
   - Or: simplify the My Work sheet to show metadata + comments + time only (no tags/custom fields), since those are project-scoped concerns
   - **Recommended:** Fetch project-scoped data lazily inside the sheet when the task loads (the sheet already fetches the task, which includes `projectId` — use that to fetch tags/fields)

| File | Change |
|------|--------|
| `frontend/app/(app)/org/[slug]/my-work/page.tsx` | Add ViewSelectorClient, add TaskDetailSheet with URL state |
| `backend/.../mywork/MyWorkController.java` | Add `@RequestParam(required = false) UUID view` if not present |

**Tests:**

| Test | Type |
|------|------|
| ViewSelectorClient renders on My Work page | Frontend unit |
| Clicking a task in My Work opens TaskDetailSheet | Frontend unit |
| MyWorkController accepts `?view=` param (if backend change needed) | Backend integration |

---

## Implementation Notes

### Sheet Width

The default `Sheet` in `components/ui/sheet.tsx` uses `sm:max-w-sm` for the right side. Task detail needs more space. Override on `SheetContent`:

```tsx
<SheetContent side="right" className="sm:max-w-xl w-full p-0">
```

Use `p-0` and manage padding internally for the header (sticky) vs. body (scrollable) split.

### URL State Pattern

Use `useSearchParams` + `router.push` with `scroll: false` to manage `?taskId=` without page navigation:

```tsx
const searchParams = useSearchParams();
const selectedTaskId = searchParams.get("taskId");

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
```

This preserves other params like `?tab=tasks&view=abc`.

### Data Flow

```
ProjectDetailPage (server component)
  ├── fetches: tasks, members, allTags, fieldDefs, fieldGroups, taskViews
  └── passes to TaskListPanel (client component)
        ├── renders task table
        ├── renders ViewSelectorClient (from taskViews)
        └── renders TaskDetailSheet (when taskId is in URL)
              ├── fetches full task detail (GET /api/tasks/{id})
              ├── renders AssigneeSelector (from members prop)
              ├── renders TagInput (from allTags prop)
              ├── renders CustomFieldSection (from fieldDefs/fieldGroups props)
              ├── renders TimeEntryList (fetches from GET /api/tasks/{id}/time-entries)
              └── renders CommentSectionClient (entity-generic, entityType="TASK")
```

### Existing Test Expectations

The inline expansion removal (130B) will break any existing tests that assert on:
- Expanded row content (TimeEntryList, CommentSectionClient inside table)
- `expandedTaskId` behavior
- `colSpan={6}` row rendering

These tests must be rewritten to assert on Sheet opening/content instead. Check `frontend/__tests__/` for any `task-list-panel` test files.

### Mobile Responsiveness

On small screens (`< sm`), `SheetContent` should go full-width (it already does — the `sm:max-w-xl` only applies at `sm` breakpoint). The Sheet's built-in close button (`SheetClose`) handles dismissal. Consider adding a swipe-to-close gesture in a future iteration.
