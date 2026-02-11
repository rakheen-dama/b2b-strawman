# ADR-022: Time Aggregation Strategy

**Status**: Accepted

**Context**: The platform needs to compute time summaries at multiple levels: per-project (total billable/non-billable), per-member, per-task, and per-date-range. These summaries are displayed on project dashboards, "My Work" views, and will eventually feed client-facing reports. The question is whether to compute these on the fly via SQL queries or pre-compute them in materialized summary tables.

**Options Considered**:

1. **On-the-fly SQL aggregation** — Compute summaries via `SUM()/GROUP BY` queries at read time. No summary tables, no cached columns.
   - Pros: Zero write-path overhead — no triggers, no update cascades, no stale-data risk. Always returns fresh data. Simpler schema (one table, no denormalization). Works identically for Starter (shared schema + @Filter) and Pro (dedicated schema). Easy to add new aggregation dimensions without schema changes.
   - Cons: Read performance degrades with data volume. Every summary request scans relevant time entries.

2. **Materialized summary table** — A `project_time_summary` table (or per-member, per-task variants) updated on every time entry insert/update/delete via triggers or application-level code.
   - Pros: O(1) reads for summaries. Predictable read latency regardless of data volume.
   - Cons: Write amplification — every `INSERT` into `time_entries` triggers an update to one or more summary rows. Cache invalidation on edits and deletes. Consistency risk — summary can drift if triggers fail or application code has bugs. Additional complexity for shared-schema tenancy (summary rows need `tenant_id`). More migrations, more entities, more tests.

3. **PostgreSQL materialized view** — `CREATE MATERIALIZED VIEW project_time_summary AS SELECT ...`. Refreshed periodically or on demand.
   - Pros: SQL-level abstraction. No application triggers.
   - Cons: `REFRESH MATERIALIZED VIEW` locks the view (unless `CONCURRENTLY`, which requires a unique index). Stale data between refreshes. Neon Postgres supports materialized views, but refresh scheduling adds operational complexity. Not real-time.

4. **Application-level cache (Caffeine)** — Cache summary results in-memory with TTL eviction.
   - Pros: Fast reads after first computation. No schema changes.
   - Cons: Stale data within TTL window. Cache invalidation on writes is complex. Doesn't help across server restarts or multiple instances. Not suitable for data that should always be fresh.

**Decision**: On-the-fly SQL aggregation (Option 1).

**Rationale**: The data volume per project is bounded and small relative to PostgreSQL's aggregation capabilities:

- A busy project: 20 tasks × 50 time entries per task = 1,000 rows.
- An exceptionally large project: 200 tasks × 100 entries = 20,000 rows.
- PostgreSQL aggregates 20,000 rows in single-digit milliseconds with proper indexes.

The relevant indexes ensure fast aggregation:
- `idx_time_entries_task_id` — task-level grouping.
- `idx_time_entries_task_id_billable` — covers the common `SUM WHERE billable` pattern.
- `idx_time_entries_member_id_date` — "My Work" date range queries.

With these indexes, project time summaries are effectively index-only scans for most queries. The overhead of maintaining materialized summaries (write amplification, consistency management, additional testing) is not justified by the marginal read-time benefit.

**Performance guardrails**:
- If a single project exceeds 100,000 time entries, revisit this decision. At that scale, a materialized view or summary table would be warranted.
- Monitor query latency on the aggregation endpoints. If P95 exceeds 100ms, add a Caffeine cache with 30-second TTL as a quick optimization before considering schema changes.

**Consequences**:
- No summary tables or materialized views.
- All aggregation endpoints run SQL queries at request time.
- `TimeEntryRepository` contains JPQL aggregation queries (or native queries for complex GROUP BY).
- Adding new aggregation dimensions (e.g., "time by week", "time by task type") requires only new queries, no schema changes.
- Performance is predictable and measurable — if it degrades, the optimization path is clear (cache → materialized view → summary table).
