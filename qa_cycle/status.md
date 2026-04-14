# QA Cycle Status — Consulting Agency 90-Day Demo (Fresh Tenant, Keycloak) — 2026-04-14

## Current State

- **QA Position**: Day 0 — Not started
- **Cycle**: 0
- **Dev Stack**: Not running
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_consulting_2026-04-14`
- **Scenario**: `qa/testplan/demos/consulting-agency-90day-keycloak.md`
- **Focus**: Fresh tenant run — full onboarding through 90-day consulting agency lifecycle.
- **Auth Mode**: Keycloak (real OIDC)
- **ALL_DAYS_COMPLETE**: false

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend (kc mode) | http://localhost:3000 | DOWN |
| Backend (local+keycloak profile) | http://localhost:8080 | DOWN |
| Gateway (BFF) | http://localhost:8443 | DOWN |
| Portal | http://localhost:3002 | DOWN |
| Keycloak | http://localhost:8180 | DOWN |
| Mailpit UI | http://localhost:8025 | DOWN |
| Postgres (docteams) | localhost:5432 | DOWN |

## Gap Tracker

| GAP_ID | Day / Checkpoint | Severity | Status | Summary | Owner | Retries |
|--------|------------------|----------|--------|---------|-------|---------|
| (none) | | | | | | |

## Legend

- **Status**: OPEN -> SPEC_READY -> FIXED -> VERIFIED | REOPENED | STUCK | WONT_FIX | RESOLVED
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-14 — Cycle initialized for consulting agency 90-day demo.
