# Cycle 4 — Day 60: Interest Run & Second Billing

**Date**: 2026-04-06
**Actor**: Alice (Owner)
**Stack**: E2E mock-auth (localhost:3001 / backend:8081 / Mailpit:8026)

## Summary

Interest/investment steps fully blocked by missing trust account. Second billing cycle partially blocked by manual line item bug (GAP-D30-02). Reports work well -- Profitability Report shows meaningful data across all 5 matters with correct revenue/cost/margin calculations. Export CSV/PDF buttons are enabled after report run. Timesheet and Invoice Aging reports also available.

## Checkpoint Results

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 60.1-60.10 | Interest calculation & LPFF | BLOCKED | No trust account (GAP-D0-02 WONT_FIX). Trust Accounting > Interest page likely exists but cannot function without trust account data. |
| 60.11-60.17 | Trust investment (Section 86(3)) | BLOCKED | No trust account. Investment placement requires trust balance. |
| 60.18-60.20 | Test Section 86(4) path | BLOCKED | Same trust dependency. |
| 60.21 | Create Sipho fee note #2 | BLOCKED | All Sipho unbilled time was already consumed in fee note #1 (INV-0001, PAID). No additional unbilled time entries to generate from. Creating a manual-only invoice is blocked by GAP-D30-02 (Add Line backend error). |
| 60.22 | Moroka fee note + trust transfer | BLOCKED | Trust transfer blocked (no trust account). Invoice creation from unbilled time would work but Moroka address fields need prerequisite setup. |
| 60.23 | QuickCollect fee note | NOT_TESTED | Would require address field setup + disbursement line (blocked by GAP-D30-02). |
| 60.24 | Approve and send all | NOT_TESTED | No new invoices created to advance. |
| 60.25 | Navigate to Reports | PASS | `/reports` page loads with 3 report categories: Financial (Invoice Aging), Project (Profitability), Time & Attendance (Timesheet). |
| 60.26 | Time tracking report | NOT_TESTED | Timesheet report link visible, not exercised (Profitability tested instead as higher priority). |
| 60.27 | Matter profitability report | PASS | Report runs successfully for April 2026. Shows 5 matters with full breakdown. Summary: 13.75 total billable hours, R13,737.50 revenue, R5,500 cost, R8,237.50 margin, 59.96% overall margin. |
| 60.28 | Export CSV | PARTIAL | "Export CSV" and "Export PDF" buttons become enabled after running report. Not clicked to verify actual download but buttons are actionable. |
| 60.29 | Screenshot profitability | N/A | Not captured. |

## Profitability Report Detail

| Matter | Hours | Revenue | Cost | Margin | Margin % |
|--------|-------|---------|------|--------|----------|
| {client} - {type} (Sipho litigation) | 5.50 | R4,325 | R1,700 | R2,625 | 60.69% |
| Deceased Estate - Peter Moroka | 2.50 | R4,300 | R1,750 | R2,550 | 59.30% |
| Shareholder Agreement - Apex Holdings | 3.00 | R3,600 | R1,500 | R2,100 | 58.33% |
| Debt Recovery - vs Mokoena (R45,000) | 2.00 | R1,100 | R400 | R700 | 63.64% |
| Debt Recovery - vs Pillay (R28,000) | 0.75 | R412.50 | R150 | R262.50 | 63.64% |
| **TOTAL** | **13.75** | **R13,737.50** | **R5,500** | **R8,237.50** | **59.96%** |

## Terminology Observations

| Element | Expected (Legal) | Actual | Gap? |
|---------|-----------------|--------|------|
| Report heading | "Matter Profitability" | "Project Profitability Report" | YES - GAP-D60-01 |
| Column header | "Matter" | "Project" | YES - same gap |
| Customer column | Client name | "—" (blank for all) | BUG - GAP-D60-02 |
| Time report | "Time Recordings" | "Timesheet Report" | Not tested |

## New Gaps Found

| ID | Summary | Severity | Status |
|----|---------|----------|--------|
| GAP-D60-01 | Profitability report heading says "Project Profitability Report" and column says "Project" instead of "Matter Profitability Report" / "Matter" when legal-za profile active. | LOW | OPEN |
| GAP-D60-02 | Customer column in profitability report shows "—" (dash) for all rows. Customer/client association not populated in report data despite invoices existing. | MEDIUM | OPEN |

## Console Errors

0 errors during Day 60 testing.

## Day 30+45+60 Overall Assessment

### What Works Well
- Invoice generation from unbilled time entries (full flow)
- VAT calculation (15% per line, correct totals)
- Invoice lifecycle: DRAFT -> APPROVED (assigns INV number) -> SENT (sends email) -> PAID (records payment)
- Email delivery via Mailpit with correct formatting
- Sequential invoice numbering (INV-0001)
- Court date postponement with reason tracking
- Payment recording with reference and history
- Profitability report with accurate revenue/cost/margin data
- Export CSV/PDF buttons on reports
- Resources capacity grid
- Legal terminology in breadcrumbs and detail pages (partial)

### Critical Blockers
1. **GAP-D30-01** (HIGH): LSSA tariff rates show NaN -- cannot add tariff lines to invoices
2. **GAP-D30-02** (HIGH): Add manual line item fails with backend parse error -- blocks disbursements and fixed-fee invoicing
3. **GAP-D0-02** (existing, WONT_FIX): No trust account creation -- blocks all trust, interest, investment, and reconciliation features

### Moderate Issues
4. **GAP-D45-01** (MEDIUM): Court date postponement replaces entry vs creating new alongside old
5. **GAP-D45-02** (MEDIUM): No auto-prescription tracking from matter type
6. **GAP-D60-02** (MEDIUM): Customer column blank in profitability report

### Terminology Gaps (Low)
7. **GAP-D30-03**: "Invoices"/"New Invoice" headings instead of "Fee Notes"/"New Fee Note"
8. **GAP-D60-01**: "Project Profitability" instead of "Matter Profitability"
9. **GAP-D30-05**: "{client} - {type}" placeholder in invoice Project column (existing GAP-D1-07)
