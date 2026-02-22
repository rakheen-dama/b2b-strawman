# E2E Automation Notes — Journey 1 & Journey 2

Compiled from live exploration of the DocTeams frontend on the e2e Docker stack.
Screenshots are in `e2e-screenshots/` at the repo root.

---

## Environment Setup

### Starting the Stack
```bash
bash compose/scripts/start-mock-dev.sh   # Build + start (5-10 min cold)
bash compose/scripts/stop-mock-dev.sh    # Stop + wipe volumes
bash compose/scripts/reseed-mock-dev.sh  # Re-seed without rebuild
```

### URLs
| Service | URL |
|---------|-----|
| Frontend | `http://localhost:3001` |
| Backend | `http://localhost:8081` |
| Mock IDP | `http://localhost:8090` |
| Mock Login Page | `http://localhost:3001/mock-login` |
| Postgres | `localhost:5433` (user: `postgres`, pass: `changeme`, db: `app`) |

### Playwright Config Override
```bash
PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e
```

---

## Authentication

### loginAs() Fixture (Preferred for Tests)
Use `e2e/fixtures/auth.ts` → `loginAs(page, 'alice' | 'bob' | 'carol')`.
This sets a JWT cookie directly — **no UI interaction needed**.

```typescript
import { loginAs } from '../fixtures/auth';

test('example', async ({ page }) => {
  await loginAs(page, 'alice');
  await page.goto('/org/e2e-test-org/dashboard');
  // Now authenticated as Alice (owner)
});
```

### Users
| Handle | userId | Name | Email | Role |
|--------|--------|------|-------|------|
| `alice` | `user_e2e_alice` | Alice Owner | `alice@e2e-test.local` | `owner` |
| `bob` | `user_e2e_bob` | Bob Admin | `bob@e2e-test.local` | `admin` |
| `carol` | `user_e2e_carol` | Carol Member | `carol@e2e-test.local` | `member` |

### Mock Login UI (Alternative)
1. Navigate to `/mock-login`
2. Select user from dropdown (`combobox "Select User"`)
3. Click `button "Sign In"`
4. Redirects to `/org/e2e-test-org/dashboard`

---

## Seed Data State After Stack Start

| Entity | State | Notes |
|--------|-------|-------|
| Org | `e2e-test-org` (Pro plan) | 3 members synced |
| Customer "Acme Corp" | **ONBOARDING** (not ACTIVE) | Seed fails to auto-transition; checklist items 0/4 completed |
| Projects | **None** | Must be created in tests |
| Tasks | **None** | Must be created in tests |
| Rates | **Not set** | All members show "Not set" |
| Invoices | **None** | |
| Retainers | **None** | |

### CRITICAL: Customer Lifecycle
The seed creates Acme Corp and transitions to ONBOARDING, but **cannot auto-transition to ACTIVE** because the 4 checklist items are not completed. Tests that need an ACTIVE customer must either:
1. Complete all 4 checklist items via the UI (see Onboarding Checklist below), OR
2. Use the backend API directly to complete checklist items

---

## Navigation Map

### Sidebar Links (All Org-Scoped under `/org/e2e-test-org/`)
```
Dashboard          → /dashboard
My Work            → /my-work
Projects           → /projects
Documents          → /documents
Customers          → /customers
Team               → /team
Notifications      → /notifications
Profitability      → /profitability
Reports            → /reports
Invoices           → /invoices
Recurring Schedules → /schedules
Retainers          → /retainers
Compliance         → /compliance
Settings           → /settings
```

### Settings Sub-Pages
```
Billing            → /settings/billing
Notifications      → /settings/notifications
Rates & Currency   → /settings/rates
Custom Fields      → /settings/custom-fields
Tags               → /settings/tags
Templates          → /settings/templates
Checklists         → /settings/checklists
Compliance         → /settings/compliance
Project Templates  → /settings/project-templates
Integrations       → /settings/integrations
Organization       → (Coming soon — disabled)
Security           → (Coming soon — disabled)
```

---

## Journey 1 — First Project: Setup to Final Invoice

