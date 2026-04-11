# QA Cycle Status — Legal Onboarding (Keycloak) — 2026-04-11

## Current State

- **QA Position**: Session 3 — resume from step 3.13 (FICA checklist tick-through for Sipho, then matter + engagement letter)
- **Cycle**: 2 (QA turn 1 complete)
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_kc_2026-04-11`
- **Scenario**: `qa/testplan/legal-onboarding-keycloak.md`
- **Focus**: Legal vertical onboarding via real Keycloak OIDC. End-to-end: access request → admin approval → KC registration → plan upgrade → legal-za profile → team invites → 3 client onboardings (Litigation, Deceased Estate, RAF) → engagement letters, trust account, court calendar, adverse parties, activity/audit sign-off.
- **Auth Mode**: Keycloak (real OIDC, no mock IDP)

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend (kc mode) | http://localhost:3000 | UP |
| Backend (local, local+keycloak profile) | http://localhost:8080 | UP |
| Gateway (BFF) | http://localhost:8443 | UP |
| Portal | http://localhost:3002 | UP |
| Keycloak | http://localhost:8180 | UP |
| Mailpit UI | http://localhost:8025 | UP |
| Postgres (docteams) | localhost:5432 | UP |

## Test Plan Structure

| Session | Focus | Status |
|---------|-------|--------|
| Session 0 | Stack startup & sanity | PASS_WITH_NOTES (GAP-S0-01 non-blocking) |
| Session 1 | Firm onboarding via product (access request, admin approval, KC registration) | PASS_WITH_NOTES (GAP-S1-01 MCP click workaround; GAP-S1-02 brand inconsistency) |
| Session 2 | Plan upgrade, team invites, legal-za activation, rates & tax | PASS_WITH_NOTES (21/27 PASS; 3 FAIL on plan upgrade path — GAP-S2-01; 3 PARTIAL on terminology/UX — GAP-S2-02/03/04) |
| Session 3 | Litigation customer onboarding (conflict, FICA, matter, engagement letter) | PARTIAL (13/~30 steps; Sipho in ONBOARDING with auto-populated FICA checklist; stopped at clean boundary — resume at 3.13) |
| Session 4 | Estates customer onboarding (trust setup, trust client, FICA, matter, first deposit) | NOT_STARTED |
| Session 5 | RAF plaintiff onboarding (conflict, FICA, RAF matter, contingency letter, court date, adverse party, first time entry) | NOT_STARTED |
| Session 6 | Cross-cutting verification & sign-off (activity feed, terminology, backend-state) | NOT_STARTED |

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Notes |
|----|---------|----------|--------|-------|----|-------|
| GAP-S0-01 | Leftover Keycloak users and tenant schemas from prior cycles (non-blocking — no collision with `mathebula-test.local` test data) | LOW | OPEN | — | — | `qa_cycle/checkpoint-results/session-0.md` — 4 stale KC users (alice/bob/qatest/thandi @ prior domains), 3 stale tenant schemas. Recommend infra teardown between cycles. |
| GAP-S1-01 | Playwright MCP `browser_click` silently fails on some buttons (Radix dialogs, Keycloak forms, Next.js dev overlay) | MEDIUM | OPEN | — | — | `qa_cycle/checkpoint-results/session-1.md` — QA automation friction, not a product bug. Workaround: `browser_evaluate(() => el.click())`. Impacts QA agent throughput; recommend adding as QA lesson. |
| GAP-S1-02 | Brand-name inconsistency: landing page uses "Kazi" but /request-access, Keycloak theme, emails, and sidebar still say "DocTeams" | LOW | OPEN | — | — | `qa_cycle/checkpoint-results/session-1.md` — user-facing confusion. Needs product decision: complete rebrand or revert. |
| GAP-S2-01 | Billing page has no plan/upgrade UI in Keycloak mode — only shows "Trial / Manual / Managed Account". No self-service Upgrade to Pro path, no `/api/billing/subscription` route. Scenario steps 2.1–2.3 cannot execute as written. | MEDIUM | OPEN | — | — | `qa_cycle/checkpoint-results/session-2.md`. Non-blocking for Sessions 3–5 because legal-za features render without a Pro gate. Needs product decision: is this a Clerk-era leftover path? |
| GAP-S2-02 | Legal terminology incomplete in Settings sidebar + several Settings pages (Project Templates, Custom Fields, Clients empty state, "Back to Customers" link, "Customer Readiness" widget, "Getting started with DocTeams" helper card) | LOW | OPEN | — | — | `qa_cycle/checkpoint-results/session-2.md` and `session-3.md`. Sidebar settings section still uses "Project Templates / Project Naming", Custom Fields subtitle mentions "projects, tasks, customers, invoices". Recommend sweep of `app/**/customers/*` and `app/**/settings/**/*` for legacy strings. |
| GAP-S2-03 | Add Rate dialog on `/settings/rates` Cost Rates tab defaults to Billing Rate — confusing UX; naive save yields "A billing rate already exists for this period" | LOW | OPEN | — | — | `qa_cycle/checkpoint-results/session-2.md`. Dialog should default `Rate Type` to match the currently active tab. |
| GAP-S2-04 | Dashboard first-run helper card titled "Getting started with DocTeams" — conflicts with Kazi rebrand (subset of GAP-S1-02) | LOW | OPEN | — | — | `qa_cycle/checkpoint-results/session-2.md`. |
| GAP-S3-01 | Client-detail "Change Status" Radix DropdownMenu requires pointerdown+pointerup before `.click()` to open (extension of GAP-S1-01). Confirmation AlertDialog then needs `browser_click` with ref. | LOW | OPEN | — | — | `qa_cycle/checkpoint-results/session-3.md`. Pure QA automation friction, not a product defect. Recommend documenting as a QA lesson. |
| GAP-S3-02 | Terminology leak in Clients empty state ("Customers represent the organisations…"), client detail "Back to Customers" link, and "Customer Readiness" widget. Subset of GAP-S2-02, captured separately because the customer-facing text appears at the exact moment a new user is creating their first client. | LOW | OPEN | — | — | `qa_cycle/checkpoint-results/session-3.md`. |

## Log

- 2026-04-11 — Branch `bugfix_cycle_kc_2026-04-11` created from `main`. Previous E2E mock-auth cycle state archived to `qa_cycle/_archive_2026-04-06_e2e/`. Fresh status.md scaffolded for Keycloak legal onboarding scenario.
- 2026-04-11 — Infra: dev stack up. Docker infra started (postgres, localstack, mailpit, keycloak); keycloak-bootstrap.sh ran successfully (padmin + org_role backfill); backend (30s) + gateway (6s) started via svc.sh. All 4 services RUNNING + HEALTHY. Health checks: backend/gateway UP, frontend HTTP 200, mailpit v1.29.2 responding. Ready for QA Session 0.
- 2026-04-11 — QA Cycle 1 turn 1: **Session 0 PASS_WITH_NOTES** (all 4 services healthy; stale KC users & tenant schemas present but non-colliding — GAP-S0-01 LOW). **Session 1 PASS_WITH_NOTES** (full onboarding walkthrough succeeded: access request → OTP verify → padmin approval → Keycloak org + invite → registration → Thandi logged into `/org/mathebula-partners/dashboard` with legal terminology visible in sidebar/KPIs). Two gaps filed: GAP-S1-01 MEDIUM (Playwright MCP click reliability — required `browser_evaluate` workaround on ~5 buttons) and GAP-S1-02 LOW (brand inconsistency between "Kazi" marketing page and "DocTeams" elsewhere). **Next**: Session 2 — plan upgrade, legal-za verification, rates, team invites for Bob + Carol. Thandi is currently logged in at /org/mathebula-partners/dashboard.
- 2026-04-11 — QA Cycle 2 turn 1: **Session 2 PASS_WITH_NOTES** (21/27 steps PASS, 3 FAIL on plan-upgrade path — GAP-S2-01, 3 PARTIAL for terminology/UX — GAP-S2-02/03/04). Confirmed legal-za profile is fully active (sidebar terms, 4 matter templates, custom field group "SA Legal — Matter Details", 22-clause legal-za clause pack incl. FICA Consent / POPIA / Trust Account Deposits / Fees Contingency, ZAR default, VAT 15% seeded as "Standard", Bob+Carol invited via real KC + registered via invite tokens, all 3 users have billing + cost rates). Billing page blocker filed as GAP-S2-01 (non-blocking for remaining sessions). **Session 3 PARTIAL** (13/~30 steps): logged in as Bob, ran conflict check for "Sipho Dlamini" (No Conflict), created client `Sipho Dlamini` (`4119d161-39a7-40b2-a462-d1869d9a1f2b`) as INDIVIDUAL with SA Legal custom field group, transitioned PROSPECT → ONBOARDING, confirmed the **Legal Client Onboarding** FICA checklist auto-populated with **11 items (8 required)** including Proof of Identity / Proof of Address / Company Registration Docs etc. Stopped at clean boundary before ticking checklist items due to budget (~70 MCP calls). New gaps: GAP-S3-01 (Radix DropdownMenu pointer-event workaround) and GAP-S3-02 (terminology leak in Clients empty state / back-link / Customer Readiness). **Next QA turn**: resume Session 3 from step 3.13 — complete FICA checklist, auto-transition to ACTIVE, create Litigation matter with 9 template action items, send Hourly engagement letter. Sipho already exists — no re-setup needed.
