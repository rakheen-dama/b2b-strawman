# ADR-049: Line Item Granularity — One Line Per Time Entry

**Status**: Accepted

**Context**: When generating an invoice from unbilled time entries, the system must decide how to map time entries to invoice line items. A customer may have dozens or hundreds of time entries across multiple projects over a billing period. The granularity choice affects invoice readability, audit traceability, and the complexity of the invoice generation and editing flows.

The existing time entry model captures: member, task, project, date, duration, billing rate snapshot, and description. Each entry represents a discrete work session. The invoice must group this data in a way that is useful to the customer (who wants to understand what they are paying for) and to the organization (who needs an audit trail from invoice back to time logs).

**Options Considered**:

1. **One line per time entry** — Each time entry becomes its own invoice line item. `time_entry_id` is set on the line, creating a 1:1 mapping. Lines are grouped by project for display, with per-project subtotals.
   - Pros:
     - **Maximum transparency**: The customer can see every individual work session. This builds trust, especially for hourly billing engagements.
     - **Full audit trail**: Every line traces back to a specific time entry. Disputes can be resolved by examining the exact time log.
     - **Simple generation logic**: No aggregation — each entry maps directly to a line.
     - **Simple voiding**: Voiding an invoice reverts each time entry individually. No "how do I split this aggregated line back into entries?" problem.
     - **Editable**: Users can remove individual time entries from a draft invoice (e.g., "this entry was a mistake, don't bill it"). This is trivial when each line maps to one entry.
   - Cons:
     - **Verbose invoices**: A billing period with 200 time entries produces 200 line items. This may be overwhelming for the customer.
     - **Large HTML/PDF**: The invoice preview may span many pages for high-volume billing periods.
     - **Performance**: More rows in `invoice_lines` per invoice. Pagination or lazy loading may be needed for very large invoices.

2. **Aggregated lines per project per member** — Group time entries by (project, member) and produce one line per combination: "Development — John Doe — 40 hrs @ $150/hr".
   - Pros:
     - Compact invoices — a typical invoice has 3-10 lines instead of 50-200.
     - Easier for the customer to scan at a glance.
     - Smaller HTML output.
   - Cons:
     - **Lost audit trail at the line level**: The line says "40 hrs" but the customer cannot see which days, which tasks, or which sessions. Disputes require digging into time logs separately.
     - **Complex generation**: Must aggregate duration, handle mixed rates (if rates changed mid-period), and compute weighted averages or split into sub-lines.
     - **Complex voiding**: Voiding means re-disaggregating the line back into individual time entries. Which entries are the "40 hrs"? A junction table is needed.
     - **Mixed rates**: If a member's rate changed mid-period, a single aggregated line cannot accurately represent "20 hrs @ $150 + 20 hrs @ $175". Must split into multiple lines, which partially defeats the aggregation.
     - **Editing friction**: Removing one time entry from an aggregated line means recomputing the aggregation. The user cannot simply delete a line — they must "open" the aggregation.

3. **Aggregated lines per project (no member split)** — One line per project: "Website Redesign — 120 hrs @ $150/hr". Maximum compression.
   - Pros:
     - Most compact possible invoice — 1-3 lines for a typical billing period.
     - Simplest customer experience for fixed-rate, single-project engagements.
   - Cons:
     - All cons from Option 2, amplified.
     - Loses member attribution entirely — cannot tell who worked on what.
     - Mixed rates across members make the "rate" column meaningless (must use blended average).
     - For multi-member projects with different rates, the aggregation produces incorrect math unless using weighted average rates, which are confusing to customers.
     - Voiding and editing are even more complex than Option 2.

**Decision**: One line per time entry (Option 1).

**Rationale**: The primary purpose of an invoice in a professional services context is to justify the charges. Transparency — showing exactly what work was done, when, by whom, and at what rate — is the strongest justification. Customers who receive detailed invoices are less likely to dispute charges because they can verify each entry against their understanding of the work.

The verbosity concern is mitigated by three design choices:
1. **Project grouping**: Lines are grouped by project with per-project subtotals. A 200-line invoice is actually 5 project sections of 40 lines each — much more scannable.
2. **Auto-generated descriptions**: Each line has a meaningful description (`"{task.title} — {member.name} — {date}"`) rather than raw IDs.
3. **Manual line items**: Users can add aggregated manual lines ("Fixed consulting fee — $5,000") alongside time-based lines. If a user wants a compact invoice, they can delete the time-based lines and add a single manual summary line.

The 1:1 mapping also simplifies the double-billing prevention mechanism ([ADR-050](ADR-050-double-billing-prevention.md)): a unique index on `time_entry_id` in `invoice_lines` prevents the same entry from appearing on two invoices. With aggregated lines, this constraint would require a junction table.

**Consequences**:
- `InvoiceLine` has a nullable `time_entry_id` FK. Time-based lines set it; manual lines leave it null.
- A unique index on `time_entry_id` (where not null) prevents double-billing at the database level.
- Large invoices (100+ lines) may require pagination in the frontend detail view, or a scrollable line items table.
- The HTML preview template must handle multi-page printing gracefully (CSS page breaks between project groups).
- Users who prefer aggregated invoices can delete time-based lines and add manual summary lines. This is a manual process in v1; future enhancement: "Summarize by project" button that replaces time-based lines with aggregated lines.
- Invoice generation is a simple loop over time entries — no aggregation logic, no rate blending, no weighted averages.
