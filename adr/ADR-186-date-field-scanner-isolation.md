# ADR-186: Date-Field Scanner Isolation

**Status**: Accepted
**Date**: 2026-03-16
**Phase**: 48 (QA Gap Closure)

## Context

Phase 48 introduces a `FIELD_DATE_APPROACHING` automation trigger powered by a scheduled job (`FieldDateScannerJob`) that scans custom field date values across all tenants daily. When a date field value falls within a configured threshold (e.g., 14, 7, or 1 days from now), the scanner publishes a `FieldDateApproachingEvent` that the automation engine processes.

The scanner must iterate all tenant schemas, query date-type custom fields, calculate proximity, check deduplication, and publish events. The key architectural question is how to handle the multi-tenant iteration: should tenants be scanned sequentially, in parallel, or should the scanning be event-driven (triggered when field values change)?

DocTeams uses schema-per-tenant isolation (ADR-064). Each tenant's data lives in a separate PostgreSQL schema. The scanner must bind `RequestScopes.TENANT_ID` via `ScopedValue.where()` for each tenant to route Hibernate queries to the correct schema. The existing scheduled job pattern (`AutomationScheduler`, `TimeReminderScheduler`) uses sequential per-tenant iteration.

Current scale: < 100 tenants. Expected medium-term scale: < 1,000 tenants.

## Options Considered

### Option 1: Sequential per-tenant scan (daily cron)

A single `@Scheduled` method iterates `OrgSchemaMappingRepository.findAll()`, processes each tenant serially within a `ScopedValue.where(TENANT_ID, schema).run(...)` block. One database connection used at a time per tenant.

- **Pros:**
  - Simple and correct: no concurrency concerns, no thread pool sizing decisions
  - Follows the established pattern used by `AutomationScheduler` and `TimeReminderScheduler`
  - One database connection at a time -- no risk of pool exhaustion
  - Easy to reason about in logs and debugging (sequential tenant processing with clear boundaries)
  - `ScopedValue.where().run()` naturally scopes each tenant's work -- no cleanup needed
  - At < 100 tenants with ~50 date fields each, total scan time is under 10 seconds (each tenant takes ~100ms for the query + calculate + dedup cycle)
  - Transaction isolation is straightforward: one transaction per tenant

- **Cons:**
  - Total scan time grows linearly with tenant count: O(tenants * fields)
  - At 1,000 tenants with 50 fields each, could take ~2 minutes (still acceptable for a daily job)
  - At 10,000+ tenants, serial scanning becomes impractical (but that scale is far beyond current plans)
  - If one tenant's scan fails, subsequent tenants still proceed (but the failed tenant misses its window)

### Option 2: Parallel tenant scan (thread pool)

Use a fixed-size thread pool (e.g., `Executors.newFixedThreadPool(4)`) to scan multiple tenants concurrently. Each thread handles a tenant within its own `ScopedValue` binding and database connection.

- **Pros:**
  - Total scan time divided by parallelism factor: 4 threads = ~4x faster
  - Better utilization of database connection pool during off-peak hours (6 AM)
  - Scales to larger tenant counts without increasing wall-clock time proportionally

- **Cons:**
  - Requires careful thread pool sizing to avoid exhausting the HikariCP connection pool (10 max connections in production)
  - `ScopedValue` bindings are per-carrier-thread -- must ensure each thread binds its own `TENANT_ID` correctly
  - Error handling is more complex: partial failures across threads, thread pool shutdown on error
  - Database connection contention: 4 concurrent connections for scanning + connections for normal API traffic could cause pool starvation during busy periods
  - Over-engineered for current scale -- the sequential approach handles 100 tenants in < 10 seconds
  - Introduces a new concurrency pattern not used elsewhere in the codebase
  - Harder to debug: interleaved logs from multiple tenants

### Option 3: Event-driven on field update

Instead of a scheduled scan, publish `FieldDateApproachingEvent` when a custom field value is created or updated. A listener calculates `daysUntil` at write time and schedules a delayed notification.

- **Pros:**
  - No scheduled job -- events are triggered by user action, so only active fields are processed
  - Near-real-time: alerts are scheduled immediately when a date field is set
  - No multi-tenant iteration -- each event is already tenant-scoped

- **Cons:**
  - Does not handle existing field values: fields set before the feature is deployed are never scanned
  - Does not handle the passage of time: a field set to 30 days out needs alerts at 14, 7, and 1 day marks. The event-driven approach must schedule three future events at write time, and those events must survive server restarts (requires persistent scheduling -- a new infrastructure component).
  - Changing a field value invalidates previously scheduled events -- requires cancellation logic
  - Significantly more complex than a daily scan: scheduling, cancellation, persistence, restart recovery
  - The existing `AutomationScheduler` (delayed action execution) uses database polling, not persistent event scheduling. Adding a second scheduling mechanism would be inconsistent.
  - Does not handle date changes due to external data updates (e.g., SARS moves a deadline)

## Decision

**Option 1 -- Sequential per-tenant scan (daily cron).**

## Rationale

The sequential scan is the right trade-off for current and medium-term scale. At < 100 tenants, the entire scan completes in under 10 seconds -- well within the daily execution window. At 1,000 tenants, it would take approximately 2 minutes, which is still acceptable for a job that runs once daily at 6 AM when system load is minimal.

The sequential approach follows the established pattern in the codebase. Both `AutomationScheduler` (Phase 37) and `TimeReminderScheduler` (Phase 5) iterate tenants sequentially using the same `OrgSchemaMappingRepository.findAll()` + `ScopedValue.where().run()` pattern. Adding a third scheduled job that follows this pattern means developers already know how it works, how to debug it, and how to extend it.

Parallel scanning (Option 2) trades simplicity for speed that is not needed. The HikariCP pool has 10 connections; dedicating 4 to a background scan during business hours could cause connection starvation. During off-peak (6 AM), the parallelism benefit is marginal because the scan already completes in seconds. The complexity cost (thread pool management, connection contention, interleaved logs) is not justified.

Event-driven scanning (Option 3) is architecturally appealing but fundamentally unsuitable for time-proximity alerts. The core problem is that "14 days before a deadline" is a function of time, not of user action. A daily scan naturally checks "is today within the threshold?" without needing to predict future dates at write time, schedule persistent events, or handle cancellations when dates change.

If the platform scales to 10,000+ tenants, the sequential scan can be converted to a parallel scan with bounded concurrency. The `FieldDateScannerJob` interface does not change -- only the internal iteration strategy. This is an additive optimization, not a design change.

## Consequences

- The scanner runs once daily at 06:00 by default. Configurable via `app.automation.field-date-scan-cron`.
- All tenants are scanned sequentially in a single thread. Total scan time is O(tenants * date_fields).
- One database connection is used at a time. No risk of pool exhaustion from the scanner.
- If the scanner crashes mid-run (e.g., out-of-memory, database disconnect), tenants processed before the crash have their alerts recorded. Unprocessed tenants are picked up in the next daily run. The dedup table prevents duplicates on retry.
- At 1,000+ tenants, the team should monitor scan duration and consider bounded parallelism if it exceeds 5 minutes. The refactoring is localized to the iteration loop.
- Alerts fire at most once per threshold per entity/field combination (enforced by `FieldDateNotificationLog` dedup table).
