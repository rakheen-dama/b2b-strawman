# QA Cycle Status — Regression Test Suite (2026-03-19)

## Current State

- **QA Position**: CYCLE_6_COMPLETE — Portal gap verification done. 5 PASS, 3 PARTIAL (read model not synced), 1 FAIL (branding URL param). Regression suite: 88 PASS, 1 FAIL (WONT_FIX), 1 PARTIAL. 0 NOT_TESTED.
- **Cycle**: 6
- **E2E Stack**: READY — No-cache rebuild of both frontend AND backend completed for portal gap fixes (GAP-PORTAL-01 through 09, commit c331c388). All 6/6 services healthy.
- **Branch**: `bugfix_cycle_regression_2026-03-19`
- **Scenario**: `qa/testplan/regression-test-suite.md`
- **Focus**: Full regression test suite across all implemented features

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Track | Notes |
|----|---------|----------|--------|-------|----|-------|-------|
| BUG-REG-001 | Settings > Rates & Currency 500 for all users | HIGH | VERIFIED | QA | #782, #785 | AUTH-01, SET-02 | Attempt 1 (PR #782): fixed members array null guard — wrong root cause. Attempt 2 (PR #785): fixed actual root cause — `AvatarCircle` component `name.length` crash when `member.name=null`. Added `name ?? ""` null-coalescing guard inside AvatarCircle. Verified Cycle 4: Alice (Owner) and Bob (Admin) both load rates page without error. |
| BUG-REG-002 | Carol (Member) gets 500 on role-gated pages | HIGH | VERIFIED | QA | #783 | AUTH-01 | All 4 pages (profitability, reports, customers, settings/roles) show "You don't have access to [Page]" with PermissionDenied component. No 500 errors. |
| BUG-REG-003 | Customer list has no free-text search input | LOW | WONT_FIX | Dev | — | CUST-01 | Missing feature, not a regression. Customer search was never implemented (no backend search endpoint, no frontend input). Out of scope for bugfix cycle. Spec written for future reference: `fix-specs/BUG-REG-003.md`. |
| GAP-PORTAL-01 | Proposal workflow — 4 backend endpoints, 0 frontend | HIGH | VERIFIED | QA | c331c388 | PORTAL | Proposals list + detail page functional. Accept returns 409 (correct business rule). Decline button present. 0 console errors. |
| GAP-PORTAL-02 | Project comments — 2 backend endpoints, 0 frontend | HIGH | SPEC_READY | QA | c331c388 | PORTAL | Frontend code complete (PortalCommentSection with list+post). BLOCKED: portal read model empty — project detail returns 404. Root cause: seed scripts never call `/internal/portal/resync/{orgId}`. Combined spec: `fix-specs/GAP-PORTAL-02-03-04.md`. |
| GAP-PORTAL-03 | Project tasks — 1 backend endpoint, 0 frontend | MEDIUM | SPEC_READY | QA | c331c388 | PORTAL | Frontend code complete (PortalTaskList component). BLOCKED: portal read model empty — tasks endpoint returns 404. Combined spec: `fix-specs/GAP-PORTAL-02-03-04.md`. |
| GAP-PORTAL-04 | Project summary/time tracking — 1 backend endpoint, 0 frontend | MEDIUM | SPEC_READY | QA | c331c388 | PORTAL | Frontend code complete (3 summary cards). BLOCKED: portal read model empty — summary endpoint returns 404. Combined spec: `fix-specs/GAP-PORTAL-02-03-04.md`. |
| GAP-PORTAL-05 | Pending acceptance requests list — 1 backend endpoint, 0 frontend | MEDIUM | VERIFIED | QA | c331c388 | PORTAL | Acceptances page loads. Empty state: "No pending acceptances". API returns []. 0 console errors. |
| GAP-PORTAL-06 | Portal profile page — 1 backend endpoint, 0 frontend | LOW | VERIFIED | QA | c331c388 | PORTAL | Profile shows email, customer name, display name, role. All fields correct. 0 console errors. |
| GAP-PORTAL-07 | Org branding on portal login page — 1 backend endpoint, 0 frontend | LOW | SPEC_READY | QA | c331c388 | PORTAL | Branding API works. Branding applied after magic link request (org name, brand color stripe, button color). BUT: `?orgId` URL param not consumed — org field not pre-populated, branding not auto-fetched on page load. Root cause: `useState("")` on line 29 never reads URL params. Spec: `fix-specs/GAP-PORTAL-07.md`. |
| GAP-PORTAL-08 | PortalProject frontend type missing fields | LOW | VERIFIED | QA | c331c388 | PORTAL | Type updated: status?, commentCount?, createdAt. Detail uses dedicated endpoint. List returns createdAt. Code renders all fields correctly. |
| GAP-PORTAL-09 | PortalDocument missing project context | LOW | VERIFIED | QA | c331c388 | PORTAL | Backend PortalDocumentResponse includes projectId+projectName. Frontend type updated. Document table renders Project column with showProject prop. |

