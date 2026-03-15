# ADR-180: Gateway Authorization Removal

**Status**: Accepted
**Date**: 2026-03-14

## Context

The Spring Cloud Gateway (BFF) currently performs both authentication and authorization duties. For authentication, it handles OAuth2 login/logout, session management, CSRF, CORS, and the TokenRelay filter that forwards JWTs to the backend. For authorization, it has `BffSecurity` (an `@PreAuthorize` helper that checks OIDC org role claims), admin endpoints that call `KeycloakAdminClient` (invitation sending, org creation), and the `/bff/me` endpoint that returns the user's org role alongside their identity.

This creates **split authorization responsibility**. The gateway makes authorization decisions based on OIDC claims (stale, from the session), while the backend makes authorization decisions based on DB-stored capabilities (current, per-request). An admin whose role is changed in the DB retains their gateway-level admin access until their OIDC session is refreshed. The two authorization surfaces can also diverge — an endpoint might be protected at the gateway level by `BffSecurity.isAdmin()` but have different or no protection at the backend level.

With [ADR-178](ADR-178-db-authoritative-role-resolution.md) establishing the DB as the sole authorization authority, and [ADR-179](ADR-179-pending-invitation-role-assignment.md) moving invitation role assignment to the backend, the gateway no longer needs authorization capabilities. The `KeycloakAdminClient` moves to the backend to support the `InvitationService`, and the gateway's admin endpoints (`/bff/admin/invite`, `/bff/orgs`) are replaced by backend API endpoints protected by `@RequiresCapability`.

## Options Considered

### Option 1: Keep gateway authorization

Maintain the current split — gateway checks OIDC claims for admin endpoints, backend checks DB capabilities for API endpoints.

- **Pros**:
  - No migration needed
  - Defense-in-depth (two authorization layers)
- **Cons**:
  - Stale session data means gateway authorization can be wrong
  - Two authorization models to maintain and reason about
  - Gateway admin endpoints bypass backend integration tests
  - `KeycloakAdminClient` in gateway creates a coupling that prevents the backend from owning the full member lifecycle

### Option 2: Move all authorization to backend (chosen)

Strip the gateway of all authorization logic. It becomes a pure authentication + proxy layer: OAuth2 login/logout, session, TokenRelay, CSRF, CORS, and `/bff/me` for identity-only. All admin operations move to backend API endpoints with `@RequiresCapability` guards.

- **Pros**:
  - Single authorization surface — all access control in one place
  - Authorization decisions always use current DB state (via MemberFilter)
  - Backend integration tests cover all authorization paths
  - Gateway becomes stateless for authorization — simpler to reason about
  - Backend owns complete member lifecycle (invitation, creation, role assignment)
- **Cons**:
  - Admin operations now require a full round trip through the gateway (TokenRelay → backend) instead of being handled at the gateway
  - Gateway loses the ability to block unauthorized requests before they reach the backend (mitigated: backend's `MemberFilter` is the real gate)

### Option 3: Shared authorization library

Extract authorization logic into a shared library used by both gateway and backend. Both check the same DB-based capabilities.

- **Pros**:
  - Consistent authorization at both layers
  - Defense-in-depth with consistent decisions
- **Cons**:
  - Shared library complexity (multi-module dependency management)
  - Gateway would need DB access or a cache sync mechanism
  - Two places to maintain authorization filter chains
  - Overkill — the gateway does not need to make authorization decisions if it forwards all API requests to the backend

## Decision

**Option 2: Move all authorization to backend.** The gateway retains only authentication responsibilities (OAuth2, session, CSRF, CORS, TokenRelay). All authorization decisions are made by the backend via `MemberFilter` + `@RequiresCapability`.

## Rationale

The gateway's authorization capabilities were an artifact of the Clerk-era architecture where the BFF handled admin operations directly (inviting members, creating orgs) because those operations required the `KeycloakAdminClient` which lived in the gateway. With `KeycloakAdminClient` moving to the backend (to support `InvitationService`), there is no longer a technical reason for the gateway to have admin endpoints.

The "defense-in-depth" argument for keeping gateway authorization is weak in this architecture: the gateway checks OIDC session claims which can be stale, while the backend checks current DB state. A gateway authorization check that passes on stale data provides false confidence, not genuine security. The backend's `MemberFilter` — applied to all `/api/**` routes by `SecurityConfig` — is the real security boundary. Every request that reaches a controller is guaranteed to have a valid member context with current capabilities.

After this change, the gateway's responsibilities become clear and narrow: (1) handle OIDC login/logout with Keycloak, (2) manage the SESSION cookie, (3) relay tokens to the backend, (4) serve `/bff/me` with identity-only data, (5) CSRF/CORS. This makes the gateway easy to reason about, test, and potentially replace (e.g., with a CDN edge proxy + cookie-to-header JWT pattern).

## Consequences

### Positive
- Single authorization surface — all access control decisions in the backend, covered by integration tests
- No stale-session authorization bugs — gateway never makes access control decisions
- Gateway codebase shrinks (~4 files deleted: `BffSecurity`, `AdminProxyController`, `KeycloakAdminClient`, and related config)
- Backend owns complete member lifecycle: invitation, lazy-create, role assignment, capability resolution

### Negative
- Admin operations (invite member, create org) add one network hop (frontend → gateway → backend) instead of being handled at the gateway. Latency impact is negligible (~1ms on local network).
- `KeycloakAdminClient` moves to the backend, adding Keycloak Admin REST API as a backend dependency (mitigated: only used by `InvitationService` and org provisioning, not on the hot path)

### Neutral
- `/bff/me` response shrinks (removes `orgRole` field) — frontend uses `/api/me/capabilities` for authorization data instead
- Gateway no longer needs the `KeycloakAdminClient` dependency in its `pom.xml` / `build.gradle`
- Frontend invitation UI calls `/api/invitations` instead of `/bff/admin/invite` — same UX, different endpoint
