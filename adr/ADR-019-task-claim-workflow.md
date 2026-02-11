# ADR-019: Task Entity and Claim Workflow

**Status**: Accepted

**Context**: Projects need a lightweight task management capability. Tasks are scoped to a Project (and therefore to a tenant). The key feature is a "claim" pattern where tasks can be created without an assignee and any eligible project member can claim them.

**Options Considered**:

1. **Workflow engine (e.g., Camunda, Temporal)** — Model tasks as workflow steps with formal state machines, escalation rules, and SLA tracking.
   - Pros: Rich workflow capabilities; audit trail; timer-based escalation.
   - Cons: Massive infrastructure overhead for what is fundamentally a TODO list with assignment. Requires a new runtime dependency. Overkill for the current requirements.

2. **Separate task and assignment tables** — `tasks` table for task metadata, `task_assignments` table for assignment history (who claimed/released/was assigned, when).
   - Pros: Full assignment history; supports multiple assignees or handoffs.
   - Cons: Extra join for every task query. Assignment history is a future concern — current requirement is single assignee with claim/release.

3. **Single task entity with assignee column** — `tasks` table with `assignee_id` (nullable FK to members). Claim = set assignee_id. Release = clear assignee_id. Status tracks the task lifecycle.
   - Pros: Simplest possible model. Single table, single query. Claim is a conditional UPDATE. Status is explicit (OPEN, IN_PROGRESS, DONE, CANCELLED). Easy to extend later (add assignment history table if needed).
   - Cons: No assignment history (only current assignee). Single assignee only.

**Decision**: Single task entity with assignee column (Option 3).

**Rationale**: The requirements describe a straightforward claim pattern — not a workflow engine. A single `tasks` table with `assignee_id` captures the full lifecycle: task created (OPEN, unassigned) → claimed (IN_PROGRESS, assigned) → completed (DONE) or cancelled (CANCELLED). The claim operation is a conditional UPDATE with optimistic locking (`WHERE assignee_id IS NULL AND id = ?`) to prevent race conditions. If assignment history becomes important later, a `task_activity_log` table can be added without changing the core model.

**Task Lifecycle**:

```
                  claim (set assignee)
     ┌──────────────────────────────────┐
     │                                  ▼
   OPEN ──────────────────────────► IN_PROGRESS ──────► DONE
     │                                  │
     │                                  │ release (clear assignee)
     │                                  │
     │                                  ▼
     │                                OPEN (back to pool)
     │
     └──────────────────────────────► CANCELLED
```

**Claim Semantics**:
- `POST /api/tasks/{id}/claim`: Sets `assignee_id = currentMember.id`, status = `IN_PROGRESS`. Fails with 409 Conflict if already assigned.
- `POST /api/tasks/{id}/release`: Clears `assignee_id`, status = `OPEN`. Only the current assignee or project lead can release.
- Direct assignment: `PUT /api/tasks/{id}` with `assigneeId` in body — project leads can assign tasks to specific members.

**Race Condition Handling**: The claim operation uses Hibernate's `@Version` for optimistic locking. Two members claiming simultaneously: one succeeds, the other gets `OptimisticLockException` → 409 Conflict response.

**Schema**:

```sql
CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    type VARCHAR(100),
    assignee_id UUID REFERENCES members(id) ON DELETE SET NULL,
    created_by UUID NOT NULL REFERENCES members(id),
    due_date DATE,
    tenant_id VARCHAR(255),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Consequences**:
- Single `tasks` table in tenant schema with `tenant_id` for shared-schema support.
- Hibernate `@Filter` and RLS applied identically to other tenant entities.
- `@Version` column enables optimistic locking for claim race conditions.
- `assignee_id ON DELETE SET NULL` — if a member is removed, their tasks become unassigned (back to pool).
- `priority` (LOW, MEDIUM, HIGH) and `type` (free-form varchar) support future workflow extensions.
- `due_date` is optional — supports deadline tracking without enforcing it.
- No notification system in this phase — staff check the task list manually.
