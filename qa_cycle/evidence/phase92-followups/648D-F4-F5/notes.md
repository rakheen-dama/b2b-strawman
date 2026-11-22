# 648D F-4 + F-5 — portal trust replay atomic and in order

Register rows: `qa_cycle/evidence/phase92-exit/defect-register.md` 648D F-4 / F-5 (sources
`qa_cycle/evidence/phase92/648D/found.md` F-4, F-5). Base: `main` @ `56fd0d3a9`.

## Root cause

`TrustLedgerPortalSyncService.recomputeMatter` (`:252` on the base) read the matter's firm history
and then wrote each visible portal row (`upsertTransaction`) and each client's balance snapshot
(`upsertBalance`) through `portalJdbcClient` with no transaction — every write was its own
autocommit statement on the portal datasource.

- **F-4:** a failure after some writes left the earlier ones committed. Reproduced: a failing
  balance write leaves the new row committed with running balance 150 while the stored balance
  stays 100.
- **F-5:** nothing ordered two replays of one matter. Reproduced: replay A reads the history
  (deposit 100) and parks before writing; replay B reads both deposits, writes 150 and commits; A
  then writes 100. The portal shows 100 against a 150 ledger until the next replay.

## Design

One portal transaction per matter replay (`portalTxTemplate.executeWithoutResult`, the
`DataSourceTransactionManager` on `portalDataSource`). Its first statement takes a per-matter
advisory transaction lock; then the firm history is read; then the rows and balances are written;
then commit (which releases the lock).

- **Lock key:** `pg_advisory_xact_lock(hashtextextended('portal_trust_matter:' || <tenant schema>
  || ':' || <matter id>, 0))`. This is new: `PortalTrustReadModelRepository.lockMatterReplay`.
  - The tenant schema comes from `RequestScopes.requireTenantId()` (bound by the listener's
    `runForTenant`). It is passed as a parameter because the portal connection's `current_schema()`
    is not the tenant's.
  - It is 64-bit, as in FU-6b's `BankStatementRepository.lockImportsOfAccount`.
  - There is no `ScopedValue.where` or `ThreadLocal`; `TenantScopeBindingTest` is green.
- **Order:** lock, then read, then write, then commit.
  - The history read (`findByProjectIdOrderByTransactionDateAsc`, on the tenant connection) runs
    after the lock is granted, at READ COMMITTED.
  - A replay that gets the lock after another committed therefore takes a later statement snapshot
    than the other's read. It sees every firm commit the other saw, plus its own trigger.
  - So the replay that commits last always read the newest history.
- **Why the lock and not a monotonic marker:** the replay rewrites every row's running balance as
  well as the balance. A marker on the balance alone would not order the row writes. It would also
  need a firm-side monotonic value (for example a history version), and no such value exists. The
  lock orders the whole replay with one statement and no schema change.
- **Why the history read moved inside:** the order requires the read to follow the lock. The read
  stays on the same tenant `EntityManager` and connection as before (the portal transaction is on a
  different transaction manager and binds only the portal `DataSource`). So it checks out no extra
  app-pool connection. This follows the same `portalTxTemplate`-in-`AFTER_COMMIT` pattern as
  `DeadlinePortalSyncService`.
- **Semantics unchanged:** the same rows and the same values; only the transaction boundary and
  the lock are added.
- **Architecture guard:** `PortalReadModelOrgScopingGuardTest` gets one justified `SCOPING_EXEMPT`
  entry for `lockMatterReplay`. It touches no table rows; the precedent is
  `PortalContactDirectoryRepository#lockOrg`.

## Deadlock reasoning

- All three listeners (`onTrustTransactionApproval`, `onTrustTransactionRecorded`,
  `onReconciliationCompleted`) are `@TransactionalEventListener(AFTER_COMMIT)`. By the time a
  replay runs, its firm transaction has committed and its firm row and advisory locks are released.
- While a replay holds the matter lock, its only firm-side statement is a plain `SELECT`. That
  takes only `AccessShareLock`, which waits only on `ACCESS EXCLUSIVE` (DDL), never on row locks or
  trust advisory locks. So the lock holder never waits for a firm-side lock.
- The holder already holds its portal connection, and its firm read reuses the connection already
  bound to the thread. It needs no further pool checkout, so waiters (each holding one portal and
  one app connection) cannot starve it.
- A replay holds at most one matter lock. The reconciliation fan-out (`syncForTrustAccount`) runs
  one transaction per matter, so each lock is released before the next is taken. There is no
  lock-order cycle between replays.
- Portal row locks taken inside the transaction belong to that matter only. Replays of one matter
  are serialised by the advisory lock before any row is touched.

