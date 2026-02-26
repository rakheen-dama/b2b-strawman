# ADR-103: Tax Rate Immutability on Finalized Invoices

**Status**: Accepted

**Context**:

Phase 26 stores denormalized tax rate data on each `InvoiceLine`: `taxRateName`, `taxRatePercent`, `taxExempt`, and a computed `taxAmount`. These snapshots ensure that finalized invoices (APPROVED, SENT, PAID, VOID) are self-contained legal documents, independent of the current state of the `TaxRate` entity. The question is: when exactly should the snapshot be taken?

The timing matters because:
- A DRAFT invoice may be edited over several days or weeks. During that time, the org might rename a tax rate, change its percentage, or deactivate it.
- If the snapshot is taken at line creation time and never refreshed, a DRAFT invoice could show a stale rate name or percentage — the user would see "VAT 15%" on their draft even though the org has changed the rate to 17%.
- If the snapshot is deferred to approval time, draft invoices have no snapshot at all, which means the line display depends on a live lookup of the `TaxRate` entity — adding complexity and fragility (what if the rate is deleted before approval?).
- The invoice preview (HTML and PDF) reads from the line-level snapshot fields. If these are empty on DRAFT, the preview must fall back to a live lookup, creating a dual-path rendering logic.

**Options Considered**:

1. **Snapshot at line creation/edit time** -- When a line gets a `taxRateId` (either via manual assignment or auto-default on generation), immediately copy `name`, `rate`, `isExempt` from the `TaxRate` entity to the line's snapshot fields. Calculate `taxAmount`. Never update the snapshot after that, even for DRAFT invoices.
   - Pros:
     - Simple: one snapshot point, one code path.
     - Line always has complete data for display, preview, and PDF — no live lookups needed.
     - Matches the time-entry rate snapshot pattern in the codebase.
   - Cons:
     - Stale snapshots on DRAFTs: if the org changes a rate from 15% to 17%, existing DRAFT lines still show 15%. The user must manually edit each line to pick up the new rate.
     - No automatic propagation of rate changes to drafts. Users might submit drafts with outdated tax rates without realizing it.

2. **Snapshot at invoice approval time** -- Store only `taxRateId` on lines until the invoice is approved. At APPROVED transition, snapshot all rate fields onto every line. DRAFT lines use live lookups for display.
   - Pros:
     - DRAFT invoices always reflect the latest rate definitions.
     - Snapshot happens at the moment the invoice becomes a legal document — semantically correct.
   - Cons:
     - DRAFT lines have no snapshot data. Preview, PDF, and response DTOs must do live lookups for DRAFT invoices, adding a second code path for rendering.
     - If a rate is deactivated or deleted between line creation and approval, the approval-time lookup may fail. Must handle: "rate not found" at approval time.
     - Approval becomes a heavier operation: must load all tax rates, snapshot all lines, recalculate all taxes.
     - The `taxAmount` field on DRAFT lines would be either null (requiring live calculation) or computed but not snapshotted (inconsistent).

3. **Snapshot at creation + auto-refresh on DRAFT (chosen)** -- Snapshot immediately when a line gets a `taxRateId` (same as Option 1). Additionally, when a tax rate's percentage or name changes, batch-refresh all DRAFT invoice lines that reference it: re-copy `name`, `rate`, `isExempt`, recalculate `taxAmount`, and recalculate parent invoice totals. APPROVED/SENT/PAID/VOID invoices are never refreshed. Invoice approval locks the snapshots permanently.
   - Pros:
     - Lines always have complete snapshot data — single rendering path for all statuses.
     - DRAFT invoices automatically pick up rate changes without user intervention.
     - Finalized invoices are permanently immutable.
     - Clear boundary: DRAFT = mutable, APPROVED+ = immutable. Matches the existing invoice lifecycle semantics.
     - Rate deactivation is safe: DRAFT lines are actively checked before deactivation (409 if in use), and the batch-refresh ensures snapshots are current.
   - Cons:
     - Slightly more complex than Option 1: `TaxRateService.updateTaxRate()` must include a batch-refresh step.
     - The batch-refresh query touches all DRAFT lines for the updated rate across the tenant. For typical usage (< 100 draft invoices), this is sub-millisecond.
     - If a rate is renamed and its percentage changes in the same update, both are refreshed — the user sees the change immediately on their next draft view.

