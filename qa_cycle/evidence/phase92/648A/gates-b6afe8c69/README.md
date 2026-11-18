# 648A observed gates: PR #1799 @ b6afe8c69 (2026-11-18, UTC)

Every command ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-648A` at HEAD
`b6afe8c691298693d0e9ffdeeb62e65588c520a8`. This re-runs the gates after the D-1 / W-1 / L-1 fixes (3a467b67b) and the later commits b97db6278, 463e1c64a and b6afe8c69. The earlier run, on aba6f46d5, is in `../gates/` and was not modified.

**Tree state.**
- Before the run, the tree held only the untracked `gates/` and this `gates-b6afe8c69/` directory (`tree-before.txt`).
- After the run it held the same (`tree-after.txt`; `diff` reports them identical).
- `git diff --stat` is empty. HEAD is unchanged. Nothing was committed or pushed.

| Gate | Result |
|---|---|
| E2E-LEGAL lifecycle | **PASS**: **107 passed / 0 failed / 0 skipped** in 3.2 min, rc 0, first run, fresh stack #1. `day-60-interest.spec.ts:458` (the D-1 test) passes; `day-90-review.spec.ts:94/147/158/172` run and pass (`:158` is "Trust reports — Investment Register with s86 basis"). No re-run was needed. See the note on 105 vs 107 below |
| E2E-LEGAL depth-ii | **PASS**: **40 passed / 4 skipped** (the baseline skips) / 0 failed in 2.0 min, rc 0, fresh stack #2 |
| OBS a — s86(4) placement leaves the client's total unchanged | **PASS** (UI = API = SQL; **D-1 fixed**): client card API 200, client line on the matter tab and the client-ledger page both render; general 241 500 → 234 000 (−7 500), invested +7 500, total and matter total 241 500 unchanged |
| OBS b — s86(3) names no client | **PASS** (UI = API = SQL) |
| OBS c — withdraw the s86(4) investment | **PASS**. The dialog shows the amount (R 7 500,00), and the date is the only input. The date input has `min` = deposit date and `max` = today; this was also shown with a past-dated placement, where min 2026-11-10 ≠ max 2026-11-18. A date before the deposit was refused, with no POST. After the withdrawal the general balance was restored to 241 500 in UI = API = SQL |
| OBS d — reversal refused on placement rows | **PASS**: no Reverse control in the UI. API evidence: both probes returned 400 "… Withdraw the investment instead of reversing its placement." and created nothing |
| OBS e — close refused for an account holding an investment | **PASS** (API evidence): 400 "An investment placed from or held in this account has not been withdrawn …". The account stayed ACTIVE |
| OBS f — no raw enum on 648A surfaces | **PASS**. Pre-existing and known: P-1 (Transactions status words) and P-2 (F-4 `ACTIVE`). **New, on a client document:** "Inv place" / "Inv return" in the Client Trust Statement (`found-obs.md` **N-1**) |
| OBS g — clip probe 1280 / 1440 (1920 reference) | **PASS (W-1 fixed)**. The register is 926/926 px at 1280, 1086/1086 at 1440 and 1182/1182 at 1920. There is no horizontal scroll, and every Status badge and every action button is inside the wrapper and the viewport, in all six register states probed. Both dialogs, the withdraw dialog and the approvals row are clean |
| OBS h — console and log | **PASS**: 0 page errors, 0 console errors (OBS and extra). Backend OBS window: **0 ERROR** / 17 WARN (16 PDFBox fallback-font + 1 "No recipients found for DOCUMENT_GENERATED"). The lifecycle window and both whole stacks: 0 ERROR |
| OBS i — future deposit date | **PASS**. The Deposit Date input has `max` = SA today (2026-11-18) on both bases. A typed 2026-11-19 is blocked in the UI: rangeOverflow, the dialog stays open, no POST is sent, nothing is created. API evidence: s86(4) and s86(3) POSTs each returned **400** "Cannot place investment" / "The deposit date cannot be in the future."; investments 4 → 4, transactions 14 → 14 |
| OBS j — approval notification wording | **PASS**. Bob's bell, the notifications page and the DB all read "**Trust placement in a section 86(4) investment requires approval**", with body "A placement in a section 86(4) investment of R7500 requires your approval." There is no "placed in" wording. Copy notes: N-2 (s86(3) noun) and P-4 (amount format) |
| OBS k — statement names the funding account | **PASS**. After the placement, the Client Trust Statement preview reads "Account: **Mathebula & Partners Trust Account**" (the funding account), with closing R234 000,00 = SQL general. The SoA (Preview & Save) shows the trust activity on the funding account, "Trust balance held: R234 000,00". After the withdrawal it is the same account. The backend logged 0 "has activity on N trust accounts" lines. Copy finding N-1; observation O-1 |

**Lifecycle count, 105 vs 107.** The brief gives the lifecycle baseline as 105/105. The suite at this head has **107** tests, the same total as the previous gate run on aba6f46d5 (93 passed + 2 failed + 12 did not run = 107). All 107 passed: `lifecycle-run1-last-run.json` reads `passed`, `failedTests []`.

## Ground rules kept

- **No changes or Maven.** No source edits, no commits, no pushes, no Maven. `pgrep -fl surefire` was empty before each stack and each e2e run.
- **One e2e stack at a time.**
  - `e2e-down.sh` ran before each `e2e-up.sh`.
  - There were two fresh stacks: #1 for lifecycle + OBS, #2 for depth-ii.
  - At the end, **0 `e2e-*` containers** are left (`docker ps -a`).
- **State changed through the UI only.**
  - Alice (owner) placed, withdrew and generated the statements.
  - Bob (admin) approved on Trust › Transactions.
  - The d, e and i probes are API calls, labelled as API evidence in `obs/api-*.json`. They were refused and created nothing.
- **Read-only evidence.** psql was used read-only (`obs-sums.sql` / `.out`). API reads used the browser context's own `mock-auth-token` against `:8081`.
- **Tooling.**
  - Node was nvm 20.11.1; pnpm was called by absolute path as `/opt/homebrew/bin/pnpm`.
  - The environment was `NODE_OPTIONS=""` and `PLAYWRIGHT_BASE_URL=http://localhost:3001`. Flags were passed without `--` (`run-suite.sh`).
  - The Playwright MCP profile was not touched. Every browser run was a headless library script from `frontend/node_modules`.
