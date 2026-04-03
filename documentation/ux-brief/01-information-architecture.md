# Information Architecture

## Navigation Structure

The main app uses a sidebar navigation with 14 top-level items. All org-scoped routes are under `/org/[slug]/`.

### Primary Navigation (Sidebar)

```
Dashboard              — Org-wide KPIs, project health, team workload, recent activity
My Work                — Personal task queue, time logged today, weekly summary, deadlines
Projects               — All projects list with filters → Project detail (tabbed)
Customers              — All customers list → Customer detail (tabbed)
Documents              — Org-level document list
Invoices               — All invoices list → Invoice detail
Retainers              — Retainer agreements list → Retainer detail
Recurring Schedules    — Scheduled project creation → Schedule detail
Profitability          — Firm-wide profitability analysis
Reports                — Configurable reports with export
Compliance             — Customer lifecycle overview, data requests, dormancy
Team                   — Member list, invitations, roles
Notifications          — Notification center
Settings               — 12+ sub-pages (see below)
```

### Settings Sub-Navigation

```
Settings
├── Organization       — Name, branding (logo, color, footer text)
├── Billing            — Plan tier, upgrade
├── Rates              — Billing rates (org-level defaults per member)
├── Tax                — Tax rates, default tax behavior
├── Tags               — Tag management (colors, names)
├── Custom Fields      — Field definitions, field groups, entity assignment
├── Templates          — Document templates (list, create, edit)
├── Clauses            — Clause library (system + custom clauses)
├── Project Templates  — Reusable project structures (tasks, members)
├── Checklists         — Onboarding checklist templates
├── Compliance         — Retention policies, compliance settings
├── Acceptance         — Document acceptance/e-sign settings
├── Email              — Email provider configuration, delivery log
├── Integrations       — Third-party connections (Stripe, PayFast, SendGrid)
├── Notifications      — Notification preferences per event type
```

## All Routes

### Public Routes
| Route | Page |
|-------|------|
| `/` | Landing page |
| `/sign-in` | Authentication (Clerk) |
| `/sign-up` | Registration (Clerk) |
| `/create-org` | Organization creation |
| `/dashboard` | Org selector / redirect |

### Org-Scoped Routes (`/org/[slug]/...`)

#### Main Pages
| Route | Page | Key Features |
|-------|------|-------------|
| `/dashboard` | Company Dashboard | KPI cards, project health widget, team workload, recent activity |
| `/my-work` | My Work | Assigned tasks, available tasks, urgency view, time logged today, weekly summary |
| `/projects` | Project List | Filterable list (status, search), create project dialog |
| `/projects/[id]` | Project Detail | Tabbed: Overview, Team, Time, Financials, Documents, Comments, Activity |
| `/customers` | Customer List | Filterable list (status, lifecycle), create customer dialog |
| `/customers/[id]` | Customer Detail | Tabbed: Projects, Invoices, Retainers, Financials, Documents, Compliance |
| `/documents` | Document List | Org-level documents, upload, visibility toggle |
| `/invoices` | Invoice List | Filterable list (status, customer), create draft dialog |
| `/invoices/[id]` | Invoice Detail | Line items, status actions, payment history, preview/download |
| `/retainers` | Retainer List | All retainer agreements with utilization indicators |
| `/retainers/[id]` | Retainer Detail | Usage vs capacity, period history, close period action |
| `/schedules` | Schedule List | Recurring schedules with execution history |
| `/schedules/[id]` | Schedule Detail | Configuration, execution history, next run |
| `/profitability` | Profitability | Project P&L table, customer profitability, team utilization |
| `/reports` | Reports | Report list, run report, export |
| `/reports/[reportSlug]` | Report Detail | Parameter form, results table, export actions |
| `/compliance` | Compliance Dashboard | Lifecycle distribution, onboarding pipeline, dormancy check |
| `/compliance/requests` | Data Requests | GDPR data subject requests list |
| `/compliance/requests/[id]` | Request Detail | Request timeline, export/deletion actions |
| `/team` | Team Management | Members table, pending invitations, invite form |
| `/notifications` | Notification Center | All notifications, mark read, preferences link |

#### Settings Pages
| Route | Page |
|-------|------|
| `/settings` | Organization settings (branding, name) |
| `/settings/billing` | Plan & billing |
| `/settings/rates` | Billing & cost rates |
| `/settings/tax` | Tax rate management |
| `/settings/tags` | Tag management |
| `/settings/custom-fields` | Field definitions & groups |
| `/settings/templates` | Document template list |
| `/settings/templates/new` | Create template |
| `/settings/templates/[id]/edit` | Edit template |
| `/settings/clauses` | Clause library |
| `/settings/project-templates` | Project template list |
| `/settings/project-templates/[id]` | Project template detail |
| `/settings/checklists` | Checklist template list |
| `/settings/checklists/new` | Create checklist template |
| `/settings/checklists/[id]` | Checklist template detail |
| `/settings/checklists/[id]/edit` | Edit checklist template |
| `/settings/compliance` | Compliance & retention settings |
| `/settings/acceptance` | Acceptance/e-sign settings |
| `/settings/email` | Email provider & delivery log |
| `/settings/integrations` | Third-party integrations |
| `/settings/notifications` | Notification preferences |

### Customer Portal Routes (`/portal/...`)
| Route | Page |
|-------|------|
| `/portal` | Redirect (auth check) |
| `/portal/login` | Magic link request |
| `/portal/auth/exchange` | Token exchange |
| `/portal/accept/[token]` | Document acceptance (unauthenticated) |
| `/portal/projects` | Project list (authenticated) |
| `/portal/projects/[id]` | Project detail (tasks, documents, comments) |
| `/portal/invoices` | Invoice list (authenticated) |
| `/portal/invoices/[id]` | Invoice detail + payment |
| `/portal/profile` | Contact profile |

## Page Count Summary

| Surface | Pages |
|---------|-------|
| Main app — primary pages | 22 |
| Main app — settings | 18 |
| Customer portal | 9 |
| Auth & public | 4 |
| **Total** | **53** |
