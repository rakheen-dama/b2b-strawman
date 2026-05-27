# Phase 75 — Scalability: Job Queue Fanout + Shard-Aware DB Resolver

## System Context

Kazi is a multi-tenant B2B practice-management platform with schema-per-tenant isolation on PostgreSQL. The backend is a Spring Boot 4 / Java 25 monolith with 60+ domain packages, 100+ tenant-scoped tables, and 19 scheduled jobs running in a single JVM. Tier A scalability work (Phase 73.5, commit `fbd163823`) shipped pool sizing, Prometheus metrics, bounded AI executor, and ShedLock leader election.

### Predecessor systems this phase builds on

- **Tier A Scalability** (commit `fbd163823`) — HikariCP pool sizing (25 max), Prometheus metrics endpoint, AI execution semaphore (20 concurrent), ShedLock on all 19 scheduled jobs. The foundation that made the monolith observable and safe under moderate load.
- **Multitenancy infrastructure** (Phase 1/13) — `SchemaMultiTenantConnectionProvider` (SET search_path routing), `TenantIdentifierResolver` (ScopedValue-based), `OrgSchemaMapping` entity (`public.org_schema_mapping` table maps `externalOrgId → schemaName`), `TenantScopedRunner.forEachTenant()` (sequential iteration with per-tenant exception isolation), `RequestScopes` (8 ScopedValues including `TENANT_ID`, `ORG_ID`).
- **ShedLock** (Tier A) — `public.shedlock` table, JDBC-based lock provider, all 19 `@Scheduled` methods protected. Prevents concurrent execution across replicas.
- **Flyway migrations** — Tenant migrations in `db/migration/tenant/V{N}__*.sql` (currently at V127). Global migrations in `db/migration/global/V{N}__*.sql` (currently at V23). Tenant migrations run per-schema via `TenantAwareFlyway`.

### What is missing today

1. **Sequential tenant iteration in all scheduled jobs.** `TenantScopedRunner.forEachTenant()` iterates tenants one by one in a `for` loop. At 1000 tenants with jobs that make external calls (LLM, Xero sync, email), sweeps take 30+ minutes and can stack. ShedLock prevents concurrent execution but doesn't solve throughput — only one pod processes each job, sequentially.

2. **No work distribution across pods.** With N replicas, ShedLock means only one replica runs each scheduled job. The other N-1 replicas are idle for scheduled work. There's no mechanism to distribute tenant-level work items across pods.

3. **Single-database assumption hardcoded everywhere.** `SchemaMultiTenantConnectionProvider` injects one `DataSource`. All tenant schemas must reside on the same PostgreSQL instance. There's no path to horizontal database scaling without rewriting the connection provider. The ceiling is approximately 2,000-5,000 tenants on a single RDS instance before connection count, shared buffer contention, and vacuum overhead become problems.

4. **No visibility into scheduled job execution.** "Did the dormancy check run? How long did it take? Which tenants failed?" — unanswerable without parsing log files. No structured execution history.

### Founder decisions that constrain this phase (2026-05-27 ideation)

- **Schedulers become enqueuers, workers become executors.** The `@Scheduled` methods keep their cron/fixedRate triggers but their only job is to fan out work items into the job queue. Workers (on any pod) claim and execute. This replaces the proposed B3 (StructuredTaskScope parallelism) from the scalability spec.
- **Job queue for scheduled work only.** Domain event delivery (outbox pattern, B1) is deferred. Events stay fire-and-forget via `@TransactionalEventListener(AFTER_COMMIT)` until service extraction (Tier C) demands guaranteed delivery.
- **Full shard routing, not just a seam.** Build the multi-DataSource infrastructure, shard-aware connection provider, and shard-aware provisioning. Zero behavioral change with a single shard, but adding a second shard is a config change.
- **Explicit shard assignment by platform admin.** No automated shard selection algorithm. Platform admin assigns a tenant to a named shard during provisioning. Shard names are meaningful and vertical-aligned: `primary`, `demo`, `kazi_accounting_1`, `kazi_legal_1`, etc.
- **Control plane / shard plane split.** Cross-tenant tables (`org_schema_mapping`, `shedlock`, `job_queue`, subscriptions) live on the control plane database. Tenant schemas live on shard databases. Initially the same PostgreSQL instance.

