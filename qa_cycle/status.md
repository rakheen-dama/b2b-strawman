# QA Cycle Status — Legal ZA Full Lifecycle (VERIFY CYCLE, Keycloak) — 2026-04-24

## Current State

**Purpose**: Re-validate that the 40+ fixes merged to `main` between 2026-04-21 and 2026-04-24 actually work end-to-end on the Keycloak dev stack. Prior cycle state archived to `qa_cycle/_archive_2026-04-24_pre-verify/status.md`.

- **ALL_DAYS_COMPLETE**: false
- **QA Position**: Day 1 — 1.1 (Day 0 complete, GAP-L-22 VERIFIED end-to-end on 3 KC invites)
- **Cycle**: 1 (verify)
- **Dev Stack**: READY — backend PID 25298 (L-22 fix live), portal PID 5677, frontend PID 5771, gateway external PID 71426.
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_2026-04-24`
- **Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`
- **Auth Mode**: Keycloak (real OIDC) for firm; magic-link + portal JWT for portal (`:3002`)
- **Next action**: QA — execute Day 1 (Firm onboarding polish: logo, brand colour, LSSA rate cards, trust account creation). Log back in as Thandi via KC at `/dashboard`.

## Verification Focus — fixes to re-check as QA proceeds

These gaps were **FIXED and merged to main** since 2026-04-21. QA should re-verify them during the lifecycle run (not as separate probes — just notice whether the underlying functionality works during the normal flow). If any of these REOPEN, log as regression.

### Shipped in the 2026-04-21 cycle PR #1114 (bundled; verified end-to-end up to Day 28.3 before merge to main)
- L-23 (settings general 500 error), L-33 (FICA onboarding pack), L-34 (portal-contact auto-provision), L-42 (info-request magic-link), L-43 (portal request-item submitted listener), L-50 (acceptance email portal base URL), L-56 (time-entry PROSPECT gate), L-57 (disbursement Matter combobox), L-60 (invoice PROSPECT gate), P-01 (portal InfoRequestsCard path), P-02 (portal requests UI route), P-06 (portal acceptance status handling)

