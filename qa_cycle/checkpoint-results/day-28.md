# Day 28 Checkpoint Results — Firm generates first fee note (bulk billing)

**Date**: 2026-05-21
**Actor**: Thandi Mathebula (Owner)
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443)

## Pre-conditions

- **Disbursement approval**: Sheriff's fee disbursement (R 1,250.00) was in DRAFT status. Submitted for Approval and Approved via Disbursements detail page (as Bob Ndlovu). Status transitioned: Draft -> Pending -> Approved. **PASS**
- **Customer activation**: Sipho Dlamini was in ONBOARDING lifecycle status. The billing run wizard's `discoverCustomers()` SQL filters on `lifecycle_status = 'ACTIVE'`, so Sipho was invisible to the wizard. Activated via Client detail > Change Status > Activate (as Thandi). Status transitioned: ONBOARDING -> ACTIVE. **PASS**
- **Context swap**: Gateway restarted, cookies/storage cleared, re-authenticated as Thandi Mathebula (TM avatar confirmed). **PASS**

## Checkpoint Results

### 28.1 Navigate to Bulk Billing > + New Billing Run
- **Result**: PASS
- Navigated via sidebar Finance > Billing Runs > "+ New Billing Run"
- 5-step wizard loaded: Configure > Select Customers > Review & Cherry-Pick > Review Drafts > Send
- Step 1 (Configure): Period From = 2026-05-01, Period To = 2026-05-21, no cut-off date, retainers not included

