# Phase 92 exit — audit facts for the closure report (652F, raw material for task 652.34)

These are the inputs for the closure report, not the report itself. Everything was established on worktree `epic-652F/exit-gate` = `main` `b08a8916a` on 2026-11-19. Shorthands: `$EV` = `qa_cycle/evidence/phase92`, `$B` = `backend/src/main/java/io/b2mash/b2b/b2bstrawman`, `$MIG` = `backend/src/main/resources/db/migration`. The exit numbers (652.32) are the orchestrator's to add.

## 1. Hypothesis outcomes

**Nothing in the phase was struck.** `find qa_cycle/evidence -name 'STRUCK*'` returns nothing.

| Item | Outcome | Red evidence | Fixed in |
|---|---|---|---|
| H-a: an approved credit reversal moves neither cashbook nor card | **reproduced** (RED, failure). Cashbook and client moved 0,00, expected −5 000,00. | `$EV/649A/RED-RUNS.md` (`main` `aecf93279` + test files only), then the flipped pin red again in `$EV/649B/red-run.txt` (`main` `5d4704455`) | 649B, PR #1789 `dea93bd38`. Test `TrustReversalEquationIntegrationTest.approvedCreditReversal_movesCashbookAndClient` (+ `…_refusedWhenTheClientNoLongerCoversIt`) |
| H-b: a debit reversal credits the card only if one exists | **reproduced only through direct repository writes** (RED). Both service-only probes were refused, so it was recorded as "structural — closed by derived balances", not patched. | `$EV/649A/RED-RUNS.md` § "H-b — service-only attempts" | 646A, PR #1788 `b1d785cf5` (derived balances; the card is only a lock anchor). Regression test `TrustReversalEquationIntegrationTest.debitReversalForAClientWithoutAnAnchor_movesBothSides` |
| H-c: a rejected credit reversal blocks any later reversal | **reproduced** (RED, error: "Transaction has already been reversed") | `$EV/649A/RED-RUNS.md`; `$EV/649B/red-run.txt` | 649B, PR #1789. Test `rejectedCreditReversal_leavesOriginalReversible`; path 7d `creditReversalRejectedThenReversedAgain_isAccepted` |
| H-d: only `APPROVED` rows can be reversed | **reproduced** (RED, error: "Transaction must be in APPROVED status to be reversed"). Resolved by founder ruling #1: any posted row is reversible under its registry rule. | `$EV/649A/RED-RUNS.md`; `$EV/649B/red-run.txt` | 649B, PR #1789. Tests `recordedDeposit_isReversible`, `recordedDepositReversal_approved_movesCashbookAndClient` |
| 652A: silent recalculation failure in the wizard | **reproduced** (vitest exit 1) and browser repro | `$EV/652A/red-before.txt` (`main` `1d7686586`), `$EV/652A/repro-browser.md` | 652A, PR #1815 `acf07bc33` |
| 652B: the manual-match pane lists only `APPROVED` | **reproduced** (3 vitest failures) and browser repro | `$EV/652B/red-before.txt` (`main` `6f55d5e2f`), `$EV/652B/repro-browser.md` | 652B, PR #1816 `e64de4dc9` |
| 652C: orphaned reconciliation draft | **reproduced** (`secondCreateForSameAccountAndPeriod_returnsTheDraft` red) and browser repro | `$EV/652C/red-before.txt` (`main` `30ad1643b`), `$EV/652C/repro-browser.md` | 652C, PR #1817 `ca2ece798` (V29) |
| 652D: closed primary account in the two statement builders | **reproduced** (8 run / 3 failures) | `$EV/652D/red-before-surefire.txt`, `red-before-mvn-summary.txt`, `README.md` (`main` `68fad6c80`) | 652D, PR #1818 `83998dd40` |
| 652G: zero-amount balance rows import as statement lines | **reproduced** (`BankStatementParserTest` exit 1) | `$EV/652G/repro-red.txt` (`main` `1621e4cfe`) | 652G, PR #1819 `fa616a66a` |
| 652H: a `REVERSED` original cannot be matched | **reproduced** (8 run / 4 failures / 2 errors, plus 2 vitest cases) | `$EV/652H/red-on-main.txt` (`main` `16c40fa37`) | 652H, PR #1820 `72145f505` |
| `BankStatementParseException` → 500 (requirements E8) | **struck before the phase**, in the architecture (R9): already mapped to 422 | architecture §16.9.3 last row (`GlobalExceptionHandler.java:150`, `BankStatementImportErrorIntegrationTest`) | not a slice |

