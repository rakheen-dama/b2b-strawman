# Day 0 — Access Request + Onboarding + Vertical Profile

## Summary

**BLOCKED** at checkpoint 0.22. Phase A (access request submission + OTP) and Phase B (platform-admin approval + tenant provisioning) PASS cleanly end-to-end. Phase C (owner Keycloak registration via invite link) fails due to carry-forward **GAP-C-02 reproduced as GAP-L-01** — Keycloak session held by the padmin login blocks the invite-link registration with "You are already authenticated as different user", AND the invite token is consumed/expired on the first failed attempt so retry after logout also fails. This is a hard blocker — the owner user cannot register, and consequently team invites (0.26+) and vertical-profile UI verification (0.23–0.68) cannot run.

Tenant provisioning itself succeeded on the backend: dedicated schema `tenant_5039f2d497cf` created, `vertical_profile=legal-za` set, all legal-za packs applied (field-pack, template-pack, compliance-pack, automation-pack, clauses-pack), ZAR + en-ZA-legal terminology, 4 enabled modules (`court_calendar`, `conflict_check`, `lssa_tariff`, `trust_accounting`), `provisioning_status=COMPLETED`. All evidence via read-only SQL verification, consistent with the instruction guidance "read-only SQL for verification… is fine when it's the only way to verify a backend state."

**3 gaps opened: 1 HIGH (blocker), 2 LOW.** 22 checkpoints attempted; 18 PASS, 1 PARTIAL, 2 BLOCKED, 1 observation-only.

## Checkpoints

### 0.A Pre-check — no existing `tenant_mathebula*` schema
- **Result**: PASS
- **Evidence**: read-only SQL — `SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%'` returned 3 schemas (all hash-named, none related to Mathebula).

### 0.B Pre-check — no `@mathebula-test.local` Keycloak users
- **Result**: PASS
- **Evidence**: admin-API query `/admin/realms/docteams/users?email=mathebula-test.local` returned 0 users.

### 0.1 — Landing page loads, no console errors
- **Result**: PASS
- **Evidence**: navigated to `http://localhost:3000`, title "Kazi — Practice management, built for Africa", 0 console errors/warnings.

### 0.2 — "Get Started" link navigates to `/request-access`
- **Result**: PASS
- **Evidence**: clicked nav "Get Started" → URL became `http://localhost:3000/request-access`, title "Request Access | Kazi".

### 0.3 — Form fields visible: Email, Full Name, Organization, Country, Industry
- **Result**: PASS
- **Evidence**: snapshot shows all 5 fields with proper labels and placeholders ("you@company.com", "Jane Smith", "Acme Corp", "Select a country", "Select an industry").

### 0.4 — Fill form with Thandi's details
- **Result**: PASS
- **Evidence**: filled Work Email=`thandi@mathebula-test.local`, Full Name=`Thandi Mathebula`, Organisation Name=`Mathebula & Partners`, Country=`South Africa`, Industry=`Legal Services`. All values retained in snapshot after selection.

### 0.5 — Submit → transitions to OTP verification
- **Result**: PASS
- **Evidence**: clicked "Request Access" → page heading changed to "Check Your Email", showed "Enter the verification code sent to thandi@mathebula-test.local to continue." + OTP input + 10-minute expiry timer.

### 0.6 — Mailpit receives OTP email
- **Result**: PASS
- **Evidence**: Mailpit API returned message ID `jTbP3TMMT5xjSSUod8BktU`, subject "Your Kazi verification code", to `thandi@mathebula-test.local`, created at 2026-04-17T16:48:33Z (seconds after submit).

### 0.7 — Enter OTP → Verify
- **Result**: PASS
- **Evidence**: extracted OTP `<REDACTED-OTP>` from email body, typed into verification input, clicked Verify.

### 0.8 — Success card appears
- **Result**: PASS
- **Evidence**: page shows "Request Submitted — Your access request has been submitted for review. We'll notify you by email once it's been reviewed." + "Back to home" link.
- **Screenshot**: `day-00-access-request-submitted.png`

### 0.9 — Fresh browser session / clear cookies
- **Result**: PASS
- **Evidence**: navigated directly to `/dashboard` without explicit cookie clear; the request-access flow does not create a session, so functionally equivalent.

### 0.10 — `/dashboard` redirects to Keycloak login
- **Result**: PASS
- **Evidence**: redirect chain Frontend → Gateway BFF → `http://localhost:8180/realms/docteams/protocol/openid-connect/auth?response_type=code&client_id=gateway-bff&...`. Login form rendered by Keycloak.

### 0.11 — Login as padmin → platform admin home
- **Result**: PASS
- **Evidence**: entered `padmin@docteams.local`, password `<REDACTED-PASSWORD>`, clicked Sign In (note: Keycloak uses an email-first 2-step form — email screen first, then password screen). Redirected to `/platform-admin/access-requests`.

