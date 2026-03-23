# QA Cycle Status — Regression Test Suite / Keycloak Dev Stack (2026-03-23)

## Current State

- **QA Position**: CYCLE_1_COMPLETE — NAV-01, CUST-01/02, PROJ-01/02, SET-02, AUTO-01, DOC-01 tested
- **Cycle**: 1
- **Dev Stack**: READY — All 5 services running (Backend:8080, Frontend:3000, Gateway:8443, Keycloak:8180, Mailpit:8025)
- **Branch**: `bugfix_cycle_kc_2026-03-23`
- **Scenario**: `qa/testplan/regression-test-suite.md`
- **Focus**: Full regression test suite against Keycloak dev stack (real OIDC auth, gateway BFF)
- **Auth Mode**: Keycloak (not mock-auth). Login via Keycloak redirect flow.
- **Results File**: `qa_cycle/checkpoint-results/kc-regression-cycle1.md`

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
| BUG-KC-001 | Settings page crashes on client-side navigation (sidebar click) | HIGH | FIXED | Dev Agent | [#827](https://github.com/rakheen-dama/b2b-strawman/pull/827) | NAV-01 | Fixed: sidebar Settings href changed from `/settings` to `/settings/general` in `nav-items.ts`. PR #827 merged. Needs re-test in cycle 2. |
| BUG-KC-002 | Create Customer Step 2 dialog footer buttons inaccessible (overflow) | MEDIUM | SPEC_READY | — | — | CUST-01 | Root cause: dialog content div has no max-height or overflow scroll. Fix: add `max-h-[60vh] overflow-y-auto` to content wrapper in CreateCustomerDialog. Spec: `qa_cycle/fix-specs/BUG-KC-002.md`. |
| BUG-KC-003 | Keycloak user passwords not set during provisioning | MEDIUM | SPEC_READY | — | — | Auth | Root cause: provisioning uses KC invite-user endpoint (email flow), but local dev users never complete registration. Fix: add `setUserPassword` to provisioning client + bootstrap script backfill. Spec: `qa_cycle/fix-specs/BUG-KC-003.md`. |

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
| NAV-01 | 16 | Settings (sidebar) | **FAIL** | BUG-KC-001: client-side crash |
| CUST-01 | 1 | Create customer | PASS | "Kgosi Holdings QA" created |
| CUST-01 | 2 | Custom fields Step 2 | PARTIAL | BUG-KC-002: overflow |
| CUST-02 | 1 | Defaults to PROSPECT | PASS | Badge shows "Prospect" |
| CUST-02 | 2 | PROSPECT -> ONBOARDING | PASS | Checklist (0/4) appeared |
| PROJ-01 | 1 | Create project with customer | PASS | "Annual Tax Return 2026" |
| PROJ-01 | 4 | Project detail tabs | PASS | 15 tabs rendered |
| PROJ-02 | 1 | Create task | PASS | "Gather supporting documents" |
| SET-02 | 1 | View billing rates | PASS | Via direct URL, ZAR, 2 members |
| AUTO-01 | 1 | View automation rules | PASS | 11 seeded rules, all enabled |
| DOC-01 | 1 | View templates | PASS | 12 seeded templates, categorized |

## Scorecard

| Track | Tested | Pass | Fail | Partial | Not Tested |
|-------|--------|------|------|---------|------------|
| Auth (pre-flight) | 4 | 4 | 0 | 0 | 0 |
| NAV-01 | 16 | 14 | 1 | 0 | 1 |
| CUST-01 | 5 | 1 | 0 | 1 | 3 |
| CUST-02 | 10 | 2 | 0 | 0 | 8 |
| PROJ-01 | 7 | 2 | 0 | 0 | 5 |
| PROJ-02 | 7 | 1 | 0 | 0 | 6 |
| SET-02 | 5 | 1 | 0 | 0 | 4 |
| AUTO-01 | 5 | 1 | 0 | 0 | 4 |
| DOC-01 | 4 | 1 | 0 | 0 | 3 |
| **Total** | **63** | **27** | **1** | **1** | **34** |

**Pass Rate (tested)**: 27/29 = 93%
**Coverage**: 29/63 = 46%

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-23T00:00Z | Setup | Keycloak QA cycle initialized on branch bugfix_cycle_kc_2026-03-23. Scenario: qa/testplan/regression-test-suite.md. All 5 dev stack services confirmed UP. Previous regression cycle (E2E mock-auth, 2026-03-19) was ALL_DAYS_COMPLETE (88 PASS, 1 FAIL/WONT_FIX, 1 PARTIAL). |
| 2026-03-23T00:01Z | Setup | Existing state: 1 org "Thornton & Associates" (alias=thornton-associates), 2 members (Thandi Thornton owner, Bob Ndlovu member), 1 tenant schema (tenant_4a171ca30392). Platform admin: padmin@docteams.local. |
| 2026-03-23T19:52Z | QA Agent | Cycle 1 started. Keycloak auth flow tested (required admin API password reset for thandi/bob). |
| 2026-03-23T20:05Z | QA Agent | Cycle 1 complete. 8 tracks tested (NAV-01, CUST-01, CUST-02, PROJ-01, PROJ-02, SET-02, AUTO-01, DOC-01). 27 PASS, 1 FAIL (Settings sidebar crash), 1 PARTIAL (dialog overflow). 3 bugs logged (BUG-KC-001 HIGH, BUG-KC-002 MEDIUM, BUG-KC-003 MEDIUM). |
| 2026-03-23T21:30Z | Product Agent | Triaged 3 bugs from cycle 1. All 3 moved OPEN -> SPEC_READY. BUG-KC-001 (HIGH, S effort): sidebar link targets redirect page, fix is single-line href change. BUG-KC-002 (MEDIUM, S effort): dialog overflow, fix is CSS max-height + scroll. BUG-KC-003 (MEDIUM, M effort): provisioning flow relies on email invite, fix needs backend method + bootstrap script. No cascading bugs detected -- none block downstream tests beyond their own track. |
| 2026-03-23T22:15Z | Dev Agent | BUG-KC-001 FIXED. Changed Settings sidebar href from `/settings` to `/settings/general` in `frontend/lib/nav-items.ts`. Build passes, 1692/1692 tests pass. PR #827 merged (squash) into bugfix_cycle_kc_2026-03-23. |
