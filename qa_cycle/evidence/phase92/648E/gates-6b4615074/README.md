# 648E observed gates: PR #1800 @ 6b4615074 (2026-11-18, UTC)

Every command ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-648E` at HEAD
`6b461507450e1b77686a9784ba03bc1d8e2595f4` (Phase 92 slice 648E — invested balance on firm read surfaces).
The format follows `648A/gates-b6afe8c69/README.md`.

**Tree state.**
- Before the run, the tree held only the untracked `gates-6b4615074/` directory (`tree-before.txt`).
- After the run it held the same (`tree-after.txt`; `diff` reports them identical).
- `git diff --stat` is empty. HEAD is unchanged (`head-before.txt`). Nothing was committed or pushed.

| Gate | Result |
|---|---|
| E2E-LEGAL lifecycle | **PASS**: **107 passed / 0 failed / 0 skipped** in 3.2 min, rc 0, first run, fresh stack #1. `day-60-interest.spec.ts:476` "Bob: Approve the s86(4) placement — QuickCollect's total is unchanged, R30,000 now invested" (the test 648E extended with the card `investedBalance` and the client-ledger page's Invested / Total / General figures) ran and passed (1.3 s). No re-run was needed |
| E2E-LEGAL depth-ii | **PASS**: **40 passed / 4 skipped** (the baseline skips) / 0 failed in 2.0 min, rc 0, fresh stack #2 |
| OBS a — QuickCollect after the lifecycle (s86(4) R30 000 approved on day 60) | **PASS** (UI = API = SQL). Page: "Placed in section 86(4) investments R 30 000,00", "General balance R 15 000,00", "Invested (section 86(4)) R 30 000,00", "Total held in trust R 45 000,00". API card: `balance` 15000, `investedBalance` 30000, `totalInvestmentPlacements` 30000, `totalTransfersOut` **0** (the placement is not in it). SQL: general 15 000, invested 30 000 |
| OBS b — Moroka s86(4) R7 500: pending → approved → withdrawn | **PASS** (UI = API = SQL in every state). Pending: unchanged, invested 0. Approved: general 241 500 → 234 000, invested 0 → 7 500, total 241 500 unchanged. Withdrawn: invested and held rows hidden, "Returned from section 86(4) investments R 7 500,00", "Balance R 241 500,00" |
| OBS c — Moroka's s86(3) placement names no client | **PASS**. Moroka's card `investedBalance` 0, `totalInvestmentPlacements` 0. No invested or held rows on the page or the Trust tab. The lifecycle's R200 000 s86(3) rows are `FIRM_INV_PLACE` / `FIRM_INV_RECEIVE` on `INTER_ACCOUNT` with `customer_id` NULL |
| OBS d — cards on the INVESTMENT account | **PASS (API evidence; the UI does not expose this account's client ledgers)**. `GET /api/trust-accounts/{Nedbank s86(4)}/client-ledgers/{QuickCollect}` and `…/{Moroka}` → 200, `balance` 0, `investedBalance` 0 (synthetic empty card). The list on that account is empty |
| OBS e — MCP | **Not browser-observable.** Covered by `CatalogueBatch2TrustBillingTest` (backend verify green): 13/13 in the worktree's surefire report, including `getTrustBalanceWithAClientCarriesTheirSection86_4InvestedBalance` and `listTrustTransactionsShowsTheClientsSection86_4InvestmentRows`. The MCP OAuth path was not attempted |
| OBS f — client Trust tab (`TrustBalanceCard`, client mode) | **PASS** (the breakdown matches the page row for row in all 5 states). **F-4 recorded (not judged):** the headline reads the general balance. QuickCollect shows **R 15 000,00 "Funds Held"** above "Total held in trust R 45 000,00", and Moroka (approved) shows R 234 000,00 above R 241 500,00 |
| OBS g — no raw enum | **PASS**: 10 substring scans of `textContent` (page `main` + Trust-tab card, 5 states). 0 hits for `CLIENT_INVESTMENT`, `INV_PLACE`, `INV_RETURN`, `INV_RECEIVE`, `INV_WITHDRAW` |
| OBS h — clip probe 1280 / 1440 (1920 reference) | **PASS**: 9 roots × 3 widths. Roots: `client-summary`, the page breakdown and the Trust-tab card; states: QuickCollect, Moroka approved and Moroka withdrawn. 0 offenders, 0 truncated, no horizontal overflow (root or page), every figure inside the viewport |
| OBS i — console and log | **PASS**: 0 console errors, 0 page errors. Backend OBS window (05:00:23–05:01:32): 4 INFO / 0 WARN / **0 ERROR**. Lifecycle window: 0 ERROR. Whole stack #1 and whole stack #2: 0 ERROR |

## Ground rules kept

- **No changes or Maven.** No source edits, no commits, no pushes, no Maven. `pgrep -f surefire` was empty before each stack and each e2e run (`surefire-empty-check … 0` in `timings.txt`).
- **One e2e stack at a time.**
  - `e2e-down.sh` ran before each `e2e-up.sh` (`stack.sh`).
  - There were two fresh stacks: #1 for lifecycle + OBS, #2 for depth-ii.
  - At the end, **0 `e2e-*` containers** are left.
- **State changed through the UI only.**
  - Alice (owner) placed the R7 500 s86(4) investment on Trust › Investments. It went into the lifecycle's existing "Nedbank Section 86(4) Investments" account, picked in the dialog. Alice also withdrew it.
  - Bob (admin) approved on Trust › Transactions.
  - There were no API writes; every API call was a GET.
- **Read-only evidence.**
  - psql was used read-only (`obs-sums.sql` / `.out`). The columns are qualified (648A H-1). Invested is derived with the brief §8.4 query.
  - API reads used the browser context's own `mock-auth-token` against `:8081` (`obs/api-*.json`).
- **Tooling.**
  - Node was nvm 20.11.1; pnpm was called by absolute path as `/opt/homebrew/bin/pnpm`.
  - The environment was `NODE_OPTIONS=""` and `PLAYWRIGHT_BASE_URL=http://localhost:3001`. Flags were passed without `--` (`run-suite.sh`).
  - The Playwright MCP tools and profile were not touched. OBS was a headless library script (`obs.cjs`) from `frontend/node_modules/@playwright/test`.
