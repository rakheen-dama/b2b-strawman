# Phase 29 — Entity Lifecycle & Relationship Integrity

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with the following core domain entities already in place:

- **Project**: name, description, createdBy, timestamps, custom fields. **No status field, no lifecycle states, no customer link.**
- **Task**: project-scoped, has `status` as raw String ("OPEN", "IN_PROGRESS"), claim/release mechanics. **No terminal state (DONE/CANCELLED), no completion timestamps, no transition validation.**
- **Customer**: full lifecycle (PROSPECT → ONBOARDING → ACTIVE → DORMANT → OFFBOARDING → OFFBOARDED) with `LifecycleStatus` enum and `CustomerLifecycleGuard`.
- **Invoice**: DRAFT → APPROVED → SENT → PAID / VOID lifecycle, linked to `customerId`.
- **TimeEntry**: linked to `projectId` and `taskId`, has `BillingStatus` (UNBILLED, BILLED, WRITTEN_OFF).
- **Retainer**: ACTIVE → PAUSED → TERMINATED, linked to `customerId`.
- **Document**: has Status enum, scoped to project.
- **ProjectBudget**: linked to `projectId`, tracks hours/currency budget vs. actuals.

Key infrastructure already available: audit events, notifications, domain events, activity feed, custom fields, tags, saved views, email delivery, portal read-model.

## Objective

Add lifecycle state machines and relationship integrity rules to Project and Task — the two core entities that currently lack formal lifecycle management. This phase does not add new business features; it adds **structural guardrails** that make the system robust and trustworthy.

Specifically:
1. Promote Task status from a raw String to a proper enum with terminal states (DONE, CANCELLED) and transition validation. Also promote Task priority from raw String to enum.
2. Add a ProjectStatus lifecycle (ACTIVE → COMPLETED → ARCHIVED) with guardrails that enforce clean completion.
3. Add a project due date (target/deadline date) for engagement deadline tracking.
4. Add an optional Project ↔ Customer link so projects can be associated with the customer they serve.
5. Add delete protection and cascade rules across entity boundaries to prevent orphaned data and impossible states.

## Constraints & Assumptions

- **Backward compatible**: existing projects default to ACTIVE, existing tasks retain their current status values (OPEN, IN_PROGRESS map directly to the new enum).
- **Migration safety**: all schema changes must be additive (new columns with defaults, new enums). No data loss.
- **No new UI pages**: lifecycle actions integrate into existing project detail, task detail, and list pages. No new top-level navigation items.
- **Guardrails are server-enforced**: frontend shows warnings/confirmations, but the backend is the source of truth for transition rules.
- **Customer link is optional**: projects without a customer (internal projects) remain valid.

---

## Section 1 — Task Lifecycle Completion

### Data Model

Promote `Task.status` from `String` to a proper Java enum `TaskStatus`:

```
TaskStatus:
  OPEN          -- newly created, unassigned
  IN_PROGRESS   -- claimed by a member
  DONE          -- work completed
  CANCELLED     -- abandoned / no longer needed
```

Promote `Task.priority` from `String` to a proper Java enum `TaskPriority`:

```
TaskPriority:
  LOW
  MEDIUM        -- default
  HIGH
  URGENT
```

Add fields to Task entity:
- `completedAt` (Instant, nullable) — set when transitioning to DONE
- `completedBy` (UUID, nullable) — member ID who marked it done
- `cancelledAt` (Instant, nullable) — set when transitioning to CANCELLED

### Transition Rules

```
OPEN         → IN_PROGRESS  (claim)
OPEN         → CANCELLED    (cancel)
IN_PROGRESS  → DONE         (complete)
IN_PROGRESS  → OPEN         (release — existing behavior)
IN_PROGRESS  → CANCELLED    (cancel)
DONE         → OPEN         (reopen — allows corrections)
CANCELLED    → OPEN         (reopen)
```

Transitions not in this list are rejected with 400 (InvalidStateException).

### Guardrails
- Only the assignee or an ADMIN+ can mark a task DONE.
- Any ADMIN+ can cancel a task.
- Reopening (DONE → OPEN or CANCELLED → OPEN) clears `completedAt`/`completedBy`/`cancelledAt` and unassigns.
- Tasks with time entries cannot be deleted (throw 409 ResourceConflictException). They can be CANCELLED.

### API Changes

- `PATCH /api/projects/{projectId}/tasks/{taskId}/complete` — transition to DONE
- `PATCH /api/projects/{projectId}/tasks/{taskId}/cancel` — transition to CANCELLED
- `PATCH /api/projects/{projectId}/tasks/{taskId}/reopen` — transition back to OPEN
- Existing `claim` and `release` endpoints unchanged but must validate against new transition rules.
- `GET /api/projects/{projectId}/tasks` — add `status` query parameter for filtering (comma-separated list of statuses, default: OPEN,IN_PROGRESS).

### Frontend Changes

