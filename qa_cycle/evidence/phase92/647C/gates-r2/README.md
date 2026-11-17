# 647C observed gates, round 2 — PR #1795 @ df8550113 (2026-11-17, SAST)

All commands ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-647C`, at HEAD
df85501137f690a42d680e1867605d0332e933ef.

- Round 1 at 6e485f412 is in `../gates/`, untouched. df8550113 changes only
  `frontend/components/trust/FundLedgerTable.tsx`, the F-1 fix.
- Before the run the tree held only the untracked `gates/` and `gates-r2/` (`tree-before.txt`). After the run it held
  the same (`tree-after.txt`).
- No source edits, no commits, no pushes, no Maven. `pgrep -fl surefire` was empty before every e2e suite.
- The only other containers on the machine belonged to an unrelated project (temurin, kafka, postgres, socat and
  testcontainers ryuk), all on ephemeral ports. None used an e2e port.
- OBS changed state only through the UI: Alice (owner) via `/mock-login`. psql was used read-only. API reads used the
  browser context's own `mock-auth-token` against `:8081`. There were **no API writes**.
- Node: nvm node 20.11.1 and `/opt/homebrew/bin/pnpm`, with `PLAYWRIGHT_BASE_URL=http://localhost:3001`. Flags were
  passed without `--`.
- Scripts are round 1's, copied here and adapted:
  - `obs.cjs`: only its output path changed;
  - `vproof.sh`: adds the F-1 build markers;
  - `probe-clip.cjs`: rewritten for the F-1 acceptance criteria;
  - `logcount.sh`: unchanged.
- Timings are in `timings.txt`. Every `*.log` is summarised in `logs-summary.txt`.

## Stack builds

`e2e-down.sh` ran first (`stack0-down.log`). Exactly one e2e stack ran at a time, and each was wiped with
`e2e-down.sh` before the next.

| Stack | Built | Flyway / initial state | Evidence |
|---|---|---|---|
| #1 (lifecycle + OBS + F-1 probe) | "Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-647C", e2e-up rc 0 (14:03:25–14:05:40 UTC) | V17–V20 success=t; 1 trust account, 2 creditor accounts, 0 trust txns, 0 statements, 0 recons | `stack1-build.log`, `stack1-proof.txt`, `stack1-backend.log`, `stack1-frontend.log`, `stack1-down.log` |
| #2 (depth-ii) | same source path, fresh after the wipe, rc 0 (14:16:01–14:17:06 UTC) | same; 0 trust txns before the suite | `stack2-build.log`, `stack2-proof.txt`, `stack2-backend.log`, `stack2-frontend.log`, `stack2-down.log` |

**Proof the stacks run this worktree's code** (`vproof.sh`; stack #2's proof equals stack #1's apart from the Flyway
timestamps):
- **Backend jar.** All of round 1's 647C markers are present: the parser keyword table, `suggestedClassification`, the
  creditor-ledger endpoint path, `CreditorLedgerRow.getChargeVatAmount`, `creditorLedgerRows` and
  `setSuggestedClassification`. The latest tenant migration is V20.
- **Frontend `.next`.** All of round 1's 647C strings are present.
- **F-1 fix (df8550113).** It is in the SSR chunk `frontend_17gbpfc._.js` and the static chunk `3zibpceod4jyz.js`:
  - `Badge,{variant:"neutral",className:"shrink whitespace-normal",children:e.original.typeLabel}`
  - `{id:"charge",meta:{label:"Bank charge",cellClassName:"whitespace-normal"}`
  - `" · ",(0,t.jsxs)("span",{className:"whitespace-nowrap",children:["VAT ",…`

  The compiled CSS `3i408hj-wm-lu.css` has a `.shrink{` rule.
- **Runtime.** Health is UP, `:3001` returns HTTP 200, the single tenant schema is `tenant_7d218705360b`, and
  `bank_statement_lines.suggested_classification` is present.

## Gate 1a — E2E-LEGAL lifecycle (stack #1): PASS (observed)

**105 passed / 0 failed / 0 skipped in 3.1 min** (14:05:53–14:08:59 UTC), rc 0. This matches the baseline of 105.
`.last-run.json` reads `passed` (`lifecycle-last-run.json`).