### Step 1: Configure Rates (Alice, owner)

**Page**: Settings → Rates & Currency (`/settings/rates`)

**UI Elements**:
- Currency selector: `combobox` showing "USD — US Dollar"
- Two tabs: `tab "Billing Rates"` (default selected), `tab "Cost Rates"`
- Member table with columns: Member, Hourly Rate, Currency, Effective From, Effective To, Actions
- Each member row has `button "Add rate for {Name}"` (e.g., "Add rate for Alice Owner")

**Add Rate Dialog** (opened by clicking "Add Rate" button):
- Rate Type toggle: `button "Billing Rate"` (active by default) / `button "Cost Rate"`
- `spinbutton "Hourly Rate"` — enter numeric rate (e.g., 500)
- `combobox` for Currency — defaults to "USD — US Dollar"
- `textbox "Effective From"` — date input, defaults to today (YYYY-MM-DD format)
- `textbox "Effective To (optional)"` — date input
- Actions: `button "Cancel"`, `button "Create Rate"`

**Automation Steps**:
```
1. loginAs(page, 'alice')
2. Navigate to /org/e2e-test-org/settings/rates
3. For each member (Alice, Bob, Carol):
   a. Click "Add rate for {Name}" button
   b. Verify "Billing Rate" is selected
   c. Fill "Hourly Rate" spinbutton with rate value (e.g., 500)
   d. Click "Create Rate"
   e. Wait for dialog to close
4. Switch to "Cost Rates" tab
5. For each member:
   a. Click "Add rate for {Name}" button
   b. Click "Cost Rate" toggle
   c. Fill hourly rate
   d. Click "Create Rate"
```

**Assertions**:
- After creating rate: member row shows the rate value instead of "Not set"
- Currency column shows currency code
- Effective From shows the date

---

### Step 2: Create & Onboard Customer (Alice, owner)

**Page**: Customers (`/customers`)

**Customer List UI**:
- Header: `heading "Customers"` with count badge
- `button "New Customer"` in top-right
- Lifecycle filter pills: All, Prospect, Onboarding, Active, Dormant, Offboarding, Offboarded
- Table columns: Name, Email, Phone, Lifecycle, Status, Created
- Customer name is a link to detail page

**Create Customer Dialog**:
- `textbox "Name"` (placeholder: "Customer name")
- `combobox "Type"` — options: Individual, Company, Trust
- `textbox "Email"` (placeholder: "customer@example.com")
- `textbox "Phone (optional)"` (placeholder: "+1 (555) 000-0000")
- `textbox "ID Number (optional)"` (placeholder: "e.g. CUS-001")
- `textbox "Notes (optional)"` (textarea)
- Actions: `button "Cancel"`, `button "Create Customer"`

**Customer Detail Page** (`/customers/{id}`):
- Header: Customer name + lifecycle badge(s) (e.g., "Active", "Onboarding")
- Action buttons: `button "Change Status"`, `button "Edit"`, `button "Archive"`
- Customer Readiness panel with progress bar and checklist items
- Tabs: Projects, Documents, **Onboarding**, Invoices, Retainer, Rates, Generated Docs, Financials

**Onboarding Tab**:
- Checklist: "Generic Client Onboarding" — 4 required items:
  1. "Confirm client engagement" — `button "Mark Complete"`
  2. "Verify contact details" — `button "Mark Complete"`
  3. "Confirm billing arrangements" — `button "Mark Complete"`
  4. "Upload signed engagement letter" — `button "Mark Complete"`
- Completing all 4 items auto-transitions customer from ONBOARDING → ACTIVE

**Automation Steps (for fresh customer)**:
```
1. Navigate to /org/e2e-test-org/customers
2. Click "New Customer" button
3. Fill: Name="Acme Corp", Type=Company, Email, Phone
4. Click "Create Customer"
5. Verify customer appears in list with "Prospect" lifecycle badge
6. Click on customer name to open detail
7. Click "Change Status" → transition to "Onboarding"
8. Click "Onboarding" tab
9. Click "Mark Complete" for each of the 4 checklist items
10. After all 4 complete → customer auto-transitions to ACTIVE
11. Verify "Active" badge appears in header
```

