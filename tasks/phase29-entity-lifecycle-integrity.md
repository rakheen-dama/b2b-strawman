# Phase 29 — Entity Lifecycle & Relationship Integrity

Phase 29 adds **lifecycle state machines and relationship integrity rules** to Project and Task -- the two core domain entities that currently lack formal lifecycle management. Projects today have no status field; tasks store status as a raw string with no terminal states and no transition validation. This phase promotes both to first-class lifecycle-managed entities with validated transitions, completion timestamps, delete protection, and cross-entity guardrails. It also adds an optional Project-to-Customer link and a project due date.

This is a **structural hardening phase**, not a feature phase. No new pages are added. Instead, the phase retrofits guardrails onto existing entities: status enums with transition validation, completion/archive workflows, delete protection, and UI integration of lifecycle actions into existing pages.

**Architecture doc**: `architecture/phase29-entity-lifecycle-integrity.md`

**ADRs**:
- [ADR-110](../adr/ADR-110-task-status-representation.md) -- Task Status Representation (Java enum + CHECK constraint)
- [ADR-111](../adr/ADR-111-project-completion-semantics.md) -- Project Completion Semantics (three-state: ACTIVE -> COMPLETED -> ARCHIVED)
- [ADR-112](../adr/ADR-112-delete-vs-archive-philosophy.md) -- Delete vs Archive Philosophy (restrict delete, promote archive)
- [ADR-113](../adr/ADR-113-customer-link-optionality.md) -- Customer Link Optionality (nullable UUID, app-validated)

**Migrations**: V47 tenant (task lifecycle columns + constraints, project lifecycle columns + customer_id + due_date).

