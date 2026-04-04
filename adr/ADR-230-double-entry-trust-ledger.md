# ADR-230: Double-Entry vs Single-Entry Trust Ledger

**Status**: Accepted

**Context**:

Phase 60 adds trust accounting to the legal vertical. Trust accounting under South Africa's Legal Practice Act Section 86 requires three views that must always agree: the trust bank account balance, the trust cashbook (the firm's record of trust transactions), and the sum of individual client ledger card balances. The system must maintain a complete, unalterable audit trail — every rand that enters or leaves the trust account must be traceable to a specific client matter, and the records must be available for inspection by the Legal Practice Council at any time.

The question is whether to model this as a true double-entry ledger (each transaction affects both the cashbook and a client ledger) or as a single-entry system where one view is derived from the other at query time. This decision affects the entity model, the write path complexity, the reconciliation check performance, and the ability to enforce balance invariants at the database level.

**Options Considered**:

1. **Single-entry with derived balances** — Store transactions once in a single TrustTransaction table, derive client balances via aggregation queries at read time. The cashbook is the single source of truth; client ledger balances are computed by filtering and summing transactions per client.
   - Pros:
     - Simplest write path: one INSERT per transaction, no secondary updates, no risk of partial writes leaving the system in an inconsistent state.
     - Single source of truth: there is exactly one table of transactions. No possibility of the cashbook and client ledgers disagreeing because the client ledger is not stored — it is computed.
     - Schema simplicity: one entity (TrustTransaction), one repository, one service. No cached balance columns to maintain, no secondary entities to keep in sync.
   - Cons:
     - Reconciliation check requires a full table scan: verifying that the cashbook total equals the sum of client ledger balances means aggregating the entire transaction table grouped by client. For a firm with 5 years of trust data (tens of thousands of transactions), this is O(n) on every check.
     - Cannot enforce non-negative client balances at the database level. A CHECK constraint on a balance column is impossible when the balance is derived. Preventing a client's trust balance from going negative requires application-level logic that reads the current aggregate, checks the result, and inserts — a race condition window exists between the read and write unless explicit row-level locking is used.
     - Client balance lookups are O(n): every time the UI displays a client's trust balance, the system must sum all transactions for that client. This is acceptable for small datasets but degrades as transaction volume grows.
     - No materialized client ledger card: the Legal Practice Act requires firms to maintain individual client ledger cards. A derived view satisfies the letter of the law but makes it harder to produce the physical artifact (a per-client statement of account) without running an aggregation query.

2. **Double-entry with immutable transactions and cached balances** — Each TrustTransaction is immutable once created. Every approved transaction atomically updates a cached balance on the corresponding ClientLedgerCard entity. The cashbook balance is computed from the sum of approved transactions (not cached). Client ledger balances are cached on the ledger card for O(1) reads.
   - Pros:
     - O(1) client balance reads: the ClientLedgerCard.balance column is always current, updated atomically with each transaction. Displaying a client's trust balance is a single row lookup.
     - Database-level non-negative enforcement: a CHECK constraint on ClientLedgerCard.balance prevents any transaction that would take a client's trust balance below zero. The constraint fires within the same database transaction as the balance update, eliminating race conditions.
     - Immutable transactions: once a TrustTransaction is persisted, it is never updated or deleted. Corrections are handled via reversal transactions (a new transaction that negates the original). This matches how real trust accounting works and satisfies the audit trail requirement.
     - Three-way reconciliation is always computable: bank balance (imported), cashbook balance (SUM of approved transactions), and client ledger total (SUM of cached ClientLedgerCard.balance values). The cashbook sum is O(n) but only needs to run during explicit reconciliation, not on every page load.
     - Natural entity model: TrustTransaction and ClientLedgerCard map directly to the legal concepts that practitioners and auditors understand. The client ledger card is a first-class entity, not a derived view.
   - Cons:
     - Write path is more complex: every transaction must update the ClientLedgerCard.balance within the same database transaction. A failure to update the cached balance after inserting a transaction would leave the system inconsistent. This requires careful transaction management.
     - Two entities to maintain: TrustTransaction and ClientLedgerCard must stay in sync. The cached balance is a denormalization — the source of truth for the balance is the sum of transactions, but the cached value must always agree. A reconciliation check (sum of transactions vs. cached balance) is needed as a consistency safety net.
     - Reversal transactions add volume: corrections create additional rows rather than modifying existing ones. Over time, the transaction table grows faster than a mutable system. This is an acceptable trade-off for auditability but means the table will be larger.

