# QA Cycle Status — Regression Test Suite / Keycloak Dev Stack (2026-03-23)

## Current State

- **QA Position**: CYCLE_2_COMPLETE — Bug fixes verified (3/3). Coverage increased to 52%. CUST-01, CUST-02, PROJ-02, SET-02/03 expanded.
- **Cycle**: 2
- **Dev Stack**: READY — All 5 services running (Backend:8080, Frontend:3000, Gateway:8443, Keycloak:8180, Mailpit:8025)
- **Branch**: `bugfix_cycle_kc_2026-03-23`
- **Scenario**: `qa/testplan/regression-test-suite.md`
- **Focus**: Full regression test suite against Keycloak dev stack (real OIDC auth, gateway BFF)
- **Auth Mode**: Keycloak (not mock-auth). Login via Keycloak redirect flow.
- **Results Files**: `qa_cycle/checkpoint-results/kc-regression-cycle1.md`, `qa_cycle/checkpoint-results/kc-regression-cycle2.md`

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend | http://localhost:3000 | UP |
| Backend | http://localhost:8080 | UP |
| Gateway (BFF) | http://localhost:8443 | UP |
| Keycloak | http://localhost:8180 | UP |
| Mailpit | http://localhost:8025 | UP |

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Track | Notes |
|----|---------|----------|--------|-------|----|-------|-------|
| BUG-KC-001 | Settings page crashes on client-side navigation (sidebar click) | HIGH | VERIFIED | Dev Agent | [#827](https://github.com/rakheen-dama/b2b-strawman/pull/827) | NAV-01 | Verified in cycle 2: sidebar Settings link navigates to /settings/general without crash. 0 console errors. |
| BUG-KC-002 | Create Customer Step 2 dialog footer buttons inaccessible (overflow) | MEDIUM | VERIFIED | Dev Agent | [#828](https://github.com/rakheen-dama/b2b-strawman/pull/828) | CUST-01 | Verified in cycle 2: Step 2 dialog shows scrollable content with Back/Create Customer buttons visible at bottom. Screenshot: `bug-kc-002-verified-step2-buttons-visible.png`. |
| BUG-KC-003 | Keycloak user passwords not set during provisioning | MEDIUM | VERIFIED-BY-CODE | Dev Agent | [#829](https://github.com/rakheen-dama/b2b-strawman/pull/829) | Auth | Verified in cycle 2: Both users have password credentials (confirmed via KC Admin API). Both authenticate successfully via token endpoint with password "password". |

## Results Summary

| Track | ID | Test | Result | Evidence |
|-------|-----|------|--------|----------|
| Auth | 0.1 | Keycloak redirect flow | PASS | Redirected to KC login page |
| Auth | 0.2 | KC login form renders | PASS | Two-step flow (email then password) |
| Auth | 0.3 | Login as Thandi | PASS | After admin password reset |
| Auth | 0.4 | Dashboard loads with identity | PASS | TT avatar, correct name/email |
| NAV-01 | 1 | Dashboard | PASS | Page loads with summary cards |
| NAV-01 | 2 | My Work | PASS | Tasks, time, weekly view |
| NAV-01 | 3 | Calendar | PASS | Month view with filters |
| NAV-01 | 4 | Projects | PASS | Status filters, empty state |
| NAV-01 | 5 | Documents | N/A | No standalone sidebar link |
| NAV-01 | 6 | Customers | PASS | Lifecycle filters |
| NAV-01 | 7 | Retainers | PASS | Stats cards, status filters |
| NAV-01 | 8 | Compliance | PASS | Distribution, pipeline, requests |
| NAV-01 | 9 | Invoices | PASS | Stats, filters, billing runs |
| NAV-01 | 10 | Proposals | PASS | Stats cards |
| NAV-01 | 11 | Profitability | PASS | Empty state |
| NAV-01 | 12 | Reports | PASS | 3 report types |
| NAV-01 | 13 | Team | PASS | 2 members, invite form |
| NAV-01 | 14 | Resources | PASS | Capacity planning table |
| NAV-01 | 15 | Notifications | PASS | Empty state |
| NAV-01 | 16 | Settings (sidebar) | **PASS** | **Cycle 2**: BUG-KC-001 verified. Navigates to /settings/general without crash. |
| CUST-01 | 1 | Create customer | PASS | "Kgosi Holdings QA" created |
| CUST-01 | 2 | Custom fields Step 2 | **PASS** | **Cycle 2**: BUG-KC-002 verified. Buttons visible, content scrollable. |
| CUST-01 | 3 | Edit customer name | **PASS** | **Cycle 2**: Changed "Kgosi Holdings QA" -> "Kgosi Holdings QA Edited". Updated immediately. |
| CUST-02 | 1 | Defaults to PROSPECT | PASS | Badge shows "Prospect" |
| CUST-02 | 2 | PROSPECT -> ONBOARDING | PASS | Checklist (0/4) appeared |
| CUST-02 | 3 | ONBOARDING -> ACTIVE (checklist) | PARTIAL | **Cycle 2**: 3/4 items completed. Last item requires document ("Signed engagement letter"). Document-required constraint is by design. |
| PROJ-01 | 1 | Create project with customer | PASS | "Annual Tax Return 2026" |
| PROJ-01 | 4 | Project detail tabs | PASS | 15 tabs rendered |
| PROJ-02 | 1 | Create task | PASS | "Gather supporting documents" |
| PROJ-02 | 3 | Task OPEN -> IN_PROGRESS | **PASS** | **Cycle 2**: Status dropdown, selected In Progress. Updated in detail + table. |
| PROJ-02 | 4 | Task IN_PROGRESS -> DONE | **PASS** | **Cycle 2**: Mark Done button. Shows "Completed by Thandi Thornton". Automation created follow-up task. |
| PROJ-02 | 5 | Reopen completed task | **PASS** | **Cycle 2**: Reopen button. Status reverted to Open. Assignee re-enabled. |
| PROJ-02 | 7 | Assign member to task | **PASS** | **Cycle 2**: Combobox shows team members. Selected Thandi. Updated in detail + table. |
| SET-02 | 1 | View billing rates | PASS | Via direct URL, ZAR, 2 members |
| SET-02 | 2 | Create billing rate | PARTIAL | **Cycle 2**: Add Rate dialog opens correctly. Rate Type toggle, Hourly Rate, Currency, Dates all render. Dialog navigated away before save. |
| SET-03 | 1 | View tax rates | **PASS** | **Cycle 2**: Tax Settings page loads. 3 seeded rates (Standard 15%, Zero-rated 0%, Exempt 0%). |
| AUTO-01 | 1 | View automation rules | PASS | 11 seeded rules, all enabled |
| AUTO-01 | (bonus) | Automation fires on task completion | **PASS** | **Cycle 2**: Task completion auto-created "Follow-up: Gather supporting documents" |
| DOC-01 | 1 | View templates | PASS | 12 seeded templates, categorized |

## Scorecard

| Track | Total | Pass | Fail | Partial | N/A | Not Tested |
|-------|-------|------|------|---------|-----|------------|
| Auth (pre-flight) | 4 | 4 | 0 | 0 | 0 | 0 |
| NAV-01 | 16 | 15 | 0 | 0 | 1 | 0 |
| CUST-01 | 5 | 3 | 0 | 0 | 0 | 2 |
| CUST-02 | 10 | 2 | 0 | 1 | 0 | 7 |
| PROJ-01 | 7 | 2 | 0 | 0 | 0 | 5 |
| PROJ-02 | 7 | 5 | 0 | 0 | 0 | 2 |
| PROJ-03 | 7 | 0 | 0 | 0 | 0 | 7 |
| SET-02 | 5 | 1 | 0 | 1 | 0 | 3 |
| SET-03 | 3 | 1 | 0 | 0 | 0 | 2 |
| AUTO-01 | 5 | 2 | 0 | 0 | 0 | 3 |
| DOC-01 | 4 | 1 | 0 | 0 | 0 | 3 |
| **Total** | **73** | **36** | **0** | **2** | **1** | **34** |

**Pass Rate (tested)**: 36/38 = 95%
**Coverage**: 38/73 = 52%

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-23T00:00Z | Setup | Keycloak QA cycle initialized on branch bugfix_cycle_kc_2026-03-23. Scenario: qa/testplan/regression-test-suite.md. All 5 dev stack services confirmed UP. Previous regression cycle (E2E mock-auth, 2026-03-19) was ALL_DAYS_COMPLETE (88 PASS, 1 FAIL/WONT_FIX, 1 PARTIAL). |
| 2026-03-23T00:01Z | Setup | Existing state: 1 org "Thornton & Associates" (alias=thornton-associates), 2 members (Thandi Thornton owner, Bob Ndlovu member), 1 tenant schema (tenant_4a171ca30392). Platform admin: padmin@docteams.local. |
| 2026-03-23T19:52Z | QA Agent | Cycle 1 started. Keycloak auth flow tested (required admin API password reset for thandi/bob). |
| 2026-03-23T20:05Z | QA Agent | Cycle 1 complete. 8 tracks tested (NAV-01, CUST-01, CUST-02, PROJ-01, PROJ-02, SET-02, AUTO-01, DOC-01). 27 PASS, 1 FAIL (Settings sidebar crash), 1 PARTIAL (dialog overflow). 3 bugs logged (BUG-KC-001 HIGH, BUG-KC-002 MEDIUM, BUG-KC-003 MEDIUM). |
| 2026-03-23T21:30Z | Product Agent | Triaged 3 bugs from cycle 1. All 3 moved OPEN -> SPEC_READY. BUG-KC-001 (HIGH, S effort): sidebar link targets redirect page, fix is single-line href change. BUG-KC-002 (MEDIUM, S effort): dialog overflow, fix is CSS max-height + scroll. BUG-KC-003 (MEDIUM, M effort): provisioning flow relies on email invite, fix needs backend method + bootstrap script. No cascading bugs detected -- none block downstream tests beyond their own track. |
| 2026-03-23T22:15Z | Dev Agent | BUG-KC-001 FIXED. Changed Settings sidebar href from `/settings` to `/settings/general` in `frontend/lib/nav-items.ts`. Build passes, 1692/1692 tests pass. PR #827 merged (squash) into bugfix_cycle_kc_2026-03-23. |
| 2026-03-23T22:30Z | Dev Agent | BUG-KC-002 FIXED. Added `max-h-[60vh] overflow-y-auto` to content wrapper div in `frontend/components/customers/create-customer-dialog.tsx`. Build passes, 1692/1692 tests pass. PR #828 merged (squash) into bugfix_cycle_kc_2026-03-23. |
| 2026-03-23T22:45Z | Dev Agent | BUG-KC-003 FIXED. Added `setUserPassword()` to `KeycloakProvisioningClient` (PUT /users/{id}/reset-password). Called in `AccessRequestApprovalService.approve()` after invite, gated by `app.keycloak.set-default-password` (enabled in keycloak profile). Bootstrap script updated with step 7/7 to backfill passwords for existing org members. Backend compiles, `AccessRequestApprovalServiceTest` passes (8/8). Pre-existing failure in `DashboardProjectIntegrationTest` unrelated. PR #829 merged (squash) into bugfix_cycle_kc_2026-03-23. |
| 2026-03-23T23:15Z | QA Agent | Cycle 2 started. Focus: verify 3 bug fixes + expand coverage of NOT_TESTED items. |
| 2026-03-23T23:45Z | QA Agent | Cycle 2 complete. All 3 bugs verified (BUG-KC-001 VERIFIED, BUG-KC-002 VERIFIED, BUG-KC-003 VERIFIED-BY-CODE). 9 new checkpoints tested: CUST-01.3 (edit name PASS), CUST-02.3 (checklist PARTIAL), PROJ-02.3 (status OPEN->IP PASS), PROJ-02.4 (IP->DONE PASS), PROJ-02.5 (reopen PASS), PROJ-02.7 (assign PASS), SET-02.2 (add rate PARTIAL), SET-03.1 (tax rates PASS), AUTO-01 bonus (automation fires PASS). Coverage 46% -> 52%. Pass rate 95%. 0 new bugs. |
