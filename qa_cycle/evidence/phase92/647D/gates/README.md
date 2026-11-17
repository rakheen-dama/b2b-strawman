# 647D observed gates — PR #1797 @ 0915e8b0d (2026-11-17, SAST)

All commands ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-647D`, at HEAD
0915e8b0db55230cd5016215ee3d1bc2cae8f123.

**Tree state.** Before the run the tree held only this new `gates/` dir (`tree-before.txt`). The same was true after the
run (`tree-after.txt`).

| Gate | Result |
|---|---|
| E2E-LEGAL lifecycle | **PASS**, 105 passed / 0 failed / 0 skipped, first run |
| E2E-LEGAL depth-ii | **PASS**, 40 passed / 4 skipped (baseline) / 0 failed |
| OBS a–g, i | **PASS** (observed) |
| OBS h | **FAIL**: the Transactions table at 1280 overflows its wrapper by 85 px once 647D rows are present (`found-obs.md` H-1) |

**Ground rules kept:**
- No source edits, no commits, no Maven.
- `pgrep -fl surefire` was empty before each stack and each e2e run.
- Exactly one e2e stack ran at a time, and `e2e-down.sh` ran before each `e2e-up.sh`.
- OBS changed state only through the UI:
  - Alice (owner) and Bob (admin) signed in via `/mock-login`.
  - The Moroka portal contact signed in through the portal's own login page.
- psql was used read-only, as evidence. API reads used each browser context's own `mock-auth-token` against `:8081`.
  There were **no API writes and no SQL writes**.
- Node: nvm node 20.11.1 and `/opt/homebrew/bin/pnpm` by absolute path, with `NODE_OPTIONS=""` and
  `PLAYWRIGHT_BASE_URL=http://localhost:3001`.
- The Playwright MCP profile was not touched: every browser run is a headless library script from the worktree's
  `frontend/node_modules`.
- Long commands ran as `nohup` + rc marker + bounded poll.
  - **Exception:** the first `obs.cjs` launch was in the foreground and was moved to the background by the tool at
    10 min. See OBS § Runs.
- Timings are in `timings.txt` (UTC). Every `*.log` is summarised in `logs-summary.txt`.

## Stack builds
No e2e stack was running at the start; `e2e-down.sh` ran first anyway (`stack0-down.log`). As in 647B, OBS ran on
stack #1 after the lifecycle suite.

| Stack | Built | Flyway / seed | Evidence |
|---|---|---|---|
| #1 (lifecycle + OBS) | "Building from source at: …/worktree-epic-647D", rc 0 (18:20:08–18:22:32 UTC) | V21 success=t; 0 trust txns, 0 statements, 0 recons, 0 items | `stack1-build.log`, `stack1-proof.txt`, `stack1-backend.log`, `stack1-frontend.log`, `stack1-down.log` |
| #2 (depth-ii) | same source path, fresh after the wipe, rc 0 (18:59:56–19:00:36) | V21 success=t; all zero | `stack2-*` |

Proof that both stacks run this worktree's code (`vproof.sh`; the two outputs are identical apart from the Flyway
timestamps):
- **Backend jar `/app/app.jar`:**
  - `transaction/TrustClientMatterRule.class` and `transaction/UnidentifiedResolutionHooks.class` exist (both new in
    647D).
  - `creditor/UnidentifiedDepositService$AllocateRequest`/`$PayToFundRequest`/`$Names` exist.
  - `UnidentifiedDepositService.class` contains:
    - `onResolutionApproved`;
    - `existsPostedResolutionOfItem` (fix cycle 1, F-647D-1);
    - `findLatestReopeningDate` (R2);
    - `Deposit received `;
    - "can no longer be unmatched".
  - `UnidentifiedDepositController.class` contains `/allocate` and `/pay-to-fund`.
  - `TrustTransactionService.class` contains `reverseUnidentifiedAllocation` (D6).
  - `TrustPostingService.class` contains `recordWithPair` (D3).
  - `TrustTransactionRepository.class` contains `existsPostedResolutionOfItem`.
  - The latest tenant migration is `V21__phase92_unidentified_deposits.sql`.
