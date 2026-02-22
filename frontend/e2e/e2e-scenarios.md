# E2E Test Scenarios

End-to-end test scenarios in GIVEN/WHEN/THEN format covering the critical user journeys of DocTeams.

**Test users:** Alice (owner), Bob (admin), Carol (member)
**Org:** `e2e-test-org`

---

## Discrete Scenarios

### Authentication & Authorization

```
GIVEN Carol is logged in as member
WHEN she navigates to /org/e2e-test-org/settings/rates
THEN she sees a permission denied message

GIVEN Alice is logged in as owner
WHEN she navigates to /org/e2e-test-org/settings/rates
THEN she sees the rate management page

GIVEN no user is authenticated
WHEN they navigate to /org/e2e-test-org/dashboard
THEN they are redirected to the login page
```

### Projects

```
GIVEN Alice is on the projects page
WHEN she creates a project named "Website Redesign"
THEN the project appears in the project list

GIVEN a project "Website Redesign" exists
WHEN Alice opens it
THEN she sees tabs for Overview, Tasks, Documents, Time, Budget, Activity

GIVEN Alice is on a project detail page
WHEN she adds Bob as a project member
THEN Bob appears in the members panel

GIVEN Carol is a member with no project access
WHEN she views the projects list
THEN she does not see "Website Redesign"

GIVEN Bob has been added to "Website Redesign"
WHEN Bob views the projects list
THEN he sees "Website Redesign"
```

### Customers & Lifecycle

```
GIVEN Alice is on the customers page
WHEN she creates a customer "Acme Corp"
THEN the customer appears with status PROSPECT

GIVEN "Acme Corp" is in PROSPECT status
WHEN Alice tries to create a project linked to that customer
THEN the action is blocked by the lifecycle guard

GIVEN "Acme Corp" is in PROSPECT status
WHEN Alice transitions it to ONBOARDING and completes all checklist items
THEN the customer auto-transitions to ACTIVE

GIVEN "Acme Corp" is ACTIVE
WHEN Alice creates a project linked to it
THEN the project is created successfully with the customer association
```

### Tasks

```
GIVEN a project exists with no tasks
WHEN Alice creates a task "Design homepage mockup"
THEN the task appears in the task list with status TODO

GIVEN a task "Design homepage mockup" exists
WHEN Bob claims the task
THEN it shows Bob as the assignee

GIVEN Bob is assigned to a task
WHEN Carol tries to claim the same task
THEN the claim is rejected (task already assigned)

GIVEN Alice opens a task
WHEN she opens the task detail sheet
THEN she sees description, assignee, comments, time entries, tags, and custom fields
```

### Time Tracking

```
GIVEN Bob is assigned to a task in "Website Redesign"
WHEN he logs 2 hours of billable time on today's date
THEN the time entry appears in the task's time list and the project time summary

GIVEN Bob has logged time on a project
WHEN he navigates to My Work
THEN he sees his time entries across all projects

GIVEN a time entry exists for today
WHEN Alice views the project Time tab
THEN the time summary shows the correct total hours
```

### Invoicing

```
GIVEN billable time entries exist for "Acme Corp" that are not yet invoiced
WHEN Alice generates an invoice from unbilled time
THEN a DRAFT invoice is created with line items matching the time entries

GIVEN a DRAFT invoice exists
WHEN Alice approves it
THEN the status changes to APPROVED and the time entries are marked as billed

GIVEN an APPROVED invoice exists
WHEN Alice marks it as SENT
THEN the invoice status updates and an audit event is recorded

GIVEN time entries have been billed on an invoice
WHEN Bob tries to edit those time entries
THEN the edit is blocked (billed time is immutable)
```

### Rate Cards & Budgets

