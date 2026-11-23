# Phase 92 exit — defect register (652F, exit-bar item 3)

Compiled 2026-11-20 on worktree `epic-652F/exit-gate` = `main` `b08a8916a`. It is the register that task 652.34's
exit bar item (3), "0 open HIGH or MEDIUM trust defects", is judged against.

**Updated 2026-11-23** for the exit-gate re-run on `main` `ef0563fe3`, after the follow-up PRs #1823–#1842. Each row the
follow-ups closed now reads `CLOSED → <FU/id> #<PR> (<merge sha>)`, followed by its 2026-11-20 status. PR numbers and merge
SHAs are from `tasks/phase92-decisions.md` (the Follow-up PRs table and the D-rows at the bottom). Every other column is unchanged.

**Sources.** Every `$EV/*/found*.md` (31 files) and `$X/found.md`; `tasks/phase92-decisions.md` (rulings, agent rows,
FU-1..FU-16); architecture §16.14.4; `$X/audit-facts.md` §1 (hypothesis outcomes) and §6 (§16.14.4 status), used, not
repeated. The 11 nested `$EV/*/gates*/found-obs.md` files were scanned too: an item carried into a top-level found.md is
merged into that row; the few never carried are in table N.

**Shorthands.** `$EV` = `qa_cycle/evidence/phase92` · `$X` = `qa_cycle/evidence/phase92-exit` · `$B` =
`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting` · `$FT` =
`frontend/app/(app)/org/[slug]/trust-accounting` · dec = `tasks/phase92-decisions.md` · "PR #n" = the squash commit on `main`.

## Rules applied

- **Severity.** The source's own severity where it states one, marked `(src)`. Otherwise the rubric below, marked `(assigned)`.
  - HIGH: a wrong money figure or balance on an authoritative surface (ledger, cashbook, reconciliation, statement, monthly
    list, Section 35, portal), lost or corrupted data, a privacy leak to a client, a security or tenant hole, or a
    legal-compliance breach.
  - MEDIUM: a wrong or misleading figure or label on a non-authoritative surface, a concurrency race or deadlock with no
    corruption, a missing refusal that allows a bad state that is detectable and correctable, a broken UI path for a
    supported task, or a lost report or audit row.
  - LOW: cosmetic, a11y, wording, test quality, refactor or doc staleness.
  - Where a defect can be reached only through the API, because no UI can create its precondition (for example closing a
    trust account, 652D (iv)), the row says so. That reason is the only one used to place a row lower than its effect
    would place it.
- **CLOSED** (added 2026-11-23). Fixed after the phase by a follow-up PR now merged on `main`. The row names the
  follow-up, its PR and its squash commit. A hypothesis that a follow-up reproduced and fixed, or tested and did not
  reproduce as harm (646C O-2), is CLOSED too, and the row says which. CLOSED counts as not open, like FIXED.
- **Open.** Any row that is not FIXED, CLOSED, DECLINED or STRUCK. **`OPEN → FU-n` counts as open.** The founder ruling of
  2026-11-18 (dec, and the task-file header: "the 2026-11-18 rulings … are follow-up PRs after the phase") schedules a
  fix. It does not close the item.
- **FIXED** only where the fix is on `main`, checked by reading the code (file:line cited) and, where the slice committed
  one, its red→green evidence.
- **HYPOTHESIS.** The harmful outcome was never reproduced by a test, a browser run or an observation. These rows are
  listed, with the severity they would have if reproduced, and are **not counted as confirmed defects**. They are totalled
  on their own line so the verdict can see them.
- **Dedup.** One row per distinct defect. Every source is cited in the row.

## Summary

224 rows (sections A–H from the found.md files, the decisions log and §16.14.4; section N from the nested gate files).

### 2026-11-23: after the follow-ups (current)

Re-counted from the tables below, by source severity under the rules above.

| Severity | FIXED (in phase) | CLOSED (follow-up) | OPEN → FU-n | OPEN — unassigned | DECLINED | STRUCK | HYPOTHESIS | Total |
|---|---|---|---|---|---|---|---|---|
| HIGH | 14 | 4 | 0 | 0 | 0 | 0 | 0 | 18 |
| MEDIUM | 28 | 17 | 0 | 0 | 0 | 0 | 0 | 45 |
| LOW | 22 | 4 | 7 | 104 | 18 | 1 | 5 | 161 |
| **Total** | 64 | 25 | 7 | 104 | 18 | 1 | 5 | 224 |

**Open trust defects at HIGH or MEDIUM: 0.** No row of any kind, trust or not, is open at HIGH or MEDIUM, and no
HIGH or MEDIUM hypothesis is left. On exit-bar item (3), **met**.

Every one of the 18 open on 2026-11-20 is closed:

- HIGH (3): 648A F-6 / F-6b → FU-9 #1825; 648F R-2 → FU-2 #1823; 649B F1 / F-652E-5 → FU-13 #1824 (widened, so the
  scope gap in note 2 is closed).
- MEDIUM (15): F-646C-1 and F-652C-1 → FU-6b #1831; F-647C → FU-6a #1829; F-646D-1 → #1835; F-646E-2 → #1837;
  CF-3a and CF-3b → CF-3 #1826; 648B F-1 → #1838; 648B F-11 → #1841 (founder ruling: option A); 648D F-4 and F-5 →
  #1836; 648F R-1 → FU-3 #1828; 649B S2 → FU-5 #1827; 649B W1 → FU-4 #1832; F-650C-1 → FU-12 #1833.

