# FU-12: report audit rows are written in their own transaction (Phase 92 F-650C-1)

Register row: `qa_cycle/evidence/phase92-exit/defect-register.md` F-650C-1 (MEDIUM, OPEN → FU-12).
Sources: `qa_cycle/evidence/phase92/650C/found.md` F-650C-1; `tasks/phase92-decisions.md` FU-12, FU-3 D-1.
Base: `main` @ 623c22cc92998f4b501642d94eb5313d2e39ddfa; merged with main at 751b9b20b.

## Root cause (reproduced)

- `DatabaseAuditService.log` is plain `@Transactional` (REQUIRED), so it joins its caller's transaction.
- Every report entry point in `ReportExportService` (`execute`, `renderPreviewHtml`, `exportAsPdf`,
  `exportAsCsv`) opens a `@Transactional(readOnly = true, isolation = REPEATABLE_READ)` transaction. Spring's
  JPA transaction manager sets Hibernate's flush mode to MANUAL for a read-only transaction.
- The `REPORT_GENERATED` row (`ReportExecutionService.execute`, reached through `ReportExportService.execute`)
  and the PDF `REPORT_EXPORTED` row (`exportAsPdf`) were saved into that transaction and never flushed. No
  error is raised. CSV worked: `writeCsvAndAudit` runs after `exportAsCsv` has returned, outside any transaction.
- Red run (`red-run.txt`), on unmodified production code: execute, timesheet PDF and **Section 35 pack PDF**
  each added 0 rows. The tightened `ReportingControllerTest.exportPdfPersistsReportExportedAuditEvent` failed
  the same way; its old `isNotEmpty()` check had passed only because of the CSV rows sibling tests write.

## Constraints

- **FU-3 D-1:** the four report entry points stay read-only REPEATABLE_READ transactions that start the
  transaction, because they give `client-ledger-statement` and Rule 54.15 their single snapshot. Their
  annotations are untouched. `AuditService.log`'s propagation is unchanged, so every other audit row still
  commits or rolls back with its business transaction.
- **ADR-T008** (`architecture/TenantScopeBindingTest`): no `ScopedValue.where` outside `multitenancy`. Ruling
  2026-11-22: do not amend it, and use no ScopedValue, ThreadLocal or TransactionSynchronization in the design.

## Design (final, review round 2): the entry points return the row; a non-transactional caller writes it

- **`AuditedReport<T>(T result, AuditEventRecord audit)`** is a small internal record, never an HTTP
  response. The transactional methods that used to log a report row now **build and return it** instead:
  - `ReportExecutionService.execute` returns `AuditedReport<ReportExecutionResponse>` (the `REPORT_GENERATED`
    row, format `preview`);
  - `ReportExportService.execute` returns `AuditedReport<ReportExecutionResponse>`;
  - `ReportExportService.exportAsPdf` returns `AuditedReport<PdfExportResult>` (the `REPORT_EXPORTED` row,
    format `pdf`).

  The HTTP response shapes (`ReportExecutionResponse`, the PDF bytes and filename) are unchanged.
- **`ReportRunService`** is not `@Transactional`; the controller's execute and PDF endpoints call it, one
  service method per endpoint. It calls the entry point **through the `ReportExportService` proxy**, never by
  self-invocation, so the entry point starts and ends its own read-only REPEATABLE_READ transaction. Once the
  call has **returned normally**, it hands the returned row to `ReportAuditRecorder.record`. A normal return
  means the proxy has committed and the session, with its connection, is closed. A rollback or a failed commit
  throws, so nothing is written.
- **`ReportAuditRecorder.record(row)`** writes in a new (`REQUIRES_NEW`) writable `TransactionTemplate`, so it
  never joins a caller's transaction. It is best effort: any `RuntimeException`, including a commit failure or
  a pool timeout, is caught and logged at **ERROR** with the event type, report id, actor, source, IP, user
  agent and details. The report is not failed, and the row can be reconstructed from the log. IP and user agent
  are already stored in audit rows. Precedent: `McpSessionAuditListener` also does not fail the request, but
  logs at WARN; ERROR was chosen because a missing row for a trust-report export is an audit gap.
- **CSV** is unchanged: `writeCsvAndAudit` runs after `exportAsCsv` has returned, with no transaction open, and
  calls `record` in its `finally`, so an export attempt whose stream failed mid-write is still audited.
- **Tenant and actor:** the row is built on the request thread inside the entry point, so the actor, IP and
  user agent are the caller's. The audit transaction's connection comes from the multitenant connection
  provider, which sets `search_path` from `RequestScopes.TENANT_ID`, still bound for the whole request.
  `actor_name` is resolved in that tenant; `ReportAuditRowsIntegrationTest` asserts `actor_name = "RAR Owner"`.