```
GIVEN Alice is on the rate settings page
WHEN she sets an org-level default billing rate of R500/hr
THEN the rate is saved and applies to new time entries

GIVEN an org rate of R500/hr and a project override of R650/hr
WHEN Bob logs time on that project
THEN the time entry snapshots the R650 rate (project overrides org)

GIVEN a project has a budget of 100 hours
WHEN 80 hours have been logged
THEN the budget status shows a warning indicator (80% threshold)

GIVEN a project has exceeded its budget
WHEN Alice views the project overview
THEN she sees a budget exceeded alert
```

### Profitability

```
GIVEN projects have billable time with rates and cost rates configured
WHEN Alice navigates to the profitability page
THEN she sees project margins, customer profitability, and utilization metrics

GIVEN Alice is on a customer detail page
WHEN she clicks the Financials tab
THEN she sees revenue, cost, and margin for that customer
```

### Comments & Notifications

```
GIVEN Alice is viewing a task detail
WHEN she posts a comment "Please review the mockup"
THEN the comment appears in the task's comment section

GIVEN Bob is a member of a project
WHEN Alice posts a comment on a task in that project
THEN Bob receives a notification (bell icon shows unread count)

GIVEN Bob has unread notifications
WHEN he clicks the notification bell
THEN he sees the notification list with Alice's comment
```

### Document Templates & PDF Generation

```
GIVEN Alice is on the templates settings page
WHEN she selects a template and clicks Preview
THEN an HTML preview renders with org branding (logo, brand color)

GIVEN an invoice exists for "Acme Corp"
WHEN Alice clicks "Generate Document" and selects the invoice template
THEN a PDF is generated and appears in the generated documents list
```

### Retainers

```
GIVEN "Acme Corp" is an ACTIVE customer
WHEN Alice creates a retainer agreement for 40 hours/month at R600/hr
THEN the retainer appears on the customer's retainer tab

GIVEN a retainer with 40 hours exists for the current period
WHEN Bob logs 35 hours against the retainer's project
THEN the retainer summary shows 35/40 hours consumed

GIVEN a retainer period has ended with 10 unused hours
WHEN Alice closes the period
THEN an invoice is generated and a new period opens (with rollover if configured)
```

### Tags, Custom Fields & Saved Views

```
GIVEN Alice is viewing the task list
WHEN she adds a tag "urgent" to a task
THEN the tag badge appears on the task

GIVEN custom fields have been configured (e.g. "Priority" dropdown)
WHEN Alice opens a task detail sheet and sets Priority to "High"
THEN the field value is saved and visible on the task

GIVEN Alice creates a saved view filtering tasks by tag "urgent"
WHEN she selects that saved view
THEN only tasks tagged "urgent" are shown
```

### Project Templates & Recurring Schedules

```
GIVEN a completed project "Monthly Bookkeeping" with tasks
WHEN Alice saves it as a project template
THEN the template appears in the template management page

GIVEN a project template exists
WHEN Alice creates a new project from that template
THEN the project is created with all template tasks pre-populated

GIVEN a recurring schedule is set for "Monthly Bookkeeping" template
WHEN the schedule's next execution date arrives
THEN a new project instance is automatically created from the template
```

### Reporting & Export

```
GIVEN time entries exist across multiple projects
WHEN Alice runs a Timesheet report for the current month
THEN she sees a table of time entries grouped by project/member

GIVEN Alice has generated a report
WHEN she clicks Export CSV
THEN a CSV file downloads with the report data
```

### Settings & Integrations

```
GIVEN Alice is on the integrations settings page
WHEN she configures a storage integration with API keys
THEN the integration shows as connected

GIVEN Alice is on the billing settings page
WHEN she upgrades from Starter to Pro
THEN the plan badge updates and new features are unlocked
```

---

## Full Journey Scenarios

### Journey 1 — First Project: Setup to Final Invoice

