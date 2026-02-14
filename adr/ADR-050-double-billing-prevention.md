# ADR-050: Double-Billing Prevention

**Status**: Accepted

**Context**: When billable time entries are included in an invoice, the system must prevent the same time entry from being billed twice. Double-billing is a serious integrity violation in professional services -- it erodes client trust and can create legal liability. The prevention mechanism must handle the full invoice lifecycle, including voiding (which should make time entries available for re-billing).

Key constraints:
- A time entry can appear on at most one *active* (non-voided) invoice.
- When an invoice is voided, its time entries must become available for inclusion in a new invoice.
- Time entries that are part of an approved invoice must be locked from editing or deletion (the invoice is a financial record; changing the underlying time data would create inconsistency).
- The mechanism must work within the existing multi-tenant model (dedicated schemas for Pro, `tenant_shared` for Starter).
- The solution should use database-level enforcement where possible, not just application-level checks.

**Options Considered**:

1. **`invoice_id` FK on TimeEntry** -- Add an `invoice_id` column to the `time_entries` table. When an invoice is approved, set `invoice_id` on each referenced time entry. When voided, clear `invoice_id` back to NULL.
   - Pros:
     - Simple, single-column addition to an existing table.
     - "Is this time entry billed?" is a trivial NULL check: `invoice_id IS NOT NULL`.
     - "Show me all unbilled time" is a simple query: `WHERE billable = true AND invoice_id IS NULL`.
     - Voiding is clean: `UPDATE time_entries SET invoice_id = NULL WHERE invoice_id = :invoiceId`.
     - No new tables or join logic needed.
     - Provides a direct navigation path from time entry to its invoice (useful for UI badges and links).
   - Cons:
     - Modifies the existing `TimeEntry` entity (schema change to a high-traffic table).
     - One-to-one: a time entry can only reference one invoice. This is the desired behavior, but it means the column is overwritten on void+rebill rather than accumulating history.
     - Doesn't inherently prevent the same time entry from appearing in two draft invoices simultaneously (drafts don't set `invoice_id` -- only approval does).

2. **Separate `billing_status` enum column on TimeEntry** -- Add a `billing_status` column with values `UNBILLED`, `BILLED`, `LOCKED`.
   - Pros:
     - Explicit status field that's clear in queries.
     - Could support intermediate states (e.g., `PENDING_APPROVAL`).
   - Cons:
     - Does not provide a link to *which* invoice the entry is on -- requires a separate lookup.
     - Synchronization risk: `billing_status` must stay in sync with the actual invoice state. If an invoice is voided but the status update fails, the entry is incorrectly marked as billed.
     - Introduces a second source of truth (the invoice's line items are one source, the time entry's status is another). These can diverge.
     - No database-level enforcement of uniqueness -- application code must prevent double-billing.

3. **Junction table (`invoice_line_time_entries`)** -- A many-to-many join table between InvoiceLine and TimeEntry.
   - Pros:
     - Supports aggregated line items (one line maps to many time entries) -- see [ADR-049](ADR-049-line-item-granularity.md).
     - Full history: if a time entry is voided and re-billed, both invoice references are preserved in the join table.
     - Does not modify the TimeEntry table.
   - Cons:
     - Additional table and join complexity for every billing query.
     - "Is this entry billed?" requires a join instead of a NULL check.
     - "Unbilled time" queries require a `NOT EXISTS` subquery instead of a simple `WHERE` clause.
     - More complex voiding logic (delete join table rows, then check if any remaining references exist).
     - Overkill for the 1:1 line-to-entry granularity chosen in [ADR-049](ADR-049-line-item-granularity.md).

**Decision**: `invoice_id` FK on TimeEntry (Option 1), combined with a partial unique index on `InvoiceLine.time_entry_id`.

**Rationale**: Given the 1:1 line-to-entry granularity ([ADR-049](ADR-049-line-item-granularity.md)), the `invoice_id` column on TimeEntry is the most natural and efficient mechanism. It provides:

1. **Database-level enforcement**: A partial unique index on `invoice_lines(time_entry_id) WHERE time_entry_id IS NOT NULL` ensures that a time entry cannot appear in two InvoiceLine rows (across all invoices in the schema). Combined with `time_entries.invoice_id`, this creates a two-layer defense.

2. **Lifecycle alignment**: The `invoice_id` column tracks the *current billing state* of the time entry:
   - `NULL` = unbilled (available for invoicing)
   - Non-NULL = billed (locked, references the active invoice)
   When an invoice is voided, `invoice_id` is set back to NULL, making the entry available again. The voided invoice's line items remain in the database for audit purposes, but the partial unique index excludes voided invoices, so the time entry can be included in a new invoice.

3. **Query efficiency**: The most common billing query ("show unbilled time for customer X") becomes `WHERE billable = true AND invoice_id IS NULL`, which is indexable and fast.

4. **Edit locking**: Time entries with `invoice_id IS NOT NULL` are immutable (PUT/DELETE return 409 Conflict). This prevents retroactive changes to time data that would make the invoice inaccurate. To fix a time entry that's been invoiced, the user must void the invoice first.

The timing of `invoice_id` assignment is important: it happens at **approval**, not at draft creation. This means two users can independently create drafts referencing the same time entry, but only one can approve (the second approval would fail because the time entries are now marked as billed). This is acceptable because:
- Invoice creation is typically a single-user workflow.
- The approval step is the financial commitment; drafts are just proposals.
- The error message at approval time clearly indicates which entries are already billed.

**Consequences**:
- V23 migration adds `invoice_id UUID REFERENCES invoices(id)` to `time_entries`. Nullable, no default.
- A partial unique index `idx_invoice_lines_time_entry_unique` on `invoice_lines(time_entry_id) WHERE time_entry_id IS NOT NULL` prevents duplicate billing at the InvoiceLine level. This index does NOT exclude voided invoices because `InvoiceLine` rows from voided invoices should still be preserved for audit; instead, the application layer only creates new InvoiceLines from time entries with `invoice_id IS NULL`.
- `TimeEntryService` rejects PUT and DELETE for time entries where `invoice_id IS NOT NULL`, returning 409 Conflict with a message referencing the invoice number.
- Voiding an invoice: (1) updates invoice status to VOID, (2) sets `invoice_id = NULL` on all time entries referencing the invoice. Both operations happen in a single transaction.
- Time entry list API gains a `billingStatus` filter: `UNBILLED` (`billable = true AND invoice_id IS NULL`), `BILLED` (`invoice_id IS NOT NULL`), `NON_BILLABLE` (`billable = false`).
- Time entry responses include `invoiceId` and `invoiceNumber` (resolved from the referenced invoice) for UI display.
