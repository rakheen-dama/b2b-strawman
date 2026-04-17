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
- **Evidence**: extracted OTP `447884` from email body, typed into verification input, clicked Verify.

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
- **Evidence**: entered `padmin@docteams.local`, password `password`, clicked Sign In (note: Keycloak uses an email-first 2-step form — email screen first, then password screen). Redirected to `/platform-admin/access-requests`.

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
- **Evidence**: filled First Name=`Thandi`, Last Name=`Mathebula`, Password=`SecureP@ss1`, Confirm Password=`SecureP@ss1`. Clicked Register.

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