- Task list: add status filter chips (All, Open, In Progress, Done, Cancelled). Default view hides Done/Cancelled.
- Task detail sheet: "Mark Done" button (primary action when IN_PROGRESS), "Cancel" in overflow menu, "Reopen" when viewing Done/Cancelled tasks.
- Task list items: visual distinction for Done (strikethrough or muted) and Cancelled (muted + badge).
- My Work page: filter defaults to OPEN + IN_PROGRESS (existing behavior preserved).

### Audit & Notifications

- Audit events for: `task.completed`, `task.cancelled`, `task.reopened`
- Notification to task assignee when their task is cancelled by someone else.

---

## Section 2 — Project Lifecycle

### Data Model

Add `ProjectStatus` enum:

```
ProjectStatus:
  ACTIVE       -- default for new and existing projects
  COMPLETED    -- all work done, financially closed (or waived)
  ARCHIVED     -- hidden from default views, read-only
```

Add fields to Project entity:
- `status` (ProjectStatus, not null, default ACTIVE)
- `customerId` (UUID, nullable) — FK to Customer (see Section 3)
- `dueDate` (LocalDate, nullable) — target completion/filing deadline for the engagement
- `completedAt` (Instant, nullable)
- `completedBy` (UUID, nullable)
- `archivedAt` (Instant, nullable)

### Transition Rules

```
ACTIVE    → COMPLETED  (complete — with guardrails)
ACTIVE    → ARCHIVED   (archive directly — skip completion for abandoned projects)
COMPLETED → ARCHIVED   (archive)
COMPLETED → ACTIVE     (reopen)
ARCHIVED  → ACTIVE     (restore)
```

### Completion Guardrails

When completing a project (ACTIVE → COMPLETED), the backend checks:

1. **All tasks must be DONE or CANCELLED.** If any tasks are OPEN or IN_PROGRESS, reject with 400 and include the count of incomplete tasks in the error response.

