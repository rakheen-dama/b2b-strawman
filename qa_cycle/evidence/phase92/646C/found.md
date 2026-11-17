# 646C — defects found outside the slice (reported, NOT fixed)

Phase rule: a defect outside the stated scope is written here, reported, and re-specced — never added.

## F-646C-1 — Two same-account statements ending on the same date both fit the period end (deferred)

- PR #1792 CodeRabbit (CR-1, Major). Rule 3 in `requireStatementFitsPeriodEnd`
  (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/TrustReconciliationService.java:624-661`)
  refuses only when another statement of the account has a line dated strictly **after** the
  selected statement's last line and on or before the period end
  (`BankStatementLineRepository.findOtherStatementsWithLinesBetween`, `l.transactionDate > :after`).
- Scenario: statement A is an intraday export taken on 31 March — last line 31 March, closing
  balance X. Statement B is the full-day export of the same account — last line 31 March, closing
  balance Y (more 31 March lines). For period end D = 31 March neither has a line after the other's
  last line, so both pass rule 3; reconciling against A uses bank balance X and leaves B's later
  31 March movements as "outstanding" although the bank had already processed them. An identical
  re-import of one file (two statements, same lines, same closing) also passes twice.
- Why deferred: the fix needs a product policy for duplicate / overlapping / re-imported
  statements (refuse the import? pick the latest-imported? require the user to retire one?), which
  goes beyond the four rules of architecture §16.3.4. The import path deliberately allows a
  re-import of a same-named file (G88-F33, `TrustReconciliationService.java:176-178` — each import
  gets its own S3 key), so a blanket "same last date" refusal would contradict that decision.
- Suggested owner: **652C** (wizard and import defects), or a founder re-spec of the statement
  import / selection policy. No code change in 646C.

## Handed to their owning slices at merge (PR #1792 second CodeRabbit pass on 92b4b5550 + final reviews)

Already specified in the architecture; recorded here so the owning builder picks them up. No code change in 646C.

- **649C — completion gate.** CodeRabbit (Major ×2): `completeReconciliation` trusts the stored
  `is_balanced` and does not re-run `requireStatementFitsPeriodEnd`, so a statement imported or a
  transaction posted after the last calculate is not caught at complete. Architecture §16.3.4: the
  gate "recalculates first (never trusts stored figures)" and evaluates G1–G4. 649C's tests should
  cover both scenarios (a later-imported statement covering the period end; a reversal dated inside
  the period posted after calculate).
- **652A — wizard reports a failed recalculation.** CodeRabbit (Minor): `handleMatch`,
  `handleExclude`, `handleUnmatch` in `reconciliation/new/page.tsx` ignore
  `{ success: false }` from `calculateReconciliation`. Since 646C, calculate can refuse (period
  rules re-checked), so the stale summary is now reachable. Exactly 652A's row in §16.13.
- **649C — comment nits (architecture reviewer, Medium).** `TrustReconciliation.java` comment on
  `integrity_flags` reads as if mapped in 646C (it is created in V19, mapped/written by 649C);
  `TrustReconciliationService.java` ~555-557 comment cut mid-sentence. 649C edits both files.
- **OBS notes** (`gates/found-obs.md`): O-1 manual-match pane lists approved transactions only
  (→ 652B); O-2 the period-end refusal comes after import + auto-match (→ 652C, or no action).
