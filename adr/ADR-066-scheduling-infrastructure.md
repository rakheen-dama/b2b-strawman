# ADR-066: Scheduling Infrastructure -- Spring @Scheduled vs External Scheduler

**Status**: Accepted

**Context**: Phase 14 requires a job scheduler to automate retainer billing: at the end of each billing period, the system must close the current period, generate a draft invoice, and create the next period. This is the platform's first recurring background job with business logic (the only existing `@Scheduled` method is `MagicLinkCleanupService`, which performs a simple DELETE of expired tokens).

The scheduler must be tenant-aware: it iterates over all active tenants and executes billing logic for each tenant in isolation. It must be idempotent (safe to re-run after restarts), observable (job execution is logged), and extensible (future jobs like dormancy detection and retention enforcement will reuse the same infrastructure).

**Options Considered**:

1. **Spring @Scheduled with custom TenantAwareJob abstraction** -- Use Spring's built-in `@Scheduled` annotation to trigger jobs on a configurable cron schedule. Build a `TenantAwareJob` abstract base class that handles tenant iteration (via `OrgSchemaMappingRepository.findAll()`), ScopedValue binding (`ScopedValue.where(RequestScopes.TENANT_ID, schema).call()`), per-tenant error isolation, and logging to a `ScheduledJobLog` entity. Concrete jobs extend this base class and implement `executeForTenant()`.
   - Pros: Zero external dependencies. Single deployable. Consistent with existing codebase patterns (MagicLinkCleanupService already uses `@Scheduled` + tenant iteration). Idempotent design handles missed runs gracefully. Configuration via `application.yml` properties. `ScheduledJobLog` provides observability without additional tools.
   - Cons: Single-instance execution (no distributed coordination). No job queuing or prioritization. No built-in retry (idempotency handles this). No cluster-aware scheduling (if running multiple backend instances, the job runs on all instances simultaneously).

2. **Quartz Scheduler** -- Use Quartz (Spring Boot has built-in integration) with a JDBC job store for persistent scheduling, cluster-aware execution, and job management.
   - Pros: Distributed execution (JDBC store provides cluster coordination). Built-in retry, misfire handling, and job queuing. Well-established library.
   - Cons: Significant additional complexity: Quartz tables in the database, Quartz-specific configuration, Quartz job/trigger lifecycle management. Quartz's own tenant-awareness would need to be built. Overkill for the current workload (a handful of lightweight jobs running daily). Quartz + Spring Boot 4 + Hibernate 7 compatibility may have edge cases.

3. **External cron (ECS scheduled task, CloudWatch Events, etc.)** -- Define jobs as HTTP endpoints and trigger them from external schedulers (AWS CloudWatch Events -> ECS task, or a cron job that calls the API).
   - Pros: Decouples scheduling from the application. Can scale independently. Leverages cloud-native scheduling.
   - Cons: Additional infrastructure to manage. HTTP endpoint security (must be internal-only). External scheduler has no tenant awareness -- the endpoint must handle iteration internally anyway. Monitoring split across application logs and cloud scheduler. More moving parts for a simple requirement.

4. **ShedLock (distributed lock for @Scheduled)** -- Use ShedLock to add distributed locking to `@Scheduled` methods, ensuring only one instance runs the job at a time in a multi-instance deployment.
   - Pros: Minimal change from Option 1. Adds cluster-safe scheduling. Small library footprint.
   - Cons: Additional dependency. ShedLock table in the database. Still single-instance execution (locks prevent concurrent runs, but don't distribute work). May be premature if the platform currently runs a single backend instance.

**Decision**: Option 1 -- Spring `@Scheduled` with custom `TenantAwareJob` abstraction.

**Rationale**: The current deployment model is a single backend instance (ECS Fargate). The job workload is light: iterate a few dozen tenants, query a few retainers per tenant, generate a few invoices. This workload does not justify the complexity of Quartz or external scheduling.

The `TenantAwareJob` abstraction extracts the existing ad-hoc pattern from `MagicLinkCleanupService` into a reusable base class. This codifies the established pattern (tenant iteration via `OrgSchemaMappingRepository`, ScopedValue binding, per-tenant error isolation) rather than introducing a new paradigm.

Idempotent design is the key enabler: if the backend restarts mid-job, the next scheduled run will simply process any retainers that were not completed. The billing job checks for OPEN periods before processing -- already-closed periods are skipped. This eliminates the need for distributed coordination or exactly-once guarantees.

If the platform later scales to many backend instances, ShedLock (Option 4) can be added as a thin layer on top of the existing `@Scheduled` methods -- a single annotation change per job, no architectural restructuring.

**Consequences**:
- All scheduled jobs run in-process on the single backend instance. No external infrastructure.
- If multiple backend instances are deployed (e.g., for high availability), all instances will run the job simultaneously. The idempotent design means this produces correct results (duplicate work, not incorrect work), but it wastes resources. ShedLock should be added at that point.
- `MagicLinkCleanupService` should be refactored to extend `TenantAwareJob` in a follow-up, eliminating duplicated tenant-iteration code.
- Job schedules are configurable via `application.yml` (`app.scheduler.{job-name}.cron`), defaulting to sensible values.
- `ScheduledJobLog` provides a queryable audit trail of all job executions, accessible via admin endpoints.
- Future jobs (dormancy detection, retention enforcement, subscription renewal, job log cleanup) plug into the same abstraction by extending `TenantAwareJob` and adding a `@Scheduled` method.
