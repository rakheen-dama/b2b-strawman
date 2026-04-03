# DocTeams UX Brief — Functional Specification for Redesign

**Date**: 2026-02-28
**Purpose**: Provide a UX designer with complete functional knowledge of the DocTeams platform to produce mockups for a redesigned frontend. No styling guidance — only features, flows, data, and user needs.
**Constraint**: The backend API is fixed. The new frontend must consume the existing REST API unchanged.

## What This Is

DocTeams is a multi-tenant B2B SaaS platform for professional services firms (agencies, consultancies, accounting firms, law firms). It helps teams manage clients, projects, tasks, time tracking, invoicing, document generation, and profitability analysis.

The platform has three frontend surfaces:
1. **Main App** — the firm-facing SaaS application (the primary redesign target)
2. **Customer Portal** — a client-facing self-service app (secondary, should be consistent)
3. **Landing Page** — marketing/signup page (lowest priority)

## Files in This Brief

| File | Contents |
|------|----------|
| [00-product-overview.md](00-product-overview.md) | Product positioning, user personas, roles, plan tiers |
| [01-information-architecture.md](01-information-architecture.md) | Navigation structure, all routes, page hierarchy |
| [02-projects-tasks.md](02-projects-tasks.md) | Projects, tasks, subtasks, assignment, lifecycle |
| [03-customers-lifecycle.md](03-customers-lifecycle.md) | Customers, lifecycle states, onboarding checklists, compliance |
| [04-time-tracking.md](04-time-tracking.md) | Time entries, My Work, billable/non-billable, rate snapshots |
| [05-invoicing-billing.md](05-invoicing-billing.md) | Invoice lifecycle, line items, tax, payments, generation from time |
| [06-documents-templates.md](06-documents-templates.md) | Document uploads, templates, clauses, PDF generation, acceptance |
| [07-rates-budgets-profitability.md](07-rates-budgets-profitability.md) | Rate cards, cost rates, budgets, profitability reports, utilization |
| [08-retainers-schedules.md](08-retainers-schedules.md) | Retainer agreements, recurring schedules, period management |
| [09-settings-admin.md](09-settings-admin.md) | Org settings, integrations, tags, custom fields, views, email, tax |
| [10-notifications-activity.md](10-notifications-activity.md) | Notifications, comments, activity feeds, preferences |
| [11-reports.md](11-reports.md) | Report definitions, execution, export |
| [12-customer-portal.md](12-customer-portal.md) | Portal auth, pages, features, branding |
| [13-api-surface.md](13-api-surface.md) | Complete REST API endpoint reference |
| [14-entity-model.md](14-entity-model.md) | Data model, entity relationships, state machines |
| [15-known-pain-points.md](15-known-pain-points.md) | Identified UX issues and areas for improvement |
| [16-sample-data.md](16-sample-data.md) | Realistic sample data for mockups (firm, members, customers, projects, invoices, time entries, dashboards) |

## Key Design Principles (Not Styling — Functional)

1. **Multi-tenant**: Every user belongs to an organization. All data is org-scoped. Users switch orgs.
2. **Role-based access**: Owner > Admin > Member. Some actions are restricted by role.
3. **Plan-gated features**: Starter (2 members) vs Pro (10 members). Feature limits, not feature locks.
4. **Entity lifecycle**: Customers, projects, and tasks all have state machines with guarded transitions.
5. **Everything is audited**: Every create/update/delete produces an audit event. Activity feeds surface this.
6. **Documents are a core surface**: Template-based PDF generation for engagement letters, invoices, proposals. This is revenue-adjacent functionality, not a utility.
