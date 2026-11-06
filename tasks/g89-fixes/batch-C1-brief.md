# Batch C1 brief: statements and reports (G89-F16 MEDIUM, G89-F18 MEDIUM, G89-F19, G89-F21, 634B)

**Branch:** `fix/g89-c1-statements-reports`, cut from a fresh `origin/main` after Batch D has merged.
**PR title:** `fix(statements,reports): fee-note-basis SoA closing balance, Section 35 pack content, closure-letter date, picker noun (G89-F16/F18/F19/F21, 634B)`
**Authorisation:** founder D1/D2/D9 (2026-11-06), recorded in `TRACKER.md`.

**Gates:**
- backend full verify (orchestrator) and the frontend gate
- two bound reviews and CodeRabbit
- browser checks on the Keycloak stack, each observing **content**, not just that a card or button exists (lesson G88-F40 → G89-F18):
  - S2-14 closure SoA: the PDF shows closing R 0,00 on a paid matter with a written-off disbursement
  - S2-25 Section 35: cells hold values and the PDF has rows
  - the closure letter date reads "6 November 2026"

`BE` = `backend/src/main/java/io/b2mash/b2b/b2bstrawman`, `RES` = `backend/src/main/resources`. All lines below were verified at `30317a851`; re-check them after A, B and D merge.

## G89-F16 (MEDIUM): the closure SoA counts a written-off disbursement, and its closing balance mixes bases

**Cause.** `BE/verticals/legal/statement/StatementOfAccountContextBuilder` builds the closing balance from three sources:
- fees from all billable time entries, billed or not (`loadFeeLines:410-444`, VAT recomputed at `:460-479`);
- disbursements from `DisbursementRepository.findForStatement:96-103`, which has no filter, so DRAFT, REJECTED and WRITTEN_OFF rows are all included;
- previous balance and payments from fee notes (`:572-644`).

The closure letter instead uses `BE/verticals/legal/closure/MatterClosureContextBuilder.billedTotals:145-175`, which takes SENT, PARTIALLY_PAID and PAID notes.

**Decision D9: fee-note basis.**
- Extract the line-splitting loop in `billedTotals` into a shared component, e.g. `BE/invoice/MatterFeeNoteTotals` (place it where invoice code lives). For a matter and an optional date range, it returns fees excl., VAT, disbursements, total, paid and outstanding.
- Notes counted: status SENT, PARTIALLY_PAID or PAID, with `issueDate ≤ periodEnd`. Use the same status set as `MatterClosureContextBuilder.BILLED_INVOICE_STATUSES:55-56`.
- SoA `summary.closing_balance_owing` = Σ note.total − Σ COMPLETED payments up to `periodEnd`. For "now" this equals Σ `Invoice.getBalanceDue()`.
- `summary.total_fees` and `summary.total_disbursements` come from the fee and disbursement lines (incl. VAT) of notes issued in the period. Then previous + fees + disbursements − payments = closing still adds up on the page.
- Keep the time-entry and disbursement tables as activity listings.
- Add an "Unbilled work in progress" summary line, shown but **not** counted in the closing balance: unbilled billable time plus disbursements that are APPROVED and UNBILLED. Adding a summary row is a template change, so it needs the legal-za pack bump. **That bump belongs to C2.** To avoid two bumps, either:
  - put the context key in C1 and the template row in C2's v13; or
  - if the template must change in C1, do the v12→v13 bump here and C2 takes v14.
  Choose one and record it as a judgment call; prefer the first.
- Narrow `findForStatement` to `approvalStatus = 'APPROVED' AND billingStatus <> 'WRITTEN_OFF'`.
- `MatterClosureContextBuilder` uses the shared helper too. If the letter's template has room, it can expose `matter.balance_owing` as a context key; leave the letter template unchanged in C1.

**Tests.**
- Reproduce first (red on main): a new `StatementOfAccountClosingBalanceIntegrationTest` with:
  - `writtenOffDisbursement_notListed_notOwing`
  - `paidFeeNote_closingBalanceZero`
  - `tariffLineOnFeeNote_counted`
  - `unbilledWip_shownNotOwing`
  - `closureLetterAndSoa_agreeOnBilledTotals`
- This is the golden scenario the register asked for: the 636B matter shape, with a paid note, a written-off R500 disbursement and a closure → closing R 0,00.
- Expect these to change (update them deliberately and list them in the PR body):
  - `StatementOfAccountContextBuilderTest` closing-balance assertions (around `:204`, `:249`, `:453`, `:486`, `:521`) and the fee/VAT tests (`:879-1013`), which are mock-based
  - `StatementControllerIntegrationTest:187,227`
  - `LegalDisbursementRepositoryTest:273-368` (it seeds DRAFT disbursements; switch them to APPROVED, or assert the filter)
  - `MatterClosureContextBuilderTest`
  - `MatterClosureServiceIntegrationTest`

## 634B: the Outstanding tile links to a Statements tab showing R 0,00

