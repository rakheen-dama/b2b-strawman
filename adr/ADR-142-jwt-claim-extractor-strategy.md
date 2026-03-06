# ADR-142: JWT Claim Extractor Strategy

**Status**: Proposed
**Date**: 2026-03-04
**Phase**: 36 (Keycloak + Gateway BFF Migration)

## Context

The backend extracts organization ID, slug, and role from JWT claims in multiple places: `JwtAuthenticationConverter`, `TenantFilter`, `MemberFilter`. Currently, `ClerkJwtUtils` is hardcoded to read Clerk's `"o"` nested claim format.

Keycloak uses a different claim structure (`"organization"` map with slug as key). The system must support both providers to enable a reversible migration.

## Decision

Introduce a `JwtClaimExtractor` interface with two implementations selected by Spring Profile:

```java
public interface JwtClaimExtractor {
    String extractOrgId(Jwt jwt);
    String extractOrgSlug(Jwt jwt);
    String extractOrgRole(Jwt jwt);
}
```

- `ClerkJwtClaimExtractor` — `@Profile("!keycloak")` — reads from `"o"` claim
- `KeycloakJwtClaimExtractor` — `@Profile("keycloak")` — reads from `"organization"` claim

The active implementation is injected into `JwtAuthenticationConverter`, `TenantFilter`, and `MemberFilter` via constructor injection.

### Why not a runtime switch?

Auth provider selection is a deployment-time decision, not a runtime decision. Using Spring Profiles:
- Eliminates dead code paths at startup
- Makes the active provider explicit in configuration
- Avoids runtime branching on every request
- Matches the frontend's build-time `NEXT_PUBLIC_AUTH_MODE` approach

## Consequences

- **Positive**: Clean separation — each implementation handles one JWT format
- **Positive**: Existing Clerk tests pass unchanged (default profile)
- **Positive**: Easy to add future providers (e.g., Auth0, Okta) by adding another `@Profile` implementation
- **Negative**: Slightly more files (interface + 2 implementations vs 1 utility class)
- **Negative**: `ClerkJwtUtils` becomes `ClerkJwtClaimExtractor` — rename across codebase
