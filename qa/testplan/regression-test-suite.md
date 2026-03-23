# Regression Test Suite — DocTeams Platform

> **Purpose**: Comprehensive regression test specification for a QA automation engineer to implement in Playwright.
> **Baseline**: Phases 1–50 complete. Existing E2E: 44 tests (smoke, lifecycle-read, lifecycle-interactive, portal).
> **Target**: ~180–220 automated tests covering all functional areas.
> **Stack**: Playwright + Chromium, Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / Keycloak 8180 / Mailpit 8025). See `qa/keycloak-e2e-guide.md` for setup.

---

## Coverage Analysis

### What's Automated Today

| File | Tests | Coverage Type |
|------|-------|--------------|
| `smoke.spec.ts` | 3 | Login, project creation, RBAC smoke |
| `lifecycle.spec.ts` | 21 | Read-only page load assertions on seeded data |
| `lifecycle-interactive.spec.ts` | 13 | Customer create, status transition, project/task/time CRUD, invoice approval, proposals, RBAC |
| `lifecycle-portal.spec.ts` | 7 | Portal page loads, API verification |
| **Total** | **44** | |

### Critical Gaps

1. **No CRUD coverage** for: invoices (create), proposals (create/send/accept), retainers, documents, templates, automations, custom fields, tags, rates, expenses, schedules, information requests, billing runs, compliance, data protection
2. **No state machine testing** — customer/invoice/proposal/task lifecycle transitions only partially covered (create + one transition)
3. **No negative testing** — invalid transitions, permission denials, guard enforcement
4. **No financial accuracy testing** — invoice math, rate resolution, tax calculations
5. **No cross-feature integration** — time→invoice, proposal→project, template→document→acceptance
6. **No data protection testing** (Phase 50) — export, anonymization, retention, DSAR
7. **No vertical architecture testing** (Phase 49) — profile switching, module gates
8. **No automation/notification testing** — triggers, email verification, preferences
9. **Portal coverage is skeletal** — no proposal acceptance, no document download, no data isolation

---

## Test Suite Architecture

### Conventions

```
frontend/e2e/tests/
├── smoke.spec.ts                          # Existing — keep as-is
├── auth/
│   ├── rbac-capabilities.spec.ts          # P0 — role-gated page access
│   └── session-lifecycle.spec.ts          # P1 — login, logout, token expiry
├── customers/
│   ├── customer-crud.spec.ts              # P0 — create, edit, list, search
│   ├── customer-lifecycle.spec.ts         # P0 — full state machine + guards
│   └── customer-data-protection.spec.ts   # P1 — export, anonymize, DSAR
├── projects/
│   ├── project-crud.spec.ts              # P0 — create, edit, archive
│   ├── project-tasks.spec.ts             # P0 — task CRUD, status transitions
│   ├── project-time.spec.ts              # P0 — time entry CRUD, rate snapshots
│   ├── project-documents.spec.ts         # P1 — attach, view, download
│   ├── project-budget.spec.ts            # P1 — budget config, threshold alerts
│   ├── project-activity.spec.ts          # P2 — activity feed, filters
│   └── project-expenses.spec.ts          # P1 — expense CRUD, billable flag
├── invoices/
│   ├── invoice-crud.spec.ts              # P0 — create, line items, edit draft
│   ├── invoice-lifecycle.spec.ts         # P0 — DRAFT→APPROVED→SENT→PAID→VOID
│   ├── invoice-arithmetic.spec.ts        # P0 — line math, tax, rounding
│   └── billing-runs.spec.ts             # P2 — bulk billing wizard
├── proposals/
│   ├── proposal-crud.spec.ts             # P1 — create, edit, form fields
│   ├── proposal-lifecycle.spec.ts        # P1 — DRAFT→SENT→ACCEPTED/DECLINED
│   └── proposal-acceptance.spec.ts       # P1 — portal acceptance→auto project
├── retainers/
│   └── retainer-crud.spec.ts             # P1 — create, view, billing
├── documents/
│   ├── template-management.spec.ts       # P1 — CRUD, clone, reset
│   ├── document-generation.spec.ts       # P1 — generate, preview, download
│   └── document-acceptance.spec.ts       # P1 — send, accept, certificate
├── portal/
│   ├── portal-auth.spec.ts              # P0 — magic link, token exchange
│   ├── portal-navigation.spec.ts         # P1 — home, projects, documents, requests
│   ├── portal-proposal-acceptance.spec.ts # P1 — view, accept, decline
│   ├── portal-data-isolation.spec.ts     # P0 — cross-customer security
│   └── portal-branding.spec.ts           # P2 — org branding in portal
├── settings/
│   ├── general-settings.spec.ts          # P1 — currency, org name, branding
│   ├── rate-cards.spec.ts                # P1 — billing/cost rate CRUD
│   ├── tax-settings.spec.ts              # P1 — tax rate CRUD
│   ├── custom-fields.spec.ts             # P2 — field definition CRUD
│   ├── tags.spec.ts                      # P2 — tag CRUD
│   ├── data-protection-settings.spec.ts  # P1 — jurisdiction, retention, processing register
│   └── roles-capabilities.spec.ts        # P2 — role management
├── automations/
│   ├── automation-crud.spec.ts           # P1 — create, edit, enable/disable
│   └── automation-triggers.spec.ts       # P2 — trigger firing, execution history
├── notifications/
│   ├── notification-bell.spec.ts         # P1 — unread count, mark read
│   └── notification-preferences.spec.ts  # P2 — channel toggles, persistence
├── finance/
│   ├── profitability.spec.ts             # P1 — dashboard loads with data
│   └── reports.spec.ts                   # P2 — report generation, parameters
├── verticals/
│   ├── profile-switching.spec.ts         # P1 — switch profile, verify effects
│   └── module-gates.spec.ts              # P1 — legal module pages gated
├── resources/
│   └── resource-planning.spec.ts         # P2 — page loads, utilization view
├── navigation/
│   ├── sidebar-navigation.spec.ts        # P1 — all nav items resolve
│   └── command-palette.spec.ts           # P2 — Cmd+K, search, navigate
├── compliance/
│   └── compliance-overview.spec.ts       # P2 — dashboard, requests
└── information-requests/
    └── information-request-crud.spec.ts  # P1 — create, send, track
```

