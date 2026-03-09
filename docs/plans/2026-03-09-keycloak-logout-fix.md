# Keycloak Logout Fix — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix Keycloak logout so it properly terminates the session and redirects to the frontend home page instead of showing a Spring whitelabel error.

**Architecture:** The frontend currently sends a GET to `${GATEWAY_URL}/logout`, but Spring Security's `LogoutFilter` only handles POST (with CSRF). We add a `/bff/csrf` endpoint so the frontend can obtain the CSRF token, then submit a hidden form POST. We also fix the post-logout redirect to point to the frontend URL and register it in Keycloak.

**Tech Stack:** Spring Boot 4 (gateway), Next.js 16 (frontend), Keycloak 26.5, Vitest

---

## Bug Summary

Three bugs combine to produce the whitelabel error:

1. **GET vs POST mismatch** — Frontend navigates via `window.location.href` (GET), but `LogoutFilter` only handles POST with CSRF
2. **Post-logout redirect to gateway** — `{baseUrl}/` resolves to `http://localhost:8443/` (gateway), not the frontend
3. **Missing Keycloak `postLogoutRedirectUris`** — Keycloak silently ignores the redirect parameter

---

### Task 1: Add `/bff/csrf` endpoint to BffController

**Files:**
- Modify: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java`
- Modify: `gateway/src/test/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfigTest.java`

**Step 1: Add the endpoint**

Add a `GET /bff/csrf` endpoint that returns the CSRF token value. Since `/bff/**` paths are CSRF-exempt in the security config, this endpoint is accessible without a token. It uses Spring Security's `CsrfToken` request attribute.

```java
/** Returns the current CSRF token so the SPA can perform form POSTs (e.g., logout). */
@GetMapping("/csrf")
public ResponseEntity<Map<String, String>> csrf(HttpServletRequest request) {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (csrfToken == null) {
        return ResponseEntity.ok(Map.of());
    }
    // Force lazy token generation
    csrfToken.getToken();
    return ResponseEntity.ok(
        Map.of(
            "token", csrfToken.getToken(),
            "parameterName", csrfToken.getParameterName(),
            "headerName", csrfToken.getHeaderName()));
}
```

**Step 2: Add test for the endpoint**

In `GatewaySecurityConfigTest`, add:

```java
@Test
void bffCsrf_returnsTokenUnauthenticated() throws Exception {
    var result = mockMvc.perform(get("/bff/csrf")).andReturn();
    int statusCode = result.getResponse().getStatus();
    assertThat(statusCode)
        .as("/bff/csrf should be publicly accessible")
        .isIn(PUBLIC_OK_STATUSES);
}
```

**Step 3: Run gateway tests**

```bash
cd gateway && ./mvnw test -pl . -q 2>/tmp/mvn-csrf.log; echo "Exit: $?"
```

**Step 4: Commit**

```bash
git add gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java \
        gateway/src/test/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfigTest.java
git commit -m "feat(gateway): add /bff/csrf endpoint for SPA CSRF token retrieval"
```

---

### Task 2: Fix post-logout redirect URI in GatewaySecurityConfig

**Files:**
- Modify: `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java`
- Modify: `gateway/src/test/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfigTest.java`

**Step 1: Change postLogoutRedirectUri**

In `oidcLogoutSuccessHandler()`, change:

```java
// BEFORE
handler.setPostLogoutRedirectUri("{baseUrl}/");

// AFTER
handler.setPostLogoutRedirectUri(frontendUrl);
```

**Step 2: Update logout test to verify redirect target**

The existing test only checks for `is3xxRedirection()`. Update it to also verify the redirect URL contains the frontend URL:

```java
@Test
void logout_invalidatesSessionAndRedirects() throws Exception {
    var result =
        mockMvc
            .perform(post("/logout").with(oauth2Login()).with(csrf().asHeader()))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    String redirectUrl = result.getResponse().getRedirectedUrl();
    // OidcClientInitiatedLogoutSuccessHandler redirects to the IdP end_session_endpoint
    // with post_logout_redirect_uri containing the frontend URL
    assertThat(redirectUrl).contains("post_logout_redirect_uri");
    assertThat(redirectUrl).contains("http%3A%2F%2Flocalhost%3A3000");
}
```

**Step 3: Run gateway tests**

```bash
cd gateway && ./mvnw test -pl . -q 2>/tmp/mvn-redirect.log; echo "Exit: $?"
```

**Step 4: Commit**

```bash
git add gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java \
        gateway/src/test/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfigTest.java
