# 647D re-gate: PR #1797 @ 45dd3022f (2026-11-17, SAST)

This is a re-gate after the frontend-only OBS h fix: `git diff 0915e8b0d..45dd3022f`. The transactions-page type Badge now
gets `className="shrink whitespace-normal"` and `data-testid="transaction-type-{id}"`.
- All commands ran from `/Users/rakheendama/Projects/2026/worktree-epic-647D` at HEAD
  45dd3022f06a590b5dd1e22b2232bc6ed214d394.
- The first run's evidence (`../`, @0915e8b0d) is untouched.

| Gate | Result |
|---|---|
| E2E-LEGAL lifecycle (stack #1, fresh) | **PASS**: 105 passed / 0 failed / 0 skipped, first run, 3.0 min |
| E2E-LEGAL depth-ii (stack #2, fresh) | **PASS**: 40 passed / 4 skipped (the baseline skips) / 0 failed, 2.0 min |
| OBS h, clip criterion (no cell outside the wrapper at 1280/1440; Approve and Reject both visible) | **PASS** at every width, for both Bob and Alice |
| OBS h, readability sub-check ("two lines max") | **One exception at 1280:** "Unidentified deposit paid to the Fund" wraps to **3** lines. Every label is 1 line at 1440 and 1920. See H-2. |
| Smoke: pending allocation approved by Bob | **PASS**: unidentified −4 321.09, client (Moroka) +4 321.09, cashbook **0** |
| Backend log | OBS window 6 INFO / 0 WARN / 0 ERROR. Whole stacks: 0 ERROR, and no WARN kind that the first run did not have. |

## Ground rules kept
- No source edits, commits, pushes or Maven.
- `pgrep -fl surefire` was empty before each stack and each run.
  - Unrelated testcontainers (temurin, kafka and postgres containers on random ports) from another process were running
    on the host. They had no `e2e-*` names and no ports shared with this stack.
- One e2e stack ran at a time, with `e2e-down.sh` before each `e2e-up.sh` (`stack0-down.log`, `stack1-down.log`).
- Node and Playwright setup:
  - nvm node v20.11.1 and `/opt/homebrew/bin/pnpm` by absolute path, with `NODE_OPTIONS=""` and
    `PLAYWRIGHT_BASE_URL=http://localhost:3001`.
  - Script flags were passed without `--`.
  - Browser runs were headless Playwright library scripts from the worktree's `frontend/node_modules`. The Playwright MCP
    and the user's Chrome profile were not touched.
- OBS state changed only through the UI: Alice and Bob signed in via `/mock-login`.
  - psql was read-only.
  - API reads used the context's own `mock-auth-token`.
  - There were no API writes and no SQL writes.
- Long commands ran as `nohup` + rc marker + bounded polls, all foreground. Nothing was left running.
- Timings are in `timings.txt` (UTC), and every `*.log` is summarised in `logs-summary.txt`.

## Stacks and code proof (`vproof.sh` → `stack1-proof.txt`, `stack2-proof.txt`)

| Stack | Up (UTC) | Used for | Fresh state |
|---|---|---|---|
| #1 | 19:15:26–19:17:53, rc 0 | lifecycle, then OBS h | V21 success=t; 0 txns / statements / recons / items |
| #2 | 19:23:11–19:23:54, rc 0 | depth-ii | same, all zero |

Both build logs read "Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-647D".
- Stack #1 rebuilt the frontend in this run ("Creating an optimized production build … ✓ Compiled successfully").
- Stack #2 reused that image. The source is the same, and the proof below is identical.

In both stacks:
- **Frontend `.next`:** the SSR chunk `.next/server/chunks/ssr/_13ydhih._.js` contains both `transaction-type-` and
  `whitespace-normal`. The excerpt is
  `o.Badge,{variant:"neutral",className:"shrink whitespace-normal","data-testid":\`transaction-type-${a.id}\`,children:a.typeLabel}`.
  The transactions route `app/(app)/org/[slug]/trust-accounting/transactions/page.js` references `_13ydhih`.
- **Backend jar:** it still carries 647D:
  - `TrustClientMatterRule` and `UnidentifiedResolutionHooks` exist;
  - `existsPostedResolutionOfItem` is in `UnidentifiedDepositService`;
  - the latest tenant migration is V21.
- **Runtime:** health is UP and `:3001` returns 200.
- **Runtime check:** in OBS every type badge had its `data-testid` (`hasTestid` true on all 15 rows) and computed
  `white-space: normal`, `flex-shrink: 1`.

## Gate 1 — E2E-LEGAL
- **lifecycle** (stack #1, 19:18:01–19:21:08): **105 passed** in 3.0 min, rc 0, first run, so no re-run was needed.
  - Outputs: `lifecycle.log`, `lifecycle-summary.txt`, `lifecycle-trust-tests.txt` (49 trust/money lines),
    `lifecycle-last-run.json` `passed`.
  - Suite screenshots (16): `lifecycle-artifacts/`.
- **depth-ii** (stack #2, 19:24:01–19:26:04): **40 passed / 4 skipped** in 2.0 min, rc 0.
  - The skips are exactly the baseline ones: `day-30-45-soa-writeoff.spec.ts:361` and `null-prefetch-probe.spec.ts:74`
    ×3.
  - Outputs: `depth-ii.log`, `depth-ii-summary.txt`, `depth-ii-trust-tests.txt`, `depth-ii-last-run.json` `passed`.
  - Screenshots (10): `depth-ii-artifacts/`.
  - Read-only sums (`depth-ii-derived-sums.out`): cashbook 3 750.00 = all creditors 3 750.00 = client ledgers 3 750.00
    (2 posted txns). This is identical to the first run and to 647B.

## Gate 2 — OBS h re-run (stack #1, after lifecycle; `regate-obs.cjs`, 19:21:17–19:22:54, rc 0)
The script was adapted from `../obs.cjs`, `../obs-g.cjs`, `../probe-clip.cjs` and `../diag-tx-width.cjs`. It adds:
- retry on an empty `docker exec` read (first run O-1);
- no 30 s waits on optional elements.

Result JSON: `obs/regate-obs-result.json`. SQL: `obs-sums.sql` / `obs-sums.out`. Uploaded CSV:
`obs/statement-RG647D-2VTTB.csv`.

### State built through the UI
Alice went through the wizard: upload, auto-match (0), review. She recorded four unknown credits as **Unidentified
deposit**; all four badges read "Recorded: Unidentified deposit":

| Item | Received | Amount |
|---|---|---|
| A | 10 Nov | 4 321.09 |
| B | 12 Nov | 1 234.56 |
| C | 13 Nov | 777.00 |
| D | 14 Nov | 2 500.25 |

Each item was then resolved as follows:

| Item | Flow | End state |
|---|---|---|
| A | Alice requested an allocation to Moroka Family Trust / Estate Late Peter Moroka, dated 14 Nov; **Bob approved** | ALLOCATED; `UNIDENT_ALLOC_OUT` + `UNIDENT_ALLOC_IN` APPROVED |
| B | Alice requested a payment to the Fund; **Bob approved** | PAID_TO_FUND; `UNIDENT_FUND_PAY` APPROVED |
| C | Alice requested an allocation to Moroka / Estate | **pending** `UNIDENT_ALLOC_OUT` (Approve / Reject) |
| D | Alice requested a payment to the Fund | **pending** `UNIDENT_FUND_PAY` (Approve / Reject) |

The Transactions page therefore held 15 rows on one page ("15 transactions found" = SQL 15). That is the 6 lifecycle
rows (two of them pending Payments and one pending Fee transfer), 4 "Unidentified deposit" rows, "Unidentified deposit
allocated" ×2 (one approved, one pending), "Deposit identified and allocated", and "Unidentified deposit paid to the Fund"
×2 (one approved, one pending). That makes **5 pending rows** with Approve/Reject, two of which are 647D's.

### Smoke: Bob approves a pending allocation (item A), figures only

| | Before request | After request | After Bob approved | Change |
|---|---|---|---|---|
| unidentified ledger | 8 832.90 | 8 832.90 | 4 511.81 | **−4 321.09** |
| Moroka client ledger | 241 500.00 | 241 500.00 | 245 821.09 | **+4 321.09** |
| all client ledgers | 286 500.00 | 286 500.00 | 290 821.09 | +4 321.09 |
| cashbook | 295 332.90 | 295 332.90 | 295 332.90 | **0** |

**The rows:**
- `UNIDENT_ALLOC_OUT` APPROVED, posted, ledger −4 321.09, cashbook 0, on the unidentified creditor, `approved_by` Bob.
- `UNIDENT_ALLOC_IN` APPROVED, posted, CLIENT, Moroka / Estate, ledger +4 321.09, cashbook 0, dated 2026-11-14.

**Item and firm position:**
- Item A is ALLOCATED, `resolved_date` 2026-11-14.
- The toast read "Transaction approved".
- The books balance, and `trust-position` `difference` is 0.
- The request alone moved nothing.

**The other items:**
- B, approved: cashbook −1 234.56 and unidentified −1 234.56; client 0, Fund 0.
- C and D, pending: nothing moved.

Final books: cashbook 294 098.34 = creditors 294 098.34 (client 290 821.09 + unidentified 3 277.25 + Fund 0).

### Clip probe: Transactions page, before (H-1 @0915e8b0d) and after (@45dd302)
Columns are measured by bounding rects. "Outside" counts any cell, or any descendant of a cell, that lies outside the
`overflow-x-auto` wrapper.

| Width | Measure | Before (first run, final state) | **After** (Bob; Alice identical) |
|---|---|---|---|
| 1280 | wrapper | 305–1231 (926) | 305–1231 (926) |
| 1280 | table | 305–1316 (**1 011**) | 305–1231 (**926**); scrollWidth 926 = clientWidth |
| 1280 | Type column | 253 | 155 |
| 1280 | Actions column | **1125–1316** (85 px past the wrapper) | **1040–1231** (right edge = wrapper edge) |
| 1280 | cells outside | **16** | **0** (15 rows) |
| 1280 | pending rows: Approve / Reject | Reject off-screen | all 5 rows: Approve 1040–1139, Reject 1147–1231, **both fully visible** |
| 1440 | wrapper / table | 1 086 / 1 086 | 305–1391 (1 086) / 1 086 |
| 1440 | Actions column | inside | 1200–1391 |
| 1440 | cells outside / buttons | 0 | **0**; all 5 pending rows have both buttons fully visible |
| 1920 | table vs wrapper | table = wrapper | 1 182 = 1 182; Actions 1488–1679; 0 outside; buttons visible |

Column widths after the fix:

| Width | Date | Reference | Type | Amount | Status | Actions |
|---|---|---|---|---|---|---|
| 1280 | 60 | 223 | 155 | 133 | 165 | 191 |
| 1440 | 84 | 245 | 269 | 133 | 165 | 191 |

On every row and width:
- the badge sits inside its cell;
- the badge is clear of the Amount cell;
- the 647D description line sits below the badge.

There is no overlap and nothing is truncated.

**Other trust creditors (Alice), after:**

| Width | Unidentified deposits table | Fund ledger table |
|---|---|---|
| 1280 | 305–1231 = wrapper 926, scroll 926, **0 outside**, 4 rows, none truncated | not rendered (no Fund-ledger rows; same as the first run) |
| 1440 | 305–1391 = wrapper 1 086, **0 outside** | not rendered |

**Verdict on the stated PASS criterion: PASS.** No cell is outside the wrapper at 1280 or 1440 (or 1920), and Approve
and Reject are both fully visible on every pending row, for both Bob and Alice.

### Label wrapping (Bob; line count from text-range client rects; badge height 22 px = 1 line, 38 = 2, 54 = 3)

| Label | 1280 | 1440 | 1920 |
|---|---|---|---|
| Deposit | 1 | 1 | 1 |
| Payment | 1 | 1 | 1 |
| Fee transfer | 1 | 1 | 1 |
| Unidentified deposit (pre-647D label; was 1 line at 139 px nowrap before the fix) | **2** | 1 | 1 |
| Unidentified deposit allocated | 2 | 1 | 1 |
| Deposit identified and allocated | 2 | 1 | 1 |
| Unidentified deposit paid to the Fund | **3** ("Unidentified / deposit paid to the / Fund") | 1 | 1 |

The short labels "Deposit", "Payment" and "Fee transfer" stay on one line at every width.

At 1280 the 647D description line under a resolution badge runs to 6 lines (Fund) or 8 lines (allocation), against 3–4
at 1440. Those rows are tall but legible, and nothing overlaps (`h-typecell-*-1280-crop.png`).

### Screenshots (`obs/`)

**Transactions page (Bob):**
- full page: `h-transactions-bob-1280.png`, `h-transactions-bob-1440.png`, `h-transactions-bob-1920.png`;
- table wrapper crops: `h-transactions-table-bob-{1280,1440}-crop.png`. In the 1280 crop the app's sticky header
  overlays the top rows. That is an element-screenshot scroll artefact; the full-page shot is clean.

**Rows (pending Fund, pending allocation, allocation credit leg):**
- whole row: `h-row-{pending-fund,pending-alloc,alloc-in}-bob-{1280,1440}-crop.png`;
- Type cell only, showing the wrapped label: `h-typecell-{pending-fund,pending-alloc,alloc-in}-bob-{1280,1440}-crop.png`.

**Other trust creditors (Alice):** `h-other-creditors-alice-{1280,1440}.png` and
`h-unidentified-section-{1280,1440}-crop.png`.

**State-building screens:** `NN-*-{1440,1280}.png` with `.txt` page text. These cover the wizard, the items page before
and after each request, and Bob's approvals.

### Log check
- **Backend, OBS window** (19:21:00–19:22:30 UTC; `obs-window-log-breakdown.txt`): 6 INFO, 0 WARN, 0 ERROR.

| Stack | Whole-stack backend log | Frontend `⨯` lines |
|---|---|---|
| #1 | 331 INFO, 151 WARN, **0 ERROR** | 18 × "The destination stream closed early.", all 19:18:06–19:18:15 (during the lifecycle suite, none in the OBS window) |
| #2 | 270 INFO, 199 WARN, **0 ERROR** | 1, of the same kind |

- **WARN kinds:** a normalised diff against the first run's `stack{1,2}-log-breakdown.txt` finds 0 new kinds (PDFBox
  fallback fonts etc.).
- **Browser:** 0 console errors and 0 page errors in the OBS run.

## Found items
- **H-1 (first run): fixed, observed.** At 1280 the table is 926 = wrapper, the Actions column is inside, and Reject is
  visible.
- **H-2 (new, LOW / cosmetic; classification is the orchestrator's call).**
  - **What:** at 1280, "Unidentified deposit paid to the Fund" wraps to 3 lines (orphan "Fund"), which misses the brief's
    "two lines max". The resolution rows' description lines run to 6–8 lines, so those rows are about 3× the height of a
    plain row.
  - **Why:** the Type column is squeezed to 155 px. The Reference column holds 223 px, because unbroken references such as
    `DEP/MOROKA/mwl2ro8o8d3b` and `FEE/MOROKA/mwl2ro8o8d3b` cannot wrap. The Date column also wraps to 3 lines
    ("14 / Nov / 2026") on some rows; that predates the fix (the first run measured Date at 50 px).
  - **Not a regression of the PASS criterion:** everything is legible, nothing overlaps or clips, and at 1440+ every label
    is on one line.
  - **Possible remedies (not done):** a `min-w` on the Type cell, or letting Reference break (`break-all`). Either needs a
    re-measure at 1280.
- **H-1b / F-647D-5 (pre-existing inline approval-error width): not re-tested.** This run made no refused approval.
- Nothing else is new. O-1 did not recur: the retrying read was never needed, and the run took 1.5 min.

## Tree state
- **Before** (`tree-before.txt`): `?? qa_cycle/evidence/phase92/647D/gates/`.
- **During the runs:** each Playwright legal config wrote untracked screenshots under
  `documentation/screenshots/legal-vertical/`. I copied them into `lifecycle-artifacts/` (16) and `depth-ii-artifacts/`
  (10), then ran `git clean -fd -- documentation/screenshots/legal-vertical`. No tracked file was modified.
- **After** (`tree-after.txt`): `?? qa_cycle/evidence/phase92/647D/gates/` only. HEAD is still 45dd3022f.
- **Stacks:** both were torn down (`stack1-down.log`, `stack2-down.log`), and 0 `e2e-*` containers are running.