- **Frontend `.next`:** these strings are in the SSR chunk `frontend_components_trust_02woek0._.js` and the static
  chunk `19gsnmimf-ja2.js`:
  - `allocate-unidentified-dialog`
  - `pay-to-fund-dialog`
  - `pay-to-fund-under-one-year-warning`
  - `unidentified-deposit-pending`
  - `unidentified-deposit-allocated-to`
- **Runtime:** health is UP and `:3001` returns 200. There is one tenant schema, `tenant_7d218705360b`.
- **Portal (OBS d only):** the branch portal was served by `next dev --port 3012` from `worktree-epic-647D/portal`, with
  `NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081` (`obs/portal-dev-server-output.log`). It was stopped after use.

## Gate 1a — E2E-LEGAL lifecycle (stack #1, fresh): PASS (observed)
**105 passed / 0 failed / 0 skipped in 3.3 min**, rc 0 (18:22:43–18:26:15 UTC). This matches the baseline of 105, on the
first run; no re-run was needed. `lifecycle-last-run.json` reads `passed`.
- Log: `lifecycle.log`, per-test list in `lifecycle-summary.txt`.
- Trust and money lines (57): `lifecycle-trust-tests.txt`. They include:
  - Day 0: trust account + LPFF rate;
  - Day 14: two deposits, ledger balance, dashboard;
  - Day 30: FEE_TRANSFER awaiting approval → Bob approves;
  - Day 45: reconciliation started;
  - Day 60: interest run maker-checker, two investments placed;
  - Day 90: Client Trust Balances / Investment Register / Receipts & Payments reports, R500 fee transfer awaiting
    approval, Carol blocked, Bob has access.
- Suite screenshots (16): `lifecycle-artifacts/`.

## Gate 1b — E2E-LEGAL depth-ii (stack #2, fresh): PASS (observed)
**40 passed / 4 skipped / 0 failed in 2.1 min**, rc 0 (19:00:48–19:02:58 UTC). This matches the baseline of 40/4. The 4
skips are exactly the baseline ones:
- `day-30-45-soa-writeoff.spec.ts:361` (fixme)
- `null-prefetch-probe.spec.ts:74` ×3 (opt-in)

Outputs:
- Log `depth-ii.log`, summary `depth-ii-summary.txt`, `depth-ii-last-run.json` `passed`.
- Trust-related tests (13 lines): `depth-ii-trust-tests.txt`. These are the Day-5 trust-funded disbursement setup, creation
  and approval, and the Day 30–45 Statement of Account and write-off.
- Screenshots (10): `depth-ii-artifacts/`.

Read-only sums after the run (`depth-ii-derived-sums.out`): cashbook 3 750.00 = all creditors 3 750.00 = client ledgers
3 750.00 (2 posted txns, 0 statements, 0 items). This is identical to 647B.

## Gate 2 — OBS (stack #1, after the lifecycle run)

### Runs
| Script | Window (UTC) | What |
|---|---|---|
| `obs.cjs` (run tag `163PR`) | 18:33:17–18:53 | prep, a–f, g up to B's request |
| `obs-g.cjs` | 18:55:31–18:57:10, rc 0 | the rest of g, final reads, clip measures |
| `portal-obs.cjs` | 18:57:53–18:58:00, rc 0 | d, portal UI (read-only) |
| `probe-clip.cjs` | 18:58:36–18:58:45, rc 0 | h, final state (read-only) |
| `diag-tx-width.cjs` | ≈18:59 | h root cause (read-only) |

**How `obs.cjs` ended.** It completed prep and a–f, every check true in substance (see below), and submitted item B's
Fund payment through the dialog. It then died on a harness TypeError: a transient empty `docker exec psql` read
(`found-obs.md` O-1). Its slow pace came from 30 s default waits on absent optional elements; it was not hung.

