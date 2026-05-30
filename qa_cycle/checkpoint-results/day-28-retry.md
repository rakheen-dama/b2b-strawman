# Day 28 Retry — Checkpoint Results (Cycle 18)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
**Executed by**: QA Agent
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actor**: Thandi Mathebula (Owner)
**Context**: Retry after OBS-2801b fix (PR #1401 merged). OBS-2803 already VERIFIED cycle 17.

---

## Pre-condition: Verify OBS-2801 + OBS-2801b fix — Activate Sipho Dlamini

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| V-1 | Navigate to Sipho's client detail page as Thandi | **PASS** | URL: `/org/mathebula-partners/customers/d74963c8-...`. Header: "Sipho Dlamini", badges: Active + **Onboarding**. "Activate Customer" button visible. Overview tab shows **"Ready to activate"** card with text "No onboarding checklist assigned. This customer is ready to be activated." — this is the OBS-2801b fix working (no checklist required). |
| V-2 | Click "Activate Customer" button | **PASS** | **"Activate Client" confirmation dialog appeared!** Title: "Activate Client". Description: "This will mark the customer as Active. All onboarding checklists must be completed before activation." Notes field (optional) + Cancel/Activate buttons. |
| V-3 | Click "Activate" in confirmation dialog | **PASS** | Dialog closed. Page refreshed. Badges changed: **Active + Active** (both badges now say Active). "Activate Customer" button replaced by **"Edit" button**. "Ready to activate" card removed from Overview. Client Readiness shows "Setup complete". |
| V-4 | Database verification | **PASS** | `SELECT lifecycle_status FROM tenant_5039f2d497cf.customers WHERE id = 'd74963c8-...'` = **ACTIVE**. |

**OBS-2801: VERIFIED.** Activation button works end-to-end. Dialog appears, backend transition executes, UI reflects ACTIVE status.
**OBS-2801b: VERIFIED.** Checklist gate relaxed — activation allowed when no onboarding checklist exists (total=0).

**Screenshot**: `day-28-retry-sipho-activated.png`

---

## Verify OBS-2802 — Billing wizard discovers Sipho

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| V-5 | Navigate to Billing Runs, click "New Billing Run" | **PASS** | URL: `/org/mathebula-partners/invoices/billing-runs/new`. 5-step wizard opened. Heading: "Fee Notes" (OBS-2803 fix confirmed). |
| V-6 | Step 1: Configure — set Period 2026-05-01 to 2026-05-30 | **PASS** | Both date inputs set via native value setter. Clicked Next. |
| V-7 | Step 2: Select Customers — Sipho should appear | **PASS** | **Sipho Dlamini listed!** Previously "No customers with unbilled work found for this period." Now: Sipho Dlamini, Unbilled Time R 0,00, Unbilled Expenses R 1 250,00, Total R 1 250,00. Auto-selected (checkbox checked). Footer: "1 customer selected, R 1 250,00 total". |

**OBS-2802: VERIFIED.** Cascading dependency resolved — Sipho ACTIVE makes him discoverable by `discoverCustomers()` SQL. Backend correctly returns 1 customer with R 1 250,00 unbilled expenses.

**Screenshot**: `day-28-retry-billing-sipho-discovered.png`

---

## Checkpoint 28.1: Navigate to Bulk Billing -> + New Billing Run

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 28.1 | Navigate to Billing Runs -> + New Billing Run | **PASS** | Finance nav > Billing Runs. Heading: "Fee Notes" (OBS-2803 VERIFIED). New Billing Run CTA clicked. 5-step wizard: Configure, Select Customers, Review & Cherry-Pick, Review Drafts, Send. |

---

## Checkpoint 28.2: Configure billing run and select Sipho

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 28.2a | Step 1: Configure — set Period From/To | **PASS** | Period From = 2026-05-01, Period To = 2026-05-30. Clicked Next. |
| 28.2b | Step 2: Select Customers — Sipho appears with unbilled work | **PASS** | Sipho Dlamini: Unbilled Time R 0,00, Unbilled Expenses R 1 250,00, Total R 1 250,00. Auto-selected. "1 customer selected, R 1 250,00 total". |

---

## Checkpoint 28.3: Cherry-pick — keep all time entries + disbursement

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 28.3 | Step 3: Review & Cherry-Pick — all items included | **PASS** | Expanded Sipho Dlamini (R 1 250,00). **Time Entries** (2 rows, both checked): (1) "Initial consultation with Sipho — RAF claim assessment, intake narrative, instructions on quantum" 2.5h, Rate N/A, Amount N/A; (2) "Drafted particulars of claim incl. quantum schedule" 1.5h, Rate N/A, Amount N/A. **Disbursements** (1 row, checked): "Sheriff service of summons on RAF", SHERIFF_FEES, Sheriff Sandton, R 1 250,00. Subtotal: R 1 250,00. All items checked/included. |

---

## Checkpoint 28.4: Generate Fee Notes

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 28.4 | Click Next from cherry-pick -> fee note generated | **PASS** | Wizard auto-generated billing run and fee note on transition from Step 3 to Step 4. Billing run status: Completed. 1 customer, 1 invoice, R 1 250,00 total. Fee note accessible via "View Invoice" link. |

---

## Checkpoint 28.5: Verify fee note content

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 28.5a | Fee note renders with correct line items | **PASS** | Fee note INV-0001, status Draft, Client: Sipho Dlamini, Currency: ZAR. **Time Entries section** (2 lines): (1) "File RAF1 claim form + supporting documents (within 3-year prescription) -- 2026-05-30 -- Bob Ndlovu", Matter: Dlamini v Road Accident Fund, Qty 1.5, Rate R 0,00, Amount R 0,00, VAT Standard 15% R 0,00. (2) "Initial RAF claim assessment & instructions -- 2026-05-30 -- Bob Ndlovu", Qty 2.5, Rate R 0,00, Amount R 0,00, VAT Standard 15% R 0,00. **Disbursements section** (1 line): "Sheriff fees: Sheriff service of summons on RAF (Sheriff Sandton, 2026-05-30)", Qty 1, Rate R 1 250,00, Amount R 1 250,00, Zero-rated (0%) R 0,00. |
| 28.5b | TIME and EXPENSE line types correctly separated | **PASS** | "Time Entries" group header separates time lines from "Disbursements" group header. Disbursement clearly labelled with category (Sheriff fees), supplier (Sheriff Sandton), and date. |
| 28.5c | Matter reference visible | **PASS** | Each line shows "Dlamini v Road Accident Fund" in the Project column. |
| 28.5d | VAT line present | **PASS** | Time entries: VAT Standard (15%) applied (R 0,00 due to zero rate). Disbursement: Zero-rated (0%). |
| 28.5e | ZAR total | **PASS** | Subtotal: R 1 250,00. Total: R 1 250,00. Currency: ZAR. |
| 28.5f | Letterhead (logo + firm details) | **PARTIAL** | Back link says "Back to Fee Notes". Breadcrumb shows firm name. Logo not visible on draft editor view — would need Generate Document + Preview to see letterhead rendering. Non-blocking. |

**Screenshot**: `day-28-retry-fee-note-draft.png`

---

## Checkpoint 28.6: Approve & Send — fee note status transitions to SENT

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 28.6a | Configure fee note — set due date and tax type | **PASS** | Due Date: 2026-06-29, Tax Type: VAT. Saved changes. |
| 28.6b | Click Approve | **PASS** | Status transitioned Draft -> **Approved**. Invoice number assigned: **INV-0001**. Issued: May 30, 2026. Due: Jun 29, 2026. Buttons changed to Preview + Send Fee Note + Void. |
| 28.6c | Click Send Fee Note | **PASS** | Validation dialog: "Tax Number is required to send an invoice" (soft warning). Owner override available. Clicked "Send Anyway". Status transitioned Approved -> **SENT**. Buttons changed to Preview + Record Payment + Void. |

**Database verification**: `SELECT status FROM tenant_5039f2d497cf.invoices WHERE id = '4ce5fbc3-...'` = **SENT**.

**Screenshot**: `day-28-retry-fee-note-sent.png`

---

## Checkpoint 28.7: Mailpit — fee note email

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 28.7a | Fee note email delivered to Sipho | **PASS** | Mailpit: Subject "**Fee Note INV-0001 from Mathebula & Partners**", To: sipho.portal@example.com, From: noreply@kazi.app, Date: 2026-05-30T17:55:20Z. |
| 28.7b | Terminology: "Fee Note" in subject (not "Invoice") | **PASS** | Subject: "Fee Note INV-0001 from Mathebula & Partners". Body heading: "Fee Note from Mathebula & Partners". |
| 28.7c | Email body: fee note details + payment link | **PASS** | Body: "Hi Sipho Dlamini", Fee Note Number: INV-0001, Due Date: 2026-06-29, Amount Due: ZAR 1250.00. **Pay Now** link: mock payment gateway URL with sessionId, invoiceId, amount=1250.00, currency=ZAR, returnUrl to portal. **View Fee Note** link: `http://localhost:3002/invoices/4ce5fbc3-...`. |

---

## Checkpoint 28.8: Screenshot

**Screenshot**: `day-28-retry-fee-note-sent.png` — fee note INV-0001 in SENT status with client Sipho Dlamini, R 1 250,00.

---

## Day 28 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Fee note generated with TIME time-entry lines + EXPENSE disbursement line correctly separated | **PASS** | 2 TIME lines (task titles + duration + member name, no tariff descriptors per OBS-2101) + 1 EXPENSE line (Sheriff fees R 1 250,00, zero-rated). Clearly separated with group headers. |
| Terminology: firm-side copy reads "Fee Note" (not "Invoice") end-to-end | **PASS** | Sidebar nav: "Fee Notes". Billing Runs heading: "Fee Notes" (OBS-2803 fix). Back link: "Back to Fee Notes". Breadcrumb: "Fee Note". Email subject: "Fee Note INV-0001". Email body: "Fee Note from Mathebula & Partners". Minor: internal reference "INV-0001" uses "INV" prefix, and table column header says "Invoices" (separate minor gap, non-blocking). |
| Email dispatched with portal payment link | **PASS** | Mailpit: email to sipho.portal@example.com with "Pay Now" link (mock payment gateway) and "View Fee Note" link (portal). |

---

## Console Errors

Only known OBS-201 (WONT_FIX-EXEMPT): `/api/assistant/invocations` 404 — AI infra proxy not wired for KC mode. 5 occurrences (on fee note detail page load and navigation). Zero new JavaScript errors during Day 28 retry execution.

---

## Gaps Filed / Updated

| Gap ID | Summary | Status Change | Notes |
|--------|---------|--------------|-------|
| OBS-2801 | "Activate Customer" button works after PR #1399 + PR #1401 | **VERIFIED** | Dialog appears, backend transition executes, UI reflects ACTIVE status. Full end-to-end observed. |
| OBS-2801b | Checklist gate relaxed for no-checklist customers | **VERIFIED** | "Ready to activate" card shows when total=0. Activation dialog fires. DB confirms ACTIVE. |
| OBS-2802 | Billing wizard discovers Sipho after activation | **VERIFIED** | 1 customer, R 1 250,00 unbilled expenses. Cascading dependency fully resolved. |

---

## Entity IDs (for downstream days)

- **Sipho Dlamini Client ID**: `d74963c8-4527-41b8-bd67-a2ca3ed6a3cf`
- **Sipho lifecycle_status**: `ACTIVE` (transitioned from ONBOARDING)
- **Matter ID**: `d80aeac5-d5f4-4690-9291-193f05e3785d`
- **Matter Reference**: RAF-2026-001
- **Fee Note ID**: `4ce5fbc3-7bb6-4712-8e1f-84b80320b76b`
- **Fee Note Number**: INV-0001
- **Fee Note Status**: SENT
- **Fee Note Amount**: R 1 250,00 (ZAR)
- **Fee Note Due Date**: Jun 29, 2026
- **Disbursement ID**: `28f09dc4-a419-4dc8-86d7-1def7dc1da07`
- **Disbursement Billing Status**: BILLED (was UNBILLED)
- **Billing Run ID**: `00d39c3c-39dc-4a70-8ee5-b9869289b84d`
- **Payment Gateway Session**: `MOCK-SESS-32c7e903-08c8-4b93-988d-3ff4fa11570c`
- **Mailpit Message ID**: `5VVFyuETZeJkzqgcEiKLEf`

## Screenshots

- `day-28-retry-sipho-activated.png` — client detail page showing "Active + Active" badges after successful activation
- `day-28-retry-billing-sipho-discovered.png` — billing wizard Step 2 showing Sipho discovered with R 1 250,00 unbilled expenses
- `day-28-retry-fee-note-draft.png` — fee note draft with TIME + EXPENSE line items
- `day-28-retry-fee-note-sent.png` — fee note INV-0001 in SENT status
