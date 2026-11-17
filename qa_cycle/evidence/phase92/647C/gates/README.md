# 647C observed gates — PR #1795 @ 6e485f412 (2026-11-17, SAST)

All commands ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-647C`, at HEAD
6e485f412845e8fcaa1d91cafa54f7b63745b3d4. Before the run the tree held only this new `gates/` dir
(`tree-before.txt`). The same was true after the run (`tree-after.txt`).

- No source edits, no commits, no Maven. `pgrep -fl surefire` was empty before each e2e run, and no Maven verify ran
  during any e2e run.
- The only other containers on the machine belonged to an unrelated project (a `maestro` chaos run). They use none
  of the e2e ports.
- OBS changed state only through the UI. Alice (owner) signed in via `/mock-login`, uploaded the statement, ran
  auto-match, recorded four lines (each confirming the pre-selected suggestion) and unmatched one.
- psql was used read-only, as evidence. API reads used the browser context's own `mock-auth-token` against `:8081`.
  There were **no API writes**.
- Node: nvm node 20.11.1 and `/opt/homebrew/bin/pnpm`, with `PLAYWRIGHT_BASE_URL=http://localhost:3001`.
- Timings are in `timings.txt`. Every `*.log` is summarised in `logs-summary.txt`.

## Stack builds
No e2e stack was running at the start; `e2e-down.sh` ran first anyway (`stack0-down.log`). Exactly one e2e stack ran at
a time, and each was wiped with `e2e-down.sh` before the next. As in 647A, OBS ran on stack #1 after the lifecycle
suite.

| Stack | Built | Flyway | Evidence |
|---|---|---|---|
| #1 (lifecycle + OBS) | "Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-647C", e2e-up rc 0 (13:33:18–13:36:17 UTC) | V17–V20 success=t (V20 at 13:34:57 UTC); 0 trust txns, 0 statements, 0 recons before the suite | `stack1-build.log`, `stack1-proof.txt`, `stack1-backend.log`, `stack1-frontend.log`, `stack1-down.log` |
| #2 (depth-ii) | same source path, fresh after the wipe, rc 0 (13:45:50–13:46:27 UTC) | V20 success=t at 13:46:22 UTC; 0 trust txns before the suite | `stack2-build.log`, `stack2-proof.txt`, `stack2-backend.log`, `stack2-frontend.log`, `stack2-down.log` |

Proof that both stacks run this worktree's code (`vproof.sh`; the two outputs are identical apart from the Flyway
timestamps):
- **Backend jar `/app/app.jar`:**
  - `reconciliation/parser/CsvBankStatementParser.class` contains `suggestClassification`, `containsAny`, `LPFF`,
    `FIDELITY`, `COMMISSION`, `REFUND`, `REVERSAL`, `SERVICE` and `INTEREST`. These are 647C's keyword table.
  - `ParsedStatementLine.class` contains `suggestedClassification`.
  - `creditor/TrustCreditorLedgerController.class` contains `/api/trust-creditor-accounts/{creditorAccountId}/transactions`
    (the exact path string from the source).
  - `creditor/TrustCreditorLedgerService.class` contains `listCreditorTransactions`.
  - `transaction/CreditorLedgerRow.class` **exists** (new in 647C) and contains `getChargeVatAmount`.
  - `TrustLedgerQueryRepository.class` contains `creditorLedgerRows`.
  - `TrustReconciliationService.class` contains `setSuggestedClassification`.
  - The latest tenant migration is V20; 647C adds none.
- **Frontend `.next`:**
  - `fund-ledger-table` is in the SSR and static chunks.
  - `classification-suggested-note` and `Suggested from the line` are in the reconciliation/new page chunk (SSR and
    static).
  - `Go to the first page` and `fund-ledger-page-out-of-range` are in the SSR chunk.
  - `Awaiting approval` is in the `frontend_lib` SSR chunks.
- **Runtime.** Health is UP and `:3001` returns HTTP 200. Each stack has the single tenant schema
  `tenant_7d218705360b`, one trust account and its two creditor accounts. The column
  `bank_statement_lines.suggested_classification` is present.

## Gate 1a — E2E-LEGAL lifecycle (stack #1): PASS (observed)
**105 passed / 0 failed / 0 skipped in 3.4 min** (204 s wall, 13:36:24–13:39:48 UTC), rc 0. This matches the
baseline of 105. `.last-run.json` reads `passed` (`lifecycle-last-run.json`).

- Log: `lifecycle.log`, summary in `lifecycle-summary.txt`.
- Trust lines (50): `lifecycle-trust-tests.txt`. They include Day 45 "Start a bank reconciliation" (wizard steps 1–2)
  and the Day 60 interest run.
