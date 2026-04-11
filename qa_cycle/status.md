# QA Cycle Status — Legal Onboarding (Keycloak) — 2026-04-11

## Current State

- **QA Position**: NOT_STARTED — Session 0 (Stack startup & sanity)
- **Cycle**: 0 (pre-flight)
- **Dev Stack**: UNKNOWN — needs verification
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_kc_2026-04-11`
- **Scenario**: `qa/testplan/legal-onboarding-keycloak.md`
- **Focus**: Legal vertical onboarding via real Keycloak OIDC. End-to-end: access request → admin approval → KC registration → plan upgrade → legal-za profile → team invites → 3 client onboardings (Litigation, Deceased Estate, RAF) → engagement letters, trust account, court calendar, adverse parties, activity/audit sign-off.
- **Auth Mode**: Keycloak (real OIDC, no mock IDP)

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend (kc mode) | http://localhost:3000 | UNKNOWN |
| Backend (local, local+keycloak profile) | http://localhost:8080 | UNKNOWN |
| Gateway (BFF) | http://localhost:8443 | UNKNOWN |
| Portal | http://localhost:3002 | UNKNOWN |
| Keycloak | http://localhost:8180 | UNKNOWN |
| Mailpit UI | http://localhost:8025 | UNKNOWN |
| Postgres (docteams) | localhost:5432 | UNKNOWN |

## Test Plan Structure

| Session | Focus | Status |
|---------|-------|--------|
| Session 0 | Stack startup & sanity | NOT_STARTED |
| Session 1 | Firm onboarding via product (access request, admin approval, KC registration) | NOT_STARTED |
| Session 2 | Plan upgrade, team invites, legal-za activation, rates & tax | NOT_STARTED |
| Session 3 | Litigation customer onboarding (conflict, FICA, matter, engagement letter) | NOT_STARTED |
| Session 4 | Estates customer onboarding (trust setup, trust client, FICA, matter, first deposit) | NOT_STARTED |
| Session 5 | RAF plaintiff onboarding (conflict, FICA, RAF matter, contingency letter, court date, adverse party, first time entry) | NOT_STARTED |
| Session 6 | Cross-cutting verification & sign-off (activity feed, terminology, backend-state) | NOT_STARTED |

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Notes |
|----|---------|----------|--------|-------|----|-------|

_No gaps yet — cycle not started._

## Log

- 2026-04-11 — Branch `bugfix_cycle_kc_2026-04-11` created from `main`. Previous E2E mock-auth cycle state archived to `qa_cycle/_archive_2026-04-06_e2e/`. Fresh status.md scaffolded for Keycloak legal onboarding scenario.