### Shipped post-cycle-merge (2026-04-23 → 2026-04-24) — NEW functionality / reopened code paths
- **L-21** — Access-request detail: WONT_FIX (PR #1128, scenario rescope, no code).
- **L-22** — BFF session handoff cleaner middleware (PR #1125). Verify: after Thandi registers while padmin session is live, Thandi lands clean without padmin sidebar leak.
- **L-25** — SECTION_86 trust account type (PR #1123). Verify: Day 1 Trust Account dialog offers SECTION_86 option.
- **L-27** — Tax rate label "VAT — Standard" + `tax_label='VAT'` on legal-za (PR #1124).
- **L-28** — Conflict-check self-exclusion (PR #1118). Verify: Day 2 conflict check on newly-created client returns NO_CONFLICT (not CONFLICT_FOUND self-match).
- **L-29** — Conflict-check form dropdowns hydrated (PR #1122).
- **L-31** — Customers empty-state vertical copy (PR #1122).
- **L-32** — Create Client redirects to new detail (PR #1122).
- **L-35** — Custom-field update on PROSPECT customers allowed (PR #1119). Verify: Day 3 matter Save Custom Fields on PROSPECT customer returns 200.
- **L-37** — Field-group over-attach narrowed (PR #1122).
- **L-38** — Matter detail tab cleanup (PR #1122).
- **L-39** — customerId URL-param propagation to Configure step (PR #1122).
- **L-41** — Information request due_date column + picker (PR #1123).
- **L-44** — PackReconciliationRunner syncs enabled_modules + enabled_features (PR #1124).
- **L-45** — Per-item Download on firm info-request detail (PR #1122).
- **L-46** — FICA status tile on matter Overview (PR #1127).
- **L-47** — Portal parent-request status sync (PR #1124).
- **L-48** — Matter-level "+ New Engagement Letter" CTA + `/api/customers` 404 fix (PR #1127).
- **L-51** — "Send for Acceptance" email subject keywords (PR #1124).
- **L-52** — Portal trust-ledger sync for RECORDED deposits (PR #1117). Verify: Day 11 Sipho portal `/trust` shows the Day-10 deposit (R 50 000).
- **L-53** — Liquidation & Distribution request pack (PR #1123).
- **L-55** — Portal trust error-message alignment (PR #1124).
- **L-58** — Court dates union into Overview deadlines (part of PR #1124 — listed as SKIPPED there; re-check).
- **L-62** — Tax number hybrid: INDIVIDUAL auto-populate, soft-warn at draft, hard-enforce at send (PR #1126). Verify: Day 28 fee-note draft succeeds with tax_number warning, send fails without.
- **L-63** — Fee-note dialog surfaces unbilled disbursements (PR #1116). Verify: Day 28 fee-note dialog shows the Day-21 Sheriff Fees R 1 250 (after it's been Approved).
- **P-03** — Portal projects visible to portal contacts linked via customer (PR #1120). Verify: Day 4 Sipho portal `/projects` shows his RAF matter (not empty).
- **P-07** — Portal `/documents` nav hidden or stubbed (PR #1123).

### CodeRabbit follow-ups (all merged)
- PR #1115, #1121 — tenantId guard + PII log redaction + a11y label + scheduler-path metadata + test hardening.

## Environment

| Service | URL | Status (post-infra-ready 2026-04-24 23:05 SAST) |
|---------|-----|--------------------------------------------------|
| Frontend (kc mode) | http://localhost:3000 | UP PID 5771 — healthy |
| Backend (local+keycloak profile) | http://localhost:8080 | UP PID 25298 — restarted, ready in 27s, /actuator/health UP (L-22 regression fix loaded: accept-invite redirect URL now `/accept-invite/complete`) |
| Gateway (BFF) | http://localhost:8443 | UP external PID 71426 — healthy |
| Portal | http://localhost:3002 | UP PID 5677 — healthy, root returns 307 |
| Keycloak | http://localhost:8180 | UP — realm `docteams` OK |
| Mailpit UI | http://localhost:8025 | UP — inbox purged |
| Postgres (docteams) | localhost:5432 | UP (8 days healthy) |
| LocalStack (S3) | http://localhost:4566 | UP |

Dev Stack READY. QA cleared to start Day 0.

## Gap Tracker

| GAP_ID | Day / Checkpoint | Severity | Status | Summary | Owner | Retries |
|--------|------------------|----------|--------|---------|-------|---------|
| GAP-L-22 | Day 0 — 0.21 / 0.22 | HIGH (BLOCKER) | VERIFIED | Accept-invite flow completes KC registration but browser lands on padmin's `/platform-admin/access-requests` (stale BFF session) instead of Thandi's firm dashboard. L-22 middleware logout bounce fires correctly; handoff check never triggers because KC registration callback returns to port 3000 via `account` client (not through gateway-bff OAuth2 success handler → no `KC_LAST_LOGIN_SUB` cookie → middleware sees valid-looking SESSION and passes through). Root cause: `KeycloakProvisioningClient.organizationRedirectUrl` points at `<frontend>/dashboard`, so the post-registration `account`-client code is delivered straight to the frontend which has no OAuth2 handler. **Fix shipped in PR #1129 (merge SHA `05d05f48`)**: KC org `redirectUrl` now targets `/accept-invite/complete`, a thin frontend bounce page that forwards to gateway `/oauth2/authorization/keycloak`; gateway-bff success handler then fires and sets `KC_LAST_LOGIN_SUB` for the L-22 middleware. **VERIFIED 2026-04-25 03:55 SAST**: Day 0 re-run Cycle 1 — all 3 KC invites (Thandi owner, Bob admin, Carol member) successfully landed on `/org/mathebula-partners/dashboard`. ORGIVT `reduri` claim confirmed = `http://localhost:3000/accept-invite/complete`. Sidebar shows correct user each time; no padmin leak. Full evidence in `qa_cycle/checkpoint-results/day-00.md` §"Day 0 Re-Run — Cycle 1 Verify". | Dev → QA | 0 |
| MINOR-Doc-Drift-26 | Day 0 — 0.26 | LOW (cosmetic/docs) | OPEN | Scenario step 0.26 references `/settings/team` but actual route is `/org/{slug}/team`. `/settings/team` returns 404. Update scenario doc or add a redirect. Does not block demo. | Product | 0 |
| MINOR-Copy-Projects | Day 0 — dashboard | LOW (cosmetic) | OPEN | Dashboard Recent Activity empty-state reads "Activity will appear as your team works on **projects**." Legal-za terminology should say "matters". One-string leak in empty-state copy. | Product → Dev | 0 |

## Deferred to future phases (do NOT re-log as new gaps if observed)

- **L-30** — KYC adapter (needs provider decision; mock-adapter wiring planned for Sprint 2).
- **L-36** — RAF-specific matter template (Sprint 2).
- **L-40** — Multi-role portal-contact CRUD (Sprint 2).
- **L-49** — Proposal fee-estimate side-table + line-item rendering (Sprint 3 — scenario 7.3/7.4/8.2/8.3 carry SKIPPED-BY-DESIGN).
- **L-54** — Beneficial-owners structured field group for TRUST/COMPANY (Sprint 3).
- **L-61** — Bulk Billing Runs default ON for legal-za (Sprint 2).
- **OBS-L-27** — Portal PDF iframe cross-origin (infra decision).

## Legend

- **Status**: OPEN → SPEC_READY → FIXED → VERIFIED | REOPENED | STUCK | WONT_FIX | RESOLVED
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-24 SAST — VERIFY CYCLE started. Prior cycle archived to `qa_cycle/_archive_2026-04-24_pre-verify/status.md`. Branch: `bugfix_cycle_2026-04-24` off main. Purpose: re-validate 40+ shipped fixes end-to-end before declaring Day 0–90 scenario green. Next action: Infra agent — restart backend for post-L-60 merges + start portal.
- 2026-04-24 21:26 SAST — Infra: stack readied for verify cycle. Backend PID 5458 ready in 27s (post-L-60-through-L-48 merges). Portal PID 5677 ready in 3s. Frontend PID 5771 ready in 3s (restarted for clean HMR state). Gateway external PID 71426 healthy. Mailpit inbox purged. Docker infra (Postgres, Keycloak, Mailpit, LocalStack) all green; KC realm `docteams` verified. All 4 svc.sh services RUNNING=yes, HEALTHY=yes. QA cleared to start Day 0.
- 2026-04-24 21:38 SAST — QA: Day 0 run executed. Pre-flight wiped prior-cycle state (tenant schema `tenant_5039f2d497cf`, KC org + users, access_request, subscriptions). 0.1–0.20 PASS (OTP flow, padmin approval, KC invitation, L-22 middleware logout bounce, KC registration submission all work). **0.21 FAIL — BLOCKER**: post-registration browser lands on padmin's `/platform-admin/access-requests` with stale BFF session instead of Thandi's firm dashboard. GAP-L-22 **REOPENED**. L-27 VAT/ZAR labels verified via `org_settings`. 10 downstream checkpoints (0.23–0.32) NOT EXECUTED. Full report: `qa_cycle/checkpoint-results/day-00.md`. Next action: Product/Dev triage on GAP-L-22.
- 2026-04-24 22:24 SAST — Product triaged GAP-L-22 regression → SPEC_READY. Spec at `qa_cycle/fix-specs/GAP-L-22-regression.md`. Chose Option A (route post-registration through gateway-bff OAuth2 via new `/accept-invite/complete` frontend bounce page + flip `KeycloakProvisioningClient.organizationRedirectUrl` to target it). Root cause confirmed in code: KC org's `redirectUrl` sends the `account`-client auth code straight to the frontend, which has no OAuth2 handler → gateway success handler never fires → no `KC_LAST_LOGIN_SUB` → middleware's L-22 check is never triggered. PR #1125 middleware is correct; only the trigger path is broken. Est S ≤30 min; one backend line-change + ~40-line frontend page clone; no migrations, no realm-export changes. Dev is cleared to implement.
- 2026-04-24 22:38 SAST — Dev: GAP-L-22 regression **FIXED** in PR #1129 (merge SHA `05d05f48`, squash-merged into `bugfix_cycle_2026-04-24`). Implementation matches spec verbatim — `KeycloakProvisioningClient.organizationRedirectUrl` flipped from `<frontend>/dashboard` to `<frontend>/accept-invite/complete`, new `frontend/app/accept-invite/complete/page.tsx` (40-line clone of `/accept-invite/continue` reusing `AcceptInviteRedirect`) forwards to `${NEXT_PUBLIC_GATEWAY_URL || http://localhost:8443}/oauth2/authorization/keycloak`. New backend tests assert the POST body's `redirectUrl` ends with `/accept-invite/complete` (incl. trailing-slash normalisation case); Vitest covers the bounce-page render + `window.location.replace` on mount. CI green (qodana pass; CodeRabbit pass). Build matrix: backend `compile/test-compile=0`, targeted tests `*KeycloakProvisioning*,*Invitation*,*Organization*Provision*` `=0`; frontend `install=0`, `format` clean, `lint=0` (only pre-existing warnings), `build=0` (`/accept-invite/complete` listed as static prerender), `test=0` (2064 pass / 2 skipped, including 2 new tests in `app/accept-invite/complete/page.test.tsx`). **NEEDS_REBUILD: true** — `KeycloakProvisioningClient.organizationRedirectUrl` is set in the constructor from `@Value`, so the running backend (PID 5458) still serves the old `/dashboard` redirectUrl until restarted. Next action: Infra — restart backend + run the standard pre-flight wipe (tenant schema + KC org + access_request + Mailpit) so QA can resume Day 0 from 0.1 with a freshly provisioned `mathebula-partners` org carrying the new redirectUrl.
- 2026-04-24 23:05 SAST — Infra: pre-flight wipe complete. Deleted 1 KC user (`thandi@mathebula-test.local` id `713ab9d6-654e-4aed-962d-491c7526a5f3`), KC org `61101fc9-cbc7-40e3-8912-dcf44f17ba4b` (`Mathebula & Partners`), 4 registry rows across organizations/access_requests/org_schema_mapping/subscriptions (organizations `2c3a4b3b-ab11-4f10-99f0-76441fffcc2f`, access_requests `d009c550-92ae-479e-97bb-0572007af638`, org_schema_mapping `604bb9ff-26f8-4901-a7e5-7f29a9b64be5`, subscriptions `309af2a6-6fc6-4c12-9fe0-4d00354f58d1` TRIALING with 0 payments), dropped tenant schema `tenant_5039f2d497cf` (103 objects CASCADE). Post-checks all 0 for mathebula; 3 unrelated tenant schemas preserved (`tenant_2a96bc3b208b`, `tenant_8ee5c5a6e45f`, `tenant_f6e34f99f3b9`). Backend restarted PID 25298 ready in 27s (L-22 regression fix loaded — source confirms `/accept-invite/complete` at `KeycloakProvisioningClient.java:80`; accept-invite redirect URL now points at `/accept-invite/complete`). Mailpit inbox purged. All 4 svc.sh services RUNNING=yes, HEALTHY=yes (backend 25298 / gateway ext 71426 / frontend 5771 / portal 5677). QA cleared to re-run Day 0 Phase A–D fresh.
- 2026-04-25 03:55 SAST — QA: Day 0 VERIFY re-run COMPLETE. 30/32 PASS, 1 N/A (0.13 L-21 WONT_FIX), 1 PARTIAL (0.26 doc drift `/settings/team` vs `/team` — non-blocker). **GAP-L-22 VERIFIED end-to-end on 3 KC invites**: Thandi (owner, 0.21), Bob (admin, 0.31), Carol (member, 0.32) all successfully reached `/org/mathebula-partners/dashboard` via the new `/accept-invite/complete` bounce → gateway OAuth2 success handler → L-22 middleware handoff. ORGIVT `reduri` claim confirmed = `http://localhost:3000/accept-invite/complete`. No padmin session leak. L-27 VAT/ZAR labels VERIFIED (tenant `org_settings`: `vertical_profile=legal-za`, `default_currency=ZAR`, `tax_label=VAT`). New access_requests row `status=APPROVED` `keycloak_org_id=3d2c66a0-516d-4357-908e-2ab76351d473`; organizations `provisioning_status=COMPLETED`; tenant schema `tenant_5039f2d497cf` recreated. 2 LOW cosmetic findings logged as new Minor gaps (doc drift, "projects" copy leak). Full results appended to `qa_cycle/checkpoint-results/day-00.md`. Next action: QA — proceed to Day 1.
