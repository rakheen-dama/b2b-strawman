# Fix Spec: LZKC-028 — Firm invoice-detail totals block renders literal "(%)" VAT labels

## Problem
On the firm invoice detail page (`/org/{slug}/invoices/{id}`), the totals block between Subtotal and Total renders the per-rate VAT summary rows as literal `"(%) R 0,00"` — tax name and rate are missing (Day 28 / 28.5, screenshot `day-28-totals-block-pct-labels.png`, reproduced live). Amounts are correct. The portal invoice detail and the backend HTML preview render the same breakdown correctly ("VAT — Standard (15.00%)"), proving the data is present — the defect is firm-frontend-only.

## Root Cause (confirmed)
Field-name mismatch between the backend DTO and the firm frontend type:

- Backend serializes `TaxBreakdownEntry(String rateName, BigDecimal ratePercent, BigDecimal taxableAmount, BigDecimal taxAmount)` — `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tax/dto/TaxBreakdownEntry.java:6-7`, carried on `InvoiceResponse.taxBreakdown` (`backend/.../invoice/dto/InvoiceResponse.java:45`).
- Firm frontend declares `TaxBreakdownEntry { taxRateName: string; taxRatePercent: number; ... }` — `frontend/lib/types/invoice.ts:39-44`.
- `frontend/components/invoices/invoice-totals-section.tsx:27` renders `{entry.taxRateName} ({entry.taxRatePercent}%)` → both `undefined` → literal `" (%)"`.

The portal proves the correct contract: `portal/lib/types.ts:94-95` declares `rateName`/`ratePercent` and `portal/components/invoice-line-table.tsx:82` renders `{entry.rateName} ({entry.ratePercent}%)` — this renders correctly (Day 30 PASS).

`invoice-totals-section.tsx` is the ONLY consumer of the wrong field names (grep-verified: no other frontend source reads `taxRateName`/`taxRatePercent` off a breakdown entry; the `InvoiceLineResponse.taxRateName` per-line field at `frontend/lib/types/invoice.ts:31` is a *different, correct* backend field — do not touch it).

## Fix
1. `frontend/lib/types/invoice.ts:39-44` — rename the `TaxBreakdownEntry` interface fields `taxRateName` → `rateName`, `taxRatePercent` → `ratePercent` (matching the backend record and the portal type). Leave `taxableAmount`/`taxAmount` as-is (already matching).
2. `frontend/components/invoices/invoice-totals-section.tsx:27` — render `{entry.rateName} ({entry.ratePercent}%)`.
3. Run TypeScript compile to catch any other consumer (none expected).

## Scope
Frontend only.
Files to modify: `frontend/lib/types/invoice.ts`, `frontend/components/invoices/invoice-totals-section.tsx`.
Migration needed: no.

## Verification
- Unit: add/extend a test for `InvoiceTotalsSection` rendering a breakdown entry `{ rateName: "VAT — Standard", ratePercent: 15, ... }` and asserting the label text "VAT — Standard (15%)".
- Live re-run of Day 28 / 28.5: open `/org/mathebula-partners/invoices/{INV-0001 id}` — totals block rows must read "VAT — Standard (15%)" / "Zero-rated (0%)", matching the line-item Tax column.
- Gate: `pnpm lint && pnpm build && pnpm test` + prettier `format:check` (frontend).

## Estimated Effort
S (< 30 min)
