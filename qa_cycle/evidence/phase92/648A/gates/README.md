# 648A observed gates — PR #1799 @ aba6f46d5 (2026-11-18, UTC)

Every command ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-648A` at HEAD
`aba6f46d5fffa231fe77263b7d15e85ecda904b8`.

**Tree state.**
- Before the run, the tree held only this new `gates/` directory (`tree-before.txt`).
- After the run it held the same (`tree-after.txt`, identical).
- HEAD is unchanged. Nothing was committed or pushed.

| Gate | Result |
|---|---|
| E2E-LEGAL lifecycle | **FAIL**: 93 passed / **2 failed** / 12 did not run (baseline 105/0). Run 1 on fresh stack #1; **re-run on fresh stack #2 gave the same result**, the same 2 tests at the same lines, so this is a defect, not a flake (`found-obs.md` **D-1**) |
| E2E-LEGAL depth-ii | **PASS**: 40 passed / 4 skipped (the baseline skips) / 0 failed, first run, fresh stack #3 |
| OBS a — s86(4) placement leaves the client's total unchanged | **FAIL** (D-1). Placement, approval and figures are correct in SQL and in the matter total (UI = API = SQL). The client's general balance can only be shown in SQL: the client-ledger card API returns 500 and the UI's client line disappears |
| OBS b — s86(3) names no client | **PASS** (UI = API = SQL) |
| OBS c — withdraw the s86(4) investment | **FAIL** (D-1) on the "general balance restored" UI/API leg. Restored in SQL; the card API returns 500 and the UI line is missing. Every other leg PASS: amount shown, date only, both rows RECORDED, WITHDRAWN, books balance in API and SQL |
| OBS d — reversal refused on a placement row | **PASS**: no Reverse control in the UI; API probe (API evidence) 400 "… Withdraw the investment instead of reversing its placement."; nothing created |
| OBS e — close refused for the account holding the s86(3) investment | **PASS** (API evidence; the UI has no close control): 400 "An investment placed from or held in this account has not been withdrawn …"; nothing changed |
| OBS f — no raw enum | **PASS** on every 648A surface. Pre-existing: P-1 (Transactions status words) and P-2 (F-4 `ACTIVE`), recorded as known |
| OBS g — clip probe 1280 / 1440 (1920 reference) | **PASS** for the stacked Add Investment Account dialog (Create Account reachable at 720 px high), both dialogs, the withdraw dialog and the approvals row. **Finding W-1** on the investments register: at 1280 and 1440, Status and Actions sit behind a horizontal scroll |
| OBS h — console and log | 0 page errors. 2 console errors per run, both D-1: the 500 on the matter tab and the 404 client-ledger page document. Backend OBS window: **13 ERROR** (all D-1), 0 WARN |

## Ground rules kept

- **No changes or Maven.** No source edits, no commits, no pushes, no Maven. `pgrep -fl surefire` was empty before each stack and each e2e run.
- **One e2e stack at a time.**
  - `e2e-down.sh` ran before each `e2e-up.sh`.
  - There were three fresh stacks: #1 lifecycle run 1 + OBS, #2 lifecycle re-run, #3 depth-ii.
  - 0 `e2e-*` containers are left.
- **State changed through the UI only.**
  - Alice (owner) placed and withdrew through Trust › Investments. She added both investment accounts through "Add investment account" inside the Place Investment dialog.
  - Bob (admin) approved on Trust › Transactions.
  - The d and e probes are API calls, labelled as API evidence. They were refused and created nothing.
- **Read-only evidence.**
  - psql was used read-only.
  - API reads used the browser context's own `mock-auth-token` against `:8081`.
  - There were no SQL writes.
- **Tooling.**
  - Node was nvm 20.11.1; pnpm was called by absolute path as `/opt/homebrew/bin/pnpm`.
  - The environment was `NODE_OPTIONS=""` and `PLAYWRIGHT_BASE_URL=http://localhost:3001`. Flags were passed without `--`.
  - The Playwright MCP profile was not touched. Every browser run was a headless library script from `frontend/node_modules`.
