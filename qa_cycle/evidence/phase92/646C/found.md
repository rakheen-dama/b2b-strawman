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
