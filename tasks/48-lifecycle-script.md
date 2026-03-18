# QA Test Plan: 90-Day SA Accounting Firm Lifecycle

**Target**: DocTeams platform (post-Phase 48)
**Vertical**: Small SA accounting firm ("Thornton & Associates")
**Stack**: E2E mock-auth on port 3001 / backend 8081 / Mailpit 8026

**Prereqs**: `bash compose/scripts/e2e-up.sh` running. Login at `http://localhost:3001/mock-login`.
**Users**: Alice (Owner), Bob (Admin), Carol (Member)
**Org**: e2e-test-org

**How to use**: Walk through each step in order. Check the box when done. Note any failures with the step number.

---

## Day 0 — Firm Setup

Actor: **Alice** (Owner)

- [ ] **0.1** Login as Alice at `/mock-login` → lands on `/org/e2e-test-org/dashboard`
- [ ] **0.2** Dashboard shows a getting-started or onboarding card
- [ ] **0.3** Navigate to **Settings > General** (`/settings/general`)
- [ ] **0.4** Set default currency to **ZAR** → Save → verify it persists on reload
- [ ] **0.5** Set brand colour to **#1B5E20** → Save → verify colour chip shows green
- [ ] **0.6** Navigate to **Settings > Rates** (`/settings/rates`)
- [ ] **0.7** Create org-level billing rate for Alice: **R1,500/hr** (ZAR)
- [ ] **0.8** Create org-level billing rate for Bob: **R850/hr**
- [ ] **0.9** Create org-level billing rate for Carol: **R450/hr**
- [ ] **0.10** Create cost rate for Alice: **R600/hr**
- [ ] **0.11** Create cost rate for Bob: **R400/hr**
- [ ] **0.12** Create cost rate for Carol: **R200/hr**
- [ ] **0.13** Navigate to **Settings > Tax** (`/settings/tax`)
- [ ] **0.14** Create tax rate: Name = **VAT**, Rate = **15%**, Active = yes
- [ ] **0.15** Navigate to **Settings > Team** (`/settings/team`) → verify Alice (Owner), Bob (Admin), Carol (Member) are listed
- [ ] **0.16** Navigate to **Settings > Custom Fields** (`/settings/custom-fields`) → verify customer fields exist (accounting pack should be pre-seeded)
- [ ] **0.17** Navigate to **Settings > Templates** (`/settings/templates`) → verify accounting template pack is listed
- [ ] **0.18** Navigate to **Settings > Automations** (`/settings/automations`) → note how many rules exist (should be pre-seeded from accounting pack)

**Day 0 Checkpoints**:
- [ ] Currency shows ZAR on settings page
- [ ] 3 billing rates visible on rates page
- [ ] VAT 15% visible on tax page
- [ ] Custom fields exist for CUSTOMER entity type
- [ ] At least 1 document template present
- [ ] Automation rules are listed (may be 0 if pack not auto-applied — note this)

---

## Day 1 — First Client Onboarding (Kgosi Construction)

### Create the customer

Actor: **Bob** (Admin)

- [ ] **1.1** Login as Bob → Navigate to **Customers** (`/customers`)
- [ ] **1.2** Click **New Customer** (or equivalent button)
- [ ] **1.3** Fill: Name = **Kgosi Construction (Pty) Ltd**, Email = **thabo@kgosiconstruction.co.za**, Phone = **+27-11-555-0100**
- [ ] **1.4** If custom fields are visible in the form, fill: Notes = "Pty Ltd, FYE 28 Feb, VAT vendor"
- [ ] **1.5** Save → verify customer appears in list with status **PROSPECT**
- [ ] **1.6** Click into customer detail → verify lifecycle badge shows **PROSPECT**

### FICA/KYC onboarding