**Automation Steps (for seed customer "Acme Corp")**:
```
1. Navigate to /org/e2e-test-org/customers
2. Click "Acme Corp" link
3. Click "Onboarding" tab
4. Click "Mark Complete" for all 4 items
5. Verify customer transitions to ACTIVE
```

---

### Step 3: Create Project (Alice, owner)

**Page**: Projects (`/projects`)

**Projects List UI**:
- Header: `heading "Projects"` with count (e.g., "0 projects")
- `button "New Project"` in top-right
- Filter tabs: All (+ Save View)
- Empty state: "No projects yet" with CTA button

**Create Project Dialog**:
- `textbox "Name"` (placeholder: "My Project")
- `textbox "Description (optional)"` (textarea, placeholder: "A brief description...")
- Actions: `button "Cancel"`, `button "Create Project"`

**Project Detail Page** (`/projects/{id}`):
- Header: Project name + "Lead" badge
- Action buttons: `button "Generate Document"` (dropdown), `button "Save as Template"`, `button "Edit"`, `button "Delete"`
- Field Groups section with `button "Add Group"`
- Tags section with `button "Add Tag"`
- **11 tabs**: Overview, Documents, Members, Customers, Tasks, Time, Budget, Financials, Rates, Generated Docs, Activity

**Overview Tab** (default):
- "Project Setup" progress panel (20% initially): Customer assigned, Rate card configured, Budget set, Team members added, No required fields defined
- Document Templates section (Standard Engagement Letter, Project Summary Report)
- Summary cards: Tasks (0/0 complete), Hours (0.0h this month), Budget (No budget), Margin (--)
- Tasks, Team Hours, Recent Activity panels

**Automation Steps**:
```
1. Navigate to /org/e2e-test-org/projects
2. Click "New Project" button
3. Fill Name="Acme Annual Audit", Description="..."
4. Click "Create Project"
5. Click on the project card/link in the list
6. Verify project detail page loads with correct name
```

---

### Step 4: Set Budget (Alice, on project detail)

**Tab**: Budget

**Empty State**: "No budget set" with `button "Set Budget"`

**Set Budget Dialog**:
- `spinbutton "Budget Hours"` (placeholder: "e.g. 200")
- `spinbutton "Budget Amount"` (placeholder: "e.g. 50000")
- `combobox` for Currency — defaults to "USD — US Dollar"
- `spinbutton "Alert Threshold (%)"` — defaults to 80
- `textbox "Notes"` (textarea, placeholder: "Optional notes...")
- Actions: `button "Cancel"`, `button "Save Budget"`

**Automation Steps**:
```
1. On project detail page, click "Budget" tab
2. Click "Set Budget" button
3. Fill Budget Hours=80
4. Verify Alert Threshold defaults to 80
5. Click "Save Budget"
6. Verify budget panel shows "80h" or similar
```

---

### Step 5: Add Project Members (Alice, on project detail)

**Tab**: Members

**Automation Steps**:
```
1. Click "Members" tab
2. Look for "Add Member" button or similar
3. Add Bob and Carol as members
4. Verify both appear in members list
```

---

### Step 6: Link Customer to Project (Alice, on project detail)

**Tab**: Customers

**Automation Steps**:
```
1. Click "Customers" tab
2. Look for "Link Customer" or "Add Customer" button
3. Select Acme Corp from dropdown/search
4. Verify Acme Corp appears as linked customer
```

---

### Step 7: Create Tasks (Alice, on project detail)

**Tab**: Tasks

**Tasks Tab UI**:
- `heading "Tasks"` with `button "New Task"`
- Filter tabs: All (+ Save View)
- Empty state: "No tasks yet"

**Create Task Dialog**:
- `textbox "Title"` (placeholder: "Task title")
- `textbox "Description (optional)"` (textarea)
- `combobox "Priority"` — options: Low, Medium (default), High
- `textbox "Type (optional)"` (placeholder: "e.g. Bug, Feature")
- `textbox "Due Date (optional)"` — date input (YYYY-MM-DD)
- `combobox` "Assign to" — defaults to "Unassigned", dropdown lists org members
- Actions: `button "Cancel"`, `button "Create Task"`