- **Long runs.** The stack builds and both suites ran as `nohup` + rc marker + bounded poll.
- **Tree restore.** Each legal run left untracked PNGs in `documentation/screenshots/legal-vertical/` (`*-tree-pre/post.txt`). The lifecycle left 16 and depth-ii left 10. They were copied to `lifecycle-run1-artifacts/curated/` and `depth-ii-artifacts/`, then removed with `git clean -fdq -- documentation/screenshots/legal-vertical`. No tracked file was modified.

## Stack builds and proof (`vproof.sh`)

| Stack | Window (UTC) | Flyway | Proof |
|---|---|---|---|
| #1 lifecycle + OBS | up 04:54:48–04:56:21, down 05:02:26 | V23 "phase92 investment accounts" success = t | `stack1-proof.txt` |
| #2 depth-ii | up 05:02:27–05:03:04, down 05:05:42 | same | `stack2-proof.txt` (identical to #1 modulo timestamps / schema name) |

For both stacks, the build log reads "Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-648E".

**Backend jar** (`/app/app.jar`):
- `LedgerTotals.class` contains `withInvestmentPlacements` (1), `investmentPlacements` (2) and `investmentReturns` (2).
- `ClientLedgerService.class` contains `investedBalance` (2). `ClientLedgerService$ClientLedgerCardResponse.class` contains `investedBalance`, `totalInvestmentPlacements` and `totalInvestmentReturns`.
- `TrustLedgerQueryRepository.class` contains `investedBalancesByClient`.
- `InvestmentRegisterQuery.class` contains `investmentBalances`, and `TrustTools.class` contains `investedMinor`.
- The latest tenant migration is V23. 648E adds none.

**Frontend `.next`:** "Invested (section 86(4))", "Total held in trust", "Placed in section 86(4) investments", "Returned from section 86(4) investments", "General balance" and `ledger-total-held` are present in the server chunks and in static `3bj93tsbrcba2.js`. `client-summary-invested` is present in the client-ledger page's server chunk.

**Runtime.** Health is UP and `:3001` returns 200.

## Gate 1 — E2E-LEGAL lifecycle: PASS (observed)

