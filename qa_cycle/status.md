# QA Cycle Status — 90-Day SA Law Firm Lifecycle (2026-04-06)

## Current State

- **QA Position**: Day 1, Step 1.1 (BLOCKED — Conflict Check page crashes)
- **Cycle**: 1
- **E2E Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_2026-04-06`
- **Scenario**: `qa/testplan/qa-legal-lifecycle-test-plan.md`
- **Focus**: Full 90-day lifecycle for SA law firm (Mathebula & Partners). Trust accounting, LSSA tariff, conflict checks, court calendar, prescription tracking, fee notes, reconciliation, interest runs, investments, Section 35 compliance, FICA/KYC, role-based access.
- **Auth Mode**: Mock-auth (E2E stack)

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend (mock auth) | http://localhost:3001 | UP |
| Backend (e2e profile) | http://localhost:8081 | UP |
| Mock IDP | http://localhost:8090 | UP |
| Mailpit | http://localhost:8026 | UP |
| Postgres | localhost:5433 | UP |

## Test Plan Structure

| Day | Focus | Steps | Status |
|-----|-------|-------|--------|
| Day 0 | Firm Setup (rates, tax, trust account, modules) | 0.1–0.23 | DONE (7 gaps) |
| Day 1 | First Client Onboarding (conflict, FICA, matter, engagement letter) | 1.1–1.28 | BLOCKED at 1.1 |
| Day 2-3 | Additional Clients (Apex, Moroka, QuickCollect — 6 matters) | 2.1–2.24 | NOT_STARTED |
| Day 7 | First Week Work (time logging, court date, comments, My Work) | 7.1–7.25 | NOT_STARTED |
| Day 14 | Trust Deposits & Conflict Detection | 14.1–14.24 | NOT_STARTED |
| Day 30 | First Billing Cycle (fee notes, tariff, trust transfer, budget) | 30.1–30.33 | NOT_STARTED |
| Day 45 | Reconciliation & Prescription | 45.1–45.22 | NOT_STARTED |
| Day 60 | Interest Run & Second Billing | 60.1–60.29 | NOT_STARTED |
| Day 75 | Complex Engagement & Adverse Parties | 75.1–75.21 | NOT_STARTED |
| Day 90 | Quarter Review & Section 35 Compliance | 90.1–90.40 | NOT_STARTED |

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Notes |
|----|---------|----------|--------|-------|----|-------|
| GAP-D0-01 | No legal matter templates (Litigation, Deceased Estate Admin, Collections, Commercial) seeded by legal-za profile | HIGH | SPEC_READY | Dev | — | Root cause: `OrgSettingsService.updateVerticalProfile()` doesn't call `projectTemplatePackSeeder`. Fix: add seeder call after line 714. |
| GAP-D0-02 | Trust Accounting lacks "Create Trust Account" dialog — cannot create trust accounts from UI | HIGH | WONT_FIX | Dev | — | Not a stub — full dashboard exists (Phase 61). Missing: CreateTrustAccountDialog component. Workaround: create via API. Exceeds 2hr scope. |
| GAP-D0-03 | No Settings > Modules page to verify/toggle legal modules | LOW | WONT_FIX | Dev | — | New feature required. Modules work correctly via profile system. Exceeds scope. |
| GAP-D0-04 | "Projects" group header not renamed to "Matters" in sidebar | LOW | SPEC_READY | Dev | — | Fix: change `{zone.label}` to `{t(zone.label)}` in nav-zone.tsx line 45. |
| GAP-D0-05 | Dashboard cards say "Active Projects"/"Project Health" instead of legal terms | LOW | SPEC_READY | Dev | — | Hardcoded labels in kpi-card-row.tsx, metrics-strip.tsx, project-health-widget.tsx not using t(). |
| GAP-D0-06 | Team page Role column empty for all members | LOW | SPEC_READY | Dev | — | Root cause: useOrgMembers() maps `orgRole` → `role` field name mismatch + missing `org:` prefix normalization. |
| GAP-D0-07 | E2E seed does not pre-apply legal-za profile — requires manual profile switch | MEDIUM | SPEC_READY | Dev | — | Fix: parameterize `verticalProfile` in seed.sh via env var `VERTICAL_PROFILE`. |
| GAP-D1-01 | Conflict Check page crashes: TypeError: Cannot read properties of undefined (reading 'map') | CRITICAL | SPEC_READY | Dev | — | BLOCKER — likely stale Docker build. Fix: add belt-and-suspenders `?? []` defaults + rebuild E2E stack. |

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-04-06T09:00Z | Setup | QA cycle initialized. Branch: bugfix_cycle_2026-04-06. E2E stack confirmed running (frontend:3001, backend:8081, mailpit:8026). |
| 2026-04-06T16:00Z | QA | Day 0 executed. Applied Legal (South Africa) profile (seed had accounting). Created 3 billing rates (Alice R2500, Bob R1200, Carol R550) and 3 cost rates (Alice R1000, Bob R500, Carol R200). Verified ZAR currency, 15% tax, team members, custom fields. Found 7 gaps: no matter templates (GAP-D0-01), trust accounting stub (GAP-D0-02), no modules page (GAP-D0-03), terminology gaps (GAP-D0-04/05/06), seed profile issue (GAP-D0-07). |
| 2026-04-06T16:05Z | QA | Day 1 started. Login as Bob. Conflict Check page crashes with TypeError (GAP-D1-01 CRITICAL BLOCKER). Clients page loads OK with "New Client" button. STOPPED at Step 1.1 per blocker rule. |
| 2026-04-06T18:00Z | Product | Triaged all 8 OPEN items. GAP-D1-01 (CRITICAL blocker): defensive defaults from PR #910 confirmed on branch but likely stale Docker image — spec'd rebuild + belt-and-suspenders defaults. GAP-D0-01 (HIGH): root cause confirmed in OrgSettingsService line 714 — projectTemplatePackSeeder not called on profile switch. GAP-D0-02 (HIGH): reclassified to WONT_FIX — full dashboard exists but CreateTrustAccountDialog missing, exceeds 2hr scope. GAP-D0-03 (LOW): WONT_FIX — new feature. GAP-D0-04/05/06 (LOW): all spec'd — terminology + role mapping fixes. GAP-D0-07 (MEDIUM): spec'd — parameterize seed script. 6 items SPEC_READY, 2 WONT_FIX. |
