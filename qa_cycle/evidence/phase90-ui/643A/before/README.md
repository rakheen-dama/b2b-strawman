# 643A before-OBS (task 643.1): trust dashboard ignores reconciliation state

Stack: mock-auth E2E (frontend :3001, backend :8081), built from `main` @ 43142e947, tenant `tenant_7d218705360b`, 2026-11-13.
Browser: Playwright MCP was locked ("Browser is already in use"), so I used Node scripts with `@playwright/test` chromium (nvm node 20), light scheme, as Alice (owner) via `/mock-login` and "Sign In". SQL was read-only SELECTs.

## Scripts (in the order I ran them)
1. `probe.cjs`: read-only. Reads trust-position, cashbook, reconciliations and trend, plus the summary-cards text. This is the only true pre-state: no reconciliations, all `lastReconciliation*` fields null, card "Not yet reconciled".
2. `drive.cjs`, run 1 (no failure capture yet). Wizard steps 1-3 passed. "Review Matches" created DRAFT reconciliation `48efb1b4…` on statement `b01e5c58…` (8 of 9 lines auto-matched), then step 4 never rendered.
3. `drive.cjs`, run 2 (failure capture added, same file as saved). This run produced the `*-between-runs-*` screenshots, which already show run 1's DRAFT, and `recon-step2-uploaded-run2-1440.png`. It created DRAFT `9656dd63…` on statement `767d4276…` (0 auto-matched, because the transactions were already matched in run 1). Step 4 crashed again, captured in `recon-step4-crash-1440.png`. The console showed `TypeError: Cannot read properties of undefined (reading 'length')`.
4. `observe.cjs`: read-only. Produced the post-state screenshots and `observe-raw.json`.

## Wizard steps I drove (for the after-OBS to repeat)
1. Trust accounting → Reconciliation (`/org/e2e-test-org/trust-accounting/reconciliation`) → **New Reconciliation** (`new-reconciliation-btn`).
2. Step 1: the primary account is preselected. Set Period End `2026-11-13` (`period-end-input`) → **Next**.
3. Step 2: **Choose CSV File** (`file-input`) with `statement-2026-11-13.csv` (Standard Bank format; it mirrors the 8 posted transactions and closes at R290 249,98) → "Statement imported successfully" → **Next**.
4. Step 3: **Run Auto-Match** → **Review Matches**. This creates a DRAFT reconciliation, then the page crashes ("Something went wrong").
5. The plan was to exclude the "Opening Balance" 0.00 line (this triggers calculate) and then press **Complete Reconciliation**. I never got there because of the crash.

## Why a balanced, completed reconciliation is not possible through the UI at HEAD
- **Wizard crash:** `POST /api/bank-statements/{id}/auto-match` returns `MatchResult(totalLines, autoMatched, alreadyMatched, unmatched)`, which has no `lines` field. The wizard calls `setLines(result.data.lines)` and gets `undefined`, so `ReconciliationSplitPane` fails on `lines.length`. Auto-match is mandatory before Review, so every UI reconciliation crashes at step 4.
- **This stack's data:** cashbook is R290 249,98 and client ledgers are R290 275,50, a difference of R25,52. The difference is the posted interest run's `INTEREST_LPFF` (a firm-level outflow with no customer). `isBalanced` requires cashbook == ledger, so this stack cannot balance even if the crash is fixed.
- Even without the crash, `isBalanced` is only computed on match/exclude/unmatch. If auto-match matches every line, the user has to make a manual change before Complete is enabled.

## Observed after the wizard (2 DRAFT reconciliations exist)
- Trust dashboard Reconciliation card: "Not yet reconciled" / "No reconciliation records found". This is the defect: `fetchDashboardData` hard-codes null. The Reconciliation list page shows 2 rows dated 13 Nov 2026, both "Unbalanced".
- Home trust widget: "Last reconciliation: Period to 13 Nov 2026, in draft".
- `GET /api/dashboard/trust-balance-trend` returns 404 ("No static resource").

The full values are in `before.json` (compact). The raw script output is in `observe-raw.json`.
