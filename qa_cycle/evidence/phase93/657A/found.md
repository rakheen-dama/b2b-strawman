# 657A — found (outside the slice; not fixed here)

## F-657A-1 — legal-lifecycle run 1: two non-trust failures that did not reproduce

- **Where:** `e2e/tests/legal-lifecycle/day-75-complex.spec.ts:224` ("Alice: Log 360min on Moroka estate matter" — matter Fees → Time tab showed only `Report estate to Master of High Court 1h`, expected `/Prepare Liquidation & Distribution account\s*6h/` within 15 s) and `day-90-review.spec.ts:98` ("Matters list shows the lifecycle's six matters" — link `Apex Annual Returns <run-id>` not found in `matters-table`). 13 dependent tests did not run.
- **Run:** fresh stack built from `05812c045`, 2026-11-23 (`e2e/lifecycle.log`). Re-run on a second fresh stack: 105/105 (`e2e/lifecycle-run2.log`).
- **Relation to 657A:** none found — the PR touches no time-recording, matter-list or non-trust frontend code, and its backend changes are confined to `verticals/legal/trustaccounting/**`, `takeon` arch test and the V33 migration. Root cause not established (snapshots of run 1 were overwritten by the depth-ii run that followed on the same checkout).
- **Next:** if either recurs on a later slice's fresh-stack run, capture the `error-context.md` immediately and reproduce on `main` before filing as a product defect.
- **Resolved (orchestrator, 2026-11-23): this was a product defect, not a flake.**
  - **Cause:** `TimeSummaryPanel` copied its server props into state at mount.
    - Fees → Time opened in the window between the Log Time dialog closing and the `revalidatePath` refresh committing mounted the panel with pre-entry data.
    - It then ignored the refresh and stayed stale until a reload.
  - **Second failure:** the Day 90 one was a knock-on of the first. The serial day-75 describe skipped "Create 2nd Commercial matter for Apex", so that matter never existed.
  - **Reproduced in a real browser:** 2/12 iterations never caught up on the old build; 0/12 on the fix, which caught up in 1.8 s.
  - **Fix:** in PR __PR__. The evidence is `qa_cycle/evidence/f657a1/README.md`.

## F-657A-2 — `ReportAuditConnectionBudgetIntegrationTest` samples the whole connection pool (pre-existing flake)

- **Seen:** Backend CI on PR #1847 @ `087f76abd` (run 45214014341, first attempt): `execute_auditWriteHoldsAtMostOneConnection` — "connections checked out while the audit row is written; idle=0 samples={duringAuditWrite=2}" (expected idle + 1). 7893 / 7894.
- **Why it is not 657A:** the test (last changed by FU-12, #1833) reads the HikariCP pool's global active count; any concurrent thread holding a connection at the sampling instant bleeds in — a per-tenant scheduled action logged in the same second. Identical code passed CI on `05812c045`, the local full verify on `087f76abd` (7894/0/0) and the CI re-run (pass, 23m47s).
- **Fix (follow-up):** id-scope the measurement to the request thread's own connection (memory `automation_count_bleed_side_effect_flavor`), not the pool total.

## F-657A-3 — reconciliation pane: 50-page cap refuses instead of filtering server-side (CodeRabbit later round, Major; deferred)

`frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/new/page.tsx:~122` reads each status page by page (1000/page, cap 50) and filters cleared rows client-side (review fix F7, D-79). An account with > 50 000 rows in one status (lifetime, matched included) cannot open the review. Fail-safe (refuses, never mis-matches). Durable fix: a server-side "unmatched and not cleared at cutover" filter on the transactions list so the pane pages eligible rows only. Also: the architecture reviewer's Low note that the paging loop belongs in the server action (`lib/actions/comments.ts:45-69` precedent).

## F-657A-4 — `listClientLedgers` omits an investment-only take-on client (CodeRabbit later round, Major; deferred to 657C)

`ClientLedgerService.java:~267` builds the list from CLIENT rows only; a client taken on with only a `TAKEON_INVESTMENT` (s86(4)) has none, so it is missing from the list while `getClientLedger` shows the investment (D-80). Latent until 657C posts openings — **657C must close this before its OBS**.

## F-657A-5 — `obs/obs.cjs` requires `@playwright/test` by absolute path (CodeRabbit later round, Minor; deferred)

Evidence script only; resolve from the checkout's `frontend/` when the script is next reused.
