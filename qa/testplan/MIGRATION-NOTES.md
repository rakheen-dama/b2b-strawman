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