## 2. Migrations as landed

All 15 landed on their **guide number** from the task file's Migrations paragraph. The architecture's §16.7 file numbers differ because the task file re-cut the slices; that column is for reference. "Slice / PR" is the commit that added the file (`git log --diff-filter=A`).

| Guide (task file) | Architecture §16.7 name | Actual file on `main` | Slice / PR |
|---|---|---|---|
| tenant V16 | V16 `phase92_trust_posting_core` (646A) | `$MIG/tenant/V16__phase92_trust_posting_core.sql` | 646A #1788 `b1d785cf5` |
| tenant V17 | V17 `phase92_trust_reversal_contra` (649B) | `$MIG/tenant/V17__phase92_trust_reversal_contra.sql` | 649B #1789 `dea93bd38` |
| tenant V18 | V18 `phase92_trust_creditor_accounts` (646B) | `$MIG/tenant/V18__phase92_trust_creditor_accounts.sql` | 646B #1790 `5d2a4791c` |
| tenant V19 | (the `trust_reconciliations` block of V18) | `$MIG/tenant/V19__phase92_trust_recon_breakdown.sql` | 646C #1792 `e7d3a1d0c` |
| tenant V20 | V19 `phase92_statement_line_classification` (647A) | `$MIG/tenant/V20__phase92_statement_line_classification.sql` | 647A #1794 `7bb94a27c` |
| tenant V21 | V20 `phase92_unidentified_deposits` (647B) | `$MIG/tenant/V21__phase92_unidentified_deposits.sql` | 647B #1796 `53eeb7723` |
| tenant V22 | (the `trust_accounts` part of V21) | `$MIG/tenant/V22__phase92_investment_account_basis.sql` | 648C #1798 `004cadd24` |
| tenant V23 | V21 `phase92_investment_accounts` (648A) | `$MIG/tenant/V23__phase92_investment_accounts.sql` | 648A #1799 `c81387734` |
| tenant V24 | V22 `phase92_investment_interest` (648B) | `$MIG/tenant/V24__phase92_investment_interest.sql` | 648B #1801 `c30545fc0` |
| **global V7** | global V7 `portal_trust_invested_balance` (648B) | `$MIG/global/V7__portal_trust_invested_balance.sql` | 648D #1802 `c441b8143` |
| tenant V25 | V23 `phase92_trust_monthly_lists` (650A) | `$MIG/tenant/V25__phase92_trust_monthly_lists.sql` | 650A #1806 `95b98cc47` |
| tenant V26 | V24 `phase92_trust_interest_memos` (650B) | `$MIG/tenant/V26__phase92_trust_interest_memos.sql` | 650B #1808 `84cf18f5b` |
| tenant V27 | V25 `phase92_drop_interest_run` (651A, minus `lpff_rates`) | `$MIG/tenant/V27__phase92_drop_interest_run.sql` | 651A #1811 `87b9e2d09` |
| tenant V28 | (the `lpff_rates` drop of V25) | `$MIG/tenant/V28__phase92_drop_lpff_rates.sql` | 651E #1812 `23fba0a84` |
| tenant V29 (conditional) | V26 `phase92_trust_recon_unique` (652C) | `$MIG/tenant/V29__phase92_trust_recon_unique.sql` | 652C #1817 `ca2ece798` |

- **Next free: tenant V30, global V8.** The highest files present are `tenant/V29__…` and `global/V7__…`. There is no global V8.
- `backend/db/BASELINE.md:70` still says "tenant **V16**, global **V7**", so it is stale. 652F leaves it unchanged: the next PR that touches `backend/` corrects it under its own backend verify (652F D-4). The correct numbers are recorded in `TASKS.md` and the closure report C.4.
- Neither `V1__baseline.sql` changed in the phase: `git diff 512b1a6c5^ HEAD -- '*/V1__baseline.sql'` is empty, and no PR's file list includes it.

## 3. Pack bumps