**Dependencies on prior phases**: Phase 4 (Task entity, Project entity, Customer entity), Phase 5 (TimeEntry linked to tasks/projects), Phase 8 (OrgSettings, ProjectBudget), Phase 10 (Invoice linked to projects/customers), Phase 6 (Audit), Phase 6.5 (Notifications, domain events).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 201 | Task Lifecycle Foundation -- Migration, Enums & Entity | Backend | -- | M | 201A, 201B | **Done** (PRs #411, #412) |
| 202 | Task Lifecycle Service + Transition Endpoints | Backend | 201 | L | 202A, 202B | **Done** (PRs #413, #415) |
| 203 | Project Lifecycle Foundation -- Migration, Enums & Entity | Backend | -- | M | 203A, 203B | |
| 204 | Project Lifecycle Service + Transition Endpoints | Backend | 203, 201 | L | 204A, 204B | |
| 205 | Project-Customer Link + Due Date | Backend | 203 | M | 205A, 205B | |
| 206 | Delete Protection & Cross-Entity Guards | Backend | 201, 203, 205 | M | 206A, 206B | |
| 207 | Task Lifecycle Frontend | Frontend | 202 | M | 207A, 207B | |
| 208 | Project Lifecycle Frontend | Frontend | 204, 205 | L | 208A, 208B | |

---

## Dependency Graph

```
[E201A Task Migration V47a           [E203A Project Migration V47b
 + TaskStatus + TaskPriority enums]   + ProjectStatus enum]
        |                                     |
[E201B Task entity refactor           [E203B Project entity refactor
 + transition methods + repo          + transition methods + repo
 query updates + entity tests]        query updates + entity tests]
        |                                     |
        |                             +-------+-------+
        |                             |               |
[E202A TaskService lifecycle   [E204A ProjectService  [E205A Project-Customer
 methods (complete/cancel/      lifecycle methods      link + due date
 reopen) + audit/events]        (complete/archive/     service methods]
        |                        reopen) + audit]            |
[E202B TaskController           [E204B ProjectController  [E205B Customer lifecycle
 transition endpoints            transition endpoints +    guard extension +
 + integration tests]            integration tests]        customer-project endpoint]
        |                             |               |
        +------------+----------------+---------------+
                     |
              [E206A Delete protection:
               ProjectService + TaskService
               child-count guards]
                     |
              [E206B Cross-entity integrity:
               archive guards, customer
               delete protection + tests]
                     |
        +------------+------------+
        |                         |
[E207A Task lifecycle      [E208A Project lifecycle
 frontend: status filter    frontend: status filter,
 chips, action buttons,     due date, action buttons,
 visual styling]            completion dialog]
        |                         |
[E207B Task detail sheet   [E208B Project customer
 lifecycle actions +        link UI + archived
 My Work filter update]     read-only mode]
```

**Parallel opportunities**:
- Epics 201 and 203 are fully independent -- can start in parallel (separate migration files V47a and V47b, or combined as V47).
- After 201B: Epic 202 can start.
- After 203B: Epics 204 and 205 can start in parallel.
- Epic 206 requires 201B, 203B, and 205B.
- After 202B: Epic 207 can start.
- After 204B + 205B: Epic 208 can start.
- Epics 207 and 208 are fully independent (task frontend vs. project frontend) -- can run in parallel.

---

## Implementation Order

### Stage 0: Database Migration + Enums (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a (parallel) | 201 | 201A | V47 tenant migration part 1: task lifecycle columns (`completed_at`, `completed_by`, `cancelled_at`) + CHECK constraints on `status` and `priority` columns + `TaskStatus` enum + `TaskPriority` enum. ~4 new files. Backend only. | **Done** (PR #411) |
| 0b (parallel) | 203 | 203A | V47 tenant migration part 2: project lifecycle columns (`status`, `customer_id`, `due_date`, `completed_at`, `completed_by`, `archived_at`) + CHECK constraint on `status` + `ProjectStatus` enum. ~3 new files. Backend only. | **Done** (PR #416) |

### Stage 1: Entity Refactoring (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 201 | 201B | Refactor `Task` entity: change `status`/`priority` from String to enum, add lifecycle fields + transition methods (`complete()`, `cancel()`, `reopen()`) + update constructor/update method + refactor `TaskRepository` queries from String to enum + entity unit tests. ~2 modified files, ~1 new test file. Backend only. | **Done** (PR #412) |
| 1b (parallel) | 203 | 203B | Refactor `Project` entity: add `status`, `customerId`, `dueDate`, lifecycle fields + transition methods (`complete()`, `archive()`, `reopen()`) + update constructor/update method + add `ProjectRepository` query methods + entity unit tests. ~2 modified files, ~1 new test file. Backend only. | |

### Stage 2: Service Layer (parallel tracks, with one dependency)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 202 | 202A | `TaskService` lifecycle methods (`completeTask`, `cancelTask`, `reopenTask`) + transition validation + audit events + domain events + notification for cancelled-by-other. ~1 modified file, ~2 new event files. Backend only. | **Done** (PR #413) |
| 2b (parallel) | 204 | 204A | `ProjectService` lifecycle methods (`completeProject`, `archiveProject`, `reopenProject`) + completion guardrails (task check, unbilled time check) + audit events + domain events + notification to project members. ~1 modified file, ~2 new event files. Backend only. | |
| 2c (parallel) | 205 | 205A | `ProjectService` extension: `customerId` and `dueDate` in create/update + customer validation (exists + not OFFBOARDED) + `ProjectRepository` filter-by-status and filter-by-customer queries + `GET /api/customers/{id}/projects` convenience query. ~2 modified files. Backend only. | |

### Stage 3: Controllers + Integration Tests (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a (parallel) | 202 | 202B | Task lifecycle controller endpoints (`PATCH .../complete`, `.../cancel`, `.../reopen`) + `TaskController` response DTO update (add `completedAt`, `completedBy`, `cancelledAt`) + update `listTasks` status filter to support comma-separated enum values + integration tests (~12 tests). ~1 modified file, ~1 new test file. Backend only. | **Done** (PR #415) |
| 3b (parallel) | 204 | 204B | Project lifecycle controller endpoints (`PATCH .../complete`, `.../archive`, `.../reopen`) + `ProjectController` response DTO update (add `status`, `customerId`, `dueDate`, `completedAt`, `completedBy`, `archivedAt`) + update `listProjects` status filter + integration tests (~14 tests). ~1 modified file, ~1 new test file. Backend only. | |
| 3c (parallel) | 205 | 205B | `CustomerLifecycleGuard` extension: add `CREATE_PROJECT` action blocking for OFFBOARDING/OFFBOARDED customers + `ProjectCustomerController` update for customer-project list + customer delete protection check (count linked projects) + integration tests (~8 tests). ~2 modified files, ~1 new test file. Backend only. | |

### Stage 4: Delete Protection

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a | 206 | 206A | `ProjectService.deleteProject()` refactor: add child-count guards (tasks, time entries, invoices, documents) + reject delete for COMPLETED/ARCHIVED + `TaskService.deleteTask()` guard: block if time entries exist + 409 error responses with guidance messages. ~2 modified files. Backend only. | |
| 4b | 206 | 206B | `CustomerService.deleteCustomer()` guard: block if linked projects/invoices/retainers exist + archive-guard for projects (no new tasks/time on archived projects via `ProjectLifecycleGuard`) + comprehensive integration tests (~10 tests). ~2 modified files, ~1 new file, ~1 new test file. Backend only. | |

### Stage 5: Frontend (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a (parallel) | 207 | 207A | Task list: status filter chips (All, Open, In Progress, Done, Cancelled) + default filter hiding Done/Cancelled + visual distinction (strikethrough/muted for Done, muted+badge for Cancelled) + task-actions.ts lifecycle server actions + frontend tests. ~3 modified files, ~1 new file. Frontend only. | |
| 5b (parallel) | 208 | 208A | Project list: status filter (Active, Completed, Archived, All) + status badge + due date column with overdue warning + project creation/edit dialog: due date picker + customer dropdown + project-actions.ts lifecycle server actions + frontend tests. ~4 modified files, ~1 new file. Frontend only. | |

### Stage 6: Frontend Detail Pages (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 6a (parallel) | 207 | 207B | Task detail sheet: "Mark Done" button (primary when IN_PROGRESS), "Cancel" in overflow, "Reopen" for Done/Cancelled + `completedAt`/`completedBy` display + My Work page filter update (ensure DONE/CANCELLED excluded by default) + frontend tests. ~3 modified files. Frontend only. | |
| 6b (parallel) | 208 | 208B | Project detail header: status badge + due date + contextual action buttons (Complete/Archive/Reopen per state) + "Complete Project" dialog with incomplete-task error and unbilled-time confirmation + archived project read-only banner + customer display in sidebar (link to customer detail) + customer "Projects" tab on customer detail page + frontend tests. ~4 modified files, ~1 new file. Frontend only. | |

### Timeline

```
Stage 0: [201A] // [203A]                                       (parallel)
Stage 1: [201B] // [203B]                                       (parallel, after respective 0x)
Stage 2: [202A] // [204A] // [205A]                              (parallel, after respective 1x)
Stage 3: [202B] // [204B] // [205B]                              (parallel, after respective 2x)
Stage 4: [206A] --> [206B]                                       (sequential, after 202B+204B+205B)
Stage 5: [207A] // [208A]                                        (parallel, after 202B / 204B+205B)
Stage 6: [207B] // [208B]                                        (parallel, after 207A / 208A)
```

**Critical path**: 203A -> 203B -> 204A -> 204B -> 206A -> 206B

**Note on migration strategy**: Slices 201A and 203A can share a single V47 migration file or use separate files (V47a/V47b). The recommended approach is a **single V47 migration** that handles both task and project schema changes, since they are deployed together and there is no sequencing dependency between them. The builder for 201A should create the V47 file; the builder for 203A should append to it. Alternatively, if run in parallel, one creates V47 and the other creates V48 -- the orchestrator should decide based on execution order.

---

## Epic 201: Task Lifecycle Foundation -- Migration, Enums & Entity

**Goal**: Create the `TaskStatus` and `TaskPriority` enums with validated transitions, add lifecycle columns to the `tasks` table via migration, and refactor the `Task` entity from raw strings to enums with lifecycle methods.

**References**: Architecture doc Section 1 (Task Lifecycle Completion), ADR-110 (Task Status Representation).

**Dependencies**: None -- this is a foundation epic.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **201A** | 201.1--201.4 | V47 tenant migration: add `completed_at`, `completed_by`, `cancelled_at` columns to `tasks` table + CHECK constraints on `status` and `priority` columns + `TaskStatus` enum with `allowedTransitions()` method + `TaskPriority` enum. ~4 new files, ~1 modified migration file. Backend only. | **Done** (PR #411) |
| **201B** | 201.5--201.10 | Refactor `Task` entity: change `status` from `String` to `TaskStatus`, `priority` from `String` to `TaskPriority`, add lifecycle fields (`completedAt`, `completedBy`, `cancelledAt`) + lifecycle transition methods (`complete()`, `cancel()`, `reopen()`) + update constructor and `update()` method + refactor `TaskRepository` queries from string literals to enum parameters + entity unit tests. ~2 modified files, ~1 new test file. Backend only. | **Done** (PR #412) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 201.1 | Create V47 tenant migration (task columns) | 201A | | New file: `db/migration/tenant/V47__entity_lifecycle_integrity.sql`. (1) `ALTER TABLE tasks ADD COLUMN completed_at TIMESTAMPTZ;` (2) `ALTER TABLE tasks ADD COLUMN completed_by UUID;` (3) `ALTER TABLE tasks ADD COLUMN cancelled_at TIMESTAMPTZ;` (4) `ALTER TABLE tasks ADD CONSTRAINT tasks_status_check CHECK (status IN ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED'));` (5) `ALTER TABLE tasks ADD CONSTRAINT tasks_priority_check CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'));` (6) Project columns in same file (see 203.1). Existing data is backward compatible -- current OPEN/IN_PROGRESS/LOW/MEDIUM/HIGH values match the CHECK values. |
| 201.2 | Create `TaskStatus` enum | 201A | | New file: `task/TaskStatus.java`. Enum values: `OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED`. Include `allowedTransitions()` method returning `Set<TaskStatus>` per the state machine: OPEN->{IN_PROGRESS, CANCELLED}, IN_PROGRESS->{DONE, OPEN, CANCELLED}, DONE->{OPEN}, CANCELLED->{OPEN}. Include `canTransitionTo(TaskStatus target)` method. Include `isTerminal()` returning true for DONE/CANCELLED. Pattern: `customer/LifecycleStatus.java`. |
| 201.3 | Create `TaskPriority` enum | 201A | | New file: `task/TaskPriority.java`. Enum values: `LOW`, `MEDIUM`, `HIGH`, `URGENT`. No transition validation needed -- priority can change freely. Simple enum, no additional methods beyond standard `valueOf()`. Pattern: `customer/CustomerType.java`. |
| 201.4 | Verify migration compatibility | 201A | 201.1 | Covered by integration test bootstrap. Verify: existing tasks with `status='OPEN'` or `status='IN_PROGRESS'` pass the CHECK constraint. Verify new columns default to NULL. Note: `IN_REVIEW` status appears in some `TaskRepository` queries -- this is NOT a valid status in the current data. Check if any tasks have `IN_REVIEW` status. If so, the migration needs a backfill step. **Decision**: based on `getTaskSummaryByProjectId` query referencing `IN_REVIEW`, this status does NOT exist in the table data (it was a UI-only concept in the dashboard). The CHECK constraint excluding it is safe. |
| 201.5 | Refactor `Task` entity: status and priority to enums | 201B | 201.2, 201.3 | Modify `task/Task.java`: change `private String status` to `private TaskStatus status` with `@Enumerated(EnumType.STRING)`. Change `private String priority` to `private TaskPriority priority` with `@Enumerated(EnumType.STRING)`. Add fields: `private Instant completedAt`, `private UUID completedBy`, `private Instant cancelledAt`. Update constructor to use `TaskStatus.OPEN` and `TaskPriority.MEDIUM` (or `TaskPriority.valueOf(priority)` if non-null). Update `getStatus()` return type to `TaskStatus` (or keep returning String via `.name()` to minimize downstream breakage -- **recommend returning enum** and updating callers). Update `getPriority()` similarly. |
| 201.6 | Add lifecycle transition methods to `Task` entity | 201B | 201.5 | In `task/Task.java`: add `complete(UUID memberId)` -- validates status is IN_PROGRESS via `TaskStatus.canTransitionTo(DONE)`, sets `status = DONE`, `completedAt = Instant.now()`, `completedBy = memberId`, `updatedAt = Instant.now()`. Add `cancel()` -- validates OPEN or IN_PROGRESS, sets `status = CANCELLED`, `cancelledAt = Instant.now()`, `updatedAt`. Add `reopen()` -- validates DONE or CANCELLED, clears `completedAt`/`completedBy`/`cancelledAt`, sets `status = OPEN`, clears `assigneeId`, `updatedAt`. Update existing `claim()` to validate `TaskStatus.OPEN.canTransitionTo(IN_PROGRESS)`. Update existing `release()` to validate `TaskStatus.IN_PROGRESS.canTransitionTo(OPEN)`. |
| 201.7 | Update `Task.update()` method for enum types | 201B | 201.5 | In `task/Task.java`: change `update()` signature to accept `TaskStatus status, TaskPriority priority` instead of Strings. The `update()` method is called from `TaskService.updateTask()` which currently passes raw strings -- the service must convert. **Alternative**: keep `update()` accepting enums and update the service to parse. Validate that the new status is a valid transition from current status using `this.status.canTransitionTo(newStatus)`. Throw `InvalidStateException` if not. |
| 201.8 | Refactor `TaskRepository` queries | 201B | 201.5 | Modify `task/TaskRepository.java`: change all `@Param("status") String status` to `@Param("status") TaskStatus status`. Update JPQL string literals: `t.status = 'OPEN'` becomes `t.status = io.b2mash.b2b.b2bstrawman.task.TaskStatus.OPEN` or use enum parameter binding. For `findAssignedToMember`: change `AND t.status IN ('OPEN', 'IN_PROGRESS')` to use `@Param("statuses") List<TaskStatus> statuses` or keep as named enum references. For `getTaskSummaryByProjectId`: update CASE expressions. Remove `IN_REVIEW` references (not a valid status). For `findByProjectIdWithFilters`: change status param to `TaskStatus`. For `countByProjectIdAndStatus`: change to `TaskStatus`. |
| 201.9 | Write `TaskStatus` unit tests | 201B | 201.2 | New file: `task/TaskStatusTest.java`. Tests: (1) open_can_transition_to_in_progress, (2) open_can_transition_to_cancelled, (3) in_progress_can_transition_to_done, (4) in_progress_can_transition_to_open (release), (5) in_progress_can_transition_to_cancelled, (6) done_can_transition_to_open (reopen), (7) cancelled_can_transition_to_open (reopen), (8) open_cannot_transition_to_done, (9) done_cannot_transition_to_in_progress, (10) done_isTerminal, (11) cancelled_isTerminal. ~11 tests. |
| 201.10 | Write `Task` entity lifecycle tests | 201B | 201.6 | New file: `task/TaskLifecycleTest.java`. Tests: (1) complete_sets_status_and_timestamps, (2) complete_rejects_from_open, (3) cancel_from_open_succeeds, (4) cancel_from_in_progress_succeeds, (5) cancel_rejects_from_done, (6) reopen_from_done_clears_fields, (7) reopen_from_cancelled_clears_fields, (8) reopen_clears_assignee, (9) claim_validates_open_status, (10) release_validates_in_progress_status. ~10 tests. Pattern: `acceptance/AcceptanceRequestTest.java` entity test style. |

### Key Files

**Slice 201A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V47__entity_lifecycle_integrity.sql`
- `backend/src/main/java/.../task/TaskStatus.java`
- `backend/src/main/java/.../task/TaskPriority.java`

**Slice 201B -- Modify:**
- `backend/src/main/java/.../task/Task.java`
- `backend/src/main/java/.../task/TaskRepository.java`

**Slice 201B -- Create:**
- `backend/src/test/java/.../task/TaskStatusTest.java`
- `backend/src/test/java/.../task/TaskLifecycleTest.java`

**Slice 201B -- Read for context:**
- `backend/src/main/java/.../customer/LifecycleStatus.java` -- enum with `allowedTransitions()` pattern
- `backend/src/main/java/.../invoice/InvoiceStatus.java` -- simple enum pattern
- `backend/src/main/java/.../task/TaskService.java` -- to understand callers of `Task.getStatus()` as String
- `backend/src/main/java/.../task/TaskController.java` -- DTO response mapping

### Architecture Decisions

- **Single V47 migration for both task and project changes**: Both are part of Phase 29 and deploy together. Avoids coordination overhead between parallel builders.
- **Enum return types from entity**: `getStatus()` returns `TaskStatus` (not String). This is a breaking change for callers but enforces type safety. Service and controller layers must adapt.
- **`IN_REVIEW` status removed**: The `getTaskSummaryByProjectId` query referenced `IN_REVIEW` but no tasks use this status. The CHECK constraint excludes it. The CASE expression in the summary query should be updated to count CANCELLED instead.
- **`update()` method validates transitions**: The general-purpose `update()` on Task now validates status transitions, preventing the controller/service from bypassing the state machine via raw updates.

---

## Epic 202: Task Lifecycle Service + Transition Endpoints

**Goal**: Add dedicated lifecycle action methods to `TaskService` (complete, cancel, reopen) with audit events, domain events, and notifications. Add PATCH endpoints to `TaskController` for each action.

**References**: Architecture doc Section 1 (API Changes, Audit & Notifications).

**Dependencies**: Epic 201 (entity + enums).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **202A** | 202.1--202.7 | `TaskService` lifecycle methods: `completeTask()` (validates IN_PROGRESS, sets completedAt/completedBy), `cancelTask()` (validates OPEN/IN_PROGRESS, permission check: assignee or ADMIN+), `reopenTask()` (validates DONE/CANCELLED, clears fields) + 3 domain event records (`TaskCompletedEvent`, `TaskCancelledEvent`, `TaskReopenedEvent`) + audit events + notification on cancel-by-other. ~1 modified file, ~3 new event files. Backend only. | **Done** (PR #413) |
| **202B** | 202.8--202.13 | `TaskController` transition endpoints: `PATCH /api/projects/{projectId}/tasks/{taskId}/complete`, `.../cancel`, `.../reopen` + update `TaskResponse` DTO (add `completedAt`, `completedBy`, `cancelledAt` fields) + update `listTasks` to support comma-separated status filter (default: `OPEN,IN_PROGRESS`) + controller integration tests (~12 tests). ~1 modified file, ~1 new test file. Backend only. | **Done** (PR #415) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 202.1 | Add `completeTask()` to TaskService | 202A | 201B | In `task/TaskService.java`: new method `completeTask(UUID taskId, UUID memberId, String orgRole)`. Load task, require view access, validate: only assignee or ADMIN+ can complete (throw `ForbiddenException` otherwise), call `task.complete(memberId)`, save, log audit event `task.completed` with details (title, project_id, completed_by), publish `TaskCompletedEvent`, publish portal event if customer-linked. Return task. |
| 202.2 | Add `cancelTask()` to TaskService | 202A | 201B | In `task/TaskService.java`: new method `cancelTask(UUID taskId, UUID memberId, String orgRole)`. Load task, require view access, validate: ADMIN+ can cancel (throw `ForbiddenException` otherwise), call `task.cancel()`, save, log audit event `task.cancelled`, publish `TaskCancelledEvent`. If task has assignee and canceller is NOT the assignee, publish notification event to assignee ("Your task X was cancelled by Y"). Return task. |
| 202.3 | Add `reopenTask()` to TaskService | 202A | 201B | In `task/TaskService.java`: new method `reopenTask(UUID taskId, UUID memberId, String orgRole)`. Load task, require view access, validate: ADMIN+ or original assignee can reopen, call `task.reopen()`, save, log audit event `task.reopened`, publish `TaskReopenedEvent`, portal event if linked. Return task. |
| 202.4 | Create `TaskCompletedEvent` | 202A | | New file: `event/TaskCompletedEvent.java`. Record implementing domain event interface: `UUID taskId, UUID projectId, UUID completedBy, String taskTitle, String tenantId, String orgId, Instant occurredAt`. Pattern: `event/TaskStatusChangedEvent.java`. |
| 202.5 | Create `TaskCancelledEvent` | 202A | | New file: `event/TaskCancelledEvent.java`. Record: `UUID taskId, UUID projectId, UUID cancelledBy, UUID assigneeId, String taskTitle, String tenantId, String orgId, Instant occurredAt`. Includes assigneeId for notification routing. |
| 202.6 | Create `TaskReopenedEvent` | 202A | | New file: `event/TaskReopenedEvent.java`. Record: `UUID taskId, UUID projectId, UUID reopenedBy, String taskTitle, String previousStatus, String tenantId, String orgId, Instant occurredAt`. |
| 202.7 | Wire notification for cancel-by-other | 202A | 202.2 | Modify `notification/NotificationEventHandler.java`: add handler for `TaskCancelledEvent`. If `event.assigneeId() != null && !event.assigneeId().equals(event.cancelledBy())`, create in-app notification for the assignee: "Your task '{taskTitle}' was cancelled". Pattern: existing `TaskAssignedEvent` handler. |
| 202.8 | Add `PATCH /complete` endpoint | 202B | 202.1 | In `task/TaskController.java`: add `@PatchMapping("/api/projects/{projectId}/tasks/{taskId}/complete")` method. Delegates to `taskService.completeTask()`. Returns updated `TaskResponse`. `@PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")` (fine-grained check in service). |
| 202.9 | Add `PATCH /cancel` and `/reopen` endpoints | 202B | 202.2, 202.3 | In `task/TaskController.java`: add `@PatchMapping("/api/projects/{projectId}/tasks/{taskId}/cancel")` and `@PatchMapping("/api/projects/{projectId}/tasks/{taskId}/reopen")`. Same pattern as complete endpoint. |
| 202.10 | Update `TaskResponse` DTO | 202B | 201.5 | In `task/TaskController.java`: add `Instant completedAt`, `UUID completedBy`, `String completedByName`, `Instant cancelledAt` to `TaskResponse` record. Update `TaskResponse.from()` to map these from the entity. Resolve `completedByName` from the member name map. |
| 202.11 | Update `listTasks` status filter | 202B | 201.8 | In `task/TaskController.java` and `task/TaskService.java`: change `status` query parameter to accept comma-separated list of `TaskStatus` values (e.g., `?status=OPEN,IN_PROGRESS`). Default when not specified: `OPEN,IN_PROGRESS` (preserves current behavior -- hides DONE/CANCELLED). Update `TaskRepository.findByProjectIdWithFilters` to accept `List<TaskStatus>` and use `t.status IN :statuses`. |
| 202.12 | Update existing claim/release for enum transition | 202B | 201.7 | In `task/TaskService.java`: update `claimTask()` -- the entity's `claim()` now validates status internally. Remove the explicit `if (!"OPEN".equals(task.getStatus()))` check and let the entity method throw. Update `releaseTask()` similarly. Catch entity-thrown `IllegalStateException` and convert to `InvalidStateException`. |
| 202.13 | Write controller integration tests | 202B | 202.8, 202.9 | New file: `task/TaskLifecycleIntegrationTest.java`. Tests: (1) complete_task_from_in_progress, (2) complete_task_rejects_from_open, (3) complete_task_only_assignee_or_admin, (4) cancel_task_from_open, (5) cancel_task_from_in_progress, (6) cancel_task_rejects_from_done, (7) cancel_task_requires_admin, (8) reopen_task_from_done, (9) reopen_task_from_cancelled, (10) reopen_clears_completion_fields, (11) list_tasks_default_hides_done_cancelled, (12) list_tasks_explicit_status_filter. ~12 tests. Pattern: existing `TaskController` test patterns (MockMvc + JWT mocking). |

### Key Files

**Slice 202A -- Modify:**
- `backend/src/main/java/.../task/TaskService.java`
- `backend/src/main/java/.../notification/NotificationEventHandler.java`

**Slice 202A -- Create:**
- `backend/src/main/java/.../event/TaskCompletedEvent.java`
- `backend/src/main/java/.../event/TaskCancelledEvent.java`
- `backend/src/main/java/.../event/TaskReopenedEvent.java`

**Slice 202B -- Modify:**
- `backend/src/main/java/.../task/TaskController.java`
- `backend/src/main/java/.../task/TaskRepository.java`
- `backend/src/main/java/.../task/TaskService.java`

**Slice 202B -- Create:**
- `backend/src/test/java/.../task/TaskLifecycleIntegrationTest.java`

**Slice 202B -- Read for context:**
- `backend/src/main/java/.../task/TaskController.java` -- existing endpoints, DTO patterns
- `backend/src/main/java/.../acceptance/AcceptanceController.java` -- PATCH endpoint pattern

### Architecture Decisions

- **Dedicated lifecycle endpoints (PATCH) vs. general update**: Lifecycle transitions get their own endpoints (`/complete`, `/cancel`, `/reopen`) rather than going through the general `PUT /api/tasks/{id}`. This separates state machine actions from data updates, follows REST conventions (PATCH for partial state change), and makes permission rules explicit per action.
- **Notification on cancel-by-other only**: No notification when the assignee cancels their own task (they already know). Only when another member cancels it.
- **Default status filter**: `listTasks` defaults to `OPEN,IN_PROGRESS` when no status parameter is provided, preserving backward compatibility. Clients that want all tasks pass `?status=OPEN,IN_PROGRESS,DONE,CANCELLED`.

---

## Epic 203: Project Lifecycle Foundation -- Migration, Enums & Entity

**Goal**: Create the `ProjectStatus` enum with validated transitions, add lifecycle columns (status, customer_id, due_date, completedAt, completedBy, archivedAt) to the `projects` table, and refactor the `Project` entity with lifecycle methods.

**References**: Architecture doc Section 2 (Project Lifecycle), Section 3 (Project-Customer Link). ADR-111, ADR-113.

**Dependencies**: None -- this is a foundation epic (can run in parallel with Epic 201).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **203A** | 203.1--203.3 | V47 tenant migration (project columns): add `status`, `customer_id`, `due_date`, `completed_at`, `completed_by`, `archived_at` to `projects` table + CHECK constraint on `status` + `ProjectStatus` enum with `allowedTransitions()`. ~2 new files, ~1 modified migration file. Backend only. | **Done** (PR #416) |
| **203B** | 203.4--203.8 | Refactor `Project` entity: add new fields + lifecycle transition methods (`complete()`, `archive()`, `reopen()`) + update constructor and `update()` + add `ProjectRepository` query methods for status/customer filtering + entity unit tests. ~2 modified files, ~1 new test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 203.1 | Add project columns to V47 migration | 203A | | Append to `db/migration/tenant/V47__entity_lifecycle_integrity.sql` (or create separate V48 if 201A runs first): (1) `ALTER TABLE projects ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';` (2) `ALTER TABLE projects ADD COLUMN customer_id UUID;` (3) `ALTER TABLE projects ADD COLUMN due_date DATE;` (4) `ALTER TABLE projects ADD COLUMN completed_at TIMESTAMPTZ;` (5) `ALTER TABLE projects ADD COLUMN completed_by UUID;` (6) `ALTER TABLE projects ADD COLUMN archived_at TIMESTAMPTZ;` (7) `ALTER TABLE projects ADD CONSTRAINT projects_status_check CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED'));` (8) `CREATE INDEX idx_projects_status ON projects (status);` (9) `CREATE INDEX idx_projects_customer_id ON projects (customer_id);` (10) `CREATE INDEX idx_projects_due_date ON projects (due_date) WHERE status = 'ACTIVE';` |
| 203.2 | Create `ProjectStatus` enum | 203A | | New file: `project/ProjectStatus.java`. Enum values: `ACTIVE`, `COMPLETED`, `ARCHIVED`. Include `allowedTransitions()`: ACTIVE->{COMPLETED, ARCHIVED}, COMPLETED->{ARCHIVED, ACTIVE}, ARCHIVED->{ACTIVE}. Include `canTransitionTo()`, `isTerminal()` (returns true for ARCHIVED). Pattern: `customer/LifecycleStatus.java`. |
| 203.3 | Verify migration backward compatibility | 203A | 203.1 | Existing projects get `status = 'ACTIVE'` via DEFAULT. `customer_id` and `due_date` default to NULL. No data backfill needed. Verified by integration test bootstrap. |
| 203.4 | Refactor `Project` entity | 203B | 203.2 | Modify `project/Project.java`: add `@Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) private ProjectStatus status;`. Add `@Column(name = "customer_id") private UUID customerId;`. Add `@Column(name = "due_date") private LocalDate dueDate;`. Add `private Instant completedAt;`, `private UUID completedBy;`, `private Instant archivedAt;`. Update constructor: set `this.status = ProjectStatus.ACTIVE`. Add getters for all new fields. Update `update()` to accept optional `customerId`, `dueDate` params (or create separate setter methods). |
| 203.5 | Add lifecycle transition methods to `Project` entity | 203B | 203.4 | In `project/Project.java`: add `complete(UUID memberId)` -- validates `status == ACTIVE` via `canTransitionTo(COMPLETED)`, sets `status = COMPLETED`, `completedAt = Instant.now()`, `completedBy = memberId`, `updatedAt`. Add `archive()` -- validates ACTIVE or COMPLETED via `canTransitionTo(ARCHIVED)`, sets `status = ARCHIVED`, `archivedAt = Instant.now()`, `updatedAt`. Add `reopen()` -- validates COMPLETED or ARCHIVED, if was COMPLETED: clears completedAt/completedBy; if was ARCHIVED: clears archivedAt (and completedAt/completedBy if was archived directly). Sets `status = ACTIVE`, `updatedAt`. Add `isReadOnly()` returning `status == ARCHIVED`. |
| 203.6 | Add `ProjectRepository` query methods | 203B | 203.4 | Modify `project/ProjectRepository.java`: add `findAllProjectsWithRoleAndStatus(UUID memberId, List<ProjectStatus> statuses)` -- extends existing `findAllProjectsWithRole` with status filter. Add `findByCustomerId(UUID customerId)` returning `List<Project>`. Add `countByCustomerId(UUID customerId)` returning `long`. Add `findByStatusAndDueDateBefore(ProjectStatus status, LocalDate date)` for overdue queries. Update existing queries as needed to use `ProjectStatus` enum. |
| 203.7 | Write `ProjectStatus` unit tests | 203B | 203.2 | New file: `project/ProjectStatusTest.java`. Tests: (1) active_can_transition_to_completed, (2) active_can_transition_to_archived, (3) completed_can_transition_to_archived, (4) completed_can_transition_to_active (reopen), (5) archived_can_transition_to_active (restore), (6) archived_cannot_transition_to_completed, (7) active_cannot_transition_to_active, (8) archived_isTerminal. ~8 tests. |
| 203.8 | Write `Project` entity lifecycle tests | 203B | 203.5 | New file: `project/ProjectLifecycleTest.java`. Tests: (1) complete_sets_status_and_timestamps, (2) complete_rejects_from_archived, (3) archive_from_active_sets_archivedAt, (4) archive_from_completed_sets_archivedAt, (5) reopen_from_completed_clears_completion_fields, (6) reopen_from_archived_clears_archivedAt, (7) isReadOnly_true_when_archived, (8) isReadOnly_false_when_active, (9) constructor_defaults_to_active. ~9 tests. |

### Key Files

**Slice 203A -- Create/Modify:**
- `backend/src/main/resources/db/migration/tenant/V47__entity_lifecycle_integrity.sql` (append project columns)
- `backend/src/main/java/.../project/ProjectStatus.java`

**Slice 203B -- Modify:**
- `backend/src/main/java/.../project/Project.java`
- `backend/src/main/java/.../project/ProjectRepository.java`

**Slice 203B -- Create:**
- `backend/src/test/java/.../project/ProjectStatusTest.java`
- `backend/src/test/java/.../project/ProjectLifecycleTest.java`

**Slice 203B -- Read for context:**
- `backend/src/main/java/.../customer/LifecycleStatus.java` -- enum transition pattern
- `backend/src/main/java/.../project/ProjectService.java` -- to understand callers

### Architecture Decisions

- **`status` column NOT NULL with DEFAULT**: All existing projects automatically become ACTIVE. No backfill script needed.
- **`customer_id` nullable, no FK constraint**: Follows ADR-113. Application-level validation only. Internal projects (no customer) remain valid.
- **Partial index on `due_date`**: Only for ACTIVE projects -- overdue queries only make sense for active projects. Completed/archived projects with past due dates are expected.

---

## Epic 204: Project Lifecycle Service + Transition Endpoints

**Goal**: Add lifecycle action methods to `ProjectService` (complete with guardrails, archive, reopen) with audit events, domain events, and notifications. Add PATCH endpoints to `ProjectController`.

**References**: Architecture doc Section 2 (Completion Guardrails, Archive Rules, API Changes, Audit & Notifications).

**Dependencies**: Epic 203 (entity + enum), Epic 201 (task status enum -- needed for completion guard checking tasks are DONE/CANCELLED).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **204A** | 204.1--204.7 | `ProjectService` lifecycle methods: `completeProject()` (guardrails: all tasks DONE/CANCELLED + unbilled time acknowledgment), `archiveProject()`, `reopenProject()` + 3 domain event records + audit events + notification to project members. ~1 modified file, ~3 new event files. Backend only. | |
| **204B** | 204.8--204.14 | `ProjectController` transition endpoints: `PATCH .../complete` (body: `{ acknowledgeUnbilledTime: boolean }`), `.../archive`, `.../reopen` + update `ProjectController` response DTO (add status, customerId, dueDate, completedAt, completedBy, archivedAt) + update `listProjects` status filter + controller integration tests (~14 tests). ~1 modified file, ~1 new test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 204.1 | Add `completeProject()` to ProjectService | 204A | 203B, 201B | In `project/ProjectService.java`: new method `completeProject(UUID projectId, boolean acknowledgeUnbilledTime, UUID memberId, String orgRole)`. Require edit access. **Guardrail 1**: query `taskRepository.countByProjectIdAndStatusNotIn(projectId, List.of(TaskStatus.DONE, TaskStatus.CANCELLED))` -- if > 0, throw `InvalidStateException` with count: "Cannot complete project: {N} tasks are still open or in progress". **Guardrail 2**: query `timeEntryRepository.countByProjectIdAndBillingStatus(projectId, BillingStatus.UNBILLED)` -- if > 0 and `!acknowledgeUnbilledTime`, throw `ResourceConflictException` with unbilled count/hours: "Project has {N} unbilled time entries ({H} hours). Set acknowledgeUnbilledTime=true to proceed." If acknowledged, record waiver in audit details. Call `project.complete(memberId)`, save, audit `project.completed`, publish `ProjectCompletedEvent`, notify all project members. |
| 204.2 | Add `archiveProject()` to ProjectService | 204A | 203B | In `project/ProjectService.java`: new method `archiveProject(UUID projectId, UUID memberId, String orgRole)`. Require edit access. Call `project.archive()`, save, audit `project.archived`, publish `ProjectArchivedEvent`, notify all project members. No preconditions on task status (archiving with open tasks is allowed for abandoned projects). |
| 204.3 | Add `reopenProject()` to ProjectService | 204A | 203B | In `project/ProjectService.java`: new method `reopenProject(UUID projectId, UUID memberId, String orgRole)`. Require edit access. Works from COMPLETED or ARCHIVED. Call `project.reopen()`, save, audit `project.reopened`, publish `ProjectReopenedEvent`. |
| 204.4 | Create `ProjectCompletedEvent` | 204A | | New file: `event/ProjectCompletedEvent.java`. Record: `UUID projectId, UUID completedBy, String projectName, boolean unbilledTimeWaived, String tenantId, String orgId, Instant occurredAt`. |
| 204.5 | Create `ProjectArchivedEvent` | 204A | | New file: `event/ProjectArchivedEvent.java`. Record: `UUID projectId, UUID archivedBy, String projectName, String tenantId, String orgId, Instant occurredAt`. |
| 204.6 | Create `ProjectReopenedEvent` | 204A | | New file: `event/ProjectReopenedEvent.java`. Record: `UUID projectId, UUID reopenedBy, String projectName, String previousStatus, String tenantId, String orgId, Instant occurredAt`. |
| 204.7 | Wire notifications for project lifecycle | 204A | 204.1, 204.2 | Modify `notification/NotificationEventHandler.java`: add handlers for `ProjectCompletedEvent` and `ProjectArchivedEvent`. For each, query `projectMemberRepository.findByProjectId(projectId)` and create in-app notification for each member: "Project '{name}' has been completed/archived". Pattern: existing notification event handlers. |
| 204.8 | Add `PATCH /complete` endpoint | 204B | 204.1 | In `project/ProjectController.java`: add `@PatchMapping("/api/projects/{id}/complete")` accepting optional `CompleteProjectRequest` body record `{ Boolean acknowledgeUnbilledTime }`. Delegates to `projectService.completeProject()`. `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")`. Returns updated project response. |
| 204.9 | Add `PATCH /archive` and `/reopen` endpoints | 204B | 204.2, 204.3 | In `project/ProjectController.java`: add `@PatchMapping("/api/projects/{id}/archive")` and `@PatchMapping("/api/projects/{id}/reopen")`. No request body. `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")`. Return updated project response. |
| 204.10 | Update project response DTOs | 204B | 203.4 | In `project/ProjectController.java`: update (or create) `ProjectResponse` record to include: `ProjectStatus status`, `UUID customerId`, `LocalDate dueDate`, `Instant completedAt`, `UUID completedBy`, `String completedByName`, `Instant archivedAt`. Update `from()` factory method. Ensure `listProjects` and `getProject` endpoints return the enriched response. |
| 204.11 | Update `listProjects` status filter | 204B | 203.6 | In `project/ProjectController.java` and `project/ProjectService.java`: add `status` query parameter to `GET /api/projects` supporting comma-separated `ProjectStatus` values (default: `ACTIVE`). Add `includeArchived=true` shorthand that includes all statuses. Update `ProjectRepository` queries or use dynamic filtering. |
| 204.12 | Add due date filter to list endpoint | 204B | 203.6 | In `project/ProjectController.java`: add `dueBefore` query parameter to `GET /api/projects` (ISO LocalDate format). Filter projects whose `dueDate < dueBefore`. Useful for deadline views and overdue detection. |
| 204.13 | Add archive guard to existing create endpoints | 204B | 203.5 | In `task/TaskService.createTask()`, `timeentry/TimeEntryService.create()`, `document/DocumentService` create methods: before creating child entities, load the project and check `project.isReadOnly()`. If true, throw `InvalidStateException("Project is archived", "Cannot create new {entity} on an archived project")`. This prevents new tasks/time/documents on archived projects. ~3-4 modified files. |
| 204.14 | Write controller integration tests | 204B | 204.8, 204.9 | New file: `project/ProjectLifecycleIntegrationTest.java`. Tests: (1) complete_project_succeeds_when_all_tasks_done, (2) complete_project_rejects_with_open_tasks, (3) complete_project_rejects_unbilled_time_without_ack, (4) complete_project_succeeds_with_unbilled_time_ack, (5) archive_project_from_active, (6) archive_project_from_completed, (7) archive_project_with_open_tasks_succeeds, (8) reopen_project_from_completed, (9) reopen_project_from_archived, (10) list_projects_default_active_only, (11) list_projects_status_filter, (12) list_projects_due_before_filter, (13) create_task_on_archived_project_rejected, (14) create_time_entry_on_archived_project_rejected. ~14 tests. |

### Key Files

**Slice 204A -- Modify:**
- `backend/src/main/java/.../project/ProjectService.java`
- `backend/src/main/java/.../notification/NotificationEventHandler.java`

**Slice 204A -- Create:**
- `backend/src/main/java/.../event/ProjectCompletedEvent.java`
- `backend/src/main/java/.../event/ProjectArchivedEvent.java`
- `backend/src/main/java/.../event/ProjectReopenedEvent.java`

**Slice 204B -- Modify:**
- `backend/src/main/java/.../project/ProjectController.java`
- `backend/src/main/java/.../project/ProjectService.java`
- `backend/src/main/java/.../task/TaskService.java` (archive guard)
- `backend/src/main/java/.../timeentry/TimeEntryService.java` (archive guard)

**Slice 204B -- Create:**
- `backend/src/test/java/.../project/ProjectLifecycleIntegrationTest.java`

**Slice 204B -- Read for context:**
- `backend/src/main/java/.../project/ProjectController.java` -- existing response DTOs
- `backend/src/main/java/.../timeentry/TimeEntryRepository.java` -- unbilled time count query
- `backend/src/main/java/.../member/ProjectMemberRepository.java` -- for notification target resolution
- `backend/src/main/java/.../task/TaskRepository.java` -- for open task count query

### Architecture Decisions

- **Completion guardrails are strict**: All tasks MUST be DONE or CANCELLED. No partial completion. This is a forcing function for clean engagement closure.
- **Unbilled time is warn, not block**: The `acknowledgeUnbilledTime` flag allows firms to explicitly waive unbilled time. The waiver is recorded in the audit event for compliance.
- **Archive allows open tasks**: Unlike completion, archiving does not require all tasks to be done. This supports the "abandoned project" use case.
- **Archive guard on child creation**: Added to TaskService, TimeEntryService, and DocumentService. These guards are checked at creation time, not at the controller level, ensuring they apply regardless of entry point.

---

## Epic 205: Project-Customer Link + Due Date

**Goal**: Add the optional customer link (`customerId`) and due date (`dueDate`) fields to the Project create/update flows, with validation and cross-entity query support.

**References**: Architecture doc Section 3 (Project-Customer Link). ADR-113.

**Dependencies**: Epic 203 (project entity has the fields).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **205A** | 205.1--205.5 | `ProjectService` extension: accept `customerId` and `dueDate` in create/update flows + customer validation (exists + lifecycle check) + update create/update request DTOs + `ProjectRepository.findByCustomerId()` + convenience endpoint `GET /api/customers/{id}/projects`. ~3 modified files. Backend only. | |
| **205B** | 205.6--205.10 | `CustomerLifecycleGuard` extension: add `CREATE_PROJECT` action blocking for OFFBOARDING/OFFBOARDED customers + customer delete protection (block delete if linked projects exist) + integration tests (~8 tests). ~3 modified files, ~1 new test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 205.1 | Update `ProjectService.createProject()` | 205A | 203.4 | Modify `project/ProjectService.java`: add `customerId` and `dueDate` parameters to `createProject()`. If `customerId` is non-null: validate customer exists via `customerRepository.findById()` (throw `ResourceNotFoundException` if not found), validate customer is not OFFBOARDING/OFFBOARDED via `CustomerLifecycleGuard.requireActionPermitted(customer, LifecycleAction.CREATE_PROJECT)`. Set `project.setCustomerId(customerId)` and `project.setDueDate(dueDate)`. |
| 205.2 | Update `ProjectService.updateProject()` | 205A | 203.4 | Modify `project/ProjectService.java`: add `customerId` and `dueDate` to `updateProject()`. If project is COMPLETED or ARCHIVED, reject customer_id change (throw `InvalidStateException`). If `customerId` is non-null, validate customer exists. If `customerId` is explicitly provided as `null` (unlink), allow it. Pass through to `project.update()` or use setter. |
| 205.3 | Update create/update request DTOs | 205A | | In `project/ProjectController.java`: update `CreateProjectRequest` and `UpdateProjectRequest` records to include `UUID customerId` and `LocalDate dueDate` fields. Wire through to service calls. |
| 205.4 | Add `GET /api/customers/{id}/projects` | 205A | 203.6 | In `customer/ProjectCustomerController.java` (or new method on existing): add `@GetMapping("/api/customers/{id}/projects")` returning `List<ProjectResponse>` of projects linked to the customer. `@PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")`. Delegates to `projectService.listProjectsByCustomer(customerId)` using `ProjectRepository.findByCustomerId()`. |
| 205.5 | Add `GET /api/projects?customerId={id}` filter | 205A | 203.6 | In `project/ProjectController.java`: add optional `customerId` query parameter to `GET /api/projects`. Filter projects by customer link. |
| 205.6 | Add `CREATE_PROJECT` to `LifecycleAction` | 205B | | Modify `compliance/LifecycleAction.java` (if it's an enum) or equivalent: add `CREATE_PROJECT` action. Pattern: existing `CREATE_INVOICE` action. |
| 205.7 | Update `CustomerLifecycleGuard` for `CREATE_PROJECT` | 205B | 205.6 | Modify `compliance/CustomerLifecycleGuard.java`: add `CREATE_PROJECT` case to `requireActionPermitted()`. Block for OFFBOARDING and OFFBOARDED customers (same rules as CREATE_TASK). |
| 205.8 | Add customer delete protection for projects | 205B | 203.6 | Modify `customer/CustomerService.java` (or wherever delete lives): before deleting a customer, check `projectRepository.countByCustomerId(customerId)`. If > 0, throw `ResourceConflictException("Cannot delete customer. {N} projects are linked. Use the offboarding lifecycle instead.")`. |
| 205.9 | Wire customer validation in project create | 205B | 205.1, 205.7 | Ensure `ProjectService.createProject()` calls `CustomerLifecycleGuard.requireActionPermitted()` when `customerId` is non-null. The guard throws `InvalidStateException` if the customer is in a blocked lifecycle state. |
| 205.10 | Write integration tests | 205B | 205.1, 205.7, 205.8 | New file: `project/ProjectCustomerLinkIntegrationTest.java`. Tests: (1) create_project_with_customer_id, (2) create_project_without_customer_id, (3) create_project_rejects_offboarded_customer, (4) update_project_change_customer, (5) update_project_remove_customer, (6) list_projects_by_customer_id, (7) get_customer_projects_endpoint, (8) delete_customer_blocked_with_linked_projects. ~8 tests. |

### Key Files

**Slice 205A -- Modify:**
- `backend/src/main/java/.../project/ProjectService.java`
- `backend/src/main/java/.../project/ProjectController.java`
- `backend/src/main/java/.../customer/ProjectCustomerController.java`

**Slice 205A -- Read for context:**
- `backend/src/main/java/.../customer/CustomerRepository.java` -- customer lookup
- `backend/src/main/java/.../compliance/CustomerLifecycleGuard.java` -- guard pattern

**Slice 205B -- Modify:**
- `backend/src/main/java/.../compliance/CustomerLifecycleGuard.java`
- `backend/src/main/java/.../customer/CustomerService.java`

**Slice 205B -- Create:**
- `backend/src/test/java/.../project/ProjectCustomerLinkIntegrationTest.java`

### Architecture Decisions

- **Soft FK only**: `customer_id` is a plain UUID column with no database REFERENCES constraint. Consistent with every other cross-entity reference in the codebase (ADR-113).
- **Customer change blocked on non-ACTIVE projects**: COMPLETED and ARCHIVED projects are frozen -- no customer reassignment. This preserves financial attribution integrity.
- **`CREATE_PROJECT` guard reuses `CustomerLifecycleGuard`**: Follows the existing pattern. The guard already handles CREATE_TASK, CREATE_TIME_ENTRY, CREATE_INVOICE. Adding CREATE_PROJECT is a one-line case addition.

---

## Epic 206: Delete Protection & Cross-Entity Guards

**Goal**: Add delete protection to projects and tasks that have accumulated operational data, and add a `ProjectLifecycleGuard` to enforce archive read-only rules consistently.

**References**: Architecture doc Section 4 (Delete Protection & Cascade Rules). ADR-112.

**Dependencies**: Epics 201, 203, 205 (all entity refactoring complete).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **206A** | 206.1--206.5 | `ProjectService.deleteProject()` refactor: child-count guards (tasks, time entries, invoices, documents) + reject delete for COMPLETED/ARCHIVED + `TaskService.deleteTask()` guard: block if time entries exist + 409 error responses with descriptive messages. ~2 modified files. Backend only. | |
| **206B** | 206.6--206.10 | `ProjectLifecycleGuard` service (reusable guard checking project status for child creation) + customer delete protection verification + comprehensive integration tests (~10 tests). ~1 new file, ~1 modified file, ~1 new test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 206.1 | Refactor `ProjectService.deleteProject()` | 206A | 203B | Modify `project/ProjectService.java`: before deleting, check: (1) `project.getStatus() != ProjectStatus.ACTIVE` -> throw `ResourceConflictException("Cannot delete {status} projects. Use reopen first.")`. (2) `taskRepository.countByProjectId(projectId) > 0` -> throw `ResourceConflictException("Cannot delete project with {N} tasks. Archive this project instead.")`. (3) `timeEntryRepository.countByProjectId(projectId) > 0` -> same pattern. (4) `invoiceRepository.countByProjectId(projectId) > 0` -> same. (5) `documentRepository.countByProjectId(projectId) > 0` -> same. Only if all counts are zero AND status is ACTIVE, proceed with delete. |
| 206.2 | Add time entry count queries | 206A | | If not already present, add `timeEntryRepository.countByProjectId(UUID projectId)` and `timeEntryRepository.countByTaskId(UUID taskId)` to `TimeEntryRepository`. Check existing queries first. |
| 206.3 | Add document/invoice count queries | 206A | | If not already present, add `documentRepository.countByProjectId(UUID projectId)` and `invoiceRepository.countByProjectId(UUID projectId)` (or equivalent). Check existing queries. |
| 206.4 | Refactor `TaskService.deleteTask()` | 206A | 201B | Modify `task/TaskService.java`: before deleting, check `timeEntryRepository.countByTaskId(taskId)`. If > 0, throw `ResourceConflictException("Cannot delete task with {N} time entries. Cancel this task instead.")`. |
| 206.5 | Descriptive 409 error messages | 206A | 206.1, 206.4 | Ensure all `ResourceConflictException` messages include: entity counts, suggested action ("Archive instead", "Cancel instead"), consistent formatting. Pattern: existing `ResourceConflictException` usage in `InvoiceService`. |
| 206.6 | Create `ProjectLifecycleGuard` | 206B | 203B | New file: `project/ProjectLifecycleGuard.java`. `@Component`. Method: `requireNotReadOnly(UUID projectId)` -- loads project, checks `project.isReadOnly()`, throws `InvalidStateException("Project is archived. No modifications allowed.")` if true. Method: `requireActive(UUID projectId)` -- loads project, checks `project.getStatus() == ProjectStatus.ACTIVE`, throws if not. This centralizes the archive guard so TaskService, TimeEntryService, DocumentService, etc. can call one method instead of each implementing their own check. |
| 206.7 | Wire `ProjectLifecycleGuard` into child services | 206B | 206.6 | Modify `task/TaskService.createTask()`, `timeentry/TimeEntryService.create()`, `document/DocumentService` (create methods): inject `ProjectLifecycleGuard` and call `requireNotReadOnly(projectId)` before creating the entity. Replace any ad-hoc archive checks added in 204.13 with the centralized guard. ~3-4 modified files. |
| 206.8 | Verify customer delete protection | 206B | 205.8 | Verify that `CustomerService` (or wherever customer delete is) also blocks delete when invoices or retainers exist. Add `invoiceRepository.countByCustomerId(customerId)` and `retainerRepository.countByCustomerId(customerId)` checks if missing. Ensure error messages guide toward offboarding lifecycle. |
| 206.9 | Verify cross-entity integrity | 206B | | Verify: (1) Archiving a project does NOT auto-complete open tasks (just prevents new child creation). (2) Offboarding a customer does NOT auto-archive linked projects. (3) Existing projects linked to OFFBOARDING customer remain linked. These are negative tests -- verify the system does NOT take cascading actions. |
| 206.10 | Write comprehensive integration tests | 206B | 206.1, 206.4, 206.6 | New file: `project/DeleteProtectionIntegrationTest.java`. Tests: (1) delete_empty_active_project_succeeds, (2) delete_project_with_tasks_rejected, (3) delete_project_with_time_entries_rejected, (4) delete_project_with_invoices_rejected, (5) delete_completed_project_rejected, (6) delete_archived_project_rejected, (7) delete_task_with_time_entries_rejected, (8) delete_task_without_time_entries_succeeds, (9) create_task_on_archived_project_rejected, (10) create_time_on_archived_project_rejected. ~10 tests. |

### Key Files

**Slice 206A -- Modify:**
- `backend/src/main/java/.../project/ProjectService.java`
- `backend/src/main/java/.../task/TaskService.java`

**Slice 206A -- Read for context:**
- `backend/src/main/java/.../timeentry/TimeEntryRepository.java`
- `backend/src/main/java/.../document/DocumentRepository.java`
- `backend/src/main/java/.../invoice/InvoiceRepository.java`

**Slice 206B -- Create:**
- `backend/src/main/java/.../project/ProjectLifecycleGuard.java`
- `backend/src/test/java/.../project/DeleteProtectionIntegrationTest.java`

**Slice 206B -- Modify:**
- `backend/src/main/java/.../task/TaskService.java`
- `backend/src/main/java/.../timeentry/TimeEntryService.java`

### Architecture Decisions

- **Centralized `ProjectLifecycleGuard`**: Rather than each service implementing its own archive check, a shared guard component avoids duplication. Pattern matches `CustomerLifecycleGuard`.
- **Hard delete only for empty ACTIVE projects**: Once a project has any operational data (tasks, time, invoices, documents), it cannot be deleted. Archive is the only path. This is the core principle from ADR-112.
- **No cascading side effects**: Archiving a project does not auto-complete tasks. Offboarding a customer does not auto-archive projects. Each entity manages its own lifecycle independently. Cross-entity rules only prevent creation, not force transitions.

---

## Epic 207: Task Lifecycle Frontend

**Goal**: Integrate task lifecycle actions into the existing task list and task detail sheet. Add status filter chips, lifecycle action buttons, and visual styling for terminal states.

**References**: Architecture doc Section 1 (Frontend Changes).

**Dependencies**: Epic 202 (backend endpoints).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **207A** | 207.1--207.6 | Task list panel: status filter chips (All, Open, In Progress, Done, Cancelled) with default hiding Done/Cancelled + visual distinction for Done (strikethrough/muted) and Cancelled (muted+badge) + `task-actions.ts` lifecycle server actions (completeTask, cancelTask, reopenTask) + update task list API call with status param + frontend tests. ~3 modified files, ~1 new file. Frontend only. | |
| **207B** | 207.7--207.11 | Task detail sheet: "Mark Done" primary action when IN_PROGRESS, "Cancel" in overflow menu, "Reopen" for Done/Cancelled + display `completedAt`/`completedBy` info + My Work page: ensure default filter excludes Done/Cancelled + frontend tests. ~3 modified files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 207.1 | Add task lifecycle server actions | 207A | | New file or append to `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts`: add `completeTask(projectId, taskId)`, `cancelTask(projectId, taskId)`, `reopenTask(projectId, taskId)` server actions. Each calls `PATCH /api/projects/{projectId}/tasks/{taskId}/{action}` via the API client. Revalidate path on success. Pattern: existing `claimTask`/`releaseTask` actions in same file. |
| 207.2 | Add status filter chips to task list | 207A | | Modify `frontend/components/tasks/task-list-panel.tsx`: add a row of filter chips above the task list. Options: All, Open, In Progress, Done, Cancelled. Default selection: Open + In Progress (multi-select). On change, update the `status` query parameter in the API call. Use Badge or Button components with active/inactive styling. Pattern: existing filter patterns in project list or saved views. |
| 207.3 | Update task list API call with status param | 207A | | Modify `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts` (or the page's data fetching): pass `status` query parameter to `GET /api/projects/{projectId}/tasks`. Default: `OPEN,IN_PROGRESS`. When "All" is selected, pass `OPEN,IN_PROGRESS,DONE,CANCELLED`. |
| 207.4 | Visual styling for terminal task states | 207A | | Modify `frontend/components/tasks/task-list-panel.tsx`: for DONE tasks, apply `line-through text-muted-foreground` to the title. For CANCELLED tasks, apply `text-muted-foreground` plus a "Cancelled" badge (slate-200 background). Update task status badge colors: DONE = green/success, CANCELLED = slate/neutral. Pattern: existing `task-badge-config.ts` for badge styling. |
| 207.5 | Update task response type | 207A | | Modify the TypeScript task type (wherever defined -- likely in `task-actions.ts` or a types file): add `completedAt: string | null`, `completedBy: string | null`, `completedByName: string | null`, `cancelledAt: string | null` fields. |
| 207.6 | Write frontend tests for task status filter | 207A | | New test or append to existing: test that the task list renders filter chips, that selecting "Done" shows done tasks, that the default view hides Done/Cancelled. ~4 tests. Pattern: existing `projects-page.test.tsx`. |
| 207.7 | Add lifecycle action buttons to task detail sheet | 207B | 207.1 | Modify `frontend/components/tasks/task-detail-sheet.tsx`: contextual action buttons based on task status. IN_PROGRESS: "Mark Done" as primary action button + "Cancel" in overflow menu. OPEN: "Cancel" in overflow. DONE: "Reopen" button. CANCELLED: "Reopen" button. Use existing button/dropdown patterns. Call corresponding server actions on click. |
| 207.8 | Display completion metadata in task detail | 207B | | Modify `frontend/components/tasks/task-detail-sheet.tsx`: when task is DONE, show "Completed by {name} on {date}" below the status badge. When CANCELLED, show "Cancelled on {date}". Use `Intl.DateTimeFormat` for date formatting. |
| 207.9 | Update My Work page filter | 207B | | Verify `frontend/app/(app)/org/[slug]/my-work/` page and its data fetching: ensure the default query only returns OPEN + IN_PROGRESS tasks (this should already be the case if the backend default is applied). Add a toggle or filter chip for "Show completed" if desired (optional -- not required by architecture doc). |
| 207.10 | Disable claim/release for terminal states | 207B | | Modify `frontend/components/tasks/task-detail-sheet.tsx`: hide "Claim" button when task is DONE or CANCELLED. Hide "Release" button when task is DONE or CANCELLED. The backend rejects these transitions, but the UI should not offer them. |
| 207.11 | Write frontend tests for task detail lifecycle | 207B | | Tests: (1) mark_done_button_shown_when_in_progress, (2) cancel_button_in_overflow_menu, (3) reopen_button_shown_for_done_task, (4) reopen_button_shown_for_cancelled_task, (5) completion_metadata_displayed_for_done_task. ~5 tests. |

### Key Files

**Slice 207A -- Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts`
- `frontend/components/tasks/task-list-panel.tsx`
- `frontend/components/tasks/task-badge-config.ts`

**Slice 207B -- Modify:**
- `frontend/components/tasks/task-detail-sheet.tsx`
- `frontend/app/(app)/org/[slug]/my-work/page.tsx` (verify only)

**Slice 207A/B -- Read for context:**
- `frontend/components/tasks/task-list-panel.tsx` -- existing task list structure
- `frontend/components/tasks/task-detail-sheet.tsx` -- existing detail sheet layout
- `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts` -- existing claim/release actions

### Architecture Decisions

- **Multi-select filter chips**: Users can select multiple statuses (e.g., Open + In Progress). Default is Open + In Progress, matching the backend default. This is more flexible than single-select.
- **No confirmation dialog for "Mark Done"**: Completing a task is a routine action. No confirmation needed. Cancel shows a brief confirmation since it's destructive.
- **Reopen always goes to OPEN**: Both Done and Cancelled tasks reopen to OPEN status with cleared assignee. The user must re-claim if they want to continue working.

---

## Epic 208: Project Lifecycle Frontend

**Goal**: Integrate project lifecycle actions into existing project list and detail pages. Add status filter, due date display, customer link UI, completion dialog with guardrails, and archived-project read-only mode.

**References**: Architecture doc Section 2 (Frontend Changes), Section 3 (Frontend Changes).

**Dependencies**: Epics 204 (project lifecycle endpoints), 205 (customer link endpoints).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **208A** | 208.1--208.7 | Project list: status filter (Active, Completed, Archived, All) + status badge + due date column with overdue warning + project creation/edit dialog: due date picker + customer dropdown + lifecycle server actions (completeProject, archiveProject, reopenProject) + update project response type + frontend tests. ~5 modified files, ~1 new file. Frontend only. | |
| **208B** | 208.8--208.14 | Project detail header: status badge + due date + contextual action buttons + "Complete Project" dialog (error for open tasks, confirmation for unbilled time) + archived project read-only banner + customer display in project sidebar + customer detail "Projects" tab + frontend tests. ~5 modified files, ~1 new component. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 208.1 | Add project lifecycle server actions | 208A | | New file or append to `frontend/app/(app)/org/[slug]/projects/actions.ts`: add `completeProject(projectId, acknowledgeUnbilledTime?)`, `archiveProject(projectId)`, `reopenProject(projectId)` server actions. Each calls `PATCH /api/projects/{id}/{action}`. Pattern: existing project actions. |
| 208.2 | Update project response type | 208A | | Update TypeScript project type (wherever defined): add `status: 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'`, `customerId: string | null`, `customerName: string | null`, `dueDate: string | null`, `completedAt: string | null`, `completedBy: string | null`, `completedByName: string | null`, `archivedAt: string | null`. |
| 208.3 | Add status filter to project list | 208A | | Modify `frontend/app/(app)/org/[slug]/projects/page.tsx`: add status filter chips/tabs (Active, Completed, Archived, All). Default: Active. Pass `status` query param to `GET /api/projects`. Show status badge on each project card/row for non-ACTIVE projects. |
| 208.4 | Add due date column to project list | 208A | | Modify project list: add "Due Date" column. For ACTIVE projects with due date in the past, show overdue indicator (red/amber badge or text color). Format date using locale-appropriate format. |
| 208.5 | Update create project dialog with due date and customer | 208A | | Modify `frontend/components/projects/create-project-dialog.tsx`: add optional date picker for "Due Date" field. Add optional searchable customer dropdown (fetch `GET /api/customers?status=ACTIVE` for options). Wire both fields into the create project API call. Pattern: existing date picker usage in task creation dialog. |
| 208.6 | Update edit project dialog with due date and customer | 208A | | Modify `frontend/components/projects/edit-project-dialog.tsx`: add due date picker and customer dropdown, pre-populated with current values. Allow clearing customer (set to null). Wire into update project API call. |
| 208.7 | Write frontend tests for project list | 208A | | Tests: (1) status_filter_defaults_to_active, (2) status_badge_shown_for_completed_project, (3) due_date_column_rendered, (4) overdue_warning_for_past_due_active_project, (5) create_dialog_has_due_date_picker, (6) create_dialog_has_customer_dropdown. ~6 tests. |
| 208.8 | Add status badge and due date to project detail header | 208B | | Modify `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`: show `ProjectStatus` badge next to project name. Show due date with overdue warning if applicable. |
| 208.9 | Add contextual action buttons to project detail | 208B | 208.1 | Modify project detail page: ACTIVE project -> "Complete Project" button + "Archive" in overflow menu. COMPLETED -> "Archive" button + "Reopen" in overflow. ARCHIVED -> "Restore" button. Wire to lifecycle server actions. |
| 208.10 | Create "Complete Project" confirmation dialog | 208B | | New component: `frontend/components/projects/complete-project-dialog.tsx`. On trigger: call a pre-check endpoint or attempt completion. If 400 (open tasks): show error dialog with count ("Cannot complete: {N} tasks are still open or in progress. Please complete or cancel them first."). If 409 (unbilled time): show confirmation dialog with hours/amount ("This project has {N} unbilled time entries totaling {H} hours. Complete anyway?") with "Complete Anyway" button that re-calls with `acknowledgeUnbilledTime=true`. On success: close dialog, refresh page. Pattern: existing delete confirmation dialogs. |
| 208.11 | Add archived project read-only banner | 208B | | Modify project detail page: when `project.status === 'ARCHIVED'`, show a prominent banner at the top: "This project is archived. It is read-only. [Restore]". Disable/hide all edit actions: create task button, log time button, upload document button, edit project button. The "Restore" link in the banner calls `reopenProject()`. |
| 208.12 | Add customer display to project detail | 208B | | Modify project detail page (sidebar or header area): if `project.customerId` is set, show "Customer: {customerName}" with a link to `/org/[slug]/customers/{customerId}`. If no customer, show nothing or "Internal Project" label. |
| 208.13 | Add "Projects" tab to customer detail page | 208B | | Modify `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` (or its tab component): add a "Projects" tab fetching `GET /api/customers/{id}/projects`. Display project list with name, status badge, due date. Each project links to its detail page. Pattern: existing customer detail tabs (e.g., invoices tab, retainers tab). |
| 208.14 | Write frontend tests for project detail lifecycle | 208B | | Tests: (1) complete_button_shown_for_active_project, (2) archive_button_shown_for_completed_project, (3) restore_button_shown_for_archived_project, (4) completion_dialog_shows_error_for_open_tasks, (5) completion_dialog_shows_unbilled_time_warning, (6) archived_banner_shown_and_edit_disabled, (7) customer_link_displayed_in_project_detail, (8) projects_tab_shown_on_customer_detail. ~8 tests. |

### Key Files

**Slice 208A -- Modify:**
- `frontend/app/(app)/org/[slug]/projects/actions.ts`
- `frontend/app/(app)/org/[slug]/projects/page.tsx`
- `frontend/components/projects/create-project-dialog.tsx`
- `frontend/components/projects/edit-project-dialog.tsx`

**Slice 208B -- Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` (or tab component)

**Slice 208B -- Create:**
- `frontend/components/projects/complete-project-dialog.tsx`

**Slice 208B -- Read for context:**
- `frontend/components/projects/delete-project-dialog.tsx` -- confirmation dialog pattern
- `frontend/components/projects/project-tabs.tsx` -- tab structure
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` -- current detail page layout
- `frontend/components/projects/link-customer-dialog.tsx` -- existing customer link dialog (may be reusable)

### Architecture Decisions

- **Completion dialog is two-phase**: First attempt triggers the backend call. If it fails with 400 (open tasks), show an error (no retry). If it fails with 409 (unbilled time), show a confirmation with "Complete Anyway" that retries with the `acknowledgeUnbilledTime` flag. This is simpler than a pre-check endpoint.
- **Customer dropdown shows ACTIVE customers only**: OFFBOARDING/OFFBOARDED customers are excluded from the creation dropdown. Existing links to non-active customers are preserved and displayed.
- **Read-only mode is UI-enforced**: The banner and disabled buttons are a UX convenience. The backend is the authoritative guard -- even if the UI has a bug, the backend rejects modifications to archived projects.
- **Existing `link-customer-dialog.tsx`**: There is already a component for linking customers to projects. Check if it can be reused or extended rather than building a new dropdown in the create/edit dialogs.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/Task.java` - Core entity to refactor: String->enum for status/priority, add lifecycle fields and transition methods
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java` - Core entity to refactor: add status, customerId, dueDate, lifecycle fields and transition methods
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` - Heaviest service modifications: lifecycle methods, completion guardrails, customer validation, delete protection
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` - Lifecycle methods, delete protection guard, enum transition enforcement
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/LifecycleStatus.java` - Pattern to follow for enum with allowedTransitions() -- TaskStatus and ProjectStatus mirror this design