**Result.** Stack #1 (fresh), 04:56:49–05:00:04. `pnpm --dir frontend run test:e2e:legal-lifecycle --reporter=list` gave **107 passed (3.2 min), rc 0**. `lifecycle-run1-last-run.json` reads `{"status":"passed","failedTests":[]}`.

**Outputs:** `lifecycle-run1.log`, `-summary.txt` (107 ✓ lines), `-trust-tests.txt` (48 lines) and `-artifacts/curated/` (16 PNGs).

**The 648E test:** line 72 of the summary is ✓ `day-60-interest.spec.ts:476:7` › "Bob: Approve the s86(4) placement — QuickCollect's total is unchanged, R30,000 now invested".

**Backend, lifecycle window:** 142 INFO / 105 WARN / **0 ERROR** (`stack1-log-breakdown.txt`). The WARNs are PDFBox fallback-font lines plus known noise: by-member 403 ×5, a draft invoice without a tax number ×2, no recipients for DOCUMENT_GENERATED, and the uri-tag meter. This is the same profile as the 648A re-gate (140 / 105 / 0).

## Gate 2 — E2E-LEGAL depth-ii: PASS (observed)

**Result.** Stack #2 (fresh), 05:03:20–05:05:21. **40 passed / 4 skipped / 0 failed** (2.0 min), rc 0. `depth-ii-last-run.json` reads `passed`.

**Skips:** exactly the baseline ones: `day-30-45-soa-writeoff.spec.ts:361` and `null-prefetch-probe.spec.ts:74` ×3.

**Outputs:** `depth-ii.log`, `depth-ii-summary.txt`, `depth-ii-trust-tests.txt` and `depth-ii-artifacts/` (10 PNGs).

**Backend:** depth-ii window 86 INFO / 153 WARN / **0 ERROR**. Whole stack #2: 272 INFO / 199 WARN / **0 ERROR** (`stack2-log-breakdown.txt`).

## Gate 3 — OBS (stack #1, after the lifecycle run; run tag NKJHH)

**Run:** `obs.cjs`, 05:00:23–05:01:31, rc 0.
- Results are in `obs/obs-result.json`, which holds every figure and check, and in `obs-run.txt` (the summary).
- SQL is in `obs-sums.sql` / `.out`.
- Screenshots are `obs/NN-*` (full page at 1440 + 1280, with a crop of each card) and `obs/h-*` (clip crops at 1280 / 1440 / 1920). Page text is in `obs/NN-*.txt`, and the scanned `textContent` is in `obs/scan-*.txt`.

**State used (from the lifecycle):**
- Funding account "Mathebula & Partners Trust Account" (primary GENERAL).
- QuickCollect Services: s86(4) CALL-1122334455, R30 000, ACTIVE, held in "Nedbank Section 86(4) Investments".
- Moroka Family Trust: matter "Estate Late Peter Moroka", R241 500 general.
- The firm's s86(3) FD-0987654321, R200 000, no client, held in "Standard Bank Section 86(3) Investments".

Every state below also passed these checks (`agree()` in `obs.cjs`):
- the card's identity (deposits + transfers in + returns − payments − fee transfers − refunds − transfers out − placements = balance);
- the list row equals the card;
- `investedBalance` = Σ `balance` of the client's s86(4) investments placed from the account (AC-4, via `GET /api/trust-accounts/{G}/investments`);
- the Trust-tab breakdown equals the page breakdown.

### a. QuickCollect after the lifecycle: PASS

| Figure | UI (client-ledger page) | API card | SQL |
|---|---|---|---|
| Deposits | R 45 000,00 | `totalDeposits` 45000 | DEPOSIT Σ amount 45 000 |
| Placed in section 86(4) investments | "− less Placed in section 86(4) investments R 30 000,00" | `totalInvestmentPlacements` 30000 | INV_PLACE Σ amount 30 000 (ledger_effect −30 000) |
| Transfers out | row hidden | `totalTransfersOut` **0** | no TRANSFER_OUT rows |
| General balance | "= General balance R 15 000,00"; summary figure "Trust Balance R 15 000,00" | `balance` 15000 | CLIENT Σ on G = 15 000 |
| Invested (section 86(4)) | "+ Invested (section 86(4)) R 30 000,00" | `investedBalance` 30000 | CLIENT_INVESTMENT Σ for investments placed from G = 30 000 |
| Total held in trust | "= Total held in trust R 45 000,00"; summary line "Plus R 30 000,00 invested (section 86(4)) — R 45 000,00 held in trust in total" | 15000 + 30000 | 45 000 |