**How the run continued.** `obs-g.cjs` continued from that exact DB/UI state (B pending) with the same helpers, plus
retry and count-guards. Nothing was repeated, and no state was changed outside the UI.

**Results:**
- JSON: `obs/obs-result.json`, `obs/obs-g-result.json`, `obs/portal-obs.json`, `obs/probe-clip.json`,
  `obs/diag-tx-width.json`, `obs/clip-*.json`.
- SQL: `obs-sums.sql` / `obs-sums.out` (appended across both scripts).
- API reads: `obs/api-*.json`.
- Screenshots: `obs/NN-*-1440.png` and `-1280.png` (every key screen at both widths), with `.txt` page text and
  `*-crop.png` element crops.
- Uploaded CSV: `obs/statement-OBS647D-163PR.csv`.

**Harness misses.** Three automated checks came back false for harness reasons only; each is re-read below from the
screenshot or JSON:
- (a) `actionsColumnHeader`: CSS uppercase renders "ACTIONS".
- (c) `uiAllocated`: the status label is "Allocated to a client", not "Allocated".
- (c) `ledgerUiShowsRow`: the firm ledger page has no description column; see N-1.

### Setup (prep, through the UI as Alice)
**State before OBS:** the lifecycle seed, i.e. trust account "Mathebula & Partners Trust Account" (single approval) and
6 rows. Baseline books as at today: cashbook **286 500.00** = all creditors 286 500.00 = client ledgers 286 500.00.
Unidentified 0, Fund 0. Moroka Family Trust's client ledger was 241 500.00.

**Statement** `statement-OBS647D-163PR.csv`:

| # | Date | Description | Amount |
|---|---|---|---|
| A | 10 Nov (today −7) | DEPOSIT UNKNOWN REF A163PR | 4 321.09 |
| B | 12 Nov (−5) | DEPOSIT UNKNOWN REF B163PR | 1 234.56 |
| C | 13 Nov (−4) | DEPOSIT UNKNOWN REF C163PR | 777.00 |

**Wizard:**
- The upload read "Lines imported: 3 … Period: 2026-11-10 to 2026-11-13".
- Auto-match read "Auto-matched 0 lines 3 lines remaining".
- Each line was recorded as **Unidentified deposit**, and each badge reads "Recorded: Unidentified deposit"
  (`02-prep-*`).
- Result: 3 HELD items (`da88d7a5…` A, `49d8d9f1…` B, `67de55e8…` C).
- Books: cashbook **292 832.65** = creditors 292 832.65 (client 286 500.00 + unidentified **6 332.65**).

### a. Allocate (slice row): PASS (`03`–`05`; `api-a3-*`, `api-a-*`)
**Before the request:**
- Other trust creditors showed all three items Held, each with **Allocate** and **Pay to the Fund** in the new ACTIONS
  column (`03-a1-*`).
- The dialog read "Allocate to a client — Received 10 Nov 2026 — DEPOSIT UNKNOWN REF A163PR — R 4 321,09. Another member
  must approve the allocation before the money moves to the client's ledger." Its date input defaulted to 2026-11-17,
  with min 2026-11-10 (the received date) and max 2026-11-17.

**The request:** Alice picked Moroka Family Trust / Estate Late Peter Moroka and an allocation date of **2026-11-14**,
added a note, and clicked Request allocation (`04-a2-*`).

**After the request:**