- **Long runs.** Long commands ran as `nohup` + rc marker + bounded poll. Timings are in `timings.txt`.
- **Tree restore.** Each legal run left untracked PNGs in `documentation/screenshots/legal-vertical/` (`*-tree-pre/post.txt`). They were copied to `lifecycle-run1-artifacts/curated/` and `depth-ii-artifacts/`, then removed with `git clean -fdq -- documentation/screenshots/legal-vertical`. No tracked file was modified.

## Stack builds and proof (`vproof.sh`)

| Stack | Window (UTC) | Flyway | Proof |
|---|---|---|---|
| #1 lifecycle + OBS | up 02:36:24–02:37:57, down 02:47:32 | V23 "phase92 investment accounts" success = t | `stack1-proof.txt` |
| #2 depth-ii | up 02:47:32–02:48:08, down 02:50:48 | same | `stack2-proof.txt` (identical to #1 modulo timestamps) |

For both stacks, the build log reads "Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-648A".

**Backend jar** (`/app/app.jar`):
- `TrustAmounts.class` is present (new in 463e1c64a) and contains "more than 2 decimal places".
- `TrustTransactionTypeLabels.class` contains `placementNoun` and "placement in a section 86(4) investment". `TrustNotificationHandler.class` references `placementNoun`.
- `LedgerTotals.class` contains "has no client-ledger total yet" **0** times. This was the D-1 throw.
- `TrustInvestmentService.class` contains "The deposit date cannot be in the future.", `onPlacementApproving` and `INV-WD-`.
- `TrustTransactionService.class` contains "Withdraw the investment instead".
- `InvestmentRegisterQuery.class` contains "Firm (section 86(3))".
- The latest tenant migration is `V23__phase92_investment_accounts.sql`, with `chk_trust_invest_client_by_basis`.

