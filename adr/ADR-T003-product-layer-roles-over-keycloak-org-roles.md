# ADR-T003: Product-Layer Roles Over Keycloak Org Roles

> Template ADR for `java-keycloak-multitenant-saas` starter template

**Status:** Accepted

**Context:** Members need roles (owner, member) to control access to features like member invitations and role management. Keycloak organizations support role assignment natively, but the application also has its own product database with a `members` table.

**Options Considered:**

1. **Keycloak org roles** — Store roles in Keycloak and read from JWT `org_roles` claim
   - Pros: Single source of truth in identity provider, roles available in JWT without DB query, Keycloak admin UI for role management
   - Cons: Keycloak Admin API is clunky for bulk queries ("all owners in org X"), role changes require Keycloak API calls with latency, roles cannot carry custom metadata, tight coupling to Keycloak

2. **Product database roles** — Store roles in `members.role` column, managed by application code
   - Pros: Simple JPA queries for role-based lookups, independent of identity provider, easy to audit, extensible with additional metadata
   - Cons: Role state could drift between Keycloak and product DB, requires sync logic on first login

3. **Hybrid approach** — Keycloak for authentication, product DB for authorization, sync on every login
   - Pros: Best of both worlds (Keycloak handles identity, app handles authorization)
   - Cons: Complexity of maintaining two role stores, sync logic needed, unclear source of truth

**Decision:** Store roles in the product database (`members.role` column), not in Keycloak organization roles. The product DB is the sole source of truth for authorization.

**Rationale:**
1. **Query flexibility:** The application can query "all owners" or "all members" with a simple JPA query. Keycloak's Admin API is clunky for these queries and introduces latency.
2. **Independence from Keycloak:** If the identity provider changes (unlikely but possible), role logic doesn't need to be rewritten. The application's authorization layer is self-contained.
3. **Audit trail:** Role changes are local database operations that can be easily audited with timestamps and changed-by tracking.
4. **Learned from experience:** Keycloak org roles have limited metadata, inconsistent API behavior across versions, and cannot be extended with custom attributes. Product-layer roles avoid these friction points entirely.

**Consequences:**
- Positive: Simple role queries and management with standard JPA
- Positive: No dependency on Keycloak for authorization decisions
- Positive: Role changes are instant (no Keycloak API round-trip)
- Negative: Role state could theoretically drift between Keycloak and the product DB (mitigated by using the product DB as the sole source of truth — Keycloak is never queried for roles after initial member creation)
- Negative: Initial role assignment during member sync must infer from Keycloak context (org creator = owner, invited = member)
