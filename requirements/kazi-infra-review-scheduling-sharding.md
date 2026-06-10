# Infrastructure Review: Job Queue Scheduling & Database Sharding

**Date**: 2026-05-29 | **Last updated**: 2026-06-10
**Scope**: Review of two recently implemented infrastructure features — job queue fanout (replacing `@Scheduled` + `TenantScopedRunner`) and database sharding (multi-database schema-per-tenant).

---

## Resolution Summary

All critical and high-priority issues have been resolved across PRs #1383–#1389. The sharding implementation is now safe for activation with secondary shards, subject to the remaining low-priority items.

| Finding | Severity | Status | Resolved In |
|---------|----------|--------|-------------|
| D1 — JobWorker doesn't bind SHARD_ID | Critical | **Resolved** | PR #1383 |
| D2 — fanOutToAllTenants hardcodes "primary" | Critical | **Resolved** | PR #1383 |
| D6 — No test for job→shard execution | Critical | **Resolved** | PR #1383 |
| S1 — Enqueuer not gated by enabled flag | Medium | **Resolved** | PR #1384 |
| D3 — Secondary shards use pooled DDL connection | Medium | **Resolved** | PR #1385 |
| D5 — 25 shard-unaware runForTenant callers | Latent | **Partially resolved** | PR #1386, #1389 |
| S2 — Single-threaded worker poll loop | Medium | **Resolved** | PR #1388 |
| D4 — TenantTransactionHelper missing SHARD_ID | Low | **Resolved** | PR #1387 |
| D7 — Shard ID regex rejects single-char IDs | Minor | **Resolved** | PR #1387 |
| S3 — Subscription handler tenant context docs | Low | **Resolved** | PR #1383 (Javadoc) |
| S4 — No Prometheus metrics for job queue | Low | **Open** | — |
| NEW — AssistantController carrier missing SHARD_ID | Critical | **Resolved** | PR #1389 |
| NEW — DomainEvent lacks shardId() | Medium | **Resolved** | PR #1389 |
| NEW — RequestScopes.getShardIdOrDefault() helper | Medium | **Resolved** | PR #1389 |

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

### Issues

#### Issue S1: `DefaultJobEnqueuer` is not gated by `kazi.job-queue.enabled` ~~(Medium)~~ **RESOLVED — PR #1384**

`DefaultJobEnqueuer` now checks `properties.isEnabled()` via `isQueueDisabled()` at the top of both `enqueue()` and `fanOutToAllTenants()`. If disabled, it no-ops with a warn-once log per job type (using a `ConcurrentHashMap`-backed set to avoid log spam).

---

#### Issue S2: Single-threaded job worker poll loop ~~(Medium)~~ **RESOLVED — PR #1388**

`JobWorker.processBatch()` now uses `Executors.newVirtualThreadPerTaskExecutor()` with a `Semaphore(parallelism)` to bound concurrent job execution. Parallelism is capped at `min(configured, max(1, hikariMaxPoolSize / 2))` via `computeEffectiveParallelism()` — protecting request traffic from pool exhaustion. Falls back to sequential processing when parallelism ≤ 1 or batch size = 1.

---

#### Issue S3: Subscription handlers receive unnecessary tenant context ~~(Low)~~ **RESOLVED — PR #1383 (Javadoc)**

Class-level Javadoc added to `TrialExpiryHandler` and cross-referenced in `JobWorker.processJob()` documenting that subscription handlers operate on global `public.*` tables, not tenant-scoped data. No code change — documentation-only.

---

#### Issue S4: No metrics on job queue throughput (Low) — **OPEN**

The job worker still doesn't expose Micrometer metrics. Recommended metrics:
- `kazi_job_queue_pending_count` (gauge, by job type)
- `kazi_job_queue_processing_duration_seconds` (histogram, by job type)
- `kazi_job_queue_claim_batch_size` (histogram)
- `kazi_job_queue_dead_letter_count` (counter, by job type)
- `kazi_job_queue_retry_count` (counter, by job type)

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

### Issues

#### Issue D1: `JobWorker` does not bind `SHARD_ID` during execution ~~(Critical)~~ **RESOLVED — PR #1383**

`JobWorker.processJob()` now calls `RequestScopes.runForTenantOnShard(job.getTenantId(), job.getOrgId(), job.getShardId(), ...)`. Inline comment references D1 as motivation.

---

#### Issue D2: `fanOutToAllTenants()` hardcodes `shard_id = 'primary'` ~~(Critical)~~ **RESOLVED — PR #1383**

`fanOutToAllTenants()` now passes `shardIdOrDefault(mapping.getShardId())` when creating job rows. `"primary"` is used only as a null/blank fallback via the private helper. Javadoc on the helper explicitly references D1/D2 findings.

---

#### Issue D3: Secondary shards use pooled connection for Flyway DDL ~~(Medium)~~ **RESOLVED — PR #1385**

`DefaultShardRegistry` now supports per-shard migration DataSources via `KAZI_SHARD_{ID}_MIGRATION_URL` env var. `getMigrationDataSource(shardId)` returns the dedicated migration DS if configured, falling back to the pooled DS otherwise. `TenantProvisioningService` uses the migration DS for both `createSchema()` and `runTenantMigrations()` on secondary shards.

---

#### Issue D4: `TenantTransactionHelper` does not bind `SHARD_ID` ~~(Low)~~ **RESOLVED — PR #1387**