- [ ] **1.7** On customer detail, find a **lifecycle transition** action (button or dropdown)
- [ ] **1.8** Transition Kgosi to **ONBOARDING** → verify badge updates
- [ ] **1.9** Check for an **Onboarding** or **Compliance** tab → click it
- [ ] **1.10** Verify a FICA checklist was auto-instantiated with items
- [ ] **1.11** Mark **first checklist item** as complete (e.g. "Certified ID Copy")
- [ ] **1.12** Mark **second checklist item** as complete (e.g. "Company Registration")
- [ ] **1.13** Verify progress indicator updates (e.g. 2/N complete)

### Information request

- [ ] **1.14** Navigate to customer detail → **Requests** tab (or **Compliance > Requests**)
- [ ] **1.15** Click **New Information Request**
- [ ] **1.16** Fill: Subject = "FICA Documents Required", add 3 items: "Certified ID Copy", "Company Registration (CM29)", "Proof of Address"
- [ ] **1.17** Save → then click **Send**
- [ ] **1.18** Open **Mailpit** (`http://localhost:8026`) → verify notification email was sent

### Proposal (engagement letter)

Actor: **Alice** (Owner)

- [ ] **1.19** Login as Alice → Navigate to **Proposals** (`/proposals`)
- [ ] **1.20** Click **New Proposal**
- [ ] **1.21** Fill: Title = "Monthly Bookkeeping — Kgosi Construction", Customer = Kgosi, Fee Model = **Retainer**, Amount = **R5,500**, Hours = **10**, Expiry = 30 days from now
- [ ] **1.22** Save → verify proposal appears in list as **DRAFT**
- [ ] **1.23** Open proposal → click **Send** → verify status changes to **SENT**
- [ ] **1.24** Check Mailpit for proposal notification

### Activate customer

- [ ] **1.25** Navigate to Kgosi customer detail
- [ ] **1.26** Complete all remaining FICA checklist items
- [ ] **1.27** Verify customer auto-transitions to **ACTIVE** (or manually transition if needed)
- [ ] **1.28** Verify lifecycle badge shows **ACTIVE**

### Create engagement (project)

- [ ] **1.29** Navigate to **Projects** (`/projects`) → click **New Project**
- [ ] **1.30** Fill: Name = "Monthly Bookkeeping — Kgosi", Customer = Kgosi Construction
- [ ] **1.31** Save → verify project appears in list
- [ ] **1.32** Open project → verify customer is linked

### Create retainer

- [ ] **1.33** Navigate to **Retainers** (`/retainers`) → click **New Retainer**
- [ ] **1.34** Fill: Customer = Kgosi, Name = "Kgosi Monthly Bookkeeping", Type = Hour Bank, Frequency = Monthly, Start = 1st of current month, Hours = 10, Fee = R5,500
- [ ] **1.35** Save → verify retainer appears in list as **ACTIVE**

### Create tasks

- [ ] **1.36** Open the Kgosi project → **Tasks** tab → create task: "Capture bank statements and receipts" assigned to **Carol**, priority **HIGH**
- [ ] **1.37** Create task: "Reconcile accounts" assigned to **Carol**, priority **HIGH**
- [ ] **1.38** Create task: "Client liaison and follow-up" assigned to **Bob**, priority **MEDIUM**

**Day 1 Checkpoints**:
- [ ] Customer created, transitioned PROSPECT → ONBOARDING → ACTIVE
- [ ] FICA checklist instantiated and items completable
- [ ] Information request sent, email in Mailpit
- [ ] Proposal created, sent, email in Mailpit
- [ ] Project created and linked to customer
- [ ] Retainer created with correct terms
- [ ] 3 tasks created on the project

---

## Day 2–3 — Additional Client Onboarding

Actor: **Bob** (Admin), then **Alice** for retainer

Repeat the Day 1 create-customer flow for each client below. For each: create customer → transition to ACTIVE (complete FICA) → create project → create tasks.

### Naledi Hair Studio (sole proprietor, hourly billing)