### Shared Infrastructure Needed

```typescript
// e2e/fixtures/auth.ts — EXTEND existing
// Add: loginAsPortalContact(page, customerEmail, orgSlug)
// Add: getApiToken(userId) → JWT for direct API calls

// e2e/fixtures/api.ts — NEW
// Direct API helpers for setup/teardown and verification
// Example: createCustomerViaApi(), getInvoiceViaApi(), etc.

// e2e/fixtures/seed.ts — NEW
// Seed state assertions — verify lifecycle-test.sh data is present
// Provide known IDs for seeded entities

// e2e/page-objects/ — NEW (recommended)
// CustomerListPage, CustomerDetailPage
// ProjectListPage, ProjectDetailPage
// InvoiceListPage, InvoiceDetailPage
// SettingsPage (general, rates, tax, etc.)
// PortalPage (home, projects, documents)
```

---

## P0 — Critical Path Tests (~50 tests)

These failures mean the product is fundamentally broken. Run on every PR.

---

### AUTH-01: RBAC Capabilities (`auth/rbac-capabilities.spec.ts`)

Tests that role-based access control works across all gated pages.

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Owner can access all settings pages | Alice | Navigate to each settings route | All pages load without error |
| 2 | Admin can access most settings | Bob | Navigate to settings/general, rates, tax, custom-fields | Pages load |
| 3 | Member blocked from rate cards | Carol | Navigate to `/settings/rates` | Permission restriction shown |
| 4 | Member blocked from profitability | Carol | Navigate to `/profitability` | Permission restriction or redirect |
| 5 | Member blocked from reports | Carol | Navigate to `/reports` | Permission restriction |
| 6 | Member can access My Work | Carol | Navigate to `/my-work` | Page loads with content |
| 7 | Member can access Projects | Carol | Navigate to `/projects` | Page loads |
| 8 | Member blocked from customer management | Carol | Navigate to `/customers` | Verify behaviour (may show read-only) |
| 9 | Admin can manage team | Bob | Navigate to `/team` | Page loads, actions available |
| 10 | Member blocked from roles settings | Carol | Navigate to `/settings/roles` | Permission restriction |

---

### CUST-01: Customer CRUD (`customers/customer-crud.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Create customer with required fields | Alice | Open dialog, fill Name+Email+Phone, submit | Customer appears in list |
| 2 | Create customer with custom fields | Alice | Open dialog, fill Step 1, advance to Step 2, fill custom fields, submit | Customer created, custom fields saved |
| 3 | Edit customer name | Alice | Open customer detail, edit name, save | Name updated |
| 4 | Search customer list | Alice | Type in search input | List filters to matching customers |
| 5 | Customer list pagination | Alice | Verify pagination controls if >10 customers | Pagination works |

---

### CUST-02: Customer Lifecycle (`customers/customer-lifecycle.spec.ts`)

