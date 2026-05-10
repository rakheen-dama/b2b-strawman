# Tasks

**Bounded context:** see [`10-bounded-contexts.md` § tasks](../10-bounded-contexts.md).
**Owner package:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/`.

## 1. Purpose

Task lifecycle inside a Project: create → claim → complete (or cancel/reopen), with optional iCal-style recurrence. Tasks are the unit-of-work for project members and the primary anchor for `TimeEntry` (effort) and `Expense` (cost).

Sibling modules — **do not merge** with this one:
- [`time-entry`](time-entry.md) owns `TimeEntry` (FK → `tasks.id`); time logging, billing snapshots, weekly grid.
- [`expenses`](expenses.md) owns `Expense` (optional FK → `tasks.id`); markup, billable disbursements.
- [`checklists`](checklists.md) owns `ChecklistTemplate` / `ChecklistInstance` — the per-customer compliance checklists. Note that this module *also* owns a different concept called `TaskItem` (inline sub-items belonging to a Task) — these are not the same thing as compliance checklists. See §6 below.

In the **legal-za** vertical the UI label for Task is "Action Item" — backend type names and tables are unchanged. See §7.

## 2. Entities owned

| Entity | Source | Notes |
|---|---|---|
| `Task` | `→ backend/.../task/Task.java:26` | Aggregate root. Includes recurrence (`recurrenceRule`, `recurrenceEndDate`, `parentTaskId`) and lifecycle timestamps (`completedAt/By`, `cancelledAt/By`). `@Version` optimistic lock. Custom fields + applied field groups stored as `jsonb`. |
| `TaskItem` | `→ backend/.../task/TaskItem.java:14` | Inline sub-checklist item belonging to a Task (`task_items` table, FK `task_id`). Distinct from the `checklist` module's `ChecklistInstance` — sub-items are an in-task UI device, not the compliance checklist surface. |
| `TaskStatus` enum | `→ backend/.../task/TaskStatus.java:7` | `OPEN, IN_PROGRESS, DONE, CANCELLED`. State-machine helper `canTransitionTo(target)` invoked by `Task.requireTransition` `→ Task.java:259`. |
| `TaskPriority` enum | `→ backend/.../task/TaskPriority.java:4` | Priority tier referenced by `Task.priority`. |
| `RecurrenceRule` value object | `→ backend/.../task/RecurrenceRule.java` | Parser/calculator for the `recurrenceRule` string (RFC 5545–style RRULE; per `glossary.md:226`). Used at create/update validation and on completion to compute `nextDueDate`. |

`Task` references `Project` (FK `project_id`) and `Member` (FK `assignee_id`, `created_by`, `completed_by`, `cancelled_by`). Tenant-scoped (per the per-tenant schema model) — no `tenant_id` column.

## 3. REST surface

Mounted by `TaskController` `→ backend/.../task/TaskController.java:34` and `TaskItemController` `→ backend/.../task/TaskItemController.java`. Path mix uses `/api/projects/{projectId}/tasks` for create/list (collection scoped to project) and `/api/tasks/{id}/*` for everything by-ID.

| Method + path | Handler | Purpose |
|---|---|---|
| `POST /api/projects/{projectId}/tasks` | `createTask` `→ TaskController.java:50` | Create a task (optional pre-assign — privileged). Validates `recurrenceRule` via `RecurrenceRule.parse` `→ TaskService.java:272`. |
| `GET /api/projects/{projectId}/tasks` | `listTasks` `→ TaskController.java:77` | List tasks for a project. Supports SavedView (`?view=`), `status`, `assigneeId`, `priority`, `assigneeFilter=unassigned`, `recurring`, custom-field and tag filters. |
| `GET /api/tasks/{id}` | `getTask` `→ TaskController.java:153` | Fetch one task. |
| `PUT /api/tasks/{id}` | `updateTask` `→ TaskController.java:162` | Update fields incl. status transition; per-field audit delta via `AuditDeltaBuilder` `→ TaskService.java:539`. |
| `DELETE /api/tasks/{id}` | `deleteTask` `→ TaskController.java:188` | Hard-delete. Refuses if any `TimeEntry` rows reference the task `→ TaskService.java:664`. |
| `POST /api/tasks/{id}/claim` | `claimTask` `→ TaskController.java:195` | Self-assign while `OPEN`; sets assignee + `IN_PROGRESS`. Caller must be a `ProjectMember` of the task's project. |
| `POST /api/tasks/{id}/release` | `releaseTask` `→ TaskController.java:204` | Clear assignee, return to `OPEN`. Assignee or lead/admin/owner. |
| `PATCH /api/tasks/{id}/complete` | `completeTask` `→ TaskController.java:213` | Mark `DONE`; if `recurrenceRule` set, also creates next instance — see §6. Returns `CompleteTaskResponse` (completed + optional `nextInstance`). |
| `PATCH /api/tasks/{id}/cancel` | `cancelTask` `→ TaskController.java:221` | Admin/owner only. Cancels and stops recurrence silently. |
| `PATCH /api/tasks/{id}/reopen` | `reopenTask` `→ TaskController.java:230` | From `DONE`/`CANCELLED` back to `OPEN`. Admin/owner or original assignee. |
| `PUT /api/tasks/{id}/field-groups` | `setFieldGroups` `→ TaskController.java:240` | Apply/remove custom-field groups. **Capability-gated** `@RequiresCapability("PROJECT_MANAGEMENT")`. |
| `POST /api/tasks/{id}/tags` | `setTaskTags` `→ TaskController.java:249` | Replace task tags (entity-tag join). |
| `GET /api/tasks/{id}/tags` | `getTaskTags` `→ TaskController.java:258` | Read task tags. |
| Sub-items: `POST /api/tasks/{id}/items`, `PATCH /api/task-items/{id}`, `DELETE /api/task-items/{id}` | `TaskItemController` `→ task/TaskItemController.java:52,72,81` | Mutations all `@RequiresCapability("PROJECT_MANAGEMENT")`. |

Per A1 §3, ~16 endpoints in the task surface (counting top-level Task ops); sub-item endpoints are additional.

Authorization is *not* a single capability gate on every method — most endpoints lean on `ProjectAccessService.requireViewAccess` (project membership) plus per-action role/assignee checks inside the service. See §6.

## 4. Frontend pages / components

Per `_discovery/A2-frontend-map.md:102,105,114` and `lib/types/task.ts:9`:

| Surface | File | Notes |
|---|---|---|
| Project detail (tasks tab) | `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` | Tasks list + creation under the project. Server actions in `task-actions.ts` and `task-item-actions.ts` (sibling files). |
| My Work (personal task view) | `frontend/app/(app)/org/[slug]/my-work/page.tsx` | Cross-project list of the caller's assigned/open tasks; combined with timesheet sub-route. `my-work-tasks-client.tsx` is the client component. |
| Calendar (month view) | `frontend/app/(app)/org/[slug]/calendar/page.tsx` | Calendar of tasks (by `dueDate`) and deadlines; client in `calendar-page-client.tsx`. |
| Task UI components | `frontend/components/tasks/` | `task-list-panel.tsx`, `task-detail-sheet.tsx`, `task-recurrence-editor.tsx`, `task-sub-items.tsx`, `assignee-selector.tsx`, `create-task-dialog.tsx`, `task-status-select.tsx`, etc. |
| TS types | `frontend/lib/types/task.ts` | `Task` shape — id, projectId, title, status, priority, assigneeId, estimatedHours, customFields. |
| Server-action: claim | `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts:139` | `claimTask(...)` — wired into `task-list-panel.tsx:377` (`onClaim`). Confirms the claim flow is reachable from the UI (see §10). |

The portal also surfaces tasks read-only via `customer-portal` (sibling module) — task changes are projected by listeners that emit `PortalTaskCreatedEvent`/`PortalTaskUpdatedEvent`/`PortalTaskDeletedEvent` from `TaskService`. See [`30-modules/customer-portal.md`](customer-portal.md) and ADR-024.

## 5. Domain events

All published via `ApplicationEventPublisher` from `TaskService` (`→ task/TaskService.java`). Listed in `event/DomainEvent.java` (the sealed permit list) and consumed by `automation/AutomationEventListener` (all events) and `notification/NotificationService` (selected). Per A1 §4:

| Event | Emitted from | Trigger |
|---|---|---|
| `TaskStatusChangedEvent` | `updateTask` `→ TaskService.java:600` | Status change via update; carries `oldStatus`, `newStatus`, `assigneeId`, `automationExecutionId` (so automation-triggered transitions can be traced). |
| `TaskCompletedEvent` | `completeTask` `→ TaskService.java:839` | `DONE` transition. Enriched details include `project_name` + `customer_name`/`customer_id` so automation `{{project.name}}`/`{{customer.name}}` resolve. |
| `TaskCancelledEvent` | `cancelTask` `→ TaskService.java:993` | `CANCELLED` transition. |
| `TaskReopenedEvent` | `reopenTask` `→ TaskService.java:1056` | Re-`OPEN` from terminal state. |
| `TaskAssignedEvent` | `updateTask` (when assignee changes) `→ TaskService.java:572` | Assignee set or changed. Drives notification fan-out. |
| `TaskClaimedEvent` | `claimTask` `→ TaskService.java:732` | Self-assignment. `previousAssigneeId` is always `null` (claim only works on unassigned). |
| `TaskRecurrenceCreatedEvent` | `createRecurringNextInstance` `→ TaskService.java:936` | A new instance was spawned on completion of a recurring parent. Carries `nextDueDate` and `parentTaskId`. |

In addition, portal read-model events `PortalTaskCreatedEvent / PortalTaskUpdatedEvent / PortalTaskDeletedEvent` are emitted by `publishPortalTaskEventIfLinked(...)` `→ TaskService.java:1081` whenever the task's project is linked to at least one customer. These are part of the portal read-model module, not the core task event set.

`AutomationEventListener` subscribes to *every* `DomainEvent` (`AutomationEventListener.java:25`, A1 §4) without `@TransactionalEventListener` semantics — task events are visible to automation rules in the same transaction. Notification listeners use `AFTER_COMMIT` (per `_discovery/A6-cross-cutting.md` §6).

## 6. Cross-cutting touchpoints

### Capability gates

The Task module does **not** apply `@RequiresCapability` blanket-style. Instead:

- Most endpoints require project membership via `ProjectAccessService.requireViewAccess(projectId, actor)` (called at the head of every service method).
- Mutating endpoints layer additional role checks inside the service: e.g. `cancelTask` requires `admin`/`owner` (`TaskService.java:969`); `deleteTask` and `updateTask` require `access.canEdit()` (lead/admin/owner) *or* assignee-on-own-task (`TaskService.java:465, 658`). Pre-assign at create is admin/owner only, silently ignored otherwise (`TaskService.java:317`, see also the `createTask` Javadoc explaining the asymmetry with re-assignment).
- The `PROJECT_MANAGEMENT` capability (slug `PROJECT_MANAGEMENT` in `Capability` enum; UI shorthand "PROJ_MGMT") is required for the field-group endpoint and all `TaskItem` mutations: `TaskController.java:239`, `TaskItemController.java:52,72,81`.
- `projectLifecycleGuard.requireNotReadOnly(projectId)` blocks task creation against archived projects (`TaskService.java:267`).

### Audit

Every transition writes one `audit_event`. Entry points: `task.created`, `task.updated` (with per-field delta via `AuditDeltaBuilder`), `task.deleted`, `task.claimed`, `task.released`, `task.completed`, `task.cancelled`, `task.reopened`, `task.recurrence_created`. Ten distinct event types span `TaskService.java:337–1051`. Audit shares the source transaction (per `_discovery/A6-cross-cutting.md` §3), so a rolled-back task change cannot leave an orphan audit row.

### Claim / release flow

`claim` is a conditional self-assignment: the service refuses if `assigneeId` is already non-null (`TaskService.java:707`) — there is no race-window UPDATE-with-WHERE because `@Version` optimistic locking on `Task` covers concurrent claims. Per ADR-019 the design is deliberately a single-table claim, not a workflow engine. `release` validates the actor is the current assignee or has edit rights.

### Recurrence

Recurring tasks store an RFC 5545–style RRULE in `Task.recurrenceRule` plus optional `recurrenceEndDate`. Lineage uses a `parentTaskId` chain: the *first* instance is its own root (`Task.getRootTaskId()` returns `id` when `parentTaskId == null`); spawned instances all point at the **root** task ID, not the previous instance — so the chain depth is always 1 (`TaskService.java:906`, see also §10 Open Questions for verification).

On completion of a recurring task, `createRecurringNextInstance` `→ TaskService.java:869` runs in the same transaction:
1. Parses the RRULE via `RecurrenceRule.parse`.
2. Computes `nextDueDate` from the **completed task's `dueDate`**, not from `now()`. Per ADR-070 the next due date is pre-calculated and stored — there is no scheduler scanning for "tasks that should spawn now".
3. Skips spawning if `recurrenceEndDate` has passed.
4. Copies title, description, priority, type, recurrence rule, end date, and assignee (auto-claim) onto the new instance.
5. Emits `task.recurrence_created` audit + `TaskRecurrenceCreatedEvent` + portal event if customer-linked.

Cancel does *not* spawn a successor — recurrence stops silently. Reopen + complete resumes it.

### Inline sub-items vs compliance checklists

`TaskItem` (this module) is a per-task to-do list rendered inside `task-detail-sheet.tsx` via `task-sub-items.tsx`. It is **not** the same model as `ChecklistInstance` (`checklist` module, `ChecklistTemplate.java:16`), which is per-customer / per-customer-type compliance gating with pack-installed templates. Per ADR-061 checklists were promoted to first-class entities specifically because they have a different lifecycle, ownership, and pack-install surface from inline task items.

## 7. Vertical specifics

- **legal-za** — the UI label "Action Item" replaces "Task" everywhere on the frontend via `lib/terminology-map.ts:49–52` (`Task → "Action Item"`, `task → "action item"`, plurals). Backend names, tables, REST paths, and event types are unchanged. Per `glossary.md:33,256` the override is purely terminology — there is no separate `ActionItem` entity.
- **accounting-za / consulting-***: no override; default "Task" label.
- No module gate on tasks themselves — tasks are universally available across all verticals (the `tasks` module is not in `enabledModules` because it is implicit/core).

## 8. Active ADRs

| ADR | Bearing |
|---|---|
| [ADR-019](../adr/ADR-019-task-claim-workflow.md) | Single-table task model with assignee column; claim/release as conditional UPDATE; rejected workflow engine and separate `task_assignments` table. Anchors §6 claim/release. |
| [ADR-024](../adr/ADR-024-portal-task-time-seams.md) | Portal-ready seams: do not build portal endpoints/events for tasks ahead of demand; ensure service layer is portal-reusable. The `PortalTask*Event` projections (§5) are the realised outcome. |
| [ADR-061](../adr/ADR-061-checklist-first-class-entities.md) | Checklists are first-class (separate module) — verifies the cross-link to [`checklists`](checklists.md) and the `TaskItem` ≠ `ChecklistInstance` distinction in §6. |
| [ADR-070](../adr/ADR-070-pre-calculated-next-execution-date.md) | Next-execution date is pre-calculated, not scanned. Anchors the "no recurrence scheduler" claim in §6 — recurrence advances on completion, not on a polling job. |

## 9. Key flows

- **Automation trigger to action.** Tasks are both a frequent automation *trigger* (via `TaskStatusChangedEvent`, `TaskCompletedEvent`, `TaskAssignedEvent`) and a frequent automation *action target* (via the `CREATE_TASK` action type — see `glossary.md:34` `Action Type` enum). Full walkthrough in [`50-flows/automation-trigger-to-action.md`](../50-flows/automation-trigger-to-action.md).
- **Portal magic-link to task completion.** Customer-side surfacing of tasks via portal read-model — see [`50-flows/portal-magic-link-to-task-completion.md`](../50-flows/portal-magic-link-to-task-completion.md).
- **Matter to cash.** Tasks anchor `TimeEntry` rows that ultimately become invoice lines — see [`50-flows/matter-to-cash.md`](../50-flows/matter-to-cash.md).

## 10. Open questions / known fragility

- **Parent-chain depth.** Code review of `TaskService.java:906` shows `parentTaskId` is set to `completedTask.getRootTaskId()` (i.e. *root*, not parent), so the chain is at most depth 1 by construction. This contradicts a depth concern raised in the source brief — the chain is bounded. **To verify**: confirm there is no path where a task that already has `parentTaskId` set has its own `parentTaskId` overwritten to an instance, and that there are no historical migrations producing a multi-hop chain. If a depth-N lineage was ever needed (audit "show me all 12 weekly instances"), the right model is a `series_id` column or an event-sourced view, not deeper FK chains.
- **Claim flow usage.** `claimTask` is wired in `task-list-panel.tsx:377` and exercised in unit tests (`task-list-panel.test.tsx`, `task-recurrence.test.tsx`), so the UI reaches it. **Open**: how often it is actually exercised by users vs the more common admin pre-assignment pattern. ADR-019 anticipated this — the model supports both, but if claim is dead UX, the "self-claim" affordance can be hidden without backend churn.
- **Recurrence on cancel.** Cancel silently stops recurrence (no successor spawn, `TaskService.java:1011` comment). This is correct for "stop the series" but conflates two intents: "this one instance is moot" vs "stop the recurring series". A separate `stopRecurrence(taskId)` operation may eventually be needed; for now reopen-then-complete is the documented escape hatch.
- **Authorization is service-layer scattered.** Unlike Trust Accounting (single guard), Task auth is a patchwork of `ProjectAccessService`, role string checks (`"admin"`/`"owner"` literals), and assignee equality. A consolidated `TaskAccessService` would reduce drift risk; tracked informally.
- **Sub-items audit.** `TaskItem` mutations (`TaskItemController`) are capability-gated but it is unclear from this module whether they emit audit events on toggle/edit/delete. **To verify** when filling in [`audit.md`](audit.md).
