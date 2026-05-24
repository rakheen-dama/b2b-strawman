# Day 45 — QA Checkpoint Results — Accounting ZA Lifecycle (Keycloak)

**Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md`
**Actor**: Thandi Thornton (Owner) at `:3000`
**Stack health (pre-test)**: frontend :3000 OK, backend :8080 OK, Keycloak :8180 OK

---

## Checkpoint 45.1 — Create fixed-fee invoice for Sipho tax return engagement

**Scenario step**: 45.1 — Create fixed-fee invoice for Sipho's tax return engagement (one-off line item R2,500)

**Result: PASS**

**Evidence**:
- Navigated to Sipho Dlamini client page > Invoices tab.
- Clicked "New Invoice" > Fetched unbilled time (2 entries: Collect IRP5 1h R450, Prepare ITR12 1.5h R675).
- Created draft from unbilled entries, then deleted both time-based line items.
- Added manual fixed-fee line item:
  - Description: "Tax Return preparation -- 2025/26 ITR12 (fixed fee)"
  - Quantity: 1
  - Unit Price: R 2,500.00
  - Tax Rate: VAT -- Standard (15%)
- Line item totals verified:
  - Amount: R 2,500.00
  - VAT: R 375.00
  - Line Total: R 2,875.00
- Invoice totals verified:
  - Subtotal: R 2,500.00
  - VAT (15%): R 375.00
  - Total: R 2,875.00

---

## Checkpoint 45.2 — Approve and send invoice

**Scenario step**: 45.2 — Approve + send

**Result: PASS**

**Evidence**:
- Clicked "Approve" > Invoice assigned number **INV-0002**, status changed to "Approved", Issue Date: May 15, 2026.
- Clicked "Send Invoice" > Validation dialog showed 1 issue (2/5 required fields filled on client), 2 passes (org name set, customer tax number set).
- Used owner override: "Send Anyway" > Invoice status changed to **Sent**.
- Payment History section appeared: "No payment events yet."

---

## Checkpoint 45.3 — SA Tax Invoice PDF generation

**Scenario step**: (ancillary to 45.2 — PDF generation for sent invoice)

**Result: PASS**

**Evidence**:
- Clicked "Generate Document" > "SA Tax Invoice".
- PDF preview rendered in dialog with correct content:
  - From: Thornton & Associates
  - To: Sipho Dlamini
  - Client VAT Number: 1234567890
  - Invoice Number: INV-0002
  - Issue Date: 15 May 2026
  - Currency: ZAR
  - Line Item: Tax Return preparation -- 2025/26 ITR12 (fixed fee), Qty 1, R2,500.00, VAT R375.00, Total R2,875.00
  - Amount Summary: Subtotal R2,500.00, VAT 15% R375.00, Total Due R2,875.00
  - Banking Details section present
  - Section 20 VAT Act compliance note present
- Clicked "Save to Documents" > "Document saved successfully"
- Generated Documents table shows: SA Tax Invoice, Thandi Thornton, May 15 2026, 2.6 KB, PDF

---

## Summary

| ID | Step | Result | Notes |
|----|------|--------|-------|
| 45.1 | Fixed-fee invoice created (R 2,500 + VAT) | **PASS** | INV-0002, 1 manual line item |
| 45.2 | Approved + sent (owner override) | **PASS** | Sent status, May 15 2026 |
| 45.3 | SA Tax Invoice PDF generated + saved | **PASS** | 2.6 KB, correct content |

**Screenshots**:
- `qa_cycle/evidence/day-45/sipho-invoice-sent-INV-0002.png`
- `qa_cycle/evidence/day-45/sipho-invoice-pdf-generated.png`

**Blockers**: None.
**New gaps**: None.

**Invoice details for status tracking**:
- Invoice ID: `9dca277d-d636-44ab-89dd-fcd02aaca957`
- Invoice Number: INV-0002
- Client: Sipho Dlamini
- Type: Fixed fee
- Subtotal: R 2,500.00
- VAT 15%: R 375.00
- Total: R 2,875.00
- Status: SENT
- Issued: May 15, 2026