| Check | UI | API | SQL |
|---|---|---|---|
| Item A | "Held"; ACTIONS cell reads **"Allocation to Moroka Family Trust mwl0skwm827e awaiting approval"**; no buttons (`05-a3-pending-unidentified-crop.png`) | HELD, `resolutionStatus` AWAITING_APPROVAL, `resolutionTransactionId` set, allocated client and matter set | HELD, `resolution_transaction_id` = the new row, `allocated_customer/project` = Moroka / Estate, `resolved_date` null |
| New row | — | — | exactly +1 row: `UNIDENT_ALLOC_OUT` `cbf2e39b…`, AWAITING_APPROVAL, **not posted**, dated 2026-11-14, NON_CLIENT on the unidentified creditor, item id set, reference **UID-DA88D7A5**, effects 0 / 0 |
| Unidentified ledger | StatCard R 6 332,65 | 6332.65 | 6 332.65 (**unchanged**) |
| Moroka ledger | — | client-ledger `balance` 241500 | 241 500.00 (**unchanged**) |
| Cashbook | — | `cashbook-balance` 292832.65 | 292 832.65 (**unchanged**) |

### b. Alice's own approval refused: PASS (`06`, `07`)
Transactions page, pending row (`06-b1-*`):
- "14 Nov 2026 · UID-DA88D7A5 · **Unidentified deposit allocated**";
- muted line under it: **"To Moroka Family Trust mwl0skwm827e — Estate Late Peter Moroka mwl0skwm827e: deposit received
  10 November 2026 — DEPOSIT UNKNOWN REF A163PR"**;
- R 4 321,09 · AWAITING APPROVAL · Approve / Reject.

Alice clicked Approve. The refusal shown inline was **"The transaction recorder cannot be the sole approver. A different
member with APPROVE_TRUST_PAYMENT capability must approve this transaction."** (`07-b2-*`).

SQL afterwards: still AWAITING_APPROVAL, not posted, `approved_by` null. The books did not move. (At 1440 the refusal
text runs past the table edge: `found-obs.md` H-1b.)

### c. Bob approves: PASS (`08`–`13`; `api-c-*`)
Bob approved on his Transactions page; the toast read "Transaction approved" (`08`, `09`).

**SQL:**
- `UNIDENT_ALLOC_OUT` is APPROVED and posted, `approved_by` = Bob, ledger −4 321.09, cashbook 0.
- **`UNIDENT_ALLOC_IN` `9cae8806…`** is APPROVED and posted, with:
  - CLIENT ledger, Moroka / Estate matter;
  - **dated 2026-11-14** (the allocation date);
  - ledger +4 321.09, cashbook 0;
  - same reference UID-DA88D7A5, item id set, approvers inherited (`approved_by` Bob);
  - description **"Deposit received 10 November 2026 — DEPOSIT UNKNOWN REF A163PR"** (the receipt date, D12 format).
- Item A: **ALLOCATED**, `allocation_credit_transaction_id` = the credit, `resolved_date` 2026-11-14.

**Movement versus prep:**

| Ledger | Change |
|---|---|
| unidentified | **−4 321.09** (6 332.65 → 2 011.56) |
| Moroka | **+4 321.09** (241 500.00 → 245 821.09) |
| client ledgers | +4 321.09 |
| cashbook | **0** (292 832.65) |
| Fund | 0 |

**UI:**
- Other trust creditors (`10-c3-allocated-unidentified-crop.png`): the status is **"Allocated to a client"** with
  **"Moroka Family Trust mwl0skwm827e — Estate Late Peter Moroka mwl0skwm827e"** under it, and the ACTIONS cell is "—".
- The API gives ALLOCATED with the same names. The StatCard R 2 011,56 = API 2011.56 = SQL.

**Client ledger page** (`11-c4-*`):
- The row reads "14 Nov 2026 · UID-DA88D7A5 · Deposit identified and allocated · R 4 321,09 · APPROVED · running
  R 4 321,09".
- Trust Balance R 245 821,09 = API `balance` 245821.09 = SQL.
- The page has no Description column. The description is verified by the API history row (`api-c-moroka-history.json`:
  `description`, `transactionDate` 2026-11-14, `typeLabel`), by SQL, and by the portal UI (d). See `found-obs.md` N-1.

