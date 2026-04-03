# Phase 59 — User Help Documentation Site

## System Context

HeyKazi is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with 58 phases of functionality spanning projects, customers, tasks, time tracking, invoicing, proposals, documents, expenses, rate cards, budgets, profitability, reports, resource planning, workflow automations, custom fields, information requests, customer portal, AI assistant, and more. The platform is approaching production with infrastructure (Phase 56), subscription billing (Phase 57), and demo readiness (Phase 58) in place.

The current state relevant to this phase:

- **Inline contextual help** (Phase 43): `HelpTooltip` component renders "?" icons throughout the app with brief help text. `EmptyStateCard` components on empty pages explain what the feature does and how to get started. `GettingStartedChecklist` guides new users through initial setup. All text sourced from the i18n message catalog.
- **i18n message catalog** (Phase 43): `lib/i18n/messages/` with `en.ts` covering all UI labels and help text. `TerminologyProvider` (Phase 48) overrides default terms per org (e.g., "Project" → "Matter" for legal firms).
- **Vertical profiles** (Phase 49): `VerticalProfile` enum (GENERIC, ACCOUNTING, LEGAL). Profiles control which modules, packs, and UI sections are visible. Accounting firms see deadline calendars and filing statuses. Legal firms see court calendar, conflict check, and LSSA tariff modules (once Phase 55 is built).
- **Phase 51 (Accounting Practice Management)**: `DeadlineTypeRegistry`, filing status tracking, SARS deadline types (ITR14, provisional tax, VAT201), recurring engagement post-schedule actions, rate and schedule profile pack seeders.
- **Phase 55 (Legal Foundations)**: Specced but NOT built. Court calendar, conflict check, LSSA tariff entities do not exist yet. Legal vertical guides should cover what's available today (generic features + legal terminology overrides + legal module stubs) and note upcoming features.
- **Landing page** (Phase 3): Marketing site at the root domain. Doc site will be a separate subdomain.
- **Monorepo structure**: `frontend/` (Next.js 16), `backend/` (Spring Boot 4), `portal/` (Next.js, customer portal), `compose/`, `infra/`. The doc site will be a new top-level directory.

**The problem**: HeyKazi has 58 phases of deep functionality but zero user-facing documentation. The inline contextual help (tooltips, empty states, getting started checklist) provides in-context guidance but can't replace a searchable, browsable knowledge base. As the product approaches production with real tenants, self-service documentation is critical for keeping support costs near zero at the 5-20 tenant scale. Users need a place to learn features in depth, understand workflows end-to-end, and find answers without contacting support.

**The solution**: A standalone Nextra documentation site at `docs.heykazi.com` with comprehensive feature guides, vertical-specific sections, and contextual deep-links from the main app's help tooltips.

## Objective

1. **Scaffold a Nextra doc site** in the monorepo (`docs/` directory), themed to match HeyKazi branding, deployed as a separate Vercel project at `docs.heykazi.com`.
2. **Write comprehensive feature guides** (~16 articles) covering every major domain in the platform — from project management through invoicing, time tracking, document generation, and beyond.
3. **Write getting started guides** (3 articles) that walk a new user from first login to productive usage.
4. **Write admin/settings guides** (4 articles) covering team management, org settings, integrations, and billing.
5. **Write vertical-specific guides** for accounting firms (3 articles covering SARS deadlines, recurring engagements, compliance packs) and legal firms (stub articles noting upcoming features from Phase 55).
6. **Wire contextual deep-links** from the main app's existing `HelpTooltip` components and `EmptyStateCard` components to specific doc site pages, so users can jump from in-app help to detailed documentation.

***

## Constraints and Assumptions

