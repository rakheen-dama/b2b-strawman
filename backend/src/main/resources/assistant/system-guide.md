# DocTeams System Guide

DocTeams is a B2B professional services platform designed for law firms, accounting firms, and consulting firms. It provides time tracking, invoicing, project management, customer lifecycle management, budgets, profitability reporting, document management, and compliance tools — all within a multi-tenant architecture where each organization has its own isolated data.

---

## Navigation

DocTeams uses a sidebar divided into six navigation zones. All routes are scoped under `/org/{slug}/`.

### Zone 1: Work
Personal productivity and scheduling.

| Page | Path | Description |
|------|------|-------------|
| Dashboard | `/org/{slug}/dashboard` | Overview of recent activity, pending tasks, and key metrics for the org |
| My Work | `/org/{slug}/my-work` | Your assigned tasks across all projects, grouped by status |
| My Work → Timesheet | `/org/{slug}/my-work/timesheet` | Personal time entry log and weekly totals |
| Calendar | `/org/{slug}/calendar` | Calendar view of tasks with due dates and deadlines |
| Court Calendar | `/org/{slug}/court-calendar` | Legal deadline tracking (law firm vertical only) |
| Deadlines | `/org/{slug}/deadlines` | Regulatory and court filing deadlines |

### Zone 2: Delivery
Project and document management.

| Page | Path | Description |
|------|------|-------------|
| Projects | `/org/{slug}/projects` | List of all projects. Click a project to see its detail page with tasks, documents, time, and budget tabs |
| Project Detail | `/org/{slug}/projects/{id}` | Project overview with tabs: Tasks, Documents, Time, Budget, Financials, Activity |
| Documents | `/org/{slug}/documents` | Organisation-wide document list |
| Recurring Schedules | `/org/{slug}/schedules` | Recurring task schedules and templates |

### Zone 3: Clients
Customer and client management.

| Page | Path | Description |
|------|------|-------------|
| Customers | `/org/{slug}/customers` | Customer list. Lifecycle: PROSPECT → ONBOARDING → ACTIVE → DORMANT → OFFBOARDING |
| Customer Detail | `/org/{slug}/customers/{id}` | Customer profile with tabs: Overview, Projects, Invoices, Documents, Activity |
| Retainers | `/org/{slug}/retainers` | Retainer agreements linking customers to monthly commitments |
| Compliance | `/org/{slug}/compliance` | FICA/KYC compliance checklist status per customer |
| Information Requests | `/org/{slug}/information-requests/{id}` | Compliance data requests sent to customers |

### Zone 4: Finance
Billing, invoicing, and financial reporting.

| Page | Path | Description |
|------|------|-------------|
| Invoices | `/org/{slug}/invoices` | Invoice list. Statuses: DRAFT → APPROVED → SENT → PAID (or VOID) |
| Invoice Detail | `/org/{slug}/invoices/{id}` | Full invoice with line items, payment status, and PDF preview |
| Billing Runs | `/org/{slug}/invoices/billing-runs` | Batch invoice generation for multiple customers |
| New Billing Run | `/org/{slug}/invoices/billing-runs/new` | Create a billing run from unbilled time entries |
| Proposals | `/org/{slug}/proposals` | Fee proposals sent to prospective clients |
| Profitability | `/org/{slug}/profitability` | Organisation-level profitability by project and customer |
| Reports | `/org/{slug}/reports` | Custom financial and utilisation reports |
| Trust Accounting | `/org/{slug}/trust-accounting` | Client trust account management (law firm vertical only) |

### Zone 5: Team & Resources
Staff and capacity management.

| Page | Path | Description |
|------|------|-------------|
| Team | `/org/{slug}/team` | Org members, roles (OWNER, ADMIN, MEMBER), and billing rates |
| Resources | `/org/{slug}/resources` | Resource allocation across projects |
| Utilisation | `/org/{slug}/resources/utilization` | Member utilisation rates and capacity planning |

### Zone 6: Settings
Configuration and administration.

| Area | Path | Description |
|------|------|-------------|
| General | `/org/{slug}/settings/general` | Org name, logo, footer, brand colour |
| Billing | `/org/{slug}/settings/billing` | Subscription plan, member limits |
| Rates & Currency | `/org/{slug}/settings/rates` | Default billing rates, cost rates, and currency |
| Integrations | `/org/{slug}/settings/integrations` | Third-party integrations including AI assistant (BYOAK) |
| Notifications | `/org/{slug}/settings/notifications` | Notification preferences per event type |
| Document Templates | `/org/{slug}/settings/templates` | Branded document templates for invoices and proposals |
| Custom Fields | `/org/{slug}/settings/custom-fields` | Add custom fields to projects, customers, or invoices |
| Tags | `/org/{slug}/settings/tags` | Tag taxonomy for projects and customers |
| Roles | `/org/{slug}/settings/roles` | Custom roles and capability assignments |
| Compliance | `/org/{slug}/settings/compliance` | FICA/KYC checklist template configuration |
| Time Tracking | `/org/{slug}/settings/time-tracking` | Time entry rounding, billing rate defaults |
| Tax | `/org/{slug}/settings/tax` | Tax rates and per-line tax configuration |
| Automations | `/org/{slug}/settings/automations` | Workflow automations triggered by entity state changes |

