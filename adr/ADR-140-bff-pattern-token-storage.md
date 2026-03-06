# ADR-140: BFF Pattern and Token Storage

**Status**: Proposed
**Date**: 2026-03-04
**Phase**: 36 (Keycloak + Gateway BFF Migration)

## Context

The current architecture stores Clerk JWTs in browser memory/cookies and sends them as `Authorization: Bearer` headers. This makes tokens accessible to JavaScript, creating an XSS token theft vector. The Spring Security team explicitly recommends against public OAuth2 clients in browsers, stating there are "no browser APIs allowing completely secure token storage."

Three options were considered:
1. **Keep current pattern** — Swap Clerk for Keycloak but keep JWTs in the browser
2. **Spring Cloud Gateway BFF** — Dedicated gateway service stores tokens server-side in Spring Session JDBC
3. **Existing backend as BFF** — Add `oauth2Login()` to the existing Spring Boot backend

## Decision

Introduce a **dedicated Spring Cloud Gateway BFF** using `spring-cloud-starter-gateway-server-webmvc` (servlet stack) with `spring-session-jdbc` for session storage.

- Browser receives only an opaque `SESSION` cookie (HttpOnly, Secure, SameSite=Lax)
- OAuth2 tokens (access, refresh, ID) stored in PostgreSQL `SPRING_SESSION_ATTRIBUTES` table
- Gateway's `TokenRelay` filter extracts the access token from the session and adds it to proxied requests
- Backend remains a pure stateless resource server — no session awareness
- CSRF protection via `CookieCsrfTokenRepository` (double-submit cookie pattern)

### Why not Option 3 (backend as BFF)?

Mixing session-based auth (for browser) and stateless JWT auth (for the gateway/internal calls) in the same Spring Security filter chain adds complexity. A dedicated gateway keeps concerns separated: the gateway handles auth ceremony + session management, the backend handles business logic.

## Consequences

- **Positive**: No tokens ever accessible to browser JavaScript — eliminates XSS token theft
- **Positive**: Backend stays stateless — no changes to its horizontal scaling model
- **Positive**: Gateway can serve as a single entry point for future microservices
- **Positive**: Token refresh handled transparently by Spring Security's `OAuth2AuthorizedClientManager`
- **Negative**: Additional service to deploy, monitor, and upgrade
- **Negative**: 1-2 extra DB queries per request (session lookup + update)
- **Negative**: Adds ~1-5ms latency per request (extra network hop)
- **Negative**: CSRF protection required (adds complexity to SPA API calls)
- **Negative**: Spring Session JDBC adds state to the architecture (requires session table cleanup)
