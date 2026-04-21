# QA Cycle Status — Legal ZA Full Lifecycle (Firm + Portal Interleaved, Keycloak) — 2026-04-21

## Current State

- **ALL_DAYS_COMPLETE**: false
- **QA Position**: Day 0 — 0.1 (open fresh browser context on `http://localhost:3000` and begin access-request flow as Thandi Mathebula). CLEARED to start — GAP-INFRA-01 cleanup complete + verified.
- **Cycle**: 1
- **Dev Stack**: READY — all 4 services UP (8080/8443/3000/3002), KC `docteams` realm reachable, Mailpit purged, no `mathebula-partners` org or tenant schema remaining.
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_legal_full_2026-04-21`
- **Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`
- **Focus**: First unified firm + client-POV 90-day legal lifecycle on a single `mathebula-partners` / `legal-za` tenant. Interleaves firm-side Keycloak flows with portal-side magic-link flows across 23 scripted days (0, 1, 2, 3, 4, 5, 7, 8, 10, 11, 14, 15, 21, 28, 30, 45, 46, 60, 61, 75, 85, 88, 90). Includes a dedicated BLOCKER-severity **isolation check** on Day 15 (Sipho must not see Moroka Family Trust data) and a repeat isolation probe on Day 90.
- **Auth Mode**: Keycloak (real OIDC) for firm; magic-link + portal JWT for portal (`:3002`)

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend (kc mode) | http://localhost:3000 | UP — HTTP 200 (already running, PID 62385) |
| Backend (local+keycloak profile) | http://localhost:8080 | UP — `{"status":"UP"}` (started fresh this cycle, PID managed by svc.sh) |
| Gateway (BFF) | http://localhost:8443 | UP — `{"status":"UP"}` (already running external, PID 71426) |
| Portal | http://localhost:3002 | UP — HTTP 307 root redirect expected (started fresh this cycle) |
| Keycloak | http://localhost:8180 | UP — `/realms/docteams` 200; bootstrap re-run idempotent, padmin user confirmed |
| Mailpit UI | http://localhost:8025 | UP — inbox purged (was 18 stale msgs) for Day 0 clean state |
| Postgres (docteams) | localhost:5432 | UP — `b2b-postgres` container healthy, DB `docteams` reachable |
| LocalStack (S3) | http://localhost:4566 | UP — container `b2b-localstack` healthy (4 days uptime) |

## Carry-Forward Watch List (from prior legal-za archives)

These are gaps/observations logged during `_archive_2026-04-21_legal-full-lifecycle` and earlier legal-za runs. If they reproduce, log fresh GAP IDs referencing the archive. Portal-specific watch items pulled forward because this is the first script to interleave firm + portal POVs on a single tenant.

