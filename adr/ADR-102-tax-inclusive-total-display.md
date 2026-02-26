# ADR-102: Tax-Inclusive Total Display

**Status**: Accepted

**Context**:

Phase 26 introduces a `taxInclusive` toggle on `OrgSettings`. When enabled, line item prices (`unitPrice`, `amount`) include tax, and the tax portion is extracted and displayed separately. The question is: how do the `Invoice` fields (`subtotal`, `taxAmount`, `total`) relate to each other in tax-inclusive mode, and how are totals presented to the user?

This decision has cascading impact: it affects every place that displays invoice totals (invoice editor, preview, PDF, portal, reports, profitability calculations) and determines whether the existing formula `total = subtotal + taxAmount` continues to hold.

Key observations:
- In **tax-exclusive** mode (the default and current behavior): `subtotal` = sum of line amounts (pre-tax), `taxAmount` = sum of line taxes, `total = subtotal + taxAmount`. This is the current formula and works correctly.
- In **tax-inclusive** mode: line amounts already include tax. If `subtotal` = sum of line amounts (inclusive), then `subtotal` already equals the final amount the customer pays. Adding `taxAmount` to it would double-count the tax.
- **Xero's approach** (the dominant accounting tool in SA, AU, NZ): In tax-inclusive mode, Xero always displays three lines: "Subtotal" (sum of inclusive line amounts), "Includes {Tax}" (extracted tax, informational), "Total" (same as subtotal). The `total` is never `subtotal + tax` in inclusive mode. Some reports show "Subtotal ex-tax" which is `subtotal - taxAmount`.
- **Backward compatibility**: Existing invoices have `total = subtotal + taxAmount`. No existing invoices use per-line tax (it doesn't exist yet). So "backward compatibility" means: legacy invoices (no per-line tax, manual `taxAmount`) must continue to compute `total = subtotal + taxAmount` regardless of org's `taxInclusive` setting. This is safe because legacy invoices predate the tax-inclusive feature.

**Options Considered**:

1. **Xero model: subtotal = inclusive sum, total = subtotal, taxAmount = extracted (chosen)** -- In tax-inclusive mode, `subtotal` stores the sum of line amounts (which include tax). `taxAmount` stores the sum of extracted line tax amounts (informational, for display). `total = subtotal` (not `subtotal + taxAmount`). Display shows: "Subtotal: R11,500", "Includes VAT: R1,500", "Total: R11,500". The formula `total = subtotal + taxAmount` is only used in tax-exclusive mode and for legacy invoices.
   - Pros:
     - Matches Xero, the most common accounting tool for SA businesses. Users familiar with Xero will find the layout intuitive.
     - `total` always represents the amount the customer pays. In exclusive mode: subtotal + tax. In inclusive mode: subtotal (which already includes tax). Conceptually clean.
     - No new columns needed. `subtotal`, `taxAmount`, `total` are reinterpreted based on context.
     - The tax breakdown section makes it clear what the tax component is. "Includes VAT: R1,500" is unambiguous.
   - Cons:
     - The formula `total = subtotal + taxAmount` no longer universally holds. Code that assumes this formula must be guarded by a `taxInclusive` check.
     - `subtotal` means different things in different modes: pre-tax sum (exclusive) vs. post-tax sum (inclusive). This can confuse developers.
     - Reports and profitability calculations that use `subtotal` as "revenue before tax" must adjust for inclusive mode: `revenueExTax = taxInclusive ? subtotal - taxAmount : subtotal`.

2. **Separate ex-tax and inc-tax subtotals** -- Add `subtotalExTax` and `subtotalIncTax` columns to `Invoice`. In exclusive mode: `subtotalExTax = sum of line amounts`, `subtotalIncTax = subtotalExTax + taxAmount`. In inclusive mode: `subtotalIncTax = sum of line amounts`, `subtotalExTax = subtotalIncTax - taxAmount`. `total` always equals `subtotalIncTax`.
   - Pros:
     - Unambiguous: each field has a single meaning regardless of mode.
     - Reports can always use `subtotalExTax` for revenue analysis without mode-switching logic.
     - `total = subtotalIncTax` is a universal formula.
   - Cons:
     - Adds two columns to the `invoices` table, replacing the existing `subtotal`. This is a more invasive migration.
     - Breaks the existing `InvoiceResponse` contract. Clients using `subtotal` must be updated.
     - Increases cognitive load for a feature most orgs won't use (most SA businesses use tax-exclusive pricing).
     - Xero doesn't do this — they manage with a single subtotal and contextual display. Adding columns for conceptual purity is over-engineering.

3. **subtotal always means ex-tax** -- In inclusive mode, extract tax from each line's `amount` first and store the ex-tax amount as the line's `amount`. `subtotal = sum of ex-tax amounts`. `total = subtotal + taxAmount` always. The line `amount` field changes meaning in inclusive mode: it's the ex-tax portion, not the price entered by the user.
   - Pros:
     - `total = subtotal + taxAmount` holds universally. Simplest formula.
     - `subtotal` always means revenue before tax. Reports and profitability calculations work without mode awareness.
   - Cons:
     - **Changes the meaning of line `amount`**: the user enters R115 (inclusive price), but `amount` stores R100 (ex-tax). The UI must back-calculate to show the user what they entered. This is confusing and error-prone.
     - Breaks the existing `amount = quantity * unitPrice` relationship. In inclusive mode, `amount` would be `quantity * unitPrice - tax`, which is not what the user sees.
     - Reverting from inclusive to exclusive mode would require recalculating all line amounts. Mode changes on an org with existing invoices become destructive.
     - Backward compatibility risk: existing code that reads `amount` as "what the customer is charged per line" would be wrong in inclusive mode.

**Decision**: Option 1 -- Xero model (subtotal = inclusive sum, total = subtotal in inclusive mode).

**Rationale**:

Option 3 is disqualified because it changes the semantic meaning of `InvoiceLine.amount`. The field currently represents `quantity * unitPrice` — the amount the customer sees on that line. Storing a derived ex-tax value instead would break this contract and confuse every consumer of line-level data.

Option 2 is technically clean but adds schema complexity for a marginal benefit. The two-column approach helps reports, but reports already need mode-awareness for other display purposes (e.g., showing "Prices include VAT" labels). An extra column doesn't eliminate that awareness — it just moves it from `subtotal` interpretation to column selection.

Option 1 matches Xero's well-established pattern. Xero serves millions of businesses in tax-inclusive jurisdictions (Australia, New Zealand, South Africa) and uses exactly this display model. Users are trained on it. The implementation is minimal: adjust `recalculateTotals()` to set `total = subtotal` when `taxInclusive && hasPerLineTax`, and update display templates to show "Includes {taxLabel}" instead of "{taxLabel}".

The formula divergence (`total = subtotal + taxAmount` in exclusive, `total = subtotal` in inclusive) is managed in exactly one place: `Invoice.recalculateTotals()`. All other code reads the pre-computed `total` field. Reports that need ex-tax revenue can compute `subtotal - taxAmount` when `taxInclusive` is true — a simple one-line derivation that doesn't warrant an additional database column.

**Implementation sketch**:

```java
// Invoice.recalculateTotals() — updated
public void recalculateTotals(BigDecimal computedSubtotal, List<InvoiceLine> lines,
                               boolean taxInclusive) {
    this.subtotal = computedSubtotal;

    boolean hasPerLineTax = lines.stream()
        .anyMatch(l -> l.getTaxRateId() != null);

    if (hasPerLineTax) {
        this.taxAmount = lines.stream()
            .map(l -> l.getTaxAmount() != null ? l.getTaxAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    // else: manual taxAmount preserved

    if (taxInclusive && hasPerLineTax) {
        this.total = this.subtotal;  // amounts already include tax
    } else {
        this.total = this.subtotal.add(this.taxAmount);
    }
}
```

Template display (tax-inclusive mode):
```
Subtotal:         R11,500.00
Includes VAT:     R1,500.00
Total:            R11,500.00
```

Template display (tax-exclusive mode):
```
Subtotal:         R10,000.00
VAT (15%):        R1,500.00
Total:            R11,500.00
```

**Consequences**:

- Positive:
  - Familiar UX for SA/AU/NZ users accustomed to Xero's display model.
  - No new database columns. Existing `subtotal`, `taxAmount`, `total` fields are reused.
  - `total` always represents the amount the customer pays. Clean semantics.
  - `InvoiceLine.amount` retains its meaning: `quantity * unitPrice`, regardless of mode.

- Negative:
  - `total = subtotal + taxAmount` is not universally true. Code that assumes this formula must be guarded. Mitigated by centralizing the computation in `recalculateTotals()`.
  - `subtotal` has dual meaning (pre-tax in exclusive, post-tax in inclusive). Developers must read the `taxInclusive` flag for correct interpretation. Documented in entity Javadoc.
  - Reports using `subtotal` as revenue must account for mode: `revenueExTax = taxInclusive ? subtotal - taxAmount : subtotal`.

- Neutral:
  - Legacy invoices (no per-line tax) are unaffected. Their `total = subtotal + taxAmount` formula continues to hold because `hasPerLineTax` is false, and the `taxInclusive` mode only applies when per-line tax is present.
  - Switching an org from exclusive to inclusive (or vice versa) does not retroactively change existing invoices. The mode is evaluated at invoice creation/recalculation time. Existing invoices retain their computed values.

- Related: [ADR-101](ADR-101-tax-calculation-strategy.md) (storage strategy), [ADR-103](ADR-103-tax-rate-immutability.md) (immutability), [ADR-048](ADR-048-invoice-numbering.md) (invoice model context).
