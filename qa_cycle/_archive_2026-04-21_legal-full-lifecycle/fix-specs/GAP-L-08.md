# Fix Spec: GAP-L-08 — Retention clock field on projects (ADR-249 minimal slice)

## Problem

From `status.md` Gap Tracker (Day 60+ / ADR-249, MED):

> No retention-clock field on `projects` (ADR-249). `projects` table has `completed_at`,
> `archived_at` but no named `retention_clock_started_at` or `retention_period_days`.
> `retention_policies` table exists separately — not inspected whether it's linked to project
> closure. Depends on GAP-L-07.

ADR-249 requires the retention clock to start on **matter closure** (a distinct `CLOSED` state
introduced by ADR-248 / Phase 67). GAP-L-07 (the CLOSED state + 4 closure gates) is NOT in this
fix batch, so this spec covers a **minimal viable slice** that unblocks the demo's data-model
story and is trivially rewire-able once CLOSED lands.

## Root Cause (validated, not hypothesis)

Validated by reading:

1. **Projects table has no retention field.**
   `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java:54-64` defines only
   `completedAt`, `completedBy`, `archivedAt`, `archivedBy`. No `retention_clock_started_at`
   column exists in migrations (grep `retention_clock` across migrations returns zero hits).

2. **`retention_policies` table is a RULES table, not a per-project ledger.**
   `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retention/RetentionPolicy.java` models a
   per-org rule (`recordType`, `retentionDays`, `triggerEvent`, `action`) — NOT a per-entity
   retention-clock instance. ADR-249 references inserting per-matter `RetentionPolicy` rows on
   closure, but that's the Phase 67 scope (depends on CLOSED state + `MatterClosedEvent`). Not
   attempted here.

3. **`project.complete(...)` is the closest "this matter is done" signal we currently have.**
   `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java:201-207` — transitions
   ACTIVE → COMPLETED and stamps `completedAt`. Called from
   `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java:416-449`
   (`completeProject`). This is the transition the minimal slice anchors to. When ADR-248's
   CLOSED state lands (GAP-L-07), the wiring moves from `complete()` to the new `close()` path
   and the column stays put.

4. **Next free V-number**: `V98__member_default_rate_unique_indexes.sql` is the tail of
   `backend/src/main/resources/db/migration/tenant/`. Next is **V99**.

## Fix

Three incremental edits. All tenant-scoped; no global-schema change.

### 1. New migration `V99__add_retention_clock_to_projects.sql`

Create `backend/src/main/resources/db/migration/tenant/V99__add_retention_clock_to_projects.sql`:

```sql
-- V99: Add retention-clock field to projects (minimal slice of ADR-249).
--
-- Purpose: record the moment the retention clock starts for a project. Per ADR-249 the
-- canonical anchor is Matter Closure (ADR-248 CLOSED state — Phase 67). Until CLOSED lands,
-- this slice anchors to the existing ACTIVE -> COMPLETED transition so legal tenants have a
-- retention-clock timestamp in place. When Phase 67 wires up a distinct CLOSED state, the
-- service hook moves from `project.complete(...)` to `project.close(...)`; the column does
-- not need to change.
--
-- Column semantics:
--   retention_clock_started_at TIMESTAMP (nullable) — set exactly once, on the first transition
--   that triggers retention. Never overwritten (re-completion keeps the original timestamp so
--   the retention sweep evaluates against the earliest trigger).
--
-- Out of scope for this slice (tracked as followups in the Phase 67 work):
--   - retention_period_days (per-project override; currently derived from per-org OrgSettings)
--   - UI exposure of the timestamp on the Matter detail page
--   - purge / anonymisation sweep consuming this field
--   - soft-cancel on matter reopen (ADR-249 §Consequences bullet 2)

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS retention_clock_started_at TIMESTAMP;

-- Optional: index to support future retention-sweep queries. Partial index keeps it tiny.
CREATE INDEX IF NOT EXISTS ix_projects_retention_clock_started_at
    ON projects (retention_clock_started_at)
    WHERE retention_clock_started_at IS NOT NULL;
```