### Firm-side (from 90-day legal archive)
- **Keycloak org-invite bounce-page mechanics** (prior GAP-L-01) — invite URL must route through `/accept-invite` to avoid same-profile cookie collision + single-use token consumption. Allow-list covers both `/login-actions/action-token` and `/protocol/openid-connect/registrations`. Regression risk if allow-list is tightened or KC endpoint shape changes.
- **Keycloak end-session confirmation page** (prior GAP-L-04, OPEN) — no `id_token_hint` causes one-click logout confirmation even without active session. Adds nuisance click to every invite-email recipient. Still LOW, but surfaces again on every fresh registration in Day 0.
- **Disbursements "Add / Log Expense" button missing** (prior GAP-L-06, OPEN, ADR-247) — Day 21 Phase B requires this button; scenario will hit it if not yet shipped.
- **Matter closure CLOSED state + pre-closure gates** (prior GAP-L-07, OPEN, ADR-248) — Day 60 Phase B requires all gates GREEN (unbilled time, trust balance, fee notes, tasks). Close-Matter flow must expose each gate.
- **Retention clock field + stamp-once semantics** (prior GAP-L-08, VERIFIED via V99 migration) — Day 60 Phase B Checkpoint 10.11 asserts `end_date = today + 5 years` per ADR-249. Partial-verified previously (code + DB only); Day 60/85 in this script will validate the UI path.
- **Statement of Account template** (prior GAP-L-09, OPEN, ADR-250) — Day 60/61 require the SoA PDF to render with Mathebula letterhead, banking details, trust ledger reconciliation, VAT. Fee-note template regression risk (prior runs used generic "Invoice" template).
- **Acceptance-eligible manifest flag on doc templates** (prior GAP-L-10, OPEN, ADR-251) — Day 7/8 proposal acceptance depends on this flag or its fallback (ENGAGEMENT_LETTER category).
- **Audit log UI** (prior GAP-L-11, OPEN) — Day 85 Checkpoint 85.4 requires filterable audit by matter + actor (including portal contacts). `/audit` / `/settings/audit` all returned 404 previously.
- **ZAR `/profitability` hydration via `@formatjs/intl-numberformat`** (prior GAP-L-12, VERIFIED) — polyfill depends on `pnpm install` being run post-merge. Infra-state regression risk after dependency updates.
- **Activity feed `"unknown"` task title** (prior GAP-L-05, VERIFIED via `putIfAbsent("title", ...)`) — regression risk on any `TaskService` refactor.
- **Untranslated Keycloak i18n keys** (prior GAP-L-02/L-03, VERIFIED) — `Info.tsx` / `Register.tsx` now call `advancedMsgStr(messageHeader)`. Regression risk on KC theme rebuilds.
- **Tariff-rate naming** (prior OBS-L-05) — VAT entry was seeded as "Standard"; verify in Day 1 settings walk that tariff names are explicit.
- **Post-registration session handoff** (prior OBS-L-06) — KC issued session_state for `account` client; explicit re-login required to land on `gateway-bff` session. May surface on Day 0 Phase C and Phase D.
- **Admin `APPROVE_TRUST_PAYMENT` capability** (prior OBS-L-13/L-26) — Admin role lacks this; only Owner can approve. Impacts Day 10 trust-deposit dual approval if flow uses Bob-as-Admin.
- **Matter closure gate error-text terminology leak** (prior Day 75 note) — "Project has 9 open task(s)" used `Project` terminology under legal-za profile. Check Day 60 Phase B gate-report copy.

### Portal-side (first QA run against this tenant — no prior legal-za portal archive; pulling generalised watch items)
- **Portal service staleness on `:3002`** (prior Day 68 BLOCKED — portal was stale, out of QA scope to restart). Infra Agent must confirm portal is UP in Session 0.
- **Portal magic-link email subject consistency** — Day 4/8/11/30/46/61/75 all depend on Mailpit delivering emails with stable subject keywords ("sign in", "action required", "proposal", "trust deposit", "fee note", "Statement of Account", "weekly update"). Regression risk if copy changes.
- **Portal org-slug override** — `PORTAL_ORG_SLUG=mathebula-partners` (not default `e2e-test-org`). Must be set if any portal E2E helper is used.
- **Portal currency rendering** — Day 11/30/46/61 all assert ZAR formatting on `/trust`, `/invoices`; uses same Node small-icu path as firm-side `/profitability`. If polyfill is not imported on the portal bundle, expect the same hydration issue.
- **Description sanitisation on portal `/trust`** — Day 11.5 asserts `[internal]` tags stripped, client-safe copy ≤ 140 chars. Regression risk.
- **Isolation (BLOCKER-severity)** — Day 15 Phases A–D + Day 90 final probe. Any portal list, URL, or API response that returns Moroka data to Sipho is an immediate BLOCKER. Verify at frontend (404/403/redirect), backend (`/portal/api/*` → 403/404), and digest-email levels.
- **Activity-trail / digest cross-entity leak** — Day 15 Phase D + Day 75 + Day 88 assert digest and portal activity view contain zero Moroka references.
- **Firm-side audit event for portal doc access** — Day 61 Checkpoint 61.9 asserts the audit log records Sipho's SoA download. Couples to GAP-L-11 (Audit log UI missing) — audit event must exist even if UI does not.

## Gap Tracker

