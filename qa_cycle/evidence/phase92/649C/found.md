# 649C — found.md (Stage A, backend)

## Hand-forwards NOT built in 649C (brief §3.2)

| # | Source | Item | Why not 649C | Status |
|---|---|---|---|---|
| N-1 | 648A F-6 / 648E C-9 | Cashbook in debit after a section 86(3) placement (sufficiency model); proposed 12th flag `CASHBOOK_IN_DEBIT` | Orchestrator ruling: founder decision not made; the spec has eleven codes | **F-6 (648A; carried as 648E C-9) — still OPEN, founder ruling required; no cashbook-in-debit flag added in 649C (orchestrator ruling).** |
| N-2 | 647A found.md #1 (HF-7 / 649B F3) | A posted REVERSAL contra is an auto-match candidate (`findUnmatchedCandidates` does not exclude `REVERSAL`) | Not in 649.9–649.12 nor §16.3.9/§16.4.4; needs a reproduce-first test | carried → 652B |
| N-3 | 647B found.md F-1 | `openItems` on the creditor-accounts response | Not in 649C's integrity / reconciliation shapes; orchestrator ruling 2026-11-18: NOT built in 649C | carried — orchestrator call (add in a later slice or drop) |
| N-4 | 647D F-647D-1 / F-647D-7 | Unmatch-after-undo as-at dating | F-647D-1 fixed in 647D; F-647D-7 accepted trade-off | no action |
| N-5 | 647D fix-cycle-1 + F-647D-8 | Split `UnidentifiedDepositService` into a resolution service | Refactor deferred by ruling R7 | carried (refactor backlog) |
| N-6 | 647A #5 (HF-8 / 649B F5) | `manualMatch` refuses a `REVERSED` original | Not a 649C task | carried → 652B |
| N-7 | 646D F-646D-2 | Harness checks the dated legacy balance reads only as at today | Optional harness hardening | carried (next HARNESS slice) |
| N-8 | 646D F-646D-3 | Move `CreditorBreakdown` from `reconciliation` to `books` | Renames a pinned type; orchestrator ruling 2026-11-18: NOT built in 649C | carried — orchestrator call |
| N-9 | 647B F-4 / 647A #3 / 647C #4 | Future-dated statement line recordable; EQ-4 rule (d) for a future-dated item | Product/legal call not made; the integrity check skips rule (d) for an item received after the date (D-6) without changing recording | carried — product call |
| N-10 | 646C OBS O-1 / O-2 | Manual-match pane approved-only; period-end refusal after import + auto-match | 652B / 652C | carried |
| N-11 | 648A F-5 (OPEN-3) | s86(3) placement cover re-check at approval | Not a 649C task | carried |

## In-scope hand-forwards — assessment lines

- **H-1a** (646C CodeRabbit Major ×2): `completeReconciliation` now recalculates first (re-runs `requireStatementFitsPeriodEnd`). Reproduced red on unchanged `main` before the change — `red-H1a.txt`.
- **H-1b**: `TrustReconciliation.java` creditor-side comment no longer claims `integrity_flags` is unmapped (the field is mapped and documented); `TrustReconciliationService.requireStatementFitsPeriodEnd` comment completed ("…so a statement with no lines uses its period_end as its last line.").
- **H-2 / H-3** (`CLIENT_LEDGER_IN_DEBIT`): flag only, as at the date asked for. **No intermediate-date scan** — a client ledger that dipped below zero between two dates and recovered (649B S2, a back-dated credit contra) is NOT flagged unless it is in debit as at the date checked; no scan over intermediate dates is specified.
- **H-4** (648E R-2): assessed; the new reads `TrustIntegrityService.check` / `checkFirm` are `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`; joined to a caller's transaction (calculate, complete, dashboard) the caller's isolation applies; existing reads (dashboard position composition, reconciliation views) unchanged (out of scope).

## New observations outside scope (not fixed)

- **O-1 — two sources for EQ-5's interest term.** The production check (brief §9.2) takes interest from `trust_investment_interest` entries (net, `POSTED`); the harness oracle takes it from the `INV_INTEREST` rows (and their contras). They agree whenever interest is recorded through `TrustInvestmentService` (every production path). A test that writes an `INV_INTEREST` row straight to the table with no entry (`TrustLedgerPortalSyncServiceIntegrationTest:338`) would be flagged `INVESTMENT_BALANCE_MISMATCH` by the service but not by the oracle — that test never runs the check, so nothing fails; recorded so a future harness user knows why the two can differ.
- **O-2 — D-7 "over one year" edge.** The flag uses `received_date < D − 1 year` (spec); the unidentified-deposits list response uses `ageDays > 365` (`UnidentifiedDepositService`). They differ only across a 29 February. Not changed.
- **O-3 — the dashboard now names the warning for a never-matched statement line.** E.g. `TrustPositionIntegrationTest`'s primary account (a statement imported, its line never matched) carries `UNRESOLVED_STATEMENT_LINES` (WARNING) in `integrity.flags`. Intended (§16.3.9 dashboard mode); statements imported before 649C stay `IMPORTED` (nothing set `RECONCILED` before), so old tenants' completed reconciliations' statements still count until re-completed — relevant for OBS on an existing stack (brief trap 12).
