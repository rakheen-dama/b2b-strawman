# Day 0 — Firm Org Onboarding (Keycloak flow) — VERIFY CYCLE Results

**Branch**: `bugfix_cycle_2026-04-24`
**Date**: 2026-04-24
**Tenant**: `mathebula-partners` (fresh — pre-existing state wiped at run start)
**Actor(s)**: Thandi (request) → padmin (approval) → Thandi (registration — BLOCKED)
**Stack**: Keycloak dev stack — frontend :3000, BFF gateway :8443, backend :8080, KC :8180, Mailpit :8025

## Pre-flight — environment cleanup (scenario 0.C / 0.D / 0.E)

Pre-existing state from prior cycle (tenant schema, org, access_request, KC users, KC org) was detected
and wiped before Day 0 began:

- Dropped tenant schema `tenant_5039f2d497cf` (103 cascading objects).
- Deleted `public.access_requests` row for thandi@mathebula-test.local.
- Deleted `public.organizations` row `07637108-…` + linked subscription.
- Deleted `public.org_schema_mapping` rows for `mathebula-partners`.
- Deleted KC users: Thandi `c5693386…`, Bob `92823624…`, Carol `b72c738b…`.
- Deleted KC organization Mathebula & Partners `6b404169…`.
- Mailpit inbox purged via `DELETE /api/v1/messages`.

All cleanup via admin REST APIs or direct SQL on `public.*` / schema DROP (no tenant-scoped SQL mutations).

## Checkpoint Results