| Pack | Change | On `main` | Landed in |
|---|---|---|---|
| `trust-reports` | 3 → 4 | `$B/verticals/legal/trustaccounting/report/TrustReportPackSeeder.java:50` `static final int PACK_VERSION = 4;`. `SLUGS` at `:71-81` lists 9 slugs, including `trust-position`, `trust-interest-charges-schedule` and `unidentified-deposits-age-analysis`; `interest-allocation` is gone. | 650C, PR #1809 `417e58073` (`git log -S'PACK_VERSION = 4'`). The architecture §16.8.6 says 650B; the re-cut moved all definition edits to 650C. |
| `legal-za` template pack | 15 → 16 | `backend/src/main/resources/template-packs/legal-za/pack.json:3` `"version": 16,` | 648F, PR #1803 `b8940021a` (`git log -S'"version": 16'`). The architecture §16.6.4 says 648B; the re-cut moved it to 648F. |

**No other pack version changed.** `git diff 512b1a6c5^ HEAD` was checked two ways:

- Over `backend/src/main/resources/**/*.json`, the only changed `"version"` line is `legal-za/pack.json` 15 → 16.
- Over `backend/src/main/java/**`, the only changed `PACK_VERSION` / `VERSION =` line is `TrustReportPackSeeder` 3 → 4. `StandardReportPackSeeder.PACK_VERSION = 2` is unchanged.

## 4. Baseline vs now (backend `<testcase>` totals)

**645.1 baseline** (`$EV/baseline/README.md`, `main` `251100d54`, 2026-11-16):

| Gate | Baseline result |
|---|---|
| BE-VERIFY | **6 871** `<testcase>` / 0 failures / 0 errors / 26 skipped |
| FE-GATE vitest | 4 357 passed / 2 skipped (478 files) |
| PORTAL-GATE vitest | 284 passed (51 files) |
| E2E-LEGAL lifecycle | 105 / 105 |
| E2E-LEGAL depth-ii | 40 passed / 4 skipped |
| Demo `cashbook − stored card total` | DEFERRED on the stack; measured in 649A as R27 000,00 |

**Per-slice BE-VERIFY totals.** Each figure is the `summary` field of the committed `.claude/markers/verify-backend.json`, which agrees with the PR body. All are 0 failures / 0 errors / 26 skipped.

| Slice | PR | Verified commit | `<testcase>` total | Delta / note |
|---|---|---|---|---|
| 645A | #1786 | `23a55222b` | 6 865 | −6 vs baseline: 9 removed, 3 added |
| 649A | #1787 | `05f98e5f0` | 6 890 | +25 |
| 646A | #1788 | `3285515df` | 6 917 | +28 new, −1 deleted |
| 649B | #1789 | `91e65c33d` | 6 945 | +28 |
| 646B | #1790 | `65d0f7f72` | 6 978 | +33 |
| 646E | #1791 | `d3964c700` | 7 009 | +31 |
| 646C | #1792 | `92b4b5550` | 7 037 | +28 |
| 646D | #1793 | `bea187443` | 7 048 | +11 |
| 647A | #1794 | `ab1714caf` | 7 093 | +45 |
| 647C | #1795 | `6e485f412` | 7 131 | |
| 647B | #1796 | `563ef6185` | 7 158 | |
| 647D | #1797 | `0915e8b0d` | 7 192 | |
| 648C | #1798 | `4bb0d595f` | 7 205 | also 7 205 @`ef8b6c19b` |
| 648A | #1799 | `b6afe8c69` | 7 242 | |
| 648E | #1800 | `6b4615074` | 7 248 | |
| 648B | #1801 | `c952121f6` | 7 283 | |
| 648D | #1802 | `7811f1f86` | 7 292 | |
| 648F | #1803 | `f8a8aabb0` | 7 311 | |
| 649C | #1804 | `a09f3bc91` | 7 340 | |
| 650A | #1806 | `82cd19bec` | 7 361 | |
| 650D | #1807 | `662e71131` | 7 364 | |
| 650B | #1808 | `ae82e5b6e` | 7 377 | |
| 650C | #1809 | `dcd3eb277` | 7 404 | |
| 651C | #1810 | — | — | frontend only, no BE-VERIFY |
| 651A | #1811 | `66f4a7e3c` | 7 362 | −42: 48 interest-run tests deleted, 6 added |
| 651E | #1812 | `3e85d3307` | 7 361 | −1 |
| 651D | #1813 | — | — | docs |
| 651B | #1814 | — | — | docs |
| 652A | #1815 | — | — | frontend only |
| 652B | #1816 | — | — | frontend only |
| 652C | #1817 | `19157ccb0` | 7 376 | +15. The final head `c92ddc0e4` changed one test method after this verify; Backend CI ran on it. See `markers.md`. |
| 652D | #1818 | `5c2f17f64` | 7 389 | +13 |
| 652G | #1819 | `58bc3b791` | 7 405 | +16 |
| 652H | #1820 | `d9f42136e` | 7 420 | +15 |
| 652E | #1821 | — | — | frontend / e2e only |

