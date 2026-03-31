# ADR-T004: Gateway BFF Over Direct API Access

> Template ADR for `java-keycloak-multitenant-saas` starter template

**Status:** Accepted

**Context:** The Next.js frontend needs to authenticate users via Keycloak and call the Spring Boot backend API. Two architectures are viable: (a) the frontend obtains JWTs directly from Keycloak via PKCE and sends them as Bearer tokens to the backend, or (b) a Backend-For-Frontend (BFF) gateway manages sessions server-side and relays tokens transparently.

**Options Considered:**

1. **Direct API access (SPA + PKCE)** — Frontend uses Keycloak JS adapter, stores tokens in memory/localStorage, sends Bearer tokens directly to backend
   - Pros: No additional service to deploy, simpler infrastructure, fewer moving parts
   - Cons: Tokens accessible to JavaScript (XSS can steal them), frontend must handle token refresh logic, CSRF less of a concern but XSS is worse, complex frontend auth code (token lifecycle, silent refresh, error handling)

2. **Gateway BFF (session-based)** — Spring Cloud Gateway manages OAuth2 login, stores sessions server-side, relays tokens to backend
   - Pros: Tokens never reach the browser (XSS-proof), transparent token refresh, CSRF + SameSite cookie protection, simplified frontend (just cookies), centralized security policy
   - Cons: Additional service to deploy, session storage requires database (Spring Session JDBC), added latency from proxy hop

3. **Next.js API routes as BFF** — Use Next.js server-side to manage tokens, proxy API calls
   - Pros: No additional service, unified frontend+BFF deployment
   - Cons: Mixing concerns (frontend framework as auth gateway), limited to Node.js ecosystem for auth libraries, harder to secure correctly, session management in Next.js is less mature than Spring Security

**Decision:** Use Spring Cloud Gateway as a BFF. The frontend never handles JWTs.

**Rationale:**
1. **XSS-proof token storage:** Tokens are stored in server-side sessions backed by PostgreSQL (Spring Session JDBC). An XSS attack on the frontend cannot steal them — there's nothing to steal in the browser.
2. **Transparent refresh:** The gateway handles token refresh automatically using Spring Security's OAuth2 client. The frontend never deals with token expiry, refresh flows, or retry-with-new-token logic.
3. **CSRF protection:** The gateway enforces CSRF tokens on all state-changing requests. Combined with SameSite=Lax cookies and HttpOnly session cookies, this provides defense-in-depth.
4. **Simplified frontend:** The frontend uses relative URLs (`/api/projects`) and session cookies. No token management library, no auth interceptors, no silent refresh — just `fetch()`.
5. **Spring ecosystem alignment:** The backend is Spring Boot, the gateway is Spring Cloud Gateway — they share the same security model, configuration patterns, and operational knowledge.

**Consequences:**
- Positive: Strongest browser security posture (tokens never in browser, HttpOnly cookies, CSRF enforced)
- Positive: Simplified frontend authentication code (no auth library needed)
- Positive: Centralized security policy (one place to audit: `GatewaySecurityConfig.java`)
- Negative: Additional service to deploy and monitor (the gateway on port 8443)
- Negative: Session storage requires a database table (Spring Session JDBC — uses the same Postgres instance)
- Negative: Added latency from the proxy hop (mitigated by local-network deployment — gateway and backend typically co-located)
