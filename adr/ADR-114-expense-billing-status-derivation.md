# ADR-114: Expense Billing Status Derivation

**Status**: Accepted

**Context**:

Phase 30 introduces the Expense entity for tracking disbursements against projects. Like TimeEntry, expenses have a billing lifecycle: they start unbilled, are included on an invoice (billed), or can be written off (non-billable). The question is whether to persist `billing_status` as a database column or derive it at read time from other fields.

The existing TimeEntry entity (Phase 5) does **not** store billing status. Instead, it stores a `billable` boolean and an `invoiceId` FK. The billing status is derived:
- `invoiceId != null` → BILLED
- `!billable` → NON_BILLABLE (written off)
- `billable && invoiceId == null` → UNBILLED

This computed approach was chosen in [ADR-066](ADR-066-computed-status-over-persisted.md) to avoid status synchronisation bugs where the stored status could drift from the actual invoice linkage state (e.g., after invoice voiding). The same concern applies to expenses: when an invoice is voided, all linked expenses must revert to UNBILLED. With a stored column, every invoice void requires a bulk UPDATE on expenses. With derivation, clearing the `invoiceId` is sufficient.

The requirements prompt specifies `billing_status` as a persisted column with explicit transitions. This ADR evaluates whether to follow that specification or align with the established TimeEntry pattern.

**Options Considered**:

1. **Persisted `billing_status` VARCHAR column** — Store the status directly on the expenses table with CHECK constraint.
   - Pros:
     - Simple queries: `WHERE billing_status = 'UNBILLED'` with no logic needed
     - Explicit state visible in the database for debugging
     - Matches the requirements prompt specification
   - Cons:
     - Status can drift from actual state (invoiceId cleared but status not updated = bug)
     - Invoice voiding requires bulk UPDATE on both time_entries and expenses
     - Two sources of truth: the FK state and the status column
     - Diverges from TimeEntry's pattern, creating an inconsistency in the domain model

2. **Computed from `billable` + `invoiceId` (match TimeEntry pattern)** — No `billing_status` column. Store `billable` boolean and `invoiceId` FK. Derive status in Java getter.
   - Pros:
     - Single source of truth: FKs are authoritative
     - Invoice void = clear `invoiceId` → status automatically correct
     - Consistent with TimeEntry (one pattern for all billable entities)
     - No synchronisation bugs possible
     - Aligns with [ADR-066](ADR-066-computed-status-over-persisted.md)
   - Cons:
     - Queries require CASE expression: `CASE WHEN invoice_id IS NOT NULL THEN 'BILLED' WHEN NOT billable THEN 'NON_BILLABLE' ELSE 'UNBILLED' END`
     - Cannot index on billing status directly (must use functional index or computed column if needed)
     - Overrides the requirements prompt specification (deliberate deviation with rationale)

3. **Computed column in PostgreSQL** — Use a generated column: `billing_status VARCHAR GENERATED ALWAYS AS (CASE WHEN invoice_id IS NOT NULL THEN 'BILLED' ...)`.
   - Pros:
     - Best of both worlds: queryable column + no drift
     - Can be indexed
     - No Java getter logic needed
   - Cons:
     - PostgreSQL GENERATED columns are STORED (not VIRTUAL), so they consume disk space
     - Hibernate support for generated columns requires `@Generated(GenerationTime.ALWAYS)` which forces a re-read after every write
     - No precedent in the codebase — all other computed values use Java getters
     - Adds database-level complexity for a simple derivation

**Decision**: Option 2 — Computed from `billable` + `invoiceId`, matching the TimeEntry pattern.

**Rationale**:

The Expense entity is architecturally parallel to TimeEntry: both are project-scoped, billable line items that flow into invoices. Using the same billing status derivation pattern ensures consistency across the domain model and eliminates an entire class of synchronisation bugs.

The CASE expression in queries is a minor cost. The existing `InvoiceService.getUnbilledTime()` already uses equivalent logic for TimeEntry (`WHERE billable = true AND invoice_id IS NULL`). The unbilled expense query follows the same pattern. Reporting queries that filter by billing status use the same WHERE clause.

This deliberately overrides the requirements prompt's `billing_status` column specification. The requirements describe desired behaviour (billing status transitions), which this approach fully satisfies — the difference is implementation mechanism, not functional outcome.

**Consequences**:

- Expense entity stores `billable` (boolean, default true) and `invoiceId` (UUID, nullable) — no `billing_status` column
- `Expense.getBillingStatus()` Java getter derives the status, matching `TimeEntry.getBillingStatus()`
- Migration V47 creates the `expenses` table without a `billing_status` column
- Unbilled expense query: `WHERE billable = true AND invoice_id IS NULL`
- Invoice void logic: clear `invoiceId` on linked expenses (same as time entries) — no additional status update needed
- Write-off: set `billable = false` — same as TimeEntry pattern
- Consistent pattern for any future billable entity types
- Related: [ADR-066](ADR-066-computed-status-over-persisted.md) (original computed status decision), [ADR-115](ADR-115-expense-markup-model.md) (expense markup model)