**Frontend `.next`:**
- "The withdrawal date cannot be before the investment's deposit date." is in the investments route chunk and in static `3d_wqt_qgopgl.js`.
- "The withdrawal date cannot be in the future." and "The deposit date cannot be in the future." are in the schemas chunks.
- `investment-client-cell` and "LPFF share" are present (W-1 layout).
- "Add investment account", "Firm (section 86(3))", "Returns to trust" and `create-trust-account-dialog` are present.

**DB.**
- `trust_investments.investment_account_id` is NOT NULL.
- `customer_id`, `project_id` and `deposit_transaction_id` are nullable.

**Runtime.** Health is UP and `:3001` returns 200.

## Gate 1 — E2E-LEGAL lifecycle: PASS (observed)

**Result.** Stack #1 (fresh), 02:39:15–02:42:27. `pnpm --dir frontend run test:e2e:legal-lifecycle --reporter=list` gave **107 passed (3.2 min), rc 0**. `lifecycle-run1-last-run.json` reads `{"status":"passed","failedTests":[]}`.

**Outputs:** `lifecycle-run1.log`, `-summary.txt` (107 ✓ lines), `-trust-tests.txt` (48 lines) and `-artifacts/curated/` (16 PNGs).

**Trust and investment lines, all ✓:**
- day-60 :227 interest page; :237 interest run; :326 Bob no approve; :346 no post; :367 investment register.
- **:374 s86(3) placement names no client.**
- **:410 Bob approves s86(3), no client's balance moves.**
- **:430 s86(4) placement.**
- **:458 Bob approves s86(4): QuickCollect's total unchanged, R30 000 invested.** This is the D-1 test that failed twice on aba6f46d5.
- :484, :510 and :531 (did not run before).
- **day-90 :94 fee notes list** (the D-1 cascade before).
- :117 Section 35.
- **:147 Client Trust Balances, :158 Investment Register with s86 basis, :172 Receipts & Payments.**
- :312, :326 (R500 fee transfer awaiting approval), :339, :362.

**Backend, lifecycle window:** 140 INFO / 105 WARN / **0 ERROR** (`stack1-log-breakdown.txt`). The WARNs are PDFBox fallback-font lines plus known noise: by-member 403 ×5, a draft invoice without a tax number ×2, no recipients for DOCUMENT_GENERATED, and the uri-tag meter.

## Gate 2 — E2E-LEGAL depth-ii: PASS (observed)

**Result.** Stack #2 (fresh), 02:48:23–02:50:25. **40 passed / 4 skipped / 0 failed** (2.0 min), rc 0. `depth-ii-last-run.json` reads `passed`.

**Skips:** exactly the baseline ones: `day-30-45-soa-writeoff.spec.ts:361` and `null-prefetch-probe.spec.ts:74` ×3.

**Outputs:** `depth-ii.log`, `depth-ii-summary.txt`, `depth-ii-trust-tests.txt` and `depth-ii-artifacts/` (10 PNGs).

**Backend:** depth-ii window 86 INFO / 153 WARN / **0 ERROR**. Whole stack #2: 272 INFO / 199 WARN / **0 ERROR**.

## Gate 3 — OBS (stack #1, after the lifecycle run; tag IOR6X)