- Log: `lifecycle.log`, summary in `lifecycle-summary.txt`.
- Trust lines (50): `lifecycle-trust-tests.txt`.
- Suite screenshots (16): `lifecycle-artifacts/`.

## Gate 1b — E2E-LEGAL depth-ii (stack #2, fresh): NOT a clean PASS. Run 1 had 1 intermittent failure outside 647C scope; the re-run is green

| Run | Result | Window | Evidence |
|---|---|---|---|
| Run 1, full suite | **32 passed / 1 failed / 4 skipped / 7 did not run in 2.0 min**, rc 1 | 14:17:15–14:19:18 UTC | `depth-ii.log`, `depth-ii-summary.txt`, `depth-ii-last-run.json` (status `failed`), `depth-ii-run1-failure/` |
| Re-run, once | `day-05-disbursements.spec.ts` + `day-30-45-soa-writeoff.spec.ts`: **20 passed / 1 skipped / 0 failed in 54.7 s**, rc 0 | 14:20:11–14:21:07 UTC | `depth-ii-rerun.log`, `depth-ii-rerun-summary.txt`, `depth-ii-rerun-last-run.json` (status `passed`) |

**Run 1.** Baseline is 40 passed / 4 skipped.
- **Failed:** `day-30-45-soa-writeoff.spec.ts:227`, "[setup] Alice (Thandi): Log 2h on Sipho's matter — letter of
  demand". After the Log Time dialog closed, the matter's Finance → Time tabpanel still read "No time tracked yet"
  after 15 s.
- **Did not run:** the 7 later tests of that serial group.
- **Skipped:** the 4 baseline skips (`:361` fixme and `null-prefetch-probe.spec.ts:74` ×3).
- The time entry **was** created: the backend logged `TimeEntryService Created time entry 64ac7a41…` at 14:18:35.70,
  and read-only SQL shows 120 min on "Letter of demand". The read was stale; the write was fine.

**Re-run.** A same-stack retry-0 re-run of the spec alone is invalid by the spec's design: its rate override would
overlap the first and it would find 4h, not "2h". The spec also needs Day 5's run state. So I re-ran it together
with its declared Day 5 prerequisite, which starts a fresh run id.
- The previously failing test passed in 4.3 s.
- The skip is the baseline fixme `:361`.

**Scope.** The failure is in the matter Time tab and time tracking, which 647C does not touch. df8550113 changes only
`FundLedgerTable.tsx`. Round 1 ran this test green. Logged as **N-1** in `found-obs.md`, with a hypothesis
(next@16.2.6 lost ping) that I did not verify.

**Screenshots** (10): `depth-ii-artifacts/`. They come from run 1 and the re-run, which overwrite the same files.

**Read-only sums** after run 1 and the re-run (`depth-ii-derived-sums.out`):
- cashbook 7 500.00 = all creditors 7 500.00 = client ledgers 7 500.00;
- 4 posted txns and 0 statements;
- this is 2 × round 1's 3 750.00 / 2, because two Day 5 runs posted on this stack.

## Gate 2 — OBS (stack #1, after lifecycle): PASS on a–f (observed)

**Script:** `obs.cjs`, the same as round 1 apart from its output path. Run tag **RQH2F**; it ran once, rc 0
(14:09:12–14:13:18 UTC).

**Output:**
- `obs/obs-result.json` (with `verdict`) and `obs/obs-output.log`
- `obs/NN-*.png` and `.txt`, with `*-crop.png` element crops
- `obs/api-*.json`
- SQL in `obs-sums.sql` / `obs-sums.out`
- the uploaded statement, `obs/statement-OBS647C-RQH2F.csv`

**Console errors: 0. Page errors: 0.**

**Starting state.** There were 6 trust rows from the lifecycle seed, and the Fund ledger was empty (`01-*`). The
statement was the same 6-line Standard Bank CSV as round 1, dated 2026-11-10 … 2026-11-17. Line 6 is the seed fee
transfer `FEE/MOROKA/mwkrm7wvb962`. The wizard showed "Lines imported: 6 … Period: 2026-11-10 to 2026-11-17" (`02`).

### a. Parser suggestions stored at import: PASS

SQL right after the upload stored these suggestions:

| Line | `suggested_classification` |
|---|---|
| 1 | FUND_INTEREST |
| 2 | FUND_SWEEP |
| 3 | BANK_CHARGE |
| 4 | CHARGE_REFUND |
| 5 | **null** |
| 6 | BANK_CHARGE |