- **Long runs.** Long commands ran as `nohup` + rc marker + bounded poll. Timings are in `timings.txt`.

## Stack builds and proof (`vproof.sh`)

| Stack | Window (UTC) | Flyway | Proof |
|---|---|---|---|
| #1 lifecycle run 1 + OBS | up 00:06:27, down 00:20 | V23 "phase92 investment accounts" success = t | `stack1-proof.txt` |
| #2 lifecycle re-run | 00:20:06–00:20:43 | same | `stack2-proof.txt` (identical to #1 modulo timestamps) |
| #3 depth-ii | 00:24:10–00:24:45 | same | `stack3-proof.txt` (identical) |

For every stack, the build log reads "Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-648A".

**Backend jar.** `/app/app.jar` contains:
- `InvestmentPlacementHooks.class`, which contains `onPlacementApproving`;
- `TrustInvestmentService` (`onPlacementApproving`, `INV-WD-`);
- `TrustTransactionService` ("Withdraw the investment instead", `onPlacementApproving`);
- `InvestmentRegisterQuery` ("Firm (section 86(3))");
- as its latest tenant migration, `V23__phase92_investment_accounts.sql`, with `chk_trust_invest_client_by_basis`.

**DB.**
- `trust_investments.investment_account_id` is NOT NULL.
- `customer_id`, `project_id` and `deposit_transaction_id` are nullable.
- The CHECK reads `investment_basis IS NOT NULL AND ((… CLIENT_INSTRUCTION … customer_id IS NOT NULL AND project_id IS NOT NULL) OR (… FIRM_DISCRETION … customer_id IS NULL AND project_id IS NULL))`.

**Frontend.** `.next` contains:
- "Add investment account", "Add Investment Account", "Returns to trust" and "Select the investment account" in the investments route chunk and the static chunk `0y6m3uner27n0.js`;
- "Firm (section 86(3))";
- `create-trust-account-dialog`.

**Runtime.** Health is UP and `:3001` returns 200.

## Gate 1 — E2E-LEGAL lifecycle: FAIL (observed twice)

| Run | Stack | Result | Log |
|---|---|---|---|
| 1 | #1 fresh, 00:08:27–00:11:31 | 93 passed, 2 failed, 12 did not run (2.9 min), rc 1 | `lifecycle-run1.log`, `-summary.txt`, `-trust-tests.txt` (48 lines), `-artifacts/` |
| 2 | #2 fresh, 00:20:51–00:23:51 | 93 passed, 2 failed, 12 did not run (3.0 min), rc 1 | `lifecycle-run2.log`, `-summary.txt`, `-trust-tests.txt`, `-artifacts/` |

The per-test outcome diff between run 1 and run 2 is empty.

**Failures (both runs):**
1. `day-60-interest.spec.ts:458` "Bob: Approve the s86(4) placement — QuickCollect's total is unchanged, R30,000 now invested":
   - The approval itself succeeded: the row is APPROVED by Bob Admin, and INV_PLACE / INV_RECEIVE are posted.
   - The matter-total assertion (line 472) passed.
   - Line 473 failed: `GET /api/trust-accounts/{id}/client-ledgers/{QC} → 500`.
   - Backend: `IllegalStateException: INV_PLACE has no client-ledger total yet (648A places it)` from `LedgerTotals.plus`.
   - This is **D-1**.
2. `day-90-review.spec.ts:94` "Fee notes list shows the run's five fee notes": `Run-state value "siphoFeeNote2" was not recorded`. This is a cascade: the serial day-60 chain stopped before 60.21 recorded it.

**Did not run (12):** day-60 :484, :510 and :531; day-90 :117–:268 (9 tests). These include "Trust reports — Investment Register with s86 basis".

**The F-5 fix is confirmed.** Day-60 `:374` passed on both runs: "Alice: Place investment — R200,000 Standard Bank Fixed Deposit (s86(3), names no client)", which uses the stacked "Add Investment Account" dialog.