**Automation Steps**:
```
1. Click "Tasks" tab
2. For each task:
   a. Click "New Task" button
   b. Fill Title, Description, Priority, Due Date
   c. Select assignee from "Assign to" dropdown
   d. Click "Create Task"
   e. Wait for dialog to close
3. Verify all tasks appear in task list with correct assignees
```

**Task Detail (Sheet/Panel)** — clicking a task opens a detail view with:
- Status controls (TODO → IN_PROGRESS → DONE)
- Comments section
- Time entry logging ("Log Time" button)
- Tags
- Custom fields

---

### Step 8: Log Time (Various users)

**NOTE**: Time entries are logged from task detail views, NOT from the project Time tab directly.

**Time Tab UI** (project level):
- Date range filter: `textbox` From, `textbox` To
- Shows summary table when time entries exist
- Empty state: "No time tracked yet"

**Log Time Flow** (from task detail):
```
1. Navigate to project → Tasks tab
2. Click on a task to open detail sheet
3. Look for "Log Time" button
4. Dialog fields: Hours, Date, Billable toggle, Notes
5. Submit
6. Verify time appears on project Time tab
```

**My Work Page** (`/my-work`):
- Summary cards: Hours This Week, Billable %, Overdue Tasks
- Time Breakdown, Upcoming Deadlines panels
- "My Tasks" section (left), "This Week" time summary + "Today" (right)
- Week navigator: `button "Previous week"`, date range, `button "Next week"`

---

### Step 9: Check Profitability (Alice, owner)

**Page**: Profitability (`/profitability`)

**UI Structure**:
- Three sections, each with their own date range filters:
  1. **Team Utilization** — From/To date inputs
  2. **Project Profitability** — From/To date inputs
  3. **Customer Profitability** — From/To date inputs
- All default to current month (e.g., 2026-02-01 to 2026-02-22)

**Assertions after time is logged**:
- Team Utilization shows billable/non-billable hours per member
- Project Profitability shows revenue, cost, margin per project
- Customer Profitability shows revenue, cost, margin per customer

---

### Step 10: Generate Invoice (Alice, owner)

**Page**: Invoices (`/invoices`)

**Invoices Page UI**:
- Summary cards: Total Outstanding, Total Overdue (red), Paid This Month (green)
- Status filter pills: All, Draft, Approved, Sent, Paid, Void
- Empty state: "No invoices found"

**Invoice generation entry points**:
1. **From customer detail page** → Invoices tab → generate from unbilled time
2. **From project detail page** → `button "Generate Document"` dropdown

**Invoice Detail Page** (`/invoices/{id}`):
- Invoice header with number, status badge, customer, dates
- Line items table
- Status transition buttons (DRAFT → APPROVED → SENT → PAID)
- "Generate Document" to create PDF

**Automation Steps**:
```
1. Navigate to customer detail → Invoices tab
   OR project detail → Generate Document dropdown
2. Select unbilled time entries
3. Create DRAFT invoice
4. Navigate to invoice detail
5. Click Approve → status becomes APPROVED
6. Click Send → status becomes SENT
7. Verify time entries marked as billed (immutable)
```

---

### Step 11: Generate Document/PDF (Alice, owner)

**From project detail page**:
- `button "Generate Document"` with dropdown arrow
- Click opens dropdown/dialog to select template
- Templates include: Standard Engagement Letter, Project Summary Report

**From invoice detail page**:
- Similar "Generate Document" button
- Generates PDF with org branding

---

## Journey 2 — Retainer Client: Monthly Recurring Work

### Step 1: Create Retainer (Alice, owner)

**Page**: Retainers (`/retainers`)

**Retainers Page UI**:
- Summary cards: Active Retainers, Periods Ready to Close, Total Overage Hours
- Status filter pills: All, Active, Paused, Terminated
- `button "New Retainer"` in top-right

