# Backend Test Provisioning & Structural Speedup — Design Spec (Phase 2)

**Status:** DRAFT for sign-off. No implementation until approved (architectural decision per CLAUDE.md §7).
**Date:** 2026-06-23
**Author:** test-speed cycle (Phase 2 of `~/.claude/plans/iterative-gliding-sundae.md`)
**Predecessor:** Phase 1 (flake-fix + JobWorker backoff + PackReconciliation scoping) — see git log `test-speed/*`, `fix/automation-scheduler-count-bleed-line305`.

---

## 1. Context & problem

Full `./mvnw clean verify` is **~23–26 min** (5809 tests, 739 class-runs, ~24 min pure test execution). Phase 1 verified that the cost is **not** test count or duplication — it is:

1. **Per-tenant provisioning** — every integration test that provisions a tenant runs **129 Flyway tenant migrations + 15 seeders** (`provisioning/TenantProvisioningService.provisionTenant` → `RequestScopes.runForTenantOnShard`). There is **no golden-schema/clone cache**: each fresh tenant re-runs the whole pipeline. `AbstractIntegrationTest` centralizes provisioning for only **17 of ~549** `@SpringBootTest` classes; the rest hand-roll it in `@BeforeAll`.
2. **Shard cluster** — 6 classes (`EndToEndMultiShardTest`, `ShardIsolationTest`, `ShardMigrationDataSourceTest`, `ShardAwareFlywayTest`, `ShardAwareProvisioningTest`, `SingleShardFullCharacterizationTest`) each pay a unique `@DynamicPropertySource` context build and boot/migrate a **second embedded Postgres** (`infrastructure/testutil/SecondaryEmbeddedPostgres`). ≈138s combined.
3. **~40 distinct Spring contexts** (cache `maxSize=32`, so the suite now exceeds the cache and evicts). NOTE: raising `maxSize` is **contraindicated** — more live contexts → higher peak heap → re-triggers the CI OOM that PR #1487's `-Xmx3g` pin just fixed.

Phase 1 already removed the two cheapest structural wins it found (JobWorker 14s→2s; PackReconciliation O(N)→O(1) per test). This spec covers the remaining structural levers, which require sign-off because they touch shared test infrastructure used by hundreds of classes.

### Measured baseline (HEAD, 2026-06-22)
| Metric | Value |
|---|---|
| `clean verify` wall | ~23 min (22:53 observed on flake-fix branch; ±1–2 min machine noise) |
| Tests | 5809, 26 skipped |
| Distinct contexts | ~40 (cache full at 32 + evictions; missCount 40) |
| Top hot classes | PackReconciliationRunnerTest 37.7s* · JobWorkerIntegrationTest 34.5s* · StatementControllerIntegrationTest 34.4s · SubscriptionItnIntegrationTest 31.8s · shard cluster ~138s |

\* Phase 1 measured outcome (verified `clean verify` 5809/0, 23:02): **PackReconciliationRunnerTest 37.7s → 2.4s** (~35s), **JobWorkerIntegrationTest 34.5s → 27.6s** (~7s). ~42s of test-execution removed; below single-run wall noise, so per-class numbers are the proof. **New top hot classes for Phase 2:** StatementControllerIntegrationTest 34.4s · SubscriptionItnIntegrationTest 31.8s · shard cluster ~138s — all provisioning-bound (Lever A) or shard (Lever B).

---

## 2. Lever A — Golden-schema / template-clone provisioning (biggest payoff, biggest blast radius)

### Premise
A freshly provisioned test tenant is deterministic: 129 migrations + 15 seeders produce the **same** DDL + reference data every time (no test data yet at provision time). So instead of re-running the pipeline per tenant, provision **one golden tenant per vertical profile** once per JVM and **clone** it.

