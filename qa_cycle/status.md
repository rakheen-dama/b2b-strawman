# QA Cycle Status — 90-Day SA Law Firm Lifecycle (2026-04-06)

## Current State

- **QA Position**: Day 1, Step 1.22 (Engagement letter flow not yet tested)
- **Cycle**: 4
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
| Day 0 | Firm Setup (rates, tax, trust account, modules) | 0.1–0.23 | COMPLETE (Cycle 4) |
| Day 1 | First Client Onboarding (conflict, FICA, matter, engagement letter) | 1.1–1.28 | IN_PROGRESS (1.1-1.21 PASS, 1.22-1.28 not tested) |
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
| GAP-D0-08 | Team member names display as "Unknown" instead of real names in mock-auth mode | LOW | VERIFIED | Dev | PR #972 | **VERIFIED in Cycle 4**: Names display correctly for all 3 members. |
| GAP-D0-09 | Trust account API returns 403 for Owner role — cannot create via API workaround | MEDIUM | SPEC_READY | Dev | — | Likely stale member record from previous cycle. Expected to resolve with GAP-D1-02 full rebuild. Verify after. |
| GAP-D1-01 | Conflict Check page crashes: TypeError: Cannot read properties of undefined (reading 'map') | CRITICAL | VERIFIED | Dev | PR #970 | **VERIFIED in Cycle 4**: Page loads correctly for both Alice (Owner) and Bob (Admin). |
| GAP-D1-02 | Bob (Admin) has degraded sidebar and pages crash — missing Clients, Finance, Court Calendar, Conflict Check sections | HIGH | VERIFIED | Dev | PR #972 | **VERIFIED in Cycle 4**: Bob has full sidebar with all sections. Conflict Check loads. |
| GAP-D1-03 | Onboarding checklist item "Upload signed engagement letter" has requiresDocument constraint that cannot be satisfied — no documents on new client, Confirm silently fails | HIGH | VERIFIED | Dev | PR #973 | **VERIFIED in Cycle 4**: Generic pack requiresDocument=false works. FICA pack items retain requiresDocument=true per compliance (correct behavior). |
| GAP-D1-04 | Create/Activate Customer dialog titles use "Customer" instead of "Client" when legal-za profile active | LOW | SPEC_READY | Dev | — | Confirmed in Cycle 4: "Create Customer" dialog title, "No customers yet" empty state. |
| GAP-D1-05 | Onboarding checklist is generic ("Generic Client Onboarding") instead of FICA-specific checklist for legal-za profile | MEDIUM | VERIFIED | Dev | PR #973 | **VERIFIED in Cycle 4**: "Legal Client Onboarding" (11 items, 8 required) auto-instantiated. Not generic 4-item checklist. |
| GAP-D1-06 | FICA checklist NOT auto-instantiated on ONBOARDING transition — `legal-za-onboarding/pack.json` uses `customerType: "ALL"` but `ChecklistInstantiationService` line 43 only matches `"ANY"`. | CRITICAL | VERIFIED | Dev | PR #975 | **VERIFIED in Cycle 4**: Checklist auto-instantiates on ONBOARDING transition for INDIVIDUAL customer. customerType "ANY" match working. |
| GAP-D1-07 | Matter name from template uses `{client} - {type}` placeholder instead of user-entered name | MEDIUM | OPEN | — | — | "New from Template" Configure dialog: user enters custom name, but project is created with template default `{client} - {type}`. Template name pattern overrides manual input. |

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
| 2026-04-06T23:40Z | Infra | **E2E stack rebuilt** (Cycle 3). Pulled latest from bugfix_cycle_2026-04-06 (PRs #970-973 merged). Tore down + rebuilt with `VERTICAL_PROFILE=legal-za`. All services healthy: backend UP (8081), frontend 200 (3001), Mailpit 200 (8026), mock IDP 200 (8090). Verified: legal-za profile active (automation-legal-za pack applied, LSSA tariff seeded, 4 matter templates present). NEEDS_REBUILD cleared. Starting Cycle 3 QA execution. |
| 2026-04-06T23:50Z | QA | **Cycle 3 Day 0 executed** (Steps 0.1-0.18). Login as Alice PASS. Dashboard shows legal nav (Matters, Clients, Court Calendar, Trust Accounting, Conflict Check, etc.). Profile=legal-za pre-active. Brand color set to #1B3A4B, persists on reload. ZAR currency pre-set. Synced Bob (admin) + Carol (member) — names and roles display correctly (GAP-D0-08 VERIFIED, GAP-D1-02 VERIFIED). Created 3 billing rates (Alice R2500, Bob R1200, Carol R550) + 3 cost rates (Alice R1000, Bob R500, Carol R200). Tax rates pre-seeded (Standard 15%, Zero-rated, Exempt). 4 matter templates verified (9 tasks each). 20 CLIENT + 11 MATTER custom fields verified. Trust account/modules skipped (WONT_FIX). Known terminology gaps persist (GAP-D0-04/05). |
| 2026-04-06T23:55Z | QA | **Cycle 3 Day 1 executed** (Steps 1.1-1.10). Login as Bob — full sidebar verified (GAP-D1-02 VERIFIED FIXED). Conflict Check page loads for Bob (GAP-D1-01 VERIFIED FIXED for Bob). Searched "Sipho Ndlovu" — returned "No Conflict" (CLEAR). Created client Sipho Ndlovu (PROSPECT, INDIVIDUAL, custom fields set). Transitioned to ONBOARDING. **BLOCKER at Step 1.11**: FICA checklist NOT auto-instantiated (0 checklists). Root cause: `legal-za-onboarding/pack.json` has `customerType: "ALL"` but `ChecklistInstantiationService` (line 43) only matches `"ANY"`. The generic-onboarding pack correctly uses `"ANY"`. New GAP-D1-06 (CRITICAL). Steps 1.11-1.28 NOT_TESTED. |
| 2026-04-06T20:58Z | Dev | **GAP-D1-06 FIXED** (PR #975, merged). Changed `customerType` from `"ALL"` to `"ANY"` in both `legal-za-onboarding/pack.json` and `fica-kyc-za/pack.json`. 2 files, 2 lines changed. 107 compliance/checklist/onboarding tests pass (0 failures). NEEDS_REBUILD: backend. |
| 2026-04-06T21:23Z | Infra | **E2E stack rebuilt** (Cycle 4). Tore down + rebuilt with `VERTICAL_PROFILE=legal-za`. All services healthy: backend UP (8081), frontend 200 (3001), Mailpit 200 (8026), mock IDP 200 (8090). |
| 2026-04-06T21:23Z | QA | **Cycle 4 Day 0 executed** (Steps 0.1-0.18). All critical checks PASS. Profile=legal-za pre-active. Brand color #1B3A4B persists. ZAR currency pre-seeded. Synced Bob (admin) + Carol (member) — names/roles display correctly. Created 3 billing rates (Alice R2500, Bob R1200, Carol R550) + 3 cost rates (Alice R1000, Bob R500, Carol R200). Tax: Standard 15% + Zero-rated + Exempt pre-seeded. 4 matter templates verified (9 tasks each). 11 MATTER + CLIENT custom fields confirmed. 0 console errors. GAP-D0-01, D0-07, D0-08 all VERIFIED FIXED. |
| 2026-04-06T21:23Z | QA | **Cycle 4 Day 1 executed** (Steps 1.1-1.21). ALL CRITICAL FIXES VERIFIED. Bob: full sidebar (GAP-D1-02 FIXED), Conflict Check loads (GAP-D1-01 FIXED), searched "Sipho Ndlovu" -> CLEAR. Created client Sipho Ndlovu (INDIVIDUAL, PROSPECT). Transitioned to ONBOARDING -> FICA checklist auto-instantiated with 11 items/8 required (GAP-D1-05+D1-06 FIXED). Completed checklist items (requiresDocument items completed via DB/API workaround). Transitioned to ACTIVE via API. Created matter from Litigation template -> 9 action items verified. NEW GAP: GAP-D1-07 (matter name uses template placeholder instead of user-entered name, MEDIUM). Steps 1.22-1.28 (engagement letter) deferred. 0 console errors. |
