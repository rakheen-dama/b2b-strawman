# ADR-157: Billing Run as Entity vs Tag

**Status**: Proposed
**Date**: 2026-03-07
**Phase**: 40 (Bulk Billing & Batch Operations)

## Context

DocTeams' invoicing system (Phase 10) supports single-customer invoice creation via `InvoiceService.createDraft()`. Phase 40 introduces bulk billing — the ability to generate, review, approve, and send invoices for many customers in a single billing cycle. This requires a mechanism to group invoices created together and track the batch lifecycle.

The simplest approach would be adding a `batch_id` column to the existing `Invoice` entity. However, bulk billing involves significant workflow state beyond grouping: a preview phase where users select customers and cherry-pick time entries (see [ADR-158](ADR-158-explicit-entry-selection-vs-snapshot.md)), per-customer failure tracking during generation, summary statistics for historical reporting, and the ability to cancel a run (voiding all associated drafts). The question is whether this state justifies a dedicated entity or can be managed with lighter-weight approaches.

In the schema-per-tenant architecture, any new entity lives within the tenant schema and benefits from the same isolation guarantees. The `OrgSettings` entity (Phase 8) already establishes the pattern of tenant-scoped configuration — the billing run extends this with tenant-scoped operational state.

## Options Considered

1. **Tag-based approach** — Add a `batch_id` UUID column on `Invoice`. Group invoices by `batch_id` for display. No separate entity.
   - Pros: Zero new tables. Minimal migration. Simple to query ("all invoices with this batch_id")
   - Cons: No lifecycle management — cannot track PREVIEW/IN_PROGRESS/COMPLETED/CANCELLED states. Preview and selection state (which customers, which entries) has nowhere to live. Per-customer failure reasons during generation are lost. Summary statistics must be computed on every query rather than stored. Cannot resume an interrupted run. No audit trail for the billing run itself
   - Cons: Cancelling a batch requires finding all invoices by `batch_id` and voiding them with no record of why

2. **Dedicated BillingRun entity (chosen)** — Full entity with lifecycle status, `BillingRunItem` for per-customer tracking, `BillingRunEntrySelection` for cherry-pick state, and stored summary statistics.
   - Pros: Full lifecycle management (PREVIEW -> IN_PROGRESS -> COMPLETED, or CANCELLED with cascading void). Preview and selection state persists server-side — closing the browser doesn't lose work. Per-customer failure tracking via `BillingRunItem.failureReason`. Historical reporting with pre-computed summary stats (total invoices, total amount, sent count). Audit events can reference the `BillingRun` entity directly. Supports resuming interrupted runs
   - Cons: Three new tables (`billing_runs`, `billing_run_items`, `billing_run_entry_selections`). More complex migration. Additional repository and service classes

3. **Hybrid — lightweight batch record + tag** — A minimal batch metadata record (name, dates, status) plus `batch_id` on invoices, but no per-customer item tracking or entry selection tables.
   - Pros: Simpler than the full entity approach — one new table instead of three. Provides basic lifecycle (status field) and grouping
   - Cons: Loses per-customer failure tracking — a failed generation for one customer is not recorded anywhere. No entry selection persistence — cherry-pick choices are ephemeral (client-side only). Preview totals must be recomputed from live data each time. Cannot determine which entries were intentionally included vs which happened to be unbilled at generation time

## Decision

Use **dedicated BillingRun entity** (Option 2). The billing run workflow involves substantial state — customer selection, entry cherry-picking, per-customer generation results, and batch-level summary statistics — that requires structured persistence.

## Rationale

A billing run is not merely a grouping of invoices — it is an operational workflow with distinct phases: configure, preview, select, generate, review, approve, send. Each phase produces state that subsequent phases depend on. The preview phase creates `BillingRunItem` records with unbilled totals. The cherry-pick phase creates `BillingRunEntrySelection` records tracking explicit user choices (see [ADR-158](ADR-158-explicit-entry-selection-vs-snapshot.md)). The generation phase updates item statuses and captures failures. This state cannot live on the `Invoice` entity because it exists before any invoices are created.

The existing codebase follows a consistent pattern: entities with lifecycle states get dedicated tables (`Invoice` with `InvoiceStatus`, `Customer` with `CustomerStatus`, `RetainerAgreement` with agreement status). A `BillingRun` with its own status lifecycle fits this pattern naturally. The `BillingRunService` orchestrates the workflow by delegating to existing `InvoiceService.createDraft()` for individual invoice generation — it doesn't duplicate invoice logic, it coordinates it.

Historical reporting is a key driver. Professional services firms bill monthly and need month-over-month comparison: "How does this March billing run compare to February?" Pre-computed summary stats (`totalInvoices`, `totalAmount`, `totalSent`) on the `BillingRun` entity make this query trivial. With a tag-based approach, these statistics require aggregation queries across all invoices every time.

## Consequences

- **Positive**: Full lifecycle tracking enables cancel-with-void — cancelling a run automatically voids all DRAFT invoices it created, with a clear audit trail
- **Positive**: Preview state persists server-side, so closing the browser mid-wizard doesn't lose customer selections or entry cherry-picks
- **Positive**: Per-customer failure tracking (`BillingRunItem.failureReason`) allows firms to see exactly which customers failed prerequisite checks and why, without re-running the batch
- **Positive**: Historical billing run records enable month-over-month reporting and billing cycle analysis
- **Negative**: Three new tables and associated repository/service classes increase schema complexity
- **Negative**: The `BillingRunEntrySelection` table can grow large for firms with many time entries per customer — mitigated by cleaning up selection records after generation completes
- **Neutral**: The `Invoice` entity gains an optional `billingRunId` foreign key for reverse lookup, but this is informational only and doesn't change existing invoice behavior