Full state machine testing. Uses seeded + freshly created customers.

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | New customer defaults to PROSPECT | Alice | Create customer | Status badge shows "Prospect" |
| 2 | PROSPECT → ONBOARDING | Bob | Click Change Status → Start Onboarding | Badge shows "Onboarding" |
| 3 | ONBOARDING → ACTIVE (via checklist completion) | Alice | Complete all checklist items | Auto-transition to "Active" |
| 4 | PROSPECT blocked from creating project | Alice | Navigate to prospect customer, attempt New Project | Action blocked or hidden |
| 5 | PROSPECT blocked from creating invoice | Alice | Attempt to create invoice for prospect customer | Action blocked |
| 6 | ACTIVE → DORMANT | Alice | Change Status → Mark Dormant | Badge shows "Dormant" |
| 7 | DORMANT → OFFBOARDING | Alice | Change Status → Start Offboarding | Badge shows "Offboarding" |
| 8 | OFFBOARDING → OFFBOARDED | Alice | Complete offboarding | Badge shows "Offboarded" |
| 9 | OFFBOARDED blocked from project creation | Alice | Attempt to create project | Blocked |
| 10 | Invalid: PROSPECT → ACTIVE (skip) | API | Direct API call | HTTP 400 or 409 |

---

### PROJ-01: Project CRUD (`projects/project-crud.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Create project with customer | Alice | Open dialog, fill Name, select Customer, submit | Project in list with customer name |
| 2 | Create project without customer | Alice | Open dialog, fill Name only, submit | Project created |
| 3 | Edit project name | Alice | Open project detail, edit name | Name updated |
| 4 | Project detail tabs load | Alice | Click each tab (Tasks, Time, Documents, Budget, Activity) | Each tab renders content |
| 5 | Archive project | Alice | Change project status to Completed → Archived | Status updated, actions restricted |
| 6 | Archived project blocks task creation | Alice | On archived project, attempt New Task | Blocked |
| 7 | Archived project blocks time logging | Carol | On archived project, attempt Log Time | Blocked |

---

### PROJ-02: Project Tasks (`projects/project-tasks.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Create task on project | Alice | Open Tasks tab, click New Task, fill title, submit | Task appears in list |
| 2 | Edit task title | Alice | Click task, edit title, save | Title updated |
| 3 | Change task status: OPEN → IN_PROGRESS | Alice | Click status dropdown, select In Progress | Status updated |
| 4 | Change task status: IN_PROGRESS → DONE | Alice | Mark task done | Status updated |
| 5 | Reopen completed task | Alice | Change DONE task back to OPEN | Status updated |
| 6 | Cancel task | Alice | Cancel task | Status shows Cancelled |
| 7 | Assign member to task | Alice | Assign Carol to task | Carol's name shown |

---

### PROJ-03: Time Entries (`projects/project-time.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Log time on task | Carol | Open task, click Log Time, fill hours+description, submit | Time entry appears in Time tab |
| 2 | Edit time entry | Carol | Click time entry, change hours, save | Hours updated |
| 3 | Delete time entry | Carol | Delete time entry | Entry removed |
| 4 | Time entry inherits correct rate | Carol | Log time, check rate snapshot | Rate matches Carol's billing rate |
| 5 | Billable flag defaults to checked | Carol | Open Log Time dialog | Billable checkbox is checked |
| 6 | Mark time entry non-billable | Carol | Uncheck billable, save | Entry saved as non-billable |
| 7 | My Work shows cross-project entries | Carol | Navigate to My Work | Entries from multiple projects shown |

---

### INV-01: Invoice CRUD (`invoices/invoice-crud.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Create draft invoice for customer | Alice | Navigate to Invoices, create new, select customer, add line items | Draft invoice created |
| 2 | Add line item to draft | Alice | On draft invoice, add line with qty/rate/description | Line appears, subtotal recalculated |
| 3 | Edit line item on draft | Alice | Modify quantity or rate | Totals update |
| 4 | Remove line item from draft | Alice | Delete a line item | Totals update |
| 5 | Draft invoice shows correct totals | Alice | Add 2+ lines | Subtotal + tax + total correct |

---

### INV-02: Invoice Lifecycle (`invoices/invoice-lifecycle.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | DRAFT → APPROVED | Alice | Click Approve on draft | Status shows Approved, invoice number assigned (INV-XXXX) |
| 2 | APPROVED → SENT | Alice | Click Send | Status shows Sent |
| 3 | SENT → PAID (record payment) | Alice | Record payment with reference | Status shows Paid, payment date set |
| 4 | VOID a sent invoice | Alice | Void invoice | Status shows Void |
| 5 | VOID releases time entries | Alice | Void invoice, check time entries | Time entries show as unbilled |
| 6 | Cannot edit approved invoice | Alice | Attempt to edit line items on approved invoice | Edit controls disabled/hidden |
| 7 | Cannot skip DRAFT → SENT | API | Direct API call to send without approve | HTTP 409 |
| 8 | Cannot transition PAID → VOID | API | Direct API call | HTTP 409 |

---

### INV-03: Invoice Arithmetic (`invoices/invoice-arithmetic.spec.ts`)

Uses API calls to verify exact amounts.

