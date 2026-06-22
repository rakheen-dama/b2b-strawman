# Maven Build Instability — Problem Report

**Date:** 2026-06-22
**Author:** orchestration session (Epic 575 cycle)
**Trigger:** Epic 575 (PR #1489) passed `./mvnw clean verify` locally and on its prior commit's CI, then failed CI on the next commit with a single flake in an unrelated test (`AutomationSchedulerScheduledTriggerIntegrationTest`, expected 5 / got 13). A re-run went green with no code change. This report generalises that incident.
**Scope:** Backend Maven build (`./mvnw verify`). Frontend/portal are out of scope.
**Companion doc:** `tasks/backend-test-suite-audit.md` (speed audit, 2026-06-14) — instability and slowness share the same root cause (integration-first suite), so the two should be read together.

---

## TL;DR

The backend build is **not flaky because of one bad test** — it is flaky because of a **structural property**: ~5,800 tests are almost all full `@SpringBootTest` integration tests that share mutable, accumulating state (one Postgres, fixed-port singletons, long-lived tenant schemas, shared mock beans) and run for ~22 minutes against a resource ceiling. That combination produces four distinct, recurring failure modes:

1. **Cross-test state bleed** — tests see data other tests created; "expected N / got M" count mismatches. *(This is what bit Epic 575.)*
2. **Resource-ceiling failures** — heap OOM at CI's default JVM size; only papered over by a pinned `-Xmx3g`.
3. **Unclean fork shutdown** — Surefire force-kills the test JVM 60s after `System.exit`, risking a non-zero exit on an otherwise-green run.
4. **Local↔CI divergence** — the same commit is green locally and red in CI, because isolation gaps are order- and timing-sensitive and the local machine has more headroom and different test ordering.

None of these are fixed by retrying forever. They need either targeted test-isolation fixes or the structural changes already catalogued in the speed audit.

---

## 1. The dominant failure mode: cross-test state bleed

### What happened in Epic 575
The only CI failure on PR #1489 was:

```
AutomationSchedulerScheduledTriggerIntegrationTest
  .scheduledTrigger_projectScopedAiSpecialist_fansOutPerActiveProject
  expected: 5L  but was: 13L
[ERROR] Tests run: 5802, Failures: 1, Errors: 0, Skipped: 26
```

The PR touched only `crm/` (DealTransitionService), `audit/`, `activity/`, and `notification/`. `DealTransitionService` is called *only* by `DealController`; the `automation/` package never references CRM. There is **zero code dependency** between the change and the failing test. It is a pre-existing flake, surfaced by ordering, not caused by the PR.

### Why this test is structurally fragile
The test itself documents the hazard (verbatim from the source):

```java
// Count the tenant's ACTIVE projects up front: sibling tests in this PER_CLASS instance
// may have seeded other ACTIVE projects, so the fan-out fires once per ACTIVE project.
long activeProjectCount = projectRepository.findByStatus(ProjectStatus.ACTIVE).size();
...
scheduler.processScheduledTenant();
Mockito.verify(runner, Mockito.times((int) activeProjectCount)).run(...);
```

The mechanism that makes it flaky:

- **Accumulating tenant state.** The test runs in a `@TestInstance(PER_CLASS)` `@SpringBootTest` against a tenant schema that is **not reset between test methods**. Sibling tests seed `ACTIVE` projects that persist. The fan-out (`processScheduledTenant`) fires once per ACTIVE project in the *whole* tenant, so the expected count is a moving target.
- **Self-compensation with a race.** The test tries to be robust by measuring `activeProjectCount` *just before* firing the scheduler. But the expected value (`5`) and the actual invocations (`13`) diverged — meaning more ACTIVE projects existed at fan-out time than at the count, or the shared `runner` mock accumulated invocations from another path/ordering. The "count up front" pattern is an **admission the test isn't isolated**, and the compensation has its own window.
- **Shared mock bean.** `runner` is a context-cached `@MockitoBean`. `clearInvocations` is timing-sensitive; invocation counts are global to the mock for the verification window.

This is the canonical shape of the problem: **a count/equality assertion over state that other tests mutate.** Any test in the suite that asserts "there are exactly N rows / N invocations / N notifications" without a private tenant or a reset is a latent flake.

### Confirmed siblings of the same class
The speed audit's "Side observation" records the same root cause from a different angle:

> a caught `DataIntegrityViolationException` from `PackReconciliationRunner` ("duplicate key … uq_field_group_type_slug") … `run(null)` fans out across **all accumulated tenant schemas** and collides — O(N) behaviour.

Same disease: **global fan-out over state that grows as the suite runs.** It is benign today only because it's logged, not thrown.

---

## 2. Resource-ceiling failures (heap OOM)

`backend/pom.xml` pins the Surefire heap and explains why:

```xml
<!-- CI runs `mvnw test` with no explicit heap, so the forked test JVM used the
     runner default (~1.75g on a 2-core/7g ubuntu-latest). The full @SpringBootTest
     suite (up to 32 cached contexts) sits right at that ceiling and OOMs on the
     heaviest context load. Pin a headroom-safe 3g ... -->
<argLine>@{argLine} -Xmx3g</argLine>
```

- The suite holds **up to 39 distinct Spring contexts** live in the cache simultaneously (each a full application context + embedded Postgres connections + bean graphs). At CI's default ~1.75 GB this OOMs **independent of any single PR** — proven previously: main and a worktree both OOM at 1.75 g, both pass at ≥3 g.
- `-Xmx3g` is **runway, not a cure.** It buys headroom; it does not reduce the number of live contexts. As the suite grows (every distinct `@MockitoBean` set / `@TestPropertySource` / `@DynamicPropertySource` adds a context), the ceiling creeps back. The durable fix is **fewer distinct contexts**, which is exactly the audit's Driver #1.

---

## 3. Unclean fork shutdown (JVM kill timeout)

The CI log shows, right before the results summary:

```
[ERROR] Surefire is going to kill self fork JVM. The exit has elapsed 60 seconds after System.exit(0).
```

`pom.xml` sets `<forkedProcessExitTimeoutInSeconds>60</forkedProcessExitTimeoutInSeconds>` + `<shutdown>kill</shutdown>`. This means: after all tests finish and the JVM calls `System.exit(0)`, **something keeps the process alive for a full 60 s**, and Surefire then force-kills it.

- The lingering threads are almost certainly **non-daemon resources that aren't closed**: embedded Postgres processes, the GreenMail SMTP singleton, scheduler/JobWorker thread pools, Hibernate/Hikari pools across 39 contexts.
- Today this is masked (the kill happens *after* a clean `System.exit(0)`), but it is a **latent source of non-zero exit codes** and adds 60 s of dead time to every CI run. A shutdown that races the kill timer can flip a green run red.

---

## 4. Shared fixed-port singletons and zombie JVMs

The suite deliberately uses **shared, fixed-port singletons** because forks run sequentially:

- **GreenMail SMTP on `:13025`** — a JVM singleton; tests must reuse it, never start their own.
- **A second embedded Postgres** booted by the 6 shard-cluster tests.

These are correct design choices for a sequential suite, but they are **fragile under contention**:

- A **zombie Maven JVM** from a previous/killed run still holding `:13025` makes the next run's GreenMail fail at class-init with a connection-refused on startup — a hard, misleading failure unrelated to the code under test. (Hence the `pgrep -f surefire | kill` step in every build recipe.)
- This is why the build runbooks insist on killing stale Maven JVMs and confirming `:13025` is clear **before** timing or verifying. That manual prerequisite is itself a sign of instability: a clean checkout on a busy machine can fail for environmental reasons.

These fixed-port singletons are also why **parallel surefire forks are contraindicated** — parallelism would multiply context builds *and* collide on the shared ports. So the suite is locked into sequential execution, which is what makes it 22 minutes long, which is what amplifies every other flake.

---

## 5. Local↔CI divergence (the trust problem)

Epic 575's commit was **green on local `./mvnw clean verify` and red in CI** with no code difference. Causes:

- **No heap pin needed locally** — the dev machine has far more than 7 GB, so the OOM ceiling never bites; CI runs at 2-core/7 GB.
- **Test ordering differs** — the audit notes execution order is "favourable" for context eviction locally; CI can order classes such that more ACTIVE projects have accumulated before the AutomationScheduler test runs.
- **Timing differs** — slower CI hardware widens the windows in time-sensitive tests (`await`/backoff sleeps, `clearInvocations`, fan-out counts).
- **Machine noise** — the audit measured ±1 min wall-time variance run-to-run on the same machine; non-determinism is already baked in.

The practical consequence: **a green local verify is necessary but not sufficient.** Per the project's own quality gates, "PASS means observed" — but observed locally doesn't guarantee CI. This erodes trust in the merge bar and forces re-runs.

---

## 6. Scale amplifies everything

From the speed audit (2026-06-14):

- **~22 min** full `clean verify`; **~92% is test execution**.
- **5,651–5,802 tests across ~695 classes in 39 Spring contexts**, almost entirely integration-first (`@SpringBootTest` + MockMvc + real embedded Postgres).
- Each context-unique class triggers a fresh **heavy tenant provision: 125 Flyway migrations + ~12 seeders/pack-installs**.

Long, sequential, integration-heavy suites are **flake amplifiers**: more shared state, more wall-clock for timing windows to open, more resources held concurrently, and a 22-minute penalty per re-run when a flake does fire. Every percentage point of per-test flakiness becomes a near-certainty across 5,800 tests.

---

## Impact

- **False reds block merges.** Epic 575 was correct and fully reviewed, yet sat behind a ~44-min CI re-run because of an unrelated flake.
- **Re-runs are expensive.** Each CI Backend run is ~40 min; a flake doubles it.
- **Trust erosion.** "Green locally" and "green in CI" diverge, so neither can be fully trusted alone — contradicting the "PASS means observed" gate.
- **Masked real bugs.** When flakes are common, a genuine regression risks being dismissed as "just the flaky suite" (the project rule "never dismiss test failures as environmental" exists precisely because this temptation is real). The `PackReconciliationRunner` duplicate-key collision is an example of a real latent issue hiding in the noise.

---

## Recommendations

Ordered by payoff-to-risk. The structural items map onto the speed audit's remediation menu — fixing instability and slowness is the same work.

### Short term (low risk, do now)
1. **Fix the specific flake.** Give `AutomationSchedulerScheduledTriggerIntegrationTest` an isolated tenant per method, or assert on the *captured project IDs* (already done: `.contains(active1, active2).doesNotContain(inactive)`) and **drop the brittle `Mockito.times(activeProjectCount)` exact-count check** in favour of "≥ the two we seeded, and the inactive one absent." Exact global counts over shared state are the antipattern.
2. **Sweep for the same antipattern.** Grep for `Mockito.times(`, `.hasSize(`, `isEqualTo(<count>)`, and `findAll().size()` in `@SpringBootTest` classes that don't reset their tenant. Each is a candidate flake. Prioritise fan-out/scheduler/reconciliation tests (global `run(null)` style).
3. **Investigate the 60 s fork-kill.** Find the non-daemon resource keeping the JVM alive after `System.exit(0)` (likely embedded Postgres / GreenMail / a scheduler pool). Closing it removes 60 s/run *and* a latent non-zero-exit risk.
4. **Make a flake re-run cheap and visible.** A targeted re-run of only the failed class (instead of the full 40-min suite) plus a flake log would cut the cost of step 1's tail. Track re-run frequency so flakes don't hide.

### Medium term (structural — needs design sign-off; from the speed audit)
5. **Cut distinct Spring contexts (39 → fewer).** Each removed context lowers both the OOM ceiling pressure (§2) and build time. Merge `@MockitoBean StorageService` files where no `verify()`, consolidate ModuleGuard contexts, drop redundant `@TestPropertySource`. (Audit option A.)
6. **Cheaper, isolated tenant provisioning.** Provision one "golden" tenant schema per JVM and **clone its DDL per test** instead of re-running 125 migrations + 12 seeders — and reset/clone gives each test a clean tenant, killing the state-bleed class at the root. (Audit option B — biggest lever, biggest blast radius.)
7. **Share one secondary embedded Postgres** across the 6 shard tests. (Audit option B.)

### Explicitly do NOT
- **Do not enable parallel surefire forks.** It multiplies context builds and breaks the fixed-port GreenMail/Postgres singletons (§4).
- **Do not raise `-Xmx` further as a "fix."** It is runway; the cure is fewer live contexts (§2).
- **Do not auto-dismiss CI reds as flakes.** Confirm no code dependency (as done for Epic 575), then re-run — and file the flake so it gets fixed, not just retried.

---

## Appendix — evidence index

| Claim | Source |
|---|---|
| Epic 575 CI flake (expected 5 / got 13), unrelated test | CI run `27941814768` Backend log; re-run `27941814768/82722137304` green |
| Test self-documents accumulating ACTIVE projects | `automation/AutomationSchedulerScheduledTriggerIntegrationTest.java:286–306` |
| Heap OOM at CI default, `-Xmx3g` pin | `backend/pom.xml:295–301` |
| 60 s fork-kill after `System.exit(0)` | CI Backend log (`Surefire is going to kill self fork JVM`) + `pom.xml:302–303` |
| GreenMail `:13025` singleton, second embedded Postgres, no parallel forks | `backend/CLAUDE.md` (Testing) + `tasks/backend-test-suite-audit.md` §108, §162 |
| ~22 min, 5,651 tests, 39 contexts, 125 migrations + 12 seeders per provision | `tasks/backend-test-suite-audit.md` TL;DR, §105–112 |
| O(N) fan-out duplicate-key collision (latent real bug in the noise) | `tasks/backend-test-suite-audit.md` §168 |
| Local-green / CI-red, ±1 min run-to-run noise | `tasks/backend-test-suite-audit.md` §27; this session |
