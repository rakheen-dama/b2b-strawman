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
| D13 | G89-F39 (the portal trust statement shows the recorded date instead of the transaction date) is **MEDIUM** and is folded into Batch D / PR #1747. Founder decision, 2026-11-07. |
| D12 | F26: pack JSON edit **plus** a tenant data migration (next free after A) and a portal re-sync. F6: fix **every** Attorneys Act / s78 reference (clause, AI skill prompts, marketing, javadoc) to the Legal Practice Act, with founder wording sign-off. |

## Batches

| # | Batch | Gaps | Gates | Branch / PR | Status |
|---|---|---|---|---|---|
| 1 | B — interest period guard | F11 (HIGH), F10 | BE + FE | PR #1745 → `e0a44ba72` | **VERIFIED**: F11 (batch-B evidence), F10 (observed in the batch-A walk) |
| 2 | A — trust ledger, client vs matter | F12 (HIGH), F22 (HIGH), F14, F13 + InterestPosted publish + matter debit guard | BE + FE + PT | PR #1746 → `9cb368e50` | **VERIFIED** on KC (F10, F12, F13, F14, F22): `qa_cycle/evidence/g89-fixes/batch-A/` |
| 3 | D — portal correctness | F23, 633C D2, 633C D6, **F39** (D13) | BE + PT | PR #1747 → `72f5f3d4d` | **VERIFIED** on KC (F23, F39, D2, D6): `qa_cycle/evidence/g89-fixes/batch-D/` |
| — | Checkpoint 1 | suites on a fresh stack + KC spot re-walk (S2-03, S2-12, CORR/S2-13, S2-45) | — | | OPEN |
| 4 | C1 — statements & reports | F16, F18, F19, F21, 634B | BE + FE | `fix/g89-c1-statements-reports` | IN PROGRESS (builder) |
| 5 | C2 — template packs & generation | F6, F26, 632A(a)(b)(c), 632C | BE + FE | | OPEN |
| 6 | G — comments | 634E(1)(2)(3)(5), 634C | BE + FE | | OPEN |
| 7 | E — layout, copy, small BE LOWs | F5, F7, F8, F9, F15, F17, F20, G-D10a, G88-F27 (both meanings), G88-M9, G88-F24, G88-F2, 634A | BE + FE | | OPEN |
| 8 | F — tooling, docs, scenarios | F1, F2, F3, screenshot compares opt-in, run-phase `timeout -k`, §6 amendments F4/F24/F25/F27, BASELINE.md next-free, file F28/F29/F30 | FE + DOC | | OPEN |
| 9 | Exit re-score | fresh-stack suites + KC re-walk | — | | OPEN |

## Gap checklist

### HIGH
- [x] G89-F11 — interest run accepted for a period that has not ended (B) — VERIFIED 2026-11-06, PR #1745
- [x] G89-F12 — matter-linked refund exceeds matter balance (A) — VERIFIED 2026-11-07, PR #1746
- [x] G89-F22 — portal trust balance ≠ firm client ledger (A) — VERIFIED 2026-11-07, PR #1746

### MEDIUM
- [x] G89-F13 — ledger card totals exclude refunds/transfers (A) — VERIFIED 2026-11-07, PR #1746
- [x] G89-F14 — matter Trust tab shows the client card (A) — VERIFIED 2026-11-07, PR #1746
- [ ] G89-F16 — closure SoA counts the written-off disbursement (C1)
- [ ] G89-F17 — reconciliation list fails to load (E)
- [ ] G89-F18 — Section 35 data pack is hollow (C1)
- [x] G89-F23 — portal feed announces internal-draft documents (D) — VERIFIED 2026-11-07, PR #1747
- [x] G89-F39 — portal trust statement shows recorded date (D, D13) — VERIFIED 2026-11-07, PR #1747

