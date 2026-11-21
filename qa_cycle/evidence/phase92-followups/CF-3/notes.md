# CF-3 (CF-3a + CF-3b): auto-match lock order — notes

Register rows CF-3a / CF-3b (`qa_cycle/evidence/phase92-exit/defect-register.md`); sources
`phase92/647A/found.md` #7, `phase92/648B/found.md` F-10, `phase92/652A/found.md` CF-3.

## Root cause

`TrustReconciliationService.autoMatchStatement` (base 409fdff7a, `:345-421`, `applyAutoMatch` `:1359`)
read the statement (`findById`), its `UNMATCHED` lines and the candidate pool **without locks**, then:

1. **CF-3a (lost / overwritten writes).** It wrote each matched line from that stale read and set
   `matched_count = alreadyMatched + autoMatched` from the stale statement. A record, manual match or
   exclude that committed in the meantime was overwritten: the line was flipped to `AUTO_MATCHED`
   over `RECORDED` (the recorded row still pointing at the line, two rows linked to one line), and a
   concurrent `incrementMatched` was lost.
2. **CF-3a, same root cause (found while reproducing, not in the register text): a row linked
   twice.** `applyAutoMatch` re-locked the row with `findByIdForUpdate`, but the pool had already put
   that row in the persistence context. A locking query hands back the managed instance without
   refreshing it, so the "already claimed?" check always read the stale `bank_statement_line_id =
   null`. A manual match (of another statement's line) or a second auto-match that linked the row
   while auto-match waited for its lock was overwritten: two lines matched to one row. The same stale
   copy also meant a skipped match was still counted (`autoMatched++` after the silent return).
3. **CF-3b (deadlock).** Auto-match locked the row (`findByIdForUpdate`) and only then UPDATEd the
   line (at flush). `manualMatch` locks line → row → statement. Opposite order, so Postgres 40P01. Two
   auto-matches of two statements of one account, locking two shared rows in opposite match orders,
   also deadlocked.

## Fix: lock order (method Javadoc on `autoMatchStatement`)

The order every line writer already uses, line → transaction → statement (`TrustPostingService` global
order, step 1). Each locked row is read fresh under its lock:

0. `findById(statement)`: a plain read, no lock. It only supplies the account and period, which are
   fixed at import. The statement is re-read under its lock in step 3.
1. **Lines:** every `UNMATCHED` line of the statement in one locking query
   (`BankStatementLineRepository.findUnmatchedByStatementIdForUpdate`, `ORDER BY transaction_date, id`,
   which is the existing matching order, so tie-breaking is unchanged). It is the first read of these
   lines in the transaction. Postgres re-checks the predicate on a row it waited for, and the code also
   re-checks `UNMATCHED`.
2. **Pool, then plan:** the candidate pool is read (plain) and every tier runs exactly as before. The
   0.60 / 0.40 suggestions are written to the (locked) lines at this point. Tier-1 and tier-2 matches
   are only *planned*.
3. **Rows:** the planned rows are locked **one at a time in id order** with
   `entityManager.refresh(row, PESSIMISTIC_WRITE)`, a locking re-read that replaces the stale pool
   copy. A row that is now linked, or whose status changed since the pool read, is skipped. Its line
   stays `UNMATCHED` and the match is not counted.
4. **Statement last:** `entityManager.refresh(statement, PESSIMISTIC_WRITE)`, then `matched_count =
   current + autoMatched` (an increment from the locked value, never from a stale read).

No client anchor or account row is taken (`linkBankLine` posts nothing).

### Why it is deadlock-free against each path

- **manualMatch** (L → T → S): if L is an `UNMATCHED` line of S, whichever side locks L first wins. The
  loser holds nothing of S's lines (auto-match locks lines before anything else) and simply waits. If
  L belongs to another statement S2, the only shared resource is T (auto-match never takes L or S2,
  and manual never takes S's lines or S), so no cycle.
- **unmatch of a matched line** (L → T → S): L is not `UNMATCHED`, so auto-match does not lock it. T is
  linked in committed state, so it is not in the pool. The only shared row is S, which both take last.
  No cycle.
- **record-from-line / unmatch of a recorded line** (L → [item / investment / entry] → S → rows →
  anchors → account): if L is in S and `UNMATCHED`, it is serialised on L as with manualMatch.
  Otherwise the only shared row is S. Auto-match holds no item, investment, anchor or account, and
  never wants the rows linked to L.
- **excludeLine** (L only): serialised on L.
- **completeReconciliation** (reconciliation → S → account): auto-match takes S last and no account
  row, and complete reads lines and rows without locks. No cycle.
- **Two auto-matches, same statement:** both lock the lines with the same ordered query. The second
  waits on the first line and then sees the first's result.
- **Two auto-matches, different statements, overlapping windows:** their lines are disjoint. Rows are
  locked in id order (a single global order), and each takes only its own statement last. No cycle.
- **Transaction-only paths** (reversal, approval: T → anchors → account): auto-match holds no anchor or
  account, so no cycle.

## Trade-offs / residual risk

- **Multi-row lockers in a non-id order** (e.g. `TrustInvestmentService.reverseInterest`: net row, then
  Fund-share row) can still form a cycle with an auto-match that plans both of the same rows. This is
  not new: the old code locked rows in match order, so the exposure was the same or larger. It is not
  removable here, because there is no global row-to-row order across trust paths (the global order
  says "in the order documented at each path"). I chose the conservative option and lock only the rows
  actually matched. I rejected locking the whole candidate pool up front, which would have removed the
  stale read without `refresh`, because it would lock transfers and unrelated rows and widen this
  exposure.
- **A skipped match does not fall through to another candidate.** That keeps the old "skip silently"
  semantics, so the line stays `UNMATCHED` for a human. Deciding every match before locking keeps the
  tier logic and its outcomes the same when nothing runs concurrently (the existing matching suites,
`TrustReconciliationServiceMatchingTest` and `ReversedOriginalMatchIntegrationTest`, are unchanged
and green).
- **`MatchResult.alreadyMatched`** is now the count read under the statement lock at the end of the run
  (the true count just before this run's matches are added). The old value was read at the start. The
  two are equal unless another writer changed the count concurrently.
- `TrustReconciliationService` now injects `EntityManager` (constructor injection, as `CalendarService`
  does) for the two locking refreshes. A later locking query on an already-loaded row would not
  refresh it, and there is no Spring Data equivalent.
- No migration. The unused derived finder
  `findByBankStatementIdAndMatchStatusOrderByTransactionDateAscIdAsc` was replaced by the locking
  query (auto-match was its only caller).

## Tests added

`backend/src/test/java/.../trustaccounting/reconciliation/AutoMatchLockOrderIntegrationTest.java`
(7 tests). Each test uses the house `gatedOnRow` pattern from `CashbookCoverAtApprovalIntegrationTest`:
a gate connection outside the pool holds one row `FOR NO KEY UPDATE`, and blocking is observed via
`pg_stat_activity` / `pg_blocking_pids`, with no sleeps. Since review round 1, the wait counts only
backends in the gate's own wait tree: those waiting on the gate, directly or transitively through a
waiter (a recursive `pg_blocking_pids` query). An unrelated waiter elsewhere in the cluster can no
longer release the gate early. `Gated.secondBlocked` records whether the second operation was seen
blocked.

| Test | Gate | Red on base (`red-run.txt`) |
|---|---|---|
| `recordFromLine_duringAutoMatchOfTheSameLine_isNeverOverwritten` | the deposit row | 2 rows linked to the line (line AUTO_MATCHED over RECORDED) |
| `manualMatchOfAnotherLine_duringAutoMatch_keepsBothCounts` | the auto-match row | `matched_count` 1, expected 2 (lost increment). With the fix, the guard is the line lock (step 1): the manual match's line is one of the locked UNMATCHED lines. |
| `manualMatchOnAnotherStatement_beforeAutoMatchTakesTheRow_isNotDoubleLinked` | the other statement | row re-linked to the auto-match line (double link) |
| `twoAutoMatchesWantingTheSameRow_linkItOnce` | the row | 2 lines matched to 1 row |
| `manualMatch_duringAutoMatchOfTheSameLineAndRow_noDeadlock` | the statement | `CannotAcquireLockException`: 40P01 deadlock on `trust_transactions` |
| `twoAutoMatchesTakingTwoRowsInOppositeOrders_noDeadlock` (round 0, gate on statement 1's first line) | the first line | `CannotAcquireLockException`: 40P01 deadlock on `trust_transactions` |
| `twoAutoMatchesTakingTwoRowsInOppositeOrders_lockThemInIdOrder_noDeadlock` (round 1, replaces the above) | row `lo` (`lo` < `hi` by `UUID.compareTo`; statement 1 matches `lo` then `hi`, statement 2 `hi` then `lo`) | both auto-matches block in step 2 behind the gate (asserted). Pins `planned.sort`: with the sort removed it deadlocks (40P01, `mutation-sort.txt`). |
| `unmatchOfAnAlreadyMatchedLine_duringAutoMatch_isNotOverwrittenByAStaleCount` (round 1, new) | the row auto-match plans for line Y | Line X was pre-matched manually, and `unmatch(X)` runs to completion without blocking (asserted). Pins the statement's locking refresh (step 3): counting from the stale copy gives `matched_count` 2, expected 1 (`mutation-refresh.txt`). |

The class ends with `assertFirmBalanced` (`@AfterAll`).

**Review round 1 (Opus review + CodeRabbit on #1826 @5cc7b28):** the round-0 crossed test gated on a
line. With the fix, auto-match #1 then blocked in step 1 and #2 ran alone, so the test stayed green
with `planned.sort` deleted. No test pinned the statement's locking refresh either. Both gaps are
closed above with test-only changes. Each new guard was proven by a mutation run on the production
code, which was restored with `git checkout` afterwards. Production code is unchanged from 5cc7b28.

## Evidence

- `red-run.txt`: 6/6 FAIL on unmodified production code (base 409fdff7a), each with the predicted
  mechanism.
- `green-run.txt` (round 1): `AutoMatchLockOrderIntegrationTest` 7/7 PASS on 3 consecutive runs,
  plus the whole reconciliation test package: 10 classes, 179 testcases, 0 failures / errors /
  skipped. (Round 0: 6/6 PASS, then 3 more green runs.)
- `mutation-sort.txt`: `planned.sort` removed → the crossed-rows test fails with 40P01 (`PessimisticLockException`, deadlock on `trust_transactions`).
- `mutation-refresh.txt`: the statement's locking refresh removed → the stale-count test fails with `matched_count` 2, expected 1.
- `tier2-run.txt` (round 0, at 5cc7b28): 24 classes, 297 testcases, 0 failures / errors / skipped. The set was the whole
  `reconciliation` test tree (including `parser`), every test class using the changed code
  (`TrustWireContractTest`, `TrustIntegrityServiceIntegrationTest`, `TrustMonthlyListIntegrationTest`,
  `TrustPositionAsAtIntegrationTest`, `TrustInterestMemoIntegrationTest`,
  `UnidentifiedDepositIntegrationTest`, `TrustInvestmentInterestIntegrationTest`,
  `TrustReportQueryIntegrationTest`), the trust lock-order neighbours
  (`TrustInvestmentWithdrawReverseRaceIntegrationTest`, `CashbookCoverAtApprovalIntegrationTest`), and
  the ArchUnit guards (`TrustPostingRulesTest`, `LayerDependencyRulesTest`,
  `MatterScopedPublisherGuardTest`, `TestConventionsTest`).
- Full `./mvnw verify`: run by the orchestrator at 5cc7b28 (green, 7 445 testcases; not re-run by the
  builder after the round-1 test-only changes).
