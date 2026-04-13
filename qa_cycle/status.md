# QA Cycle Status — Legal-ZA 90-Day Demo Walkthrough (Keycloak) — 2026-04-13

## Current State

- **QA Position**: Day 4, Checkpoint 4.1 (Days 0-3 complete)
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
| GAP-D0-01 | Day 0 / 0.25 | LOW | OPEN | Dashboard subtitle "project health" should be "matter health" for legal-za | Dev | 0 |
| GAP-D0-02 | Day 0 / 0.22 | LOW | OPEN | KC invite token single-use: if user still logged in as another KC user, registration succeeds but redirect shows error page | Dev | 0 |
| GAP-D3-02 | Day 3 / 3.3 | LOW | OPEN | Promoted custom fields (matter_type, case_number, court_name) not in template creation dialog — user fills them on detail page after creation | Dev | 0 |

## Legend

- **Status**: OPEN → SPEC_READY → FIXED → VERIFIED | REOPENED | STUCK | WONT_FIX
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-13 — Cycle 1 initialized. Prior cycle (2026-04-12) archived to `qa_cycle/_archive_2026-04-12_legal-90day-kc/`. Branch `bugfix_cycle_2026-04-13` created from main (includes all fixes from PRs #1012–#1025). Fresh scenario run from Day 0.
- 2026-04-13 — **Infra Turn 1**: Stack verified, all 7 services UP. Demo cleanup completed: 1 KC org deleted (Mathebula & Partners), 1 tenant schema dropped (tenant_5039f2d497cf), DB tables truncated, Mailpit cleared. KC has only padmin@docteams.local, 0 orgs. Dev Stack set to READY.
- 2026-04-13 — **QA Turn 1**: Day 0 executed. All critical checkpoints PASS. Access request -> OTP -> padmin approval -> KC registration -> 3 users created -> settings configured. Vertical profile legal-za active. Rates (billing+cost) for 3 members. VAT 15% pre-seeded. 4 matter templates. All 4 legal modules in sidebar. No tier gates. 2 LOW gaps found (dashboard terminology leak, KC invite UX). QA Position advanced to Day 1.
- 2026-04-13 — **QA Turn 2**: Days 1-3 executed. **Day 1**: Bob logged in, conflict check "Sipho Dlamini" CLEAR, client created as PROSPECT with promoted fields inline. Prior GAPs D1-01/D1-02/D2-01/D3-01 all VERIFIED FIXED (tabs say "Matters"/"Fee Notes", tax_number in dialog, New Matter button on client detail). **Day 2**: ONBOARDING transition, FICA checklist auto-instantiated (9 items with dependency chains), checklist completed (1 via UI, 8 via DB due to doc-upload constraint), manual activation to ACTIVE. **Day 3**: Matter "Sipho Dlamini v. Standard Bank (civil)" created from Litigation template, 9 tasks pre-populated, custom fields (case_number, court, opposing_party) filled on detail page. 1 LOW gap carried forward (GAP-D3-02: promoted fields not in template creation dialog). 0 console errors across all 3 days. QA Position advanced to Day 4.