| Script | Window (UTC) | What |
|---|---|---|
| `obs.cjs` | 02:43:42–02:45:47, rc 0 | baseline; a (with i-UI and j); k; b (with i-UI); c; d; e; g; i-API. Results in `obs/obs-result.json` (all checks and figures) and `obs-run.txt` (summary). SQL in `obs-sums.sql` / `.out`. Screenshots `obs/01-…33-*` and `obs/g-register-*` |
| `obs-extra.cjs` | 02:47:02–02:47:12, rc 0 | c2: a past-dated (2026-11-10) R1 000 s86(3) placement through the UI, approved by Bob, to separate the withdraw-date `min` from `max`; the dialog was cancelled, so there was no withdrawal. k2: Client Trust Statement preview after the withdrawal (preview only). Results in `obs/obs-extra-result.json` and `obs/x1–x3-*` |

**Lifecycle state used:**
- Client Moroka Family Trust (highest general balance on the primary funding account, R241 500) on matter "Estate Late Peter Moroka".
- Funding account "Mathebula & Partners Trust Account".
- The lifecycle's QuickCollect R30 000 s86(4) and R200 000 s86(3) placements were already approved.

### a. Section 86(4): PASS (D-1 fixed)

**Place dialog** (`obs/03-a1-*`):
- Basis s86(4); help text "Interest paid to client, with 5% to the LPFF (Section 86(5))".
- "Add investment account" opened the stacked "Add Investment Account" dialog (`04-a2-*`): Investment and s86(4) basis, both disabled; primary unticked.
- Create Account added `INVESTMENT | CLIENT_INSTRUCTION | is_primary f` (count +1), and the picker selected it.

**Placement:** R7 500 at 7 % (`05-a3-*`).

**Pending:**
- DB: `INV_PLACE | AWAITING_APPROVAL | CLIENT | posted f`.
- Register (`07-a4-*`): "… Section 86(4) · OBS s86(4) Investments IOR6X · … R 7 500,00 … R 0,00 · **Awaiting approval**", 0 Withdraw.
- Client figures unchanged.

**Bob:** the row reads "Placed in section 86(4) investment at Nedbank (OBS s86(4) Investments IOR6X)" · R 7 500,00 · AWAITING APPROVAL (`12-a6-*`). Approve → APPROVED (`13-a6-*`).

| After approval | UI | API | SQL |
|---|---|---|---|
| Matter total | R 241 500,00, unchanged (`15-a8-*`) | `/matters/{m}/trust-balance` 241 500 | CLIENT 234 000 + CLIENT_INVESTMENT 7 500 = 241 500 |
| Client total (all accounts) | — | — | 241 500 → 241 500 (**Δ 0**) |
| **Client general balance** | matter tab client line "Moroka Family Trust … — trust balance across all matters: **R 234 000,00**" (`15-a8-matter-trust-card-approved-crop.png`); client-ledger page "Trust Balance R 234 000,00" (`16-a8-*`) | **200**, card `balance 234000`; the list returns **200** | 241 500 → 234 000 (**Δ −7 500**); matter general Δ −7 500 |
| Invested | register R 7 500,00 · Active · Record Interest · Withdraw (`14-a7-*`) | investment `balance 7500`, ACTIVE | CLIENT_INVESTMENT +7 500 |
| Cashbooks | — | trust-position: funding 49 000 = 49 000, diff 0; s86(4) account 7 500 = 7 500, diff 0 | funding −7 500; s86(4) account +7 500 |

All 16 `a.checks` are true, including `apiClientCardGeneral` and `uiClientGeneral`, which failed on aba6f46d5.

The client-ledger page itemises the placement as "less Transfers out R 7 500,00". This is by design in 3a467b67b: per `LedgerTotals`, 648E adds the separate Invested figure. See O-1.

### b. Section 86(3): PASS

**Place dialog:**
- s86(3) help text "Interest on a section 86(3) investment belongs to the Legal Practitioners' Fidelity Fund."
- **Client ID / Matter ID fields: 0 / 0.**

**Stacked dialog:** Investment / s86(3) (disabled), primary unticked. The new account is `FIRM_DISCRETION`, not primary.

**Placement:** R2 500.

**Register:**

