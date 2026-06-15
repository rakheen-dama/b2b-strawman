# Backend Test Suite Speed Audit

**Date:** 2026-06-14
**Question asked:** Backend builds take excessively long. Audit tests for repeated coverage, slow tests, and low/zero-value tests; find why the build is slow.
**Status:** Investigation complete. **No code changed** — this is an assessment for you to act on later.

---

## TL;DR

- A full `./mvnw clean verify` is **~22 min**; **~21 min (≈92%) is test execution**, the rest is compile/spotless/pmd/package.
- The suite runs **5,651 tests across 695 classes** in **39 distinct Spring contexts**. Green: 0 failures, 0 errors, 26 skipped.
- **The slowness is NOT the Spring context cache.** I hypothesised cache thrashing (default `maxSize=32` vs ~32 contexts) and tested it. It was wrong — see "Debunked hypothesis" below. Raising the cache to 64 saved **~0 wall time** (within run-to-run noise).
- **The real cost is integration-test weight**: ~39 one-time Spring context builds (~3–10s each) + a **second embedded Postgres** booted by the shard tests + **heavy per-tenant provisioning** (each provision = 125 Flyway migrations + ~12 seeders/pack-installs) + a few **real-time `await`/backoff sleeps**.
- **~50% of all test time lives in ~35 classes.** The other 660 classes are mostly already cheap (453 classes run under 1s).
- **Honest expectation:** safe, test-only fixes can take this from ~22 min to roughly **~19–20 min**. Getting materially below that needs structural work (shared-fixture provisioning, squashed migrations for tests, or parallel forks) that carries real risk and is an architectural decision.

---

## Measured baseline (this audit, two full runs)

| Run | Wall time | Tests | Contexts (final cache `size`) | `missCount` |
|---|---|---|---|---|
| Baseline (`maxSize=32`, default) | **21:51** | 5651, 0F/0E, 26 skipped | 32 (cache full) | 40 |
| Experiment (`maxSize=64`) | 22:50 | 5651, 0F/0E, 26 skipped | **39** (all cached, no eviction) | 39 |

Method: `./mvnw clean verify -Dlogging.level.org.springframework.test.context.cache=DEBUG`. Wall time has run-to-run variance of at least ±1 min on this machine (JIT warmup, OS cache, background load), so the +59s on the experiment is **not** a real regression — it's noise, plus the cost of retaining 7 more live contexts in heap.

---

## Debunked hypothesis: the context cache was NOT the bottleneck

The initial theory: 32-slot default cache + ~32 distinct contexts → LRU eviction → expensive context rebuilds.

What the data actually showed:
- There are exactly **39 distinct contexts** (revealed by `size=39` once `maxSize` was raised).
- Baseline ran with `maxSize=32` and recorded **`missCount=40`**. With 39 distinct contexts, 40 total builds means only **1 redundant rebuild** — not the ~8 I estimated.
- Why so few rebuilds despite 39 > 32? **Test execution order is favorable.** Surefire runs class-by-class; once all classes needing a given context have run, evicting that context is free. Only one evicted context was ever needed again.
- Raising `maxSize` to 64 dropped `missCount` 40→39 (eliminated that single rebuild) and saved no measurable time.

**Conclusion:** a `maxSize` bump is harmless future-proofing (prevents thrashing if the suite grows well past 39 contexts) but is **not a speedup**. Do not sell it as one. The correct mechanism, if ever wanted, is a classpath `spring.properties` with `spring.test.context.cache.maxSize=N` — **note that `junit-platform.properties` does NOT work for this property** (verified empirically; Spring reads it via `SpringProperties`, not JUnit Platform config).

---

## Where the 20.9 min of test time actually goes

Class-time distribution (fresh surefire reports, one full run):

| Per-class time | Classes | Total | % of test time |
|---|---|---|---|
| > 30s | 3 | 98s | 7.8% |
| 10–30s | 26 | 469s | **37.4%** |
| 5–10s | 14 | 97s | 7.7% |
| 2–5s | 50 | 145s | 11.5% |
| 1–2s | 149 | 200s | 15.9% |
| 0.5–1s | 286 | 236s | 18.8% |
| < 0.5s | 167 | 10s | 0.8% |

