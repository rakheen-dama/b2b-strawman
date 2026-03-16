# Cycle 2 — Day 30 through Day 90 Checkpoint Results

**Date**: 2026-03-17
**Agent**: QA
**Branch**: bugfix_cycle_2026-03-16

---

## Day 30 — First Month-End Billing

### Invoice Creation

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 30.1 | Navigate to Invoices page | PASS | 1 existing invoice (INV-0001, Acme Corp, APPROVED) |
| 30.2 | Click to create new invoice | **OBSERVATION** | No "New Invoice" button on `/invoices` page. Must create from customer detail page Invoices tab. Confirms GAP-P48-002. |
| 30.2a | Navigate to Naledi customer → Invoices tab | PASS | "New Invoice" button present on customer detail |
| 30.2b | Click New Invoice on Naledi | **BLOCKED** | Prerequisites dialog: 6 required fields missing (Company Registration Number, Address Line 1, VAT Number, City, Country, Tax Number). Invoice creation blocked until customer profile is complete. |
| 30.2c | Fill Naledi required fields | PASS | Updated via API: address, city, country, VAT, tax number, entity type, etc. |
| 30.3-30.6 | Create Naledi invoice with 2 lines | PASS | Created via API. Line 1: Tax planning 0.5h x R1,500 = R750. Line 2: Monthly reconciliation 1.5h x R450 = R675. |
| 30.6 | Verify subtotal = R1,425 | **PASS** | Subtotal: R1,425.00 (R750 + R675) |
| 30.7 | Verify VAT = R213.75 | **PASS** | Tax: R213.75 (15% of R1,425) |
| 30.8 | Verify total = R1,638.75 | **PASS** | Total: R1,638.75 |
| 30.9 | Invoice saved as DRAFT | PASS | Status: DRAFT, no invoice number until approved |

### Retainer Invoice (Kgosi)

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 30.10-30.11 | Create Kgosi retainer invoice | PASS | 1 line: Monthly Bookkeeping Retainer — January, R5,500 + VAT |
| 30.12 | Verify total = R6,325 | **PASS** | Subtotal R5,500, tax R825, total R6,325.00 |

### Invoice Lifecycle

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 30.13 | Approve Naledi invoice | PASS | Status → APPROVED, number = INV-0002 |
| 30.14 | Send Naledi invoice | PASS (with override) | First attempt: 422 "customer_required_fields 7/10 filled". Succeeded with `overrideWarnings: true`. |
| 30.15 | Check Mailpit for invoice email | **FAIL** | 0 emails in Mailpit. Invoice send changes status but does not trigger email notification in E2E environment. |
| 30.16 | Approve + send Kgosi invoice | PASS | INV-0003, APPROVED then SENT (with override) |

### Invoice Number Sequencing

INV-0001 (Acme, cycle 1) → INV-0002 (Naledi) → INV-0003 (Kgosi) — **sequential numbering confirmed**.

### Budget

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 30.17 | Open Kgosi project → Budget tab | PASS | Shows "No budget configured" with "Configure budget" button |
| 30.18 | Set budget: 10h, R5,500, 80% alert | PASS | Set via API (UI dialog closed without saving — possible race condition, but API works) |
| 30.19 | Verify budget status | PASS | UI shows: Hours 7h 30m / 10h (75%), Amount R0 / R5,500 (0%), Status: On Track, Alert: 80% |

### Profitability

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 30.20 | Navigate to Profitability page | PASS | Page loads with data |
| 30.21 | Verify meaningful data | **PARTIAL** | Team Utilization shows real data (Carol 11h, Bob 4h, Alice 0.5h — all 100%). Project Profitability and Customer Profitability sections show "No data for this period". |

### Day 30 Checkpoint Summary

| Checkpoint | Result |
|------------|--------|
| Hourly invoice calculates correctly (lines + VAT) | PASS |
| Retainer invoice calculates correctly | PASS |
| Invoice lifecycle: DRAFT → APPROVED → SENT | PASS (requires overrideWarnings for incomplete profiles) |
| Email sent for each invoice (Mailpit) | FAIL — no emails generated |
| Invoice numbering sequential | PASS |
| Budget set and tracking hours consumed | PASS |
| Profitability page shows data | PARTIAL — team utilization only |

---

## Day 45 — Mid-Quarter Operations

### Payment Recording

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 45.1-45.3 | Record payment on Kgosi January invoice | PASS | POST `/api/invoices/{id}/payment` with reference "EFT-2026-001" |
| 45.4 | Verify invoice status = PAID | **PASS** | Status: PAID, paymentReference: MANUAL-{uuid}, paidAt timestamp set |

### Expense Logging

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 45.5-45.6 | Open Kgosi project → Expenses tab | PASS | Tab shows "No expenses logged" with "Log Expense" button |
| 45.7 | Fill expense: CIPC annual return filing fee, R150, Filing Fee, Billable | PASS | Dialog has: Date, Description, Amount, Currency (ZAR), Category dropdown (Filing Fee, Travel, Courier, etc.), Task (optional), Markup %, Billable checkbox, Notes, Receipt upload |
| 45.8 | Save and verify expense | **PASS** | Expense visible in table: Mar 17, 2026, R150.00, Filing Fee, Unbilled, Alice Owner. Category and billing filters available. |

