# Phase 18 — Task Detail Experience: Missing Functionality

Phase 18 resolves four task UX gaps identified in QA gap analysis (Group A: findings #2, #3, #5, #6). It introduces a slide-over Sheet panel as the primary task detail surface, replacing the existing inline row expansion in `TaskListPanel`. Tags, custom fields, saved views, and assignee selection are wired to tasks for the first time. All backend schema support already exists; this phase is predominantly frontend work with a single minor backend addition.

**Architecture doc**: `architecture/phase18-task-detail-experience-missing-functionality.md`

**ADRs**:
- [ADR-080](../adr/ADR-080-task-detail-sheet-panel.md) — Task Detail Surface: Slide-over Sheet Panel

**MIGRATION**: None. All backend schema support already exists.

**Dependencies on prior phases**: Phase 4 (Task entity), Phase 5 (TimeEntry, task-time linking), Phase 6.5 (Comments, CommentSectionClient), Phase 8 (BillingRate for time entries), Phase 11 (TagInput, CustomFieldSection, ViewSelectorClient, SavedView).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 129 | Assignee Selection & Backend Prep | Both | -- | S | 129A | **Done** (PR #261) |
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

## Implementation Order

### Stage 1: Foundation (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 129 | 129A | Optional `assigneeId` on `CreateTaskRequest` (backend) + new `AssigneeSelector` component (frontend). Foundation for all sheet assignee interactions. | **Done** (PR #261) |

### Stage 2: Task Detail Sheet Core (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 130 | 130A | New `TaskDetailSheet` component — full right-side panel with metadata, time entries, comments tabs. Depends on `AssigneeSelector` from 129A. | **Done** (PR #262) |
| 2b | Epic 130 | 130B | Wire sheet into `TaskListPanel` via URL state (`?taskId=`). Remove inline row expansion. Thread `members`, `allTags`, `fieldDefinitions`, `fieldGroups` props from page down to panel. |

### Stage 3: Feature Wiring (Parallel tracks after Stage 2)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 131 | 131A | Add `TagInput` + `CustomFieldSection` sections to `TaskDetailSheet`. Depends on props threaded in 130B. |
| 3b | Epic 131 | 131B | Add `ViewSelectorClient` to `TaskListPanel` header. Fetch TASK saved views server-side. Parallel with 131A and 132A. |
| 3c | Epic 132 | 132A | Add `ViewSelectorClient` + `TaskDetailSheet` to My Work page. Potential backend change to `MyWorkController` for `?view=` param. Parallel with 131A/131B. |

### Timeline

```
Stage 1:  [129A]
Stage 2:  [130A] --> [130B]
Stage 3:  [131A] --> [131B]  //  [132A]
```

**Critical path**: 129A -> 130A -> 130B -> 131A -> 131B
**Parallel savings**: Stage 3 has two parallel tracks (131 series and 132A).

---

## Epic 129: Assignee Selection & Backend Prep

**Goal**: Add optional `assigneeId` to `CreateTaskRequest` (with role-aware service logic), and build the reusable `AssigneeSelector` combobox component that the sheet and create dialog will share. This is the only backend change in Phase 18.

**References**: ADR-080 (Backend impact section). Architecture doc Epic 129.

**Dependencies**: None (foundation epic).

**Scope**: Both (Backend + Frontend)

**Estimated Effort**: S

### Slices

| Slice | Summary | Status |
|-------|---------|--------|
| **129A** | Backend: `CreateTaskRequest` + `TaskService` change. Frontend: `AssigneeSelector` component, `CreateTaskDialog` update. ~4 tests. | **Done** (PR #261) |

### Tasks

#### Slice 129A — AssigneeSelector component + CreateTaskRequest assigneeId

**Backend changes (2 files):**

| File | Change |
|------|--------|
| `backend/.../task/CreateTaskRequest.java` | Add optional `UUID assigneeId` field |
| `backend/.../task/TaskService.java` | In `createTask()`: if `assigneeId` is provided AND caller is lead/admin (check `RequestScopes.ORG_ROLE`), set `task.setAssigneeId(assigneeId)` and set status to `IN_PROGRESS`. If caller is regular member, ignore `assigneeId` silently — the frontend will not show the picker to them. |

**Frontend changes (2 files):**

| File | Change |
|------|--------|
| `frontend/components/tasks/assignee-selector.tsx` | **New file.** `"use client"` component wrapping shadcn `Command` (combobox). Props: `members: {id, name, email}[]`, `currentAssigneeId: string \| null`, `onAssigneeChange: (id: string \| null) => void`, `disabled?: boolean`. Shows member list with search, plus "Unassigned" option at top. Use explicit `value` prop on `CommandItem` per MEMORY.md cmdk lesson: `value={\`${name} ${email}\`}`. Render as a `Popover` trigger button showing current assignee name or "Unassigned" placeholder. |
| `frontend/components/tasks/create-task-dialog.tsx` | Add `AssigneeSelector` below priority field, only rendered when `canManage` prop is true. Pass selected `assigneeId` in the form submission body. Add `members` and `canManage` to the component's props interface. |

**Tests:**

| Test | Type |
|------|------|
| `TaskService.createTask` with `assigneeId` as admin — task created with assignee, status `IN_PROGRESS` | Backend integration |
| `TaskService.createTask` with `assigneeId` as regular member — task created without assignee, status `OPEN` | Backend integration |
| `AssigneeSelector` renders members, selecting one fires `onAssigneeChange` | Frontend unit |
| `CreateTaskDialog` shows assignee picker when `canManage=true`, hides when `false` | Frontend unit |

### Key Files

**Slice 129A — Create:**
- `frontend/components/tasks/assignee-selector.tsx`

**Slice 129A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/CreateTaskRequest.java` — Add optional `assigneeId`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` — Role-aware assignee wiring
- `frontend/components/tasks/create-task-dialog.tsx` — Add `AssigneeSelector`

### Architecture Decisions

- **Silent ignore for regular members**: When a non-admin/non-lead passes `assigneeId`, the backend silently ignores it rather than returning a 403. The frontend gates the picker behind `canManage`, so the mismatch is never user-visible. The silent ignore is a defense-in-depth safety net.
- **Status auto-set to `IN_PROGRESS`**: Pre-assigning a task at creation implies the assignee is actively working it. Setting `IN_PROGRESS` avoids a separate status update step.

---

## Epic 130: Task Detail Sheet Core

**Goal**: Build the `TaskDetailSheet` component — a right-side slide-over panel showing full task detail (metadata, description, time entries, comments). Wire it into `TaskListPanel` via URL search param state (`?taskId=`), and remove the existing inline row expansion entirely.

**References**: ADR-080. Architecture doc Epics 130A, 130B, Implementation Notes.

**Dependencies**: Epic 129 (`AssigneeSelector` component).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Summary | Status |
|-------|---------|--------|
| **130A** | New `TaskDetailSheet` component — header, metadata row, description, tabbed Time Entries + Comments. ~5 tests. | **Done** (PR #262) |
| **130B** | Wire sheet into `TaskListPanel` with `?taskId=` URL state. Remove inline row expansion. Thread props from project detail page. ~4 tests. | Pending |

### Tasks

#### Slice 130A — TaskDetailSheet component

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
3. **Description** — Read-only `<p>`. An "Edit" button may open an edit dialog (future slice).
4. **Tabbed content** — Two tabs: "Time Entries" (`TimeEntryList`) and "Comments" (`CommentSectionClient`). These are the same components currently in the inline expansion — they move here.

**Data fetching:** The sheet fetches task data via a client-side `fetch` to `GET /api/tasks/{taskId}` when `taskId` changes (`useEffect` + state).

**Sheet configuration:**
- `side="right"`
- Width override: `className="sm:max-w-xl w-full"` on `SheetContent`
- Body: `overflow-y-auto` for independent scrolling
- Use `p-0` on `SheetContent`, manage padding internally for sticky header vs. scrollable body split

**Assignee changes from the sheet:** When `AssigneeSelector` fires `onAssigneeChange`, call `PUT /api/tasks/{taskId}` with `{ assigneeId }`. Optimistically update local state.

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

#### Slice 130B — Wire sheet into TaskListPanel, remove inline expansion

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
- Task titles in the list get `cursor-pointer` and `hover:text-teal-600` to signal clickability

**Modify:** `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`
- Thread `members` (already fetched), `allTags`, `fieldDefinitions`, `fieldGroups` props down to `TaskListPanel`
- Add `members` to `TaskListPanel`'s props interface

| File | Change |
|------|--------|
| `frontend/components/tasks/task-list-panel.tsx` | Remove inline expansion; add URL-state sheet wiring; update task row click handler |
| `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` | Thread `members`, `allTags`, `fieldDefinitions`, `fieldGroups` to `TaskListPanel` |

**Tests:**

| Test | Type |
|------|------|
| Clicking a task row updates URL with `?taskId=` | Frontend unit |
| Sheet opens with correct task when URL has `?taskId=` | Frontend unit |
| Closing sheet clears `?taskId=` from URL | Frontend unit |
| Inline expansion is fully removed (no `colSpan` row) | Frontend unit |

### Key Files

**Slice 130A — Create:**
- `frontend/components/tasks/task-detail-sheet.tsx`

**Slice 130B — Modify:**
- `frontend/components/tasks/task-list-panel.tsx` — Remove inline expansion, add sheet wiring
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Thread props to panel

### Architecture Decisions

- **URL state (`?taskId=`) over React state**: The sheet state is persisted in the URL. Refreshing or sharing the URL reopens the sheet to the same task — the same addressability as a dedicated page without the navigation tradeoff. `useSearchParams` + `router.push({ scroll: false })` is the pattern.
- **Preserve existing URL params**: The `openTask` and `closeTask` helpers must construct a new `URLSearchParams` from the current value and only add/delete `taskId`, preserving `?tab=` and `?view=` params.
- **Existing test impact**: Tests asserting on the expanded row (colSpan, `TimeEntryList` inside table, `CommentSectionClient` inside table, `expandedTaskId` behavior) must be rewritten to assert on Sheet opening/content instead. Check `frontend/__tests__/` for any `task-list-panel` test files before building.

---

## Epic 131: Tags, Custom Fields & Saved Views

**Goal**: Wire `TagInput` and `CustomFieldSection` into the `TaskDetailSheet` body, following the exact pattern established on the project and customer detail pages. Add `ViewSelectorClient` to the `TaskListPanel` header, following the pattern from the projects and customers list pages.

**References**: ADR-080 (Consequences section). Architecture doc Epics 131A, 131B.

**Dependencies**: Epic 130 (sheet must exist; props threaded from page in 130B).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Summary | Status |
|-------|---------|--------|
| **131A** | Add `TagInput` + `CustomFieldSection` to `TaskDetailSheet`. Add server actions for tag/field mutations if not present. ~4 tests. | Pending |
| **131B** | Add `ViewSelectorClient` to `TaskListPanel` header. Fetch TASK saved views server-side. Forward `view` param in task fetch. ~3 tests. | Pending |

### Tasks

#### Slice 131A — Tags + Custom Fields in TaskDetailSheet

**Modify:** `frontend/components/tasks/task-detail-sheet.tsx`

Add two new sections between the description and the tabbed content:

1. **Tags section** — `<TagInput entityType="TASK" entityId={taskId} />`. Server action: calls `POST /api/tasks/{taskId}/tags`. Props: `allTags` (passed from parent), `currentTags` (from task fetch response — `task.tags` is already in `TaskResponse`).

2. **Custom Fields section** — `<CustomFieldSection entityType="TASK" entityId={taskId} />`. Server action: calls `PUT /api/tasks/{taskId}/field-groups` for field group application and `PUT /api/tasks/{taskId}` with `customFields` map for value changes. Props: `fieldDefinitions`, `fieldGroups`, `appliedFieldGroups` (from task response), `customFields` (from task response), `canManage`.

Both components follow the exact same integration pattern as the project detail page and customer detail page. Copy the wiring pattern from there.

**Server actions needed:** Check if `task-actions.ts` already has actions for tag/custom-field mutations. If not, add:
- `setTaskTags(taskId: string, tagIds: string[])` — calls `POST /api/tasks/{taskId}/tags`
- `updateTaskCustomFields(taskId: string, customFields: Record<string, unknown>)` — calls `PUT /api/tasks/{taskId}` with `{ customFields }`
- `setTaskFieldGroups(taskId: string, fieldGroupIds: string[])` — calls `PUT /api/tasks/{taskId}/field-groups`

**Pass-through props:** `TaskListPanel` must pass `allTags`, `fieldDefinitions`, `fieldGroups` to `TaskDetailSheet`. These were threaded from the page in 130B.

| File | Change |
|------|--------|
| `frontend/components/tasks/task-detail-sheet.tsx` | Add `TagInput` + `CustomFieldSection` sections between description and tabs |
| `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts` | Add server actions for tag/field mutations if not already present |

**Tests:**

| Test | Type |
|------|------|
| `TagInput` renders in sheet with current task tags | Frontend unit |
| `CustomFieldSection` renders in sheet with current field values | Frontend unit |
| Tag change calls `setTaskTags` server action | Frontend unit |
| Custom field change calls `updateTaskCustomFields` server action | Frontend unit |

---

#### Slice 131B — ViewSelectorClient on TaskListPanel

**Modify:** `frontend/components/tasks/task-list-panel.tsx`

Add `ViewSelectorClient` in the panel header area (above the task table, alongside the existing "Create Task" button area).

**Pattern (copy from projects list page):**
1. Fetch saved views for TASK entity type server-side in the project detail page: `GET /api/views?entityType=TASK`
2. Pass `savedViews` as a prop to `TaskListPanel`
3. Render `<ViewSelectorClient entityType="TASK" savedViews={savedViews} ... />` in the header
4. The view ID comes from `?view=` search param (`ViewSelectorClient` already handles this via `router.push`)
5. The task list endpoint already accepts `?view={viewId}` — the server action that fetches tasks must forward this param

**Modify:** `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`
- Fetch saved views for TASK: `const taskViews = await api.get('/api/views?entityType=TASK');`
- Pass to `TaskListPanel`: `taskViews={taskViews}`

**Modify:** `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts`
- Ensure `fetchTasks` (or equivalent server action) accepts and forwards `viewId` param to the backend

| File | Change |
|------|--------|
| `frontend/components/tasks/task-list-panel.tsx` | Add `ViewSelectorClient` in panel header |
| `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` | Fetch TASK saved views, pass as `taskViews` prop |
| `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts` | Forward `view` param in task fetch server action |

**Tests:**

| Test | Type |
|------|------|
| `ViewSelectorClient` renders in task list header | Frontend unit |
| Selecting a view updates `?view=` URL param | Frontend unit |
| Task list re-fetches with view filter applied | Frontend integration |

### Key Files

**Slice 131A — Modify:**
- `frontend/components/tasks/task-detail-sheet.tsx` — Add tag and custom field sections
- `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts` — Add tag/field server actions

**Slice 131B — Modify:**
- `frontend/components/tasks/task-list-panel.tsx` — Add `ViewSelectorClient` in header
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Fetch and pass TASK saved views
- `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts` — Forward `view` param

### Architecture Decisions

- **No new backend work for tags/custom fields**: The backend already supports `EntityType.TASK` in `FieldDefinition`, `EntityTag` covers tasks polymorphically, and `Task.java` already has `custom_fields` (JSONB) and `applied_field_groups`. This slice is purely frontend wiring.
- **`ViewSelectorClient` URL param preservation**: The `?view=` param set by `ViewSelectorClient` must coexist with `?taskId=` and `?tab=`. Confirm the component uses `URLSearchParams` construction from the current search params.

---

## Epic 132: My Work Enhancements

**Goal**: Wire `ViewSelectorClient` and `TaskDetailSheet` into the My Work page. Verify (and optionally add) `?view=` param support on `MyWorkController`. The My Work page is cross-project, so tag/custom field data is fetched lazily inside the sheet based on the task's `projectId`.

**References**: ADR-080 (Consequences section). Architecture doc Epic 132A.

**Dependencies**: Epic 131 (`TaskDetailSheet` fully functional with tags/fields for the project-scoped case; `ViewSelectorClient` usage pattern established).

**Scope**: Frontend (+ minor potential backend check)

**Estimated Effort**: S

### Slices

| Slice | Summary | Status |
|-------|---------|--------|
| **132A** | Add `ViewSelectorClient` + `TaskDetailSheet` (with URL state) to My Work page. Potential backend check on `MyWorkController`. ~3 tests. | Pending |

### Tasks

#### Slice 132A — ViewSelectorClient + Task Sheet on My Work

**Modify:** `frontend/app/(app)/org/[slug]/my-work/page.tsx`

Two changes:

1. **ViewSelectorClient** — Add `<ViewSelectorClient entityType="TASK" savedViews={savedViews} />` in the page header below `MyWorkHeader`. Fetch saved views server-side: `GET /api/views?entityType=TASK`. Forward `?view={viewId}` to the `/api/my-work/tasks` endpoint.

2. **Task sheet opening** — When a user clicks a task in the assigned/unassigned lists, open `TaskDetailSheet` with that task's ID. The My Work page is cross-project, so project-scoped data (members, tags, field defs) is not readily available at the page level. **Recommended approach**: fetch project-scoped data lazily inside the sheet when the task loads — the task response includes `projectId`, which the sheet uses to fetch tags/fields on demand.

**Backend check:** Verify `MyWorkController.getMyTasks()` accepts a `@RequestParam(required = false) UUID view` parameter. If not, add it and delegate to `ViewFilterService` the same way `TaskController.listTasks` does. This is the only potential backend change in this epic.

| File | Change |
|------|--------|
| `frontend/app/(app)/org/[slug]/my-work/page.tsx` | Add `ViewSelectorClient` in header; add `TaskDetailSheet` with URL state |
| `backend/.../mywork/MyWorkController.java` | Add `@RequestParam(required = false) UUID view` if not already present |

**Tests:**

| Test | Type |
|------|------|
| `ViewSelectorClient` renders on My Work page | Frontend unit |
| Clicking a task in My Work opens `TaskDetailSheet` | Frontend unit |
| `MyWorkController` accepts `?view=` param (if backend change needed) | Backend integration |

### Key Files

**Slice 132A — Modify:**
- `frontend/app/(app)/org/[slug]/my-work/page.tsx` — Add `ViewSelectorClient` and `TaskDetailSheet`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mywork/MyWorkController.java` — Add `?view=` param if missing

### Architecture Decisions

- **Lazy project-scoped data in the sheet**: The My Work sheet is simpler than the project-scoped sheet — it shows metadata + comments + time entries immediately, and fetches tags/field defs on task load (using `projectId` from the task response). This avoids pre-loading every project's tags/fields at the page level across potentially many projects.
- **`ViewSelectorClient` on My Work applies globally**: The selected view filters the entire My Work task list (both "assigned to me" and "unassigned in my projects" sections). This matches user expectation: saved views are workspace-level filter presets.

---

## Implementation Notes

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

### Sheet Width

The default `Sheet` in `components/ui/sheet.tsx` uses `sm:max-w-sm`. Task detail needs more space. Override on `SheetContent`:

```tsx
<SheetContent side="right" className="sm:max-w-xl w-full p-0">
```

Use `p-0` and manage padding internally for the header (sticky) vs. body (scrollable) split.

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

### Existing Test Impact

The inline expansion removal (130B) will break any existing tests that assert on:
- Expanded row content (`TimeEntryList`, `CommentSectionClient` inside table)
- `expandedTaskId` behavior
- `colSpan={6}` row rendering

These tests must be rewritten to assert on Sheet opening/content instead. Check `frontend/__tests__/` for any `task-list-panel` test files before starting 130B.

### Mobile Responsiveness

On small screens (`< sm`), `SheetContent` goes full-width (it already does — the `sm:max-w-xl` only applies at `sm` breakpoint). The Sheet's built-in close button (`SheetClose`) handles dismissal.

---

## Test Summary

| Epic | Slice | Backend Tests | Frontend Tests | Total |
|------|-------|---------------|----------------|-------|
| 129 | 129A | ~2 | ~2 | ~4 |
| 130 | 130A | -- | ~5 | ~5 |
| 130 | 130B | -- | ~4 | ~4 |
| 131 | 131A | -- | ~4 | ~4 |
| 131 | 131B | -- | ~3 | ~3 |
| 132 | 132A | ~1 | ~2 | ~3 |
| **Total** | | **~3** | **~20** | **~23** |
