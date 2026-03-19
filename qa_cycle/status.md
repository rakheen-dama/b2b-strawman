# QA Cycle Status — Regression Test Suite (2026-03-19)

## Current State

- **QA Position**: CUST-01 #3 (Edit customer name) — AUTH-01, NAV-01 complete, CUST-01 #1 and CUST-02 #1 verified
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
| CUST-02 | 1 | New customer defaults to PROSPECT | PASS | Confirmed via customer list lifecycle column |

## Scorecard

| Track | Tested | Pass | Fail | Not Tested |
|-------|--------|------|------|------------|
| AUTH-01 | 10 | 5 | 4 | 0 |
| NAV-01 | 16 | 16 | 0 | 0 |
| CUST-01 | 1 | 1 | 0 | 4 |
| CUST-02 | 1 | 1 | 0 | 9 |
| PROJ-01 | 0 | 0 | 0 | 7 |
| PROJ-02 | 0 | 0 | 0 | 7 |
| PROJ-03 | 0 | 0 | 0 | 7 |
| INV-01 | 0 | 0 | 0 | 5 |
| INV-02 | 0 | 0 | 0 | 8 |
| INV-03 | 0 | 0 | 0 | 6 |
| PORTAL-01 | 0 | 0 | 0 | 5 |
| PORTAL-02 | 0 | 0 | 0 | 4 |
| **Total** | **28** | **23** | **4** | **62** |

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-19T00:00Z | Setup | Regression QA cycle initialized on branch bugfix_cycle_regression_2026-03-19. Scenario: qa/testplan/regression-test-suite.md. Previous cycle (data integrity) was ALL_DAYS_COMPLETE. |
| 2026-03-19T00:00Z | Setup | E2E stack PARTIAL — e2e-backend not running. Dispatching Infra Agent to restore. |
| 2026-03-19T00:01Z | Infra | e2e-backend had exited (code 137, ~33h ago). Restarted via `docker compose up -d backend`. Container became healthy in ~20s. Health endpoint returns UP. Tenant schema `tenant_7d218705360b` confirmed. All 6/6 services healthy. Stack status → READY. |
| 2026-03-19T20:30Z | QA | Cycle 1 started. Authenticated as Alice (Owner), Bob (Admin), Carol (Member) via mock-login. |
| 2026-03-19T20:35Z | QA | AUTH-01 complete: 5 PASS, 4 FAIL, 1 PARTIAL. Found BUG-REG-001 (Rates 500) and BUG-REG-002 (Carol RBAC 500s). Both non-cascading. |
| 2026-03-19T20:40Z | QA | NAV-01 complete: 16/16 PASS. All sidebar navigation items resolve to working pages for Alice (Owner). |
| 2026-03-19T20:45Z | QA | CUST-01 #1 PASS: Created "REG-Test Customer Corp" via New Customer dialog (2-step wizard). Customer count 8 -> 9. CUST-02 #1 PASS: New customer defaults to Prospect. |
| 2026-03-19T20:45Z | QA | QA Position advanced to CUST-01 #3. Cycle 1 results committed. |
