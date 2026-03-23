# Keycloak Regression Cycle 1 — Results

**Date**: 2026-03-23
**Stack**: Keycloak dev stack (Frontend 3000, Backend 8080, Gateway 8443, Keycloak 8180)
**User**: Thandi Thornton (owner), thandi@thornton-test.local
**Branch**: `bugfix_cycle_kc_2026-03-23`

---

## Pre-flight: Keycloak Authentication

| # | Test | Result | Evidence |
|---|------|--------|----------|
| 0.1 | Navigate to /dashboard redirects to Keycloak login | PASS | Redirected to `localhost:8180/realms/docteams/protocol/openid-connect/auth` |
| 0.2 | Keycloak login form renders (two-step: email then password) | PASS | Email field shown first, password field after email submit |
| 0.3 | Login with thandi@thornton-test.local / password | PASS | Redirected to `/org/thornton-associates/dashboard` after credential reset via admin API. **Note**: Initial password was invalid; required admin API reset (`PUT /admin/realms/docteams/users/{id}/reset-password`). Passwords may not have been set correctly during setup. |
| 0.4 | Dashboard loads with correct user identity | PASS | Shows "TT" avatar, "Thandi Thornton", "thandi@thornton-test.local" in sidebar |

---

## Track NAV-01: Sidebar Navigation (P1)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| NAV-01.1 | Dashboard link works | PASS | `/org/thornton-associates/dashboard` loads. Shows Active Projects, Hours, Margin, Tasks, Utilization, Budget Health cards. |
| NAV-01.2 | My Work link works | PASS | `/org/thornton-associates/my-work` loads. Shows tasks, time tracking, weekly view, expenses. |
| NAV-01.3 | Calendar link works | PASS | `/org/thornton-associates/calendar` loads. Month view with filters (All/Tasks/Projects, Members). |
| NAV-01.4 | Projects (Engagements) link works | PASS | `/org/thornton-associates/projects` loads. Status filters (Active/Completed/Archived/All). |
| NAV-01.5 | Documents link works | N/A | No standalone "Documents" sidebar link. Documents accessed via project/customer tabs or Settings > Templates. |
| NAV-01.6 | Customers (Clients) link works | PASS | `/org/thornton-associates/customers` loads. Lifecycle filters (Prospect/Onboarding/Active/Dormant/Offboarding/Offboarded). |
| NAV-01.7 | Retainers link works | PASS | `/org/thornton-associates/retainers` loads. Shows Active Retainers, Periods Ready to Close, Overage Hours stats. |
| NAV-01.8 | Compliance link works | PASS | `/org/thornton-associates/compliance` loads. Shows Lifecycle Distribution, Onboarding Pipeline, Data Requests, Dormancy Check. |
| NAV-01.9 | Invoices link works | PASS | `/org/thornton-associates/invoices` loads. Shows Outstanding/Overdue/Paid stats. Status filters. Billing Runs sub-tab. |
| NAV-01.10 | Proposals (Engagement Letters) link works | PASS | `/org/thornton-associates/proposals` loads. Shows Total/Pending/Accepted/Conversion Rate stats. |
| NAV-01.11 | Profitability link works | PASS | `/org/thornton-associates/profitability` loads. Empty state with link to rate card settings. |
| NAV-01.12 | Reports link works | PASS | `/org/thornton-associates/reports` loads. Shows 3 report types: Invoice Aging, Project Profitability, Timesheet. |
| NAV-01.13 | Team link works | PASS | `/org/thornton-associates/team` loads. Shows 2 members (Thandi=Owner, Bob=Admin). Invite form visible. |
| NAV-01.14 | Resources link works | PASS | `/org/thornton-associates/resources` loads. Shows capacity planning table (4-week view, 40h/week per member). |
| NAV-01.15 | Notifications link works | PASS | `/org/thornton-associates/notifications` loads. Shows "You're all caught up" empty state. |
| NAV-01.16 | Settings link works (sidebar click) | **FAIL** | **BLOCKER**: Client-side navigation via sidebar link crashes with "Application error: a client-side exception has occurred". Console error: `Rendered more hooks than during the previous render` in React Router/useMemo. Screenshot: `qa_cycle/screenshots/nav01-16-settings-crash.png`. Direct URL navigation to `/org/thornton-associates/settings/general` works fine -- only client-side routing is broken. |

