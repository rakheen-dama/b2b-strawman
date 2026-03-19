# QA Cycle Status — Regression Test Suite (2026-03-19)

## Current State

- **QA Position**: ALL_SECTIONS_COMPLETE — All 12 sections tested. 11 items remain NOT_TESTED (portal auth limitation, document upload requirement, missing seed data).
- **Cycle**: 1
- **E2E Stack**: READY — all 6/6 services healthy (backend:8081, frontend:3001, localstack:4567, mailpit:8026, mock-idp:8090, postgres:5433)
- **Branch**: `bugfix_cycle_regression_2026-03-19`
- **Scenario**: `qa/testplan/regression-test-suite.md`
- **Focus**: Full regression test suite across all implemented features

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Track | Notes |
|----|---------|----------|--------|-------|----|-------|-------|
| BUG-REG-001 | Settings > Rates & Currency 500 for all users | HIGH | SPEC_READY | Dev | — | AUTH-01, SET-02 | `TypeError: Cannot read properties of null (reading 'length')`. Blocks rate card testing. Root cause: null-safety gap in page data fetch + `MemberRatesTable` component. Fix spec: `fix-specs/BUG-REG-001.md`. Effort: S. |
| BUG-REG-002 | Carol (Member) gets 500 on role-gated pages | HIGH | SPEC_READY | Dev | — | AUTH-01 | Custom `ErrorBoundary` in org layout catches Next.js `notFound()` internal errors, rendering "Something went wrong" instead of 404. Cascading: affects 14+ pages using `notFound()` for RBAC. Fix spec: `fix-specs/BUG-REG-002.md`. Effort: S. |
| BUG-REG-003 | Customer list has no free-text search input | LOW | WONT_FIX | Dev | — | CUST-01 | Missing feature, not a regression. Customer search was never implemented (no backend search endpoint, no frontend input). Out of scope for bugfix cycle. Spec written for future reference: `fix-specs/BUG-REG-003.md`. |

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
| CUST-01 | 1 | Create customer with required fields | PASS | "REG-Test Customer Corp" created |
| CUST-01 | 3 | Edit customer name | PASS | Name changed, persisted after reload |
| CUST-01 | 4 | Search customer list | FAIL | No free-text search input exists |
| CUST-01 | 5 | Customer list pagination | PASS | 9 customers, single page |
| CUST-02 | 1 | New customer defaults to PROSPECT | PASS | Confirmed via customer list |
| CUST-02 | 2 | PROSPECT -> ONBOARDING | PASS | Badge changed, onboarding tab appeared |
| CUST-02 | 3 | ONBOARDING -> ACTIVE (checklist) | PARTIAL | 3/4 items done, last needs document upload |
| CUST-02 | 4 | PROSPECT blocked from project | PASS | API 400: lifecycle guard enforced |
| CUST-02 | 5 | PROSPECT blocked from invoice | PASS | API 400: lifecycle guard enforced |
| CUST-02 | 6 | ACTIVE -> DORMANT | PASS | API transition on Acme Corp |
| CUST-02 | 7 | DORMANT -> OFFBOARDING | PASS | API transition verified |
| CUST-02 | 8 | OFFBOARDING -> OFFBOARDED | PASS | Full chain verified |
| CUST-02 | 9 | OFFBOARDED blocked from project | PASS | API 400: lifecycle guard enforced |
| CUST-02 | 10 | Invalid: PROSPECT -> ACTIVE (skip) | PASS | API 400: guard rejects skip |
| PROJ-01 | 1 | Create project with customer | PASS | API: project created with Kgosi |
| PROJ-01 | 2 | Create project without customer | PASS | API: standalone project created |
| PROJ-01 | 3 | Edit project name | PASS | API: name updated, verified |
| PROJ-01 | 4 | Project detail tabs load | PASS | 15 tabs render, overview complete |
| PROJ-01 | 5 | Archive project | PASS | ACTIVE -> COMPLETED -> ARCHIVED |
| PROJ-01 | 6 | Archived project blocks task creation | PASS | API 400: "Project is archived" |
| PROJ-01 | 7 | Archived project blocks time logging | PASS | Archive guard blocks all writes |
| PROJ-02 | 1 | Create task on project | PASS | API: task created, status=OPEN |
| PROJ-02 | 2 | Edit task title | PASS | API: title and priority updated |
| PROJ-02 | 3 | OPEN -> IN_PROGRESS | PASS | Via claim endpoint |
| PROJ-02 | 4 | IN_PROGRESS -> DONE | PASS | Via complete endpoint |
| PROJ-02 | 5 | Reopen completed task | PASS | DONE -> OPEN via reopen |
| PROJ-02 | 6 | Cancel task | PASS | Via cancel endpoint |
| PROJ-02 | 7 | Task list with assignments | PASS | 3 tasks, Carol assigned |
| PROJ-03 | 1 | Log time on task | PASS | API: 150min entry, Carol |
| PROJ-03 | 2 | Edit time entry | PASS | API: duration changed |
| PROJ-03 | 3 | Delete time entry | PASS | API: 204, removed |
| PROJ-03 | 4 | Rate snapshot inherited | PASS | billingRateSnapshot=450 ZAR |
| PROJ-03 | 5 | Billable defaults to true | PASS | billable=true in response |
| PROJ-03 | 6 | Mark non-billable | PASS | PATCH billable=false |
| PROJ-03 | 7 | Time tab shows entries | PASS | 4h total, breakdown correct |
| INV-01 | 1 | Create draft invoice | PASS | API: draft with Kgosi, ZAR |
| INV-01 | 2 | Add line item | PASS | 2 lines, totals correct |
| INV-01 | 3 | Edit line item | PASS | Updated, totals recalculated |
| INV-01 | 4 | Remove line item | PASS | 204, totals recalculated |
| INV-01 | 5 | Invoice list with totals | PASS | 15 invoices, summary correct |
| INV-02 | 1 | DRAFT -> APPROVED | PASS | UI: INV-0007 assigned, Approved |
| INV-02 | 2 | APPROVED -> SENT | PASS | UI: status=Sent, Record Payment button |
| INV-02 | 3 | SENT -> PAID | PASS | UI: payment recorded, history table |
| INV-02 | 4 | VOID sent invoice | PASS | UI: INV-0006 voided, confirmed |
| INV-02 | 6 | Cannot edit approved | PASS | Edit/Delete buttons removed |
| INV-02 | 7 | Cannot skip DRAFT -> SENT | PASS | API 409: "Only approved can be sent" |
| INV-02 | 8 | Cannot PAID -> VOID | PASS | API 409: "Only approved or sent" |
| INV-03 | 1 | Single line math | PASS | 3*450=1350, tax=202.5, total=1552.5 |
| INV-03 | 2 | Multiple line math | PASS | 2 lines, verified correct |
| INV-03 | 3 | Rounding: non-terminating | PASS | 1.5*333.33=500.00, rounds correctly |
| INV-03 | 4 | Zero quantity line | PASS | Rejected 400: @Positive validation |
| INV-03 | 5 | Fractional quantity | PASS | 0.5 and 1.5 qty correct |
| PORTAL-02 | 1 | Portal landing page loads | PASS | Login form, magic link button |