## Objective

Replace the sequential scheduler → tenant-loop pattern with a **job queue table** that distributes work across all replicas, and replace the single-DataSource connection provider with a **shard-aware DB resolver** that routes tenants to the correct PostgreSQL instance. Both changes must be zero-behavioral-change under the current single-shard, single-database deployment — the new infrastructure activates when a second shard is configured.

## Constraints & Assumptions

- **Schema-per-tenant only** (ADR-T001). Tenant tables live in `tenant_xxxxxxxxxxxx` schemas on shard databases.
- **Global tables stay in `public` schema** on the control plane database. This includes `org_schema_mapping`, `shedlock`, `job_queue`, and all subscription/billing tables.
- **Virtual threads.** Workers use virtual threads (Java 25). `ScopedValue` carrier propagation is mandatory per ADR-T002.
- **PostgreSQL 16** for all databases (control plane and shards).
- **No message broker.** Job queue uses `SELECT ... FOR UPDATE SKIP LOCKED` on the control plane database. This is sufficient for the monolith deployment model. A message broker (SNS/SQS) is a Tier C prerequisite, not a Phase 75 concern.
- **Backward compatible.** A deployment with zero shard configuration runs identically to today — single DataSource, all schemas on one instance.
- **Next global migration**: V24. Next tenant migration: V128.
- **Next ADR**: 293.

---

## 1. Job Queue Table — Data Model

### 1.1 `public.job_queue` table (V24 global migration)

```sql
CREATE TABLE public.job_queue (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type        VARCHAR(100) NOT NULL,
    tenant_id       VARCHAR(50)  NOT NULL,     -- schema name (e.g., tenant_a1b2c3d4e5f6)
    org_id          VARCHAR(100) NOT NULL,     -- external org ID
    shard_id        VARCHAR(50)  NOT NULL DEFAULT 'primary',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    payload         JSONB,                     -- job-type-specific data (nullable)
    priority        INT          NOT NULL DEFAULT 0, -- higher = sooner (0 = normal)
    claimed_by      VARCHAR(100),              -- pod identifier (hostname or instance ID)
    claimed_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    retry_count     INT          NOT NULL DEFAULT 0,
    max_retries     INT          NOT NULL DEFAULT 3,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_job_status CHECK (status IN ('PENDING', 'CLAIMED', 'COMPLETED', 'FAILED', 'DEAD_LETTER'))
);

CREATE INDEX idx_job_queue_claimable
    ON public.job_queue (priority DESC, next_attempt_at ASC)
    WHERE status = 'PENDING' AND next_attempt_at <= NOW();

CREATE UNIQUE INDEX idx_job_queue_dedup
    ON public.job_queue (job_type, tenant_id)
    WHERE status IN ('PENDING', 'CLAIMED');

CREATE INDEX idx_job_queue_status_type
    ON public.job_queue (job_type, status, created_at);
```

**Key design decisions:**
- `idx_job_queue_dedup` prevents double-enqueue: if a PENDING or CLAIMED job exists for the same (job_type, tenant_id), the enqueue is a no-op. This means a scheduler that ticks every 30 seconds won't pile up work if previous jobs haven't finished.
- `next_attempt_at` enables exponential backoff: on failure, set `next_attempt_at = NOW() + (2^retry_count * base_delay)`.
- `shard_id` is denormalized from `org_schema_mapping` for worker efficiency — workers can claim jobs without joining the mapping table.
- `priority` allows urgent jobs (e.g., real-time sync triggers) to jump the queue.

### 1.2 Enums

```java
public enum JobStatus {
    PENDING, CLAIMED, COMPLETED, FAILED, DEAD_LETTER
}
```

### 1.3 Stale job recovery

Jobs stuck in `CLAIMED` status beyond a configurable timeout (default 15 minutes) are reset to `PENDING` by a ShedLock-protected sweep. This handles pod crashes mid-execution.

