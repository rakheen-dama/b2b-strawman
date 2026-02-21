# ADR-086: Mock IDP Strategy

**Status**: Proposed

**Context**:

The E2E testing stack needs a way to issue JWTs that the backend will accept. In production, Clerk issues JWTs signed with its RSA private key, and the backend validates them against Clerk's JWKS endpoint (`/.well-known/jwks.json`). For E2E testing, we need a local service that can: (1) serve a JWKS endpoint with a known public key, and (2) issue JWTs signed with the corresponding private key, with configurable claims (user ID, org ID, org role).

The backend's JWT validation is standard Spring Security OAuth2 Resource Server — it fetches the public key from `jwk-set-uri` and validates the signature. It does not validate the issuer URL or audience claim in the current configuration. The `ClerkJwtAuthenticationConverter` extracts the `o.rol` claim and maps it to Spring authorities. The `TenantFilter` extracts `o.id` for schema resolution. Both operate on JWT claims, not on any Clerk-specific API.

**Options Considered**:

1. **Custom Node.js mock IDP container (chosen)** — A lightweight Express server (~200 lines) that generates an RSA key pair at build time, serves JWKS, and issues JWTs on demand via `POST /token`.
   - Pros: Full control over JWT structure and claims; dynamic token issuance (Playwright tests can request tokens for specific users); stateless and fast (~5ms per token); no external dependencies; trivial to extend (add claims, change expiry, etc.).
   - Cons: Custom code to maintain (~200 lines); must keep JWT claim format in sync with Clerk v2 structure.

2. **WireMock with static mappings** — Configure WireMock to serve a static JWKS response and pre-canned token responses.
   - Pros: No custom code; WireMock is a well-known tool; declarative configuration.
   - Cons: Cannot dynamically generate JWTs (WireMock returns static responses — each user/role combination needs a pre-generated token); pre-generated tokens have fixed expiry (must be regenerated periodically or set to far-future); adding a new test user requires regenerating tokens and updating mappings; WireMock image is 200MB+ vs ~50MB for a Node.js container.

3. **Keycloak dev mode** — Run Keycloak in dev mode as the mock IDP.
   - Pros: Full OIDC-compliant IDP; well-documented; supports dynamic token issuance via standard OIDC flows.
   - Cons: Massive overhead — Keycloak image is 400MB+, startup time 10-30 seconds; requires realm/client/user configuration; produces JWTs in Keycloak's format (not Clerk v2 format), requiring backend converter changes or a custom Keycloak SPI to emit `o.id`/`o.rol` claims; overkill for the use case.

4. **Hardcoded JWTs in test code** — Generate long-lived JWTs offline, store them as constants in test fixtures.
   - Pros: Zero infrastructure; no running service needed; works immediately.
   - Cons: Tokens expire (unless set to 100-year expiry, which is a security anti-pattern even in tests); cannot dynamically change claims per test; JWKS endpoint still needed for backend validation (backend must be configured to skip signature validation, which is a different security posture).

**Decision**: Option 1 — custom Node.js mock IDP container.

**Rationale**:

The mock IDP's primary consumer is Playwright tests, which need dynamic token issuance — "give me a token for user X with role Y". WireMock's static responses make this awkward (pre-generate N tokens and hope tests don't need N+1). Keycloak is a production-grade IDP being used as a test stub — massive overkill. Hardcoded tokens create a maintenance burden and require disabling signature validation.

A custom Node.js service is ~200 lines of straightforward code: generate RSA key pair, serve JWKS, sign JWTs with `jsonwebtoken`. The claim format matches Clerk v2 exactly (`sub`, nested `o.id`/`o.rol`/`o.slg`), so the backend's `ClerkJwtAuthenticationConverter` and `ClerkJwtUtils` work unmodified. The container is ~50MB (Node.js alpine), starts in <1 second, and issues tokens in <5ms. If the claim format ever changes (Clerk v3), updating the mock is a one-line change.

**Consequences**:

- Positive:
  - Dynamic token issuance enables per-test user/role configuration
  - Clerk v2 JWT format means zero backend code changes
  - Lightweight container (~50MB, <1s startup)
  - Stateless — no database, no session management, no cleanup between tests
  - Extensible — add new claims, users, or endpoints trivially

- Negative:
  - ~200 lines of custom code to maintain
  - JWT claim format must stay in sync with what `ClerkJwtAuthenticationConverter` expects (currently `o.id`, `o.rol`)

- Neutral:
  - RSA key pair is test-only and baked into the container image — no secrets management
  - Mock IDP is only used in E2E/test environments — never deployed to production