Why (a), write after the report transaction, over (b), write inline in `REQUIRES_NEW`: (a) never audits a
report whose transaction rolled back, and holds one connection at a time. (b) would write before the outcome is
known, and would hold two connections.

## History of the design

1. **PR (863e8155e):** `TransactionSynchronization.afterCommit` wrote the row in `REQUIRES_NEW`.
2. **Review round 1 (59684913e):** CodeRabbit's connection-budget finding was **measured and confirmed** (table
   below): at `afterCommit` the report connection is still checked out, so that write held two. Fixed by
   collecting rows at `afterCommit` into a `ScopedValue` and writing them in a non-transactional
   `ReportRunService` after the entry point returned.
3. **Review round 2 (this commit):** the full verify at 59684913e failed one test:
   `TenantScopeBindingTest.no_direct_scopedvalue_binding_outside_multitenancy_and_exempt`, because
   `ReportAuditRecorder.writeAfter` called `ScopedValue.where`. My Tier 2 had not included the `architecture`
   package. Per the ruling, the ScopedValue / `afterCommit` collection was replaced by the design above, which
   has the same semantics with simpler mechanics. `recordAfterCommit` and `writeAfter` are removed, and no
   production code in `reporting` registers a synchronization or binds a ScopedValue.

## Connection budget (review round 1; re-measured in round 2)

Measured by `reporting/ReportAuditConnectionBudgetIntegrationTest` on the real HTTP path, using
`HikariPoolMXBean.getActiveConnections()` on `appDataSource` (the tenant pool, which the primary shard reuses).
Scheduling is off in tests, so the pool was otherwise idle: idle = 0 in every run that recorded it.

The samples:

- `insideReportTransaction`: in a `renderPdf` spy;
- `afterCommitBeforeAuditWrite`: a test-only `afterCommit` probe, a framework fact rather than production code;
- `duringAuditWrite`: in an `AuditService.log` spy, after the real call, so the audit transaction's own
  connection has been acquired.

| Path | Code | idle | inside report tx | at `afterCommit` | during the audit write |
|---|---|---|---|---|---|
| PDF | 751b9b20b (`afterCommit` write) | 0 | 1 | **1** | **2** |
| execute | 751b9b20b | 0 | — | — | **2** |
| CSV | 751b9b20b | 0 | — | — | 1 |
| PDF | round 1 (59684913e) | 0 | 1 | 1 | 1 |
| execute / CSV | round 1 | 0 | — | — | 1 / 1 |
| PDF | **round 2 (final)** | 0 | 1 | 1 | **1** |
| execute / CSV | **round 2 (final)** | 0 | — | — | **1 / 1** |

Evidence:

- `connection-probe-before.txt`: 751b9b20b, 2 failures, actual 2;
- `connection-probe-after.txt`: round 1, 4/4;
- `connection-probe-round2.txt`: final code, 4/4.

At `afterCommit` the report transaction's JDBC connection is still checked out. Spring calls
`triggerAfterCommit` before `cleanupAfterCompletion` closes the EntityManager, and Hibernate's delayed
acquisition holds the connection until the session closes. The final design writes after the proxy has
returned, by which point that cleanup has run.

The strict asserts (`idle + 1` inside the report and during the write) were stable across all runs, so they are
kept. The evidence file write now calls `Files.createDirectories` first (reviewer note).

## Audit sites covered (grep `REPORT_` in `src/main`: these are all of them)

| Site | Event | Before FU-12 | Now |
|---|---|---|---|
| `ReportExecutionService.execute` (via `POST /execute` → `ReportRunService` → `ReportExportService.execute`) | `REPORT_GENERATED`, format `preview` | lost | returned by the entry point, written by `ReportRunService` after it returns |
| `ReportExportService.exportAsPdf` (via `ReportRunService`) | `REPORT_EXPORTED`, format `pdf` | lost | returned, written by `ReportRunService` after it returns |
| `ReportExportService.writeCsvAndAudit` (`finally`) | `REPORT_EXPORTED`, format `csv` | written | written at once (no transaction), best effort |
| `ReportExportService.renderPreviewHtml` | none | none | none (never audited; not added, scope) |

`ReportExecutionService.execute` has one production caller, `ReportExportService.execute`; no production code
calls either `execute` or `exportAsPdf` except `ReportRunService` (grep). Tests that call them directly receive
the row in the `AuditedReport` and assert it.

## Failure semantics

- The report query or render throws, or the transaction rolls back or fails to commit: the entry point
  throws, so nothing is written.
  - HTTP test: a `generateFilename` spy throws after the row was built
    (`ReportAuditConnectionBudgetIntegrationTest.pdfExportFailingAfterTheAuditWasBuilt_writesNoRow`).
  - Unit test: `runService_entryPointThrows_writesNothing`.
