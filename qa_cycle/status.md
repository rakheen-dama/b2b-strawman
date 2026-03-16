# QA Cycle Status — 2026-03-15

## Current State

- **QA Position**: Day 2, Checkpoint 2.1 complete — proceeding to Day 3
- **Cycle**: 2
- **E2E Stack**: Running (rebuilt after PRs #687-690)
- **Branch**: `bugfix_cycle_2026-03-15`
- **Scenario**: `tasks/phase47-lifecycle-script.md`

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Day | Notes |
|----|---------|----------|--------|-------|----|-----|-------|
| GAP-008 | Accounting template pack not seeded (only 3 generic templates, missing 7 accounting-specific) | blocker | VERIFIED | Infra | — | 0 | Fixed and verified: All 10 templates visible (3 common + 7 accounting-za) |
| GAP-008A | Org settings page "Coming Soon" — cannot rename org or set currency | major | WONT_FIX | — | — | 0 | Requires new feature (org settings CRUD). Out of scope for bugfix cycle. Workaround: branding set via Templates page. |
| GAP-008B | FICA field groups not auto-attached during customer creation | major | SPEC_READY | Dev | — | 1 | Only Contact & Address shown in Step 2. Fix spec: `qa_cycle/fix-specs/GAP-008B.md` |
| GAP-008C | Projects page JS error on first load (TypeError: null ref) | bug | VERIFIED | Dev | #689 | 0 | Cycle 2: Projects page loads correctly, shows "Website Redesign" project. Console has only pre-existing React #418 hydration mismatch (cosmetic). |
| GAP-001 | PROPOSAL_SENT automation trigger does not exist | major | WONT_FIX | — | — | 0 | New automation trigger type = new feature. Out of scope for bugfix cycle. |
| GAP-002 | FIELD_DATE_APPROACHING automation trigger does not exist | major | WONT_FIX | — | — | 0 | New automation trigger type = new feature. Out of scope for bugfix cycle. |
| GAP-003 | CHECKLIST_COMPLETED automation trigger does not exist | major | WONT_FIX | — | — | 14 | New automation trigger type = new feature. Out of scope for bugfix cycle. |
| GAP-004 | Statement-of-account template is a stub | major | OPEN | Dev | — | 90 | Day 90 — not triaged yet (QA hasn't reached Day 90). |
| GAP-005 | Terminology overrides not loaded at runtime | minor | WONT_FIX | — | — | 0 | Not blocking QA — cosmetic |
| GAP-006 | Rate card defaults not auto-seeded from profile | minor | WONT_FIX | — | — | 0 | Manual setup works, not blocking |
| GAP-007 | Delayed automation triggers cannot be verified | minor | WONT_FIX | — | — | 14 | Testing limitation, not blocking |
| GAP-009 | FICA checklist does not filter by entity type | major | OPEN | Dev | — | 3 | Day 3 — not triaged yet. |
| GAP-010 | Trust-specific custom fields missing | major | OPEN | Dev | — | 3 | Day 3 — not triaged yet. |
| GAP-011 | No retainer overage/overflow billing | major | WONT_FIX | — | — | 30 | New feature. Out of scope for bugfix cycle. |
| GAP-012 | No effective hourly rate per retainer report | major | WONT_FIX | — | — | 60 | New feature. Out of scope for bugfix cycle. |
| GAP-013 | No proposal/engagement letter lifecycle tracking | minor | WONT_FIX | — | — | 1 | New feature (proposal dashboard). Out of scope. Confirmed in cycle 2: engagement letter templates are project-scoped, not customer-scoped. |
| GAP-014 | No disbursement/expense invoicing workflow | minor | WONT_FIX | — | — | 45 | New feature. Out of scope for bugfix cycle. |
| GAP-015 | No bulk time entry creation | minor | WONT_FIX | — | — | 30 | UX enhancement. Out of scope for bugfix cycle. |
| GAP-016 | No SA-specific invoice PDF formatting | minor | WONT_FIX | — | — | 30 | New feature. Out of scope for bugfix cycle. |
| GAP-017 | No recurring engagement auto-creation | minor | WONT_FIX | — | — | 1 | New feature. Out of scope for bugfix cycle. |
| GAP-018 | No client onboarding progress tracker | minor | WONT_FIX | — | — | 1 | New feature. Out of scope for bugfix cycle. |
| GAP-019 | Currency displays as USD not ZAR | cosmetic | OPEN | Dev | — | 0 | Low priority. QA can proceed with USD display. |
| GAP-020 | Portal contacts required for information requests | minor | OPEN | Dev | — | 1 | Cycle 2: Confirmed — "Create Information Request" dialog shows "No portal contacts found for this customer. Please add a portal contact first." Save/Send buttons disabled without portal contact. |
| GAP-021 | No SARS integration or eFiling export | minor | WONT_FIX | — | — | 75 | Future enhancement |
| GAP-022 | No engagement letter auto-creation from template | cosmetic | WONT_FIX | — | — | 75 | Nice to have |
| GAP-023 | No saved views on list pages | minor | WONT_FIX | — | — | 90 | UX enhancement. Out of scope for bugfix cycle. |
| GAP-024 | No aged debtors report | major | OPEN | Dev | — | 90 | Day 90 — not triaged yet (QA hasn't reached Day 90). |
| GAP-025 | Team member list API calls port 8080 instead of 8081 in E2E stack | bug | VERIFIED | Dev | #688 | 0 | Cycle 2: Team page shows all 3 members (Alice Owner, Bob Admin, Carol Member) with "3 members" count. |
| GAP-026 | FICA/KYC checklist template not seeded by accounting-za pack | major | VERIFIED | Dev | #690 | 0 | Cycle 2: Settings > Checklists shows 4 templates including "FICA KYC — SA Accounting" (9 items). Manually instantiated on Kgosi Construction — all 9 items visible with correct required/optional flags. |
| GAP-027 | Customer pages SSR crash after ONBOARDING lifecycle transition | blocker | VERIFIED | Dev | #687 | 1 | Cycle 2: Created customer, transitioned to ONBOARDING — page renders correctly. Status shows "Onboarding". Onboarding tab appears. All tabs accessible. Customer list page also works. No crash. |
| GAP-028 | Customer detail page intermittent render crash | major | VERIFIED | Dev | #687 | 1 | Same root cause as GAP-027. Fixed by PR #687. Cycle 2: Customer detail pages render reliably after SSR hydration (~2s). |
| GAP-029 | React #418 hydration mismatch on multiple pages | cosmetic | OPEN | — | — | 0 | Pre-existing issue across all pages. Console shows `Minified React error #418` on every page load. Non-blocking — pages render correctly after hydration. Likely SSR/client mismatch in date formatting or locale-dependent content. |

## Status Values

- **OPEN** -> **SPEC_READY** -> **IN_PROGRESS** -> **FIXED** -> **VERIFIED** -> done
- **REOPENED** (fix didn't work) -> back to SPEC_READY
- **WONT_FIX** (documented, not blocking e2e flow)
- **BUG** (logged, prioritized, not blocking unless cascading)

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-15T22:35Z | Setup | Initial status seeded from gap report |
| 2026-03-15T23:12Z | Infra | GAP-008 FIXED: Added verticalProfile to ProvisioningController DTO + seed.sh. All 10 templates seeded (3 common + 7 accounting-za). E2E stack running. |
| 2026-03-16T00:15Z | QA | Day 0 execution complete (cycle 1). 13 checkpoints: 8 PASS, 3 PARTIAL, 2 FAIL. GAP-008 VERIFIED. New gaps: GAP-025 (team API port), GAP-026 (FICA checklist not seeded). No blockers for Day 1. |
| 2026-03-15T21:27Z | QA | Day 1 execution partial (cycle 1). 10 checkpoints: 2 PASS, 1 PARTIAL, 7 FAIL (6 blocked). Kgosi Construction created with all 16 custom fields. Checklist partially tested (generic 4-item, not FICA 9-item). ONBOARDING transition succeeded in backend but caused cascading frontend crash (GAP-027 BLOCKER). New gaps: GAP-027 (SSR crash, blocker), GAP-028 (intermittent render crash). QA halted — cannot proceed past checkpoint 1.4. |
| 2026-03-16T01:30Z | Product | Triage cycle 1 complete. 5 items SPEC_READY (GAP-027, GAP-026, GAP-025, GAP-008B, GAP-008C). 11 items WONT_FIX (new features out of scope). GAP-028 deduped with GAP-027 (same root cause). Priority order: GAP-027 (blocker) > GAP-026 (FICA checklist) > GAP-025 (team API port) > GAP-008C/GAP-008B (lower priority). Fix specs written to `qa_cycle/fix-specs/`. |
| 2026-03-16T02:00Z | Dev | GAP-027 FIXED: Added null guards (`?.` / `?? 0` / `?? []`) to all `customerReadiness.requiredFields` accesses in customer detail page. Added error boundaries at `customers/error.tsx` and `customers/[id]/error.tsx`. GAP-028 also fixed (same root cause). PR #687 merged to `bugfix_cycle_2026-03-15`. NEEDS_REBUILD=true (frontend changed). |
| 2026-03-16T02:15Z | Dev | GAP-025 FIXED: Added `NEXT_PUBLIC_BACKEND_URL=http://localhost:8081` as Docker build arg in `compose/docker-compose.e2e.yml` and declared `ARG NEXT_PUBLIC_BACKEND_URL` in `frontend/Dockerfile`. PR #688 merged to `bugfix_cycle_2026-03-15`. NEEDS_REBUILD=true (frontend build arg changed). |
| 2026-03-16T02:30Z | Dev | GAP-008C FIXED: Created `error.tsx` error boundary at projects route level. Added null guards on `project.status` (PROJECT_STATUS_BADGE lookup), `project.createdAt` (formatDate), and customer API result values (optional chaining). PR #689 merged to `bugfix_cycle_2026-03-15`. NEEDS_REBUILD=true. |
| 2026-03-16T02:45Z | Dev | GAP-026 FIXED: Removed 4-line inactive override in `CompliancePackSeeder` (non-autoInstantiate templates were wrongly set to `active=false`). Added `V71__activate_fica_checklist_template.sql` tenant migration to fix existing data. PR #690 merged to `bugfix_cycle_2026-03-15`. NEEDS_REBUILD=true (backend changed). |
| 2026-03-16T03:05Z | Infra | E2E stack rebuilt after cycle 1 fixes (PRs #687-690). All services healthy: frontend (3001), backend (8081), mock-idp (8090) all returning HTTP 200. NEEDS_REBUILD cleared. Stack ready for QA re-verification. |
| 2026-03-16T00:05Z | QA | Cycle 2 fix verifications complete. 4/4 VERIFIED: GAP-027 (blocker, customer ONBOARDING crash), GAP-026 (FICA checklist seeding), GAP-025 (team API port), GAP-008C (projects page JS error). All customer pages functional post-ONBOARDING transition. FICA checklist (9 items) instantiatable from Manually Add Checklist dialog. Team page shows 3 members. Projects page renders correctly. |
| 2026-03-16T00:10Z | QA | Day 1 re-execution (cycle 2). Kgosi Construction created and transitioned to ONBOARDING without crash. FICA checklist instantiated (9 items). Requests tab accessible (GAP-020 confirmed — portal contacts required). Generate Document dropdown shows customer-scoped templates only (engagement letters are project-scoped). Day 1 result: 2 PASS, 3 PARTIAL, 3 FAIL, 1 NOT TESTED. No new blockers. |
| 2026-03-16T00:15Z | QA | Day 2 partial execution (cycle 2). Naledi Hair Studio created as Individual type by Bob Admin. Customer list shows 3 customers. New gap GAP-029 logged (cosmetic React #418 hydration mismatch on all pages). Day 2 result: 1 PASS, 5 NOT TESTED. |
