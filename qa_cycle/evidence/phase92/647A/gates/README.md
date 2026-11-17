# 647A observed gates — PR #1794 @ ab1714caf (2026-11-17, SAST)

All commands ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-647A`, at HEAD
ab1714cafa60b85abd666d25036aeca28ce58c2e. Before the run the tree held only this new `gates/` dir
(`tree-before.txt`). The same was true after the run (`tree-after.txt`).

- No source edits, no commits, no Maven. No Maven verify ran during any e2e run.
- OBS changed state only through the UI. Alice (owner) signed in via `/mock-login`, uploaded the statement,
  ran auto-match, recorded five lines and unmatched one.
- psql was used read-only, as evidence.
- API reads used the browser context's own `mock-auth-token` against `:8081`. The frontend calls the backend
  from server actions, so a page network capture cannot see these calls.
- There was one API **write attempt**: the OBS d probe `POST /api/bank-statement-lines/{line 3}/record` with
  `FUND_INTEREST` (a credit-only classification) on a debit line. The dialog never offers it. It was refused
  with 400 and created nothing.
- Timings are in `timings.txt`. Every `*.log` (gitignored) is summarised in `logs-summary.txt`.

## Stack builds
No e2e stack was running at the start; `e2e-down.sh` ran first anyway (`stack0-down.log`). Exactly one e2e
stack ran at a time, and each was wiped with `e2e-down.sh` before the next. OBS ran on stack #1 after the
lifecycle suite (as in 646D), so no third stack was needed.

| Stack | Built | Flyway | Evidence |
|---|---|---|---|
| #1 (lifecycle + OBS) | "Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-647A", e2e-up rc 0 | V17–V20 success=t, V20 `phase92 statement line classification` at 11:47:14 UTC; 0 trust txns, 0 statements, 0 recons before the suite | `stack1-build.log`, `stack1-proof.txt`, `stack1-backend.log` (261 KB), `stack1-frontend.log`, `stack1-down.log` |
| #2 (depth-ii) | same source path, fresh after the wipe, rc 0 | V20 success=t at 11:58:17 UTC; 0 trust txns before the suite | `stack2-build.log`, `stack2-proof.txt`, `stack2-backend.log` (250 KB), `stack2-frontend.log`, `stack2-down.log` |

Proof that both stacks run this worktree's code (`vproof.sh`, identical on both):
- **Backend jar.** `/app/app.jar`:
  - `reconciliation/StatementLinePostingService.class` (new in 647A) contains `A zero-amount statement line
    cannot be recorded` and `Only a line recorded from the statement is reversed`.
  - `StatementLinePostingService$RecordFromLineRequest.class` contains `The VAT amount has at most two decimal
    places` (the review-round fix).
  - `TrustReconciliationController.class` contains `/api/bank-statement-lines/{lineId}/record`.
  - `TrustTransactionService.class` contains `reverseForUnmatch`.
  - The latest tenant migration in the jar is `V20__phase92_statement_line_classification.sql`.
- **Frontend `.next`.**
  - `Recoverable from the Fidelity Fund`, `record-from-statement-dialog`, `Unmatch and reverse`,
    `unmatch-recorded-dialog` and `Recorded: ` are in the reconciliation/new page chunk (SSR + static).
  - `Bank charge refunded from the business account` is in the `lib/types` chunks.
- **Runtime.** Health is UP and `:3001` returns HTTP 200. Each stack has the single tenant schema
  `tenant_7d218705360b`, one trust account and its two creditor accounts (Fund interest and charges,
  Unidentified deposits).

## Gate 1a — E2E-LEGAL lifecycle (stack #1): PASS (observed)
**105 passed / 0 failed / 0 skipped in 3.3 min** (202 s wall, 11:48:57–11:52:19 UTC), rc 0. This matches the
baseline of 105. `.last-run.json` reads `passed` (`lifecycle-last-run.json`).

The run includes the trust paths around 647A:
- Day 45 "Start a bank reconciliation — select the trust account and period" (the wizard's steps 1–2);
- the Day 60 interest run and investments;
- the deposits, fee transfer and payments that seed the books OBS used.

- Log: `lifecycle.log`, summary in `lifecycle-summary.txt`.
- Trust lines (50): `lifecycle-trust-tests.txt`.
- Suite screenshots (16 files, 4.0 MB): `lifecycle-artifacts/`.

## Gate 1b — E2E-LEGAL depth-ii (stack #2, fresh): PASS (observed)
**40 passed / 4 skipped / 0 failed in 2.9 min** (173 s wall, 11:58:36–12:01:29 UTC), rc 0. This matches the
baseline of 40/4. The 4 skips are the baseline ones:
- `day-30-45-soa-writeoff.spec.ts:361` (fixme)
- `null-prefetch-probe.spec.ts:74` ×3 (opt-in)

Log: `depth-ii.log`, summary in `depth-ii-summary.txt`, `depth-ii-last-run.json`. Screenshots (10 files, 3.7 MB) in
`depth-ii-artifacts/`. The read-only sums after the run (`depth-ii-derived-sums.out`) match 646C/646D/646E: cashbook
3 750.00 = every trust creditor 3 750.00 = client ledgers 3 750.00 (2 posted txns, 0 statements).

## Gate 2 — OBS (stack #1, after lifecycle): PASS (observed)
**Script:** `obs.cjs` (headless Chromium library script from the worktree's `frontend/node_modules`, run tag
`MUSQT`). It ran once, rc 0.

**Output:**
- `obs/obs-result.json` and `obs/obs-output.log`
- `obs/NN-*.png` and `.txt`, with crops of each dialog, the split pane and the Fund card
- `obs/api-*.json`, every API read and the d probe
- SQL in `obs-sums.sql` / `obs-sums.out`
- the uploaded statement, `obs/statement-OBS647A-MUSQT.csv`

**Window:** 11:52:36–11:56:44 UTC. **Console errors: 0. Page errors: 0** (both tabs).

**Dates.** Today is 2026-11-17 (SAST and UTC). The statement lines are dated today −7 … today −3; the period
end is the last line's date, 2026-11-14.

**Statement** (Standard Bank CSV layout, uploaded through step 2 of the wizard). The wizard showed "Lines
imported: 5 … Period: 2026-11-10 to 2026-11-14". Auto-match matched 0 and left 5 for review. The period end
2026-11-14 fitted, so the wizard raised no refusal.

| # | Date | Description / reference | Amount | Recorded as |
|---|---|---|---|---|
| 1 | 2026-11-10 | Interest credit `INT/OBS647A/MUSQT` | +1 234.56 | FUND_INTEREST |
| 2 | 2026-11-11 | Fidelity Fund interest transfer `LPFF/…` | −1 119.56 | FUND_SWEEP |
| 3 | 2026-11-12 | Monthly service fee `SVC/…` | −57.50 | BANK_CHARGE, recoverable, VAT 7.50 |
| 4 | 2026-11-13 | Fee refund from business account `RFD/…` | +115.00 | CHARGE_REFUND |
| 5 | 2026-11-14 | Cash deposit fee `CDF/…` | −115.00 | BANK_CHARGE, not recoverable, VAT 0 |

**Lifecycle books before OBS** (all dated 2026-11-17; 3 posted rows: Moroka deposit R 250 000, QuickCollect deposit
R 45 000, Moroka fee transfer R 8 500). Cashbook = trust creditors = client ledgers = **R 286 500,00**, Fund
ledger **R 0,00**. As at any date up to 2026-11-14, every term is 0.

### a. Record each line from the statement: PASS (`04`–`09`)
For each line Alice selected it on step 4, clicked **Record from statement**, chose the classification and clicked
Record. The dialog (`04`–`08` with `*-crop.png`) shows:
- "Line #n, {date} — {description} — R x DR/CR. The entry is dated the line's date and posts at once; unmatching the
  line reverses it."
- only the sign-valid classifications (see d);
- for BANK_CHARGE only, **"Recoverable from the Fidelity Fund?"** (Recoverable / Not recoverable), the help text
  "Is this charge on the Legal Practitioners' Fidelity Fund's list of recoverable bank charges?", and "VAT included
  in the charge" with "Enter 0 if the charge carries no VAT." Those fields are absent until Bank charge is chosen.

After each Record, the dialog closed, no error banner showed, the progress moved n−1 → n of 5, and the line
showed its badge (`09-a6-split-pane-crop.png`):

| # | Badge | SQL row (`obs-sums.out` a1–a5) | Line / statement | Δ cashbook, Δ Fund (today) |
|---|---|---|---|---|
| 1 | Recorded: Interest received for the Legal Practitioners' Fidelity Fund | FUND_INTEREST, RECORDED, 1 234.56, 2026-11-10, NON_CLIENT, effects +1 234.56/+1 234.56, posted | RECORDED / FUND_INTEREST; matched 0 → 1 | +1 234.56, +1 234.56 |
| 2 | Recorded: Interest paid to the Legal Practitioners' Fidelity Fund | FUND_SWEEP, RECORDED, 1 119.56, 2026-11-11, NON_CLIENT, −1 119.56/−1 119.56 | RECORDED / FUND_SWEEP; 1 → 2 | −1 119.56, −1 119.56 |
| 3 | Recorded: Bank charge | BANK_CHARGE, RECORDED, 57.50, 2026-11-12, NON_CLIENT, −57.50/−57.50, `charge_recoverable` t, `charge_vat_amount` 7.50 | RECORDED / BANK_CHARGE; 2 → 3 | −57.50, −57.50 |
| 4 | Recorded: Bank charge refunded from the business account | CHARGE_REFUND, RECORDED, 115.00, 2026-11-13, NON_CLIENT, +115.00/+115.00 | RECORDED / CHARGE_REFUND; 3 → 4 | +115.00, +115.00 |
| 5 | Recorded: Bank charge | BANK_CHARGE, RECORDED, 115.00, 2026-11-14, NON_CLIENT, −115.00/−115.00, recoverable f, VAT 0.00 | RECORDED / BANK_CHARGE; 4 → 5 | −115.00, −115.00 |

For every row:
- `creditor_account_id` is the account's FUND_INTEREST_AND_CHARGES account `4966f021…`.
- `bank_statement_line_id` is the line, and the line's `trust_transaction_id` is the row.
- `approved_by` is null (no approval asked or needed), and `posted_at` is set.
- The charge columns are null on the non-charge rows.
- Each Record created exactly one trust row (count 6 → 11).
- The API (`obs/api-a{n}-transaction.json`) returns the same type, amount, date and status RECORDED.

Every per-line check in `obs-result.json` (`steps.a.lines[].checks`, 17 checks × 5 lines) is true. Each Δ equals
the posting table (FUND_INTEREST +L / FUND_SWEEP −L / BANK_CHARGE −L / CHARGE_REFUND +L, on both cashbook and Fund).

### b. Books after all five: PASS (`10`; UI = API = SQL)
| Figure | UI | API | SQL |
|---|---|---|---|
| Fund ledger, Other trust creditors card (as at today) | **R 57,50** "Interest received for the Fund, less bank charges" | `creditor-accounts` balance 57.5, inDebit false | 57.50 |
| Net Fund movement | — | — | **+57.50** = +1 234.56 −1 119.56 −57.50 +115.00 −115.00 |
| Net cashbook movement | — | — | +57.50 (286 500.00 → 286 557.50) |
| Statement `matched_count` | "5 / 5 lines resolved" | `bank-statements/{id}` | 5 |
| Recon as at 14 Nov (split pane) | Bank 57,50 · adjusted 57,50 · cashbook 57,50 · difference 0,00 · cashbook 57,50 = trust creditors 57,50 · Fund 57,50 · **"Reconciliation is balanced"** | `trust-reconciliations/{id}` bank 57.5, cashbook 57.5, isBalanced true | — |

Trust equation as at each date. API is `cashbook-balance?asAt` plus `total-balance?asAt` (creditorBreakdown); SQL is
the `TrustLedgerQueryRepository.position` terms. API = SQL on every term at every date, and cashbook = total trust
creditors at every date:

| asAt | cashbook | trust creditors | Fund ledger | client ledgers |
|---|---|---|---|---|
| 2026-11-09 | 0 | 0 | 0 | 0 |
| 2026-11-10 (line 1) | 1 234.56 | 1 234.56 | 1 234.56 | 0 |
| 2026-11-11 (line 2) | 115.00 | 115.00 | 115.00 | 0 |
| 2026-11-12 (line 3) | 57.50 | 57.50 | 57.50 | 0 |
| 2026-11-13 (line 4) | 172.50 | 172.50 | 172.50 | 0 |
| 2026-11-14 (line 5) | 57.50 | 57.50 | 57.50 | 0 |
| 2026-11-17 (today) | 286 557.50 | 286 557.50 | 57.50 | 286 500.00 |

### c. Unmatch the recoverable charge (line 3): PASS (`11`–`13`)
Alice clicked **Unmatch** on line 3. The confirmation (`11-c1-unmatch-dialog-crop.png`) reads:

> **Unmatch recorded line?** This line was recorded as Bank charge. Unmatching it reverses the entry it created, dated
> 12 Nov 2026 — the line's date. The line returns to Unmatched. [Keep] [Unmatch and reverse]

She confirmed. Line 3 then shows **Unmatched** (amber, no badge). The progress reads 4 / 5, and no error banner shows.

| Check | Result (SQL = API) |
|---|---|
| Contra | `c0657aa9…` REVERSAL, RECORDED, 57.50, **2026-11-12** (the line's date), NON_CLIENT, Fund account `4966f021…`, effects +57.50/+57.50, posted, `reversal_of` = the original, no line link |
| Original `7accbcab…` | **REVERSED**, `bank_statement_line_id` null, `reversed_by_id` = the contra; it keeps its charge columns (t / 7.50) |
| Line 3 | **UNMATCHED**, classification **null**, `trust_transaction_id` null |
| `matched_count` | 5 → **4** |
| Trust rows | 11 → 12 (only the contra) |
| Fund ledger today | 57.50 → **115.00** (+57.50). The Other trust creditors card shows R 115,00 (`13`), the API 115 |
| Cashbook today | 286 557.50 → **286 615.00** (+57.50) |
| As at 12 Nov | Fund / cashbook 57.50 → 115.00. As at 11 Nov: unchanged 115.00 |
| Equation | As at 11 Nov, 12 Nov and today, cashbook = trust creditors (115 / 115 / 286 615); API = SQL |
| Recon as at 14 Nov | Cashbook 115,00 = trust creditors 115,00 (difference 0). Bank 57,50 vs cashbook 115,00 gives difference **−R 57,50** and "Reconciliation is not balanced". This is expected: the bank line is unresolved again. API: isBalanced false |

All 19 checks in `steps.c.checks` are true.

### d. Sign rule: PASS — only sign-valid classifications are offered; the server refuses the other
What the dialog offered (`steps.d.offered`; the option values come from the radio buttons):

| Line | Sign | Offered |
|---|---|---|
| 1 (+1 234.56), 4 (+115.00) | credit | "Interest received for the Legal Practitioners' Fidelity Fund" (FUND_INTEREST), "Bank charge refunded from the business account" (CHARGE_REFUND) |
| 2 (−1 119.56), 3 (−57.50), 5 (−115.00) | debit | "Interest paid to the Legal Practitioners' Fidelity Fund" (FUND_SWEEP), "Bank charge" (BANK_CHARGE) |

After the unmatch, line 3 was reopened (`14`). It again offered only the two debit options, and Cancel closed the
dialog with nothing posted. The UI cannot send a wrong-sign request.

The supplementary API probe on line 3 (`obs/api-d-sign-probe-credit-type-on-debit-line.json`) sent
`{"classification":"FUND_INTEREST"}`. It got **400** "Classification does not match the line" / "Interest
received for the Legal Practitioners' Fidelity Fund is recorded from a credit line; this line is a debit". Trust
rows stayed at 12 → 12, and the line stayed UNMATCHED / null.

### e. Console and page errors: PASS
- Console errors: **0**. Page errors: **0**, across the wizard tab and the Other trust creditors tab.
- Backend: **0 ERROR**. The OBS window logged 12 INFO only (`obs-window-log-breakdown.txt`); the 400 probe logs
  nothing at WARN or above.
- Frontend: **0** `⨯` lines in the OBS window.

### Logs
| Log | Result |
|---|---|
| Backend stack #1 | 342 INFO, 151 WARN, **0 ERROR** (`stack1-log-breakdown.txt`). OBS window 11:52:36–11:56:44 UTC: 12 INFO, 0 WARN, 0 ERROR |
| Backend stack #2 | 272 INFO, 199 WARN, **0 ERROR** (`stack2-log-breakdown.txt`) |

The WARNs are the known noise:
- PDFBox fallback fonts and the font cache;
- startup bean notices;
- 5 expected 403s on time-summary/by-member (stack #1);
- tax-number-missing drafts;
- notification no-recipients;
- LibreOffice absent;
- the `uri` meter-tag cap.

The frontend logged `⨯ Error: The destination stream closed early.` as follows:
- stack #1: 23 times, all between 11:49:04 and 11:52:06, during the lifecycle suite. **0 fell in the OBS window.**
- stack #2: twice.

This is the same navigation-abort noise recorded in 646B–646D.

Final EQ-2 on stack #1 (`stack1-final-eq.out`): cashbook 286 615.00 = every trust creditor 286 615.00 (client ledgers
286 500.00 + Fund ledger 115.00), 9 posted rows.

## Found items
No defect in 647A's scope (`found-obs.md`). Observations:
- **O-1 (brief / harness):** `GET …/cashbook-balance?asAt` returns only `{balance}`. The creditor breakdown is on
  `GET …/total-balance?asAt` (`ClientLedgerService.TotalBalanceResponse`). OBS read both, as 646D did. Not a defect.
- **O-2 (pre-existing, out of scope):** step 4's "Unmatched Trust Transactions" pane lists `FEE/MOROKA/…` R 8 500
  dated **17 Nov**, after the 14 Nov period end (`09`, `12`). The wizard fetches every APPROVED unlinked row with no
  date bound (`reconciliation/new/page.tsx` `handleStartReview` / `handleUnmatch`, `status: "APPROVED"`; unchanged by
  647A, and present on `main`). It does not affect the figures, which are computed server-side as at the period end.
  Suggested owner: 652A (it owns those handlers).
- **O-3 (UX, low):** both bank-charge lines read just "Recorded: Bank charge". The badge does not say recoverable or
  not; that detail is visible only in the row (API/SQL). It is a product call whether the badge should say it.
- **O-4 (harness):** the full-page screenshots draw the fixed sidebar and top bar part-way down the page (a Playwright
  `fullPage` artefact). The element crops are exact.

## Cleanup
- After each legal run I copied the suite screenshots to `lifecycle-artifacts/` and `depth-ii-artifacts/`, then ran
  `git clean -fd -- documentation/screenshots/legal-vertical` and `git checkout` on it.
- `frontend/test-results/` is ignored.
- Both stacks were torn down (`stack1-down.log`, `stack2-down.log`); no e2e container is running.
- Afterwards `git status --short` shows only `?? qa_cycle/evidence/phase92/647A/gates/` (`tree-after.txt`). HEAD is
  ab1714caf.