The observed figures equal the brief's worked case 2 (15 000 / 30 000 / 45 000 / 30 000). Σ investment balance from the API is 30 000, from one ACTIVE investment. Screenshots: `01-a-*`, `02-a-*`.

### b. Moroka: fresh s86(4) R7 500 (OBS4E-NKJHH): PASS

**Placement:**
- Alice opened the Place Investment dialog with basis s86(4).
- She picked "Nedbank Section 86(4) Investments" and entered the Client and Matter IDs, R7 500 at 7 % (`05-b1-*`).
- The DB row is `INV_PLACE | AWAITING_APPROVAL | CLIENT | posted f`.

**Bob:** the Transactions row read "Placed in section 86(4) investment at Nedbank (Nedbank Section 86(4) Investments) R 7 500,00 AWAITING APPROVAL". Approve → APPROVED (`08-b3-*`).

**Withdrawal:** Alice opened the Withdraw dialog. It read "R 7 500,00" (`10-b5-*`). Confirm → INV_WITHDRAW −7 500 on the s86(4) account and INV_RETURN +7 500 on G, both RECORDED and posted.

| State | General (UI = API = SQL) | Invested | Total held | Placements / Returns | Page |
|---|---|---|---|---|---|
| baseline (= c) | 241 500 | 0 | 241 500 | 0 / 0 | "= Balance R 241 500,00"; no invested rows (`03-c-*`) |
| pending | 241 500 | 0 | 241 500 | 0 / 0 | summary text identical to the baseline (`06-b2-*`) |
| approved | **234 000** (−7 500) | **7 500** | **241 500 (unchanged)** | 7 500 / 0 | "Placed in section 86(4) investments R 7 500,00", "General balance R 234 000,00", "Invested (section 86(4)) R 7 500,00", "Total held in trust R 241 500,00"; summary line "Plus R 7 500,00 invested (section 86(4)) — R 241 500,00 held in trust in total" (`09-b4-*`) |
| withdrawn | 241 500 | 0 | 241 500 | 7 500 / 7 500 | invested, held and summary line gone; "+ Returned from section 86(4) investments R 7 500,00", "− Placed in section 86(4) investments R 7 500,00", "= Balance R 241 500,00" (`11-b6-*`) |

**Approved state, other checks:**
- `totalTransfersOut` was unchanged (0).
- QuickCollect's card was unchanged (15 000 / 30 000). Both investments sit on the same s86(4) account, so the per-client filter holds.

All 19 `b.checks` are true.

### c. Moroka baseline: the s86(3) placement names no client: PASS

- Moroka's card has `investedBalance` 0 and `totalInvestmentPlacements` 0.
- The page and the Trust tab have no invested or held rows, and the label reads "Balance".
- SQL: the lifecycle's s86(3) FD-0987654321 has `customer_id` NULL, and its rows `FIRM_INV_PLACE` −200 000 / `FIRM_INV_RECEIVE` +200 000 are `INTER_ACCOUNT` with `customer_id` NULL.

### d. Cards on the investment account: PASS (API evidence)

**The UI has no route to these ledgers.** The client-ledgers list and the detail page both resolve `accounts.find(a => a.isPrimary) ?? accounts[0]`, and there is no account picker. So these are API reads (`obs/api-d-*.json`):
- `GET /api/trust-accounts/{Nedbank s86(4)}/client-ledgers/{QuickCollect}` → 200, `balance` 0, `investedBalance` 0.
- The same call for Moroka → 200, 0 / 0.
- `…/client-ledgers` on that account → 200, empty list.

This is the brief's case 4: nothing is placed *from* an investment account. Separately, SQL puts QuickCollect's CLIENT_INVESTMENT on that account at 30 000; that figure is shown on the banking account's card, not here.

### e. MCP: not browser-observable

