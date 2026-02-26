# ADR-101: Tax Calculation Strategy

**Status**: Accepted

**Context**:

Phase 26 adds per-line-item tax to invoices. Each invoice line can have a tax rate applied, and the invoice displays a tax breakdown by rate. The fundamental question is: when is tax calculated and where is it stored? The answer affects performance (read-time vs. write-time computation), auditability (can we prove what tax was on a finalized invoice?), and correctness (what happens when a tax rate percentage changes?).

Key constraints:
- Invoices are legal documents. Once approved (APPROVED, SENT, PAID), their tax amounts must be immutable regardless of subsequent rate changes.
- DRAFT invoices are editable. Users expect drafts to reflect the latest rate definitions.
- The system already stores denormalized snapshots on invoice lines for other purposes (time entry rate snapshots on billing, customer name snapshots on invoices).
- Invoice preview, PDF generation, and portal display all need tax amounts. Computing on every read would add latency to these hot paths.
- The existing `Invoice.taxAmount` field is a flat manual value. Backward compatibility requires that legacy invoices (no per-line tax) continue to work.

**Options Considered**:

1. **Calculate on save, store per-line (denormalized) (chosen)** -- When a line is created or edited, calculate `taxAmount = f(amount, ratePercent, taxInclusive)` and store it on the `InvoiceLine` row alongside denormalized `taxRateName`, `taxRatePercent`, and `taxExempt` fields. The invoice-level `taxAmount` is the sum of line-level tax amounts. When a tax rate percentage is changed, batch-recalculate all DRAFT invoice lines referencing that rate; leave APPROVED/SENT/PAID lines untouched.
   - Pros:
     - Fast reads: tax amounts are pre-computed. Preview, PDF, and portal display require no additional calculation.
     - Audit trail: the denormalized fields on each line are the invoice's permanent record of what tax was applied, at what rate, with what name.
     - Immutability on finalized invoices is automatic: no recalculation runs against non-DRAFT invoices.
     - Consistent with existing patterns: billing rates are snapshotted on time entries, customer names are snapshotted on invoices.
     - Simple backward compatibility: lines without `taxRateId` have null tax fields; the invoice falls back to its manual `taxAmount`.
   - Cons:
     - Storage overhead: 5 additional columns per invoice line. Negligible for the expected data volumes.
     - Must remember to recalculate when a line's amount changes (quantity or unit price edit). But `InvoiceLine.recalculateAmount()` already exists — this is a natural extension.
     - DRAFT invoices need batch recalculation when a tax rate changes. This is an explicit operation in `TaxRateService.updateTaxRate()`.

2. **Calculate on read from rate definitions (normalized)** -- Store only `taxRateId` on each line. When rendering an invoice (preview, response, PDF, portal), look up the current `TaxRate` and compute tax amounts dynamically. No denormalized fields.
   - Pros:
     - Minimal storage: one FK column per line.
     - Rates always "current": changing a rate percentage immediately affects all invoices (drafts and finalized).
   - Cons:
     - **Critical flaw**: Changing a rate silently alters historical invoices. A 15% → 20% rate change would retroactively change the tax on paid invoices, violating tax law and audit requirements.
     - Performance overhead: every invoice read (preview, PDF, portal, list) requires joining or loading tax rates.
     - No audit trail: the invoice itself doesn't record what rate was applied. If a rate is deleted, historical invoices lose their tax information entirely.
     - Backward compatibility is harder: must detect "no taxRateId" vs. "taxRateId points to deleted rate" vs. "legacy manual tax".

3. **Hybrid: calculate on save for finalized, on read for drafts** -- DRAFT invoices always recalculate tax dynamically from current rate definitions. When an invoice transitions to APPROVED, snapshot all values (denormalize) and lock them.
   - Pros:
     - Drafts always reflect the latest rates without explicit batch recalculation.
     - Finalized invoices have the same immutability guarantees as Option 1.
   - Cons:
     - Two different calculation paths: dynamic for drafts, static for finalized. Every display path (preview, response, PDF) must check invoice status to decide which path to use.
     - Approval becomes a heavier operation: must snapshot every line's tax data at approval time.
     - Confusing UX: if a user views a draft invoice, leaves, and the rate changes, the next view shows different tax amounts with no user action. The user may not understand why the tax changed.
     - If a tax rate is deactivated or deleted between draft creation and approval, the approval-time snapshot may fail or produce unexpected results.
     - Complexity outweighs benefit: the batch-recalculation approach in Option 1 achieves the same goal (drafts reflect current rates) with a single, consistent calculation path.

**Decision**: Option 1 -- Calculate on save, store per-line.

> **Cross-reference note**: Option 1 above includes batch-refresh behavior for DRAFT invoices (recalculate when a rate changes). This is the "snapshot at creation + auto-refresh on DRAFT" strategy detailed in [ADR-103](ADR-103-tax-rate-immutability.md). ADR-101 focuses on the **storage strategy** (denormalized vs. normalized); ADR-103 focuses on **snapshot timing** (when snapshots are taken and refreshed).

**Rationale**:

Option 2 is disqualified because it violates the fundamental principle that finalized invoices are immutable legal documents. Retroactively changing tax on paid invoices is not acceptable.

Option 3 solves a problem that Option 1 already handles well. The "drafts should reflect current rates" requirement is met by batch recalculation in `TaxRateService.updateTaxRate()`: when a rate percentage changes, the service finds all DRAFT invoice lines with that `taxRateId`, re-snapshots the rate fields, recalculates `taxAmount`, and recalculates parent invoice totals. This is a single, well-defined operation that runs in the same transaction as the rate update. It avoids the dual-path complexity of Option 3.

The denormalized approach aligns with established patterns in the codebase: `TimeEntry` snapshots billing rate values, `Invoice` snapshots customer name and email, `InvoiceLine` snapshots project references. Adding tax rate snapshots is consistent and expected.

**DRAFT recalculation policy**: When a tax rate percentage is changed, all DRAFT invoice lines referencing that rate are recalculated. APPROVED, SENT, PAID, and VOID invoices are never recalculated. This policy is documented in the Phase 26 architecture and enforced in `TaxRateService.updateTaxRate()`.

**Consequences**:

- Positive:
  - Invoice tax amounts are pre-computed and fast to read. No calculation overhead on preview, PDF, portal, or list endpoints.
  - Finalized invoices are permanently immutable: rate changes, renames, and deactivations do not affect them.
  - Clear audit trail: each line records exactly what rate name, percentage, and exempt flag was applied.
  - Consistent with existing snapshot patterns in the codebase.

- Negative:
  - 5 additional nullable columns on `invoice_lines`. Storage overhead is negligible (< 100 bytes per line).
  - `TaxRateService.updateTaxRate()` must perform a batch query and update when the rate percentage changes. For typical usage (< 100 draft invoices per org), this completes in milliseconds.
  - Developers must remember to call tax calculation when creating or modifying lines. This is mitigated by centralizing all tax logic in `TaxCalculationService` and calling it from well-defined points in `InvoiceService`.

- Related: [ADR-103](ADR-103-tax-rate-immutability.md) (snapshot timing), [ADR-102](ADR-102-tax-inclusive-total-display.md) (total display), [ADR-049](ADR-049-line-item-granularity.md) (line item structure).
