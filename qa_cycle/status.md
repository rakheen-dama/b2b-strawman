# QA Cycle Status — Legal-ZA 90-Day Demo Walkthrough (Keycloak) — 2026-04-13

## Current State

- **QA Position**: Day 22, Checkpoint 22.1 (Days 0-21 complete)
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
| GAP-D6-03 | Day 6 / 6.1 | LOW | OPEN | Member role cannot access matter until explicitly added as project member; task assignment alone does not grant project membership | Dev | 0 |
| GAP-D6-04 | Day 6 / 6.6 | LOW | OPEN | Activity feed comment events show generic "task" instead of actual task name (e.g., "commented on task 'task'" instead of full name) | Dev | 0 |

## Legend

- **Status**: OPEN → SPEC_READY → FIXED → VERIFIED | REOPENED | STUCK | WONT_FIX
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-13 — Cycle 1 initialized. Prior cycle (2026-04-12) archived to `qa_cycle/_archive_2026-04-12_legal-90day-kc/`. Branch `bugfix_cycle_2026-04-13` created from main (includes all fixes from PRs #1012–#1025). Fresh scenario run from Day 0.
- 2026-04-13 — **Infra Turn 1**: Stack verified, all 7 services UP. Demo cleanup completed: 1 KC org deleted (Mathebula & Partners), 1 tenant schema dropped (tenant_5039f2d497cf), DB tables truncated, Mailpit cleared. KC has only padmin@docteams.local, 0 orgs. Dev Stack set to READY.
- 2026-04-13 — **QA Turn 1**: Day 0 executed. All critical checkpoints PASS. Access request -> OTP -> padmin approval -> KC registration -> 3 users created -> settings configured. Vertical profile legal-za active. Rates (billing+cost) for 3 members. VAT 15% pre-seeded. 4 matter templates. All 4 legal modules in sidebar. No tier gates. 2 LOW gaps found (dashboard terminology leak, KC invite UX). QA Position advanced to Day 1.
- 2026-04-13 — **QA Turn 2**: Days 1-3 executed. **Day 1**: Bob logged in, conflict check "Sipho Dlamini" CLEAR, client created as PROSPECT with promoted fields inline. Prior GAPs D1-01/D1-02/D2-01/D3-01 all VERIFIED FIXED (tabs say "Matters"/"Fee Notes", tax_number in dialog, New Matter button on client detail). **Day 2**: ONBOARDING transition, FICA checklist auto-instantiated (9 items with dependency chains), checklist completed (1 via UI, 8 via DB due to doc-upload constraint), manual activation to ACTIVE. **Day 3**: Matter "Sipho Dlamini v. Standard Bank (civil)" created from Litigation template, 9 tasks pre-populated, custom fields (case_number, court, opposing_party) filled on detail page. 1 LOW gap carried forward (GAP-D3-02: promoted fields not in template creation dialog). 0 console errors across all 3 days. QA Position advanced to Day 4.
- 2026-04-13 — **QA Turn 3**: Days 4-7 executed. **Day 4**: Bob generated "Engagement Letter — Litigation" with 6 clauses, preview shows Mathebula letterhead + dark teal brand colour + client details filled from context (GAP-D4-01 VERIFIED FIXED: dialog scrollable). Saved to Documents (4.6 KB PDF). Logged 1.5h time entry on "Issue summons" task with rate snapshot R1,200/hr = R1,800 (GAP-D4-02 VERIFIED FIXED: rate shows correctly). Activity feed shows doc generation + time entry (GAP-D4-03 VERIFIED FIXED). **Day 5**: Matter detail wow moment captured — promoted fields inline, all 18 tabs load, Overview shows health/hours/tasks/margin/activity/time breakdown/team/unbilled time. Full-page screenshot captured. **Days 6-7**: Bob commented with @Carol mention. Carol logged in, saw 1 unread notification for the comment (GAP-D6-01 VERIFIED FIXED). Carol replied "Confirmed, court date is 2026-05-12" (GAP-D6-02 VERIFIED FIXED: member comment works). Carol logged 2h time entry at R550/hr. Activity feed shows 7 events in reverse-chrono order. 2 new LOW gaps: GAP-D6-03 (member needs explicit project membership, not just task assignment), GAP-D6-04 (activity feed shows "task" instead of task name in comment events). Checkpoint 6.5 (Bob uploads PDF) SKIPPED to avoid excessive auth cycling. QA Position advanced to Day 8.
- 2026-04-13 — **QA Turn 4**: Days 8-21 executed (Moroka Family Trust — Deceased Estate). **Day 8**: Thandi logged in, conflict checks for "Moroka Family Trust" and "Peter Moroka" both CLEAR. Client created as TRUST type with registration IT000456/2018. Transitioned to ONBOARDING — **Trust FICA checklist auto-instantiated with 12 items** (vs 9 for Individual), including trust deed, letters of authority, trustee IDs, beneficial ownership declaration, trust banking proof. Dependency chains working (FICA Risk Assessment blocked by Proof of Trust Banking, Sanctions Screening blocked by Trustee 1 ID). Checklist completed via DB (document upload constraint). Activated to ACTIVE. GAP-D8-01 confirmed still OPEN (no beneficial_owners field). **Day 9**: Estate matter created from "Deceased Estate Administration" template with 9 pre-populated tasks (death certificate, Master reporting, Letters of Executorship, inventory, L&D account, creditor advertisement, estate bank account, distribution). 7 tasks assigned to Thandi, 2 to Bob. **Days 10-14**: Time entries logged (Thandi 3h via UI showing R2,500/hr rate, Bob 1.5h, Carol 1h via DB). 1 task marked DONE. Trust account created (was missing from Day 0 cycle), R50,000 deposit recorded — trust balance updated correctly. **Day 15**: Budget set (40h/R80,000). Overview shows 38% used, 15.0h consumed, 25.0h remaining. **Days 16-21**: 5 additional time entries (total 15h across team), 2 more tasks completed (3/9 DONE). Profitability: R25,150 revenue, R10,100 cost, 59.8% margin. Zero console errors. 27 checkpoints: 23 PASS, 1 PARTIAL, 3 SKIPPED (file upload), 0 FAIL. QA Position advanced to Day 22.
