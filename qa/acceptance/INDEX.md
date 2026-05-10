# Feature Acceptance Test Index

Master catalog of all Kazi platform features, organized by functional domain.
Each feature area links to a detailed acceptance test spec (created incrementally via `/acceptance-spec`).

## How to Use

1. Pick a feature area from the index below
2. Run `/acceptance-spec <feature-id>` to generate/update its detailed spec
3. The skill reads phase requirements + architecture docs and produces acceptance criteria
4. Specs live in `qa/acceptance/specs/{feature-id}.md`

## Status Key

| Symbol | Meaning |
|--------|---------|
| `[ ]` | No spec written yet |
| `[S]` | Spec written, no automation |
| `[A]` | Automated (Playwright tests exist) |
| `[P]` | Partial automation |

---

## Domain 1 — Authentication & Tenancy

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `auth-login` | Login / logout / session lifecycle | 36 | — | auth | [ ] |
| `auth-rbac` | Role-based capabilities & permission gating | 41, 46 | auth-login | auth | [ ] |
| `auth-jit-provisioning` | JIT tenant + member provisioning from Keycloak | 36 | auth-login | auth | [ ] |
| `tenant-provisioning` | Admin-approved org provisioning (access request → approval → creation) | 39 | auth-login | auth | [ ] |
| `tenant-subscriptions` | Subscription payments (PayFast), billing method, read-only enforcement | 57, 58 | tenant-provisioning | billing-platform | [ ] |

## Domain 2 — Team & Members

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `team-members` | Member list, invite, remove, role assignment | 1, 41, 46 | auth-rbac | team | [ ] |
| `team-roles` | Custom OrgRole CRUD, capability matrix, role assignment | 41, 46 | team-members | team | [ ] |
| `team-invitations` | PendingInvitation lifecycle, Keycloak invite flow | 46 | team-members | team | [ ] |

## Domain 3 — Projects

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `project-crud` | Create, edit, list, search, filter projects | 1 | auth-login | project-core | [ ] |
| `project-members` | Add/remove project members, access control | 1 | team-members, project-crud | project-core | [ ] |
| `project-lifecycle` | Status transitions (ACTIVE → ON_HOLD → COMPLETED → ARCHIVED) + guards | 29 | project-crud | project-core | [ ] |
| `project-budget` | Budget configuration (hours/currency), threshold alerts, status visualization | 8 | project-crud, rate-cards | project-finance | [ ] |
| `project-templates` | Template CRUD, save-from-project, instantiate-from-template | 16 | project-crud | project-ops | [ ] |
| `project-schedules` | Recurring schedule CRUD, scheduler execution engine | 16 | project-templates | project-ops | [ ] |

## Domain 4 — Tasks & Work

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `task-crud` | Create, edit, list, assign, claim/release tasks | 4 | project-crud | task-core | [ ] |
| `task-lifecycle` | Status transitions + guards, task detail sheet | 29, 18 | task-crud | task-core | [ ] |
| `task-recurring` | Recurring task rules, auto-generation | 30 | task-crud | task-ops | [ ] |
| `my-work` | Personal task/time dashboard, filters, grouping | 5, 18 | task-crud, time-entries | work | [ ] |
| `calendar-view` | Calendar page with time entries + tasks | 30 | time-entries, task-crud | work | [ ] |

## Domain 5 — Time & Expenses

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `time-entries` | Time entry CRUD, billable/non-billable, rate snapshots | 5, 8 | task-crud, rate-cards | time-billing | [ ] |
| `time-bulk-entry` | Weekly grid bulk time entry | 48 | time-entries | time-billing | [ ] |
| `time-reminders` | Reminder scheduler, org settings, member preferences | 30 | time-entries | time-ops | [ ] |
| `expenses` | Expense CRUD, receipt upload, billable flag, project tab | 30 | project-crud | expense | [ ] |
| `expense-billing` | Expense → invoice line, unbilled expense summary | 30 | expenses, invoicing | expense | [ ] |

## Domain 6 — Rate Cards & Profitability

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `rate-cards` | Org default rates, project overrides, customer overrides, member rates | 8 | team-members | finance-foundation | [ ] |
| `profitability` | Project P&L, customer profitability, margin analysis | 8 | rate-cards, time-entries | finance-analytics | [ ] |

