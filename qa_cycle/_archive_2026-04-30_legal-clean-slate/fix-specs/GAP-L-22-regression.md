# Fix Spec: GAP-L-22 REGRESSION — KC registration callback bypasses gateway-bff OAuth2 handler

## Problem

QA Day 0 checkpoint 0.21 (verify cycle, 2026-04-24): after Thandi completes KC
registration via the invite link, the browser lands on padmin's
`/platform-admin/access-requests` page with **"Authentication session expired"** —
not on her firm dashboard. The L-22 middleware shipped in PR #1125 fires the KC
logout bounce correctly at step 0.18, but the **handoff check on the return leg
never triggers** because the KC registration callback goes to
`http://localhost:3000/dashboard?code=…` directly (code issued for KC client
`account`, not `gateway-bff`). That auth code is never consumed by anyone — the
frontend doesn't know how, and the gateway never sees it — so the gateway's
OAuth2 success handler never fires, `KC_LAST_LOGIN_SUB` cookie is never set, and
the L-22 middleware passes the request through with padmin's stale BFF SESSION
cookie intact. `/bff/me` returns padmin's claims for ~5s until the refresh fails
with `[invalid_grant] Session not active`.

Evidence: `qa_cycle/checkpoint-results/day-00.md` steps 0.18→0.22,
`.svc/logs/gateway.log` at `2026-04-24T21:35:07 → 21:37:19`, screenshots
`day-00-screenshots/gap-L22-reopened-thandi-lost-on-padmin-page.png` +
`L22-post-thandi-register-padmin-page-leak.png`.

## Root Cause (confirmed via code read)

The KC organization is created with its `redirectUrl` attribute set to the
**frontend** origin. KC renders that value into every invite link's
`redirect_uri` query param. After registration, the `account`-client auth code
is delivered straight to the frontend, which has no OAuth2 handler — bypassing
the gateway-bff flow entirely.

- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java:79`
  ```java
  this.organizationRedirectUrl = frontendBaseUrl.replaceAll("/+$", "") + "/dashboard";
  ```
  `frontendBaseUrl` is injected from `app.base-url` (default
  `http://localhost:3000`, see `backend/src/main/resources/application.yml:85`).
- Same file line 94 stamps `redirectUrl=organizationRedirectUrl` on the KC org
  during `POST /admin/realms/docteams/organizations` — so every invite link
  emitted from that org (invitation from founder AND all subsequent team
  invites via `KeycloakAdminClient.inviteMember`) carries
  `?redirect_uri=http://localhost:3000/dashboard` downstream.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/keycloak/KeycloakAdminClient.java:153-166`
  — `inviteMember(orgId, email, role, redirectUrl)` omits the `redirectUrl`
  form param when it is null/blank (the current caller in
  `InvitationService.java:116` passes `null`), so KC uses the **org default**
  every time.
- `frontend/lib/auth/middleware.ts:81-95` — the L-22 handoff check only runs
  when `KC_LAST_LOGIN_SUB` is present. That cookie is only set by the gateway's
  OAuth2 success handler (`gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java:128-148`),
  which only fires when Spring Security completes an auth-code exchange on
  `/login/oauth2/code/keycloak` for client `gateway-bff`. The post-registration
  code issued for client `account` lands on `/dashboard` on port 3000 and never
  reaches the gateway, so the success handler never fires. Middleware sees the
  SESSION cookie (padmin's), no signal cookie, passes through.

The arch consultant's framing is correct. The middleware is not broken. The
**trigger path** (forcing the callback to pass through the gateway's OAuth2
flow so the success handler fires) is what's missing.

## Fix — Option A: route the post-registration callback through gateway-bff

Change the KC organization's `redirectUrl` so the browser lands on a
**frontend-hosted bounce page** that immediately forwards to
`http://localhost:8443/oauth2/authorization/keycloak`. That gateway URL starts a
fresh OAuth2 code flow for client `gateway-bff`:

1. Gateway redirects browser to KC `/auth?client_id=gateway-bff&…`.
2. KC sees its fresh SSO cookie (set moments ago when registration completed),
   skips the login screen, and issues an auth code for `gateway-bff`.
