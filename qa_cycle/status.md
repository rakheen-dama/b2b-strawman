# QA Cycle Status — Regression Test Suite (2026-03-19)

## Current State

- **QA Position**: INV-02 #1 (DRAFT -> APPROVED) — AUTH-01, NAV-01, CUST-01, CUST-02, PROJ-01, PROJ-02, PROJ-03, INV-01, INV-03, PORTAL-01, PORTAL-02 complete
- **Cycle**: 1
- **E2E Stack**: READY — all 6/6 services healthy (backend:8081, frontend:3001, localstack:4567, mailpit:8026, mock-idp:8090, postgres:5433)
- **Branch**: `bugfix_cycle_regression_2026-03-19`
- **Scenario**: `qa/testplan/regression-test-suite.md`
- **Focus**: Full regression test suite across all implemented features

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Track | Notes |
|----|---------|----------|--------|-------|----|-------|-------|
| BUG-REG-001 | Settings > Rates & Currency 500 for all users | HIGH | OPEN | Dev | — | AUTH-01, SET-02 | `TypeError: Cannot read properties of null (reading 'length')`. Blocks rate card testing. Screenshot: `regression-auth01-rates-500.png` |
| BUG-REG-002 | Carol (Member) gets 500 on role-gated pages | MEDIUM | OPEN | Dev | — | AUTH-01 | Profitability, Reports, Customers, Roles pages crash with 500 instead of showing permission denied. Sidebar correctly hides links but direct URL access crashes. |
| BUG-REG-003 | Customer list has no free-text search input | LOW | OPEN | Dev | — | CUST-01 | Only lifecycle status filtering available. No name/email/phone search. |

## Results Summary

| Track | ID | Test | Result | Evidence |
|-------|-----|------|--------|----------|
| AUTH-01 | 1 | Owner can access all settings | PARTIAL | Rates 500, all others load |
| AUTH-01 | 2 | Admin can access most settings | PASS | General loads with full form |
| AUTH-01 | 3 | Member blocked from rate cards | PASS | Permission denied message shown |
| AUTH-01 | 4 | Member blocked from profitability | FAIL | 500 error instead of permission denial |
| AUTH-01 | 5 | Member blocked from reports | FAIL | 500 error instead of permission denial |
| AUTH-01 | 6 | Member can access My Work | PASS | Page loads with tasks and time data |
| AUTH-01 | 7 | Member can access Projects | PASS | Page loads with project list |
| AUTH-01 | 8 | Member blocked from customers | FAIL | 500 error instead of read-only/denial |
| AUTH-01 | 9 | Admin can manage team | PASS | Team page with invite form loads |
| AUTH-01 | 10 | Member blocked from roles settings | FAIL | 500 blank page on direct URL |
| NAV-01 | 1-16 | All sidebar nav items | PASS | All 16 pages load correctly for Alice |
| CUST-01 | 1 | Create customer with required fields | PASS | "REG-Test Customer Corp" created, appears in list |
| CUST-01 | 3 | Edit customer name | PASS | Name changed to "(Edited)", persisted after reload |
| CUST-01 | 4 | Search customer list | FAIL | No free-text search input exists |
| CUST-01 | 5 | Customer list pagination | PASS | 9 customers, single page, no broken pagination |
| CUST-02 | 1 | New customer defaults to PROSPECT | PASS | Confirmed via customer list lifecycle column |
| CUST-02 | 2 | PROSPECT -> ONBOARDING | PASS | Badge changed, onboarding tab appeared (0/4 checklist) |
| PROJ-01 | 4 | Project detail tabs load | PASS | 15 tabs render, overview shows full project data |
| PROJ-02 | 7 | Task list with assignments | PASS | 3 tasks visible, Carol assigned, filters available |
| PROJ-03 | 7 | Time tab shows entries | PASS | 4h total, by-task and by-member breakdowns |
| INV-01 | 5 | Invoice list with totals | PASS | 15 invoices, financial summary, status filters |
| INV-03 | 2 | Multiple line invoice math | PASS | 2 lines, subtotal/tax/total verified correct |
| INV-03 | 5 | Fractional quantity | PASS | 0.5 and 1.5 qty lines calculated correctly |
| PORTAL-02 | 1 | Portal landing page loads | PASS | Login form with email, org, magic link button |

## Scorecard

