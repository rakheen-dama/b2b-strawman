# Phase 5 — Task & Time Lifecycle

Phase 5 extends the existing task management capability (Phase 4) with **time tracking** and **cross-project work views**. It adds a `TimeEntry` entity for billable/non-billable time recording, a "My Work" aggregation layer for staff, and per-project time rollups. All additions are evolutionary -- they reuse the existing tenant isolation model, follow the same entity patterns, and require no changes to the core multi-tenant or billing architecture. See `phase5-task-time-lifecycle.md` (Section 11 of ARCHITECTURE.md) and [ADR-021](../adr/ADR-021-time-tracking-model.md)--[ADR-024](../adr/ADR-024-portal-task-time-seams.md) for design details.

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 44 | TimeEntry Backend — Entity, CRUD & Validation | Backend | — | M | 44A, 44B | |
| 45 | TimeEntry Frontend — Log Time Dialog & Task Time List | Frontend | 44 | M | 45A, 45B | |
| 46 | Project Time Summary — Backend | Backend | 44 | M | 46A | |
| 47 | Project Time Summary — Frontend | Frontend | 46 | S | 47A | |
| 48 | My Work — Backend | Backend | 44 | M | 48A | |
| 49 | My Work — Frontend | Frontend | 48 | M | 49A, 49B | |

## Dependency Graph

```
[E44 TimeEntry Backend] ──────────────┬──► [E45 TimeEntry Frontend]
                                       ├──► [E46 Project Time Summary Backend] ──► [E47 Project Time Summary Frontend]
                                       └──► [E48 My Work Backend] ──────────────► [E49 My Work Frontend]
```

**Parallel tracks**: After Epic 44 (TimeEntry Backend) lands, Epics 45 (TimeEntry Frontend), 46 (Project Time Summary Backend), and 48 (My Work Backend) can all begin in parallel -- they have zero dependency on each other. Epics 47 and 49 depend on their respective backend epics.

## Implementation Order

### Stage 1: Backend Foundation

| Order | Epic | Rationale |
|-------|------|-----------|
| 1 | Epic 44: TimeEntry Backend | Migration + entity + CRUD is the prerequisite for all other Phase 5 work. |

### Stage 2: Frontend + Aggregation + My Work (Parallel Tracks)

| Order | Epic | Rationale |
|-------|------|-----------|
| 2a | Epic 45: TimeEntry Frontend | Depends on time entry API from Epic 44. |
| 2b | Epic 46: Project Time Summary Backend | Depends on `time_entries` table from Epic 44. |
| 2c | Epic 48: My Work Backend | Depends on `time_entries` table from Epic 44, plus existing Task/ProjectMember entities from Phase 4. |

### Stage 3: Remaining Frontend (After Stage 2)

| Order | Epic | Rationale |
|-------|------|-----------|
| 3a | Epic 47: Project Time Summary Frontend | Depends on summary API from Epic 46. |
| 3b | Epic 49: My Work Frontend | Depends on My Work API from Epic 48. |

### Timeline

```
Stage 1:  [E44]                       <- foundation (must complete first)
Stage 2:  [E45] [E46] [E48]          <- parallel (after E44)
Stage 3:  [E47] [E49]                <- parallel (after their respective Stage 2 deps)
```

---

## Epic 44: TimeEntry Backend — Entity, CRUD & Validation

**Goal**: Create the `time_entries` table, implement TimeEntry entity with full CRUD endpoints nested under tasks, enforce permission model (creator or project lead+ for edit/delete), and validate inputs. Includes tenant isolation verification for both Pro (dedicated schema) and Starter (shared schema + `@Filter`).

**References**: [ADR-021](../adr/ADR-021-time-tracking-model.md), `phase5-task-time-lifecycle.md` Section 11.1.1, 11.2.3, 11.3.2, 11.7.1