3. KC redirects to `http://localhost:8443/login/oauth2/code/keycloak?code=…`.
4. Gateway exchanges the code, `sessionFixation.changeSessionId` rotates the
   SESSION id (bye, padmin's session row), the OAuth2 success handler fires
   and sets `KC_LAST_LOGIN_SUB=<Thandi's sub>`.
5. Browser lands on `defaultSuccessUrl=http://localhost:3000/dashboard` with
   the new SESSION cookie + the short-lived signal cookie.
6. L-22 middleware sees `KC_LAST_LOGIN_SUB` present → calls `/bff/me` →
   now returns Thandi → match → consume cookie → pass through. Thandi lands
   on her own firm dashboard.

We use a frontend bounce page rather than pointing the org `redirectUrl`
directly at the gateway because KC 26.x validates `organization.redirectUrl`
against existing realm clients' `validRedirectUris`. The `gateway-bff` client
already has `http://localhost:3000/*` registered (see
`compose/keycloak/realm-export.json:57-60`), so a new frontend page under
`/accept-invite/*` is inside that allow-list with zero realm-export changes.

### Step 1 — Create the post-registration bounce page

**File to create:** `frontend/app/accept-invite/complete/page.tsx`

A server component mirroring `frontend/app/accept-invite/continue/page.tsx`
(same `AcceptInviteRedirect` client helper, same noscript fallback, same
layout). The redirect target is the gateway OAuth2 kickoff:

```tsx
import type { Metadata } from "next";
import { AcceptInviteRedirect } from "../redirect-client";

export const metadata: Metadata = {
  title: "Finishing sign-in",
  description: "Signing you in to your new organization",
};

const GATEWAY_URL = (
  process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8443"
).replace(/\/$/, "");

// Always forward to the gateway OAuth2 kickoff. The gateway starts a fresh
// auth-code flow for client=gateway-bff; KC skips the login screen (SSO cookie
// from the just-completed registration is still valid) and issues a code for
// gateway-bff. The success handler sets KC_LAST_LOGIN_SUB; L-22 middleware
// then verifies the handoff against /bff/me.
//
// Any query params KC appended to the reduri (e.g. ?code=…&session_state=…)
// are intentionally dropped — that code is for client=account and has no
// consumer. The gateway flow obtains its own fresh code for gateway-bff.
const OAUTH2_KICKOFF_URL = `${GATEWAY_URL}/oauth2/authorization/keycloak`;

export default function AcceptInviteCompletePage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-12 dark:bg-slate-950">
      <div className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-8 text-center shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <h1 className="font-display text-xl font-semibold tracking-tight text-slate-900 dark:text-slate-100">
          Finishing sign-in&hellip;
        </h1>
        <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
          Welcome aboard — signing you in to your dashboard.
        </p>
        <AcceptInviteRedirect redirectUrl={OAUTH2_KICKOFF_URL} />
      </div>
    </div>
  );
}
```

Grep confirmations (done during spec):
- `/accept-invite(.*)` is already in `PUBLIC_ROUTES` at
  `frontend/lib/auth/middleware.ts:15` — the new route is publicly reachable
  without a SESSION cookie. ✓
- `AcceptInviteRedirect` (`frontend/app/accept-invite/redirect-client.tsx`) is
  already the shared client bounce — reused, no duplication.
- The validator in `frontend/app/accept-invite/validate.ts` does **not** need
  to be touched — it only gates `?kcUrl=` params for the inbound bounce flow;
  the `/complete` page has no query params to validate.

### Step 2 — Point the KC organization at the new page

**File to modify:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java`

Change line 79 from:
```java
this.organizationRedirectUrl = frontendBaseUrl.replaceAll("/+$", "") + "/dashboard";
```
to:
```java
this.organizationRedirectUrl =
    frontendBaseUrl.replaceAll("/+$", "") + "/accept-invite/complete";
```

Update the inline comment at lines 90-91 to reflect the new flow:
```java
// redirectUrl targets /accept-invite/complete so post-registration bounces back
// through gateway-bff's OAuth2 login flow (the account-client auth code is
// intentionally discarded). This lets the gateway's OAuth2 success handler
// fire and set KC_LAST_LOGIN_SUB for the L-22 middleware handoff check. See
// qa_cycle/fix-specs/GAP-L-22-regression.md.
```

No other backend files change. `KeycloakAdminClient.inviteMember(orgId, email,
role, null)` continues to pass `redirectUrl=null` → KC falls back to the org
default → the new bounce URL flows through automatically for Bob and Carol's
invites on Day 0 checkpoints 0.28/0.29.

### Step 3 — Existing orgs (forward compatibility)

The `mathebula-partners` org in the current QA environment was created with the
old `/dashboard` redirectUrl. The verify cycle's standard pre-flight already
wipes the KC org + tenant + access_request row before each Day 0 run (see
`qa_cycle/checkpoint-results/day-00.md` lines 14-20), so QA gets a freshly
provisioned org with the new redirectUrl on the first new approval. **No
migration is required for the QA cycle.**

For production deployment (separate track — not this fix), the standard shape
would be: (a) add a `PATCH /admin/realms/{realm}/organizations/{orgId}` call on
the `KeycloakAdminClient` that updates the `redirectUrl` attribute and (b) run
a one-time reconciler over all existing orgs. Call that out as a follow-up in
the Status Triage section; do **not** bundle it into this spec.

## Scope

**Backend + Frontend.**

- Files to modify:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java`
    (single-line change on line 79 + comment refresh on lines 90-91)
- Files to create:
  - `frontend/app/accept-invite/complete/page.tsx` (~40 lines, pattern clone of
    `frontend/app/accept-invite/continue/page.tsx`)
- Tests to update / add:
  - `backend/src/test/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClientTest.java`
    (or nearest equivalent) — assert the `redirectUrl` in the posted org-create
    body now ends with `/accept-invite/complete` (update whatever existing
    assertion pins `/dashboard`).
  - New Playwright / Vitest coverage for the frontend page is **optional** —
    the component is a trivial clone of `/accept-invite/continue` which
    already has behavioural tests via `validate.test.ts` surrounding it; the
    `/complete` route has no input validation to unit-test.
- Migration needed: **no** (Flyway unchanged; KC realm export unchanged).
- Env / config: reuses existing `app.base-url` (backend) and
  `NEXT_PUBLIC_GATEWAY_URL` (frontend — already consumed by
  `frontend/lib/auth/middleware.ts:5`). No new properties.
- KC realm export (`compose/keycloak/realm-export.json`): **unchanged** —
  `http://localhost:3000/*` in `gateway-bff.redirectUris` already covers the
  new path.

## Verification

QA re-runs Day 0 from a fresh pre-flight wipe:

1. `dev-down.sh --clean` is **not** needed; the standard QA pre-flight (wipe
   tenant schema + KC org + access_request + Mailpit) re-creates the org with
   the new `redirectUrl`.
2. **Phase A** (0.1-0.17): request-access + padmin approval + KC invitation
   email — all previously PASS, should remain PASS. Additional assertion:
   open the Mailpit invite email, inspect the `href` on the "Accept
   Invitation" button — the outer URL is the same frontend bounce
   (`/accept-invite?kcUrl=…`) but the `kcUrl` param now decodes to an inner
   `redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Faccept-invite%2Fcomplete`.
3. **Phase B — the fix-under-test** (0.18-0.22): Thandi opens the invite in
   the same browser context where padmin's SESSION cookie is still live.
   Expected flow:
   - 0.18: `/accept-invite` → KC logout bounce fires (unchanged).
   - 0.19: registration page renders (unchanged).
   - 0.20: Thandi submits registration. KC redirects to
     `http://localhost:3000/accept-invite/complete?code=…&session_state=…`.
   - **new**: `/accept-invite/complete` page loads → client-side
     `window.location.replace('http://localhost:8443/oauth2/authorization/keycloak')`.
   - Gateway starts fresh OAuth2 for `gateway-bff` → KC issues code
     immediately (SSO cookie still valid) → gateway completes exchange →
     success handler sets `KC_LAST_LOGIN_SUB=<Thandi's sub>` cookie →
     sessionFixation rotates SESSION id → browser redirected to
     `http://localhost:3000/dashboard`.
   - L-22 middleware: sees `KC_LAST_LOGIN_SUB` present → `/bff/me` returns
     Thandi's userId → match → consume cookie → pass through.
   - 0.21: Thandi lands on `/org/mathebula-partners/dashboard`. **PASS**.
   - 0.22: Sidebar shows Mathebula & Partners + Thandi's user card. **PASS**.
4. **Phase C / D** (0.28-0.32): Bob + Carol registration via their invite
   emails should show the same clean handoff (their invites inherit the same
   org `redirectUrl`). All three registrants — Thandi, Bob, Carol — land on
   their own firm dashboard with no sidebar / page leakage from the
   previously-authenticated user.
5. **Regression sanity**: returning users (padmin logging in a second time,
   Bob re-logging in on Day 4) see **no** change — they never hit
   `/accept-invite/complete`, and the middleware's `KC_LAST_LOGIN_SUB` path
   already handles the routine match case with `return NextResponse.next()`.
6. **Gateway log**: `.svc/logs/gateway.log` should show one `/oauth2/authorization/keycloak`
   → `/login/oauth2/code/keycloak` → success handler → `KC_LAST_LOGIN_SUB`
   cookie set per registration. No more `[invalid_grant] Session not active`
   after a Thandi-style registration.

## Estimated Effort

**S (≤30 min).** One frontend file create (40-line clone), one backend
line-change (string literal + comment). Backend test assertion flip is the
only test touch. No migrations, no new config, no realm changes. High
confidence: every moving part is already in place — we're only redirecting
the post-registration bounce through an existing frontend route that hits an
existing gateway OAuth2 kickoff that hits an already-fixed L-22 middleware.

## Risk / Rejected Alternatives

### Why not Option B (make middleware more aggressive without the signal cookie)
Rejected. The middleware cannot cheaply detect "this is a just-completed KC
callback" without the signal cookie — it would have to `/bff/me`-probe on
*every* authenticated request, tripling fetch load for the common
already-signed-in case. Broad over-firing also risks re-auth loops: if
`/bff/me` briefly returns stale data, the middleware would force a logout on a
healthy session. The L-22 invariant that we never silently log out a returning
user based on server-side heuristics stays intact only if the signal cookie
is the trigger. Fix the trigger (Option A), not the detector (Option B).

### Why not Option C (Spring Security AuthenticationSuccessHandler doing the logout)
Not revived. The original arch consultant rejected this because a silent
server-side logout would catch returning users, not just post-registration
handoff. PR #1125 already explicitly chose Option B+ over this for that
reason, and nothing about the regression changes that calculus.

### Residual risk
- **KC redirect-uri validation**: if KC 26.5 applies per-client validation on
  `organization.redirectUrl` at org-create time (not realm-wide), the POST to
  `/admin/realms/{realm}/organizations` could 400 with "Invalid redirect uri".
  Mitigation: the new URL is a subpath of an existing whitelisted pattern
  (`http://localhost:3000/*` on gateway-bff). KC 26.x wildcard matching
  accepts any path under `/*`. Confirmed by existing `/dashboard` behaviour —
  same wildcard is in play. Zero realm-export change needed.
- **`kcUrl` allow-list (`frontend/app/accept-invite/validate.ts`)** is
  unchanged. The outbound bounce (step 0.18) still validates the inbound
  `kcUrl` param. The new `/accept-invite/complete` page is a **terminal
  redirect** with no attacker-controllable input — zero open-redirect surface.
- **Double bounce**: the user now traverses `/accept-invite` → KC logout →
  `/accept-invite/continue` → KC register → **`/accept-invite/complete`** →
  gateway OAuth2 → KC → gateway callback → `/dashboard`. One additional hop.
  Imperceptible in practice (sub-second, no UI flash once HTTPS keeps the
  favicon warm), and QA scenario 0.21 tolerates any intermediate bounces.

## Status Triage

**SPEC_READY.** One-file backend line change + 40-line frontend page clone.
Root cause is precisely scoped and evidenced in code. Follow-up item tracked
separately: when this ships to an environment with pre-existing KC orgs, run
a one-time reconciler that `PATCH`es `redirectUrl` on each existing org
(`KeycloakAdminClient.updateOrganizationRedirectUrl(orgId, url)` — ~15 lines
following the pattern of the existing `updateMemberRole`). Not part of this
spec; QA pre-flight wipes all state before Day 0 so the verify cycle does not
need the reconciler.
