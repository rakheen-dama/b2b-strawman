# ADR-378: Ledger balances are derived from per-row posting effects set by one transaction-type registry; reversals are contra entries

**Status**: Accepted (amends [ADR-230](ADR-230-double-entry-trust-ledger.md) and [ADR-231](ADR-231-negative-balance-prevention.md))

**Context**:

[ADR-230](ADR-230-double-entry-trust-ledger.md) chose immutable transactions with cached balances, and [ADR-231](ADR-231-negative-balance-prevention.md) protected the cached client balance with `SELECT … FOR UPDATE` plus the CHECK `chk_client_ledger_balance_non_negative`. At `main` `8440d3cf4` the cashbook is recomputed from transactions while the client ledger is the stored `client_ledger_cards.balance`. Three facts follow (paths relative to `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/`):

- **The stored balance is changed by ten mutator calls plus an upsert**: nine in `transaction/TrustTransactionService.java` (`:378`, `:552`, `:559`, `:1036`, `:1038`, `:1040`, `:1353`, `:1568`, `:1574`), one at `interest/InterestService.java:604`, and the zero-card upsert at `TrustTransactionService.java:1706-1720`. Any path that writes a transaction and not the card breaks the books silently. Known cases: the demo seeder (cashbook R27 000,00, card total R0,00), the client-less `INTEREST_LPFF` row behind the measured R 25,52 gap, approval of a credit reversal, and a debit reversal where no card exists.
- **The meaning of a transaction type is restated in eleven code locations**: the DDL CHECK; the cashbook `CASE` query; the six client-balance `CASE` queries, counted as one location; the credit / debit sets in three services; `LedgerTotals`; two report queries; the statement-of-account builder; and the portal sync. It is restated again in the label maps of three codebases (backend, frontend, portal).
- **A reversal rewrites history.** The original is set `REVERSED` and drops out of every sum; the `REVERSAL` row counts 0 and is dated today. A balance as at any earlier date changes after the fact. A credit reversal is saved `AWAITING_APPROVAL` and, on the code read, its approval changes neither cashbook nor card (hypothesis H-a, to be reproduced before any fix).

Two requirements cannot be met by a stored balance at all. Ruling R1 needs every term of the equation as at a date, and a stored figure only answers "now". The integrity check must flag a client ledger in debit, and a column with a non-negative CHECK cannot hold one; the demo data deliberately contains such a client. PR #1746 already moved the totals shown on a card to derived figures; the balance was left stored.

**Options Considered**:

1. **Keep stored balances and guard them** with the harness and a reconciling job.
   - Pros: Reads stay one row. No change to the sufficiency checks or list pages.
   - Cons: The divergence class is guarded, not removed: the ten mutator call sites stay, and every new posting path adds another. No as-at figure exists, so R1 would need derived queries anyway and the stored figure becomes a second source of truth for the same number. A client in debit cannot be represented.
2. **Stored balances maintained by a database trigger** on `trust_transactions`.
   - Pros: One writer; no code path can forget the card.
   - Cons: The sign rules move into PL/pgSQL in every tenant schema, a further copy of the type semantics that the Java tests and labels cannot share. Still no as-at figure and still no client in debit.
3. **Derived balances computed with sign `CASE` expressions generated from the registry**, no stored effects.
   - Pros: No new columns and no backfill. The registry is still the single source.
   - Cons: Every balance query carries a generated `CASE` over twenty-five types. No row-level constraint can state the equation, because a row does not know its own effect. A reversal's effect depends on its original's type, so each sum needs a self-join.
4. **Derived balances over stored per-row effects (chosen).**
   - Pros: One writer, one sum. No SQL knows a type's sign. A row CHECK can constrain a posted row's shape to "moves the cashbook and one ledger equally, or a ledger only". Every balance is available as at any date.
   - Cons: Four new columns and a backfill. Account totals cost a scan that grows with history. The effects are stored facts: a registry edit does not retroactively change posted rows, which is correct for books but means a wrong registry entry must be corrected by postings, not by a code fix.
5. **Derived balances plus a periodic snapshot table.**
   - Pros: Bounds the aggregate; reads start from the last snapshot.
   - Cons: A second stored figure to keep right, built before any tenant needs it. Recorded as the escape hatch, not built.

For reversals, two models were weighed. **The original drops out of the sums** (today): simple, and net balances are right as at now; but history changes retroactively, which is fatal to a locked month, and the bank statement shows both movements on their own days. **A contra entry**: the original stays posted and the `REVERSAL` row carries the negated effects on its own date; every as-at balance stays true, at the cost of a migration that restores effects to old reversed rows and a changed meaning for `status = 'REVERSED'`.

**Decision**: Option 4 — `client_ledger_cards.balance` and its totals are dropped and the card row remains only as the per-client lock, each posted transaction stores `ledger`, `cashbook_effect`, `ledger_effect` and `posted_at` written once by `TrustPostingService` from the `TrustTransactionType` registry, every balance is a sum over those columns as at a date, the registry is the single source of a type's cashbook sign, ledger, approval rule, reversal rule and labels, and a reversal is a contra row carrying the negated effects on its own date while the original stays in the books.

**Rationale**:

R1 decides it. Reconciliation, the monthly list and the as-at trust position must be derived whatever is done with the card, so the only open question is whether a stored figure stays beside them for list pages. Keeping it would keep the ten mutator call sites and the possibility that the two disagree, which is the defect class this phase exists to remove. With effects on the row there is one write, in `TrustPostingService`, and an ArchUnit rule (`TrustPostingRulesTest`) forbids any other class from writing effects or saving a posted row, the demo seeder included. Storing the effects, not generating `CASE` expressions, is what allows `chk_trust_txn_posting` to exist and what lets a `REVERSAL` carry its own figures without a join. That CHECK is a row-shape check only, the row-level half of EQ-2 ([ADR-377](ADR-377-trust-equation-and-where-it-is-enforced.md)): it does not know whether the sign or the ledger suits the type. That is EQ-6, asserted by the harness, which re-derives each posted row's expected effects from the registry.

