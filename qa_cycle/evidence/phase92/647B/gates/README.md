# 647B observed gates — PR #1796 @ 563ef6185 (2026-11-17, SAST)

All commands ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-647B`, at HEAD
563ef6185e8a186c077b517e711ee86948fc26df.

**Tree state.** Before the run the tree held only this new `gates/` dir (`tree-before.txt`). The same was true after the
run (`tree-after.txt`).

**Ground rules kept:**
- No source edits, no commits, no Maven.
- `pgrep -fl surefire` was empty before each stack and each e2e run.
- Each stack's backend jar was built inside its Docker image.
- OBS changed state only through the UI. Alice (owner) signed in via `/mock-login`, then:
  - uploaded statements and ran auto-match;
  - recorded the unknown credit as an Unidentified deposit;
  - unmatched it;
  - for the probe, recorded a bank charge and a second unknown credit.
- psql was used read-only, as evidence. API reads used the browser context's own `mock-auth-token` against `:8081`.
  There were **no API writes**.
- Node: nvm node 20.11.1 and `/opt/homebrew/bin/pnpm` by absolute path, with `NODE_OPTIONS=""` and
  `PLAYWRIGHT_BASE_URL=http://localhost:3001`.
- Timings are in `timings.txt`. Every `*.log` is summarised in `logs-summary.txt`.

## Stack builds
No e2e stack was running at the start; `e2e-down.sh` ran first anyway (`stack0-down.log`). Exactly one e2e stack ran at
a time, and each was wiped with `e2e-down.sh` before the next. As in 647C, OBS ran on stack #1 after the lifecycle
suite. Stack #1b was an extra fresh stack for the lifecycle re-run (see Gate 1a).

| Stack | Built | Flyway / seed | Evidence |
|---|---|---|---|
| #1 (lifecycle run 1 + OBS) | "Building from source at: …/worktree-epic-647B", rc 0 (16:01:51–16:05:05 UTC) | V21 success=t at 16:03:31; 0 trust txns, 0 statements, 0 recons, 0 items | `stack1-build.log`, `stack1-proof.txt`, `stack1-backend.log`, `stack1-frontend.log`, `stack1-down.log` |
| #1b (lifecycle re-run) | same source path, fresh after the wipe, rc 0 (16:18:05–16:19:16) | V21 success=t at 16:18:37; all zero | `stack1b-*` |
| #2 (depth-ii) | same, fresh, rc 0 (16:23:16–16:24:27) | V21 success=t at 16:23:47; all zero | `stack2-*` |

Proof that every stack runs this worktree's code (`vproof.sh`; the three outputs are identical apart from the Flyway
timestamps):
- **Backend jar `/app/app.jar`:**
  - `creditor/UnidentifiedDeposit{,Controller,Repository,Service,Status}.class` and
    `UnidentifiedDepositService${StatementCredit,Opened,UnidentifiedDepositResponse}.class` exist (all new in 647B).
  - `UnidentifiedDepositController.class` contains `/api/trust-accounts/{accountId}/unidentified-deposits`.
  - `UnidentifiedDepositService.class` contains `lockHeldForUnmatch` and `markReversedByUnmatch`.
  - `StatementLineClassification.class` contains `UNIDENTIFIED_DEPOSIT`.
  - `StatementLinePostingService.class` contains `StatementCredit` and the investment-account refusal text.
  - The latest tenant migration is `V21__phase92_unidentified_deposits.sql`.
- **Tenant DB:**
  - `flyway_schema_history` has V21 "phase92 unidentified deposits" with success=t.
  - The columns `trust_transactions.unidentified_deposit_id` and
    `unidentified_deposits.{deposit_transaction_id,status,resolved_date}` exist.
  - The constraints `chk_trust_txn_unidentified_item`, `chk_unidentified_deposit_resolved` and
    `uq_unidentified_deposit_txn` exist.
- **Frontend `.next`:**
  - `unidentified-deposits-table` and `unidentified-deposit-row` are in the SSR and static chunks.
  - `unidentified-deposits-section` is in the SSR chunk.
  - `classification-unidentified-note` and "Held on the unidentified deposits ledger" are in the reconciliation/new
    page chunk (SSR and static).
  - "Unidentified deposit" appears in 4 SSR chunks.
- **Runtime:** health is UP and `:3001` returns HTTP 200. There is one tenant schema, `tenant_7d218705360b`, with one
  trust account and two creditor accounts.