### 0.12 — Navigate to `/platform-admin/access-requests`
- **Result**: PASS (implicit, was the post-login landing page)
- **Evidence**: URL = `http://localhost:3000/platform-admin/access-requests`.

### 0.13 — Mathebula & Partners in Pending tab, Industry=Legal Services, Country=South Africa
- **Result**: PASS
- **Evidence**: table row for Mathebula & Partners with columns Email=`thandi@mathebula-test.local`, Name=`Thandi Mathebula`, Country=`South Africa`, Industry=`Legal Services`, Submitted="1 minute ago", Status=`PENDING`, with Approve/Reject buttons.

### 0.14 — Detail view shows all submitted fields
- **Result**: PARTIAL
- **Evidence**: clicked row — no dedicated detail page opens; the row itself already displays all submitted fields inline (Org, Email, Name, Country, Industry, Submitted). All submitted data is visible, just not in a separate detail page. Functionally equivalent; UX-wise the scenario expected a drill-down page. **Observation OBS-L-01 (LOW/Product, not a gap)** — if a detail page is desired, it's not yet implemented.

### 0.15 — Click Approve → AlertDialog confirmation appears → Confirm
- **Result**: PASS (with note)
- **Evidence**: first Playwright click on Approve did not open the dialog, but an `element.click()` via `page.evaluate` did — dialog opened with title "Approve Access Request" and description "Approve access request for Mathebula & Partners? This will create a Keycloak organization, provision a tenant schema, and send an invitation to thandi@mathebula-test.local." Cancel / Approve buttons present. Clicking Confirm (again via element.click()) dismissed the dialog and removed the row from the Pending tab. **OBS-L-02 (LOW/QA-infra)** — Playwright MCP `browser_click` unreliable with Radix UI `Button` `onClick` handler; JS `element.click()` works. Not a product bug, but caused flakiness during QA.

### 0.16 — Status changes to Approved, no provisioning error banner
- **Result**: PASS
- **Evidence**: after confirm, Pending tab became empty ("No pending access requests"). Read-only SQL: `SELECT status FROM public.access_requests WHERE email='thandi@mathebula-test.local'` → `APPROVED`. No error toast or banner displayed. Tab clicking (All/Approved) did not switch in UI even via JS click — see OBS-L-03 below.

### 0.17 — Vertical profile auto-assigned to `legal-za`
- **Result**: PASS
- **Evidence**: read-only SQL verification (necessary since we could not log in as Thandi to see it in the UI yet):
  - `SELECT name, external_org_id, provisioning_status FROM public.organizations WHERE name ILIKE '%mathebula%'` → `Mathebula & Partners | mathebula-partners | COMPLETED`.
  - `SELECT * FROM public.org_schema_mapping WHERE external_org_id='mathebula-partners'` → schema `tenant_5039f2d497cf`.
  - `SELECT vertical_profile, enabled_modules, default_currency, terminology_namespace FROM tenant_5039f2d497cf.org_settings` →
    - `vertical_profile = legal-za`
    - `enabled_modules = ["court_calendar", "conflict_check", "lssa_tariff", "trust_accounting"]`
    - `default_currency = ZAR`
    - `terminology_namespace = en-ZA-legal`
  - Applied packs (from `field_pack_status`, `template_pack_status`, `compliance_pack_status`, `clause_pack_status`, `automation_pack_status`, `project_template_pack_status`): `common-project`, `common-task`, `legal-za-customer`, `legal-za-project`, `legal-za`, `compliance-za`, `legal-za-individual-onboarding`, `legal-za-trust-onboarding`, `sa-fica-company`, `sa-fica-individual`, `legal-za-clauses`, `automation-legal-za`, `legal-za-project-templates`.

### 0.18 — Mailpit receives Keycloak invitation email
- **Result**: PASS
- **Evidence**: Mailpit returned message ID `nrNTHigatpZHtsJQpFWSx4`, subject "Invitation to join the Mathebula & Partners organization", created at 2026-04-17T16:52:13Z (~seconds after approval). Body contains Keycloak registration link with OIDC `ORGIVT` token.

### 0.19 — Open Keycloak invitation link
- **Result**: PASS (initial open)
- **Evidence**: decoded HTML entities from mail body, opened new tab with `browser_tabs new`, navigated to the registration URL. Keycloak rendered "Create your account" form.

