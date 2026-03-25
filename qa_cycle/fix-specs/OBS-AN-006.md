# Fix Spec: OBS-AN-006 — Gateway BFF returns 302 for server action mutations

## Problem

All POST/PUT/DELETE requests from Next.js server actions through the gateway BFF (port 8443) return HTTP 302 redirects instead of proxying to the backend. This blocks every mutation in the UI when running in Keycloak mode: toggle automation rule, delete rule, save notification preferences, activate template, etc.

GET requests through the gateway work correctly (pages load with data). The backend API (port 8080) works correctly when called directly. The issue is isolated to the gateway's handling of mutating requests originating from the Next.js server.

Evidence from QA Cycle 2:
- Toggle automation rule via UI: 302 (GAP-AN-003 reopened)
- Save notification preferences via UI: 302 (but works via direct backend API call)
- All backend POST/PUT/DELETE endpoints verified working via curl/direct calls

## Root Cause

The gateway's Spring Security configuration in `GatewaySecurityConfig.java` (line 39-72) uses `.oauth2Login()` which registers a default `AuthenticationEntryPoint` that issues HTTP 302 redirects to the Keycloak authorization endpoint for any unauthenticated request.

**File**: `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java`, lines 39-72

The authentication flow works as follows:

1. User authenticates via browser -> gateway OAuth2 flow -> SESSION cookie set on `localhost` (no domain attribute, shared across ports per RFC 6265)
2. Next.js middleware (`frontend/lib/auth/middleware.ts`, line 55) checks for SESSION cookie presence
3. For page loads (GETs), the API client (`frontend/lib/api/client.ts`, lines 21-37) reads the SESSION cookie via `cookies()` and forwards it as `Cookie: SESSION=<value>` to the gateway
4. The gateway validates the session and proxies the request to the backend via `TokenRelay` filter

**The 302 occurs because**: When the Next.js server sends a POST/PUT/DELETE to the gateway, Spring Security's `oauth2Login()` default entry point treats the request as "unauthenticated" and responds with 302 redirect to `http://localhost:8180/realms/docteams/protocol/openid-connect/auth?...`.

There are two complementary issues:

### Issue A: No API-specific AuthenticationEntryPoint

The gateway has no custom `exceptionHandling` configuration. With `oauth2Login()`, Spring Security's default `AuthenticationEntryPoint` is `LoginUrlAuthenticationEntryPoint` (or the OAuth2 equivalent), which returns 302 for ALL unauthenticated requests regardless of whether they're browser page loads or API calls. API consumers (like the Next.js server) cannot follow OAuth2 redirects — they need a 401 response to handle authentication failures programmatically.

### Issue B: fetch() follows redirects by default

Even if the session IS valid, if the gateway returns a 302 for any reason, Node.js `fetch()` follows redirects automatically. The `apiRequest` function in `client.ts` does not set `redirect: "manual"`, so a 302 from the gateway gets silently followed. The final response is likely the Keycloak login HTML page (200 with text/html), which `apiRequest` tries to parse as JSON, causing a confusing error.

### Why GETs work

GET requests to `/api/**` succeed because they go through the same `apiRequest` path and the session IS valid for those. The 302 appears to be triggered specifically when the gateway encounters a POST/PUT/DELETE and Spring Security's session validation or the `TokenRelay` filter behaves differently for mutating requests. Possible sub-causes:
- The `TokenRelay` filter may require re-authentication or token refresh for the OAuth2 access token on mutating requests
- Spring Security's `SavedRequestAwareAuthenticationSuccessHandler` (default with oauth2Login) may interfere with POST processing
- The gateway's session `timeout: 8h` may not be the issue, but the underlying OAuth2 access token (stored in the session) may have expired, causing `TokenRelay` to trigger a re-authorization flow

## Fix

### Step 1: Add API-specific AuthenticationEntryPoint (gateway)

**File**: `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java`

Add `.exceptionHandling()` to the security filter chain to return 401 for API requests instead of 302:

```java
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
```

After the `.csrf(...)` block and before `.sessionManagement(...)`, add:

```java
.exceptionHandling(ex -> ex
    .defaultAuthenticationEntryPointFor(
        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
        new AntPathRequestMatcher("/api/**")))
```

This ensures that unauthenticated requests to `/api/**` get a clean 401 instead of a 302 redirect. The frontend's `apiRequest` already handles 401 by redirecting to sign-in (`handleApiError` in `client.ts` line 141-143).

### Step 2: Add redirect: "manual" to fetch in apiRequest (frontend)

**File**: `frontend/lib/api/client.ts`

Add `redirect: "manual"` to the `fetch()` call (line 82) so that 302 responses are surfaced as errors rather than silently followed:

```typescript
const response = await fetch(`${API_BASE}${endpoint}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...authOptions.headers,
      ...options.headers,
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
    cache: options.cache,
    next: options.next,
    credentials: authOptions.credentials,
    redirect: "manual",
  });
```

Then add a check after the fetch to detect 3xx responses and treat them as authentication failures:

```typescript
// Detect redirect responses (e.g., gateway redirecting to login)
if (response.status >= 300 && response.status < 400) {
  throw new ApiError(401, "Authentication session expired");
}
```

### Step 3: Investigate TokenRelay behavior for mutations (diagnostic)

The dev agent should add temporary debug logging to verify whether:
1. The SESSION cookie arrives at the gateway for POST requests (check via gateway debug logging)
2. The OAuth2 access token in the session is still valid
3. The `TokenRelay` filter is the component issuing the 302

Add to `application.yml` temporarily for diagnosis:

```yaml
logging:
  level:
    org.springframework.security: DEBUG
    org.springframework.cloud.gateway: DEBUG
```

If the root cause turns out to be OAuth2 access token expiry (TokenRelay can't relay an expired token), the fix is to configure token refresh:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            # ... existing config ...
        provider:
          keycloak:
            # ... existing config ...
```

And ensure the `OAuth2AuthorizedClientManager` is configured with a `RefreshTokenOAuth2AuthorizedClientProvider`.

## Scope

- **Gateway** (primary): `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java` — add exceptionHandling
- **Frontend** (defensive): `frontend/lib/api/client.ts` — add redirect: "manual" and 3xx detection
- **Gateway** (diagnostic): `gateway/src/main/resources/application.yml` — temporary debug logging

## Verification

1. Start the Keycloak dev stack (backend, gateway, frontend, Keycloak)
2. Log in as thandi@thornton-test.local through the gateway OAuth2 flow
3. Navigate to Settings > Automations
4. Toggle an automation rule — verify the toggle succeeds (no 302, backend state changes)
5. Delete an automation rule — verify deletion succeeds
6. Navigate to Settings > Notifications, change a preference toggle, verify save works
7. Verify GET requests still work (pages load with data)
8. Check gateway logs for any 302 responses on /api/** endpoints

## Estimated Effort

M-L (2-4 hours) — Step 1 is a straightforward config change. Step 2 is a small frontend change. Step 3 (diagnosis) may reveal a deeper TokenRelay/token-refresh issue that requires additional gateway configuration.
