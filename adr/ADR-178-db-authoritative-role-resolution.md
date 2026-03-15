# ADR-178: DB-Authoritative Role Resolution

**Status**: Accepted
**Date**: 2026-03-14

## Context

The DocTeams platform has two parallel authorization systems running simultaneously. The first is JWT-based: `ClerkJwtAuthenticationConverter` maps the JWT `org_role` claim (or Keycloak organization role) into `ROLE_ORG_*` Spring Security granted authorities, and controllers enforce access with `@PreAuthorize("hasRole('ORG_OWNER')")`. The second is DB-based: `OrgRoleService.resolveCapabilities(memberId)` reads the member's `orgRoleId` FK, resolves the `OrgRole` entity's capabilities plus per-member overrides, and binds them to `RequestScopes.CAPABILITIES`, checked by `@RequiresCapability`.

This split creates three problems. First, **stale tokens**: when an admin changes a member's role in the DB, the JWT still carries the old `org_role` claim until the token is refreshed (up to 5 minutes with default Keycloak settings). The `MemberFilter` currently has a `jwtHasExplicitRole` branch that *prefers* the JWT role over the DB role, meaning a demoted admin retains elevated access until their session refreshes. Second, **Keycloak immaturity**: Keycloak Organization Roles have been unstable — CVE-2026-1529 exposed forged invitation JWTs, and the multi-org claims format still lacks consistent role delivery across KC versions. Third, **developer confusion**: new controllers must decide whether to use `@PreAuthorize` or `@RequiresCapability`, and the answer is not obvious from the codebase conventions.

The DB-based system is already more capable: it supports custom roles with arbitrary capability sets, per-member capability overrides, and immediate role changes (via cache eviction). The JWT-based system is a legacy artifact from the Clerk era (Phase 20 removed Clerk but kept the JWT role extraction pattern). Making the DB authoritative eliminates the dual system without losing any functionality.

## Options Considered

### Option 1: Keep JWT-first with cache

Continue using JWT `org_role` as the primary role source. Add a cache layer that checks the DB on cache miss but still prefers the JWT value when available.

- **Pros**:
  - No migration needed for ~264 `@PreAuthorize` annotations
  - JWT role checks are zero-latency (no DB round trip)
- **Cons**:
  - Stale token window remains (up to 5 minutes)
  - Keycloak org role bugs continue to affect the system
  - Custom roles cannot be represented in JWT claims
  - Per-member overrides are invisible to `@PreAuthorize`

### Option 2: DB-first with JWT fallback

Use the DB role as primary, but fall back to JWT `org_role` if the DB lookup fails (e.g., member not yet created).

- **Pros**:
  - Gradual migration — JWT fallback provides safety net
  - DB failures don't lock out users
- **Cons**:
  - Maintains two code paths indefinitely
  - Fallback path is rarely exercised and thus poorly tested
  - JWT role can contradict DB role during fallback
  - Still depends on Keycloak org role configuration

### Option 3: DB-only (chosen)

JWT carries identity only (`sub`, `organization`, `groups`, `email`). All authorization comes from the DB via `MemberFilter` → `orgRoleId` FK → `OrgRole` → capabilities → `RequestScopes.CAPABILITIES`. The `MemberFilter` lazy-create path assigns a default role (or invitation role) to new members.

- **Pros**:
  - Immediate role change propagation (DB + cache eviction)
  - Single authorization model — `@RequiresCapability` everywhere
  - Custom roles fully supported without JWT changes
  - Eliminates Keycloak org role dependency and associated CVEs
  - Already 80% built (OrgRole, Capability, OrgRoleService exist)
- **Cons**:
  - Adds DB lookup per request (mitigated by Caffeine cache with 1-hour TTL)
  - Requires migrating ~264 `@PreAuthorize` annotations across 69 files
  - `MemberFilter` cache eviction becomes a critical path for security

### Option 4: External authorization service (OPA/Cedar)

Deploy Open Policy Agent or AWS Cedar as a sidecar, externalizing all authorization decisions.

- **Pros**:
  - Policy-as-code with rich expression language
  - Decoupled from application deployment
  - Industry-standard approach for complex authorization
- **Cons**:
  - Significant operational complexity (new service to deploy, monitor, update)
  - Additional network hop per request (~2-5ms latency)
  - Overkill for the current RBAC model (3 system roles + custom roles + overrides)
  - Would still need the same DB-stored role/capability data as input

## Decision

**Option 3: DB-only.** The JWT carries identity (`sub`, `organization`, `groups`, `email`), and all authorization decisions come from the product database via `MemberFilter` → `OrgRole` → capabilities → `RequestScopes`.

## Rationale

The DB-only approach is the natural conclusion of work already in progress. The `OrgRole` entity, `Capability` enum, `OrgRoleService`, `@RequiresCapability` annotation, and `RequestScopes.CAPABILITIES` ScopedValue ([ADR-009](../architecture/ARCHITECTURE.md)) are all production-ready. The remaining JWT-based authorization path exists only because it was never fully migrated — not because it provides unique value.

The cache concern is well-mitigated. The existing `MemberFilter` Caffeine cache (50K entries, 1-hour TTL) already handles the DB lookup for member resolution. Adding role/capability data to the cached `MemberInfo` record means zero additional DB queries per request. Cache eviction on role mutation is explicit and bounded — `assignRole()` evicts one member, `updateRole()` evicts all members of that role (rare admin operation).

The migration of ~264 `@PreAuthorize` annotations is mechanical (three patterns, clear mapping) and fully covered by the existing 830+ backend integration tests. Any missed annotation surfaces immediately as a 403 in tests.

## Consequences

### Positive
- Role changes take effect on next request, not next token refresh
- Single authorization model eliminates developer confusion
- Custom roles with arbitrary capabilities fully supported
- Keycloak Organization Role dependency eliminated (CVE-2026-1529 irrelevant)
- Per-member capability overrides work consistently across all endpoints

### Negative
- `MemberFilter` cache eviction is now a security-critical path — missed eviction means stale authorization (mitigated by 1-hour TTL safety net)
- Migration touches ~69 controller files in a single slice (mechanical but large diff)

### Neutral
- JWT token size decreases slightly (no `org_role` claim)
- Keycloak configuration simplified (no org role user attribute mapper needed)
- `MemberFilter` cache now carries authorization data alongside identity data
