# ADR-020: Customer Portal Authentication and Access Approach

**Status**: Accepted (conceptual — not implemented in this phase)

**Context**: The platform will eventually expose a read-only customer portal where Customers can view their projects, documents, and status updates. This ADR establishes the architectural direction so that current data model decisions (Customer entity, document visibility, project linking) are portal-ready without premature implementation.

**Options Considered**:

1. **Reuse Clerk (same instance, customer role)** — Add Customers as Clerk org members with a restricted `org:customer` role.
   - Pros: Single auth system; JWT-based; Clerk handles password reset, MFA, etc.
   - Cons: Conflates staff and customer identity. Customer counts against member limits (plan enforcement breaks). Clerk's org member model expects collaborators, not external read-only users. No natural way to scope a Clerk member to specific projects/documents — the org-level JWT grants access to the entire tenant, and fine-grained scoping must be built in the backend anyway.

2. **Separate Clerk instance for customers** — A second Clerk application dedicated to customer-facing auth, with its own user pool.
   - Pros: Clean separation of staff and customer identity. Clerk handles all auth UX (sign-in, MFA, password reset). JWT from the customer Clerk instance carries the customer's identity.
   - Cons: Two Clerk subscriptions and configurations. Must map between Clerk customer user ID and the internal `customers.id` record. Customer onboarding requires Clerk account creation (friction for customers who may only log in once). Overkill if customer access patterns are simple (view documents, check status).

3. **Email-based magic links (self-hosted)** — Customers receive a magic link via email. Clicking the link creates a short-lived session token tied to their `customers.email`. No password, no separate identity provider.
   - Pros: Zero friction for customers (no account creation, no password). Simple to implement (send email, verify token, create session). Maps directly to `customers.email` — no identity mapping layer. Cost-effective (email sending only, no auth SaaS fees). Appropriate for the access pattern (infrequent, read-only, low-stakes).
   - Cons: Security depends on email account security (acceptable for read-only access). No MFA (can be added later if needed). Must build session management (JWT issuance, expiry, refresh). No built-in password reset flow (not needed — magic links are stateless).

4. **Token-based invite links** — Staff generates a unique, expiring link for a Customer. The link grants scoped read-only access without any authentication.
   - Pros: Simplest possible implementation. No auth system needed.
   - Cons: No persistent identity — each link is a one-time or time-limited session. Can't build a "my portal" experience. Links can be forwarded to unauthorized parties. Not suitable for ongoing access.

**Decision**: Email-based magic links as the primary approach, with Separate Clerk instance as a future upgrade path (Options 3 → 2).

**Rationale**: Customer portal access is read-only and infrequent. Magic links provide zero-friction auth that maps naturally to the existing `customers.email` field. The flow is:

1. Customer navigates to `/portal` and enters their email.
2. Backend looks up `customers` table by email (within the org context).
3. If found, sends a magic link to that email.
4. Customer clicks link → backend verifies token → issues a short-lived JWT with claims: `{ customer_id, org_id, type: "customer" }`.
5. Portal pages use this JWT for all API calls to `/portal/*` endpoints.

If customer access patterns grow more complex (document uploads, collaboration, MFA requirements), the magic link system can be replaced with a separate Clerk instance without changing the data model — the mapping layer (`customers.email` → external identity) remains the same.

**Architectural Separation**:

| Aspect | Staff (current) | Customer Portal (future) |
|--------|----------------|------------------------|
| Auth provider | Clerk (org members) | Magic links → JWT |
| JWT issuer | Clerk | Self-hosted |
| JWT claims | `sub`, `org_id`, `org_role` | `customer_id`, `org_id`, `type: "customer"` |
| API prefix | `/api/*` | `/portal/*` |
| Spring Security filter chain | `JwtAuthFilter` → `TenantFilter` → `MemberFilter` | `CustomerAuthFilter` → `TenantFilter` (no MemberFilter) |
| Data access | Full CRUD (role-based) | Read-only, scoped to customer's projects and SHARED documents |
| Frontend | `/(app)/org/[slug]/*` | `/portal/*` (separate app or route group) |

**Portal Visibility Rules**:

| Data | Visible to Customer? | Condition |
|------|---------------------|-----------|
| Projects | Yes | Customer linked via `customer_projects` |
| Project documents | Yes | `scope = 'PROJECT'` AND `visibility = 'SHARED'` AND project linked to customer |
| Customer documents | Yes | `scope = 'CUSTOMER'` AND `customer_id = self` AND `visibility = 'SHARED'` |
| Org documents | Yes | `scope = 'ORG'` AND `visibility = 'SHARED'` |
| Tasks | No | Staff-only (may change later) |
| Other customers | No | Never |
| Team members | No | Never |

**Consequences**:
- `documents.visibility` column (INTERNAL/SHARED) is added now to support future portal filtering.
- `CustomerAuthFilter` (future) reads the customer JWT and binds `RequestScopes.CUSTOMER_ID` (new ScopedValue).
- `/portal/*` endpoints are a separate Spring Security filter chain with read-only access.
- No Clerk dependency for customer auth — decoupled from the staff identity system.
- Customer onboarding remains staff-driven (staff creates Customer record, enters email). Customer self-registration is a future extension.
- The magic link approach can be swapped for a Clerk instance later without schema changes.