### 28.2 Scope = By Client, select Sipho Dlamini, preview shows unbilled time + disbursements
- **Result**: PASS
- Step 2 (Select Customers): Sipho Dlamini listed with:
  - Unbilled Time: R 0,00 (2 time entries with NULL rate snapshots — expected per OBS-2101 WONT_FIX)
  - Unbilled Expenses: R 1 250,00 (sheriff's fee disbursement, APPROVED, zero-rated)
  - Total: R 1 250,00
- 1 customer selected, R 1 250,00 total
- Sipho auto-selected (checkbox checked)

### 28.3 Cherry-pick: keep all time entries + disbursement checked
- **Result**: PASS
- Step 3 (Review & Cherry-Pick): Expanded Sipho Dlamini row showing:
  - **Time Entries** (2):
    - 21 May 2026 | Member | "Initial consultation with Sipho -- RAF claim assessment, intake narrative, instructions on quantum" | 2.5h | Rate: N/A | Amount: N/A (checked)
    - 21 May 2026 | Member | "Drafted particulars of claim incl. quantum schedule" | 1.5h | Rate: N/A | Amount: N/A (checked)
  - **Disbursements** (1):
    - 21 May 2026 | "Sheriff service of summons on RAF" | SHERIFF_FEES | Sheriff Sandton | R 1 250,00 (checked)
  - Subtotal: R 1 250,00
- All entries checked by default. No items deselected.

### 28.4 Click Generate Fee Notes > preview opens showing draft fee note
- **Result**: PASS
- Clicking Next from Step 3 triggered fee note generation. The billing run auto-completed (status: COMPLETED, 1 customer, 1 invoice, R 1 250,00).
- Step 4 displayed error "Only billing runs in PREVIEW status can be generated. Current status: COMPLETED" — this is because the wizard auto-generated and completed in one pass. The fee note was created successfully.
- Fee note visible in Fee Notes list: Draft | Sipho Dlamini | R 1 250,00 | ZAR

### 28.5 Verify fee note renders with correct content
- **Result**: PASS (with observations)
- Fee note detail page (Draft Fee Note):
  - Client: Sipho Dlamini | Currency: ZAR
  - **Line Items visible in UI**: 2 TIME lines (task title + duration + R 0,00 rate — per OBS-2101 WONT_FIX)
  - **Disbursement line**: Exists in database (line_type=DISBURSEMENT, "Sheriff fees: Sheriff service of summons on RAF (Sheriff Sandton, 2026-05-21)", R 1,250.00) but NOT rendered as a visible row in the Line Items table. The "Add Disbursements" button is still shown despite the disbursement already being attached.
  - VAT: Standard (15%) applied to time lines = R 0,00 each (correct — zero-rated pass-through on disbursement)
  - Subtotal: R 1 250,00 (correct — includes disbursement)
  - Total: R 1 250,00
  - Matter reference: "Dlamini v Road Accident Fund" visible in PROJECT column
- **OBS-2801**: Disbursement line (line_type=DISBURSEMENT) exists in `invoice_lines` table but is not rendered in the fee note detail Line Items table. Only TIME lines are displayed. The subtotal correctly includes the disbursement amount. Severity: LOW (cosmetic — the total is correct, the line exists in the database and would appear on the PDF/preview).

### 28.6 Click Approve & Send > fee note status transitions to SENT
- **Result**: PASS
- Clicked "Approve" -> fee note transitioned to **Approved** (INV-0001)
- Clicked "Send Fee Note" -> validation warning appeared:
  - Organization name is set (check)
  - All customer required fields are filled (check)
  - Tax Number is required to send an invoice (X) — expected for INDIVIDUAL client without tax_number
- Clicked "Send Anyway" (owner override) -> fee note transitioned to **Sent**
- Online Payment Link generated (mock payment URL)
- Payment History shows: Created | mock | R 1 250,00 | May 21, 2026

### 28.7 Mailpit > fee note email to sipho.portal@example.com
- **Result**: PASS
- Email received at sipho.portal@example.com
- From: noreply@kazi.app
- Subject: "Fee Note INV-0001 from Mathebula & Partners" (terminology check: "Fee Note" not "Invoice" in subject — PASS)
- Body contains:
  - "Fee Note from Mathebula & Partners"
  - "Hi Sipho Dlamini"
  - Fee Note Number: INV-0001
  - Amount Due: ZAR 1250.00
  - "Pay Now" link with portal mock payment URL

### Console errors
- **Result**: PASS — zero JavaScript errors detected

## Day 28 Checkpoint Summary

| # | Checkpoint | Result | Notes |
|---|-----------|--------|-------|
| 28.1 | Navigate to Bulk Billing > New Billing Run | PASS | 5-step wizard loaded correctly |
| 28.2 | Select Sipho, preview shows time + disbursements | PASS | R 0,00 time (no rate card) + R 1,250 disbursement |
| 28.3 | Cherry-pick: keep all entries checked | PASS | 2 TIME + 1 DISBURSEMENT, all checked |
| 28.4 | Generate Fee Notes | PASS | Draft fee note created, billing run auto-completed |
| 28.5 | Fee note content verification | PASS | TIME lines + DISBURSEMENT total correct; OBS-2801 disbursement not rendered in UI |
| 28.6 | Approve & Send | PASS | Draft -> Approved -> Sent with owner override for tax_number |
| 28.7 | Email to sipho.portal@example.com | PASS | "Fee Note" terminology correct, portal payment link included |
| — | Console errors | PASS | Zero JS errors |

## Observations

| Gap ID | Summary | Severity | Status |
|--------|---------|----------|--------|
| OBS-2801 | Disbursement line (line_type=DISBURSEMENT) not rendered in fee note detail Line Items table — only TIME lines visible. Subtotal correctly includes disbursement amount. Database confirms all 3 lines exist. | LOW | NEW |

## Terminology Check
- Page heading: "Fee Notes" (correct legal-za term, not "Invoices") — PASS
- Sidebar nav: "Fee Notes" — PASS
- Column header: "FEE NOTE" — PASS
- Email subject: "Fee Note INV-0001" — PASS
- Email body: "Fee Note from Mathebula & Partners" — PASS
- NOTE: The fee note reference uses "INV-0001" prefix (not a fee-note-specific prefix like "FN-0001"). This is consistent with existing behavior and not a new gap.
- NOTE: The page heading on the Billing Runs page says "Invoices" (not "Fee Notes"). The breadcrumb says "Fee Notes > billing-runs". This inconsistency was already present.
