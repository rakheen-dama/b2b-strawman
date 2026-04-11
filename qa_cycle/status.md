# QA Cycle Status — Legal Onboarding (Keycloak) — 2026-04-11

## Current State

- **QA Position**: Session 2 — Plan upgrade, team invites, legal-za activation (NOT_STARTED)
- **Cycle**: 1 (QA turn 1 complete)
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
| Session 2 | Plan upgrade, team invites, legal-za activation, rates & tax | NOT_STARTED |
| Session 3 | Litigation customer onboarding (conflict, FICA, matter, engagement letter) | NOT_STARTED |
| Session 4 | Estates customer onboarding (trust setup, trust client, FICA, matter, first deposit) | NOT_STARTED |
| Session 5 | RAF plaintiff onboarding (conflict, FICA, RAF matter, contingency letter, court date, adverse party, first time entry) | NOT_STARTED |
| Session 6 | Cross-cutting verification & sign-off (activity feed, terminology, backend-state) | NOT_STARTED |

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Notes |
|----|---------|----------|--------|-------|----|-------|
| GAP-S0-01 | Leftover Keycloak users and tenant schemas from prior cycles (non-blocking — no collision with `mathebula-test.local` test data) | LOW | OPEN | — | — | `qa_cycle/checkpoint-results/session-0.md` — 4 stale KC users (alice/bob/qatest/thandi @ prior domains), 3 stale tenant schemas. Recommend infra teardown between cycles. |
| GAP-S1-01 | Playwright MCP `browser_click` silently fails on some buttons (Radix dialogs, Keycloak forms, Next.js dev overlay) | MEDIUM | OPEN | — | — | `qa_cycle/checkpoint-results/session-1.md` — QA automation friction, not a product bug. Workaround: `browser_evaluate(() => el.click())`. Impacts QA agent throughput; recommend adding as QA lesson. |
| GAP-S1-02 | Brand-name inconsistency: landing page uses "Kazi" but /request-access, Keycloak theme, emails, and sidebar still say "DocTeams" | LOW | OPEN | — | — | `qa_cycle/checkpoint-results/session-1.md` — user-facing confusion. Needs product decision: complete rebrand or revert. |

## Log

- 2026-04-11 — Branch `bugfix_cycle_kc_2026-04-11` created from `main`. Previous E2E mock-auth cycle state archived to `qa_cycle/_archive_2026-04-06_e2e/`. Fresh status.md scaffolded for Keycloak legal onboarding scenario.
- 2026-04-11 — Infra: dev stack up. Docker infra started (postgres, localstack, mailpit, keycloak); keycloak-bootstrap.sh ran successfully (padmin + org_role backfill); backend (30s) + gateway (6s) started via svc.sh. All 4 services RUNNING + HEALTHY. Health checks: backend/gateway UP, frontend HTTP 200, mailpit v1.29.2 responding. Ready for QA Session 0.
- 2026-04-11 — QA Cycle 1 turn 1: **Session 0 PASS_WITH_NOTES** (all 4 services healthy; stale KC users & tenant schemas present but non-colliding — GAP-S0-01 LOW). **Session 1 PASS_WITH_NOTES** (full onboarding walkthrough succeeded: access request → OTP verify → padmin approval → Keycloak org + invite → registration → Thandi logged into `/org/mathebula-partners/dashboard` with legal terminology visible in sidebar/KPIs). Two gaps filed: GAP-S1-01 MEDIUM (Playwright MCP click reliability — required `browser_evaluate` workaround on ~5 buttons) and GAP-S1-02 LOW (brand inconsistency between "Kazi" marketing page and "DocTeams" elsewhere). **Next**: Session 2 — plan upgrade, legal-za verification, rates, team invites for Bob + Carol. Thandi is currently logged in at /org/mathebula-partners/dashboard.