**Trust and investment lines (run 1; run 2 identical):**
- day-60 :227 interest page ✓
- :237 interest run ✓
- :326 Bob no approve ✓
- :346 no post ✓
- :367 investment register ✓
- **:374 s86(3) placement names no client ✓**
- **:410 Bob approves s86(3), no client's balance moves ✓**
- **:430 s86(4) placement, Awaiting approval ✓**
- **:458 Bob approves s86(4) ✘ (D-1)**
- day-90 :147, :158 and :172 trust reports: not run
- day-90 :312 ✓, :326 R500 fee transfer awaiting approval ✓, :339 ✓, :362 ✓

The full list is in `lifecycle-run1-trust-tests.txt`.

## Gate 3 — E2E-LEGAL depth-ii: PASS (observed)

- **Result:** stack #3 (fresh), 00:24:52–00:26:52. **40 passed / 4 skipped / 0 failed** in 1.9 min, rc 0. `depth-ii-last-run.json` reads `passed`.
- **Skips:** exactly the baseline ones: `day-30-45-soa-writeoff.spec.ts:361` and `null-prefetch-probe.spec.ts:74` ×3.
- **Outputs:** `depth-ii.log`, `depth-ii-summary.txt`, `depth-ii-trust-tests.txt` (15 lines) and `depth-ii-artifacts/` (10 screenshots).
- **Stack #3 backend:** 271 INFO / 199 WARN / **0 ERROR** (`stack3-log-breakdown.txt`).

## Gate 2 — OBS (stack #1, after lifecycle run 1; tag DE43H)

The lifecycle state was suitable:
- Moroka had R241 500 on its matter.
- The funding cashbook was R56 500.

The R30 000 QuickCollect s86(4) placement and the R200 000 s86(3) placement from the lifecycle were already approved.

| Script | Window (UTC) | What |
|---|---|---|
| `obs.cjs` run 1 | 00:15:27–00:16:47, rc 1 | baseline; a; b; c up to "Confirm Withdrawal". Stopped on a harness SQL error (H-1). This is the run of record for a and b and for c's dialog. Outputs in `obs/run1/` and `obs-sums.sql`/`.out` |
| `obs-cont.cjs` | 00:17:31–00:18:01, rc 0 | **no UI state change.** c's post-withdrawal evidence, d, e and the g register probe. Outputs in `obs/` (shots 51–56) and `obs-sums-cont.*` |
| `probe-clip.cjs` | 00:19:16–00:19:36, rc 0 | g: stacked dialog at W×720 (read-only; both dialogs cancelled); client-ledger pages. Output in `obs/probe-clip.json` |

Figures were read with `measure.cjs` (shared bounding-rect measure) and `obs/run1/obs-result.json` / `obs/obs-cont-result.json` (all checks and figures).

### a. Section 86(4): FAIL (D-1). Every other leg PASS

**Place dialog** (`obs/run1/03-a1-*`):
- Basis "Section 86(4) — invested on a client's instruction".
- Help text: "Interest paid to client, with 5% to the LPFF (Section 86(5))".
- The picker listed only the lifecycle's "Nedbank Section 86(4) Investments …".

**"Add investment account"** opened the stacked **"Add Investment Account"** dialog (`04-a2-*`):
- Type "Investment", **disabled**.
- Basis "Section 86(4) — invested on a client's instruction", **disabled**.
- "Set as primary trust account" **unticked**.

Create Account sent one POST. The account was added as `INVESTMENT | CLIENT_INSTRUCTION | is_primary f` (count +1), and the picker then showed "OBS s86(4) Investments DE43H (Nedbank 0864DE43H)".

**Placement:** Client ID = Moroka, Matter ID = Estate Late Peter Moroka, R7 500, 7 %.

