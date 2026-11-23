# §1.3 — `reverseInterest` vs auto-match (CF-3b class): NOT REPRODUCED, closed with a guard

Source: `tasks/trust-accounting-open-issues-2026-11-23.md` §1.3; `qa_cycle/evidence/phase92-followups/CF-3/notes.md` "Trade-offs / residual risk" (first bullet).

## The hypothesis

`TrustInvestmentService.reverseInterest` locks an entry's rows in `entryRowIds` order — the net row, then the Fund-share row — which is not id order. Auto-match (CF-3, #1826) locks the rows it plans in `UUID.compareTo` order (`TrustReconciliationService.java:812`, `planned.sort`). If both could hold one of two shared rows and wait for the other, Postgres would abort one with 40P01.

## Why it cannot happen: a reversible entry has ONE trust row

The cycle needs `reverseInterest` to lock two trust rows. It never does. The chain (line numbers at this PR's head):

1. `reverseInterest` refuses any entry recorded from a statement line **before it locks a row**: `TrustInvestmentService.java:868` (`entry.getBankStatementLineId() != null` → 400 "Unmatch the line to reverse it"); the row loop starts at `:889`.
2. An entry's `bank_statement_line_id` is set only at construction, from the recording line (`record(...)`, `line == null ? null : line.getId()`); `TrustInvestmentInterest` has no setter for it. So an entry that passes step 1 was recorded from a **bank advice** (`line == null`).
3. A bank advice cannot be `GROSS`: `TrustInvestmentService.java:1072` refuses `presentation == GROSS && line == null` ("A gross interest credit is recorded from the investment account's statement line…").
4. Only a `GROSS` entry gets a Fund-share row (`:1168`), and the database pins it: `V24__phase92_investment_interest.sql:41` `chk_invest_interest_fund_txn` — `(presentation = 'GROSS') = (fund_share_transaction_id IS NOT NULL)`.
5. So for every entry that reaches the row loop, `entryRowIds` (`:1291`) returns exactly the net row.

With one shared trust row, `reverseInterest` and auto-match share only that row. Auto-match takes no investment row, interest entry, client anchor or account row (CF-3 notes, "No client anchor or account row is locked"); `reverseInterest` takes no statement line or statement. A wait-for cycle needs two resources each held by one side and wanted by the other — there is only one. Two-row entries (GROSS, from a line) are reversed only by **unmatch**, whose rows are linked to the line and therefore never in the auto-match pool (`findPostedInAutoMatchWindow` is split into linked / unlinked and only unlinked rows are candidates, `TrustReconciliationService.java:758` ff.).

The rows *can* both be on the investment account and both unlinked candidates in principle (no type filter in `findPostedInAutoMatchWindow`) — the gap is closed by steps 1–5, not by the pool.

## Tests (guard) — `ReverseInterestAutoMatchLockOrderIntegrationTest`

`backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/investment/ReverseInterestAutoMatchLockOrderIntegrationTest.java`. House gate pattern from `AutoMatchLockOrderIntegrationTest` (a gate connection outside the pool holds one row `FOR NO KEY UPDATE`; blocking observed via `pg_stat_activity` / recursive `pg_blocking_pids`; no sleeps). One tenant, fresh investment per case, `assertFirmBalanced` at the end.

| Test | What it pins |
|---|---|
| `aReversibleInterestEntryHasExactlyOneRow` | A bank-advice entry is NET with no Fund-share row; GROSS from a bank advice is refused; a GROSS line entry (two rows) is refused by `reverseInterest` before any row is touched. The hypothesis' precondition. |
| `autoMatchHoldingTheLine_thenReverseInterestOfItsRow_noDeadlock` | Gate on the net row. Auto-match (holding the statement's line) blocks on it; the reversal (holding the investment and the entry) blocks behind (asserted). Released: auto-match links the row; the reversal re-reads it under its lock and is refused 400 "This interest is matched to a bank statement line…". No 40P01. |
| `reverseInterestHoldingTheRow_thenAutoMatchOfIt_noDeadlock` | Gate on the investment account (the posting lock the reversal takes after its row). The reversal holds the row and blocks; auto-match blocks behind it on the row (asserted). Released: the contra posts; auto-match re-reads the row, sees it REVERSED and skips it (line stays UNMATCHED). No 40P01. |

- `red-run.txt`: 3/3 PASS on unmodified production code — not reproduced, as the analysis predicts.
- Green: 3/3 in the targeted run and in Tier 2 (see `../backdated-cover-all/tier2-run.txt`).

## Production change

None to the lock order (no fix is warranted for an impossible cycle). The `reverseInterest` Javadoc now states the one-row invariant and why it cannot cross auto-match's id order. (The method signature did change, for D-2: it takes the reason.)

## Residual risk

- If a future change lets a bank-advice entry carry a Fund-share row (e.g. allowing GROSS without a line), or lets an entry's line be cleared without reversing it, `reverseInterest` would lock two rows in a non-id order again. `aReversibleInterestEntryHasExactlyOneRow` fails first in either case; the fix would then be to lock `entryRowIds` sorted by `UUID.compareTo`, re-reading each under its lock.
- The general CF-3 residual stands: there is no global row-to-row order across trust paths; any other multi-row locker in a non-id order would need the same analysis.