## Results Summary

| Track | ID | Test | Result | Evidence |
|-------|-----|------|--------|----------|
| AUTH-01 | 1 | Owner can access all settings | PASS | All settings pages load including Rates & Currency (Cycle 4 verified, PR #785) |
| AUTH-01 | 2 | Admin can access most settings | PASS | General loads with full form |
| AUTH-01 | 3 | Member blocked from rate cards | PASS | Permission denied message shown |
| AUTH-01 | 4 | Member blocked from profitability | PASS | "You don't have access to Profitability" — PermissionDenied component (Cycle 2 verified) |
| AUTH-01 | 5 | Member blocked from reports | PASS | "You don't have access to Reports" — PermissionDenied component (Cycle 2 verified) |
| AUTH-01 | 6 | Member can access My Work | PASS | Page loads with tasks and time data |
| AUTH-01 | 7 | Member can access Projects | PASS | Page loads with project list |
| AUTH-01 | 8 | Member blocked from customers | PASS | "You don't have access to Customers" — PermissionDenied component (Cycle 2 verified) |
| AUTH-01 | 9 | Admin can manage team | PASS | Team page with invite form loads |
| AUTH-01 | 10 | Member blocked from roles settings | PASS | "You don't have access to Roles & Permissions" — PermissionDenied component (Cycle 2 verified) |
| NAV-01 | 1-16 | All sidebar nav items | PASS | All 16 pages load correctly for Alice |
| CUST-01 | 1 | Create customer with required fields | PASS | "REG-Test Customer Corp" created |
| CUST-01 | 2 | Create customer with custom fields | PASS | POST /api/field-definitions created "QA Test Custom Field" (TEXT, CUSTOMER). Field appears in list. Custom field CRUD functional. (Cycle 5) |
| CUST-01 | 3 | Edit customer name | PASS | Name changed, persisted after reload |
| CUST-01 | 4 | Search customer list | FAIL | No free-text search input exists |
| CUST-01 | 5 | Customer list pagination | PASS | 9 customers, single page |
| CUST-02 | 1 | New customer defaults to PROSPECT | PASS | Confirmed via customer list |
| CUST-02 | 2 | PROSPECT -> ONBOARDING | PASS | Badge changed, onboarding tab appeared |
| CUST-02 | 3 | ONBOARDING -> ACTIVE (checklist) | PASS | 4/4 items completed. Uploaded doc via S3 presigned URL, completed "Upload signed engagement letter" checklist item with documentId. Checklist status=COMPLETED. Customer auto-transitioned ONBOARDING -> ACTIVE. (Cycle 5) |
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
| INV-02 | 5 | VOID releases time entries | PASS | Created project->task->time entry (120min, snapshot=1500 ZAR)->invoice from timeEntryIds->approved (INV-0008)->voided. Post-void: time entry invoiceId=null (released). (Cycle 5) |
| INV-02 | 6 | Cannot edit approved | PASS | Edit/Delete buttons removed |
| INV-02 | 7 | Cannot skip DRAFT -> SENT | PASS | API 409: "Only approved can be sent" |
| INV-02 | 8 | Cannot PAID -> VOID | PASS | API 409: "Only approved or sent" |
| INV-03 | 1 | Single line math | PASS | 3*450=1350, tax=202.5, total=1552.5 |
| INV-03 | 2 | Multiple line math | PASS | 2 lines, verified correct |
| INV-03 | 3 | Rounding: non-terminating | PASS | 1.5*333.33=500.00, rounds correctly |
| INV-03 | 4 | Zero quantity line | PASS | Rejected 400: @Positive validation |
| INV-03 | 5 | Fractional quantity | PASS | 0.5 and 1.5 qty correct |
| INV-03 | 6 | Rate snapshot immutability | PASS | Alice rate 1500->2000. Old invoice INV-0008 still unitPrice=1500, new invoice unitPrice=2000. Snapshot immutable. (Cycle 5) |
| PORTAL-01 | 1 | Kgosi sees only Kgosi projects | PASS | Portal API: 5 Kgosi projects, 0 Vukani/Naledi/Moroka. Playwright: 6 projects in UI (all Kgosi). (Cycle 5) |
| PORTAL-01 | 2 | Kgosi cannot see Naledi data | PASS | Portal /documents returns []. Portal /projects returns only Kgosi projects. No cross-customer data. (Cycle 5) |
| PORTAL-01 | 3 | Direct URL to Vukani project | PASS | GET /portal/projects/{vukani-id} with Kgosi JWT: HTTP 404 "No project found". (Cycle 5) |
| PORTAL-01 | 4 | API: Kgosi JWT on Vukani project | PASS | GET /portal/projects/{vukani-id}/tasks with Kgosi JWT: HTTP 404. Complete isolation. (Cycle 5) |
| PORTAL-01 | 5 | Vukani sees only Vukani projects | PASS | Vukani portal: 2 projects (Monthly Bookkeeping, BEE Certificate Review). No Kgosi data. (Cycle 5) |
| PORTAL-02 | 1 | Portal landing page loads | PASS | Login form, magic link button |
| PORTAL-02 | 2 | Portal documents list | PASS | Playwright: Documents page renders with "No documents" empty state. Page functional. (Cycle 5) |
| PORTAL-02 | 3 | Portal invoice view | PARTIAL | API: GET /portal/invoices returns 3 invoices (correct data isolation). Frontend: /portal/invoices returns 404 (page not implemented). Backend works; frontend page missing. (Cycle 5) |
| PORTAL-02 | 4 | No firm-side nav leaks | PASS | Playwright: Portal nav = Projects, Requests, Documents only. API: /api/projects, /api/customers, /api/invoices all return 401 with portal JWT. (Cycle 5) |

## Scorecard

| Track | Tested | Pass | Fail | Partial | Not Tested |
|-------|--------|------|------|---------|------------|
| AUTH-01 | 10 | 10 | 0 | 0 | 0 |
| NAV-01 | 16 | 16 | 0 | 0 | 0 |
| CUST-01 | 5 | 4 | 1 | 0 | 0 |
| CUST-02 | 10 | 10 | 0 | 0 | 0 |
| PROJ-01 | 7 | 7 | 0 | 0 | 0 |
| PROJ-02 | 7 | 7 | 0 | 0 | 0 |
| PROJ-03 | 7 | 7 | 0 | 0 | 0 |
| INV-01 | 5 | 5 | 0 | 0 | 0 |
| INV-02 | 8 | 8 | 0 | 0 | 0 |
| INV-03 | 6 | 6 | 0 | 0 | 0 |
| PORTAL-01 | 5 | 5 | 0 | 0 | 0 |
| PORTAL-02 | 4 | 3 | 0 | 1 | 0 |
| **Total** | **90** | **88** | **1** | **1** | **0** |

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
| 2026-03-19T21:30Z | Dev | BUG-REG-001 FIXED via PR #782 (squash-merged). Added null coalescing on `settingsRes.value` and `Array.isArray()` guard on `membersRes.value` in `settings/rates/page.tsx`. Added `!members` defensive guard in `MemberRatesTable` before `.length` check. Added `settings?.defaultCurrency ?? "USD"` fallback. Build passes. 263/264 test files pass (1 pre-existing failure in portal-login.test.tsx). Frontend change — NEEDS_REBUILD. |
| 2026-03-19T21:37Z | Dev | BUG-REG-002 FIXED via PR #783 (squash-merged). Fixed `ErrorBoundary.getDerivedStateFromError` to detect and re-throw errors with `NEXT_NOT_FOUND`/`NEXT_REDIRECT` digest. Replaced `notFound()` RBAC gates with `<PermissionDenied>` component on profitability, reports, customers, and settings/roles pages. Build passes. 263/264 test files pass (1 pre-existing failure in portal-login.test.tsx). Frontend change — NEEDS_REBUILD. |
| 2026-03-19T21:41Z | Infra | E2E frontend rebuilt via `e2e-rebuild.sh frontend`. Next.js build succeeded (45s). All 6/6 services healthy. Smoke test: HTTP 200 on http://localhost:3001. Stack status -> READY for QA re-verification of BUG-REG-001 and BUG-REG-002 fixes. |
| 2026-03-19T21:55Z | QA | Cycle 2 verification started. Authenticated as Alice (Owner) for BUG-REG-001, then Carol (Member) for BUG-REG-002. |
| 2026-03-19T21:58Z | QA | BUG-REG-001 REOPENED. Rates page still shows "Something went wrong" with same TypeError. Root cause: Docker `e2e-rebuild.sh` used cached build layers — SSR chunk `components_rates_member-rates-table_tsx_6ae47b6d._.js` still has unfixed `n.length` without `!n` guard. Source files on disk have the fix but compiled output does not. Needs `--no-cache` rebuild. |
| 2026-03-19T22:00Z | QA | BUG-REG-002 VERIFIED. All 4 role-gated pages tested as Carol: profitability, reports, customers, settings/roles. All show PermissionDenied component ("You don't have access to [Page]") instead of 500. ErrorBoundary fix is working. |
| 2026-03-19T22:02Z | QA | AUTH-01 scores updated: #4, #5, #8, #10 changed FAIL -> PASS. #1 remains PARTIAL (rates still broken). Scorecard: 75 PASS, 1 FAIL, 2 PARTIAL, 12 NOT_TESTED. Cycle set to 2. |
| 2026-03-19T22:10Z | Infra | Frontend rebuilt with `docker compose build --no-cache frontend`. Previous `e2e-rebuild.sh` used cached layers so SSR chunks still had unfixed code. No-cache rebuild took ~75s. Verified fix in compiled output: `member-rates-table` chunk now contains `n&&0!==n.length` (null guard). All 6/6 services healthy. Smoke test HTTP 200. Stack status -> READY for Cycle 3 re-verification of BUG-REG-001. |
| 2026-03-19T22:15Z | QA | Cycle 3 started. Authenticated as Alice (Owner). Navigated to `/org/e2e-test-org/settings/rates`. |
| 2026-03-19T22:16Z | QA | BUG-REG-001 STILL FAILING. Same "Something went wrong" error, HTTP 500. Console: `TypeError: Cannot read properties of null (reading 'length')` at `39d93f5aac721830.js:7:4858`. Server log: crash at `_86c21c83._.js:7:4589`. |
| 2026-03-19T22:20Z | QA | Root cause analysis: PR #782 fix IS deployed (confirmed `Array.isArray` guard in server chunk, `n&&0!==n.length` in client chunk). Crash is NOT in `MemberRatesTable.members.length` check. Crash is in `AvatarCircle` component at `name.length` where `name` prop is null. AvatarCircle computes a hash of the name string for avatar color — `null.length` throws. The `members` array passes all guards (not null, not empty) but contains entries where `member.name` is null. PR #782 fixed the wrong root cause. |
| 2026-03-19T22:22Z | QA | BUG-REG-001 remains REOPENED. New fix needed: either (1) AvatarCircle should guard `name ?? ""`, or (2) MemberRatesTable should filter `members.filter(m => m?.name)`, or (3) server component should filter before passing to client. AUTH-01 #1 remains PARTIAL. Scorecard unchanged. Cycle set to 3. Bob testing skipped (page crashes before content renders). |
| 2026-03-20T00:00Z | Product | BUG-REG-001 fix spec REWRITTEN. Previous spec (PR #782) fixed wrong root cause (members array null/empty). Actual root cause: `AvatarCircle` component at `frontend/components/ui/avatar-circle.tsx` line 11 — `name.length` in `hashName()` crashes when `member.name` is null. Fix: guard `name ?? ""` inside AvatarCircle (protects all 7 call sites across 5 files). Spec at `qa_cycle/fix-specs/BUG-REG-001.md`. BUG-REG-001 remains REOPENED for Dev. |
| 2026-03-20T00:07Z | Dev | BUG-REG-001 FIXED (attempt 2) via PR #785 (squash-merged). Added `const safeName = name ?? ""` inside `AvatarCircle` component and updated props type to `string | null | undefined`. Uses `safeName` in `hashName()` and `getInitials()` calls. Single file change: `frontend/components/ui/avatar-circle.tsx`. Build passes. 263/264 test files pass (1 pre-existing failure in portal-login.test.tsx). Frontend change — NEEDS_REBUILD. |
| 2026-03-20T00:15Z | Infra | Frontend rebuilt with `docker compose build --no-cache frontend` (fresh build, no cached layers). Fix verified in compiled SSR chunk `_e63b3b77._.js`: AvatarCircle function contains `h=a??""` (minified null-coalescing guard). All 6/6 services healthy. Smoke test HTTP 200 on http://localhost:3001. Stack status -> READY for Cycle 4 re-verification of BUG-REG-001. |
| 2026-03-20T00:30Z | QA | Cycle 4 started. Authenticated as Alice (Owner). Navigated to `/org/e2e-test-org/settings/rates`. |
| 2026-03-20T00:31Z | QA | BUG-REG-001 VERIFIED. Rates & Currency page loads fully for Alice (Owner): heading, default currency (ZAR), billing rates table with 6 members. Members with null names render with "?" avatar and email-only display. 0 console errors. Screenshot: `qa_cycle/screenshots/bug-reg-001-cycle4-alice-PASS.png`. |
| 2026-03-20T00:33Z | QA | Authenticated as Bob (Admin). Navigated to `/org/e2e-test-org/settings/rates`. Page loads identically — full rate table, all 6 members, null-name members display correctly. 0 console errors. Screenshot: `qa_cycle/screenshots/bug-reg-001-cycle4-bob-PASS.png`. |
| 2026-03-20T00:35Z | QA | BUG-REG-001 status: FIXED -> VERIFIED. AUTH-01 #1: PARTIAL -> PASS. Scorecard: 76 PASS, 1 FAIL (WONT_FIX), 1 PARTIAL, 12 NOT_TESTED. Both bugs now VERIFIED. QA Position -> ALL_DAYS_COMPLETE. Cycle set to 4. Results written to `qa_cycle/checkpoint-results/regression-cycle4-verification.md`. |
| 2026-03-20T22:30Z | QA | Cycle 5 started. Goal: resolve all 12 NOT_TESTED items using Mailpit API + direct API calls for portal auth. |
| 2026-03-20T22:32Z | QA | Portal auth flow established: POST /portal/auth/request-link (e2e profile returns token directly), POST /portal/auth/exchange -> portal JWT. orgId is "e2e-test-org" (slug), not "org_e2e_test". |
| 2026-03-20T22:35Z | QA | PORTAL-01 complete: 5/5 PASS. Kgosi sees 5 Kgosi projects (API + Playwright). Vukani sees 2 Vukani projects. Cross-customer access returns 404. Complete data isolation verified. |
| 2026-03-20T22:38Z | QA | PORTAL-02 #2,#3,#4 tested. #2 PASS (documents page renders). #3 PARTIAL (API returns invoices, frontend /portal/invoices is 404). #4 PASS (nav shows only Projects/Requests/Documents, API returns 401 for org endpoints with portal JWT). |
| 2026-03-20T22:40Z | QA | INV-02 #5 PASS: Full chain project->task->time entry->invoice(from timeEntryIds)->approve(INV-0008)->void. Post-void time entry invoiceId=null (released). |
| 2026-03-20T22:41Z | QA | INV-03 #6 PASS: Alice rate 1500->2000. Old invoice INV-0008 unitPrice=1500 (immutable). New invoice unitPrice=2000. Rate restored to 1500. |
| 2026-03-20T22:42Z | QA | CUST-01 #2 PASS: Created custom field definition (TEXT, CUSTOMER entity type, slug=qa_test_custom_field). Field appears in GET field-definitions list. |
| 2026-03-20T22:43Z | QA | CUST-02 #3 upgraded PARTIAL -> PASS: Uploaded document via S3 presigned URL (upload-init -> PUT S3 -> confirm). Completed 4th checklist item "Upload signed engagement letter" with documentId. Checklist status=COMPLETED. Customer auto-transitioned ONBOARDING -> ACTIVE. |
| 2026-03-20T22:45Z | QA | ALL_DAYS_COMPLETE with full coverage. 88 PASS, 1 FAIL (WONT_FIX), 1 PARTIAL, 0 NOT_TESTED. Cycle set to 5. Results written to `qa_cycle/checkpoint-results/regression-cycle5-remaining.md`. |
| 2026-03-20T23:00Z | Infra | Full no-cache rebuild of both frontend AND backend for portal gap fixes (GAP-PORTAL-01 through 09, commit c331c388). Stopped+removed old containers, built with `--no-cache`, restarted. All 6/6 services healthy. Smoke tests: HTTP 200 on localhost:3001 (frontend) and localhost:8081/actuator/health (backend). Database reseeded. Stack status -> READY for portal gap verification. |
| 2026-03-20T23:15Z | QA | Cycle 6 started. Portal gap verification. Authenticated as Kgosi portal contact (thabo@kgosiconstruction.co.za) via magic link flow. |
| 2026-03-20T23:16Z | QA | Discovered portal read model (`portal.*` schema) is empty. All portal_projects, portal_documents, portal_comments, portal_tasks, portal_project_summaries tables have 0 rows. Portal resync endpoint returns 401 (API key auth issue). Root cause: DB reseeded but portal read model never re-synced. |
| 2026-03-20T23:20Z | QA | GAP-PORTAL-01 VERIFIED. Proposals list shows PROP-0001 (Retainer, R5,500, Pending). Detail page shows full info + Accept/Decline buttons. Accept returns 409 (correct business rule). 0 console errors. |
| 2026-03-20T23:22Z | QA | GAP-PORTAL-02/03/04 REOPENED. Frontend code complete (comments section, task list, summary cards all implemented). BLOCKED by empty portal read model — project detail/comments/tasks/summary endpoints return 404 "No project found". Project list works (uses PortalQueryService, not read model). |
| 2026-03-20T23:24Z | QA | GAP-PORTAL-05 VERIFIED. Acceptances page loads with "No pending acceptances" empty state. API returns []. 0 console errors. |
| 2026-03-20T23:25Z | QA | GAP-PORTAL-06 VERIFIED. Profile page shows email (thabo@kgosiconstruction.co.za), customer (Kgosi Construction), role (primary). 0 console errors. |
| 2026-03-20T23:27Z | QA | GAP-PORTAL-07 REOPENED. Branding API works (returns orgName, brandColor #1B5E20). Branding applied AFTER magic link request (heading changes, brand color stripe, button styled). BUT `?orgId=e2e-test-org` URL param not consumed — orgSlug state initialized to empty, useSearchParams not used. |
| 2026-03-20T23:28Z | QA | GAP-PORTAL-08 VERIFIED. PortalProject type has status?, commentCount?, createdAt. Detail endpoint uses /portal/projects/{id} (not searching list). Code renders status badge, createdAt date, commentCount in tab. |
| 2026-03-20T23:29Z | QA | GAP-PORTAL-09 VERIFIED. Backend PortalDocumentResponse has projectId+projectName. Frontend type updated. Documents page passes showProject to table. Project column renders doc.projectName. |
| 2026-03-20T23:30Z | QA | Cycle 6 complete. 5 VERIFIED (01, 05, 06, 08, 09), 3 REOPENED (02, 03, 04 — read model sync), 1 REOPENED (07 — URL param). Results written to `qa_cycle/checkpoint-results/regression-cycle6-portal-gaps.md`. |
| 2026-03-20T23:45Z | Product | GAP-PORTAL-02/03/04 triaged. Shared root cause confirmed: seed scripts (`compose/seed/seed.sh` and `compose/seed/rich-seed.sh`) never call `POST /internal/portal/resync/{orgId}` after populating data. Portal read model tables remain empty. Backend resync endpoint + service work correctly. Fix: add resync call as final seed step. Combined spec: `fix-specs/GAP-PORTAL-02-03-04.md`. All 3 items -> SPEC_READY. Effort: XS. |
| 2026-03-20T23:50Z | Product | GAP-PORTAL-07 triaged. Root cause confirmed: `frontend/app/portal/page.tsx` line 29 initializes `orgSlug` state to `""` and never reads `useSearchParams()`. Existing `useEffect` on line 67 already auto-fetches branding when `orgSlug` is non-empty, so fix is just initializing state from URL param. Spec: `fix-specs/GAP-PORTAL-07.md`. -> SPEC_READY. Effort: XS. |