**NAV-01 Summary**: 14 PASS, 1 FAIL, 1 N/A (16 checkpoints)

---

## Track CUST-01: Customer CRUD (P0)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| CUST-01.1 | Create customer with required fields | PASS | Created "Kgosi Holdings QA" (Company, kgosi@qatest.local, +27 11 555 0001). Customer appears in list with count "1". |
| CUST-01.2 | Create customer with custom fields | PARTIAL | Step 2 dialog shows SA Accounting custom fields (Trust Details, Client Details, Contact & Address). **UI Bug**: Dialog with many custom fields overflows viewport -- Back/Create Customer buttons are unreachable via normal scroll. Required JS click to submit. Customer created successfully despite button accessibility issue. |
| CUST-01.3 | Edit customer name | NOT TESTED | Deferred to next iteration |
| CUST-01.4 | Search customer list | NOT TESTED | Only 1 customer; search not meaningful |
| CUST-01.5 | Customer list pagination | NOT TESTED | Only 1 customer |

**CUST-01 Summary**: 1 PASS, 1 PARTIAL, 3 NOT TESTED

---

## Track CUST-02: Customer Lifecycle (P0)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| CUST-02.1 | New customer defaults to PROSPECT | PASS | "Kgosi Holdings QA" created with "Prospect" badge in list and detail. |
| CUST-02.2 | PROSPECT -> ONBOARDING | PASS | Change Status > Start Onboarding (with confirmation dialog + optional notes). Badge updated to "Onboarding". Onboarding checklist (0/4) appeared. New "Onboarding" tab added. |
| CUST-02.3 | ONBOARDING -> ACTIVE (via checklist) | NOT TESTED | Would require completing all 4 checklist items |
| CUST-02.4 | PROSPECT blocked from creating project | NOT TESTED | Customer was already transitioned to Onboarding |
| CUST-02.5-10 | Remaining lifecycle tests | NOT TESTED | Deferred |

**CUST-02 Summary**: 2 PASS, 8 NOT TESTED

---

## Track PROJ-01: Project CRUD (P0)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| PROJ-01.1 | Create project with customer | PASS | Created "Annual Tax Return 2026" linked to "Kgosi Holdings QA". Project appears in list with "Lead" status, count "1 engagement". |
| PROJ-01.2 | Create project without customer | NOT TESTED | |
| PROJ-01.3 | Edit project name | NOT TESTED | |
| PROJ-01.4 | Project detail tabs load | PASS | 15 tabs render: Overview, Documents, Members, Customers, Tasks, Time, Expenses, Budget, Financials, Staffing, Rates, Generated Docs, Requests, Customer Comments, Activity. Overview shows summary cards (Budget, Hours, Tasks, Revenue). |
| PROJ-01.5 | Archive project | NOT TESTED | |
| PROJ-01.6 | Archived project blocks task creation | NOT TESTED | |
| PROJ-01.7 | Archived project blocks time logging | NOT TESTED | |

**PROJ-01 Summary**: 2 PASS, 5 NOT TESTED

---

## Track PROJ-02: Project Tasks (P0)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| PROJ-02.1 | Create task on project | PASS | Created "Gather supporting documents" on Tasks tab. Task appears in table with Priority=Medium, Status=Open, Assignee=Unassigned. Actions: Log Time, Claim. |
| PROJ-02.2-7 | Remaining task tests | NOT TESTED | |

**PROJ-02 Summary**: 1 PASS, 6 NOT TESTED

---

## Track SET-02: Rate Cards (P1, via direct URL)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| SET-02.1 | View billing rates | PASS | `/settings/rates` loads via direct URL. Default Currency = ZAR. Billing Rates and Cost Rates tabs. Both team members listed with "Not set" rates. |
| SET-02.2-5 | Remaining rate tests | NOT TESTED | |

