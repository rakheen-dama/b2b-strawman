# G89 fix initiative — tracker

Fixes every defect from the Phase 89 exit gate (`tasks/defects-2026-11-06-phase89-exit.md`, register `qa_cycle/evidence/phase89-rewalk/gaps.md`) as ordinary bug PRs. Exit bar: 0 HIGH + 0 MEDIUM open, every scenario mismatch ruled, proven by the suites on a fresh stack + a targeted Keycloak re-walk.

**Execution rule:** development is strictly sequential, with one builder and one PR in flight. Scouting the next batch may overlap with the current build.

**Status vocabulary:** `OPEN` · `IN PROGRESS` · `PR #n` · `MERGED-AWAITING-VERIFY` · `VERIFIED` (browser / DB / Mailpit observed, with evidence path).

## Founder decisions (2026-11-06)

| # | Decision |
|---|---|
| D1 | One PR per batch is authorised (CLAUDE.md §7 same-family clusters). Each gap gets its own reproduce-first test and its own commit inside the PR. |
| D2 | Scope = everything listed: the 27 G89 ids, all §4 carried follow-ups, and the carried Phase 88 LOWs. |
| D3 | §6: amend all four scenarios (F4, F24, F25, F27), with a file:line justification. |
| D4 | G89-F11 stays HIGH. |
| D5 | Batch A option (i): interest credits are allocated to matters pro-rata by balance-days. Child table `interest_allocation_lines`, tenant V12, ADR-366 amends ADR-234. |
| D6 | No backfill of legacy client-level INTEREST_CREDIT rows. There are no live tenants; dev/e2e stacks are wiped and the VPS demo is reseeded. |
| D7 | Trust rows with no matter → G89-F28, decided later. |
| D8 | A matter closed before an interest run, but which earned interest during the period, is still credited. |
| D9 | F16: SoA closing balance on a **fee-note basis**, using a shared helper with the closure letter and the Outstanding tile. Unbilled WIP shows as a separate line, not counted in the balance. |
| D10 | 632C: keep the current receipt numbering (R1 is never reissued). Only the identical file names are fixed. |
| D11 | 632A(a)/(c): hide dedicated-flow templates (SoA, closure letter, payment receipt) from the generic menus and refuse them on the generic endpoint. Bind trust data to the Client Trust Statement. Hide the Trust Receipt until a deposit-bound flow exists. Hide the common-pack PROJECT templates for legal-za. |
| D12 | F26: pack JSON edit **plus** a tenant data migration (next free after A) and a portal re-sync. F6: fix **every** Attorneys Act / s78 reference (clause, AI skill prompts, marketing, javadoc) to the Legal Practice Act, with founder wording sign-off. |

## Batches

| # | Batch | Gaps | Gates | Branch / PR | Status |
|---|---|---|---|---|---|
| 1 | B — interest period guard | F11 (HIGH), F10 | BE + FE | PR #1745 → `e0a44ba72` | F11 **VERIFIED** (KC, `qa_cycle/evidence/g89-fixes/batch-B/`); F10 DEFERRED → Checkpoint 1 (e2e day-60 spec) |
| 2 | A — trust ledger, client vs matter | F12 (HIGH), F22 (HIGH), F14, F13 + InterestPosted publish + matter debit guard | BE + FE + PT | PR #1746 → `9cb368e50` | MERGED-AWAITING-VERIFY (KC check running) |
| 3 | D — portal correctness | F23, 633C D2, 633C D6 | BE + PT | `fix/g89-d-portal-correctness` | IN PROGRESS (builder) |
| — | Checkpoint 1 | suites on a fresh stack + KC spot re-walk (S2-03, S2-12, CORR/S2-13, S2-45) | — | | OPEN |
| 4 | C1 — statements & reports | F16, F18, F19, F21, 634B | BE + FE | | OPEN |
| 5 | C2 — template packs & generation | F6, F26, 632A(a)(b)(c), 632C | BE + FE | | OPEN |
| 6 | G — comments | 634E(1)(2)(3)(5), 634C | BE + FE | | OPEN |
| 7 | E — layout, copy, small BE LOWs | F5, F7, F8, F9, F15, F17, F20, G-D10a, G88-F27 (both meanings), G88-M9, G88-F24, G88-F2, 634A | BE + FE | | OPEN |
| 8 | F — tooling, docs, scenarios | F1, F2, F3, screenshot compares opt-in, run-phase `timeout -k`, §6 amendments F4/F24/F25/F27, BASELINE.md next-free, file F28/F29/F30 | FE + DOC | | OPEN |
| 9 | Exit re-score | fresh-stack suites + KC re-walk | — | | OPEN |

