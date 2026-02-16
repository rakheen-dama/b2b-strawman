# ADR-065: Hour Bank Computation -- Query-Time vs Stored

**Status**: Accepted

**Context**: Hour bank retainers track how many hours a customer has used against their allocated pool in the current billing period. The system needs to display real-time usage (how many hours have been consumed, how many remain, whether overage has occurred) and also freeze this value when a period closes for invoicing purposes.

The hour bank usage is derived from `TimeEntry` records: specifically, billable time entries on projects linked to the retainer's customer, within the period date range, that have not already been billed to another invoice. The question is whether to compute this value dynamically at query time or maintain it as a stored/cached value.

**Options Considered**:

1. **Dynamic query-time computation for OPEN periods, frozen on close** -- When the current period's hour bank is requested, execute a SQL query that sums `time_entries.duration_minutes` with the appropriate filters. When the period closes (via the billing job or manual trigger), execute the same query one final time and store the result in `used_hours`. After close, the stored value is authoritative.
   - Pros: Always accurate for OPEN periods -- no stale data. No synchronization complexity. No triggers or event listeners needed to update a cached value when time entries change. Frozen value on close is immutable, providing a stable basis for invoicing. Simple to reason about.
   - Cons: Query cost on every request for the current period. If the customer has many time entries, the query could be slow (mitigated by indexes on `time_entries(task_id, billable, date, invoice_id)`).

2. **Stored/cached value updated by triggers or event listeners** -- Maintain `used_hours` in real-time by incrementing/decrementing it whenever a time entry is created, updated, or deleted. Use either a database trigger or an application-level event listener.
   - Pros: Read performance is O(1) -- no computation needed. Period close is trivial (value already correct).
   - Cons: Synchronization complexity is high. Every time entry mutation (create, update duration, update billable flag, delete, change task, change project) must correctly update the cached value. Race conditions under concurrent edits. Drift risk: if any code path misses the update, the cached value becomes wrong and invoices are incorrect. Database triggers couple the schema to business logic. Application-level listeners require careful handling of transaction boundaries.

3. **Materialized view** -- Create a PostgreSQL materialized view that aggregates time entry data per customer per period.
   - Pros: Declarative. Refresh is explicit.
   - Cons: Materialized views are not real-time (need explicit refresh). `REFRESH MATERIALIZED VIEW` is a heavyweight operation. Not compatible with schema-per-tenant multitenancy (would need one view per tenant schema). Hibernate does not natively support materialized views as entities.

**Decision**: Option 1 -- Dynamic query-time computation for OPEN periods, frozen on close.

**Rationale**: Accuracy is paramount for financial data. A dynamic query ensures that the hour bank always reflects the current state of time entries, without any risk of drift or synchronization bugs. The query is straightforward (a SUM with JOINs and filters) and will be fast given that time entries are indexed by `task_id`, `date`, and `billable`.

The trade-off is a SQL query on every request for the current period's usage. In practice, this query scans at most one billing period's worth of time entries for one customer -- a few hundred rows at most. With proper indexes, this is sub-millisecond. The dashboard and list views can show the cached `used_hours` from the entity (which is zero for OPEN periods or the frozen value for closed periods) and only the detail view needs the dynamic computation.

The frozen-on-close approach ensures invoice accuracy: once a period closes, the `used_hours`, `overage_hours`, and `allocated_hours` values are immutable and serve as the source of truth for the generated invoice. No subsequent time entry edits can alter a closed period's financials.

**How double-counting is avoided**: The dynamic query includes the filter `invoice_id IS NULL` on time entries. If an admin manually invoices some time entries from a retainer-associated project (via the standard Phase 10 unbilled-time flow), those entries will have their `invoice_id` set and will be excluded from the hour bank computation. This provides a clean delineation between retainer billing (agreement-level) and ad-hoc billing (time-entry-level).

**Consequences**:
- The `GET /api/retainers/{id}/periods/current` endpoint executes a SQL query to compute `usedHours`. The response includes `usedHours`, `remainingHours`, `overageHours`, and `utilizationPercent` -- all computed on the fly.
- `RetainerPeriod.used_hours` in the database is 0 for OPEN periods (or could be periodically updated by the billing job for approximation). The authoritative value for OPEN periods comes from the dynamic query.
- When the billing job or manual trigger closes a period, it executes the dynamic query one final time, stores the result in `used_hours`, and computes `overage_hours`. These values are then immutable.
- List views (retainer list, customer retainer tab) can show a "last computed" usage percentage for performance, with a note that the detail view has real-time data.
- Indexes required: `time_entries(task_id, billable, date, invoice_id)` should already exist or be added for query performance.
