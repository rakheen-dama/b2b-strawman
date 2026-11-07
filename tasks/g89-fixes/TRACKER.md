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
| D14 | (2026-11-07) **G89-F50** (Section 35 pack lists only the latest interest run in the FY) is **MEDIUM**; fix in Batch E (all runs). **G89-F45** (multi-matter fee note counted in full on each matter) is **LOW**, backlog. **G89-F44** (portal Firm actions lacks deposit/interest events): fix in Batch E. |
| D15 | (2026-11-07) Founder **signs off the PR #1749 legal wording as written**: s78(1) → s86(2) trust account; s86 LPA citations; compliance-audit prompt s86(1)-(2)/(3)-(4); Section 35 historical-name javadoc; "Unbilled work in progress (not yet billed)". |
| D16 | (2026-11-07) The VIEW_TRUST fallback line "Trust details are not available." is **signed off**. **G89-F51** (the Statements tab shows the stored `trust_balance_held` to users without VIEW_TRUST, pre-existing) is **MEDIUM**, to be fixed in Batch E server-side with `TrustDataVisibility`. |
| D17 | (2026-11-07, orchestrator sequencing) **G89-F51** is moved from Batch E into PR #1749. CodeRabbit flagged it there as a Major on the same DTO and table as a PR-introduced Major (a withheld generation saved as a zero trust balance). Severity and fix approach are unchanged from D16. |
| D18 | (2026-11-07) **G89-F52 is HIGH**, as Batch H, its own PR, next after C2 and before G. Generated-document download/presign/list endpoints don't check trust or client access:
- an SoA PDF containing client trust activity can be downloaded by any matter viewer without VIEW_TRUST;
- client-level generated docs, such as a Client Trust Statement, are downloadable by any tenant member.

**Fix:** customer-access checks for client-level generated docs, plus VIEW_TRUST for trust-bearing documents. |
| D19 | (2026-11-07) Batch H also covers **G89-F53** (template generate/preview/generate-docx skip the matter-access check), **G89-F54** (statements list/get skip matter membership) and **G89-F56** (AI contract review reads any document by id with no access check, and the text goes to the LLM). All are rated **HIGH**. F52 lists: trust-bearing rows stay listed, flagged `containsTrustData`, with Download disabled for members without VIEW_TRUST. |
| D20 | (2026-11-07, orchestrator, under the D19 ruling for the same class) Fold into Batch H / PR #1750: **G89-F58** HIGH (AI skills such as ContractReview and Drafting load the caller-named matter without a matter check; matter data reaches the LLM, same class as F56), **G89-F59** (contract review without a matterId → 500), **G89-F60** (`DELETE /api/generated-documents/{id}` has no matter check). |
| D12 | F26: pack JSON edit **plus** a tenant data migration (next free after A) and a portal re-sync. F6: fix **every** Attorneys Act / s78 reference (clause, AI skill prompts, marketing, javadoc) to the Legal Practice Act, with founder wording sign-off. |

## Batches

| # | Batch | Gaps | Gates | Branch / PR | Status |
|---|---|---|---|---|---|
| 1 | B — interest period guard | F11 (HIGH), F10 | BE + FE | PR #1745 → `e0a44ba72` | **VERIFIED**: F11 (batch-B evidence), F10 (observed in the batch-A walk) |
| 2 | A — trust ledger, client vs matter | F12 (HIGH), F22 (HIGH), F14, F13 + InterestPosted publish + matter debit guard | BE + FE + PT | PR #1746 → `9cb368e50` | **VERIFIED** on KC (F10, F12, F13, F14, F22): `qa_cycle/evidence/g89-fixes/batch-A/` |
| 3 | D — portal correctness | F23, 633C D2, 633C D6, **F39** (D13) | BE + PT | PR #1747 → `72f5f3d4d` | **VERIFIED** on KC (F23, F39, D2, D6): `qa_cycle/evidence/g89-fixes/batch-D/` |
| — | Checkpoint 1 | suites on a fresh stack + KC spot re-walk (S2-03, S2-12, CORR/S2-13, S2-45) | — | main `d6742789f` | **PASS**: all 7 scored runs 0 failed, per-test identical to 636A (`qa_cycle/evidence/g89-fixes/checkpoint-1/`); the KC spot rows were covered by the Batch A/B/D walks |
| 4 | C1 — statements & reports | F16, F18, F19, F21, 634B | BE + FE | PR #1748 → `08c64dd88` | **VERIFIED** on KC (F16, 634B, F21, F18, F19): `qa_cycle/evidence/g89-fixes/batch-C1/` |
| 5 | C2 — template packs & generation | F6, F26, 632A(a)(b)(c), 632C (+ SoA WIP row) | BE + FE | PR #1749 → `c38616dd5` | **VERIFIED** on KC (632A(a)(b)(c)/S1-67, F6, F26/S2-50, WIP). DEFERRED: 632C (needs a new payment, moved to the exit re-walk); VIEW_TRUST withheld (needs a custom role, moved to the Batch H walk) |
| 5b | **H — document access control** | **F52, F53, F54, F56, F58 (all HIGH)**, F55, F59, F60 | BE + FE | PR #1750 @53cabef57 | gates running: full verify, 2 reviews, pre-merge e2e (all suites) |
| 6 | G — comments | 634E(1)(2)(3)(5), 634C | BE + FE | | OPEN |
| 7 | E — layout, copy, small BE LOWs | F5, F7, F8, F9, F15, F17, F20, G-D10a, G88-F27 (both meanings), G88-M9, G88-F24, G88-F2, 634A | BE + FE | | OPEN |
| 8 | F — tooling, docs, scenarios | F1, F2, F3, screenshot compares opt-in, run-phase `timeout -k`, §6 amendments F4/F24/F25/F27, BASELINE.md next-free, file F28/F29/F30 | FE + DOC | | OPEN |
| 9 | Exit re-score | fresh-stack suites + KC re-walk | — | | OPEN |