## Gap checklist

### HIGH
- [x] G89-F11 — interest run accepted for a period that has not ended (B) — VERIFIED 2026-11-06, PR #1745
- [ ] G89-F12 — matter-linked refund exceeds matter balance (A)
- [ ] G89-F22 — portal trust balance ≠ firm client ledger (A)

### MEDIUM
- [ ] G89-F13 — ledger card totals exclude refunds/transfers (A)
- [ ] G89-F14 — matter Trust tab shows the client card (A)
- [ ] G89-F16 — closure SoA counts the written-off disbursement (C1)
- [ ] G89-F17 — reconciliation list fails to load (E)
- [ ] G89-F18 — Section 35 data pack is hollow (C1)
- [ ] G89-F23 — portal feed announces internal-draft documents (D)

### LOW / carried
- [ ] F1 [ ] F2 [ ] F3 (F)
- [ ] F5 [ ] F7 [ ] F8 [ ] F9 [ ] F15 [ ] F20 (E)
- [ ] F6 [ ] F26 (C2)
- [ ] F10 (B) — merged #1745; observe at Checkpoint 1 (not reachable on the 636B tenant)
- [ ] F19 [ ] F21 (C1)
- [ ] F4 [ ] F24 [ ] F25 [ ] F27 — scenario amendments (F)
- [ ] G-D10a [ ] G88-F27 (Access denied) [ ] G88-F27 (disbursement "Invoiced · Yes") [ ] G88-M9 [ ] G88-F24 [ ] G88-F2 [ ] 634A (E)
- [ ] 633C D2 [ ] 633C D6 (D)
- [ ] 632A(a) [ ] 632A(b) [ ] 632A(c) [ ] 632C (C2)
- [ ] 634B Outstanding tile (C1, verify first)
- [ ] 634E(1) [ ] 634E(2) [ ] 634E(3) [ ] 634E(5) [ ] 634C @-autocomplete / access filter (G)
- [ ] P-E.8 weekly digest observed (exit re-score)
- [ ] New ids filed: G89-F28 (no-matter trust rows), F29 (reversal publishes no portal event), F30 (credit reversals no-op) (F)

## Log
- 2026-11-06 — plan approved; decisions D1–D8; Batch B started.
- 2026-11-06 — decisions D9–D12 (C1/C2 scout findings).
- 2026-11-06 23:4x — PR #1745 (Batch B) merged as `e0a44ba72`. Gates:
  - full verify 6583 / 0 / 0 / 26 skipped (17:53)
  - frontend gate green (3673 tests)
  - 2× APPROVE @6fc390e20
  - CodeRabbit: 0 findings
  - CI green
  KC backend restarted on `e0a44ba72` (javap confirms `requirePeriodEnded`). Note: `verify.sh` preflight killed the KC backend/gateway Maven launchers (app JVMs survived). Batch A builder launched.
- 2026-11-07 ~01:30 — PR #1746 (Batch A) merged as `9cb368e50`.
  - The first full verify @2ca83fd83 caught an ADR-337 wire-vocabulary regression (`InterestAllocationLineResponse.projectId`).
  - Fix commit 6c253d089 also took 2 CodeRabbit Minors (ADR-234 append-only; distinct co-client reference).
  - Final gates @6c253d089: verify 6610 / 0 / 0 / 26 (21:42); FE 3681 green; portal 273 green; 2× re-review APPROVE.
  - KC backend restarted; tenant V12 applied on both tenants.
  - Batch D builder launched.
- New ids to file in Batch F:
  - G89-F36: firm reference > 100 chars aborts portal sync.
  - G89-F37: transfers cannot be reversed via API (RECORDED, reversal needs APPROVED).
  - G89-F38: `admin-audit-30day/fixtures.ts` posts to a non-existent `/api/trust-transactions`.
- Batch E follow-ups from the A reviews:
  - matter Trust tab hides the matter balance when the client-ledger fetch fails;
  - interest notification test lacks a plain member;
  - sr-only "less" cue in the ledger breakdown;
  - ADR-366 should note that the portal omits unmattered credits.
