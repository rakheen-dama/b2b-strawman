# FU-4 notes: client-ledger-statement keeps reversal reasons off the client's copy (register row 649B W1)

## Root cause
`ClientLedgerStatementQuery.buildRowsAndBalances` put `tx.getDescription()` into the row `description`. A REVERSAL row's description is the firm user's free-text reason. The report is offered to be handed to the client, so firm-internal text could reach a client.

## Change
One line group in `ClientLedgerStatementQuery`: `description` = `TrustTransactionTypeLabels.clientDescription(type, description, reference)` (same arguments as ClientTrustStatementContext, StatementOfAccountContextBuilder, TrustLedgerPortalSyncService). Columns, wording and template unchanged; no pack change, no reconcile.

## Output paths checked
All paths consume the rows from `executeAll` / `executeForExport`; the template and column definition only read `description` from them (TrustReportPackSeeder col "description"). Tested: execute rows, HTML (renderHtml, same path as preview), PDF (PDFBox text), CSV (writeCsv). The reversed payment row itself keeps its own description.

## Other reports
Other trust reports in `report/` (receipts-payments, client-trust-balances, trust-position, reconciliation, investment-register, interest-charges, unidentified-deposits, section-35 pack) print no per-transaction free-text description (no getDescription / "description" row key found). Nothing to flag.

## Tests
New `ClientLedgerStatementReversalReasonIntegrationTest` (2 tests: rows; CSV+HTML+PDF). Red on unmodified code: red-run.txt (2 failures). Green: green-run.txt. Tier 2: 23 classes, 211 testcases, 0 failures/errors/skips (report package, reporting package, ClientTrustStatementContextIntegrationTest, TrustTransactionTypeLabelsTest, LayerDependencyRulesTest, TestConventionsTest, TerminologyRemnantScanTest). Full verify left to orchestrator.