**Dependencies**: None (builds on existing Phase 4 Task entity and ProjectAccessService)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **44A** | 44.1--44.5 | V13 migration, TimeEntry entity, TimeEntryRepository, basic service (create + list by task), integration tests | **Done** (PR #88) |
| **44B** | 44.6--44.11 | TimeEntry update, delete, permission enforcement (creator or lead+), top-level controller endpoints, access control tests, shared schema isolation tests | **Done** (PR #89) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 44.1 | Create V13 tenant migration for time_entries table | 44A | | `db/migration/tenant/V13__create_time_entries.sql`. Columns per Section 11.1.1: `id` (UUID PK DEFAULT gen_random_uuid()), `task_id` (UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE), `member_id` (UUID NOT NULL REFERENCES members(id)), `date` (DATE NOT NULL), `duration_minutes` (INTEGER NOT NULL), `billable` (BOOLEAN NOT NULL DEFAULT true), `rate_cents` (INTEGER), `description` (TEXT), `tenant_id` (VARCHAR(255)), `created_at` (TIMESTAMPTZ NOT NULL DEFAULT now()), `updated_at` (TIMESTAMPTZ NOT NULL DEFAULT now()). CHECK constraints: `chk_duration_positive CHECK (duration_minutes > 0)`, `chk_rate_non_negative CHECK (rate_cents >= 0 OR rate_cents IS NULL)`. Indexes: `idx_time_entries_task_id`, `idx_time_entries_member_id_date`, `idx_time_entries_task_id_date`, `idx_time_entries_task_id_billable`, `idx_time_entries_tenant_id`. RLS: `ALTER TABLE time_entries ENABLE ROW LEVEL SECURITY; CREATE POLICY tenant_isolation_time_entries ON time_entries USING (tenant_id = current_setting('app.current_tenant', true) OR tenant_id IS NULL) WITH CHECK (tenant_id = current_setting('app.current_tenant', true) OR tenant_id IS NULL)`. Pattern: follow `V11__create_tasks.sql` structure. |
| 44.2 | Create TimeEntry entity | 44A | | `timeentry/TimeEntry.java` -- JPA entity mapped to `time_entries`. Fields: UUID id, taskId (UUID, NOT NULL), memberId (UUID, NOT NULL), date (LocalDate, NOT NULL), durationMinutes (int, NOT NULL), billable (boolean, NOT NULL, default true), rateCents (Integer, nullable), description (String, nullable), tenantId (String), createdAt (Instant), updatedAt (Instant). Annotations: `@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))`, `@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")`, `@EntityListeners(TenantAwareEntityListener.class)`, implements `TenantAware`. Constructor sets `createdAt`/`updatedAt` to `Instant.now()`. No `@Version` -- time entries do not need optimistic locking. Pattern: follow `task/Task.java` entity structure exactly. |
| 44.3 | Create TimeEntryRepository | 44A | | `timeentry/TimeEntryRepository.java` -- extends `JpaRepository<TimeEntry, UUID>`. Methods: `Optional<TimeEntry> findOneById(UUID id)` (JPQL `@Query` for `@Filter` compatibility -- CRITICAL, do NOT use `findById()`), `List<TimeEntry> findByTaskId(UUID taskId)` (JPQL, ORDER BY `date DESC, createdAt DESC`), `List<TimeEntry> findByMemberIdAndDateBetween(UUID memberId, LocalDate from, LocalDate to)` (JPQL, ORDER BY `date DESC, createdAt DESC`). Pattern: follow `task/TaskRepository.java` with JPQL `findOneById`. |
| 44.4 | Create TimeEntryService (create + list) and TimeEntryController (task-scoped endpoints) | 44A | | **Service** (`timeentry/TimeEntryService.java`): constructor injection of `TimeEntryRepository`, `TaskRepository`, `ProjectAccessService`. `createTimeEntry(UUID taskId, LocalDate date, int durationMinutes, boolean billable, Integer rateCents, String description, UUID memberId, String orgRole)` -- looks up task via `taskRepository.findOneById()`, checks project view access via `projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole)`, validates duration > 0 (throw `InvalidStateException`), creates and saves `TimeEntry`. `listTimeEntriesByTask(UUID taskId, UUID memberId, String orgRole)` -- checks project view access, returns `timeEntryRepository.findByTaskId(taskId)`. All `@Transactional`. **Controller** (`timeentry/TimeEntryController.java`): `POST /api/tasks/{taskId}/time-entries` (201 Created, MEMBER+), `GET /api/tasks/{taskId}/time-entries` (200, MEMBER+). DTOs: `CreateTimeEntryRequest(LocalDate date, int durationMinutes, Boolean billable, Integer rateCents, String description)` with `@NotNull` on date and durationMinutes, `TimeEntryResponse(UUID id, UUID taskId, UUID memberId, String memberName, LocalDate date, int durationMinutes, boolean billable, Integer rateCents, String description, Instant createdAt, Instant updatedAt)` with `static from(TimeEntry, Map<UUID, String> memberNames)`. Use `RequestScopes.requireMemberId()` and `RequestScopes.getOrgRole()`. Batch-resolve member names via `MemberRepository.findAllById()`. Pattern: follow `task/TaskController.java`. |
| 44.5 | Add TimeEntry create + list integration tests | 44A | | `timeentry/TimeEntryIntegrationTest.java`. ~12 tests: create time entry (201), create with missing date (400), create with zero duration (400), create with negative duration (400), create with negative rateCents (400 -- DB CHECK violation), create with valid rateCents (201), list time entries for task, list empty task returns empty list, non-project-member cannot create (404 -- security-by-obscurity), non-project-member cannot list (404), tenant isolation (time entry in tenant A invisible in tenant B), task not found returns 404. Seed test members, project, task in `@BeforeAll` with Pro tier (follow `TaskIntegrationTest.java` pattern). Sync plan to PRO for test orgs. |
| 44.6 | Add update and delete endpoints | 44B | | Add to `TimeEntryService.java`: `updateTimeEntry(UUID timeEntryId, LocalDate date, Integer durationMinutes, Boolean billable, Integer rateCents, String description, UUID memberId, String orgRole)` -- finds via `findOneById()`, resolves task to get projectId, checks permission (creator OR `projectAccessService.checkAccess().canEdit()`), validates durationMinutes > 0 if provided, applies non-null fields, saves. `deleteTimeEntry(UUID timeEntryId, UUID memberId, String orgRole)` -- same permission check, hard delete. Add to `TimeEntryController.java`: `PUT /api/time-entries/{id}` (200, MEMBER+), `DELETE /api/time-entries/{id}` (204, MEMBER+). DTOs: `UpdateTimeEntryRequest(LocalDate date, Integer durationMinutes, Boolean billable, Integer rateCents, String description)` -- all nullable for partial update. Pattern: follow `TaskController` update/delete pattern but with creator-or-lead permission model. |
| 44.7 | Implement permission enforcement logic | 44B | | In `TimeEntryService`: extract a private method `requireEditPermission(TimeEntry entry, UUID memberId, String orgRole)` -- if `entry.getMemberId().equals(memberId)` return (creator allowed). Otherwise check `projectAccessService.checkAccess(task.getProjectId(), memberId, orgRole).canEdit()` -- if false, throw `ForbiddenException("Cannot modify time entry", "Only the creator or a project lead/admin/owner can modify this time entry")`. Reuse in both `updateTimeEntry` and `deleteTimeEntry`. |
| 44.8 | Add update integration tests | 44B | | Add to `TimeEntryIntegrationTest.java` or new `TimeEntryUpdateIntegrationTest.java`. ~8 tests: creator can update own entry (200), creator can update duration only (partial update 200), creator can update billable flag (200), lead can update any member's entry (200), contributor cannot update another member's entry (403), cannot update with zero duration (400), update non-existent entry (404), update preserves unchanged fields. |
| 44.9 | Add delete integration tests | 44B | | ~5 tests: creator can delete own entry (204), lead can delete any member's entry (204), contributor cannot delete another member's entry (403), delete non-existent entry (404), after delete GET returns 404. |
| 44.10 | Add access control edge case tests | 44B | | ~4 tests: org admin can edit any time entry in any project (200), org owner can delete any time entry (204), project contributor can only modify own entries, verify `task_id` and `member_id` are immutable on update (not changeable via PUT). |
| 44.11 | Verify shared schema isolation for time entries | 44B | | Provision two Starter orgs, create time entries in each, verify entries are isolated (org A cannot see org B's entries). Verify `tenant_id` is populated correctly on shared schema entities. ~3 additional tests. Pattern: follow `StarterTenantIntegrationTest.java`. |

### Key Files

**Slice 44A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V13__create_time_entries.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryIntegrationTest.java`

**Slice 44A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/Task.java` -- entity pattern reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskRepository.java` -- JPQL findOneById pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskController.java` -- controller + DTO pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAware.java` -- interface to implement
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAwareEntityListener.java` -- entity listener
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/task/TaskIntegrationTest.java` -- test setup pattern

**Slice 44B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` -- Add update/delete + permission
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryController.java` -- Add PUT/DELETE endpoints

**Slice 44B -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryAccessControlTest.java` (or extend existing test class)

**Slice 44B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectAccessService.java` -- `checkAccess().canEdit()` pattern
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/task/TaskClaimIntegrationTest.java` -- permission test pattern
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/StarterTenantIntegrationTest.java` -- shared schema test pattern

### Architecture Decisions

- **`timeentry/` package**: New feature package following existing `task/`, `customer/`, `project/` pattern.
- **No `@Version`**: Time entries do not have concurrent-claim semantics. Last-write-wins is acceptable for edit operations on time tracking data.
- **Creator-or-lead permission model**: Different from Task (which uses project-access-only). Time entries have a dual permission check: creator always allowed, otherwise `canEdit()` required.
- **Hard delete**: Time entries are operational data, not auditable records. Hard delete keeps the data model simple.
- **JPQL `findOneById`**: Required because `JpaRepository.findById()` uses `EntityManager.find()` which bypasses Hibernate `@Filter`. Same pattern as Task and Customer.
- **Two-slice decomposition**: 44A (migration + entity + basic CRUD) is the foundation. 44B (update/delete + permissions + access control tests) layers on the permission model. This keeps each slice under 8 files created.

---

## Epic 45: TimeEntry Frontend — Log Time Dialog & Task Time List

**Goal**: Build the frontend for logging time against tasks. Includes `LogTimeDialog` for creating entries, `TimeEntryList` for viewing entries on a task, edit/delete actions with permission-based visibility, and duration formatting utilities.

**References**: `phase5-task-time-lifecycle.md` Section 11.7.2, 11.2.3

**Dependencies**: Epic 44 (TimeEntry API)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **45A** | 45.1--45.5 | TypeScript types, server actions (create/list), LogTimeDialog, TimeEntryList component, integration into task detail | **Done** (PR #90) |
| **45B** | 45.6--45.10 | Edit/delete actions, EditTimeEntryDialog, permission-based action visibility, duration formatting, tests | **Done** (PR #91) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 45.1 | Add TimeEntry TypeScript types | 45A | | In `lib/types.ts`: `TimeEntry { id, taskId, memberId, memberName, date, durationMinutes, billable, rateCents, description, createdAt, updatedAt }`, `CreateTimeEntryRequest { date, durationMinutes, billable?, rateCents?, description? }`, `UpdateTimeEntryRequest { date?, durationMinutes?, billable?, rateCents?, description? }`. Pattern: follow existing `Task` type definitions. |
| 45.2 | Create time entry server actions | 45A | | `app/(app)/org/[slug]/projects/[id]/time-entry-actions.ts` -- `fetchTimeEntries(taskId)` calls `GET /api/tasks/{taskId}/time-entries`, `createTimeEntry(slug, projectId, taskId, formData)` calls `POST /api/tasks/{taskId}/time-entries` with `revalidatePath`. Standard `ActionResult` pattern. Pattern: follow `task-actions.ts`. |
| 45.3 | Build LogTimeDialog component | 45A | | `components/tasks/log-time-dialog.tsx` -- client component. Shadcn Dialog triggered by "Log Time" outline button on task detail. Form fields: Duration (two inputs: hours + minutes, converted to `durationMinutes` on submit), Date (date input, default today), Description (optional textarea), Billable (checkbox, default checked). "Log Time" pill button + "Cancel" plain button. Server action `createTimeEntry`. Inline validation: hours + minutes must sum > 0. Form reset on dialog close. Pattern: follow `create-task-dialog.tsx`. |
| 45.4 | Build TimeEntryList component | 45A | | `components/tasks/time-entry-list.tsx` -- client component. Renders below the task detail. Header: "Time Entries" label + total duration badge (formatted as "Xh Ym"). Catalyst-style table rows: Date, Duration (formatted), Member name, Billable (badge: green "Billable" / olive "Non-billable"), Description (truncated). Empty state: Clock icon + "No time logged yet". Pattern: follow `task-list-panel.tsx` for table styling. |
| 45.5 | Integrate time entries into task detail view | 45A | | Modify `app/(app)/org/[slug]/projects/[id]/page.tsx` or `components/tasks/task-list-panel.tsx` -- when a task is expanded/selected, show time entries below it. Add "Log Time" button alongside existing task action buttons. Fetch time entries per task. Consider a task detail expandable row or a task detail dialog that includes time entries. Pattern: follow existing project detail page tab integration. |
| 45.6 | Add update and delete server actions | 45B | | Add to `time-entry-actions.ts`: `updateTimeEntry(slug, projectId, timeEntryId, data)` calls `PUT /api/time-entries/{id}`, `deleteTimeEntry(slug, projectId, timeEntryId)` calls `DELETE /api/time-entries/{id}`. Both use `revalidatePath`. Handle 403 errors with user-friendly messages. Pattern: follow `task-actions.ts` update/delete. |
| 45.7 | Build EditTimeEntryDialog component | 45B | | `components/tasks/edit-time-entry-dialog.tsx` -- client component. Pre-populated form with existing time entry data. Same fields as LogTimeDialog. "Save" pill button + "Cancel" plain button. Pattern: follow `edit-project-dialog.tsx`. |
| 45.8 | Build DeleteTimeEntryDialog component | 45B | | `components/tasks/delete-time-entry-dialog.tsx` -- AlertDialog confirmation. "Delete this time entry? This action cannot be undone." "Delete" destructive pill + "Cancel" plain. Controlled `open` state (server action revalidates, does not redirect). Pattern: follow `delete-project-dialog.tsx`. |
| 45.9 | Add permission-based action visibility and duration formatting | 45B | | In `TimeEntryList`: show edit/delete buttons only for own entries OR if user is lead/admin/owner. Pass current `memberId` and `orgRole` as props. Create `lib/format.ts` addition: `formatDuration(minutes: number): string` -- returns "1h 30m", "45m", "2h". Update `TimeEntryList` and `LogTimeDialog` to use this formatting. Pattern: follow role-based button visibility in `task-list-panel.tsx`. |
| 45.10 | Add frontend tests | 45B | | `__tests__/components/tasks/log-time-dialog.test.tsx` and `__tests__/components/tasks/time-entry-list.test.tsx`. ~8 tests: LogTimeDialog renders form fields, validates duration > 0, submits with correct data format; TimeEntryList renders entries with formatted duration, shows empty state, edit/delete visible for own entries, edit/delete hidden for others' entries (contributor), billable badge styling. Pattern: follow `add-member-dialog.test.tsx`. Always include `afterEach(() => cleanup())` for Radix Dialog. |

### Key Files

**Slice 45A -- Create:**
- `frontend/app/(app)/org/[slug]/projects/[id]/time-entry-actions.ts`
- `frontend/components/tasks/log-time-dialog.tsx`
- `frontend/components/tasks/time-entry-list.tsx`

**Slice 45A -- Modify:**
- `frontend/lib/types.ts` -- Add TimeEntry types
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` or `frontend/components/tasks/task-list-panel.tsx` -- Integrate time entries

**Slice 45B -- Create:**
- `frontend/components/tasks/edit-time-entry-dialog.tsx`
- `frontend/components/tasks/delete-time-entry-dialog.tsx`
- `frontend/__tests__/components/tasks/log-time-dialog.test.tsx`
- `frontend/__tests__/components/tasks/time-entry-list.test.tsx`

**Slice 45B -- Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/time-entry-actions.ts` -- Add update/delete actions
- `frontend/components/tasks/time-entry-list.tsx` -- Add edit/delete buttons, permission checks
- `frontend/lib/format.ts` -- Add `formatDuration` utility

### Architecture Decisions

- **Duration input as hours + minutes**: Users think in "1 hour 30 minutes", not "90 minutes". Two input fields converted to `durationMinutes` on submit.
- **Time entries shown per-task, not per-project**: Time is logged against tasks. The task detail view is the natural location for viewing and creating time entries.
- **Permission check via props**: Current `memberId` and `orgRole` passed from the server component. Frontend hides actions client-side; backend enforces server-side.
- **Controlled AlertDialog for delete**: Delete action revalidates the page (does not redirect), so the dialog needs controlled `open` state to close after action completes.

---

## Epic 46: Project Time Summary — Backend

**Goal**: Implement backend aggregation queries and controller endpoints for per-project time summaries: total billable/non-billable rollup, per-member breakdown (lead+ only), and per-task breakdown. On-the-fly SQL aggregation per ADR-022.

**References**: [ADR-022](../adr/ADR-022-time-aggregation-strategy.md), `phase5-task-time-lifecycle.md` Section 11.2.4, 11.3.3, 11.8.2

**Dependencies**: Epic 44 (TimeEntry entity and repository)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **46A** | 46.1--46.7 | Aggregation queries in repository, ProjectTimeSummaryController with 3 endpoints, access control, integration tests | **Done** (PR #92) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 46.1 | Add aggregation queries to TimeEntryRepository | 46A | | Add native or JPQL queries: `projectTimeSummary(UUID projectId)` -- returns `ProjectTimeSummaryProjection` (billableMinutes, nonBillableMinutes, totalMinutes, contributorCount, entryCount). `projectTimeSummaryByMember(UUID projectId)` -- returns `List<MemberTimeSummaryProjection>` (memberId, memberName, billableMinutes, nonBillableMinutes, totalMinutes). `projectTimeSummaryByTask(UUID projectId)` -- returns `List<TaskTimeSummaryProjection>` (taskId, taskTitle, billableMinutes, totalMinutes, entryCount). All queries accept optional `LocalDate from, LocalDate to` parameters for date-range scoping. Use `@Query(nativeQuery = true)` for complex GROUP BY with JOINs (JPQL doesn't support all aggregation patterns cleanly). Ensure queries join through `tasks` table (`JOIN tasks t ON te.task_id = t.id WHERE t.project_id = :projectId`). Pattern: follow existing `TaskRepository` custom queries, but with native SQL for aggregation. |
| 46.2 | Create summary projection interfaces | 46A | | `timeentry/ProjectTimeSummaryProjection.java` (interface with getters: `Long getBillableMinutes()`, `Long getNonBillableMinutes()`, `Long getTotalMinutes()`, `Long getContributorCount()`, `Long getEntryCount()`). `timeentry/MemberTimeSummaryProjection.java` (UUID getMemberId, String getMemberName, Long getBillableMinutes, Long getNonBillableMinutes, Long getTotalMinutes). `timeentry/TaskTimeSummaryProjection.java` (UUID getTaskId, String getTaskTitle, Long getBillableMinutes, Long getTotalMinutes, Long getEntryCount). Pattern: Spring Data JPA projection interfaces. |
| 46.3 | Create ProjectTimeSummaryController | 46A | | `timeentry/ProjectTimeSummaryController.java` -- Three endpoints: `GET /api/projects/{id}/time-summary` (MEMBER+, returns project totals), `GET /api/projects/{id}/time-summary/by-member` (lead/admin/owner only, returns per-member breakdown), `GET /api/projects/{id}/time-summary/by-task` (MEMBER+, returns per-task breakdown). All accept optional `from` and `to` query params (`@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate`). DTOs: `ProjectTimeSummaryResponse`, `MemberTimeSummaryResponse`, `TaskTimeSummaryResponse` as nested records. Access control: call `projectAccessService.requireViewAccess()` for totals and by-task; call `requireViewAccess()` then check `canEdit()` for by-member (throw `ForbiddenException` if contributor). Pattern: follow `TaskController.java` style. |
| 46.4 | Add TimeEntryService aggregation methods | 46A | | Add to `TimeEntryService`: `getProjectTimeSummary(UUID projectId, UUID memberId, String orgRole, LocalDate from, LocalDate to)`, `getProjectTimeSummaryByMember(UUID projectId, UUID memberId, String orgRole, LocalDate from, LocalDate to)`, `getProjectTimeSummaryByTask(UUID projectId, UUID memberId, String orgRole, LocalDate from, LocalDate to)`. Each checks access and delegates to repository. By-member additionally checks `canEdit()` to restrict to lead+. All `@Transactional(readOnly = true)`. |
| 46.5 | Add project summary integration tests | 46A | | `timeentry/ProjectTimeSummaryIntegrationTest.java`. ~10 tests: project time summary returns correct totals (billable + non-billable), empty project returns zeroes, date range filters correctly, by-member breakdown returns per-member rows, by-member restricted to lead (contributor gets 403), by-task breakdown returns per-task rows, admin can access by-member, non-project-member gets 404, multiple time entries aggregate correctly, verify billable/non-billable separation. Seed test data: project with 2+ members, 3+ tasks, 5+ time entries across members and tasks. |
| 46.6 | Add date range filtering tests | 46A | | ~3 tests: time entries outside date range excluded from summary, NULL date range returns all-time totals, single-day range (`from == to`) returns only that day's entries. |
| 46.7 | Verify aggregation respects tenant isolation | 46A | | ~2 tests: time entries in tenant A do not appear in tenant B's project summary, shared schema (Starter) aggregation respects `@Filter` / tenant_id. |

### Key Files

**Slice 46A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/ProjectTimeSummaryController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/ProjectTimeSummaryProjection.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/MemberTimeSummaryProjection.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TaskTimeSummaryProjection.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/ProjectTimeSummaryIntegrationTest.java`

**Slice 46A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRepository.java` -- Add aggregation queries
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` -- Add aggregation service methods

**Slice 46A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectAccessService.java` -- `checkAccess().canEdit()` for by-member restriction
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryController.java` -- existing TimeEntry patterns
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryIntegrationTest.java` -- test setup to reuse

### Architecture Decisions

- **On-the-fly SQL aggregation (ADR-022)**: No materialized views or summary tables. PostgreSQL handles 20,000-row aggregations in single-digit milliseconds with proper indexes.
- **Native queries for aggregation**: JPQL lacks full support for conditional SUM (`CASE WHEN ... THEN ... ELSE 0 END`) and multi-table GROUP BY. Native SQL is clearer and more maintainable for these queries.
- **Spring Data projection interfaces**: Return typed projections from native queries without mapping to DTOs manually. Clean pattern that avoids `Object[]` results.
- **Per-member breakdown restricted to lead+**: Per-member time breakdowns reveal individual staff productivity -- management data. Contributors see only totals and per-task breakdowns.
- **Single-slice epic**: This is 7 tasks touching ~8 files created and ~2 modified. Fits within slice limits because the aggregation queries are self-contained and the controller follows an established pattern.

---

## Epic 47: Project Time Summary — Frontend

**Goal**: Build a `TimeSummaryPanel` component for the project detail page showing total billable/non-billable time, per-task breakdown, and per-member breakdown (for leads/admins/owners). Adds a "Time" tab to the existing project detail tabs.

**References**: `phase5-task-time-lifecycle.md` Section 11.7.2

**Dependencies**: Epic 46 (Project Time Summary API)

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **47A** | 47.1--47.6 | TypeScript types, server actions, TimeSummaryPanel component, tab integration, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 47.1 | Add TimeSummary TypeScript types | 47A | | In `lib/types.ts`: `ProjectTimeSummary { projectId, billableMinutes, nonBillableMinutes, totalMinutes, contributorCount, entryCount }`, `MemberTimeSummary { memberId, memberName, billableMinutes, nonBillableMinutes, totalMinutes }`, `TaskTimeSummary { taskId, taskTitle, billableMinutes, totalMinutes, entryCount }`. |
| 47.2 | Create time summary server actions | 47A | | `app/(app)/org/[slug]/projects/[id]/time-summary-actions.ts` -- `fetchProjectTimeSummary(projectId, from?, to?)` calls `GET /api/projects/{id}/time-summary`, `fetchTimeSummaryByMember(projectId, from?, to?)` calls `GET /api/projects/{id}/time-summary/by-member`, `fetchTimeSummaryByTask(projectId, from?, to?)` calls `GET /api/projects/{id}/time-summary/by-task`. Handle 403 on by-member gracefully (return null, hide section). Pattern: follow `task-actions.ts`. |
| 47.3 | Build TimeSummaryPanel component | 47A | | `components/projects/time-summary-panel.tsx` -- client component. Shows three sections: (1) **Total Summary** -- stat cards: "Total Time" (formatted as "Xh Ym"), "Billable" (green), "Non-billable" (olive), "Contributors", "Entries". Use `formatDuration` from `lib/format.ts`. (2) **By Task** -- Catalyst-style table: Task Title, Billable Time, Total Time, Entries. Sorted by total desc. (3) **By Member** (lead+ only) -- table: Member Name, Billable Time, Non-billable Time, Total Time. Hidden if fetch returns null/403. Empty state: Clock icon + "No time tracked yet". Pattern: follow `project-members-panel.tsx` for table styling. |
| 47.4 | Add date range picker | 47A | | Optional date range filter at the top of `TimeSummaryPanel` -- "From" and "To" date inputs. Default: no range (all time). When dates change, re-fetch summaries. Simple `<input type="date">` styled with olive theme. |
| 47.5 | Integrate as "Time" tab on project detail page | 47A | | Modify `components/projects/project-tabs.tsx` -- add "Time" tab alongside "Documents", "Members", "Tasks". Modify `app/(app)/org/[slug]/projects/[id]/page.tsx` -- fetch time summary data, pass to `TimeSummaryPanel`. Pattern: follow existing tab integration. |
| 47.6 | Add frontend tests | 47A | | `__tests__/components/projects/time-summary-panel.test.tsx`. ~6 tests: renders total summary cards with formatted durations, renders by-task table, renders by-member table for leads, hides by-member section for contributors, empty state renders, date range inputs present. Pattern: follow existing component test files. Always include `afterEach(() => cleanup())`. |

### Key Files

**Slice 47A -- Create:**
- `frontend/app/(app)/org/[slug]/projects/[id]/time-summary-actions.ts`
- `frontend/components/projects/time-summary-panel.tsx`
- `frontend/__tests__/components/projects/time-summary-panel.test.tsx`

**Slice 47A -- Modify:**
- `frontend/lib/types.ts` -- Add TimeSummary types
- `frontend/components/projects/project-tabs.tsx` -- Add "Time" tab
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` -- Fetch and pass time summary data

### Architecture Decisions

- **Duration formatting reuse**: Uses `formatDuration` utility added in Epic 45. If Epic 47 runs before 45, this function should be created here instead.
- **By-member section conditional rendering**: Frontend gracefully handles 403 by hiding the section. No error message -- contributors simply do not see the per-member breakdown.
- **Simple date inputs**: No date picker library dependency. `<input type="date">` provides native browser date picking. Consistent with the minimal dependency approach.

---

## Epic 48: My Work — Backend

**Goal**: Implement the "My Work" backend with cross-project task aggregation and member-scoped time summaries. Three endpoints: my tasks (assigned + unassigned in my projects), my time entries, and my time summary. Self-scoped -- no ProjectAccessService needed. Per ADR-023.

**References**: [ADR-023](../adr/ADR-023-my-work-cross-project-query.md), `phase5-task-time-lifecycle.md` Section 11.2.1, 11.3.1

**Dependencies**: Epic 44 (TimeEntry entity and repository)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **48A** | 48.1--48.7 | New TaskRepository queries, MyWorkService, MyWorkController with 3 endpoints, integration tests | **Done** (PR #93) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 48.1 | Add cross-project queries to TaskRepository | 48A | | Add to `task/TaskRepository.java`: `findAssignedToMember(UUID memberId)` -- JPQL `SELECT t FROM Task t WHERE t.assigneeId = :memberId AND t.status IN ('OPEN', 'IN_PROGRESS') ORDER BY t.dueDate ASC NULLS LAST, t.createdAt DESC`. `findUnassignedInMemberProjects(UUID memberId)` -- JPQL `SELECT t FROM Task t WHERE t.assigneeId IS NULL AND t.status = 'OPEN' AND t.projectId IN (SELECT pm.projectId FROM ProjectMember pm WHERE pm.memberId = :memberId) ORDER BY t.priority DESC, t.createdAt DESC`. Both queries naturally respect tenant isolation via `@Filter` (Starter) or schema isolation (Pro). Pattern: follow existing JPQL queries in `TaskRepository`. |
| 48.2 | Add member time summary query to TimeEntryRepository | 48A | | Add to `timeentry/TimeEntryRepository.java`: `memberTimeSummary(UUID memberId, LocalDate from, LocalDate to)` -- native query returning `MemberTimeSummaryProjection` (billableMinutes, nonBillableMinutes, totalMinutes) for the member across all projects. `memberTimeSummaryByProject(UUID memberId, LocalDate from, LocalDate to)` -- native query returning `List<MyWorkProjectTimeSummaryProjection>` (projectId, projectName, billableMinutes, nonBillableMinutes, totalMinutes). Add `MyWorkProjectTimeSummaryProjection.java` interface. |
| 48.3 | Create MyWorkService | 48A | | `mywork/MyWorkService.java` -- constructor injection of `TaskRepository`, `TimeEntryRepository`, `MemberRepository`. `getMyTasks(UUID memberId, String filter, String status, UUID projectId)` -- calls `findAssignedToMember` and/or `findUnassignedInMemberProjects` based on filter param (`assigned`, `unassigned`, `all`). Optional status and projectId filtering. Returns `MyWorkTasksResponse` with assigned and unassigned lists, each enriched with project name (batch-load via `ProjectRepository.findAllById()`). `getMyTimeEntries(UUID memberId, LocalDate from, LocalDate to)` -- delegates to `timeEntryRepository.findByMemberIdAndDateBetween()`. Enriches with task title and project name. `getMyTimeSummary(UUID memberId, LocalDate from, LocalDate to)` -- returns total + by-project breakdown. All `@Transactional(readOnly = true)`. No `ProjectAccessService` call -- query structure provides authorization (ADR-023). |
| 48.4 | Create MyWorkController | 48A | | `mywork/MyWorkController.java` -- Three endpoints: `GET /api/my-work/tasks` (MEMBER+, query params: `filter`, `status`, `projectId`), `GET /api/my-work/time-entries` (MEMBER+, query params: `from`, `to` with defaults to today), `GET /api/my-work/time-summary` (MEMBER+, query params: `from` required, `to` required). DTOs: `MyWorkTasksResponse(List<MyWorkTaskItem> assigned, List<MyWorkTaskItem> unassigned)`, `MyWorkTaskItem(UUID id, UUID projectId, String projectName, String title, String status, String priority, String dueDate, long totalTimeMinutes)`, `MyWorkTimeEntryItem(UUID id, UUID taskId, String taskTitle, UUID projectId, String projectName, LocalDate date, int durationMinutes, boolean billable, String description)`, `MyWorkTimeSummaryResponse(UUID memberId, LocalDate fromDate, LocalDate toDate, long billableMinutes, long nonBillableMinutes, long totalMinutes, List<MyWorkProjectSummary> byProject)`. Uses `RequestScopes.requireMemberId()`. Pattern: follow `TaskController.java` style. |
| 48.5 | Add totalTimeMinutes to MyWorkTaskItem | 48A | | For each task in the "my tasks" response, include total logged time. Add a batch query: `timeEntryRepository.sumDurationByTaskIds(List<UUID> taskIds)` returning `Map<UUID, Long>` of taskId to total minutes. Avoids N+1 queries. Use native query with `GROUP BY task_id`. |
| 48.6 | Add My Work integration tests | 48A | | `mywork/MyWorkIntegrationTest.java`. ~12 tests: my assigned tasks returns only tasks assigned to me, my unassigned tasks returns only OPEN tasks in my projects, filter=assigned returns only assigned, filter=unassigned returns only unassigned, status filter works, projectId filter narrows results, tasks in projects I'm not a member of are excluded, my time entries returns only my entries, my time entries date range filtering, my time summary totals correct, my time summary by-project breakdown correct, empty state (no tasks/entries) returns empty lists. Seed: 2 projects (member in both), 1 project (not a member), tasks across all projects, time entries across projects. |
| 48.7 | Add cross-project isolation tests | 48A | | ~3 tests: member in tenant A sees only tenant A tasks, member in tenant B sees only tenant B tasks, Starter (shared schema) isolation verified. Pattern: follow `StarterTenantIntegrationTest.java`. |

### Key Files

**Slice 48A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mywork/MyWorkService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mywork/MyWorkController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/MyWorkProjectTimeSummaryProjection.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mywork/MyWorkIntegrationTest.java`

**Slice 48A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskRepository.java` -- Add `findAssignedToMember`, `findUnassignedInMemberProjects`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRepository.java` -- Add member summary queries, `sumDurationByTaskIds`

**Slice 48A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMember.java` -- `projectId` field name for JPQL subquery
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectRepository.java` -- batch load project names
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` -- existing task query patterns
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` -- `requireMemberId()`

### Architecture Decisions

- **Self-scoped queries (ADR-023)**: No `ProjectAccessService` call. `WHERE assignee_id = :memberId` is its own authorization. `WHERE project_id IN (SELECT pm.project_id FROM project_members pm WHERE pm.member_id = :memberId)` uses project membership as the access boundary.
- **`mywork/` package**: Separate from `task/` because "My Work" is a cross-domain concept spanning tasks, time entries, and projects.
- **Batch-load names and time totals**: Avoid N+1 by collecting all relevant IDs and batch-loading project names and time totals in single queries.
- **Required date range for summary**: `from` and `to` are required on the summary endpoint to prevent unbounded aggregation. Task listing does not require date range (shows active tasks regardless).
- **Single-slice epic**: 7 tasks touching ~4 new files and ~2 modified files. The "My Work" domain is self-contained and does not depend on complex permission logic.

---

## Epic 49: My Work — Frontend

**Goal**: Build the "My Work" dashboard page with assigned tasks, available (unassigned) tasks in the member's projects, and a weekly time summary. Add sidebar navigation link.

**References**: `phase5-task-time-lifecycle.md` Section 11.4.1, 11.7.2

**Dependencies**: Epic 48 (My Work API)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **49A** | 49.1--49.5 | Types, server actions, My Work page, AssignedTaskList, sidebar nav link | |
| **49B** | 49.6--49.10 | AvailableTaskList, TimeEntrySummary panel, weekly time chart, loading state, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 49.1 | Add My Work TypeScript types | 49A | | In `lib/types.ts`: `MyWorkTaskItem { id, projectId, projectName, title, status, priority, dueDate, totalTimeMinutes }`, `MyWorkTasksResponse { assigned: MyWorkTaskItem[], unassigned: MyWorkTaskItem[] }`, `MyWorkTimeEntryItem { id, taskId, taskTitle, projectId, projectName, date, durationMinutes, billable, description }`, `MyWorkTimeSummary { memberId, fromDate, toDate, billableMinutes, nonBillableMinutes, totalMinutes, byProject: MyWorkProjectSummary[] }`, `MyWorkProjectSummary { projectId, projectName, billableMinutes, nonBillableMinutes, totalMinutes }`. |
| 49.2 | Create My Work server actions | 49A | | `app/(app)/org/[slug]/my-work/actions.ts` -- `fetchMyTasks(filter?, status?, projectId?)` calls `GET /api/my-work/tasks`, `fetchMyTimeEntries(from?, to?)` calls `GET /api/my-work/time-entries`, `fetchMyTimeSummary(from, to)` calls `GET /api/my-work/time-summary`. Standard ActionResult pattern. Pattern: follow `task-actions.ts`. |
| 49.3 | Build My Work page | 49A | | `app/(app)/org/[slug]/my-work/page.tsx` -- server component. Page header: Instrument Serif h1 "My Work". Layout: two-column grid (tasks left, time summary right) on desktop, stacked on mobile. Fetches my tasks and weekly time summary (current week: Monday to Sunday). Passes data to client components. Pattern: follow `projects/page.tsx` for page structure. |
| 49.4 | Build AssignedTaskList component | 49A | | `components/my-work/assigned-task-list.tsx` -- client component. Header: "My Tasks" + count badge. Catalyst-style table rows: Project Name (olive badge), Title, Priority (badge), Status (badge), Due Date (overdue styling), Logged Time (formatted via `formatDuration`). Click navigates to project detail. "Claim" / "Release" / "Mark Done" action buttons. Empty state: CheckCircle icon + "No tasks assigned". Pattern: follow `task-list-panel.tsx`. |
| 49.5 | Add sidebar navigation link | 49A | | Modify `frontend/lib/nav-items.ts` -- add "My Work" entry with `ClipboardList` icon from lucide-react, href `/org/[slug]/my-work`. Position: second item (after Dashboard, before Projects). |
| 49.6 | Build AvailableTaskList component | 49B | | `components/my-work/available-task-list.tsx` -- client component. Header: "Available Tasks" + count badge. Shows unassigned OPEN tasks in the member's projects. Table rows: Project Name, Title, Priority, Due Date. "Claim" button on each row. Collapsible section (default collapsed if empty). Empty state: Inbox icon + "No available tasks". Pattern: follow `assigned-task-list.tsx`. |
| 49.7 | Build WeeklyTimeSummary component | 49B | | `components/my-work/weekly-time-summary.tsx` -- client component. Shows the right column of the My Work page. Stat cards: "This Week" total (formatted), "Billable" (green text), "Non-billable" (olive text). By-project breakdown table: Project Name, Billable, Non-billable, Total. Week navigation: "<" and ">" buttons to move week range. Current week label ("Feb 10 -- Feb 16, 2026"). Uses `fetchMyTimeSummary` server action. Pattern: follow stat card styling from dashboard. |
| 49.8 | Build TodayTimeEntries component | 49B | | `components/my-work/today-time-entries.tsx` -- client component. Shows today's time entries below the weekly summary. Header: "Today" + total formatted. List of entries: Time, Task Title (linked), Project Name, Duration, Billable badge. "Log Time" quick action (opens LogTimeDialog from Epic 45 if available). Empty state: "No time logged today". |
| 49.9 | Add loading.tsx for My Work page | 49B | | `app/(app)/org/[slug]/my-work/loading.tsx` -- skeleton with card placeholders for task lists and time summary. Pattern: follow `projects/loading.tsx`. |
| 49.10 | Add frontend tests | 49B | | `__tests__/components/my-work/assigned-task-list.test.tsx` and `__tests__/components/my-work/weekly-time-summary.test.tsx`. ~8 tests: assigned task list renders with project names, available task list renders, empty states render, time summary displays formatted totals, by-project breakdown renders, week navigation changes date range, claim button present on available tasks, priority badge colors. Pattern: follow existing component test files. Always include `afterEach(() => cleanup())`. |

### Key Files

**Slice 49A -- Create:**
- `frontend/app/(app)/org/[slug]/my-work/page.tsx`
- `frontend/app/(app)/org/[slug]/my-work/actions.ts`
- `frontend/components/my-work/assigned-task-list.tsx`

**Slice 49A -- Modify:**
- `frontend/lib/types.ts` -- Add My Work types
- `frontend/lib/nav-items.ts` -- Add "My Work" nav entry

**Slice 49B -- Create:**
- `frontend/components/my-work/available-task-list.tsx`
- `frontend/components/my-work/weekly-time-summary.tsx`
- `frontend/components/my-work/today-time-entries.tsx`
- `frontend/app/(app)/org/[slug]/my-work/loading.tsx`
- `frontend/__tests__/components/my-work/assigned-task-list.test.tsx`
- `frontend/__tests__/components/my-work/weekly-time-summary.test.tsx`

### Architecture Decisions

- **Two-column layout**: Tasks on the left (primary action zone), time summary on the right (reference data). Matches common project management tool layouts.
- **Week-based time view**: Default to current week (Monday--Sunday). Users review time weekly for billing/capacity. Week navigation allows historical review.
- **Cross-project context**: Each task/entry shows project name as a badge. This is the primary differentiator from project-scoped task views.
- **Sidebar position**: "My Work" placed after "Dashboard" and before "Projects" in the sidebar. It is the most frequently accessed page for individual contributors.
- **Two-slice decomposition**: 49A (page + primary task list + nav) and 49B (secondary lists + time summary + tests). Keeps each slice at 5--6 files.