**The top ~35 classes ≈ 50% of all test time.** The bottom 453 classes (< 1s each) are already cheap; there is little to win there without deleting tests.

### Top 35 hot classes (cumulative)

| Time | Cum % | Class | Dominant cost |
|---|---|---|---|
| 34.2s | 2.7% | SubscriptionItnIntegrationTest | 7× `provisionTenant` inside `@Test` methods + own `@MockitoBean` context |
| 33.4s | 5.4% | ObservabilityTest | unique `@TestPropertySource` (sharding+jobqueue) context |
| 30.3s | 7.8% | JobWorkerIntegrationTest | real exponential backoff: dead-letter test waits 2+4+8=14s |
| 29.3s | 10.1% | StatementControllerIntegrationTest | 2 legal-za provisions + own `@Import` context |
| 28.1s | 12.4% | DemoDataSeederIntegrationTest | full demo seed (heavy provisioning) |
| 26.5s | 14.5% | PackReconciliationRunnerTest | `reconciliationRunner.run(null)` fans out over ALL accumulated tenant schemas (O(N)) |
| 26.4s | 16.6% | PortalRetainerControllerIntegrationTest | `@MockitoBean VerticalModuleGuard` own context + 2 provisions |
| 24.1s | 18.5% | PortalDeadlineControllerIntegrationTest | own context + legal-za provision |
| 22.3s | 20.3% | InvokeAiSpecialistActionExecutorIntegrationTest | own context (`@MockitoBean` + fake config) |
| 22.2s | 22.1% | EndToEndMultiShardTest | **secondary embedded Postgres** + `@DynamicPropertySource` context |
| 20.9s | 23.7% | SchedulerMigrationBatch1BTest | unique `@TestPropertySource` context + Awaitility |
| 20.8s | 25.4% | AutomationSchedulerScheduledTriggerIntegrationTest | `@MockitoBean`+`@MockitoSpyBean` own context |
| 20.5s | 27.0% | JobWorkerParallelismTest | jobqueue context + slow handler sleeps |
| 19.7s | 28.6% | SecurityAuditTest | unique `@TestPropertySource(logging=WARN)` context |
| 19.5s | 30.1% | ShardIsolationTest | **secondary Postgres** + `@DynamicPropertySource` context |
| 19.0s | 31.7% | ShardAwareFlywayTest | sharding context + provisioning |
| 17.9s | 33.1% | SingleShardFullCharacterizationTest | sharding+jobqueue context |
| 17.6s | 34.5% | ShardMigrationDataSourceTest | **secondary Postgres** + `@DynamicPropertySource` context |
| 15.2s | 35.7% | PortalDigestSchedulerIntegrationTest | provisioning + scheduler |
| 15.0s | 36.9% | ShardAwareProvisioningTest | sharding context + provisioning |
| 12.9s | 37.9% | CapabilityAuthorizationTest | own `@Import(TestConfig)` context |
| 12.7s | 38.9% | AiExecutionGateServiceTest | `@MockitoBean` context |
| 12.6s | 39.9% | SecurityIntegrationTest | provisioning + security chain |
| 12.2s | 40.9% | MemberFilterJitSyncTest | `@TestPropertySource(jit-provisioning)` context |
| 11.4s | 41.8% | TagIntegrationTest | provisioning + many small tests |
| 11.1s | 42.7% | DemoProvisionServiceTest | `@MockitoBean` + provisioning |
| 10.8s | 43.6% | EmailWebhookControllerIntegrationTest | `@DynamicPropertySource` (ECDSA key) context |
| 10.4s | 44.4% | XeroIntegrationControllerIntegrationTest | `@MockitoBean` context |
| 10.2s | 45.2% | AccountingSyncControllerIntegrationTest | `@MockitoBean` context |
| 9.9s | 46.0% | AccountingSyncWorkerTest | `@MockitoBean` context |
| 9.7s | 46.8% | XeroCustomerImportServiceTest | `@MockitoBean` context |
| 9.4s | 47.5% | XeroOAuthServiceTest | `@MockitoBean` context |
| 8.8s | 48.2% | DemoCleanupServiceTest | `@MockitoBean` + provisioning |
| 8.1s | 48.9% | AnthropicApiClientTest | (HTTP client test) |
| 6.7s | 49.4% | TenantFilterJitProvisioningTest | `@TestPropertySource` context |

