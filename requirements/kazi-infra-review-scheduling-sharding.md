# Infrastructure Review: Job Queue Scheduling & Database Sharding

**Date**: 2026-05-29
**Scope**: Review of two recently implemented infrastructure features — job queue fanout (replacing `@Scheduled` + `TenantScopedRunner`) and database sharding (multi-database schema-per-tenant).

---

## Part 1: Job Queue Scheduling

### Architecture Summary

The implementation replaces the old pattern of `@Scheduled` → `TenantScopedRunner.forEachTenant()` (sequential, single-threaded, O(N) tenants) with a true per-tenant fanout via a Postgres-backed job queue.

```
Old Pattern:
  @Scheduled → forEachTenant() → inline work per tenant (sequential, one pod)

New Pattern:
  @Scheduled → fanOutToAllTenants() → N rows in public.job_queue (one per tenant)
                                     → JobWorker claims + executes (parallel, multi-pod)
```

### What Works Well

| Aspect | Assessment |
|--------|-----------|
| **True distributed fanout** | Each tenant gets its own job row. N pods claim different tenants via `SELECT FOR UPDATE SKIP LOCKED` — zero lock contention. |
| **Database-level dedup** | Unique index `(job_type, tenant_id) WHERE status IN ('PENDING','CLAIMED')` prevents double-enqueue even under race conditions. |
| **Dual-mode migration** | `isDualMode` flag runs both old and new paths in parallel for shadow testing before cutover. Well-thought-out migration strategy. |
| **Retry with exponential backoff** | `2^retryCount * backoffBaseSeconds` (default 10s). Dead-letter after max retries. |
| **Stale claim recovery** | Hourly `StaleJobRecoveryTask` resets orphaned `CLAIMED` jobs (pod crash scenario) without incrementing retry count — correct semantics since the job never ran. |
| **Admin API** | Paginated listing, manual retry of dead-letters, stats by status/jobType. Good operational visibility. |
| **Lifecycle management** | `SmartLifecycle` with `phase = Integer.MAX_VALUE - 10` ensures the poll loop stops before DataSource closes on shutdown. |
| **Handler registration** | `JobHandlerRegistry` validates uniqueness of job type names at startup — fail-fast on misconfiguration. |

### Issues to Fix

#### Issue S1: `DefaultJobEnqueuer` is not gated by `kazi.job-queue.enabled` (Medium)

**File**: `infrastructure/jobqueue/DefaultJobEnqueuer.java`

**Problem**: `JobWorker` and `JobHandlerRegistry` are both `@ConditionalOnProperty(name = "kazi.job-queue.enabled", havingValue = "true")`, but `DefaultJobEnqueuer` is not. If a scheduler calls `fanOutToAllTenants()` while `kazi.job-queue.enabled=false`, rows are written to `public.job_queue` but nothing drains them. The queue fills up silently.

This can happen during the dual-mode migration phase: if an operator sets `isDualMode=true` for a scheduler but forgets to set `kazi.job-queue.enabled=true`, the old inline path runs AND the queue fills with unprocessed jobs.

**Recommendation**: Either:
- (a) Make `DefaultJobEnqueuer` conditional on `kazi.job-queue.enabled`, OR
- (b) Add a guard at the top of `fanOutToAllTenants()` that checks the `enabled` property and no-ops if false (with a WARN log so operators notice)

Option (b) is safer because it prevents silent queue accumulation while keeping the bean available for programmatic use.

---

#### Issue S2: Single-threaded job worker poll loop (Medium)

**File**: `infrastructure/jobqueue/JobWorker.java`

**Problem**: The worker spawns a single virtual thread running `pollLoop()`. It claims a batch, then processes jobs sequentially within that batch. At scale with 1000+ jobs per sweep (1000 tenants), the claim-execute loop is sequential within each pod.

With 3 pods and 1000 jobs, each pod claims ~333 jobs and processes them one at a time. If each job takes 1 second, one pod takes 333 seconds to drain its share — over 5 minutes for a 60-second cron sweep.

**Recommendation**: Process claimed batches in parallel using a bounded virtual thread pool:

```java
private static final int WORKER_PARALLELISM = 10; // configurable

// After claiming a batch:
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    for (var job : batch) {
        scope.fork(() -> { processJob(job); return null; });
    }
    scope.join();
}
```

This transforms per-pod throughput from `batchSize / avgJobDuration` to `batchSize / (avgJobDuration / parallelism)`. With parallelism of 10 and 1-second jobs, 333 jobs drain in ~33 seconds instead of 333 seconds.

**Guardrail**: The parallelism should be bounded to avoid overwhelming the connection pool. `min(workerParallelism, hikariMaxPoolSize / 2)` is a safe heuristic.

---

#### Issue S3: Subscription handlers receive unnecessary tenant context (Low)

