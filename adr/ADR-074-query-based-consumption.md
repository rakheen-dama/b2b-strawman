# ADR-074: Query-Based Consumption

**Status**: Accepted
**Date**: 2026-02-19

**Context**:

Retainer periods track how many billable hours have been consumed against the customer's hour allocation. When a time entry is created, updated, or deleted for a customer with an active retainer, the period's `consumed_hours` must be updated. The question is whether to maintain this value incrementally (adjust +/- on each change) or recalculate it from source data each time.

The platform already has time entries with `billable` flag, `duration_minutes`, and `date` fields (Phase 5). Time entries link to tasks, which link to projects, which link to customers via `customer_projects`. The retainer period defines a date range (`period_start` inclusive, `period_end` exclusive). Consumption is the sum of all billable time entry durations for the customer's projects within that date range.

Time entries are frequently edited after initial creation — members correct durations, change billable status, backdate entries, or delete erroneous ones. Any consumption tracking mechanism must handle all these mutation patterns correctly.

**Options Considered**:

1. **Query-based recalculation (chosen)** — On every time entry change, re-query all billable time entries for the customer within the period date range and write the sum to `consumed_hours`.
   - Pros: Self-healing — always consistent regardless of mutation type (create, update, delete, backdate); no compensating logic needed for edge cases; simple to implement and reason about; easy to verify correctness (run the query manually); no drift between calculated and actual values.
   - Cons: More expensive per update than incremental adjustment; queries the full period's time entries on every change; slightly higher database load during bulk time entry imports.

2. **Incremental counter** — On create, add duration. On delete, subtract duration. On update, compute delta and adjust.
   - Pros: Minimal database work per change (single UPDATE with arithmetic); no need to query related time entries; O(1) per operation.
   - Cons: Requires compensating logic for every mutation type; backdate across period boundaries requires detecting old period and new period; changing `billable` flag requires detecting whether the entry was previously counted; bulk edits can cause counter drift if any operation fails mid-batch; concurrent modifications can cause race conditions without explicit locking; debugging discrepancies requires manual reconciliation; any bug in delta calculation silently corrupts the counter.

3. **Materialized view** — Create a PostgreSQL materialized view that aggregates time entries by customer and period range.
   - Pros: Database-native aggregation; can be refreshed on demand or on schedule.
   - Cons: Materialized views require explicit refresh (not real-time); `REFRESH MATERIALIZED VIEW` takes a lock; adds DDL complexity to tenant migrations; does not integrate well with JPA entity model; cannot be updated incrementally in standard PostgreSQL.

4. **Hybrid — incremental with periodic reconciliation** — Use incremental counter for speed, with a scheduled job that recalculates from source data to detect drift.
   - Pros: Fast per-operation updates; drift is eventually detected and corrected.
   - Cons: Two code paths to maintain (increment + reconciliation); reconciliation job adds infrastructure; window between drift and correction; consumers see stale data during the window; more complex than either pure approach.

**Decision**: Option 1 — query-based recalculation.

**Rationale**:

The consumption query is inherently well-scoped: one customer, one date range, indexed joins. The query path is `time_entries` JOIN `tasks` (on `task_id`) JOIN `customer_projects` (on `project_id`) WHERE `customer_id = ?` AND `billable = true` AND `date >= ?` AND `date < ?`. With indexes on `customer_projects(customer_id)`, `tasks(project_id)`, and a composite on `time_entries(task_id, billable, date)`, this returns in sub-millisecond time even for thousands of time entries per period.

The query runs on every time entry change event, which is frequent but not high-throughput — a typical firm logs tens to low hundreds of time entries per day across all members. The marginal cost of a single indexed aggregation query is negligible compared to the complexity cost of maintaining an incremental counter with correct compensating logic for every mutation pattern.

The self-healing property is the decisive advantage. In financial contexts, data correctness is non-negotiable. An incremental counter that drifts by even one hour due to a concurrent modification or missed edge case would produce incorrect invoices. Query-based recalculation eliminates this entire class of bugs by construction.

**Consequences**:

- Positive:
  - Consumption is always exactly correct — no drift, no reconciliation needed
  - Simple implementation — one query, one write
  - Handles all mutation types (create, update, delete, backdate) identically
  - Easy to test — the expected value is the result of a deterministic query
  - No race conditions — the query reads committed data

- Negative:
  - Slightly more expensive per update than O(1) incremental adjustment (mitigated by the query being well-indexed and fast)
  - Bulk time entry imports trigger N recalculations (mitigated by debouncing — batch the event and recalculate once after the import completes, or accept N fast queries)
  - Consumption is eventually consistent within the same transaction — if two time entries are created simultaneously, the second recalculation overwrites the first (mitigated by the second calculation being correct since it sees both entries)
