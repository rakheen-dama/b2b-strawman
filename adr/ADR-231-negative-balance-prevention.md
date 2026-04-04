# ADR-231: Negative Balance Prevention Strategy

**Status**: Accepted

**Context**:

Under the Legal Practice Act Section 86, a law firm may NEVER use one client's trust money for another client's purpose. This means a client's trust balance can never go below zero. This is not a business rule — it is a legal requirement that, if violated, can result in the firm being struck off the roll. The system must prevent negative balances with absolute certainty, even under concurrent access.

A trust account typically processes 50–200 transactions per day for a small firm. Multiple bookkeepers may approve payments for the same client simultaneously. The system handles debit operations (PAYMENT, TRANSFER_OUT, FEE_TRANSFER, REFUND approval) that reduce a client's trust balance. If two concurrent requests each read a balance of R10,000 and each approve an R8,000 payment, the client ends up at negative R6,000 — a violation that could trigger a Law Society investigation.

**Options Considered**:

1. **Application-level check only** — The service layer reads the current balance from the ClientLedgerCard, checks if the balance is sufficient for the requested debit, and creates the transaction if it is. This is a standard read-then-write pattern with no database-level enforcement.
   - Pros:
     - Simplest implementation: a single `if (balance >= amount)` check in the service method. No database changes, no locking strategy, no constraint migrations.
     - Clear error messages: the application knows the client name, current balance, and requested amount, so it can produce "Insufficient trust balance for Sipho Mabena. Available: R5,000. Requested: R8,000."
     - Easy to test: a single unit test verifies the balance check logic.
   - Cons:
     - Vulnerable to race conditions. Two concurrent requests can both read R10,000, both pass the check, and both create R8,000 debits — resulting in a negative R6,000 balance. This is not theoretical: a busy bookkeeper approving multiple payments in quick succession, or two bookkeepers working on the same client's account, can trigger concurrent requests.
     - No protection against direct database access. A migration, data fix, or manual SQL update that bypasses the application layer can create a negative balance with no enforcement.
     - The race window is small but the consequence is catastrophic. A single violation can trigger a Law Society audit. "It only happens under concurrency" is not an acceptable defence when the firm's practising certificate is at stake.

2. **Database CHECK constraint only** — A `CHECK (balance >= 0)` constraint on the `client_ledger_cards` table. The database rejects any UPDATE that would set the balance below zero, regardless of how the update is issued.
   - Pros:
     - Absolute enforcement: the database kernel guarantees the invariant. No application bug, race condition, or direct SQL access can violate it. The constraint is checked within the transaction's write lock on the row.
     - Simple to implement: a single line in a Flyway migration. No application code changes.
     - Protects against all access paths: application code, migrations, manual SQL, third-party tools, future microservices — anything that touches the database respects the constraint.
   - Cons:
     - Poor error messages. When the constraint fires, the application receives a generic `DataIntegrityViolationException` with a message like `ERROR: new row for relation "client_ledger_cards" violates check constraint "client_ledger_cards_balance_check"`. The user sees a 500 error or a generic "operation failed" message instead of a helpful "Insufficient trust balance for Sipho Mabena."
     - Still vulnerable to the race condition at the application level. Without explicit locking, two concurrent requests can both read R10,000. The first commits an R8,000 debit (balance becomes R2,000). The second tries to commit an R8,000 debit and the CHECK constraint rejects it — but the user gets a cryptic error instead of a clear insufficient-balance message. The constraint prevents the violation but the UX is broken.
     - Cannot provide contextual information in the error: client name, available balance, requested amount. The constraint only knows the row values, not the business context.

3. **Belt and suspenders: SELECT FOR UPDATE + CHECK constraint** — The application layer uses `SELECT ... FOR UPDATE` on the ClientLedgerCard row to acquire a row-level lock, reads the current balance, validates it against the requested debit amount, creates the transaction, and updates the balance — all within a single database transaction. A `CHECK (balance >= 0)` constraint on the table acts as a safety net in case the application logic has a bug.
   - Pros:
     - Eliminates the race condition. `SELECT FOR UPDATE` acquires an exclusive row-level lock. The second concurrent request blocks until the first transaction commits or rolls back. When the second request proceeds, it reads the updated balance (R2,000, not R10,000) and correctly rejects the R8,000 debit with a clear error message.
     - Clear error messages from the application layer: "Insufficient trust balance for Sipho Mabena. Available: R2,000. Requested: R8,000." The user knows exactly what happened and what the current balance is.
     - Defence in depth. If the application logic has a bug — an edge case in the balance calculation, a missing check on a new transaction type, a developer who forgets to acquire the lock — the CHECK constraint catches the violation at the database level. The user gets a less helpful error, but the invariant is preserved.
     - Protects against all access paths. Direct SQL, migrations, and data fixes are caught by the CHECK constraint even when they bypass the application's locking logic.
     - The lock scope is narrow: one row per client per trust account. Different clients never contend with each other. The lock is held only for the duration of the transaction (read balance, validate, create transaction, update balance), which involves no external calls and completes in milliseconds.
   - Cons:
     - More complex than either option alone. Developers must remember to acquire the lock before any debit operation. A new transaction type that debits trust funds must follow the same pattern.
     - Row-level locks can cause deadlocks if the transaction acquires locks on multiple rows in inconsistent order. Mitigation: trust debit operations lock exactly one ClientLedgerCard row per transaction. Transfers between clients lock both rows in a deterministic order (by ID).
     - Slightly higher latency under contention. If two requests target the same client, the second waits for the first to complete. At 50–200 transactions per day, this contention is negligible — the wait is measured in milliseconds.

