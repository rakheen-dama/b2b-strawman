# QA Cycle Status — Legal-ZA 90-Day Demo Walkthrough (Keycloak) — 2026-04-12

## Current State

- **QA Position**: Cycle 1 — not yet started. Infra verification pending, then QA Agent runs Day 0 (pre-demo sanity + access request).
- **Cycle**: 1 (fresh)
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_demo_legal_2026-04-12`
- **Scenario**: `qa/testplan/demos/legal-za-90day-keycloak.md`
- **Focus**: 90-day legal-ZA demo walkthrough executed end-to-end against the real Keycloak dev stack. Goal is to prove the scripted customer demo runs clean — access request → admin approval → KC registration → plan upgrade → legal-za profile → team invites → 3 client lifecycles (Litigation, Deceased Estate, RAF) → engagement letters, trust accounting, court calendar, adverse parties, activity feed, audit sign-off. Any sharp edges that break the demo narrative become fix-spec targets.
- **Auth Mode**: Keycloak (real OIDC — platform admin `padmin@docteams.local` is pre-seeded via `keycloak-bootstrap.sh`; all other users come through the onboarding flow).

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend (kc mode) | http://localhost:3000 | UP |
| Backend (local+keycloak profile) | http://localhost:8080 | UP |
| Gateway (BFF) | http://localhost:8443 | UP |
| Portal | http://localhost:3002 | UP |
| Keycloak | http://localhost:8180 | UP |
| Mailpit UI | http://localhost:8025 | UP |
| Postgres (docteams) | localhost:5432 | UP |

## Gap Tracker

| GAP_ID | Day / Checkpoint | Severity | Status | Summary | Owner | Retries |
|--------|------------------|----------|--------|---------|-------|---------|

_Empty — gaps will be added as the QA Agent discovers them._

## Legend

- **Status**: OPEN → SPEC_READY → FIXED → VERIFIED | REOPENED | STUCK | WONT_FIX
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-12 — Cycle 1 initialized. Prior cycle (KC 2026-04-12, legal-onboarding scenario) closed as ALL_DAYS_COMPLETE and archived to `qa_cycle/_archive_2026-04-12_legal-onboarding-kc/`. Branch `bugfix_cycle_demo_legal_2026-04-12` created from main. Scenario: `qa/testplan/demos/legal-za-90day-keycloak.md`.
- 2026-04-12 — Infra Turn 1: Verified KC dev stack health — all 4 Docker containers (b2b-postgres, b2b-keycloak, b2b-localstack, b2b-mailpit) reporting healthy (Up 17 hours). Keycloak `docteams` realm responds 200. svc.sh reports backend/gateway/frontend/portal all running and healthy (PIDs 55326/55489/90632/90679). Endpoint spot-checks: backend :8080 /actuator/health UP, gateway :8443 /actuator/health UP, frontend :3000 HTTP 200, portal :3002 HTTP 200, Mailpit API returning messages. No services required starting. Dev Stack READY for QA walkthrough.