---

## 2. Job Queue — Services

### 2.1 `JobEnqueuer`

Spring-managed service. Used by scheduler methods to fan out work.

```java
public interface JobEnqueuer {
    /** Enqueue a single job. No-op if a PENDING/CLAIMED job already exists for (jobType, tenantId). */
    void enqueue(String jobType, String tenantId, String orgId, String shardId, @Nullable JsonNode payload);

    /** Fan out one job per active tenant. Returns count of jobs enqueued (excludes dedup skips). */
    int fanOutToAllTenants(String jobType, @Nullable JsonNode payload);

    /** Fan out with priority. */
    int fanOutToAllTenants(String jobType, @Nullable JsonNode payload, int priority);
}
```

- `fanOutToAllTenants()` reads `OrgSchemaMappingRepository.findAll()` and batch-inserts into `job_queue` using `INSERT ... ON CONFLICT DO NOTHING` on the dedup index.
- Runs within the caller's transaction if one exists, otherwise in its own transaction.

### 2.2 `JobHandler` interface

```java
public interface JobHandler {
    /** The job type this handler processes. Must be unique across all handlers. */
    String jobType();

    /** Execute the job. Tenant scope (TENANT_ID, ORG_ID) is already bound via ScopedValue. */
    void execute(@Nullable JsonNode payload);
}
```

Handlers are discovered via Spring's `@Component` scanning. A `JobHandlerRegistry` maps `jobType → JobHandler` at startup and fails fast if duplicates exist.

### 2.3 `JobWorker`

Poll loop running on every pod. NOT ShedLock-protected — the whole point is that multiple pods claim work concurrently.

**Poll cycle:**
1. `SELECT * FROM job_queue WHERE status = 'PENDING' AND next_attempt_at <= NOW() ORDER BY priority DESC, next_attempt_at ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED`
2. Set `status = 'CLAIMED'`, `claimed_by = {podId}`, `claimed_at = NOW()` for each claimed row.
3. For each claimed job:
   a. Bind `RequestScopes.TENANT_ID` and `RequestScopes.ORG_ID` via `ScopedValue.Carrier`
   b. Look up `JobHandler` by `job_type`
   c. Execute with shard-aware DataSource binding (see Section 5)
   d. On success: `status = 'COMPLETED'`, `completed_at = NOW()`
   e. On failure: `retry_count++`. If `retry_count >= max_retries`: `status = 'DEAD_LETTER'`, `error_message = exception.getMessage()`. Else: `status = 'PENDING'`, `next_attempt_at = NOW() + backoff(retry_count)`
4. Repeat after configurable poll interval (default 2 seconds).

**Configuration:**
```yaml
kazi:
  job-queue:
    enabled: true
    batch-size: 20
    poll-interval-ms: 2000
    stale-claim-timeout-minutes: 15
    max-retries-default: 3
    backoff-base-seconds: 10     # 10s, 20s, 40s, 80s...
```

**Shutdown:** Graceful — stop polling, wait for in-flight jobs to complete (with a hard timeout), release claimed jobs back to PENDING on forced shutdown.

### 2.4 `StaleJobRecoveryTask`

ShedLock-protected `@Scheduled(fixedDelay = 60_000)` that resets CLAIMED jobs older than `stale-claim-timeout-minutes` back to PENDING. Prevents stuck jobs from pods that crashed.

### 2.5 Admin API (internal, platform-admin only)

```
GET    /api/admin/jobs?status=DEAD_LETTER&jobType=...&limit=50
POST   /api/admin/jobs/{id}/retry          -- Reset to PENDING, clear retry_count
DELETE /api/admin/jobs/{id}                 -- Hard delete (DEAD_LETTER only)
GET    /api/admin/jobs/stats                -- Counts by status and job_type
```

---

## 3. Scheduler Migration

### 3.1 Migration pattern

Each `@Scheduled` method transforms from:

```java
// BEFORE: scheduler does the work
@Scheduled(fixedDelay = 30_000)
@SchedulerLock(name = "accounting_sync_drain_pending_entries", lockAtLeastFor = "15s")
void drainPendingEntries() {
    tenantScopedRunner.forEachTenant((tenantId, orgId) -> {
        // ... expensive external API calls per tenant
    });
}
```

to:

```java
// AFTER: scheduler enqueues, worker executes
@Scheduled(fixedDelay = 30_000)
@SchedulerLock(name = "accounting_sync_drain_pending_entries", lockAtLeastFor = "15s")
void drainPendingEntries() {
    jobEnqueuer.fanOutToAllTenants("accounting_sync_drain");
}
```

The business logic moves into a `JobHandler`:

```java
@Component
class AccountingSyncDrainHandler implements JobHandler {
    @Override public String jobType() { return "accounting_sync_drain"; }

    @Override
    public void execute(@Nullable JsonNode payload) {
        // Tenant scope already bound. Same logic as before.
    }
}
```

ShedLock stays on the scheduler method — it prevents duplicate enqueue waves, not duplicate execution (the dedup index handles that).

### 3.2 Migration order

Migrate in two batches, highest-frequency and highest-impact first:

**Batch 1 (high-frequency, external calls):**
1. `AutomationScheduler.pollScheduledTriggers()` — highest frequency, runs automation rules
2. `AutomationScheduler.pollDelayedActions()` — delayed action execution
3. `AccountingSyncWorker.drainPendingEntries()` — 30s interval, Xero API calls
4. `AccountingPaymentPollWorker.pollAllConnections()` — 15min interval, Xero API calls
5. `TimeReminderScheduler.checkTimeReminders()` — 15min interval

**Batch 2 (daily/hourly, lower frequency):**
6. `RecurringScheduleExecutor.executeSchedules()` — daily 2:00 UTC
7. `DormancyScheduledJob.executeDormancyCheck()` — daily 2:00 UTC
8. `ProposalExpiryProcessor.processExpiredProposals()` — hourly
9. `AcceptanceExpiryProcessor.processExpired()` — hourly
10. `MagicLinkCleanupService.cleanupExpiredTokens()` — hourly
11. `AiExecutionGateService.expireStaleGates()` — hourly
12. `FieldDateScannerJob.execute()` — daily 6:00 UTC
13. `RequestReminderScheduler.checkRequestReminders()` — interval-based
14. `SubscriptionExpiryJob` (3 methods) — daily 3:00-3:10 UTC
15. `CourtDateReminderJob.execute()` — daily 6:00 UTC
16. `AiInvocationExpirySweeper.sweep()` — daily 3:00 UTC
17. `PortalDigestScheduler.scheduledRun()` — weekly Monday 8:00 UTC

### 3.3 Dual-mode transition

During migration, each scheduler can run in "dual mode" (controlled by a config flag) where both the old `forEachTenant` path and the new `jobEnqueuer.fanOutToAllTenants` path are active. The old path is removed after the new path is verified in production. This flag is per job type:

```yaml
kazi:
  job-queue:
    dual-mode:
      accounting_sync_drain: false  # fully migrated
      automation_poll_triggers: true # still in dual mode
```

---

## 4. Shard-Aware DB Resolver — Data Model

### 4.1 `org_schema_mapping` extension (V25 global migration)

```sql
ALTER TABLE public.org_schema_mapping
    ADD COLUMN shard_id VARCHAR(50) NOT NULL DEFAULT 'primary';
```

### 4.2 `public.shard_config` table (V25 global migration)

```sql
CREATE TABLE public.shard_config (
    shard_id     VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    jdbc_url     VARCHAR(500),    -- NULL for 'primary' (uses default DataSource)
    username     VARCHAR(100),
    pool_size    INT NOT NULL DEFAULT 25,
    read_only    BOOLEAN NOT NULL DEFAULT FALSE,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO public.shard_config (shard_id, display_name)
    VALUES ('primary', 'Primary Database');
```

