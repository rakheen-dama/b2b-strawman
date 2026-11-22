# FU-12: report audit rows are written in their own transaction (Phase 92 F-650C-1)

Register row: `qa_cycle/evidence/phase92-exit/defect-register.md` F-650C-1 (MEDIUM, OPEN → FU-12).
Sources: `qa_cycle/evidence/phase92/650C/found.md` F-650C-1; `tasks/phase92-decisions.md` FU-12, FU-3 D-1.
Base: `main` @ 623c22cc92998f4b501642d94eb5313d2e39ddfa.

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

## Constraint (FU-3 D-1)

The report transaction must stay a read-only REPEATABLE_READ transaction that the entry point starts, because it
gives `client-ledger-statement` and Rule 54.15 their single snapshot. `AuditService.log`'s propagation must not
change globally. Neither changed: the `@Transactional` annotations on `ReportExportService` are untouched, and
so is `DatabaseAuditService`.

## Design: option (a), after commit; since review round 1, written once the report transaction has completed

The rows are written in their own `REQUIRES_NEW` writable transaction, and only for a report transaction that
committed. Two parts (`reporting/ReportAuditRecorder` and `reporting/ReportRunService`):

- **Collect at commit.** `ReportAuditRecorder.recordAfterCommit(record)` is called at the audit sites, inside the
  report transaction. It registers a `TransactionSynchronization` whose `afterCommit` **adds the row to a
  collection** owned by the enclosing `writeAfter` call (a `ScopedValue`, the project convention; no
  `ThreadLocal`). A report whose transaction rolls back is never collected.
- **Write after completion.** `ReportRunService` is not `@Transactional`; the controller's execute and PDF
  endpoints call it, one service method per endpoint. It calls `ReportAuditRecorder.writeAfter(() ->
  reportExportService.execute(...) / exportAsPdf(...))`. The call goes **through the `ReportExportService`
  proxy**, not by self-invocation, so the entry point still starts its own read-only REPEATABLE_READ
  transaction. `writeAfter` writes the collected rows only after that call has returned, by which point the
  report transaction has completed, its session is closed and its connection is back in the pool.
- **CSV:** `writeCsvAndAudit` runs after `exportAsCsv` has returned, with no transaction open, so it writes at
  once. It needs no wrapper.
- **Direct callers** (tests, or a future in-process caller not wrapped in `writeAfter`) still get their row,
  written at `afterCommit` in `REQUIRES_NEW`. That is correct, but it briefly holds a second connection, so the
  production endpoints go through `ReportRunService`. Inside a caller's own transaction, `writeAfter` only runs
  the call, and the row is written at that transaction's commit; the rollback test uses this path.
- **Best effort:** any `RuntimeException`, including one from the new transaction's commit or a pool timeout,
  is caught. It is logged at **ERROR** with the event type, report definition id, actor, source, IP, user agent
  and details, so the report is not failed and the row can be reconstructed. IP and user agent are already
  stored in audit rows. This matters at `afterCommit` for direct callers, because Spring propagates an
  `afterCommit` exception to the committing caller. The nearest precedent, `McpSessionAuditListener`, also
  catches and does not fail the request, but logs at WARN; ERROR was chosen here because a missing row for a
  trust-report export is an audit gap.

Why (a) over (b), an inline `REQUIRES_NEW` call: (a) never audits a report whose transaction rolled back. For
example, a PDF that rendered but whose transaction then failed is not recorded as exported. (b) would commit
the row before the outcome is known, and it also holds two connections. (a) needs no change to the
`AuditService` interface, whose contract still says "within the current transaction".

**Tenant and actor:** the `AuditEventRecord` is built by the caller on the request thread, so the actor, IP,
user agent and source are the caller's. The new transaction's connection comes from the multitenant connection
provider, which sets `search_path` from `RequestScopes.TENANT_ID`. That ScopedValue is still bound, because
`writeAfter` runs inside the request's scope. `actor_name` enrichment (`DatabaseAuditService.enrichActorName`)
runs in the new transaction against the tenant's `members` table. The integration test asserts
`actor_name = "RAR Owner"`, which proves the write ran in the right tenant schema.

## Review round 1, finding 1: connection budget (measured, then fixed)

CodeRabbit (Major) and the independent reviewer raised the same question: does the `REQUIRES_NEW` write from
`afterCommit` need a second connection while the report transaction's connection is still checked out?

**Measured** with `reporting/ReportAuditConnectionBudgetIntegrationTest`, on the real HTTP path, using
`HikariPoolMXBean.getActiveConnections()` on `appDataSource`. That is the tenant pool: the primary shard reuses
it. Scheduling is off in tests, so the pool was otherwise idle (idle = 0 for every test). The samples:

- `insideReportTransaction`: in a `renderPdf` spy, after the real call;
- `afterCommitBeforeAuditWrite`: an `afterCommit` callback registered by the render, ahead of the audit's, so
  it runs before the audit write;
- `duringAuditWrite`: in an `AuditService.log` spy, after the real call, so the audit transaction's own
  connection is already acquired by its member lookup.

| Path | Code | idle | inside report tx | at `afterCommit`, before the write | during the audit write |
|---|---|---|---|---|---|
| PDF | before (751b9b20b) | 0 | 1 | **1** | **2** |
| execute | before | 0 | — | — | **2** |
| CSV | before | 0 | — | — | 1 |
| PDF | after the fix | 0 | 1 | 1 | **1** |
| execute | after | 0 | — | — | **1** |
| CSV | after | 0 | — | — | **1** |

Evidence: `connection-probe-before.txt` (2 failures, actual 2) and `connection-probe-after.txt` (4/4).

**The fact:** at `afterCommit` the report transaction's JDBC connection is **still checked out**. Spring calls
`triggerAfterCommit` before `cleanupAfterCompletion`, which closes the EntityManager. Spring Boot's Hibernate
connection handling (delayed acquisition, held until the session closes) keeps the connection until then. The
read-only + REPEATABLE_READ preparation in `HibernateJpaDialect.beginTransaction` acquires it at begin. So the
old design held two connections per execute or PDF report for the length of the audit write. Under concurrent
reports this could exhaust the pool: each audit write waits up to `connection-timeout`, then the row is dropped.

**Fixed** with the coordinator's preferred design, described above: the write happens after the
`@Transactional` entry-point method has returned, in `ReportRunService` (one small non-transactional
collaborator). Nothing else changed:

- semantics: written only if the report transaction commits, never on rollback, best effort with an ERROR log;
- the four entry points (still `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`, still the
  transaction starters);
- the FU-3 guard `ReportExecutionServiceTest.reportEntryPoints_readOneSnapshot_readOnlyRepeatableRead`, which
  needed no edit;
- `AuditService.log`.

The `afterCommit` sample is still 1 after the fix. That is the framework fact; the fix no longer writes there.

`ReportAuditConnectionBudgetIntegrationTest` now guards the fix:

- the audit write holds at most one connection on the PDF, execute and CSV paths;
- a PDF whose transaction rolls back after its audit was registered writes no row (a `generateFilename` spy
  throws; the test checks the 400 ProblemDetail);
- the report transaction is read-only.

## Review round 1, finding 2

`ReportAuditRowsIntegrationTest.refusedPdfExport_writesNoRow` now asserts 400 and the ProblemDetail `status`
(400), `title` ("Missing required parameter") and `detail` ("Parameter 'trust_account_id' is required"),
instead of any 4xx. That was the only error-path assertion in the new tests that had the gap; the new HTTP
rollback test asserts its ProblemDetail the same way.

## Audit sites covered (grep `REPORT_` in `src/main`: these are all of them)

| Site | Event | Before | After |
|---|---|---|---|
| `ReportExecutionService.execute` (via `POST /execute` → `ReportExportService.execute`) | `REPORT_GENERATED`, format `preview` | lost | collected at commit, written by `ReportRunService` after the transaction completes |
| `ReportExportService.exportAsPdf` | `REPORT_EXPORTED`, format `pdf` | lost | collected at commit, written by `ReportRunService` after the transaction completes |
| `ReportExportService.writeCsvAndAudit` (`finally`) | `REPORT_EXPORTED`, format `csv` | written | written at once (no transaction), now best effort |
| `ReportExportService.renderPreviewHtml` | none | none | none (never audited; not added, scope) |

`ReportExecutionService.execute` has one production caller, `ReportExportService.execute`, which is read-only.
Tests also call it directly, in its own writable transaction; there the row is written at that transaction's
commit, the direct-caller path.

## Failure semantics

- The report query or render throws: no row (the audit is registered only after rendering).
- The report transaction rolls back after the audit was registered: no row, because `afterCommit` never runs and
  nothing is collected. This is tested on the HTTP path (a `generateFilename` spy throws), in the unit test
  (`writeAfter_rolledBackReport_writesNothing`), and on the direct-caller path (an export joined to a
  rollback-only transaction).
- The audit write fails: the report is still returned, and an ERROR log is written.
- CSV: `finally` is kept, so a stream failure mid-write still records the export attempt. This is unchanged and
  is the conservative choice for an export audit trail. The one behaviour change is that an audit failure there
  no longer propagates; before, it could replace the stream's own exception or fail a fully streamed CSV.

## Tests