- Suite screenshots (16): `lifecycle-artifacts/`.

## Gate 1b — E2E-LEGAL depth-ii (stack #2, fresh): PASS (observed)
**40 passed / 4 skipped / 0 failed in 2.2 min** (134 s wall, 13:46:58–13:49:12 UTC), rc 0. This matches the baseline
of 40/4. The 4 skips are exactly the baseline ones:
- `day-30-45-soa-writeoff.spec.ts:361` (fixme)
- `null-prefetch-probe.spec.ts:74` ×3 (opt-in)

- Log: `depth-ii.log`, summary in `depth-ii-summary.txt`, `depth-ii-last-run.json`.
- Screenshots (10): `depth-ii-artifacts/`.
- Read-only sums after the run (`depth-ii-derived-sums.out`) match 647A: cashbook 3 750.00 = every trust creditor
  3 750.00 = client ledgers 3 750.00 (2 posted txns, 0 statements).

## Gate 2 — OBS (stack #1, after lifecycle): PASS on a–f (observed), with one visual defect F-1 found
**Script:** `obs.cjs`, a headless Chromium library script from the worktree's `frontend/node_modules`. Run tag `QP4KZ`;
it ran once, rc 0.

**Supplementary probe:** `probe-clip.cjs`, read-only, run for F-1.

**Output:**
- `obs/obs-result.json` (with `verdict`) and `obs/obs-output.log`
- `obs/NN-*.png` and `.txt`, with `*-crop.png` element crops
- `obs/api-*.json`
- SQL in `obs-sums.sql` / `obs-sums.out`
- the uploaded statement, `obs/statement-OBS647C-QP4KZ.csv`

**Window:** 13:40:10–13:44:16 UTC (probe 13:45:11–13:45:16). **Console errors: 0. Page errors: 0** (wizard tab and
Other trust creditors tab).