```
GIVEN Alice has just created org "e2e-test-org" with Bob (admin) and Carol (member)
 AND no projects, customers, rates, or templates are configured

-- Org Setup --

WHEN Alice navigates to Settings → Rates
 AND sets the org default billing rate to R500/hr
 AND sets cost rates: Alice R300/hr, Bob R250/hr, Carol R200/hr
THEN the rate cards are saved and visible on the settings page

-- Customer Onboarding --

WHEN Alice navigates to Customers → New Customer
 AND creates "Acme Corp" with contact details
THEN Acme Corp appears with status PROSPECT

WHEN Alice transitions Acme Corp to ONBOARDING
 AND completes all onboarding checklist items (FICA, engagement letter, etc.)
THEN Acme Corp auto-transitions to ACTIVE

-- Project Creation --

WHEN Alice navigates to Projects → New Project
 AND creates "Acme Annual Audit" linked to Acme Corp
 AND sets a budget of 80 hours
THEN the project appears in the list linked to Acme Corp with budget 80h

WHEN Alice opens the project and adds Bob and Carol as members
THEN both appear in the project members panel

-- Task Breakdown --

WHEN Alice creates tasks:
   - "Gather source documents" (assigned to Carol)
   - "Review financial statements" (assigned to Bob)
   - "Draft audit report" (assigned to Alice)
   - "Client review meeting" (unassigned)
THEN all four tasks appear in the task list with correct assignees

-- Work Execution --

WHEN Carol opens "Gather source documents" from My Work
 AND logs 6 hours of billable time across two days
 AND adds a comment "All documents received from client"
 AND marks the task as DONE
THEN the time entries appear on the project Time tab
 AND the comment appears in the task activity
 AND Bob and Alice receive notifications about the comment

WHEN Bob opens "Review financial statements"
 AND logs 12 hours of billable time across three days
 AND adds tag "needs-attention" to the task
 AND marks the task as DONE
THEN the project time summary shows 18 total hours (6 + 12)
 AND the budget indicator shows 18/80 hours (22.5%)

WHEN Alice opens "Draft audit report"
 AND logs 20 hours of billable time across a week
 AND uploads a document "draft-audit-report.pdf" scoped to the project
 AND marks the task as DONE
THEN the project time summary shows 38 total hours
 AND the document appears in the project Documents tab

WHEN Alice assigns "Client review meeting" to herself
 AND logs 2 hours after the meeting
 AND posts a comment "Client approved with minor changes"
 AND marks the task as DONE
THEN all 4 tasks show as DONE
 AND project total is 40 hours, budget shows 40/80 (50%)

-- Profitability Check --

WHEN Alice views the project Financials tab
THEN she sees:
   - Revenue: 40h × R500 = R20,000
   - Cost: Carol 6h×R200 + Bob 12h×R250 + Alice 22h×R300 = R10,800
   - Margin: R9,200 (46%)

-- Invoice Generation --

WHEN Alice navigates to the project and clicks "Generate Invoice"
 AND selects all unbilled time entries (40 hours)
THEN a DRAFT invoice is created with 4 line items (one per member's time)
 AND the invoice total shows R20,000

WHEN Alice reviews the draft, approves it, and marks it as SENT
THEN the invoice status is SENT
 AND the 40 hours of time entries are marked as billed
 AND an audit event is recorded

-- Document Generation --

WHEN Alice navigates to the invoice and clicks "Generate Document"
 AND selects the invoice PDF template
THEN a PDF is generated with org branding and invoice details
 AND it appears in the generated documents list with a download link

-- Project Wrap-up --

WHEN Alice views the company dashboard
THEN the project shows healthy status with all tasks complete
 AND the profitability widget shows Acme Corp at 46% margin
```

### Journey 2 — Retainer Client: Monthly Recurring Work