| # | Test | Steps | Expected |
|---|------|-------|----------|
| 1 | Single line: 3h × R450 | Create invoice with 1 line | Subtotal R1,350.00, VAT R202.50, Total R1,552.50 |
| 2 | Multiple lines | 2 lines: (2 × R500) + (1 × R1,500) | Subtotal R2,500.00, VAT R375.00, Total R2,875.00 |
| 3 | Rounding: non-terminating decimal | 1.5h × R333.33 | Subtotal R499.99 or R500.00 (document behaviour), VAT correctly computed |
| 4 | Zero quantity line | 0 × R500 | Line total R0.00 |
| 5 | Fractional quantity | 0.25h × R1,200 | Line total R300.00 |
| 6 | Rate snapshot immutability | Change rate AFTER logging time, create invoice | Invoice uses snapshot rate, not current rate |

---

### PORTAL-01: Data Isolation (`portal/portal-data-isolation.spec.ts`)

**Priority**: P0 — security-critical.

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Kgosi portal sees only Kgosi projects | Kgosi contact | Login, view projects | Only Kgosi projects listed |
| 2 | Kgosi cannot see Naledi data | Kgosi contact | Search or navigate to other customers | Zero results, no data leakage |
| 3 | Direct URL to another customer's project | Kgosi contact | Navigate to Vukani project URL | 404 or access denied |
| 4 | API: Kgosi JWT on Vukani project | Kgosi contact | GET /portal/projects/{vukani-id} with Kgosi JWT | 403 or 404 |
| 5 | Vukani portal sees only Vukani projects | Vukani contact | Login, view projects | Only Vukani projects listed |

---

### PORTAL-02: Portal Auth (`portal/portal-auth.spec.ts`)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| 1 | Portal landing page loads | Navigate to /portal | Page renders login/landing |
| 2 | Valid token grants access | Set portal auth cookie | Portal pages accessible |
| 3 | Invalid token rejected | Set invalid cookie | Portal content inaccessible |
| 4 | No firm-side nav leaks | Authenticated portal user | No Settings, Team, Reports links visible |

---

## P1 — Core Feature Tests (~90 tests)

High-value features that should work correctly. Run on every PR.

---

### PROP-01: Proposal CRUD (`proposals/proposal-crud.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Open New Proposal dialog | Alice | Click New Proposal | Dialog shows Title, Customer, Fee Model, Amount, Currency |
| 2 | Create fixed-fee proposal | Alice | Fill fields, submit | Proposal in list with DRAFT status |
| 3 | Create hourly proposal | Alice | Select hourly fee model, fill fields | Created |
| 4 | View proposal detail | Alice | Click proposal in list | Detail page shows all fields |
| 5 | Edit draft proposal | Alice | Change title, save | Title updated |

---

### PROP-02: Proposal Lifecycle (`proposals/proposal-lifecycle.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | DRAFT → SENT | Alice | Click Send | Status shows Sent |
| 2 | SENT → ACCEPTED (via portal) | Portal contact | Accept proposal in portal | Status shows Accepted |
| 3 | Accepted proposal creates project | Alice | Check projects after acceptance | New project created with proposal details |
| 4 | SENT → DECLINED (via portal) | Portal contact | Decline with reason | Status shows Declined, reason visible to firm |
| 5 | Cannot accept EXPIRED proposal | API | Accept after expiry | Rejected |

---

### PROP-03: Portal Proposal Acceptance (`portal/portal-proposal-acceptance.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Portal contact sees proposal detail | Kgosi contact | Navigate to proposal | Title, amount (ZAR), description, milestones visible |
| 2 | Accept proposal | Kgosi contact | Click Accept, confirm | Confirmation shown |
| 3 | Decline proposal with reason | Naledi contact | Click Decline, enter reason, confirm | Declined status |
| 4 | No unresolved variables in portal view | Portal contact | View any proposal | No `{{...}}` literals |

---

### RET-01: Retainer CRUD (`retainers/retainer-crud.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View retainer list | Alice | Navigate to /retainers | List shows seeded retainers |
| 2 | View retainer detail | Alice | Click retainer | Detail shows customer, type, amount, hours |
| 3 | Create retainer | Alice | Fill form (Hour Bank, Monthly, 10hrs, R5,500) | Retainer created |

---

### DOC-01: Template Management (`documents/template-management.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Template list shows seeded templates | Alice | Navigate to /settings/templates | Templates listed |
| 2 | Create new template | Alice | Click New, fill name + content, save | Template created |
| 3 | Clone template | Alice | Click Clone on existing | Clone created with "Copy of" prefix |
| 4 | Edit template content | Alice | Open template, modify content, save | Content updated |

---

### DOC-02: Document Generation (`documents/document-generation.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Generate document from template | Alice | On customer/project, click Generate Document, select template | Document generated |
| 2 | Preview shows resolved variables | Alice | View HTML preview | Customer name, org name, dates visible — no `{{...}}` |
| 3 | Download PDF | Alice | Click Download | PDF downloads, non-zero size |
| 4 | Generated documents list | Alice | Navigate to /documents | Generated docs listed |