- [ ] **2.1** Create customer: **Naledi Hair Studio**, email = naledi@naledihair.co.za
- [ ] **2.2** Transition to ACTIVE (complete FICA checklist)
- [ ] **2.3** Create project: "Monthly Bookkeeping — Naledi"
- [ ] **2.4** Create task: "Monthly reconciliation" → assigned to Carol
- [ ] **2.5** Create task: "Tax advisory" → assigned to Alice

### Vukani Tech Solutions (retainer + hourly overflow)

- [ ] **2.6** Create customer: **Vukani Tech Solutions (Pty) Ltd**, email = finance@vukanitech.co.za
- [ ] **2.7** Transition to ACTIVE
- [ ] **2.8** Create project: "Monthly Bookkeeping — Vukani"
- [ ] **2.9** Create tasks: "Monthly reconciliation" (Carol), "Sage accounts reconciliation" (Carol)
- [ ] **2.10** Create retainer (Alice): Customer = Vukani, Name = "Vukani Monthly Retainer", Hours = 8, Fee = R4,500/month

### Moroka Family Trust (fixed fee)

- [ ] **2.11** Create customer: **Moroka Family Trust**, email = trustees@morokatrust.co.za
- [ ] **2.12** Transition to ACTIVE
- [ ] **2.13** Create project: "Annual Administration — Moroka Trust"
- [ ] **2.14** Create task: "Annual trust return" → assigned to Bob

**Day 2–3 Checkpoints**:
- [ ] 4 total customers visible on customer list, all ACTIVE
- [ ] 4 projects visible on projects page
- [ ] 2 retainers visible on retainers page (Kgosi, Vukani)
- [ ] Different customers have different task sets

---

## Day 7 — First Week of Work

### Carol (Junior) — bookkeeping

