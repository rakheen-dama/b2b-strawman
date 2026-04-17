# QA Cycle Status — Legal ZA 90-Day Demo (Fresh Tenant, Keycloak) — 2026-04-17

## Current State

- **ALL_DAYS_COMPLETE**: false
- **QA Position**: Day 0 — 0.1 (not started)
- **Cycle**: 1
- **Dev Stack**: UNKNOWN (needs Infra Agent verification)
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_legal_2026-04-17`
- **Scenario**: `qa/testplan/demos/legal-za-90day-keycloak.md`
- **Focus**: Fresh tenant run — full onboarding through 90-day law firm lifecycle. Re-run with v3 profile set (post-consulting cycle) to validate legal-za vertical end-to-end.
- **Auth Mode**: Keycloak (real OIDC)

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend (kc mode) | http://localhost:3000 | UNKNOWN |
| Backend (local+keycloak profile) | http://localhost:8080 | UNKNOWN |
| Gateway (BFF) | http://localhost:8443 | UNKNOWN |
| Portal | http://localhost:3002 | UNKNOWN |
| Keycloak | http://localhost:8180 | UNKNOWN |
| Mailpit UI | http://localhost:8025 | UNKNOWN |
| Postgres (docteams) | localhost:5432 | UNKNOWN |

## Carry-Forward Watch List (from prior legal-za archives)

These are gaps logged during `_archive_2026-04-13_legal-90day-kc-v2` and earlier runs. If they reproduce, log fresh GAP IDs referencing the archive:

- **Trust accounting module not gated correctly** (prior runs flagged direct URL exposure pre-fix). Now expected to show "Module Not Available" when disabled.
- **Matter template custom-field defaults** (analogous to consulting GAP-C-09) — template fields may not auto-fill on project creation.
- **Retention clock / matter closure gating** (ADRs 247-249 recently added) — new code paths; treat as first-run.
- **Statement of account template + acceptance-eligible manifest flag** (ADRs 250-251 recently added) — new code paths; validate end-to-end.

## Gap Tracker

| GAP_ID | Day / Checkpoint | Severity | Status | Summary | Owner | Retries |
|--------|------------------|----------|--------|---------|-------|---------|
| _no gaps logged yet_ | — | — | — | — | — | — |

## Legend

- **Status**: OPEN -> SPEC_READY -> FIXED -> VERIFIED | REOPENED | STUCK | WONT_FIX | RESOLVED
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-17 — Cycle initialized. Prior consulting-za cycle (ALL_DAYS_COMPLETE 14:32 SAST same day) archived to `_archive_2026-04-17_consulting-complete/`. Fresh branch `bugfix_cycle_legal_2026-04-17` created from `main`. Scenario: `qa/testplan/demos/legal-za-90day-keycloak.md`. First action: Infra Agent to verify stack health and run `keycloak-bootstrap.sh` if needed.