1. **Architecture constraints**
   - Nextra 4.x with Next.js App Router — matches the frontend stack (Next.js 16, React 19, TypeScript).
   - MDX content files in the monorepo under `docs/`. Content is version-controlled alongside code so it can evolve with features.
   - Deployed as a separate Vercel project (`docs.heykazi.com`), not part of the main frontend build.
   - Tailwind CSS v4 for styling, matching HeyKazi brand colors and typography from the design foundation (Phase 3/Phase 30).
   - Built-in Nextra search (Flexsearch-based) — no external search service needed at this scale.

2. **Content constraints**
   - Text-only guides for v1 — no screenshots, no embedded videos, no animated GIFs. Well-structured text with code examples where relevant (API responses, configuration snippets).
   - Content is platform-authored (the HeyKazi team writes it). Tenants cannot contribute or customize help content.
   - Content should be written for the **end user** (firm staff) — not developers. Assume no technical background. Use the product's UI terminology consistently.
   - Where vertical-specific terminology applies (e.g., "Matter" instead of "Project" for legal), note the terminology difference in the guide.
   - Agents will draft content based on codebase analysis (reading controllers, services, frontend components, existing inline help text). The founder reviews and edits.

3. **Linking constraints**
   - The main app's `HelpTooltip` and `EmptyStateCard` components gain an optional `docsUrl` prop that links to the doc site.
   - Links open in a new tab (`target="_blank"`).
   - Use relative paths on the doc site (e.g., `/features/invoicing`) so the base URL can change without updating every link.
   - Define a `DOCS_BASE_URL` environment variable in the frontend config (default: `https://docs.heykazi.com`).

4. **What NOT to build**
   - No API reference or developer documentation (this is end-user help, not a developer portal)
   - No changelog automation or release notes integration
   - No in-app help widget or embedded iframe (the doc site is a separate destination)
   - No search analytics, page view tracking, or feedback mechanisms in v1
   - No user comments or community features on doc pages
   - No versioning or multi-version docs (one version — current)
   - No PDF export of documentation
   - No AI-powered search or chatbot on the doc site (the in-app AI assistant from Phase 52 already covers conversational help)

***

## Section 1 — Nextra Project Scaffold

### 1.1 Project Structure

New top-level directory in the monorepo:

```
docs/
├── app/                          # Next.js App Router pages (Nextra 4)
│   └── layout.tsx                # Root layout with theme config
├── content/                      # MDX content files
│   ├── getting-started/
│   ├── features/
│   ├── admin/
│   ├── verticals/
│   │   ├── accounting/
│   │   └── legal/
│   └── index.mdx                 # Landing/home page
├── components/                   # Custom doc components (callouts, feature cards)
├── public/                       # Static assets (logo, favicon)
├── theme.config.tsx              # Nextra theme configuration
├── next.config.ts                # Next.js config with Nextra plugin
├── tailwind.config.ts            # Tailwind config (HeyKazi brand tokens)
├── tsconfig.json
├── package.json
└── .env.local                    # Local dev env vars
```

### 1.2 Theming & Branding

- Match HeyKazi's brand: primary color (from `OrgSettings.brandColor` default), font stack, logo.
- Dark mode support (Nextra provides this out of the box — keep it enabled).
- Custom footer with HeyKazi copyright, link to main app, link to landing page.
- Custom header with HeyKazi logo + "Docs" label, search bar, link back to app.

### 1.3 Navigation Structure

Nextra generates navigation from the file tree. The sidebar should follow this structure:

```
Getting Started
  ├── Quick Setup
  ├── Invite Your Team
  └── Your First Project

Features
  ├── Projects
  ├── Customers
  ├── Tasks
  ├── Time Tracking
  ├── Invoicing
  ├── Documents & Templates
  ├── Proposals
  ├── Expenses
  ├── Rate Cards & Budgets
  ├── Reports & Data Export
  ├── Resource Planning
  ├── Workflow Automations
  ├── Custom Fields & Tags
  ├── Information Requests
  ├── Customer Portal
  └── AI Assistant

Administration
  ├── Team & Permissions
  ├── Organization Settings
  ├── Integrations
  └── Billing & Subscription

For Accounting Firms
  ├── SARS Deadline Management
  ├── Recurring Engagements
  └── Compliance Packs

For Legal Firms (Coming Soon)
  ├── Court Calendar
  ├── Conflict Checks
  └── LSSA Tariff Billing
```

