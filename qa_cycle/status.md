# QA Cycle Status — Accounting-ZA 90-Day Demo (Fresh Tenant) — 2026-04-14-v2

## Current State

- **QA Position**: Day 0 (not started)
- **Cycle**: 0
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_2026-04-14-v2`
- **Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md`
- **Focus**: Fresh tenant run on updated main (all PR #1030-1036 fixes merged). Monitoring console errors and UI error indicators throughout. Goal: verify all fixes work end-to-end on a clean tenant.
- **Auth Mode**: Keycloak (real OIDC)

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

## Legend

- **Status**: OPEN → SPEC_READY → FIXED → VERIFIED | REOPENED | STUCK | WONT_FIX
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-14 — Cycle v2 initialized. Fresh tenant run on main after PR #1036 merge (includes all fixes from v1 cycle). Branch `bugfix_cycle_2026-04-14-v2` created.
- 2026-04-14 — Infra Agent: Dev stack started. Keycloak bootstrapped. Cleaned all previous data (deleted thornton-associates org, 3 users, dropped tenant_4a171ca30392 schema, truncated 6 public tables, cleared Mailpit). All services UP and healthy. Database is pristine for fresh tenant run.
