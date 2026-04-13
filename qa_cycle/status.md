# QA Cycle Status — Legal-ZA 90-Day Demo Walkthrough (Keycloak) — 2026-04-13

## Current State

- **QA Position**: Day 0, Checkpoint 0 (not started)
- **Cycle**: 1
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_2026-04-13`
- **Scenario**: `qa/testplan/demos/legal-za-90day-keycloak.md`
- **Focus**: Fresh 90-day legal-ZA demo walkthrough against the real Keycloak dev stack. Prior cycle (2026-04-12) ran all 90 days and identified 28 gaps. Many fixes merged to main via PRs #1012–#1025. This cycle re-verifies the full scenario from Day 0 on current main to confirm fixes and discover regressions.
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
| (none yet) | | | | | | |

## Legend

- **Status**: OPEN → SPEC_READY → FIXED → VERIFIED | REOPENED | STUCK | WONT_FIX
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-13 — Cycle 1 initialized. Prior cycle (2026-04-12) archived to `qa_cycle/_archive_2026-04-12_legal-90day-kc/`. Branch `bugfix_cycle_2026-04-13` created from main (includes all fixes from PRs #1012–#1025). Fresh scenario run from Day 0.
- 2026-04-13 — **Infra Turn 1**: Stack verified, all 7 services UP. Demo cleanup completed: 1 KC org deleted (Mathebula & Partners), 1 tenant schema dropped (tenant_5039f2d497cf), DB tables truncated, Mailpit cleared. KC has only padmin@docteams.local, 0 orgs. Dev Stack set to READY.