## Gate 1a — E2E-LEGAL lifecycle: PASS (observed on re-run; run 1 hit a known-class intermittent, not 647B)

### Run 1 (stack #1)
**91 passed / 2 failed / 12 did not run in 4.6 min**, rc 1 (`lifecycle-run1.log`, `lifecycle-run1-summary.txt`,
`lifecycle-run1-failures/`).

The root failure was `day-75-complex.spec.ts:224` "Log 360min on Moroka estate matter":
- the time entry was saved (SQL) and the header showed "6,0h · 1 entry";
- for 15 s, though, the Fees → Time tab still showed only the older 1 h entry.

The serial Day-75 describe then stopped, so "Apex Annual Returns" was never created. `day-90-review.spec.ts:83` (the six
matters) failed as a cascade. See `found-obs.md` N-2.

### Re-run (fresh stack #1b)
The spec cannot run alone: it needs Days 0–60 on the same stack, and on stack #1 its own earlier steps had already added
the adverse parties it asserts are absent. So the re-run was the whole suite on a fresh stack.

**105 passed / 0 failed / 0 skipped in 3.5 min**, rc 0 (16:19:19–16:22:50 UTC). This matches the baseline of 105.
`lifecycle-last-run.json` reads `passed`.
- Log: `lifecycle.log`, summary in `lifecycle-summary.txt`.
- Trust lines (49): `lifecycle-trust-tests.txt`.
- Suite screenshots (16): `lifecycle-artifacts/`. Run 1's are in `lifecycle-run1-artifacts/`.

### Classification
The run-1 failure did not reproduce on the PR head, so it does not count against the PR. 647B touches no
time/matter-tab code. It is the same class as 647C's N-1 Time-tab stall.

## Gate 1b — E2E-LEGAL depth-ii (stack #2, fresh): PASS (observed)
**40 passed / 4 skipped / 0 failed in 1.9 min**, rc 0 (16:24:29–16:26:26 UTC). This matches the baseline of 40/4. The 4
skips are exactly the baseline ones:
- `day-30-45-soa-writeoff.spec.ts:361` (fixme)
- `null-prefetch-probe.spec.ts:74` ×3 (opt-in)

- Log: `depth-ii.log`, summary in `depth-ii-summary.txt`, `depth-ii-last-run.json` `passed`.
- Screenshots (10): `depth-ii-artifacts/`.
- Read-only sums after the run (`depth-ii-derived-sums.out`) match 647C: cashbook 3 750.00 = all creditors 3 750.00 =
  client ledgers 3 750.00 (2 posted txns, 0 statements, 0 items).

## Gate 2 — OBS (stack #1, after lifecycle run 1): PASS on a–f (observed)

### Scripts
All are headless Chromium library scripts from the worktree's `frontend/node_modules`. The Playwright MCP profile was
not touched.
- `obs.cjs` (run tag `W2SMN`, 16:10:46–16:13:40 UTC) did steps a–e, **every check true** (`obs/obs-result.json`
  `steps.*.checks`). It then failed in its supplementary f-prep: a click to re-open line 1 was lost (harness, `found-obs.md`
  O-2), so the file has `fatal` and no `verdict` key. The a–e steps and evidence were already written.
- `obs-fprep.cjs` prepared data for the probe through the UI:
  - attempt 1 was refused at step 4 because of the period rule (O-1);
  - attempt 2 (16:16:33–16:17:09) passed, with all checks true (`obs/obs-fprep-result.json`).
- `probe-clip.cjs` (read-only, 16:17:15–16:17:20) did step f (`obs/probe-clip.json`, `verdict: PASS`).

### Outputs
- Screenshots: `obs/NN-*.png`, with `.txt` page text and `*-crop.png` element crops.
- API reads: `obs/api-*.json`.
- SQL: `obs-sums.sql` / `obs-sums.out`.
- Uploaded CSVs: `obs/statement-OBS647B-*.csv`.

### Errors
- Console errors **0** and page errors **0**, in all three scripts.
- Backend, OBS and probe window 16:10:46–16:17:20 UTC: **19 INFO, 0 WARN, 0 ERROR** (`obs-window-log-breakdown.txt`).
- Frontend: **0** `⨯` lines in the OBS window. The last `⨯` was at 16:09:51, during the suite.

### Dates and baseline
Today (SAST) is 2026-11-17.