git commit -m "fix(gateway): redirect to frontend after logout instead of gateway root"
```

---

### Task 3: Add postLogoutRedirectUris to Keycloak realm export

**Files:**
- Modify: `compose/keycloak/realm-export.json`

**Step 1: Add postLogoutRedirectUris to gateway-bff client**

In the `gateway-bff` client object, after the `redirectUris` array, add:

```json
"postLogoutRedirectUris": [
    "http://localhost:3000",
    "http://localhost:3000/*"
],
```

**Step 2: Commit**

```bash
git add compose/keycloak/realm-export.json
git commit -m "fix(keycloak): register post-logout redirect URIs for gateway-bff client"
```

---

### Task 4: Frontend — Form POST logout with CSRF token

**Files:**
- Modify: `frontend/components/auth/user-menu-bff.tsx`
- Modify: `frontend/__tests__/lib/auth/keycloak-login-logout.test.ts`

**Step 1: Update handleSignOut to use form POST**

Replace the `getKeycloakLogoutUrl()` function and `handleSignOut()`:

```tsx
/**
 * Performs Keycloak logout by fetching the CSRF token from the gateway
 * and submitting a hidden form POST to the gateway logout endpoint.
 * This is required because Spring Security's LogoutFilter only accepts POST with CSRF.
 */
export async function performKeycloakLogout(): Promise<void> {
  // Fetch CSRF token from the gateway BFF endpoint (CSRF-exempt under /bff/**)
  const res = await fetch(`${GATEWAY_URL}/bff/csrf`, { credentials: "include" });
  const data = await res.json();

  // Create and submit a hidden form POST to the gateway logout endpoint
  const form = document.createElement("form");
  form.method = "POST";
  form.action = `${GATEWAY_URL}/logout`;

  if (data.token) {
    const input = document.createElement("input");
    input.type = "hidden";
    input.name = data.parameterName || "_csrf";
    input.value = data.token;
    form.appendChild(input);
  }

  document.body.appendChild(form);
  form.submit();
}
```

Update `handleSignOut`:
```tsx
function handleSignOut() {
    performKeycloakLogout();
}
```

Keep `getKeycloakLogoutUrl()` exported (used in tests and potentially elsewhere), but mark it as the raw URL.

**Step 2: Update the test**

Replace the logout URL test with a test for the new `performKeycloakLogout` function:

```ts
it("performKeycloakLogout fetches CSRF token and submits form POST", async () => {
    // Mock fetch to return a CSRF token
    const mockFetch = vi.fn().mockResolvedValue({
        json: () => Promise.resolve({
            token: "test-csrf-token",
            parameterName: "_csrf",
            headerName: "X-XSRF-TOKEN",
        }),
    });
    vi.stubGlobal("fetch", mockFetch);

    // Mock form submission
    const mockSubmit = vi.fn();
    const mockAppendChild = vi.fn();
    const mockCreateElement = vi.spyOn(document, "createElement");

    let capturedForm: any;
    mockCreateElement.mockImplementation((tag: string) => {
        if (tag === "form") {
            capturedForm = {
                method: "",
                action: "",
                appendChild: mockAppendChild,
                submit: mockSubmit,
            };
            return capturedForm as any;
        }
        if (tag === "input") {
            return { type: "", name: "", value: "" } as any;
        }
        return document.createElement(tag);
    });

    vi.spyOn(document.body, "appendChild").mockImplementation((node) => node);

    const { performKeycloakLogout } = await import(
        "@/components/auth/user-menu-bff"
    );

    await performKeycloakLogout();

    // Verify CSRF token was fetched from gateway
    expect(mockFetch).toHaveBeenCalledWith(
        "http://localhost:8443/bff/csrf",
        { credentials: "include" },
    );

    // Verify form was created with correct action and method
    expect(capturedForm.method).toBe("POST");
    expect(capturedForm.action).toBe("http://localhost:8443/logout");

    // Verify form was submitted
    expect(mockSubmit).toHaveBeenCalled();

    mockCreateElement.mockRestore();
});
```

Also keep the existing `getKeycloakLogoutUrl` test since the function still exists.

**Step 3: Run frontend tests**

```bash
cd frontend && pnpm test -- --reporter=verbose 2>&1 | tail -20
```

**Step 4: Commit**

```bash
git add frontend/components/auth/user-menu-bff.tsx \
        frontend/__tests__/lib/auth/keycloak-login-logout.test.ts
git commit -m "fix(frontend): use form POST with CSRF for Keycloak logout"
```

---

### Task 5: Final verification

**Step 1: Run all gateway tests**
```bash
cd gateway && ./mvnw test -q 2>/tmp/mvn-final.log; echo "Exit: $?"
```

**Step 2: Run all frontend tests**
```bash
cd frontend && pnpm test 2>&1 | tail -20
```

**Step 3: Build frontend to verify no compile errors**
```bash
cd frontend && pnpm build 2>&1 | tail -10
```
