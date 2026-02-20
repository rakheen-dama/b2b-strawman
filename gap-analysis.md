# Product Walkthrough vs. Frontend Implementation — Gap Analysis

> Generated: 2026-02-20
> Source: `docs/product-walkthrough.md` analysed against `frontend/` codebase

---

## Severity Legend

| Severity | Meaning |
|----------|---------|
| **Critical** | Broken functionality — clicking something produces a 404 or error |
| **High** | Feature described in walkthrough does not exist in the code |
| **Medium** | UX flow differs materially from walkthrough description |
| **Low** | Label/cosmetic mismatch or minor omission in walkthrough |

---

## Critical Issues

### 1. Broken `/invoices/new` route
**Walkthrough ref:** Chapter 5.3 ("New Invoice" button, "Generate Invoice" action)
**Files affected:** `frontend/components/projects/overview-tab.tsx`, `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`

Both the project overview "Unbilled Time" action card and customer overview link to `/org/${slug}/invoices/new?projectId=...` or `/org/${slug}/invoices/new?customerId=...`. **No `new/page.tsx` exists** under the invoices route. Clicking "Create Invoice" from either location produces a 404.

Invoice creation is only possible via `InvoiceGenerationDialog` opened from the customer's Invoices tab.

### 2. No Assignee field in task creation dialog
**Walkthrough ref:** Chapter 3.4 ("Create tasks with Priority and Assignee")
**File:** `frontend/components/tasks/create-task-dialog.tsx`

The Create Task dialog has Title, Description, Priority, Type, and Due Date — but **no Assignee selector**. Tasks can only be assigned via the Claim mechanism (current user claims the task). Admins/leads cannot pre-assign tasks to other members at creation time.

### 3. No dedicated task detail page
**Walkthrough ref:** Chapters 4.1, 4.3 ("Task detail: Log Time button", "Comments section below task")
**Expected route:** `/org/[slug]/projects/[id]/tasks/[taskId]` — does not exist

The walkthrough describes navigating to a task's detail page to log time and view comments. The implementation uses **inline expansion** — clicking a task row in the task list expands it to show time entries and comments in-place. There is no standalone task page.

---

## High Severity Gaps

### 4. Dashboard "Total Revenue" KPI missing
**Walkthrough ref:** Chapter 9.1 ("KPI cards: Total revenue, Hours logged, Active projects, Overdue tasks")
**File:** `frontend/components/dashboard/kpi-row.tsx`

Actual dashboard KPIs: Active Projects, Hours Logged, Billable %, Overdue Tasks, Avg. Margin. There is **no "Total Revenue" card**. The walkthrough also omits Billable % and Avg. Margin which do exist.

### 5. Tags & Custom Fields not on Tasks
**Walkthrough ref:** Chapter 10.3 ("Tags & Custom Fields on any project/task/customer")
**Missing from:** `frontend/components/tasks/`

`TagInput` and `CustomFieldSection` components are wired to Projects and Customers detail pages but **not to Tasks**. The walkthrough claims all three entity types support tags and custom fields.

### 6. Saved Views not available on Tasks
**Walkthrough ref:** Chapter 10.1 ("Works the same on Customers and Tasks lists")
**File:** `frontend/components/views/view-selector-client.tsx`

`ViewSelectorClient` is wired to Projects and Customers list pages only. Tasks don't have a standalone list page with a saved view selector (tasks live inside project detail tabs).

### 7. Template preview requires raw UUID, not entity selector
**Walkthrough ref:** Chapter 8.2 ("Preview: Select the entity to render against")
**File:** `frontend/components/templates/template-editor.tsx` (or similar preview component)

The template preview form requires typing a raw entity UUID. The walkthrough describes a user-friendly entity selector (e.g., searchable dropdown of projects/customers). No such selector exists.

### 8. No "New Invoice" button on Invoices list page
**Walkthrough ref:** Chapter 5.3 ("Navigate to Invoices → Click New Invoice")
**File:** `frontend/app/(app)/org/[slug]/invoices/page.tsx`

The Invoices list page shows existing invoices with filters but **has no "New Invoice" creation button**. Invoice creation is only accessible from customer detail → Invoices tab.

---

## Medium Severity Gaps

### 9. Customer form: no "Contact Name" field
**Walkthrough ref:** Chapter 2.1 ("Fill in: Name, Contact Name, Contact Email, Phone")
**File:** `frontend/components/customers/create-customer-dialog.tsx`

The actual form fields are: Name, Email, Phone, ID Number, Notes. There is no separate "Contact Name" field. The walkthrough implies a company name + contact person distinction that doesn't exist.

### 10. "Add Customer" vs "New Customer" button label
**Walkthrough ref:** Chapter 2.1 ("Click Add Customer")
**File:** `frontend/components/customers/create-customer-dialog.tsx`