The three HIGH / MEDIUM hypotheses are closed too: 647C #2 / 649B F2 was reproduced as HIGH and fixed (#1839); HF-7 was
reproduced as MEDIUM and fixed (FU-16 #1834); 646C O-2 was tested and not reproduced as harm (#1840), with a LOW
remainder. Four LOW rows were closed by follow-ups as well: F-17 → FU-8 #1830, 650A F-12 → FU-11 (closed by #1825),
F-650C-2 → FU-13 #1824, 652H M-1 → FU-16 #1834.

**Under the rubric applied throughout: 2 open, both MEDIUM:** 648E F-4 and F-652E-3. Both are LOW by source (note 4), so
the headline stays 0 by the source-severity rule. CF-3a and F-650C-2, the other two rubric-raised rows, are closed.

**Carried to the backlog (not closed, left at their actual status).** 116 LOW rows stay open: 7 `OPEN → FU-n`
(648F O-1 → FU-7; 650A F-13 → FU-10; F-650C-OBS-3b → FU-14; FU-15; F-652B-1, F-652B-5 and F-652H-2 → FU-16 (c)),
104 `OPEN — unassigned` and 5 LOW hypotheses. Among them:

- the rubric-only rows 648E F-4 and F-652E-3 (rubric MEDIUM, above);
- the rows placed LOW because their precondition is API-only today (note 5): 652D (i) (rubric HIGH), F-652D-O3
  (rubric MEDIUM), 652D (iii) and §16.14.4 #9. They become real once a UI can close an account or post to a
  non-primary GENERAL account;
- the LOW remainder of 646C O-2 (net-to-zero stray links on an undiscarded wrong file).

### 2026-11-20: 652F (history, kept as compiled)

| Severity | FIXED | OPEN → FU-n | OPEN — unassigned | DECLINED | STRUCK | HYPOTHESIS | Total |
|---|---|---|---|---|---|---|---|
| HIGH | 14 | 3 | 0 | 0 | 0 | 1 | 18 |
| MEDIUM | 28 | 7 | 8 | 0 | 0 | 2 | 45 |
| LOW | 22 | 11 | 104 | 18 | 1 | 5 | 161 |
| **Total** | 64 | 21 | 112 | 18 | 1 | 8 | 224 |

All 18 open HIGH and MEDIUM rows concern trust. No non-trust row is open at HIGH or MEDIUM.

**Open trust defects at HIGH or MEDIUM: 18.** HIGH (3): 648A F-6 / F-6b (→ FU-9), 648F R-2 (→ FU-2), 649B F1 / F-652E-5
(→ FU-13, scope gap). MEDIUM (15): F-646C-1 (→ FU-6), F-646D-1 (unassigned), F-646E-2 (unassigned), F-647C future-dated
line (→ FU-6), CF-3a (unassigned), CF-3b (unassigned), 648B F-1 (unassigned), 648B F-11 (unassigned), 648D F-4
(unassigned), 648D F-5 (unassigned), 648F R-1 (→ FU-3), 649B S2 (→ FU-5), 649B W1 (→ FU-4), F-650C-1 (→ FU-12),
F-652C-1 (→ FU-6).

**Under the rubric applied throughout: 21 open (5 HIGH, 16 MEDIUM).** CF-3a (MEDIUM → HIGH) and F-650C-2 (LOW → HIGH,
→ FU-13) rise to HIGH; 648E F-4 and F-652E-3 (LOW → MEDIUM, both unassigned) rise to MEDIUM (note 4). The headline number
stays 18, by the source-severity rule above; follow-up triage should start from 21.

On exit-bar item (3), **FAIL**: the bar is 0 and the register holds 18 (21 under the rubric). Ten of the 18 are already scheduled as
follow-ups by the founder ruling of 2026-11-18. That ruling defers them; it does not close them.

**Unreproduced trust hypotheses that would be HIGH or MEDIUM if reproduced: 3. They are not counted above.** 647C #2 /
649B F2 (HIGH: the client-ledger history running balance), HF-7 (MEDIUM: a contra auto-matched to an unrelated line),
646C O-2 (MEDIUM: links left by auto-match on a refused statement). The mechanism of the first two is visible in the code
on `main` (cited in their rows). If the verdict counts them, the total is 21 by source severity (24 under the rubric).

**Notes for the closure report** (2026-11-20; kept as compiled. The 2026-11-23 status is added in brackets.)

1. **FU-1 appears already delivered.** 648A F-3 (PR #1799) re-checks s86(3) cover at approval, under the funding-account
   lock, with both FU-1 tests (two pending placements, sequential and concurrent): `TrustInvestmentService.java:496-505`.
   FU-1's `max(today, date)` equals today for a placement, which cannot be future-dated. The open part of F-6a + F-6b is
   FU-9: client debits and the `CASHBOOK_IN_DEBIT` flag. [2026-11-23: FU-1 and FU-9 DONE in PR #1825 (0c01b72b5).]
2. **FU-13 does not cover 649B F1.** FU-13's stated fix (`contra.transaction_date <= :toDate`) fixes F-650C-2 (a sweep
   reversed after the year end). A pair reversed inside the period would still drop out of Receipts & Payments, which is
   what the exit month shows (F-652E-5: #7–#10 absent). `$X/found.md` F-652E-5 says "already logged as FU-13"; the FU-13
   text needs widening, or a new FU. [2026-11-23: FU-13 was widened and is DONE in PR #1824 (c4f66f9f6).]
3. **Eight open MEDIUM rows have no follow-up:** F-646D-1, F-646E-2, CF-3a, CF-3b, 648B F-1, 648B F-11, 648D F-4, 648D
   F-5. CF-3a / CF-3b (the auto-match lock) were last routed to "652A/B" and to the orchestrator, and no slice took them.
   [2026-11-23: all eight closed: #1835, #1837, CF-3 #1826 (both CF-3 rows), #1838, #1841, #1836 (both 648D rows).]
4. **Source severity kept where the rubric reads higher:** CF-3a MEDIUM (src; rubric HIGH, corrupted reconciliation state),
   F-650C-2 LOW (src; rubric HIGH, a Section 35 figure), 648E F-4 LOW (src; rubric MEDIUM), F-652E-3 LOW (src; rubric
   MEDIUM). Raising CF-3a would move one open row from MEDIUM to HIGH; raising the other three would add 3 to the open
   total: 21 (5 HIGH, 16 MEDIUM), or 24 with the hypotheses. All four rows are trust rows and open. [2026-11-23: CF-3a
   and F-650C-2 are closed; 648E F-4 and F-652E-3 stay open at LOW and are carried to the backlog.]
5. **Placed LOW because the precondition is API-only today:** 652D (i) (two open GENERAL accounts for one client; rubric
   HIGH), F-652D-O3 (rubric MEDIUM), 652D (iii), §16.14.4 #9. They become real once a UI can close an account or post to a
   non-primary GENERAL account. [2026-11-23: unchanged, all four carried to the backlog.]
6. **Fixed claims not verified:** 650A F-5 ("likely resolved as a side effect"; no test) stays OPEN. No other FIXED row
   rests on a claim alone: each cites code on `main` or the slice's committed red→green evidence. The FIXED rows for 649C
   O-OBS-2 and 648A W-1 / M1–M3 were checked by grep (`page.tsx:630` `reconciliation-error-title`;
   `trust-investment-basis.test.tsx:379`; `TrustNotificationHandler.java:197`; `frontend/lib/schemas/trust.ts:383`).

## A. Stage 1–2 (645A, 646A–646E) and the pre-phase hypotheses

| ID | Source(s) | Description | Trust? | Severity | Disposition |
|---|---|---|---|---|---|
| H-a | `$X/audit-facts.md` §1; `$EV/649A/RED-RUNS.md` | An approved credit reversal moved neither the cashbook nor the client card | Yes | HIGH (assigned) | FIXED (PR #1789; `TrustReversalEquationIntegrationTest.approvedCreditReversal_movesCashbookAndClient`, red in `$EV/649B/red-run.txt`) |
| H-b | `$X/audit-facts.md` §1; `$EV/649A/RED-RUNS.md` | A debit reversal credited the card only if one existed (reproduced through direct repository writes only) | Yes | HIGH (assigned) | FIXED (PR #1788, derived balances; V16 drops the card balance; regression test `…debitReversalForAClientWithoutAnAnchor_movesBothSides`) |
| H-c | `$X/audit-facts.md` §1 | A rejected credit reversal blocked any later reversal | Yes | MEDIUM (assigned) | FIXED (PR #1789; `rejectedCreditReversal_leavesOriginalReversible`) |
| H-d | `$X/audit-facts.md` §1; founder ruling #1 | Only `APPROVED` rows could be reversed | Yes | MEDIUM (assigned) | FIXED (PR #1789; `recordedDeposit_isReversible`) |
| SEED-1 | `$EV/baseline/README.md`; `$X/audit-facts.md` §4–5 | Seeded demo books unbalanced before the phase: cashbook R27 000,00 vs stored card total R0 | Yes | HIGH (assigned) | FIXED (PR #1788; seeded through the posting service, EQ-2 difference 0,00) |
| E8-PARSE | `$X/audit-facts.md` §1; architecture §16.9.3, R9 | `BankStatementParseException` → HTTP 500 | Yes | LOW (assigned) | STRUCK / NOT REPRODUCED (already 422: `GlobalExceptionHandler.java:150`, `BankStatementImportErrorIntegrationTest`) |
| 645A F-1 | `$EV/645A/found.md`; dec "645A F-1" | Interest-run wizard Calculate step overflows the dialog at 1440×900 | No | LOW (src) | DECLINED (ruling 645A F-1: dropped; the wizard was removed in PR #1810) |
| 646A obs-1 | `$EV/646A/found.md` #1 | Client-ledger Type filter offered "Interest Credit" / "Interest LPFF", which can never match after V16 | Yes | LOW (assigned) | FIXED (PR #1791: `git log -S'Interest LPFF'` → `ca02cac48`; no `INTEREST_LPFF` / `INTEREST_CREDIT` in `frontend/{app,components,lib}` outside tests) |
| 646A obs-2 | `$EV/646A/found.md` #2 | `chk_trust_txn_customer_required` still named `INTEREST_LPFF` | Yes | LOW (assigned) | FIXED (PR #1790; `V18__phase92_trust_creditor_accounts.sql:50` drops it, `:53` adds `chk_trust_txn_ledger_target`) |
| 646A obs-3 | `$EV/646A/found.md` #3 (CodeRabbit); `$EV/648D/found.md` F-2 (data half) | Portal read-model rows of interest postings deleted by V16 stay on a persistent database | Yes | LOW (assigned) | DECLINED (founder decision 5 / R12: no real tenant posted a run; environments are reset) |
| 646A obs-4 | `$EV/646A/found.md` #4 | EXPLAIN: the planner prefers `idx_trust_txn_account_date` over `idx_trust_txn_ledger_sum` (9–21 ms) | No | LOW (assigned) | OPEN — unassigned (re-check on a multi-account dataset; ADR-378 snapshot escape hatch) |
| 646B-1 | `$EV/646B/found.md` | `postReversal` did not copy `creditor_account_id` / `investment_id` onto the contra, so reversing a NON_CLIENT or investment row would fail `chk_trust_txn_ledger_target` | Yes | MEDIUM (assigned) | FIXED (PR #1794; `$B/transaction/TrustPostingService.java:268` `reversal.copyLedgerTargetFrom(original)`) |
| F-646C-1 | `$EV/646C/found.md`; dec F-646C-1, FU-6; `$EV/652C` D-6/D-12 | Two same-account statements ending on the same date (or an identical re-import) both pass the period-fit rule, so a month can be reconciled against a superseded intraday statement | Yes | MEDIUM (assigned): the reconciliation stays internally balanced but misstates which items are outstanding | **CLOSED → FU-6b #1831 (8b9acc684)**. 2026-11-20 status: OPEN → FU-6 |
| 646C-HF1 | `$EV/646C/found.md` (CodeRabbit Major ×2 → 649C) | `completeReconciliation` trusted the stored `is_balanced` and did not re-run the period-fit rules | Yes | HIGH (assigned): a month could be completed on stale figures | FIXED (PR #1804; `$B/reconciliation/TrustReconciliationService.java:1084` re-runs `requireStatementFitsPeriodEnd` in complete; `$EV/649C/red-H1a.txt`) |
| 646C-HF2 / 652A | `$EV/646C/found.md` (→ 652A); `$X/audit-facts.md` §1 | The wizard ignored `{success:false}` from calculate after match / exclude / unmatch: stale summary shown as current | Yes | MEDIUM (assigned) | FIXED (PR #1815; `$FT/reconciliation/new/page.tsx:128` banner, `:183` stale marker; `$EV/652A/red-before.txt`) |
| 646C-HF3 | `$EV/646C/found.md`; `$EV/649C/found.md` H-1b | Two stale or cut-off code comments (`integrity_flags`, period-fit comment) | No | MEDIUM (src: architecture reviewer; comment only) | FIXED (PR #1804; `TrustReconciliationService.java:840` comment completed) |
| 646C O-1 / 652B | `$EV/646C/found.md`; `$EV/646C/gates/found-obs.md` O-1; `$EV/649C/found.md` N-10; `$X/audit-facts.md` §1 | The manual-match pane listed `APPROVED` rows only, so a `RECORDED` deposit could not be matched in the wizard | Yes | MEDIUM (assigned): broken UI path | FIXED (PR #1816; `$FT/reconciliation/new/page.tsx:58` `MATCHABLE_STATUSES`; `$EV/652B/red-before.txt`) |
| 646C O-2 | `$EV/646C/found.md`; `$EV/646C/gates/found-obs.md` O-2 | Auto-match runs on a statement that `createReconciliation` then refuses; any links it made would persist and could hide a transaction from another draft's outstanding items | Yes | MEDIUM if reproduced (assigned) | **CLOSED (not reproduced as harm) → #1840 (7371213a8); LOW remainder: two coincidental stray links of opposite sign and equal total on an undiscarded wrong file cancel in G1, so a completed reconciliation could under-list both outstanding items (backlog; dec 646C-O2 D-1)**. 2026-11-20 status: HYPOTHESIS — unverified (0 links in the run that saw it) |
| F-646D-1 | `$EV/646D/found.md` (orchestrator ruling D8) | A back-dated fee transfer has no lower bound: it is accepted before the deposit it draws on, leaving the client ledger in debit as at past dates; the cover check is as at today only | Yes | MEDIUM (assigned): missing refusal; the intermediate-date debit is not flagged (649C H-2/H-3 checks the date asked only) | **CLOSED → F-646D-1 #1835 (ae0f415cd)** (extension to back-dated PAYMENT / REFUND / TRANSFER_OUT proposed to the founder, not done: dec F-646D-1 D-1). 2026-11-20 status: OPEN — unassigned (verified: `$B/transaction/TrustTransactionService.java:1458` refuses only a future date. Mitigated, not closed, by 650A `BACKDATED_AFTER_LOCK` and the 650D locked-month warning. Same class as S2/FU-5, which covers deposit reversals only) |
| F-646D-2 | `$EV/646D/found.md`; `$EV/649C/found.md` N-7 | The harness checks the dated balance reads only as at today | No | LOW (assigned) | OPEN — unassigned |
| F-646D-3 | `$EV/646D/found.md`; `$EV/649C/found.md` N-8 | `CreditorBreakdown` lives in `reconciliation` but is a books concept | No | LOW (assigned) | OPEN — unassigned |
| F-646E-1 | `$EV/646E/found.md` | The reconciliation split pane printed a raw type code | Yes | LOW (assigned) | FIXED (PR #1792; `frontend/components/trust/ReconciliationSplitPane.tsx:538` renders `tx.typeLabel`) |
| F-646E-2 | `$EV/646E/found.md` | The Transactions page's date and client filters are silently ignored: the URL changes, the rows do not | Yes | MEDIUM (assigned): broken UI path for a supported task, with no indication | **CLOSED → F-646E-2 #1837 (e2e198911)**. 2026-11-20 status: OPEN — unassigned (verified: `$B/transaction/TrustTransactionController.java:43-51` takes no date or client param; `$FT/transactions/actions.ts:62-66` still sends them; `page.tsx:313` still renders `TransactionFilters`) |
| F-646E-3 / HF-4 | `$EV/646E/found.md`; `$EV/647A/found.md` #4 | `PAIR` reversals always routed to `reverseTransferOut`, so an `UNIDENT_ALLOC_OUT` reversal would fail | Yes | MEDIUM (assigned) | FIXED (PR #1797; `TrustTransactionService.java:1300-1301` routes `UNIDENT_ALLOC_OUT` separately) |

## B. Stage 3 (647A–647D)

| ID | Source(s) | Description | Trust? | Severity | Disposition |
|---|---|---|---|---|---|
| HF-7 | `$EV/647A/found.md` #1; `$EV/649B/found.md` F3; `$EV/649C/found.md` N-2; `$EV/652H/found.md`; dec FU-16 | A posted `REVERSAL` contra is an auto-match candidate, so it could match an unrelated opposite-sign line and put its original back into outstanding items | Yes | MEDIUM if reproduced (assigned) | **CLOSED → FU-16 #1834 (008084bd0)** (reproduced as MEDIUM, fixed: FU-16 (b)). 2026-11-20 status: HYPOTHESIS — unverified (owner FU-16). The precondition is verified on `main`: `findUnmatchedCandidates` (`$B/transaction/TrustTransactionRepository.java:333`) has no `REVERSAL` exclusion, and contras are auto-matched to their own lines in the exit month (#8, #10). A false match on an unrelated line has never been reproduced. |
| 647A-D1 | `$EV/647A/found.md` #2; `$EV/648B/found.md` R-2 | `FUND_INTEREST` was not refused on a section 86(4) account | Yes | MEDIUM (assigned) | FIXED (PR #1801; `$B/reconciliation/StatementLinePostingService.java:201`) |
| F-647C (future-dated line) | `$EV/647A/found.md` #3; `$EV/647C/found.md` #4; `$EV/647B/found.md` F-4 and "Not newly observed"; `$EV/649C/found.md` N-9; dec F-647C, FU-6 | A statement line dated after today can be recorded (and imported). It then cannot be unmatched until its date, the Fund card and running balance disagree, and harness EQ-4 rule (d) breaks for a future-dated item | Yes | MEDIUM (assigned): missing refusal (founder ruled the refusal) | **CLOSED → FU-6a #1829 (3aa89cf7b)**. 2026-11-20 status: OPEN → FU-6 (verified: no future-date check in `recordFromLine` or `importBankStatement`; the only one is the period end, `TrustReconciliationService.java:864`) |
| HF-8 | `$EV/647A/found.md` #5; `$EV/649B/found.md` F5; `$EV/649C/found.md` N-6; dec 652H D-0; `$X/audit-facts.md` §1 | `manualMatch` (and auto-match) refused a `REVERSED` original, so its own bank line could not be matched | Yes | MEDIUM (assigned): broken path; exit-month #7/#9 could not be matched | FIXED (PR #1820; `TrustReconciliationService.java:458`; `$EV/652H/red-on-main.txt`) |
| 647A #6a | `$EV/647A/found.md` #6 | VAT with more than two decimals reached `chk_trust_txn_charge` as a 500 | Yes | LOW (assigned) | FIXED (PR #1794, in-slice `@Digits` + Zod) |
| 647A #6b | `$EV/647A/found.md` #6 | Native `step` validation silently blocks a submit in jsdom; trust dialogs relying on it could too | No | LOW (assigned) | OPEN — unassigned |
| 647A #6c | `$EV/647A/found.md` #6 | `bank_statement_line.unmatched` audit for a matched (not recorded) line carries no details | Yes | LOW (assigned) | OPEN — unassigned |
| CF-3a | `$EV/647A/found.md` #7; `$EV/648B/found.md` F-10; `$EV/652A/found.md` CF-3 | `autoMatchStatement` reads the statement and its lines without a lock and writes `matched_count = alreadyMatched + autoMatched`, so a concurrent record or manual match can be overwritten (a line flipped to `AUTO_MATCHED` over `RECORDED`, a lost count) | Yes | MEDIUM (src, 648B F-10). Under the rubric this reads as HIGH (corrupted data); the source severity is kept. | **CLOSED → CF-3 #1826 (7fede74af)**. 2026-11-20 status: OPEN — unassigned (no FU covers it. Verified: `TrustReconciliationService.java:345-407` uses `findById` and `setMatchedCount(alreadyMatched + autoMatched)`. 648B added a backstop for interest only: the V24 unique partial index) |
| CF-3b | `$EV/647A/found.md` #7; `$EV/652A/found.md` CF-3 | `manualMatch` (line → transaction) and auto-match (transaction → line) can deadlock: 40P01, shown as a generic server error | Yes | MEDIUM (assigned): deadlock, no corruption | **CLOSED → CF-3 #1826 (7fede74af)**. 2026-11-20 status: OPEN — unassigned (closes with CF-3a) |
| CF-1 | `$EV/647A/found.md` #7; `$EV/652A/found.md` CF-1; dec 652A D-6 | After a wizard reset, a late recalculation in match / exclude / unmatch restored the old account's reconciliation into the new wizard | Yes | MEDIUM (assigned) | FIXED (PR #1815; `wizardGeneration` guards in `$FT/reconciliation/new/page.tsx`; `$EV/652A/red-before-d6.txt`) |
| 647B F-1 | `$EV/647B/found.md`; `$EV/649C/found.md` N-3 | `openItems` on the creditor-accounts response is not built | Yes | LOW (assigned) | OPEN — unassigned |
| 647B F-2 | `$EV/647B/found.md` | Direct `UNIDENTIFIED_DEPOSIT` test rows now carry an anchor statement (affects later test design) | No | LOW (assigned) | DECLINED (consequence of ruling R5; recorded only) |
| 647B F-3 | `$EV/647B/found.md` | Fund-ledger pager extracted; no behaviour change | No | LOW (assigned) | DECLINED (no behaviour change; recorded only) |
| 647C #1 | `$EV/647C/found.md` #1; `$EV/648B/found.md` R-3 | The import-time suggestion for an s86(4) interest line was `FUND_INTEREST`, not `INVESTMENT_INTEREST` | Yes | LOW (assigned) | FIXED (PR #1801; `TrustReconciliationService.java:281`) |
| 647C #2 / 649B F2 | `$EV/647C/found.md` #2; `$EV/649B/found.md` F2 (CodeRabbit example); `$EV/652H/found.md` "Not touched" | The client-ledger `/history` running balance leaves out a `REVERSED` original and its contra, so between the original's date and a later contra date the running balance disagrees with the as-at client balance | Yes | HIGH if reproduced (assigned): a wrong balance on the firm client ledger | **CLOSED → 647C-2 #1839 (682a0707e)** (reproduced as HIGH, fixed: dec 647C-2 D-1). 2026-11-20 status: HYPOTHESIS — unverified (no red test written. The mechanism is on `main`: `TrustTransactionRepository.java:108-109` still filters `status IN ('RECORDED','APPROVED') AND transaction_type <> 'REVERSAL'`. Unassigned) |
| 647C #3 | `$EV/647C/found.md` #3 | Substring keyword matching suggests `BANK_CHARGE` for "Sheriff fees payment" etc. | Yes | LOW (assigned) | DECLINED (orchestrator ruling 647C #4: implemented to spec; the firm confirms) |
| F-647D-1 | `$EV/647D/found.md`; `$EV/649C/found.md` N-4 | Unmatching the line of an item whose allocation was undone back-dated a contra, so the unidentified ledger read in debit as at past dates | Yes | HIGH (assigned): wrong balance as at a date | FIXED (PR #1797; `$B/creditor/UnidentifiedDepositService.java:280`; `UnidentifiedDepositIntegrationTest.java:1284`; `$EV/647D/red-F-647D-1.txt`) |
| F-647D-2a | `$EV/647D/found.md` | The client–matter link was not re-checked when an allocation was approved | Yes | LOW (assigned): the link was valid at request; no figure is wrong | FIXED (PR #1797; `allocation_approval_rechecksTheClientMatterLink…`) |
| F-647D-2b | `$EV/647D/found.md` | The same for payments: `performApprovalCompletion` does not re-check the link | Yes | LOW (assigned) | OPEN — unassigned |
| 647D-RF | `$EV/647D/found.md` (fix cycle 1, F-647D-8); `$EV/649C/found.md` N-5 | Split `UnidentifiedDepositService` (~760 lines) | No | LOW (assigned) | OPEN — unassigned (refactor backlog; ruling R7) |
| F-647D-3 | `$EV/647D/found.md` | Shared trust pickers do not forward the field id / aria attributes | No | LOW (assigned) | OPEN — unassigned |
| F-647D-4 | `$EV/647D/found.md`; `$EV/647D/gates/found-obs.md` H-1 | Second call-site wrapping Badge; add a `wrap` prop to the shared Badge | No | LOW (assigned) | OPEN — unassigned (call site fixed in PR #1797; `packages/ui/src/badge.tsx:8` is still `whitespace-nowrap`) |
| F-647D-5 | `$EV/647D/found.md` | A refused approval's inline error runs past the table edge | No | LOW (assigned) | OPEN — unassigned |
| F-647D-6 | `$EV/647D/found.md` | Allocate / Fund-payment date pickers use `receivedDate` as `min` for a reopened item; the server's bound is later | Yes | LOW (assigned): the server refuses with the right date | OPEN — unassigned |
| F-647D-7 | `$EV/647D/found.md`; `$EV/649C/found.md` N-4 | Once a resolution has posted, the line can never be unmatched | Yes | LOW (assigned) | DECLINED (647D R1: accepted trade-off) |
| F-647D-9 | `$EV/647D/found.md` | At 1280 px long type labels wrap to 3 lines | No | LOW (src: cosmetic) | OPEN — unassigned |

## C. Stage 4–5 (648A–648F)

| ID | Source(s) | Description | Trust? | Severity | Disposition |
|---|---|---|---|---|---|
| 648A F-1 / D-1 | `$EV/648A/found.md`; `$EV/648A/gates/found-obs.md` D-1 | Once any s86(4) placement was approved, every client-ledger read (card, list, overview, MCP, Client Trust Balances) returned 500 | Yes | HIGH (src) | FIXED (PR #1799; `$B/ledger/LedgerTotals.java:68`; `InvestmentClientLedgerReadSurfacesIntegrationTest`; `$EV/648A/red-backend-gate-d1.txt`) |
| 648A F-2 | `$EV/648A/found.md` | `trust-transactions.test.tsx` makes real HTTP calls to :8080 (noise; passes) | No | LOW (assigned) | OPEN — unassigned |
| 648A F-3 / F-6a | `$EV/648A/found.md` F-3; `$EV/649C/found.md` N-11; dec F-6a, FU-1 | s86(3) placement cover was checked at request only, so two pending placements could jointly overdraw the cashbook | Yes | HIGH (assigned): trust banking account in debit | FIXED (PR #1799; `$B/investment/TrustInvestmentService.java:496-505` `onPlacementApproving` locks the funding account and re-checks the cashbook; tests `TrustInvestmentServiceIntegrationTest.java:665` and `:725`, `$EV/648A/red-backend-review-f3.txt`, `red-backend-review2-concurrent.txt`). **FU-1 is already delivered by this fix**; see the summary note. |
| 648A F-6 / F-6b | `$EV/648A/found.md` F-6; `$EV/648E/found.md` C-9; `$EV/649C/found.md` N-1; dec F-6b, FU-9; `$EV/legal/F-6-opinion.md` | A client payment, refund or fee transfer after an s86(3) placement is checked against the client ledger only, so the banking account's cashbook can go into debit (sequential, no race). Nothing surfaces it. | Yes | HIGH (src: Reviewer A) | **CLOSED → FU-9 #1825 (0c01b72b5)**. 2026-11-20 status: OPEN → FU-9 (verified: the behaviour is pinned by `TrustInvestmentServiceIntegrationTest.java:843` `clientPaymentAfterASection86_3Placement_isCheckedAgainstTheClientLedgerOnly_ruling648A_F6`; no `CASHBOOK_IN_DEBIT` in `backend/src/main`) |
| 648A F-4 | `$EV/648A/found.md` | An investment account (or its funding account) could be closed while it held investments; the withdrawal then posted on a closed account | Yes | MEDIUM (assigned) | FIXED (PR #1799; `$B/TrustAccountService.java:359`, `:372`; `$EV/648A/red-backend-review-f4.txt`) |
| 648A F-5 | `$EV/648A/found.md` | The stacked "Add Investment Account" dialog put "Create Account" outside the viewport | No | LOW (assigned) | FIXED (PR #1799; `CreateTrustAccountDialog.tsx:143` `max-h-[90vh] overflow-y-auto`) |
| 648A RC1-a | `$EV/648A/found.md` "Review cycle 1" | Recorded s86(4) interest was a memo counter, not on the books, so a withdrawal returned the ledger balance without it | Yes | MEDIUM (assigned) | FIXED (PR #1801; posted `INV_INTEREST` / `FUND_INV_SHARE` entries, V24) |
| 648A RC1-b | `$EV/648A/found.md` "Review cycle 1"; `$EV/648E/found.md` C-8; `$EV/648B/found.md` R-1 | `investmentBalance` had no as-at bound, so a withdrawal dated before an interest entry would return it early | Yes | MEDIUM (assigned) | FIXED (PR #1801; `TrustInvestmentService.java:617` refusal. The firm card's `investedBalance` stays undated by design, `$EV/648F/found.md`) |
| 648A W-1 | `$EV/648A/found.md`; `$EV/648A/gates/found-obs.md` W-1 | The investments register hid Status and Actions behind a horizontal scroll at 1280 / 1440 | Yes | MEDIUM (src) | FIXED (PR #1799; vitest "register fits the card"; re-gate `$EV/648A/gates-b6afe8c69/found-obs.md`) |
| 648A L-1 | `$EV/648A/found.md` | The two placement type labels differed in form | No | LOW (src) | FIXED (PR #1799; `TrustTransactionType.java:285`) |
| 648A A-1 | `$EV/648A/found.md`; `$EV/648D/found.md` "Carried" | Portal transaction list printed "Inv place" / "Inv return" | Yes | LOW (src) | FIXED (PR #1802; `portal/components/trust/transaction-list.tsx:136,184` render `typeLabel`) |
| 648A A-2 | `$EV/648A/found.md`; `$EV/648E/found.md` C-2 | Client Trust Balances printed no transfer or investment columns, so an s86(4) placement left the balance unexplained | Yes | LOW (src) | FIXED (PR #1806; the report became the Rule 54.15 list with `generalBalance` / `investedBalance` / `balance`, `$B/report/ClientTrustBalancesQuery.java:279-280`) |
| 648A A-3 / N-2 | `$EV/648A/found.md` A-3, N-2; `$EV/648E/found.md` C-3; `$EV/648D/found.md` F-1; `$EV/648F/found.md` | Firm surfaces (activity feed, audit registry, approval notification) say "placement in a section 86(3) investment **account**" | No | LOW (src) | OPEN — unassigned (verified: `$B/transaction/TrustTransactionTypeLabels.java:45`) |
| 648A RC4-H | `$EV/648A/found.md` "Review cycle 4" | `findDistinctTrustAccountIdsByCustomerId` counted the s86(4) receive leg, so both client statements could pick the investment account (R0, no lines) | Yes | HIGH (src) | FIXED (PR #1799; `TrustTransactionRepository.java:398` `ledger = CLIENT`; `$EV/648A/red-backend-review4-statement-account.txt`) |
| 648A RC4-M | `$EV/648A/found.md` M1–M3 | Approval notifications' label (M1), withdrawal date rules on the dialog (M2), amount rule moved to `TrustAmounts` (M3) | Yes | MEDIUM (src) | FIXED (PR #1799; `$B/transaction/TrustAmounts.java`) |
| 648A HO-1 | `$EV/648A/found.md` "Hand-off" | `TrustAccountService` ↔ `TrustInvestmentRepository` mutual package dependency | No | LOW (assigned) | OPEN — unassigned |
| 648A HO-2 | `$EV/648A/found.md` "Hand-off" | `LedgerTotals` folded placements into transfers as an interim | Yes | LOW (assigned) | FIXED (PR #1800; `LedgerTotals.java:28,68` `investmentPlacements`) |
| 648A N-1 | `$EV/648A/found.md`; `$EV/648A/gates-b6afe8c69/found-obs.md` N-1; `$EV/648E/found.md` C-4 | The Client Trust Statement (a client document) printed "Inv place" / "Inv return" | Yes | MEDIUM (src) | FIXED (PR #1803; `$B/ledger/ClientTrustStatementContext.java:102` registry label) |
| 648A O-1 | `$EV/648A/found.md` O-1; `$EV/648E/found.md` C-1 | After an s86(4) placement the client ledger page and both client documents showed the general balance only | Yes | MEDIUM (assigned) | FIXED (PR #1800 firm ledger; PR #1803 both documents' invested block). The residual SoA summary wording is 648F O-1. |
| 648A P-4 | `$EV/648A/found.md`; `$EV/648E/found.md` C-5 | Trust notification bodies print unformatted amounts ("R7500") | No | LOW (assigned) | OPEN — unassigned (`$B/event/TrustNotificationHandler.java:115,133`) |
| 648B F-1 | `$EV/648B/found.md` | An s86(4) interest entry cannot be reversed from the UI; the endpoint exists | Yes | MEDIUM (src) | **CLOSED → 648B F-1 #1838 (c0df2cc12)**. 2026-11-20 status: OPEN — unassigned (verified: no frontend caller of `/api/trust-investment-interest/{id}/reverse`; the controller is `$B/investment/TrustInvestmentController.java:79`) |
| 648B F-2 | `$EV/648B/found.md` | No per-entry interest view | Yes | LOW (src) | OPEN — unassigned |
| 648B F-3 | `$EV/648B/found.md` | Interest dialog line picker makes one request per open statement | No | LOW (src) | OPEN — unassigned |
| 648B F-4 | `$EV/648B/found.md` | The wizard's INVESTMENT_INTEREST path offers no typed Fund share | Yes | LOW (src) | OPEN — unassigned |
| 648B F-5 | `$EV/648B/found.md` | Reversing interest whose net row is matched is refused ("unmatch first"); not covered by a ruling | Yes | LOW (src: INFO) | OPEN — unassigned (confirm or overrule) |
| 648B F-6 | `$EV/648B/found.md` | Unmatching a GROSS interest line after its sweep leaves the Fund ledger in debit | Yes | LOW (src) | DECLINED (ADR-381 / founder decision 9: a Fund ledger in debit is allowed and flagged) |
| 648B F-7/8/9/13 | `$EV/648B/found.md` | Maintainability: `TrustInvestmentService` size; investment↔reconciliation package cycle; duplicated reversal audit block and event publish | No | LOW (src: INFO) | OPEN — unassigned |
| 648B F-11 | `$EV/648B/found.md` | No trust write carries an idempotency key; a resubmit after a lost response posts twice | Yes | MEDIUM (src) | **CLOSED → 648B F-11 #1841 (56bdc641e)** (founder ruling 2026-11-22: option A, an idempotency key on every trust write). 2026-11-20 status: OPEN — unassigned (cross-cutting; product / architecture) |
| 648B F-12 | `$EV/648B/found.md`; dec F-12 | V24 drops `interest_earned` without carrying totals over | Yes | LOW (src) | DECLINED (ruling F-12) |
| 648B F-14 | `$EV/648B/found.md` | Interest dialog controls stay editable while submitting | No | LOW (assigned) | OPEN — unassigned |
| 648B F-15 | `$EV/648B/found.md` | Reset-on-open vitest cannot catch a reset keyed on the line list | No | LOW (assigned) | OPEN — unassigned |
| 648B F-16 | `$EV/648B/found.md` | Wizard investment dropdown truncates the deposit number | No | LOW (src) | OPEN — unassigned |
| F-17 | `$EV/648B/found.md` F-17; `$EV/648D/found.md`; dec F-17, FU-8; dec 652D D-4 | Backend CI Surefire fork OOMs at `-Xmx3g` on green code | No | LOW (assigned): CI infrastructure | **CLOSED → FU-8 #1830 (390c2dd0b)**. 2026-11-20 status: OPEN → FU-8 |
| 648C F-1 | `$EV/648C/found.md` | Spec defect: the §16.7.7 basis CHECK accepted an INVESTMENT row with a NULL basis (three-valued logic); same risk in 648A's CHECK | Yes | MEDIUM (assigned) | FIXED (PR #1798, V22; the 648A half in V23, `V23__phase92_investment_accounts.sql:18-19`, PR #1799) |
| 648C F-2 | `$EV/648C/found.md` | An investment account's basis could change while it held investments | Yes | MEDIUM (assigned) | FIXED (PR #1799; `$B/TrustAccountService.java:274-282`) |
| 648C F-3 | `$EV/648C/found.md`; `$EV/648C/gates/found-obs.md` P-1; `$EV/648E/found.md` C-7 | "Set as primary" defaults ticked for every account type; pickers do not check the type | Yes | LOW (assigned): not observed to pick a wrong account (the preset dialog unticks it) | OPEN — unassigned (`CreateTrustAccountDialog.tsx:71`) |
| 648C F-4 | `$EV/648C/found.md`; `$EV/648A/gates/found-obs.md` P-2 | Raw `ACTIVE` / `CLOSED` status badge on Settings › Trust accounts | No | LOW (assigned) | OPEN — unassigned (`settings/fees/_tabs/trust-accounting.tsx:88-89`) |
| 648D F-2 | `$EV/648D/found.md` | Architecture §16.7.8 says `recomputeMatter` deletes stale portal rows; it only upserts | No | LOW (assigned): doc claim (data half is 646A obs-3) | OPEN — unassigned |
| 648D F-3 | `$EV/648D/found.md` (CodeRabbit CR-6) | `backfillForTenant` attributes every row of a matter to the first row's client (no production caller) | Yes | LOW (assigned) | OPEN — unassigned |
| 648D F-4 | `$EV/648D/found.md` (CodeRabbit CR-1) | `recomputeMatter` writes portal rows and the balance in separate autocommit statements: a failure between them leaves the portal's running balance disagreeing with its stored balance until the next replay | Yes | MEDIUM (assigned): a partial-failure inconsistency in the client-visible read model; the ledger is right and the next replay heals it | **CLOSED → 648D #1836 (6d2bc3731)**. 2026-11-20 status: OPEN — unassigned |
| 648D F-5 | `$EV/648D/found.md` (CodeRabbit CR-2) | Two concurrent replays of one matter can write an older portal balance last | Yes | MEDIUM (assigned): concurrency race; the portal shows a stale balance until the next replay | **CLOSED → 648D #1836 (6d2bc3731)**. 2026-11-20 status: OPEN — unassigned |
| 648D F-6 | `$EV/648D/found.md` (CodeRabbit CR-4) | Existing matters with a pre-V7 s86(4) investment read invested 0 on the portal until their next trust event | Yes | LOW (assigned) | DECLINED (R12 / founder decision 5) |
| 648D F-7 | `$EV/648D/found.md` | Placement API with `interestRate: 7` returns 500, not 400 (UI converts the percentage) | Yes | LOW (assigned) | OPEN — unassigned |
| 648E F-1 | `$EV/648E/found.md` | `TrustBalanceCard` matter mode: the client line shows the general balance under a tile that shows general + invested | Yes | LOW (src) | OPEN — unassigned |
| 648E F-2 | `$EV/648E/found.md` | Client-ledgers list shows no invested column | Yes | LOW (src) | OPEN — unassigned |
| 648E F-3 | `$EV/648E/found.md` | `toLocaleString("en-ZA")` output depends on the ICU build | No | LOW (src: INFO) | OPEN — unassigned |
| 648E F-4 | `$EV/648E/found.md` | `TrustBalanceCard` client mode: a fully invested client shows "No Funds" / R0.00 above "Total held in trust R30 000" | Yes | LOW (src). Under the rubric this reads as MEDIUM (misleading figure); the source severity is kept. | OPEN — unassigned (verified: `TrustBalanceCard.tsx:119-123,149` still read `ledger.balance`) |
| 648E C-6 | `$EV/648E/found.md`; `$EV/648C/gates/found-obs.md` P-3 | Approval Settings lists investment accounts, whose setting governs nothing | No | LOW (assigned) | OPEN — unassigned (Phase 91 settings shell) |
| 648E R-1 | `$EV/648E/found.md` | `backend/CLAUDE.md` does not say whether REPEATABLE_READ on composed reads is the pattern | No | LOW (assigned) | OPEN — unassigned |
| 648E R-2 | `$EV/648E/found.md`; `$EV/649C/found.md` H-4 | Other multi-statement trust reads still compose separate READ COMMITTED snapshots | Yes | LOW (assigned): assessed in 649C, no wrong figure observed | OPEN — unassigned |
| 648F R-1 | `$EV/648F/found.md`; `$EV/650A/found.md` F-6; dec R-1, R-1 note, FU-3; PR #1809 D-4; `$EV/652D/found.md` F-652D-CR1, dec 652D D-3 | Client documents and the Rule 54.15 / client-ledger-statement reports read general and invested balances (and, since 652D, the account choice) in separate statements inside a writing transaction; a placement approved between the reads can be counted twice on a generated document | Yes | MEDIUM (assigned): race on a sub-second window; the ledger is right and a regenerated document is right, but a wrong stored document is possible | **CLOSED → FU-3 #1828 (a7570cf2e)** (all five sites, incl. the 650A report read and the 652D account choice: dec FU-3 D-1). 2026-11-20 status: OPEN → FU-3 (FU-3's text names the statement builders' balance reads; the 650A report read and the 652D account choice are the same class and are not named in it) |
| 648F R-2 | `$EV/648F/found.md`; dec R-2, FU-2; `$EV/650C/found.md` O-OBS-1 | Every trust report (incl. `client-ledger-statement`, Section 35) needs only `FINANCIAL_VISIBILITY`, not `VIEW_TRUST`: a custom role without trust access can read client trust rows and balances | Yes | HIGH (assigned): an access-control hole (intra-tenant; needs a custom role) | **CLOSED → FU-2 #1823 (c3646bccc)**. 2026-11-20 status: OPEN → FU-2 (verified: `backend/…/reporting/ReportingController.java:32,38,45,53,62,73` `@RequiresCapability("FINANCIAL_VISIBILITY")` only) |
| 648F O-1 | `$EV/648F/found.md`; dec O-1, FU-7 | The Statement of Account's "Trust balance held" is the general balance while the Client Trust Statement's "Held in trust" is general + invested | Yes | LOW (src) | OPEN → FU-7 |
| 648F ENV | `$EV/648F/found.md` | SoA "Download PDF" fails on the e2e Docker stack (presigned URL from inside the container) | No | LOW (assigned) | DECLINED (known environment limit, recorded in 628F) |

## D. Stage 6 (649B, 649C)

| ID | Source(s) | Description | Trust? | Severity | Disposition |
|---|---|---|---|---|---|
| 649B F1 / F-652E-5 | `$EV/649B/found.md` F1 (CodeRabbit CR4); `$EV/646E/gates/found-obs.md` F1 (observed); `$X/found.md` F-652E-5 (observed in the exit month: pairs #7–#10 absent); dec FU-13 | Trust Receipts & Payments, and so Section 35 part (1), omits **both** halves of a reversed pair (the `REVERSED` original and its contra), even when both fall in the period: a deposit and a payment the bank did process are missing, and gross receipts and payments are understated (the net is unchanged) | Yes | HIGH (assigned): a wrong figure on Section 35 | **CLOSED → FU-13 #1824 (c4f66f9f6)** (FU-13 widened: all posted rows by their own date, incl. REVERSED originals and contras; the scope gap is closed). 2026-11-20 status: OPEN → FU-13, **with a scope gap**: FU-13's stated fix (`contra.transaction_date <= :toDate`) covers the reversed-after-period case (F-650C-2) and would still drop a same-period pair. Verified: `$B/transaction/TrustTransactionRepository.java:340-352` `findForReceiptsPayments` still filters `status IN ('RECORDED','APPROVED') AND transactionType <> 'REVERSAL'`. |
| 649B F4 | `$EV/649B/found.md` F4 (CodeRabbit CR3) | Outstanding deposits and payments ignored both halves of a reversed pair, so an unmatched contra was not outstanding | Yes | HIGH (assigned): wrong reconciliation figures | FIXED (PR #1792; `$B/transaction/TrustLedgerQueryRepository.java:480-507` outstanding over `cashbook_effect`; a pair cancels only while both are off any line) |
| 649B F6 | `$EV/649B/found.md` F6; `$EV/652H/found.md` "Not touched" | Statement account resolution ignores a client whose only activity on an account was reversed | Yes | LOW (assigned): the reversed pair nets to zero on that account | OPEN — unassigned (`TrustTransactionRepository.java:395-397`) |
| 649B F7 | `$EV/649B/found.md` F7 | Client-ledger-statement and Receipts & Payments reports printed raw type codes | Yes | LOW (assigned) | FIXED (PR #1791; `ClientLedgerStatementQuery.java:96`, `TrustReceiptsPaymentsQuery.java:114` `firmLabel`) |
| 649B F8 | `$EV/649B/found.md` F8; `$EV/646E/gates/found-obs.md` | A `REVERSAL` row was drawn as an outflow whatever it reversed | Yes | LOW (assigned) | FIXED (PR #1791; `direction` on the DTO, `frontend/components/trust/trust-transactions-table.tsx:43`) |
| 649B S1 | `$EV/649B/found.md` S1, CR6 | The firm's free-text reversal reason reached the client portal, the Statement of Account and the Client Trust Statement | Yes | HIGH (assigned): firm-internal text on client surfaces | FIXED (PR #1789, two cycles; one rule `TrustTransactionTypeLabels.clientDescription`, used by `TrustLedgerPortalSyncService`, `StatementOfAccountContextBuilder`, `ClientTrustStatementContext.java:108`; red runs in `$EV/649B/red-run.txt`) |
| 649B S2 | `$EV/649B/found.md` S2; `$EV/649C/found.md` H-2/H-3; dec S2, FU-5 | A back-dated deposit reversal checks the client's cover as at today only, so the client ledger can be in debit as at dates between the reversal date and today; the integrity check does not scan those dates | Yes | MEDIUM (assigned): missing refusal (founder ruled the fix) | **CLOSED → FU-5 #1827 (308c2665d)**. 2026-11-20 status: OPEN → FU-5 |
| 649B R1 | `$EV/649B/found.md` R1 | Reverse button shown on a deposit whose contra awaits approval | Yes | LOW (assigned): the backend refuses | FIXED (PR #1791; `frontend/lib/types/trust.ts:130-135` `reversalPending`) |
| 649B R2 | `$EV/649B/found.md` R2 | Package cycle: `transaction.TrustTransactionService` imports `reconciliation.BankStatementLineRepository` | No | LOW (assigned) | OPEN — unassigned (still at `TrustTransactionService.java:27`) |
| 649B R3 | `$EV/649B/found.md` R3 | `reverseForUnmatch` had no precondition, so unmatching an ordinarily matched line could book a contra against a payment the bank did make | Yes | HIGH (assigned; latent until 647A) | FIXED (PR #1794; `TrustTransactionService.java:1341` refuses unless the line is `RECORDED`) |
| 649B CR1 | `$EV/649B/found.md` CR1 | `reverseForUnmatch` lacked the investment-placement refusal | Yes | MEDIUM (assigned) | FIXED (PR #1789; the guard moved to the registry in PR #1799: `TrustTransactionService.java:1359` `isFinalInvestmentRow`; test `TrustReversalEquationIntegrationTest.java:408` `reverseForUnmatch_refusesAnInvestmentsPlacement`) |
| 649B CR2 | `$EV/649B/found.md` CR2 | `assertApprovalRefused` did not assert the detail | No | LOW (assigned) | FIXED (PR #1789) |
| 649B W1 | `$EV/649B/found.md` W1; `$EV/646E/gates/found-obs.md` W1; dec W1, FU-4 | The firm `client-ledger-statement` report prints the firm's reversal reason, and the UI card ("Generate Statement — … for this client") invites handing it to the client | Yes | MEDIUM (assigned): firm-internal text can reach a client through a firm-handed document | **CLOSED → FU-4 #1832 (4e07df3b6)**. 2026-11-20 status: OPEN → FU-4 (verified: `ClientLedgerStatementQuery.java:97` `tx.getDescription()`) |
| 649C O-1 | `$EV/649C/found.md` | EQ-5's interest term has two sources (service: entries; harness: rows) | No | LOW (assigned) | OPEN — unassigned (note for harness users) |
| 649C O-2 | `$EV/649C/found.md` | "Over one year" flag and list differ across 29 February | Yes | LOW (assigned) | OPEN — unassigned |
| 649C O-3 | `$EV/649C/found.md` | Statements reconciled before 649C stay `IMPORTED` and raise `UNRESOLVED_STATEMENT_LINES` for good | Yes | LOW (assigned): legacy dev data only (decision 5, environments reset) | OPEN — unassigned |
| 649C B-H1 | `$EV/649C/found.md` | `books` ↔ `reconciliation` package dependency | No | LOW (assigned) | OPEN — unassigned |
| 649C B-M2 / 650A F-8 | `$EV/649C/found.md`; `$EV/650A/found.md` F-8 | `TrustIntegrityService` size; `IntegrityMessages` name | No | LOW (src: INFO) | OPEN — unassigned |
| 649C F-OBS-1 | `$EV/649C/found.md` | After a gate refusal the wizard refreshed the figures but not the lines | Yes | LOW (src) | FIXED (PR #1804; `$FT/reconciliation/new/page.tsx:533` re-reads the statement; OBS run 2 PASS) |
| 649C O-OBS-2 | `$EV/649C/found.md` | The refusal banner did not show the title | No | LOW (assigned) | FIXED (PR #1804; OBS run 2) |
| 649C O-OBS-3 | `$EV/649C/found.md` | "R 1 500,00" (frontend) vs "R1 500,00" (backend sentence) on one card | No | LOW (src: cosmetic) | OPEN — unassigned |
| 649C VT-1 | `$EV/649C/found.md` "Orchestrator notes" | Vitest timing flake in `invoice-generation-validation.test.tsx` | No | LOW (assigned) | OPEN — unassigned |
| 649C CR-RECON | `$EV/649C/found.md` "Orchestrator notes" | `createReconciliation` accepted a new draft on a statement already `RECONCILED` | Yes | LOW (assigned) | OPEN — unassigned (652C now answers 409 "already reconciled" for the same account and period end, dec 652C D-8/D-9; other period ends not checked) |

## E. Stage 7 (650A–650D)

| ID | Source(s) | Description | Trust? | Severity | Disposition |
|---|---|---|---|---|---|
| 650A F-1 | `$EV/650A/found.md` (CodeRabbit C1) | A posting stamped before `locked_at` but committed after the lock read the books was in neither the frozen Rule 54.15 list nor its after-lock schedule | Yes | HIGH (assigned): a posting missing from the locked list and its adjustments | FIXED (PR #1806; `$B/TrustAccountRepository.java:23-31` `findByIdForShare`; `TrustMonthlyListIntegrationTest.java:713,754` `lockRace_*`) |
| 650A F-2 | `$EV/650A/found.md` | `completedAt` uses `Instant.now()`, not the clock | No | LOW (src) | OPEN — unassigned |
| 650A F-3 | `$EV/650A/found.md` | Hibernate's JSON mapper has no JSR-310 support | No | LOW (src: INFO) | OPEN — unassigned |
| 650A F-4 / F-14 / O-OBS-3 | `$EV/650A/found.md` | Section 35 "Client Trust Balances" basis differed from v3, and a locked year end showed locked balances without the after-lock explanation | Yes | LOW (src: INFO) | FIXED (PR #1809, D-1/D-2; `$B/report/Section35DataPackQuery.java:255` after-lock note) |
| 650A F-5 | `$EV/650A/found.md` | Two concurrent completions to the same month end hit the unique index → 500 | Yes | LOW (src) | OPEN — unassigned (the source says "likely resolved" by the F-1 account lock and 652C's create-time 409; no concurrency test covers it; **not verified**) |
| 650A F-7 / F-650C-4 | `$EV/650A/found.md`; `$EV/650C/found.md`; PR #1809 D-3 | `liveList` / `trust-position` run a full integrity check per account | No | LOW (src: INFO) | OPEN — unassigned |
| 650A F-9 | `$EV/650A/found.md` | Sibling trust server pages swallow a re-login redirect | No | LOW (src) | OPEN — unassigned |
| 650A F-10 | `$EV/650A/found.md` | Placement A→B and withdrawal B→A deadlocked (40P01 → 500) | Yes | MEDIUM (assigned) | FIXED (PR #1806; `TrustInvestmentWithdrawReverseRaceIntegrationTest.lockOrder_placementAndWithdrawalOnTheSameAccounts…`) |
| 650A F-11 | `$EV/650A/found.md` | s86(4) placement vs a posting for the same client deadlocked | Yes | MEDIUM (assigned) | FIXED (PR #1806; `…lockOrder_placementAndAPostingForTheSameClient…`) |
| 650A O-OBS-1 | `$EV/650A/found.md` | Metrics `uri` tag cap reached on every stack | No | LOW (src: INFO) | OPEN — unassigned |
| 650A O-OBS-2 | `$EV/650A/found.md` | "destination stream closed early" logged on a fast navigation | No | LOW (src) | OPEN — unassigned |
| 650A F-12 | `$EV/650A/found.md`; dec 650A F-12, FU-11 | `UNIDENT_ALLOC_OUT` approval vs a concurrent s86(4) placement can deadlock (one request fails, rolled back) | Yes | LOW (src) | **CLOSED → FU-11 #1825 (0c01b72b5)** (closed by FU-9 R-1's pre-approval lock order). 2026-11-20 status: OPEN → FU-11 |
| 650A F-13 | `$EV/650A/found.md`; dec F-13, FU-10 | The after-lock boundary compares app-host clock times; on a multi-host deployment `BACKDATED_AFTER_LOCK` could miss a posting | Yes | LOW (assigned): deployments are single-host; the ledger stays right | OPEN → FU-10 |
| 650A F-15 | `$EV/650A/found.md` | OBS harness does not compare the creditor-kind cell | No | LOW (src) | OPEN — unassigned |
| 650B F-OBS-1 | `$EV/650B/found.md` | Memo table Source column broke words mid-word at 1280 px | No | LOW (src) | FIXED (PR #1808; `frontend/components/trust/InterestMemosTable.tsx:98` `min-w-40`) |
| 650B O-OBS-2 | `$EV/650B/found.md` | The server's memo refusals are unreachable from the UI (the client schema mirrors them) | No | LOW (assigned) | DECLINED (by design, 650B D-7) |
| 650B F-CI-1 | `$EV/650B/found.md` | Memo response timestamp more precise than the stored value (Linux) | No | LOW (assigned) | FIXED (PR #1808) |
| 650B O-CI-1 | `$EV/650B/found.md`; dec 650B O-CI-1 | Other trust entities return untruncated timestamps | No | LOW (assigned) | OPEN — unassigned |
| F-650C-1 | `$EV/650C/found.md`; dec FU-12 | `REPORT_GENERATED` / PDF `REPORT_EXPORTED` audit rows are never flushed (read-only transaction), so PDF exports of every report, including the Section 35 pack, leave no audit trail | Yes (cross-cutting; covers every trust-report export) | MEDIUM (src) | **CLOSED → FU-12 #1833 (1ddd9d819)**. 2026-11-20 status: OPEN → FU-12 (verified: `backend/…/audit/DatabaseAuditService.java:82` plain `@Transactional`; `ReportExportService.java:92,110-158` read-only) |
| F-650C-2 | `$EV/650C/found.md`; dec FU-13 | Receipts & payments drops an entry reversed after the period (Section 35 part (1) vs parts (5)/(6)) | Yes | LOW (src). Under the rubric this reads as HIGH (Section 35 figure); the source severity is kept. | **CLOSED → FU-13 #1824 (c4f66f9f6)**. 2026-11-20 status: OPEN → FU-13 |
| F-650C-3 | `$EV/650C/found.md` | Section 35 note still names the `customer_id` parameter | No | LOW (src) | OPEN — unassigned |
| F-650C-OBS-1 | `$EV/650C/found.md` | Wide trust-report PDFs (Rule 54.28 schedule, trust position) cut off columns, including c/f and Check | Yes | HIGH (src) | FIXED (PR #1809; `$B/report/TrustReportPackSeeder.java:889-893` `@page { size: A4 landscape; }`; `$EV/650C/gates/obs-r2/`) |
| F-650C-OBS-2 | `$EV/650C/found.md` | "−" (U+2212) printed as "#" in PDFs | Yes | MEDIUM (src) | FIXED (PR #1809; `$B/report/TrustReconciliationReportQuery.java:98,119` en dash) |
| F-650C-OBS-3a | `$EV/650C/found.md` | Firm-wide Rule 54.28 schedule silently omitted an account with postings before its opened date | Yes | MEDIUM (src) | FIXED (PR #1809; `$B/report/TrustInterestChargesScheduleQuery.java:163,183`) |
| F-650C-OBS-3b / FU-14 | `$EV/650C/found.md`; dec 650C OBS-3, FU-14 | Postings dated before a trust account's `opened_date` are accepted, with no refusal and no flag | Yes | LOW (assigned): no figure is wrong after OBS-3a; whether such a posting is an error is an unruled policy question (back-recording history on a newly opened account hits it) | OPEN → FU-14 |
| F-650C-OBS-4 | `$EV/650C/found.md` | Raw ISO dates in summary cards and Section 35 labels | No | LOW (src) | FIXED (PR #1809; `Section35DataPackQuery.java:607`) |
| F-650C-OBS-5 | `$EV/650C/found.md` | "After Lock Net" card printed "0" | No | LOW (src) | FIXED (PR #1809; `frontend/components/reports/report-results.tsx:51-52`) |
| F-650C-OBS-6 | `$EV/650C/found.md` | "Total Trust Balance" card is the clients-only total, beside a larger "Creditor Total" | Yes | LOW (src) | OPEN — unassigned |
| F-650C-OBS-7 | `$EV/650C/found.md` | Schedule Month and Check cells break mid-word on screen | No | LOW (src) | OPEN — unassigned |
| 650C O-OBS-1 | `$EV/650C/found.md` | A member without financial visibility opening a report URL gets the generic error page | No | LOW (src: INFO) | OPEN — unassigned (FU-2 family; not named in FU-2) |
| 650C O-OBS-2 | `$EV/650C/found.md` | Section 35 prints nothing (no "None") when there are no investments | Yes | LOW (src: INFO) | OPEN — unassigned |
| 650C HN | `$EV/650C/found.md` O-OBS-3, O-OBS-R2-1 | Harness false positives; two date styles | No | LOW (src: INFO) | DECLINED (harness / not a defect) |
| 650D O-OBS-1 | `$EV/650D/found.md`; `$EV/652B/found.md` F-652B-4; `$EV/648A/gates/found-obs.md` P-3 | Record Transfer, Record Fee Transfer and Place Investment ask for raw client / matter / fee-note UUIDs | Yes | LOW (assigned): poor but workable (the ids are in the page URLs); a judgement call, see the summary | OPEN — unassigned (Phase 91) |
| 650D O-OBS-2 | `$EV/650D/found.md` | `TrustBalanceCard` "Last transaction: 2026/10/28" date format | No | LOW (assigned) | OPEN — unassigned |

## F. Stage 8 (651A, 651C, 651D)

| ID | Source(s) | Description | Trust? | Severity | Disposition |
|---|---|---|---|---|---|
| F-651A-1 | `$EV/651A/found.md`; dec 651A D-5 | `TrustLedgerQueryRepository.clientMatterBalances` has no main caller | No | LOW (assigned) | OPEN — unassigned |
| F-651A-2 | `$EV/651A/found.md`; dec 651A D-9 | `memberId` on `/api/me/capabilities` has no reader | No | LOW (assigned) | OPEN — unassigned |
| F-651A-3 / §16.14.4 #1–2 | `$EV/651A/found.md`; architecture §16.14.4; `$X/audit-facts.md` §6 | `ReconciliationCompleted`, `InvestmentMaturing` and `Payment*` events are never published; their listeners are dead | No | LOW (assigned) | OPEN — unassigned (absence of a publisher verified by grep in `audit-facts.md` §6; dead listener vs missing publish undecided) |
| F-651A-4 / F-651C-3 | `$EV/651A/found.md`; `$EV/651C/found.md` | Stale comment: "the Day 60 interest run is calculated but never posted" | No | LOW (src) | OPEN — unassigned (still at `portal/e2e/tests/portal-client-90day/day-14-30.spec.ts:24-25`) |
| F-651A-5 | `$EV/651A/found.md` | Removal test uses `Class.forName` on hard-coded names | No | LOW (src: Medium optional, review B) | DECLINED (in-slice decision 651E D-6: kept as a cheap guard) |
| F-651C-1 | `$EV/651C/found.md`; dec 651E D-7 | Investments register sub-label "LPFF share Arrangement" refers to the retired rate table | Yes | LOW (src) | OPEN — unassigned |
| F-651C-2 | `$EV/651C/found.md`; dec 651C D-2, 651A D-1, 651E D-2 | Audit labels `interest_run` / `lpff_rate` kept | No | LOW (src: INFO) | DECLINED (kept so historical audit rows stay readable, ADR-383) |
| F-651D-1..4 | `$EV/651D/found.md`; dec 651D D-7 | `kazi-architecture/` pages: profile switch narrative, portal nav gate, "trust capabilities only on legal roles", an incomplete helper list | No | LOW (assigned) | OPEN — unassigned (a separate docs PR, 651D D-7) |
| 651B-SCN | dec 651B D-10..D-13 | Lifecycle scenario text outside the authorised tables still describes the removed model; Day 60 has no record-interest step; id reuse 60.1–60.3 | No | LOW (assigned) | OPEN — unassigned (listed for the founder; scenario edits need authorisation, root `CLAUDE.md` §6) |

## G. Stage 9 (652A–652H) and the exit month (652E)

| ID | Source(s) | Description | Trust? | Severity | Disposition |
|---|---|---|---|---|---|
| CF-2 | `$EV/652A/found.md` CF-2; `$EV/652B/found.md` F-652B-3; `$EV/647A/gates/found-obs.md` O-2 | Step-4 "Unmatched Trust Transactions" lists transactions dated after the period end | Yes | LOW (assigned): the server figures are as at the period end and the completion gate refuses an imbalance | OPEN — unassigned |
| CF-4 | `$EV/652A/found.md`; dec 652A D-1 | After a Complete refusal, a failed recalculation leaves the figures unmarked as stale | Yes | LOW (assigned): needs a refusal and a failed request; the refusal banner is shown | OPEN — unassigned |
| CF-6 | `$EV/652A/found.md`; dec 652A D-7 | The three handlers' `catch` blocks are not generation-guarded (an old banner after a reset) | No | LOW (src: banner only) | OPEN — unassigned |
| 652A O-1 | `$EV/652A/found.md` | The out-of-date line is a live region mounted with its text (may not be announced) | No | LOW (assigned) | OPEN — unassigned |
| 652A O-2 / F-652B-2 | `$EV/652A/found.md` O-2; `$EV/652B/found.md` F-652B-2 | After auto-match a line shows `UNMATCHED` while its row stores a transaction id (possibly a stored suggestion) | Yes | LOW if reproduced (assigned) | HYPOTHESIS — unverified |
| 652A O-3 | `$EV/652A/found.md` | Two failure messages joined with a single space | No | LOW (src: cosmetic) | OPEN — unassigned |
| F-652B-1 | `$EV/652B/found.md`; dec FU-16 (c) | The pane offers a transfer leg's contra, which the backend then refuses | Yes | LOW (assigned): refused, nothing lands | OPEN → FU-16 (FU-16 (c), still open after #1834) |
| F-652B-5 | `$EV/652B/found.md`; dec FU-16 (c) | The zero-bank-movement type list is hard-coded on the frontend | No | LOW (assigned) | OPEN → FU-16 (FU-16 (c), still open after #1834) |
| 652C-1 | `$X/audit-facts.md` §1; `$EV/652C/red-before.txt` | Orphaned reconciliation draft (a second create for the same account and period) | Yes | MEDIUM (assigned) | FIXED (PR #1817, V29) |
| F-652C-1 | `$EV/652C/found.md`; dec 652C D-5/D-6, FU-6; `$EV/647B/gates/found-obs.md` O-1 | An imported statement cannot be retired or replaced: a re-upload after auto-match strands the first statement's matches (not offered to the new statement, not visible to unmatch), and a statement refused at step 4 stays `IMPORTED` with no UI to discard it, counting in later period checks and `UNRESOLVED_STATEMENT_LINES` | Yes | MEDIUM (assigned): broken UI path for a supported task (correcting an upload) | **CLOSED → FU-6b #1831 (8b9acc684)**. 2026-11-20 status: OPEN → FU-6 (FU-6's "replace" half; the step-4 leftover is not named in FU-6) |
| F-652C-2 | `$EV/652C/found.md` | After the stale-statement 409, the stale tab keeps Complete enabled | Yes | LOW (src) | OPEN — unassigned |
| F-652C-3..9 | `$EV/652C/found.md` | Legacy null-statement draft gets the 409; missing-body test; unexercised lock-side guard; Javadoc / `errorTitle` / reload affordance; evidence-script paths; ProblemDetail assertions; DTO placement | No | LOW (src) | OPEN — unassigned |
| 652D-1 | `$X/audit-facts.md` §1 | The two statement builders printed a closed primary account | Yes | MEDIUM (assigned) | FIXED (PR #1818; `$EV/652D/red-before-surefire.txt`) |
| 652D (i) | `$EV/652D/found.md` (i), (ii) residual | A client with money on two open GENERAL accounts sees one account on the SoA and the CTS; the other account's money is omitted with no note | Yes | LOW (assigned): API-only today; every posting and ledger page binds to the primary account (dec 652E D-1), so the UI cannot put one client's money on two GENERAL accounts. Under the rubric the effect would be HIGH. | OPEN — unassigned |
| 652D (iii) | `$EV/652D/found.md` | Eight trust pages default to a CLOSED primary account | Yes | LOW (assigned): an account can be closed only through the API (652D (iv)) | OPEN — unassigned |
| 652D (iv) | `$EV/652D/found.md` | No UI to close a trust account, and no way to move the primary flag | No | LOW (assigned): feature gap | OPEN — unassigned |
| 652D (v) / §16.14.4 #5 | `$EV/652D/found.md`; architecture §16.14.4; `$X/audit-facts.md` §6 | Dashboard looks for a primary `GENERAL` account; the demo seeder's main account is `SECTION_86` | Yes | LOW (assigned): `DashboardService.resolveTrustAccount` falls back to the first account | OPEN — unassigned |
| F-652D-O1 | `$EV/652D/found.md` | The trust-accounts settings list hides CLOSED accounts | No | LOW (assigned) | OPEN — unassigned |
| F-652D-O2 | `$EV/652D/found.md` | The CTS trust context is rebuilt three times per save / preview | No | LOW (src) | OPEN — unassigned |
| F-652D-O3 | `$EV/652D/found.md` | The CTS header "Trust Account: … Section 86(2) trust account" is firm-level and can name a different account from the "Account:" line | Yes | LOW (assigned): arises only with a replacement primary, which needs an API-only close. Under the rubric the effect would be MEDIUM. | OPEN — unassigned (product wording call) |
| 652G-1 | `$X/audit-facts.md` §1 | Zero-amount balance rows imported as statement lines | Yes | MEDIUM (assigned): spurious unresolved lines block completion | FIXED (PR #1819; `$EV/652G/repro-red.txt`) |
| F-652G-1 | `$EV/652G/found.md` | Stale comment about `period_end` (`TrustReconciliationService.java:826-827`) | No | LOW (assigned) | OPEN — unassigned |
| F-652G-2 | `$EV/652G/found.md` | Balance-marker vocabulary misses NBSP, trailing punctuation and abbreviations (such rows stay lines, as before) | Yes | LOW (assigned) | OPEN — unassigned (widen only on a real bank export, L4) |
| F-652G-3 | `$EV/652G/found.md` | `isBalanceRow` compiles a pattern per call | No | LOW (assigned) | OPEN — unassigned |
| 652H R-1 | `$EV/652H/found.md` R-1; dec 652H D-1 | After 652H, a row recorded from a statement line and then unrecorded could re-match its own former line | Yes | MEDIUM (assigned) | FIXED (PR #1820; `TrustTransactionType.recordedOnlyFromStatementLine()`; `autoMatch_doesNotOfferAnUnrecordedStatementRowToItsFormerLine`, `manualMatch_refusesAnUnrecordedStatementRow`) |
| F-652H-1 | `$EV/652H/found.md` | The same for `INV_INTEREST`, which also has a non-statement path, so it is not in the statement-only set | Yes | LOW (src) | OPEN — unassigned (the source folds it into the auto-match pair-rules follow-up; FU-16's text does not name it) |
| F-652H-2 | `$EV/652H/found.md`; dec FU-16 (c) | Frontend `STATEMENT_ONLY_TYPES` mirrors the backend set by hand | No | LOW (src) | OPEN → FU-16 (FU-16 (c), still open after #1834) |
| F-652H-3 | `$EV/652H/found.md`; dec 652H D-4..D-6 | A REVERSED original could auto-match an unrelated line (old reference, or by amount and date), hiding an unbooked receipt | Yes | HIGH (src: reviewer A) | FIXED (PR #1820, D-5/D-6; `ReversedOriginalMatchIntegrationTest` `autoMatch_reEnteredDepositUnderANewReference…`, `autoMatch_doesNotLetAReversedOriginalAbsorbAnUnrelatedLineByAmountAndDate`; `$EV/652H/red-reenter.txt`) |
| 652H D-6a | `$EV/652H/found.md` "Accepted by design" | A live row of a REVERSED original's amount pushes it off its own line, which stays unmatched for a human | Yes | LOW (assigned): fails safe | DECLINED (652H D-6) |
| 652H M-1 | `$EV/652H/found.md` M-1; dec FU-16 (a) | D-6's shadow sees only unlinked rows, so cross-statement a line can match a REVERSED original while its live re-entry is already linked | Yes | LOW (src) | **CLOSED → FU-16 #1834 (008084bd0)**. 2026-11-20 status: OPEN → FU-16 |
| F-652E-1 | `$X/found.md` | The firm's unadjusted and adjusted bank totals appear on no screen | Yes | LOW (src) | OPEN — unassigned |
| F-652E-2 | `$X/found.md` | The Rule 54.28 schedule shows no firm totals of gross interest and charges | Yes | LOW (src) | OPEN — unassigned |
| F-652E-3 | `$X/found.md` | Trust-position summary cards stay firm-wide when the report is filtered to one account | Yes | LOW (src). Under the rubric this reads as MEDIUM (misleading figure); the source severity is kept. | OPEN — unassigned |
| F-652E-4 | `$X/found.md` | Firm-wide Rule 54.15 list: a client on two accounts' lists shows Source "—" (the data is locked; only the label is missing) | Yes | LOW (src) | OPEN — unassigned |
| F-652E-6 | `$X/found.md` | Dashboard "Trust balance trend" chart rendered as an empty box in the evidence screenshot | No | LOW (src) | HYPOTHESIS — unverified |
| F-652E-7 | `$X/found.md` | Transient duplicate `#param-dateFrom` input on a report page load (seen once) | No | LOW (src) | HYPOTHESIS — unverified (not reproduced in 12 probe loads) |
| F-652E-8 | `$X/found.md` | Vitest timing flake in `trust-balance-trend-chart.test.tsx` | No | LOW (src) | OPEN — unassigned |
| F-652F-1 | `$X/found.md` | Vitest timeout in `dark-mode-contrast-guard.test.ts` D-3 case under a concurrent Maven verify | No | LOW (src) | OPEN — unassigned |

## H. Decisions log and architecture §16.14.4 (not in a found.md)

| ID | Source(s) | Description | Trust? | Severity | Disposition |
|---|---|---|---|---|---|
| FU-15 | dec 650C PR-size, FU-15 | `pre-pr-merge-gate.sh:180` fails closed above 300 files (`gh pr diff` HTTP 406) | No | LOW (assigned) | OPEN → FU-15 |
| §16.14.4 #3 | architecture §16.14.4; `$X/audit-facts.md` §6 | Nothing sets an investment `MATURED` | Yes | LOW (assigned) | DECLINED (§16.14.3: setting `MATURED` is out of scope) |
| §16.14.4 #4 | same | `TRUST_RECONCILIATION_OVERDUE`, `TRUST_APPROVAL_AGING` are catalogued, never created | No | LOW (assigned) | OPEN — unassigned |
| §16.14.4 #6 | same | `matterBalance` for the closure gate is not account-scoped | Yes | LOW (assigned) | DECLINED (§16.14.4: kept by design) |
| §16.14.4 #7 | same | Audit label registry keys are dotted while emitted types are underscored | No | LOW (assigned) | OPEN — unassigned (`AuditEventTypeRegistry.java:91,96,101`) |
| §16.14.4 #8 | same | The transactions list endpoint takes a single `status` | No | LOW (assigned) | DECLINED (R9: frontend merges the fetches, PR #1816 / #1820) |
| §16.14.4 #9 | same | Postings on a CLOSED trust account are refused, including a late statement line | Yes | LOW (assigned): an account can be closed only through the API today (652D (iv)); the policy awaits a ruling | OPEN — unassigned |

## N. Nested `gates*/found-obs.md` items never carried into a top-level found.md

| ID | Source(s) | Description | Trust? | Severity | Disposition |
|---|---|---|---|---|---|
| 646D-G O-1 | `$EV/646D/gates/found-obs.md` | A future fee-transfer date is refused by the browser's own bubble, not the app's sentence | No | LOW (src) | OPEN — unassigned |
| 647A-G O-3 | `$EV/647A/gates/found-obs.md` | A recorded bank charge's badge does not show whether it is recoverable | Yes | LOW (src) | OPEN — unassigned |
| 647B-G N-2 | `$EV/647B/gates/found-obs.md` N-2; `$EV/647C/gates-r2/found-obs.md` | Lifecycle intermittent: the matter Time tab stays stale after a time entry (not trust) | No | LOW (assigned) | HYPOTHESIS — unverified (did not reproduce; fits the next@16.2.6 lost-ping note) |
| 647B-G O-2 | `$EV/647B/gates/found-obs.md` | A click on another line straight after a Record can be lost | No | LOW (assigned) | HYPOTHESIS — unverified |
| 647C-G O-2 | `$EV/647C/gates/found-obs.md` | Fund ledger Amount is unsigned; direction by colour and an `aria-hidden` arrow only (a11y) | Yes | LOW (src) | OPEN — unassigned |
| 647D-G N-1 | `$EV/647D/gates/found-obs.md` | The firm client-ledger page has no Description column | Yes | LOW (assigned) | OPEN — unassigned |
| 648A-G P-1 | `$EV/648A/gates/found-obs.md` | Transactions status column shows enum-cased words | No | LOW (src) | OPEN — unassigned |
| 648A-G E-1 | `$EV/648A/gates/found-obs.md` | A console 404 on each client-ledger page visit | No | LOW (assigned) | OPEN — unassigned |
| 648C-G W-1 | `$EV/648C/gates/found-obs.md` | Settings row of the primary account wraps at 1280 px | No | LOW (src: cosmetic) | OPEN — unassigned |