---

### DOC-03: Document Acceptance (`documents/document-acceptance.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Send document for acceptance | Alice | Send engagement letter to portal contact | Email sent (Mailpit) |
| 2 | Portal contact views document | Portal contact | Navigate to document | Preview renders with correct content |
| 3 | Portal contact accepts | Portal contact | Click Accept | Acceptance recorded |
| 4 | Firm sees acceptance metadata | Alice | View document detail | Acceptor name, date/time, status = ACCEPTED |

---

### SET-01: General Settings (`settings/general-settings.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View org name and currency | Alice | Navigate to /settings/general | Org name and ZAR visible |
| 2 | Update brand colour | Alice | Change colour, save | Colour persisted |
| 3 | Update document footer | Alice | Edit footer text, save | Footer updated |

---

### SET-02: Rate Cards (`settings/rate-cards.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View billing rates | Alice | Navigate to /settings/rates | Rates listed for team members |
| 2 | Create billing rate | Alice | Add rate for member | Rate saved |
| 3 | Edit billing rate | Alice | Modify amount | Amount updated |
| 4 | View cost rates | Alice | Switch to cost rates tab/section | Cost rates visible |
| 5 | Rate hierarchy: project override wins | Alice | Set project-level rate, log time | Time entry uses project rate |

---

### SET-03: Tax Settings (`settings/tax-settings.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View tax rates | Alice | Navigate to /settings/tax | Tax rates shown (e.g., VAT 15%) |
| 2 | Create tax rate | Alice | Add new rate (name, percentage) | Rate saved |
| 3 | Tax applies to invoices | Alice | Create invoice with taxable lines | Tax calculated correctly |

---

### NOTIF-01: Notification Bell (`notifications/notification-bell.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Bell shows unread count | Bob | Login, check header | Badge shows number |
| 2 | Click bell opens dropdown | Bob | Click notification bell | Dropdown with notifications |
| 3 | Mark as read decrements count | Bob | Click a notification | Count decreases |
| 4 | Navigate to notifications page | Bob | Click "View All" or navigate | Full notifications page loads |
| 5 | Mark all as read | Bob | Click "Mark all as read" | Count goes to 0 |

---

### NOTIF-02: Notification Preferences (`notifications/notification-preferences.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View preference categories | Alice | Navigate to notification settings | Categories and channels shown |
| 2 | Disable email for category | Alice | Toggle off email for invoices | Toggle persists on reload |
| 3 | Re-enable email | Alice | Toggle back on | Persists |

---

### IREQ-01: Information Requests (`information-requests/information-request-crud.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View information requests list | Alice | Navigate to /information-requests | List loads |
| 2 | Create information request | Alice | Create request with items (required + optional) | Request created |
| 3 | Send information request | Alice | Send to portal contact | Status updated, email in Mailpit |
| 4 | Portal contact views request | Portal contact | Navigate to request | Items listed |
| 5 | Track completion | Alice | View request detail | Progress shown per item |

---

### VERT-01: Profile Switching (`verticals/profile-switching.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View current profile | Alice | Navigate to /settings/general | Profile dropdown shows current |
| 2 | Switch to legal-za profile | Alice | Select Legal (SA), confirm | Legal modules enabled, terminology updates |
| 3 | Switch back to accounting-za | Alice | Select Accounting (SA), confirm | Legal modules disabled |
| 4 | Profile switch is additive | Alice | Switch profile, verify existing data intact | No data lost |

---

### VERT-02: Module Gates (`verticals/module-gates.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Trust Accounting hidden when module disabled | Alice | With accounting-za profile, check sidebar | No Trust Accounting link |
| 2 | Trust Accounting visible when module enabled | Alice | With legal-za profile, check sidebar | Trust Accounting link visible |
| 3 | Trust Accounting page loads for legal profile | Alice | Navigate to /trust-accounting | Page renders (stub) |
| 4 | Court Calendar gated | Alice | Toggle between profiles | Visible only with legal-za |
| 5 | Conflict Check gated | Alice | Toggle between profiles | Visible only with legal-za |
| 6 | Direct URL to gated page without module | Alice | Navigate to /trust-accounting with accounting-za | 404 or redirect |

---

### DP-01: Customer Data Protection (`customers/customer-data-protection.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Download customer data export | Alice | On customer detail, click Download Data, confirm | Export initiated, download link appears |
| 2 | Anonymize customer (preview) | Alice | Click Delete Personal Data | Preview shows entity counts |
| 3 | Anonymize customer (execute) | Alice | Type customer name to confirm, submit | Status changes to ANONYMIZED, PI fields cleared |
| 4 | ANONYMIZED customer is read-only | Alice | View anonymized customer | Edit disabled, badge shown |
| 5 | Cannot transition out of ANONYMIZED | API | Attempt status change | HTTP 400/409 |