The last slice verify is 7 420 against the 645.1 baseline of 6 871 (**+549**). The 652.32 exit BE-VERIFY on `main` should be ≥ 7 420, or explain any difference. The exit numbers are the orchestrator's to add.

## 5. Before / after against §16.1.1

"Before" is HEAD `8440d3cf4`, as stated in the architecture.

| Concern | Before (one line) | Now | Slice(s) |
|---|---|---|---|
| Interest on s86(2)/(3) money | Calculated from daily balances × `lpff_rates`; posted as `INTEREST_CREDIT` / `INTEREST_LPFF` | Recorded from the statement line as `FUND_INTEREST`, with the sweep as `FUND_SWEEP`. The calculated run is guarded, then removed. | 645A #1786 (guard), 647A #1794 (record), 651C #1810, 651A #1811, 651E #1812 (removal; V27, V28) |
| Interest on s86(4) investments | A hand-typed running total; no transaction; no 5 % | One `trust_investment_interest` entry per credit (gross / Fund 5 % / net) → `INV_INTEREST` (+ `FUND_INV_SHARE`) | 648B #1801 (V24); paths 20 and 21 |
| Non-client trust money | Impossible except `INTEREST_LPFF` | `trust_creditor_accounts` (Fund interest and charges; Unidentified deposits) plus `unidentified_deposits` items | 646B #1790 (V18), 647B #1796 (V21), 647D #1797 |
| Bank charges | No type | `BANK_CHARGE` against the Fund ledger (recoverable flag, VAT); `CHARGE_REFUND` | 647A #1794 (V20); path 12 |
| Client ledger balance | Stored on `client_ledger_cards.balance`, CHECK ≥ 0 | Derived as `SUM(ledger_effect)`. V16 drops `balance` and the total columns (`V16__…sql:90-94`); the card is only a lock anchor. | 646A #1788 |
| Type semantics | 11 code locations plus three label maps | `TrustTransactionType` registry; effects stored per row; labels from the registry (type endpoint, `typeLabel`) | 646A #1788, 646B #1790 (25 types), 646E #1791 |
| Reversal | The original drops out of sums; credits never reversible | Contra entry on its own `reversalDate`; the original keeps its effects; H-a, H-c and H-d fixed | 649B #1789 (V17); paths 6–10 |
| Reconciliation equation | adjusted bank = cashbook = stored card total, as at now | The full equation as at the period end (client + investment + non-client + inter-account), plus the completion gate G1–G4 | 646C #1792 (V19), 649C #1804; paths 22 and 24 |
| Statement line | UNMATCHED / AUTO / MANUAL / EXCLUDED | + `RECORDED` with a `classification`; unmatch reverses what was created | 647A #1794, 647C #1795 (suggestions), 652G #1819 (zero-amount balance rows not imported), 652H #1820 (a REVERSED original is matchable) |
| Investments | A placement was a `PAYMENT` and left the books | The money moves to an `INVESTMENT` account and the client's investment creditor balance; the client total is unchanged | 648C #1798 (V22), 648A #1799 (V23), 648E #1800, 648D #1802 (portal, global V7), 648F #1803 (documents); paths 17–19 |
| Integrity | None | Harness (tests), integrity check (dashboard card), completion gate | 649A #1787 (harness v0), 649C #1804 |
| Monthly list | `client-trust-balances` from stored cards; clients only | Rule 54.15 list: every creditor by name, compared with bank + investment, locked per month | 650A #1806 (V25), 650D #1807 (`lockedThrough` + warning), 650C #1809 (`trust-position` report); paths 23 and 24 |
| Rule 54.28 | `interest-allocation` report of a calculated run | Interest-and-charges schedule from postings, plus memo records for at-source sweeps | 650B #1808 (V26), 650C #1809 |
| Seeded demo books | Cashbook R27 000 vs stored card total R0 | Seeded through the posting service; EQ-2 holds (difference 0,00); one client at −R3 000,00 | 646A #1788; path 11. The integrity *flag* on the demo is not asserted in a test; see `harness.md`. |
| Trust report pack | v3, 7 slugs | v4, 9 slugs (+3, `interest-allocation` removed), with a membership reconcile | 650C #1809 |

