# Regression Test Suite — Cycle 1 Results

**Date**: 2026-03-19
**Agent**: QA Agent
**Branch**: `bugfix_cycle_regression_2026-03-19`
**Stack**: E2E mock-auth (port 3001/8081)

---

## AUTH-01: RBAC Capabilities

| # | Test | Actor | Result | Evidence |
|---|------|-------|--------|----------|
| 1 | Owner can access all settings pages | Alice | **PARTIAL** | General, Tax, Custom Fields, Roles, Automations, Templates, Tags, Compliance all load. **Settings > Rates & Currency crashes with 500** ("Cannot read properties of null (reading 'length')"). Screenshot: `regression-auth01-rates-500.png` |
| 2 | Admin can access most settings | Bob | **PASS** | Settings > General loads with full form (currency, tax, branding). Settings nav shows all sections. |
| 3 | Member blocked from rate cards | Carol | **PASS** | `/settings/rates` shows "You do not have permission to manage rates and currency settings. Only admins and owners can access this page." |
| 4 | Member blocked from profitability | Carol | **FAIL** | `/profitability` shows "Something went wrong" (500 error) instead of a proper permission restriction message. |
| 5 | Member blocked from reports | Carol | **FAIL** | `/reports` shows "Something went wrong" (500 error) instead of a proper permission restriction message. |
| 6 | Member can access My Work | Carol | **PASS** | My Work page loads with tasks table, time breakdown, and weekly summary. |
| 7 | Member can access Projects | Carol | **PASS** | Projects page loads with project list. |
| 8 | Member blocked from customer management | Carol | **FAIL** | `/customers` shows "Something went wrong" (500 error) instead of read-only view or permission message. |
| 9 | Admin can manage team | Bob | **PASS** | Team page loads with member list, invite form (email + role + Send Invite button). Shows "5 of 10 members". |
| 10 | Member blocked from roles settings | Carol | **FAIL** | `/settings/roles` returns 500 (blank page). Settings nav for Carol correctly hides the "Roles & Permissions" link, but direct URL access crashes instead of returning 403/redirect. |

**Summary**: 5 PASS, 4 FAIL, 1 PARTIAL (10 tests)

**Blockers Found**:
- **BUG-REG-001**: Settings > Rates & Currency page crashes with 500 for ALL users (including Owner). Error: `TypeError: Cannot read properties of null (reading 'length')`. This blocks SET-02 (Rate Cards) testing entirely.
- **BUG-REG-002**: Carol (Member) gets 500 "Something went wrong" on Profitability, Reports, Customers, and Roles pages instead of proper permission denied messages. The sidebar correctly hides these links, but direct URL access crashes instead of showing a graceful denial.

---

## NAV-01: Sidebar Navigation (Alice/Owner)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| 1 | Dashboard link works | **PASS** | Page loads with heading "Dashboard", project health cards, team workload chart, metrics (Active Projects: 9, Hours: 23.0h, Billable: 100%). |
| 2 | My Work link works | **PASS** | Page loads with "My Work" heading, tasks table (2 tasks), time breakdown by project, weekly summary. |
| 3 | Calendar link works | **PASS** | Page loads with "Calendar" heading, month view (March 2026), project/task/member filters. |
| 4 | Projects link works | **PASS** | Page loads with project list (multiple projects visible). |
| 5 | Documents link works | **PASS** | Page loads with "Organization Documents" heading, Upload Document button. Shows empty state. |
| 6 | Customers link works | **PASS** | Page loads with customer table (multiple rows visible). |
| 7 | Retainers link works | **PASS** | Page loads with "Retainers" heading, 2 retainers listed (Kgosi Monthly Bookkeeping, Vukani Monthly Retainer) with usage bars. |
| 8 | Compliance link works | **PASS** | Page loads with lifecycle distribution (1 Prospect, 1 Onboarding, 5 Active, 0 Dormant, 1 Offboarded), onboarding pipeline, data requests, dormancy check. |
| 9 | Invoices link works | **PASS** | Page loads with "Invoices" heading, 15 invoices listed, status filters (Draft/Approved/Sent/Paid/Void), financial summary (Outstanding R 345, Paid This Month R 12,563.75). |
| 10 | Proposals link works | **PASS** | Page loads with "Proposals" heading, 2 proposals listed, stats cards (Total: 2, Pending: 1, Accepted: 0, Rate: 0%). |
| 11 | Profitability link works | **PASS** | Page loads with team utilization table, project profitability table, customer profitability table -- all with real data. |
| 12 | Reports link works | **PASS** | Page loads with "Reports" heading, 3 report types: Invoice Aging, Project Profitability, Timesheet. |
| 13 | Team link works | **PASS** | Page loads with "Team" heading, member count (5), invite form. |
| 14 | Resources link works | **PASS** | Page loads with capacity planning grid (5 team members x 5 weeks), search/filter controls. |
| 15 | Notifications link works | **PASS** | Page loads with notification list (11 unread), Mark All as Read button. Shows various notification types (automation, proposal, invoice, customer, retainer, task). |
| 16 | Settings link works | **PASS** | Redirects to /settings/general. Shows full settings nav with 6 sections (General, Work, Documents, Finance, Clients, Access & Integrations). |