**Baseline** (`01-*`): 6 trust rows from the lifecycle seed. EQ-2: cashbook 286 500.00 = all creditors 286 500.00.
The Unidentified deposits ledger is 0.00:
- the StatCard reads R 0,00;
- the API balance is 0;
- the table shows the empty state "No unidentified deposits. …".

**Statement** `statement-OBS647B-W2SMN.csv` (Standard Bank CSV layout):

| # | Date | Description | Reference | Amount |
|---|---|---|---|---|
| 1 | 2026-11-12 (today −5) | DEPOSIT UNKNOWN REF 12345 | UNK/OBS647B/W2SMN | +4 321.09 |
| 2 | 2026-11-17 | MONTHLY SERVICE FEE | SVC/OBS647B/W2SMN | −57.50 |

### a. Wizard; the unknown credit stays unmatched: PASS (`02`–`04`)
- The upload read "Lines imported: 2 … Period: 2026-11-12 to 2026-11-17".
- Auto-match read "Auto-matched 0 lines, 2 lines remaining".
- The review step showed no refusal, and "0 / 2 lines resolved".
- Line 1 shows "#1 12 Nov 2026 DEPOSIT UNKNOWN REF 12345 … R 4 321,09 CR **Unmatched**". SQL: UNMATCHED, no
  `trust_transaction_id`, no suggestion. No row was posted.

### b. Record from statement: offered on the credit, not on the debit: PASS (`05`, `06`)

| Line | Choices offered (value → label) |
|---|---|
| **Debit** (line 2) | FUND_SWEEP "Interest paid to the Legal Practitioners' Fidelity Fund", BANK_CHARGE "Bank charge". **No UNIDENTIFIED_DEPOSIT.** |
| **Credit** (line 1) | FUND_INTEREST, CHARGE_REFUND, **UNIDENTIFIED_DEPOSIT "Unidentified deposit"** |

- Cancel on the debit dialog posted nothing.
- On the credit dialog nothing was pre-selected. Choosing Unidentified deposit showed the note "Held on the
  unidentified deposits ledger until you identify the owner." Alice then confirmed.

### c. The line shows the label: PASS (`07`)
- The badge reads exactly **"Recorded: Unidentified deposit"**. The raw enum is not in the line's text, and there was no
  error banner.
- SQL: the line is RECORDED with classification UNIDENTIFIED_DEPOSIT.
- Exactly +1 trust row and +1 item were created.

### d. Other trust creditors: Held item, UI = API = SQL: PASS (`08`; `api-d-after-record-*.json`)
**Item `142ca912…` against row `6a15a693…`:**

| Field | UI | API | SQL |
|---|---|---|---|
| Received | 12 Nov 2026 | 2026-11-12 | 2026-11-12 |
| Description | DEPOSIT UNKNOWN REF 12345 / UNK/OBS647B/W2SMN | `bankDescription`, `bankReference` same | same |
| Amount | R 4 321,09 | 4321.09 | 4321.09 |
| Age | **5 days** | `ageDays` **5**, `olderThanOneYear` false | expected = 2026-11-17 − 2026-11-12 = **5** |
| Status | **Held** (badge) | HELD | HELD, `resolved_date` null |

- **Both links are set.** The row (`UNIDENTIFIED_DEPOSIT`, RECORDED, NON_CLIENT, posted, dated 2026-11-12, ledger
  effect = cashbook effect = +4 321.09, on the unidentified-deposits creditor account) has `unidentified_deposit_id` =
  the item. The item's `deposit_transaction_id` = the row.
- `?status=HELD` returns `totalElements` 1.
- **Ledger balance:** StatCard R 4 321,09 = API 4321.09 = SQL 4321.09 (baseline + 4 321.09).
- **Books equation:** cashbook 290 821.09 = all creditors 290 821.09. The unidentified ledger is 4 321.09 and the Fund
  ledger 0.

### e. Unmatch reverses the item: PASS (`09`–`11`; `api-e-after-unmatch-*.json`)
The confirmation read: "Unmatch recorded line? This line was recorded as Unidentified deposit. Unmatching it reverses
the entry it created, dated 12 Nov 2026 — the line's date. The line returns to Unmatched. [Keep] [Unmatch and
reverse]". Alice confirmed.