---

## Common Workflows

### Creating a new client engagement
1. Go to **Clients → Customers** and create a new customer (starts as PROSPECT)
2. Complete the FICA/KYC compliance checklist (auto-transitions to ACTIVE when all items checked)
3. Go to **Delivery → Projects**, click "New Project", link it to the customer
4. Add tasks under the project's Tasks tab
5. Team members log time against tasks from My Work or the project's Time tab

### Generating an invoice from unbilled time
1. Go to **Finance → Invoices → Billing Runs**
2. Click "New Billing Run" — select the billing period and currency
3. Review unbilled time entries per customer
4. Click "Generate Invoices" — creates DRAFT invoices
5. Review each draft: **Finance → Invoices**, open the invoice
6. Click "Approve" to set status to APPROVED
7. Click "Send" to mark as SENT (and optionally email the customer)
8. When payment arrives, click "Record Payment"

### Viewing profitability
- **Project profitability**: Go to the project detail page → Financials tab
- **Customer profitability**: Go to the customer detail page → Financials tab
- **Org-level profitability**: Go to **Finance → Profitability**

### Tracking budget vs. actuals
1. Go to a project → Budget tab
2. Set a budget (hours, amount, or both) and alert threshold
3. The budget status shows ON_TRACK, AT_RISK, or OVER_BUDGET
4. Alerts are sent when the threshold is crossed

### Checking unbilled WIP (Work in Progress)
- For a specific customer: use the `get_unbilled_time` tool with `customerId`
- For a specific project: use the `get_unbilled_time` tool with `projectId`
- For a billing overview: go to Finance → Invoices → Billing Runs → New Billing Run

---

## Terminology

| Term in DocTeams | Alternative Terms Used by Clients | Meaning |
|-----------------|----------------------------------|---------|
| Project | Matter (legal), Engagement (accounting), Job | A bounded unit of work for a customer |
| Customer | Client, Matter Party | An external organisation or individual |
| Task | Work item, Activity | A discrete piece of work within a project |
| Time Entry | Time log, Billable hours, WIP | A logged duration against a task |
| Invoice | Bill, Tax Invoice, Fee Account | A payment demand sent to a customer |
| Billing Run | Batch billing, End-of-month billing | Generating multiple invoices at once |
| Unbilled time | WIP, Unbilled WIP, Work in progress | Time entries not yet on an invoice |
| Retainer | Standing order, Monthly fee | A recurring fixed-fee arrangement |
| Proposal | Fee proposal, Letter of engagement | A pre-engagement document with quoted fees |
| Profitability | Margin, P&L | Revenue minus cost per project/customer |
| FICA | KYC, AML, Due diligence | Client identity verification compliance |
| Lifecycle status | Customer status | PROSPECT → ONBOARDING → ACTIVE → DORMANT → OFFBOARDING |
| Org | Organization, Firm, Practice | The tenant (your company) |
| Member | Staff member, User, Employee | A person with access to the org |
| Owner | Firm owner, Partner | Highest org role — full access |
| Admin | Manager | Second-tier org role — most settings access |
| Member | Staff | Base org role — project access only |

---

## Quick Reference

### Invoice statuses
- `DRAFT` — editable, not yet approved
- `APPROVED` — reviewed and approved, ready to send
- `SENT` — delivered to customer
- `PAID` — payment received
- `VOID` — cancelled; time entries unlocked for re-billing

### Project statuses
- `ACTIVE` — in progress
- `COMPLETED` — work done, may still have unbilled time
- `ARCHIVED` — closed, read-only

### Task statuses
- `OPEN` — not started
- `IN_PROGRESS` — being worked on
- `DONE` — completed
- `CANCELLED` — abandoned

### Customer lifecycle
- `PROSPECT` → Cannot create projects or invoices until ONBOARDING
- `ONBOARDING` → Complete all FICA checklist items to auto-advance
- `ACTIVE` → Full access to projects, invoices, time entries
- `DORMANT` → Inactive but not offboarded
- `OFFBOARDING` → In the process of leaving
- `OFFBOARDED` → Fully closed

### Capabilities (what roles can do)
- `PROJECT_MANAGEMENT` — create/edit/archive projects, manage project members
- `CUSTOMER_MANAGEMENT` — create/edit customers, manage lifecycle
- `INVOICING` — view and manage invoices, run billing
- `FINANCIAL_VISIBILITY` — view profitability reports, budgets, rate cards
- `RESOURCE_PLANNING` — manage resource allocations and capacity
- Owners and Admins have all capabilities. Members have PROJECT_MANAGEMENT only (unless custom roles are configured).