**Firm-wide books:**
- The dashboard (`12-c5-*`) reads "Trust Balance R 292 832,65 … Trust creditors R 292 832,65 (agrees with the cashbook)".
- `trust-position`: `cashbookBalance` 292 832.65, `creditorBreakdown` client 290 821.09 + unidentified 2 011.56 = total
  292 832.65, **`difference` 0**.
- SQL EQ: cashbook = creditors = client + unidentified + Fund.

**Reverse button:** Reverse is offered on the `UNIDENT_ALLOC_OUT` row and **not** on the `UNIDENT_ALLOC_IN` row
(VIA_PAIR) (`13-c6-*`).

### d. Portal, ruling D12 = spec verbatim: PASS (DB + portal UI observed)
**DB:** `portal.portal_trust_transaction` for Moroka gained the row `9cae8806…`:
- type **`UNIDENT_ALLOC_IN`**, amount 4 321.09, `transaction_date` 2026-11-14, matter Estate;
- description **"Deposit received 10 November 2026 — DEPOSIT UNKNOWN REF A163PR"**, reference UID-DA88D7A5.

There are **0** `UNIDENT_ALLOC_OUT` / `UNIDENT_FUND_PAY` / `UNIDENTIFIED_DEPOSIT` rows in the portal table, for any
client. This was checked after c and again at the end, after the Fund payment.

**Portal UI** (`portal-obs.cjs`; `obs/d1-portal-moroka-trust-{1440,1280,390}.png`, `d1-portal-allocated-row-crop.png`,
`portal-obs.json` verdict PASS):
- The Moroka contact signed in on the portal's own login page (dev magic link). The trust statement for the Estate matter
  shows **"14 Nov 2026 · Deposit identified and allocated · Deposit received 10 November 2026 — DEPOSIT UNKNOWN REF
  A163PR · R 4 321,09 · R 4 321,09"**.
- No "Unident…" or firm-only label appears. The label also shows on the 390 px mobile layout.
- **Timing caveat:** the portal UI was captured after step e. The statement therefore also shows the undo's
  "17 Nov 2026 · Reversal · Reversal — REV-UID-DA88D7A5 · R 4 321,09 · R 241 500,00". The reversal reason is not shown
  to the client.
- `obs.cjs`'s and `obs-g.cjs`'s own portal attempts did not capture. The first matched a hidden mobile-only line; the
  second was a dev-only StrictMode double exchange (`found-obs.md` O-2). Both are harness or dev-server issues.

### e. Undo the allocation: PASS (`14`–`16`)
Alice clicked Reverse on the `UNIDENT_ALLOC_OUT` row. The dialog read "This will create a reversal entry. Please provide
a reason." PAIR posts at once, so there was no approval step. Reason given, date blank (= today) (`14-e1-*`).

**SQL:**
- exactly **+2 rows**, both REVERSAL and posted, dated **2026-11-17**, both carrying item A's id:
  - the unidentified-ledger contra +4 321.09;
  - the Moroka CLIENT contra −4 321.09, Estate matter;
  - cashbook effect 0 on both;
- both legs are REVERSED;
- item A is **HELD**, with `resolution_transaction_id`, allocated client/matter and `resolved_date` all null.

**Balances:** back to the prep figures (unidentified 6 332.65, Moroka 241 500.00, cashbook 292 832.65), with every delta
0 and the books balanced.

**UI** (`16-e3-*`): A is "Held" with Allocate and Pay to the Fund offered again. The API gives HELD and
`resolutionTransactionId` null. The StatCard R 6 332,65 = API = SQL.

### f. F-647D-1 refusal: PASS (`17`)
In the still-open wizard tab, Alice clicked Unmatch on line A. The confirmation read "Unmatch recorded line? This line
was recorded as Unidentified deposit. Unmatching it reverses the entry it created, dated 10 Nov 2026 …". Alice clicked
**Unmatch and reverse**.

