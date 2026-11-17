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
