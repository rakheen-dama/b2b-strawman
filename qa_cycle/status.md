# QA Cycle Status — 90-Day SA Law Firm Lifecycle (2026-04-06)

## Current State

- **QA Position**: Day 1, Step 1.14 (BLOCKED — onboarding checklist document requirement prevents ACTIVE transition)
- **Cycle**: 2
- **E2E Stack**: READY
- **NEEDS_REBUILD**: true (mock IDP + backend changes from GAP-D1-02+D0-08+D1-03+D1-05 need full E2E rebuild)
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
| Day 0 | Firm Setup (rates, tax, trust account, modules) | 0.1–0.23 | COMPLETE (known gaps) |
| Day 1 | First Client Onboarding (conflict, FICA, matter, engagement letter) | 1.1–1.28 | BLOCKED at 1.14 (GAP-D1-03) |
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
| GAP-D0-01 | No legal matter templates (Litigation, Deceased Estate Admin, Collections, Commercial) seeded by legal-za profile | HIGH | VERIFIED | Dev | PR #971 | **VERIFIED in Cycle 2**: 4 legal templates present (each with 9 tasks) on Project Templates page from initial provisioning. |
| GAP-D0-02 | Trust Accounting lacks "Create Trust Account" dialog — cannot create trust accounts from UI | HIGH | WONT_FIX | Dev | — | Not a stub — full dashboard exists (Phase 61). Missing: CreateTrustAccountDialog component. Workaround: create via API. Exceeds 2hr scope. |
| GAP-D0-03 | No Settings > Modules page to verify/toggle legal modules | LOW | WONT_FIX | Dev | — | New feature required. Modules work correctly via profile system. Exceeds scope. |
| GAP-D0-04 | "Projects" group header not renamed to "Matters" in sidebar | LOW | SPEC_READY | Dev | — | Fix: change `{zone.label}` to `{t(zone.label)}` in nav-zone.tsx line 45. |
| GAP-D0-05 | Dashboard cards say "Active Projects"/"Project Health" instead of legal terms | LOW | SPEC_READY | Dev | — | Hardcoded labels in kpi-card-row.tsx, metrics-strip.tsx, project-health-widget.tsx not using t(). |
| GAP-D0-06 | Team page Role column empty for all members | LOW | SPEC_READY | Dev | — | Root cause: useOrgMembers() maps `orgRole` → `role` field name mismatch + missing `org:` prefix normalization. |
| GAP-D0-07 | E2E seed does not pre-apply legal-za profile — requires manual profile switch | MEDIUM | VERIFIED | Dev | e7a13e67 | **VERIFIED in Cycle 2**: legal-za profile active from start, no manual switch needed. Settings > General shows "Legal (South Africa)" with Apply Profile disabled. |
| GAP-D0-08 | Team member names display as "Unknown" instead of real names in mock-auth mode | LOW | FIXED | Dev | PR #972 | Mock IDP JWT now includes `name` claim. Bundled with GAP-D1-02 fix. NEEDS_REBUILD. |
| GAP-D0-09 | Trust account API returns 403 for Owner role — cannot create via API workaround | MEDIUM | SPEC_READY | Dev | — | Likely stale member record from previous cycle. Expected to resolve with GAP-D1-02 full rebuild. Verify after. |
| GAP-D1-01 | Conflict Check page crashes: TypeError: Cannot read properties of undefined (reading 'map') | CRITICAL | VERIFIED | Dev | PR #970 | Page loads correctly for Alice (Owner). **VERIFIED FIXED in Cycle 2.** |
| GAP-D1-02 | Bob (Admin) has degraded sidebar and pages crash — missing Clients, Finance, Court Calendar, Conflict Check sections | HIGH | FIXED | Dev | PR #972 | Mock IDP JWT now includes `role` claim. MemberFilter reads JWT role as fallback when no invitation exists. NEEDS_REBUILD. |
| GAP-D1-03 | Onboarding checklist item "Upload signed engagement letter" has requiresDocument constraint that cannot be satisfied — no documents on new client, Confirm silently fails | HIGH | FIXED | Dev | PR #973 | Generic-onboarding pack item 4 changed to `requiresDocument:false`. Combined with GAP-D1-05 in single PR. |
| GAP-D1-04 | Create/Activate Customer dialog titles use "Customer" instead of "Client" when legal-za profile active | LOW | SPEC_READY | Dev | — | Hardcoded "Customer" in create-customer-dialog.tsx and TransitionConfirmDialog.tsx. Fix: wrap with t(), add compound phrases to terminology-map. |
| GAP-D1-05 | Onboarding checklist is generic ("Generic Client Onboarding") instead of FICA-specific checklist for legal-za profile | MEDIUM | FIXED | Dev | PR #973 | Swapped autoInstantiate flags: generic=false, legal-za=true. Legal-za tenants now get 11-item FICA checklist. Combined with GAP-D1-03. |

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-04-06T09:00Z | Setup | QA cycle initialized. Branch: bugfix_cycle_2026-04-06. E2E stack confirmed running (frontend:3001, backend:8081, mailpit:8026). |
| 2026-04-06T16:00Z | QA | Day 0 executed. Applied Legal (South Africa) profile (seed had accounting). Created 3 billing rates (Alice R2500, Bob R1200, Carol R550) and 3 cost rates (Alice R1000, Bob R500, Carol R200). Verified ZAR currency, 15% tax, team members, custom fields. Found 7 gaps: no matter templates (GAP-D0-01), trust accounting stub (GAP-D0-02), no modules page (GAP-D0-03), terminology gaps (GAP-D0-04/05/06), seed profile issue (GAP-D0-07). |
| 2026-04-06T16:05Z | QA | Day 1 started. Login as Bob. Conflict Check page crashes with TypeError (GAP-D1-01 CRITICAL BLOCKER). Clients page loads OK with "New Client" button. STOPPED at Step 1.1 per blocker rule. |
| 2026-04-06T18:00Z | Product | Triaged all 8 OPEN items. GAP-D1-01 (CRITICAL blocker): defensive defaults from PR #910 confirmed on branch but likely stale Docker image — spec'd rebuild + belt-and-suspenders defaults. GAP-D0-01 (HIGH): root cause confirmed in OrgSettingsService line 714 — projectTemplatePackSeeder not called on profile switch. GAP-D0-02 (HIGH): reclassified to WONT_FIX — full dashboard exists but CreateTrustAccountDialog missing, exceeds 2hr scope. GAP-D0-03 (LOW): WONT_FIX — new feature. GAP-D0-04/05/06 (LOW): all spec'd — terminology + role mapping fixes. GAP-D0-07 (MEDIUM): spec'd — parameterize seed script. 6 items SPEC_READY, 2 WONT_FIX. |
| 2026-04-06T18:30Z | Dev | **GAP-D1-01 FIXED** (PR #970, merged). Added defensive `?.content ?? []` and `?.page?.totalElements ?? 0` defaults in 3 files: page.tsx (server fetch), conflict-check-client.tsx (client refetch), conflict-check-history.tsx (history refetch). 3 files, 6 lines changed. Build green, 302 test files / 1871 tests pass. NEEDS_REBUILD: frontend. |
| 2026-04-06T19:41Z | Dev | **GAP-D0-01 FIXED** (PR #971, merged). Added `projectTemplatePackSeeder.seedPacksForTenant()` call in `OrgSettingsService.updateVerticalProfile()` after existing rate and schedule pack seeder calls. Injected `ProjectTemplatePackSeeder` as constructor dependency. 1 file, +7/-2 lines. OrgSettingsIntegrationTest 38/38 pass. NEEDS_REBUILD: backend. |
| 2026-04-06T20:15Z | Dev | **GAP-D0-07 FIXED** (commit e7a13e67). Parameterized `VERTICAL_PROFILE` env var in seed.sh and docker-compose.e2e.yml (default: accounting-za). **E2E stack rebuilt** with `VERTICAL_PROFILE=legal-za`. Verified: verticalProfile=legal-za, enabledModules=[court_calendar, conflict_check, lssa_tariff, trust_accounting], 4 legal matter templates (Litigation, Deceased Estate Admin, Collections, Commercial). GAP-D0-01 also verified resolved — templates seeded from initial provisioning. Note: seed Step 2 (plan-sync) fails with 404 — pre-existing issue, `/internal/orgs/plan-sync` endpoint does not exist in backend. Members/customer/project not seeded due to `set -eu` exit. |
| 2026-04-06T21:16Z | QA | **Cycle 2 Day 0 executed.** All steps 0.1-0.23 tested. Profile=legal-za verified pre-active. Created 3 billing rates (Alice R2500, Bob R1200, Carol R550) + 3 cost rates (Alice R1000, Bob R500, Carol R200). VAT 15% pre-seeded. 4 matter templates verified (each 9 tasks). Trust account creation still blocked (GAP-D0-02 WONT_FIX + new GAP-D0-09 API 403). Custom fields loaded for both matter and client entities. GAP-D0-01 VERIFIED FIXED. GAP-D0-07 VERIFIED FIXED. GAP-D1-01 VERIFIED FIXED (page loads for Alice). New: GAP-D0-08 (Unknown member names), GAP-D0-09 (trust API 403). |
| 2026-04-06T21:16Z | QA | **Cycle 2 Day 1 executed (partial, BLOCKED).** Steps 1.1-1.13 tested. Bob (Admin) sidebar degraded and Conflict Check crashes (GAP-D1-02 HIGH). Continued as Alice. Conflict check for "Sipho Ndlovu" returned CLEAR. Client created (Prospect), transitioned to Onboarding. Generic checklist auto-instantiated (4 items, not FICA-specific — GAP-D1-05). Completed 3/4 items. **BLOCKER**: Item 4 "Upload signed engagement letter" has requiresDocument constraint — no documents available on new client, Confirm silently fails (GAP-D1-03 HIGH). Client stuck at ONBOARDING. Manual Activate also blocked. Steps 1.16-1.28 NOT_TESTED due to cascade. |
| 2026-04-06T22:00Z | Product | **Cycle 2 triage complete — all 5 OPEN items now SPEC_READY.** Root causes confirmed via code inspection: (1) GAP-D1-03 BLOCKER: generic-onboarding pack item 4 `requiresDocument:true` creates chicken-and-egg on new clients. Fix: set to `false`. (2) GAP-D1-02 HIGH: mock IDP JWT missing `role` claim, MemberFilter creates Bob as "member" not "admin". Fix: add `role`+`name` to JWT, read in MemberFilter. (3) GAP-D0-09 MEDIUM: likely stale member from Cycle 1, expected to resolve with GAP-D1-02 full rebuild. (4) GAP-D1-05 MEDIUM: legal-za-onboarding pack has `autoInstantiate:false` while generic has `true`. Fix: swap both. (5) GAP-D1-04 LOW: hardcoded "Customer" in dialog titles. Fix: wrap with t(). (6) GAP-D0-08 LOW: mock IDP JWT missing `name` claim, bundled with GAP-D1-02. Fix specs written to qa_cycle/fix-specs/. Implementation priority: GAP-D1-03+D1-05 (unblocks lifecycle) → GAP-D1-02+D0-08 (unblocks multi-user) → GAP-D0-09 (verify) → GAP-D1-04 (terminology). |
| 2026-04-06T22:35Z | Dev | **GAP-D1-02+D0-08 FIXED** (PR #972, merged). Added `name` and `role` claims to mock IDP JWT payload (`compose/mock-idp/src/index.ts`). Added JWT `role` claim fallback in `MemberFilter.lazyCreateMember()` — reads role from JWT when no pending invitation exists, before first-member-owner promotion. 2 files, +11 lines. Mock IDP build green, backend 185 targeted tests pass (0 failures). NEEDS_REBUILD: mock-idp + backend Docker images. |
| 2026-04-06T22:35Z | Dev | **GAP-D1-03+D1-05 FIXED** (PR #973, merged). Three pack JSON changes: (1) generic-onboarding item 4 `requiresDocument` false + `requiredDocumentLabel` null, (2) generic-onboarding `autoInstantiate` false, (3) legal-za-onboarding `autoInstantiate` true. Updated ComplianceProvisioningTest to match. 107 compliance/checklist/onboarding tests pass. NEEDS_REBUILD: backend. |
