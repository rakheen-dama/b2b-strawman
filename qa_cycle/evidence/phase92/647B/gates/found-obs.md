# 647B observed gates — found items

PR #1796 @ 563ef6185. These are found items only. Nothing was fixed.

**No product defect in 647B's scope was found.** All of OBS a–f passed, the clip probe was clean at 1280 and 1440, and
both legal suites matched their baselines on the gate runs. The items below are either out of scope or harness notes.

## N-2 — lifecycle intermittent: the matter Time tab stays stale after a time entry is logged (not 647B; did not reproduce)

**Where:** lifecycle run 1 on stack #1 (`lifecycle-run1.log`, `lifecycle-run1-failures/`).

**What failed:** `day-75-complex.spec.ts:224` "Alice: Log 360min on Moroka estate matter".
- The 360-minute entry **was saved**. SQL read on stack #1: `time_entries` 360 min, billable, on "Prepare Liquidation &
  Distribution account", created 16:09:00 UTC.
- The matter header showed it too: "Unbilled WIP R 15 000,00 · 6,0h · 1 entry".
- For 15 s, though, the Fees → Time recording tab still showed only the older 1 h entry ("Report estate to Master of High
  Court 1h"). See `lifecycle-run1-failures/day-75-…/test-failed-1.png`.

**Cascade:** the serial Day-75 describe then skipped the rest of its tests, so "Apex Annual Returns" was never created.
`day-90-review.spec.ts:83` then failed looking for that matter. Together this is 2 failed and 12 did not run, which gave
91/105.

**Re-run:** a fresh stack (#1b, down and up) ran the whole lifecycle suite again. The spec could not be re-run alone: it
needs Days 0–60 on the same stack, and on stack #1 its own earlier steps had already added the adverse parties it asserts
are absent. The result was **105/105 passed** (`lifecycle.log`, `lifecycle-last-run.json` `"status": "passed"`).

**Why it is not 647B:** the PR touches no time-tracking, matter-tab or fee code (`git diff --stat main...HEAD`). It is
the same class as 647C's N-1 (a depth-ii Time tab stale on "No time tracked yet"). It is consistent with the known
next@16.2.6 lost-React-ping stall after a server action. A main comparison was not needed because the failure did not
reproduce on the PR head.

## O-1 — a reconciliation refused at step 4 leaves its uploaded statement behind (pre-existing, 646C/647A; out of scope)

**Where:** OBS f-prep attempt 1 (`obs/obs-fprep-attempt1-*`).

**What happened:**
1. A second one-line statement (an unknown credit dated today−1) was uploaded through a new wizard with period end today.
2. The review pane never appeared. No reconciliation row was created for that statement (SQL).
3. By `TrustReconciliationService` (~l.715), the cause is the 646C rule "Statement … has movements between this
   statement's last line and 17 Nov 2026": OBS statement 1 has a line dated today. **The UI text was not captured**
   (the attempt took no failure screenshot, and the 400 is not logged by the backend), so this cause is inferred from
   code plus DB and was not observed.

**The finding:** the uploaded statement stays `IMPORTED` with no reconciliation (`statement-OBS647B-FPREP-W8G9M.csv`,
period 2026-11-16).
- Its line then counts as "another imported statement" in later period checks: attempt 2 had to move to today−2 to avoid
  it.
- There is no UI to discard it, and an in-progress reconciliation cannot be resumed from the Reconciliation list (it
  only links to `/reconciliation/new`).

**Scope:** this is pre-existing wizard behaviour, not 647B's. It is noted for the reconciliation backlog.

## O-2 (harness, unproven hypothesis) — a click on another line straight after a Record can be lost

**Where:** `obs.cjs` f-prep (after a–e had all passed).

**What happened:**
1. Line 2 was recorded as a Bank charge (its badge appeared).
2. The script immediately clicked line 1 (Unmatched) to open its Record dialog.
3. `record-btn-<line1>` never rendered within 30 s, and line 1 was not selected (`obs/12-zz-fatal.png`).

**Hypothesis:** `ReconciliationSplitPane.handleRecord` calls `setSelectedLineId(null)` after `await onRecord(...)`. If the
lines refresh (the badge appears) before that await settles, a selection made in between is cleared.

This was **not reproduced** in isolation, and is unlikely at human speed. `obs-fprep.cjs` waits for the Record button and
retries the click once.

## Not newly observed / by design
- The Fund ledger running balance is **−R 57,50** after the f-prep bank charge. This is a Fund ledger in debit, which
  ADR-381 allows; the StatCard shows its in-debit hint.
- F-1 from 647C (Fund ledger Running balance clipped at 1280/1440) is **fixed** on this head. The probe found every Fund
  ledger column inside its `overflow-x-auto` wrapper at 1280 (926 px table = 926 px wrapper) and at 1440 (1 086 = 1 086),
  with nothing truncated.
