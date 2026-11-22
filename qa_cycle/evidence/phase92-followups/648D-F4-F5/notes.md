# 648D F-4 + F-5 — portal trust replay atomic and in order

Register rows: `qa_cycle/evidence/phase92-exit/defect-register.md` 648D F-4 / F-5 (sources
`qa_cycle/evidence/phase92/648D/found.md` F-4, F-5). Base: `main` @ `56fd0d3a9`; round 2 on the
coordinator's merge of `origin/main` @ `27c3fbded`.

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
- **F-5, read side (round 2, review finding):** the history read was an entity query
  (`findByProjectIdOrderByTransactionDateAsc`).
  - It ran `AFTER_COMMIT` on the committed transaction's still-bound `EntityManager` (connection
    handling `DELAYED_ACQUISITION_AND_HOLD`).
  - Any row already managed in that persistence context therefore came back as the cached,
    possibly stale instance.
  - Reproduced: a transaction loads an unposted payment, another transaction posts it, and the
    first transaction's replay still sees it as `AWAITING_APPROVAL` (effect 0). The payment is
    filtered out: portal 100 against a 70 ledger.

## Design

One portal transaction per matter replay (`portalTxTemplate.executeWithoutResult`, the
`DataSourceTransactionManager` on `portalDataSource`). Its first statement takes a per-matter
advisory transaction lock; then the firm history is read as a column projection; then the rows and
balances are written; then commit (which releases the lock).

- **Lock key:** `pg_advisory_xact_lock(hashtextextended('portal_trust_matter:' || <tenant schema>
  || ':' || <matter id>, 0))`. This is new: `PortalTrustReadModelRepository.lockMatterReplay`.
  - The tenant schema comes from `RequestScopes.requireTenantId()` (bound by the listener's
    `runForTenant`). It is passed as a parameter because the portal connection's `current_schema()`
    is not the tenant's.
  - It is 64-bit, as in FU-6b's `BankStatementRepository.lockImportsOfAccount`.
  - There is no `ScopedValue.where` or `ThreadLocal`; `TenantScopeBindingTest` is green.
- **Order:** lock, then read, then write, then commit.
  - The history read runs after the lock is granted, at READ COMMITTED.
  - A replay that gets the lock after another committed therefore takes a later statement snapshot
    than the other's read. It sees every firm commit the other saw, plus its own trigger.
  - So the replay that commits last always read the newest history.
- **History read as a projection (round 2):**
  - New query `TrustTransactionRepository.findHistoryRowsByProjectId`: a JPQL constructor
    expression into the new record `TrustTransactionHistoryRow`, with the same `WHERE` and
    `ORDER BY` as the old entity query.
  - The row's values come from the result-set columns and never pass through the persistence
    context. So the read reflects committed data even for rows the thread's transaction already
    manages, which makes the lock-then-read guarantee unconditional.
  - It runs on the same held connection: no `detach` or `clear`, no `REQUIRES_NEW`, no extra
    pool checkout.
  - The old entity query had no other caller and was removed.
- **Record fields:** exactly what the projection reads:
  - `id`, `transactionType`, `amount`, `customerId`, `projectId`, `reference`, `description`
  - `transactionDate`, `status`, `approvedAt`, `createdAt`, `ledger`, `ledgerEffect`

  The consumers are:
  - the status filter and portal scope;
  - `projectedLedger` / `isVisibleRow` (ledger, type);
  - `signedAmount` (ledger effect);
  - `deriveOccurredAt` / `recordedAt` (approved / created / transaction date);
  - `upsertRow` (id, type, amount, date, reference);
  - `portalDescription` (type, description, reference).

  No investment column is read by the projection. The invested balance is the sum of the
  `CLIENT_INVESTMENT` ledger effects.
- **The backfill shares the same helpers.** It still loads entities tenant-wide and maps each
  through `TrustTransactionHistoryRow.of(entity)`, so there is one set of projection helpers.
- **Values:** the same rows and the same row and balance values. For a row created in the
  replaying transaction itself, the timestamps now come from the stored columns rather than the
  in-memory instants. Postgres stores both at microsecond precision, so the written portal values
  are the stored firm values.
- **Why the lock and not a monotonic marker:** the replay rewrites every row's running balance as
  well as the balance. A marker on the balance alone would not order the row writes. It would also
  need a firm-side monotonic value (for example a history version), and no such value exists.
- **Why the history read moved inside:** the order requires the read to follow the lock. The read
  stays on the same tenant connection as before (the portal transaction is on a different
  transaction manager and binds only the portal `DataSource`). This is the same
  `portalTxTemplate`-in-`AFTER_COMMIT` pattern as `DeadlinePortalSyncService`.
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