```
GIVEN org "e2e-test-org" exists with rates configured (from Journey 1)
 AND Acme Corp is an ACTIVE customer

-- Retainer Setup --

WHEN Alice navigates to Acme Corp → Retainers tab
 AND creates a retainer: "Monthly Bookkeeping", 20 hours/month, R600/hr
THEN the retainer appears as ACTIVE with period starting this month
 AND a linked project "Monthly Bookkeeping" is visible

-- Template for Repeatability --

WHEN Alice opens the retainer project
 AND creates recurring tasks:
   - "Reconcile bank statements"
   - "Process invoices"
   - "Prepare management accounts"
   - "Monthly review call"
THEN the tasks appear in the project

WHEN Alice saves this project as a template "Monthly Bookkeeping Template"
 AND creates a recurring schedule: monthly on the 1st, linked to Acme Corp
THEN the template and schedule appear in Settings → Templates

-- Month 1 Execution --

WHEN Carol logs 8 hours against "Reconcile bank statements"
 AND Bob logs 6 hours against "Process invoices"
 AND Bob logs 4 hours against "Prepare management accounts"
 AND Alice logs 1 hour against "Monthly review call"
THEN the retainer summary shows 19/20 hours consumed (95%)
 AND time entries show the R600/hr retainer rate (overrides org rate)

WHEN Alice closes the retainer period at month-end
THEN an invoice is generated: 19h × R600 = R11,400
 AND a new period opens for next month with 20 fresh hours
 AND 1 unused hour from this period is handled per rollover config

-- Month 2 Auto-Creation --

WHEN the recurring schedule fires on the 1st of next month
THEN a new project instance is created from "Monthly Bookkeeping Template"
 AND all 4 template tasks are pre-populated with assignments
 AND it's linked to Acme Corp and the retainer
```

### Journey 3 — Multi-Customer Dashboard View (Owner's Morning)

```
GIVEN three ACTIVE customers exist: Acme Corp, Beta Ltd, Gamma Inc
 AND each has projects with varying health: on-track, at-risk, over-budget
 AND time entries, invoices, and retainers exist across all three

-- Company Dashboard --

WHEN Alice logs in and lands on the dashboard
THEN she sees:
   - Active projects count with health breakdown (green/amber/red)
   - Revenue pipeline: draft invoices, sent invoices, overdue invoices
   - Team utilization: billable vs non-billable hours this week
   - Budget alerts for any project exceeding 80% threshold

-- Drill Down --

WHEN Alice clicks on the at-risk project for Beta Ltd
THEN she sees the project overview with:
   - Budget at 90% consumed with tasks still open
   - Overdue tasks flagged
   - Activity feed showing recent comments and time logs

-- Profitability Review --

WHEN Alice navigates to Profitability → By Customer
THEN she sees all three customers ranked by margin
 AND can compare revenue, cost, and margin side by side

-- Reporting --

WHEN Alice runs a Timesheet report for this month across all projects
 AND exports to CSV
THEN a file downloads with every time entry: date, member, project, customer, hours, rate, billable flag
```

### Journey 4 — New Team Member Onboarding (RBAC Boundaries)

```
GIVEN Alice (owner) and Bob (admin) exist
 AND projects and customers are already set up

-- Adding Carol --

WHEN Alice invites Carol as a member via the team page
 AND Carol is synced to the org
THEN Carol appears in the team list with role "member"

-- Carol's Restricted View --

WHEN Carol logs in and views the dashboard
THEN she sees only My Work (no company-wide dashboard widgets)

WHEN Carol navigates to Projects
THEN she sees an empty list (no project access yet)

WHEN Carol tries to navigate to Settings → Rates
THEN she sees a permission denied message

WHEN Carol tries to navigate to Customers
THEN she sees only customers linked to projects she has access to (none yet)

-- Granting Access --

WHEN Alice adds Carol to "Acme Annual Audit"
THEN Carol sees that project in her project list

WHEN Carol navigates to the project
THEN she can view tasks, log time, and add comments
 BUT she cannot edit budget, manage members, or delete the project

WHEN Carol opens My Work
THEN she sees tasks assigned to her in "Acme Annual Audit"
 AND time entries she has logged
```