## 6. §16.14.4 noted-not-fixed items

| Item | Status on `main` `b08a8916a` | Evidence |
|---|---|---|
| `TrustDomainEvent.ReconciliationCompleted` never published; its two listeners are dead | **still open** | No `ReconciliationCompleted.of(` call outside `TrustDomainEvent.java`. The listeners remain at `$B/verticals/legal/trustaccounting/event/TrustNotificationHandler.java:74` and `$B/customerbackend/service/TrustLedgerPortalSyncService.java:173`. The 650A lock is a direct call, as R10 intended. |
| `InvestmentMaturing`, `PaymentAwaitingApproval / PaymentApproved / PaymentRejected` never published | **still open** | No `.of(` call for any of them outside `TrustDomainEvent.java` |
| Nothing ever sets an investment `MATURED` | **still open** | `MATURED` is only read (`TrustInvestmentService.java:126-127` `HELD = {ACTIVE, MATURED}`, `TrustAccountService.java:278,360`) and labelled (`TrustReportWords.java:53`). Nothing sets it. |
| Notification types `TRUST_RECONCILIATION_OVERDUE`, `TRUST_APPROVAL_AGING` never created | **still open** | They appear only in the catalogue list `$B/notification/NotificationService.java:205,207` |
| Demo seeder main account is `SECTION_86`, while the dashboard and statement builders look for a primary `GENERAL` | **statement-builder half addressed; dashboard half open** | 652D #1818 fixed the statement builders' account choice (closed primary). Its case (c) showed the seeder's `SECTION_86` main account takes the single-candidate path and was green before and after (`$EV/652D/README.md:60`, `$EV/652D/found.md:35`). The seeder still creates `SECTION_86` (`$B/demo/seed/LegalDemoDataSeeder.java:226`). `DashboardService.java:624` still filters `isPrimary() && accountType() == GENERAL`. |
| `matterBalance` for the closure gate is not account-scoped | **kept by design** (unchanged) | `$B/verticals/legal/closure/gates/TrustBalanceZeroGate.java:48` → `TrustLedgerQueryRepository.matterBalance(projectId)` (`:439`) |
| Audit label registry keys are dotted while the emitted types are underscored | **still open** (the old mismatch). New events got correct keys in their slices. | `$B/audit/AuditEventTypeRegistry.java:91,96,101` (`trust.transaction.approved`, `trust.transaction.rejected`, `trust.*`) |
| The transactions list endpoint takes a single `status` | **endpoint unchanged; worked around in the frontend as designed (R9)** | `TrustTransactionController.java:46` `@RequestParam(required = false) String status`. 652B #1816 merged the fetches for RECORDED + APPROVED, and 652H #1820 added REVERSED (`MATCHABLE_STATUSES`, `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/new/page.tsx:58`). |
| Postings on a `CLOSED` trust account are refused, including a late statement line | **still open** (kept as-is; raised for a later ruling) | Pinned by `StatementLinePostingIntegrationTest.aLineOnAClosedAccount_isRefused` |

## 7. §16.6.5 consumer follow-ups (`../claude-for-legal-sa`, `kazi-legal-za` plugin)

The phase did not change the consumer repo, as E7 requires. Its last commit is `10cf12b` from 2026-07-04, and `git log --since=2026-11-15` is empty.

The Kazi-side MCP contract did change:

- `McpTrustBalanceDto` (`$B/mcp/dto/McpTrustBalanceDto.java:34-44`) gained `investedMinor`, `fundLedgerMinor`, `unidentifiedDepositsMinor`, `totalTrustCreditorsMinor`, `cashbookMinor` and `asAt`. These names drop the architecture's `…MinorUnits` suffix.
- `McpTrustTransactionItem` gained `typeLabel`, `direction` and `ledger` (`McpTrustTransactionItem.java:29-32`).