## Gap checklist

### HIGH
- [ ] G89-F52 — generated-document downloads bypass trust/client access (H, D18)
- [ ] G89-F53 — template generate/preview/docx skip matter access (H, D19)
- [ ] G89-F54 — statements list/get skip matter membership (H, D19)
- [ ] G89-F56 — AI contract review reads any document unchecked (H, D19)
- [ ] G89-F58 — AI skills load caller-named matter without access check (H, D20)
- [x] G89-F11 — interest run accepted for a period that has not ended (B) — VERIFIED 2026-11-06, PR #1745
- [x] G89-F12 — matter-linked refund exceeds matter balance (A) — VERIFIED 2026-11-07, PR #1746
- [x] G89-F22 — portal trust balance ≠ firm client ledger (A) — VERIFIED 2026-11-07, PR #1746

### MEDIUM
- [x] G89-F13 — ledger card totals exclude refunds/transfers (A) — VERIFIED 2026-11-07, PR #1746
- [x] G89-F14 — matter Trust tab shows the client card (A) — VERIFIED 2026-11-07, PR #1746
- [x] G89-F16 — closure SoA counts the written-off disbursement (C1) — VERIFIED 2026-11-07, PR #1748
- [ ] G89-F17 — reconciliation list fails to load (E)
- [x] G89-F18 — Section 35 data pack is hollow (C1) — VERIFIED 2026-11-07, PR #1748
- [ ] G89-F50 — Section 35 interest allocation lists only the latest run in the FY (E, D14) — MEDIUM
- [ ] G89-F51 — Statements tab shows stored trust balance to users without VIEW_TRUST (moved to C2 / PR #1749, D17) — MEDIUM — merged #1749, awaiting KC
- [x] G89-F23 — portal feed announces internal-draft documents (D) — VERIFIED 2026-11-07, PR #1747
- [x] G89-F39 — portal trust statement shows recorded date (D, D13) — VERIFIED 2026-11-07, PR #1747

### LOW / carried
- [ ] F1 [ ] F2 [ ] F3 (F)
- [ ] F5 [ ] F7 [ ] F8 [ ] F9 [ ] F15 [ ] F20 (E)
- [x] F6 [x] F26 (C2) — VERIFIED 2026-11-07, PR #1749
- [x] F10 (B) — VERIFIED 2026-11-07 in the Batch A walk (wizard shows "Client" and "Lerato Mthembu" and "LPFF arrangement rate 0.00%")
- [x] F19 [x] F21 (C1) — VERIFIED 2026-11-07, PR #1748
- [ ] F4 [ ] F24 [ ] F25 [ ] F27 — scenario amendments (F)
- [ ] G-D10a [ ] G88-F27 (Access denied) [ ] G88-F27 (disbursement "Invoiced · Yes") [ ] G88-M9 [ ] G88-F24 [ ] G88-F2 [ ] 634A (E)
- [x] 633C D2 [x] 633C D6 (D) — VERIFIED 2026-11-07, PR #1747
- [x] 632A(a) [x] 632A(b) [x] 632A(c) — VERIFIED 2026-11-07, PR #1749 · [ ] 632C (merged; KC observation at the exit re-walk)
- [x] 634B Outstanding tile / Statements tab (C1) — VERIFIED 2026-11-07, PR #1748
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
- 2026-11-07 ~03:40 — PR #1748 (C1) opened with 2× APPROVE @d3b9bea18.
  - The first full verify was aborted by the macOS `dirhelper` deleting the embedded-PG `timezonesets` mid-run. That is proven, and recorded in `tasks/lessons.md`. The stale extraction was deleted and the verify re-launched.
  - Batch F adds a tooling fix: pin the zonky working dir outside `$TMPDIR`.
  - Candidates:
    - **G89-F45:** a fee note with lines on two matters is counted in full on both matters' SoA/closure/tile. Pre-existing, consistent across surfaces.
    - Missing tests for the Section 35 investment, interest and `_total` projections (follow-up).
- 2026-11-07 ~04:00 — **Checkpoint 1 PASS** on main `d6742789f` (#1745 + #1746 + #1747).
  - Legal suites: lifecycle 105/0/0, depth-ii 39/0/4, default 165/0/14.
  - Portal suites: vitest 278, 90-day 13/0/0, responsive 21/0/0, isolation 2/0/0.
  - Every result is per-test identical to 636A; tenant V12 and global V4 applied.
  - One class (c) event: the first G1 frontend build failed on a `next/font/google` download; the retry passed.
  - New tooling candidate **G89-F46**: frontend/portal production builds depend on Google Fonts being reachable. It has now hit both CI (#1747 Portal job) and the e2e build. Self-host the fonts via `next/font/local` (Batch F).
- 2026-11-07 ~04:35 — PR #1748 (C1) merged as `08c64dd88`.
  - CodeRabbit Major (mixed-currency fee-note aggregation) fixed in `fc13d86c0` (red test first); fee-note totals are now restricted to the org default currency, like the Outstanding tile.
  - Final gates @fc13d86c0: verify 6644 / 0 / 0 / 26 (17:15), FE gate @d3b9bea18 (fix is backend-only), 2× re-review APPROVE, CI green.
  - KC backend restarted (trust-reports pack v1→v2 reconcile on boot).
  - C1 KC check and the C2 builder launched. C2 takes legal-za v13 and adds the SoA "Unbilled WIP" row.
- 2026-11-07 ~05:00 — **C1 VERIFIED on KC.**
  - **F16:** a new SoA on Sipho's paid matter reads "Closing balance owing: R0,00", and the written-off R 500 is not listed.
  - **634B:** the tab's newest row is R 0,00, matching the tile. The generic-menu SoA is excluded.
  - **F21:** the closure letter reads "Date: 7 November 2026".
  - **F18:** cells and Total rows populated; PDF row lines present; CSV has 0 blank amounts.
  - **F19:** "Select a trust account…".
  - The orchestrator read the screenshots and the PDF text.
- **Founder D14:**
  - F50 MEDIUM → Batch E;
  - F45 LOW → backlog;
  - F44 → Batch E.
- **New LOWs from the C1 walk, all to Batch E:**
  - **G89-F47:** the Section 35 on-screen ITEM cell doesn't wrap the long INT-CREDIT refs, so AMOUNT is off-screen at 1440.
  - **G89-F48:** the Section 35 PDF prints raw money ("10000.00").
  - **G89-F49:** CSV negative amounts are apostrophe-prefixed by the formula-injection guard, so they read as text.
- **PR #1749 (C2) opened.** A security review flagged a capability bypass:
  - the Client Trust Statement trust block is gated by module only, not by VIEW_TRUST;
  - the pre-existing SoA `buildTrustBlock` has the same gap, so it is fixed in the same round (same rule, one helper).
  - The builder has been resumed for the fix, red tests first.
- 2026-11-07 ~13:50 — PR #1749 (C2) review round 1 @3788a897b.
  - **Backend reviewer: APPROVE**, with one confidence-80 item. The dedicated-flow slug prefix over-matches firm ORG_CUSTOM templates (e.g. `statement-of-account-cover-letter`).
  - **Frontend reviewer: REQUEST_CHANGES.** `e2e/tests/documents/document-generation.spec.ts` uses "Matter Summary Report", which D11 hides for legal-za, so 3 of 5 tests would fail (class b, intended change).
  - The partial verify reached 4200 tests with 0 failures before it was stopped for the fix round.
  - Builder resumed: repoint the spec, restrict the prefix arm to clones and platform rows (red first), and optionally hide the "Trust Activity" heading when withheld.
  - Batch E add: F51.
- 2026-11-07 ~15:40 — PR #1749 (C2) pre-merge gates:
  - Full verify @150bc743e: 6683 / 0 / 0 / 26 (24:06).
  - Re-reviews: 2× APPROVE @150bc743e.
  - **Pre-merge e2e** (`qa_cycle/evidence/g89-fixes/pr1749-e2e/`) matched Checkpoint 1 per-test on lifecycle 105/0, default 165/0/14 (document-generation 5/5) and all portal suites. Tenant Flyway 13 / global 5. Expected differences observed (menus, no suffix, WIP R7 000,00, receipt `-r1-`).
  - **depth-ii 31/3/4/5.** No PR regression:
    - (b) `day-30-45-soa-writeoff:107`: broken by **#1748**'s D9 change and red on main too. Spec fix goes into #1749.
    - (c) `day-14-conveyancing:354`: networkidle-after-preview harness hang, also on main (5 of 16). Spec fix goes into #1749.
    - (c) `day-75-85-closure:391`: capture artefact; passes on re-run and on main.
  - Builder resumed for the two spec fixes. depth-ii is to be re-run from the new head before merge.
- 2026-11-07 ~16:30 — PR #1749 (C2): all gates were green at 32bd5a633:
  - depth-ii re-run 40/0/4/0 (`pr1749-e2e/rerun-32bd5a633/`);
  - 2× spec-delta APPROVE;
  - FE gate 3688 green.
  - **Not merged.** CodeRabbit on the final head raised 5 comments (2 Major):
    - Major: a withheld generation is saved as a zero trust balance (PR-introduced).
    - Major: F51, the saved trust balance is readable without VIEW_TRUST.
    - Minor: the generic-menu scan cache.
    - Minor: the refusal tests.
    - Minor: the V13 scope. Replied: D12 covers it, no change.
  - The builder has been resumed for the CodeRabbit round, with F51 moved in (D17).
  - Needs: full verify, 2 re-reviews, FE gate and a depth-ii re-run (it asserts the statements-table trust cell).
  - Process gap noted: the audit-trail line written before CodeRabbit had been read. Rule: read the CodeRabbit inline comments *before* writing the `## CodeRabbit:` line.
- 2026-11-07 ~17:35 — **PR #1749 (C2) merged as `c38616dd5`.**
  - Final gates @1b8da6a18:
    - full verify 6692 / 0 / 0 / 26 (22:04);
    - FE 3689 green;
    - lifecycle 105/0 and depth-ii 40/0/4 on fresh stacks;
    - 2× APPROVE;
    - CodeRabbit: all 5 triaged (4 fixed, 1 no-change), no new actionable comments on the final head.
  - KC stack restarted. Tenant V13 and global V5 applied.
  - C2 KC check launched; Batch H scout running.
  - Next free migrations: tenant **V14**, global **V6**.
- 2026-11-07 ~18:10 — **C2 VERIFIED on KC** (5 PASS, 2 DEFERRED).
  - **Menus:** the hidden templates are gone from the generic menus and still listed in Settings with a hint.
  - **Labels:** required-field labels read "Matter › Seller Name".
  - **Client Trust Statement:** "Section 86(2) trust account", opening R0,00, 5 lines, closing R7 000,00.
  - **Trust Receipt:** shows the s86 wording.
  - **F26:** 0 suffixed rows on firm, portal and templates.
  - **WIP:** "Unbilled work in progress … R2 500,00" with closing R0,00.
- Founder **D19**: F53, F54, F56 are HIGH and go into H; lists keep rows and disable Download.
- New LOW **G89-F57** → E: the SoA summary "Total fees: R0,00" while Professional Fees lists unbilled time (R2 875,00) is confusing under D9. Relabel the summary lines as billed-in-period.
- 2026-11-07 ~20:30 — PR #1750 (Batch H) opened @19f595a52.
  - Red tests: 21 testcases, 13 failures + 2 errors on main.
  - Targeted: 2791 green. FE: 3696 green. Tenant **V14** (`contains_trust_data`); next free is V15.
  - The builder found F58, F59 and F60 (D20) and is folding them in before the gates.
- 2026-11-07 ~21:30 — PR #1750 fold-in done @53cabef57.
  - F58: matter checks in the contract-review and drafting skills; the provider stub records prompts, and the tests assert the secret never reaches the LLM.
  - F59: null-safe, refused before the LLM when no matter can be resolved.
  - F60: delete returns 404 to a non-member.
  - Shared change: `ProjectAccessService.requireViewAccess` gets `noRollbackFor`, so a refusal is recorded instead of `UnexpectedRollbackException`. Reviewers have been asked to check callers that write before the check.
  - New candidates (not folded):
    - **G89-F61:** the matter-intake skill sends up to 500 matter names to the LLM, including matters the caller is not on.
    - **G89-F62:** drafting without a templateId returns 500.