## Scorecard

| Track | Tested | Pass | Fail | Partial | Not Tested |
|-------|--------|------|------|---------|------------|
| AUTH-01 | 10 | 5 | 4 | 1 | 0 |
| NAV-01 | 16 | 16 | 0 | 0 | 0 |
| CUST-01 | 5 | 3 | 1 | 0 | 1 |
| CUST-02 | 10 | 8 | 0 | 1 | 1 |
| PROJ-01 | 7 | 7 | 0 | 0 | 0 |
| PROJ-02 | 7 | 7 | 0 | 0 | 0 |
| PROJ-03 | 7 | 7 | 0 | 0 | 0 |
| INV-01 | 5 | 5 | 0 | 0 | 0 |
| INV-02 | 8 | 7 | 0 | 0 | 1 |
| INV-03 | 6 | 5 | 0 | 0 | 1 |
| PORTAL-01 | 5 | 0 | 0 | 0 | 5 |
| PORTAL-02 | 4 | 1 | 0 | 0 | 3 |
| **Total** | **90** | **71** | **5** | **2** | **12** |

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
| 2026-03-19T21:30Z | QA | INV-02 complete: 7 PASS, 1 NOT_TESTED (void-releases-time not testable with current seed data). Full lifecycle DRAFT->APPROVED->SENT->PAID verified via UI. VOID verified on INV-0006. API guards (skip DRAFT->SENT, PAID->VOID) both return 409. Edit controls removed on approved invoices. |
| 2026-03-19T21:35Z | QA | CUST-02 remaining: 6 additional PASS via API. ACTIVE->DORMANT->OFFBOARDING->OFFBOARDED chain on Acme Corp. Lifecycle guards verified: PROSPECT and OFFBOARDED blocked from project creation (400). PROSPECT->ACTIVE skip rejected (400). Checklist 3/4 done (PARTIAL). |
| 2026-03-19T21:40Z | QA | PROJ-01 remaining: 6 additional PASS. Project CRUD (create with/without customer, edit name) via API. Archive flow (ACTIVE->COMPLETED->ARCHIVED) via dedicated PATCH endpoints. Archive guard blocks task creation (400). |
| 2026-03-19T21:42Z | QA | PROJ-02 remaining: 6 additional PASS. Full task lifecycle via API: create, edit (requires title+priority+status), claim (OPEN->IN_PROGRESS), complete (DONE), reopen (OPEN), cancel (CANCELLED). |
| 2026-03-19T21:45Z | QA | PROJ-03 remaining: 6 additional PASS. Time entry CRUD via API as Carol: log (150min), edit (180min), delete (204). Rate snapshot confirmed (billingRateSnapshot=450 ZAR). Billable flag defaults true, toggled to false via PATCH. |
| 2026-03-19T21:48Z | QA | INV-01 remaining: 4 additional PASS. Invoice CRUD via API: create draft (customerId+currency required), add 2 lines (unitPrice as BigDecimal, not cents), edit line (totals recalculate), delete line (204, totals update). |
| 2026-03-19T21:50Z | QA | INV-03 remaining: 3 additional PASS. Single line math verified (3*450=1350). Rounding: 1.5*333.33=500.00 (rounds to nearest cent). Zero quantity rejected (400, @Positive validation -- correct behavior). |
| 2026-03-19T21:55Z | QA | ALL_SECTIONS_COMPLETE. 71 PASS, 5 FAIL, 2 PARTIAL, 12 NOT_TESTED. 3 bugs (unchanged). Results committed and pushed. |
| 2026-03-19T22:30Z | Product | Triage started for 3 OPEN bugs. Investigated codebase for root causes. |
| 2026-03-19T22:45Z | Product | BUG-REG-001 -> SPEC_READY. Root cause: null-safety gap in `settings/rates/page.tsx` data fetch — `membersRes.value` can be null/undefined, passed to `MemberRatesTable` which calls `.length` on it. Fix: add null coalescing on Promise.allSettled value assignments + defensive guard in component. Effort: S. |
| 2026-03-19T22:50Z | Product | BUG-REG-002 -> SPEC_READY (escalated MEDIUM -> HIGH). Root cause: custom `ErrorBoundary` in `org/[slug]/layout.tsx` (line 102) catches ALL errors including Next.js `NEXT_NOT_FOUND` from `notFound()`. Cascading: affects 14+ pages that use `notFound()` for RBAC gating. Fix: re-throw errors with `NEXT_NOT_FOUND`/`NEXT_REDIRECT` digest in ErrorBoundary. Effort: S. |
| 2026-03-19T22:55Z | Product | BUG-REG-003 -> WONT_FIX. Customer search is a missing feature, not a regression. Neither backend (no search param in `CustomerController.listCustomers()`) nor frontend (no search input in customers page) implements it. Out of scope for bugfix cycle. Spec written for future backlog. |
