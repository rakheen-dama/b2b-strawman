# Fix Spec: GAP-L-01 — Keycloak Invite Session Collision + Token Consumption

## Problem

From `day-00.md` §0.22: "instead of redirect, Keycloak rendered an error page titled 'Something went wrong' with body 'You are already authenticated as different user 'padmin@docteams.local' in this session. Please sign out first.' Even though we opened the invite in a new browser-tab (per the GAP-C-02 carry-forward workaround guidance), tabs in the same Chrome profile share cookies for `localhost:8180`, so the padmin KC session was inherited. After manually logging padmin out… we re-opened the invite link — but Keycloak now responds 'expiredActionMessage — The link you clicked is no longer valid. It may have expired or already been used.' So the invite token was consumed by the first failed attempt."

Day 0 is blocked at checkpoint 0.22; Day 1+ cannot proceed until Thandi can register.

## Root Cause (validated)

Validated via grep/read:

1. **Email template**: `compose/keycloak/themes/docteams/email/html/org-invite.ftl` line 9 contains
   `<a href="${link}" …>Accept Invitation</a>`. The `${link}` is rendered by Keycloak core (Java) as
   `http://localhost:8180/realms/docteams/login-actions/action-token?key=<JWT>&client_id=gateway-bff`.
   The user lands DIRECTLY on Keycloak with no opportunity for our app to inspect/clear the existing
   Keycloak SSO cookie (set on `localhost:8180` by the padmin login).
2. **Cookie-jar scope**: `realm-export.json` line 62 registers `"post.logout.redirect.uris": "http://localhost:3000##http://localhost:3000/*"` for `gateway-bff` — so a frontend page under `http://localhost:3000/*` IS eligible as a `post_logout_redirect_uri`.
3. **Token-consumption behavior**: This is baked into Keycloak 26.5 core (`OrgInviteTokenActionToken`
   calls `setConsumed=true` before the session-collision check). Verified by reading KC 26 source
   docs; we cannot fix this without writing a custom Keycloak SPI `ActionTokenHandler` — which is
   >2hr of Java + JAR packaging + theme rebuild. **Rejected as out-of-scope for this cycle.**
4. **Frontend routing**: `frontend/proxy.ts` + `frontend/lib/auth/middleware.ts` lines 7-15 define a
   `PUBLIC_ROUTES` list; adding `/accept-invite(.*)` makes the new page reachable without auth.
5. **Email template customization**: `compose/keycloak/themes/docteams/email/html/org-invite.ftl` is
   already a docteams override — it's a template-only file, bind-mounted into KC container via
   `compose/docker-compose.yml` line 77 (`./keycloak/themes:/opt/keycloak/themes`). We can edit it
   without a JAR rebuild; a KC restart picks it up.

**Rejected hypothesis** — "the gateway logout handler could destroy the KC session on invite click": the
Gateway BFF session (cookie `SESSION` on `localhost:8443`) is separate from the KC SSO cookie
(`localhost:8180`). Killing the BFF session doesn't clear the KC session that's actually causing the
collision. We must route through KC's own `end_session_endpoint`.

## Fix

A two-part, <2-hour fix that makes the invite flow retry-safe by preventing the KC session
collision from ever occurring — so the token is NOT consumed on a failed attempt.

### Phase 1 — Frontend bounce page (`/accept-invite`) — 30 min

**Goal**: Intercept the invite click, destroy any resident KC SSO session, then forward to the
original KC invite URL.

1. Create `frontend/app/accept-invite/page.tsx` (new file, server component):
   - Read `kcUrl` query param (URL-encoded original KC invite URL).
   - If absent or not on the `localhost:8180` origin (allow-list the KC realm prefix
     `http://localhost:8180/realms/docteams/login-actions/`), render an error.
   - Otherwise, immediately render a client component that:
     (a) First-render: calls `window.location.replace` to
         `http://localhost:8180/realms/docteams/protocol/openid-connect/logout?client_id=gateway-bff&post_logout_redirect_uri=<URL-encoded http://localhost:3000/accept-invite/continue?kcUrl=<original_kc_url>>`
         — KC clears its own SSO cookie on this call, then redirects back to our `/continue` page.

2. Create `frontend/app/accept-invite/continue/page.tsx` (new file, server/client hybrid):
   - Read `kcUrl` query param. Validate allow-list as above.
   - Render a client component that immediately does `window.location.replace(kcUrl)`.
   - Because the KC session is now cleared, the user lands on KC with a clean cookie jar; the
     invite token is NOT invalidated by a collision this time and the registration form is
     presented normally.
   - Include a visible message ("Preparing your registration…") in case the redirect is slow.

3. Edit `frontend/lib/auth/middleware.ts` line 7-15: add `"/accept-invite(.*)"` to
   `PUBLIC_ROUTES`. Both `/accept-invite` AND `/accept-invite/continue` must be publicly routable
   (no session required).

4. Security constraint: the `kcUrl` validator MUST strictly allow only
   `http://localhost:8180/realms/docteams/login-actions/` as a prefix (and in production, the
   corresponding KC hostname). This prevents open-redirect abuse.

### Phase 2 — Email template rewrite — 15 min

Edit `compose/keycloak/themes/docteams/email/html/org-invite.ftl` line 9:

**Before:**
```html
<a href="${link}" class="btn-primary" …>Accept Invitation</a>
```

**After:**
```html
<#assign bounceBase = "http://localhost:3000/accept-invite"/>
<#assign bounceUrl  = bounceBase + "?kcUrl=" + link?url('UTF-8')/>
<a href="${bounceUrl}" class="btn-primary" …>Accept Invitation</a>
```

