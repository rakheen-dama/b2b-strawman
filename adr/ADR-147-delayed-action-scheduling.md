# ADR-147: Delayed Action Scheduling

**Status**: Accepted
**Date**: 2026-03-06
**Phase**: 37 (Workflow Automations v1)

## Context

Automation actions can be delayed — "wait 3 days then send a reminder," "wait 1 hour then create the follow-up task." When a rule fires and an action has a `delayDuration`, the system must schedule the action for future execution. The scheduling mechanism must be reliable (actions must not be lost), observable (admins can see pending actions), and cancelable (if the rule is disabled or deleted before execution).

The platform already has a proven pattern for scheduled work: `TimeReminderScheduler` and `RequestReminderScheduler` both use `@Scheduled` with `fixedRate` polling, iterate all tenants via `OrgSchemaMappingRepository`, bind `ScopedValue` per tenant, and process within `TransactionTemplate`. This pattern handles per-tenant isolation, error isolation (one tenant failure does not affect others), and observability (log messages per tenant).

## Options Considered

1. **Database polling (15-min interval)** — Delayed actions are written as `ActionExecution` records with `status = SCHEDULED` and `scheduledFor` timestamp. A `@Scheduled` component polls every 15 minutes, finds due records, and executes them.
   - Pros:
     - Reuses the proven `TimeReminderScheduler` pattern — per-tenant iteration, ScopedValue binding, error isolation
     - No new infrastructure — uses the existing PostgreSQL database
     - Scheduled actions are visible in the database (queryable, cancelable, auditable)
     - Simple implementation — one scheduled method, one query
     - Works correctly in multi-instance deployments with `@SchedulerLock` (ShedLock) or similar
     - Cancelation is trivial — update status to CANCELLED
   - Cons:
     - 15-minute granularity — actions scheduled for "30 minutes from now" might execute anywhere from 30 to 45 minutes later
     - Polling adds a small database query every 15 minutes per tenant (negligible at target scale)
     - Not suitable for sub-minute precision (not needed for business process automation)

2. **Message queue (RabbitMQ or SQS)** — Delayed actions are published to a message queue with a delay/TTL. A consumer picks them up when the delay expires.
   - Pros:
     - Near-exact timing — action executes close to the scheduled time
     - Decoupled execution — queue consumer can be scaled independently
     - Built-in retry semantics
   - Cons:
     - New infrastructure dependency — RabbitMQ or SQS must be provisioned, configured, and maintained
     - Message queue state is not easily queryable — admins cannot browse pending actions
     - Cancelation is difficult — cannot easily remove a message from a queue
     - Multi-tenant isolation requires either per-tenant queues (operationally complex) or tenant routing in messages
     - Over-engineers the requirement — business process delays are measured in hours/days, not seconds

3. **Quartz scheduler** — Use the Quartz job scheduling library with a JDBC job store. Each delayed action becomes a Quartz trigger.
   - Pros:
     - Precise scheduling — sub-second accuracy
     - Built-in persistence via JDBC store
     - Supports complex schedules (cron expressions, calendars)
   - Cons:
     - Heavy dependency — Quartz adds 11 database tables and significant configuration
     - Complex API for a simple use case (one-shot delayed execution)
     - Quartz tables are in a single schema — conflicts with per-tenant schema isolation
     - Overkill — no need for cron expressions or calendar-based scheduling

4. **Application-level ScheduledExecutorService** — Use Java's `ScheduledExecutorService` to schedule in-memory futures for each delayed action.
   - Pros:
     - Precise timing
     - No database overhead
     - Simple API
   - Cons:
     - Not durable — scheduled actions are lost on application restart
     - Not visible — pending actions exist only in JVM memory
     - Does not survive deployments or instance scaling
     - Fundamentally unsuitable for actions delayed by hours or days

## Decision

Option 1 — Database polling with 15-minute interval.

## Rationale

Database polling is the right mechanism for business process automation delays. The delays in this domain are measured in hours and days — "wait 7 days then remind," "wait 1 hour then create follow-up." A 15-minute polling granularity means a 7-day delay might execute at 7 days + 0-15 minutes. This is indistinguishable from exact timing for all practical purposes.

The implementation reuses the proven `TimeReminderScheduler` pattern, which has been running reliably since Phase 5. The per-tenant iteration with `ScopedValue` binding, error isolation via per-tenant try-catch, and `TransactionTemplate` wrapping are all established patterns. The `AutomationScheduler` is structurally identical to `TimeReminderScheduler` — the only difference is the query (find due `ActionExecution` records instead of members without time entries).

Scheduled actions stored in the database are first-class data: they appear in the execution log, admins can see pending actions, and cancelation is a simple status update. This observability is critical for an automation system — admins need to know what is scheduled and be able to cancel it.

Adding a message queue (Option 2) or Quartz (Option 3) introduces significant infrastructure complexity for no meaningful benefit at target firm sizes. The polling query (`WHERE status = 'SCHEDULED' AND scheduled_for <= now()`) is indexed and fast — a partial index on `status = 'SCHEDULED'` ensures the query touches only pending records.

## Consequences

- `ActionExecution` records with `status = SCHEDULED` and `scheduledFor` serve as the schedule store
- `AutomationScheduler` polls every 15 minutes using `@Scheduled(fixedRate = 900_000)`
- Delayed actions have up to 15-minute execution jitter (acceptable for business process delays)
- Scheduled actions are visible in the execution log and cancelable by disabling/deleting the rule
- No new infrastructure dependencies
- Pattern is consistent with `TimeReminderScheduler` and `RequestReminderScheduler`
- Partial index `WHERE status = 'SCHEDULED'` on `action_executions` keeps the scheduler query fast as the table grows
