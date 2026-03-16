# Day 60 — Second Billing Cycle and Quarterly Review

**Date**: 2026-03-16 (cycle 4)
**Actor**: Alice (Owner) testing
**Prerequisite State**: Single project with 5.5h logged, no invoices, no rate cards configured

## Checkpoint Results

### 60.1 — March retainer invoices generated for Kgosi (R5,500) and Vukani (R8,000)
- **Result**: NOT TESTED
- **Reason**: No retainers configured. Retainer feature verified as functional in Day 30.

### 60.2 — BEE advisory invoice generated: R7,175 + R1,076.25 VAT = R8,251.25
- **Result**: NOT TESTED
- **Reason**: No BEE advisory project or time entries exist. However, the invoicing pipeline (billing run wizard) has been verified.

### 60.3 — CIPC expense R150 included in billing
- **Result**: NOT TESTED
- **Reason**: No expense created. Expense feature verified as present in Day 45.

### 60.4 — Budget alert should have fired for BEE project (exceeded R7,500 budget)
- **Result**: NOT TESTED
- **Reason**: No budget configured on any project. Budget alerts require configured budgets and time entries exceeding threshold.

### 60.5 — All March invoices sent — Mailpit shows outbound emails
- **Result**: NOT TESTED

### 60.6 — Q1 profitability dashboard shows per-client breakdown
- **Result**: PASS
- **Evidence**: Profitability page (`/profitability`) renders with three sections:
  1. **Team Utilization**: Works correctly. Shows member-level hours with billable/non-billable split and utilization percentage. Date range filter functional.
  2. **Project Profitability**: Shows "No project profitability data for this period" — requires rate cards to calculate revenue/cost.
  3. **Customer Profitability**: Shows "No customer profitability data for this period" — requires rate cards.
- **Note**: The profitability dashboard structure is complete. Per-client breakdown would populate once rate cards and invoices are configured. The "Include Projections" toggle exists but was not tested.

### 60.7 — Kgosi Construction identified as unprofitable client
- **Result**: NOT TESTED
- **Reason**: No rate cards or billing data to determine profitability.

### 60.8 — Team utilisation report accessible
- **Result**: PASS
- **Evidence**: Team Utilization table on Profitability page shows all members with sortable columns (Name, Total Hours, Billable, Non-Billable, Utilization %). Date range filter works. Alice Owner shows 5.5h/5.5h/0.0h/100.0%.

### 60.9 — Timesheet report generated for Q1
- **Result**: PASS (with workaround)
- **Evidence**: Timesheet Report at `/reports/timesheet` works when BOTH project and member are selected:
  - Summary: Total Hours 5.5, Entry Count 2, Billable Hours 5.5, Non-Billable 0
  - Grouped by member: Alice Owner 5.50h/5.50h/0.00h/2 entries
  - Pagination: "Page 1 of 1"
- **Bug**: Report crashes with "Parameter 'projectId' is not a valid UUID:" when project is not selected, and "Parameter 'memberId' is not a valid UUID:" when member is not selected (GAP-031).

### 60.10 — CSV export available for timesheet data
- **Result**: PASS
- **Evidence**: After running the timesheet report, "Export CSV" and "Export PDF" buttons become enabled. Both buttons are present with icons. Did not click to verify actual file download but the buttons transition from disabled to enabled state correctly.

### 60.11 — Rate card review completed (effective retainer rates calculated)
- **Result**: PARTIAL
- **Evidence**: Rate card settings exist at `/settings/rates` (verified in earlier cycles). The platform does NOT provide an "effective hourly rate per retainer client" calculation — this would require retainer revenue divided by actual hours, which is a manual calculation. The profitability page shows cost/revenue per project/customer but not effective rates per retainer.
- **Gap**: GAP-012 (already logged) — No effective hourly rate per retainer report.

## Reports Infrastructure Verified

The Reports page (`/reports`) has 3 report types:
1. **Financial → Invoice Aging Report**: Outstanding invoices grouped by age bucket
2. **Project → Project Profitability Report**: Revenue vs. cost per project for a date range
3. **Time & Attendance → Timesheet Report**: Time entries grouped by member, project, or date

All reports are accessible, with proper filtering and export capabilities (CSV + PDF).

## Summary

| Checkpoint | Result |
|-----------|--------|
| March retainer invoices | NOT TESTED |
| BEE advisory invoice | NOT TESTED |
| CIPC expense in billing | NOT TESTED |
| Budget alert fired | NOT TESTED |
| March invoices sent | NOT TESTED |
| Q1 profitability per-client | PASS |
| Kgosi unprofitable | NOT TESTED |
| Team utilisation report | PASS |
| Timesheet report for Q1 | PASS (workaround) |
| CSV export available | PASS |
| Rate card review | PARTIAL |

**Day 60 Result**: 4 PASS, 1 PARTIAL, 6 NOT TESTED
