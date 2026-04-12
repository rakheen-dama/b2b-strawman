# QA Cycle Status — Legal-ZA 90-Day Demo Walkthrough (Keycloak) — 2026-04-12

## Current State

- **QA Position**: Day 0 blocked at CP 0.15 — GAP-D0-01 (HIGH) — waiting for triage. Day 0 Phases A–C executed (CP 0.1–0.25), GAP-D0-01 surfaced at first Approve attempt, manually worked past it to capture downstream evidence through first login. Day 0 Phases D–K (team invites, settings, rates, custom fields, templates, modules, trust account, billing) NOT executed — will resume after GAP-D0-01 is FIXED. 7 new gaps total (1 HIGH, 2 MED, 4 LOW).
- **Cycle**: 1 (fresh)
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_demo_legal_2026-04-12`
- **Scenario**: `qa/testplan/demos/legal-za-90day-keycloak.md`
- **Focus**: 90-day legal-ZA demo walkthrough executed end-to-end against the real Keycloak dev stack. Goal is to prove the scripted customer demo runs clean — access request → admin approval → KC registration → plan upgrade → legal-za profile → team invites → 3 client lifecycles (Litigation, Deceased Estate, RAF) → engagement letters, trust accounting, court calendar, adverse parties, activity feed, audit sign-off. Any sharp edges that break the demo narrative become fix-spec targets.
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
| GAP-D0-01 | Day 0 / CP-0-15 | HIGH | FIXED | `KeycloakProvisioningClient.findOrganizationByAlias` broken — KC `/organizations?search=alias&exact=true` matches name not alias, so 409 retry path crashes with `IllegalStateException` whenever a stale KC org exists. Blocks idempotent re-approval. | Dev | 0 |
| GAP-D0-02 | Day 0 / CP-0-06 & CP-0-18 | LOW | SPEC_READY | OTP + invitation emails still branded "DocTeams" (subject, from `noreply@docteams.local`, body); should be Kazi / `noreply@kazi.africa`. | Dev | 0 |
| GAP-D0-03 | Day 0 / CP-0-10 | LOW | SPEC_READY | Keycloak realm theme still says "DocTeams" (page title, heading "Sign in to DocTeams", footer "© 2026 DocTeams"). | Dev | 0 |
| GAP-D0-04 | Day 0 / CP-0-14 | LOW | WONT_FIX | No detail drill-down view on access-request rows; scenario step says "click into the request → verify detail view". Inline row exposes all fields, so content requirement satisfied. Deferred — new UI surface, out of scope for Cycle 1. | Product | 0 |
| GAP-D0-05 | Day 0 / CP-0-04 | LOW | FIXED | Industry dropdown says "Legal" instead of "Legal Services" — scenario mismatch (minor copy drift). | Dev | 0 |
| GAP-D0-06 | Day 0 / CP-0-22 | MED | FIXED | Post-registration redirect lands on `/?code=...` marketing landing page instead of `/org/{slug}/dashboard`. Session cookie is set but the code param is discarded and user must manually navigate. | Dev | 0 |
| GAP-D0-07 | Day 0 / CP-0-23 | MED | FIXED | Sidebar + breadcrumb show org **slug** (`MATHEBULA-PARTNERS`) instead of display name (`Mathebula & Partners`). Breaks demo polish. | Dev | 0 |

## Legend

- **Status**: OPEN → SPEC_READY → FIXED → VERIFIED | REOPENED | STUCK | WONT_FIX
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-12 — Cycle 1 initialized. Prior cycle (KC 2026-04-12, legal-onboarding scenario) closed as ALL_DAYS_COMPLETE and archived to `qa_cycle/_archive_2026-04-12_legal-onboarding-kc/`. Branch `bugfix_cycle_demo_legal_2026-04-12` created from main. Scenario: `qa/testplan/demos/legal-za-90day-keycloak.md`.
- 2026-04-12 — Infra Turn 1: Verified KC dev stack health — all 4 Docker containers (b2b-postgres, b2b-keycloak, b2b-localstack, b2b-mailpit) reporting healthy (Up 17 hours). Keycloak `docteams` realm responds 200. svc.sh reports backend/gateway/frontend/portal all running and healthy (PIDs 55326/55489/90632/90679). Endpoint spot-checks: backend :8080 /actuator/health UP, gateway :8443 /actuator/health UP, frontend :3000 HTTP 200, portal :3002 HTTP 200, Mailpit API returning messages. No services required starting. Dev Stack READY for QA walkthrough.
- 2026-04-12 — QA Turn 1 (Day 0): 21 pass, 1 fail (CP 0.15 on first attempt), 3 partial. Pre-run cleanup found residual state from prior cycle (3 KC users, 1 KC org, 1 org row, 1 access_request, 1 tenant schema) — cleaned via Admin API + psql. New gaps: GAP-D0-01 (HIGH), GAP-D0-02 (LOW), GAP-D0-03 (LOW), GAP-D0-04 (LOW), GAP-D0-05 (LOW), GAP-D0-06 (MED), GAP-D0-07 (MED). Stopping point: CP 0.25 (dashboard wow-moment screenshot captured). Day 0 Phases D–K not executed this turn — blocked on GAP-D0-01 triage. Full details in `qa_cycle/checkpoint-results/day-00.md`.
- 2026-04-12 — Product Turn 1: triaged 7 Day 0 gaps. Verified every root cause by reading referenced source files. SPEC_READY: GAP-D0-01 (HIGH — alias lookup uses wrong KC search semantic, fix: list-and-match client-side), GAP-D0-02 (branding strings in AccessRequestService + application.yml + realm-export.json), GAP-D0-03 (Keycloak login theme TSX + index.html + realm displayName), GAP-D0-05 (one-word label change in lib/access-request-data.ts), GAP-D0-06 (KC org `redirectUrl` set to bare `frontendBaseUrl`; change to `frontendBaseUrl + "/dashboard"` so frontend middleware + gateway OAuth2 handle the code exchange), GAP-D0-07 (sidebar + breadcrumb need org display name piped through `/bff/me` → `AuthContext` → `DesktopSidebar`). WONT_FIX: GAP-D0-04 — introducing a new detail drawer/page is a scope-expanding UI add; inline row already exposes all fields required by the scenario, so content requirement is met. Priority order for Dev: GAP-D0-01 → GAP-D0-06 → GAP-D0-07 → GAP-D0-05 → GAP-D0-02 → GAP-D0-03. Total specs written: 6. Fix specs in `qa_cycle/fix-specs/`.
- 2026-04-12 — Dev Turn 1: GAP-D0-01 FIXED via PR #1013 (squash commit b62da54c). `KeycloakProvisioningClient.findOrganizationByAlias` now fetches orgs and filters on the `alias` field client-side, with a list-all fallback for the anomaly path. Added `KeycloakProvisioningClientTest` (WireMock-based, 4/4 green) covering alias match among name-similar orgs, list-all fallback, no-match error, and happy path. Backend changed → NEEDS_REBUILD=true.
- 2026-04-12 — Infra Turn 2: backend restarted after GAP-D0-01 merge (PR #1013). Old PID 55326 → new PID 83247. Health UP (`/actuator/health` → `{"status":"UP"}`). svc.sh status: backend/gateway/frontend/portal all running+healthy. KC orgs in `docteams` realm: `['mathebula-partners', 'moyo-dlamini-attorneys', 'qa-verify-corp', 'thornton-associates']` — note `mathebula-partners` is still present (QA Turn 1 cleanup did not remove it, or it was recreated); this gives QA a live stale-org scenario to re-verify GAP-D0-01 against. NEEDS_REBUILD cleared (false).
- 2026-04-12 — Dev Turn 2: GAP-D0-06 FIXED via PR #1014 (squash commit 9639a4f6). `KeycloakProvisioningClient.createOrganization` now sets KC org `redirectUrl` to `frontendBaseUrl + "/dashboard"` instead of bare `frontendBaseUrl`. New `organizationRedirectUrl` field derived in constructor with trailing-slash guard. 16 tests pass (AccessRequestApprovalServiceTest: 9, PlatformAdminControllerTest: 7). Backend changed → NEEDS_REBUILD=true.
- 2026-04-12 — Dev Turn 3: GAP-D0-07 FIXED via PR #1015 (squash commit 8d1cc50d). Added `orgName` field to `SettingsResponse` DTO, populated from `public.organizations` table via `OrganizationRepository.findByExternalOrgId(RequestScopes.ORG_ID)`. Frontend sidebar (desktop + mobile) and breadcrumbs now render `orgName` with fallback to slug. 7 files changed across backend + frontend. Build green: backend compile clean, frontend lint 0 errors, build green, 1932 tests pass. NEEDS_REBUILD=true (backend changed).
- 2026-04-12 — Dev Turn 4: GAP-D0-05 FIXED via PR #1016 (squash commit cab293bd). Renamed industry label "Legal" to "Legal Services" in `frontend/lib/access-request-data.ts` + updated test fixture. Frontend-only, HMR picks it up.
- 2026-04-12 — Infra Turn 3: backend restarted after D0-06+D0-07 fixes (PRs #1014, #1015). Old PID 83247 → new PID 95751. Health UP (`/actuator/health` → `{"status":"UP"}`). svc.sh status: backend/gateway/frontend/portal all running+healthy. NEEDS_REBUILD cleared.
