# 646C — The trust equation as at a date (TrustBooksService, period-bounded reconciliation, two-comparison split pane)

Builder evidence. OBS, E2E-LEGAL and the full backend verify are the orchestrator's.

- `red-run.txt` — the red runs on HEAD `59691960e` before any production change (root CLAUDE.md §4):
  R1–R4 (BE — `createReconciliation` accepted a null statement, a statement with a line after the
  period end, a statement whose gap another statement covers, and a future period end), R5 (BE —
  figures "as at now": an April deposit counted in March's cashbook and as a March outstanding
  deposit, R4 000), R6 (BE, path 22 — a payment matched to an April line not outstanding as at 31
  March: adjusted bank R1 000 vs cashbook R600), R7 (BE, 649B F4 — a reversed matched deposit's
  contra not outstanding: adjusted bank R1 000 vs cashbook R0), R8 (FE — only the "Three-Way
  Balance Check", no comparisons / breakdown / line figures), R9 (FE, F-646E-1 — "FEE TRANSFER"
  printed instead of the registry label).
- No `found.md`: nothing out of scope was found beyond what the brief already lists (Non-scope,
  Discrepancies 12/13).

Builder gates (this worktree): Tier-2 targeted backend tests — 69 classes, 565 testcases, 0
failures / 0 errors / 0 skipped (`**/verticals/legal/trustaccounting/**/*Test` + the brief's 16
importer/arch classes); FE gate — lint 0 errors (78 pre-existing warnings), typecheck,
format:check, vitest 4 392 passed / 2 skipped (480 files), build — all green.

Rulings applied: O1 (24 code files), O2 (day-45 stops at upload; asserts step-1 copy + `max` =
today), O3 (titles/sentences pinned), O4 (Complete gated on `isBalanced` only; unresolved count
shown), O5 (`otherNonClient` tested in its own tenant), O6 (V19 backfills `difference` for every
row, edge pinned), O7 (`integrity_flags` added, unmapped), O8 (this folder).

## Review fixes (PR #1792)

- High 1 — `TrustPositionAsAtIntegrationTest.reconciliation_storesAndReturnsEveryCreditorTermInItsOwnComponent`:
  five distinct non-zero creditor terms (client 1 000, investment 200, Fund 50, unidentified 300,
  inter-account 700), every breakdown component, the stored columns (`creditor_total`,
  `difference`) and a `getReconciliation` re-read. Passes on HEAD; a swapped
  fund/unidentified setter makes it fail (`red-run.txt`).
- High 2 — rule 1 (account mismatch) and the boundaries of rules 2–4 in
  `TrustReconciliationServiceReconciliationTest` (pass on HEAD: coverage, not defects).
- Medium — `calculateReconciliation` re-runs `requireStatementFitsPeriodEnd`; red first
  (`red-run.txt` § "Review fix — calculate re-checks the period rules"). `completeReconciliation`
  is not re-checked: it acts on the stored `is_balanced` that the last calculate set (a draft never
  calculated stays unbalanced), so a statement imported after the last calculate is caught at the
  next calculate. Closing that window at complete is left to 649C (integrity flags / complete gates).
- Medium — day-45 spec comment now points at the planned Phase 92 exit spec (652E).

## Review notes — accepted / deferred

- N+1 in `listReconciliations` via `toReconciliationResponse` (two line queries per row) — accepted:
  a trust account holds one reconciliation per month, so volumes stay small.
- `CreditorBreakdown.of(...)` extraction — deferred: extract when 649C / 650B adds a second consumer.
- Nested records in `TrustReconciliationService` / `TrustBooksService` — accepted: the signature is
  spec-mandated (architecture §16.3.3).
- `books` ↔ `reconciliation` package cycle — accepted: it matches the existing
  `transaction` ↔ `reconciliation` cycle; `LayerDependencyRulesTest` stays green.
- CodeRabbit CR-1 (same-date statements both pass rule 3) — deferred: needs a duplicate /
  re-import statement policy beyond §16.3.4; recorded as F-646C-1 in `found.md` (owner 652C or a
  founder re-spec).
- CodeRabbit CR-2 (calculate re-checks statement fitness) — already fixed in 4fd2fd3e9.
- CodeRabbit CR-3 (a typed future period end passes step 1) — fixed: Next refuses a date after
  `todayInSA()` with the backend's wording inline; red first (`red-run.txt` § "CodeRabbit fix —
  future period end blocked in step 1").