The wizard's error banner read **"This deposit has already been allocated or paid to the Fund and that was later undone,
so its statement line can no longer be unmatched."** (`17-f1-refusal-banner-crop.png`).
- This is the ProblemDetail **detail**. The title "Cannot unmatch statement line" is in the jar, but the banner shows
  only the detail.
- SQL: line A is still RECORDED / UNIDENTIFIED_DEPOSIT, item A still HELD, **0 rows added**, and the books are
  unchanged.

### g. Pay to the Fund: PASS (B approved, C rejected)

**Item B, approved.**

*The request* (UI, by `obs.cjs`; `19-g1-*`): the dialog read "Pay to the Fidelity Fund — Received 12 Nov 2026 — DEPOSIT
UNKNOWN REF B163PR — R 1 234,56. Another member must approve the payment. **Held for 5 days**", followed by the warning
**"This deposit has been held for less than a year. Unclaimed trust money is ordinarily paid to the Legal Practitioners'
Fidelity Fund only after the period the Act sets — check before you request this payment."** Reference
LPFF-B/OBS647D/163PR, date = today.

*Pending:*
- SQL: +1 `UNIDENT_FUND_PAY` `6906010c…`, AWAITING_APPROVAL, not posted, dated 2026-11-17.
- Items page: "Held", with "**Payment to the Fund awaiting approval**" and no buttons (`31-g2-*`).
- Books unchanged.
- Bob's Transactions row: "Unidentified deposit paid to the Fund" with the line "To the Legal Practitioners' Fidelity
  Fund: deposit received 12 November 2026 — DEPOSIT UNKNOWN REF B163PR" (`32-g3-*`).

*After Bob approved* (`33-g4-*`):
- SQL: the row is APPROVED and posted, `approved_by` Bob, on the unidentified creditor, **ledger −1 234.56, cashbook
  −1 234.56**. Item B is **PAID_TO_FUND**, `resolved_date` 2026-11-17.
- UI: "Paid to the Fund", ACTIONS "—" (`34-g5-*`). The API gives PAID_TO_FUND.
- StatCard R 5 098,09 = API 5098.09 = SQL 5 098.09.

*Movement (per the posting table, path 16 = `cashbookAndUnidentified(−L)`):*

| Ledger | Before → after | Change |
|---|---|---|
| cashbook | 292 832.65 → 291 598.09 | −1 234.56 |
| unidentified | 6 332.65 → 5 098.09 | −1 234.56 |
| **Fund (FUND_INTEREST_AND_CHARGES) creditor** | 0 → 0 | 0 (API card balance 0); the Fund ledger is not a leg of this payment |
| client ledgers | — | 0 |

**Item C, reject path** (`35`–`40`):
- *Dialog* (`36-gc1-*`): "**Held for 4 days**". The warning is visible with `role="status"`. The date defaulted to
  2026-11-17, with min 2026-11-13 and max 2026-11-17.
- *Request:* it created the `UNIDENT_FUND_PAY` for C. The items page showed "Payment to the Fund awaiting approval".
- *Rejection:* Bob rejected it with a reason (`39-gc4-*`).
- *Result:*
  - SQL: the row is **REJECTED**, not posted. Item C is **HELD**, with `resolution_transaction_id` and `resolved_date`
    null.
  - Books unchanged.
  - UI: "Held", with Allocate and Pay to the Fund offered again (`40-gc5-*`).

**Final books** (`obs-g-result.json` final):

| As at | Cashbook | Creditors | Detail | Balanced |
|---|---|---|---|---|
| today | 291 598.09 | 291 598.09 | client 286 500.00 + unidentified 5 098.09 + Fund 0 | yes |
| 2026-11-14 (allocation date) | 6 332.65 | 6 332.65 | client 4 321.09 + unidentified 2 011.56 | yes |
| 2026-11-10 (receipt A) | 4 321.09 | 4 321.09 | — | yes |

`trust-position` `difference` 0.