- The tests drive the real path: an event published in a tenant transaction, and the
  `AFTER_COMMIT` listener replays the matter on the committing thread.
- There is one `@MockitoSpyBean` (`PortalTrustReadModelRepository`), used only in this class, so
  it adds one context key.

The three tests:

- `aBalanceWriteThatFailsLeavesNoRowOfTheSameReplayBehind` (F-4):
  - Deposit 100; then inject a failing `upsertBalance` and deposit 50.
  - Assert the failed replay left nothing: the portal rows are exactly the first deposit, the
    latest row's running balance equals the stored balance, and the balance is 100.
- `aReplayThatReadOlderHistoryNeverOverwritesANewerBalance` (F-5, write order):
  - Replay A (deposit 100, thread A) parks at its first portal write, after its history read and
    before any portal write.
  - Thread B deposits 50. The test waits, by observing `pg_stat_activity` and never by sleeping,
    until B is queued on the lock or has finished. Then it releases A.
    - Round 2: the poll counts only a backend waiting on an advisory lock whose statement is the
      matter-replay lock (`query LIKE '%portal_trust_matter%'`). An unrelated advisory waiter
      cannot release A early.
  - Assert the balance is 150, the latest row is 150, and B really waited on A's matter lock.
  - The gate parks before any write on purpose. A first attempt parked at the balance write, after
    the rows. With the transaction but no lock, A's row locks then serialised B, so the test timed
    out instead of showing the stale write.
- `aReplayReadsARowItsTransactionHeldUnpostedAsPostedOnceAnotherTransactionPostedIt` (F-5, read
  side, round 2). It is deterministic and single-threaded:
  - Deposit 100 and an awaiting-approval payment of 30 are committed.
  - In one tenant transaction:
    1. `findById(payment)` makes the payment a managed entity, `AWAITING_APPROVAL`, effect 0.
    2. A `REQUIRES_NEW` transaction, with its own persistence context, posts it through
       `TrustPostingService.postOnApproval` and commits.
    3. The outer transaction publishes a `TrustTransactionRecordedEvent` for the matter.
  - On commit the replay runs `AFTER_COMMIT` on that thread, against the outer transaction's
    still-open persistence context.
  - Assert the portal holds the payment row and the deposit row, and the balance and latest
    running balance are 70.

## Evidence (this directory)

| File | Code | Result |
|------|------|--------|
| `red-run.txt` | base `main` sources, round-1 test (2 tests) | 2/2 FAIL. F-4: the failed replay's new row is committed (2 portal rows where only the first deposit's is expected; the stored balance was not rewritten). F-5: `expected 150.00 but was 100.00`, B did not wait. |
| `red-stale-read.txt` | round-2 head with the history read switched back to the entity query (mapped to rows; lock and transaction kept) | stale-read test FAIL: portal rows `[deposit]`, missing the posted payment. The F-4 and F-5 write-order tests pass. |
| `green-run.txt` | round-2 head | 3/3 PASS |
| `mutation-no-transaction.txt` | round-2 head minus the `portalTxTemplate` wrapper (lock kept; the xact lock then releases at once) | 2/3 FAIL (F-4 partial commit; F-5 `was 100.00`); the stale-read test passes |
| `mutation-no-lock.txt` | round-2 head minus `lockMatterReplay` (transaction kept) | 1/3 FAIL: F-5 `was 100.00`; F-4 and stale-read pass |
| `tier2-part1.txt` | `architecture.**` + `customerbackend.**` + portal-trust classes elsewhere | 48 classes, 347 testcases, 0 failures/errors |
| `tier2-part2-trustaccounting.txt` | `verticals.legal.trustaccounting.**` (every trust service test that posts and so triggers a replay) | 78 classes, 863 testcases, 0 failures/errors |
| `tier2-part3-trust-consumers.txt` | 12 trust-posting classes outside those packages (demo seed, statements, dashboard, reporting, MCP, templates, FICA, audit guard) | 12 classes, 94 testcases, 0 failures/errors |
| `tier2-part4-repository-users.txt` | the remaining `TrustTransactionRepository` users (`LegalDemoDataSeederTest`, `DisbursementServiceTest`, `StatementRenderingIntegrationTest`, `StatementOfAccountContextBuilderTest`) | 4 classes, 73 testcases, 0 failures/errors |

Counts are `<testcase>` elements in `target/surefire-reports`, cleared between parts. The
mutation and red runs skip `spotless:check` (`-Dspotless.check.skip=true`) because the temporary
mutations are not formatted. The full `./mvnw verify` was not run (the orchestrator owns it).

## Residual risk

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
