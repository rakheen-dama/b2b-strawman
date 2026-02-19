# ADR-071: Daily Batch Scheduler

**Status**: Accepted
**Date**: 2026-02-19

**Context**:

Recurring schedules automate project creation based on time-based frequencies (weekly, monthly, quarterly, etc.). The system needs an execution mechanism to check which schedules are due and create the corresponding projects. The choices range from a simple scheduled job within the application to external distributed scheduling infrastructure.

The platform currently has one `@Scheduled` method (`MagicLinkCleanupService.cleanupExpiredTokens()` — hourly `fixedRate`). The deployment model is ECS/Fargate with potentially multiple application instances. Recurring project creation is inherently a low-frequency, date-granular operation — firms think in periods (this month, this quarter), not in minutes or hours. The scheduling precision required is "the right day", not "the right second."

**Options Considered**:

1. **Daily `@Scheduled` cron job (chosen)** — A Spring `@Scheduled(cron = "0 0 2 * * *")` method that runs at 02:00 UTC daily. Iterates all tenant schemas, finds due schedules, creates projects.
   - Pros: Zero additional infrastructure — uses Spring's built-in scheduler; simple mental model — "it runs once a day and creates projects that are due"; idempotent by design — `ScheduleExecution` unique constraint prevents duplicates if run twice; easy to test — call the method directly in integration tests; proven pattern in the codebase (similar to `MagicLinkCleanupService`); observable — each execution is logged and tracked in `ScheduleExecution`.
   - Cons: Maximum 24-hour delay if the scheduler misses a run (deployment, crash); no retry on individual failure within the same day (catches up next day); all tenants processed sequentially (acceptable at current scale); requires leader election or single-instance guarantee if running multiple app instances.

2. **Event-driven with message queue** — Publish schedule-due events to a message queue (SQS/RabbitMQ). A consumer processes each event independently.
   - Pros: Retry semantics built into the queue; parallel processing across tenants; decoupled from the main application lifecycle.
   - Cons: New infrastructure dependency (SQS or RabbitMQ); who publishes the events? Still needs a scheduler to check due dates; adds operational complexity (dead letter queues, consumer scaling, message format versioning); overkill for daily, date-granular operations; no existing message queue in the stack.

3. **External scheduler (Quartz, AWS EventBridge)** — Use a dedicated scheduling library or cloud service.
   - Pros: Battle-tested scheduling with cron expressions, misfire handling, clustering; AWS EventBridge integrates natively with ECS.
   - Cons: Quartz adds a complex dependency (database tables, cluster coordination, classloader issues with Spring Boot 4); EventBridge adds AWS coupling and requires Lambda or ECS task definitions; both are overkill for a single daily job; Quartz has known compatibility issues with newer Java versions.

4. **Per-schedule timers** — Create an individual scheduled task for each `RecurringSchedule` at its specific `next_execution_date`.
   - Pros: Precise timing — each schedule fires exactly when due.
   - Cons: Dynamic schedule management (add/remove/pause timers at runtime); timers lost on application restart; complex state management; does not survive deployments; memory overhead scales with schedule count.

**Decision**: Option 1 — daily `@Scheduled` cron job.

**Rationale**:

The recurring project creation domain is fundamentally daily-granular. A firm's monthly bookkeeping engagement starts on the 1st of the month, not at 14:37:22 UTC. The lead time feature (`lead_time_days`) operates in whole days. The scheduler's job is to answer one question each day: "are there any schedules due today?" This question is answered by a single indexed query per tenant schema, followed by straightforward project creation logic.

A daily batch job at 02:00 UTC (chosen to avoid business hours in most timezones) provides sufficient precision with minimal complexity. The idempotency guarantee from the `(schedule_id, period_start)` unique constraint on `ScheduleExecution` means that even if the scheduler runs twice (e.g., during a rolling deployment), no duplicate projects are created. If the scheduler misses a day entirely (unlikely but possible during extended maintenance), it catches up on the next run — the `WHERE next_execution_date <= :today` query finds all overdue schedules, not just today's.

For multi-instance deployments, a simple `@SchedulerLock` (from ShedLock, a popular library for distributed lock management) or a database advisory lock can ensure only one instance runs the scheduler. Alternatively, the idempotency guarantee means concurrent execution is safe — wasteful but not harmful. This is a solved problem that does not require distributed scheduling infrastructure.

**Consequences**:

- Positive:
  - No new infrastructure — uses Spring's built-in `@Scheduled` annotation
  - Idempotent execution — safe to run multiple times per day without duplicates
  - Self-healing — missed runs are caught up automatically on the next execution
  - Simple to test — invoke the scheduler method directly in integration tests
  - Observable — each execution creates a `ScheduleExecution` record with timestamp and outcome

- Negative:
  - Maximum 24-hour delay if a run is missed (acceptable for daily-granular scheduling; mitigated by catch-up logic)
  - Sequential tenant processing — at very large tenant counts, a single run might take significant time (mitigated by the simplicity of the per-tenant query; parallelization can be added later with virtual threads if needed)
  - Multi-instance coordination needed — either use `@SchedulerLock` / advisory lock, or accept idempotent-but-redundant concurrent execution (the latter is simpler and safe)
  - 02:00 UTC is not configurable per tenant (acceptable — the scheduler creates projects that are due, not time-sensitive; the project exists by the time business hours start in any timezone)