**New Retainer Dialog**:
- `combobox` "Customer" — "Select a customer..." (required, dropdown of ACTIVE customers)
- `textbox "Name"` (placeholder: "e.g. Monthly Retainer - Acme Corp")
- `combobox "Type"` — default "Hour Bank"
- `combobox "Frequency"` — default "Monthly"
- `spinbutton "Allocated Hours"` (placeholder: "e.g. 40")
- `spinbutton "Period Fee"` (placeholder: "e.g. 5000.00")
- `combobox "Rollover Policy"` — default "Forfeit unused hours"
- `textbox "Start Date"` — date input (required)
- `textbox "End Date (optional)"` — date input
- `textbox "Notes (optional)"` (textarea)
- Actions: `button "Cancel"`, `button "Create Retainer"` (disabled until customer selected)

**PREREQUISITE**: Customer must be ACTIVE. If Acme Corp is still in ONBOARDING, complete the onboarding checklist first (see Journey 1, Step 2).

**Automation Steps**:
```
1. Ensure Acme Corp is ACTIVE (complete onboarding if needed)
2. Navigate to /org/e2e-test-org/retainers
3. Click "New Retainer"
4. Select "Acme Corp" from Customer dropdown
5. Fill Name="Monthly Bookkeeping"
6. Type=Hour Bank, Frequency=Monthly
7. Allocated Hours=20
8. Period Fee=12000.00 (20h × $600/hr)
9. Rollover Policy=Forfeit unused hours
10. Start Date=first of current month
11. Click "Create Retainer"
12. Verify retainer appears in list with ACTIVE status
```

**Retainer Detail Page** (`/retainers/{id}`):
- Retainer info: name, customer, status, type, frequency
- Current period summary: hours consumed vs allocated
- Period history
- Actions: Close Period, Pause, Terminate

---

### Step 2: Create Project Template (Alice, owner)

**From project detail page**:
- `button "Save as Template"` in project header
- This saves the current project (with tasks) as a reusable template

**Settings → Project Templates** (`/settings/project-templates`):
- Lists saved project templates
- `button "New Schedule"` (on Recurring Schedules page)

---

### Step 3: Set Up Recurring Schedule (Alice, owner)

**Page**: Recurring Schedules (`/schedules`)

**UI Structure**:
- `heading "Recurring Schedules"`
- `button "New Schedule"` in top-right
- Status filter buttons: Active, Paused, Completed, All
- Empty state: "No active schedules found."

**New Schedule Dialog** (expected fields based on app structure):
- Template selection (from saved project templates)
- Customer assignment
- Frequency (Monthly, Weekly, etc.)
- Start date, next execution date
- Auto-creation settings

**Automation Steps**:
```
1. First save a project as template (Step 2)
2. Navigate to /org/e2e-test-org/schedules
3. Click "New Schedule"
4. Select the project template
5. Link to Acme Corp
6. Set frequency=Monthly, start date=1st of month
7. Click Create
8. Verify schedule appears as Active
```

---

### Step 4: Log Time Against Retainer (Various users)

```
1. Log into retainer project
2. Create tasks (or use template-generated tasks)
3. Log time entries as different users (carol, bob, alice)
4. Verify retainer summary shows consumed hours vs allocated
5. Watch for budget/alert thresholds
```

---

### Step 5: Close Retainer Period (Alice, owner)

```
1. Navigate to retainer detail page
2. Click "Close Period" (at month end)
3. Verify: invoice generated for consumed hours
4. Verify: new period opens with fresh allocation
5. Verify: rollover policy applied (forfeit/carry-forward)
```

---

## Common UI Patterns for Automation

### Dialog Pattern
All create/edit dialogs follow the same pattern:
1. Click trigger button (e.g., "New Customer", "Add Rate")
2. Dialog opens with `dialog "{Title}"` role
3. Fill form fields
4. Click primary action button (e.g., "Create Customer", "Create Rate")
5. Dialog auto-closes on success
6. Close button (`button "Close"` with X icon) always top-right

### Tab Navigation
Project detail and customer detail pages use tab panels:
```typescript
await page.getByRole('tab', { name: 'Tasks' }).click();
await page.getByRole('tabpanel', { name: 'Tasks' }).waitFor();
```