### 2. Entity field on `Project.java`

In `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java`, after line 64 (after the `archivedBy` field):

```java
@Column(name = "retention_clock_started_at")
private Instant retentionClockStartedAt;
```

Add the getter after the `getArchivedBy()` getter (around line 153):

```java
public Instant getRetentionClockStartedAt() {
  return retentionClockStartedAt;
}
```

Modify the `complete(UUID memberId)` method (lines 201-207) to set the clock, preserving the
never-overwrite semantics:

```java
/** Marks project COMPLETED. Records completedAt, completedBy, and (once) retentionClockStartedAt. */
public void complete(UUID memberId) {
  requireTransition(ProjectStatus.COMPLETED, "complete");
  this.status = ProjectStatus.COMPLETED;
  this.completedAt = Instant.now();
  this.completedBy = memberId;
  // Retention clock starts on the FIRST transition to COMPLETED and is never re-stamped
  // by subsequent re-completions. When Phase 67 introduces CLOSED, move this assignment to
  // `close(...)` — ADR-249.
  if (this.retentionClockStartedAt == null) {
    this.retentionClockStartedAt = this.completedAt;
  }
  this.updatedAt = Instant.now();
}
```

**Do NOT change `reopen()`** in this slice. Reopen currently nulls `completedAt`; in Phase 67's
ADR-249 §Consequences bullet 3 the retention row is soft-cancelled rather than deleted, and the
implementation lives in the (future) `RetentionPolicy` ledger — not in this column. Leaving
`retentionClockStartedAt` un-nulled on reopen is intentional and correct: re-completing a
reopened matter keeps the earliest retention anchor, matching the ADR's "inserts a fresh
retention row" behaviour when the new closure fires.

### 3. Response DTO (minimal exposure, read-only)

If `ProjectResponse` (the DTO returned by the project controller) already surfaces `completedAt`
and `archivedAt`, add `retentionClockStartedAt` alongside. This is a pure read-model change.

Grep check: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectResponse.java`
should contain `completedAt` fields. Mirror the pattern. If the DTO is a record, just add the
new field in the same order as the entity getter.

If the DTO doesn't currently surface lifecycle timestamps, skip this step — the column being
present in the DB and entity is the slice's required outcome. Follow-up work picks it up in UI.

## Scope

- **Backend**: YES (entity, service no-op aside from `complete()`, DTO if applicable).
- **Migration**: YES (V99).
- **Frontend / Gateway / Keycloak theme / Seed**: NO.
- **Config**: NO.

Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java` (one field, one getter, 3 lines in `complete()`)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectResponse.java` (conditional, one field)

Files to create:
- `backend/src/main/resources/db/migration/tenant/V99__add_retention_clock_to_projects.sql`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/project/RetentionClockIntegrationTest.java` (new test)

Migration needed: **YES (V99)**.
KC restart required: NO.
Backend restart required: YES (schema change + entity field + service code — per project CLAUDE.md).
Frontend restart required: NO.

## Verification

### Integration test

Create `backend/src/test/java/io/b2mash/b2b/b2bstrawman/project/RetentionClockIntegrationTest.java`
following the pattern in `ProjectLifecycleIntegrationTest.java`:

1. **`retentionClockStartedAt_setsOnFirstCompletion`**: Create an ACTIVE project; assert
   `retentionClockStartedAt` is null. POST `/api/projects/{id}/complete` (with
   `acknowledgeUnbilledTime=true` to pass Guardrail 2). Assert the column is now non-null and
   equals the project's `completedAt` to within ms precision.
2. **`retentionClockStartedAt_notOverwrittenOnReComplete`**: Complete the project; read the
   column (`t0`). Reopen via `/api/projects/{id}/reopen`. Re-complete. Assert
   `retentionClockStartedAt == t0` (unchanged by the second complete).