**Afterwards:**
- **Line:** shows **Unmatched**, with no error. SQL: UNMATCHED, classification null, no link.
- **Item:** SQL status **REVERSED**, `resolved_date` **2026-11-12** (the line's date).
- **Original row:** REVERSED, `reversed_by_id` = contra `4da4c7a0…`.
- **Contra:** **REVERSAL**, dated **2026-11-12**, ledger effect −4 321.09, on the unidentified-deposits creditor,
  **`unidentified_deposit_id` = the same item**. Exactly one row was added.
- **The item's ledger nets to 0.00** (sum of `ledger_effect` over the rows naming it).
- **Table:** shows the item as **Reversed**, with the age cell "—". The API gives status REVERSED, `resolvedDate`
  2026-11-12, and `?status=HELD` returns 0.
- **Balances:** StatCard R 0,00 = API 0 = SQL 0.00. EQ-2 is back to 286 500.00 = 286 500.00.

### f. Clip probe at 1280 / 1440: PASS (`f-probe-*`; `obs/probe-clip.json`)
**Data on the page** (prepared through the UI by `obs.cjs` f-prep and `obs-fprep.cjs`):
- the Unidentified deposits table has 2 rows: the Reversed item, and a **Held** item of R 1 500,00 received 15 Nov,
  age "2 days";
- the Fund ledger has 1 row: a 17 Nov bank charge "Not recoverable · VAT R 7,50", running balance −R 57,50.

**Both tables sit in `div.relative.w-full.overflow-x-auto`, and every table width equals its wrapper:**

| Viewport | Wrapper (left–right) | Unidentified table width | Fund ledger table width |
|---|---|---|---|
| 1280 | 305–1231 | 926 | 926 |
| 1440 | 305–1391 | 1 086 | 1 086 |
| 1920 (reference) | — | 1 182 | 1 182 |

- **Unidentified deposits:** every `th` and `td` (Received, Description, Amount, Age, Status) is inside the wrapper and
  the viewport, with no `scrollWidth > clientWidth`.
- **Fund ledger:** every column, including Bank charge and **Running balance**, is inside; its right edge is 1231 at 1280
  and 1391 at 1440. Nothing is truncated. **647C's F-1 is no longer present.**

Final stack #1 state (`stack1-final-eq.out`): cashbook **287 942.50** = all creditors 287 942.50, made up of:
- 286 500.00 from the lifecycle;
- +1 500.00, the Held item;
- −57.50, the Fund charge.

The unidentified ledger is 1 500.00 and the Fund ledger −57.50.

### Logs
| Log | Result |
|---|---|
| Backend stack #1 | 348 INFO, 134 WARN, **0 ERROR** |
| Backend stack #1b | 326 INFO, 151 WARN, **0 ERROR** |
| Backend stack #2 | 272 INFO, 199 WARN, **0 ERROR** |

The WARN kinds are a subset of 647C's. A normalised diff of the `*-log-breakdown.txt` files finds no new kind. They are
the known noise: PDFBox fallback fonts, startup notices, expected 403s, and so on.

The frontend logged `⨯ Error: The destination stream closed early.` as follows:
- stack #1: 39 times;
- stack #1b: 34 times;
- stack #2: once.

All of these fell during the suites. This is the known navigation-abort noise.

## Found items
See `found-obs.md`. **No product defect was found in 647B's scope.**
- **N-2:** the lifecycle intermittent described above (a stale Time tab; not 647B; did not reproduce).
- **O-1 (pre-existing, out of scope):** a step-4 refusal leaves its uploaded statement `IMPORTED` with no reconciliation.
  That statement then shapes later period checks, and the wizard cannot be resumed or discarded from the UI. The refusal
  text was inferred from code plus DB, not observed.
- **O-2 (harness hypothesis):** a click on another line straight after a Record can be lost. This was not reproduced in
  isolation.

## Cleanup
- After each legal run I copied the curated screenshots:
  - run 1 → `lifecycle-run1-artifacts/` (16);
  - re-run → `lifecycle-artifacts/` (16);
  - depth-ii → `depth-ii-artifacts/` (10).
  
  I then ran `git clean -fd -- documentation/screenshots/legal-vertical`. No tracked file was modified by any run.
- All three stacks were torn down (`stack1-down.log`, `stack1b-down.log`, `stack2-down.log`); 0 e2e containers are
  running.
- `git status --short` shows only `?? qa_cycle/evidence/phase92/647B/gates/` (`tree-after.txt`). HEAD is 563ef6185.