All lines were UNMATCHED, and the trust rows stayed at 6 → 6.

### b. Auto-match clears the suggestion: PASS (`03`, `04`)

The wizard showed "Auto-matched 1 lines 5 lines remaining for manual review" and "1 / 6 lines resolved".

Line 6 changed as follows:
- it became AUTO_MATCHED;
- it is linked to `8d65a67a…`, the FEE_TRANSFER R 8 500;
- its `suggested_classification` went from **BANK_CHARGE to null**.

Lines 1–5 kept their suggestions. Rows stayed at 6 → 6. The API agrees with SQL. All 6 `steps.b.checks` are true.

### c. Pre-selection in the dialog: PASS (`05`–`14`)

**Look pass** (open, then Cancel):

| Line | Checked | Note "Suggested from the line's description — check it before recording." | Charge fields at once |
|---|---|---|---|
| 1 | FUND_INTEREST | yes | — |
| 2 | FUND_SWEEP | yes | — |
| 3 | BANK_CHARGE | yes | **yes** |
| 4 | CHARGE_REFUND | yes | — |
| 5 | **none** | **absent** | — |

Nothing was posted by looking: rows stayed at 6.

**Record pass.** Each click confirmed the pre-selected choice and created exactly one row:

| Line | Badge | Row |
|---|---|---|
| 1 | "Recorded: Interest received for the Legal Practitioners' Fidelity Fund" | `e5eae8c2…` FUND_INTEREST +1 234.56 |
| 2 | "Recorded: Interest paid to the Legal Practitioners' Fidelity Fund" | `bc26a837…` FUND_SWEEP −1 119.56 |
| 3 | "Recorded: Bank charge" | `7931fd5b…` BANK_CHARGE −57.50, recoverable, VAT 7.50 |
| 4 | "Recorded: Bank charge refunded from the business account" | `ccec35a1…` CHARGE_REFUND +57.50 |

All `recordChecks` are true.

### d. Fund ledger rows with running balance: PASS (`15`)

There are 4 `fund-ledger-row`s: UI 4 = SQL 4 = API `totalElements` 4. They are newest first, and UI = API = SQL row
by row.

| Row (newest first) | Running balance (UI) |
|---|---|
| 13 Nov refund | R 115,00 |
| 12 Nov bank charge, charge cell "Recoverable · VAT R 7,50" | R 57,50 |
| 11 Nov sweep | R 115,00 |
| 10 Nov interest | R 1 234,56 |

**The top running balance is R 115,00. It equals the Fund StatCard (115), the API card (115) and the SQL balance
(115).** All 8 `steps.d.checks` are true.

### e. Out-of-range page: PASS (`16`, `17`)

`?page=50` shows "No entries on this page. Go to the first page":
- 0 rows;
- not the empty-ledger message;
- the API returns `content: []`, `number` 50, `totalElements` 4, `totalPages` 1.

The link loaded `?page=0`, which shows 4 rows. All 5 checks are true.

### f. Unmatch: PASS (`18`–`21`)

The dialog read "Unmatch recorded line? This line was recorded as Bank charge. Unmatching it reverses the entry it
created, dated 12 Nov 2026 — the line's date. …".

After the unmatch:
- the original `7931fd5b…` is **REVERSED**, with `reversed_by` the contra `131ff4db…` (REVERSAL, dated
  2026-11-12, effect +57.50);
- line 3 is UNMATCHED with classification null and the suggestion kept as BANK_CHARGE;
- trust rows went 10 → 11.

The Fund ledger now has 5 rows (UI = API = SQL):

| Row (newest first) | Badge | Running balance |
|---|---|---|
| 13 Nov refund | — | **R 172,50** |
| 12 Nov REV-SVC Reversal | — | R 115,00 |
| 12 Nov SVC Bank charge | **Reversed** | R 57,50 |
| 11 Nov sweep | — | R 115,00 |
| 10 Nov interest | — | R 1 234,56 |

**The top running balance is R 172,50. It equals the StatCard (172.5), the API (172.5) and the SQL balance
(172.50).** All 13 `steps.f.checks` are true. Re-opening line 3 pre-selected Bank charge again, with the note and
the charge fields, and Cancel posted nothing (11 → 11).

