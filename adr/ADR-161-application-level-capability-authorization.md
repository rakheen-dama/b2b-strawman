# ADR-161: Application-Level Capability Authorization

**Status**: Proposed
**Date**: 2026-03-08
**Phase**: 41 (Organisation Roles & Capability-Based Permissions)

## Context

DocTeams currently authorizes API requests using three coarse org-level roles from Keycloak JWTs: `org:owner`, `org:admin`, `org:member`. These are mapped to Spring Security granted authorities (`ROLE_ORG_OWNER`, `ROLE_ORG_ADMIN`, `ROLE_ORG_MEMBER`) via `ClerkJwtAuthenticationConverter` and checked with `@PreAuthorize("hasAnyRole(...)")`. This model cannot express "this person can manage invoices but not see profitability reports" — endpoints are either admin-only or open to all members.

Phase 41 introduces 7 domain-area capabilities (FINANCIAL_VISIBILITY, INVOICING, PROJECT_MANAGEMENT, TEAM_OVERSIGHT, CUSTOMER_MANAGEMENT, AUTOMATIONS, RESOURCE_PLANNING) composable into custom roles. The question is where capability resolution and enforcement should live: in the identity provider (Keycloak), in the application database, or in a hybrid model.

Key constraints:
- The IDP layer (currently using an auth abstraction from Phase 20, with Clerk as the active provider) issues JWTs with coarse org roles. No IDP configuration changes are permitted in this phase — the auth layer is stable and should not be touched for a permission model change.
- The backend extracts org roles via `ClerkJwtUtils` and passes them through the `MemberFilter`. No gateway changes are permitted.
- Capabilities are tenant-scoped — each firm defines its own custom roles. IDP org roles are global across the platform.
- The platform has ~50+ existing `@PreAuthorize` annotations that need migration.

## Options Considered

1. **IDP roles/groups mapped to capabilities** — Define IDP-level roles (e.g., `cap:invoicing`, `cap:financial_visibility`) and assign them to users via IDP groups. Map these to Spring Security authorities in the JWT converter. Custom roles would be IDP groups with capability roles assigned.
   - Pros: Token-based enforcement — capabilities travel with the JWT, no per-request DB lookup. Standard OAuth2/OIDC pattern. Gateway could enforce capabilities without hitting the backend. IDP admin UI provides role management for free.
   - Cons: **Violates the hard constraint** — requires IDP role/group creation, JWT mapper changes, and potentially gateway configuration updates. IDP org roles are platform-global, not tenant-scoped — cannot support per-tenant custom roles without IDP org-level role management. JWT bloat: 7 capabilities + overrides per user added to every token. Role changes require token refresh or revocation — no immediate effect. The auth layer is stable and adding roles/groups introduces unnecessary coupling and risk.

2. **Application database with per-request resolution (chosen)** — Store OrgRole entities and capability mappings in the tenant schema. Resolve capabilities once per request in `MemberFilter` after member lookup. Bind to `RequestScopes.CAPABILITIES` as a `ScopedValue`. Enforce via a custom `@RequiresCapability` annotation backed by an `AuthorizationManager`.
   - Pros: No IDP or gateway changes required. Tenant-scoped by design — each schema has its own roles and capabilities. Immediate effect — role changes apply on the next request, no token refresh needed. Query flexibility: "find all members with INVOICING capability" is a simple JOIN. Existing `MemberFilter` → `RequestScopes` pattern is proven and well-understood. Small, bounded DB queries (roles table has ~5–20 rows per tenant, overrides have ~0–2 rows per member).
   - Cons: Per-request DB overhead — two additional queries per request (OrgRole + overrides). Cannot enforce at the gateway level — all requests must reach the backend. Capabilities not visible in JWT introspection or token debugging.

3. **Hybrid: IDP for coarse roles, app DB for fine-grained capabilities** — Keep Keycloak's three org roles for gateway-level routing (admin vs member), but resolve fine-grained capabilities from the app DB. The JWT role determines the "tier" (admin = all capabilities, member = DB-resolved capabilities).
   - Pros: Gateway can still reject unauthenticated/unauthorized requests using JWT roles. Fine-grained capabilities in the app DB without Keycloak changes. Incremental — only adds DB resolution for the member tier.
   - Cons: Two authorization systems to reason about — JWT roles at the gateway, DB capabilities at the backend. The "admin = all capabilities" bypass makes the hybrid model functionally identical to Option 2 for all admin/owner users. Additional conceptual overhead for no practical benefit — the gateway already passes all authenticated requests to the backend for member-level authorization.

## Decision

Use **application database with per-request resolution** (Option 2). OrgRole entities, capability mappings, and per-user overrides are stored in the tenant schema. Capabilities are resolved once per request in `MemberFilter` and bound to `RequestScopes.CAPABILITIES`. A custom `@RequiresCapability` annotation replaces the existing `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")` pattern for capability-gated endpoints.

## Rationale

Option 1 is ruled out by the hard constraint: no IDP changes in this phase. Even without the constraint, IDP org roles are platform-global and cannot represent tenant-scoped custom roles without significant IDP customization.

Option 3 adds conceptual complexity (two authorization systems) for no practical benefit. The gateway already forwards all authenticated requests to the backend. The admin/owner bypass means the hybrid model degenerates to Option 2 for the majority of admin requests. The only advantage — gateway-level capability enforcement — is not needed because the gateway's role is routing, not fine-grained authorization.

Option 2 aligns with the existing architecture. `MemberFilter` already resolves member identity per request and binds it to `RequestScopes`. Adding capability resolution is a natural extension — one additional JOIN query on small tables. The `ScopedValue` pattern ensures capabilities are available throughout the request without threading concerns. The per-request DB cost (two queries on tables with ~20 rows) is negligible compared to the business logic queries that follow.

The application DB approach also provides the best developer experience: capabilities are regular Hibernate entities, queryable with JPQL, testable with standard integration tests, and manageable with Flyway migrations. No external IDP configuration or token lifecycle management required.

## Consequences

- **Positive**: Zero Keycloak/gateway changes — the constraint is satisfied completely
- **Positive**: Tenant-scoped custom roles — each firm defines its own role vocabulary in its own schema
- **Positive**: Immediate effect — role and capability changes apply on the very next HTTP request, no token refresh required
- **Positive**: Query flexibility — "which members have INVOICING?" is a standard SQL JOIN, useful for audit and reporting
- **Positive**: Testable — capability resolution can be unit-tested with in-memory data, integration-tested with Testcontainers
- **Negative**: Per-request DB overhead — two additional queries. Mitigated by small table sizes and potential Caffeine caching of OrgRole entities if needed
- **Negative**: Gateway cannot enforce capabilities — a request with insufficient capabilities will be rejected at the controller layer, not at the gateway. This is acceptable because the gateway's primary role is authentication and routing, not fine-grained authorization
- **Negative**: Capabilities are not visible in JWT tokens — debugging authorization issues requires inspecting the DB, not just decoding a JWT. Mitigated by the `GET /api/me/capabilities` endpoint which exposes the resolved set
- **Neutral**: The existing `@PreAuthorize` annotations coexist with `@RequiresCapability` during migration. Both patterns work simultaneously, allowing incremental rollout