### Mechanism options (must be validated by a spike — Postgres has no `CREATE SCHEMA LIKE`)
| Option | How | Pros | Cons / risks |
|---|---|---|---|
| **A1. Schema-only DDL+data snapshot replay** (recommended to spike first) | After provisioning a golden tenant, `pg_dump --schema=<golden> --no-owner` once → a SQL string. Per test tenant: `CREATE SCHEMA tenant_x` + replay the dump with the schema name rewritten. Include `flyway_schema_history` so startup migration sees it current. | Skips Flyway per-step overhead AND seeder business logic. zonky ships the `pg_dump` binary. | Schema-rename rewriting (search_path-qualified objects, sequences, FKs); dump/replay correctness; one dump per profile. |
| **A2. SQL `clone_schema()` function** | Install a known `clone_schema(src, dst, copy_data)` plpgsql function; call per tenant. | No external process; in-DB. | Complex (sequences, defaults, FKs, check constraints); maintenance burden; must track new object types. |
| **A3. Template database** (`CREATE DATABASE x TEMPLATE golden`) | Template the whole DB. | Native, fast. | **Mismatch**: model is schema-per-tenant in ONE db; templating copies all schemas, not one tenant. Would require per-test databases + datasource re-pointing — large rework. Likely reject. |

### Integration point
- Add a test-only `TenantProvisioningStrategy` seam (or override `provisionTenant` behind a test bean) that clones from the golden instead of running the pipeline.
- Extend `testutil/AbstractIntegrationTest` so its 17 subclasses get it free; provide a `TestTenant.provision(orgId, profile)` helper for the hand-rolled `@BeforeAll` callers to migrate onto incrementally.
- Keep the real pipeline as the golden-builder (run once per profile per JVM) so coverage of the real migration/seeder path is preserved.

### Open questions the spike MUST answer (do not assume)
1. **Clone cost vs pipeline cost** — measure A1 clone time per tenant vs the 129-migration+15-seeder time it replaces. If clone isn't materially cheaper, abort.
2. **Mutation isolation** — each test still gets its own fresh schema (cloned), so today's isolation holds. Confirm no test relies on running migrations/seeders themselves (the carve-outs below).
3. **Profile coverage** — legal-za / consulting-za / accounting-za install the most packs; need one golden per profile actually exercised; null-profile golden too.
4. **Flyway history consistency** — cloned schema's `flyway_schema_history` must satisfy the startup `TenantMigrationRunner` (no re-migration, no "checksum mismatch").

### Carve-outs (MUST keep the real pipeline — clone would mask drift/bugs)
- `OrgSettingsSchemaSnapshotTest` and all `*MigrationTest` classes that assert DDL/constraints (CHECK, FK cascade, unique) — must run real migrations.
- `ShardAwareFlywayTest` / `ShardMigrationDataSourceTest` / `ShardAwareProvisioningTest` — assert migration behavior directly.
- `DemoDataSeederIntegrationTest`, `PackReconciliationRunnerTest`, `DemoProvisionServiceTest` — exercise seeders/reconciliation as the unit under test.

### Expected payoff
Unknown until the spike; potentially large (provisioning is the dominant recurring cost across hundreds of classes). Honest: this is the only lever that can move the suite materially below ~20 min. Also the highest risk — gate on the spike's measured clone-vs-pipeline delta.

### SPIKE RESULT (2026-06-23, branch `spike/golden-schema-clone-cost`)
Measured on zonky embedded PG16, legal-za profile (`GoldenSchemaCloneSpikeTest`):
- golden schema = **115 base tables**
- **pipeline** (129 migrations + 15 seeders) = **943 ms/tenant** (883–1086)
- **clone** (CREATE TABLE LIKE INCLUDING ALL + INSERT SELECT, FK triggers off) = **245 ms/tenant** (221–282)
- **speedup = 3.8×** — passes the literal ≥3× gate.

**But two caveats reframe the lever:**
1. The spike clone is NOT production-faithful — it skips FK recreation and sequence isolation. A real clone must do both across 115 tables, eroding 3.8× toward **~2.5–3× (borderline/fails the gate).**
2. Pipeline cost is **only ~943 ms/tenant** — provisioning is not a multi-second monster. With ~100–200 real fresh provisions suite-wide, Lever A's **total ceiling is ~100–140s (~8–10%)** — and it has the **largest blast radius of any lever** (every integration test).

**Revised recommendation:** Lever A is borderline-on-gate with a modest ceiling and maximum blast radius. **Prefer Lever B** (shard cluster, ~138s, only 6 classes — comparable absolute saving, far less risk) and **Lever C** (correctness). Revisit Lever A only if a cheaper clone (template database, or filesystem snapshot) or a "shared read-only golden tenant for non-mutating tests" approach changes the economics. Spike test is throwaway — do NOT merge.

---

## 3. Lever B — Shard cluster consolidation (~138s, 6 classes, bounded blast radius)