| GAP_ID | Day / Checkpoint | Severity | Status | Summary | Owner | Retries |
|--------|------------------|----------|--------|---------|-------|---------|
| GAP-INFRA-01 | Session 0 / 0.C | HIGH | VERIFIED | Pre-existing `mathebula-partners` org (KC id `d49948bb-8dca-4ee3-8a87-4ff97e63ae2b`, registry id `3730e32a-5310-43e3-b001-495def2b524d`) + tenant schema `tenant_5039f2d497cf` remained from prior archived lifecycle run. Cleaned up 2026-04-21 19:22 SAST: deleted 3 KC users (Bob/Carol/Thandi), KC org, `subscriptions` row `ec8b9574…`, `access_requests` row `4b9c3030…`, `org_schema_mapping` row `bd05eca8…`, `organizations` row `3730e32a…`, and `DROP SCHEMA tenant_5039f2d497cf CASCADE` (103 objects). Post-checks: no mathebula org in KC, 0 `@mathebula-test.local` users, 0 registry rows, schema gone, other 3 tenant schemas intact, all services UP. | Infra | 0 |

## Legend

- **Status**: OPEN -> SPEC_READY -> FIXED -> VERIFIED | REOPENED | STUCK | WONT_FIX | RESOLVED
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-21 18:59 SAST — Cycle 1 started. Archived prior legal-90day state. Fresh status seeded from legal-za-full-lifecycle-keycloak.md.
- 2026-04-21 19:04 SAST — Infra verification complete. Stack: Docker infra already up (KC, Postgres, LocalStack, Mailpit all healthy 3-4d uptime); gateway (PID 71426) + frontend (PID 62385) already running; backend + portal were stale and started fresh via `svc.sh start backend portal` (ready 30s + 3s). All four services pass health checks (8080/8443/3000/3002). Keycloak bootstrap re-run (idempotent) — padmin and gateway-bff mappers OK. Session 0 prep: 0.A ✓ (all svc UP), 0.B ✓ (portal 307 on `/`), 0.C BLOCKER (pre-existing `mathebula-partners` org + schema `tenant_5039f2d497cf` — see GAP-INFRA-01), 0.D ✓ (0 KC users matching `mathebula-test`), 0.E n/a (`public.portal_contacts` table does not exist — portal contacts live per-tenant, so dropping the mathebula schema in 0.C would cover this), 0.F ✓ (Mailpit purged, was 18 stale), 0.G pending QA/Product (PayFast sandbox vs stub — not infra), 0.H ✓ (backend logs confirm legal-za packs installed + reconciled for tenant_5039f2d497cf; 4 tenants reconciled 4 succeeded 0 failed). QA NOT cleared to start Day 0 until GAP-INFRA-01 resolved.
- 2026-04-21 19:22 SAST — Infra cleanup turn (GAP-INFRA-01 → VERIFIED). Re-verified live Keycloak state via admin REST (token from master realm). **Correction to prior snapshot**: live KC org id was `d49948bb-8dca-4ee3-8a87-4ff97e63ae2b` (not `3730e32a…` — that was the Postgres `organizations.id`); 3 users with `@mathebula-test.local` existed (not 0): Bob Ndlovu `79fb8ec4…`, Carol Mokoena `9110900e…`, Thandi Mathebula `e57609a8…`, all MANAGED members of the org. Deleted in order: (1) 3 KC users (HTTP 204 each), (2) KC org `d49948bb…` (HTTP 204), (3) `subscription_payments` (0 rows), `subscriptions` row `ec8b9574-028d-433f-a11e-b6e4823e5c34` (TRIALING, no payments — surfaced on first DELETE attempt via FK; expected for a provisioned org), `access_requests` row `4b9c3030-aed6-494c-aca0-f8fc3442fd73`, `org_schema_mapping` row `bd05eca8-1de1-4493-9645-4904eec5627e`, `organizations` row `3730e32a-5310-43e3-b001-495def2b524d` — all in one transaction, COMMIT OK, (4) `DROP SCHEMA tenant_5039f2d497cf CASCADE` — dropped 103 objects. Post-checks: KC org list empty for `mathebula`, 0 `@mathebula-test.local` users, registry counts all 0, schema gone, 3 other tenant schemas (`2a96bc3b208b`, `8ee5c5a6e45f`, `f6e34f99f3b9`) intact. Health: backend `{"status":"UP"}`, gateway `{"status":"UP"}`, KC realm `/realms/docteams` HTTP 200, frontend HTTP 200, portal HTTP 307, Mailpit purged (HTTP 200). Dev Stack → READY. QA cleared to start Day 0 Phase A.
