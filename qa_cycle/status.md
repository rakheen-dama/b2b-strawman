# QA Cycle Status — Consulting Agency 90-Day Demo (Fresh Tenant, Keycloak) — 2026-04-17

## Current State

- **QA Position**: Day 8 — 8.1 (Zolani creates Ubuntu Startup retainer client)
- **Cycle**: 1
- **Dev Stack**: READY
- **NEEDS_REBUILD**: true (GAP-C-04+C-07 fix PR #1055 merged — backend restart required so V97 runs against existing consulting-za tenant to enable `resource_planning` + `automation_builder` modules)
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
| GAP-C-02 | D0 / 0.13,0.23 | MED | OPEN | KC invite link is single-use but a pre-existing KC session on the same browser triggers "already authenticated as different user" error, consuming the token. No recovery path in-product (retry shows "The link you clicked is no longer valid"). Deferred: requires Keycloak registration-flow UX design (detect error, surface "Sign in as different user" CTA, coordinate with gateway BFF logout). Workaround documented (incognito window per invite). Not a blocker for Day 8+ QA since all 3 users already enrolled. | Product | 0 |
| GAP-C-03 | D0 / 0.16,0.28,0.56 | MED | SPEC_READY | Terminology map missing keys for settings sidebar labels. Spec: `qa_cycle/fix-specs/GAP-C-03.md`. Two-line addition to `frontend/lib/terminology-map.ts`. | Dev | 0 |
| GAP-C-04 | D0 / 0.17 | HIGH | FIXED | Fixed via PR #1055 (squash `7c64d3ee`): added `resource_planning` to consulting-za `enabledModules` + V97 tenant backfill migration. Awaits backend restart + QA re-verify at Day 8. | Dev | 0 |
| GAP-C-05 | D0 / 0.29 | HIGH | FIXED | Fixed via PR #1054 (squash `2e080193`): `VerticalProfileRegistry` parses `rateCardDefaults`; `MemberRateSeedingService` seeds billing+cost rates on JIT sync; V96 backfill migration covers existing members. 4633 tests green. Awaits backend restart + QA re-verify at Day 8. | Dev | 0 |
| GAP-C-06 | D0 / 0.31 | HIGH | FIXED | Same fix as GAP-C-05 (PR #1054). `cost_rates` now seeded per-member on JIT sync. | Dev | 0 |
| GAP-C-07 | D0 / 0.51 | LOW | FIXED | Fixed in same PR as GAP-C-04 (#1055): added `automation_builder` to consulting-za `enabledModules`, backfilled via V97. | Dev | 0 |
| GAP-C-08 | D0 / 0.57 | LOW | SPEC_READY | `/trust-accounting` calls `notFound()` instead of rendering inline "Module Not Available" like `/court-calendar`. Spec: `qa_cycle/fix-specs/GAP-C-08.md`. ~10 line frontend change. | Dev | 0 |
| GAP-C-09 | D2 / 2.4, D5 / 5.7, D1 / 1.4 | MED | SPEC_READY | **Split into three sub-issues**: (c) conditional visibility of `msa_start_date` — `IntakeFieldResponse` DTO drops `visibilityCondition` field; 20-min fix specced. (a) template `matterType` → project `campaign_type` auto-fill — deferred, needs new `matter_type` column on project_templates + new project-creation logic (~4h, L). (b) template budget defaults — deferred, needs new `default_budget_*` columns + JSON schema change (~2h, M). Spec: `qa_cycle/fix-specs/GAP-C-09.md` covers (c) only; (a)+(b) deferred per spec body. | Dev | 0 |
| GAP-C-10 | D4 (exec friction) | LOW | OPEN | Gateway already uses `OidcClientInitiatedLogoutSuccessHandler` w/ id_token_hint; root cause of observed stickiness not immediately clear from code — likely browser cookie TTL or KC session-cookie flag interplay. Deferred: requires live repro + network trace before a spec can land. Workaround (KC admin API logout + browser close) documented in days-01-07.md. Not a prod user-facing blocker. | Product | 0 |

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
- 2026-04-17 11:30 SAST — Product Agent: Triaged GAP-C-02..C-10. **Escalated C-04/C-05/C-06 MED → HIGH** (cascading blockers for Day 34/36/75). Wrote 6 fix specs (`GAP-C-03`, `GAP-C-04`, `GAP-C-05-C-06` combined, `GAP-C-07`, `GAP-C-08`, `GAP-C-09`). Deferred C-02 (KC registration-flow UX redesign) and C-10 (needs live repro). Deferred GAP-C-09 sub-issues (a) and (b) — template matter-type + budget auto-fill need new DB columns + ~4–6h service work; only sub-issue (c) conditional-visibility is in-scope (<2h DTO change). **Major root-cause correction on GAP-C-04**: QA hypothesis was "rate-dependency" but grep shows it's `moduleGuard.requireModule("resource_planning")` throwing 403 because consulting-za profile has `enabledModules: []`. Independent of C-05/C-06 — fixable with a one-line JSON change. **Root-cause surprise on GAP-C-05**: `BillingRateService.resolveRate()` cascades PROJECT → CUSTOMER → MEMBER overrides but has **no `ORG_DEFAULT` branch** — the 8 seeded rate-pack rows (memberId=null) are literally unreachable dead data. Fix uses profile JSON's `rateCardDefaults` (already role-aligned with org roles) to seed member-default rates on JIT sync. **Root-cause surprise on GAP-C-09(c)**: `IntakeFieldResponse` DTO drops `visibilityCondition` field even though `FieldDefinitionResponse` preserves it, frontend wires the logic correctly, and the field pack JSON defines it. Two-line backend DTO fix. Dev loop should pick up in this cascade-priority order: **(1) GAP-C-05+C-06 (rate cascade unblocks Day 34/36/75), (2) GAP-C-04 (unblocks Bob/Carol dashboards starting Day 8, plus Day 75), (3) GAP-C-09(c) (prevents bad data during Day 8+ client creation), (4) GAP-C-07 + GAP-C-03 + GAP-C-08** (cosmetic / low-risk polish — can batch with one JSON change to consulting-za.json).
- 2026-04-17 11:06 SAST — QA Agent: Days 1–7 **PARTIAL PASS**, no blockers. BrightCup Coffee Roasters client created (Prospect → Onboarding → Active), 2 projects created (Website Design & Build, Brand Identity Refresh), **9.0 hours** of time logged across 3 entries by Bob (2h Discovery) and Carol (3h Wireframes + 4h Page Design). Budget on Website Build configured at 120h / R120,000 with 4% consumption. **GAP-C-04 does NOT self-heal** after 9h logged — same "Unable to load utilization data." error. Root cause appears to be billable-rate dependency (members have no rates → 500 on utilization endpoint). 2 new gaps opened: GAP-C-09 (MED, Dev) — project template custom-field + budget defaults do not flow into created projects; conditional field visibility on `msa_start_date` not wired. GAP-C-10 (LOW, Product) — KC SSO session sticky across in-app logout. Results: `qa_cycle/checkpoint-results/days-01-07.md`. Next: Day 8 — 8.1 (Zolani creates Ubuntu Startup retainer client).
- 2026-04-17 12:02 SAST — Orchestrator (acting as Dev): GAP-C-05+GAP-C-06 fixed via PR #1054, merged as `2e080193`. Parses `rateCardDefaults` from `consulting-za.json` in `VerticalProfileRegistry`; new `MemberRateSeedingService` seeds billing+cost rates on JIT sync via hook in `MemberSyncService`; V96 tenant backfill migration for existing members. Full `./mvnw verify`: **4633 tests, 0 failures, 0 errors** (10m45s). Worktree PR created by orchestrator after original Dev agent session ended mid-verify; code from that session validated as passing before commit. NEEDS_REBUILD flagged — backend restart required for both the new JIT seeding hook AND to run V96 on the existing tenant schema.
- 2026-04-17 12:06 SAST — Infra Agent: Backend restarted for GAP-C-05+C-06 fix. Killed stale PID 72337, started fresh PID 12346 (etime 15s, startup 9.251s). V96 migration ran against tenant_2a96bc3b208b (confirmed in `flyway_schema_history`, `success=t` at 12:05:22 SAST), backfilled 3 billing_rates and 3 cost_rates. Zolani/Bob/Carol now have rates assigned. V96 also ran on the other two tenant schemas (tenant_8ee5c5a6e45f, tenant_f6e34f99f3b9). No ERROR entries in backend log after "Started BackendApplication". Frontend/gateway/portal not touched — still UP.
- 2026-04-17 12:16 SAST — Orchestrator (acting as Dev): GAP-C-04 + GAP-C-07 fixed via batched PR #1055, merged as `7c64d3ee`. Single JSON change — added `resource_planning` and `automation_builder` to consulting-za `enabledModules`. V97 tenant migration backfills both modules for existing consulting-za tenants (idempotent via jsonb containment; preserves other manually-enabled modules; profile-scoped so legal-za / accounting-za unaffected). Focused tests green (ConsultingZaProfileProvisioningTest + VerticalProfileRegistryTest, 17/17). Full verify skipped this iteration given low-risk scope (1 JSON + 1 additive migration + extended tests). NEEDS_REBUILD flagged — backend restart required for V97 to run against `tenant_2a96bc3b208b`.