**Important:** Credentials (`username`, `password`) are NOT stored in the database. The `jdbc_url` and `username` in `shard_config` are metadata only — actual credentials come from environment variables or AWS Secrets Manager, keyed by shard_id:
- `KAZI_SHARD_PRIMARY_URL` (or falls back to the default `spring.datasource.url`)
- `KAZI_SHARD_PRIMARY_USERNAME`
- `KAZI_SHARD_PRIMARY_PASSWORD`
- `KAZI_SHARD_KAZI_LEGAL_1_URL`
- etc.

For the `primary` shard, the existing Spring Boot DataSource is used directly — no additional configuration needed.

---

## 5. Shard-Aware DB Resolver — Services

### 5.1 `ShardRegistry`

Manages named `DataSource` instances. Reads `shard_config` at startup and creates a `HikariDataSource` for each active shard (except `primary`, which reuses the existing Spring-managed DataSource).

```java
public interface ShardRegistry {
    /** Get the DataSource for a shard. Throws if shard not found or inactive. */
    DataSource getDataSource(String shardId);

    /** Get the primary (control plane) DataSource. */
    DataSource getPrimaryDataSource();

    /** List all active shard IDs. */
    Set<String> getActiveShardIds();

    /** Refresh shard configuration (called when shard_config changes). */
    void refresh();
}
```

- On startup: reads `shard_config`, creates DataSources from env vars.
- The `primary` shard is always the Spring Boot default `DataSource`.
- Shards with no matching env var credentials are logged as warnings and marked inactive.
- Pool sizing per shard is configurable via `shard_config.pool_size`.

### 5.2 `ShardAwareConnectionProvider`

Replaces `SchemaMultiTenantConnectionProvider`. Implements Hibernate's `MultiTenantConnectionProvider<String>`.

**Tenant identifier format change:** The tenant identifier passed to Hibernate changes from `tenant_xxxxxxxxxxxx` (schema name only) to `{shardId}:{schemaName}` (e.g., `primary:tenant_a1b2c3d4e5f6`). This allows the connection provider to resolve both the shard and the schema from a single identifier.

```java
@Override
public Connection getConnection(String tenantIdentifier) {
    ShardAndSchema parsed = ShardAndSchema.parse(tenantIdentifier);
    DataSource ds = shardRegistry.getDataSource(parsed.shardId());
    Connection conn = ds.getConnection();
    conn.createStatement().execute("SET search_path TO " + sanitizeSchema(parsed.schemaName()));
    return conn;
}
```

- `TenantIdentifierResolver` is updated to return `{shardId}:{schemaName}` by looking up `OrgSchemaMapping` (which now includes `shard_id`).
- Schema validation regex unchanged: `^tenant_[0-9a-f]{12}$`.
- Shard ID validation: `^[a-z][a-z0-9_]{0,48}[a-z0-9]$` (lowercase, underscore-separated, max 50 chars).
- The `public` schema identifier becomes `primary:public` (control plane is always on the primary shard).

### 5.3 `TenantIdentifierResolver` changes

```java
@Override
public String resolveCurrentTenantIdentifier() {
    if (!RequestScopes.TENANT_ID.isBound()) {
        return "primary:public";  // default: control plane
    }
    String schemaName = RequestScopes.TENANT_ID.get();
    String shardId = RequestScopes.SHARD_ID.isBound()
        ? RequestScopes.SHARD_ID.get()
        : "primary";
    return shardId + ":" + schemaName;
}
```

### 5.4 `RequestScopes` extension

Add a new ScopedValue:

```java
public static final ScopedValue<String> SHARD_ID = ScopedValue.newInstance();
```

Update `bindTenantScope()` to include shard ID:

```java
private static ScopedValue.Carrier bindTenantScope(String tenantId, @Nullable String orgId, @Nullable String shardId) {
    ScopedValue.Carrier carrier = ScopedValue.where(TENANT_ID, tenantId);
    if (orgId != null && !orgId.isBlank()) {
        carrier = carrier.where(ORG_ID, orgId);
    }
    if (shardId != null && !shardId.isBlank()) {
        carrier = carrier.where(SHARD_ID, shardId);
    }
    return carrier;
}
```