### h. Clip probe 1280 / 1440: FAIL on the Transactions table at 1280; PASS on the items table
This used the same bounding-rect method as 647B's `probe-clip.cjs`, plus a per-cell descendant check. Measures were
taken in each 647D state (`obs/clip-*.json`) and in the final state (`probe-clip.cjs` at 1280/1440/1920).

**Unidentified deposits table (new ACTIONS column, pending text, allocated text):** **PASS** in every state:
- held (a1);
- allocation pending (a3);
- allocated (c3; status cell 729–1017 at 1280);
- Fund pending (g2);
- final (z2).

The table equals its wrapper at both widths (1280: 305–1231 = 926; 1440: 305–1391 = 1 086). No cell is outside and none
is truncated. The buttons wrap within the cell at 1280.

**Transactions table:** **FAIL at 1280** (`found-obs.md` H-1). PASS at 1440 and 1920.

| State | Wrapper | Table | Actions column |
|---|---|---|---|
| (b) allocation pending | 926 | 970 | 1084–1275 |
| (g) / final | 926 | **1 011** | **1125–1316** |

Every Actions cell (16 cells in the final state) lies partly outside the `overflow-x-auto` wrapper (right edge 1231); on
pending rows the **Reject** button is off-screen without a sideways scroll (`h-diag-transactions-1280-crop.png`).

`diag-tx-width.cjs` (DOM-only experiment) found the cause:
- hiding the 647D **description lines** changes nothing (still 1011);
- hiding the 4 resolution rows, or letting the type badges wrap, brings the table back to exactly 926.

The non-wrapping type badges of 647D's rows ("Unidentified deposit paid to the Fund" 237 px, "Deposit identified and
allocated" 205 px, "Unidentified deposit allocated" 196 px) widen the Type column to 253 px.

### i. Backend log: PASS
**OBS window 18:33:00–18:59:30 UTC: 93 INFO, 0 WARN, 0 ERROR** (`obs-window-log-breakdown.txt`). The refused
self-approval (400), the refused unmatch (400) and the 401 on the portal's second dev-mode exchange produced no WARN or
ERROR lines.

Frontend: **0** `⨯` lines in the OBS window. All 42 `⨯ Error: The destination stream closed early.` lines on stack #1
fell during the lifecycle suite; the last was at 18:25:53.

| Stack | Whole-stack backend log |
|---|---|
| #1 | 439 INFO, 151 WARN, **0 ERROR** |
| #2 | 270 INFO, 199 WARN, **0 ERROR** |

A normalised diff of the WARN kinds against 647B's three breakdowns finds **no new kind**. They are the known noise
(PDFBox fallback fonts, etc.; see `stack*-log-breakdown.txt`).

Browser:
- page errors **0**;
- console errors: only the portal's dev-mode 401 on the second `/auth/exchange` call (O-2), once per portal sign-in.

## Found items
See `found-obs.md`.
- **H-1 (FAIL, OBS h):** the 1280 Transactions-table overflow described above. 647D's rows trigger it; the mechanism is
  the existing non-wrapping type badge.
- **H-1b:** an inline approval error is cut off at 1440.
- **O-1:** harness, a transient empty `docker exec` read; continuation explained.
- **O-2:** dev-only portal StrictMode double exchange.
- **N-1:** the firm's client-ledger page has no Description column.

## Cleanup
- After each legal run I copied the curated screenshots (`lifecycle-artifacts/` 16, `depth-ii-artifacts/` 10). I then
  ran `git clean -fd -- documentation/screenshots/legal-vertical`. No tracked file was modified by any run, and `next dev`
  on the portal left no tracked change.
- The portal dev server (:3012) was stopped, and both stacks were torn down (`stack1-down.log`, `stack2-down.log`).
  0 e2e containers are running.
- `git status --short` shows only `?? qa_cycle/evidence/phase92/647D/gates/` (`tree-after.txt`). HEAD is 0915e8b0d.