---

## The three real cost drivers

### 1. Distinct Spring context builds (~39 contexts, ~3–10s each)
Most classes (459) share one **baseline context** — good. The remaining ~38 contexts each cost a one-time build. The hot classes above are almost all "context-unique" classes. **Fewer distinct contexts = fewer builds = faster.** This is where the original plan's "consolidation" work has genuine value (I under-valued it when I thought the cache was the issue). The context the build actually pays for: each `@MockitoBean` type-set, each distinct `@TestPropertySource` property-set, each inner `@TestConfiguration`/`@Import`, each `@DynamicPropertySource` (always unique).

### 2. A second embedded Postgres in the shard cluster (~110s across 6 classes)
`EndToEndMultiShardTest`, `ShardIsolationTest`, `ShardMigrationDataSourceTest`, `ShardAwareFlywayTest`, `ShardAwareProvisioningTest`, `SingleShardFullCharacterizationTest` each pay a unique `@DynamicPropertySource` context build, and the multi-shard ones boot a **secondary embedded Postgres** and migrate it. This is the single richest vein, but consolidating it touches multitenancy test infrastructure (risk).

### 3. Heavy per-tenant provisioning + real-time waits
`provisionTenant` runs **125 tenant Flyway migrations + ~12 seeders/pack-installs** (field packs, clause packs, compliance, reports, rates, project templates, schedules, legal tariffs, mock PSP). It is idempotent (re-calls for an already-provisioned org short-circuit), so the *number of real full provisions* is far less than the 702 call sites — but every context-unique class triggers at least one fresh, heavy provision (legal-za/consulting-za profiles install the most packs). Plus a few classes burn real clock on `await`/backoff (JobWorker dead-letter = 14s of real exponential backoff under a 30s ceiling).

---

## Audit findings: repeated coverage, slow, low/zero-value tests

These came from a focused sweep (read both files before every duplication claim — a prior audit wrongly flagged controller/integration pairs as duplicates).