### 5.5 `TenantFilter` changes

The HTTP request entry point. After resolving `externalOrgId → OrgSchemaMapping`, bind `SHARD_ID` in addition to `TENANT_ID` and `ORG_ID`.

### 5.6 `TenantScopedRunner` changes

`forEachTenant()` now reads shard_id from `OrgSchemaMapping` and binds `SHARD_ID` per iteration. The job queue worker does the same when processing jobs (using the denormalized `shard_id` column on the job).

### 5.7 Shard-aware Flyway migration runner

`TenantAwareFlyway` currently runs migrations against all tenant schemas on the single DataSource. It must be updated to:
1. Read all active shards from `ShardRegistry`
2. For each shard: get DataSource, discover tenant schemas, run Flyway migrations
3. Report per-shard migration results

Migrations are the same SQL files for all shards — the schema structure is identical.

---

## 6. Shard-Aware Tenant Provisioning

### 6.1 Provisioning flow changes

`TenantProvisioningService.provisionTenant()` currently:
1. Creates a schema name (e.g., `tenant_a1b2c3d4e5f6`)
2. Inserts into `org_schema_mapping`
3. Creates the schema on the (single) database
4. Runs Flyway migrations on the new schema
5. Seeds default data

After Phase 75:
1. Creates a schema name (unchanged)
2. **Accepts `shardId` parameter** (required for provisioning, provided by platform admin)
3. **Validates shard exists and is active** via `ShardRegistry`
4. Inserts into `org_schema_mapping` **with `shard_id`**
5. Creates the schema **on the shard's DataSource**
6. Runs Flyway migrations on the new schema **on the shard's DataSource**
7. Seeds default data **on the shard's DataSource**

### 6.2 Platform admin shard assignment

The existing tenant provisioning API (webhook-driven from Keycloak) must accept an optional `shardId` field. If omitted, defaults to `primary`. A future platform admin UI can expose shard selection during org onboarding.

```
POST /api/admin/tenants/provision
{
  "externalOrgId": "org_abc123",
  "shardId": "kazi_legal_1"     // optional, defaults to "primary"
}
```

### 6.3 Tenant shard migration (future-proofing)

Not in scope for Phase 75, but the data model supports it: change `org_schema_mapping.shard_id`, run `pg_dump` of the schema from the old shard, `pg_restore` on the new shard, flip the mapping. The `ShardAwareConnectionProvider` picks up the change on next request.

---

## 7. Configuration

### 7.1 Application configuration

```yaml
kazi:
  sharding:
    enabled: true                     # false = legacy single-DataSource mode
    control-plane-datasource: primary # always
  shards:
    primary:
      # Uses spring.datasource.* — no additional config needed
    # Example additional shard (not configured initially):
    # kazi_legal_1:
    #   url: ${KAZI_SHARD_KAZI_LEGAL_1_URL}
    #   username: ${KAZI_SHARD_KAZI_LEGAL_1_USERNAME}
    #   password: ${KAZI_SHARD_KAZI_LEGAL_1_PASSWORD}
    #   pool-size: 25

  job-queue:
    enabled: true
    batch-size: 20
    poll-interval-ms: 2000
    stale-claim-timeout-minutes: 15
    max-retries-default: 3
    backoff-base-seconds: 10
```

### 7.2 Backward compatibility

When `kazi.sharding.enabled: false` (or absent), the system behaves identically to today:
- `SchemaMultiTenantConnectionProvider` is used (not `ShardAwareConnectionProvider`)
- `TenantIdentifierResolver` returns schema name only (no shard prefix)
- `OrgSchemaMapping.shard_id` is ignored
- Single DataSource, no ShardRegistry

This ensures zero risk to existing deployments.

---

## 8. Observability

### 8.1 Job queue metrics (Prometheus)