This is covered by `CatalogueBatch2TrustBillingTest` in the backend verify. The worktree's `backend/target/surefire-reports/TEST-…CatalogueBatch2TrustBillingTest.xml` reports tests=13, failures=0, errors=0, skipped=0. The relevant cases are `getTrustBalanceWithAClientCarriesTheirSection86_4InvestedBalance` and `listTrustTransactionsShowsTheClientsSection86_4InvestmentRows`. The MCP OAuth path was not attempted.

### f. Client Trust tab: PASS, with F-4 recorded

**Breakdown:** `/clients/{id}?tab=trust` shows the same `LedgerTotalsBreakdown` rows as the page in all 5 states (`tabBreakdownEqPage` true): placements, General balance, Invested, Total held in trust, and Returned after the withdrawal (`02-a-*`, `10-b4-*`, `12-b6-*`).

**F-4 (from `../found.md`; recorded, not judged):** the headline and badge read the general balance.

| Client / state | Headline | Badge | Total held in trust (breakdown) |
|---|---|---|---|
| QuickCollect | R 15 000,00 | "Funds Held" | R 45 000,00 |
| Moroka, approved | R 234 000,00 | "Funds Held" | R 241 500,00 |

The "No Funds" case named in F-4 (the whole balance invested) did not arise on this data.

### g. No raw enum: PASS

There were 10 scans: the client-ledger page `main` (including the history table and type filters) and the Trust-tab card, each in 5 states (QuickCollect, then Moroka baseline, pending, approved and withdrawn). Each was a case-sensitive substring search of `textContent` for `CLIENT_INVESTMENT`, `INV_PLACE`, `INV_RETURN`, `INV_RECEIVE` and `INV_WITHDRAW`. There were **0 hits** (`obs/scan-*.txt`).

### h. Clip probe: PASS

This used the `measure.cjs` `measureIn` pattern (copied from 648A), plus a page-level `scrollWidth` check and a per-figure check of `dd`, the summary line and the headline.

| Root | States | 1280 | 1440 | 1920 |
|---|---|---|---|---|
| `client-summary` | QuickCollect, Moroka approved, Moroka withdrawn | ok | ok | ok |
| page `ledger-totals-breakdown` | same | ok | ok | ok |
| Trust-tab `trust-balance-card` | same | ok | ok | ok |

In every case there were 0 offenders, 0 truncated elements, no horizontal overflow of the root or the page, and every figure was inside the viewport. Crops are in `obs/h-*-{1280,1440,1920}-crop.png`. At 1280 the summary line wraps to 2 lines inside its card (`h-b4-client-summary-moroka-approved-1280-crop.png`).

### i. Console and logs: PASS

**Browser:** `obs.cjs` had **0 console errors and 0 page errors**.

**Backend, OBS window (05:00:23–05:01:32):** 4 INFO / 0 WARN / **0 ERROR**.

**Whole stacks:**

| Stack | INFO | WARN | ERROR |
|---|---|---|---|
| #1 | 333 | 151 | **0** |
| #2 | 272 | 199 | **0** |

**Frontend `⨯`:**
- Stack #1: 22 × "The destination stream closed early." (known).
- Stack #2: 1.

## Found items

**No new defect** was observed, so there is no `found-obs.md`.
- F-4 (`../found.md`, LOW, trust UI owner) was observed as described: the client Trust tab's headline and badge show the general balance.
- In the lifecycle data the whole balance is never invested, so the "No Funds" variant was not reproduced.

## Timings (`timings.txt`)

| Step | UTC |
|---|---|
| stack #1 up | 04:54:48–04:56:21 |
| lifecycle | 04:56:49–05:00:04 |
| OBS | 05:00:23–05:01:31 |
| stack #1 down | 05:02:26 |
| stack #2 up | 05:02:27–05:03:04 |
| depth-ii | 05:03:20–05:05:21 |
| stack #2 down | 05:05:42 |

## Cleanup

- Both stacks were torn down (`stack1-down.log`, `stack2-down.log`, rc 0). `docker ps -a` shows **0 `e2e-*` containers**.
- `git status --short` matches `tree-before.txt` exactly: only `?? qa_cycle/evidence/phase92/648E/gates-6b4615074/`.
- `git diff --stat` is empty. HEAD is 6b4615074.
