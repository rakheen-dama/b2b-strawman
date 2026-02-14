# ADR-049: Line Item Granularity

**Status**: Accepted

**Context**: When generating an invoice from unbilled time entries, the system must decide how to map time entries to invoice line items. A customer might have 50 unbilled time entries across 3 projects over a month. The line item granularity determines what appears on the printed invoice, how easy it is to audit, and how verbose the invoice becomes.

Key constraints:
- Invoices must be verifiable: clients should be able to cross-reference line items against the work they approved.
- Time entries have snapshotted billing rates (`billing_rate_snapshot`) that may differ even within the same project (different team members have different rates).
- The invoice preview groups line items by project, so visual readability is handled at the presentation layer.
- The system must support a mix of auto-generated (from time entries) and manual line items (fixed fees, adjustments, discounts).
- Double-billing prevention requires tracking which time entries have been invoiced. Granularity affects this tracking mechanism.

**Options Considered**:

1. **One line item per time entry** -- Each time entry becomes its own InvoiceLine row with a direct FK to the time entry.
   - Pros:
     - Maximum transparency: every tracked hour is individually visible on the invoice.
     - Clean double-billing prevention: the InvoiceLine → TimeEntry FK is a 1:1 mapping, enforceable via a partial unique index.
     - Easy audit trail: any line item can be traced to the exact time entry (who, when, what task, how long).
     - No information loss during invoice generation -- the full granularity is preserved.
     - Simple generation logic: iterate time entries, create one line per entry, no aggregation needed.
     - Voiding is straightforward: each line references exactly one time entry to revert.
   - Cons:
     - Verbose invoices: 50 time entries = 50 line items. For high-volume projects, invoices can be long.
     - More database rows per invoice (proportional to number of time entries).
     - Clients accustomed to summary-style invoices may find the detail level overwhelming.

2. **Aggregated by project + member + rate** -- Group time entries by (project, member, billing rate) and produce one summary line per group.
   - Pros:
     - Compact invoices: 50 time entries might collapse to 5-8 lines.
     - Closer to traditional consulting invoice formats.
   - Cons:
     - Information loss: individual time entry dates, tasks, and descriptions are not visible on the invoice.
     - Double-billing prevention becomes complex: the junction between an InvoiceLine and its source time entries is a many-to-many relationship, requiring a separate `invoice_line_time_entries` join table.
     - Voiding requires traversing the join table to find and revert all source time entries.
     - If a client disputes a specific line, the staff member must look up the underlying time entries separately.
     - Aggregation logic is more complex (grouping, summing, handling edge cases like mid-period rate changes).

3. **Aggregated by project only** -- One line per project with total hours and a blended rate.
   - Pros:
     - Most compact invoice format.
     - Simple for fixed-price projects where granularity doesn't matter.
   - Cons:
     - Blended rates are misleading when team members have different billing rates.
     - Loses all ability to audit individual time entries from the invoice.
     - Still requires a join table for time entry tracking.
     - Not suitable for time-and-materials billing where per-hour accountability is expected.
     - Cannot accommodate different rates within the same project without distortion.

**Decision**: One line item per time entry (Option 1).

**Rationale**: The 1:1 mapping between time entries and invoice lines provides the strongest audit trail and the simplest double-billing prevention mechanism. In professional services, invoice disputes almost always drill down to "what work was done on which day" -- and the per-entry granularity answers this without any secondary lookup. The verbosity concern is mitigated at two levels:

1. **Invoice preview grouping**: The HTML invoice template groups line items by project with per-project subtotals, so the visual presentation is organized regardless of granularity. A 50-line invoice reads as "Project A: 20 entries, subtotal $X / Project B: 30 entries, subtotal $Y" rather than a flat list.

2. **Description auto-generation**: Each line's `description` field is auto-populated from time entry metadata (task title, date, member name), producing readable descriptions like "Backend API development -- 2025-01-15 -- Jane Smith (2.5 hrs)". Users can edit descriptions on the draft before approving.

The direct FK on InvoiceLine (`time_entry_id UUID REFERENCES time_entries`) enables a partial unique index (`WHERE invoice.status != 'VOID'`) that enforces double-billing prevention at the database level -- the strongest possible guarantee.

Future enhancement: if clients request summary invoices, a "compact view" can be added to the invoice preview template (grouping lines by project+rate for display purposes) without changing the underlying data model. The granular data is always preserved.

**Consequences**:
- `InvoiceLine.time_entry_id` is a nullable UUID FK. Null for manual line items; non-null for auto-generated lines from time entries.
- A partial unique index on `invoice_lines(time_entry_id)` excludes voided invoices, preventing the same time entry from appearing on two active invoices.
- Invoice generation creates one `InvoiceLine` per selected time entry. The `quantity` field holds the time entry's duration in hours (decimal), and `unit_price` holds the `billing_rate_snapshot`.
- The `description` field is auto-generated but editable on drafts. Format: "{task title} -- {date} -- {member name}".
- Invoice preview template groups lines by `project_id` and shows per-project subtotals for readability.
- Large invoices (100+ lines) are acceptable because the grouping structure keeps them navigable. If needed, a future "summary mode" rendering can aggregate lines visually without changing the data model.