**Note**: Test plan includes "Recurring Schedules" which maps to sidebar item "Recurring Schedules" under Delivery. Loaded successfully with schedule list page.

**Summary**: 16 PASS, 0 FAIL (16 tests)

---

## Console Errors

| Page | Error | Severity |
|------|-------|----------|
| `/settings/rates` | `TypeError: Cannot read properties of null (reading 'length')` + 500 status | HIGH |
| `/profitability` (Carol) | 500 status | MEDIUM |
| `/reports` (Carol) | 500 status | MEDIUM |
| `/customers` (Carol) | 500 status | MEDIUM |
| `/settings/roles` (Carol) | 500 status | MEDIUM |

---

## CUST-01: Customer CRUD

| # | Test | Actor | Result | Evidence |
|---|------|-------|--------|----------|
| 1 | Create customer with required fields | Alice | **PASS** | Clicked "New Customer", filled Name ("REG-Test Customer Corp"), Email ("reg-test@example.com"), Phone ("+27-11-555-9000"). Step 2 showed custom field groups (SA Accounting Trust Details, Client Details, Contact & Address) with required fields. Submitted via "Create Customer". Customer appeared in list as row 9 with Lifecycle "Prospect", Created "Mar 19, 2026". Customer count incremented from 8 to 9. |
| 2 | Create customer with custom fields | Alice | **NOT TESTED** | Step 2 dialog confirmed: custom field groups display correctly with required (*) and optional fields. SA Accounting Trust Details has Trust Registration Number, Trust Deed Date, Trust Type (required). SA Accounting Client Details has SARS Tax Reference, Financial Year-End, Entity Type, Registered Address, Primary Contact, FICA Verified (all required). Full creation with custom fields not tested in this cycle. |
| 3 | Edit customer name | Alice | **NOT TESTED** | |
| 4 | Search customer list | Alice | **NOT TESTED** | |
| 5 | Customer list pagination | Alice | **NOT TESTED** | List shows 9 customers; no pagination needed at this count. |

**Summary**: 1 PASS, 0 FAIL, 4 NOT TESTED (5 tests)

---

## CUST-02: Customer Lifecycle (partial)

| # | Test | Actor | Result | Evidence |
|---|------|-------|--------|----------|
| 1 | New customer defaults to PROSPECT | Alice | **PASS** | Created "REG-Test Customer Corp" -- Lifecycle column shows "Prospect". Confirmed via customer list. |

**Summary**: 1 PASS (of 10 tests, 9 not tested)

---

## Execution Summary

**Tests executed**: 28 (AUTH-01: 10, NAV-01: 16, CUST-01: 1, CUST-02: 1)
**Pass**: 23
**Fail**: 4
**Partial**: 1
**Not Tested**: ~172 (remaining P0 + P1 + P2 tests)

**Bugs found**: 2
- **BUG-REG-001** (HIGH): Settings > Rates & Currency 500 for all users
- **BUG-REG-002** (MEDIUM): Carol (Member) gets 500 on role-gated pages instead of permission denied

**Next checkpoint**: CUST-01 #3 (Edit customer name), then CUST-02 (full lifecycle), PROJ-01, PROJ-02, PROJ-03, INV-01, INV-02, INV-03, PORTAL-01, PORTAL-02