### Zero / low-value
| Class | Issue | Tests | Note |
|---|---|---|---|
| `AcceptanceControllerIntegrationTest` | `@Disabled` (stale "LocalStack" reason; LocalStack removed, `InMemoryStorageService` is `@Primary`) | 14 | Dead. You chose to leave as-is for now. Cheap (shares baseline context, doesn't execute). |
| `PortalAcceptanceControllerIntegrationTest` | `@Disabled`, same stale reason | 12 | Same. |
| `BillingPropertiesTest` | full `@SpringBootTest` just to assert `@ConfigurationProperties` literal values | 3 | Could be a slice / `ApplicationContextRunner`. Low value, low cost. |
| `ShardConfigMigrationTest` (nested `ShardAndSchemaParsingTest`) | 9 pure value-object tests wrapped in `@SpringBootTest` | 9 | Extract to a plain JUnit test (no Spring context). |

### Proven duplicate coverage (conservative — only what was provable by quoting both files)
| Finding | Verdict | Action if pursued |
|---|---|---|
| `TrustAccountingMigrationTest.v85CreatesAllTenTables` vs `TrustCoexistenceTest` table-existence | identical 10-table `information_schema` assertion | delete the duplicate method (1 test) |
| `SingleShardCharacterizationTest` CRUD vs `SingleShardFullCharacterizationTest` | identical create/read/list project assertions; **different contexts** | merge the 1–2 unique tests into Full, delete the other class (removes 1 context build) |
| `SchedulerMigrationBatch1A` 3 individual handler-registration checks | subsumed by `Batch1B.allFiveBatch1HandlersPresent` | delete 3 methods (no context saving) |
| `SchedulerMigrationBatch2A.allTwelveHandlersRegistered` | strict subset of `Batch2B.allNineteenHandlersRegistered` | delete 1 method |

### NOT duplicates (investigated, keep both)
Trust transaction Controller vs Service; DataExport Controller/Service/AuditTrail triple; AccessRequest Public/Approval/Verify triple; `TrustCoexistenceTest` vs `MultiVerticalCoexistenceTest`; `ShardAwareFlywayTest` vs `ShardMigrationDataSourceTest`. Schema-pin tests (`OrgSettingsSchemaSnapshotTest` etc.) are **high value — keep**. Most `*MigrationTest` files assert DB-level constraints (CHECK, FK cascade, unique) not covered by functional tests — keep.

### Context fragmentation enumeration (the 39 contexts)
1 baseline (459 files). The other ~38: 9 distinct `@TestPropertySource` property-sets, ~15 distinct `@MockitoBean` type-sets, 8 inner `@TestConfiguration`/`@Import` variants, 4 `@DynamicPropertySource` (all justified — runtime-generated values). Notable cheap merges:
- `@MockitoBean StorageService` appears in **10 files** but `InMemoryStorageService` is already the `@Primary` test bean — files that don't `verify()` the mock could drop it and fold back into the baseline context.
- `VerticalModuleGuard`: `PortalDeadlineControllerIntegrationTest` already uses a shared `ModuleGuardTestConfig`; `PortalRetainerControllerIntegrationTest` + `PortalTrustControllerIntegrationTest` still use `@MockitoBean` → two separate contexts that could be one.
- `SchedulerMigrationBatch1B`'s `dual-mode.accounting_sync_drain=false` property is already the default → its separate context may be unnecessary.

---

## Remediation options (for later decision)

Ranked by confidence-of-payoff vs risk. Honest expected total saving in parentheses.

**A. Low-risk test-only fixes (~1–2 min; ~5–7 small PRs)**
- JobWorker dead-letter: `maxRetries` 3→1, drop `atMost(30s)`→6s (saves ~12s, zero correctness risk — still proves the dead-letter path).
- Context consolidation: merge ModuleGuard contexts; drop redundant `@TestPropertySource`; drop `@MockitoBean StorageService` where no `verify()`. Each merge removes a ~3–10s context build.
- Move pure-unit tests off `@SpringBootTest` (`ShardAndSchemaParsingTest`, `BillingPropertiesTest`).
- Delete the proven duplicate assertions above (noise reduction).
- *Skip the SubscriptionItn "provision once + reset" refactor* — the `Subscription` entity has a validated state machine with no public status setter; resetting fights the billing invariants the test protects. Higher risk than its ~20s is worth.

**B. Structural, higher-risk (potential ~3–5 min, needs design sign-off)**
- **Shard cluster (~110s):** share one secondary embedded Postgres and collapse the 6 unique `@DynamicPropertySource` contexts where possible.
- **Cheaper test provisioning:** provision one "golden" tenant schema per JVM, then clone its DDL for subsequent tenants instead of re-running 125 migrations + 12 seeders. Test-only override of `TenantProvisioningService`. Biggest lever, biggest blast radius.
- (Not recommended) squashing the 125 prod migrations into a baseline — speeds provisioning everywhere including prod, but high coordination/risk on existing schema history.
- (Contraindicated) parallel surefire forks — would multiply context builds and break the fixed-port GreenMail singleton.

**C. Accept ~22 min** as the inherent cost of a 5,651-test integration-first suite and keep only documentation of the above.

---

## Side observation (separate from speed)
The full-verify log contains a caught `DataIntegrityViolationException` from `PackReconciliationRunner` ("duplicate key … uq_field_group_type_slug") for `org_dsr_svc_test`. The build stays green (it's logged, not thrown to a test), but it confirms `PackReconciliationRunnerTest`'s `run(null)` fans out across all accumulated tenant schemas and collides — the O(N) behavior noted above. Worth a look independent of the speed work.

---

## Verification method (for whoever implements later)
- Measure with `./mvnw clean verify` wall time; expect ±1 min machine noise, so compare 2 runs.
- For context-count changes, add `-Dlogging.level.org.springframework.test.context.cache=DEBUG` and read the final `size`/`missCount` (lower `size` = fewer context builds).
- Per backend/CLAUDE.md: full clean `verify` is the merge bar; reproduce a class's slow time before/after; never enable parallel forks; kill stale Maven JVMs and confirm `:13025` is clear before timing.