`TenantTransactionHelper` now has a 3-arg overload accepting `shardId` with full `ScopedValue.where(SHARD_ID, ...)` binding. The 2-arg overload inherits `SHARD_ID` from the enclosing scope if bound, defaulting to `"primary"` only when unbound. Shard ID is validated via `ShardAndSchema.requireValidShardId()`.

---

#### Issue D5: 25 call sites use `runForTenant()` instead of `runForTenantOnShard()` ~~(Latent)~~ **PARTIALLY RESOLVED — PR #1386, #1389**

**Resolved (PR #1386):**
- ArchUnit guard `no_new_shard_unaware_tenant_binding` added — new code is forced to use `runForTenantOnShard`/`callForTenantOnShard`
- 4 "mapping-in-hand" sites migrated: `JobWorker`, `TenantProvisioningService`, `SubscriptionExpiryJob`, `PaymentWebhookController`

**Resolved (PR #1389):**
- `DomainEvent.shardId()` default method added — reads from `RequestScopes.SHARD_ID` at call time
- `RequestScopes.getShardIdOrDefault()` centralized helper added
- 35 AFTER_COMMIT event listener call sites migrated from `runForTenant()` to `runForTenantOnShard()` across `NotificationEventHandler` (18), `PortalEventHandler` (13), `AccountingSyncEventListener` (3), `PortalDocumentNotificationHandler` (1), `PortalEmailNotificationChannel` (1)
- `AssistantController` virtual thread carrier now binds `SHARD_ID` (previously omitted — active bug for secondary-shard AI chat)
- ArchUnit grandfathered set shrunk by 1 (`PortalDocumentNotificationHandler` fully migrated)

**Remaining (~20 call sites in 20 files):**
These use non-`DomainEvent` event types (`PortalDomainEvent`, `BillingRunEvent`, trust accounting events, schedule events) that don't carry `shardId()`. They are safe today because they inherit `SHARD_ID` from the outer ScopedValue scope (TenantFilter for HTTP, TenantScopedRunner for scheduled jobs, JobWorker for queue jobs). They are tracked in the D5 ArchUnit grandfathered set and will need their event types enriched with `shardId` before full migration.

**Note on `DomainEvent.shardId()`**: The default method reads from the ScopedValue at call time, not construction time. This is correct for AFTER_COMMIT listeners (same thread as publisher). When events are serialized for the outbox pattern (Tier B, item B1), `shardId` must become an explicit record field on all 42 `DomainEvent` implementations. The Javadoc includes an explicit warning against deferred invocation past the publishing scope's lifetime.

---

#### Issue D6: No test verifies job execution routes to correct shard ~~(Testing Gap)~~ **RESOLVED — PR #1383**

`EndToEndMultiShardTest.jobForShard2Tenant_executesAgainstShard2Database()` now covers the full path: provisions tenant on shard2, enqueues job, starts live `JobWorker`, awaits completion, asserts handler's write landed in shard2 database via direct JDBC, and asserts the tenant schema does NOT exist on primary. A separate test `fanOutToAllTenants_stampsShardIdFromMapping()` verifies D2 regression.

---

#### Issue D7: `ShardAndSchema` ID validation rejects single-character shard IDs ~~(Minor)~~ **RESOLVED — PR #1387**

Regex updated to `^[a-z]([a-z0-9_]{0,48}[a-z0-9])?$` with optional tail group. Single-character IDs like `"a"` now pass validation. Confirmed in Javadoc.

---

## Cross-Cutting: Job Queue × Sharding Integration

~~The job queue and sharding features interact at two specific points, and both have bugs:~~

Both integration points are now fixed:

```
Enqueue path:
  fanOutToAllTenants() → reads org_schema_mapping (has shard_id)
                       → creates JobQueue row with mapping.getShardId()  ✅ Fixed PR #1383

Execute path:
  JobWorker.processJob() → reads job.getShardId()
                         → calls runForTenantOnShard(tenantId, orgId, shardId)  ✅ Fixed PR #1383
                         → handler executes against correct database

Test coverage:
  EndToEndMultiShardTest verifies full path with physical DB assertion  ✅ Fixed PR #1383
```

---

## Summary

### Job Queue Scheduling

The implementation is **architecturally sound and operationally ready**. True per-tenant fanout with Postgres `FOR UPDATE SKIP LOCKED`, parallel batch execution, and shard-aware routing are all in place.

**Remaining**: Prometheus metrics (S4) — low priority, non-blocking.

### Database Sharding

The implementation is **safe for activation with secondary shards**. All critical and high-priority bugs have been resolved. The shard routing pipeline is verified end-to-end: enqueue → claim → execute → correct database.

**Remaining**: ~20 `runForTenant` callers using non-`DomainEvent` event types — tracked in ArchUnit grandfathered set, safe via ScopedValue inheritance, will need event type enrichment before outbox migration (B1).

### PRs Shipped

| PR | Findings | Description |
|----|----------|-------------|
| #1383 | D1, D2, D6, S3 | Critical — worker binds SHARD_ID, fan-out stamps real shard, regression tests |
| #1384 | S1 | Enqueuer no-ops + WARN-once when queue disabled |
| #1385 | D3, D5 (ArchUnit) | Per-shard migration DataSource, ArchUnit guard for new code |
| #1386 | D5 (4 migrations) | 4 mapping-in-hand sites migrated to runForTenantOnShard |
| #1387 | D4, D7 | TenantTransactionHelper shard binding, regex relaxation |
| #1388 | S2 | Bounded virtual-thread batch parallelism with pool-headroom guardrail |
| #1389 | D5 (35 migrations), NEW | AssistantController carrier, DomainEvent.shardId(), 35 event listener migrations, getShardIdOrDefault() helper |