---

### DP-02: Data Protection Settings (`settings/data-protection-settings.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View data protection tab | Alice | Navigate to /settings/data-protection | Tab renders with jurisdiction selector |
| 2 | Set jurisdiction to South Africa | Alice | Select ZA, save | Default retention months applied |
| 3 | Set information officer | Alice | Fill name + email, save | Persisted |
| 4 | View retention policies | Alice | Navigate to retention section | Policies listed per entity type |
| 5 | Edit retention period | Alice | Change months, save | Updated (validated against financial minimum) |
| 6 | View processing register | Alice | Navigate to register section | Processing activities listed |
| 7 | Generate PAIA manual | Alice | Click Generate | Document generated, preview available |
| 8 | View DSAR list | Alice | Navigate to DSAR section | Requests listed with deadlines |
| 9 | Create DSAR | Alice | Log new request | Request created with jurisdiction deadline |

---

### NAV-01: Sidebar Navigation (`navigation/sidebar-navigation.spec.ts`)

Verify every sidebar item resolves to a working page.

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Dashboard link works | Alice | Click Dashboard | Page loads |
| 2 | My Work link works | Alice | Click My Work | Page loads |
| 3 | Calendar link works | Alice | Click Calendar | Page loads |
| 4 | Projects link works | Alice | Click Projects | Page loads |
| 5 | Documents link works | Alice | Click Documents | Page loads |
| 6 | Customers link works | Alice | Click Customers | Page loads |
| 7 | Retainers link works | Alice | Click Retainers | Page loads |
| 8 | Compliance link works | Alice | Click Compliance | Page loads |
| 9 | Invoices link works | Alice | Click Invoices | Page loads |
| 10 | Proposals link works | Alice | Click Proposals | Page loads |
| 11 | Profitability link works | Alice | Click Profitability | Page loads |
| 12 | Reports link works | Alice | Click Reports | Page loads |
| 13 | Team link works | Alice | Click Team | Page loads |
| 14 | Resources link works | Alice | Click Resources | Page loads |
| 15 | Notifications link works | Alice | Click Notifications | Page loads |
| 16 | Settings link works | Alice | Click Settings | Settings hub loads |

---

### AUTO-01: Automation CRUD (`automations/automation-crud.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View seeded automation rules | Alice | Navigate to /settings/automations | Rules listed |
| 2 | Create custom automation rule | Alice | Click New, select trigger + action, configure, save | Rule created |
| 3 | Disable automation rule | Alice | Toggle off an enabled rule | Rule shows disabled |
| 4 | Enable automation rule | Alice | Toggle on a disabled rule | Rule shows enabled |
| 5 | View execution history | Alice | Navigate to executions page | History entries listed |

---

### PORTAL-03: Portal Navigation (`portal/portal-navigation.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Portal home loads | Kgosi contact | Navigate to portal after auth | Home/dashboard renders |
| 2 | Portal projects list | Kgosi contact | Navigate to /portal/projects | Kgosi projects listed |
| 3 | Portal project detail | Kgosi contact | Click a project | Detail renders (name, status, docs) |
| 4 | Portal documents page | Kgosi contact | Navigate to /portal/documents | Documents listed |
| 5 | Portal requests page | Kgosi contact | Navigate to /portal/requests | Information requests listed |
| 6 | No firm-side leakage | Kgosi contact | Check all pages | No Settings, Team, Reports, Invoices links |

---

### EXPENSE-01: Project Expenses (`projects/project-expenses.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View expenses tab on project | Alice | Navigate to project, click Expenses | Tab loads |
| 2 | Create billable expense | Bob | Add expense (description, amount, billable=true) | Expense appears |
| 3 | Create non-billable expense | Bob | Add expense with billable=false | Saved correctly |
| 4 | Edit expense | Bob | Modify amount | Updated |

---

### BUDGET-01: Project Budget (`projects/project-budget.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View budget tab | Alice | Navigate to project Budget tab | Budget information shown |
| 2 | Set project budget | Alice | Configure budget hours/amount | Budget saved |
| 3 | Budget shows usage | Alice | After logging time, check budget | Used/remaining displayed |

---

## P2 — Extended Feature Tests (~60 tests)

Lower priority but important for full coverage. Run nightly or before releases.

---

### FIN-01: Profitability Dashboard (`finance/profitability.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Page loads with data | Alice | Navigate to /profitability | Charts/metrics render with non-zero data |
| 2 | Project profitability view | Alice | Switch to project view | Per-project P&L visible |
| 3 | Customer profitability view | Alice | Switch to customer view | Per-customer data visible |
| 4 | Utilization metrics | Alice | Check utilization section | Percentages calculated |

---