**Cause.**
- The tile (`frontend/components/projects/kpi-dashboard.tsx:231-238`) shows a live figure from `CollectionsReadService.outstandingSummary:270-293`.
- The tab (`frontend/components/legal/project-statements-tab.tsx:117-151`) shows the snapshot `summary.closingBalanceOwing` (`StatementService.list:279-306`, `extractSnapshotSummary:492-499`).
- `list` filters to the base `statement-of-account` template id only (`:286-299`). That includes **generic-menu SoAs**, whose snapshot has no `summary` key, so `zeroSummary()` prints R 0,00. It excludes `statement-of-account-*` clones that `resolveTemplate:363-391` accepts.

**Fix.**
- In `list`, include the base slug plus `statement-of-account-*` templates.
- Require `contextSnapshot` to contain `summary`, which only `StatementService.generate:150-155` writes.
- (C2 hides the SoA from the generic menu.)

**Test.** `StatementListIntegrationTest`:
- `genericMenuSoa_withoutSummary_excluded`
- `clonedTemplateStatement_included`

## G89-F18 (MEDIUM): the Section 35 data pack renders no data

This re-opens G88-F40.

**Cause.**
- `BE/verticals/legal/trustaccounting/report/Section35DataPackQuery.addSection:208-230` copies raw sub-report rows and adds `_section`.
- The pack's column definitions (`TrustReportPackSeeder:724-730`) are `_section`, `label` and `amount`. Only the reconciliation rows carry `label` and `amount`, so every other cell reads "— —".
- The summary holds `sections` (a list of maps) and a raw BigDecimal year-end total.
- `frontend/components/reports/report-results.tsx:73-90` prints the summary with `String(value)`, which produces "[object Object]".
- `formatCellValue` (`:28-44`) uses `toFixed(2)`.
- CSV (`ReportRenderingService.writeCsv:127-158`) uses the same broken columns.
- The PDF template `SECTION_35_TEMPLATE` (`TrustReportPackSeeder:983-1013`) prints only section names and row counts.

**Fix: flatten in the backend.**
- In `addSection`, set `label` and `amount` on every row using a per-section projector. **Keep the raw keys**: `InvestmentReportBasisIntegrationTest:355-364` asserts `applicableLpffRate`, and `TrustReportQueryIntegrationTest:482-510` plus `InvestmentReportBasisIntegrationTest:335,403` assert `summary.sections`. Projections:
  - receipts & payments: label = `date · reference · clientName (type)`, amount = credit − debit
  - client balances: `clientName` → `balance`
  - investments: `clientName · institution accountNumber` → `principal`
  - interest allocation: `clientName` → `clientShare`
  - reconciliation: already in this shape
  - plus one "Total" row per section
- PDF: in `SECTION_35_TEMPLATE`, iterate the rows for each section (`th:each="row : ${rows}" th:if="${row['_section'] == section['sectionName']}"`) and print label and amount, with money formatted the way the other trust report templates do it.
- Bump `TrustReportPackSeeder.PACK_VERSION` 1→2 with `RECONCILE` entries for `section-35-data-pack` `templateBody` (sha256 from → to), and for `columnDefinitions` if they change. Follow the precedent of `StandardReportPackSeeder` v2 (`:35`, `:51-110`, `entry(...)` `:307`) and `StandardReportPackSeederReconcileTest`. Extend `TrustReportPackSeederIntegrationTest`.
- Frontend `report-results.tsx`:
  - skip summary entries whose value is an object or array
  - format money summary values and `currency` cells with `formatCurrency` from `@/lib/format`
  - update `frontend/__tests__/report-results.test.tsx`

**Tests.**
- Reproduce first: `Section35DataPackContentIntegrationTest`.
  - `everyRow_hasLabelAndAmount`
  - `pdf_containsRowLabels_notOnlyCounts`: render, then extract the text, or assert on the template output HTML
  - `csv_hasNoBlankAmountColumn`
- vitest: no "[object Object]", and money is formatted.

## G89-F21 (LOW): the closure letter prints an ISO date

**Fix.** Add `"closure.date", "date"` to `MatterClosureContextBuilder.FORMAT_HINTS` (around `:59-63`). `VariableFormatter:121-129` parses ISO dates. No pack bump is needed.

**Test.** Update the PDF assertion at `MatterClosureServiceIntegrationTest:749-751` to the long date ("6 November 2026" style, built from the run date). The context assertion at `:947` stays.

## G89-F19 (LOW): the report picker says "Select a trustaccount..."

**Cause.** `frontend/components/reports/entity-picker.tsx:26-28` falls back to the lowercased raw type, and `entityNounLabel` (`components/audit/entity-type-labels.ts:114-126`) knows only project, customer and invoice.

**Fix.** Add a `trustAccount` → "trust account" noun to the label map, or split camelCase in the fallback. Use "…" instead of "..." at `:91` and `:97`. Add a vitest.

## Process

Same as `batch-B-brief.md` "Process":
- check `pwd` before working
- commit each red test before its fix
- targeted tests: `verticals/legal/statement/**`, `closure/**`, `disbursement/**`, `trustaccounting/report/**`, `reporting/**`, `invoice/**`, `collections/**`, and every importer of a changed class
- run the frontend gate
- open the PR; do not run the full verify and do not merge
- report the PR number, head sha, test counts, changed existing tests and judgment calls
