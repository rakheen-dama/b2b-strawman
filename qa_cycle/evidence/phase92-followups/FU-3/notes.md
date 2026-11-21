# FU-3: one snapshot for each document's trust reads (Phase 92 R-1)

Register row: `qa_cycle/evidence/phase92-exit/defect-register.md` "648F R-1". Sources: `648F/found.md` R-1,
`650A/found.md` F-6, `652D/found.md` F-652D-CR1, `tasks/phase92-decisions.md` R-1, "R-1 note", FU-3, 652D D-3.
Ruling: founder R-1, option (a). The trust reads run in a separate `REQUIRES_NEW`, read-only,
REPEATABLE_READ sub-transaction. The isolation of the writing outer transactions is unchanged.

## Root cause

The Statement of Account and the Client Trust Statement read their trust figures in several statements:

1. the candidate accounts;
2. with more than one candidate, each account's general and invested balance (652D account choice);
3. the general ledger statement (opening balance, then lines);
4. the section 86(4) invested statement.

All of those reads run inside the generate transaction:

- `StatementService.generate` and `GeneratedDocumentService.generateDocument` / `generateDocx` are `@Transactional`, at READ COMMITTED.
- That transaction writes the `GeneratedDocument` and `Document` rows.
- The inner `@Transactional(readOnly = true, isolation = REPEATABLE_READ)` methods join it, so their isolation is ignored.

Each statement therefore sees whatever had committed by the time it ran. Two consequences:

- **Placement approved between reads 3 and 4.** The R30 000 is still in the general closing balance and is also in the invested balance. The stored document prints "Held in trust R80 000" for a client who has R50 000.
- **Investment return committed between the two reads of step 2.** The account that holds all the client's money reads as holding nothing (general balance before the return, invested balance after it). The document then prints another account.

The ledger itself is right. The stored document is wrong.

## Sites: verified at main 5019e87b5