## Domain 7 — Invoicing & Billing

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `invoicing` | Invoice CRUD, lifecycle (DRAFT→APPROVED→SENT→PAID→VOID), line items | 10 | time-entries, rate-cards | invoice-core | [S] |
| `invoice-tax` | Tax rates, tax calculation engine, tax display on preview/PDF/portal | 26 | invoicing | invoice-core | [S] |
| `invoice-generation` | Generate invoice from unbilled time + expenses, customer selection | 10, 30 | invoicing, time-entries, expenses | invoice-core | [ ] |
| `invoice-email` | Invoice delivery via email, payment link inclusion | 24 | invoicing, email-delivery | invoice-delivery | [ ] |
| `invoice-payments` | Online payment collection (Stripe, PayFast), webhook reconciliation | 25 | invoicing | invoice-payments | [ ] |
| `billing-runs` | Batch billing: preview, cherry-pick, generate, approve, send | 40 | invoicing, invoice-generation | billing-batch | [ ] |
| `retainers` | Retainer agreements, consumption tracking, period close, invoice generation | 17 | invoicing, time-entries | retainer | [ ] |

## Domain 8 — Customers & Client Management

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `customer-crud` | Customer create, edit, list, search, link to projects | 4 | project-crud | customer-core | [ ] |
| `customer-lifecycle` | Status transitions (PROSPECT → ACTIVE → DORMANT → ARCHIVED) + guards | 14, 29 | customer-crud | customer-core | [ ] |
| `customer-compliance` | Compliance checklists, pack seeding, instantiation | 14 | customer-crud | customer-compliance | [ ] |
| `customer-data-protection` | Data export, anonymization, retention, DSAR requests | 50 | customer-crud | data-protection | [ ] |
| `information-requests` | Request templates, send to client, portal submission, review, reminders | 34 | customer-crud, portal-core | client-comms | [ ] |

## Domain 9 — Proposals & Engagements

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `proposal-crud` | Create, edit, list proposals; pipeline stats | 32 | customer-crud | proposal | [ ] |
| `proposal-lifecycle` | DRAFT → SENT → ACCEPTED/DECLINED; portal acceptance orchestration | 32 | proposal-crud, portal-core | proposal | [ ] |
| `proposal-to-project` | Accepted proposal → auto-create project + retainer | 32 | proposal-lifecycle, project-crud | proposal | [ ] |
| `prerequisites` | Prerequisite definitions, lifecycle transition gates, engagement prerequisites | 33 | proposal-lifecycle, customer-lifecycle | prerequisites | [ ] |

## Domain 10 — Documents

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `document-crud` | Document upload, list, download, scope (project/customer/global) | 1, 4 | project-crud | doc-core | [ ] |
| `document-templates` | Tiptap template CRUD, variable system, category/tag | 12, 31 | — | doc-templates | [ ] |
| `document-clauses` | Clause library CRUD, template-clause association, pack seeding | 27 | document-templates | doc-templates | [ ] |
| `document-generation` | Generate document from template + variables, preview, download PDF | 12, 31 | document-templates, document-clauses | doc-generation | [ ] |
| `document-word` | DOCX template upload, field discovery, merge, PDF conversion | 42 | document-templates | doc-generation | [ ] |
| `document-acceptance` | Send for e-signature, portal acceptance, certificate generation, expiry | 28 | document-generation, portal-core | doc-signing | [ ] |