Notes:
- The `bounceBase` must be configurable per environment. For local dev, hard-code
  `http://localhost:3000`. For prod (when we get there), wire it through a Keycloak SMTP-realm
  attribute or accept a local-dev-only fix for now (this QA cycle is local-only).
- `link?url('UTF-8')` uses Freemarker's built-in URL encoder to preserve the Keycloak action-token
  JWT correctly.

### Phase 3 — Add "Log out and continue" CTA on KC error page — 30 min

For the edge case where a user somehow bypasses the bounce page (e.g., bookmarked an old invite
URL) and still hits the collision error, improve the UX by adding a self-service CTA.

Edit `compose/keycloak/theme/src/login/pages/Error.tsx`:

1. Detect the "authenticated as different user" case by pattern-matching `message.summary` for the
   string `"already authenticated as different user"` (English) — in a new detection block.
2. Conditionally render an extra button below the error message:
   `Sign out and try again` → links to
   `http://localhost:8180/realms/docteams/protocol/openid-connect/logout?client_id=gateway-bff&post_logout_redirect_uri=http://localhost:3000/`.
3. Only show when the detection matches; otherwise keep the existing "Back to application" link.

Then rebuild the theme JAR:

```bash
cd compose/keycloak/theme
pnpm install && pnpm run build-keycloak-theme
cp dist_keycloak/keycloak-theme.jar ../providers/keycloak-theme.jar
```

Restart Keycloak: `docker restart b2b-keycloak`.

### Phase 4 — Verification

After all three phases:

```bash
# Restart KC to pick up theme + email changes
docker restart b2b-keycloak
```

Manual reproduction steps (QA re-runs Day 0 from 0.19):
1. Log in as padmin, approve Mathebula access request, wait for invite email in Mailpit.
2. Extract invite URL from email body — it should now start with
   `http://localhost:3000/accept-invite?kcUrl=…`.
3. Click in same-profile browser tab (while padmin session is still active on `:8180`).
4. Expect: brief flash of bounce page → KC logout → land on KC registration form with email
   pre-bound, NO "already authenticated as different user" error.
5. Complete registration → redirect to dashboard.

Edge-case tests:
- Without a pre-existing padmin session: bounce page still redirects through KC logout (which is a
  no-op when no session exists) → still lands on registration form. (Second call to
  `end_session_endpoint` with no session is handled cleanly by KC 26.)
- With a bookmarked old invite URL (bypasses bounce): user sees Phase 3's "Sign out and try again"
  CTA.

## Scope

- **Frontend**: YES — new pages `app/accept-invite/page.tsx` and `app/accept-invite/continue/page.tsx`; edit `lib/auth/middleware.ts`.
- **Keycloak theme**: YES — edit `themes/docteams/email/html/org-invite.ftl`; edit
  `theme/src/login/pages/Error.tsx` + rebuild JAR.
- **Backend**: NO.
- **Gateway**: NO.
- **Seed/migration**: NO.
- **Realm JSON changes**: NO (post-logout redirect URI whitelist already covers
  `http://localhost:3000/*`).

Files to modify:
- `compose/keycloak/themes/docteams/email/html/org-invite.ftl`
- `compose/keycloak/theme/src/login/pages/Error.tsx`
- `frontend/lib/auth/middleware.ts`

Files to create:
- `frontend/app/accept-invite/page.tsx`
- `frontend/app/accept-invite/continue/page.tsx`
- `frontend/app/accept-invite/validate.ts` (shared `kcUrl` allow-list validator)

JAR rebuild required: YES (for Phase 3). Output artifact:
`compose/keycloak/providers/keycloak-theme.jar` replaced.

KC restart required: YES (`docker restart b2b-keycloak` after theme changes).

## Verification (QA checkpoint)

Re-run Day 0 checkpoints 0.19 → 0.22 from `qa/testplan/demos/legal-za-90day-keycloak.md`.
Then continue to 0.23–0.68 and beyond.

Expected: 0.22 PASSES; Thandi lands on dashboard; retry-safety holds if she accidentally clicks the
email link twice.

## Estimated Effort

**M (~75 min total)**: Phase 1 (30 min) + Phase 2 (15 min) + Phase 3 (30 min).

Phase 3 alone is optional-but-recommended; Phases 1+2 together already unblock QA and solve both
the collision and token-consumption problems end-to-end.

## Notes

**Alternative rejected — Keycloak SPI `ActionTokenHandler` to re-order validation**: this is the
"proper" fix for token-consumption, but writing a custom `InviteOrgActionToken` Java provider,
packaging it as a JAR, testing it, and wiring it into the realm takes 4-6 hours. The bounce-page
approach is strictly better for this cycle — it prevents the collision from ever occurring, so the
token-consumption bug in KC core becomes irrelevant.

**Alternative rejected — Playwright-level workaround ("always use fresh Chrome profile")**: the
user instruction explicitly says "The fix must not require changes to the QA scenario file — the
user wants the product to meet the scenario, not vice versa." A fresh-profile workaround fixes only
the QA flow, not real end-user UX. Rejected.

**Production concern — hardcoded `http://localhost:3000` in email template**: acceptable for the
local dev QA cycle. Before shipping to staging/prod, `bounceBase` must be wired through a
realm-scoped SMTP-template attribute or the `frontendBaseUrl` equivalent used by
`KeycloakProvisioningClient.java`. Logged as a follow-up (not a blocker for this cycle).

**Open question (for user)**: if Phase 3's theme rebuild fails in the agent's environment (Node
version mismatch, pnpm issues), Phase 3 is OPTIONAL — Phases 1+2 are sufficient to unblock QA.
Dev agent should verify Phase 1+2 works first, then attempt Phase 3. If Phase 3 stalls, defer it
and file a follow-up GAP.
