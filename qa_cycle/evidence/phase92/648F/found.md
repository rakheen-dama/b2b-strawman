# 648F — found (out of scope; reported, not fixed)

- **648D F-1 / 648A N-2 — carried forward unchanged.** Firm-surface wording "section 86(3) investment
  **account**" in `ActivityMessageFormatter.trustTypeNoun`, `AuditEventTypeRegistry trust_firm_inv_place.*`
  and `TrustTransactionTypeLabels.placementNoun` (`FIRM_INV_PLACE` → "placement in a section 86(3) investment
  account"). Not touched by 648F: neither client document calls `placementNoun` (callers:
  `ActivityMessageFormatter`, `TrustNotificationHandler` only), and a section 86(3) placement is
  `INTER_ACCOUNT` with no client, so it never reaches the Statement of Account or the Client Trust Statement.
  Needs its own slice (firm surfaces).
- **648E C-8 — partially closed here.** The two client documents now read an *as-at* invested balance
  (`TrustLedgerQueryRepository.clientInvestedBalance`). The firm client-ledger card's `investedBalance`
  (`investedBalancesByClient`, 648E) is still undated — unchanged by design (out of scope, §18).

## Review round (PR #1803) — residuals recorded, not fixed here

- **R-1 — cross-ledger snapshot on the client documents and the report (review F1, CodeRabbit Major).**
  `ClientLedgerService.getClientInvestedStatement` now carries `@Transactional(readOnly = true, isolation =
  REPEATABLE_READ)` (648E convention, `getClientLedger` / `listClientLedgers`). That isolation applies only
  where the call *starts* the transaction; Spring joins an existing caller transaction and ignores the inner
  isolation, and Postgres cannot raise a transaction's isolation after its first query. On every production
  path an outer transaction already exists, and it writes:
  - Statement of Account: `StatementService.generate` `@Transactional` (`verticals/legal/statement/StatementService.java:125`)
    → `StatementOfAccountContextBuilder.build` (readOnly, joins, `:163`) → `buildTrustBlock` reads the general
    statement (`:582`) then the invested statement (`:615`); the outer transaction saves the
    `GeneratedDocument` and `Document` rows (`StatementService.java:178`, `:195`).
  - Client Trust Statement: `GeneratedDocumentService.generateDocument` `@Transactional`
    (`template/GeneratedDocumentService.java:245`; also `generateDocx` `:715`) → `PdfRenderingService.buildContext`
    / `generatePdf` (readOnly, joins, `template/PdfRenderingService.java:241`, `:120`) → `CustomerContextBuilder:158`
    → `ClientTrustStatementContext.buildFor` reads general (`:92`) then invested (`:121`); the outer transaction
    saves the generated document. Read-only previews (`GeneratedDocumentService.previewDocument` `:502`) are the
    only read-only CTS boundary — generic for every template, not the stored PDF.
  - `client-ledger-statement` report: `ReportExportService.execute` `@Transactional(readOnly = true)`
    (`reporting/ReportExportService.java:93`) → `ReportExecutionService.execute` `@Transactional` (`:46`), which
    writes the `REPORT_GENERATED` audit row (`:62`) in the same transaction → `ClientLedgerStatementQuery`
    reads the general rows/balances then the invested statement. Exports (`exportAsPdf` `:106`,
    `exportAsCsv` `:141`) are read-only but generic for all reports.
  So a single read-only REPEATABLE_READ service call for general + invested would *also* join the writing
  outer transaction and read on its READ COMMITTED snapshot — restructuring buys nothing; and the brief forbids
  changing the isolation of a writing transaction. **Residual:** a placement approved between the general and
  the invested read of one document can make `trust.invested.total_held` count it twice (or a concurrent
  interest posting can land in the period totals but not the closing balance). Same class as the pre-existing
  two-statement reads in `getClientLedgerStatement` (opening + lines, `ClientLedgerService.java:350`).
  **Options (orchestrator/founder call):** (a) a `REQUIRES_NEW`, read-only, REPEATABLE_READ sub-transaction for
  the trust reads of a document (second pooled connection per generation); (b) REPEATABLE_READ on the generate
  boundaries themselves (write transactions — serialization-failure retry policy needed); (c) accept (a
  sub-second window on a firm-initiated generation; the next generation is right).

- **R-2 — `client-ledger-statement` report does not require `VIEW_TRUST` (CodeRabbit Major, pre-existing).**
  Confirmed: every report endpoint checks only `@RequiresCapability("FINANCIAL_VISIBILITY")`
  (`reporting/ReportingController.java:32,38,45,53,62,73` — execute `:44`, preview `:52`, export pdf `:61`,
  export csv `:72`); `ReportExecutionService` / `ReportExportService` / `ClientLedgerStatementQuery` make no
  capability check; `VIEW_TRUST` is a separate capability (`orgrole/Capability.java:19`, "View trust accounts,
  transactions, and reports") and the client-ledger endpoints require it (`verticals/legal/trustaccounting/ledger/ClientLedgerController.java:29,36,43,61`;
  documents use `TrustDataVisibility.visibleToCurrentMember`, `TrustDataVisibility.java:37`). The query read
  trust rows and balances before 648F (`ClientLedgerStatementQuery` at 93cfed739 `:66`, `:70`).
  **Impact:** a custom role holding `FINANCIAL_VISIBILITY` without `VIEW_TRUST` can read a client's trust rows,
  opening/closing balances and — since 648F — the section 86(4) invested block through this report (and the
  other trust-reports-pack queries on the same controller). **Not changed here** (access control is out of
  scope; one fix per PR). **Proposed owner:** a scoped access-control slice — orchestrator/founder decision
  between a 403 for trust reports without `VIEW_TRUST` and a response that withholds the trust figures.