**Before Bob approves:**
- DB: `INV_PLACE | AWAITING_APPROVAL | CLIENT | posted f`.
- Register row (`06-a4-*`): "… Section 86(4) · OBS s86(4) Investments DE43H · … R 7 500,00 … Balance R 0,00 · **Awaiting approval**". **0 Withdraw buttons.**
- API: `awaitingApproval true`, `balance 0`.
- Client figures unchanged (SQL general 241 500, total 241 500).
- Matter Trust tab: "R 241 500,00", and the client line still read R 241 500,00 (`07-a5-*`).

**Bob on Transactions** (`09-a6-*`):
- The row reads "Placed in section 86(4) investment" · "**Placed in section 86(4) investment at Nedbank (OBS s86(4) Investments DE43H)**" · R 7 500,00 · AWAITING APPROVAL · Approve / Reject.
- After Approve: APPROVED (`10-a6-*`).
- DB: INV_PLACE APPROVED, CLIENT −7 500 on the trust account; INV_RECEIVE APPROVED, CLIENT_INVESTMENT +7 500 on the OBS s86(4) account; both posted.

| After approval | UI | API | SQL |
|---|---|---|---|
| Matter total (general + invested) | R 241 500,00, **unchanged** (`12-a8-*`) | `/matters/{m}/trust-balance` 241 500, unchanged | 241 500 = CLIENT 234 000 + CLIENT_INVESTMENT 7 500 |
| Client total (all accounts) | — | — | 241 500 → 241 500 (**Δ 0**) |
| Client / matter general balance | **client line missing** (D-1) | **500** on `/client-ledgers/{Moroka}` (D-1) | 241 500 → 234 000 (**Δ −7 500**); matter general Δ −7 500 |
| Invested | register Balance R 7 500,00, Active, Withdraw (`11-a7-*`) | investment `balance 7500`, `ACTIVE` | CLIENT_INVESTMENT +7 500 |
| Cashbooks | — | trust-position: funding 49 000 = creditors 49 000, diff 0; s86(4) account 7 500 = 7 500, diff 0 | funding 56 500 → 49 000 (−7 500); OBS s86(4) account 0 → 7 500 |

### b. Section 86(3): PASS (`obs/run1/14-b1-*` … `20-b7-*`)

**Place dialog:**
- s86(3) radio.
- Help: "Interest on a section 86(3) investment belongs to the Legal Practitioners' Fidelity Fund."
- **Client ID and Matter ID fields: 0 / 0.**

**Stacked dialog:**
- Investment (disabled).
- "Section 86(3) — the firm invests surplus trust money" (disabled).
- Primary unticked.
- The new account is `FIRM_DISCRETION`, not primary, and was selected in the picker.

**Placement:** R2 500.

**Register:**

| When | Row |
|---|---|
| Before approval | "**Firm (section 86(3))** · Section 86(3) · OBS s86(3) Investments DE43H · … Awaiting approval", 0 Withdraw |
| After approval | "Firm (section 86(3)) … Balance R 2 500,00 · Active · Record Interest · Withdraw" |

**Bob saw** "Placed in section 86(3) investment at Standard Bank (OBS s86(3) Investments DE43H)" and approved.

**SQL:**
- The investment has `customer_id IS NULL`, `project_id IS NULL`, and is ACTIVE.
- FIRM_INV_PLACE (trust account, −2 500) and FIRM_INV_RECEIVE (s86(3) account, +2 500) are both APPROVED, posted, on **INTER_ACCOUNT**, with `customer_id NULL`.
- **Every client's general balance string was identical before and after**: no client ledger moved.

**Cashbooks:**
- Funding 49 000 → 46 500.
- s86(3) account 2 500.
- trust-position diff 0 on all three accounts.

### c. Withdraw the s86(4) investment: FAIL (D-1, UI/API leg). Every other leg PASS

**Dialog** (`obs/run1/21-c1-withdraw-dialog-*`):
- It reads "Returns to trust **R 7 500,00**".
- Its only visible input is `withdrawalDate` (type date, default 2026-11-18). There is no amount field, and nothing was typed.
- Confirm Withdrawal.