- [ ] **7.1** Login as Carol → navigate to **My Work** (`/my-work`)
- [ ] **7.2** Verify assigned tasks are visible (Kgosi: 2 tasks, Naledi: 1, Vukani: 2)
- [ ] **7.3** Open Kgosi project → Tasks tab → click "Capture bank statements" task
- [ ] **7.4** Mark task status → **In Progress**
- [ ] **7.5** Click **Log Time** → fill: **180 minutes** (3 hrs), Date = today, Description = "Captured bank statements and receipts for January", Billable = yes
- [ ] **7.6** Save → verify time entry appears in the Time tab
- [ ] **7.7** Check that the rate snapshot is **R450** (Carol's billing rate)
- [ ] **7.8** Open Vukani project → log time on "Sage accounts reconciliation": **120 min**, "Reconciled Sage accounts"

### Bob (Admin) — client communication

- [ ] **7.9** Login as Bob → open Kgosi project
- [ ] **7.10** Find comments section (may be a tab or inline) → add comment: **"Missing February bank statements — sent follow-up email to Thabo"**
- [ ] **7.11** Verify comment appears with Bob's name and timestamp
- [ ] **7.12** Log time on "Client liaison" task: **60 min**, "Client liaison — outstanding documentation follow-up"
- [ ] **7.13** Verify rate snapshot is **R850** (Bob's rate)

### Alice (Owner) — advisory

- [ ] **7.14** Login as Alice → open Naledi project
- [ ] **7.15** Log time on "Tax advisory" task: **30 min**, "Tax planning discussion — provisional tax implications"
- [ ] **7.16** Verify rate snapshot is **R1,500** (Alice's rate)

### Verification

- [ ] **7.17** Navigate to Kgosi project → **Activity** tab → verify time entries and comment appear in feed
- [ ] **7.18** Navigate to **My Work** as Carol → verify tasks show updated status

**Day 7 Checkpoints**:
- [ ] Time entries created by 3 different users on 3 different projects
- [ ] Rate snapshots match each user's billing rate
- [ ] Task transitioned to IN_PROGRESS
- [ ] Comment visible on project
- [ ] Activity feed shows chronological events
- [ ] My Work page reflects correct task assignments

---

## Day 14 — Two Weeks In

### More time logging

Actor: **Carol**

- [ ] **14.1** Log time on Kgosi "Capture bank statements": 120 min, "Bank statement capture — week 2"
- [ ] **14.2** Log time on Kgosi "Reconcile accounts": 90 min, "Account reconciliation — January"
- [ ] **14.3** Log time on Vukani "Monthly reconciliation": 60 min, "Monthly reconciliation — January"
- [ ] **14.4** Log time on Naledi "Monthly reconciliation": 90 min, "Monthly reconciliation — Naledi"

Actor: **Bob**

- [ ] **14.5** Log time on Moroka "Annual trust return": 180 min, "Trust return data gathering"

### Notifications

Actor: **Bob**

- [ ] **14.6** Navigate to **Notifications** (`/notifications`) → note any automation-triggered notifications
- [ ] **14.7** Check Mailpit for any automated emails (FICA reminders, etc.)

**Day 14 Checkpoints**:
- [ ] Total time entries across all projects: ~10+
- [ ] Notification page loads and shows notifications (if any)

---

## Day 30 — First Month-End Billing

### Hourly invoice for Naledi

Actor: **Alice** (Owner)

- [ ] **30.1** Navigate to **Invoices** (`/invoices`)
- [ ] **30.2** Click to create a new invoice
- [ ] **30.3** Select customer = **Naledi Hair Studio**, project = "Monthly Bookkeeping — Naledi", currency = ZAR
- [ ] **30.4** Add line item: Description = "Tax planning discussion", Qty = 0.5, Unit Price = R1,500, Tax = VAT (15%)
- [ ] **30.5** Add line item: Description = "Monthly reconciliation", Qty = 1.5, Unit Price = R450, Tax = VAT (15%)
- [ ] **30.6** Verify subtotal = **R1,425** (R750 + R675)
- [ ] **30.7** Verify VAT = **R213.75** (15% of R1,425)
- [ ] **30.8** Verify total = **R1,638.75**
- [ ] **30.9** Save as DRAFT → verify invoice number is auto-generated

### Retainer invoice for Kgosi

- [ ] **30.10** Create invoice: Customer = Kgosi, Description = "Retainer — January", currency = ZAR
- [ ] **30.11** Add line: "Monthly Bookkeeping Retainer — January", Qty = 1, Unit Price = R5,500, Tax = VAT
- [ ] **30.12** Verify total = **R6,325** (R5,500 + 15% VAT)

### Invoice lifecycle

- [ ] **30.13** Open Naledi invoice → click **Approve** → verify status = **APPROVED**
- [ ] **30.14** Click **Send** → verify status = **SENT**
- [ ] **30.15** Check Mailpit → verify invoice email received
- [ ] **30.16** Repeat approve + send for Kgosi invoice

### Budget

- [ ] **30.17** Open Kgosi project → **Budget** tab
- [ ] **30.18** Set budget: Hours = **10**, Amount = **R5,500**, Currency = ZAR, Alert at 80%
- [ ] **30.19** Verify budget status shows hours consumed so far

### Profitability

- [ ] **30.20** Navigate to **Profitability** (`/profitability`)
- [ ] **30.21** Verify page loads with meaningful data (revenue, costs, margins)

**Day 30 Checkpoints**:
- [ ] Hourly invoice calculates correctly (lines + VAT)
- [ ] Retainer invoice calculates correctly
- [ ] Invoice lifecycle: DRAFT → APPROVED → SENT works
- [ ] Email sent for each invoice (check Mailpit)
- [ ] Invoice numbering sequence is sequential
- [ ] Budget set and tracking hours consumed
- [ ] Profitability page shows data

---

## Day 45 — Mid-Quarter Operations

### Payment recording

Actor: **Alice**

- [ ] **45.1** Open Kgosi January invoice
- [ ] **45.2** Click **Record Payment** (or payment action)
- [ ] **45.3** Enter reference = "EFT-2026-001" → submit
- [ ] **45.4** Verify invoice status changes to **PAID**

### Expense logging

Actor: **Bob**

- [ ] **45.5** Open Kgosi project → **Expenses** tab
- [ ] **45.6** Click **Add Expense** (or equivalent)
- [ ] **45.7** Fill: Date = today, Description = "CIPC annual return filing fee", Amount = R150, Category = Services, Billable = yes
- [ ] **45.8** Save → verify expense appears in list

### Ad-hoc engagement

Actor: **Alice**

- [ ] **45.9** Create new project: "BEE Certificate Review — Vukani", Customer = Vukani Tech
- [ ] **45.10** Set budget: Hours = 5, Amount = R7,500
- [ ] **45.11** Create task: "BEE certificate analysis" → assigned to Alice, priority HIGH
- [ ] **45.12** Log time: 120 min, "BEE scorecard analysis"

### Resource planning

- [ ] **45.13** Navigate to **Resources** (`/resources`) → verify allocation grid loads
- [ ] **45.14** Navigate to **Resources > Utilization** (`/resources/utilization`) → verify utilization data

**Day 45 Checkpoints**:
- [ ] Payment recorded, invoice status = PAID
- [ ] Expense visible on project, marked as billable
- [ ] Ad-hoc project created with budget tracking
- [ ] Resource planning page shows meaningful capacity data

---

## Day 60 — Second Billing Cycle

### February invoices

Actor: **Alice**

- [ ] **60.1** Create Kgosi February retainer invoice with line: "Monthly Bookkeeping Retainer — February" R5,500 + VAT
- [ ] **60.2** Add expense line to same invoice: "CIPC annual return filing — disbursement" R150 + VAT
- [ ] **60.3** Verify expense + retainer totals correctly on the invoice
- [ ] **60.4** Approve and send

### BEE advisory invoice

- [ ] **60.5** Create invoice for Vukani: "BEE Certificate Review"
- [ ] **60.6** Add line: "BEE scorecard analysis — 2 hrs" Qty = 2, Unit Price = R1,500 + VAT
- [ ] **60.7** Verify total = **R3,450** (R3,000 + 15% VAT)
- [ ] **60.8** Check BEE project budget tab → verify hours consumed = 2 of 5

### Reports

- [ ] **60.9** Navigate to **Reports** (`/reports`) → verify report list loads
- [ ] **60.10** Open a report (e.g. time tracking) → verify data renders
- [ ] **60.11** If export button exists → click CSV export → verify file downloads

**Day 60 Checkpoints**:
- [ ] Second billing cycle invoices created correctly
- [ ] Expense line item included in invoice
- [ ] Budget vs actual tracking accurate
- [ ] Reports page functional with data

---

## Day 75 — Year-End Engagement

### Create year-end project

Actor: **Alice**

- [ ] **75.1** Create project: "Annual Tax Return 2026 — Kgosi", Customer = Kgosi Construction
- [ ] **75.2** Create tasks: "Gather financial data" (Carol, HIGH), "Prepare trial balance" (Bob, HIGH), "Submit ITR14" (Alice, URGENT)

### Information request for tax documents

- [ ] **75.3** Navigate to Kgosi customer → Requests tab → New Information Request
- [ ] **75.4** Fill: Subject = "Annual Tax Return — Documents Required"
- [ ] **75.5** Add items: "Trial Balance" (required), "Bank Statements (12 months)" (required), "Loan Agreements" (optional), "Fixed Asset Register" (required)
- [ ] **75.6** Save → Send → check Mailpit for email

### Year-end work

Actor: **Carol**

- [ ] **75.7** Login as Carol → open "Annual Tax Return 2026 — Kgosi" project
- [ ] **75.8** Log time on "Gather financial data": 240 min, "Review client documents and begin data capture"

### Verify multi-engagement

- [ ] **75.9** Open Kgosi customer → Projects tab → verify both "Monthly Bookkeeping" AND "Annual Tax Return" projects are listed
- [ ] **75.10** Verify time logged on both projects doesn't conflict

**Day 75 Checkpoints**:
- [ ] Year-end project created successfully
- [ ] Information request sent with 4 items
- [ ] Multiple concurrent projects per customer work without conflict
- [ ] Time logging works on new engagement

---

## Day 90 — Quarter Review

### Portfolio review

Actor: **Alice**

- [ ] **90.1** Navigate to **Customers** → verify all 4 clients listed with ACTIVE status
- [ ] **90.2** Check customer lifecycle summary (if available as a widget or filter count)
- [ ] **90.3** Navigate to **Invoices** → verify 4+ invoices visible across clients
- [ ] **90.4** Filter by status **PAID** → verify Kgosi January invoice shows
- [ ] **90.5** Filter by status **SENT** → verify other invoices show

### Profitability

- [ ] **90.6** Navigate to **Profitability** → verify data across multiple clients
- [ ] **90.7** Check utilization tab → verify billable hours per team member
- [ ] **90.8** Check project profitability → compare revenue vs cost for Kgosi (retainer R5,500 vs hours × cost rates)

### Dashboard

- [ ] **90.9** Navigate to **Dashboard** → verify KPI cards show meaningful non-zero data
- [ ] **90.10** Verify recent activity widget shows entries

### Document generation

- [ ] **90.11** Open Kgosi customer → find **Generate Document** button or dropdown
- [ ] **90.12** Select a customer template → click **Preview** → verify HTML renders with Kgosi's details
- [ ] **90.13** If Generate PDF available → generate → verify download works

### Compliance overview

- [ ] **90.14** Navigate to **Compliance** (`/compliance`) → verify compliance dashboard loads
- [ ] **90.15** Check that all customers show as FICA complete

### Role-based access

- [ ] **90.16** Login as **Carol** → navigate to `/settings/rates` → verify access is blocked (permission error)
- [ ] **90.17** Login as **Carol** → navigate to `/my-work` → verify personal tasks and time visible
- [ ] **90.18** Login as **Bob** → navigate to `/settings/general` → verify admin access works

**Day 90 Checkpoints**:
- [ ] 4 customers, all ACTIVE
- [ ] 5+ projects across customers
- [ ] 4+ invoices (mix of DRAFT, SENT, PAID)
- [ ] Profitability data meaningful
- [ ] Dashboard shows non-zero KPIs
- [ ] Document template preview works
- [ ] FICA compliance tracked
- [ ] Role-based access enforced (Carol blocked from admin)
- [ ] **Overall verdict: Could this firm actually run on this platform?** [ YES / NO ]

---

## Known Limitations (Not in System)

These items from the original script are **not currently testable**:

| Item | Reason |
|------|--------|
| Upload logo image | S3 upload may not work in E2E without LocalStack being healthy — test manually if LocalStack is up |
| Bulk billing run | Feature exists but seeding enough data for meaningful batch may be tedious — test via `/invoices/billing-runs` page load |
| Payment links (Stripe/PayFast) | PSP is no-op in E2E — verify invoice has no payment link (expected) |
| Saved views | Create/persist a view filter if the UI supports it — lower priority |
| CSV import for time entries | Epic 366B (not yet implemented) |
| Weekly time grid | Epic 366A (not yet implemented) |
| Client-uploaded documents via portal | Portal upload depends on magic-link auth — test if portal auth works |
| Automation rule firing verification | Automations are seeded but trigger conditions may not fire in a single test session — check execution history page |

---

## Failure Reporting

For each failure, note:
```
Step: [number]
Expected: [what should happen]
Actual: [what happened]
Screenshot: [if applicable]
URL: [page URL when failure occurred]
Console errors: [any JS errors in browser console]
```