The button label is "New Customer", not "Add Customer".

### 11. Comment visibility toggle restricted to leads/admins
**Walkthrough ref:** Chapter 4.3 ("Comments can be Internal or Shared")
**File:** `frontend/components/comments/add-comment-form.tsx`

The visibility toggle (Internal/Shared) is only shown to users with `canManageVisibility` permission (admin/owner/lead). Regular members cannot toggle visibility — their comments default to Internal. The walkthrough implies any member can set visibility.

### 12. Email notification toggles disabled ("Coming soon")
**Walkthrough ref:** Chapter 10.4 ("Each type has in-app and email toggles")
**File:** `frontend/components/notifications/notification-preferences-form.tsx`

The email toggle column is permanently disabled with a "Coming soon" tooltip. Only in-app notifications are functional. The walkthrough presents both as working.

### 13. Cost Rates: tab switch, not "scroll down"
**Walkthrough ref:** Chapter 1.3 ("Settings → Rates, scroll down")
**File:** `frontend/app/(app)/org/[slug]/settings/rates/page.tsx`

Billing Rates and Cost Rates are in separate **tabs** (Billing Rates | Cost Rates), not stacked vertically. The walkthrough says "scroll down" to find cost rates.

### 14. Rates page: no top-level "Add Rate" button
**Walkthrough ref:** Chapter 1.3 ("Click Add Rate")
**File:** `frontend/components/rates/`

There is no single top-level "Add Rate" button. Rate creation is per-member via an inline button in each row that has no rate set. The walkthrough implies a single prominent button.

### 15. Document upload: no "Confirm" step
**Walkthrough ref:** Chapter 2.6 ("Upload a file → After upload, click Confirm to finalize")
**File:** `frontend/components/customers/customer-document-upload.tsx`

Upload happens automatically via presigned URL + XHR with a progress indicator. There is no separate "Confirm" button — the dialog auto-closes after upload completes. The walkthrough describes a two-step flow.

### 16. Post-signup flow: redirect, not dashboard prompt
**Walkthrough ref:** Chapter 1.1 ("After sign-up, land on Dashboard with prompt to create org")
**File:** `frontend/app/(app)/dashboard/page.tsx`

After sign-up with no org, the user is **redirected directly** to `/create-org` (showing the Clerk widget), not to a dashboard page that prompts org creation.

### 17. Project detail tab order differs
**Walkthrough ref:** Chapter 3 ("Tabs: Overview, Documents, Customers, Members, Tasks...")
**File:** `frontend/components/projects/project-tabs.tsx`

Actual tab order: Overview, Documents, **Members, Customers**, Tasks...
Walkthrough order: Overview, Documents, **Customers, Members**, Tasks...
(Members and Customers are swapped.)

### 18. Lead transfer: dropdown menu, not role badge click
**Walkthrough ref:** Chapter 3.3 ("Transfer Project Lead: click the role indicator next to his name")
**File:** `frontend/components/projects/project-members-panel.tsx`

Lead transfer is accessed via a three-dot (⋮) dropdown menu on a non-lead member row → "Transfer Lead" item. The role badge/indicator itself is not clickable.

### 19. Invoice generation dialog: no customer/project selector
**Walkthrough ref:** Chapter 5.3 ("Select customer, select project, date range")
**File:** `frontend/components/invoices/invoice-generation-dialog.tsx`

The dialog is scoped to a single customer (pre-determined from context). There is no customer or project selector — all unbilled time for the customer is shown grouped by project.

### 20. Retainer consumption: no inline alert banners
**Walkthrough ref:** Chapter 6.5 ("At 80% consumption, notification warning; at 100%, overage alert")
**File:** `frontend/components/retainers/retainer-detail-client.tsx`

There are no visible alert banners on the retainer detail page at 80% or 100%. The progress bar changes colour, and backend notifications fire, but the walkthrough implies prominent on-page alerts.

### 21. Recurring schedule: name pattern comes from template
**Walkthrough ref:** Chapter 6.3 ("Project name pattern: {customer} Bookkeeping — {month} {year}")
**File:** `frontend/components/recurring/create-schedule-dialog.tsx`

The name pattern is defined in the **template**, not configured per-schedule. The schedule form has an optional "Name Override" field but no pattern editor with `{customer}`, `{month}`, `{year}` tokens.

### 22. No template detail view (read-only)
**Walkthrough ref:** Chapter 8.1 ("Click into one to see the Thymeleaf HTML template")
**File:** `frontend/app/(app)/org/[slug]/settings/templates/`

There is no read-only template detail page. The only way to view a template's content is via the edit page (`/settings/templates/[id]/edit`). Template names in the list are not clickable links.

### 23. No navigation to draft invoice after retainer period close
**Walkthrough ref:** Chapter 6.6 ("Close Period → generates DRAFT invoice")

