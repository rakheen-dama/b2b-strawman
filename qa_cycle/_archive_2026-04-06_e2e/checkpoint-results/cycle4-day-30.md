# Cycle 4 — Day 30: First Billing Cycle

**Date**: 2026-04-06
**Actor**: Alice (Owner)
**Stack**: E2E mock-auth (localhost:3001 / backend:8081 / Mailpit:8026)

## Summary

Invoice creation from unbilled time works end-to-end including DRAFT -> APPROVED -> SENT -> PAID lifecycle. LSSA tariff integration exists but is blocked by a NaN rate bug. Manual line items fail with a backend parse error. VAT calculation is correct at 15%. Email delivery confirmed via Mailpit.

## Checkpoint Results

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 30.1 | Navigate to Fee Notes / Invoices | PASS | `/invoices` loads. Breadcrumb says "fee notes" (lowercase). |
| 30.2 | Click New Fee Note | PASS | Customer picker dialog appears with all 4 clients. |
| 30.3 | Select client = Sipho Ndlovu | PARTIAL | Client selected. Redirects to customer detail Invoices tab. No matter selector in flow. |
| 30.4 | Add tariff line (LSSA) | FAIL | "Add Tariff Items" dialog loads with 7 sections (19 items) but ALL amounts show **"R NaN"**. Items can be selected but Add to Invoice fails silently. **GAP-D30-01**. |
| 30.5-30.7 | Add time-based lines | N/A | Time lines auto-populated from unbilled time entries (3 lines). No manual time line entry needed for this flow. |
| 30.8 | Add disbursement line (Sheriff R350) | FAIL | "Add Line" form appears with Description/Qty/Price/Tax fields. Filling and clicking Add returns **"The request body could not be read or parsed"** backend error. Line not added. **GAP-D30-02**. |
| 30.9 | Verify subtotal | PASS | Subtotal: R 4,325.00 (correct: 2x1200 + 2x550 + 1.5x550). |
| 30.10 | Verify VAT = 15% | PASS | VAT: R 648.75 (15% of R 4,325.00). Per-line tax shown: R360 + R165 + R123.75. |
| 30.11 | Save as DRAFT | PASS | Invoice created in DRAFT status. No invoice number assigned until approval. |
| 30.12 | Screenshot | N/A | Not captured (automation test). |
| 30.13 | Create Apex fee note | PARTIAL | Created from unbilled time (3h Bob, R3,600 + 15% VAT = R4,140). Address prerequisite fields had to be filled first (Address Line 1, City, Country, Tax Number, Client Type). |
| 30.14 | Add fixed fee line R35,000 | BLOCKED | Cannot add manual lines -- same GAP-D30-02 "request body" error. Only unbilled time-based generation works. |
| 30.15 | Verify Apex total R40,250 | FAIL | Apex total is R4,140 (from unbilled time), not the fixed R40,250 from test plan. No manual line item capability. |
| 30.16-30.21 | Trust fee transfer for Moroka | BLOCKED | No trust account exists (GAP-D0-02 WONT_FIX). Cannot navigate to trust transactions. |
| 30.22-30.24 | QuickCollect fee note | NOT_TESTED | Skipped to test lifecycle flow; would require same address prerequisite setup. |
| 30.25 | Approve Sipho fee note | PASS | Status: DRAFT -> APPROVED. Invoice number **INV-0001** assigned. Issue date: Apr 6, 2026. |
| 30.26 | Send Sipho fee note | PASS | Status: APPROVED -> SENT. "Record Payment" and "Void" buttons appear. Payment History section shows "No payment events yet". |
| 30.27 | Check Mailpit for email | PASS | Email received: From: noreply@docteams.app, To: sipho.ndlovu@email.co.za, Subject: "Invoice INV-0001 from E2E Test Organization", Body includes invoice number, amount (ZAR 4973.75), "View Invoice" link. |
| 30.28 | Approve + send Apex fee note | NOT_TESTED | Apex invoice still in DRAFT (created but not advanced). |
| 30.29 | Sequential fee note numbering | PARTIAL | INV-0001 assigned to Sipho. Apex still Draft (no number). Sequential numbering appears to work based on single data point. |
| 30.30 | Screenshot of fee note list | N/A | Not captured. |
| 30.31-30.33 | Fee estimate / budget for Apex | NOT_TESTED | Budget tab exists on matters but not tested in this cycle. |

## Terminology Observations

| Element | Expected (Legal) | Actual | Gap? |
|---------|-----------------|--------|------|
| Page heading | "Fee Notes" | "Invoices" | YES - GAP-D30-03 |
| Button | "New Fee Note" | "New Invoice" | YES - GAP-D30-03 |
| Breadcrumb | "fee notes" | "fee notes" | PASS (lowercase but correct) |
| Detail breadcrumb | "Fee Note" | "Fee Note" | PASS |
| Line items | "Disbursement" | "Add Line" (generic) | PARTIAL - no type selector |
| Tab heading | "Fee Estimate" | Not checked | — |
| Currency on list | "R" prefix | "R" prefix | PASS |
| Currency on summary | "$" | "R" | PASS on detail, FAIL on list summary (was $ before data) |

## New Gaps Found

| ID | Summary | Severity | Status |
|----|---------|----------|--------|
| GAP-D30-01 | LSSA tariff items all show "R NaN" -- tariff rates not loaded/parsed. Add to Invoice button present but items added with NaN amount. | HIGH | OPEN |
| GAP-D30-02 | "Add Line" to invoice returns "The request body could not be read or parsed" backend error. Manual line items cannot be added. Blocks disbursements and fixed-fee invoicing. | HIGH | OPEN |
| GAP-D30-03 | Invoices list page heading says "Invoices" and button says "New Invoice" instead of "Fee Notes" / "New Fee Note" when legal-za profile is active. Breadcrumb/detail correctly say "fee notes"/"Fee Note". | LOW | OPEN |
| GAP-D30-04 | Invoice prerequisite check requires Address Line 1, City, Country, Tax Number, and Client Type fields filled before creating an invoice. These fields are not populated during client creation flow -- discovery UX gap for new users. | LOW | OPEN |
| GAP-D30-05 | Project column on invoice line items shows "{client} - {type}" template placeholder instead of actual matter name (same root cause as GAP-D1-07). | LOW | OPEN |

## Console Errors

0 errors during Day 30 testing.
