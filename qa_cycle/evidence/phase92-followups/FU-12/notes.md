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

## Design: option (a), after commit, in `REQUIRES_NEW`

New `reporting/ReportAuditRecorder` (report paths only), method `recordAfterCommit(AuditEventRecord)`:

- **Inside a transaction:** registers a `TransactionSynchronization` whose `afterCommit` runs `AuditService.log`
  in a `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW` (writable). At `afterCommit` the committed
  transaction's EntityManager is still bound to the thread, so joining would write into a finished read-only
  transaction. `REQUIRES_NEW` suspends it and opens a fresh one, as the Spring javadoc requires.
- **No transaction open** (the CSV `finally` block): writes at once, in the same `REQUIRES_NEW` template.
  This is the same behaviour as before, now with the best-effort catch.
- **Best effort:** any `RuntimeException`, including one from the new transaction's commit or a pool timeout,
  is caught. It is logged at **ERROR** with the event type, report definition id, actor and details, so the
  report is not failed. This matters: Spring propagates an `afterCommit` exception to the committing caller, so
  without the catch the HTTP request would fail after the report had been produced. The nearest precedent,
  `McpSessionAuditListener`, also catches and does not fail the request, but logs at WARN; ERROR was chosen
  here as the brief asked, because a missing row for a trust-report export is an audit gap.

Why (a) over (b), an inline `REQUIRES_NEW` call: (a) never audits a report whose transaction rolled back. For
example, a PDF that rendered but whose transaction then failed is not recorded as exported. (b) would commit
the row before the outcome is known. Both cost the same second pooled connection, briefly. (a) needs no change
to the `AuditService` interface, whose contract still says "within the current transaction".

**Tenant and actor:** the `AuditEventRecord` is built by the caller on the request thread, so the actor, IP,
user agent and source are the caller's. The new transaction's connection comes from the multitenant connection
provider, which sets `search_path` from `RequestScopes.TENANT_ID`. That ScopedValue is still bound, because the
commit runs inside the request's scope. `actor_name` enrichment (`DatabaseAuditService.enrichActorName`) runs in
the new transaction against the tenant's `members` table. The integration test asserts
`actor_name = "RAR Owner"`, which proves the write ran in the right tenant schema.

## Audit sites covered (grep `REPORT_` in `src/main`: these are all of them)

| Site | Event | Before | After |
|---|---|---|---|
| `ReportExecutionService.execute` (via `POST /execute` → `ReportExportService.execute`) | `REPORT_GENERATED`, format `preview` | lost | after commit |
| `ReportExportService.exportAsPdf` | `REPORT_EXPORTED`, format `pdf` | lost | after commit |
| `ReportExportService.writeCsvAndAudit` (`finally`) | `REPORT_EXPORTED`, format `csv` | written | written at once (no transaction), now best effort |
| `ReportExportService.renderPreviewHtml` | none | none | none (never audited; not added, scope) |

`ReportExecutionService.execute` has one production caller, `ReportExportService.execute`, which is read-only.
Tests also call it directly, in its own writable transaction; there the row is written after that transaction
commits.

## Failure semantics

- The report query or render throws: no row (the audit is registered only after rendering).
- The report transaction rolls back after the audit was registered: no row (`afterCommit` never runs). This is
  tested by joining the export to a transaction that is set rollback-only.
- The audit write fails: the report is still returned, and an ERROR log is written.
- CSV: `finally` is kept, so a stream failure mid-write still records the export attempt. This is unchanged and
  is the conservative choice for an export audit trail. The one behaviour change is that an audit failure there
  no longer propagates; before, it could replace the stream's own exception or fail a fully streamed CSV.

## Tests

- `reporting/ReportAuditRowsIntegrationTest` (new; legal-za tenant; rows id-scoped by definition id and the ids
  the call added):
  - execute adds one `REPORT_GENERATED` row; PDF (timesheet) adds one `REPORT_EXPORTED` row; **Section 35 PDF**
    adds one; CSV adds one. Each check covers the actor id, `USER`, `actor_name` and format.
  - A refused PDF (missing `trust_account_id`) adds none.
  - An export in a rolled-back transaction adds none; the same export committed adds one.
- `reporting/ReportAuditRecorderTest` (new, unit, no Spring):
  - with no transaction, the row is written now in `REQUIRES_NEW` (writable);
  - inside a transaction, it is not written before commit and is written at `afterCommit`;
  - after a rollback it is never written;
  - an audit failure throws nothing and logs an ERROR, both directly and from `afterCommit`.
- `ReportingControllerTest.exportPdfPersistsReportExportedAuditEvent`: tightened to an id-scoped delta of PDF
  rows (`hasSize(1)`).
- Read-only REPEATABLE_READ guards, unchanged and green:
  - `ReportExecutionServiceTest.reportEntryPoints_readOneSnapshot_readOnlyRepeatableRead` (annotations on all
    four entry points);
  - `TrustSnapshotReadsIntegrationTest.clientLedgerStatementReport_placementApprovedBetweenTheReads_countsItOnce`
    and `rule5415Report_depositCommittedBetweenThePositionAndTheLines_listAgreesWithItself` (behaviour, with
    the audit row now written after commit in those report runs).

Evidence: `red-run.txt` (4 failures on unmodified code), `green-run.txt` (47/47: the FU-12 tests and the
FU-3 guards, final code), `tier2-run.txt` (every testcase).

## Tier 2 (2026-11-22, exit 0)

`./mvnw -q spotless:apply`, then one `./mvnw -q test` covering:

- the `reporting`, `report`, `audit` (including `audit.export`) and `verticals.legal.trustaccounting.report`
  packages;
- `TrustSnapshotReadsIntegrationTest` and `DashboardWithoutCapacityTest` (the other classes that use the
  changed code);
- `LayerDependencyRulesTest`, `TestConventionsTest` and `TerminologyRemnantScanTest`.

Result: **59 classes, 438 testcases, 0 failures, 0 errors, 0 skipped** (counted from the surefire XML
`<testcase>` elements). The full `./mvnw verify` was not run here; the orchestrator runs it.

## Architecture doc

`kazi-architecture/30-modules/reporting.md` §6 "Audit" is at the repo root, not under `backend/`, so editing it
does not trip the FU-26 merge-gate marker rule. Updated:

- removed the wrong line "recorded inside the same transaction as the read", along with stale line numbers;
- added the preview (no row);
- added a paragraph on `ReportAuditRecorder`, covering after-commit `REQUIRES_NEW`, the no-transaction CSV
  path and best effort with an ERROR log.

## Residual risk

- **Commit-to-audit gap.** If the JVM dies between the report commit and the audit commit, the report is served
  but not audited. This is inherent to after-commit (option b has the mirror-image gap); the ERROR log covers
  only in-process failures.
- **Extra connection.** Each audited report briefly holds a second pooled connection at commit. The outer
  session has not been released yet; `TrustSnapshotReads` already has the same pattern. In the test pool of 3,
  `TrustSnapshotReadsIntegrationTest` stays green.
- **Unknown future callers.** A future caller of `ReportExecutionService.execute` inside an unrelated writing
  transaction would now get the row only if that transaction commits. That is the intended semantics.