**Dates.** Today is 2026-11-17. The lines are dated today −7 … today −3, plus the auto-match line on 2026-11-17 (the
seed row's date). The period end is 2026-11-17, and the wizard raised no refusal.

**Books before OBS.** There were 6 trust rows from the lifecycle seed. The Fund ledger had **0 rows**. The page showed
the empty state "No entries on the Fund ledger yet. …" and the Fund card read R 0,00 (`01-*`).

**Statement** (Standard Bank CSV layout, uploaded through step 2). The wizard showed "Lines imported: 6 … Period:
2026-11-10 to 2026-11-17" (`02`).

| # | Date | Description | Amount | Expected suggestion |
|---|---|---|---|---|
| 1 | 2026-11-10 | CREDIT INTEREST | +1 234.56 | FUND_INTEREST |
| 2 | 2026-11-11 | LPFF FIDELITY FUND INTEREST SWEEP | −1 119.56 | FUND_SWEEP |
| 3 | 2026-11-12 | MONTHLY SERVICE FEE | −57.50 | BANK_CHARGE |
| 4 | 2026-11-13 | SERVICE FEE REFUND | +57.50 | CHARGE_REFUND |
| 5 | 2026-11-14 | DEPOSIT J NKOSI (ordinary) | +3 210.00 | none |
| 6 | 2026-11-17 | TRUST TO BUSINESS FEE TRANSFER, ref `FEE/MOROKA/mwkqkcdh76db` (the seed's approved fee transfer) | −8 500.00 | BANK_CHARGE at import, then cleared by auto-match |

### a. Parser suggestions stored at import: PASS (`02`; `obs-sums.out` "a: lines at import")
Read-only SQL right after the upload, before auto-match:

| Line | `suggested_classification` |
|---|---|
| 1 | FUND_INTEREST |
| 2 | FUND_SWEEP |
| 3 | BANK_CHARGE |
| 4 | CHARGE_REFUND |
| 5 | **null** |
| 6 | BANK_CHARGE |

All 6 match the expected values, and all lines are UNMATCHED. The trust row count stayed at 6 → 6, so the import
posted nothing.

### b. Auto-match clears the suggestion: PASS (`03`, `04`; `api-b-bank-statement-after-auto-match.json`)
The wizard showed "Auto-matched 1 lines, 5 lines remaining for manual review" and "1 / 6 lines resolved".

Line 6 changed as follows:
- `match_status` became **AUTO_MATCHED**;
- `trust_transaction_id` became `4d43bdd4…`, the seed FEE_TRANSFER R 8 500 matched by reference;
- `suggested_classification` went from **BANK_CHARGE to null**.

Lines 1–5 kept their suggestions and stayed UNMATCHED. Auto-match created no row (6 → 6). The API's
`suggestedClassification` per line agrees with SQL (null on lines 5 and 6).

### c. Pre-selection in the dialog: PASS (`05`–`09` look, `10`–`13` record, `14`)
**Pass 1: open, screenshot, Cancel.** Each dialog has a `*-dialog-crop.png`.

| Line | Offered | Checked | Note "Suggested from the line's description — check it before recording." | Charge fields at once |
|---|---|---|---|---|
| 1 | FUND_INTEREST, CHARGE_REFUND | **FUND_INTEREST** | visible | no |
| 2 | FUND_SWEEP, BANK_CHARGE | **FUND_SWEEP** | visible | no |
| 3 | FUND_SWEEP, BANK_CHARGE | **BANK_CHARGE** | visible | **yes**: `charge-recoverable-radio` and `charge-vat-input` were visible without a click (`07-c-look-line3-dialog-crop.png`) |
| 4 | FUND_INTEREST, CHARGE_REFUND | **CHARGE_REFUND** | visible | no |
| 5 (ordinary) | FUND_INTEREST, CHARGE_REFUND | **none** | **absent** | no (`09-c-look-line5-dialog-crop.png`) |

After every Cancel the trust rows were unchanged and the line was still UNMATCHED. After pass 1 there were 6 rows,
and no row was linked to an OBS line other than the auto-matched seed row.

**Pass 2: record lines 1–4, confirming the pre-selected choice.** The script did not click the classification.

- Before each Record click, SQL showed `rows_for_line = 0` for that line (`obs-sums.out` "c: line n rows before
  Record"), and the total row count equalled the previous count. **Nothing was posted before the click.**
- For BANK_CHARGE, Alice chose Recoverable and VAT 7.50.

After each click the dialog closed with no error banner. Each click created exactly one row:

| Line | Badge | Row (SQL) |
|---|---|---|
| 1 | "Recorded: Interest received for the Legal Practitioners' Fidelity Fund" | `a47cd4cc…` FUND_INTEREST, RECORDED, 1 234.56, 2026-11-10, effect +1 234.56 |
| 2 | "Recorded: Interest paid to the Legal Practitioners' Fidelity Fund" | `c5b0ac79…` FUND_SWEEP, RECORDED, 1 119.56, 2026-11-11, −1 119.56 |
| 3 | "Recorded: Bank charge" | `d13f6191…` BANK_CHARGE, RECORDED, 57.50, 2026-11-12, −57.50, recoverable true, VAT 7.50 |
| 4 | "Recorded: Bank charge refunded from the business account" | `862e5d44…` CHARGE_REFUND, RECORDED, 57.50, 2026-11-13, +57.50 |

Each line became RECORDED with `classification` equal to the confirmed choice. Its `suggested_classification` stayed
as stored. The wizard showed "5 / 6 lines resolved". Every check in `steps.c.record[].checks` is true.

### d. Fund ledger rows with running balance: PASS on the figures (`15`; `api-d-after-record-*.json`); see F-1
On Other trust creditors:
- `fund-ledger-table` lists **4 `fund-ledger-row`s**, one per Fund ledger entry. SQL has 4 and the API reports
  `totalElements` 4.
- The order is newest first, and UI = API = SQL, row by row (id and reference).

Running balances: SQL is the window `SUM(ledger_effect) OVER (ORDER BY transaction_date, created_at, id)` over the
posted NON_CLIENT rows of the Fund creditor account `a01987bd…`.

| Row (newest first) | UI `fund-ledger-balance` | API `runningBalance` | SQL window sum |
|---|---|---|---|
| 13 Nov refund RFD/… | R 115,00 | 115 | 115.00 |
| 12 Nov bank charge SVC/…, `fund-ledger-charge` **"Recoverable · VAT R 7,50"** | R 57,50 | 57.5 | 57.50 |
| 11 Nov sweep LPFF/… | R 115,00 | 115 | 115.00 |
| 10 Nov interest INT/… | R 1 234,56 | 1234.56 | 1 234.56 |

**The top running balance is R 115,00. It equals the Fund StatCard (R 115,00), the API card balance (115) and the
SQL creditor balance as at today (115.00).** The rows without a charge show "—" in the charge cell. All the
`steps.d.checks` are true.

The figures are right, but the Running balance column is visually pushed off the right edge at 1440 and 1280 px:
**F-1**.

### e. Out-of-range page: PASS (`16`, `17`; `api-e-fund-ledger-transactions-page50.json`)
`?page=50` shows **"No entries on this page. Go to the first page"** (`fund-ledger-page-out-of-range`):
- 0 rows;
- not the empty-ledger message;
- the API returns `content: []`, `number` 50, `totalElements` 4, `totalPages` 1.

Clicking **Go to the first page** loaded `…/other-creditors?page=0`, which shows all **4 rows**.

### f. Unmatch: PASS (`18`–`21`; `api-f-after-unmatch-*.json`)
Alice clicked Unmatch on line 3 (the bank charge). The confirmation read "Unmatch recorded line? This line was
recorded as Bank charge. Unmatching it reverses the entry it created, dated 12 Nov 2026 — the line's date. …
[Keep] [Unmatch and reverse]". She confirmed.

On the reconciliation:
- Line 3 shows **Unmatched**, with no error banner, and the wizard reads 4 / 6 resolved.
- SQL: the original `d13f6191…` is **REVERSED**, and `reversed_by_id` is the contra `ba4aa882…`. The contra is a
  REVERSAL dated 2026-11-12 with effect +57.50.
- The line is UNMATCHED with classification null. The trust rows went 10 → 11; only the contra was added.

The Fund ledger (`20-*`) now has **5 rows**. UI = API = SQL on every row:

| Row (newest first) | Badge | Running balance (UI = API = SQL) |
|---|---|---|
| 13 Nov refund | — | **R 172,50** |
| 12 Nov `REV-SVC/…` "Statement line unmatched", type **Reversal** (the contra) | — | R 115,00 |
| 12 Nov SVC/… Bank charge, still "Recoverable · VAT R 7,50" | **Reversed** | R 57,50 |
| 11 Nov sweep | — | R 115,00 |
| 10 Nov interest | — | R 1 234,56 |

**The top running balance is R 172,50. It equals the StatCard (R 172,50), the API (172.5) and the SQL balance
(172.50).** All 13 checks in `steps.f.checks` are true.

Supplementary: the unmatched line kept `suggested_classification = BANK_CHARGE`, as the Javadoc says ("kept through a
recorded line's unmatch"). Re-opening its dialog pre-selected Bank charge again, with the note and the charge fields
(`21`). Cancel posted nothing (rows 11 → 11).

Final EQ-2 on stack #1 (`stack1-final-eq.out`): cashbook **286 672.50** = all trust creditors 286 672.50; the Fund
ledger is 172.50. This is the lifecycle's 286 500.00 plus +1 234.56 −1 119.56 −57.50 +57.50 +57.50.

### Console, page and log errors: PASS
- Console errors **0**, page errors **0**.
- Backend, OBS and probe window 13:40:10–13:45:16 UTC: **15 INFO, 0 WARN, 0 ERROR** (`obs-window-log-breakdown.txt`).
- Frontend: **0** `⨯` lines in the OBS window.

### Logs
| Log | Result |
|---|---|
| Backend stack #1 | 343 INFO, 151 WARN, **0 ERROR** (`stack1-log-breakdown.txt`) |
| Backend stack #2 | 272 INFO, 199 WARN, **0 ERROR** (`stack2-log-breakdown.txt`) |

The WARN kinds are the same set as 647A's on both stacks; a normalised diff finds no new kind. They are the known
noise: PDFBox fallback fonts, startup notices, expected 403s, tax-number drafts, notification no-recipients,
LibreOffice absent, and the meter-tag cap.

The frontend logged `⨯ Error: The destination stream closed early.` as follows:
- stack #1: 36 times, all between 13:36:29 and 13:39:36, during the lifecycle suite;
- stack #2: once.

This is the known navigation-abort noise (646B–647A).

## Found items
See `found-obs.md`.
- **F-1 (647C scope, visual):** the Fund ledger table (1 102 px) is wider than its `overflow-x-auto` wrapper:
  - at 1440 px the wrapper is 1 086 px, so the last digit of every running balance and the header's "E" are hidden
    ("R 172,5");
  - at 1280 px the wrapper is 926 px, so the whole Running balance column is off-screen;
  - at 1920 px it fits.
  
  The DOM text is correct, so the figure checks pass. Measured by `probe-clip.cjs` (`obs/probe-clip.json`,
  `obs/22-probe-*`).
- **O-1:** the fee-transfer line got a BANK_CHARGE suggestion at import ("FEE"), which auto-match cleared. This is
  the known substring false positive (`../found.md` #3).
- **O-2 (a11y, low):** Amount is unsigned; its direction is shown only by colour and an `aria-hidden` arrow.
- **O-3 (harness):** `fullPage` screenshots draw the fixed sidebar mid-page; the crops are exact.

## Cleanup
- After each legal run I copied the suite screenshots to `lifecycle-artifacts/` (16) and `depth-ii-artifacts/` (10).
  I then ran `git clean -fd -- documentation/screenshots/legal-vertical` and `git checkout` on it.
- Both stacks were torn down (`stack1-down.log`, `stack2-down.log`); 0 e2e containers are running.
- `git status --short` shows only `?? qa_cycle/evidence/phase92/647C/gates/` (`tree-after.txt`). HEAD is 6e485f412.
