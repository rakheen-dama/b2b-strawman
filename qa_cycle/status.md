# QA Cycle Status — Accounting-ZA 90-Day Demo Walkthrough (Keycloak) — 2026-04-14

## Current State

- **QA Position**: Day 0 Phase F (custom field detail checks deferred; Phases A-E, H sidebar, I billing complete)
- **Cycle**: 1
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_2026-04-14`
- **Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md`
- **Focus**: Fresh 90-day accounting-ZA demo walkthrough against the real Keycloak dev stack. Exercises the accounting vertical profile (terminology, FICA packs, document templates, time/billing/profitability) end-to-end.
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
| GAP-D0-01 | Day 0 / 0.11 | LOW | OPEN | Access requests page tab switching broken -- Pending tab stays selected regardless of which tab is clicked | Dev | 0 |
| GAP-D0-02 | Day 0 / 0.30 | LOW | OPEN | Rate cards not pre-seeded from accounting-za vertical profile; all members show "Not set" | Dev | 0 |

## Legend

- **Status**: OPEN → SPEC_READY → FIXED → VERIFIED | REOPENED | STUCK | WONT_FIX
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-14 — Cycle initialized. Prior cycle (2026-04-13, legal-za) archived to `qa_cycle/_archive_2026-04-13_legal-90day-kc-v2/`. Branch `bugfix_cycle_2026-04-14` created from main. Fresh accounting-ZA scenario run from Day 0.
- 2026-04-14 — **Infra Agent: cleanup & startup complete.** Deleted Keycloak org "Mathebula & Partners" (legal-za leftover). Dropped tenant schema `tenant_5039f2d497cf` (100 tables). Truncated 6 public tables (access_requests, org_schema_mapping, organizations, processed_webhooks, subscription_payments, subscriptions). Cleared Mailpit inbox. Restarted backend + gateway. All 4 services healthy (backend:8080, gateway:8443, frontend:3000, portal:3002). Docker infra up (Postgres, Keycloak, Mailpit, LocalStack). `padmin@docteams.local` verified present and enabled. Dev stack marked READY.
- 2026-04-14 — **QA Agent: Day 0 Phases A-E, H (sidebar), I (billing) executed.** 42 checkpoints: 36 PASS, 2 PARTIAL, 8 DEFERRED (field promotion detail, templates, automations, modules). 0 FAIL. 2 LOW gaps found (GAP-D0-01: tab switching, GAP-D0-02: rate pre-seeding). Org "Thornton & Associates" created with accounting-za profile, 3 Keycloak users registered (Thandi/Owner, Bob/Admin, Carol/Member), ZAR currency, VAT 15%, brand colour #1B5E20 set. No tier upgrade UI found. No legal module leakage. Screenshots captured.