**Decision**: Option 3 -- Snapshot at creation + auto-refresh on DRAFT.

**Rationale**:

Option 2 introduces a dual-path rendering problem: DRAFT invoices need live tax rate lookups while finalized invoices read from snapshots. This affects the preview template (which must conditionally look up rates), the response DTO builder (which must conditionally resolve rate names), and the PDF generation pipeline. The complexity is not worth the benefit when Option 3 achieves the same outcome (drafts reflect current rates) without a dual path.

Option 1 is simpler but creates a real UX problem: a user changes a tax rate from 15% to 17%, then views their existing draft invoices expecting to see the new rate, and instead sees the old one on every line. They must edit each line individually to trigger a re-snapshot. For an invoice with 20+ lines, this is tedious and error-prone. The batch-refresh in Option 3 eliminates this friction.

Option 3 provides the best of both worlds: immediate snapshots (single rendering path, always-complete line data) plus automatic propagation of rate changes to drafts (good UX, no stale data). The batch-refresh is implemented as a simple query + update in `TaxRateService.updateTaxRate()`:

```java
// In TaxRateService.updateTaxRate()
if (ratePercentChanged || nameChanged || exemptChanged) {
    List<InvoiceLine> draftLines = invoiceLineRepository
        .findByTaxRateIdAndInvoice_Status(taxRate.getId(), InvoiceStatus.DRAFT);
    for (InvoiceLine line : draftLines) {
        line.refreshTaxSnapshot(taxRate);
        taxCalculationService.recalculateLineTax(line, orgSettings.isTaxInclusive());
    }
    // Recalculate parent invoice totals for affected invoices
    Set<UUID> affectedInvoiceIds = draftLines.stream()
        .map(InvoiceLine::getInvoiceId).collect(Collectors.toSet());
    for (UUID invoiceId : affectedInvoiceIds) {
        invoiceService.recalculateInvoiceTotals(invoiceId);
    }
}
```

The batch refresh runs in the same transaction as the rate update, ensuring consistency.

**Consequences**:

- Positive:
  - Single rendering path: all invoice lines (DRAFT through VOID) have complete snapshot data. Preview, PDF, portal, and response DTOs read from line fields only — no live lookups needed.
  - DRAFT invoices automatically reflect rate changes. Users see current rates without manual per-line edits.
  - Finalized invoices are permanently immutable. Rate renames, percentage changes, and deactivations do not affect them.
  - Deactivation guard is strengthforward: check for DRAFT lines referencing the rate. If none exist (or all have been refreshed to a different rate), deactivation succeeds.

- Negative:
  - `TaxRateService.updateTaxRate()` has a side effect: batch-refreshing DRAFT lines. This must be documented and tested.
  - If an org has many DRAFT invoices with many lines referencing a rate, the batch refresh could be slow. For realistic volumes (< 1000 lines), this is sub-second. No pagination needed for v1.
  - The batch refresh creates a coupling between `TaxRateService` and `InvoiceLineRepository`. This is acceptable — tax rates and invoice lines are inherently coupled.

- Neutral:
  - The snapshot fields are always populated when `taxRateId` is non-null. Code can safely read `taxRateName`, `taxRatePercent`, `taxExempt` without null checks (except for legacy lines where all tax fields are null).
  - Adding a new snapshot field in the future (e.g., `taxRateCode` for reporting) follows the same pattern: snapshot on creation, batch-refresh on rate update for DRAFTs.

- Related: [ADR-101](ADR-101-tax-calculation-strategy.md) (calculation and storage strategy), [ADR-102](ADR-102-tax-inclusive-total-display.md) (total display in inclusive mode), [ADR-049](ADR-049-line-item-granularity.md) (line item structure).