### LOW / carried
- [ ] F1 [ ] F2 [ ] F3 (F)
- [ ] F5 [ ] F7 [ ] F8 [ ] F9 [ ] F15 [ ] F20 (E)
- [ ] F6 [ ] F26 (C2)
- [x] F10 (B) — VERIFIED 2026-11-07 in the Batch A walk (wizard shows "Client" and "Lerato Mthembu" and "LPFF arrangement rate 0.00%")
- [ ] F19 [ ] F21 (C1)
- [ ] F4 [ ] F24 [ ] F25 [ ] F27 — scenario amendments (F)
- [ ] G-D10a [ ] G88-F27 (Access denied) [ ] G88-F27 (disbursement "Invoiced · Yes") [ ] G88-M9 [ ] G88-F24 [ ] G88-F2 [ ] 634A (E)
- [x] 633C D2 [x] 633C D6 (D) — VERIFIED 2026-11-07, PR #1747
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
- 2026-11-07 ~02:00 — Batch A **VERIFIED** on the Keycloak stack. The UI accepted a backdated LPFF rate (01 Oct), so the October interest-run scenario ran in full:
  - **Allocation:** interest R 55,21 was allocated to the RAF matter.
  - **F22 (portal):** the portal showed R 10 055,21, equal to the firm ledger.
  - **F12 (refund guard):** the over-matter refund was refused with the matter-named message; the exact refund was approved.
  - **F14 (matter Trust tab):** the matter figure and the client-wide line were both correct.
  - **F13 (ledger totals):** the totals reconciled before and after the refunds.
  - **F10 (wizard):** client names shown, verified in the same walk.
  - **Data now in the tenant:** the extra rows are listed in `batch-A/RESULT.md`.
- New candidates from the A walk:
  - **G89-F39:** the portal trust statement shows the *recorded* date, not the transaction date. A deposit dated 01 Oct reads "7 Nov 2026"; the portal home tile and the deposit email do the same. Proposed MEDIUM, since it is a client-facing trust record; awaiting the founder's call.
  - **G89-F40:** the guard refusal prints raw amounts ("R10055.21"). LOW, goes to Batch E.
  - **G89-F41:** the interest run "Posted Date" is shown in UTC (06 Nov for a 01:18 SAST post), the G88-F48 class. LOW, goes to Batch E.
- 2026-11-07 ~01:50 — PR #1747 (Batch D) opened.
  - Both reviewers APPROVE @1816aee86.
  - The partial verify reached 4600 tests with 0 failures before it was stopped.
  - Founder D13: fold G89-F39 (MEDIUM) into Batch D. The builder is resumed for the F39 fix round; full verify and re-review follow on the new head.
- 2026-11-07 ~02:05 — PR #1747 F39 fix round done (head `2748903dc`).
  - Global **V4** adds `portal_trust_transaction.transaction_date` and `recorded_at`.
  - Events carry `transactionDate`; ordering is now transaction_date, recorded_at, id.
  - Orchestrator call, following the D6 reasoning: no deploy-time repair of existing portal rows. V4 fills them from `occurred_at` (SA day), and the true dates are rewritten on the matter's next trust event (full-replay `recomputeMatter`). This affects only dev/QA/VPS-demo data.
  - New candidate **G89-F42**: `TrustLedgerPortalSyncService.backfillForTenant` trims each matter to its last 50 rows. It has no production caller today, but any future boot runner built on it would delete portal history.
  - Full verify and 2 re-reviews are running on `2748903dc`.
  - For the KC check, verify with a *fresh* back-dated deposit (old Lerato rows keep their 7 Nov date until the next event).
- 2026-11-07 ~02:45 — PR #1747 (Batch D + F39) merged as `72f5f3d4d`.
  - Gates @2748903dc:
    - full verify: 6630 / 0 / 0 / 26 (17:18)
    - portal gate: 278 green
    - reviews: 2× APPROVE on the F39 head
    - CodeRabbit: REVIEWED @1816aee86 (0 findings), DEFERRED on the F39 head
  - The first CI Portal attempt failed in `next build`: the `next/font/google` download failed (network). The re-run passed.
  - KC backend restarted; global V4 applied.
  - Batch D KC check and the C1 builder launched.
- New LOW candidate **G89-F43**: the portal trust `from`/`to` filter still compares `occurred_at`.
- 2026-11-07 ~03:00 — Batch D **VERIFIED** on KC.
  - **F23:** an internal-draft Matter Summary Report was hidden from Lerato's Firm actions. After "Share with client" it appeared.
  - **D6:** the trust email reads "Type Deposit · Date 15 Oct 2026" (Mailpit `QkA55gTkNQepzfKNaKaL9w`).
  - **F39:** the back-dated deposit triggered a full recompute. Rows now read 01 Oct / 15 Oct / 31 Oct / 07 Nov, in sequence, and the balance R 5 000,00 equals the firm ledger.
  - **D2:** badges read "Due soon" and "Upcoming".
- Walk notes (LOW candidates, not filed as defects yet):
  - "Share with client" lives under Work › Generated documents, not on the Documents tab.
  - Portal Firm actions shows no trust deposit or interest events (product question, candidate **G89-F44**).
  - The home "Last trust movement" tile shows the latest row by transaction date, not the most recently recorded one.