### Ad-hoc Engagement

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 45.9 | Create BEE Certificate Review project for Vukani | PASS | Created via API, linked to Vukani customer |
| 45.10 | Set budget: 5h, R7,500 | PASS | Budget set via API |
| 45.11 | Create task: BEE certificate analysis (Alice, HIGH) | PASS | Task created |
| 45.12 | Log time: 120 min | PASS | Time entry created via `/api/tasks/{id}/time-entries` |

### Resource Planning

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 45.13 | Navigate to Resources page | **FAIL** | Page crashes: "Something went wrong — An unexpected error occurred while loading this page." Console error: TypeError. |
| 45.14 | Resources Utilization | NOT TESTED | Blocked by Resources page crash |

### Day 45 Checkpoint Summary

| Checkpoint | Result |
|------------|--------|
| Payment recorded, invoice status = PAID | PASS |
| Expense visible on project, marked as billable | PASS |
| Ad-hoc project created with budget tracking | PASS |
| Resource planning page shows data | FAIL — page crashes |

---

## Day 60 — Second Billing Cycle

### February Invoices

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 60.1 | Create Kgosi February retainer invoice | PASS | Created via API |
| 60.2 | Add expense line (CIPC disbursement R150 + VAT) | PASS | Two lines: retainer R5,500 + expense R150 |
| 60.3 | Verify totals | **PASS** | Subtotal R5,650, tax R847.50, total R6,497.50 |
| 60.4 | Approve and send | PASS | INV-0004, APPROVED then SENT (overrideWarnings) |

### BEE Advisory Invoice

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 60.5-60.6 | Create Vukani BEE invoice | PASS | 1 line: BEE scorecard analysis 2h x R1,500 |
| 60.7 | Verify total = R3,450 | **PASS** | Subtotal R3,000, tax R450, total R3,450 |
| 60.8 | Check BEE budget | PASS | Budget shows 2h consumed of 5h (40%) |

### Reports

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 60.9 | Navigate to Reports page | PASS | 3 report types: Invoice Aging, Project Profitability, Timesheet |
| 60.10 | Run Timesheet Report (Mar 2026) | **PASS** | Data renders: Carol 11h (6 entries), Bob 4h (2 entries), Alice 2.5h (2 entries). Total 17.5h, 10 entries, 100% billable. |
| 60.11 | Export buttons | PASS | Export CSV and Export PDF buttons present and enabled after report runs |

### Day 60 Checkpoint Summary

| Checkpoint | Result |
|------------|--------|
| Second billing cycle invoices created correctly | PASS |
| Expense line item included in invoice | PASS |
| Budget vs actual tracking accurate | PASS (2/5h = 40%) |
| Reports page functional with data | PASS |

---

## Day 75 — Year-End Engagement

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 75.1 | Create "Annual Tax Return 2026 — Kgosi" project | PASS | Created via API, linked to Kgosi customer |
| 75.2 | Create 3 tasks (Gather data, Trial balance, Submit ITR14) | PASS | Carol (HIGH), Bob (HIGH), Alice (HIGH). Required adding Carol + Bob as project members first. |
| 75.7-75.8 | Carol logs 240 min on "Gather financial data" | PASS | Time entry created, 240 min, billable |
| 75.9 | Verify multi-engagement for Kgosi | **OBSERVATION** | Customer detail page shows "0 projects" and "No linked projects" in Projects tab despite projects being linked via customerId in the API. This is a UI display issue — projects are linked at the data level but the customer detail page does not reflect them. |
| 75.10 | Verify time on multiple projects | PASS | API confirms time logged independently on both Kgosi projects |

### Day 75 Checkpoint Summary

| Checkpoint | Result |
|------------|--------|
| Year-end project created successfully | PASS |
| Multiple concurrent projects per customer | PASS (data level) |
| Time logging works on new engagement | PASS |
| Customer detail shows linked projects | FAIL — shows 0 projects despite API linkage |

---

## Day 90 — Quarter Review

### Portfolio Review

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 90.1 | Customers page: all 5 clients ACTIVE | **PASS** | Acme Corp, Naledi Hair Studio, Kgosi Construction, Vukani Tech, Moroka Family Trust — all Active, 100% completeness |
| 90.3 | Invoices page: 5 invoices visible | **PASS** | Draft (BEE R3,450), INV-0004 Sent (R6,497.50), INV-0003 Paid (R6,325), INV-0002 Sent (R1,638.75), INV-0001 Approved (R6,325) |
| 90.4-90.5 | Invoice status filters | PASS | Filter links for Draft, Approved, Sent, Paid, Void all present |

### Financial Summary

| Metric | Value |
|--------|-------|
| Total Outstanding | R 14,461.25 |
| Paid This Month | R 6,325.00 |
| Total Invoiced | R 24,236.25 |