**Files**: `billing/TrialExpiryHandler.java`, `GraceExpiryHandler.java`, `CancellationEndHandler.java`

**Problem**: These handlers query `public.subscriptions` (a global table), not tenant-scoped data. The `JobWorker` binds `TENANT_ID` and sets `search_path` to a tenant schema before calling `execute()`, but these handlers never query tenant tables. The tenant scope binding is wasted work — and potentially misleading, since a reader might assume the handler operates on tenant data.

**Recommendation**: Either:
- (a) Document this clearly in handler Javadoc (partially done already), OR
- (b) Introduce a `GlobalJobHandler` interface (no tenant scoping) vs `TenantJobHandler` (scoped), and have the worker skip tenant binding for global handlers

Option (a) is sufficient for now. Option (b) is a nice-to-have if more global handlers are added.

---

#### Issue S4: No metrics on job queue throughput (Low)

**Problem**: The job worker logs processing times but doesn't expose Micrometer metrics. With Prometheus now available (Tier A), the queue should report:
- `kazi_job_queue_pending_count` (gauge, by job type)
- `kazi_job_queue_processing_duration_seconds` (histogram, by job type)
- `kazi_job_queue_claim_batch_size` (histogram)
- `kazi_job_queue_dead_letter_count` (counter, by job type)
- `kazi_job_queue_retry_count` (counter, by job type)

**Recommendation**: Add a `MeterBinder` implementation in the job queue package that reads from `JobQueueRepository` stats and the admin stats endpoint. Timer instrumentation around `processJob()` for per-handler duration histograms.

---

### Scheduling Improvements Roadmap

| Priority | Item | Effort |
|----------|------|--------|
| **P0** | Fix S1 — gate enqueuer on `enabled` flag | 30 min |
| **P1** | Fix S2 — parallel job execution within worker | 1-2 days |
| **P2** | Add S4 — Prometheus metrics for queue throughput | 1 day |
| **P3** | Consider S3 — separate global vs tenant handler types | 2-3 days |

---

## Part 2: Database Sharding

### Architecture Summary

Sharding adds support for multiple Postgres databases on top of the existing schema-per-tenant model. It is fully opt-in via `kazi.sharding.enabled` (default `false`). When enabled, tenant routing uses a composite identifier (`shardId:schemaName`) through Hibernate's `MultiTenantConnectionProvider`, with one HikariCP pool per shard.

```
Request Flow (sharding enabled):
  JWT → TenantFilter → org_schema_mapping lookup (with shard_id)
      → ScopedValue.SHARD_ID + TENANT_ID bound
      → TenantIdentifierResolver returns "shardId:schemaName"
      → ShardAwareConnectionProvider routes to correct DataSource
      → SET search_path TO tenant_xxx
```

### What Works Well