**SET-02 Summary**: 1 PASS, 4 NOT TESTED

---

## Track AUTO-01: Automation CRUD (P1, via direct URL)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| AUTO-01.1 | View seeded automation rules | PASS | `/settings/automations` loads via direct URL. 11 seeded rules listed, all enabled. Rules: Request Complete Follow-up, Proposal Follow-up (5d), Document Review Notification, New Project Welcome, Budget Alert Escalation, Overdue Invoice Reminder, Task Completion Chain, SARS Deadline Reminder, Invoice Overdue (30d), Engagement Budget Alert (80%), FICA Reminder (7d). |
| AUTO-01.2-5 | Remaining automation tests | NOT TESTED | |

**AUTO-01 Summary**: 1 PASS, 4 NOT TESTED

---

## Track DOC-01: Template Management (P1, via direct URL)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| DOC-01.1 | Template list shows seeded templates | PASS | `/settings/templates` loads via direct URL. 12 seeded templates organized by category (Compliance, Cover Letter, Engagement Letter, Other, Project Summary, Report). All Tiptap format, Platform source, Active status. Branding section with logo upload, brand color, footer text. |
| DOC-01.2-4 | Remaining template tests | NOT TESTED | |

**DOC-01 Summary**: 1 PASS, 3 NOT TESTED

---

## Bugs Found

### BUG-KC-001: Settings page crashes on client-side navigation (BLOCKER for Settings tests)

- **Severity**: HIGH
- **Steps**: Click "Settings" in sidebar from any page (client-side navigation)
- **Expected**: Settings page loads
- **Actual**: White screen with "Application error: a client-side exception has occurred"
- **Console error**: `Error: Rendered more hooks than during the previous render` at `updateWorkInProgressHook` -> `updateMemo` -> `useMemo` -> `Router`
- **Workaround**: Navigate via direct URL (full page load) to `/org/thornton-associates/settings/general`
- **Impact**: Blocks natural navigation to all settings sub-pages. All settings pages work fine via direct URL.
- **Screenshot**: `qa_cycle/screenshots/nav01-16-settings-crash.png`

### BUG-KC-002: Create Customer dialog Step 2 action buttons inaccessible

- **Severity**: MEDIUM
- **Steps**: Create Customer > fill Step 1 > Next > Step 2 shows many custom fields
- **Expected**: Back and Create Customer buttons scrollable/visible
- **Actual**: Dialog content overflows viewport; footer buttons (Back/Create Customer) are outside viewport and cannot be scrolled to
- **Workaround**: Click "Create Customer" via JavaScript (`element.click()`)
- **Impact**: Users with smaller screens cannot complete Step 2 of customer creation without scrolling past the dialog boundary

### BUG-KC-003: Keycloak user passwords not set during provisioning

- **Severity**: MEDIUM
- **Steps**: Attempt login with documented password `password` for thandi@thornton-test.local
- **Expected**: Login succeeds
- **Actual**: "Invalid password" error. Required admin API password reset.
- **Impact**: Users cannot log in after initial Keycloak provisioning without manual password reset.

---

## Overall Scorecard

| Track | Tested | Pass | Fail | Partial | Not Tested |
|-------|--------|------|------|---------|------------|
| NAV-01 | 16 | 14 | 1 | 0 | 1 |
| CUST-01 | 5 | 1 | 0 | 1 | 3 |
| CUST-02 | 10 | 2 | 0 | 0 | 8 |
| PROJ-01 | 7 | 2 | 0 | 0 | 5 |
| PROJ-02 | 7 | 1 | 0 | 0 | 6 |
| SET-02 | 5 | 1 | 0 | 0 | 4 |
| AUTO-01 | 5 | 1 | 0 | 0 | 4 |
| DOC-01 | 4 | 1 | 0 | 0 | 3 |
| **Total** | **59** | **23** | **1** | **1** | **34** |

**Pass Rate (tested)**: 23/25 = 92%
**Coverage**: 25/59 = 42%