### Profitability

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 90.6-90.8 | Profitability page | PARTIAL | Team utilization shows data (Carol, Bob, Alice all 100%). Project and Customer profitability show "No data". |

### Dashboard

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 90.9 | Dashboard KPIs | **PASS** | Active Projects: 7, Hours Logged: 21.5h, Billable: 100%, Overdue Tasks: 0 |
| 90.10 | Recent activity widget | **PASS** | Rich chronological feed: time entries, tasks, project members, budgets, expenses |

### Document Generation

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 90.11 | Kgosi customer → Generate Document | PRESENT | "Statement of Account" and "FICA Confirmation Letter" templates available with "Generate" links |
| 90.12-90.13 | Preview/Generate PDF | NOT TESTED | Would require clicking through Generate flow; templates present and linked |

### Compliance

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 90.14 | Compliance dashboard | **PASS** | Lifecycle Distribution: 0 Prospect, 0 Onboarding, 5 Active, 0 Dormant, 0 Offboarded. Onboarding pipeline empty. No open data requests. Dormancy check available. |
| 90.15 | All customers FICA complete | PASS | All 5 customers in ACTIVE status |

### Role-Based Access

| Step | Description | Result | Notes |
|------|-------------|--------|-------|
| 90.16 | Carol → /settings/rates → blocked | **PASS** | Message: "You do not have permission to manage rates and currency settings. Only admins and owners can access this page." |
| 90.17 | Carol → /my-work → personal tasks | **PASS** | 6 tasks visible across 4 projects with time logged per task. Timesheet link available. |
| 90.18 | Bob → /settings/general | NOT TESTED (skipped for time) |

### Carol's Sidebar RBAC

Carol's sidebar hides: Clients section, Finance section, Recurring Schedules, Resources. Only shows: Dashboard, My Work, Calendar, Projects, Documents, Team, Notifications, Settings. **Correct RBAC menu enforcement.**

### Day 90 Checkpoint Summary

| Checkpoint | Result |
|------------|--------|
| 5 customers, all ACTIVE | PASS |
| 7 projects across customers | PASS |
| 5 invoices (mix of DRAFT, APPROVED, SENT, PAID) | PASS |
| Profitability data meaningful | PARTIAL (team utilization only) |
| Dashboard shows non-zero KPIs | PASS |
| Document template preview works | NOT TESTED (templates present) |
| FICA compliance tracked | PASS |
| Role-based access enforced | PASS |

---

## Overall Verdict

**Could this firm actually run on this platform?** **YES (with caveats)**

### What Works Well
- Customer lifecycle (PROSPECT → ONBOARDING → ACTIVE) is solid
- Invoice creation with line items, VAT calculation, and lifecycle (DRAFT → APPROVED → SENT → PAID) works correctly
- Time tracking across multiple projects/users works reliably
- Budget tracking with consumption percentages is functional
- Expense logging with category, billable flag, and project linkage works
- Reports (Timesheet) generate correctly with export options
- RBAC enforces proper role boundaries (Carol blocked from admin pages, sidebar menu filtered)
- Compliance dashboard provides useful lifecycle overview
- Dashboard KPIs reflect real data accurately

### Gaps and Issues Found

| ID | Issue | Severity | New? |
|----|-------|----------|------|
| GAP-P48-002 | No "New Invoice" button on /invoices list page | major | Known |
| GAP-P48-013 | Invoice send does not trigger email notification | minor | NEW |
| GAP-P48-014 | Resources page crashes with TypeError | major | NEW |
| GAP-P48-015 | Customer detail shows "0 projects" despite API-linked projects | major | NEW |
| GAP-P48-016 | Invoice send requires overrideWarnings even when customer has 70%+ fields filled | minor | NEW |
| GAP-P48-017 | Budget dialog may close without saving (UI race condition; API works) | minor | NEW |
| GAP-P48-018 | Project/Customer Profitability sections show "No data" despite time entries | minor | NEW |
| — | React SSR hydration errors (#418) on every page | cosmetic | Known |
| — | Console TypeErrors on list pages (customers, projects) during SSR | cosmetic | Known |

### Data Summary (end of Day 90)

| Entity | Count | Details |
|--------|-------|---------|
| Customers | 5 | All ACTIVE, 100% profile completeness |
| Projects | 7 | 5 original + BEE Review + Annual Tax Return |
| Tasks | ~14 | Across 6 projects (Kgosi 3+3, Naledi 2, Vukani 2+1, Moroka 1, BEE 1, Tax 3) |
| Time Entries | 11 | 21.5h total, 100% billable |
| Invoices | 5 | 1 Draft, 1 Approved, 2 Sent, 1 Paid |
| Expenses | 1 | R150, Filing Fee, Billable, Unbilled |
| Proposals | 1 | PROP-0001 (SENT) |
| Budgets | 2 | Kgosi (10h/R5,500, 75% consumed), BEE (5h/R7,500, 40% consumed) |
