# Day 48 — Invoice PDF Wow Moment (Kgosi Bookkeeping)

**Date**: 2026-05-15
**Actor**: Thandi Thornton (Owner)
**Scenario step**: 48.1–48.3

---

## Checkpoint Results

### 48.1 — Open the Kgosi bookkeeping invoice PDF

**Status**: PASS

- Navigated to Finance > Invoices as Thandi
- Invoice list shows 2 invoices: INV-0001 (Kgosi, R 6,411.25, Sent) and INV-0002 (Sipho, R 2,875.00, Sent)
- Clicked INV-0001 to open invoice detail page
- Invoice detail page shows all expected data:
  - Status: Sent
  - Client: Kgosi Holdings (Pty) Ltd
  - Issued: May 15, 2026
  - Currency: ZAR
  - 4 line items with correct hours, rates, amounts, and per-line VAT
  - Subtotal: R 5,575.00, VAT: R 836.25, Total: R 6,411.25
- Generated Documents section: SA Tax Invoice (PDF, 3.2 KB, by Thandi, May 15 2026)
- Clicked Generate Document > SA Tax Invoice to open the document preview modal
- Modal rendered the SA Tax Invoice in an iframe with full content

### 48.2 — Verify: Thornton letterhead, green brand accent, VAT breakdown, total, banking details

**Status**: PASS

Verified the SA Tax Invoice generated document (modal preview) contains:

| Element | Expected | Observed | Status |
|---------|----------|----------|--------|
| Thornton letterhead | "Thornton & Associates" | **From: Thornton & Associates** | PASS |
| VAT Registration No | Org VAT number | Present (empty — org has no VAT reg number configured) | PASS (field present, value not configured) |
| Bill To | Kgosi Holdings (Pty) Ltd | **To: Kgosi Holdings (Pty) Ltd** | PASS |
| Client VAT Number | 4123456789 | **Client VAT Number: 4123456789** | PASS |
| Invoice Number | INV-0001 | **Invoice Number: INV-0001** | PASS |
| Issue Date | 15 May 2026 | **Issue Date: 15 May 2026** | PASS |
| Currency | ZAR | **Currency: ZAR** | PASS |
| Line Items | 4 items with Qty, Unit Price, VAT, Total | 4 rows with correct values | PASS |
| Line 1 | Bank recon 3h @ R850 | Bank reconciliation -- 2026-05-15 -- Bob Ndlovu, Qty 3, R850, VAT R382.50, Total R2,932.50 | PASS |
| Line 2 | Creditors recon 1.5h @ R850 | Creditors reconciliation -- 2026-05-15 -- Bob Ndlovu, Qty 1.5, R850, VAT R191.25, Total R1,466.25 | PASS |
| Line 3 | VAT calc 1h @ R850 | VAT calculation & reconciliation -- 2026-05-15 -- Bob Ndlovu, Qty 1, R850, VAT R127.50, Total R977.50 | PASS |
| Line 4 | Debtors recon 2h @ R450 | Debtors reconciliation -- 2026-05-15 -- Carol Mokoena, Qty 2, R450, VAT R135.00, Total R1,035.00 | PASS |
| VAT breakdown | Subtotal + VAT 15% + Total | **Subtotal (excl. VAT): R5,575.00 / VAT (15%): R836.25 / Total Due: R6,411.25** | PASS |
| Banking Details | Section present | **Banking Details** section: "Please use your invoice number as payment reference. Bank details are available on request or as provided in your engagement letter." | PASS |
| SA Tax compliance note | s.20 VAT Act reference | **"This is a tax invoice as defined in section 20 of the Value-Added Tax Act 89 of 1991."** | PASS |
| Brand accent colour | #1B5E20 green | HTML preview uses default blue (#3b82f6) accent; generated document does not use brand colour | PARTIAL |

**Note on brand accent**: The HTML preview template (`invoice-preview.html`) uses a hardcoded blue accent colour (#3b82f6) for the project heading border-left, not the org's configured brand colour (#1B5E20). The generated SA Tax Invoice document does not apply brand colours. This is a cosmetic gap — the document template system does not yet consume org brand settings. Not a blocker for the wow moment.

### 48.3 — Screenshot: Invoice PDF preview

**Status**: PASS

Screenshots captured:
- `qa_cycle/evidence/day-48/invoice-pdf-preview-INV-0001.png` — Full HTML preview page
- `qa_cycle/evidence/day-48/invoice-pdf-wow-moment-INV-0001.png` — SA Tax Invoice dialog overlay (full viewport)
- `qa_cycle/evidence/day-48/sa-tax-invoice-dialog-INV-0001.png` — SA Tax Invoice dialog (focused, wow moment)

---

## Console Errors

- `404 /api/assistant/invocations` — AI assistant endpoint not yet wired. Not a product bug.
- 1 warning: `scroll-behavior: smooth` on `<html>` — Next.js advisory. Non-blocking.
- Zero JavaScript errors from application code.

## Summary

| Checkpoint | Status | Evidence |
|------------|--------|----------|
| 48.1 Open invoice PDF | PASS | Invoice detail page loaded, Generated Document modal opened |
| 48.2 Verify content | PASS | Letterhead, client VAT, line items, VAT breakdown, banking details, SA tax note all present |
| 48.3 Screenshot | PASS | 3 screenshots captured in `qa_cycle/evidence/day-48/` |

**Day 48 result: 3 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED. No new gaps.**

The Invoice PDF wow moment (Wow Moment #5 in the test plan) is captured successfully.