| # | Follow-up | Status |
|---|---|---|
| 1 | The `trust-reconciliation` skill should use the full equation and the new creditor fields | **open**. `kazi-legal-za/skills/trust-reconciliation/SKILL.md:40-41,55,67` still reads one "account total" from `get_trust_balance` and compares it with the visible creditor balances. |
| 2 | Remove references to interest runs, LPFF rates, `INTEREST_CREDIT` / `INTEREST_LPFF` | **nothing to remove found**. A grep of the consumer repo finds no such reference in the plugin; the only hits are unrelated `jurisdictions/za` statute files on the prescribed rate. Not formally closed: no change was made. |
| 3 | Read `typeLabel` instead of mapping type codes | **open**. `SKILL.md:42,62` reads `type`. |
| 4 | The demo's overdrawn client appears as an integrity flag; say so in the walkthrough | **open**. There is no mention. |
| 5 | Consider a read tool for the integrity report | **open** (out of scope; a later phase) |

## 8. Decisions-log follow-ups (`tasks/phase92-decisions.md`, "Follow-up PRs" table, lines 33–50)

The ids run FU-1..FU-16. In the file, FU-9 is listed after FU-11.

| Id | Covers | One line |
|---|---|---|
| FU-1 | F-6a | Re-check s86(3) placement cashbook cover at approval, under the funding-account lock (two pending placements must not jointly overdraw) |
| FU-2 | R-2 | Gate trust-figure reports by the trust-view capability, not only financial visibility |
| FU-3 | R-1 | Statement builders read general + invested balances in one read-only `REQUIRES_NEW` REPEATABLE_READ sub-transaction |
| FU-4 | W1 | The firm client-ledger-statement report applies the "reversal reasons stay off client surfaces" rule |
| FU-5 | S2 | Deposit-reversal approval checks cover as at `reversalDate` as well as today |
| FU-6 | F-646C-1, F-647C | Refuse a duplicate same-end-date statement unless it replaces the first, and refuse future-dated lines on import / record |
| FU-7 | O-1 | `legal-za` SoA label "Trust balance held in the firm's trust account" (pack bump + reconcile entry) |
| FU-8 | F-17 | Raise the Surefire `-Xmx` in `backend/pom.xml` (durable fix: cull context-cache keys) |
| FU-9 | F-6a + F-6b | One PR with FU-1: approval-time cashbook cover for every firm-initiated outflow under an account lock, plus a `CASHBOOK_IN_DEBIT` integrity flag |
| FU-10 | F-13 | Order the after-lock boundary by a database-side value, not the app-host `clock.instant()` |
| FU-11 | 650A F-12 | Fix the UNIDENT_ALLOC_OUT approval lock order (item + destination client anchor before the account lock), avoiding the deadlock with a concurrent s86(4) placement |
| FU-12 | 650C F-650C-1 | `REPORT_GENERATED` / `REPORT_EXPORTED` audit rows are never flushed (read-only transaction); write them in `REQUIRES_NEW` or after commit |
| FU-13 | 650C F-650C-2 | Receipts & payments as-at reversal rule: apply `contra.transaction_date <= :toDate` so Section 35 part (1) agrees with parts (5) and (6) |
| FU-14 | 650C OBS-3 | Postings dated before a trust account's `opened_date` are accepted; decide whether to refuse or flag them (proposal: flag) |
| FU-15 | 650C merge | `pre-pr-merge-gate.sh:180` fails closed above 300 files (`gh pr diff` HTTP 406); fall back to the paginated files API |
| FU-16 | 652H M-1, F-652H-2, HF-7 | Auto-match pair rules: shadows of a REVERSED original, contras not candidates for unrelated lines, and the statement-only type set on the wire |

## Facts not established here (UNKNOWN)

- **The defect register (exit-bar item 3: severity and disposition of every `$EV/*/found.md`)**: UNKNOWN. It is outside this file's brief and was not compiled. The `found.md` files are listed by `find qa_cycle/evidence/phase92 -name found.md`: 31 files, plus `qa_cycle/evidence/phase92-exit/found.md`.
- **Exit-suite counts (652.32)**: UNKNOWN. The orchestrator is still running them.
