# ADR-158: Explicit Entry Selection vs Snapshot

**Status**: Proposed
**Date**: 2026-03-07
**Phase**: 40 (Bulk Billing & Batch Operations)

## Context

The bulk billing wizard (Phase 40) includes a cherry-pick step where users review unbilled time entries and expenses per customer and choose which ones to include in the billing run. Between the moment a user previews unbilled work and the moment they click "Generate Invoices," the underlying data can change: new time entries may be logged, existing entries may be edited or deleted, and expenses may be added or removed. The system needs a strategy for handling this temporal gap.

This is particularly important in a multi-user professional services firm. While the billing administrator is reviewing the preview (which may take 30 minutes to an hour for a large client base), other team members continue logging time. The billing run must produce deterministic results — the invoices generated should reflect exactly what the administrator reviewed and approved, not whatever happens to be unbilled at the moment of generation.

The existing `InvoiceService.createDraft()` accepts explicit `timeEntryIds` — it already supports generating an invoice from a specific set of entries rather than "all unbilled." The question is how the billing run tracks which entries the user selected during preview so they can be passed to `createDraft()` at generation time.

## Options Considered

1. **Snapshot approach** — Copy all unbilled entry IDs into a JSONB column on `BillingRunItem` at preview time. At generation, use the snapshot as the entry list.
   - Pros: Simple storage — one JSONB column per customer item. No additional table. Fast to read (single column)
   - Cons: Snapshot goes stale if entries are edited or deleted between preview and generate — generating from a stale snapshot could reference deleted entries or miss price corrections. No mechanism to toggle individual entries on/off without replacing the entire snapshot. If the user wants to exclude 2 out of 50 entries, the entire snapshot must be rewritten. Cannot track which entries the user explicitly reviewed vs which were auto-included
   - Cons: JSONB column with hundreds of UUIDs per customer becomes unwieldy for firms with high time entry volume

2. **Explicit selection tracking (chosen)** — `BillingRunEntrySelection` records track which entries are included or excluded. Each record links a `BillingRunItem` to a specific time entry or expense with an `included` boolean. Default is all included; user toggles persist as individual records.
   - Pros: Deterministic — exactly the entries the user reviewed and approved are what get billed. Survives time entry changes between preview and generate (new entries logged after preview don't sneak in because they have no selection record). Individual entry toggling is a simple `UPDATE` on one record. Supports audit trail of user's billing decisions. Works naturally with the existing `InvoiceService.createDraft(customerId, timeEntryIds)` interface
   - Cons: Additional table (`billing_run_entry_selections`) with potentially many rows — one per unbilled entry per customer. For a firm with 40 customers averaging 100 unbilled entries each, that's 4,000 selection records per billing run. Requires cleanup after generation completes

3. **No selection tracking** — Always bill all unbilled entries at generation time. No cherry-pick capability.
   - Pros: Simplest implementation — no selection state to manage. Generation queries unbilled entries fresh, guaranteeing up-to-date data
   - Cons: Eliminates the core cherry-pick feature that differentiates bulk billing from running single-invoice creation 40 times. Firms frequently need to hold back specific entries (disputed hours, incomplete work, entries pending manager review). Without cherry-pick, the billing run is just a batch loop with no user control over content
   - Cons: Non-deterministic — the set of entries billed depends on the exact moment of generation, not on what the user reviewed

## Decision

Use **explicit selection tracking** (Option 2). `BillingRunEntrySelection` records provide deterministic, auditable billing decisions that survive the temporal gap between preview and generation.

## Rationale

Determinism is the primary driver. In a billing context, "I reviewed these entries and approved them for billing" must produce exactly those entries on the invoice, regardless of what other team members do between preview and generation. The snapshot approach (Option 1) appears to offer this, but breaks down when entries are deleted or amounts are corrected — the snapshot references stale data. Explicit selection tracking references live entries by ID, so amount corrections are automatically reflected while the set of entries remains fixed.

The `BillingRunEntrySelection` table also enables the cherry-pick UX naturally. Each entry in the preview has a checkbox backed by a selection record. Toggling the checkbox updates a single row. The frontend can query selections to restore checkbox state if the user navigates away and returns — the wizard state persists server-side, consistent with the `BillingRun` entity's role as persistent workflow state ([ADR-157](ADR-157-billing-run-entity-vs-tag.md)).

At generation time, the service queries `BillingRunEntrySelection` records where `included = true` for each customer, collects the `entryId` values, and passes them directly to `InvoiceService.createDraft()`. This reuses the existing single-invoice creation path entirely — the billing run adds selection tracking on top without modifying invoice generation logic.

The row count concern (potentially thousands of selection records per run) is manageable. These records are write-once-read-once — created during preview, read during generation, then inert. A cleanup job can delete selection records for completed runs after a configurable retention period. The `UNIQUE(billingRunItemId, entryType, entryId)` constraint prevents duplicates, and the `billingRunItemId` foreign key enables efficient per-customer queries.

## Consequences

- **Positive**: Billing results are deterministic — invoices contain exactly the entries the administrator reviewed and approved, regardless of concurrent time logging by other team members
- **Positive**: Cherry-pick state persists server-side, surviving browser refreshes and session interruptions
- **Positive**: Individual entry toggling is efficient (single-row UPDATE) and supports the checkbox UX pattern
- **Positive**: Integrates cleanly with existing `InvoiceService.createDraft(customerId, timeEntryIds)` — no changes to the invoice creation path
- **Negative**: The `billing_run_entry_selections` table can accumulate significant row counts for large firms — mitigated by post-generation cleanup
- **Negative**: Creating selection records during preview adds latency — for a customer with 200 unbilled entries, 200 INSERT operations are needed. Batch INSERT mitigates this
- **Neutral**: New entries logged after preview are not automatically included. The administrator must refresh the preview to see them. This is intentional — it prevents surprise line items on invoices