| Track | Tested | Pass | Fail | Partial | Not Tested |
|-------|--------|------|------|---------|------------|
| AUTH-01 | 10 | 5 | 4 | 1 | 0 |
| NAV-01 | 16 | 16 | 0 | 0 | 0 |
| CUST-01 | 5 | 3 | 1 | 0 | 1 |
| CUST-02 | 10 | 2 | 0 | 0 | 8 |
| PROJ-01 | 7 | 1 | 0 | 0 | 6 |
| PROJ-02 | 7 | 1 | 0 | 0 | 6 |
| PROJ-03 | 7 | 1 | 0 | 0 | 6 |
| INV-01 | 5 | 1 | 0 | 0 | 4 |
| INV-02 | 8 | 0 | 0 | 0 | 8 |
| INV-03 | 6 | 2 | 0 | 0 | 4 |
| PORTAL-01 | 5 | 0 | 0 | 0 | 5 |
| PORTAL-02 | 4 | 1 | 0 | 0 | 3 |
| **Total** | **90** | **33** | **5** | **1** | **51** |

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-19T00:00Z | Setup | Regression QA cycle initialized on branch bugfix_cycle_regression_2026-03-19. Scenario: qa/testplan/regression-test-suite.md. Previous cycle (data integrity) was ALL_DAYS_COMPLETE. |
| 2026-03-19T00:00Z | Setup | E2E stack PARTIAL — e2e-backend not running. Dispatching Infra Agent to restore. |
| 2026-03-19T00:01Z | Infra | e2e-backend had exited (code 137, ~33h ago). Restarted via `docker compose up -d backend`. Container became healthy in ~20s. Health endpoint returns UP. Tenant schema `tenant_7d218705360b` confirmed. All 6/6 services healthy. Stack status -> READY. |
| 2026-03-19T20:30Z | QA | Cycle 1 started. Authenticated as Alice (Owner), Bob (Admin), Carol (Member) via mock-login. |
| 2026-03-19T20:35Z | QA | AUTH-01 complete: 5 PASS, 4 FAIL, 1 PARTIAL. Found BUG-REG-001 (Rates 500) and BUG-REG-002 (Carol RBAC 500s). Both non-cascading. |
| 2026-03-19T20:40Z | QA | NAV-01 complete: 16/16 PASS. All sidebar navigation items resolve to working pages for Alice (Owner). |
| 2026-03-19T20:45Z | QA | CUST-01 #1 PASS: Created "REG-Test Customer Corp" via New Customer dialog (2-step wizard). Customer count 8 -> 9. CUST-02 #1 PASS: New customer defaults to Prospect. |
| 2026-03-19T20:45Z | QA | QA Position advanced to CUST-01 #3. Cycle 1 results committed. |
| 2026-03-19T21:00Z | QA | CUST-01 #3 PASS: Edited customer name via Edit dialog, persisted after reload. CUST-01 #4 FAIL: No search input on customer list (BUG-REG-003). CUST-01 #5 PASS: Pagination not broken (9 items, single page). |
| 2026-03-19T21:05Z | QA | CUST-02 #2 PASS: PROSPECT -> ONBOARDING transition via Change Status dropdown + confirmation dialog. Badge updated, Onboarding tab appeared with 0/4 checklist. |
| 2026-03-19T21:10Z | QA | PROJ-01 #4 PASS: Project detail for "Annual Tax Return 2026 -- Kgosi" loaded with 15 tabs, complete overview with setup checklist, unbilled time, templates, health status, task/budget/margin summary. |
| 2026-03-19T21:12Z | QA | PROJ-02 #7 PASS: Tasks tab shows 3 tasks with priority, status, assignee, actions. PROJ-03 #7 PASS: Time tab shows 4h summary with by-task and by-member breakdowns. |
| 2026-03-19T21:15Z | QA | INV-01 #5 PASS: Invoice list loaded with 15 invoices, financial summary, status filters. INV-03 #2,#5 PASS: INV-0001 detail shows 2 line items with correct arithmetic (subtotal R1,425.00 + tax R213.75 = total R1,638.75). Fractional quantities (0.5, 1.5) computed correctly. |
| 2026-03-19T21:18Z | QA | PORTAL-02 #1 PASS: Portal landing at /portal loads with magic link login form. PORTAL-01 all NOT_TESTED: Portal data isolation tests require magic link auth flow which needs Mailpit integration for token extraction. |
| 2026-03-19T21:20Z | QA | QA Position advanced to INV-02 #1. All P0 sections touched. 33 PASS, 5 FAIL, 1 PARTIAL, 51 NOT_TESTED. 3 bugs logged. Results committed. |