```
kazi_job_queue_enqueued_total{job_type}         -- counter
kazi_job_queue_completed_total{job_type}        -- counter
kazi_job_queue_failed_total{job_type}           -- counter
kazi_job_queue_dead_letter_total{job_type}      -- counter
kazi_job_queue_pending_count{job_type}          -- gauge
kazi_job_queue_claimed_count{job_type}          -- gauge
kazi_job_queue_execution_seconds{job_type}      -- histogram
kazi_job_queue_claim_wait_seconds{job_type}     -- histogram (time from enqueue to claim)
```

### 8.2 Shard metrics

```
kazi_shard_connection_pool_active{shard_id}     -- gauge
kazi_shard_connection_pool_idle{shard_id}       -- gauge
kazi_shard_connection_pool_pending{shard_id}    -- gauge
kazi_shard_tenant_count{shard_id}               -- gauge
```

---

## 9. Test Strategy

### 9.1 Job queue tests

- **Unit tests**: `JobEnqueuer` dedup logic, backoff calculation, stale claim recovery.
- **Integration tests**: Full enqueue → claim → execute → complete cycle with real PostgreSQL. Verify `FOR UPDATE SKIP LOCKED` behavior with concurrent worker threads.
- **Migration verification**: Each migrated scheduler's `JobHandler` produces identical side effects to the old `forEachTenant` path (characterization tests).

### 9.2 Shard tests

- **Single-shard characterization tests**: Run the full test suite with sharding enabled but only the `primary` shard configured. All tests must pass identically — this is the critical backward-compatibility gate.
- **Multi-shard integration tests**: Provision a tenant on a second in-memory DataSource. Verify: CRUD operations route to the correct shard, Flyway migrations run on both shards, `TenantScopedRunner` iterates tenants across shards.
- **Shard isolation test**: Tenant on shard A cannot access data on shard B, even with crafted tenant identifiers.

### 9.3 Test profile

Tests use embedded PostgreSQL (existing pattern). Multi-shard tests create a second embedded instance. No Testcontainers — per project convention.

---

## 10. Out of Scope

- **Outbox pattern (B1)** — domain event delivery stays fire-and-forget. Job queue is for scheduled work only.
- **Circuit breakers (B2)** — external service resilience is a separate concern.
- **Redis caching (B5)** — cache infrastructure is independent of job queue and sharding.
- **Microservice extraction (Tier C)** — SNS/SQS, shared auth library, contract testing are all deferred.
- **Automated shard selection** — no round-robin or least-loaded algorithms. Platform admin assigns explicitly.
- **Tenant shard migration tooling** — the data model supports it, but the migration utility (pg_dump/restore automation) is future work.
- **Frontend dashboard for job queue** — admin API only. A dashboard can be built later.
- **Frontend shard management UI** — shard configuration is platform-admin-level, done via API/config files.

## 11. ADR Topics to Address

| ADR | Topic |
|-----|-------|
| ADR-293 | Job queue table over in-process parallelism (StructuredTaskScope) — why distributed work distribution via `FOR UPDATE SKIP LOCKED` over in-JVM parallel streams. Multi-pod scalability, visibility, retry semantics. |
| ADR-294 | Scheduler-as-enqueuer pattern — schedulers keep their cron triggers but only fan out work items. Separation of scheduling concern from execution concern. |
| ADR-295 | Control plane / shard plane database split — cross-tenant tables on control plane, tenant schemas on shard databases. Why not a fully shared-nothing model. |
| ADR-296 | Composite tenant identifier (`shardId:schemaName`) — Hibernate receives a single string, connection provider parses both. Alternative: two separate resolution steps. |
| ADR-297 | Explicit shard assignment over automatic placement — platform admin chooses shard. Vertical-aligned naming convention. Why not consistent hashing or least-loaded. |

## 12. Style & Boundaries

- All new code in `io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue` (job queue) and updates to `io.b2mash.b2b.b2bstrawman.multitenancy` (sharding).
- `JobHandler` implementations live in their existing domain packages (e.g., `AccountingSyncDrainHandler` in `integration.accounting.sync`).
- No new frontend pages or components in this phase.
- Backend-only phase. Portal and frontend are unaffected.
- Configuration is `application.yml` — no runtime admin UI for shard management in this phase.
