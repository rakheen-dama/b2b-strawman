# Product Overview

## What DocTeams Does

DocTeams is practice-management software for professional services firms. It replaces the spreadsheet-and-email chaos of running a client-services business with a unified platform covering:

- **Client management** — track customers from prospect through onboarding to active engagement
- **Project delivery** — organize work into projects with tasks, assignments, and deadlines
- **Time tracking** — log billable and non-billable hours against tasks
- **Invoicing** — generate invoices from tracked time, manage the draft-to-paid lifecycle
- **Document generation** — create professional PDFs (engagement letters, proposals, NDAs) from templates
- **Profitability analysis** — understand margins per project, per customer, and across the firm
- **Client portal** — give customers self-service access to their projects, documents, and invoices

The target market is 5-50 person firms: agencies, consultancies, accounting practices, and (the founder's lighthouse vertical) small-to-medium law firms in South Africa.

## User Personas

### 1. Firm Owner / Partner
- **Goal**: Visibility into firm health — revenue, utilization, profitability
- **Daily use**: Dashboard, profitability reports, invoice approval
- **Pain points**: Doesn't know which projects are profitable until it's too late
- **Role**: OWNER (full access)

### 2. Practice Manager / Office Manager
- **Goal**: Keep operations running — onboard clients, assign work, chase invoices
- **Daily use**: Customer management, project setup, invoice generation, compliance checklists
- **Pain points**: Too many tabs, too many manual steps between "new client" and "first invoice"
- **Role**: ADMIN (most access, no plan/billing changes)

### 3. Team Member / Associate
- **Goal**: Know what to work on, log time accurately, move tasks forward
- **Daily use**: My Work, task detail, time logging
- **Pain points**: Context-switching between "what's assigned to me" across multiple projects
- **Role**: MEMBER (read projects, create tasks, log time, comment)

### 4. Client / Customer Contact (Portal)
- **Goal**: See project progress, review documents, pay invoices
- **Usage**: Occasional — check project status, download documents, accept engagement letters
- **Pain points**: "Where's my invoice?" emails to the firm
- **Auth**: Magic link (no password, no account creation)

## Roles & Permissions

| Capability | Owner | Admin | Member |
|-----------|-------|-------|--------|
| View dashboard & profitability | Yes | Yes | Personal only |
| Create/edit projects | Yes | Yes | No |
| Create/edit customers | Yes | Yes | No |
| Create/edit tasks | Yes | Yes | Yes (within assigned projects) |
| Log time | Yes | Yes | Yes |
| Create/approve invoices | Yes | Yes | No |
| Manage templates & clauses | Yes | Yes | No |
| Manage team members | Yes | Yes | No |
| Change org settings | Yes | Yes | No |
| Change billing plan | Yes | No | No |
| Delete projects/customers | Yes | No | No |

## Plan Tiers

| Feature | Starter | Pro |
|---------|---------|-----|
| Team members | Up to 2 | Up to 10 |
| Projects | Unlimited | Unlimited |
| Customers | Unlimited | Unlimited |
| Time tracking | Yes | Yes |
| Invoicing | Yes | Yes |
| Document generation | Yes | Yes |
| Custom fields | Yes | Yes |
| Profitability reports | Yes | Yes |
| Price | Free tier | Paid |

The difference is team size, not feature access. All features are available on Starter — just limited to 2 members. This keeps the upgrade path simple: "You need more people? Upgrade."

## Authentication

- **Main app**: Clerk (SSO provider) — email/password, Google OAuth, org switching
- **Customer portal**: Magic link emails — no password, no account creation. Token-based JWT auth stored in localStorage.
- **Backend**: JWT validation. Clerk JWT for main app, custom JWT for portal.
