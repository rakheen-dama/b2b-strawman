# 646D observed gates — PR #1793 @ bea187443 (2026-11-17, SAST)

All commands ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-646D`, at HEAD
bea18744318e651b770d85595fd654c1ad3f37ec. Before the run the tree held only this new `gates/` dir
(`tree-before.txt`). The same was true after the run.

- No source edits, no commits, no Maven.
- OBS changed state only through the UI. Alice (owner) recorded, Bob (admin) approved, both signed in via
  `/mock-login`.
- psql was used read-only, as evidence.
- API reads used the browser context's own `mock-auth-token` against `:8081`. The frontend calls the backend
  from server actions, so a page network capture cannot see these calls.
- There was one API **write attempt**: the OBS d probe `POST …/transactions/fee-transfer` with a future date. It
  reproduces a refusal the UI had already shown, was refused with 400, and created nothing.
- Timings are in `timings.txt`.

## Stack builds
No e2e stack was running at the start; `e2e-down.sh` ran first anyway (`stack0-down.log`). Exactly one e2e
stack ran at a time, and each was wiped with `e2e-down.sh` before the next.

| Stack | Built | Flyway | Evidence |
|---|---|---|---|
| #1 (lifecycle + OBS) | "Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-646D" | V16–V19 success=t, V19 `phase92 trust recon breakdown` at 09:27:08 UTC; 0 trust txns, 0 recons before the suite | `stack1-build.log`, `stack1-proof.txt`, `stack1-backend.log` (276 KB), `stack1-frontend.log`, `stack1-down.log` |
| #2 (depth-ii) | same source path, fresh after the wipe | V16–V19 success=t, V19 at 09:33:56 UTC; 0 trust txns before the suite | `stack2-build.log`, `stack2-proof.txt`, `stack2-backend.log` (244 KB), `stack2-frontend.log`, `stack2-down.log` |
| #3 (OBS supplement: trend chart only, see O-2) | same source path, fresh after the wipe | V19 success=t at 09:38:38 UTC | `stack3-build.log`, `stack3-proof.txt`, `stack3-backend.log`, `stack3-down.log` |

Proof that every stack runs this worktree's code (`vproof.sh`, identical on all three):
- **Backend jar.** `/app/app.jar`:
  - `mcp/dto/McpTrustBalanceDto.class` contains `totalTrustCreditorsMinor` and `cashbookMinor`, both new in 646D.
  - `mcp/tool/TrustTools.class` contains `trust account or client`, the 646D review fix CR-1.
  - `transaction/TrustTransactionService.class` contains `The fee transfer date cannot be in the future`, 646D's
    `resolveFeeTransferDate`.
  - `ClientLedgerService$TotalBalanceResponse.class` contains `creditorBreakdown`.
  - The latest tenant migration is still `V19__phase92_trust_recon_breakdown.sql`; 646D adds none.
- **Frontend `.next`.**
  - `Fee transfer date (optional)` and `fee-transfer-date-input` (the RecordFeeTransferDialog chunk, plus 3 static
    chunks).
  - `trust-position-creditors` (the Home widget).
  - `The fee transfer date cannot be in the future` (the `lib/schemas/trust` chunks).
  - `Trust creditors` (the widget / overview / recon chunks).
- **Runtime.** Health is UP and `:3001` returns HTTP 200. Each stack has the single tenant schema
  `tenant_7d218705360b`.

## Gate 1a — E2E-LEGAL lifecycle (stack #1): PASS (observed)
**105 passed / 0 failed / 0 skipped in 3.0 min** (182 s wall, 09:27:31–09:30:33 UTC), rc 0. This matches the
baseline of 105. `.last-run.json` reads `passed` (`lifecycle-last-run.json`).

The run includes the trust paths 646D touches:
- "Alice: Trust dashboard overview" (day-07-14);
- the Day-30 Moroka R 8 500 fee transfer, recorded undated and approved by Bob;
- the Day-90 R 500 Sipho fee transfer, left awaiting approval.

Its screenshot `lifecycle-artifacts/trust-dashboard-overview.png` shows the overview hint "Trust creditors R 295 000,00".

- Log: `lifecycle.log`, summary in `lifecycle-summary.txt`.
- Trust lines (41): `lifecycle-trust-tests.txt`.
- Suite screenshots (16 files, 4.0 MB): `lifecycle-artifacts/`.

## Gate 1b — E2E-LEGAL depth-ii (stack #2, fresh): PASS (observed)
**40 passed / 4 skipped / 0 failed in 2.1 min** (127 s wall, 09:34:31–09:36:38 UTC), rc 0. This matches the
baseline of 40/4. The 4 skips are the baseline ones:
- `day-30-45-soa-writeoff.spec.ts:361` (fixme)
- `null-prefetch-probe.spec.ts:74` ×3 (opt-in)

Log: `depth-ii.log`, summary in `depth-ii-summary.txt`, `depth-ii-last-run.json`. Screenshots (10 files, 3.7 MB) in
`depth-ii-artifacts/`. The read-only sums after the run (`depth-ii-derived-sums.out`) match 646C/646E: cashbook
3 750.00 = every trust creditor 3 750.00 = client ledgers 3 750.00 (2 posted txns).

## Gate 2 — OBS (stack #1, after lifecycle): PASS (observed), with one UX observation (O-1)
**Script:** `obs.cjs` (headless Chromium library script, run tag `HT7QW`).

**Output:**
- `obs/obs-result.json` and `obs/obs-output.log`
- `obs/NN-*.png` and `.txt`, with crops of the widget, the trend and the dialog
- `obs/api-*.json`, every API read
- SQL in `obs-sums.sql` / `obs-sums.out`

**Window:** 09:31:24–09:32:40 UTC. **Console errors: 0. Page errors: 0.**

The first OBS attempt failed at its first locator, a strict-mode violation (O-3). It changed nothing (trust txn
count 6 before and after), and its log is kept as `obs/obs-output-run1-fatal.log`. I fixed the script and re-ran it.

**Dates.** Today is 2026-11-17 (SAST and UTC). The back date is 2026-11-14 (today − 3).

**Lifecycle books** (all dated 2026-11-17; 3 posted rows):
- Moroka deposit R 250 000;
- QuickCollect deposit R 45 000;
- Moroka fee transfer R 8 500, APPROVED.

Cashbook = trust creditors = client ledgers = **R 286 500,00**. There are 3 rows AWAITING_APPROVAL.

**Fee note used:** Acme Corp `INV-0001`, SENT, total R 8 625,00, paid 0. It has a single matter, "Website Redesign"
(ACTIVE, linked to Acme). Acme had no trust money. So that the back-dated transfer is not before the client's money
arrived, Alice first recorded a **back-dated deposit through the UI**: `DEP/OBS646D/HT7QW`, R 1 000,00 dated
**2026-11-12**, RECORDED and posted (`03-dep-backdated-deposit-recorded.png`).

### a. Home widget shows "Trust creditors": PASS (`01`, `08`)
The widget rows read "Bank cashbook", **"Trust creditors"**, "Last reconciliation" and "Awaiting approval". There is
no "Client ledgers" row. The headline is "R 0,00" with the **Balanced** badge.

UI = API (`GET /api/dashboard/trust-position`) = SQL (posted rows dated ≤ today, the
`TrustLedgerQueryRepository.position` terms). Every field matched in both states:

| Term | State 01 (lifecycle books) UI / API / SQL | State 02 (after deposit + 2 fee transfers) UI / API / SQL |
|---|---|---|
| Bank cashbook | 286 500,00 / 286 500 / 286 500.00 | 287 350,00 / 287 350 / 287 350.00 |
| Trust creditors (`creditorBreakdown.total`) | 286 500,00 / 286 500 / 286 500.00 | 287 350,00 / 287 350 / 287 350.00 |
| `clientLedgerTotal` = `creditorBreakdown.clientLedgers` | — / 286 500 / 286 500.00 | — / 287 350 / 287 350.00 |
| investment / Fund / unidentified / other / inter-account | — / 0 ×5 / 0 ×5 | — / 0 ×5 / 0 ×5 |
| Difference | R 0,00 Balanced / 0 / 0.00 | R 0,00 Balanced / 0 / 0.00 |

State 02 = 286 500 + 1 000 − 100 − 50. Known limit: before 647A, no product path posts a non-client creditor row,
so in the browser the creditor total equals the client ledgers. The non-client terms are 0 here and are proven only
in tests (builder R1–R3).

### b. Trust overview hint + trend series read "Trust creditors": PASS (`02`, `09`, plus supplement O-2)
**Overview hint.** The Trust Balance tile reads "Mathebula & Partners Trust Account cashbook balance ✓ **Trust
creditors R 286 500,00** (agrees with the cashbook)". In state 02 it reads R 287 350,00. The words "Client ledgers
R…" appear nowhere on the page.

It matches `GET /api/trust-accounts/{id}/total-balance` (`balance` 286 500 / 287 350, `creditorBreakdown.total`
the same), `cashbook-balance` (286 500 / 287 350) and SQL.

**Trend card.** The legend reads "Cashbook" and **"Trust creditors"**, and "Client ledgers" is absent. The sr-only
summary reads: "Trust balance trend: Cashbook from R 0,00 in Dec 2025 to R 287 350,00 in Nov 2026 (to date) …;
**Trust creditors** from R 0,00 in Dec 2025 to R 287 350,00 in Nov 2026 (to date) …".

`GET /api/dashboard/trust-balance-trend?months=12`:
- the last point is 2026-11, `periodEnd` 2026-11-17, partial, cashbook 286 500 / 287 350, `clientLedgerTotal`
  (= every trust creditor since 646D) 286 500 / 287 350;
- 2026-10 is 0 / 0.

Both equal the SQL position as at today and as at 2026-10-31.

**Chart drawing (O-2).** On stack #1 the screenshots caught the chart mid-draw (host load ≈ 100), so no series line
is visible in `02`/`09`. I ran a supplementary check on fresh stack #3 (`obs-trend-probe.cjs`,
`obs/trend-probe/`):
1. Alice recorded R 2 000 dated 2026-10-20 and R 5 000 dated today through the UI.
2. The series draw fully after about 1 s (`trend-15000ms.png`): flat at R 0, then R 2 000 at Oct 2026, then dashed
   to R 7 000 (to date). The "Trust creditors" line overlays the equal cashbook line.
3. API Oct 2 000 / 2 000 and Nov 7 000 / 7 000 (partial) = SQL month-ends.
4. Console 0, page errors 0.

### c. Back-dated fee transfer: PASS (`04`–`06`)
Steps (Trust → Transactions → Record Transaction → **Record Fee Transfer**, as Alice):
1. The new field **"Fee transfer date (optional)"** carries the help text "Leave blank for today. Use the date the
   bank shows the transfer.", `max="2026-11-17"`, and is initially empty.
2. Client = Acme, fee note = INV-0001, amount **100**, reference `FEE/OBS646D/HT7QW-C`, date **2026-11-14**.
   Record.

What I observed:

| Check | Result |
|---|---|
| UI row after recording | "**14 Nov 2026** FEE/OBS646D/HT7QW-C Fee transfer R 100,00 AWAITING APPROVAL" |
| API `GET …/transactions/{id}` | `transactionDate` **2026-11-14**, AWAITING_APPROVAL, `postedAt` null |
| SQL | `transaction_date` 2026-11-14, posted f, effects 0.00 |
| While awaiting | `cashbook-balance?asAt=2026-11-14` = 1 000 (unchanged: an unposted row does not count) |
| **Bob approves through the UI** (single-approval account) | row → "**14 Nov 2026** … R 100,00 APPROVED" |
| API after approval | `transactionDate` **2026-11-14** (unchanged), APPROVED, `postedAt` 09:32:20Z, `approvedBy` = Bob |
| SQL after approval | `transaction_date` 2026-11-14, posted t, ledger/cashbook effect −100.00, `approved_by` = Bob |
| Fee note after approval | INV-0001 PARTIALLY_PAID, `amount_paid` 100.00 |

As-at reads after approval. API (`cashbook-balance?asAt=` and `total-balance?asAt=`) = SQL at every date
(`obs/api-c-*-asAt-*.json`):

| asAt | cashbook | client ledgers | creditor total | Acme's ledger (SQL) |
|---|---|---|---|---|
| 2026-11-11 (before the deposit) | 0 | 0 | 0 | 0 |
| 2026-11-12 (deposit) | 1 000 | 1 000 | 1 000 | 1 000 |
| **2026-11-13** (day before the transfer) | **1 000** | 1 000 | 1 000 | 1 000 |
| **2026-11-14** (back date) | **900** | 900 | 900 | 900 |
| 2026-11-17 (today) | 287 400 | 287 400 | 287 400 | 900 |

`asAt=2026-11-13` vs `asAt=2026-11-14` differ by **exactly −100.00**, the fee amount, on both the cashbook and
the creditors. The deposit step on 11-11 → 11-12 is +1 000.00. Acme's ledger is never in debit as at any date.

### d. Future date refused: PASS — refused client-side, nothing created (`04-d1-*`); see O-1 for the wording
Steps: in the same dialog, with all fields filled, I typed **2026-11-18**. The input accepts the value. Clicking
Record Fee Transfer gives:
- Chromium's **native** constraint bubble, **"Value must be 2026/11/17 or earlier."**, which is visible in
  `04-d1-dialog-crop.png` (`validity.rangeOverflow` true);
- the dialog **stays open**;
- FEE_TRANSFER count **2 → 2** and trust txn count **7 → 7** (SQL).

The app's own sentence, "The fee transfer date cannot be in the future", is **not** rendered. The form is not
`noValidate`, so the browser check fires first (O-1).

A supplementary API probe of the same request returned **400** "Invalid fee transfer" / "The fee transfer date
cannot be in the future" and created nothing (counts still 2 / 7, `obs/api-d-future-date-probe.json`). Changing
the date to 2026-11-14 then recorded c.

### e. Default date: PASS (`07`)
Alice recorded a second transfer against the same (now PARTIALLY_PAID) fee note:
- amount **50**, reference `FEE/OBS646D/HT7QW-E`;
- the date field **left blank** (value "" at submit).

| Check | Result |
|---|---|
| UI row | "**17 Nov 2026** … R 50,00 AWAITING APPROVAL" |
| API | `transactionDate` **2026-11-17** (today, SAST) |
| After Bob approved through the UI | row → "**17 Nov 2026** … R 50,00 APPROVED" |
| API after approval | `transactionDate` 2026-11-17 |
| SQL | `transaction_date` 2026-11-17, APPROVED, posted t, effects −50.00 |
| Fee note | PARTIALLY_PAID, `amount_paid` 150.00 |

### Logs
| Log | Result |
|---|---|
| Backend stack #1 | 342 INFO, 183 WARN, **0 ERROR** (`stack1-log-breakdown.txt`). OBS window 09:31:24–09:32:40 UTC: 17 INFO, 32 WARN, 0 ERROR (`obs-window-log-breakdown.txt`). All 32 WARNs are PDFBox fallback-font lines from the receipts of the two approved fee-transfer payments |
| Backend stack #2 | 270 INFO, 199 WARN, **0 ERROR** (`stack2-log-breakdown.txt`) |
| Backend stack #3 | 186 INFO, 46 WARN, **0 ERROR**. Probe window: 4 INFO only (`trend-probe-window-log-breakdown.txt`) |

The WARNs are known noise:
- PDF fallback fonts and the font cache;
- startup bean notices;
- 5 expected 403s on time-summary/by-member (stack #1);
- tax-number-missing drafts;
- notification no-recipients;
- LibreOffice absent;
- the `uri` meter-tag cap.

The future-date refusal (400) logs nothing at WARN or above.

The frontend logged `⨯ Error: The destination stream closed early.` as follows:
- stack #1: 21 times, all between 09:27:36 and 09:30:21, during the lifecycle suite. **0 fell in the OBS window.**
- stack #2: once.
- stack #3: 0.

This is the same navigation-abort noise recorded in 646B, 646E and 646C.

Final EQ-2 on stack #1 (`stack1-final-eq2.out`): cashbook 287 350.00 = every trust creditor 287 350.00 = client
ledgers 287 350.00 (6 posted).

## Found items
No defect in 646D's scope (`found-obs.md`):
- **O-1 (UX, low):** a future fee-transfer date is refused by the browser's native `max` check ("Value must be
  2026/11/17 or earlier."). The app's sentence is unreachable in the dialog because the form is not `noValidate`.
  This is an orchestrator/UX call: accept it, or add `noValidate`.
- **O-2:** the trend chart was screenshotted mid-draw under load. The supplement on stack #3 shows it draws
  correctly. Not a defect.
- **O-3:** the first OBS run briefly saw two widget copies during a route transition. Harness only; one widget
  once settled.

F-646D-1 (no lower bound on the date) was not exercised: the transfer was deliberately dated after the deposit.

## Cleanup
- After each legal run I copied the suite screenshots to `lifecycle-artifacts/` and `depth-ii-artifacts/`, then ran
  `git clean -fd -- documentation/screenshots/legal-vertical` and `git checkout` on it.
- `frontend/test-results/` is ignored.
- All three stacks were torn down (`stack1-down.log`, `stack2-down.log`, `stack3-down.log`); no e2e container is
  running.
- Afterwards `git status --short` shows only `?? qa_cycle/evidence/phase92/646D/gates/`. HEAD is bea187443.