**SQL:**
- **INV_WITHDRAW | RECORDED | CLIENT_INVESTMENT | OBS s86(4) account | −7 500**, posted.
- **INV_RETURN | RECORDED | CLIENT | trust account | +7 500**, posted.
- The investment is `WITHDRAWN`, withdrawal 2026-11-18, amount 7 500.
- Client general 241 500 (**restored**), invested 0, total 241 500.

**Register:** "… Balance R 0,00 · Withdrawn", 0 Withdraw buttons (`51-c2-*`).

**Books — API trust-position:**

| Account | Cashbook | Creditors | Difference |
|---|---|---|---|
| Funding | 54 000 | 54 000 | 0 |
| OBS s86(4) account | 0 | 0 | 0 |
| OBS s86(3) account | 2 500 | 2 500 | 0 |

**Books — SQL:** for every one of the 5 accounts, sum `cashbook_effect` = sum `ledger_effect` (`obs-sums-cont.out`).

**UI (D-1):**
- The matter tab still shows R 241 500,00, but the client line is missing (`52-c3-*`).
- The client ledger page shows "Something went wrong" (`53-c3-*`).
- The Trust Accounting overview shows "Unable to load trust account data" (`54-c4-*`). So the books balance could not be shown in the UI; it was shown in API and SQL.
- API: the client card returns **500**.

### d. Reversal refused: PASS

**UI** (Alice, Transactions, `55-d1-*`):
- The INV_PLACE (s86(4), withdrawn) and FIRM_INV_PLACE (s86(3), active) rows are present and APPROVED.
- `reversal-action-*` count 0 and Reverse buttons 0 on both.

**API evidence** (`obs/api-d-*.json`): `POST /api/trust-transactions/{id}/reverse` with `{reason, reversalDate}`:
- Both returned **400** "Cannot reverse transaction".
- Detail: "This entry placed a trust investment. **Withdraw the investment instead** of reversing its placement."

trust_transactions count 14 → 14, and `reversed_by_id` stayed NULL on both.

### e. Close refused: PASS

The Settings › Trust accounts card has **no close control** (0).

**API evidence** (`obs/api-e-*.json`): `POST /api/trust-accounts/{OBS s86(3) account}/close` → **400** "Cannot close trust account".
- Detail: "An investment placed from or held in this account has not been withdrawn. Withdraw it before closing the account."
- The account stayed `ACTIVE`, `closed_date` NULL.

### f. No raw enum: PASS for 648A surfaces

Raw underscore tokens were checked on 18 scans (`obs/run1/scan-*`, `obs/scan-*`; substring re-check per H-2). Surfaces:
- the investments page, at awaiting, active, withdrawn and final;
- both Place dialogs, empty and filled;
- both stacked Add Investment Account dialogs;
- the withdraw dialog;
- the approvals rows.

Tokens checked: `FIRM_DISCRETION`, `CLIENT_INSTRUCTION`, `INV_PLACE`, `FIRM_INV_*`, `INV_RECEIVE/WITHDRAW/RETURN`, `AWAITING_APPROVAL`, `CLIENT_INVESTMENT`, `INTER_ACCOUNT`, `INVESTMENT`, `GENERAL`, `S86_*`, `ACTIVE`, `MATURED`, `WITHDRAWN`, `CANCELLED`. **0 hits.** The register shows "Active / Withdrawn / Awaiting approval", "Section 86(3)/(4)" and "Firm (section 86(3))".

Known, recorded separately:
- **P-1:** the Transactions status column shows `RECORDED` / `APPROVED` / `AWAITING APPROVAL`. This is pre-existing; the E2E asserts it.
- **P-2:** the F-4 `ACTIVE` badge ×5 on the Settings card.

### g. Clip probe: PASS, plus W-1

**Stacked "Add Investment Account" at W×720** (`obs/probe-clip.json`; `g-stacked-add-dialog-*x720-top/-scrolled.png`):
- The scroll container is DialogContent's inner div: `overflow-y: auto`, `max-height 648px`, scrollHeight 876 > clientHeight 646.
- The dialog box is 36–684, inside the viewport.
- "Create Account" is at 853–889, below the fold, before scrolling. After scrolling the container, it is at **623–659: inside the viewport and inside the dialog**. Playwright reports it visible.
- No horizontal overflow (510/510).
- Same at 1280, 1440 and 1920. This confirms the F-5 fix.