- `reporting/ReportAuditRowsIntegrationTest` (new; legal-za tenant; rows id-scoped by definition id and the ids
  the call added):
  - execute adds one `REPORT_GENERATED` row; PDF (timesheet) adds one `REPORT_EXPORTED` row; **Section 35 PDF**
    adds one; CSV adds one. Each check covers the actor id, `USER`, `actor_name` and format.
  - A refused PDF (missing `trust_account_id`) adds none; the test asserts the 400 ProblemDetail.
  - An export in a rolled-back transaction adds none; the same export committed adds one.
- `reporting/ReportAuditRecorderTest` (new, unit, no Spring, 9 tests):
  - with no transaction, the row is written now in `REQUIRES_NEW` (writable);
  - direct callers: not written before commit, written at `afterCommit`, never written after a rollback;
  - `writeAfter`: a committed row is written only after the report call returns, and not at `afterCommit`;
  - `writeAfter` after a rollback writes nothing; inside a caller's transaction, the row is written at that
    transaction's commit;
  - an audit failure throws nothing and logs an ERROR with source, IP and user agent, directly, from
    `afterCommit`, and from `writeAfter`.
- `reporting/ReportAuditConnectionBudgetIntegrationTest` (new in round 1, 4 tests; see the round 1 section).
- `ReportingControllerTest.exportPdfPersistsReportExportedAuditEvent`: tightened to an id-scoped delta of PDF
  rows (`hasSize(1)`).
- Read-only REPEATABLE_READ guards, unchanged and green:
  - `ReportExecutionServiceTest.reportEntryPoints_readOneSnapshot_readOnlyRepeatableRead` (annotations on all
    four entry points);
  - `TrustSnapshotReadsIntegrationTest.clientLedgerStatementReport_placementApprovedBetweenTheReads_countsItOnce`
    and `rule5415Report_depositCommittedBetweenThePositionAndTheLines_listAgreesWithItself` (behaviour, with
    the audit row now written after commit in those report runs).

Evidence:

- `red-run.txt`: 4 failures on unmodified code;
- `connection-probe-before.txt` and `connection-probe-after.txt`: round 1;
- `green-run.txt`: 55/55, the FU-12 tests, the budget guard and the FU-3 guards, round-1 code;
- `tier2-run.txt`: every testcase.

## Tier 2

Each run: `./mvnw -q spotless:apply`, then one `./mvnw -q test` covering:

- the `reporting`, `report`, `audit` (including `audit.export`) and `verticals.legal.trustaccounting.report`
  packages;
- `TrustSnapshotReadsIntegrationTest` and `DashboardWithoutCapacityTest` (the other classes that use the
  changed code);
- `LayerDependencyRulesTest`, `TestConventionsTest` and `TerminologyRemnantScanTest`.

Counts are of the surefire XML `<testcase>` elements.

- Initial PR (2026-11-22T02:48Z): 59 classes, 438 testcases, 0 failures, 0 errors, 0 skipped.
- **Review round 1** (2026-11-22T10:48Z, round-1 code on 751b9b20b): **61 classes, 448 testcases, 0 failures,
  0 errors, 0 skipped** (`tier2-run.txt`). `green-run.txt`: 55/55 FU-12 and guard tests.

The full `./mvnw verify` was not run here; the orchestrator runs it (it passed at 751b9b20b, before round 1).

## Architecture doc

`kazi-architecture/30-modules/reporting.md` §6 "Audit" is at the repo root, not under `backend/`, so editing it
does not trip the FU-26 merge-gate marker rule. Updated:

- removed the wrong line "recorded inside the same transaction as the read", along with stale line numbers;
- added the preview (no row);
- added a paragraph on `ReportAuditRecorder` / `ReportRunService`: collected at commit and written once the
  report transaction has completed (one connection at a time), the no-transaction CSV path, and best effort
  with an ERROR log.

## Residual risk

- **Commit-to-audit gap.** If the JVM dies between the report commit and the audit commit, the report is served
  but not audited. This is inherent to after-commit (option b has the mirror-image gap); the ERROR log covers
  only in-process failures.
- **Connection budget.** Execute and PDF now hold one connection at a time; the old two-at-once pattern
  remains only on the direct-caller path, which no production endpoint uses. Under pool saturation an audit
  write still waits up to `connection-timeout` for its single connection and, on timeout, is dropped with an
  ERROR. Nothing reserves capacity for it, as for any other request.
- **Unknown future callers.** A future in-process caller of `ReportExportService.execute` / `exportAsPdf` that
  does not go through `ReportRunService` gets correct rows but the two-connection pattern. The
  `ReportAuditRecorder` and `ReportRunService` javadocs say so; no ArchUnit rule enforces it.
- **`ReportRunService` must stay non-transactional.** An annotation there would make the entry points join it
  and break FU-3 D-1. Its javadoc says so, and `ReportAuditConnectionBudgetIntegrationTest` would fail (two
  connections).
