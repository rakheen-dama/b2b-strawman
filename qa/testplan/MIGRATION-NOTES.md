# QA Selector Migration Notes: Matter Detail Redesign

**Applies to**: All QA lifecycle scripts in `qa/testplan/demos/`
**Phase**: 73 (Epics 532–536, PRs #1339–#1345), then sidebar→header+tabs iteration (commit 50aec5407)
**Updated**: 2026-05-22

## What Changed

Phase 73 restructured the matter detail page (`/org/[slug]/projects/[id]`) from a
single-column vertical layout to a sidebar + grouped-tab layout.

A follow-up iteration removed the sidebar entirely:
- Matter identity (name, status, work type, client, reference) moved to a **header card** (`data-testid="matter-header-card"`)
- Description, metadata, and tags moved to a **Details tab** (`tab-item-details`)
- Custom fields moved to a **Fields tab** (`tab-item-fields`)
- Lifecycle actions (Close/Reopen/Complete) moved to the **header card** (`data-testid="header-lifecycle-actions"`)

### Before Phase 73
- 21 flat tabs in a `TabsPrimitive.List`
- Tab selector: `page.getByRole('tab', { name: 'Time' })`
- Action buttons (Complete, Close, Generate Document) in the page header
- Matter metadata (name, status, custom fields, tags) in the page header/body

### After Phase 73 + sidebar removal
- 7 grouped tab groups in `GroupedTabBar` (`data-testid="grouped-tab-bar"`)
- Tab navigation requires two steps for multi-tab groups
- Primary lifecycle action (Complete/Close/Reopen) in header card
- Secondary actions (Edit, Delete, Archive, Save as Template) in overflow menu; Generate Document is a standalone dropdown button before the overflow trigger
- Matter identity in header card; description/tags on Details tab; custom fields on Fields tab

---

## Tab Group Reference

| Group | data-testid | Sub-tabs (data-testid="tab-item-{id}") | Type |
|-------|-------------|----------------------------------------|------|
| Details | `tab-group-details` | details, fields | Multi (dropdown) |
| Overview | `tab-group-overview` | overview | Single (no dropdown) |
| Work | `tab-group-work` | tasks, documents, generated, staffing | Multi (dropdown) |
| Finance | `tab-group-finance` | time, expenses, disbursements, budget, rates, financials, statements, trust | Multi (dropdown) |
| Client | `tab-group-client` | customers, requests, customer-comments, adverse-parties | Multi (dropdown) |
| Schedule | `tab-group-schedule` | court-dates | Single (no dropdown) |
| Activity | `tab-group-activity` | activity, audit | Multi (dropdown) |

> `audit` sub-tab only appears when TEAM_OVERSIGHT capability is enabled.

---

## Playwright Selector Patterns

### Single-tab group (Overview, Schedule)
One click — no dropdown:
```js
page.getByTestId('tab-group-overview').click()    // Overview
page.getByTestId('tab-group-schedule').click()    // Schedule → Court Dates
```

### Multi-tab group (Details, Work, Finance, Client, Activity)
Two clicks — click group to open dropdown, then click sub-tab:
```js
// Details → Fields (custom fields)
page.getByTestId('tab-group-details').click()
page.getByTestId('tab-item-fields').click()

// Details → Details (description, priority, tags)
page.getByTestId('tab-group-details').click()
page.getByTestId('tab-item-details').click()

// Finance → Time
page.getByTestId('tab-group-finance').click()
page.getByTestId('tab-item-time').click()

// Work → Tasks
page.getByTestId('tab-group-work').click()
page.getByTestId('tab-item-tasks').click()

// Client → Requests (Info Requests)
page.getByTestId('tab-group-client').click()
page.getByTestId('tab-item-requests').click()

// Activity → Activity (feed)
page.getByTestId('tab-group-activity').click()
page.getByTestId('tab-item-activity').click()
```

---

## Action Button Migration

| Old Location | Old Selector | New Location | New Selector |
|---|---|---|---|
| Page header | `page.getByText('Complete Matter')` | Header card | `page.getByTestId('header-lifecycle-actions').getByRole('button', { name: /complete/i })` |
| Page header | `page.getByText('Close Matter')` | Header card | `page.getByTestId('header-lifecycle-actions').getByRole('button', { name: /close matter/i })` |
| Page header | `page.getByText('Reopen')` | Header card | `page.getByTestId('header-lifecycle-actions').getByRole('button', { name: /reopen/i })` |
| Page header | `page.getByText('Generate Document')` | Standalone button (before overflow trigger) | `page.getByText('Generate Document')` (still works — button still visible) |
| Page header | `page.getByText('Edit Matter')` | Overflow menu | `page.getByTestId('overflow-actions-trigger').click()` then `page.getByText(/edit/i)` |
| Page header | `page.getByText('Delete')` | Overflow menu | `page.getByTestId('overflow-actions-trigger').click()` then `page.getByText(/delete/i)` |
| Page header | `page.getByText('Save as Template')` | Overflow menu | `page.getByTestId('overflow-actions-trigger').click()` then `page.getByText('Save as Template')` |

---

## Old Tab Name → New Location Quick Reference

| Old Tab Name | New Group | New Tab ID | Navigation |
|---|---|---|---|
| _(was sidebar)_ Description, tags, metadata | Details | details | `tab-group-details` → `tab-item-details` |
| _(was sidebar)_ Custom fields | Details | fields | `tab-group-details` → `tab-item-fields` |
| Overview | Overview (standalone) | overview | `tab-group-overview` click |
| Tasks | Work | tasks | `tab-group-work` → `tab-item-tasks` |
| Documents | Work | documents | `tab-group-work` → `tab-item-documents` |
| Generated Docs | Work | generated | `tab-group-work` → `tab-item-generated` |
| Members / Staffing | Work | staffing | `tab-group-work` → `tab-item-staffing` |
| Time | Finance | time | `tab-group-finance` → `tab-item-time` |
| Expenses | Finance | expenses | `tab-group-finance` → `tab-item-expenses` |
| Disbursements | Finance | disbursements | `tab-group-finance` → `tab-item-disbursements` |
| Budget / Fee Estimate | Finance | budget | `tab-group-finance` → `tab-item-budget` |
| Rates | Finance | rates | `tab-group-finance` → `tab-item-rates` |
| Financials | Finance | financials | `tab-group-finance` → `tab-item-financials` |
| Statements | Finance | statements | `tab-group-finance` → `tab-item-statements` |
| Trust | Finance | trust | `tab-group-finance` → `tab-item-trust` |
| Clients / Customers | Client | customers | `tab-group-client` → `tab-item-customers` |
| Info Requests / Requests | Client | requests | `tab-group-client` → `tab-item-requests` |
| Client Comments / Comments | Client | customer-comments | `tab-group-client` → `tab-item-customer-comments` |
| Adverse Parties | Client | adverse-parties | `tab-group-client` → `tab-item-adverse-parties` |
| Court Dates / Court Calendar | Schedule (standalone) | court-dates | `tab-group-schedule` click |
| Activity | Activity | activity | `tab-group-activity` → `tab-item-activity` |
| Audit | Activity | audit | `tab-group-activity` → `tab-item-audit` |

---

# QA Selector Migration Notes: Customer Detail Redesign

**Applies to**: All QA lifecycle scripts in `qa/testplan/` that reference customer/client detail pages
**Phase**: 77 (Epics 556–561, PRs #1391–#1396)
**Updated**: 2026-05-30

## What Changed

Phase 77 restructured the customer detail page (`/org/[slug]/customers/[id]`) from a vertical-stack layout to a **header card + grouped tabs** layout — the same pattern applied to the matter detail page in Phase 73.

### Before Phase 77
- 7 action buttons in the header row (Summarise, Change Status, Generate Document, Export Data, Anonymize, Edit, Archive)
- Metadata (address, contact, business details, custom fields, tags, setup progress, AI panels) inline above tabs, pushing tabs below the fold
- 11 flat tabs in a single row (Projects, Documents, Onboarding, Invoices, Retainer, Requests, Rates, Generated Docs, Financials, Trust, Audit)
- Default tab: Projects

### After Phase 77
- `ClientHeaderCard` (`data-testid="client-header-card"`) with name, badges, contact, context line, 1 smart primary action (`data-testid="smart-primary-action"`), and `ClientOverflowMenu` (`data-testid="client-overflow-trigger"`)
- 6 grouped tab groups via shared `GroupedTabBar` (`data-testid="grouped-tab-bar"`)
- Metadata moved to **Details** tab group (Details, Fields, Tags sub-tabs)
- Setup progress, AI panels, financial summary moved to **Overview** tab (default landing)
- Default tab: Overview

---

## Customer Tab Group Reference

| Group | data-testid | Sub-tabs (data-testid="tab-item-{id}") | Type |
|-------|-------------|----------------------------------------|------|
| Details | `tab-group-details` | details, fields, tags | Multi (dropdown) |
| Overview | `tab-group-overview` | overview | Single (no dropdown) |
| Work | `tab-group-work` | projects, documents, generated | Multi (dropdown) |
| Finance | `tab-group-finance` | invoices, rates, retainer, financials, trust | Multi (dropdown) |
| Compliance | `tab-group-compliance` | onboarding, requests | Multi (dropdown) |
| Activity | `tab-group-activity` | — (renders directly) | Single (no dropdown) |

> **Note:** Activity group renders the audit timeline directly when clicked — no sub-tab click required.

> `trust` sub-tab only appears when `trust_accounting` module is enabled.
> `audit` sub-tab only appears when TEAM_OVERSIGHT capability is enabled.
> `Finance` group hides entirely when all sub-tabs are gated off (non-admin user).

---

## Playwright Selector Patterns

### Single-tab group (Overview, Activity)
One click — no dropdown:
```js
page.getByTestId('tab-group-overview').click()     // Overview (default landing)
page.getByTestId('tab-group-activity').click()     // Activity → Audit
```

### Multi-tab group (Details, Work, Finance, Compliance)
Two clicks — click group to open dropdown, then click sub-tab:
```js
// Details → Details (address, contact, business details)
page.getByTestId('tab-group-details').click()
page.getByTestId('tab-item-details').click()

// Details → Fields (custom fields)
page.getByTestId('tab-group-details').click()
page.getByTestId('tab-item-fields').click()

// Details → Tags
page.getByTestId('tab-group-details').click()
page.getByTestId('tab-item-tags').click()

// Work → Projects
page.getByTestId('tab-group-work').click()
page.getByTestId('tab-item-projects').click()

// Work → Documents
page.getByTestId('tab-group-work').click()
page.getByTestId('tab-item-documents').click()

// Finance → Invoices
page.getByTestId('tab-group-finance').click()
page.getByTestId('tab-item-invoices').click()

// Compliance → Onboarding
page.getByTestId('tab-group-compliance').click()
page.getByTestId('tab-item-onboarding').click()

// Compliance → Requests
page.getByTestId('tab-group-compliance').click()
page.getByTestId('tab-item-requests').click()
```

---

## Action Button Migration

| Old Location | Old Selector | New Location | New Selector |
|---|---|---|---|
| Header row | `page.getByText('Edit')` | Overflow menu | `page.getByTestId('client-overflow-trigger').click()` then `page.getByText(/edit/i)` |
| Header row | Lifecycle transition dropdown | Header card | `page.getByTestId('client-header-card').getByRole('button', { name: /start onboarding/i })` |
| Header row | `page.getByText('Generate Document')` | Overflow menu | `page.getByTestId('client-overflow-trigger').click()` then `page.getByText(/generate document/i)` |
| Header row | `page.getByText('Export Data')` | Overflow menu | `page.getByTestId('client-overflow-trigger').click()` then `page.getByText(/export/i)` |
| Header row | `page.getByText('Anonymize')` | Overflow menu | `page.getByTestId('client-overflow-trigger').click()` then `page.getByText(/anonymize/i)` |
| Header row | `page.getByText('Archive')` | Overflow menu | `page.getByTestId('client-overflow-trigger').click()` then `page.getByText(/archive/i)` |
| Header row | `page.getByText('Summarise')` | Overflow menu | `page.getByTestId('client-overflow-trigger').click()` then `page.getByText(/summarise/i)` |

---

## Content Relocation

| Content | Old Location | New Location |
|---|---|---|
| Address, Contact, Business Details | Inline above tabs | Details tab (`tab-group-details` → `tab-item-details`) |
| Custom fields section | Inline above tabs | Fields tab (`tab-group-details` → `tab-item-fields`) |
| Tags section | Inline above tabs | Tags tab (`tab-group-details` → `tab-item-tags`) |
| Setup Progress card | Inline above tabs | Overview tab (`tab-group-overview` click) |
| Unbilled Time card | Inline above tabs | Overview tab |
| Template Readiness card | Inline above tabs | Overview tab |
| Lifecycle Action Prompt | Inline above tabs | Overview tab |
| Pending AI Suggestions | Below tabs | Overview tab |
| FICA Verification Panel | Below tabs | Overview tab |

---

## Field Promotion Checkpoint Changes

Pre-Phase 77, promoted fields were visible "on page load, inline at the top of the detail page."

Post-Phase 77, promoted fields are on the **Details tab** (`tab-group-details` → `tab-item-details`). QA scripts that verify field promotion on customer detail pages must add a navigation step:

1. Navigate to customer detail page
2. Click **Details** tab group (`tab-group-details`)
3. Click **Details** sub-tab (`tab-item-details`)
4. THEN verify promoted fields render as first-class inputs
5. Click **Fields** sub-tab (`tab-item-fields`) and verify promoted slugs do NOT appear in CustomFieldSection

---

## Old Tab Name → New Location Quick Reference

| Old Tab Name | New Group | New Tab ID | Navigation |
|---|---|---|---|
| _(new)_ Details (address, contact, business) | Details | details | `tab-group-details` → `tab-item-details` |
| _(new)_ Fields (custom fields) | Details | fields | `tab-group-details` → `tab-item-fields` |
| _(new)_ Tags | Details | tags | `tab-group-details` → `tab-item-tags` |
| _(new)_ Overview (setup, AI, financial summary) | Overview (standalone) | overview | `tab-group-overview` click |
| Projects | Work | projects | `tab-group-work` → `tab-item-projects` |
| Documents | Work | documents | `tab-group-work` → `tab-item-documents` |
| Generated Docs | Work | generated | `tab-group-work` → `tab-item-generated` |
| Invoices | Finance | invoices | `tab-group-finance` → `tab-item-invoices` |
| Rates | Finance | rates | `tab-group-finance` → `tab-item-rates` |
| Retainer | Finance | retainer | `tab-group-finance` → `tab-item-retainer` |
| Financials | Finance | financials | `tab-group-finance` → `tab-item-financials` |
| Trust | Finance | trust | `tab-group-finance` → `tab-item-trust` |
| Onboarding | Compliance | onboarding | `tab-group-compliance` → `tab-item-onboarding` |
| Requests | Compliance | requests | `tab-group-compliance` → `tab-item-requests` |
| Audit | Activity (standalone) | audit | `tab-group-activity` click |
