# F-657A-2: `ReportAuditConnectionBudgetIntegrationTest` read 2 pooled connections instead of 1

## What was seen

Backend CI on PR #1847 @ `087f76abd`, run 45214014341, attempt 1:

- **Failure:** `execute_auditWriteHoldsAtMostOneConnection`, with `idle=0 samples={duringAuditWrite=2}` (expected `idle + 1`).
- **Re-run:** the CI re-run passed. The full verify on the same head passed locally.
- **Excerpt:** `ci-run-45214014341-attempt1-excerpt.txt`.

The test samples `HikariPoolMXBean.getActiveConnections()` on the tenant pool. It assumes the pool is otherwise idle in tests ("scheduling is off").

## Root cause: a test-environment defect, not a product defect

`template/LegacyContentImportRunner` is an `ApplicationRunner` (`@Order(200)`).

- **What it does at start:** `run()` starts a **virtual thread** on every application start.
- **What that thread does:** walks **every tenant schema** with `TenantScopedRunner.forEachTenant`. Each tenant gets a transaction that loads all document templates and clauses, so the thread holds one pooled connection per tenant.
- **Why tests were exposed:** scheduling (`kazi.scheduling.enabled`) and the job queue (`kazi.job-queue.enabled`) are switched off in `application-test.yml`. This runner is not a scheduled job, so it was never switched off.
- **What that meant in the suite:** every `@SpringBootTest` context start launched the sweep over the shared embedded database's hundreds of tenants. The sweep then kept running while the new context's test methods ran.

Timeline in the failing CI attempt:

| Time (UTC) | What |
|---|---|
| 16:35:28 | `ReportAuditConnectionBudgetIntegrationTest` starts its own context: its `@MockitoSpyBean`s give it a unique cache key. |
| 16:35:34–45 | Startup runners log on `main`; `LegacyContentImportRunner` (order 200) then starts its background sweep. |
| 16:35:47.24 | `execute_…` samples `duringAuditWrite = 2`: the request's connection plus the sweep's. |
| 16:35:47.76 | The sweep thread `virtual-1778` reaches `tenant_aaaaaaaaaaaa` and logs its known per-tenant error, so it was running inside the test's window. |

The same thread name pattern recurs throughout the CI log (`virtual-739` … `virtual-1778`). Its timing is irregular because it fires on context starts, not on a schedule.

## Reproduction, before the fix (deterministic)

**Test:** `F657a2ReproDiagnosticTest.java.txt` was a diagnostic test, run once and kept here, not in the suite. It:

- uses the same context and spies as the flaky test;
- adds 25 extra tenants so the sweep has work to do, as the full suite's tenants give it;
- runs 10 rounds of the same `execute` request and pool sample, each round twice:
  - once from a quiet pool;
  - once immediately after calling the **real** `LegacyContentImportRunner.run(...)`, the startup trigger.

| | idle → duringAuditWrite (10 rounds) |
|---|---|
| quiet | `0→1` ×10 |
| after the startup trigger | **`0→2` ×10**, the exact CI signature |

Source: `repro-before-fix.txt`.

## Fix

- **Runner:** `LegacyContentImportRunner.run()` is gated by `kazi.legacy-content-import.enabled`.
  - It defaults to `true`, so prod, dev and local are unchanged.
  - `application-test.yml` sets it to `false`, beside `scheduling` and `job-queue`.
- **Its own tests:** `LegacyContentImportRunnerTest` already calls `runImport()` directly, so the import itself keeps its coverage.
- **New guard test:** `LegacyContentImportRunnerStartupGateTest` (unit test, Mockito) checks both settings:
  - disabled: the trigger sweeps no tenant;
  - enabled: the trigger still sweeps in the background.
- **Flaky test's Javadoc:** `ReportAuditConnectionBudgetIntegrationTest` now names all three switches its "pool is otherwise idle" premise relies on.

The assertion in `ReportAuditConnectionBudgetIntegrationTest` is **unchanged**. Its global-pool measurement is valid once the premise holds. Loosening the assertion would have hidden a real background job.

## After the fix

| | idle → duringAuditWrite (10 rounds) |
|---|---|
| quiet | `0→1` ×10 |
| after the startup trigger (now a no-op in the test profile) | `0→1` ×10 |

Source: `repro-after-fix.txt`.

**Targeted run:** `LegacyContentImportRunnerStartupGateTest` 2/2, `LegacyContentImportRunnerTest` 3/3, `ReportAuditConnectionBudgetIntegrationTest` 4/4, diagnostic 1/1.

## Gates

- **BE-VERIFY:** `bash scripts/verify.sh -q` on `e7d2dbb1b` (the code commit), 20:03–20:32 SAST, exit 0.
  - **7,897 `<testcase>`, 0 failures, 0 errors**, counted from the surefire XML.
- **Sweep gone from the suite log:**
  - per-tenant errors logged by the background sweep thread: **60** in CI attempt 1 on `087f76abd` (main), **0** in this verify;
  - the only virtual-thread log line in this verify is `JobWorker` running a deliberate `failing_test_job` inside a job-queue test.
- **Frontend:** unchanged, so no frontend gate is required.
