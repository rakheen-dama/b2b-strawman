# 646C observed gates — PR #1792 @ 92b4b5550 (2026-11-17, SAST)

All commands ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-646C`, at HEAD
92b4b5550ab8315bf76391e78bf0db08dfd70481. The tree was clean before and after.

- No source edits, no commits, no Maven.
- OBS changed state only through the UI, as Alice (owner) signed in via `/mock-login`.
- psql was used read-only, as evidence.
- API reads used the browser context's own `mock-auth-token` against `:8081`. The frontend calls the backend
  from server actions, so a page network capture cannot see these calls.
- There was one API **write attempt**: the OBS d probe `POST …/reconciliations`. It reproduces a refusal the
  UI had already shown, was refused with 400, and created nothing (reconciliation count stayed at 1).

## Stack builds
The previous slice's stack was torn down first (`stack0-down.log`). Exactly one e2e stack ran at a time.

| Stack | Built | Flyway | Evidence |
|---|---|---|---|
| #1 (lifecycle + OBS) | "Building from source at: …/worktree-epic-646C" | V16–V19 success=t, V19 `phase92 trust recon breakdown` at 07:34:17 UTC; 0 trust txns, 0 recons before the suite | `stack1-build.log`, `stack1-proof.txt`, `stack1-backend.log`, `stack1-frontend.log` |
| #2 (depth-ii) | same source path, after an `e2e-down.sh` wipe (`stack1-down.log`) | V16–V19 success=t, V19 at 07:40:45 UTC; 0 trust txns before the suite | `stack2-build.log`, `stack2-proof.txt`, `stack2-backend.log` |

Proof that both stacks run this worktree's code (`vproof.sh`):
- `/app/app.jar` holds `books/TrustBooksService.class`, `transaction/TrustPositionRow.class` and
  `transaction/OutstandingItemsRow.class`. All three are new in 646C.
- The jar also holds `db/migration/tenant/V19__phase92_trust_recon_breakdown.sql`.
- `TrustLedgerQueryRepository.class` contains `FUND_INTEREST_AND_CHARGES`, which comes from 646C's
  six-term position SQL.
- `trust_reconciliations` has `creditor_total`, `difference` (numeric) and `integrity_flags` (jsonb), plus
  `investment_creditor_total`, `fund_ledger_balance`, `unidentified_deposit_total` and
  `inter_account_balance`.
- The frontend container's `.next/server/chunks/ssr/…reconciliation_new_page_tsx…js` contains the 646C
  testids `comparison-cashbook-creditors`, `cashbook-to-creditors-difference`, `creditor-breakdown` and
  `period-end-error`.
- Health is UP and `:3001` returns HTTP 200. Both stacks have the single tenant schema `tenant_7d218705360b`.

## Gate 1a — E2E-LEGAL lifecycle (stack #1): PASS (observed)
**105 passed / 0 failed / 0 skipped in 3.2 min** (195 s wall), rc 0. This matches the baseline of 105.
`.last-run.json` reads `passed` (`lifecycle-last-run.json`).

`day-45-recon-prescription.spec.ts`, which 646C changed, passed all 4 of its tests (#59–#62). These include
"Start a bank reconciliation — select the trust account and period end, reach the statement upload step".

- Log: `lifecycle.log`, summary in `lifecycle-summary.txt`.
- Trust and reconciliation lines: `lifecycle-trust-tests.txt`.
- Suite screenshots: `lifecycle-artifacts/`.

## Gate 1b — E2E-LEGAL depth-ii (stack #2, fresh): PASS (observed)
**40 passed / 4 skipped / 0 failed in 2.0 min** (123 s wall), rc 0. This matches the baseline of 40/4. The
4 skips are the baseline ones:
- `day-30-45-soa-writeoff.spec.ts:361` (fixme)
- `null-prefetch-probe.spec.ts:74` ×3 (opt-in)

Log: `depth-ii.log`, summary in `depth-ii-summary.txt`, screenshots in `depth-ii-artifacts/`. The read-only
sums after the run (`depth-ii-derived-sums.out`) match 646B/646E: cashbook 3 750.00 = client ledgers
3 750.00 (2 txns).

## Gate 2 — OBS (stack #1, after lifecycle): PASS (observed)
**Script:** `obs.cjs`, adapted from the 646E template.

**Output:**
- `obs/obs-result.json` and `obs/obs-output.log`
- `obs/NN-*.png` and `.txt`
- `obs/api-reconciliation-*.json`
- SQL in `obs-sums.sql` / `obs-sums.out`
- CSVs in `csv/`

**Console errors: 0. Page errors: 0.**

Dates: today 2026-11-17 (SAST and UTC). Run tag `DS39J`. The lifecycle left these books (all dated
2026-11-17):

| Row | Amount | Status |
|---|---|---|
| Moroka deposit `DEP/MOROKA/…` | 250 000 | RECORDED |
| QuickCollect deposit `DEP/QC/…` | 45 000 | RECORDED |
| Moroka fee transfer `FEE/MOROKA/…` | 8 500 | APPROVED |
| 3 rows | — | AWAITING_APPROVAL, unposted |

Cashbook = client ledgers = **286 500.00**.

### a. Future period end refused in step 1 (CR-3): PASS (`01`, `02`)
Steps: Trust accounting → Reconciliation → **New Reconciliation**. Select the account (the only option,
"Mathebula & Partners Trust Account (Standard Bank - 0123456789)"). The input's `max` is `2026-11-17`.

Typing **2026-11-18** is accepted by the input, and Next is enabled. Clicking Next gives:
- **"The period end cannot be in the future"** (`role=alert`, `aria-invalid=true`).
- The wizard **stays on step 1**: the upload card is not shown.
- 0 reconciliations exist in SQL.

Editing the date clears the error. **2026-11-17** then advances to step 2, "Upload Bank Statement".

### b. Draft reconciliation shows both comparisons: PASS (`03`–`07`)
**Statement.** I uploaded `csv/obs646c-DS39J-statement.csv`, FNB format (detected by the FNB header; date,
description, amount, balance, reference):

| # | Date | Amount | Reference | Becomes |
|---|---|---|---|---|
| 1 | 15/11 | +250 000.00 | `DEP/MOROKA/mwkdnayiff1d` | auto-match by exact reference |
| 2 | 16/11 | −8 500.00 | `EFT-OBS646C-DS39J` | 1 day off the fee transfer → stays UNMATCHED, for manual match |
| 3 | 17/11 | −85.50 | `CHG-OBS646C-DS39J` | bank charge, to exclude |

The import gave "Lines imported: 3, Period: 2026-11-15 to 2026-11-17". The closing balance is **241 414.50**.

**Auto-match.** "Auto-matched 1 lines, 2 lines remaining for manual review". Review Matches then created and
calculated the draft, with period end 17 Nov 2026.

The review card is titled **"As at 17 Nov 2026"**. It shows **two comparisons, each with its own Difference**:
- **"Bank and cashbook"** (EQ-1)
- **"Cashbook and trust creditors"** (EQ-2)

It also shows the **"Trust creditors" breakdown**: client ledgers, Fidelity Fund interest and charges, and
unidentified deposits. The investment, inter-account and other-non-client rows are hidden because each is 0.
Below the breakdown are the **unresolved-line count** and the **excluded total** sentence.

**Complete Reconciliation is disabled** in all three states, which all read "Reconciliation is not balanced".

Then, through the UI:
1. I clicked line #2. The fee transfer was highlighted as the candidate, and I clicked **Match**.
2. I clicked line #3 and clicked **Exclude** (reason "Non-trust item").

The figures changed as follows:

### c. Cross-check UI = API = SQL: PASS — every term, every state
Each figure was read three ways:
- **UI:** read from the `dl` rows.
- **API:** `GET /api/trust-reconciliations/{id}`, saved as `obs/api-reconciliation-0N-*.json` (200 each time).
- **SQL:** read-only. It reproduces `TrustLedgerQueryRepository.position` (six terms, posted rows dated
  ≤ 2026-11-17) and `.outstanding` verbatim, together with the statement's `closing_balance`, the signed
  sum of the EXCLUDED lines and the count of UNMATCHED lines.

All 18 terms × 3 states agree (`states[*].allMatch = true`).

| Term | 01 review opened | 02 after manual match | 03 after exclude |
|---|---|---|---|
| Bank balance (statement closing) | 241 414.50 | 241 414.50 | 241 414.50 |
| + Outstanding deposits | 45 000.00 (QC deposit) | 45 000.00 | 45 000.00 |
| − Outstanding payments | 8 500.00 (fee transfer) | **0.00** | 0.00 |
| = Adjusted bank balance | 277 914.50 | **286 414.50** | 286 414.50 |
| Cashbook | 286 500.00 | 286 500.00 | 286 500.00 |
| **EQ-1 difference** (bank − cashbook) | **−8 585.50** | **−85.50** | −85.50 |
| Client ledgers | 286 500.00 | 286 500.00 | 286 500.00 |
| Investment / Fund ledger / Unidentified / Other non-client / Inter-account | 0 / 0 / 0 / 0 / 0 | same | same |
| Trust creditors (total) | 286 500.00 | 286 500.00 | 286 500.00 |
| **EQ-2 difference** (cashbook − creditors) | **0.00** | 0.00 | 0.00 |
| `difference` (adjusted bank − creditors; API/SQL/stored, not shown in UI) | −8 585.50 | −85.50 | −85.50 |
| Excluded lines total (signed) | 0.00 | 0.00 | **−85.50** |
| Unresolved lines | 2 | 1 | **0** |
| Progress | 1 / 3 | 2 / 3 | 3 / 3 |
| isBalanced / Complete | false / disabled | false / disabled | false / disabled |

How to read the table:
- **After the manual match**, the fee transfer leaves the outstanding payments, because it is now linked
  to a line dated 16/11, which is ≤ the period end. EQ-1 moves from −8 585.50 to −85.50.
- **After the exclusion**, EQ-1 stays at −85.50, which is exactly the excluded bank charge. That is what the
  card's own sentence says: "An excluded line adjusts nothing…".

The stored row (`obs-sums.out`, state 03) holds bank 241 414.50, outstanding 45 000/0, adjusted 286 414.50,
cashbook 286 500, client 286 500, investment/fund/unidentified/inter-account 0, `creditor_total` 286 500.00,
**`difference` −85.50**, `is_balanced` f, DRAFT, `integrity_flags` `[]`.

The line links in SQL:
- #1 AUTO_MATCHED ↔ the Moroka DEPOSIT.
- #2 MANUALLY_MATCHED ↔ the FEE_TRANSFER, with a two-way link.
- #3 EXCLUDED, reason "Non-trust item".

### d. Refusal (period end before the statement's last line): PASS — UI-observed (`08`)
Steps:
1. New Reconciliation, with period end **2026-11-14**.
2. Upload `csv/obs646c-DS39J-late.csv`: lines 16/11 +12.34 and 17/11 +7.77. It imports, with period
   2026-11-16 to 2026-11-17.
3. Run Auto-Match: 0 matched.
4. Click Review Matches.

The wizard **stays on step 3** with the red banner: **"This statement has lines dated after 14 November
2026. Choose a later period end, or import a statement that ends on or before it."** The review step is not
reached. The reconciliation count stays at **1**.

A supplementary API probe of the same create returned 400 with title "Statement does not fit the period
end" and the same detail, and created nothing.

Draft b re-read after this import still returns 200 / DRAFT / −85.50. The late statement's lines are not
after b's last line (17/11), so rule 3 does not trip.

### Logs
| Log | Result |
|---|---|
| Backend stack #1 | 326 INFO, 151 WARN, **0 ERROR**. OBS window 07:38:33–07:38:53 UTC: 1 INFO, 0 WARN, 0 ERROR (`obs-window-log-breakdown.txt`) |
| Backend stack #2 | 268 INFO, 199 WARN, **0 ERROR** |

The WARNs on both stacks are known noise (`stack1-log-breakdown.txt`, `stack2-log-breakdown.txt`):
- PDF fallback fonts and the font cache;
- startup bean notices;
- 5 expected 403s on time-summary/by-member (stack #1 only);
- tax-number-missing drafts;
- notification no-recipients;
- LibreOffice absent;
- the `uri` meter-tag cap.

The refusals in a and d are 400s and log nothing at WARN or above.

The frontend on stack #1 logged 27 × `⨯ Error: The destination stream closed early.`, all during the
lifecycle suite. **0 fell in the OBS window.** This is the same navigation-abort noise recorded in 646B (27)
and 646E (33).

Final EQ-2 on stack #1: cashbook 286 500.00 = client ledgers 286 500.00 (`stack1-final-eq2.out`).

## Found items
No new defect in 646C's scope (`found-obs.md`). Two observations:
- **O-1:** the manual-match pane lists only APPROVED transactions, so the RECORDED R 45 000 deposit cannot
  be matched by hand. This was observed, predates 646C, and is **already tracked as 652B**.
- **O-2:** the period-end refusal fires only after the statement has been imported and auto-matched. The
  refused statement stays imported. Auto-match linking lines before the refusal is a **hypothesis, not
  reproduced** (0 links in this run). Suggested owner: 652C, or no action if the order is intended.

## Cleanup
- After each legal run I copied the suite screenshots to `lifecycle-artifacts/` and `depth-ii-artifacts/`,
  then ran `git clean -fd -- documentation/screenshots/legal-vertical` and `git checkout` on it.
- Afterwards `git status --short` was **empty** (`frontend/test-results/` and `.epic-brief.md` are ignored).
  HEAD is 92b4b5550.
- **Stack #2 (depth-ii) is left UP.** Nothing else started by this run is running.