### Current shape
Each shard class uses `@DynamicPropertySource` (always a unique context cache key) and references the JVM-singleton `SecondaryEmbeddedPostgres`. They variously register `KAZI_SHARD_SHARD2_URL` (+ `_MIGRATION_URL`, `SHARD3_URL`) and migrate the secondary.

### Approach
- Introduce a shared abstract base (e.g. `AbstractShardClusterTest`) that registers the secondary-shard `@DynamicPropertySource` **once** with a single, consistent property-set, so the 6 classes collapse toward **one** shard context cache key instead of up-to-6.
- Keep the secondary Postgres as the existing JVM singleton (already shared); ensure all 6 reuse it without re-migrating redundantly.
- Carve out classes that genuinely need a *different* shard topology (e.g. shard3-without-migration-url fallback in `ShardMigrationDataSourceTest`) — those keep their own key by necessity.

### Open questions
1. How many of the 6 `@DynamicPropertySource` property-sets are actually identical vs intentionally different? (Determines how many contexts collapse.)
2. Does collapsing contexts change test isolation for shard-registry state (`ShardRegistry.refresh()` is called per class)?

### Expected payoff
**CORRECTED after investigation (2026-06-23).** The secondary Postgres is ALREADY a JVM singleton (`SecondaryEmbeddedPostgres.POSTGRES` is `static final`, booted once, shared by all 3 multi-shard classes) — there is **no redundant secondary-PG-per-class to remove**. The only reclaimable cost is duplicate **context builds**. Measured/estimated collapsible keys: `EndToEndMultiShardTest`+`ShardIsolationTest` share one config (their only differentiator is per-class `@DynamicPropertySource` → replace with a shared `@Import`ed `DynamicPropertyRegistrar` bean for `KAZI_SHARD_SHARD2_URL`), and a couple of single-shard keys may merge if their `@TestPropertySource` sets are truly identical (the sub-agent's grouping had inconsistencies — verify against the cache-DEBUG map before editing). `ShardMigrationDataSourceTest` (shard2+shard3, shard3 deliberately no migration URL) MUST stay separate. **Realistic wall-time saving: ~4–6s.** The real value is **context-cache-key reduction → CI OOM headroom** (per [[backend_test_heap_oom_2026-06]], the durable fix is culling context keys), plus cleaner shard test infra. Low-to-medium risk, but tiny wall-time ROI.

---

## 4. Lever C — Seeder non-idempotency (correctness, surfaced by Phase 1)

`PackReconciliationRunner.run()` at startup logged a caught `DataIntegrityViolationException` (`uq_field_group_type_slug` on `org_dsr_svc_test`) when reconciling an accumulated tenant. Phase 1 scoped the *test* away from it, but the **root cause remains**: some pack seeder is not idempotent for a particular tenant state, so a second reconciliation pass collides on a unique constraint. In production this is caught per-tenant (logged, `failed++`), so it is not a build-breaker — but it means startup reconciliation silently fails for affected tenants.

**Action:** a separate reproduce-before-fix investigation (own PR): identify which seeder violates `uq_field_group_type_slug` on a re-run and make its upsert truly idempotent (`ON CONFLICT DO NOTHING`/`DO UPDATE`). Out of scope for the speed work; tracked here so it is not lost.

---

## 5. Recommended sequencing
1. **Spike Lever A1** (golden-schema snapshot replay) on a throwaway branch — measure clone-vs-pipeline per-tenant cost on zonky PG16. Decision gate: proceed only if clone is ≥3× cheaper than the pipeline.
2. If A1 proves out → implement behind the `AbstractIntegrationTest` seam, migrate the 17 subclasses first, measure, then expand to hand-rolled callers in batches (each batch its own PR + full verify).
3. **Lever B** (shard consolidation) — independent of A; can run in parallel as its own PR.
4. **Lever C** (seeder idempotency) — independent correctness PR.

## 6. Verification (per lever)
- Reproduce-before: record current per-class time(s) from the baseline surefire reports.
- After: targeted class time + full `./mvnw clean verify` green (the merge bar); compare 2 full runs for the aggregate wall-time claim (±1–2 min noise). For context-count changes, `-Dlogging.level.org.springframework.test.context.cache=DEBUG` and read final `size`.
- Golden-schema: a dedicated test asserting a cloned tenant is byte-identical (DDL + reference rows) to a pipeline-provisioned one, so clone fidelity is pinned.