Final EQ-2 on stack #1 (`stack1-final-eq.out`): cashbook **286 672.50** = all creditors 286 672.50, and the Fund
ledger is **172.50**. These figures are identical to round 1.

### F-1 re-probe (stack #1, after OBS): **F-1 FIXED** (observed)

Script: `probe-clip.cjs`, read-only, at 1280×1100, 1440×1100 and 1920×1100. Results in `obs/probe-clip.json` and
`obs/probe-clip-output.log`; the full table and analysis are in `found-obs.md`.

| Viewport | Table width | Wrapper clientWidth | Running balance header right / max balance right | Wrapper visible right | Result |
|---|---|---|---|---|---|
| 1280 | 926 | 926 | 1231 / 1231 (span 1223) | 1231 | inside |
| 1440 | 1086 | 1086 | 1391 / 1391 (span 1383) | 1391 | inside |
| 1920 | 1182 | 1182 | 1679 / 1679 (span 1671) | 1679 | inside |

All seven checks are true at all widths:
- `tableFitsWrapper`
- `balanceHeaderInsideVisible`
- `everyBalanceInsideVisible`
- `balanceOneLine`
- `amountOneLine`
- `badgesReadable`
- `noPageHorizontalOverflow`

**Crops:** `obs/22-probe-fund-ledger-{w}-crop.png` and `-rightcols.png` show "R 172,50" … "R 1 234,56" and
"RUNNING BALANCE" in full at every width. Type badges wrap between words: 3 lines at 1280, 2 lines at 1440 and 1920.

**Probe run 1** (`obs/probe-clip-run1.json`) is kept. Its `amountOneLine: false` came from counting the Amount
cell's arrow svg as a line; run 2 counts text nodes only.

Probe console errors: 2, both `MockAuthContextProvider: failed to fetch userinfo TypeError: Failed to fetch` on
`/dashboard` immediately after mock login (one in run 1, two in run 2). This is the known spurious post-login userinfo
abort; it is not on the Fund ledger page. Page errors: 0.

### Console, page and log errors

| Source | Result |
|---|---|
| OBS | console **0**, page **0** |
| F-1 probe | console 2 (the mock-auth userinfo abort on `/dashboard`), page 0 |
| Backend, OBS + probe window 14:09:12–14:14:55 UTC | **18 INFO, 0 WARN, 0 ERROR** (`obs-window-log-breakdown.txt`) |
| Backend stack #1 | 343 INFO / 151 WARN / **0 ERROR** (`stack1-log-breakdown.txt`). The WARN kinds and counts are identical to round 1's stack #1 (normalised diff empty). |
| Backend stack #2 | 295 INFO / 199 WARN / **0 ERROR** (`stack2-log-breakdown.txt`). The WARN kinds and counts are identical to round 1's stack #2; INFO is higher because of the re-run. |
| Frontend | `⨯ Error: The destination stream closed early.`: stack #1 ×32, all at 14:05:57–14:08:46 (the lifecycle window, 0 in the OBS window); stack #2 ×1 at 14:18:20. This is the known navigation-abort noise. |

## Found items

See `found-obs.md`.
- **F-1: FIXED** (measurement table above).
- **N-1 (not 647C scope, intermittent):** in depth-ii run 1, the matter Time tab showed a stale "No time tracked yet"
  after a successful 2h log. The re-run was green.
- **O-4 (low, cosmetic, predates df8550113):** Reference values break mid-token because of
  `[overflow-wrap:anywhere]`. The new column widths move where the breaks land.
- O-1, O-2 and O-3 from round 1 are unchanged.

## Cleanup

- After each legal run I copied the suite screenshots to `lifecycle-artifacts/` (16) and `depth-ii-artifacts/` (10).
  I then ran `git clean -fd -- documentation/screenshots/legal-vertical` and `git checkout` on it.
- Both stacks were torn down (`stack1-down.log`, `stack2-down.log`); 0 `e2e-*` containers are running.
- The volumes `compose_e2e_postgres_data` and `compose_e2e_localstack_data` that `docker volume ls` still lists were
  created 2026-02-28 under compose project `compose`, not this stack's project `docteams-e2e`. They were left
  untouched.
- `git status --short` shows only `?? qa_cycle/evidence/phase92/647C/gates/` and `?? …/gates-r2/`
  (`tree-after.txt`). HEAD is df8550113.