### Sidebar Navigation
```typescript
await page.getByRole('link', { name: 'Projects' }).click();
```
Active link has `[active]` state. All links in `navigation "Main navigation"`.

### Empty State Pattern
Pages with no data show a centered empty state with:
- Icon image
- `heading` describing empty state
- `paragraph` with CTA text
- Often a `button` to create first item

### Filter Pills / Status Tabs
Many pages have filter options as link pills or tabs:
```typescript
// Link-based filters (Customers, Invoices, Retainers)
await page.getByRole('link', { name: 'Active' }).click();

// Button-based filters (Recurring Schedules)
await page.getByRole('button', { name: 'Active' }).click();

// Tab-based filters (Tasks "All" view)
await page.getByRole('tab', { name: 'All' }).click();
```

### Date Inputs
Date fields use native HTML `<input type="date">` with format `YYYY/MM/DD`:
```typescript
await page.getByRole('textbox', { name: 'Effective From' }).fill('2026-02-23');
```

### Combobox / Select Dropdowns
Two patterns:
1. **Native select** (e.g., Customer Type): `combobox "Type"` with `option` children
2. **Custom combobox** (e.g., Customer dropdown): `combobox` with text content, opens a listbox on click

---

## Known Issues & Gotchas

1. **Seed doesn't complete ACTIVE transition** — Acme Corp stays in ONBOARDING. Tests must complete the 4 checklist items manually.

2. **Console errors on page load** — Two React hydration errors appear (Minified React error #418, TypeError null reading). These are hydration mismatches and don't block functionality.

3. **"No org" badge in header** — The header shows "No org" text even though the org is active. This is a display issue with the mock auth provider not populating all org metadata.

4. **Date format varies** — Some date inputs show `yyyy/mm/dd` placeholder, others show `YYYY-MM-DD`. Use ISO format (`2026-02-23`) when filling.

5. **Create Retainer button disabled** — The "Create Retainer" button stays disabled until a customer is selected from the dropdown. The customer must be ACTIVE.

6. **Time is logged from task detail, not project Time tab** — The project Time tab is read-only (shows summaries). Time entry creation happens from within task detail panels.

---

## Screenshot Reference

| # | File | Description |
|---|------|-------------|
| 01 | `01-mock-login.png` | Mock login page with user dropdown |
| 02 | `02-dashboard.png` | Dashboard (empty state) |
| 03 | `03-settings-hub.png` | Settings hub with all config cards |
| 04 | `04-rates-billing.png` | Rates & Currency page — Billing Rates tab |
| 05 | `05-add-rate-dialog.png` | Add Rate dialog (billing) |
| 06 | `06-customers-list.png` | Customers list with Acme Corp |
| 07 | `07-customer-detail-acme.png` | Customer detail — Acme Corp overview |
| 08 | `08-customer-onboarding-checklist.png` | Onboarding tab with 4 checklist items |
| 09 | `09-new-customer-dialog.png` | Create Customer dialog |
| 10 | `10-projects-empty.png` | Projects page (empty state) |
| 11 | `11-new-project-dialog.png` | Create Project dialog |
| 12 | `12-project-detail-overview.png` | Project detail — Overview tab (full page) |
| 13 | `13-project-tasks-empty.png` | Project detail — Tasks tab (empty) |
| 14 | `14-new-task-dialog.png` | Create Task dialog |
| 15 | `15-project-time-tab.png` | Project detail — Time tab (empty) |
| 16 | `16-set-budget-dialog.png` | Set Budget dialog |
| 17 | `17-invoices-page.png` | Invoices page with summary cards |
| 18 | `18-profitability-page.png` | Profitability page — 3 sections |
| 19 | `19-my-work-page.png` | My Work page (full layout) |
| 20 | `20-retainers-page.png` | Retainers page with summary cards |
| 21 | `21-new-retainer-dialog.png` | New Retainer dialog (full form) |
| 22 | `22-recurring-schedules-page.png` | Recurring Schedules page |
