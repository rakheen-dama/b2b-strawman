# ADR-050: Double-Billing Prevention

**Status**: Accepted

**Context**: When generating invoices from time entries, the system must guarantee that a single time entry cannot appear on two active invoices. Double-billing erodes customer trust and creates accounting errors. The mechanism must handle the full invoice lifecycle: draft creation (time entries are "selected" but not yet committed), approval (time entries are committed to the invoice), voiding (time entries are released for re-invoicing), and deletion (draft is discarded).

Key requirements:
- A time entry can appear on at most one non-voided invoice.
- When an invoice is voided, its time entries become available for re-invoicing.
- Billed time entries (on an approved/sent/paid invoice) cannot be edited or deleted — the user must void the invoice first.
- The mechanism should work for both dedicated-schema and shared-schema tenants.

**Options Considered**:

1. **`invoice_id` FK on TimeEntry + unique index on InvoiceLine.time_entry_id** — Add an `invoice_id` column to the `time_entries` table, set when the invoice is approved and cleared when voided. Additionally, enforce a unique index on `invoice_lines.time_entry_id` (excluding NULLs) as a database-level constraint.
   - Pros:
     - **Simple query for unbilled time**: `WHERE billable = true AND invoice_id IS NULL`. No joins needed.
     - **Direct FK reference**: From a time entry, you can immediately find the invoice it belongs to (`invoiceId` + `invoiceNumber` in the response).
     - **Edit protection is trivial**: `if (timeEntry.invoiceId != null) throw 409`.
     - **Void is clean**: `UPDATE time_entries SET invoice_id = NULL WHERE invoice_id = :voidedInvoiceId`.
     - **Database constraint**: The unique index on `invoice_lines.time_entry_id` prevents insertion of a second line referencing the same time entry, regardless of application bugs.
     - **Consistent with Phase 8 pattern**: `TimeEntry` already has denormalized fields (`billingRateSnapshot`, `billingRateCurrency`). Adding `invoice_id` follows the same pattern.
   - Cons:
     - Modifies the `time_entries` table (ALTER TABLE ADD COLUMN) — but this is a nullable column with no default, so no table rewrite.
     - The unique index on `invoice_lines.time_entry_id` means voided invoices' line items must have their `time_entry_id` cleared (or the lines must be deleted) before the time entry can be re-invoiced. The application layer handles this during the void flow.
     - Slightly denormalized: the "billed" status is derivable from the existence of a line item on a non-voided invoice, but storing `invoice_id` on the time entry avoids expensive joins.

2. **Junction table (`invoice_time_entries`)** — A separate many-to-many table linking invoices to time entries, with a status column indicating the billing state.
   - Pros:
     - Clean relational design — no modification to the `time_entries` table.
     - Can track history: which invoices a time entry has been on (including voided ones).
     - Supports potential future scenarios like partial billing of a time entry.
   - Cons:
     - **Complexity**: Every unbilled time query requires a LEFT JOIN to the junction table. Every billing status check requires a JOIN.
     - **Performance**: The unbilled time query (used in the invoice generation flow) becomes significantly more expensive with a JOIN vs. a simple column check.
     - **More entities**: A new `InvoiceTimeEntry` entity, repository, and migration. More code to maintain.
     - **Void flow is complex**: Must update the junction table status, not just clear a column.
     - **YAGNI**: Partial billing and billing history are explicitly out of scope. The junction table solves problems we do not have.

3. **Separate `billing_status` enum on TimeEntry** — Add a `billing_status` column (`UNBILLED`, `BILLED`, `INVOICED`) to `time_entries` instead of an `invoice_id` FK. No direct reference back to the invoice.
   - Pros:
     - Simple column addition.
     - Clear semantics: the status enum is self-documenting.
   - Cons:
     - **No invoice reference**: From a time entry, you cannot determine which invoice it belongs to without querying `invoice_lines`. This breaks the "show invoice number on billed time entries" requirement.
     - **Duplicate data**: `billing_status = BILLED` is redundant with `invoice_id IS NOT NULL`. If both are stored, they can drift out of sync.
     - **Void flow requires search**: To find all time entries for a voided invoice, you must query `invoice_lines` by `invoice_id` → get `time_entry_ids` → update `billing_status`. With `invoice_id` on the time entry, you can update directly: `WHERE invoice_id = :id`.
     - **No DB constraint**: Without a FK reference, there is no database-level mechanism to prevent a time entry from being marked as billed by two different invoices.

**Decision**: `invoice_id` FK on TimeEntry + unique index on InvoiceLine.time_entry_id (Option 1).

**Rationale**: The combination of application-layer validation and database constraints provides defense-in-depth:

1. **Application layer** (primary guard): `InvoiceService.createDraft()` validates that all selected time entries have `invoice_id IS NULL` before creating line items. `InvoiceLifecycleService.approve()` re-validates before marking entries as billed. This prevents double-billing in normal usage.

2. **Database layer** (defense-in-depth): The unique index on `invoice_lines.time_entry_id` (WHERE NOT NULL) prevents two line items from referencing the same time entry, even if the application layer has a bug. This catches race conditions where two concurrent draft creations select the same time entries.

The `invoice_id` FK on `TimeEntry` serves dual purposes:
- **Unbilled time query**: `WHERE billable = true AND invoice_id IS NULL` is the fastest possible predicate — no joins.
- **Edit protection**: `if (timeEntry.getInvoiceId() != null) throw 409` — checked in `TimeEntryService.update()` and `delete()`.

When an invoice is voided, the void flow:
1. Sets `Invoice.status = VOID`.
2. Clears `time_entries.invoice_id` for all affected entries: `UPDATE time_entries SET invoice_id = NULL WHERE invoice_id = :invoiceId`.
3. The unique index on `invoice_lines` still references the voided invoice's lines, but since those entries now have `invoice_id = NULL`, they are available for re-invoicing. The next draft creation will create new `InvoiceLine` rows pointing to the same `time_entry_id` — this is allowed because the old lines are on a voided invoice. To make this work cleanly with the unique index, the void flow also clears `time_entry_id` on the voided invoice's lines.

**Consequences**:
- `TimeEntry` gains an `invoice_id` column (V22 migration). Nullable, no default, no backfill.
- `InvoiceLine` has a unique index on `time_entry_id` (WHERE NOT NULL). This is a simple partial unique index that PostgreSQL supports natively.
- The void flow must clear `time_entry_id` on the voided invoice's line items AND clear `invoice_id` on the associated time entries. Both updates happen in the same transaction.
- Billed time entries are effectively immutable: `TimeEntryService.update()` and `delete()` check `isLocked()` and return 409. Users must void the invoice to unlock the entries.
- The `invoiceId` and `invoiceNumber` fields are included in time entry API responses for cross-referencing. `invoiceNumber` is resolved via a join or separate query (not stored on the time entry).