3. **`retentionClockStartedAt_nullForActiveProjects`**: Create an ACTIVE project; do not
   complete. Assert `retentionClockStartedAt` remains null in the entity row (read via
   `projectRepository.findById(id).orElseThrow().getRetentionClockStartedAt()`).

Use `TestEntityHelper` + `TestJwtFactory.ownerJwt` and
`@Import(TestcontainersConfiguration.class)` per `backend/CLAUDE.md`.

### Manual reproduction in <5 min

1. On the running stack, create a matter with no unbilled time, no open tasks.
2. `POST /api/projects/{id}/complete` (with `acknowledgeUnbilledTime=true` if needed).
3. `docker exec -it b2b-postgres psql -U postgres -d app -c "SELECT id, status, completed_at, retention_clock_started_at FROM tenant_<your_tenant>.projects WHERE id='<your_project_id>';"` — expect non-null `retention_clock_started_at`.

## Estimated Effort

**M (~60-75 min)** — migration + entity field + 3 lines in `complete()` + 3 integration tests.
Backend rebuild + restart adds ~1 min each; V99 runs automatically on restart for all existing
tenant schemas.

## Out of Scope / Follow-up

Document explicitly in the spec so Dev does NOT build these — they are part of Phase 67 /
GAP-L-07 / GAP-L-08-FollowUp:

- **Per-project `retention_period_days` column.** ADR-249 defers to
  `OrgSettings.legalMatterRetentionYears` (default 5 for `legal-za`). Not added here; Phase 67
  extends `OrgSettings` and computes the effective period at sweep time.
- **UI exposure on Matter detail page.** No frontend work in this slice. Phase 67's "Closure
  audit" panel surfaces the timestamp alongside `MatterClosureLog`.
- **Purge / anonymisation sweep consuming the column.** The existing Phase 50 retention sweep
  does not yet read this column. Sweep consumption is Phase 67 scope.
- **Soft-cancel on reopen.** ADR-249 §Consequences bullet 3 requires reopen to soft-cancel the
  retention row (not the clock timestamp on the project). That model lives on the
  `RetentionPolicy` ledger rows, which this slice does not create. Reopen handling is Phase 67
  scope.
- **Move hook from `complete()` to `close()` when CLOSED state ships (GAP-L-07).** The column
  stays; only the service line that stamps it relocates.
- **`MatterClosedEvent` domain event + handler.** Phase 67 scope.
- **Retroactive backfill of existing COMPLETED projects.** Out of scope — the QA scenario
  re-runs on a fresh tenant; no legacy data of concern.

## Notes

- **Design decision: anchor to COMPLETED, not ARCHIVED.** ADR-249 explicitly rejects ARCHIVED
  (Options Considered §1) because archival is a dashboard-visibility choice, not a compliance
  event. COMPLETED is the nearest available "work done" signal today — closer in intent to the
  eventual CLOSED than ARCHIVED is. When CLOSED lands, this wiring moves cleanly. ADR-249
  Option 3 considered COMPLETED and rejected it for the final model — we acknowledge this slice
  is an **interim anchor** pending CLOSED, and the ADR Option 3 critique (multiple
  completions restart the clock) is neutralised here by the `if (retentionClockStartedAt == null)`
  guard.
- **Never-overwrite guard matters.** Without it, reopen → re-complete would reset the retention
  clock, defeating the whole purpose. The guard at `Project.complete(...)` is therefore the
  load-bearing correctness invariant of this slice.
- **Column name `retention_clock_started_at`** (snake_case per existing table convention,
  `completed_at` / `archived_at`); Java field `retentionClockStartedAt`.
- **No timezone stored** — the existing columns `completed_at`, `archived_at` use
  `TIMESTAMP` (not `TIMESTAMP WITH TIME ZONE`); we mirror that for consistency. Instant is
  serialized as UTC by Hibernate.
- **Rejected alternative — use existing `completed_at`** as the retention clock. Rejected
  because it's a different semantic: `completed_at` is reset to null on reopen; the retention
  clock must not be. Distinct column required.
