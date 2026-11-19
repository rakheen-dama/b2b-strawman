# 652A — found items (carried forward, not fixed)

Not absorbed under `tasks/phase92-decisions.md` › 652A D-1 (one fix per PR, root CLAUDE.md §7; precedent 650D / 650A F-12),
except CF-1 and CF-5, absorbed by 652A D-6 (CodeRabbit round 1 on PR #1815).
Line numbers are on the 652A branch head unless marked "main".

## CF-1 — no `wizardGeneration` guard in `handleMatch` / `handleExclude` / `handleUnmatch`

**Status: ABSORBED in 652A (D-6), commit 0d9ec379e.** Each handler now captures the generation before
the action and drops every post-await write once it has moved. Red→green: the four
`drops a … recalculation after a … that lands after the wizard was reset` cases
(`red-before-d6.txt`, `green-after.txt` › D-6). The helper fold (reviewer B) was not done: the
unmatch branch now diverges (CF-5), so one shared helper would not be clearly simpler.

- Source: `qa_cycle/evidence/phase92/647A/found.md:91-98`; code `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/new/page.tsx:286-296` (match), `:319-329` (exclude), `:367-377` (unmatch).
- Defect: each handler awaits `calculateReconciliation(reconciliation.id)` and then calls `setReconciliation(...)` / now also `setFiguresStale(...)` / `setError(...)` with no generation check, so a Back-and-reset while the recalculation is in flight restores the old reconciliation (or its banner / stale marker) into the new wizard. `handleRecord` (647A) has the guard (`:407-410`).
- Not absorbed: D-1 (re-assigned out of 652A by the orchestrator scope ruling).
- Added by the PR #1815 review (reviewer B): 652A's new failure branches make the race wider. A
  Back-and-reset during a recalculation can now also restore a stale banner and out-of-date marker
  into the new wizard, not only the old reconciliation. This strengthens the case for the guard.
  When CF-1 is fixed, fold the four copies of the success/failure branch (match `:289-295`,
  exclude `:322-328`, unmatch `:370-376`, record `:411-417`) into one helper that takes the
  generation and the action's verb (reviewer B Medium).
- Suggested owner: orchestrator / follow-up slice.

## CF-2 — step-4 "Unmatched Trust Transactions" lists transactions dated after the period end

- Source: `qa_cycle/evidence/phase92/647A/gates/found-obs.md:9-13` (O-2, "Suggested owner: 652A"); code `page.tsx:52` (`fetchUnmatchedTransactions`, no date bound), called at `:226` (`handleStartReview`), `:363` (`handleUnmatch`), `:447` (`handleComplete`).
- Defect: APPROVED unlinked transactions dated after the period end are offered for matching. Seen again by the 652A scout: the R1 250 payment dated 19 Nov was offered against a 16 Nov period end (`repro-browser.md`). Server-side figures are unaffected (calculated as at the period end).
- Not absorbed: D-1.
- Suggested owner: 652B re-spec (652B already edits `fetchUnmatchedTransactions` for RECORDED + APPROVED) or a follow-up.

## CF-3 — auto-match lock order; manualMatch vs auto-match deadlock (backend)

- Source: `qa_cycle/evidence/phase92/647A/found.md:69-90`.
- Defect: `autoMatchStatement` reads the statement and its UNMATCHED lines without a lock and sets `matched_count = alreadyMatched + autoMatched`, so a concurrent record / manual match can be overwritten; `manualMatch` (line → transaction) vs auto-match (transaction → line) can deadlock (40P01, surfaced as a generic server error). Fix order: line lock, then transaction, statement last with `incrementMatched`.
- Not absorbed: D-1 (backend; 652A is frontend-only).
- Suggested owner: orchestrator / follow-up.

## CF-4 — `handleComplete`'s refusal path ignores a failed recalculation

- Source: `page.tsx:450-453` (main `:414-416`): `if (calcResult.success && calcResult.data) setReconciliation(...)` with no `else`; also the `calculateReconciliation` call inside its `Promise.all` (`:444-448`) has no `.catch`, so a rejected request goes to the outer catch.
- Defect: after a Complete refusal, if the refreshing recalculation also fails, the refusal banner is shown but the summary's figures are not marked stale. 652A only clears the marker there on success.
- Not absorbed: D-1 (scout observation).
- Suggested owner: follow-up.

## CF-5 — `handleUnmatch` re-reads the unmatched transactions before recalculating, with no catch

**Status: ABSORBED in 652A (D-6), commit 0d9ec379e.** The re-read and the recalculation run side by
side (`Promise.all`, re-read `.catch(() => null)`): a failed re-read no longer skips the
recalculation, the stale marker follows only the recalculation, and the banner adds "The unmatched
transactions could not be refreshed and may be out of date." Red→green: `still recalculates after
an unmatch when the transactions cannot be re-read`, `marks the figures stale after an unmatch when
neither the re-read nor the recalculation works`.

- Source: PR #1815 review (reviewer A Medium); code `page.tsx:361-364` (main `:331-334`):
  `setUnmatchedTransactions(await fetchUnmatchedTransactions(selectedAccountId))` runs after the saved
  unmatch and before the recalculation (`:366-377`), with no catch of its own.
- Defect: if that read rejects, the outer catch (`:381-383`) shows a bare "Failed to fetch". No
  recalculation runs and the out-of-date marker is not set, so the summary can show stale figures
  as current after a saved unmatch.
- Pre-existing on main (same order, no catch). Not absorbed: D-1 (one fix per PR).
- Suggested owner: follow-up, with CF-1 (same handler family).

## Observations (no action in 652A)

- **O-1 (PR #1815 review, reviewers A and B, Medium).** The out-of-date line
  (`ReconciliationSplitPane.tsx:600-608`, `role="status"`, data-testid `reconciliation-figures-stale`)
  is mounted with its text already in it. Screen readers do not reliably announce a live region
  that is inserted together with its content, so the announcement may be missed. The line also has
  no icon; only amber text marks it. Candidate for Phase 91 a11y/design.
- **O-2 (652A OBS, hypothesis, unverified).** After auto-match on the OBS stack, the payments line
  in statement S1 was still `UNMATCHED` in the wizard but already carried the payment's id on the
  `bank_statement_lines` row (read-only SELECT, `obs/README.md`). It may be a stored suggestion
  rather than a link. Not investigated in 652A; it predates this slice (652A changes no matching
  code). Needs a reproduction before anyone treats it as a defect.
- **O-3 (652A OBS run 2, cosmetic).** When both the re-read and the recalculation fail after an
  unmatch, the banner joins the two messages with a single space. A server error without a full
  stop reads "…could not be recalculated: Failed to fetch The unmatched transactions could not be
  refreshed…" (`obs-run2/obs-r3-4-both-failed.png`). Refused-recalculation errors end in a full stop,
  so it reads correctly there. Not changed in 652A: it only shows when both requests fail at the
  network level. Candidate for the Phase 91 wording pass.
- **O-4 (652A OBS run 2, harness limit).** Next.js runs server actions one at a time, so in the
  browser a held calculate also holds the new statement's upload. The r2 success case therefore
  cannot show that the guard stops the old reconciliation being restored. The vitest
  `drops a $outcome recalculation …` case pins that; the refused case (banner/stale guard) is observed.
