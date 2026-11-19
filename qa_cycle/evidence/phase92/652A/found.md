# 652A — found items (carried forward, not fixed)

Not absorbed under `tasks/phase92-decisions.md` › 652A D-1 (one fix per PR, root CLAUDE.md §7; precedent 650D / 650A F-12).
Line numbers are on the 652A branch head unless marked "main".

## CF-1 — no `wizardGeneration` guard in `handleMatch` / `handleExclude` / `handleUnmatch`

- Source: `qa_cycle/evidence/phase92/647A/found.md:90-97`; code `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/new/page.tsx:286-296` (match), `:319-329` (exclude), `:367-377` (unmatch).
- Defect: each handler awaits `calculateReconciliation(reconciliation.id)` and then calls `setReconciliation(...)` / now also `setFiguresStale(...)` / `setError(...)` with no generation check, so a Back-and-reset while the recalculation is in flight restores the old reconciliation (or its banner / stale marker) into the new wizard. `handleRecord` (647A) has the guard (`:407-410`).
- Not absorbed: D-1 (re-assigned out of 652A by the orchestrator scope ruling).
- Suggested owner: orchestrator / follow-up slice.

## CF-2 — step-4 "Unmatched Trust Transactions" lists transactions dated after the period end

- Source: `qa_cycle/evidence/phase92/647A/gates/found-obs.md:9-12` (O-2, "Suggested owner: 652A"); code `page.tsx:52` (`fetchUnmatchedTransactions`, no date bound), called at `:226` (`handleStartReview`), `:363` (`handleUnmatch`), `:447` (`handleComplete`).
- Defect: APPROVED unlinked transactions dated after the period end are offered for matching. Seen again by the 652A scout: the R1 250 payment dated 19 Nov was offered against a 16 Nov period end (`repro-browser.md`). Server-side figures are unaffected (calculated as at the period end).
- Not absorbed: D-1.
- Suggested owner: 652B re-spec (652B already edits `fetchUnmatchedTransactions` for RECORDED + APPROVED) or a follow-up.

## CF-3 — auto-match lock order; manualMatch vs auto-match deadlock (backend)

- Source: `qa_cycle/evidence/phase92/647A/found.md:68-89`.
- Defect: `autoMatchStatement` reads the statement and its UNMATCHED lines without a lock and sets `matched_count = alreadyMatched + autoMatched`, so a concurrent record / manual match can be overwritten; `manualMatch` (line → transaction) vs auto-match (transaction → line) can deadlock (40P01, surfaced as a generic server error). Fix order: line lock, then transaction, statement last with `incrementMatched`.
- Not absorbed: D-1 (backend; 652A is frontend-only).
- Suggested owner: orchestrator / follow-up.

## CF-4 — `handleComplete`'s refusal path ignores a failed recalculation

- Source: `page.tsx:450-453` (main `:414-416`): `if (calcResult.success && calcResult.data) setReconciliation(...)` with no `else`; also the `calculateReconciliation` call inside its `Promise.all` (`:444-448`) has no `.catch`, so a rejected request goes to the outer catch.
- Defect: after a Complete refusal, if the refreshing recalculation also fails, the refusal banner is shown but the summary's figures are not marked stale. 652A only clears the marker there on success.
- Not absorbed: D-1 (scout observation).
- Suggested owner: follow-up.