## Tests

New file: `backend/src/test/java/.../customerbackend/service/TrustLedgerPortalReplayConsistencyIntegrationTest.java`.

- The test drives the real path: `TrustTransactionService.recordDeposit` publishes its event, and
  the `AFTER_COMMIT` listener replays the matter on the committing thread.
- It has one `@MockitoSpyBean` (`PortalTrustReadModelRepository`), used only in this class, so it
  adds one context key.

The two tests:

- `aBalanceWriteThatFailsLeavesNoRowOfTheSameReplayBehind` (F-4):
  - Deposit 100; then inject a failing `upsertBalance` and deposit 50.
  - Assert the failed replay left nothing: the portal rows are exactly the first deposit, the
    latest row's running balance equals the stored balance, and the balance is 100.
- `aReplayThatReadOlderHistoryNeverOverwritesANewerBalance` (F-5):
  - Replay A (deposit 100, thread A) parks at its first portal write, after its history read and
    before any portal write.
  - Thread B deposits 50. The test waits, by observing `pg_stat_activity` (`wait_event =
    'advisory'`) and never by sleeping, until B is queued on the lock or has finished. Then it
    releases A.
  - Assert the balance is 150, the latest row is 150, and B really waited on A's matter lock.
  - The gate parks before any write on purpose. A first attempt parked at the balance write, after
    the rows. With the transaction but no lock, A's row locks then serialised B, so the test timed
    out instead of showing the stale write. The final gate shows the race directly.

## Evidence (this directory)

| File | Code | Result |
|------|------|--------|
| `red-run.txt` | base `main` sources, final test | 2/2 FAIL. F-4: the failed replay's new row is committed (2 portal rows where only the first deposit's is expected; the stored balance was not rewritten). F-5: `expected 150.00 but was 100.00`, B did not wait. |
| `green-run.txt` | fix | 2/2 PASS |
| `mutation-no-transaction.txt` | fix minus the `portalTxTemplate` wrapper (lock kept; the xact lock then releases at once) | 2/2 FAIL (F-4 partial commit; F-5 `was 100.00`) |
| `mutation-no-lock.txt` | fix minus `lockMatterReplay` (transaction kept) | F-4 PASS, F-5 FAIL `was 100.00` |
| `tier2-part1.txt` | `architecture.**` + `customerbackend.**` + portal-trust classes elsewhere | 48 classes, 346 testcases, 0 failures/errors (the first attempt failed `PortalReadModelOrgScopingGuardTest` on the new lock statement; fixed with the justified exemption, then rerun) |
| `tier2-part2-trustaccounting.txt` | `verticals.legal.trustaccounting.**` (every trust service test that posts and so triggers a replay) | 77 classes, 856 testcases, 0 failures/errors |
| `tier2-part3-trust-consumers.txt` | 12 trust-posting classes outside those packages (demo seed, statements, dashboard, reporting, MCP, templates, FICA, audit guard) | 12 classes, 94 testcases, 0 failures/errors |

Counts are `<testcase>` elements in `target/surefire-reports`. The full `./mvnw verify` was not run
(the orchestrator owns it).

## Residual risk

- **Stale persistence context (pre-existing, unchanged).** The `AFTER_COMMIT` history read uses the
  committed transaction's still-open `EntityManager`. A row that this transaction itself loaded is
  returned with its loaded state, not re-read.
  - Only `status` and the once-set `ledger_effect` (set at posting, `TrustTransaction:324`) matter
    for the balance. The leftover case is narrow: this transaction loaded a row of the same matter
    while it was unposted, another transaction posted it concurrently, and this replay was the last
    to commit. Then the posted row would be missed until the next replay of the matter.
  - Closing it would need a fresh `EntityManager` (`REQUIRES_NEW`) for the read. That adds an
    app-pool checkout while the original connection is still held, which is pool-starvation risk.
    Not done; flagged as a candidate if it is ever observed.
- **Backfill vs replay.** `backfillForTenant` (no production caller) writes many matters in one
  portal transaction without the matter lock. Previously a replay held one portal row lock at a
  time; now it holds its matter's row locks until commit.
  - A concurrent backfill could deadlock with a replay: the backfill trims old rows after writing
    the window, while the replay writes old rows first. Postgres detects this and aborts one side
    (40P01). An aborted replay is logged and heals on the next replay; it is not a hang.
  - Fix if the backfill ever gets a caller: take each matter's replay lock in the backfill before
    writing it.
- **Serialisation cost.** Replays of one matter now queue. Each replay is a single-matter read and a
  handful of upserts, so the wait is short. Replays of different matters do not contend.
