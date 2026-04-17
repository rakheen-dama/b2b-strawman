# QA Cycle Status — Consulting Agency 90-Day Demo (Fresh Tenant, Keycloak) — 2026-04-17

## Current State

- **QA Position**: Day 8 — 8.1 (Zolani creates Ubuntu Startup retainer client)
- **Cycle**: 1
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false (GAP-C-01 fix live; new gaps GAP-C-02..08 opened but none are blockers)
- **Branch**: `bugfix_cycle_consulting_2026-04-17`
- **Scenario**: `qa/testplan/demos/consulting-agency-90day-keycloak.md`
- **Focus**: Fresh tenant run — full onboarding through 90-day consulting agency lifecycle. Re-run after v1 used wrong vertical profile.
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

## Carry-Forward Watch List (from 2026-04-14 archive)

These are architectural gaps expected to recur on this run — log fresh GAP IDs if they reproduce, then defer:

- **Retainer primitive missing** (GAP-C-07, GAP-C-09 in v1) — no native retainer entity; manual project-per-cycle workaround. HIGH severity but out-of-scope for a QA cycle.
- **Retainer invoice format** (GAP-C-08 in v1) — retainer invoices indistinguishable from project invoices (no hours consumed/remaining summary).

## Gap Tracker

| GAP_ID | Day / Checkpoint | Severity | Status | Summary | Owner | Retries |
|--------|------------------|----------|--------|---------|-------|---------|
| GAP-C-01 | D0 / 0.10 | HIGH | VERIFIED | `INDUSTRY_TO_PROFILE` missing Marketing/Consulting → consulting-za entries. Fix (PR #1053) confirmed live: Marketing → `consulting-za`, all 8 expected packs installed. | Dev | 0 |
| GAP-C-02 | D0 / 0.13,0.23 | MED | OPEN | KC invite link is single-use but a pre-existing KC session on the same browser triggers "already authenticated as different user" error, consuming the token. No recovery path in-product (retry shows "The link you clicked is no longer valid"). Users lock themselves out on first click. Product should force-logout KC session or offer "sign in as different user" CTA. | Product | 0 |
| GAP-C-03 | D0 / 0.16,0.28,0.56 | MED | OPEN | `en-ZA-consulting` terminology override only applies "Customer → Client" in sidebar. "Time Entry → Time Log" and "Rate Card → Billing Rates" are NOT applied (sidebar still shows "Time Tracking" and "Rates & Currency"). | Product | 0 |
| GAP-C-04 | D0 / 0.17 | MED | OPEN | `TeamUtilizationWidget` shows "Unable to load utilization data." Does **NOT** self-heal after 9h logged across 3 entries + 2 contributors (verified D1–D7). Root cause likely billable-rate dependency — widget 500s whenever rate lookup fails. Should degrade to hours-only mode or render actionable "Assign billing rates to unlock utilization" CTA. Severity should arguably move MED → HIGH since it breaks the Day 75 wow moment regardless of data volume. | Dev | 0 |
| GAP-C-05 | D0 / 0.29 | MED | OPEN | `rate-pack-consulting-za` seeds 8 billing_rates at correct ZAR amounts but Settings > Rates & Currency UI has no "Role" column — just member-scoped rows. Cannot surface "Creative Director — R1,800/hr" etc. Either surface role-defaults in UI, or align scenario with current data model. | Product | 0 |
| GAP-C-06 | D0 / 0.31 | MED | OPEN | `cost_rates` table empty after consulting-za profile seeding. Rate pack seeder populated `billing_rates` but did not load cost-rate defaults. | Dev | 0 |
| GAP-C-07 | D0 / 0.51 | LOW | OPEN | Settings > Automations UI shows "Automation Rule Builder is not enabled" feature-flag gate even though consulting-za installed 6 rules. Should allow viewing/listing pack-installed rules without the feature flag. | Product | 0 |
| GAP-C-08 | D0 / 0.57 | LOW | OPEN | `/trust-accounting` throws "Something went wrong" (generic error boundary). `/court-calendar` and `/conflict-check` correctly render "Module Not Available". Inconsistent progressive-disclosure handling. | Dev | 0 |
| GAP-C-09 | D2 / 2.4, D5 / 5.7, D1 / 1.4 | MED | OPEN | Project templates' custom-field defaults + budget defaults do NOT flow into the created project. `campaign_type` stays empty on Website Build + Brand Identity despite templates' `matterType: WEBSITE_BUILD` / `BRAND_IDENTITY`. Budget tab remains "No budget configured" despite templates carrying 120h/R120k / 80h/R110k defaults. Additionally: conditional visibility (`msa_start_date` hidden when `msa_signed == false`) is not wired on Create Customer Step 2. Will cascade into Day 9+ retainer templates (`retainer_tier` auto-fill) and Day 42+ retainer clone flows. | Dev | 0 |
| GAP-C-10 | D4 (exec friction) | LOW | OPEN | Keycloak SSO session persists across the in-app "Log Out" flow. Gateway BFF /logout endpoint + KC realm /logout do not invalidate the gateway session cookie for the purposes of the next browser request. QA agent worked around by calling KC admin API user-logout + closing/reopening the browser. Not a prod user-facing blocker but affects demo handoffs, support sessions, and QA scripting. | Product | 0 |

## Legend

- **Status**: OPEN -> SPEC_READY -> FIXED -> VERIFIED | REOPENED | STUCK | WONT_FIX | RESOLVED
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-17 — Cycle initialized. V1 (2026-04-14) archived to `_archive_2026-04-14_consulting-v1-wrong-profile/` — that run hit the industry → profile mapping bug and silently fell back to `consulting-generic`, invalidating 6 downstream profile-content gaps.
- 2026-04-17 — Orchestrator pre-diagnosed GAP-C-01 root cause: `AccessRequestApprovalService.java:29-32` has `INDUSTRY_TO_PROFILE` map with only "Accounting" and "Legal Services". "Marketing" and "Consulting" resolve to null. Fix spec written to `qa_cycle/fix-specs/GAP-C-01.md` directly (skipping Product triage since evidence is unambiguous).
- 2026-04-17 — Dev Agent: GAP-C-01 fixed via PR #1053, merged. Backend needs restart to pick up mapping change.
- 2026-04-17 10:16 SAST — Infra Agent: Dev stack READY. Docker infra (postgres, keycloak, mailpit, localstack) was already up (13h uptime, healthy). Local services: stale 10h-old backend on port 8080 was holding the port so the svc.sh restart failed silently — killed the stale java PID 41862, started fresh backend (PID 72337, up in 9.2s) which loaded `consulting-za` vertical profile and picked up PR #1053. Gateway was down (stale) — started fresh (PID 71302). Portal was down — started fresh (PID 71100). Frontend (PID 15582, 13h Next.js dev server) left running since HMR handles code changes. All health endpoints return UP / 200. No ERROR entries in backend log after "Started BackendApplication". Non-fatal warnings only (LibreOffice missing → docx4j fallback; Hibernate dialect auto-detect suggestion; CGLIB proxying notes).
- 2026-04-17 10:40 SAST — QA Agent: Day 0 **DEGRADED PASS** through all phases A-I. **GAP-C-01 VERIFIED**: industry "Marketing" correctly resolves to `consulting-za`, all 8 expected packs (`consulting-za-customer`, `consulting-za-project`, `consulting-za` template, `consulting-za-clauses`, `consulting-za-creative-brief`, `automation-consulting-za`, `rate-pack-consulting-za`, `consulting-za-project-templates`) installed. Vertical profile dropdown in Settings>General shows "South African Agency & Consulting Firm". All 3 users (Zolani/Owner, Bob/Admin, Carol/Member) registered and can log in. 7 new gaps opened (GAP-C-02..08), none blocking. Key findings: invite-link single-use trap (GAP-C-02), terminology override only partially applied (GAP-C-03), utilization widget error vs empty state (GAP-C-04), rate pack UI mismatch (GAP-C-05). Results: `qa_cycle/checkpoint-results/day-00.md`. Screenshots: 3 files in `checkpoint-results/`. Next: Day 1 — 1.1.
- 2026-04-17 11:06 SAST — QA Agent: Days 1–7 **PARTIAL PASS**, no blockers. BrightCup Coffee Roasters client created (Prospect → Onboarding → Active), 2 projects created (Website Design & Build, Brand Identity Refresh), **9.0 hours** of time logged across 3 entries by Bob (2h Discovery) and Carol (3h Wireframes + 4h Page Design). Budget on Website Build configured at 120h / R120,000 with 4% consumption. **GAP-C-04 does NOT self-heal** after 9h logged — same "Unable to load utilization data." error. Root cause appears to be billable-rate dependency (members have no rates → 500 on utilization endpoint). 2 new gaps opened: GAP-C-09 (MED, Dev) — project template custom-field + budget defaults do not flow into created projects; conditional field visibility on `msa_start_date` not wired. GAP-C-10 (LOW, Product) — KC SSO session sticky across in-app logout. Results: `qa_cycle/checkpoint-results/days-01-07.md`. Next: Day 8 — 8.1 (Zolani creates Ubuntu Startup retainer client).