**Decision**: Option 3 — Belt and suspenders: SELECT FOR UPDATE + CHECK constraint.

**Rationale**:

Option 1 is unacceptable for a legal compliance feature. Race conditions are not theoretical — a busy bookkeeper approving multiple payments for the same client could trigger concurrent requests. The consequence of a single violation is not a minor data inconsistency; it is a potential Law Society investigation and the firm being struck off the roll. No amount of "it's unlikely" justifies the risk when a proven concurrency control mechanism (SELECT FOR UPDATE) exists and is trivial to implement.

Option 2 catches violations but with terrible UX. A raw `DataIntegrityViolationException` tells the user nothing useful. The bookkeeper needs to see "Insufficient trust balance for Sipho Mabena. Available: R5,000. Requested: R8,000" — not a generic error page. Furthermore, the constraint fires after the fact: the request has already done its work, the transaction is being committed, and only then does the constraint reject it. The application cannot provide helpful guidance (e.g., "The maximum payment you can approve is R5,000") because it never checked the balance.

Option 3 gives both guarantees. The SELECT FOR UPDATE prevents races — the second concurrent request blocks until the first completes, then reads the correct (updated) balance. The application check provides a clear, contextual error message. The CHECK constraint is the final safety net for any code path that bypasses the application logic (direct SQL, migrations, future services, bugs in the locking code itself).

Trust transaction volumes are low. A small law firm processes 50–200 trust transactions per day. The row-level lock contention is negligible — milliseconds of wait time when two requests happen to target the same client simultaneously. This is not a high-throughput payment system where lock contention degrades performance. The correctness guarantee is worth infinitely more than the unmeasurable performance cost.

The CHECK constraint also catches a class of bugs that no application-level logic can prevent: a developer who adds a new debit transaction type and forgets to acquire the lock, a migration that adjusts balances with a bulk UPDATE, or a support engineer running a manual SQL fix. These scenarios bypass the application layer entirely. The constraint is the last line of defence.

**Consequences**:

- Every debit operation (PAYMENT, TRANSFER_OUT, FEE_TRANSFER, REFUND approval) must acquire the ClientLedgerCard row lock via `SELECT ... FOR UPDATE` before checking the balance. This is a mandatory step in the debit workflow, not an optional optimisation. The service method should be structured as: (1) lock the row, (2) read the balance, (3) validate, (4) create the transaction, (5) update the balance — all within a single `@Transactional` method.
- The lock scope is per-client, per-trust-account. Operations on different clients never contend with each other. Two bookkeepers approving payments for different clients proceed in parallel with no blocking.
- The approval transaction must be short-lived. No external calls (S3 uploads, email sends, webhook dispatches) may occur while holding the row lock. External side effects must happen after the transaction commits (e.g., via `@TransactionalEventListener`).
- Transfers between two clients (TRANSFER_OUT from Client A, TRANSFER_IN to Client B) must lock both ClientLedgerCard rows in a deterministic order (by primary key ID, ascending) to prevent deadlocks. This ordering must be documented and enforced in the transfer service method.
- Integration tests must verify both layers independently: (1) the application-level rejection produces a clear error message with the client name, available balance, and requested amount, and (2) a direct SQL test that bypasses the application confirms the CHECK constraint rejects a negative balance at the database level.
- The Flyway migration that creates or alters the `client_ledger_cards` table must include `CHECK (balance >= 0)`. This constraint is non-negotiable and must not be removed or relaxed in future migrations.
- Repository interfaces must expose a `findByIdForUpdate` (or equivalent `@Lock(PESSIMISTIC_WRITE)` annotated query) method. Standard `findById` must NOT be used for debit operations — code review should flag any debit path that reads the ledger card without acquiring the lock.
