# Phase 75 Ideation — Scalability: Job Queue Fanout + Shard-Aware DB Resolver
**Date**: 2026-05-27

## Catalyst
Founder wants to address two specific scalability bottlenecks from the Tier B/C spec (`kazi-scalability-req.md`), but with different solutions than originally proposed. Not incremental hardening — foundational infrastructure for horizontal scaling.

## Decision
**Job queue table for scheduler fanout + full shard-aware DB resolver.** Founder proposed both topics with clear direction: (1) schedulers become enqueuers, workers execute, replacing the sequential `TenantScopedRunner.forEachTenant()` loop; (2) multi-DataSource infrastructure with explicit shard assignment by platform admin.

## Key design choices (founder-selected)
1. **Job queue over StructuredTaskScope** — replaces B3 from the scalability spec. Distributed work distribution via `FOR UPDATE SKIP LOCKED` instead of in-JVM parallelism. Multi-pod scaling, visibility, retry for free.
2. **Job queue for scheduled work only** — outbox pattern (B1) deferred. Domain events stay fire-and-forget until Tier C.
3. **Full shard routing, not seam-only** — multi-DataSource infrastructure, shard-aware connection provider, shard-aware provisioning. Zero-change with single shard.
4. **Explicit shard assignment by platform admin** — no automated algorithms. Shard names are vertical-aligned: `primary`, `demo`, `kazi_accounting_1`, `kazi_legal_1`. Supports the vertical fork model.
5. **Control plane / shard plane split** — `org_schema_mapping`, `shedlock`, `job_queue` on control plane DB. Tenant schemas on shard DBs. Initially same Postgres instance.

## What was explicitly rejected
- StructuredTaskScope parallelism (B3 as originally specified) — too JVM-local, no multi-pod benefit
- Outbox pattern (B1) — deferred, not needed until service extraction
- Automated shard selection (round-robin, least-loaded) — too early, explicit assignment is clearer
- Seam-only approach to sharding — founder wants full implementation, not just interfaces

## Architecture notes
- Composite tenant identifier `{shardId}:{schemaName}` for Hibernate resolution
- `ShardRegistry` manages named DataSources, primary shard reuses Spring Boot default
- Flyway migrations must run per-shard
- `kazi.sharding.enabled: false` gives exact backward-compatible behavior
- V24/V25 global migrations, V128 tenant migration (if needed)
- ADRs 293-297

## Scope snapshot
- 2 new global tables: `job_queue`, `shard_config`
- 1 altered table: `org_schema_mapping` + `shard_id` column
- New package: `infrastructure.jobqueue`
- Major refactors: `SchemaMultiTenantConnectionProvider` → `ShardAwareConnectionProvider`, `TenantIdentifierResolver`, `TenantScopedRunner`, `RequestScopes`, `TenantFilter`, `TenantAwareFlyway`
- 19 scheduler methods migrated to enqueue-only pattern with corresponding `JobHandler` implementations
- Estimated: 8-9 epics, ~15 slices, backend-only

## Phase roadmap after 75
- **Phase 76 candidates**: (a) Circuit breakers (B2) — natural next scalability item. (b) Redis cache (B5) — enables multi-pod caching. (c) Async PDF (B4) — CPU offloading. (d) Return to feature work (AI skills from Phase 74 backlog).
- **Scalability sequencing**: Job queue + sharding are the two highest-leverage items. B2/B5 are important but less foundational. B1 (outbox) gates Tier C service extraction.

## Next step
`/architecture requirements/claude-code-prompt-phase75.md`