### 1.4 Deployment

- Separate Vercel project linked to the monorepo with root directory set to `docs/`.
- Custom domain: `docs.heykazi.com`.
- Build command: `next build` (or Nextra's recommended build command).
- Environment variable: `NEXT_PUBLIC_APP_URL=https://app.heykazi.com` (for "Back to App" links).

***

## Section 2 — Getting Started Guides

Three articles that walk a brand-new user from first login to being productive.

### 2.1 Quick Setup (`getting-started/quick-setup.mdx`)

Content outline:
- What HeyKazi is (1 paragraph overview)
- First login experience — what you see after Keycloak auth
- The Getting Started Checklist (reference in-app checklist from Phase 43)
- Setting up your organization: org name, branding (logo, brand color), timezone
- Overview of the main navigation zones (sidebar sections from Phase 44)
- Where to find help (this doc site, in-app tooltips, AI assistant)

### 2.2 Invite Your Team (`getting-started/invite-your-team.mdx`)

Content outline:
- How team invitations work (Keycloak orgs + JIT provisioning from Phase 36/46)
- Sending invitations from the Team page
- Understanding roles and permissions (owner, admin, member — link to admin/team-permissions guide)
- Custom roles and capabilities (Phase 41 OrgRole system)
- What new team members see on first login

### 2.3 Your First Project (`getting-started/first-project.mdx`)

Content outline:
- Creating a project (manual vs. from template)
- Adding a customer and linking them to the project
- Creating tasks and assigning team members
- Logging your first time entry
- Viewing project overview (dashboard, time summary, budget)
- Next steps: invoicing, documents, proposals

***

## Section 3 — Core Feature Guides

Each guide should follow a consistent structure:
1. **What it does** (1-2 paragraph overview)
2. **Key concepts** (terminology, statuses, relationships to other features)
3. **How to use it** (step-by-step for primary workflows)
4. **Tips & best practices** (2-3 practical tips)
5. **Related features** (links to other guides)

### 3.1 Projects (`features/projects.mdx`)

Cover:
- Project creation (manual, from template, from accepted proposal)
- Project lifecycle: PLANNING → ACTIVE → ON_HOLD → COMPLETED → ARCHIVED (Phase 29)
- Project members and access control
- Project settings (customer link, due date, budget)
- Project templates and recurring schedules (Phase 16)
- Project overview tab (health score, time summary, budget status)
- Project tabs: Tasks, Time, Documents, Expenses, Activity, Budget, Financials

### 3.2 Customers (`features/customers.mdx`)

Cover:
- Customer lifecycle: PROSPECT → ONBOARDING → ACTIVE → DORMANT → ARCHIVED (Phase 14)
- Creating customers (smart intake dialog with prerequisite checks from Phase 33)
- Onboarding checklists and compliance packs (Phase 14)
- Customer-project linking
- Customer detail tabs: Projects, Documents, Invoices, Proposals, Information Requests, Financials, Data Protection
- Customer portal contacts (Phase 7 — magic links)

### 3.3 Tasks (`features/tasks.mdx`)

Cover:
- Task creation and assignment
- Task lifecycle: OPEN → IN_PROGRESS → IN_REVIEW → COMPLETED → CANCELLED (Phase 29)
- Task detail sheet (Phase 18) — side panel with all task information
- Claiming and releasing tasks
- Recurring tasks (Phase 30)
- Task tags and custom fields
- Task saved views (Phase 11)
- My Work page — cross-project personal task view (Phase 5)

### 3.4 Time Tracking (`features/time-tracking.mdx`)

Cover:
- Logging time (Log Time dialog, task-level, project-level)
- Billable vs. non-billable time
- Bulk time entry (weekly grid from Phase 48)
- Calendar view (Phase 30)
- Time reminders (Phase 30 — org settings + personal preferences)
- Rate snapshots (how billing rates are captured at time of entry)
- Time entries on the My Work page

### 3.5 Invoicing (`features/invoicing.mdx`)

Cover:
- Invoice lifecycle: DRAFT → APPROVED → SENT → PARTIALLY_PAID → PAID → VOID (Phase 10)
- Creating invoices manually
- Generating invoices from unbilled time (Phase 10, Epic 83)
- Billing runs (Phase 40 — bulk invoice generation across customers)
- Invoice line items (time-based, expense-based, manual)
- Tax handling (Phase 26 — tax rates, VAT calculation)
- Invoice preview and PDF generation
- Sending invoices via email (Phase 24)
- Payment collection (Phase 25 — Stripe/PayFast payment links)
- Invoice custom fields (Phase 23)
- Retainer invoicing (Phase 17 — period close and billing)

### 3.6 Documents & Templates (`features/documents.mdx`)

Cover:
- Document types: rich text (Tiptap editor) and DOCX templates (Phase 42)
- Document templates: creating, editing, cloning templates
- Template variables and context builders (project, customer, invoice data)
- Clause library (Phase 27 — reusable document clauses)
- Generating documents from templates (generation dialog, variable substitution)
- PDF generation and download
- DOCX merge pipeline (Phase 42 — upload Word template, field discovery, merge)
- Document acceptance / e-signing (Phase 28 — send for acceptance, portal signing)
- Document scopes (project-level, customer-level)

### 3.7 Proposals (`features/proposals.mdx`)

Cover:
- Proposal lifecycle: DRAFT → SENT → VIEWED → ACCEPTED → REJECTED → EXPIRED (Phase 32)
- Creating proposals (Tiptap editor, template-based)
- Sending proposals to clients (email + portal link)
- Client portal acceptance flow
- Accepted proposal → project conversion (engagement orchestration from Phase 33)
- Proposal pipeline stats and list view
- Proposal expiry (automatic expiry processor)

### 3.8 Expenses (`features/expenses.mdx`)

Cover:
- Logging expenses (project-level, categories, receipts)
- Expense billing integration (marking expenses as billable, including in invoices)
- Unbilled expense summary on project and invoice views
- Expense categories and amounts

### 3.9 Rate Cards & Budgets (`features/rate-cards-budgets.mdx`)

Cover:
- Rate hierarchy: org default → project override → customer override (Phase 8)
- Cost rates (internal cost tracking)
- Setting up billing rates for team members
- Project budgets (hours and currency)
- Budget status indicators and alerts
- Budget vs. actual tracking
- Profitability reports (project, customer, org, utilization)

### 3.10 Reports & Data Export (`features/reports.mdx`)

Cover:
- Built-in report types: Timesheet, Invoice Aging, Project Profitability (Phase 19)
- Running reports with date ranges and filters
- Report rendering (table view, CSV/Excel export)
- Saved report definitions
- Profitability dashboards (Phase 8/9 — company dashboard, project financials)

### 3.11 Resource Planning (`features/resource-planning.mdx`)

Cover:
- Allocations: assigning team members to projects with hour budgets (Phase 38)
- Allocation grid (weekly/monthly view)
- Capacity forecasting (available hours vs. allocated)
- Utilization tracking (actual hours vs. capacity)
- Project staffing tab
- Notifications for over/under-allocation

### 3.12 Workflow Automations (`features/workflow-automations.mdx`)

Cover:
- Automation rules: trigger → condition → action (Phase 37)
- Trigger types (task status changed, customer status changed, invoice status changed, proposal sent, field date approaching, etc.)
- Condition evaluation (field checks, status checks)
- Action types (update field, send notification, change status, create task, delayed actions)
- Automation templates (pre-built rules from template gallery)
- Execution log (monitoring what automations have run)
- Cycle detection and safety

### 3.13 Custom Fields & Tags (`features/custom-fields-tags.mdx`)

Cover:
- Field definitions and field types (text, number, date, select, multi-select, boolean)
- Field groups and auto-apply rules (Phase 23)
- Conditional field visibility (Phase 23)
- Adding custom fields to entities (projects, customers, tasks, invoices)
- Tags: creating, applying, filtering by tags (Phase 11)
- Saved views: filtered, sorted, column-customized list views (Phase 11)
- Field packs (pre-configured field sets seeded per vertical)

### 3.14 Information Requests (`features/information-requests.mdx`)

Cover:
- Request templates (Phase 34 — reusable templates for common document requests)
- Sending information requests to clients (email + portal link)
- Client portal upload flow
- Review and approve/reject submissions
- Request reminders and expiry
- Project template integration (auto-create requests when project starts)
- Dashboard widget for pending requests

### 3.15 Customer Portal (`features/customer-portal.mdx`)

Cover:
- What clients see in the portal (projects, invoices, proposals, documents, information requests)
- Portal contacts and magic link authentication (Phase 7)
- Client actions: view project status, download documents, accept proposals, sign documents, upload requested files, pay invoices
- Portal branding (org logo, brand color from org settings)
- Enabling/disabling portal features

### 3.16 AI Assistant (`features/ai-assistant.mdx`)

Cover:
- What the AI assistant can do (Phase 52 — BYOAK Claude integration)
- Setting up your API key (BYOAK — bring your own API key)
- Asking questions about your data (read tools: projects, customers, tasks, time entries, invoices, etc.)
- Taking actions with confirmation (write tools: create tasks, log time, update statuses)
- Chat panel in the app shell
- Privacy: API key stored encrypted, data stays within your tenant

***

## Section 4 — Admin & Settings Guides

### 4.1 Team & Permissions (`admin/team-permissions.mdx`)

Cover:
- Default roles: Owner, Admin, Member (Phase 41)
- Custom roles and capability-based permissions (Phase 41)
- Capability groups: financial, invoicing, project, customer, automation, resource, team
- Creating custom roles with specific capability sets
- Assigning roles to team members
- Pending invitations (Phase 46)
- How capabilities affect sidebar visibility and page access

### 4.2 Organization Settings (`admin/org-settings.mdx`)

Cover:
- General settings: org name, branding (logo, brand color, footer text)
- Vertical profile selection (Generic, Accounting, Legal)
- Terminology overrides (Phase 48 — customize labels per org)
- Notification settings (default preferences for new members)
- Time tracking settings (time reminders, default billable status)
- Document acceptance settings (expiry period, certificate format)
- Data protection settings (retention periods, POPIA jurisdiction)
- Module visibility per vertical profile

### 4.3 Integrations (`admin/integrations.mdx`)

Cover:
- Integration infrastructure (Phase 21 — BYOAK, ports + adapters pattern)
- Email delivery: SMTP default vs. SendGrid BYOAK (Phase 24)
- Payment gateway: PayFast / Stripe integration for invoice payments (Phase 25)
- Storage: S3/LocalStack for document storage
- AI assistant: Claude API key setup (Phase 52)
- Integration status indicators on settings page

### 4.4 Billing & Subscription (`admin/billing.mdx`)

Cover:
- Subscription lifecycle: trial → active → cancellation → grace period → locked (Phase 57)
- What happens during trial (full access, countdown banner)
- Subscribing via PayFast (card billing)
- Admin-managed billing (for pilot, debit order, complimentary arrangements)
- Read-only mode (grace period behavior)
- Locked state (what's blocked, how to restore)
- Member limits per subscription

***

## Section 5 — Vertical Guides

### 5.1 Accounting Firm Guides

#### 5.1.1 SARS Deadline Management (`verticals/accounting/sars-deadlines.mdx`)

Cover:
- Deadline types: ITR14, Provisional Tax (IRP6), VAT201, Annual Financial Statements, PAYE EMP501
- How deadline calculation works (Phase 51 `DeadlineTypeRegistry` — financial year end → SARS due dates)
- Deadline calendar page (monthly/yearly view)
- Filing status tracking per customer (NOT_DUE, DUE, OVERDUE, FILED)
- Dashboard deadline widget
- Automation integration: `FIELD_DATE_APPROACHING` trigger for deadline reminders

#### 5.1.2 Recurring Engagements (`verticals/accounting/recurring-engagements.mdx`)

Cover:
- Project templates for common accounting engagements (monthly bookkeeping, annual audit, tax returns)
- Recurring schedules (Phase 16 — auto-create projects on schedule)
- Post-schedule actions (Phase 51 — engagement kickoff: create tasks, set deadlines, notify team)
- Schedule pack seeders (Phase 51 — pre-configured schedules for SARS deadlines)
- Managing recurring work across multiple clients

#### 5.1.3 Compliance Packs (`verticals/accounting/compliance-packs.mdx`)

Cover:
- What compliance packs are (Phase 14 — checklist templates for customer onboarding)
- Accounting-specific compliance items: FICA verification, tax clearance, CIPC status, SARS registration
- Onboarding checklist flow (PROSPECT → ONBOARDING → complete checklist → ACTIVE)
- Custom compliance items (adding firm-specific checks)
- Compliance dashboard visibility

### 5.2 Legal Firm Guides (Stubs)

Legal modules (Phase 55) are specced but not yet built. These pages should:
- Explain what the feature will do (brief description)
- Note "Coming Soon" status clearly
- Describe what IS available today for legal firms (vertical profile, legal terminology overrides, module stubs)
- Link to related generic features that legal firms can use now

#### 5.2.1 Court Calendar (`verticals/legal/court-calendar.mdx`) — STUB
- Planned: court date tracking, hearing management, prescription monitoring, reminder automation
- Available now: project due dates, task deadlines, calendar view (generic equivalents)

#### 5.2.2 Conflict Checks (`verticals/legal/conflict-checks.mdx`) — STUB
- Planned: adverse party registry, conflict search algorithm, advisory warnings
- Available now: customer records with custom fields for adverse party notes

#### 5.2.3 LSSA Tariff Billing (`verticals/legal/lssa-tariff.mdx`) — STUB
- Planned: tariff schedule entity, tariff-based invoice line items, prescribed fee calculations
- Available now: manual line items on invoices, billing rate cards

***

## Section 6 — Contextual Deep-Links from Main App

### 6.1 Link Infrastructure

Add to the main frontend app:

- `NEXT_PUBLIC_DOCS_URL` environment variable (default: `https://docs.heykazi.com`)
- A `docsLink(path: string)` utility function that constructs `${DOCS_URL}${path}`
- Extend `HelpTooltip` component with an optional `docsPath` prop — when provided, renders a "Learn more" link below the tooltip text
- Extend `EmptyStateCard` component with an optional `docsPath` prop — renders a "Read the guide" link in the empty state

### 6.2 Link Mapping

Map existing inline help touchpoints to doc pages. Key mappings:

| App Location | Docs Path |
|-------------|-----------|
| Projects empty state | `/features/projects` |
| Customers empty state | `/features/customers` |
| Tasks empty state | `/features/tasks` |
| Time entries empty state | `/features/time-tracking` |
| Invoices empty state | `/features/invoicing` |
| Documents empty state | `/features/documents` |
| Proposals empty state | `/features/proposals` |
| Reports page | `/features/reports` |
| Rate cards settings | `/features/rate-cards-budgets` |
| Automations empty state | `/features/workflow-automations` |
| Custom fields settings | `/features/custom-fields-tags` |
| Information requests empty state | `/features/information-requests` |
| Resource planning empty state | `/features/resource-planning` |
| Team settings page | `/admin/team-permissions` |
| Org settings page | `/admin/org-settings` |
| Integrations settings page | `/admin/integrations` |
| Billing page | `/admin/billing` |
| AI assistant first-use | `/features/ai-assistant` |
| Getting started checklist | `/getting-started/quick-setup` |

### 6.3 "Help" in Navigation

Add a "Help" item to the app sidebar (bottom section, near settings):
- Icon: question mark circle or book
- Clicking opens `docs.heykazi.com` in a new tab
- This is in addition to the existing contextual help tooltips throughout the app

***

## Section 7 — Doc Site Home Page

The doc site landing page (`content/index.mdx`) should include:

1. **Hero section**: "HeyKazi Documentation" with a brief tagline and search bar
2. **Quick links**: Cards linking to Getting Started, Features, Administration, Verticals
3. **Popular articles**: 4-6 links to the most commonly needed guides (time tracking, invoicing, projects, getting started)
4. **Vertical sections**: "For Accounting Firms" and "For Legal Firms" cards with brief descriptions

***

## Section 8 — Testing Strategy

### 8.1 Doc Site Build

- Verify Nextra project builds successfully (`next build` exits 0)
- Verify all MDX files render without errors
- Verify all internal links resolve (no broken cross-references between doc pages)
- Verify search index is generated and functional

### 8.2 Frontend Link Integration

- Verify `HelpTooltip` renders "Learn more" link when `docsPath` is provided
- Verify `EmptyStateCard` renders "Read the guide" link when `docsPath` is provided
- Verify links construct correct URLs using `NEXT_PUBLIC_DOCS_URL` env var
- Verify links open in new tab

***

## ADR Topics

1. **Nextra over alternatives** — Why Nextra 4 was chosen over Mintlify, Fumadocs, Docusaurus, or GitBook. Key factors: Next.js App Router compatibility (same stack as the main app), MDX in the monorepo (content evolves with code), Vercel deployment (existing platform), zero SaaS cost, built-in search via Flexsearch.

2. **Separate site vs. in-app help** — Why a standalone doc site (`docs.heykazi.com`) rather than an embedded help widget or in-app help center. The in-app AI assistant (Phase 52) already provides conversational help. Inline tooltips (Phase 43) provide contextual guidance. A separate doc site complements both by offering deep, browsable, searchable reference material. This also keeps the main app bundle size unaffected.

3. **Content authorship model** — Why platform-authored only (not tenant-editable). At 5-20 tenant scale, one set of well-maintained content is more valuable than a system that lets each tenant create their own (which would require a content management infrastructure, permissions, and per-tenant storage). Tenant-editable content can be added later if demand materializes.

***

## Style and Boundaries

- Doc site project: new `docs/` top-level directory in the monorepo. Standard Next.js project with Nextra plugin. No shared code imports from `frontend/` — the doc site is fully independent.
- Content: MDX files under `docs/content/`. Follow Nextra conventions for frontmatter (`title`, `description`), page ordering (`_meta.json` files), and navigation.
- Components: minimal custom components. Use Nextra's built-in `Callout`, `Steps`, `Tabs`, `Cards` components. Only create custom components if Nextra's built-ins don't cover a need.
- Theming: Tailwind CSS v4 config with HeyKazi brand tokens. Customize Nextra's default theme — don't build a custom theme from scratch.
- Frontend changes: limited to adding `docsPath` prop to `HelpTooltip` and `EmptyStateCard` components, adding a `docsLink()` utility, wiring ~20 link mappings, and adding a "Help" sidebar item. These are small, targeted changes to existing components.
- Content tone: friendly, professional, task-oriented. Use "you" language. Keep sentences short. Lead with what the user wants to accomplish, then explain how. Avoid jargon. Use the product's UI labels exactly as they appear in the app.
- Content length: each feature guide should be 500-1000 words. Getting started guides can be longer (800-1200 words). Vertical guides 400-800 words. Stubs 150-300 words.