The registry's `approval` attribute has four values so that it can be authoritative for every type. `NONE` posts at once as `RECORDED`. `REQUIRED` is saved `AWAITING_APPROVAL` and posts on approval. `WITH_PAIR` is never recorded on its own: the row is created and posted in the same transaction that posts its paired row (`UNIDENT_ALLOC_IN`, `INV_RECEIVE`, `FIRM_INV_RECEIVE`, `INV_RETURN`, `FIRM_INV_RETURN`) and inherits whatever control its pair had. `WITH_ORIGINAL` is for `REVERSAL` only and follows the original type's `reversal` rule. Which types carry which value, and why, is recorded in [ADR-380](ADR-380-statement-line-classification-and-posting.md).

The read cost was weighed and accepted. One client's balance and the sufficiency check become an index range scan on `idx_trust_txn_ledger_sum` over tens to low hundreds of rows. The matters and clients lists stay one grouped query per page. The account total, used by reconciliation, the dashboard and MCP, becomes an aggregate linear in the account's posted rows; the architecture estimates about 10–30 ms at 50 000 rows on the covering index, which is an estimate, not a measurement. At design-partner volumes that is cheaper than the defects it removes. The limit is real: the aggregate grows with history. Option 5 is the recorded path if a tenant's account passes about a million rows, and `trust_monthly_lists` already holds exactly the figures such a snapshot would start from.

ADR-231's lock stays. `findByAccountAndCustomerForUpdate` on the card row is how every posting for a client is serialised, so the row is kept as a lock anchor and the sufficiency checks compute the derived balance under that lock, at record time and at approval time as today. What is replaced is the CHECK half: a non-negative constraint on a column that no longer exists. Sufficiency is still refused by the service for firm-initiated debits; the difference is that a client who is in debit for any other reason can now be seen and flagged. The transaction type stays a `varchar(20)` column with the entity field a `String`, per [ADR-238](ADR-238-entity-type-varchar-vs-enum.md); a schema test asserts that `chk_trust_txn_type` lists exactly the enum's constants.

The switch is one slice (646A) by design. A state where some readers use the stored balance and others the derived one is two sources of truth across a merge. The risk of a large slice is carried by the harness, which slice 649A builds first against the old model: it must report identical figures for every existing posting path before and after. No production data exists (founder decision 5), so the migration backfills effects from the HEAD sign table, deletes the interest postings that cannot satisfy the new CHECK ([ADR-383](ADR-383-retired-interest-run-data-is-dropped.md)) and drops the columns in each tenant schema without a compatibility window. The contra model lands separately in 649B because it changes behaviour and depends on a founder ruling (architecture §16.12.2 #1: whether a `RECORDED` row may be reversed).

**Consequences**:

- Positive:
  - The card/cashbook divergence class is gone: there is no second figure to forget.
  - Any balance can be asked as at any date, which the reconciliation, the monthly list and the trust position need.
  - A client ledger in debit is visible and flagged. A client with transactions and no card can no longer be missing from a report.
  - Adding a transaction type is one enum constant; labels, direction and filter options reach the firm app and the portal from the API (`GET /api/trust-transaction-types`, `typeLabel`, `direction`).
  - A reversal leaves earlier as-at balances unchanged, and a reversal dated by the bank in a past month can be put in that month (`reversalDate`).
- Negative:
  - Account-level totals are no longer constant-time. The snapshot escape hatch is designed and not built.
  - 646A touches most of the backend that reads trust money (dashboard, portal sync, statements, MCP, closure gate, collections, reports) and is deliberately not split; it needs a full verify and the legal Playwright suites.
  - `status = 'REVERSED'` changes meaning in 649B: a reversed original still counts, and its contra carries the negation. Any reader that filtered on status instead of summing effects would be wrong; the queries no longer filter on it.
  - `chk_trust_txn_posting` accepts a row with the wrong sign or the wrong ledger for its type; only the harness (EQ-6) and the single-writer rule stand against that.
  - The database no longer refuses a negative client balance. Prevention rests on the service's check under the row lock, and detection on the integrity check.
  - The sign `CASE` blocks in the V16 and V17 migrations are a frozen copy of the HEAD sign table. They are the last place a type's sign is written in SQL.

**Related**: [ADR-230](ADR-230-double-entry-trust-ledger.md) (amended: cached balances become derived), [ADR-231](ADR-231-negative-balance-prevention.md) (amended: the row lock stays, the CHECK is replaced by a derived check under the lock), [ADR-238](ADR-238-entity-type-varchar-vs-enum.md) (type stays a string column), [ADR-361](ADR-361-client-ledger-running-balance-server-side.md) (the running-balance window now sums `ledger_effect`), [ADR-372](ADR-372-read-only-chart-series-endpoints.md) (trend series), [ADR-329](ADR-329-trust-aware-collections-extension-seam.md) (reads the derived general balance), [ADR-377](ADR-377-trust-equation-and-where-it-is-enforced.md), [ADR-383](ADR-383-retired-interest-run-data-is-dropped.md); [architecture §16.2.1, §16.2.2, §16.2.3, §16.3.2, §16.3.11, §16.7.2, §16.7.3](../architecture/phase92-trust-books-provably-right.md); slices 649A, 646A, 649B.
