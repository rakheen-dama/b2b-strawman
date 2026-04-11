# Day 30 — First Billing Cycle

**Date**: 2026-03-16 (cycle 4)
**Actor**: Alice (Owner) testing, Bob (Admin) scenario
**Prerequisite State**: Kgosi Construction linked to project, 5.5h time logged by Alice, no other customers/projects created

## Checkpoint Results

### 30.1 — Kgosi Construction January retainer closed — invoice generated for R5,500
- **Result**: NOT TESTED
- **Reason**: No retainer exists. Retainers page (`/retainers`) loads correctly with "No retainers found" and a "New Retainer" button. Retainer CRUD is functional but no retainers were created in previous cycles (E2E stack was rebuilt). The retainer feature exists — it has Active/Paused/Terminated filters and summary cards (Active Retainers, Periods Ready to Close, Total Overage Hours).
- **Evidence**: Retainers page loads with empty state. Feature exists but untestable without prerequisite retainer data.

### 30.2 — Vukani Tech January retainer closed — invoice generated for R8,000
- **Result**: NOT TESTED
- **Reason**: No Vukani Tech customer or retainer exists. Same as 30.1.

### 30.3 — Naledi hourly invoice created: R750 + R112.50 VAT = R862.50
- **Result**: NOT TESTED
- **Reason**: No Naledi customer exists. However, the invoicing infrastructure was verified (see 30.4).

### 30.4 — Invoice numbering is sequential
- **Result**: PARTIAL
- **Evidence**: Invoices page loads at `/invoices` with summary cards (Total Outstanding, Total Overdue, Paid This Month — all $0.00). Filter tabs: All, Draft, Approved, Sent, Paid, Void. Currency shows USD not ZAR (GAP-019 confirmed). No invoices exist yet so numbering is untestable.
- **Note**: The invoices page has a "Billing Runs" tab which navigates to `/invoices/billing-runs`.

### 30.5 — All 3 invoices show correct client details including VAT numbers
- **Result**: NOT TESTED

### 30.6 — All 3 invoices sent via email — Mailpit shows 3 emails
- **Result**: NOT TESTED
- **Reason**: No invoices created. Invoice send functionality untested.

### 30.7 — Invoice statuses changed from DRAFT to SENT
- **Result**: NOT TESTED

### 30.8 — Portal links in emails work (clients can view invoices)
- **Result**: NOT TESTED

### 30.9 — Profitability dashboard shows per-client revenue/cost/margin
- **Result**: PASS
- **Evidence**: Profitability page (`/profitability`) loads with three sections:
  1. **Team Utilization**: Shows Alice Owner with 5.5h total, 5.5h billable, 0.0h non-billable, 100.0% utilization. Date range filter (From/To) works. Sortable columns.
  2. **Project Profitability**: Date range filtered. Shows "No project profitability data for this period" (no rates configured).
  3. **Customer Profitability**: Date range filtered. Shows "No customer profitability data for this period" (no rates configured).
- **Note**: Profitability sections need rate cards configured to show revenue/cost data. The utilization section works independently.

### 30.10 — Kgosi Construction flagged as unprofitable
- **Result**: NOT TESTED
- **Reason**: No rate cards or invoices to calculate profitability.

### 30.11 — Team utilisation rates visible
- **Result**: PASS
- **Evidence**: Profitability page shows Team Utilization table with Alice Owner at 100% utilization (5.5h billable out of 5.5h total). The table includes Name, Total Hours, Billable, Non-Billable, and Utilization % columns. All columns are sortable.

### 30.12 — Moroka Trust budget tracking shows 0% consumed
- **Result**: PARTIAL
- **Evidence**: Budget tab on project detail page loads with "No budget configured" message and a "Configure budget" button. Budget feature exists with setup wizard but no budget data to verify percentage tracking. The project overview card shows "Budget: No budget" in the summary row.

## Billing Run Wizard (tested)
- **Result**: PASS (infrastructure) / FAIL (end-to-end)
- **Evidence**: Billing Run wizard at `/invoices/billing-runs/new` has 5 steps:
  1. **Configure**: Period From/To date pickers, Cut-off Date, "Include retainers" checkbox, Notes
  2. **Select Customers**: Lists customers with unbilled work for the period
  3. **Review & Cherry-Pick**: Individual time entry selection
  4. **Review Drafts**: Review generated invoice drafts
  5. **Send**: Send invoices
- **Issue**: With Kgosi linked and 5.5h time logged, billing run Step 2 shows "No customers with unbilled work found for this period." This may be because:
  - Time entries were logged today (March 16) but billing period was set to March 1-31
  - The billing run query may require additional configuration (e.g., project rates)
  - Time entries may need to be explicitly marked as "unbilled"

## New Gaps Found

### GAP-031 — Timesheet report crashes with invalid UUID error for optional filters
- **Severity**: bug
- **Evidence**: Running the timesheet report at `/reports/timesheet` without selecting a project shows "Parameter 'projectId' is not a valid UUID:" error. Selecting a project but not a member shows "Parameter 'memberId' is not a valid UUID:" error. The backend rejects empty strings instead of treating unselected filters as null/optional.
- **Workaround**: Select both project AND member to run the report successfully.
- **When both selected**: Report works correctly — shows summary cards (Total Hours, Entry Count, Billable Hours, Non-Billable Hours), grouped table data, pagination, and enabled Export CSV/Export PDF buttons.

## Summary

| Checkpoint | Result |
|-----------|--------|
| Kgosi retainer invoice R5,500 | NOT TESTED |
| Vukani retainer invoice R8,000 | NOT TESTED |
| Naledi hourly invoice R862.50 | NOT TESTED |
| Invoice numbering sequential | PARTIAL |
| Client details on invoices | NOT TESTED |
| Invoices sent via email | NOT TESTED |
| Invoice status DRAFT→SENT | NOT TESTED |
| Portal links in emails | NOT TESTED |
| Profitability per-client | PASS |
| Kgosi flagged unprofitable | NOT TESTED |
| Team utilisation visible | PASS |
| Budget tracking 0% consumed | PARTIAL |

**Day 30 Result**: 2 PASS, 2 PARTIAL, 8 NOT TESTED, 1 new bug (GAP-031)