| Aspect | Assessment |
|--------|-----------|
| **Clean feature toggle** | `@ConditionalOnProperty` cleanly switches between `SchemaMultiTenantConnectionProvider` (legacy) and `ShardAwareConnectionProvider` (new). No runtime branching in hot paths. |
| **Composite tenant identifier** | `shardId:schemaName` carries both routing dimensions through Hibernate's existing resolution channel. No framework hacks or custom session factories. |
| **DataSource-per-shard lifecycle** | `DefaultShardRegistry` manages HikariCP pools per shard, resolves credentials from env vars (`KAZI_SHARD_{ID}_URL/USERNAME/PASSWORD`), handles pool creation/teardown with `@PreDestroy`. |
| **Circular dependency resolution** | `@Lazy` on `ShardRegistry` + pre-seeded primary DataSource in constructor breaks the EMF → ShardRegistry → Repository → EMF cycle. Well-documented in code comments. |
| **Cache coherence** | `TenantMapping` record bundles schema + org + shard together. Cache hits can't silently drop the shard assignment — a problem that would be invisible and devastating. |
| **`TenantScopedRunner` is shard-aware** | Correctly calls `runForTenantOnShard()` with `mapping.getShardId()` for scheduled job iteration. |
| **Provisioning correctness** | Creates schema on correct DataSource, runs Flyway on correct DataSource, creates `OrgSchemaMapping` row last (so `TenantFilter` can't resolve the tenant until setup is fully complete). |
| **Test infrastructure** | Second embedded Postgres (`SecondaryEmbeddedPostgres`) for true multi-database integration tests. Physical isolation verified in `ShardIsolationTest`. |

### Issues to Fix

#### Issue D1: `JobWorker` does not bind `SHARD_ID` during execution (Critical)

**File**: `infrastructure/jobqueue/JobWorker.java`, line 122

**Problem**: The worker calls `RequestScopes.runForTenant(job.getTenantId(), job.getOrgId(), ...)` instead of `RequestScopes.runForTenantOnShard(job.getTenantId(), job.getOrgId(), job.getShardId(), ...)`.

The `job.getShardId()` value is available (it's stored in the job row and logged via MDC), but it's never propagated to the `ScopedValue` that `TenantIdentifierResolver` reads. Inside the handler, `TenantIdentifierResolver` sees `SHARD_ID` unbound and defaults to `"primary"`. The handler executes against the primary database regardless of which shard the tenant actually lives on.

**Impact**: For any tenant on a secondary shard, job handlers silently read from and write to the wrong database. Data corruption or missing data with no error signal.

**Fix**:
```java
// Line 122 — change:
RequestScopes.runForTenant(job.getTenantId(), job.getOrgId(), () -> { ... });
// To:
RequestScopes.runForTenantOnShard(job.getTenantId(), job.getOrgId(), job.getShardId(), () -> { ... });
```

**Verification**: Add a test to `EndToEndMultiShardTest` that provisions a tenant on shard2, enqueues a job, executes it via the worker, and asserts the handler's write lands in the shard2 database — not primary.

---

#### Issue D2: `fanOutToAllTenants()` hardcodes `shard_id = 'primary'` (Critical)

**File**: `infrastructure/jobqueue/DefaultJobEnqueuer.java`, line 107

**Problem**: `fanOutToAllTenants()` iterates all `OrgSchemaMapping` rows but creates every job with `DEFAULT_SHARD_ID = "primary"` instead of using `mapping.getShardId()`.

Even if Issue D1 were fixed, secondary-shard tenants would receive jobs stamped with `shard_id = 'primary'`, causing the worker to route to the primary database.

**Fix**:
```java
// Line 107 — change:
var job = new JobQueue(jobType, mapping.getSchemaName(), mapping.getExternalOrgId(),
    DEFAULT_SHARD_ID, payload, priority, maxRetries);
// To:
var job = new JobQueue(jobType, mapping.getSchemaName(), mapping.getExternalOrgId(),
    mapping.getShardId(), payload, priority, maxRetries);
```

---

#### Issue D3: Secondary shards use pooled connection for Flyway DDL (Medium)

**File**: `provisioning/TenantProvisioningService.java`

**Problem**: The primary shard uses a dedicated `migrationDataSource` for Flyway — a direct connection to Neon without PgBouncer, because PgBouncer in transaction mode rejects DDL statements (`CREATE SCHEMA`, `CREATE TABLE`, etc.). Secondary shards use `shardRegistry.getDataSource(shardId)` — the runtime HikariCP pool — for both schema creation and Flyway migrations.

If a secondary shard is behind PgBouncer in transaction mode, `CREATE SCHEMA` will fail with a PgBouncer error. If secondary shards connect directly (no PgBouncer), this is fine.

**Recommendation**: Support a per-shard migration URL. Either:
- (a) Add `migration_jdbc_url` to `shard_config` table, with an env var pattern `KAZI_SHARD_{ID}_MIGRATION_URL`, OR
- (b) Document that secondary shards must use direct connections (not PgBouncer) and enforce this with a validation check at shard registration

---

#### Issue D4: `TenantTransactionHelper` does not bind `SHARD_ID` (Low)

**File**: `multitenancy/TenantTransactionHelper.java`, line 44

**Problem**: Binds `TENANT_ID` and `ORG_ID` but not `SHARD_ID`. Currently only called during provisioning inside `runForTenantOnShard()`, so the outer scope provides `SHARD_ID`. But the helper is a public component — any future caller using it for a secondary-shard tenant without wrapping in `runForTenantOnShard()` first would silently route to primary.

**Recommendation**: Either:
- (a) Add `SHARD_ID` binding (accept it as a parameter, default to `"primary"`), OR
- (b) Document the dependency on outer scope binding in Javadoc + add an assertion: `assert RequestScopes.SHARD_ID.isBound() : "SHARD_ID must be bound before calling TenantTransactionHelper"`

---

#### Issue D5: 25 call sites use `runForTenant()` instead of `runForTenantOnShard()` (Latent)

**Problem**: A grep across the codebase shows 25 files calling `RequestScopes.runForTenant()` (the two-arg variant that doesn't bind `SHARD_ID`). These are primarily event listeners (`@TransactionalEventListener`) and utility methods. When `kazi.sharding.enabled=true` with secondary shards, any of these paths that execute for a secondary-shard tenant will route to the primary database.

Today this is not a bug because sharding is disabled and no secondary shards exist. But every one of these call sites is a latent wrong-shard route that will activate when sharding goes live.

**Affected patterns**:
- `@TransactionalEventListener(AFTER_COMMIT)` handlers that re-bind tenant scope
- Portal sync event handlers
- Accounting sync event listeners
- Notification dispatch handlers

**Recommendation**: Before enabling sharding in production:
1. Audit all 25 `runForTenant()` call sites
2. For each, determine if it could execute for a secondary-shard tenant
3. Migrate to `runForTenantOnShard()` where needed (the shard ID is available from the `OrgSchemaMapping` lookup or can be passed via the domain event payload)
4. Consider deprecating `runForTenant()` with a compile warning to prevent future regressions, or add an ArchUnit rule that flags new usages

---

#### Issue D6: No test verifies job execution routes to correct shard (Testing Gap)

**File**: `infrastructure/jobqueue/EndToEndMultiShardTest.java`

**Problem**: The test provisions a tenant on shard2 and verifies the job is persisted with `shard_id = 'shard2'`. But it does not execute the job via `JobWorker` and verify that the handler's database operations land in the shard2 database.

This means Issues D1 and D2 are not caught by the test suite. The test proves "job was created correctly" but not "job executed against the correct database."

**Recommendation**: Extend the test to:
1. Create a simple test handler that writes a marker row to a tenant table
2. Execute the job via the worker
3. Query shard2's database directly to verify the marker row exists
4. Query primary's database to verify the marker row does NOT exist

---

#### Issue D7: `ShardAndSchema` ID validation rejects single-character shard IDs (Minor)

**File**: `multitenancy/ShardAndSchema.java`, line 15

**Problem**: The regex `[a-z][a-z0-9_]{0,48}[a-z0-9]$` requires at least 2 characters. A shard ID of `"a"` is rejected. `"primary"` is special-cased to always pass. This is a minor constraint but could surprise operators choosing short shard names.

**Recommendation**: Either relax the regex to allow single-character IDs (`[a-z][a-z0-9_]{0,48}[a-z0-9]?$`) or document the 2-character minimum in the admin API / shard setup docs.

---

### Sharding Improvements Roadmap

| Priority | Item | Effort | Blocks |
|----------|------|--------|--------|
| **P0** | Fix D1 — `JobWorker` must call `runForTenantOnShard()` | 30 min | Sharding activation |
| **P0** | Fix D2 — `fanOutToAllTenants()` must use `mapping.getShardId()` | 30 min | Sharding activation |
| **P0** | Fix D6 — test that job execution routes to correct shard | 2-3 hrs | Validates D1+D2 fixes |
| **P1** | Fix D5 — audit and migrate 25 `runForTenant()` call sites | 1-2 days | Sharding activation |
| **P1** | Fix D3 — per-shard migration DataSource for Flyway DDL | 3-4 hrs | Secondary shards behind PgBouncer |
| **P2** | Fix D4 — `TenantTransactionHelper` shard binding | 1 hr | — |
| **P3** | Fix D7 — relax shard ID regex or document constraint | 30 min | — |

---

## Cross-Cutting: Job Queue × Sharding Integration

The job queue and sharding features interact at two specific points, and both have bugs:

```
Enqueue path:
  fanOutToAllTenants() → reads org_schema_mapping (has shard_id)
                       → creates JobQueue row
                       → BUG D2: ignores mapping.getShardId(), hardcodes "primary"

Execute path:
  JobWorker.processJob() → reads job.getShardId()
                         → BUG D1: calls runForTenant() instead of runForTenantOnShard()
                         → handler executes against wrong database
```

**Combined impact**: For any tenant on a secondary shard, jobs are both created with the wrong shard ID AND executed against the wrong database. This is a double failure — fixing only one of the two bugs would still result in wrong-shard execution.

**Fix order**: Fix D2 first (enqueue), then D1 (execute), then D6 (test). The test should verify the full path: enqueue with correct shard → claim → execute against correct shard → verify data in correct database.

---

## Summary

### Job Queue Scheduling

The implementation is **architecturally sound**. True per-tenant fanout with Postgres `FOR UPDATE SKIP LOCKED` is the right pattern for this workload. The dual-mode migration strategy, dedup index, stale recovery, and admin API are all well-designed.

**Key improvements**: Gate the enqueuer on the enabled flag (S1), parallelize job execution within the worker (S2), and add Prometheus metrics (S4).

### Database Sharding

The implementation is **structurally clean** — the feature toggle, composite identifier routing, DataSource lifecycle, and test infrastructure are all done correctly. The design avoids common pitfalls (cache-hit shard loss, circular dependency, connection pool isolation).

**Blockers before activation**: Two critical bugs in the job queue integration (D1, D2) would cause silent wrong-database execution for secondary-shard tenants. Additionally, 25 call sites using the shard-unaware `runForTenant()` are latent wrong-shard routes (D5). All P0 items must be resolved before `kazi.sharding.enabled=true` can be set in any environment with secondary shards.

Neither feature has production-visible issues today because sharding is disabled and the job queue is in dual-mode rollout. The bugs are latent — they will surface only when secondary shards are activated.
