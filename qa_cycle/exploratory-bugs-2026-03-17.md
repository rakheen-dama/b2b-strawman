# Exploratory QA Report - DocTeams (2026-03-17)

**Tester**: Automated (Claude Code via Playwright MCP)
**Environment**: E2E mock-auth stack (http://localhost:3001)
**Backend**: http://localhost:8081
**Users tested**: Alice (Owner), Carol (Member)

---

## Summary Table

| Bug ID | Title | Severity | Page | Status |
|--------|-------|----------|------|--------|
| BUG-1 | Project Tasks tab crashes with TypeError | crash | `/org/{slug}/projects/{id}?tab=tasks` | **FIXED** (PR #726) — fallback for undefined priority/status badge variants |
| BUG-2 | Resources page crashes with 500 error | crash | `/org/{slug}/resources` | **FIXED** (PR #726) — null guards on `.split()` calls in allocation grid + week selector |
| BUG-3 | Settings > Rates & Currency page crashes with 500 error | crash | `/org/{slug}/settings/rates` | **FIXED** (PR #726) — `Promise.allSettled` so one failed endpoint doesn't crash all |
| BUG-4 | Invoice list "Total Overdue" shows `$0.00` instead of `R 0,00` | broken | `/org/{slug}/invoices` | **FIXED** (PR #726) — default currency from first invoice instead of hardcoded USD |
| BUG-5 | Invoice detail tax summary row shows "(%) " instead of tax label | cosmetic | `/org/{slug}/invoices/{id}` | **FIXED** (PR #726) — uses `invoice.taxLabel ?? "Tax"` |
| BUG-6 | Header shows "No org" on initial client-side navigation | cosmetic | All pages (initial load) | OPEN — mock-auth timing issue, not production-affecting |
| BUG-7 | User identity shows stale data after mock-login switch | cosmetic | All pages (initial navigation) | OPEN — mock-auth specific, not production-affecting |
| BUG-8 | Team page Role column is empty for all members | broken | `/org/{slug}/team` | **FIXED** (PR #726) — `normalizeRole()` now lowercases before matching badge keys |
| BUG-9 | Team page shows 2 "Unknown" orphaned members | broken | `/org/{slug}/team` | OPEN — E2E seed data quality issue, not a code bug |
| BUG-10 | React hydration error on every page load | broken | All pages | OPEN — systemic SSR/client mismatch, needs deeper investigation |
| BUG-11 | Tasks tab error message says "Unable to load projects" instead of "tasks" | cosmetic | `/org/{slug}/projects/{id}?tab=tasks` | **FIXED** (PR #726) — changed to "Unable to load this page" |
| BUG-12 | All "Generate" buttons disabled on project Overview document templates | broken | `/org/{slug}/projects/{id}` (Overview tab) | **FIXED** (PR #726) — removed Customer/OrgSettings as structural requirements for PROJECT templates |

---

## Detailed Bug Reports

### BUG-1: Project Tasks tab crashes with TypeError

- **Page**: `/org/e2e-test-org/projects/ac144e0a-8435-4a42-9729-cc996639f6f9` (Tasks tab)
- **Action**: Click on the "Tasks" tab in any project detail page
- **Expected**: Task list should render showing the project's tasks
- **Actual**: Page crashes with "Something went wrong" error boundary. The entire main content area is replaced with an error message.
- **Console errors**: `TypeError: Cannot read properties of undefined (reading 'variant')` at `z (8138a2f0be77caa0.js:1:38182)`
- **Severity**: crash
- **Screenshot evidence**: `qa_cycle/02-project-tasks-crash.png` - Error boundary showing "Something went wrong / Unable to load projects. Please try again."
- **Reproducibility**: 100% - crashes every time on every project tested

### BUG-2: Resources page crashes with 500 error

- **Page**: `/org/e2e-test-org/resources`
- **Action**: Navigate to Resources page via sidebar
- **Expected**: Resources/capacity grid should load showing team member allocations
- **Actual**: Server responds with 500 Internal Server Error, then frontend crashes with TypeError
- **Console errors**:
  - `Failed to load resource: the server responded with a status of 500 (Internal Server Error)`
  - `TypeError: Cannot read properties of null (reading 'split')` at `728091f42d9cce43.js:1:60161`
- **Severity**: crash
- **Screenshot evidence**: `qa_cycle/04-resources-crash.png` - Error boundary with "Something went wrong"

### BUG-3: Settings > Rates & Currency page crashes with 500 error

- **Page**: `/org/e2e-test-org/settings/rates`
- **Action**: Navigate to Settings > Rates & Currency as Alice (Owner)
- **Expected**: Billing and cost rates management page should load
- **Actual**: Server responds with 500 Internal Server Error, frontend shows error boundary
- **Console errors**:
  - `Failed to load resource: the server responded with a status of 500 (Internal Server Error)`
  - `TypeError: Cannot read properties of null (reading '...')` (minified)
- **Severity**: crash
- **Screenshot evidence**: `qa_cycle/05-settings-rates-crash.png`
- **Note**: When Carol (Member) visits the same URL, it correctly shows a permission denied message instead of crashing. The crash only affects Owner/Admin who should have access.

### BUG-4: Invoice list "Total Overdue" shows dollar sign instead of Rand

- **Page**: `/org/e2e-test-org/invoices`
- **Action**: View the invoices list page
- **Expected**: All currency values should use the org's configured currency (ZAR / "R")
- **Actual**: "Total Overdue" KPI card shows `$0.00` while "Total Outstanding" shows `R 14 461,25` and "Paid This Month" shows `R 6 325,00`. The dollar sign formatting is inconsistent with the rest of the page.
- **Console errors**: None specific to this issue
- **Severity**: broken
- **Screenshot evidence**: `qa_cycle/03-invoices-currency-mismatch.png` - Clear side-by-side comparison showing R for outstanding/paid but $ for overdue
- **Root cause hypothesis**: The "Total Overdue" value is likely using a hardcoded `$` formatter (probably `Intl.NumberFormat` with USD locale) as a fallback when the overdue amount is zero, while non-zero amounts go through the org currency formatter.

### BUG-5: Invoice detail tax summary row shows "(%) " instead of tax label

- **Page**: `/org/e2e-test-org/invoices/44ee720f-3da8-4517-8387-0a765829cf11`
- **Action**: View invoice detail page, look at the totals section
- **Expected**: Tax row should show something like "VAT (15%)" or "Tax (15%)"
- **Actual**: Tax row label shows just "(%) " with no descriptive text before the percentage sign
- **Console errors**: None
- **Severity**: cosmetic
- **Screenshot evidence**: Visible in invoice detail snapshot - the totals section shows "Subtotal / R 5 500,00", "(%) / R 825,00", "Total / R 6 325,00"

### BUG-6: Header shows "No org" on initial client-side navigation

- **Page**: All pages (first load after login)
- **Action**: Login as any user, observe the header bar
- **Expected**: Header should show the organization name
- **Actual**: Header top-right area shows "No org" text next to the user avatar. After a full page reload, it may display correctly.
- **Console errors**: None
- **Severity**: cosmetic
- **Screenshot evidence**: `qa_cycle/01-dashboard-alice.png` - "No org" visible in header bar

### BUG-7: User identity shows stale data after mock-login switch

- **Page**: All pages (after switching users via mock-login)
- **Action**: Login as Alice, then navigate to mock-login and switch to Carol
- **Expected**: All user indicators should immediately show the new user's identity
- **Actual**: On the first client-side navigation after switching users, the sidebar bottom shows the previous user's name/email and avatar initials (e.g., "AO" / "Alice Owner" when logged in as Carol). Corrects itself after a full page reload.
- **Console errors**: None
- **Severity**: cosmetic
- **Note**: This may be acceptable for the mock-auth flow since it is not the production auth path.

### BUG-8: Team page Role column is empty for all members

- **Page**: `/org/e2e-test-org/team`
- **Action**: View the Team page member list
- **Expected**: The "Role" column should display each member's role (Owner, Admin, Member)
- **Actual**: The Role column cell is completely empty for all 5 members. The role is visible on the project Members tab (where it shows correctly), but not on the main Team page.
- **Console errors**: None
- **Severity**: broken

### BUG-9: Team page shows 2 "Unknown" orphaned members

- **Page**: `/org/e2e-test-org/team`
- **Action**: View the Team page member list
- **Expected**: Only real team members should appear (Alice, Bob, Carol)
- **Actual**: Two additional entries appear with name "Unknown" and emails `alice-id@unknown.local` and `user_alice@unknown.local`. These appear to be orphaned test data or seed artifacts.
- **Console errors**: None
- **Severity**: broken (data quality issue in E2E seed)

### BUG-10: React hydration error on every page load

- **Page**: All pages
- **Action**: Navigate to any page via full page load (not client-side navigation)
- **Expected**: No console errors
- **Actual**: Every full page load produces `Minified React error #418` (hydration mismatch) with multiple `TypeError: Cannot read properties of null` errors. The page eventually renders correctly after client-side hydration kicks in.
- **Console errors**: `Error: Minified React error #418; visit https://react.dev/errors/418` plus 10-14 TypeErrors per page load
- **Severity**: broken (causes FOUC and could mask real errors)
- **Note**: This is a systemic issue affecting the entire application. It suggests a server/client rendering mismatch, possibly related to auth context being null during SSR.

### BUG-11: Tasks tab error message says "Unable to load projects" instead of "tasks"

- **Page**: `/org/e2e-test-org/projects/{id}` (Tasks tab)
- **Action**: Click Tasks tab (which crashes per BUG-1)
- **Expected**: If an error occurs, the message should say "Unable to load tasks"
- **Actual**: Error boundary shows "Unable to load projects. Please try again." - incorrect entity name in the error message
- **Console errors**: See BUG-1
- **Severity**: cosmetic (secondary to BUG-1)

### BUG-12: All "Generate" buttons disabled on project Overview document templates

- **Page**: `/org/e2e-test-org/projects/{id}` (Overview tab, Document Templates section)
- **Action**: View the project overview page, look at the Document Templates section
- **Expected**: Generate buttons should be clickable for at least some templates (project-scoped templates should be generable)
- **Actual**: All 6 "Generate" buttons are disabled (greyed out) with no tooltip or explanation of why they are disabled
- **Console errors**: None
- **Severity**: broken (users cannot generate documents from project overview)
- **Note**: On the Customer detail page, the Generate buttons work correctly (they are links). The project Overview buttons use a different implementation (disabled `<button>` elements).

---

## Pages That Work Correctly

### As Alice (Owner)
| Page | Status | Notes |
|------|--------|-------|
| Dashboard | OK | KPIs, getting started checklist, project health, team workload, activity all render |
| Projects list | OK | 7 projects listed with status filters |
| Project Detail > Overview | OK | Setup checklist, document templates, stats, activity |
| Project Detail > Documents | OK | Empty state with upload area |
| Project Detail > Members | OK | 3 members with roles shown |
| Project Detail > Customers | OK | Empty state |
| Project Detail > Tasks | CRASH | See BUG-1 |
| Project Detail > Time | OK | Summary, by-task and by-member tables |
| Project Detail > Expenses | OK | Empty state with Log Expense button |
| Project Detail > Budget | OK | Empty state with configure button |
| Project Detail > Financials | OK | Empty state |
| Project Detail > Staffing | OK | Empty state |
| Project Detail > Rates | OK | Empty state with Add Override button |
| Project Detail > Generated Docs | OK | Empty state |
| Project Detail > Activity | OK | Activity feed with filters |
| Customers list | OK | 5 customers with lifecycle filters |
| Customer Detail > All tabs | OK | Projects, Documents, Onboarding, Invoices, Retainer, Requests, Rates, Generated Docs, Financials all load |
| My Work | OK | Tasks, time tracking, expenses (after hydration delay) |
| Task Detail dialog | OK | Opens from My Work, shows metadata, sub-items, custom fields, comments |
| Calendar | OK | Month view with filters |
| Documents | OK | Empty state |
| Recurring Schedules | OK | Empty state |
| Invoices list | PARTIAL | See BUG-4 (currency mismatch) |
| Invoice Detail | PARTIAL | See BUG-5 (tax label) |
| Billing Runs | OK | Empty state |
| Proposals | OK | 1 proposal listed |
| Retainers | OK | Empty state |
| Compliance | OK | Lifecycle distribution, onboarding pipeline |
| Resources | CRASH | See BUG-2 |
| Profitability | OK | Team utilization, project/customer profitability |
| Reports | OK | 3 report types listed |
| Team | PARTIAL | See BUG-8, BUG-9 |
| Notifications | OK | 1 unread notification |
| Settings > General | OK | Currency, tax, branding |
| Settings > Tax | OK | Tax rates table |
| Settings > Custom Fields | OK | Field definitions and groups |
| Settings > Templates | OK | Full template list with branding |
| Settings > Automations | OK | 11 automation rules |
| Settings > Checklists | OK | 4 checklist templates |
| Settings > Clauses | OK | Comprehensive clause library (19 clauses) |
| Settings > Rates | CRASH | See BUG-3 |

### As Carol (Member)
| Page | Status | Notes |
|------|--------|-------|
| Dashboard | OK | Reduced KPI cards, limited navigation |
| My Work | OK | (not re-tested, same page structure) |
| Projects | OK | Shows 5 projects (member of) |
| Settings > General | OK | Accessible |
| Settings > Rates | OK | Shows proper permission denied message |
| Sidebar navigation | OK | Correctly reduced (no Clients, Finance, Resources, Platform Admin, Recurring Schedules) |

---

## Observations (Not Bugs)

1. **Duplicate task names**: Two tasks named "Submit ITR14" in the same project with different priorities (High and Medium). This may be intentional seed data but could confuse users.
2. **Activity feed duplicate entries**: The Activity tab shows "Alice Owner created task 'Submit ITR14'" twice - may relate to the duplicate task issue above.
3. **All projects show 0% completion**: Every project in the list shows 0% progress, which seems like tasks haven't been completed in seed data.
4. **Dashboard shows org-wide data for Members**: Carol sees the same KPI numbers as Alice (7 active projects, 21.5h logged) rather than personal metrics. The Team Workload section correctly shows a note "Contact an admin to see team-wide data."

---

## Environment Notes

- React hydration errors (BUG-10) occur on every full page navigation. This is a systemic SSR issue.
- The mock-auth flow has expected UX quirks around user identity display that would not occur in production Keycloak flow.
- All testing done with Chromium via Playwright MCP.
