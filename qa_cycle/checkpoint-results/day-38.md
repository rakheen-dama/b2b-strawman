# Day 38 Checkpoint Results — Approve Invoice + Generate PDF

**Date**: 2026-05-15
**Agent**: QA
**Actor**: Thandi Thornton (Owner)
**Stack**: Keycloak dev stack (frontend :3000, backend :8080)

## Scenario Steps

### 38.1 — Approve draft invoice, transition to SENT, generate PDF

| Step | Action | Result | Status |
|------|--------|--------|--------|
| 38.1a | Navigate to Finance > Invoices | Invoice list loads. 1 invoice: Kgosi Holdings (Pty) Ltd, Draft, R 6,411.25 | PASS |
| 38.1b | Open draft invoice (ID: b6ba784c) | Invoice detail page loads. 4 line items, Subtotal R 5,575.00, VAT R 836.25, Total R 6,411.25. Buttons: Preview, Approve, Delete Draft | PASS |
| 38.1c | Click "Approve" | Status transitions from Draft to **Approved**. Invoice number assigned: **INV-0001**. Issue date: May 15, 2026. Buttons change to: Preview, Send Invoice, Void. Edit/Delete buttons removed from line items (read-only). | PASS |
| 38.1d | Click "Send Invoice" | Validation dialog appears: "2 of 5 required fields filled" (missing due date, payment terms, billing period). Org name and customer tax number pass. Owner override available via "Send Anyway". | PASS |
| 38.1e | Click "Send Anyway" (owner override) | Status transitions from Approved to **Sent**. Payment History section appears ("No payment events yet."). Buttons: Preview, Record Payment, Void. | PASS |
| 38.1f | Click "Generate Document" dropdown | Menu shows 2 options: "Invoice Cover Letter" and "SA Tax Invoice" | PASS |
| 38.1g | Select "SA Tax Invoice" | Document preview dialog renders with full invoice content in iframe | PASS |
| 38.1h | Click "Save to Documents" | Document saved. Confirmation: "Document saved successfully". Generated Documents section shows: SA Tax Invoice, Thandi Thornton, May 15, 2026, 3.2 KB, PDF | PASS |

### 38.2 — Verify PDF content (Thornton letterhead + brand colour + VAT breakdown + banking details)

| Checkpoint | Expected | Observed | Status |
|-----------|----------|----------|--------|
| Thornton letterhead | "From: Thornton & Associates" | Present in PDF header | PASS |
| VAT Registration No | Field present | Present (empty -- org hasn't set this, acceptable) | PASS |
| Client details | "To: Kgosi Holdings (Pty) Ltd" + VAT number | "To: Kgosi Holdings (Pty) Ltd", Client VAT Number: 4123456789 | PASS |
| Invoice number | INV-0001 | INV-0001 | PASS |
| Issue date | Date present | 15 May 2026 | PASS |
| Currency | ZAR | ZAR | PASS |
| Line items | 4 items with Description, Qty, Unit Price, VAT, Total | All 4 present: Bank recon (3h, R850, R382.50, R2,932.50), Creditors (1.5h, R850, R191.25, R1,466.25), VAT calc (1h, R850, R127.50, R977.50), Debtors (2h, R450, R135, R1,035) | PASS |
| VAT breakdown | Subtotal excl VAT + VAT 15% + Total | Subtotal R5,575.00, VAT (15%) R836.25, Total Due R6,411.25 | PASS |
| Banking details | Section present | "Banking Details" heading with payment reference instruction | PASS |
| SA tax compliance | Section 20 reference | "This is a tax invoice as defined in section 20 of the Value-Added Tax Act 89 of 1991." | PASS |
| Brand colour | Green accent (#1B5E20) | Cannot verify from accessibility snapshot; visual screenshot captured | PARTIAL |

## Summary

- **Total checks**: 18
- **PASS**: 17
- **PARTIAL**: 1 (brand colour -- visually present in screenshot but not verifiable via accessibility tree)
- **FAIL**: 0
- **New gaps**: 0

## Notes

1. The invoice lifecycle follows a 3-step flow: Draft -> Approved -> Sent (not direct Draft -> Sent as the test plan implied). This is correct behavior -- approval is a separate step from sending.
2. Send validation checks caught missing fields (due date, payment terms, billing period). Owner override available -- this is good UX for admin flexibility while maintaining data quality warnings.
3. Console errors are limited to `/api/assistant/invocations` 404 (AI assistant not configured) and minor Radix DialogContent accessibility warnings. No functional errors.
4. The "Generate Document" dropdown offers both "Invoice Cover Letter" and "SA Tax Invoice" templates -- the SA Tax Invoice is the correct choice for Day 38.

## Evidence

- `qa_cycle/evidence/day-38/sa-tax-invoice-preview.png` — SA Tax Invoice preview dialog
- `qa_cycle/evidence/day-38/invoice-sent-with-pdf.png` — Full invoice page with Sent status and generated document