### FIN-02: Reports (`finance/reports.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Reports hub loads | Alice | Navigate to /reports | Report cards listed |
| 2 | Run a report with parameters | Alice | Select report, set date range, run | Results displayed |
| 3 | Export report to CSV | Alice | Click Export | CSV downloads |

---

### CF-01: Custom Fields (`settings/custom-fields.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View custom field definitions | Alice | Navigate to /settings/custom-fields | Fields listed by entity type |
| 2 | Create text custom field | Alice | Add field (name, type=text, entity=customer) | Field created |
| 3 | Custom field appears on entity form | Alice | Open customer create dialog | New field visible in Step 2 |
| 4 | Custom field value persists | Alice | Set value, save, reopen | Value retained |

---

### TAG-01: Tags (`settings/tags.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View tags | Alice | Navigate to /settings/tags | Tags listed |
| 2 | Create tag | Alice | Add new tag with colour | Tag created |
| 3 | Apply tag to entity | Alice | Tag a project or customer | Tag appears on entity |

---

### ROLE-01: Roles & Capabilities (`settings/roles-capabilities.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View roles list | Alice | Navigate to /settings/roles | Roles listed (Owner, Admin, Member) |
| 2 | View role capabilities | Alice | Click a role | Capabilities checkboxes shown |
| 3 | Edit role capabilities | Alice | Toggle a capability, save | Change persisted |

---

### SCHED-01: Recurring Schedules (`schedules/schedule-crud.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | View schedules list | Alice | Navigate to /schedules | Schedules listed |
| 2 | View schedule detail | Alice | Click a schedule | Detail shows recurrence pattern |

---

### RES-01: Resource Planning (`resources/resource-planning.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Resources page loads | Alice | Navigate to /resources | Page renders |
| 2 | Utilization view | Alice | Navigate to /resources/utilization | Utilization data shown |

---

### AUTO-02: Automation Triggers (`automations/automation-triggers.spec.ts`)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| 1 | Customer status change fires rule | Create customer, transition to ONBOARDING | Execution history shows COMPLETED |
| 2 | Invoice payment fires rule | Record payment on SENT invoice | Notification created |
| 3 | Disabled rule does NOT fire | Disable rule, trigger event | No execution recorded |

---

### COMPLY-01: Compliance (`compliance/compliance-overview.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Compliance dashboard loads | Alice | Navigate to /compliance | Page renders |
| 2 | Compliance requests list | Alice | Navigate to /compliance/requests | Requests listed |

---

### BR-01: Billing Runs (`invoices/billing-runs.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Billing runs page loads | Alice | Navigate to /invoices/billing-runs | Page renders |
| 2 | New billing run wizard | Alice | Click New, step through wizard | Billing run created |

---

### CMD-01: Command Palette (`navigation/command-palette.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Cmd+K opens palette | Alice | Press Cmd+K | Palette dialog opens |
| 2 | Search navigates to page | Alice | Type "Invoices", press Enter | Navigates to /invoices |
| 3 | Escape closes palette | Alice | Press Escape | Palette closes |

---

### PORTAL-04: Portal Branding (`portal/portal-branding.spec.ts`)

| # | Test | Actor | Steps | Expected |
|---|------|-------|-------|----------|
| 1 | Firm name visible in portal | Kgosi contact | Check portal header/branding | Org name shown (not "DocTeams") |
| 2 | Brand colour applied | Kgosi contact | Check accent colours | Brand colour visible in UI elements |

---

## Implementation Guidance

### Data Strategy

All tests should run against the **Keycloak dev stack** (frontend 3000 / backend 8080 — see `qa/keycloak-e2e-guide.md`). Two data approaches:

1. **Seeded data** (`compose/seed/lifecycle-test.sh`): Use for read-only assertions and as a base for interactive tests. Known entities: 4 customers (Kgosi, Naledi, Vukani, Moroka), multiple projects, time entries, invoices, proposals, retainers.

2. **Test-created data**: For CRUD tests, create fresh entities with a `RUN_ID` suffix (as in `lifecycle-interactive.spec.ts`) to avoid collisions. Prefer API-based setup for preconditions (faster, more reliable) and UI-based verification for actual test assertions.

### Page Object Pattern

Recommended for maintainability as the suite grows beyond ~50 tests:

```typescript
// e2e/page-objects/customer-list.page.ts
export class CustomerListPage {
  constructor(private page: Page) {}
  async goto(orgSlug: string) { await this.page.goto(`/org/${orgSlug}/customers`); }
  async openNewCustomerDialog() { /* ... */ }
  async searchFor(term: string) { /* ... */ }
  async getCustomerNames(): Promise<string[]> { /* ... */ }
}
```

Priority page objects: `CustomerListPage`, `CustomerDetailPage`, `ProjectListPage`, `ProjectDetailPage`, `InvoiceListPage`, `InvoiceDetailPage`, `SettingsPage`, `PortalPage`.

### Parallel Execution

