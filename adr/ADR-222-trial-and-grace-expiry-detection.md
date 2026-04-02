# ADR-222: Trial and Grace Expiry Detection

**Status**: Proposed  
**Date**: 2026-04-02  
**Phase**: 57

## Context

The subscription lifecycle includes time-based transitions that happen without user action:
- **Trial expiry:** A TRIALING subscription whose `trial_ends_at` has passed must transition to EXPIRED.
- **Grace period expiry:** A GRACE_PERIOD/EXPIRED/SUSPENDED subscription whose `grace_ends_at` has passed must transition to LOCKED.
- **Pending cancellation end:** A PENDING_CANCELLATION subscription whose `current_period_end` has passed must transition to GRACE_PERIOD.

These transitions must happen reliably — a tenant should not remain in TRIALING indefinitely because no one triggered the check. The question is when and how to detect that these timestamps have been exceeded.

The platform currently has 10+ scheduled jobs using Spring `@Scheduled` (see context inventory Section 10), including `DormancyScheduledJob` (daily cron at 2 AM), `RecurringScheduleExecutor` (daily cron at 2 AM), and `ProposalExpiryProcessor` (hourly). The pattern is well-established.

## Options Considered

### Option 1: Scheduled Job (Daily Poll) (Selected)

A Spring `@Scheduled` job runs daily (e.g., 3 AM to avoid collision with the 2 AM dormancy and recurring schedule jobs). It queries `subscriptions` for all rows past their expiry timestamps and transitions them in batch.

- **Pros:** Simple and proven — matches the existing `DormancyScheduledJob` and `RecurringScheduleExecutor` patterns. Batch processing is efficient (one query per state transition type). Runs independently of user activity. Easy to test (invoke the method directly in integration tests). Logging and error handling are straightforward. Daily granularity is sufficient for business requirements (trials are measured in days, not hours).
- **Cons:** Up to 24-hour delay between actual expiry and state transition. A trial that expires at 2 AM won't be detected until the 3 AM job runs the next day (if it just missed the window). If the job fails, transitions are delayed until the next successful run.

### Option 2: On-Access Lazy Check

Check subscription status lazily on each request. The `SubscriptionGuardFilter` (ADR-221) already resolves subscription status per request — extend it to also check timestamps and trigger transitions inline.

- **Pros:** Zero delay — transitions happen the instant an expired tenant makes a request. No scheduled job infrastructure needed. Consistent with the "check-then-act" pattern already in the filter.
- **Cons:** Transitions only happen when the tenant accesses the platform. A tenant that never logs in after trial expiry would remain in TRIALING state indefinitely — this matters for reporting, billing audits, and admin dashboards. Each request performs a timestamp check and potentially a database write (state transition) — adding write operations to the request hot path. Concurrent requests from the same tenant could race on the transition, requiring pessimistic locking or idempotency guards. The filter's responsibility expands from "check access" to "check access + mutate state" — violates single responsibility.

### Option 3: Database-Level Job (pg_cron)

Use PostgreSQL's `pg_cron` extension to run a SQL function directly in the database on a schedule.

- **Pros:** No application code needed. Runs even if the application is down. Database-native, no ORM overhead.
- **Cons:** `pg_cron` is not available on all PostgreSQL hosting providers (Neon supports it, but it's not standard). Mixes business logic into the database layer. No application-level logging, audit events, or notification triggers — the transition happens silently in SQL. Cannot easily trigger side effects (email notifications to org owners about trial expiry). Harder to test than a Spring `@Scheduled` method. Violates the project's convention of keeping business logic in the application layer.

### Option 4: Hybrid (Lazy Check + Scheduled Job)

Both: the scheduled job runs daily for batch cleanup, and the filter also performs a lazy check for immediate transitions on access.

- **Pros:** Best of both worlds — immediate transitions for active tenants, guaranteed cleanup for inactive ones. No tenant remains in a stale state.
- **Cons:** Dual transition paths increase complexity. Both paths must be idempotent (they are, since transitions are one-way and checked by current state). More code to maintain. The lazy path adds write operations to the request hot path (same concern as Option 2). Overengineered for the actual requirement — daily granularity is sufficient for trial/grace transitions measured in days/months.

## Decision

**Option 1 — Scheduled job (daily poll).**

## Rationale

1. **Daily granularity is sufficient.** Trials are 14 days. Grace periods are 60 days. A 24-hour detection delay is imperceptible at these timescales. No business scenario requires sub-day precision for subscription state transitions.

2. **Proven pattern.** The codebase has 10+ scheduled jobs using `@Scheduled`. The `DormancyScheduledJob` runs at 2 AM daily and performs the exact same pattern: query for rows past a threshold, transition their state, log the results. Adding another daily job at 3 AM is zero incremental complexity.

3. **Clean separation of concerns.** The `SubscriptionGuardFilter` reads subscription status from a cache and makes access decisions. It should not also mutate subscription state. Keeping the mutation in a scheduled job means the filter remains a pure read-path component.

4. **Side effects are straightforward.** When the scheduled job transitions a subscription, it can also trigger notifications (e.g., "Your trial has expired — subscribe to continue"), create audit events, and update the status cache. These side effects are natural in a service method but awkward in a filter.

5. **Testability.** The scheduled job is a normal Spring bean method that can be called directly in integration tests. No need to simulate HTTP requests or manipulate clocks within a filter chain.

## Consequences

- **Positive:** Simple, proven, testable. One query per transition type per day. Side effects (notifications, audit events) are natural. No impact on request hot path. Matches existing codebase patterns.
- **Negative:** Up to 24-hour delay between actual expiry and detected transition. During this window, the `SubscriptionGuardFilter`'s cached status may be stale (showing TRIALING when the trial has technically expired). This is acceptable because: (a) the cache TTL is 5 minutes, so the filter will re-read from DB within 5 minutes — the DB still shows TRIALING until the job runs, but the UX impact is minimal (the user sees "Trial ends today" rather than "Trial expired"), and (b) no financial exposure — the user is not being charged during this window.
- **Mitigations:** If sub-hour precision is needed in the future (e.g., for a metered billing model), switch to Option 4 (hybrid) by adding a lazy check in the filter alongside the daily job. The job can also be run more frequently (hourly) by changing the cron expression — daily was chosen for simplicity, not as a hard constraint.