| ID | Description | Result | Evidence |
|----|-------------|--------|----------|
| 0.1 | Landing page loads, zero console errors | PASS | Page title = "Kazi — Practice management, built for Africa". Only a react-devtools hint + `scroll-behavior: smooth` warning. |
| 0.2 | Get Started routes to `/request-access` | PASS | Clicking Nav > Get Started navigated to `/request-access`. |
| 0.3 | Form shows Email, Full Name, Organization, Country, Industry | PASS | All 5 fields present. |
| 0.4 | Fill form with Thandi / Mathebula & Partners / ZA / Legal Services + Submit | PASS | Form accepted, submit button enabled after validation, POST succeeded. |
| 0.5 | Transitions to OTP step | PASS | Card swapped to "Check Your Email / Enter the verification code sent to thandi@mathebula-test.local". |
| 0.6 | Mailpit OTP email arrives (subject contains "verification") | PASS | Subject "Your Kazi verification code". Code `006327` extracted from body. |
| 0.7 | Enter OTP → Verify | PASS | OTP accepted. |
| 0.8 | Success card "Request has been submitted for review" | PASS | "Request Submitted / Your access request has been submitted for review." |
| 0.9 | Incognito → `/dashboard` → KC login page | PASS | Redirected to `http://localhost:8180/realms/docteams/protocol/openid-connect/auth…`. |
| 0.10 | Login as padmin@docteams.local / password | PASS | KC auth succeeded, redirected to /platform-admin/access-requests. |
| 0.11 | Navigate to /platform-admin/access-requests | PASS | Landed there directly after KC callback. |
| 0.12 | Mathebula visible in Pending; Industry=Legal Services, Country=South Africa | PASS | Row rendered with all expected fields. |
| 0.13 | Click row → detail view | PARTIAL (WONT_FIX per L-21) | Clicking row cell does not open a detail page. Per status.md L-21 = WONT_FIX (scenario rescope). Approve button available directly on row — fine. |
| 0.14 | Click Approve → AlertDialog → Confirm | PASS | AlertDialog "Approve Access Request" opened with copy "This will create a Keycloak organization, provision a tenant schema, and send an invitation to thandi@mathebula-test.local." Confirm fired the approval. (Note: initial Playwright MCP click occasionally did not propagate through Radix button; native DOM click worked — tooling quirk, not product bug.) |
| 0.15 | Status = Approved, no provisioning error banner | PASS | DB: `access_requests.status=APPROVED`, `reviewed_at` populated, `provisioning_error` empty, `keycloak_org_id=61101fc9-cbc7-40e3-8912-dcf44f17ba4b`. Pending tab empties immediately. |
| 0.16 | Vertical profile auto-assigned = `legal-za` | PASS | `tenant_5039f2d497cf.org_settings.vertical_profile='legal-za'`, `default_currency='ZAR'`, `tax_label='VAT'` (L-27 verified end-to-end through approval flow). |
| 0.17 | Mailpit → Keycloak invitation email to Thandi | PASS | Subject: "Invitation to join the Mathebula & Partners organization". Contains KC action-token URL `…/accept-invite?kcUrl=…&client_id=account…&token=eyJ…`. |
| 0.18 | Open KC invitation link (while padmin session live — L-22 middleware probe) | PASS | L-22 middleware (PR #1125) fired as designed: `/accept-invite` → redirect to KC `/logout?client_id=gateway-bff&post_logout_redirect_uri=/accept-invite/continue?kcUrl=…` → logout confirmation page → `/accept-invite/continue` → client-side redirect to registration page. |
| 0.19 | KC registration page loads with org pre-bound | PASS | Heading: "Create an account to join the Mathebula & Partners organization". Email pre-filled `thandi@mathebula-test.local`. |
| 0.20 | Fill Thandi / Mathebula / SecureP@ss1 / SecureP@ss1 + submit | PASS | Form filled. Submit redirected to `http://localhost:3000/dashboard?session_state=…&code=…` (account client callback). |
| 0.21 | Lands on `/org/mathebula-partners/dashboard` | **FAIL — BLOCKER (GAP-L-22 REOPENED)** | **Browser landed on `/platform-admin/access-requests`** (padmin's page from step 0.11) with red banner "Failed to load access requests: Authentication session expired". Thandi never reaches her firm dashboard. KC confirmed user creation (`thandi@mathebula-test.local` id `713ab9d6-654e-4aed-962d-491c7526a5f3`, `emailVerified=false`). |
| 0.22 | Sidebar shows Mathebula & Partners / Thandi | **FAIL — BLOCKER** | Nav shows padmin's platform-admin tabs (Access Requests / Billing / Demo). No user card. No firm sidebar. |
| 0.23 | Legal terminology (Matters, Clients, Fee Notes) | **NOT EXECUTED** | Blocked by 0.21. |
| 0.24 | Legal modules nav (Trust / Court / Conflict) | **NOT EXECUTED** | Blocked by 0.21. |
| 0.25 | Wow screenshot `day-00-firm-dashboard-legal.png` | **NOT EXECUTED** | Blocked. |
| 0.26 | Settings > Team | **NOT EXECUTED** | Blocked. |
| 0.27 | Thandi listed as Owner, no "Upgrade to Pro" gate | **NOT EXECUTED** | Blocked. |
| 0.28 | Invite Bob as Admin | **NOT EXECUTED** | Blocked. |
| 0.29 | Invite Carol as Member | **NOT EXECUTED** | Blocked. |
| 0.30 | Mailpit shows 2 KC invitation emails | **NOT EXECUTED** | Blocked. |
| 0.31 | Bob registers in incognito | **NOT EXECUTED** | Blocked. |
| 0.32 | Carol registers in incognito | **NOT EXECUTED** | Blocked. |

## New Gap — GAP-L-22 REOPENED (BLOCKER)

### Summary
Session-handoff cleaner (PR #1125) fires the RP-initiated KC logout correctly, but the end-to-end
invite-accept flow fails to complete the handoff. Thandi registers successfully in KC but is dropped
onto padmin's `/platform-admin/access-requests` page with the stale BFF SESSION still bound to padmin
(refresh fails with `[invalid_grant] Session not active`) — no path to Thandi's own dashboard.

### Repro (today, 2026-04-24, bugfix_cycle_2026-04-24)
1. Fresh env, no tenant yet.
2. Thandi submits request-access + verifies OTP.
3. padmin logs in via KC and approves (browser keeps padmin BFF `SESSION` cookie).
4. In the same browser context, open the KC invitation URL from the Mailpit email.
5. L-22 middleware (OK): redirects to `/realms/docteams/…/logout?client_id=gateway-bff&post_logout_redirect_uri=/accept-invite/continue?kcUrl=…` → user confirms logout (KC deletes its SSO cookie + invalidates the padmin KC session — visible later as `[invalid_grant] Session not active` in gateway log).
6. `/accept-invite/continue` renders, then client redirects to KC account-client registration URL.
7. User fills registration form (Thandi / Mathebula / SecureP@ss1) + submits.
8. KC creates the user (`713ab9d6-654e-4aed-962d-491c7526a5f3`), then redirects to `reduri=http://localhost:3000/dashboard?code=…`.
9. **Frontend lands on `/platform-admin/access-requests` (not `/dashboard`)** showing "Failed to load access requests: Authentication session expired".
10. `document.cookie` (non-HttpOnly view) is empty on frontend; but gateway log at the same time still shows **`BFF /me claims: preferred_username=padmin@docteams.local`** for several seconds after the logout + registration finished, followed by `[invalid_grant] Session not active` on the next `/me` refresh.

### Why the L-22 middleware didn't catch it
Middleware only triggers the stale-session-sub check when the `KC_LAST_LOGIN_SUB` cookie is present
(set by the gateway's OAuth2 success handler). In this flow, **the gateway OAuth2 success handler
never fires** — the callback `?code=…` returned to `http://localhost:3000/dashboard`, and that auth
code is for KC client `account`, not `gateway-bff`. The frontend has no handler for this code, so no
success event, no `KC_LAST_LOGIN_SUB` cookie, no handoff check.

Meanwhile the BFF still has padmin's SESSION row. Middleware sees SESSION cookie → allows
request → server component calls `/bff/me` → succeeds with padmin's cached ID token until the next
refresh attempt fails. Net: a UX hole where Thandi is effectively "logged in as padmin" for a brief
moment and then sees a generic "Authentication session expired" error on the wrong page.

### Severity
**BLOCKER**. Prevents Day 0 from completing — Thandi cannot reach the firm dashboard, so checkpoints
0.21–0.32 (dashboard verification + team invites) cannot execute, and Days 1–90 are blocked.

### Evidence
- Screenshot: `qa_cycle/checkpoint-results/day-00-screenshots/gap-L22-reopened-thandi-lost-on-padmin-page.png`
- Screenshot: `qa_cycle/checkpoint-results/day-00-screenshots/L22-post-thandi-register-padmin-page-leak.png`
- Gateway log lines at `2026-04-24T21:35:07 → 21:37:19` in `.svc/logs/gateway.log` — BFF `/me` returns padmin claims repeatedly after the logout; `[invalid_grant] Session not active` errors interleaved.
- KC admin REST: Thandi user `713ab9d6-654e-4aed-962d-491c7526a5f3` confirmed created (`emailVerified=false`).
- Access request row: `d009c550-92ae-479e-97bb-0572007af638` `status=APPROVED`, `keycloak_org_id=61101fc9-cbc7-40e3-8912-dcf44f17ba4b`.
- Tenant schema: `tenant_5039f2d497cf` exists with `org_settings.vertical_profile='legal-za'`.

### Hypothesis for fix (owner: Dev)
Two plausible angles:
1. **Preferred**: the accept-invite flow should land the user through the **BFF OAuth2 flow** after registration — i.e. set the OrgIvt token's `reduri` (or wrap the account-client redirect) to send the browser through `http://localhost:8443/oauth2/authorization/keycloak?redirect_uri=/dashboard` so the callback runs through the gateway's OAuth2 success handler and L-22 middleware gets `KC_LAST_LOGIN_SUB` to compare.
2. Have the gateway invalidate its local SESSION when it observes `[invalid_grant] Session not active` on a refresh, rather than keeping a dangling BFF session whose refresh will perpetually fail.
3. Alternatively: have the L-22 middleware **probe `/bff/me` for every authenticated route** (not just when `KC_LAST_LOGIN_SUB` is present) and force re-login when the ID token has silently gone invalid.

Option 1 is preferred because it preserves the existing handoff check as the single source of truth
for "new user landed cleanly".

## Verification of other L-* items observed in passing

- **L-27** tax label `VAT` + `default_currency=ZAR` on legal-za — VERIFIED (org_settings post-approval confirms).
- **L-21** (WONT_FIX) — confirmed: row click does not open a detail page; Approve button directly on row is the intended UX.
- **L-22** (PR #1125 partial) — logout-bounce step is correct; downstream callback landing is NOT — see REOPENED above.
- **Vertical profile auto-assign** from industry "Legal Services" → `legal-za` — VERIFIED.
- **Approve dialog copy** mentions KC org, tenant schema, invite email — L-23 area indirectly healthy.
- **Mailpit flow** (OTP + KC invitation) — both emails delivered cleanly, no bounce.

All other "Verify" items in status.md (L-25/28/29/31/32/35/37/38/39/41/44/45/46/47/48/50/51/52/53/55/56/57/58/60/62/63 and P-01/02/03/06/07) remain **BLOCKED** behind the L-22 regression — cannot reach the firm dashboard to exercise them.

## Tally

- **PASS**: 18 (0.1–0.12, 0.14–0.20)
- **PARTIAL**: 1 (0.13 — L-21 WONT_FIX behavior, expected)
- **FAIL — BLOCKER**: 2 (0.21, 0.22 — L-22 REOPENED)
- **NOT EXECUTED**: 10 (0.23 → 0.32)

## Next QA Position

**Day 0 — 0.21** (blocked on GAP-L-22 REOPENED).

QA halted per protocol. Next cycle requires Product/Dev triage on GAP-L-22 REOPENED before Day 0
can finish.

## Infra status

Stack remained healthy throughout the run. No Infra attention required.