Group tests by independence:
- **Serial groups**: Tests that modify shared state (lifecycle transitions, settings changes)
- **Parallel groups**: Read-only assertions, isolated CRUD with unique RUN_IDs
- Use Playwright's `test.describe.serial()` for ordered flows

### CI Integration

```
P0 tests: Run on every PR (target: <5 min)
P1 tests: Run on every PR (target: <10 min)
P2 tests: Nightly or pre-release (target: <15 min)
Full suite: Before deployment
```

### Mailpit Verification

For email-dependent tests (automation triggers, information requests, document acceptance), use Mailpit's API:

```typescript
// e2e/fixtures/mailpit.ts
const MAILPIT_API = 'http://localhost:8025/api/v1';

export async function getLatestEmail(to: string) {
  const res = await fetch(`${MAILPIT_API}/search?query=to:${to}&limit=1`);
  return res.json();
}

export async function clearMailbox() {
  await fetch(`${MAILPIT_API}/messages`, { method: 'DELETE' });
}
```

### Test Counts Summary

| Priority | Suites | Est. Tests | Run |
|----------|--------|-----------|-----|
| P0 | 12 suites | ~50 | Every PR |
| P1 | 16 suites | ~90 | Every PR |
| P2 | 12 suites | ~60 | Nightly |
| **Total** | **40 suites** | **~200** | |

---

## Existing Tests to Keep / Refactor

| Existing File | Action |
|---------------|--------|
| `smoke.spec.ts` | **Keep as-is** — fast smoke check |
| `lifecycle.spec.ts` | **Keep as baseline** — refactor into domain-specific suites over time. These 21 read-only tests serve as a quick sanity check and can coexist with the deeper suites. |
| `lifecycle-interactive.spec.ts` | **Migrate** — move individual test groups into `customers/`, `projects/`, `invoices/`, `proposals/` suites. Deprecate once migration complete. |
| `lifecycle-portal.spec.ts` | **Migrate** — move into `portal/` suites. Deprecate once migration complete. |

---

## Appendix: Feature × Test Coverage Matrix

| Feature Area | Existing E2E | Manual QA Plan | New Regression Suite |
|---|---|---|---|
| Auth / RBAC | 3 smoke tests | 48-lifecycle Day 90 | AUTH-01 (10 tests) |
| Customer CRUD | 1 create test | 48-lifecycle Day 1 | CUST-01 (5 tests) |
| Customer Lifecycle | 2 transition tests | data-integrity T1 | CUST-02 (10 tests) |
| Projects | 1 create test | 48-lifecycle Day 1 | PROJ-01 (7 tests) |
| Tasks | 1 create test | 48-lifecycle Day 1 | PROJ-02 (7 tests) |
| Time Entries | 1 log test | 48-lifecycle Day 7 | PROJ-03 (7 tests) |
| Invoices | 1 approve test | data-integrity T3 | INV-01/02/03 (19 tests) |
| Proposals | 2 view tests | portal-experience T4 | PROP-01/02/03 (13 tests) |
| Retainers | 1 view test | 48-lifecycle Day 1 | RET-01 (3 tests) |
| Documents/Templates | 1 page load | doc-content T1-T8 | DOC-01/02/03 (11 tests) |
| Portal | 7 basic tests | portal-experience T1-T7 | PORTAL-01/02/03/04 (19 tests) |
| Notifications | 1 page load | automation-notif T5 | NOTIF-01/02 (8 tests) |
| Automations | 1 page load | automation-notif T1-T7 | AUTO-01/02 (8 tests) |
| Settings | 2 page loads | 48-lifecycle Day 0 | SET-01/02/03 (10 tests) |
| Data Protection | 0 | none | DP-01/02 (14 tests) |
| Vertical Architecture | 0 | none | VERT-01/02 (10 tests) |
| Navigation | 0 | none | NAV-01 (16 tests) |
| Profitability | 1 page load | 48-lifecycle Day 30 | FIN-01 (4 tests) |
| Reports | 1 page load | 48-lifecycle Day 60 | FIN-02 (3 tests) |
| Custom Fields | 1 page load | none | CF-01 (4 tests) |
| Tags | 0 | none | TAG-01 (3 tests) |
| Roles | 0 | none | ROLE-01 (3 tests) |
| Expenses | 0 | 48-lifecycle Day 45 | EXPENSE-01 (4 tests) |
| Budget | 1 tab load | 48-lifecycle Day 30 | BUDGET-01 (3 tests) |
| Compliance | 0 | none | COMPLY-01 (2 tests) |
| Billing Runs | 0 | none | BR-01 (2 tests) |
| Command Palette | 0 | none | CMD-01 (3 tests) |
| Resources | 1 page load | none | RES-01 (2 tests) |
| Info Requests | 1 page load | doc-content T4 | IREQ-01 (5 tests) |
| Schedules | 0 | none | SCHED-01 (2 tests) |