- The audit write fails: the report is still returned, and an ERROR log is written with the row's fields.
- CSV: `finally` is kept, so a failed stream still records the export attempt; an audit failure there no longer
  propagates.

## Tests

- **`reporting/ReportAuditRowsIntegrationTest`** (legal-za tenant; rows id-scoped by definition id and the ids
  the call added):
  - execute, timesheet PDF, **Section 35 PDF** and CSV each add exactly one row, with the right actor, `USER`,
    `actor_name` and format;
  - a refused PDF adds none, and the test asserts 400 plus the ProblemDetail `status`, `title` and `detail`
    (CodeRabbit round 1 minor);
  - `entryPointReturnsTheRowAndWritesNone_runServiceWritesItOnce`.
- **`reporting/ReportAuditConnectionBudgetIntegrationTest`** (4 tests): at most one connection during the
  audit write on PDF, execute and CSV; an HTTP PDF that fails after its row was built writes no row
  (ProblemDetail asserted); the report transaction is read-only.
- **`reporting/ReportAuditRecorderTest`** (unit, 6 tests):
  - `record` uses a new writable `REQUIRES_NEW` transaction;
  - an audit failure does not throw and logs an ERROR with source, IP and user agent;
  - `ReportRunService` writes the returned row after the entry point returns (PDF and execute);
  - `ReportRunService` writes nothing when the entry point throws, and still returns the report when the audit
    write fails.
- **`ReportExecutionServiceTest.executeReturnsReportGeneratedAuditRecord`** replaces the old row-lookup test:
  `execute` returns the row (type, entity, actor, slug, format). The other direct calls use `.result()`.
- **`ReportingControllerTest.exportPdfPersistsReportExportedAuditEvent`:** tightened to an id-scoped delta of
  PDF rows.
- **Guards, unchanged and green:**
  - `ReportExecutionServiceTest.reportEntryPoints_readOneSnapshot_readOnlyRepeatableRead`;
  - the `TrustSnapshotReadsIntegrationTest` report race tests;
  - the whole `architecture` package, including `TenantScopeBindingTest`.

Evidence:

- `red-run.txt`: 4 failures on unmodified code;
- `connection-probe-*.txt`;
- `green-run.txt`: the FU-12 tests and the guards, final code;
- `tier2-run.txt`: every testcase.

## Tier 2

Each run: `./mvnw -q spotless:apply`, then one `./mvnw -q test`. Counts are of the surefire XML `<testcase>`
elements.

- Initial PR (02:48Z): 59 classes, 438 testcases, all green.
- Round 1 (10:48Z): 61 classes, 448 testcases, all green. That run did **not** include the whole
  `architecture` package, and `TenantScopeBindingTest` failed in the orchestrator's full verify.
- **Round 2 (final, 11:34Z): 65 classes, 460 testcases, 0 failures, 0 errors, 0 skipped**, including all 7 classes of the `architecture` package (`TenantScopeBindingTest` among them). The run is in `tier2-run.txt`; `green-run.txt` has the 57/57 FU-12, guard and ADR-T008 tests.

The full `./mvnw verify` was not run here; the orchestrator runs it.

## Architecture doc

`kazi-architecture/30-modules/reporting.md` §6 "Audit" is at the repo root, not under `backend/`, so editing it
does not trip the FU-26 merge-gate marker rule. It now describes the final design:

- the entry points return the row, and `ReportRunService` writes it after they return;
- one connection at a time;
- the no-transaction CSV path;
- best effort with an ERROR log.

The wrong line "recorded inside the same transaction as the read" and the stale line numbers are removed.

## Residual risk

- **Commit-to-audit gap.** If the JVM dies between the report commit and the audit commit, the report is served
  but not audited. This is inherent to writing after the report transaction; the ERROR log covers only
  in-process failures.
- **Pool saturation.** An audit write waits up to `connection-timeout` for its single connection like any other
  request, and on timeout is dropped with an ERROR. Nothing reserves capacity for it.
- **Bypassing `ReportRunService`.** A future in-process caller of `ReportExportService.execute` / `exportAsPdf`
  that ignores the returned `AuditedReport.audit()` would write no row. The return type makes the row explicit
  at every call site; no ArchUnit rule enforces it.
- **`ReportRunService` must stay non-transactional.** With an annotation, the entry points would join its
  transaction (breaking FU-3 D-1), the row would be written before the outcome is known, and the write would
  hold two connections. Its javadoc says so, and `ReportAuditConnectionBudgetIntegrationTest` would fail.
