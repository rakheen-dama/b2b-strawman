# ADR-070: Pre-Calculated next_execution_date

**Status**: Accepted
**Date**: 2026-02-19

**Context**:

Recurring schedules need to determine when the next project should be created. The daily scheduler queries all active schedules to find those that are "due" — i.e., whose next execution date is today or in the past. The system must decide whether this date is stored as a pre-calculated column on the `RecurringSchedule` entity (materialized) or computed on-the-fly from the schedule's `start_date`, `frequency`, `lead_time_days`, and execution history (derived).

The scheduler runs across all tenant schemas, potentially checking hundreds of schedules per run. The query pattern and its performance characteristics matter, but simplicity and correctness matter more — the scheduler runs once daily, not in a hot loop. The more important consideration is debuggability: when an admin asks "when will this schedule next fire?", the answer should be immediately visible, not require reverse-engineering the period calculation logic.

**Options Considered**:

1. **Pre-calculated `next_execution_date` column (chosen)** — Store the next execution date on the schedule row. Update it after each execution or when the schedule is resumed.
   - Pros: Trivially simple scheduler query (`WHERE next_execution_date <= :today AND status = 'ACTIVE'`); immediately visible in the UI and database — no calculation needed to answer "when next?"; single source of truth — the column IS the schedule's next fire date; easy to debug — `SELECT * FROM recurring_schedules WHERE next_execution_date <= CURRENT_DATE` shows exactly which schedules are due.
   - Cons: Must keep the column in sync — updated on execution, resume, and schedule creation; stale if the update logic has a bug (mitigated by idempotency on `ScheduleExecution`); one extra column per schedule row.

2. **On-the-fly calculation** — Compute the next execution date at query time from `start_date`, `frequency`, `lead_time_days`, and the last `ScheduleExecution` record.
   - Pros: No sync issues — always derived from source data; no extra column to maintain.
   - Cons: Complex SQL or application-level date arithmetic on every scheduler run; harder to debug — "when will this fire?" requires running the calculation; query cannot use a simple index scan — must join with `schedule_executions` and perform date arithmetic; the calculation logic must handle all frequency types (weekly through annually) with month-end edge cases; non-obvious behavior when inspecting the database directly.

3. **Hybrid — pre-calculated with periodic reconciliation** — Store the column but run a nightly reconciliation job that recalculates all `next_execution_date` values from source data.
   - Pros: Self-healing if the column gets out of sync.
   - Cons: Additional complexity (a scheduler to fix the scheduler); if reconciliation disagrees with the stored value, which is correct?; unnecessary — the update points are well-defined and few (execution, resume, creation).

**Decision**: Option 1 — pre-calculated `next_execution_date` column.

**Rationale**:

The pre-calculated column makes the scheduler's hot path — finding due schedules — a simple indexed column comparison. More importantly, it makes the schedule's state immediately legible. An admin viewing the schedule list sees the exact next execution date without needing to understand period calculation rules. A database query for debugging is a simple `SELECT` rather than a date arithmetic expression joined across tables.

The column is updated at exactly three points: (1) when a schedule is created (initial calculation from `start_date` and `lead_time_days`), (2) after each successful execution (advance to the next period), and (3) when a paused schedule is resumed (recalculate from the current date forward, skipping past periods). These are low-frequency, well-defined update points with clear ownership in the service layer. The idempotency constraint on `ScheduleExecution` (`schedule_id, period_start` unique) provides a safety net — even if `next_execution_date` were somehow wrong, the system cannot create duplicate projects for the same period.

**Consequences**:

- Positive:
  - Scheduler query is `WHERE next_execution_date <= :today AND status = 'ACTIVE'` — index-friendly, no joins
  - Schedule state is immediately visible in the UI and database
  - No complex date arithmetic at query time
  - Debuggable — "why didn't this schedule fire?" is answerable by checking the column value

- Negative:
  - Column must be updated at three code paths (creation, execution, resume) — if any path misses the update, the schedule stalls (mitigated by keeping the update logic in a single service method `calculateNextExecutionDate()`)
  - Column could theoretically become stale if the application crashes between creating a project and updating the schedule (mitigated by wrapping both operations in the same transaction)
  - One additional column per schedule row (negligible storage — a DATE column is 4 bytes)