| When | Row |
|---|---|
| Before approval | "**Firm (section 86(3))** · Section 86(3) · … Awaiting approval", 0 Withdraw (`23-b4-*`) |
| After approval | "Firm (section 86(3)) … R 2 500,00 · Active · Record Interest · Withdraw" (`26-b7-*`) |

**Bob saw** "Placed in section 86(3) investment at Standard Bank (OBS s86(3) Investments IOR6X)". The type label now reads "Placed in section 86(3) investment" (**L-1 fixed**; `32-d1-inv3Place-row-crop.png`).

**SQL:**
- The investment has `customer_id` and `project_id` NULL.
- Both rows are INTER_ACCOUNT, posted, `customer_id` NULL.
- **Every client's general balance string was identical before and after.**
- Funding −2 500; s86(3) account +2 500; trust-position diff 0.

### c. Withdraw the s86(4) investment: PASS

**Dialog** (`27-c1-*`):
- It reads "Returns to trust **R 7 500,00**".
- Its only visible input is `withdrawalDate` (type date). Its attributes are **`min="2026-11-18"`** (= the investment's `deposit_date`) and **`max="2026-11-18"`** (= SA today).

**Discriminating run** (`obs-extra.cjs`, `x1/x2-*`), because the deposit was also today:
- A R1 000 s86(3) placement was dated **2026-11-10** in the UI and approved by Bob.
- Its Withdraw dialog has **`min="2026-11-10"`, `max="2026-11-18"`**.
- Typing 2026-11-08 was refused:
  - `rangeUnderflow`, "Value must be 2026/11/10 or later.";
  - the dialog stayed open;
  - **no POST** was sent;
  - transactions 16 → 16.
- The dialog was cancelled, and the investment stayed ACTIVE.

**After "Confirm Withdrawal" on the OBS s86(4) investment:**
- **SQL:**
  - INV_WITHDRAW RECORDED, CLIENT_INVESTMENT, −7 500.
  - INV_RETURN RECORDED, CLIENT, +7 500. Both are posted.
  - The investment is WITHDRAWN, amount 7 500.
- **Client general restored:**
  - **UI:** the matter-tab client line reads "R 241 500,00" (`29-c3-*`), and the client-ledger page loads (`30-c3-*`).
  - **API:** card **200, `balance 241500`**; list 200.
  - **SQL:** general 241 500, invested 0, total 241 500.
- **Books:**
  - API trust-position: funding 54 000 = 54 000; s86(4) 0 = 0; s86(3) 2 500 = 2 500; all diff 0.
  - SQL: sum `cashbook_effect` = sum `ledger_effect` per account.
  - The Trust Accounting overview loads: "Trust Balance R 54 000,00 … Trust creditors R 54 000,00 (agrees with the cashbook)" (`31-c4-*`). It failed under D-1 before.
- **Register:** "… R 0,00 · Withdrawn", 0 Withdraw (`28-c2-*`).

### d. Reversal refused: PASS

- **UI:** both placement rows (s86(4) withdrawn, s86(3) active) are APPROVED, with `reversal-action-*` 0 and Reverse buttons 0 (`32-d1-*`).
- **API evidence** (`obs/api-d-*.json`):
  - Both probes returned 400 "This entry placed a trust investment. Withdraw the investment instead of reversing its placement."
  - trust_transactions count unchanged; `reversed_by_id` stayed NULL.

### e. Close refused: PASS

- The Settings › Trust accounts card has no close control.
- **API evidence** (`obs/api-e-*.json`): `POST /api/trust-accounts/{OBS s86(3) account}/close` → 400 "An investment placed from or held in this account has not been withdrawn. Withdraw it before closing the account."
- The account stayed ACTIVE, `closed_date` NULL.

### f. No raw enum: PASS for the 648A UI surfaces

Coverage was 18 scans (`obs/scan-*`):
- the investments page in its awaiting, active, withdrawn and final states;
- both Place dialogs, empty and filled;
- both stacked Add dialogs;
- the withdraw dialog;
- the approvals rows.

**Re-check** with the H-2 substring method on `textContent` (`f-substring-recheck.txt`). The only hits are pre-existing and known:
- `RECORDED` / `APPROVED` / `AWAITING` in the Transactions status column (P-1);
- `ACTIVE` on the Settings trust-accounts card (P-2, F-4).

**New on a client document (not a UI scan):** the Client Trust Statement prints the type words **"Inv place"** and **"Inv return"** (**N-1**).

### g. Clip probe: PASS (W-1 fixed)

**Register** (`registerFit`, `obs/g-register-*-crop.png`). The register is 8 columns: Client / Investment account / Rate / Term / Principal / Balance / Status / Actions.

| Width | Table | Wrapper | Horizontal scroll | Rightmost button right | Wrapper right |
|---|---|---|---|---|---|
| 1280 | 926 | 926 | **none** | 1210 | 1231 |
| 1440 | 1086 | 1086 | **none** | 1345 | 1391 |
| 1920 | 1182 | 1182 | **none** | 1614 | 1679 |

**States probed:**
- s86(4) awaiting, s86(4) active, s86(3) awaiting, s86(3) active, s86(4) withdrawn, final.
- In every state, at every width: every Status badge was inside, 0 badges were truncated, and every Record Interest / Withdraw button was inside the wrapper and the viewport.
- "Awaiting approval" and "Withdrawn" are visible without scrolling (`g-register-final-1280-crop.png`).

**Stacked "Add Investment Account" at W×720:**
- The box is 36–684, inside the viewport.
- At 1280, "Create Account" starts below the fold (853–889). Scrolled into view, it is at 647–683, inside the viewport. At 1440/1920 it is in view without scrolling.
- The account was created by clicking it (count +1).
- 0 offenders, 0 truncated.

**Place dialog, filled (both bases), at 720 high:** 36–684, 0 offenders, 0 truncated, no horizontal overflow.

**Withdraw dialog:** 178–542, clean.

**Approvals row:**

| Width | Table | Description | Actions |
|---|---|---|---|
| 1280 | 926/926 | 4 lines, inside the row | 1040–1231 |
| 1440 | 1086/1086 | 2 lines | 1200–1391 |

### h. Console and logs: PASS

**Browser:** `obs.cjs` and `obs-extra.cjs` both had **0 console errors and 0 page errors**. The 404 and 500 of D-1 / E-1 are gone.

**Backend, OBS window (02:43:42–02:47:30):** 13 INFO / 17 WARN / **0 ERROR**.
- The WARNs are 16 PDFBox fallback-font lines (statement rendering) and 1 "No recipients found for DOCUMENT_GENERATED", from the SoA save.
- The refused d, e and i probes logged no ERROR.

**Whole stacks:**

| Stack | INFO | WARN | ERROR |
|---|---|---|---|
| #1 | 347 | 168 | **0** |
| #2 | 272 | 199 | **0** |

**Frontend `⨯`:**
- Stack #1: 19 × "The destination stream closed early." (known).
- Stack #2: 1.

### i. Future deposit date: PASS

**UI, both bases** (`06-a3i-*`, `22-b3i-*`):
- The Deposit Date input has `max="2026-11-18"` (SA today) and default value 2026-11-18.
- 2026-11-19 was typed into a fully filled dialog, then "Place Investment" was clicked:
  - `validity.rangeOverflow true` ("Value must be 2026/11/18 or earlier.");
  - the dialog stayed open;
  - **0 POSTs** were sent;
  - investments 2 → 2 and transactions 8 → 8 (s86(4)); 3 → 3 and 10 → 10 (s86(3)).
- The browser's native constraint check blocks the submit before the zod sentence would render. The form's own "The deposit date cannot be in the future." is therefore not displayed, which is expected.
- The date was then reset to today, and the real placement went through.

**API evidence** (`obs/api-i-place-future-s86-4.json`, `…-s86-3.json`):
- `POST /api/trust-accounts/{funding}/investments` with `depositDate` 2026-11-19 → **400** application/problem+json, title "Cannot place investment", detail "**The deposit date cannot be in the future.**"
- This held for s86(4) (with client and matter) and for s86(3).
- Investments 4 → 4, transactions 14 → 14, probe rows 0.

### j. Approval notification wording: PASS

**DB** (`obs-sums.out` [j1]): two rows, for Alice and for Bob, both of type `TRUST_PAYMENT_AWAITING_APPROVAL`:
- title "Trust placement in a section 86(4) investment requires approval";
- body "A placement in a section 86(4) investment of R7500 requires your approval."

**Bob's bell panel** (`10-j1-*`) and **notifications page** (`11-j1-*`) both list "Trust placement in a section 86(4) investment requires approval". No "placed in" wording appears anywhere.

The lifecycle's own entries read:
- "Trust placement in a section 86(3) investment account requires approval" (N-2);
- "Trust fee transfer requires approval".

### k. Statement names the funding account: PASS

**After the approved s86(4) placement** (`17-k1-*`, `k1-client-trust-statement-preview-after-s86-4-placement.txt`). Alice, on the client page, used Generate Document › Client Trust Statement (preview only). It reads:
- "Account: **Mathebula & Partners Trust Account**";
- opening R0,00;
- lines: Deposit R250 000 / Fee transfer R8 500 / "Inv place" INV-8C856A57 "Placed in section 86(4) investment at Nedbank (OBS s86(4) Investments IOR6X)" R7 500;
- **Closing balance R234 000,00**, which equals the SQL general on the funding account.

The investment account's name appears only inside that line's description, never as the statement's account.

**Statement of Account** on the matter (Finance › Statements, Preview & Save; `18-k1-*`):
- The Trust Activity section shows Deposits R250 000; Payments: Fee transfer R8 500 and "Placed in section 86(4) investment" R7 500; closing R234 000,00.
- "Trust balance held: R234 000,00".

**After the withdrawal** (`x3-k2-*`): "Account: Mathebula & Partners Trust Account". The lines are Deposit / Fee transfer / Inv place / **Inv return**.

**The data only shows the fix through the log:**
- SQL: the client's RECORDED/APPROVED activity covers the funding account (CLIENT) and the investment account (CLIENT_INVESTMENT). On the CLIENT ledger only, it is the funding account alone.
- Backend: **0** "has activity on N trust accounts" WARN lines on stack #1. Before 463e1c64a, the selection query would have returned two accounts here and logged that WARN.
- The funding account is the tenant's primary GENERAL, so the old preference rule would also have printed the funding account. The account named on the statement therefore cannot tell the old code from the new on this data; the absent multi-account WARN is the signature of the fix.

## Found items

See `found-obs.md`.
- **N-1 (MEDIUM, copy on a client document):** the Client Trust Statement prints "Inv place" / "Inv return" as the type of the s86(4) placement and return lines.
- **N-2 (LOW, copy):** the s86(3) notification noun still says "investment account", while the L-1 type label no longer does.
- **O-1 (observation, needs a ruling):** client-facing balances on the statements and the client-ledger page show the general balance only, so the invested R7 500 is not mentioned. `LedgerTotals` assigns the Invested figure to 648E.
- **P-1, P-2 (pre-existing), P-4 (pre-existing):** the amount in the notification body is unformatted ("R7500").
- **D-1, W-1, L-1 from the aba6f46d5 run: all three observed fixed.**

## Cleanup

- Both stacks were torn down (`stack1-down.log`, `stack2-down.log`, rc 0). `docker ps -a` shows **0 `e2e-*` containers**.
- `git status --short` matches `tree-before.txt` exactly: only `?? …/gates/` and `?? …/gates-b6afe8c69/`. `git diff --stat` is empty. HEAD is b6afe8c69.
