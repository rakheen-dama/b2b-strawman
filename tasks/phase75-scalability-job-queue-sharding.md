# Phase 75 — Scalability: Job Queue Fanout + Shard-Aware DB Resolver

> **Architecture**: [`architecture/phase75-scalability-job-queue-sharding.md`](../architecture/phase75-scalability-job-queue-sharding.md)
> **Requirements**: [`requirements/claude-code-prompt-phase75.md`](../requirements/claude-code-prompt-phase75.md)
> **ADRs**: [ADR-293](../architecture/phase75-scalability-job-queue-sharding.md#adr-293-job-queue-table-over-in-process-parallelism) (Job queue table over in-process parallelism), [ADR-294](../architecture/phase75-scalability-job-queue-sharding.md#adr-294-scheduler-as-enqueuer-pattern) (Scheduler-as-enqueuer), [ADR-295](../architecture/phase75-scalability-job-queue-sharding.md#adr-295-control-plane--shard-plane-database-split) (Control plane / shard plane split), [ADR-296](../architecture/phase75-scalability-job-queue-sharding.md#adr-296-composite-tenant-identifier-format) (Composite tenant identifier), [ADR-297](../architecture/phase75-scalability-job-queue-sharding.md#adr-297-explicit-shard-assignment-over-automatic-placement) (Explicit shard assignment)
> **Predecessors**: Phase 73.5 / Tier A Scalability (commit `fbd163823` -- HikariCP pool sizing, Prometheus metrics, ShedLock on all 19 scheduled jobs), Phase 1/13 (Multitenancy -- `SchemaMultiTenantConnectionProvider`, `TenantIdentifierResolver`, `OrgSchemaMapping`, `TenantScopedRunner`, `RequestScopes`), Phase 71 (Xero Accounting Integration -- `AccountingSyncWorker`, `AccountingPaymentPollWorker`)
> **Starting epic**: 547 . Last completed: 546 (Phase 74)
> **Migration high-water at phase start**: global **V23**, tenant **V127**. Phase 75 ships **two** global migrations (V24, V25). No tenant migrations.

Phase 75 replaces the sequential scheduler-then-tenant-loop pattern with a job queue table (`public.job_queue`) that distributes work across all replicas via `SELECT FOR UPDATE SKIP LOCKED`, and replaces the single-DataSource connection provider with a shard-aware DB resolver that routes tenants to the correct PostgreSQL instance. Both changes are zero-behavioral-change under the current single-shard, single-database deployment. The new infrastructure activates when a second shard is configured. Backend-only phase -- no frontend, no portal.

---

## Open Questions

- **`infrastructure` package existence.** The architecture places job queue classes in `infrastructure.jobqueue`. Verify at implementation time whether `io.b2mash.b2b.b2bstrawman.infrastructure` already exists as a package. If not, create it. The package must be registered in any ArchUnit package-dependency rules if such exist.
- **ShedLock table pre-seeded rows.** V24 migration inserts a `stale_job_recovery` row into `public.shedlock`. Verify at implementation time that `shedlock` table exists (V23 created it) and that the `ON CONFLICT` guard handles idempotent re-runs.
- **`@ConfigurationProperties` binding for map keys with underscores.** `JobQueueProperties.dualMode` is a `Map<String, Boolean>` keyed by `job_type` strings with underscores (e.g., `accounting_sync_drain`). Verify at implementation time that Spring Boot relaxed binding maps YAML underscored keys to map entries correctly. If dashes-to-underscores normalization causes issues, switch to dashed keys in YAML with explicit key matching.
- **HikariCP dynamic DataSource creation.** `DefaultShardRegistry` creates `HikariDataSource` instances programmatically at startup. Verify that HikariCP 6.x (Spring Boot 4) supports the builder API used. The existing `DataSourceConfig` pattern should serve as reference.
- **Graceful shutdown hook ordering.** `JobWorker` needs to stop polling before Spring shuts down the DataSource. Verify that `@PreDestroy` on the worker executes before HikariCP pool close. If not, use `SmartLifecycle` with explicit phase ordering.
- **`OrgSchemaMapping.shard_id` column vs existing `findAll()` callers.** `TenantScopedRunner.forEachTenant()` and `DefaultJobEnqueuer.fanOutToAllTenants()` both call `mappingRepository.findAll()`. After V25, the returned `OrgSchemaMapping` entities will include `shard_id`. Verify no downstream code breaks from the new field.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 547 | Job Queue Entity Foundation + Migration | Backend | -- | M | 547A, 547B | **Done** (PR #1372) |
| 548 | Job Worker + Handler Infrastructure | Backend | 547 | M | 548A, 548B | **Done** (PR #1373) |
| 549 | Scheduler Migration Batch 1 (5 High-Frequency Jobs) | Backend | 548 | M | 549A, 549B | **Done** (PR #1374) |
| 550 | Scheduler Migration Batch 2 (14 Remaining Jobs) + Admin API | Backend | 549 | L | 550A, 550B, 550C | |
| 551 | Shard Config + Registry + Migration | Backend | -- | M | 551A, 551B | |
| 552 | Shard-Aware Connection Provider | Backend | 551 | M | 552A, 552B | |
| 553 | Shard-Aware Request Scopes + Filter + Runner | Backend | 551, 552 | S | 553A | |
| 554 | Shard-Aware Provisioning + Flyway | Backend | 551, 552, 553 | M | 554A, 554B | |
| 555 | Integration Tests + Observability | Backend | 550, 554 | M | 555A, 555B | |

**Slice count: 17** (9 architecture slices expanded to 17 numbered slices to enforce the 6-12 files / ~800 LOC slice-sizing budget and split large epics into sub-slices).

---

## Dependency Graph

```
PHASES already complete:
  Phase 73.5 / Tier A (HikariCP pool sizing, Prometheus metrics, ShedLock on 19 jobs)
  Phase 1/13  (Multitenancy — SchemaMultiTenantConnectionProvider, TenantIdentifierResolver,
               OrgSchemaMapping, TenantScopedRunner, RequestScopes, TenantFilter)
  Phase 71    (Xero Accounting — AccountingSyncWorker, AccountingPaymentPollWorker)
                                 │
           ┌─────────────────────┴──────────────────────┐
           │                                             │
           ▼                                             ▼
┌──────────────────────────────────┐   ┌──────────────────────────────────┐
│ JOB QUEUE TRACK                  │   │ SHARDING TRACK                   │
│                                  │   │                                  │
│ Stage 1 (sequential)             │   │ Stage 1 (sequential)             │
│  [547A  V24 migration + entity   │   │  [551A  V25 migration + entity   │
│         + enum + repository]     │   │         + ShardAndSchema record  │
│              │                   │   │         + OrgSchemaMapping ext]  │
│              ▼                   │   │              │                   │
│  [547B  JobEnqueuer interface +  │   │              ▼                   │
│         DefaultJobEnqueuer +     │   │  [551B  ShardRegistry interface  │
│         JobQueueProperties +     │   │         + DefaultShardRegistry   │
│         unit tests]              │   │         + ShardConfigRepository  │
│              │                   │   │         + unit tests]            │
│              ▼                   │   │              │                   │
│ Stage 2 (sequential)             │   │              ▼                   │
│  [548A  JobHandler interface +   │   │ Stage 2 (sequential)             │
│         JobHandlerRegistry +     │   │  [552A  ShardAwareConnection-   │
│         JobQueueConfig +         │   │         Provider + conditional   │
│         JobQueueProperties ext]  │   │         wiring in HibernateM.T. │
│              │                   │   │         Config]                  │
│              ▼                   │   │              │                   │
│  [548B  JobWorker poll loop +    │   │              ▼                   │
│         StaleJobRecoveryTask +   │   │  [552B  TenantIdentifierResolver│
│         graceful shutdown +      │   │         changes (composite id)  │
│         integration tests]       │   │         + characterization test]│
│              │                   │   │              │                   │
│              ▼                   │   │              ▼                   │
│ Stage 3                          │   │ Stage 3                          │
│  [549A  Migrate 3 schedulers:    │   │  [553A  RequestScopes.SHARD_ID  │
│         automation (2) +         │   │         + TenantFilter cache    │
│         accounting sync]         │   │         change + TenantScoped-  │
│              │                   │   │         Runner shard binding]   │
│              ▼                   │   │              │                   │
│  [549B  Migrate 2 schedulers:    │   │              ▼                   │
│         accounting payment +     │   │ Stage 4                          │
│         time reminder]           │   │  [554A  TenantProvisioningService│
│              │                   │   │         shard param + validation │
│              ▼                   │   │         + provisioning API ext]  │
│ Stage 4                          │   │              │                   │
│  [550A  Migrate 7 schedulers:    │   │              ▼                   │
│         batch 2 daily/hourly]    │   │  [554B  TenantMigrationRunner   │
│              │                   │   │         shard iteration +       │
│              ▼                   │   │         Flyway per shard +      │
│  [550B  Migrate 7 schedulers:    │   │         integration tests]     │
│         batch 2 remaining +      │   │                                  │
│         subscription (3 methods)]│   └──────────────────────────────────┘
│              │                   │                    │
│              ▼                   │                    │
│  [550C  JobQueueAdminController  │                    │
│         + admin API tests]       │                    │
│              │                   │                    │
└──────────────┼────────────────────────────────────────┘
               │                                │
               ▼                                ▼
     ┌─────────────────────────────────────────────────┐
     │ CONVERGENCE — Stage 5                           │
     │                                                  │
     │  [555A  JobQueueMetrics + ShardMetrics +         │
     │         ShardHealthIndicator +                   │
     │         JobQueueHealthIndicator]                 │
     │              │                                   │
     │              ▼                                   │
     │  [555B  End-to-end integration tests +           │
     │         shard isolation test +                   │
     │         characterization test]                   │
     └─────────────────────────────────────────────────┘
```

**Parallel opportunities:**
- The **Job Queue Track** (547-550) and **Sharding Track** (551-554) are fully independent and can progress in parallel from day 1.
- Within each track, slices are sequential (each depends on its predecessor).
- **555A** and **555B** depend on both tracks completing (converge point).
- Within Epic 549, 549A and 549B are sequential (549B depends on 549A verifying the pattern works).
- Within Epic 550, 550A, 550B, and 550C are sequential (550C admin API depends on handlers being registered).

---

## Implementation Order

### Stage 1 -- Entity Foundations + Migrations (parallel tracks begin)

| Order | Slice | Summary |
|-------|-------|---------|
| 1a | **547A** | V24 global migration (`job_queue` table + 4 indexes + ShedLock seed); `JobQueue` entity + `JobStatus` enum + `JobQueueRepository` with custom `@Query` methods for claim, stale recovery, stats. |
| 1b | **547B** | `JobEnqueuer` interface + `DefaultJobEnqueuer` implementation (batch insert with `ON CONFLICT DO NOTHING`); `JobQueueProperties` configuration class; unit tests for dedup logic and configuration binding. |
| 1c | **551A** | V25 global migration (`shard_config` table + `org_schema_mapping.shard_id` extension + FK + index); `ShardConfig` entity + `ShardAndSchema` record; `OrgSchemaMapping` entity modification (add `shard_id` field); `OrgSchemaMappingRepository` extension (add `findByShardId`). |
| 1d | **551B** | `ShardRegistry` interface + `DefaultShardRegistry` implementation (DataSource lifecycle); `ShardConfigRepository`; unit tests for startup, env var resolution, `ShardAndSchema` parsing. |

### Stage 2 -- Worker Infrastructure + Connection Provider (parallel tracks)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 2a | **548A** | `JobHandler` interface + `JobHandlerRegistry` (startup validation); `JobQueueConfig` (`@Configuration` + `@ConditionalOnProperty`); extend `JobQueueProperties` with worker fields. | 552A |
| 2b | **548B** | `JobWorker` poll loop (virtual threads, `FOR UPDATE SKIP LOCKED`, ScopedValue binding, retry/dead-letter); `StaleJobRecoveryTask` (`@Scheduled` + `@SchedulerLock`); graceful shutdown; integration tests for enqueue-claim-execute cycle, concurrent workers, stale recovery, dead letter. | 552B |
| 2c | **552A** | `ShardAwareConnectionProvider` implementing `MultiTenantConnectionProvider<String>` (parse composite identifier, resolve DataSource, set search_path); conditional wiring in `HibernateMultiTenancyConfig` (`@ConditionalOnProperty`). | 548A |
| 2d | **552B** | `TenantIdentifierResolver` changes (composite `{shardId}:{schemaName}` format when sharding enabled); single-shard characterization test (enable sharding with primary only, run test suite). | 548B |

### Stage 3 -- Scheduler Batch 1 + Request Scopes (parallel tracks)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 3a | **549A** | Migrate 3 schedulers: `AutomationScheduler.pollScheduledTriggers()` -> `AutomationPollTriggersHandler`; `AutomationScheduler.pollDelayedActions()` -> `AutomationPollDelayedHandler`; `AccountingSyncWorker.drainPendingEntries()` -> `AccountingSyncDrainHandler`. Dual-mode support. | 553A | **Done** (PR #1374) |
| 3b | **549B** | Migrate 2 schedulers: `AccountingPaymentPollWorker.pollAllConnections()` -> `AccountingPaymentPollHandler`; `TimeReminderScheduler.checkTimeReminders()` -> `TimeReminderHandler`. Characterization tests. | 553A | **Done** (PR #1374) |
| 3c | **553A** | `RequestScopes.SHARD_ID` ScopedValue + `runForTenantOnShard()` method; `TenantFilter` cache change (`Cache<String, String>` -> `Cache<String, TenantMapping>`); `TenantScopedRunner.forEachTenant()` shard binding per iteration. | 549A, 549B |

### Stage 4 -- Scheduler Batch 2 + Provisioning (parallel tracks)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 4a | **550A** | Migrate 7 schedulers: `RecurringScheduleExecutor`, `DormancyScheduledJob`, `ProposalExpiryProcessor`, `AcceptanceExpiryProcessor`, `MagicLinkCleanupService`, `AiExecutionGateService`, `FieldDateScannerJob`. | 554A | **Done** (PR #1375) |
| 4b | **550B** | Migrate 7 schedulers: `RequestReminderScheduler`, `SubscriptionExpiryJob` (3 methods: trial, grace, cancellation), `CourtDateReminderJob`, `AiInvocationExpirySweeper`, `PortalDigestScheduler`. | 554B |
| 4c | **550C** | `JobQueueAdminController` (list, retry, delete, stats endpoints); platform-admin only; integration tests with MockMvc. | After 550B |
| 4d | **554A** | `TenantProvisioningService` shard parameter + validation via `ShardRegistry`; provisioning API extension (optional `shardId` field); integration test for provisioning on non-primary shard. | 550A |
| 4e | **554B** | `TenantMigrationRunner` shard iteration (iterate active shards from `ShardRegistry`, run Flyway per shard per schema); integration test with two embedded Postgres instances. | 550B |

### Stage 5 -- Convergence: Observability + Integration Tests

| Order | Slice | Summary |
|-------|-------|---------|
| 5a | **555A** | `JobQueueMetrics` (Micrometer counters, gauges, histograms per architecture Section 75.12.1); `ShardMetrics` (per-shard pool metrics per Section 75.12.2); `ShardHealthIndicator` + `JobQueueHealthIndicator`. |
| 5b | **555B** | End-to-end integration test (enqueue -> claim -> execute against shard 2); shard isolation test (tenant on shard A cannot access shard B); single-shard characterization test (full `./mvnw verify` with sharding enabled + primary only). |

### Timeline

```
Track A (Job Queue):  [547A] -> [547B] -> [548A] -> [548B] -> [549A] -> [549B] -> [550A] -> [550B] -> [550C]
Track B (Sharding):   [551A] -> [551B] -> [552A] -> [552B] -> [553A] -> [554A] -> [554B]
Convergence:          [555A] -> [555B]  (after both tracks complete)
```

A realistic day-by-day cadence (2 tracks in parallel): 547A + 551A days 1-2; 547B + 551B days 2-4; 548A + 552A days 4-6; 548B + 552B days 6-9; 549A + 553A days 9-11; 549B days 11-13; 550A + 554A days 13-16; 550B + 554B days 16-19; 550C days 19-20; 555A days 20-22; 555B days 22-24.

---

## Epic 547: Job Queue Entity Foundation + Migration

**Goal**: Create the V24 global migration that adds the `public.job_queue` table with all indexes, build the `JobQueue` JPA entity and `JobStatus` enum, create the `JobQueueRepository` with custom query methods, and implement the `JobEnqueuer` service that fans out one job per tenant using batch inserts with dedup. After this epic, schedulers can enqueue jobs into the queue, but nothing processes them yet.

**References**: Architecture Section 75.2.1 (Job Queue Table), Section 75.8.1 (V24 Migration), Section 75.10.1 (Implementation Guidance -- JobQueue entity, JobStatus, JobQueueRepository, JobEnqueuer, DefaultJobEnqueuer, JobQueueProperties).

**Dependencies**: None (first epic in the job queue track).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **547A** | 547A.1-547A.4 | ~5 backend files (1 migration + 1 entity + 1 enum + 1 repository + 1 test) | V24 global migration (job_queue table + 4 indexes + ShedLock seed row); `JobQueue` entity; `JobStatus` enum; `JobQueueRepository` with custom queries; migration verification test. | **Done** (PR #1372) |
| **547B** | 547B.1-547B.5 | ~6 backend files (1 interface + 1 implementation + 1 config properties + 2 config files + 1 test) | `JobEnqueuer` interface; `DefaultJobEnqueuer` (batch insert + dedup); `JobQueueProperties` (`@ConfigurationProperties`); `application.yml` + `application-test.yml` config additions; unit/integration tests. | **Done** (PR #1372) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 547A.1 | Create V24 global migration | `backend/src/main/resources/db/migration/global/V24__create_job_queue.sql` | verified by 547A.4 (migration runs clean) | existing `V23__create_shedlock_table.sql` for global migration format | SQL verbatim from architecture Section 75.8.1: `CREATE TABLE IF NOT EXISTS public.job_queue` (16 columns); `CHECK` constraint on `status`; 4 indexes: `idx_job_queue_claimable` (partial on `status = 'PENDING'`), `idx_job_queue_dedup` (unique partial on `status IN ('PENDING', 'CLAIMED')`), `idx_job_queue_status_type` (composite for admin API), `idx_job_queue_stale_claims` (partial on `status = 'CLAIMED'`); ShedLock seed: `INSERT INTO public.shedlock (name, lock_until, locked_at, locked_by) VALUES ('stale_job_recovery', NOW(), NOW(), 'migration') ON CONFLICT (name) DO NOTHING`. All `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` for idempotency. |
| 547A.2 | Create `JobStatus` enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobStatus.java` | covered by 547A.4 | existing enum patterns (e.g., `LifecycleStatus`) | `public enum JobStatus { PENDING, CLAIMED, COMPLETED, FAILED, DEAD_LETTER }`. Simple enum, no methods needed beyond standard `name()` / `valueOf()`. |
| 547A.3 | Create `JobQueue` entity + `JobQueueRepository` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueue.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueRepository.java` | covered by 547A.4 | existing `OrgSchemaMapping` entity for `@Table(schema = "public")` pattern; existing `*Repository` interfaces | `@Entity @Table(name = "job_queue", schema = "public")`. Fields per architecture Section 75.2.1: `id` (UUID, PK), `jobType` (VARCHAR 100), `tenantId` (VARCHAR 50), `orgId` (VARCHAR 100), `shardId` (VARCHAR 50, default `primary`), `status` (`@Enumerated(STRING)` JobStatus, default PENDING), `payload` (JSONB via `@JdbcTypeCode(SqlTypes.JSON)`), `priority` (int, default 0), `claimedBy`, `claimedAt`, `completedAt`, `retryCount` (int, default 0), `maxRetries` (int, default 3), `nextAttemptAt`, `errorMessage`, `createdAt`. Constructor: `JobQueue(String jobType, String tenantId, String orgId, String shardId, JsonNode payload, int maxRetries)`. Repository: `JpaRepository<JobQueue, UUID>` with `@Query` methods: `findClaimable(int limit)` (native query: `SELECT * FROM public.job_queue WHERE status = 'PENDING' AND next_attempt_at <= NOW() ORDER BY priority DESC, next_attempt_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED`); `findStaleClaimed(Instant threshold)` (`WHERE status = 'CLAIMED' AND claimed_at < :threshold`); `countByStatus()` for stats; `findByStatusAndJobType(JobStatus, String, Pageable)` for admin API. |
| 547A.4 | Integration test for V24 migration + entity round-trip | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueEntityTest.java` | ~4 tests: (1) V24 migration runs clean on embedded Postgres; (2) `JobQueue` entity round-trip (save + findById); (3) default values correct (status PENDING, retryCount 0, shardId `primary`); (4) `shedlock` table contains `stale_job_recovery` row | standard `@SpringBootTest @Import(TestcontainersConfiguration.class) @ActiveProfiles("test")` pattern | Verify migration runs clean on embedded Postgres. Verify entity CRUD. No tenant provisioning needed -- `job_queue` is in `public` schema. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 547B.1 | Create `JobQueueProperties` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueProperties.java` | covered by 547B.4 | existing `@ConfigurationProperties` patterns in the codebase | `@ConfigurationProperties("kazi.job-queue")`. Fields: `boolean enabled = false`, `int batchSize = 20`, `long pollIntervalMs = 2000`, `int staleClaimTimeoutMinutes = 15`, `int maxRetriesDefault = 3`, `int backoffBaseSeconds = 10`, `Map<String, Boolean> dualMode = new HashMap<>()`. Method: `boolean isDualMode(String jobType)` returns `dualMode.getOrDefault(jobType, false)`. Java record NOT suitable here -- mutable map + `@ConfigurationProperties` binding requires standard class with setters. |
| 547B.2 | Create `JobEnqueuer` interface | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobEnqueuer.java` | covered by 547B.4 | architecture Section 75.2.1 | `public interface JobEnqueuer { void enqueue(String jobType, String tenantId, String orgId, String shardId, @Nullable JsonNode payload); int fanOutToAllTenants(String jobType, @Nullable JsonNode payload); int fanOutToAllTenants(String jobType, @Nullable JsonNode payload, int priority); }`. |
| 547B.3 | Create `DefaultJobEnqueuer` implementation | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/DefaultJobEnqueuer.java` | 547B.4 | `TenantScopedRunner` for `OrgSchemaMappingRepository.findAll()` pattern | `@Component`. Constructor injection: `OrgSchemaMappingRepository`, `JobQueueRepository`, `JobQueueProperties`. `fanOutToAllTenants()`: reads all mappings, constructs `JobQueue` entities, batch-saves via `repository.saveAll()`. The `idx_job_queue_dedup` unique partial index guards against race conditions -- `DataIntegrityViolationException` per-entity is caught and treated as a no-op (dedup skip). `enqueue()`: single insert with same dedup handling. Both methods use `maxRetriesDefault` from properties. Returns count of jobs actually enqueued (excludes dedup skips). |
| 547B.4 | Add configuration to `application.yml` + `application-test.yml` | `backend/src/main/resources/application.yml` (modify), `backend/src/test/resources/application-test.yml` (modify) | covered by 547B.5 | existing `kazi:` config block | Add to `application.yml` under `kazi:`: `job-queue: enabled: true, batch-size: 20, poll-interval-ms: 2000, stale-claim-timeout-minutes: 15, max-retries-default: 3, backoff-base-seconds: 10`. Add to `application-test.yml` under `kazi:`: `job-queue: enabled: false, poll-interval-ms: 100`. |
| 547B.5 | Unit/integration tests for enqueuer + dedup | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/DefaultJobEnqueuerTest.java` | ~5 tests: (1) `fanOutToAllTenants()` enqueues one job per tenant mapping; (2) second `fanOutToAllTenants()` call is a no-op (dedup -- all jobs still PENDING); (3) mark one job COMPLETED, re-enqueue -- assert count +1; (4) `enqueue()` single job dedup; (5) configuration properties bind correctly from test YAML | standard integration test with embedded Postgres | Provision 3 test tenants. Enqueue. Assert row count. Enqueue again. Assert still same count. Mark one COMPLETED. Enqueue. Assert count +1. |

### Key Files

**Create (backend):**
- `backend/src/main/resources/db/migration/global/V24__create_job_queue.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueue.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobEnqueuer.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/DefaultJobEnqueuer.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueProperties.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueEntityTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/DefaultJobEnqueuerTest.java`

**Modify (backend):**
- `backend/src/main/resources/application.yml` -- add `kazi.job-queue` config block
- `backend/src/test/resources/application-test.yml` -- add `kazi.job-queue` test config

**Read for context:**
- `backend/src/main/resources/db/migration/global/V23__create_shedlock_table.sql` -- global migration format
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMapping.java` -- entity on `public` schema pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMappingRepository.java` -- repository pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java` -- `mappingRepository.findAll()` pattern

### Architecture Decisions

- **Job queue table over in-process parallelism** ([ADR-293](../architecture/phase75-scalability-job-queue-sharding.md#adr-293-job-queue-table-over-in-process-parallelism)) -- `SELECT FOR UPDATE SKIP LOCKED` distributes work across pods, replacing the proposed StructuredTaskScope parallelism (B3). Multi-pod scalability, visibility, retry semantics.
- **Entity in `public` schema** -- `@Table(name = "job_queue", schema = "public")` mirrors the `OrgSchemaMapping` pattern. The job queue is a control plane table, not a tenant table.
- **JSONB payload** -- `@JdbcTypeCode(SqlTypes.JSON)` for the `payload` field allows job-type-specific data without schema changes. Most jobs will have a null payload (fan-out jobs carry no per-tenant context beyond the tenant ID itself).
- **Dedup via unique partial index** -- `INSERT ... ON CONFLICT DO NOTHING` on `idx_job_queue_dedup` prevents double-enqueue at the database level. The application catches `DataIntegrityViolationException` as a no-op rather than pre-checking existence (avoids TOCTOU race).

### Non-scope

- No worker poll loop (lands in 548B).
- No handler interface or registry (lands in 548A).
- No scheduler migrations (lands in 549/550).
- No admin API (lands in 550C).

---

## Epic 548: Job Worker + Handler Infrastructure

**Goal**: Build the consume-side infrastructure for the job queue. Create the `JobHandler` interface and `JobHandlerRegistry` for dispatching jobs to type-specific handlers, implement the `JobWorker` poll loop using virtual threads with `FOR UPDATE SKIP LOCKED` claiming and `ScopedValue` tenant binding, add the `StaleJobRecoveryTask` for crash recovery, and implement graceful shutdown. After this epic, any registered `JobHandler` will have its jobs claimed and executed by workers across all pods.

**References**: Architecture Section 75.2.2 (`JobHandler`, `JobHandlerRegistry`), Section 75.2.3 (`JobWorker`), Section 75.2.4 (`StaleJobRecoveryTask`), Section 75.10.1 (Implementation Guidance).

**Dependencies**: Epic 547 (job queue entity, repository, enqueuer, properties).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **548A** | 548A.1-548A.3 | ~4 backend files (1 interface + 1 registry + 1 config + 1 test) | `JobHandler` interface; `JobHandlerRegistry` (Spring bean discovery, fail-fast on duplicates); `JobQueueConfig` (`@Configuration` + `@ConditionalOnProperty`). | **Done** (PR #1373) |
| **548B** | 548B.1-548B.4 | ~4 backend files (1 worker + 1 stale recovery task + 1 test file + 1 test handler) | `JobWorker` (poll loop, virtual threads, claim, ScopedValue binding, retry/dead-letter, backoff, graceful shutdown); `StaleJobRecoveryTask`; integration tests. | **Done** (PR #1373) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 548A.1 | Create `JobHandler` interface | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobHandler.java` | covered by 548A.3 | architecture Section 75.2.2 | `public interface JobHandler { String jobType(); void execute(@Nullable JsonNode payload); }`. Handlers are discovered via Spring `@Component` scanning. The `jobType()` return value must be unique across all registered handlers. `execute()` runs with tenant scope already bound by the worker. |
| 548A.2 | Create `JobHandlerRegistry` + `JobQueueConfig` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobHandlerRegistry.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueConfig.java` | 548A.3 | Spring `ApplicationContext.getBeansOfType()` pattern | `@Component` `JobHandlerRegistry`: constructor takes `List<JobHandler>`, builds `Map<String, JobHandler>` from `jobType() -> handler`, throws `IllegalStateException` at startup if any `jobType` has duplicate registrations. Method: `JobHandler getHandler(String jobType)` throws `IllegalArgumentException` if not found. `@Configuration` `JobQueueConfig`: `@ConditionalOnProperty(name = "kazi.job-queue.enabled", havingValue = "true")`, `@EnableConfigurationProperties(JobQueueProperties.class)`. |
| 548A.3 | Unit test for handler registry | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobHandlerRegistryTest.java` | ~3 tests: (1) registry resolves handler by jobType; (2) duplicate jobType at startup throws `IllegalStateException`; (3) unknown jobType throws `IllegalArgumentException` | plain unit test (no Spring context) | Create test `JobHandler` implementations in the test class. Construct `JobHandlerRegistry` directly. Verify lookup and error cases. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 548B.1 | Create `JobWorker` poll loop | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobWorker.java` | 548B.4 | architecture Section 75.2.3 | `@Component @ConditionalOnProperty(name = "kazi.job-queue.enabled", havingValue = "true")`. Implements `SmartLifecycle` for ordered shutdown. Fields: `JobQueueRepository`, `JobHandlerRegistry`, `JobQueueProperties`, `PlatformTransactionManager`. `start()`: launch virtual thread for poll loop. Poll cycle: (1) open transaction, (2) call `repository.findClaimable(batchSize)` -- this is the `FOR UPDATE SKIP LOCKED` query, (3) for each job: set `status = CLAIMED`, `claimedBy = podId()`, `claimedAt = now()`, flush, (4) commit claim transaction, (5) for each claimed job: bind `RequestScopes.TENANT_ID`, `ORG_ID`, `SHARD_ID` via `ScopedValue.Carrier`, populate SLF4J MDC (`tenantId`, `orgId`, `shardId`, `jobType`, `jobId`) in try/finally, look up handler, execute, on success: `status = COMPLETED`, `completedAt = now()`, on failure: `retryCount++`, if `>= maxRetries`: `status = DEAD_LETTER`, `errorMessage = ex.getMessage()`, else `status = PENDING`, `nextAttemptAt = now + 2^retryCount * backoffBase`. (6) Sleep `pollIntervalMs`. `stop()`: set running flag to false, interrupt poll thread, wait for in-flight jobs with 30s hard timeout. `podId()`: `InetAddress.getLocalHost().getHostName()` or `HOSTNAME` env var. |
| 548B.2 | Create `StaleJobRecoveryTask` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/StaleJobRecoveryTask.java` | 548B.4 | existing `@Scheduled` + `@SchedulerLock` pattern from any of the 19 schedulers | `@Component @ConditionalOnProperty(name = "kazi.job-queue.enabled", havingValue = "true")`. `@Scheduled(fixedDelay = 60_000)` + `@SchedulerLock(name = "stale_job_recovery", lockAtLeastFor = "30s")`. Queries `repository.findStaleClaimed(Instant.now().minusMinutes(properties.getStaleClaimTimeoutMinutes()))`. For each stale job: reset `status = PENDING`, `claimedBy = null`, `claimedAt = null`. Log count of recovered jobs at WARN level. |
| 548B.3 | Create test `JobHandler` for integration tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/TestJobHandler.java` | used by 548B.4 | -- | `@Component @Profile("test")` test handler that records execution calls in a `ConcurrentLinkedQueue<String>` (stores tenantId). `jobType()` returns `"test_job"`. `execute()` adds `RequestScopes.TENANT_ID.get()` to the queue. Also a `FailingTestJobHandler` variant that always throws to test retry/dead-letter. |
| 548B.4 | Integration tests for worker lifecycle | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobWorkerIntegrationTest.java` | ~6 tests: (1) enqueue 20 jobs, start worker, assert all completed within 10s; (2) concurrent worker test: 2 worker threads, 20 jobs, each job claimed exactly once; (3) failing handler: job retries 3 times then moves to DEAD_LETTER with error message; (4) stale recovery: insert CLAIMED job with `claimed_at` 30min ago, run `StaleJobRecoveryTask`, assert PENDING; (5) backoff: failing job has increasing `next_attempt_at`; (6) graceful shutdown: jobs in-flight complete before stop | `@SpringBootTest @Import(TestcontainersConfiguration.class) @ActiveProfiles("test")` with `@TestPropertySource(properties = {"kazi.job-queue.enabled=true", "kazi.job-queue.poll-interval-ms=100"})` | Provision 1 tenant. Enqueue via `DefaultJobEnqueuer`. Enable worker via property override. Use `TestJobHandler` to track executions. Concurrent test uses `CountDownLatch` to verify no double-claims. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobHandlerRegistry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueConfig.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobWorker.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/StaleJobRecoveryTask.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobHandlerRegistryTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/TestJobHandler.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobWorkerIntegrationTest.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` -- ScopedValue binding pattern (`runForTenant`)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java` -- `forEachTenant` pattern for ScopedValue carrier construction
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationScheduler.java` -- `@Scheduled` + `@SchedulerLock` pattern reference

### Architecture Decisions

- **Scheduler-as-enqueuer pattern** ([ADR-294](../architecture/phase75-scalability-job-queue-sharding.md#adr-294-scheduler-as-enqueuer-pattern)) -- `@Scheduled` methods keep their cron triggers but only fan out jobs. Workers claim and execute.
- **`SmartLifecycle` over `@PreDestroy`** -- `JobWorker` implements `SmartLifecycle` with explicit phase ordering to ensure the poll loop stops before the DataSource pool closes during shutdown. `getPhase()` returns `Integer.MAX_VALUE - 10` (stops early in shutdown sequence).
- **Virtual threads for worker** -- Each claimed job executes on a virtual thread. `ScopedValue` carrier propagation is native to virtual threads per Java 25. No thread pool sizing concern.
- **MDC explicit binding** -- SLF4J MDC and `ScopedValue` are independent mechanisms. The worker explicitly calls `MDC.put(...)` before dispatching to the handler and `MDC.remove(...)` in the `finally` block.

### Non-scope

- No scheduler migrations (lands in 549/550).
- No admin API (lands in 550C).
- No metrics (lands in 555A).

---

## Epic 549: Scheduler Migration Batch 1 (5 High-Frequency Jobs)

**Goal**: Migrate the 5 highest-frequency and highest-impact schedulers from the sequential `forEachTenant` pattern to the job queue enqueue pattern. Each scheduler's business logic moves into a `JobHandler` implementation, and the `@Scheduled` method body becomes a single `jobEnqueuer.fanOutToAllTenants(jobType)` call. Dual-mode transition support is included for safe rollout.

**References**: Architecture Section 75.3 (Scheduler Migration), Section 75.11.1 (Complete Scheduler Inventory -- Batch 1), Section 75.11.2 (Migration Pattern), Section 75.11.3 (Dual-Mode Transition).

**Dependencies**: Epic 548 (worker + handler infrastructure).

**Scope**: Backend only

**Estimated Effort**: M

### Schedulers in Batch 1

| # | Class | Method | Schedule | ShedLock Name | New `job_type` |
|---|---|---|---|---|---|
| 1 | `AutomationScheduler` | `pollScheduledTriggers()` | `fixedDelay = 60_000` (60s) | `automation_poll_scheduled_triggers` | `automation_poll_triggers` |
| 2 | `AutomationScheduler` | `pollDelayedActions()` | `fixedDelay = 900_000` (15m) | `automation_poll_delayed_actions` | `automation_poll_delayed` |
| 3 | `AccountingSyncWorker` | `drainPendingEntries()` | `fixedDelay = 30_000` (30s) | `accounting_sync_drain_pending_entries` | `accounting_sync_drain` |
| 4 | `AccountingPaymentPollWorker` | `pollAllConnections()` | `fixedDelay = 900_000` (15m) | `accounting_payment_poll_all_connections` | `accounting_payment_poll` |
| 5 | `TimeReminderScheduler` | `checkTimeReminders()` | `fixedRate = 900_000` (15m) | `time_reminder_check_time_reminders` | `time_reminder_check` |

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **549A** | 549A.1-549A.4 | ~6 backend files (3 new handlers + 2 modified schedulers + 1 test) | Migrate `AutomationScheduler.pollScheduledTriggers()`, `AutomationScheduler.pollDelayedActions()`, `AccountingSyncWorker.drainPendingEntries()`. Create 3 `JobHandler` implementations. Update scheduler method bodies. | **Done** (PR #1374) |
| **549B** | 549B.1-549B.3 | ~4 backend files (2 new handlers + 2 modified schedulers + 1 test) | Migrate `AccountingPaymentPollWorker.pollAllConnections()`, `TimeReminderScheduler.checkTimeReminders()`. Create 2 `JobHandler` implementations. Characterization tests for all 5 batch 1 handlers. | **Done** (PR #1374) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 549A.1 | Create `AutomationPollTriggersHandler` + update scheduler | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationPollTriggersHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationScheduler.java` (modify) | 549A.4 | architecture Section 75.11.2 (migration pattern) | New `@Component` handler in `automation` package. `jobType()` returns `"automation_poll_triggers"`. `execute()` contains the per-tenant trigger polling logic extracted from `AutomationScheduler.pollScheduledTriggers()` inner lambda. Transaction boundaries preserved exactly. Scheduler method body changes to: `if (jobQueueProperties.isDualMode("automation_poll_triggers")) { tenantScopedRunner.forEachTenant(...old code...); } jobEnqueuer.fanOutToAllTenants("automation_poll_triggers");`. Inject `JobEnqueuer` and `JobQueueProperties` into `AutomationScheduler`. |
| 549A.2 | Create `AutomationPollDelayedHandler` + update scheduler | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationPollDelayedHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationScheduler.java` (modify -- same file as 549A.1) | 549A.4 | same migration pattern | New `@Component` handler. `jobType()` returns `"automation_poll_delayed"`. `execute()` contains the per-tenant delayed action logic extracted from `AutomationScheduler.pollDelayedActions()`. Scheduler method body changes to dual-mode + `jobEnqueuer.fanOutToAllTenants("automation_poll_delayed")`. |
| 549A.3 | Create `AccountingSyncDrainHandler` + update scheduler | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncDrainHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncWorker.java` (modify) | 549A.4 | architecture Section 75.11.2 (code example uses this exact handler) | New `@Component` handler in `integration.accounting.sync` package. `jobType()` returns `"accounting_sync_drain"`. `execute()` contains per-tenant Xero sync logic from `drainPendingEntries()`. Uses existing `AccountingSyncService.drainPending()` via `transactionTemplate.execute()`. Scheduler body changes to enqueue call. Inject `JobEnqueuer` and `JobQueueProperties` into `AccountingSyncWorker`. |
| 549A.4 | Integration tests for batch 1 (first 3) | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/SchedulerMigrationBatch1ATest.java` | ~4 tests: (1) `AutomationPollTriggersHandler` registered with correct jobType; (2) `AutomationPollDelayedHandler` registered with correct jobType; (3) `AccountingSyncDrainHandler` registered with correct jobType; (4) enqueue + execute cycle completes without error for each handler type | integration test with job queue enabled | Uses `@TestPropertySource(properties = "kazi.job-queue.enabled=true")`. Provisions a tenant. Enqueues one job per type. Verifies workers process them. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 549B.1 | Create `AccountingPaymentPollHandler` + update scheduler | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingPaymentPollHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingPaymentPollWorker.java` (modify) | 549B.3 | same migration pattern as 549A | New `@Component` handler. `jobType()` returns `"accounting_payment_poll"`. `execute()` contains per-tenant payment polling logic from `pollAllConnections()`. Scheduler body changes to enqueue call. |
| 549B.2 | Create `TimeReminderHandler` + update scheduler | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/TimeReminderHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/TimeReminderScheduler.java` (modify) | 549B.3 | same migration pattern | New `@Component` handler in `schedule` package. `jobType()` returns `"time_reminder_check"`. `execute()` contains per-tenant time reminder logic from `checkTimeReminders()`. Scheduler body changes to enqueue call. |
| 549B.3 | Characterization tests for all 5 batch 1 handlers | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/SchedulerMigrationBatch1BTest.java` | ~5 tests: (1) `AccountingPaymentPollHandler` registered correctly; (2) `TimeReminderHandler` registered correctly; (3) all 5 batch 1 handlers are present in `JobHandlerRegistry`; (4) full `./mvnw verify` passes (verified by CI, not an in-test assertion); (5) dual-mode config flag toggles old path execution | integration test | Verify all 5 handlers coexist in the registry. Test dual-mode toggle: set `kazi.job-queue.dual-mode.accounting_sync_drain: true`, verify old path is also invoked. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationPollTriggersHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationPollDelayedHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncDrainHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingPaymentPollHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/TimeReminderHandler.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/SchedulerMigrationBatch1ATest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/SchedulerMigrationBatch1BTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationScheduler.java` -- replace `forEachTenant` bodies with enqueue calls, add `JobEnqueuer` + `JobQueueProperties` injection
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncWorker.java` -- replace body with enqueue
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingPaymentPollWorker.java` -- replace body with enqueue
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/TimeReminderScheduler.java` -- replace body with enqueue

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationScheduler.java` -- existing scheduler to migrate
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncWorker.java` -- existing scheduler
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/TimeReminderScheduler.java` -- existing scheduler

### Architecture Decisions

- **Handler lives in domain package** -- `AccountingSyncDrainHandler` lives in `integration.accounting.sync`, not in `infrastructure.jobqueue`. Feature-centric organization per CLAUDE.md convention.
- **Dual-mode for safe rollout** -- Each scheduler checks `jobQueueProperties.isDualMode(jobType)` to optionally run both old and new paths. The dedup index prevents double execution. Default is `false` (new path only).
- **ShedLock stays on scheduler** -- Prevents concurrent enqueue waves from multiple pods. The job queue dedup index is a second safety net.
- **Transaction boundaries preserved** -- If the original scheduler used `transactionTemplate.execute()`, the handler does the same. No implicit transaction from the worker -- handlers manage their own.

### Non-scope

- No batch 2 scheduler migrations (lands in 550).
- No admin API (lands in 550C).

---

## Epic 550: Scheduler Migration Batch 2 (14 Remaining Jobs) + Admin API

**Goal**: Complete the scheduler migration by converting the remaining 14 scheduler methods (across 12 classes) to the job queue enqueue pattern, build the admin API for job queue management (list, retry, delete, stats), and remove dual-mode code from batch 1 schedulers (now stable).

**References**: Architecture Section 75.3.2 (Migration Order -- Batch 2), Section 75.7.1 (Job Queue Admin API), Section 75.11.1 (Complete Scheduler Inventory -- Batch 2).

**Dependencies**: Epic 549 (batch 1 migration complete, pattern verified).

**Scope**: Backend only

**Estimated Effort**: L

### Schedulers in Batch 2

| # | Class | Method | Schedule | New `job_type` |
|---|---|---|---|---|
| 6 | `RecurringScheduleExecutor` | `executeSchedules()` | daily 2:00 UTC | `recurring_schedule_execute` |
| 7 | `DormancyScheduledJob` | `executeDormancyCheck()` | daily 2:00 UTC | `dormancy_check` |
| 8 | `ProposalExpiryProcessor` | `processExpiredProposals()` | hourly | `proposal_expiry` |
| 9 | `AcceptanceExpiryProcessor` | `processExpired()` | hourly | `acceptance_expiry` |
| 10 | `MagicLinkCleanupService` | `cleanupExpiredTokens()` | hourly | `magic_link_cleanup` |
| 11 | `AiExecutionGateService` | `expireStaleGates()` | hourly | `ai_gate_expiry` |
| 12 | `FieldDateScannerJob` | `execute()` | daily 6:00 UTC | `field_date_scan` |
| 13 | `RequestReminderScheduler` | `checkRequestReminders()` | 6-hour interval | `request_reminder_check` |
| 14 | `SubscriptionExpiryJob` | `processTrialExpiry()` | daily 3:00 UTC | `subscription_trial_expiry` |
| 15 | `SubscriptionExpiryJob` | `processGraceExpiry()` | daily 3:05 UTC | `subscription_grace_expiry` |
| 16 | `SubscriptionExpiryJob` | `processPendingCancellationEnd()` | daily 3:10 UTC | `subscription_cancellation_end` |
| 17 | `CourtDateReminderJob` | `execute()` | daily 6:00 UTC | `court_date_reminder` |
| 18 | `AiInvocationExpirySweeper` | `sweep()` | daily 3:00 UTC | `ai_invocation_expiry` |
| 19 | `PortalDigestScheduler` | `scheduledRun()` | weekly Mon 8:00 UTC | `portal_digest` |

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **550A** | 550A.1-550A.8 | ~12 backend files (7 new handlers + 7 modified schedulers + 1 test) | Migrate schedulers #6-12: `RecurringScheduleExecutor`, `DormancyScheduledJob`, `ProposalExpiryProcessor`, `AcceptanceExpiryProcessor`, `MagicLinkCleanupService`, `AiExecutionGateService`, `FieldDateScannerJob`. | **Done** (PR #1375) |
| **550B** | 550B.1-550B.8 | ~12 backend files (7 new handlers + 5 modified schedulers + 1 test) | Migrate schedulers #13-19: `RequestReminderScheduler`, `SubscriptionExpiryJob` (3 handlers), `CourtDateReminderJob`, `AiInvocationExpirySweeper`, `PortalDigestScheduler`. |
| **550C** | 550C.1-550C.3 | ~3 backend files (1 controller + 1 DTO record + 1 test) | `JobQueueAdminController` (list, retry, delete, stats); platform-admin security; integration tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 550A.1 | Create `RecurringScheduleHandler` + update executor | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleExecutor.java` (modify) | 550A.8 | batch 1 migration pattern (549A) | `jobType()` = `"recurring_schedule_execute"`. Extract per-tenant recurring schedule execution logic. Daily 2:00 UTC. |
| 550A.2 | Create `DormancyCheckHandler` + update job | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/DormancyCheckHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/DormancyScheduledJob.java` (modify) | 550A.8 | batch 1 migration pattern | `jobType()` = `"dormancy_check"`. Extract per-tenant dormancy check logic. Daily 2:00 UTC. |
| 550A.3 | Create `ProposalExpiryHandler` + update processor | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalExpiryHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalExpiryProcessor.java` (modify) | 550A.8 | batch 1 migration pattern | `jobType()` = `"proposal_expiry"`. Extract per-tenant proposal expiry logic. Hourly. |
| 550A.4 | Create `AcceptanceExpiryHandler` + update processor | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceExpiryHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceExpiryProcessor.java` (modify) | 550A.8 | batch 1 migration pattern | `jobType()` = `"acceptance_expiry"`. Extract per-tenant acceptance expiry logic. Hourly. |
| 550A.5 | Create `MagicLinkCleanupHandler` + update service | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkCleanupHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkCleanupService.java` (modify) | 550A.8 | batch 1 migration pattern | `jobType()` = `"magic_link_cleanup"`. Extract per-tenant magic link cleanup logic. Hourly. |
| 550A.6 | Create `AiGateExpiryHandler` + update service | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiGateExpiryHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateService.java` (modify) | 550A.8 | batch 1 migration pattern | `jobType()` = `"ai_gate_expiry"`. Extract per-tenant AI gate expiry logic from `expireStaleGates()`. Hourly. |
| 550A.7 | Create `FieldDateScanHandler` + update job | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/FieldDateScanHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/FieldDateScannerJob.java` (modify) | 550A.8 | batch 1 migration pattern | `jobType()` = `"field_date_scan"`. Extract per-tenant field date scanning logic. Daily 6:00 UTC. |
| 550A.8 | Integration tests for batch 2 (first 7) | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/SchedulerMigrationBatch2ATest.java` | ~3 tests: (1) all 7 handlers registered with correct jobTypes; (2) all 7 handlers execute without error when enqueued; (3) 12 total handlers in registry (5 batch 1 + 7 batch 2a) | integration test with job queue enabled | Verify handler registration and basic execution. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 550B.1 | Create `RequestReminderHandler` + update scheduler | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestReminderHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestReminderScheduler.java` (modify) | 550B.7 | batch 1 migration pattern | `jobType()` = `"request_reminder_check"`. Extract per-tenant request reminder logic. 6-hour interval. |
| 550B.2 | Create `TrialExpiryHandler` + update job | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/TrialExpiryHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionExpiryJob.java` (modify) | 550B.7 | batch 1 migration pattern | `jobType()` = `"subscription_trial_expiry"`. Extract per-tenant trial expiry logic from `processTrialExpiry()`. Daily 3:00 UTC. Inject `JobEnqueuer` + `JobQueueProperties` into `SubscriptionExpiryJob`. |
| 550B.3 | Create `GraceExpiryHandler` + update job | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/GraceExpiryHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionExpiryJob.java` (modify -- same file) | 550B.7 | batch 1 migration pattern | `jobType()` = `"subscription_grace_expiry"`. Extract per-tenant grace expiry logic from `processGraceExpiry()`. Daily 3:05 UTC. |
| 550B.4 | Create `CancellationEndHandler` + update job | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/CancellationEndHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionExpiryJob.java` (modify -- same file) | 550B.7 | batch 1 migration pattern | `jobType()` = `"subscription_cancellation_end"`. Extract per-tenant cancellation end logic from `processPendingCancellationEnd()`. Daily 3:10 UTC. |
| 550B.5 | Create `CourtDateReminderHandler` + update job | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtDateReminderHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtDateReminderJob.java` (modify) | 550B.7 | batch 1 migration pattern | `jobType()` = `"court_date_reminder"`. Extract per-tenant court date reminder logic. Daily 6:00 UTC. |
| 550B.6 | Create `AiInvocationExpiryHandler` + update sweeper; Create `PortalDigestHandler` + update scheduler | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/invocation/AiInvocationExpiryHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/invocation/AiInvocationExpirySweeper.java` (modify), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestHandler.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestScheduler.java` (modify) | 550B.7 | batch 1 migration pattern | `AiInvocationExpiryHandler.jobType()` = `"ai_invocation_expiry"`. Daily 3:00 UTC. `PortalDigestHandler.jobType()` = `"portal_digest"`. Weekly Mon 8:00 UTC. |
| 550B.7 | Integration tests for batch 2 (remaining 7) + full registry validation | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/SchedulerMigrationBatch2BTest.java` | ~4 tests: (1) all 7 batch 2b handlers registered correctly; (2) all 19 handlers present in `JobHandlerRegistry`; (3) `SubscriptionExpiryJob` has 3 distinct handlers; (4) no duplicate jobType strings across all handlers | integration test | Complete registry validation. All 19 job types accounted for. |
| 550B.8 | Remove dual-mode code from batch 1 schedulers | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationScheduler.java` (modify), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncWorker.java` (modify), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingPaymentPollWorker.java` (modify), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/TimeReminderScheduler.java` (modify) | verified by CI `./mvnw verify` | -- | Remove `if (isDualMode(...))` blocks and the `tenantScopedRunner.forEachTenant(...)` old-path code from all 5 batch 1 schedulers. Scheduler methods now contain only: `jobEnqueuer.fanOutToAllTenants(jobType)`. Clean up unused `TenantScopedRunner` imports if no other code in the class uses it. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 550C.1 | Create `JobQueueAdminController` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueAdminController.java` | 550C.3 | existing admin controllers; architecture Section 75.7.1 | `@RestController @RequestMapping("/api/admin/jobs")`. Platform-admin only (check `RequestScopes.isPlatformAdmin()` or use existing `PlatformAdminFilter`). Endpoints: `GET /api/admin/jobs?status=DEAD_LETTER&jobType=...&limit=50` (paginated list); `POST /api/admin/jobs/{id}/retry` (reset to PENDING, clear retryCount -- DEAD_LETTER only); `DELETE /api/admin/jobs/{id}` (hard delete -- DEAD_LETTER only); `GET /api/admin/jobs/stats` (counts by status and jobType). Delegates to a `JobQueueAdminService` for each operation. Thin controller per CLAUDE.md convention. |
| 550C.2 | Create `JobQueueAdminService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueAdminService.java` | 550C.3 | existing service patterns | `@Service`. Methods: `listJobs(JobStatus, String jobType, int limit)` returns paginated jobs; `retryJob(UUID id)` validates job is DEAD_LETTER, resets status/retryCount/claimedBy/claimedAt, sets nextAttemptAt to now; `deleteJob(UUID id)` validates DEAD_LETTER, hard deletes; `getStats()` returns aggregated counts by status and jobType. |
| 550C.3 | Integration tests for admin API | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueAdminControllerIntegrationTest.java` | ~6 tests: (1) list dead-lettered jobs returns correct results; (2) retry resets job to PENDING; (3) retry non-DEAD_LETTER job returns 400; (4) delete removes DEAD_LETTER job; (5) delete non-DEAD_LETTER returns 400; (6) stats endpoint returns correct counts | MockMvc with platform-admin JWT | Uses `TestJwtFactory` with platform-admin group. Seeds job_queue rows directly for test setup. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/DormancyCheckHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalExpiryHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceExpiryHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkCleanupHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiGateExpiryHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/FieldDateScanHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestReminderHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/TrialExpiryHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/GraceExpiryHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/CancellationEndHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtDateReminderHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/invocation/AiInvocationExpiryHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueAdminController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueAdminService.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleExecutor.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/DormancyScheduledJob.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalExpiryProcessor.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceExpiryProcessor.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkCleanupService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/FieldDateScannerJob.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestReminderScheduler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionExpiryJob.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtDateReminderJob.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/invocation/AiInvocationExpirySweeper.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestScheduler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationScheduler.java` (remove dual-mode)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncWorker.java` (remove dual-mode)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingPaymentPollWorker.java` (remove dual-mode)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/TimeReminderScheduler.java` (remove dual-mode)

### Architecture Decisions

- **Three sub-slices for 14 handlers** -- 550A (7 handlers) and 550B (7 handlers) each stay within the 12-file budget. 550C is the admin API. Splitting by daily/hourly grouping keeps each slice's diff coherent.
- **Admin API is platform-admin only** -- Secured via `RequestScopes.isPlatformAdmin()`. No tenant scope needed (job_queue is in `public` schema).
- **Retry is DEAD_LETTER only** -- Prevents accidental re-execution of COMPLETED or in-flight jobs. Only dead-lettered jobs can be manually retried.
- **Dual-mode removal in 550B.8** -- By the time batch 2 lands, batch 1 has been verified through batch 2's implementation. Safe to remove the old code paths.

### Non-scope

- No metrics (lands in 555A).
- No frontend dashboard for job queue -- admin API only.

---

## Epic 551: Shard Config + Registry + Migration

**Goal**: Create the V25 global migration that adds the `public.shard_config` table and extends `org_schema_mapping` with a `shard_id` column, build the `ShardConfig` JPA entity, the `ShardAndSchema` record for composite identifier parsing, update the `OrgSchemaMapping` entity with the shard field, and implement the `ShardRegistry` service that manages named DataSource instances. After this epic, the system knows about shards but does not route to them yet.

**References**: Architecture Section 75.4 (Shard-Aware DB Resolver -- Data Model), Section 75.5.1 (`ShardRegistry`), Section 75.8.2 (V25 Migration), Section 75.10.1 (Implementation Guidance).

**Dependencies**: None (first epic in the sharding track, runs in parallel with job queue track).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **551A** | 551A.1-551A.5 | ~6 backend files (1 migration + 1 entity + 1 record + 1 entity modification + 1 repository modification + 1 test) | V25 global migration (shard_config table + org_schema_mapping extension); `ShardConfig` entity; `ShardAndSchema` record; `OrgSchemaMapping` shard_id field; `OrgSchemaMappingRepository.findByShardId()`. |
| **551B** | 551B.1-551B.4 | ~5 backend files (1 interface + 1 implementation + 1 repository + 1 config file modification + 1 test) | `ShardRegistry` interface; `DefaultShardRegistry` (DataSource lifecycle from env vars); `ShardConfigRepository`; sharding config in `application.yml`; unit tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 551A.1 | Create V25 global migration | `backend/src/main/resources/db/migration/global/V25__create_shard_config.sql` | verified by 551A.5 | existing `V24__create_job_queue.sql` (from 547A) for format | SQL verbatim from architecture Section 75.8.2: `CREATE TABLE IF NOT EXISTS public.shard_config` (9 columns); seed `INSERT INTO public.shard_config (shard_id, display_name) VALUES ('primary', 'Primary Database') ON CONFLICT DO NOTHING`; `ALTER TABLE public.org_schema_mapping ADD COLUMN IF NOT EXISTS shard_id VARCHAR(50) NOT NULL DEFAULT 'primary'`; FK constraint `fk_org_schema_mapping_shard` (idempotent via `DO $$ IF NOT EXISTS` block); `CREATE INDEX IF NOT EXISTS idx_org_schema_mapping_shard ON public.org_schema_mapping (shard_id)`. Comments on table and columns. |
| 551A.2 | Create `ShardConfig` entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardConfig.java` | covered by 551A.5 | existing `OrgSchemaMapping` entity for `@Table(schema = "public")` pattern | `@Entity @Table(name = "shard_config", schema = "public")`. Fields: `shardId` (String, PK -- `@Id` with no generation, assigned by admin), `displayName`, `jdbcUrl`, `username`, `poolSize` (int, default 25), `readOnly` (boolean, default false), `active` (boolean, default true), `createdAt`, `updatedAt`. No Lombok. Standard getters/setters. Protected no-arg constructor. |
| 551A.3 | Create `ShardAndSchema` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardAndSchema.java` | covered by 551A.5 | architecture Section 75.5.2 | `public record ShardAndSchema(String shardId, String schemaName)`. Static method `parse(String composite)`: splits on first colon, validates shard ID format (`^[a-z][a-z0-9_]{0,48}[a-z0-9]$` or `primary`), validates schema name (`^tenant_[0-9a-f]{12}$` or `public`), throws `IllegalArgumentException` on invalid format. Static method `format(String shardId, String schemaName)`: returns `shardId + ":" + schemaName`. Constant: `DEFAULT = new ShardAndSchema("primary", "public")`. |
| 551A.4 | Extend `OrgSchemaMapping` entity + repository | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMapping.java` (modify), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMappingRepository.java` (modify) | covered by 551A.5 | existing entity field patterns | Add `@Column(name = "shard_id", nullable = false) private String shardId = "primary"` field to `OrgSchemaMapping`. Add getter `getShardId()`. Update constructor to accept optional `shardId` parameter (overloaded: existing 2-arg constructor defaults to `"primary"`; new 3-arg constructor accepts `shardId`). Add `findByShardId(String shardId)` to repository: `List<OrgSchemaMapping> findByShardId(String shardId)`. |
| 551A.5 | Integration test for V25 migration + entity round-trips | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardConfigMigrationTest.java` | ~6 tests: (1) V25 migration runs clean; (2) `shard_config` table contains `primary` seed row; (3) `org_schema_mapping.shard_id` defaults to `primary` for existing mappings; (4) `ShardConfig` entity round-trip; (5) `ShardAndSchema.parse()` round-trip with valid composite identifiers; (6) `ShardAndSchema.parse()` rejects invalid formats (missing colon, invalid shard chars, invalid schema) | standard integration test | No tenant provisioning needed -- both tables are in `public` schema. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 551B.1 | Create `ShardConfigRepository` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardConfigRepository.java` | covered by 551B.4 | existing repository patterns | `JpaRepository<ShardConfig, String>`. Method: `List<ShardConfig> findByActiveTrue()`. |
| 551B.2 | Create `ShardRegistry` interface + `DefaultShardRegistry` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardRegistry.java` (new), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/DefaultShardRegistry.java` (new) | 551B.4 | architecture Section 75.5.1 | Interface: `DataSource getDataSource(String shardId)`, `DataSource getPrimaryDataSource()`, `Set<String> getActiveShardIds()`, `void refresh()`. Implementation: `@Component @ConditionalOnProperty(name = "kazi.sharding.enabled", havingValue = "true")`. Constructor: takes primary `DataSource` (Spring-managed), `ShardConfigRepository`, `Environment` (for env var lookup). At startup (`@PostConstruct`): reads active shard configs, for each non-primary shard: resolve env vars `KAZI_SHARD_{SHARD_ID_UPPER}_URL`, `_USERNAME`, `_PASSWORD`. If env vars missing: log WARN and skip shard. Otherwise: create `HikariDataSource` with `poolSize` from config. Store in `ConcurrentHashMap<String, DataSource>`. Primary shard always maps to the injected Spring DataSource. `getDataSource()` throws `IllegalArgumentException` for unknown/inactive shards. |
| 551B.3 | Add sharding configuration to `application.yml` + `application-test.yml` | `backend/src/main/resources/application.yml` (modify), `backend/src/test/resources/application-test.yml` (modify) | covered by 551B.4 | architecture Section 75.9.2 | Add to `application.yml` under `kazi:`: `sharding: enabled: true, control-plane-datasource: primary`. Add to `application-test.yml` under `kazi:`: `sharding: enabled: false`. |
| 551B.4 | Unit/integration tests for `ShardRegistry` + `ShardAndSchema` | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardRegistryTest.java` | ~5 tests: (1) startup with primary only -- `getActiveShardIds()` returns `{"primary"}`; (2) `getPrimaryDataSource()` returns the Spring-managed DataSource; (3) `getDataSource("primary")` returns the primary DataSource; (4) `getDataSource("unknown")` throws `IllegalArgumentException`; (5) `findByShardId("primary")` returns all mappings | integration test with `@TestPropertySource(properties = "kazi.sharding.enabled=true")` | Multi-shard DataSource creation tested only when env vars are configured. Single-shard (primary only) is the default test case. |

### Key Files

**Create (backend):**
- `backend/src/main/resources/db/migration/global/V25__create_shard_config.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardConfig.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardAndSchema.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardConfigRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardRegistry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/DefaultShardRegistry.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardConfigMigrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardRegistryTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMapping.java` -- add `shardId` field + getter + constructor overload
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMappingRepository.java` -- add `findByShardId()`
- `backend/src/main/resources/application.yml` -- add `kazi.sharding` config block
- `backend/src/test/resources/application-test.yml` -- add `kazi.sharding` test config

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMapping.java` -- entity to extend
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/SchemaMultiTenantConnectionProvider.java` -- existing connection provider (schema validation pattern)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/FlywayConfig.java` -- global Flyway uses `migrationDataSource`

### Architecture Decisions

- **Control plane / shard plane split** ([ADR-295](../architecture/phase75-scalability-job-queue-sharding.md#adr-295-control-plane--shard-plane-database-split)) -- `shard_config`, `org_schema_mapping`, `shedlock`, and `job_queue` stay on the control plane (primary shard's `public` schema). Tenant schemas can reside on any shard.
- **Explicit shard assignment** ([ADR-297](../architecture/phase75-scalability-job-queue-sharding.md#adr-297-explicit-shard-assignment-over-automatic-placement)) -- Shard ID stored per tenant in `org_schema_mapping.shard_id`. Platform admin assigns during provisioning. No automated placement algorithm.
- **Credentials from env vars, not DB** -- `shard_config.jdbc_url` and `username` are metadata only. Actual credentials from `KAZI_SHARD_{ID}_*` environment variables. Prevents secret leakage through the database.
- **Primary shard reuses Spring DataSource** -- No new `HikariDataSource` for the primary shard. The existing Spring Boot-managed DataSource is used directly, preserving all existing pool configuration.

### Non-scope

- No connection provider changes (lands in 552).
- No request scope changes (lands in 553).
- No provisioning changes (lands in 554).

---

## Epic 552: Shard-Aware Connection Provider

**Goal**: Build the `ShardAwareConnectionProvider` that replaces `SchemaMultiTenantConnectionProvider` when sharding is enabled, update `TenantIdentifierResolver` to return the composite `{shardId}:{schemaName}` format, and wire conditional provider selection in `HibernateMultiTenancyConfig`. After this epic, Hibernate connections are resolved through the shard-aware provider, routing to the correct DataSource based on the composite tenant identifier.

**References**: Architecture Section 75.5.2 (`ShardAwareConnectionProvider`), Section 75.5.3 (`TenantIdentifierResolver` changes), Section 75.10.1 (Implementation Guidance -- conditional wiring).

**Dependencies**: Epic 551 (ShardConfig entity, ShardRegistry, ShardAndSchema record).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **552A** | 552A.1-552A.3 | ~4 backend files (1 new provider + 1 modified config + 1 modified existing provider + 1 test) | `ShardAwareConnectionProvider` implementing `MultiTenantConnectionProvider<String>`; conditional wiring in `HibernateMultiTenancyConfig`; `@ConditionalOnProperty` on existing `SchemaMultiTenantConnectionProvider`. |
| **552B** | 552B.1-552B.3 | ~3 backend files (1 modified resolver + 1 modified config + 1 test) | `TenantIdentifierResolver` composite identifier format (when sharding enabled); backward compatibility (schema-only when sharding disabled); single-shard characterization test. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 552A.1 | Create `ShardAwareConnectionProvider` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardAwareConnectionProvider.java` | 552A.3 | existing `SchemaMultiTenantConnectionProvider.java` for full `MultiTenantConnectionProvider<String>` interface implementation | `@Component @ConditionalOnProperty(name = "kazi.sharding.enabled", havingValue = "true")`. Implements `MultiTenantConnectionProvider<String>`. Constructor: `ShardRegistry shardRegistry`. `getAnyConnection()`: `shardRegistry.getPrimaryDataSource().getConnection()`. `getConnection(tenantIdentifier)`: parse via `ShardAndSchema.parse(tenantIdentifier)`, get DataSource via `shardRegistry.getDataSource(parsed.shardId())`, get connection, set search_path via `SET search_path TO {sanitizedSchema}`. `releaseConnection()`: reset search_path to `public`, close connection. `getReadOnlyConnection()` / `releaseReadOnlyConnection()`: same as existing provider pattern. `sanitizeSchema()`: same regex validation as existing provider (`^tenant_[0-9a-f]{12}$` or `public`). |
| 552A.2 | Conditional wiring in `HibernateMultiTenancyConfig` + annotate existing provider | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/HibernateMultiTenancyConfig.java` (modify), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/SchemaMultiTenantConnectionProvider.java` (modify) | 552A.3 | architecture Section 75.10.1 (exact conditional wiring) | Add `@ConditionalOnProperty(name = "kazi.sharding.enabled", havingValue = "false", matchIfMissing = true)` to `SchemaMultiTenantConnectionProvider`. Change `HibernateMultiTenancyConfig.multiTenancyCustomizer()` parameter from `SchemaMultiTenantConnectionProvider` to `MultiTenantConnectionProvider<String>` -- Spring selects the active bean based on the property. The `TenantIdentifierResolver` parameter stays as-is (same bean, behavior changes internally). |
| 552A.3 | Integration test for shard-aware connection provider | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardAwareConnectionProviderTest.java` | ~4 tests: (1) with sharding enabled + primary only: `getConnection("primary:tenant_test")` sets correct search_path; (2) `getConnection("primary:public")` sets search_path to public; (3) invalid composite identifier throws `IllegalArgumentException`; (4) release resets search_path to public | `@TestPropertySource(properties = "kazi.sharding.enabled=true")` | Tests use primary shard only (single embedded Postgres). Multi-shard routing tested in 555B. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 552B.1 | Update `TenantIdentifierResolver` for composite format | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantIdentifierResolver.java` (modify) | 552B.3 | architecture Section 75.5.3 | Inject `@Value("${kazi.sharding.enabled:false}") boolean shardingEnabled`. When `shardingEnabled = true`: `resolveCurrentTenantIdentifier()` returns `ShardAndSchema.format(shardId, schemaName)` where `shardId` comes from `RequestScopes.SHARD_ID` (if bound) or `"primary"`, and `schemaName` from `RequestScopes.TENANT_ID` (if bound) or `"public"`. Default becomes `"primary:public"`. When `shardingEnabled = false`: behavior unchanged (returns schema name only, or `"public"`). `isRoot()`: when sharding enabled, check `ShardAndSchema.DEFAULT.format()` (`"primary:public"`). |
| 552B.2 | Add conditional `isRoot()` handling | included in 552B.1 | 552B.3 | -- | `isRoot(String tenantId)`: when sharding enabled, returns `"primary:public".equals(tenantId)`. When disabled, returns `"public".equals(tenantId)`. |
| 552B.3 | Single-shard characterization test | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/SingleShardCharacterizationTest.java` | ~3 tests: (1) enable sharding with primary only, provision tenant, CRUD operations work identically; (2) `TenantIdentifierResolver` returns `"primary:tenant_xxx"` format; (3) existing entity queries return correct results with composite identifier | `@TestPropertySource(properties = "kazi.sharding.enabled=true")` | Critical backward-compatibility gate: all existing functionality works with sharding enabled but only primary shard configured. Provision a tenant, create a project, verify CRUD. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardAwareConnectionProvider.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardAwareConnectionProviderTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/SingleShardCharacterizationTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/HibernateMultiTenancyConfig.java` -- inject `MultiTenantConnectionProvider<String>` (interface) instead of concrete class
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/SchemaMultiTenantConnectionProvider.java` -- add `@ConditionalOnProperty` annotation
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantIdentifierResolver.java` -- composite identifier logic

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/SchemaMultiTenantConnectionProvider.java` -- existing connection provider to match
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantIdentifierResolver.java` -- current resolver to extend

### Architecture Decisions

- **Composite tenant identifier** ([ADR-296](../architecture/phase75-scalability-job-queue-sharding.md#adr-296-composite-tenant-identifier-format)) -- Hibernate receives `{shardId}:{schemaName}` as a single string. The connection provider parses both components. Self-contained, no dependency on ScopedValue from within Hibernate.
- **Conditional bean selection** -- `@ConditionalOnProperty` on both providers ensures exactly one is active. `HibernateMultiTenancyConfig` injects the shared `MultiTenantConnectionProvider<String>` interface.
- **Backward compatibility via property toggle** -- `kazi.sharding.enabled=false` (the default when missing) uses the legacy `SchemaMultiTenantConnectionProvider`. Zero risk to existing deployments.

### Non-scope

- No ScopedValue changes (lands in 553).
- No provisioning changes (lands in 554).
- No multi-shard integration tests (lands in 555B).

---

## Epic 553: Shard-Aware Request Scopes + Filter + Runner

**Goal**: Thread the `SHARD_ID` ScopedValue through the HTTP request path (`TenantFilter`), the scheduled job iteration path (`TenantScopedRunner`), and update the tenant cache in `TenantFilter` to include shard information. After this epic, every code path that binds tenant scope also binds shard scope, enabling the `TenantIdentifierResolver` to construct the composite identifier.

**References**: Architecture Section 75.5.4 (`RequestScopes` extension), Section 75.5.5 (`TenantFilter` changes), Section 75.5.6 (`TenantScopedRunner` changes).

**Dependencies**: Epic 551 (OrgSchemaMapping.shard_id), Epic 552 (ShardAwareConnectionProvider + TenantIdentifierResolver consume SHARD_ID).

**Scope**: Backend only

**Estimated Effort**: S

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **553A** | 553A.1-553A.5 | ~5 backend files (3 modified + 1 new record + 1 test) | `RequestScopes.SHARD_ID` ScopedValue + `runForTenantOnShard()`; `TenantFilter` cache change; `TenantScopedRunner` shard binding; `TenantMapping` record. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 553A.1 | Add `SHARD_ID` ScopedValue + `runForTenantOnShard()` to `RequestScopes` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` (modify) | 553A.5 | existing `runForTenant()` method pattern | Add `public static final ScopedValue<String> SHARD_ID = ScopedValue.newInstance()`. Add method `public static void runForTenantOnShard(String tenantId, @Nullable String orgId, @Nullable String shardId, Runnable action)`. Modifies `bindTenantScope()` to accept optional `shardId` and chain `carrier.where(SHARD_ID, shardId)` when non-null/non-blank. Also add `callForTenantOnShard()` variant. Update `runForTenantWithMember()` to accept optional `shardId` if needed. |
| 553A.2 | Create `TenantMapping` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantMapping.java` | 553A.5 | Java record pattern | `public record TenantMapping(String schemaName, String orgId, String shardId) {}`. Used as the cache value type in `TenantFilter`. Replaces the previous `Cache<String, String>` (orgId -> schemaName) with `Cache<String, TenantMapping>` (orgId -> full mapping). |
| 553A.3 | Update `TenantFilter` cache + shard binding | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java` (modify) | 553A.5 | existing `TenantFilter.java` | Change `tenantCache` from `Cache<String, String>` to `Cache<String, TenantMapping>`. Update `resolveTenant()` to return `TenantMapping` instead of `String`. Update `lookupTenant()` to load `OrgSchemaMapping` and extract `schemaName`, `externalOrgId`, `shardId` into `TenantMapping`. Update `doFilterInternal()`: on cache hit, bind `TENANT_ID` (schemaName), `ORG_ID` (orgId), and `SHARD_ID` (shardId) in the `ScopedValue.Carrier`. Critical: without this change, cached requests would never have `SHARD_ID` bound, silently routing to primary shard. |
| 553A.4 | Update `TenantScopedRunner.forEachTenant()` shard binding | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java` (modify) | 553A.5 | existing `forEachTenant()` | Update the loop body to read `mapping.getShardId()` and call `RequestScopes.runForTenantOnShard(tenantId, orgId, shardId, () -> action.accept(tenantId, orgId))` instead of `RequestScopes.runForTenant(tenantId, orgId, ...)`. This ensures non-migrated schedulers (if any remain) and any other `forEachTenant()` callers get shard awareness. |
| 553A.5 | Integration tests for shard scope propagation | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardScopePropagationTest.java` | ~5 tests: (1) `runForTenantOnShard()` binds `SHARD_ID`; (2) `TenantFilter` resolves and binds `SHARD_ID` from `OrgSchemaMapping`; (3) `TenantFilter` cache hit includes `SHARD_ID` binding; (4) `TenantScopedRunner.forEachTenant()` binds `SHARD_ID` per iteration; (5) nested `runForTenantOnShard()` calls correctly rebind `SHARD_ID` | integration test with sharding enabled | Provision a tenant, make HTTP request, verify `RequestScopes.SHARD_ID.get()` == `"primary"` within request scope. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantMapping.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardScopePropagationTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` -- add `SHARD_ID`, `runForTenantOnShard()`, update `bindTenantScope()`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java` -- cache type change, shard binding in filter chain
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java` -- shard binding per iteration

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` -- current ScopedValue definitions
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java` -- current cache implementation
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ScopedFilterChain.java` -- ScopedValue binding in filter chain

### Architecture Decisions

- **TenantFilter cache includes shard_id** -- Critical correctness decision. Without caching the shard_id, every cache hit would leave `SHARD_ID` unbound, silently routing to the primary shard. The `TenantMapping` record captures all three values (schemaName, orgId, shardId) in a single cache entry.
- **`runForTenantOnShard()` as extension, not replacement** -- The existing `runForTenant()` remains for backward compatibility. `runForTenantOnShard()` adds the optional shard parameter. `TenantScopedRunner` uses the new method internally.
- **Single slice** -- This epic touches only 3 modified files + 1 new record + 1 test. Well within the 6-12 file budget for a single slice.

### Non-scope

- No connection provider changes (already in 552).
- No provisioning changes (lands in 554).

---

## Epic 554: Shard-Aware Provisioning + Flyway

**Goal**: Update `TenantProvisioningService` to accept a `shardId` parameter and create tenant schemas on the target shard's DataSource, update `TenantMigrationRunner` to iterate all active shards and run Flyway migrations per shard, and extend the provisioning API to accept the optional `shardId` field.

**References**: Architecture Section 75.6 (Shard-Aware Tenant Provisioning), Section 75.5.7 (Shard-aware Flyway), Section 75.7.2 (Provisioning API Extension).

**Dependencies**: Epic 551 (ShardRegistry), Epic 552 (ShardAwareConnectionProvider), Epic 553 (RequestScopes.SHARD_ID for scope binding after provisioning).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **554A** | 554A.1-554A.4 | ~5 backend files (2 modified services + 1 modified controller + 1 modified DTO/record + 1 test) | `TenantProvisioningService` shard parameter + shard DataSource for schema creation/migration/seeding; provisioning API `shardId` field; validation via `ShardRegistry`. |
| **554B** | 554B.1-554B.3 | ~3 backend files (1 modified runner + 1 test + 1 test utility) | `TenantMigrationRunner` shard iteration (per-shard Flyway); integration test with two embedded Postgres instances. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 554A.1 | Update `TenantProvisioningService` for shard awareness | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` (modify) | 554A.4 | existing `provisionTenant()` method | Add optional `shardId` parameter to `provisionTenant()` (overload existing method; default `null` -> `"primary"`). Inject `ShardRegistry` (optional -- `ObjectProvider<ShardRegistry>` to handle `kazi.sharding.enabled=false`). When `shardId` is non-null and sharding is enabled: (1) validate shard exists and is active via `shardRegistry.getActiveShardIds()`, throw `InvalidStateException` if invalid; (2) resolve DataSource via `shardRegistry.getDataSource(shardId)` for `createSchema()` and `runTenantMigrations()`; (3) pass `shardId` to `OrgSchemaMapping` constructor (3-arg). When sharding disabled or `shardId` is null: existing behavior unchanged (uses `migrationDataSource`). The `createSchema()` and `runTenantMigrations()` private methods gain a `DataSource` parameter (instead of always using `migrationDataSource`). |
| 554A.2 | Update provisioning controller/API for `shardId` field | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/ProvisioningController.java` (modify) | 554A.4 | existing provisioning endpoint | Add optional `shardId` field to the provisioning request DTO. Pass through to `TenantProvisioningService.provisionTenant()`. If `shardId` references an inactive or non-existent shard, the service throws `InvalidStateException` which renders as 400 ProblemDetail. |
| 554A.3 | Update `OrgSchemaMapping` creation to include `shardId` | included in 554A.1 | 554A.4 | -- | The `createMapping()` private method now uses the 3-arg `OrgSchemaMapping(externalOrgId, schemaName, shardId)` constructor when `shardId` is non-null. |
| 554A.4 | Integration tests for shard-aware provisioning | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/ShardAwareProvisioningTest.java` | ~4 tests: (1) provision tenant with default shard (no `shardId`) -- `org_schema_mapping.shard_id` = `"primary"`; (2) provision with explicit `shardId = "primary"` -- same result; (3) provision with invalid shard -- 400 error with ProblemDetail; (4) provisioning API accepts `shardId` in request body | integration test with sharding enabled | Tests use primary shard only (single embedded Postgres). Multi-shard provisioning verified in 555B when a second DataSource is available. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 554B.1 | Update `TenantMigrationRunner` for shard iteration | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantMigrationRunner.java` (modify) | 554B.3 | existing `run()` method | Inject `ShardRegistry` (optional -- `ObjectProvider<ShardRegistry>`). When sharding enabled: (1) read active shard IDs from `ShardRegistry`; (2) for each shard: get DataSource, query `OrgSchemaMappingRepository.findByShardId(shardId)` for tenant schemas on that shard; (3) for each schema: run Flyway migration using the shard's DataSource. When sharding disabled: existing behavior unchanged (all schemas migrated against `migrationDataSource`). Log per-shard migration results: `"Migrated shard {} -- {} schemas, {} migrations applied"`. |
| 554B.2 | Create test utility for second embedded Postgres | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/testutil/SecondaryEmbeddedPostgres.java` | used by 554B.3 and 555B | existing `TestcontainersConfiguration.java` for embedded Postgres pattern | Utility that starts a second embedded Postgres instance on a different port. Provides a `DataSource` that can be registered with `ShardRegistry` for multi-shard tests. Uses zonky embedded Postgres API (same as primary). Important: this is NOT Testcontainers -- it's a second embedded Postgres binary instance. |
| 554B.3 | Integration tests for shard-aware Flyway | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/ShardAwareFlywayTest.java` | ~3 tests: (1) single shard: migration runs as before; (2) with ShardRegistry returning primary only: all schemas migrated; (3) verify `TenantMigrationRunner` queries schemas per shard (mocked `ShardRegistry` with `findByShardId()` assertions) | integration test | Full multi-shard Flyway test (with real second embedded Postgres) deferred to 555B. This slice tests the iteration logic with a single shard and verifies the per-shard query pattern. |

### Key Files

**Create (backend):**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/testutil/SecondaryEmbeddedPostgres.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/ShardAwareProvisioningTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/ShardAwareFlywayTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` -- add `shardId` parameter, shard DataSource resolution
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/ProvisioningController.java` -- add `shardId` to request DTO
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantMigrationRunner.java` -- shard iteration logic

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` -- current provisioning flow
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantMigrationRunner.java` -- current migration runner
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/FlywayConfig.java` -- global Flyway (unchanged)

### Architecture Decisions

- **Optional `ShardRegistry` injection** -- `ObjectProvider<ShardRegistry>` handles the case where sharding is disabled (no `ShardRegistry` bean exists). All shard-aware code paths check `shardRegistry != null` before using it.
- **Flyway per shard** -- Tenant migrations are the same SQL files for all shards. Only the DataSource differs. Global migrations always run on the primary shard (control plane) via `FlywayConfig` (unchanged).
- **Provisioning API backward compatible** -- `shardId` is optional in the request body. Omitted or null defaults to `"primary"`. Existing API clients are unaffected.

### Non-scope

- No multi-shard integration tests with real second database (lands in 555B).
- No shard management admin UI.

---

## Epic 555: Integration Tests + Observability

**Goal**: Build the cross-cutting observability infrastructure (Micrometer metrics for job queue and shard pools, health indicators), implement the end-to-end integration tests that verify both tracks working together (job queue execution against a non-primary shard), shard isolation tests, and the single-shard characterization test that gates backward compatibility.

**References**: Architecture Section 75.8 (Observability), Section 75.12.1 (Job Queue Metrics), Section 75.12.2 (Shard Metrics), Section 75.12.3 (Health Check Extensions), Section 75.9 (Test Strategy).

**Dependencies**: Epic 550 (all schedulers migrated), Epic 554 (shard-aware provisioning + Flyway).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **555A** | 555A.1-555A.4 | ~5 backend files (2 metrics classes + 2 health indicators + 1 test) | `JobQueueMetrics` (counters, gauges, histograms); `ShardMetrics` (per-shard pool metrics); `ShardHealthIndicator`; `JobQueueHealthIndicator`. |
| **555B** | 555B.1-555B.4 | ~4 backend files (3 test files + 1 test utility reuse) | End-to-end integration test (enqueue -> claim -> execute on shard 2); shard isolation test; single-shard characterization (`./mvnw verify` with sharding enabled + primary only). |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 555A.1 | Create `JobQueueMetrics` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueMetrics.java` | 555A.4 | existing Prometheus metrics patterns (if any from Tier A) | `@Component @ConditionalOnProperty(name = "kazi.job-queue.enabled", havingValue = "true")`. Constructor: `MeterRegistry`, `JobQueueRepository`. Registers: `kazi_job_queue_enqueued_total` (Counter, label `job_type`), `kazi_job_queue_completed_total`, `kazi_job_queue_failed_total`, `kazi_job_queue_dead_letter_total`, `kazi_job_queue_pending_count` (Gauge, polled via `@Scheduled(fixedRate = 30_000)`), `kazi_job_queue_claimed_count` (Gauge), `kazi_job_queue_execution_seconds` (Timer/Histogram), `kazi_job_queue_claim_wait_seconds` (Timer/Histogram). The counters are incremented by `JobWorker` (inject `JobQueueMetrics` into worker). Gauges poll the database periodically. |
| 555A.2 | Create `ShardMetrics` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardMetrics.java` | 555A.4 | architecture Section 75.12.2 | `@Component @ConditionalOnProperty(name = "kazi.sharding.enabled", havingValue = "true")`. Constructor: `MeterRegistry`, `ShardRegistry`, `OrgSchemaMappingRepository`. Registers per shard: `kazi_shard_connection_pool_active` (Gauge), `kazi_shard_connection_pool_idle` (Gauge), `kazi_shard_connection_pool_pending` (Gauge) -- read from HikariCP MBeans via `HikariPoolMXBean`. `kazi_shard_tenant_count` (Gauge) -- refreshed every 60s from `org_schema_mapping` count by shard_id. |
| 555A.3 | Create health indicators | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardHealthIndicator.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueHealthIndicator.java` | 555A.4 | Spring Boot `HealthIndicator` pattern | `ShardHealthIndicator @ConditionalOnProperty(name = "kazi.sharding.enabled")`: for each active shard, validate connection with `SELECT 1`. Report UP only if all active shards reachable. Individual shard status in health details. `JobQueueHealthIndicator @ConditionalOnProperty(name = "kazi.job-queue.enabled")`: report UP if job queue responding. Include stats (pending count, oldest pending age, dead letter count). Report DOWN if pending queue age exceeds configurable threshold. |
| 555A.4 | Integration tests for metrics + health | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/ObservabilityTest.java` | ~4 tests: (1) Prometheus metrics endpoint includes `kazi_job_queue_*` metrics; (2) health check endpoint includes job queue health; (3) health check includes shard health (when enabled); (4) `JobQueueMetrics` increments counters on enqueue/complete | integration test with both features enabled | Use `@TestPropertySource(properties = {"kazi.job-queue.enabled=true", "kazi.sharding.enabled=true"})`. Verify metrics registration and health endpoint inclusion. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 555B.1 | End-to-end integration test (multi-shard + job queue) | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/EndToEndMultiShardTest.java` | ~3 tests: (1) provision tenant on shard 2, enqueue job, worker executes against shard 2 DataSource; (2) verify CRUD operations on shard 2 tenant work correctly; (3) Flyway migrations run on both shards at startup | uses `SecondaryEmbeddedPostgres` from 554B.2 | Programmatically register a second embedded Postgres DataSource as `"test_shard_2"` in `ShardRegistry`. Insert `shard_config` row. Provision tenant on shard 2. Enqueue a test job. Verify the `TestJobHandler` executes with correct `SHARD_ID` binding. |
| 555B.2 | Shard isolation test | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardIsolationTest.java` | ~2 tests: (1) tenant on shard 1 cannot access schemas on shard 2; (2) crafted composite identifier with wrong shard routes to correct shard (data not found, not wrong shard's data) | uses `SecondaryEmbeddedPostgres` | Provision tenant A on primary, tenant B on test_shard_2. Attempt to read tenant B's data while scoped to tenant A's shard. Verify ResourceNotFoundException (not stale data from wrong shard). |
| 555B.3 | Single-shard full characterization test | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/SingleShardFullCharacterizationTest.java` | ~2 tests: (1) with `kazi.sharding.enabled=true` and only primary shard: provision tenant, full CRUD (project, document, task, customer), all operations succeed identically; (2) `TenantScopedRunner.forEachTenant()` binds shard for each tenant | integration test | This is the critical backward-compatibility gate. Every standard operation must work identically with sharding enabled but no additional shards configured. |
| 555B.4 | Verify `./mvnw verify` passes with sharding enabled | -- | meta-task: CI verification | -- | Not a code task. Verify that the full test suite passes when `kazi.sharding.enabled=true` is set globally (via system property or test profile override). Document the result. If any tests fail, fix them in this slice. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueMetrics.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardMetrics.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardHealthIndicator.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobQueueHealthIndicator.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/ObservabilityTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/EndToEndMultiShardTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardIsolationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/SingleShardFullCharacterizationTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobWorker.java` -- inject `JobQueueMetrics`, increment counters on claim/complete/fail

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/infrastructure/jobqueue/JobWorker.java` -- add metrics hooks
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/infrastructure/testutil/SecondaryEmbeddedPostgres.java` -- from 554B

### Architecture Decisions

- **Metrics are opt-in via `@ConditionalOnProperty`** -- Both `JobQueueMetrics` and `ShardMetrics` are only registered when their respective features are enabled. No metric pollution when features are off.
- **Gauges poll the database** -- `kazi_job_queue_pending_count` and `kazi_shard_tenant_count` are database queries, not in-memory counters. Polled every 30-60 seconds to avoid hot-path overhead. Counters (enqueued, completed, failed) are incremented in-memory by the worker.
- **Second embedded Postgres for multi-shard tests** -- Uses the same zonky embedded Postgres library as the primary. NOT Testcontainers. Consistent with the project's anti-Testcontainers policy.
- **Shard isolation test is a security gate** -- Verifies that a crafted tenant identifier cannot cross shard boundaries. This is defense-in-depth: the connection provider validates the composite identifier, and each shard's DataSource is isolated.

### Non-scope

- No performance benchmarks (out of scope for this phase -- tracked as future work).
- No frontend dashboard for metrics.
- No Grafana/CloudWatch dashboard configuration.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/SchemaMultiTenantConnectionProvider.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java`