After closing a retainer period, no navigation or link to the generated draft invoice is provided. The walkthrough implies the user can immediately review the generated invoice.

---

## Low Severity Gaps

### 24. Customer lifecycle: DORMANT and OFFBOARDING states omitted
**Walkthrough ref:** Chapter 2.4 ("Prospect → Onboarding → Active → Offboarded")

The actual state machine includes DORMANT (between Active and Offboarding) and OFFBOARDING (between Active/Dormant and Offboarded). The walkthrough simplifies this to four states.

### 25. Customer detail tabs: "Retainer" tab not mentioned
**Walkthrough ref:** Chapter 2.1 ("Tabs: Projects, Documents, Invoices, Rates, Generated Docs, Financials, Onboarding")

The actual tab list includes a "Retainer" tab (conditional on lifecycle status). The walkthrough omits it.

### 26. Onboarding "Start Checklist" vs "Manually Add Checklist"
**Walkthrough ref:** Chapter 2.5 ("Click Start Checklist")

The button label is "Manually Add Checklist", not "Start Checklist".

### 27. My Work KPI: "Utilization" vs "Billable %"
**Walkthrough ref:** Chapter 4.2 ("Personal KPIs: utilization rate")

The KPI card is labelled "Billable %" not "Utilization rate". Same concept, different label.

### 28. Project health: "Team capacity" not a signal
**Walkthrough ref:** Chapter 9.3 ("Health score considers: Team capacity")
**File:** `frontend/components/projects/project-health-calculator.ts` (or backend equivalent)

The health calculator uses: budget consumption, overdue task ratio, days since last activity, and task existence. "Team capacity" is not a factor.

### 29. Log Time: two separate fields, not "hours or minutes"
**Walkthrough ref:** Chapter 4.1 ("Duration: 2.5 hours or 150 minutes")
**File:** `frontend/components/tasks/log-time-dialog.tsx`

Duration input is two separate numeric fields (Hours + Minutes side by side), not a single field with a unit toggle.

### 30. Billable toggle on Log Time not mentioned
**Walkthrough ref:** Chapter 4.1

The Log Time form includes a prominent "Billable" checkbox that the walkthrough does not mention.

### 31. Budget form has Currency and Notes fields not mentioned
**Walkthrough ref:** Chapter 3.5 ("Hours budget, Amount budget, Alert threshold")
**File:** `frontend/components/budget/budget-config-dialog.tsx`

The form also includes Currency (required when amount is set) and Notes (optional). Walkthrough omits these.

### 32. Custom field types understated
**Walkthrough ref:** Chapter 1.3 ("field types: Text, Dropdown, Number")

The implementation supports 9 types: Text, Number, Date, Dropdown, Boolean, Currency, URL, Email, Phone. The walkthrough mentions only 3.

### 33. Data Request: "Generate Export" vs "Export Data"
**Walkthrough ref:** Chapter 7.3 ("Export Data → generates a data package")

The button label is "Generate Export", not "Export Data". Functionally identical.

### 34. Conditional tab visibility not documented
**Walkthrough ref:** Multiple chapters

Several tabs on project and customer detail pages are conditional on the user's role (`canManage` — lead/admin/owner). Financials, Rates, and Generated Docs tabs are hidden from regular members. The walkthrough doesn't mention role-based tab visibility.

---

## Summary Statistics

| Severity | Count |
|----------|-------|
| Critical | 3 |
| High | 5 |
| Medium | 15 |
| Low | 11 |
| **Total** | **34** |

## Recommended Actions

### Fix in Code (functionality bugs)
1. **Create `/invoices/new` page** or update action card links to open `InvoiceGenerationDialog` instead of navigating to a non-existent route (Critical #1)
2. **Add Assignee selector to Create Task dialog** — allow leads/admins to pre-assign tasks (Critical #2)

### Fix in Walkthrough (documentation drift)
3. Update all button labels to match actual UI ("New Customer", "Manually Add Checklist", "Generate Export", etc.)
4. Correct UX flow descriptions (tab-based rates, inline task expansion, automatic upload)
5. Add lifecycle states DORMANT and OFFBOARDING
6. Clarify invoice creation flow (from customer detail, not from invoices list page)
7. Document role-based visibility restrictions (comment visibility, conditional tabs)
8. Remove references to "Total Revenue" KPI and "Team capacity" health signal

### Feature Gaps to Evaluate (build or document as "not yet")
9. Tags & Custom Fields on Tasks (High #5)
10. Saved Views on Tasks (High #6)
11. Template preview entity selector (High #7)
12. Dedicated task detail page vs inline expansion (decide which is the intended UX)
13. Email notifications (currently stubbed)
14. "New Invoice" button on invoices list page