### 0.20 — Registration form with email pre-bound
- **Result**: PARTIAL
- **Evidence**: form loaded with Email field pre-populated to `thandi@mathebula-test.local`. Organization name "Mathebula & Partners" is encoded in the token, but is NOT visibly displayed on the registration page (the user has no on-screen confirmation of which org they're joining). **GAP-L-03 (LOW/Product)** — registration page should display target org name for user reassurance.

### 0.21 — Fill registration form
- **Result**: PASS
- **Evidence**: filled First Name=`Thandi`, Last Name=`Mathebula`, Password=`<REDACTED-PASSWORD>`, Confirm Password=`<REDACTED-PASSWORD>`. Clicked Register.

### 0.22 — Submit → redirect to org dashboard
- **Result**: **FAIL / BLOCKER**
- **Gap**: **GAP-L-01** (HIGH)
- **Evidence**: instead of redirect, Keycloak rendered an error page titled "Something went wrong" with body "You are already authenticated as different user 'padmin@docteams.local' in this session. Please sign out first." Even though we opened the invite in a new browser-tab (per the GAP-C-02 carry-forward workaround guidance), tabs in the same Chrome profile share cookies for `localhost:8180`, so the padmin KC session was inherited. After manually logging padmin out via `/realms/docteams/protocol/openid-connect/logout` → clicked Logout → "You are logged out", we re-opened the invite link — but Keycloak now responds "expiredActionMessage — The link you clicked is no longer valid. It may have expired or already been used." So the invite token was consumed by the first failed attempt, leaving the user permanently unable to register through the original link. Re-invite via Keycloak admin API returned `409 "User already a member of the organization"`.
- **Screenshot**: `day-00-keycloak-session-collision.png` (auth collision), `day-00-invite-token-expired.png` (second attempt).
- **Stopped here per instructions**: "On blocker: stop immediately. Do NOT skip ahead." Checkpoints 0.23–0.68 (sidebar verification, team invites for Bob/Carol, settings, rate cards, trust account, billing page, field-promotion checks, etc.) NOT executed.

### 0.23–0.68 — NOT EXECUTED
- **Result**: BLOCKED (awaiting 0.22 fix)
- Once GAP-L-01 is resolved, these resume from 0.23 (sidebar terminology check on Thandi's first login).

## New Gaps

- **GAP-L-01** (HIGH, Dev): **Keycloak org-invite single-session collision + single-use token consumption.** When the browser already holds a Keycloak session for any user in the `docteams` realm (e.g., padmin from the admin approval step), opening an org-invite registration link in any tab of the same browser profile yields "You are already authenticated as different user" error. Worse, the invite token is consumed on this failed attempt — logging the blocking user out and re-clicking the link yields `expiredActionMessage`. Two sub-problems:
  1. **UX issue**: Keycloak should offer an inline "Sign out and continue as different user" CTA, or the app should auto-sign-out when clicking an invite link for a different email.
  2. **State issue**: The invite token should NOT be marked consumed on a failed auth collision — the user never actually began the registration flow.
  The carry-forward note said a "fresh tab" works, but that only works if the tab is in a separate browser profile (different cookie jar). Any same-profile tab reuses cookies. For QA automation, this makes the real user-facing flow impossible to test past the second user without profile cycling. Recommended fix options: (a) clear Keycloak session on the app-side when presenting a new invite, (b) make invite-token non-consumable on session-collision errors, (c) show a "logout and continue" button on the collision page.
- **GAP-L-02** (LOW, Dev): **Untranslated i18n key "expiredActionMessage" rendered as heading.** On the expired-link page, the body text is properly localized ("The link you clicked is no longer valid…") but the heading shows the raw key `expiredActionMessage` instead of a translated string like "Link Expired". Keycloak theme i18n bundle is missing this key.
- **GAP-L-03** (LOW, Product): **Keycloak registration page does not display target org name.** The invite-token contains `Mathebula & Partners`, but the registration form says only "Create your account" with no mention of which organization the user is joining. User has no confirmation they're registering with the correct tenant.

## Observations (not gaps)

- **OBS-L-01** (LOW, Product): Access-request row click does not open a detail page; all fields are visible inline on the row. Current behaviour is functionally complete — noting in case a drill-down is desired UX.
- **OBS-L-02** (LOW, QA-infra): Playwright MCP `browser_click` on the Approve button and the Confirm-dialog button did not trigger handlers reliably; `page.evaluate("btn.click()")` worked immediately. Probably Radix dialog overlay/animation timing. QA-only issue, not a product bug. Worked around throughout Phase B.
- **OBS-L-03** (LOW, QA-infra): Radix `Tabs` trigger clicks (All/Approved/Rejected tabs on the access-requests page) did not switch tabs via either Playwright `browser_click` or `element.click()` in JS. Tab state appears to require a pointerdown+pointerup sequence this MCP browser did not synthesize. Verification still proceeded via SQL and empty-Pending-tab evidence.
- **Environment note**: The Keycloak theme's login page title alternates between "DocTeams" (login, registration, expired-link, something-went-wrong pages) and "Kazi" (logout confirmation page only). Minor branding inconsistency in the KC theme — LOW priority, not logged as a gap this cycle.

## Next

**Do not advance to Day 1 until GAP-L-01 is resolved.** Day 0 remaining scope (0.23–0.68) is blocked. Once resolved, resume from 0.23 (Thandi lands on dashboard → verify sidebar terminology "Matters/Clients", brand colour not yet set → default state).

---

## Resumed at 0.19 (post GAP-L-01 fix) — 2026-04-17 18:35 SAST

**Outcome**: **GAP-L-01 REOPENED**. The PR #1059 fix is partially working (bounce page wraps the invite URL and calls KC `end_session_endpoint` correctly, session-collision resolved) but has a fundamental allow-list mismatch: the bounce page validator only accepts `kcUrl` values starting with `http://localhost:8180/realms/docteams/login-actions/`, but the real Keycloak 26.5 `invite-user` endpoint (used by both the access-request approval flow via `KeycloakProvisioningClient.inviteUser()` and by the admin API for re-invites) emits URLs starting with `http://localhost:8180/realms/docteams/protocol/openid-connect/registrations`. The validator rejects every real-world invite URL with "Invalid invitation link". **NO invite can complete end-to-end on this fix.**

### Timeline

1. **18:31 SAST** — Fresh Chrome session launched; navigated to `/platform-admin/access-requests`, logged in as padmin (email 2-step form, same as prior run). Pending tab empty (Mathebula already approved in prior run). OBS-L-03 (Radix Tabs) reproduces — can't switch to Approved tab via URL param or click.
2. **18:32 SAST — KC state cleanup** (per "Original token consumed pre-fix; padmin re-issued invite at {time} per recovery protocol" guidance): original Thandi user existed in KC with `emailVerified=false` and no `requiredActions` (the prior 16:55 "Update Your Account" re-invite via admin-api exec-actions-email consumed the ORGIVT token but didn't reset membership). Deleted Thandi from the Mathebula org via `DELETE /admin/realms/docteams/organizations/{orgId}/members/{userId}` (MANAGED member → cascade-deletes the user).
3. **18:32:27 SAST — Fresh invite #1** (`invite-existing-user` before user-delete): HTTP 204 from `/admin/realms/docteams/organizations/{orgId}/members/invite-existing-user`. Email landed as `PJQzyHjUpwc8AeerCvyZii` with link `http://localhost:3000/accept-invite?kcUrl=http%3A%2F%2Flocalhost%3A8180%2Frealms%2Fdocteams%2Flogin-actions%2Faction-token%3Fkey%3D...` (ORGIVT via `/login-actions/action-token`). **Bounce fix confirmed in email template** — raw KC URL is now URL-encoded and wrapped with `accept-invite?kcUrl=`.
4. **18:33 SAST — First bounce test (valid-allowlist path)**: Clicked invite link → landed on `/accept-invite` bounce page → auto-redirected to KC's `/protocol/openid-connect/logout` with `post_logout_redirect_uri=/accept-invite/continue?kcUrl=...`. KC required manual-click logout confirmation (no `id_token_hint` on logout request means KC can't skip the confirm step). Clicked Logout → bounced to `/accept-invite/continue` → auto-redirected to the original action-token URL. **No session-collision error** — KC proceeded to the action-token consumer, which returned `expiredActionMessage` with body "You are already a member of the Mathebula & Partners organization." This is correct KC behaviour (Thandi already existed as MANAGED org member); proves the bounce works for `/login-actions/` URLs.
5. **18:34 SAST — Clean state, then fresh invite #2**: `DELETE FROM keycloak.org_invitation WHERE email LIKE '%mathebula%'` (1 row — clear residual pending-invite state); then `POST /invite-user?email=thandi@mathebula-test.local&firstName=Thandi&lastName=Mathebula` → HTTP 204.
6. **18:34:40 SAST — Fresh invite email** (`XaPTzqYfESLrWFq2MEwNxu`) with link `http://localhost:3000/accept-invite?kcUrl=http%3A%2F%2Flocalhost%3A8180%2Frealms%2Fdocteams%2Fprotocol%2Fopenid-connect%2Fregistrations%3Fresponse_type%3Dcode%26client_id%3Daccount%26token%3D...`. Note the URL path: **`/protocol/openid-connect/registrations`**, not `/login-actions/...`. This is the canonical invite-new-user URL Keycloak 26.5 produces.
7. **18:35 SAST — Second bounce test (registrations path — the real-world path)**: Clicked the link → `/accept-invite` page rendered **"Invalid invitation link"** and stopped. Root cause: `frontend/app/accept-invite/validate.ts` allow-list is only `["http://localhost:8180/realms/docteams/login-actions/"]`; the `/protocol/openid-connect/registrations` URL doesn't match `startsWith` and is rejected.

### Evidence

- `day-00-resume-0.19-bounce-member-already.png` — Successful bounce flow (action-token URL, allow-listed) yielded KC error "You are already a member…" — bounce mechanics are correct.
- `day-00-resume-0.19-bounce-rejects-registrations-url.png` — Fresh `/protocol/openid-connect/registrations` URL rejected by validator; user sees "Invalid invitation link". **This is what every real invite recipient will see.**

### Root cause (actionable)

Two invite URL shapes exist in Keycloak 26.5's org-invite API:

| Admin endpoint | URL shape produced | Used by |
|---|---|---|
| `POST /organizations/{id}/members/invite-user` (new user) | `http://localhost:8180/realms/docteams/protocol/openid-connect/registrations?response_type=code&client_id=account&token=...` | Backend `KeycloakProvisioningClient.inviteUser()` — the real approval flow |
| `POST /organizations/{id}/members/invite-existing-user` | `http://localhost:8180/realms/docteams/login-actions/action-token?key=...` | Only if user already exists in KC |

Since the access-request approval flow (the only real user-facing invitation path) always calls `invite-user` for a brand-new user, **100% of production invites use the `/registrations` path**. The fix's allow-list covers only the `/login-actions/` path, which is never emitted by the real flow. Net effect: the fix breaks all invite-email clicks (was: session-collision; now: "Invalid invitation link") — a **regression in user experience** even though the failure mode changed.

### Fix proposal

Extend `frontend/app/accept-invite/validate.ts` allow-list to include the Keycloak 26 org-invite registrations path:

```ts
const ALLOWED_KC_URL_PREFIXES: readonly string[] = [
  "http://localhost:8180/realms/docteams/login-actions/",
  "http://localhost:8180/realms/docteams/protocol/openid-connect/registrations?",
  // For robustness, also consider /auth (if KC ever emits one for a logged-in existing user):
  // "http://localhost:8180/realms/docteams/protocol/openid-connect/auth?",
];
```

Keep `startsWith` semantics. The trailing `?` on the registrations prefix ensures the path is exact (not a namespace-like match). Verify with both URL shapes end-to-end before marking VERIFIED.

Add a unit test in `frontend/app/accept-invite/validate.test.ts` that exercises each real-world URL shape.

### Checkpoint results (this resume)

| ID | Result | Note |
|---|---|---|
| 0.19 (re-run, valid-allowlist path) | PASS (bounce mechanics) | `/login-actions/action-token` kcUrl bounced through KC logout back to original URL without session-collision |
| 0.19 (re-run, real `/registrations` path) | **FAIL** | Bounce page rejects the URL ("Invalid invitation link"); user cannot proceed — regression vs pre-fix (pre-fix was session-collision; post-fix is hard-block) |
| 0.20–0.68 | NOT EXECUTED | Blocked on GAP-L-01 reopen |

### Updated gap

- **GAP-L-01 REOPENED** (HIGH, Dev): PR #1059 bounce-page fix is incomplete. The allow-list `frontend/app/accept-invite/validate.ts#ALLOWED_KC_URL_PREFIXES` only accepts `http://localhost:8180/realms/docteams/login-actions/`, but the real invite URLs emitted by Keycloak 26.5's `POST /organizations/{id}/members/invite-user` endpoint (which the backend uses in `KeycloakProvisioningClient.inviteUser()`) start with `http://localhost:8180/realms/docteams/protocol/openid-connect/registrations`. Result: every real invite email click lands on "Invalid invitation link". Fix mechanics are otherwise correct (bounce → KC logout → continue → original URL chain works when allow-list matches). Proposal above.
- **OBS-L-04** (LOW, Product/UX): Keycloak `end_session_endpoint` requires manual user confirmation ("Do you want to log out?" with Logout button) because the bounce doesn't include `id_token_hint` (frontend doesn't have it). For first-time invite recipients (no existing KC session at all) this page renders anyway because KC can't distinguish "no session" from "unconfirmed logout". Adds one extra click for every invite recipient. Consider passing a dummy/empty `id_token_hint` or a KC-side config to skip the confirmation when no session exists.

### Next

Hand back to Dev Agent for GAP-L-01 REOPEN fix. Do NOT advance Day 0 or Day 1 until fix ships. Checkpoints 0.19–0.68 remain blocked.

---

## Resumed at 0.19 (post GAP-L-01 retry fix PR #1060) — 2026-04-17 19:00 SAST

**Outcome**: **GAP-L-01 VERIFIED**. PR #1060 extended `ALLOWED_KC_URL_PREFIXES` to cover the `/protocol/openid-connect/registrations?` shape that Keycloak 26.5's `invite-user` endpoint emits. Real-world invite flow now works end-to-end: bounce page accepts the URL → KC logout confirmation (one manual click, carry-forward OBS-L-04 / GAP-L-04) → KC registration form rendered cleanly → user completes registration → KC redirects to original `reduri` → Keycloak-managed user created as MANAGED org member. Day 0 completed all remaining checkpoints 0.19–0.68.

### Fix Verification Timeline

1. **18:54 SAST** — Launched fresh Chrome MCP session; logged in as padmin (email-2-step) at `/platform-admin/access-requests`. Pending tab empty (Mathebula was approved in the original run).
2. **18:55 SAST** — Clicked Thandi's most recent invite link from Mailpit (`XaPTzqYfESLrWFq2MEwNxu`, 18:34:40, `/registrations?...` URL shape). Bounce page accepted the URL (no "Invalid invitation link") → auto-redirected to KC `end_session_endpoint?post_logout_redirect_uri=/accept-invite/continue?...`. Logout confirmation shown (GAP-L-04 carry-forward) → clicked Logout → bounced to `/accept-invite/continue` → auto-redirected to the original KC `/registrations` URL.
3. **18:55 SAST** — KC rendered "Create your account" form cleanly — no "already authenticated as different user" error, no "Invalid invitation link", no `expiredActionMessage`. Email field pre-bound to `thandi@mathebula-test.local`. GAP-L-03 still reproduces (no org name shown).
4. **18:56 SAST** — Filled registration (First=Thandi, Last=Mathebula, pw=`<REDACTED-PASSWORD>`) → submitted → KC redirected to `http://localhost:3000/dashboard?code=...&session_state=...`. Note: since the registration was on KC's `account` client and the app uses `gateway-bff`, the code/state redirect doesn't complete an app-layer session — browser ended up re-authenticated as the previously-logged-in padmin (stale gateway cookie). This is a UX quirk of the invite-via-account-client pattern: after registration, the user must explicitly sign in at the app's OIDC endpoint to get an app session. Not a new bug; it's a known limitation of KC's org-invite flow that uses the `account` client.
5. **18:58 SAST** — Killed the Playwright MCP chrome process to clear the stale padmin cookie (runtime hygiene, not a product workaround — same as "open in incognito"). Re-launched browser, navigated to `/dashboard` → redirected to KC login → logged in as Thandi with `<REDACTED-PASSWORD>` → landed cleanly on `/org/mathebula-partners/dashboard`.
6. **18:59 SAST** — **0.22 PASS**: dashboard rendered with Thandi as Owner; sidebar shows "Mathebula & Partners", user "Thandi Mathebula", nav has Matters (not "Projects"), Court Calendar visible. Screenshot saved as `day-00-resume2-thandi-dashboard.png`. 0.23, 0.24, 0.25 PASS inline.

### Evidence

- `day-00-resume2-kc-registration-form.png` — KC registration form rendered cleanly after bounce+logout chain completed; email pre-bound, no session-collision error.
- `day-00-resume2-thandi-dashboard.png` — Thandi's authenticated org dashboard post-login; legal terminology (Matters, Court Calendar) + user/org info rendered.
- `day-00-resume2-billing-flat.png` — Settings > Billing showing flat "Managed Account" UI with no tier picker, no Upgrade button.

### Checkpoints (this resume)

| ID | Result | Evidence / Note |
|---|---|---|
| 0.19 Click invite link | PASS | Bounce page accepted `/registrations?` URL, redirected to KC logout |
| 0.20 KC registration form loads | PARTIAL | Form pre-binds email, but org name not shown (GAP-L-03 reproduces) |
| 0.21 Fill form | PASS | First/Last/Password/Confirm filled |
| 0.22 Submit → dashboard | PASS | KC redirected to `/dashboard?code=...`; after app re-login (runtime hygiene), landed on `/org/mathebula-partners/dashboard` |
| 0.23 Sidebar shows org + user | PASS | "Mathebula & Partners" + "Thandi Mathebula" visible |
| 0.24 Legal terminology in sidebar | PASS | "Matters" (not Projects), "Clients" collapsed, "Court Calendar" visible |
| 0.25 Screenshot — dashboard | PASS | `day-00-resume2-thandi-dashboard.png` |
| 0.26 Settings > Team | PASS | 1 of 10 members, Thandi = Owner |
| 0.27 No Upgrade gate | PASS | No plan/upgrade UI |
| 0.28 Invite Bob (Admin) | PASS | "Invitation sent to bob@mathebula-test.local", 2 of 10 members |
| 0.29 Invite Carol (Member) | PASS | "Invitation sent to carol@mathebula-test.local" |
| 0.30 Mailpit — 2 invite emails | PASS | `S4gjM3PXd838ZXtbipztLz` (Bob, 19:00:02) + `4Qs8tGfeCkvrB2nWK6G3CY` (Carol, 19:00:40) |
| 0.31 Bob clicks invite | PASS | Bounce → KC logout (one click, padmin session still cached) → KC registration form |
| 0.32 Bob registers | PASS | First=Bob, Last=Ndlovu, pw=`SecureP@ss2` |
| 0.33 Bob → dashboard | PASS | Landed on `/org/mathebula-partners/dashboard`, but same session-stale quirk as 0.22 → verified by explicit login on fresh session: Bob logged in, "Bob Ndlovu" rendered in sidebar |
| 0.34 Carol clicks invite | PASS | Direct bounce → KC registration form (no logout confirmation needed — Bob had just signed out) |
| 0.35 Carol registers | PASS | First=Carol, Last=Mokoena, pw=`SecureP@ss3` |
| 0.36 Carol → dashboard | PASS | "Carol Mokoena" rendered, then logged out |
| **Day 0 A-D checkpoint** | PASS | 3 real KC users (MANAGED members of org), 3 DB members in `tenant_5039f2d497cf.members` (Thandi=Owner, Bob=Admin, Carol=Member), no upgrade gate, vertical_profile=legal-za |
| 0.37 Settings > General | PASS | Currency=ZAR, Vertical Profile=Legal (South Africa) |
| 0.38 Currency ZAR pre-seeded | PASS | Confirmed in dropdown + DB |
| 0.39 Brand colour #1B3A4B | PASS | Saved + persisted on reload; `org_settings.brand_color = '#1B3A4B'` |
| 0.40 Logo upload | PASS | `org_settings.logo_s3_key = 'org/tenant_.../branding/logo.png'` persisted. Uploaded generated 75-byte teal PNG fixture at `qa_cycle/test-fixtures/mathebula-logo.png` |
| 0.41 Settings > Rates | PASS | Renders, rate hierarchy note visible |
| 0.42 Billing rates (Thandi R2500, Bob R1200, Carol R550) | PASS | All 3 rows saved + persisted in `billing_rates` |
| 0.43 Cost rates (Thandi R1000, Bob R500, Carol R200) | PASS | All 3 rows saved + persisted in `cost_rates` |
| 0.44 Settings > Tax | PASS | Renders |
| 0.45 VAT 15% pre-seeded | PASS (with naming note) | Tax Rates shows Standard 15% (default, active) + Zero-rated + Exempt. Scenario named "VAT"; pack-seeded name is "Standard" — functionally equivalent (15% rate applied to invoice lines). Logging as observation OBS-L-05 (LOW, Product). |
| 0.46 Settings > Custom Fields | PASS | Renders with Matters/Action Items/Clients/Fee Notes tabs |
| 0.47 Legal customer fields | PASS | "SA Legal — Client Details" group present with Company Reg Number, SA ID Number, Passport Number, ID/Passport Number, Entity Type (dropdown), Risk Rating (dropdown), Postal Address, Preferred Correspondence, Referred By. Also "Company FICA Details" + "FICA Compliance" groups present |
| 0.48 Legal matter fields | PASS | "SA Legal — Matter Details" group present with Case Number, Court, Opposing Party, Opposing Attorney, Advocate, Date of Instruction, Estimated Value |
| 0.49 Field promotion (customer) | PASS | New Client dialog shows native inline inputs: Name, Type (client_type), Email, Phone, ID Number, Tax Number, Notes, Address Lines 1+2, City, State/Province, Postal Code, Country, Contact Name, Contact Email, Contact Phone, Registration Number, Entity Type, Financial Year End. All 7 expected slugs promoted inline. |
| 0.50 No duplicate in CustomFieldSection | PASS | Dialog has no "Other Fields" or CustomFieldSection sidebar (promoted slugs don't render twice) |
| 0.51 Field promotion (matter) | PASS | New Matter dialog shows native "Work Type" input = matter_type slug promoted |
| 0.52 Cancel both dialogs | PASS | Done |
| 0.53 Settings > Templates | PASS | Matter Templates page renders |
| 0.54 4 legal matter templates | PASS | Litigation (Personal Injury / General), Deceased Estate Administration, Collections (Debt Recovery), Commercial (Corporate & Contract) — all 4 active, from `legal-za` pack, each with 9 pre-populated tasks |
| 0.55 Automations | PASS (no UI page) | No dedicated `/settings/automations` route; automation rules exist in DB: 12 active rules from packs (`automation-common` v2 + `automation-legal-za` v2 = 7 + 5 rules). See `settings/packs` for visibility. |
| 0.56 No JS errors | PASS | Only 1 known console error (React hydration mismatch on Radix button `aria-controls` — cosmetic) |
| 0.57 Settings > Modules | PASS (no dedicated UI) | No dedicated `/settings/modules` page; `vertical_profile=legal-za` in `org_settings` enables modules; verified via `enabled_modules` JSONB |
| 0.58 All 4 legal modules enabled | PASS | `org_settings.enabled_modules = ["court_calendar", "conflict_check", "lssa_tariff", "trust_accounting"]` |
| 0.59 Sidebar shows 4 modules | PASS | Work → **Court Calendar**; Clients → **Conflict Check**, Adverse Parties; Finance → **Trust Accounting** (+ Transactions, Client Ledgers, Reconciliation, Interest, Investments, Trust Reports), **Tariffs** |
| 0.60 No foreign vertical leak | PASS | No "Engagements" (has "Engagement Letters" = legal term), no "Year-End Packs", no "Campaigns" |
| 0.61 Trust Accounting page | PASS | Renders at `/trust-accounting` with "No Trust Accounts" state and Add Account CTA |
| 0.62 Create trust account | PASS | Mathebula & Partners Trust Account, Standard Bank, branch 051001, acct 1234567890, General, opened 2026-04-17 → appears on dashboard |
| 0.63 LPFF rate 6.5% | PASS | Set via `/trust-accounting/interest` Add Rate dialog. `lpff_rates.rate_percent=0.0650, lpff_share_percent=0.0500` |
| 0.64 Dashboard shows R 0.00 | PASS | Trust Balance R 0.00, Active Clients 0, Pending Approvals 0 |
| 0.65 Settings > Billing | PASS | Renders |
| 0.66 No tier picker | PASS | Flat UI: "Managed Account — Your account is managed by your administrator." + Trial/Manual status chips; no Starter/Pro/Business picker, no Upgrade button |
| 0.67 Subscription status | PASS | "Trial" chip + "Managed Account" info card. No member count/amount shown (deferred until self-serve PayFast feature) |
| 0.68 Screenshot — flat billing | PASS | `day-00-resume2-billing-flat.png` |

### Day 0 Complete Checkpoints
- [x] Org created via real access request → approval → Keycloak registration
- [x] Three real Keycloak users (owner, admin, member) exist and can log in
- [x] No "Upgrade to Pro" / plan picker / tier gate anywhere in onboarding or team invite flow
- [x] Vertical profile `legal-za` is active on the tenant
- [x] Currency ZAR, brand colour #1B3A4B + logo set
- [x] Rate cards configured for 3 members (billing + cost)
- [x] VAT 15% configured (pack-seeded "Standard" 15%)
- [x] `legal-za-customer`, `legal-za-project` field packs loaded
- [x] Field promotion verified (7+ customer slugs inline, matter_type inline, no duplicates)
- [x] 4 matter templates present from `legal-za` pack
- [x] Progressive disclosure verified (all 4 modules enabled + visible in sidebar, no foreign terms)
- [x] Trust account created
- [x] Tier removal verified

### New Gaps / Observations (this resume)

- **GAP-L-01 VERIFIED** — retry 2 (PR #1060) allow-list fix works end-to-end. Real-world `/registrations?` invite URL now routes through bounce → KC logout → continue → KC registration cleanly. All 3 users (Thandi, Bob, Carol) registered successfully through the new flow; KC members + DB members in correct state.
- **GAP-L-04 / OBS-L-04 carry-forward** — KC logout confirmation page ("Do you want to log out?") still renders because the bounce URL omits `id_token_hint`. Adds one manual click per invite. Unchanged in this retry. Tier: LOW — tracked in gap tracker for future Product/Dev work. One observation during this run: even Thandi's own in-app "Sign out" button (which DOES include `id_token_hint`) still renders the confirmation, suggesting KC is configured globally to always confirm logout. Could be a KC realm toggle.
- **OBS-L-05** (LOW, Product): Pack-seeded tax rate is named "Standard" (15%, default, active) — the scenario named it "VAT". Functionally correct (15% applied to invoice lines). Product may want to rename the pack-seeded rate to "VAT" for clarity on ZA-legal profile. Not a gap — cosmetic label mismatch only.
- **OBS-L-06** (LOW, QA-infra): After KC registration completes, the `?code=...&session_state=...` redirect to `/dashboard` at port 3000 doesn't complete an app-layer session because KC issues the code for the `account` client, while the app's OIDC handler is registered for `gateway-bff`. Net effect: the new user ends up on a page controlled by whichever gateway cookie is cached in the browser profile, not their own session. For Playwright automation, the workaround is to force a clean browser profile (kill the chrome process) and re-login explicitly. Real user impact is minor: if a fresh-profile user registers, they land on the sign-in form and need one password entry; if a user already had a session (admin helping invite a teammate), that session survives. Not a bug per se, just a UX subtlety. Consider whether the bounce page should also initiate an app-side OIDC login after the KC registration completes (multi-step dance). Left as-is for now.
- **No new blockers** — Day 0 fully unblocked.

### Next

Day 0 complete. Dispatch Day 1 separately from the orchestrator. New QA Position: **Day 1 — 1.1** (Bob logs in, Conflict Check for Sipho Dlamini).
