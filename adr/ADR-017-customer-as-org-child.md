# ADR-017: Customers as Children of Organization

**Status**: Accepted

**Context**: The platform needs to support "Customers" — the external clients that an Organization serves. In DocTeams, these are the people/entities whose projects and documents are being managed. The question is how to model Customers relative to the existing multi-tenant architecture.

**Options Considered**:

1. **Customers as separate tenants** — Each Customer gets their own schema or row-level isolation boundary, similar to how Organizations are modelled today.
   - Pros: Maximum isolation; familiar pattern; natural fit if Customers manage their own data.
   - Cons: Fundamentally wrong abstraction — Customers don't "own" their data, the Organization does. Creates a two-axis multitenancy problem (Org × Customer) that exponentially increases schema/RLS complexity. Provisioning overhead per Customer is unjustifiable. Breaks the invariant that all data within a tenant belongs to one Organization.

2. **Customers as Clerk Organization members** — Invite Customers into the Clerk organization with a restricted role (e.g., `org:customer`).
   - Pros: Reuses existing Clerk auth; Customers appear in the member list; JWT naturally scopes them to the org.
   - Cons: Clerk's org membership model is designed for staff/collaborators, not external clients. Member limits on plans would count Customers against the cap. Clerk's role system doesn't support the kind of fine-grained, data-level scoping Customers need (e.g., "see only your projects"). Mixing staff and Customer identity in the same system creates confusion in the UI and data model.

3. **Customers as tenant-scoped domain entities** — A `customers` table within the tenant schema, with foreign keys to projects via a junction table. Customers are data records managed by staff. Future portal auth is handled separately.
   - Pros: Clean separation of concerns — Customers are business data, not identity/auth primitives. No impact on Clerk member limits or role system. Natural fit for the existing data model (like Projects and Documents). Easy to add portal auth later by mapping an external identity to a Customer record. Works identically in both shared and dedicated schemas.
   - Cons: No built-in auth for Customers (must be added later for the portal). Staff must manually manage Customer records (no self-service onboarding yet).

**Decision**: Customers as tenant-scoped domain entities (Option 3).

**Rationale**: Customers are fundamentally different from Organization members. They don't collaborate on documents or manage projects — they are the *subjects* of the Organization's work. Modelling them as tenant-scoped data entities preserves the existing single-axis multitenancy model (Organization = tenant) and avoids polluting Clerk's identity system with non-staff users. The junction table `customer_projects` provides the many-to-many relationship needed to link Customers to their relevant Projects.

The design is deliberately "data-first, identity-later": Customer records capture contact details and project relationships now. When the customer portal is built (see ADR-020), an external identity (email-based magic link, separate Clerk instance, or token) is mapped to the existing Customer record via `customer.email`. This avoids over-engineering auth before there's a portal to use it.

**Consequences**:
- `customers` table in tenant schema with `tenant_id` for shared-schema support.
- `customer_projects` junction table for many-to-many with Projects.
- Customer data inherits all existing tenant isolation guarantees (schema isolation for Pro, Hibernate `@Filter` + RLS for Starter).
- Clerk member limits are unaffected — Customers don't consume member seats.
- Future customer portal requires a separate auth mechanism (see ADR-020).
- Staff UI must provide Customer CRUD — no self-service Customer onboarding in this phase.
