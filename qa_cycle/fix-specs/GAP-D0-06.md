# Fix Spec: GAP-D0-06 — Post-registration redirect lands on marketing page instead of dashboard

## Problem
After a Keycloak-invited user completes registration (CP 0.21), Keycloak redirects the browser to `http://localhost:3000/?session_state=...&iss=...&code=...`. The frontend root `/` renders the public marketing landing page and silently discards the `code` param. The user must manually navigate to `/dashboard` to reach the app. This breaks the demo narrative: the wow-moment handoff is "click Register → land on your firm's dashboard".

## Root Cause (confirmed)
When the backend approves an access request, it creates the KC organization via:

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java`, line 88:
```java
var body = Map.of("name", name, "alias", slug, "enabled", true, "redirectUrl", frontendBaseUrl);
```

`frontendBaseUrl` comes from the `@Value("${app.base-url:http://localhost:3000}")` constructor param (line 51). This `redirectUrl` attribute on the Keycloak organization is the URL KC sends the user to after invitation registration completes. So post-registration KC issues a redirect to `http://localhost:3000?code=...` — the root of the frontend.

The frontend root route (`frontend/app/page.tsx`) is a pure marketing landing page. It does not inspect `code` / `session_state` / `iss` query params. The auth middleware (`frontend/lib/auth/middleware.ts`, line 8) lists `/` as a **public** route, so it does not intercept the request to trigger an OIDC exchange either.

Meanwhile, the gateway is already configured correctly:
- `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java`, line 56: `oauth2Login(oauth2 -> oauth2.defaultSuccessUrl(frontendUrl + "/dashboard", true))` — once a Spring Security OAuth2 flow completes, it does land on `/dashboard`.
- `gateway/src/main/resources/application.yml`, line 37: registered `redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"` = `http://localhost:8443/login/oauth2/code/keycloak`.

The problem is that Keycloak's organization `redirectUrl` bypasses the gateway's OAuth2 flow entirely. The user's browser is handed directly to the frontend with an OIDC code that nothing knows how to consume.

## Fix
Set the org `redirectUrl` to the frontend's `/dashboard` path. The flow then becomes:
1. KC finishes registration → redirects browser to `http://localhost:3000/dashboard`.
2. Frontend middleware sees `/dashboard` is NOT in PUBLIC_ROUTES, checks for `SESSION` cookie, doesn't find one (user's session is with Keycloak not the gateway), redirects to `http://localhost:8443/oauth2/authorization/keycloak`.
3. Gateway starts OAuth2 authorization_code flow against KC. Keycloak still has the user's KC session cookie (just registered), so it silently issues a code without showing a login page.
4. Gateway's `/login/oauth2/code/keycloak` callback consumes the code, establishes the `SESSION` cookie, and uses `defaultSuccessUrl` to redirect to `http://localhost:3000/dashboard`.
5. Frontend `/dashboard` now has the SESSION cookie → flows through `getAuthContext()` → redirects to `/org/{slug}/dashboard` via the existing logic in `frontend/app/(app)/org/[slug]/layout.tsx` lines 45–47.

### Implementation
In `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java`:

1. Update the constructor so that `frontendBaseUrl` is the **base** URL (keep the field), but derive a new field `organizationRedirectUrl` by appending `/dashboard`:
   ```java
   this.frontendBaseUrl = frontendBaseUrl;
   this.organizationRedirectUrl = frontendBaseUrl.replaceAll("/+$", "") + "/dashboard";
   ```
   (Use a simple concatenation if you prefer; the `replaceAll` just guards against a trailing slash in the config value.)

2. Line 88 — replace `"redirectUrl", frontendBaseUrl` with `"redirectUrl", organizationRedirectUrl`:
   ```java
   var body = Map.of(
       "name", name,
       "alias", slug,
       "enabled", true,
       "redirectUrl", organizationRedirectUrl);
   ```

3. Document in a comment above line 88 why `/dashboard` is the correct target (invitation flow → frontend middleware → gateway OAuth2 → `/dashboard` success URL).

### Why not change `app.base-url` directly
Because `frontendBaseUrl` is likely used elsewhere (e.g., other templates, email links) as the bare domain. Scoping the `/dashboard` suffix to only this KC-org `redirectUrl` is safer and more targeted.

### Regression risk
The existing unit/integration tests for `KeycloakProvisioningClient` may assert the exact body map. Update any test that checks `"redirectUrl"` to match the new value.

## Scope
- Backend
- Files to modify:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java`
  - Any test file that asserts the `createOrganization` request body's `redirectUrl` key — grep `redirectUrl` under `backend/src/test/` before committing.
- Files to create: none
- Migration needed: no

## Verification
1. Clean KC state for test org.
2. Run through CP 0.1–0.21 end-to-end (access request → OTP → admin approval → KC registration).
3. After clicking Register in the KC invitation flow, observe the final browser URL.
4. **Expected**: Browser ends at `http://localhost:3000/org/mathebula-partners/dashboard` (org-scoped dashboard), WITHOUT the user having to manually navigate. No `code=` query param in the final URL — the gateway OAuth2 callback should have consumed it.
5. Dev tools → Network tab should show the chain: `/?` (no — should not appear) vs. `/dashboard` → `8443/oauth2/authorization/keycloak` → `8180/realms/docteams/...auth?` → `8443/login/oauth2/code/keycloak?code=...` → `/dashboard` → `/org/{slug}/dashboard`.
6. Re-run CP 0.22 — should now PASS instead of PARTIAL.

## Estimated Effort
S (< 30 min) — single field change, possibly 1 test update.

## Priority Reason
MED severity, demo narrative breaker: a customer watching the demo will see the landing page after registration and ask "is this the app?" This is worth fixing in Cycle 1.