3. **Full double-entry with separate journal and ledger tables** — Traditional accounting model with a Chart of Accounts, a Journal table (raw entries with debits and credits), and a Ledger table (posted entries after approval). Each transaction creates multiple journal entries (debit one account, credit another). A trial balance verifies that total debits equal total credits.
   - Pros:
     - Maximum accounting purity: this is the textbook double-entry bookkeeping model. Every transaction is decomposed into debit and credit entries that must balance. The trial balance is an inherent consistency check.
     - Extensible to general accounting: if the system ever needs to handle a full general ledger (income, expenses, assets, liabilities), the infrastructure is already in place.
     - Familiar to accountants and auditors: the journal/ledger/trial-balance model is the standard taught in every accounting course. Auditors would immediately understand the data model.
   - Cons:
     - Massive over-engineering for trust accounting. A trust account is a single bank account with client sub-accounts. There is no chart of accounts (no income, expenses, assets, or liabilities beyond the trust account itself). There is no general ledger. There is no trial balance in the traditional sense — the reconciliation check is bank = cashbook = sum of client ledgers, not debits = credits.
     - Adds ~5 extra entities (ChartOfAccount, JournalEntry, JournalLine, LedgerEntry, LedgerLine) with associated repositories, services, and controllers. This is a significant development and maintenance burden for a single-account system.
     - Every trust transaction would create 2-4 journal lines (debit trust bank, credit client ledger, or vice versa). The write path is 3-4x more complex than Option 2 with no practical benefit — the same consistency guarantees can be achieved with a simpler model.
     - The journal/ledger distinction (unposted vs. posted entries) adds a workflow step that trust accounting does not need. Trust transactions are either approved or not — there is no intermediate "journaled but not yet posted" state that serves a purpose.
     - Developers maintaining this system need accounting domain knowledge to understand the data model. Option 2's model (transactions + client ledger cards) is immediately understandable to any developer; Option 3's model requires explaining why a single deposit creates four rows across two tables.

**Decision**: Option 2 — Double-entry with immutable transactions and cached balances.

**Rationale**:

Option 1 fails on two critical requirements. First, the reconciliation check — the cornerstone of trust accounting compliance — requires summing all transactions to derive the cashbook balance. This is O(n) and degrades as transaction volume grows. A firm with 5 years of trust data and tens of thousands of transactions will experience noticeable latency on every reconciliation check. More critically, Option 1 cannot enforce non-negative client balances at the database level. A CHECK constraint requires a materialized balance column; a derived balance can only be validated at the application level, which introduces race condition risks that are unacceptable for trust funds.

Option 3 is overkill. Trust accounting is fundamentally a single-account system: one trust bank account, many client sub-accounts. There is no chart of accounts, no general ledger, no trial balance, no debit/credit decomposition that serves a practical purpose. The full double-entry bookkeeping model adds ~5 entities and 3-4x write path complexity for zero practical benefit. If the system ever needs general accounting (unlikely — this is a practice management tool, not an accounting package), it can be added separately without retrofitting the trust module.

Option 2 gives the best balance of correctness, performance, and simplicity. Immutable transactions satisfy the Legal Practice Act's audit trail requirement — every transaction is preserved forever, corrections are visible as reversal transactions, and no record is ever altered or deleted. Cached balances on ClientLedgerCard provide O(1) balance lookups and enable a CHECK constraint that prevents negative balances at the database level, eliminating an entire class of application bugs. The three-way reconciliation check (bank balance vs. cashbook sum vs. client ledger total) is always computable from materialized data: the bank balance is imported, the cashbook balance is SUM of approved transactions, and the client ledger total is SUM of cached balances.

The cached balance is a deliberate denormalization. The source of truth for a client's balance is always the sum of their transactions. The cached value is an optimization that must be updated atomically with each transaction insertion. A background reconciliation job can verify that cached balances match computed balances and flag any drift — but the atomic update within a single database transaction makes drift practically impossible.

Immutability is enforced at the application level: the TrustTransactionService rejects update and delete operations. Database-level immutability (e.g., a trigger that prevents UPDATE/DELETE on the trust_transactions table) can be added as a defense-in-depth measure but is not required for correctness if the application layer is the only writer.

**Consequences**:

- Every approved trust transaction must atomically update the corresponding ClientLedgerCard.balance within the same database transaction. The service method inserts the TrustTransaction and updates the balance in a single `@Transactional` block. If either operation fails, both roll back.
- Reversals create new TrustTransaction rows with negated amounts — never updates or deletes to existing rows. The audit trail grows monotonically. A reversed transaction is linked to its original via a `reversal_of` foreign key, making the correction chain explicit.
- The cashbook balance is derived from `SUM(amount) WHERE status = APPROVED` on the trust_transactions table — it is not cached. This avoids a second cached balance that could drift. The O(n) cost is acceptable because the cashbook balance is only needed during explicit reconciliation checks, not on every page load.
- Client ledger balances are cached on ClientLedgerCard.balance and protected by a `CHECK (balance >= 0)` constraint. This means the database itself will reject any transaction that would overdraw a client's trust funds, regardless of application-level bugs.
- The three-way reconciliation check compares: (1) the imported bank statement balance, (2) the computed cashbook sum (`SUM` of approved transactions), and (3) the sum of cached client ledger balances (`SUM` of ClientLedgerCard.balance). All three values must agree. A discrepancy indicates either an unrecorded bank transaction, a missing client allocation, or a cached balance drift — each of which is a distinct, diagnosable problem.
- A background reconciliation job should periodically verify that each ClientLedgerCard.balance equals the sum of its approved transactions. This is a consistency safety net, not a primary correctness mechanism — the atomic update in the write path is the primary guarantee.
- Related: Trust accounting immutability is a stricter requirement than the general audit trail (Phase 6, ADR-025). Audit events are append-only by convention; trust transactions are append-only by legal mandate.