2. **Unbilled time check (warn, don't block).** If there are UNBILLED time entries:
   - The completion request must include an explicit `acknowledgeUnbilledTime: true` flag.
   - Without the flag, reject with 409 and include the unbilled hours/amount in the error response.
   - With the flag, proceed and record the waiver in the audit event details.

### Archive Rules

Archived projects are **read-only**:
- Cannot create new tasks, time entries, documents, or invoices against an archived project.
- Cannot modify existing tasks or time entries.
- Budget configuration is frozen.
- Existing invoices (DRAFT, SENT) can still transition through their own lifecycle (you can still pay an invoice for an archived project).

### API Changes

- `PATCH /api/projects/{id}/complete` — body: `{ "acknowledgeUnbilledTime": boolean }` (optional, only needed if unbilled time exists)
- `PATCH /api/projects/{id}/archive` — no body
- `PATCH /api/projects/{id}/reopen` — no body (works from COMPLETED or ARCHIVED)
- `GET /api/projects` — add `status` query parameter (comma-separated, default: ACTIVE). Add `includeArchived=true` shorthand.
- `POST /api/projects` and `PUT /api/projects/{id}` — add optional `dueDate` field (ISO LocalDate).
- `GET /api/projects?dueBefore={date}` — filter projects by due date (useful for deadline views).

### Frontend Changes

- Project list: filter defaults to ACTIVE. Add status filter (Active, Completed, Archived, All). Completed/Archived projects show status badge.
- Project list: show due date column. Overdue projects (due date in the past, still ACTIVE) get a visual warning indicator (red/amber badge).
- Project detail header: status badge + due date display (with overdue warning if applicable). Action buttons contextual:
  - ACTIVE: "Complete Project" button + "Archive" in overflow menu
  - COMPLETED: "Archive" button + "Reopen" in overflow menu
  - ARCHIVED: "Restore" button (reopens to ACTIVE)
- Project creation/edit dialog: due date picker field.
- "Complete Project" action: if incomplete tasks exist, show error dialog with count. If unbilled time exists, show confirmation dialog with hours/amount and "Complete Anyway" button.
- Archived project detail: read-only banner at top, all edit actions disabled/hidden.

### Audit & Notifications

- Audit events for: `project.completed`, `project.archived`, `project.reopened`
- Notification to all project members when project is completed or archived.

---

## Section 3 — Project ↔ Customer Link

### Data Model

Add to Project entity:
- `customerId` (UUID, nullable) — references Customer entity within the same tenant schema.

This is a **soft FK** (UUID column, validated at application level) consistent with the existing pattern (Invoice.customerId, TimeEntry.projectId, etc.).

### Business Rules

- Customer can be assigned to a project at creation or updated at any time (by ADMIN+).
- Customer can be changed or removed (set to null) on any ACTIVE project.
- Customer cannot be changed on COMPLETED or ARCHIVED projects (they're read-only or frozen).
- If the linked customer is in OFFBOARDING or OFFBOARDED status, block creation of new projects for that customer (reuse `CustomerLifecycleGuard` pattern — add `CREATE_PROJECT` action).
- Existing projects linked to an OFFBOARDING/OFFBOARDED customer remain linked (no cascade unlink) but no new projects can be created.

### API Changes

- `POST /api/projects` — add optional `customerId` field to create request.
- `PUT /api/projects/{id}` — add optional `customerId` field to update request.
- `GET /api/projects?customerId={id}` — filter projects by customer.
- `GET /api/customers/{id}/projects` — convenience endpoint returning projects linked to a customer.

### Frontend Changes

- Project creation dialog: optional customer dropdown (searchable, shows ACTIVE customers only).
- Project detail: customer display in header/sidebar (clickable link to customer detail).
- Project settings/edit: customer can be changed via dropdown.
- Customer detail page: "Projects" tab showing linked projects with status.

---

## Section 4 — Delete Protection & Cascade Rules

### Project Delete Protection

Currently, OWNER can delete any project. Add guardrails:

| Project State | Has Children? | Can Delete? | Behavior |
|--------------|---------------|-------------|----------|
| ACTIVE | No tasks, no time, no invoices, no documents | Yes | Hard delete |
| ACTIVE | Has any children | No | 409 — "Archive this project instead" |
| COMPLETED | Any | No | 409 — "Cannot delete completed projects" |
| ARCHIVED | Any | No | 409 — "Cannot delete archived projects" |

The principle: once a project has any operational data, it cannot be deleted. Archive is the soft-delete equivalent.

### Task Delete Protection

| Condition | Can Delete? | Behavior |
|-----------|-------------|----------|
| No time entries | Yes | Hard delete |
| Has time entries | No | 409 — "Cancel this task instead. It has N time entries." |

### Customer Delete Protection

Review existing customer delete behavior (if any) and ensure:
- Customer with linked projects → block delete (409)
- Customer with invoices → block delete (409)
- Customer with retainers → block delete (409)
- Guide user toward OFFBOARDING lifecycle instead.

### Cross-Entity Integrity Checks

When archiving a project:
- All open/in-progress tasks are **not** auto-completed (the user should explicitly handle them first, or archive acts as a force-close).
- Actually — allow archiving with open tasks (for abandoned projects), but prevent completion with open tasks.

When offboarding a customer:
- Linked ACTIVE projects get a warning but are NOT auto-archived. The firm may still have wrap-up work.
- Block creation of new projects for OFFBOARDING/OFFBOARDED customers.

---

## Section 5 — Migration Strategy

### Tenant Schema Migration (V46 or next available)

```sql
-- Task: add new columns, constrain status and priority
ALTER TABLE tasks ADD COLUMN completed_at TIMESTAMPTZ;
ALTER TABLE tasks ADD COLUMN completed_by UUID;
ALTER TABLE tasks ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE tasks ADD CONSTRAINT tasks_status_check
  CHECK (status IN ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED'));
ALTER TABLE tasks ADD CONSTRAINT tasks_priority_check
  CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'));

-- Project: add lifecycle columns + due date
ALTER TABLE projects ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE projects ADD COLUMN customer_id UUID;
ALTER TABLE projects ADD COLUMN due_date DATE;
ALTER TABLE projects ADD COLUMN completed_at TIMESTAMPTZ;
ALTER TABLE projects ADD COLUMN completed_by UUID;
ALTER TABLE projects ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE projects ADD CONSTRAINT projects_status_check
  CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED'));
```

### Data Backfill

- All existing projects get `status = 'ACTIVE'` (via DEFAULT).
- All existing tasks retain their current status string values (OPEN, IN_PROGRESS are valid enum values).
- All existing task priorities retain their current values (LOW, MEDIUM, HIGH are valid; add URGENT as new option).
- No customer_id or due_date backfill needed — existing projects remain unlinked (null) with no due date.

---

## Out of Scope

- **Task dependencies / subtasks**: tasks remain flat, no parent-child relationships.
- **Project templates interaction**: project templates create ACTIVE projects; no change needed.
- **Retainer ↔ Project link**: retainers are customer-scoped, not project-scoped. No change.
- **Bulk operations**: no bulk-complete or bulk-archive. One project at a time.
- **Scheduled archival**: no auto-archive after N days of inactivity. Manual only.
- **Portal impact**: portal read-model may need to filter by project status (exclude archived). Minimal change if needed.
- **Saved views**: existing saved view infrastructure should work with new status fields without changes to the view engine. New filter options can be added as a follow-up if needed.

## ADR Topics to Address

1. **Task status representation** — enum vs. String with CHECK constraint. Recommend enum in Java + CHECK in DB.
2. **Project completion semantics** — what "completed" means (all tasks done + billing acknowledged) vs. "archived" (hidden from view, read-only). Two-state vs. three-state model.
3. **Delete vs. archive philosophy** — why hard delete is restricted once operational data exists. Archive as soft-delete.
4. **Customer link optionality** — why the FK is nullable and validated at application level (consistent with existing patterns, supports internal projects).

## Style & Boundaries

- Follow existing code patterns: feature packages, constructor injection, no Lombok, records for DTOs.
- Reuse `CustomerLifecycleGuard` pattern for project lifecycle guards.
- Reuse `InvalidStateException` for transition violations, `ResourceConflictException` for delete blocks.
- All lifecycle transitions must emit audit events and domain events (for activity feed).
- All transition endpoints should be `PATCH` (partial state change), not `PUT`.
- Backend guardrails are authoritative — frontend confirmation dialogs are UX, not security.