## Domain 11 — Portal (Client-Facing)

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `portal-auth` | Magic link authentication, token exchange, session | 7, 22 | customer-crud | portal-core | [ ] |
| `portal-projects` | Project list, detail, tasks, documents view | 22 | portal-auth, project-crud | portal-pages | [ ] |
| `portal-invoices` | Invoice list, detail, payment flow | 22 | portal-auth, invoicing | portal-pages | [ ] |
| `portal-proposals` | Proposal view, accept/decline flow | 32 | portal-auth, proposal-lifecycle | portal-pages | [ ] |
| `portal-requests` | Information request upload + submit | 34 | portal-auth, information-requests | portal-pages | [ ] |
| `portal-acceptance` | Document acceptance page, pending list | 28 | portal-auth, document-acceptance | portal-pages | [ ] |
| `portal-trust-ledger` | Trust ledger view (legal-za) | 68 | portal-auth, trust-accounting | portal-vertical | [ ] |
| `portal-retainer-usage` | Retainer usage view (legal-za, consulting-za) | 68 | portal-auth, retainers | portal-vertical | [ ] |
| `portal-deadlines` | Deadline visibility (accounting-za, legal-za) | 68 | portal-auth, deadlines-acct | portal-vertical | [ ] |
| `portal-branding` | Org branding display in portal | 22 | portal-auth | portal-pages | [ ] |
| `portal-data-isolation` | Cross-customer security (contact A cannot see contact B's data) | 22 | portal-auth | portal-security | [ ] |

## Domain 12 — Communication & Activity

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `comments` | Comment CRUD on projects/tasks/customers, threaded display | 6.5 | project-crud | communication | [ ] |
| `activity-feed` | Activity tab on entities, event formatting, filters | 6.5 | comments | communication | [ ] |
| `notifications-inapp` | Notification bell, notification page, mark read, preferences | 6.5 | — | notifications | [ ] |
| `email-delivery` | Email provider port, SMTP/SendGrid adapter, template rendering, delivery log | 24 | — | notifications | [ ] |
| `email-notifications` | Email channel for domain events, unsubscribe, bounce handling | 24 | email-delivery, notifications-inapp | notifications | [ ] |

## Domain 13 — Dashboards & Reporting

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `dashboard-company` | Company-wide KPIs, revenue, utilization, health scores | 9 | profitability | dashboards | [ ] |
| `dashboard-personal` | Personal dashboard, my tasks, my time, upcoming | 9 | my-work | dashboards | [ ] |
| `dashboard-project` | Project overview tab, health score, budget status | 9 | project-budget, profitability | dashboards | [ ] |
| `reports` | Report definitions, execution framework, timesheet/aging/profitability queries | 19 | time-entries, invoicing | reports | [ ] |
| `report-export` | CSV/PDF export pipeline, rendering | 19 | reports | reports | [ ] |

## Domain 14 — Resource Planning

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `resource-allocation` | Allocation grid, capacity service, utilization tracking | 38 | team-members, project-crud | resources | [ ] |
| `resource-dashboard` | Utilization dashboard, project staffing UI | 38 | resource-allocation | resources | [ ] |

## Domain 15 — Workflow & Automation

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `automation-rules` | Rule CRUD, trigger matching, condition evaluation, template gallery | 37 | — | automation | [ ] |
| `automation-actions` | Action executors, variable resolution, delayed actions | 37 | automation-rules | automation | [ ] |
| `automation-log` | Execution log, dashboard widget | 37 | automation-rules | automation | [ ] |

## Domain 16 — Extensibility & Configuration

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `custom-fields` | Field definitions, field groups, auto-apply, conditional visibility | 11, 23, 63 | — | extensibility | [ ] |
| `tags` | Tag CRUD, entity tagging, filter by tag | 11 | — | extensibility | [ ] |
| `saved-views` | View definitions, filter/sort persistence, quick-switch | 11 | custom-fields, tags | extensibility | [ ] |
| `module-gating` | Feature module toggle, progressive disclosure, nav gating | 62 | — | config | [ ] |
| `packs-catalog` | Pack catalog, install pipeline, profile provisioning | 65 | module-gating | config | [ ] |
| `integration-ports` | Integration management API, BYOAK key storage, integration cards | 21 | — | integrations | [ ] |

## Domain 17 — Settings & Navigation

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `settings-general` | Org name, currency, branding, terminology overrides | 1, 48 | auth-login | settings | [ ] |
| `settings-layout` | Settings hub, sidebar navigation, breadcrumbs | 44 | — | settings | [ ] |
| `navigation` | Sidebar zones, mobile sidebar, command palette (⌘K) | 44 | auth-rbac | navigation | [ ] |
| `contextual-actions` | Action points, setup guidance, empty states | 15, 43 | — | ux | [ ] |
| `getting-started` | Onboarding checklist, inline help, error recovery | 43 | — | ux | [ ] |

## Domain 18 — Audit & Compliance

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `audit-trail` | Audit event capture for all domain events, security events | 6 | — | audit | [ ] |
| `audit-view` | Global audit log page, filters, row expansion, presets, export (CSV/PDF) | 69 | audit-trail | audit | [ ] |
| `audit-timeline` | Per-entity audit timeline component (customer/project/invoice tabs) | 69 | audit-trail | audit | [ ] |
| `audit-sensitive` | Sensitive events dashboard widget, matter closure override surface | 69 | audit-view | audit | [ ] |

## Domain 19 — AI Assistants

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `ai-chat` | In-app AI assistant, chat panel, SSE streaming, tool use | 52 | integration-ports | ai | [ ] |
| `ai-billing` | Billing specialist assistant, invoice grouping suggestions | 70 | ai-chat, invoicing | ai-specialists | [ ] |
| `ai-intake` | Intake assistant, vision document parsing, field diff review | 70 | ai-chat, customer-crud | ai-specialists | [ ] |

## Domain 20 — Platform Admin

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `admin-panel` | Platform admin panel, access request review, org management | 39 | tenant-provisioning | admin | [ ] |
| `admin-billing` | Admin billing management, billing method override | 58 | tenant-subscriptions | admin | [ ] |
| `admin-demo` | Demo tenant provisioning, data seeding, cleanup | 58 | admin-panel | admin | [ ] |

## Domain 21 — Vertical: Accounting (accounting-za)

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `acct-deadlines` | Deadline type registry, calculation service, calendar page | 51 | project-crud | accounting | [ ] |
| `acct-filing-status` | Filing status tracking per customer | 51 | customer-crud | accounting | [ ] |
| `acct-schedule-actions` | Post-schedule actions (engagement kickoff) | 51 | project-schedules | accounting | [ ] |

## Domain 22 — Vertical: Legal (legal-za)

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `legal-court-calendar` | Court date entity, calendar page, prescription tracker | 55 | project-crud | legal | [ ] |
| `legal-conflict-check` | Adverse party registry, conflict search algorithm | 55 | customer-crud | legal | [ ] |
| `legal-tariffs` | LSSA tariff schedule, invoice tariff integration | 55 | invoicing | legal | [ ] |
| `legal-disbursements` | Disbursement entity, invoicing integration, project tab | 67 | invoicing | legal | [ ] |
| `legal-matter-closure` | Matter closure workflow, checklist, trust balance guards | 67 | project-lifecycle, trust-accounting | legal | [ ] |
| `legal-statement-of-account` | Statement of account generation | 67 | invoicing, legal-disbursements | legal | [ ] |
| `legal-kyc` | KYC adapter infrastructure, verification dialog, checklist integration | 61 | customer-crud, integration-ports | legal | [ ] |

## Domain 23 — Vertical: Trust Accounting (legal-za, Section 86)

Trust accounting is separated from other legal features because it is a self-contained, regulation-heavy domain with strict isolation requirements.

| ID | Feature | Phase | Dependencies | Grouping | Status |
|----|---------|-------|-------------|----------|--------|
| `trust-accounts` | Trust account CRUD, LPFF rate configuration | 60 | module-gating | trust | [ ] |
| `trust-transactions` | Deposit, transfer, client ledger card | 60 | trust-accounts | trust | [ ] |
| `trust-approvals` | Approval workflow, payment/fee-transfer/refund recording | 60 | trust-transactions | trust | [ ] |
| `trust-reconciliation` | Bank statement import, auto-matching, manual matching | 60 | trust-transactions | trust | [ ] |
| `trust-interest` | Interest calculation, posting, investment basis distinction | 60, 61 | trust-transactions | trust | [ ] |
| `trust-investments` | Investment register, investment CRUD | 60 | trust-approvals | trust | [ ] |
| `trust-reports` | Section 35 reports, data pack | 60 | trust-transactions, trust-reconciliation | trust | [ ] |

---

## Inseparable Groupings

These feature clusters must be tested together — they share state and have tight coupling:

| Group | Features | Reason |
|-------|----------|--------|
| `auth` | auth-login, auth-rbac, auth-jit-provisioning | Session context flows through everything |
| `invoice-core` | invoicing, invoice-tax, invoice-generation | Tax and generation depend on invoice entity state |
| `project-core` | project-crud, project-members, project-lifecycle | Lifecycle guards reference members and state |
| `task-core` | task-crud, task-lifecycle | Lifecycle depends on task state |
| `customer-core` | customer-crud, customer-lifecycle | Lifecycle depends on customer state |
| `time-billing` | time-entries, rate-cards, time-bulk-entry | Rate snapshots bind time to billing |
| `proposal` | proposal-crud, proposal-lifecycle, proposal-to-project | Pipeline is end-to-end |
| `doc-templates` | document-templates, document-clauses | Clauses are template children |
| `doc-generation` | document-generation, document-word | Both use template + variable system |
| `trust` | All trust-* features | Regulatory domain with strict invariants |
| `portal-core` | portal-auth, portal-data-isolation | Auth + isolation are prerequisites for all portal features |
| `notifications` | notifications-inapp, email-delivery, email-notifications | Channel abstraction binds them |

## Suggested Test Order

Priority tiers for which specs to write first:

### P0 — Revenue Path (test these first)
1. `auth-login` → `auth-rbac`
2. `customer-crud` → `customer-lifecycle`
3. `project-crud` → `project-lifecycle` → `project-members`
4. `task-crud` → `task-lifecycle`
5. `time-entries` → `rate-cards`
6. `invoicing` → `invoice-tax` → `invoice-generation`
7. `invoice-payments`

### P1 — Client Experience
8. `portal-auth` → `portal-data-isolation`
9. `portal-projects` → `portal-invoices`
10. `proposal-crud` → `proposal-lifecycle` → `proposal-to-project`
11. `document-templates` → `document-generation` → `document-acceptance`
12. `information-requests` → `portal-requests`
13. `retainers`

### P2 — Operations & Productivity
14. `billing-runs`
15. `automation-rules` → `automation-actions`
16. `resource-allocation`
17. `my-work` → `calendar-view`
18. `dashboards` (company, personal, project)
19. `reports` → `report-export`

### P3 — Verticals & Advanced
20. `trust-accounts` → full trust chain
21. `legal-court-calendar` → `legal-conflict-check` → `legal-disbursements`
22. `acct-deadlines` → `acct-filing-status`
23. `ai-chat` → `ai-billing` → `ai-intake`
24. `audit-view` → `audit-timeline`
25. `admin-panel` → `admin-billing` → `admin-demo`
