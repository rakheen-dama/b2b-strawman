# QA Cycle Status — Consulting Agency 90-Day Demo (Fresh Tenant, Keycloak) — 2026-04-14

## Current State

- **QA Position**: Day 36 — checkpoint 36.1 (invoicing phase pending)
- **Cycle**: 1
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_consulting_2026-04-14`
- **Scenario**: `qa/testplan/demos/consulting-agency-90day-keycloak.md`
- **Focus**: Fresh tenant run — full onboarding through 90-day consulting agency lifecycle.
- **Auth Mode**: Keycloak (real OIDC)
- **ALL_DAYS_COMPLETE**: false

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
| GAP-C-01 | D0 / 0.10 | LOW | OPEN | `vertical_profile` not auto-assigned for "Marketing" industry | Product | 0 |
| GAP-C-02 | D0 / 0.24 | LOW | OPEN | Default currency USD not ZAR for SA-registered org | Product | 0 |
| GAP-C-03 | D0 / 0.28 | MED | OPEN | `consulting-generic` has no rate-card defaults | Product | 0 |
| GAP-C-04 | D0 / 0.34 | MED | OPEN | No agency-flavoured custom field pack | Product | 0 |
| GAP-C-05 | D0 / 0.40 | MED | OPEN | No templates pre-seeded for consulting-generic | Product | 0 |
| GAP-C-06 | D0 / 0.43 | MED | OPEN | No automation pack for consulting-generic | Product | 0 |
| GAP-C-07 | D9 / 9.3 | HIGH | OPEN | No retainer primitive — manual project-per-cycle workaround | Product | 0 |

## Legend

- **Status**: OPEN -> SPEC_READY -> FIXED -> VERIFIED | REOPENED | STUCK | WONT_FIX | RESOLVED
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-14 — Cycle initialized for consulting agency 90-day demo.
- 2026-04-14 — Infra Agent: Clean teardown (volumes wiped), fresh Docker infra started, Keycloak bootstrapped (padmin@docteams.local), all 4 services UP (backend:8080, gateway:8443, frontend:3000, portal:3002). Dev stack READY.
- 2026-04-14 — QA Agent: Day 0 Phases A-D complete. Access request, OTP, admin approval, Keycloak registration, team invites all PASS. 3 users registered (Zolani/Owner, Bob/Admin, Carol/Member). Phase E in progress — currency set to ZAR, brand colour #F97316, Zolani billing rate R1800/hr created. 3 profile-content gaps logged (GAP-C-01 to GAP-C-03).
- 2026-04-14 — QA Agent: Day 0 complete (Phases E-I). All billing/cost rates created (Zolani R1800/R800, Bob R1200/R550, Carol R750/R350). VAT 15% already present. Custom field promotion verified on Customer + Project dialogs. Template "Website Redesign Project" (6 tasks) created manually. Progressive disclosure verified: zero legal/accounting leaks. Tier removal verified: flat billing UI. 6 profile-content gaps logged (GAP-C-01 to GAP-C-06).
- 2026-04-14 — QA Agent: Days 1-7 complete. BrightCup Coffee Roasters created (PROSPECT -> ACTIVE). First project created from template (6 tasks). 9 hours logged across 3 tasks (Discovery 2h, Wireframes 3h, Design 4h). Budget configured (40h/R40,000, 23%/41% consumed, On Track). All tabs load with generic terminology. No product bugs found.
- 2026-04-14 — QA Agent: Days 8-35 complete. Ubuntu Startup + Masakhane Foundation created and activated. Retainer project (5 tasks, 20h/R24,000 budget, 55%/83% consumed) + NGO project (6 tasks, 60h/R60,000 budget, 21%/38% consumed) created. 32.5 total hours logged. Profitability page renders all 3 projects with ZAR currency, margins, utilization. 1 new gap logged (GAP-C-07: no retainer primitive). No product bugs.