**Place dialog, filled (both bases), at 720 high:**
- At 1280 the box is 384–896; at 1440 it is 464–976.
- 0 offenders, 0 truncated, no horizontal overflow.

**Withdraw dialog:** 384–896 at 1280, 364 px high, 0 offenders, 0 truncated.

**Approvals row with the long description:**

| Viewport | Table / wrapper width | Description | Actions |
|---|---|---|---|
| 1280 | 926 / 926 | wraps to 4 lines in 147 px (scrollWidth = clientWidth, inside the row) | 1040–1231, inside the viewport |
| 1440 | 1086 / 1086 | 2 lines | inside the viewport |

The type badge wraps and is not clipped.

**W-1, the investments register** (`g-register-table-1280/1440-crop.png`):
- The table is 1344 px wide in an `overflow-x-auto` wrapper of 926 px (1280) or 1086 px (1440).
- At 1280, Balance (cut mid-figure), Status and Actions need a horizontal scroll. At 1440, Actions does, and the "Withdrawn" badge is cut at the edge.
- No cell content is truncated, and the badges are 100/100 etc.
- The card stays inside the viewport.

This is reported as a finding: the Withdraw button and the "Awaiting approval" status are off-screen in the initial view.

### h. Console and logs

**Browser (OBS run 1 and the continuation):**
- 0 page errors.
- Console errors (2 each, both D-1):
  - "Failed to load resource: 500" on the matter Trust tab (the client card fetch);
  - "404" for the client-ledger page document (its error state).
- The probe had 1 console error (the same 404) and 0 page errors.

**Backend, OBS window** (00:15:27–00:19:40): **13 INFO / 0 WARN / 13 ERROR**. Every ERROR is the D-1 `IllegalStateException`.

**Whole stacks:**

| Stack | INFO | WARN | ERROR |
|---|---|---|---|
| #1 | 348 | 133 | 16 |
| #2 | 321 | 133 | 3 |
| #3 | 271 | 199 | 0 |

- All 16 ERRORs on stack #1 are D-1 (`stack1-error-kinds.txt`: 8 `getByCustomer`, 8 `listClientLedgers`), and so are the 3 on stack #2.
- The WARNs are the known PDFBox fallback-font / startup noise (`stack*-log-breakdown.txt`).

**Frontend `⨯`:**
- Stack #1: 24 × "The destination stream closed early." (known) + 2 × `ApiError: An unexpected error occurred` (D-1).
- Stack #3: 1.

## Found items

See `found-obs.md`.
- **D-1 (HIGH, defect):** client-ledger card and list return 500 after any approved s86(4) placement. It fails the lifecycle E2E and OBS a/c, and breaks the Client Ledgers pages, the Trust Accounting overview and the matter tab's client line. It also corrects `found.md` F-1.
- **W-1 (MEDIUM, layout):** register Status / Actions sit behind a horizontal scroll at 1280 and 1440.
- **L-1 (LOW):** the two placement type labels differ ("… investment" vs "… investment account").
- **P-1, P-2, P-3:** pre-existing or known.
- **H-1, H-2:** harness notes.
- **E-1:** the 404 explained.

## Cleanup

- After each legal run I copied the curated screenshots into `lifecycle-run1-artifacts/curated`, `lifecycle-run2-artifacts/curated` and `depth-ii-artifacts`. I then ran `git clean -fdq -- documentation/screenshots/legal-vertical`.
- `git diff --stat` is empty. `frontend/test-results/` is gitignored.
- All three stacks were torn down (`stack1/2/3-down.log`). 0 `e2e-*` containers are running.
- `git status --short` shows only `?? qa_cycle/evidence/phase92/648A/gates/`, and HEAD is aba6f46d5. Nothing was committed.