| # | Site | Where (current) | Status before FU-3 | Change |
|---|---|---|---|---|
| 1 | Statement of Account | `StatementOfAccountContextBuilder.buildTrustBlock` (was `:527-626`); reached from `StatementService.generate` `@Transactional` `:125` | **RED**: torn | Fixed. Everything after the visibility check (account choice, ledger statement, invested block) is now `readTrustBlock`, run in one `TrustSnapshotReads.read`. |
| 2 | Client Trust Statement | `ClientTrustStatementContext.buildFor` (was `:84-129`); reached via `CustomerContextBuilder:158` ← `PdfRenderingService.buildContext` / `generatePdf` / `previewWithValidation` ← `GeneratedDocumentService.generateDocument` `:245` / `generateDocx` `:715` / `previewDocument` `:502` | **RED**: torn | Fixed. Everything after the visibility check is now `trustBlock`, run in one `TrustSnapshotReads.read`. Every caller of `CustomerContextBuilder` gets this, including the read-only previews. |
| 3 | `client-ledger-statement` report | `ClientLedgerStatementQuery.buildRowsAndBalances` ← `ReportExecutionService.execute` / `executeForExport` ← `ReportExportService.execute` / `renderPreviewHtml` / `exportAsPdf` / `exportAsCsv` | **Already one snapshot.** 650C D-38 (PR #1809) put `@Transactional(readOnly = true, isolation = REPEATABLE_READ)` on all four `ReportExportService` entry points. That transaction is opened *there*, so the query and its audit call join a read-only RR transaction. | Not changed. Guard test added; it is green before and after the fix. |
| 4 | Rule 54.15 (`client-trust-balances`, 650A F-6) | `ClientTrustBalancesQuery.accountList` → `TrustMonthlyListService.liveList` (`integrityService.check`, then `buildLines`) | **Already one snapshot**, through the same 650C D-38 boundary | Not changed. Guard test added; it is green before and after the fix. |
| 5 | 652D account choice | `ClientTrustStatementContext.statementAccountId` / `heldOn` (general, then invested, per candidate) | **RED**: torn (CTS) | Fixed by 1 and 2. The choice runs inside each document's snapshot. |

`ReportExportService` is the only production entry to the report queries: `ReportingController` → `ReportExportService`, and `ReportRenderingService.renderPreviewHtml` is reached only from there. `liveList`'s other caller, `TrustMonthlyListController`, starts its own RR transaction. The 650A note and the 648F note predate 650C D-38. That is why the register row still lists sites 3 and 4.

Reproduce-before-fix: I did **not** change sites 3 and 4. The race test does not reproduce on them, and adding a `REQUIRES_NEW` inside a transaction that is already read-only RR would cost a second connection for nothing.

## Design

`verticals/legal/trustaccounting/TrustSnapshotReads` is a `@Component` wrapping one `TransactionTemplate`:

- settings: `PROPAGATION_REQUIRES_NEW`, `ISOLATION_REPEATABLE_READ`, `readOnly = true`;
- API: `<T> T read(Supplier<T>)`.

It follows the existing `PortalContactDirectoryService.tenantReadTx` pattern. There is no interface or adapter (YAGNI). Contract, stated in the Javadoc:

- one call per document, holding every trust figure on it and nothing else;
- return values and DTOs, never entities;
- no writes, no locks.

Each builder keeps its visibility check outside the snapshot. A member who may not see trust money therefore costs no second connection. What comes out of the snapshot is plain data:

- the SoA returns the private `TrustBlock` record (BigDecimal values, `TrustTxDto` lists, the invested map);
- the CTS returns a `Map` of values.

The `TrustAccount` entity the CTS loads stays inside the lambda; only its id and name are read.

Unchanged: figures, wording, templates, report columns, the isolation of any outer transaction. No migration.

## Tenant context on the new connection

`REQUIRES_NEW` suspends the caller's EntityManager and opens a new one. Its connection comes from `SchemaMultiTenantConnectionProvider.getConnection(tenantIdentifier)`, which runs `SET search_path TO <schema>`. The tenant identifier comes from `TenantIdentifierResolver.resolveCurrentTenantIdentifier()`, which reads `RequestScopes.TENANT_ID`. That ScopedValue is still bound on the same thread.

`snapshotRead_insideAWritingTransaction_ownTenantConnection_readOnlyRepeatableRead` proves this with `pg_backend_pid()`, `current_schema()`, `transaction_isolation` and `transaction_read_only`. It checks that, inside a writing caller transaction:

- the snapshot runs on a **different** backend;
- that backend's schema is the **tenant's schema**;
- the snapshot is `repeatable read` and read-only `on`;
- the caller's own connection afterwards is the same backend, still `read committed`, read-write.

## Connection pool

- **What a generation holds.** It now holds two pooled connections, but only while the trust reads run: the caller's C1 for the whole generation, and C2 for a handful of SELECTs. C2 is returned before rendering, the PDF conversion and the writes.
- **No database-level self-deadlock.** The classic `REQUIRES_NEW` hazard is the inner transaction waiting on a lock its own outer transaction holds. That cannot happen here:
  - the snapshot is read-only and takes no locks; every query in it is a plain SELECT (checked: no `@Lock` / `FOR UPDATE` / `FOR SHARE` on `findDistinctTrustAccountIdsByCustomerId`, `findForStatement`, `findByAccountTypeAndPrimaryTrue`, or the `TrustLedgerQueryRepository` balance queries);
  - PostgreSQL MVCC readers never wait on writers;
  - the outer generate transaction holds no trust-row locks anyway.
- **Pool-level starvation.** This needs every connection in the pool to be held by a generation that is waiting for its C2 at the same moment, i.e. pool-size concurrent document generations all inside the few-millisecond read window.
  - Pool sizes: prod `application-prod.yml` is **25** (backend/CLAUDE.md still says 10; dev is 10); tests are 3.
  - Even then it is not a permanent deadlock. Hikari's `connection-timeout` (10 s in prod) fails the waiting C2 checkout, that generation rolls back, and its C1 frees.
  - The founder accepted the second connection per generation. The codebase already nests `REQUIRES_NEW` inside request transactions (`NotificationService`, `PortalContactDirectoryService`).
- **In tests (pool 3).** The first green attempt showed what this looks like when the pool is too small.
  - The harness waited for the whole posting thread before letting the reader continue. A trust approval's after-commit listener (`PortalEmailNotificationChannel.dispatchToContacts`, `REQUIRES_NEW`) needs a second connection while the approval still holds its own. With the reader holding C1 + C2, the pool of 3 had none left.
  - Hikari timed out after 60 s (`total=3, active=3`). The listener logged the failure, which is the bounded failure mode described above, not a hang. Approvals already take two connections at peak today, independent of FU-3.
  - The harness now releases the reader at the posting's commit, via an `afterCommit` synchronization on the posting's own transaction, and joins the thread afterwards. Production code is unchanged by this.

## Tests

`backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/TrustSnapshotReadsIntegrationTest.java` is one class with one Spring context key: `@MockitoSpyBean` on `ClientLedgerService` and on `TrustIntegrityService`.

Each race test arms a one-shot hook on a trust reader. At the armed read, a concurrent posting runs on a separate platform thread, inside its own transaction, and commits; the read goes on after that commit. The assertions are invariants that hold on either side of the posting, so only a torn read fails them.

| Test | Path | Concurrent posting | Before | After |
|---|---|---|---|---|
| `statementOfAccount_placementApprovedBetweenTheReads_storedStatementCountsItOnce` | `StatementOfAccountContextBuilder.build` inside a writing transaction (`StatementService.generate`'s propagation) | placement approval, before the invested read | FAIL: general + invested = **80 000** | PASS |
| `clientTrustStatement_placementApprovedBetweenTheReads_storedDocumentCountsItOnce` | real `GeneratedDocumentService.generateDocument`; hook only at the read made from `generatePdf` (the stored PDF's context) | placement approval | FAIL: stored HTML "Held in trust: R80 000,00" | PASS |
| `clientTrustStatement_investmentReturnedDuringTheAccountChoice_choosesTheAccountHoldingIt` | `CustomerContextBuilder.buildContext` inside a writing transaction; two accounts, A (all invested) first by id | investment return, between A's general and invested balance reads | FAIL: chose B | PASS |
| `clientLedgerStatementReport_placementApprovedBetweenTheReads_countsItOnce` | `POST /api/report-definitions/client-ledger-statement/execute` | placement approval | PASS (650C D-38) | PASS (guard) |
| `rule5415Report_depositCommittedBetweenThePositionAndTheLines_listAgreesWithItself` | `POST /api/report-definitions/client-trust-balances/execute`; hook after `integrityService.check` | deposit | PASS (650C D-38) | PASS (guard) |
| `snapshotRead_insideAWritingTransaction_ownTenantConnection_readOnlyRepeatableRead` | the runner | none | (new class) | PASS |

Every race test also asserts that the posting actually ran between the reads (`assertFired`). The class ends with `assertFirmBalanced`.

`StatementOfAccountContextBuilderTest` (a Mockito unit test that constructs the builder) gets a pass-through `TrustSnapshotReads` mock.

Evidence:

- `red-run.txt`: unmodified production code with the final test; the 3 document tests fail, the 2 report guards and the runner proof pass.
- `green-run.txt`: with the fix; all 6 pass.
- `tier2-run.txt`: 131 test classes, 1 439 test cases, 0 failures / 0 errors / 0 skipped. Scope:
  - whole trees: `verticals/legal/statement`, `template`, `reporting`, `trustaccounting/ledger`, `/investment`, `/report`;
  - every test class outside those trees that references a changed class or its callers (`CustomerContextBuilder`, `GeneratedDocumentService`, `StatementService`, `PdfRenderingService`, `ClientLedgerService`);
  - the four ArchUnit guards.

  The full `./mvnw verify` is left to the orchestrator.

## Residual risk / left alone

- **Several contexts per generation (652D F-652D-O2; not this defect class).** `generateDocument` builds the customer context more than once: once for validation and the `containsTrustData` flag (`printsTrustData` → trust `status == available`), and again for the PDF in `generatePdf`. Each build is now one consistent snapshot, but they are separate snapshots.
  - Hazard: a client's *first* trust posting committing between the two builds could leave the stored PDF printing a trust block while `containsTrustData` is false (G89-F52 download gate).
  - Assessment: sub-second window, first-ever activity only, and pre-existing.
  - Not changed (one fix per PR). Candidate follow-up: compute `containsTrustData` from the PDF's own context.
- **Non-trust figures are not on the snapshot.** The SoA's fees, disbursements and fee-note summary still read at the outer READ COMMITTED transaction, so they can be a few milliseconds apart from the trust snapshot. This is outside the R-1 ruling (trust reads only) and has no cross-figure invariant with trust money.
- **The report guards depend on `ReportExportService` staying read-only RR.** 650C found.md F-650C-1 (the `REPORT_GENERATED` / `REPORT_EXPORTED` audit row is never flushed in that read-only transaction) is still open. A fix for it that made the report transaction writable or READ COMMITTED would reopen sites 3 and 4. The two guard tests would then fail.
- **Snapshot moment.** The snapshot is taken at the first trust query, not at the start of the request. A posting committed before that point is on the document; one committed after it is not. Both outcomes are consistent; the next generation shows the later state.
