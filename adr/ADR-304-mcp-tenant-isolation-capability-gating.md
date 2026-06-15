# ADR-304: Tenant Isolation & Capability Gating for Machine-Consumable Reads

**Status**: Accepted

**Context**:

MCP exposes firm data to an automated client (Claude) that can issue many tool calls quickly and combine results in ways a human navigating the UI would not. Two guarantees must hold structurally, not by convention: (1) **tenant isolation** — a tool call can only ever read the one tenant resolved from the token; cross-tenant reads must be impossible by construction; and (2) **authorization** — a member sees via MCP exactly what their RBAC + project access permit, never more.

Kazi enforces tenant isolation by pure schema-per-tenant: Hibernate `search_path` is set from `RequestScopes.TENANT_ID`; there is no `@Filter`/`@FilterDef`/`tenant_id` column/RLS. Authorization is uneven across domains — there is no single "view" capability. Firm-side reads fall into three regimes (verified against source):
- **Project-access-gated** (no `@RequiresCapability`; `ProjectAccessService.requireViewAccess` → 404 if the member isn't on the project, owner/admin see all): matters, project documents, project activity.
- **Capability-gated** (org-wide): invoices + firm-wide unbilled → `INVOICING`; trust → `VIEW_TRUST`; firm audit log → `TEAM_OVERSIGHT`; compliance → `CUSTOMER_MANAGEMENT`; firm AI profile → `AI_MANAGE`.
- **Org-wide, no capability** (any authenticated member): customers, org/customer-scoped documents.

The question: what is the front-door gate for MCP itself, how do per-tool gates compose with it, and how is the AI-profile resource gated.

**Options Considered**:

1. **New `MCP_ACCESS` front-door capability + per-tool deference to each domain's existing regime; firm-profile keeps `AI_MANAGE` (CHOSEN)** — Add `MCP_ACCESS` to the `Capability` enum (default roles OWNER, ADMIN). `initialize` may succeed without it, but every tool returns an authorization error without it. Each tool then additionally inherits its domain's existing gate; `MCP_ACCESS` never widens any of them. The `kazi://firm-profile` resource keeps its existing `AI_MANAGE` gate (a member with `MCP_ACCESS` but not `AI_MANAGE` gets a clean auth error on that resource only). Tenant isolation rides the existing schema-per-tenant `search_path`, asserted by explicit cross-tenant isolation tests.
   - Pros: Clean separation — `MCP_ACCESS` controls "may this member use MCP at all," per-domain gates control "what data." Reuses the exact `@RequiresCapability` + `ProjectAccessService` paths, so MCP scope = web scope. No new isolation mechanism. Firm-profile is not silently relaxed.
   - Cons: A new capability must be seeded into OWNER/ADMIN role defaults (code-only if owner/admin resolve via the `ALL` pseudo-capability, or a seeder/backfill if role-capability rows are materialised — confirm at `/breakdown`).

2. **No MCP-specific capability; rely solely on per-domain gates** — Any authenticated member can open MCP; each tool's existing gate decides data access.
   - Pros: No new capability; less seeding.
   - Cons: No firm-level kill switch for "who may use the MCP connector at all" beyond the per-tenant enablement flag. The firm cannot grant MCP to owners/admins only while withholding it from members who otherwise have, say, `INVOICING`. Loses a clean front-door control the requirements call for.

3. **`MCP_ACCESS` as a single super-gate that grants all reads** — One capability that, once held, returns all firm data via MCP.
   - Pros: Simplest mental model and implementation.
   - Cons: **Catastrophic** — it would widen access beyond the web app (a member with `MCP_ACCESS` but no `VIEW_TRUST` would see trust data via MCP). Breaks the core invariant that MCP never exceeds web-app scope. Non-starter.

**Decision**: Option 1 — `MCP_ACCESS` is a pure front-door gate (default OWNER/ADMIN); every tool additionally enforces its domain's existing regime (project-access or per-domain capability or org-wide) and `MCP_ACCESS` widens nothing; `kazi://firm-profile` retains `AI_MANAGE`. Tenant isolation remains schema-per-tenant `search_path` from the token, covered by explicit isolation tests.

**Rationale**:

1. **Front-door and data gates are different questions.** "May this member connect Claude to Kazi at all" (a firm policy lever) is distinct from "what may they read" (existing RBAC). Conflating them (Option 3) breaks scope equality; omitting the front door (Option 2) loses a control the firm wants.
2. **Never widen.** The invariant that MCP ≤ web-app scope is enforced by deferring to the same `@RequiresCapability` and `ProjectAccessService` paths the controllers use. `MCP_ACCESS` is necessary-but-not-sufficient; it can only subtract reach, never add it.
3. **Firm-profile stays `AI_MANAGE` (per the locked product decision).** Even though it is house-style grounding rather than client PII, it is not silently relaxed — the existing gate holds, and an `MCP_ACCESS`-only member gets a clean error on that resource. This is the conservative, defensible default.
4. **Tenant isolation needs no new mechanism.** Because the token binds exactly one `TENANT_ID` and Hibernate sets `search_path` from it, a tool physically cannot query another schema. The job is to *prove* it with tests, not to invent isolation.

**Consequences**:
- Positive: Clean front-door control; MCP scope provably ≤ web scope; firm-profile protected; isolation inherited, not reinvented.
- Negative: `MCP_ACCESS` enum addition + role-default seeding (mechanism confirmed at `/breakdown`: code-only via `ALL` expansion, or seeder backfill — no global migration expected either way).
- Negative: Per-tool gating must be implemented and tested individually (no shortcut), including the trust module-gate edge case for non-legal tenants.
- Neutral: `MCP_ACCESS` is also the natural lever for a future "read-only data export" notion.
- Testing (structural guarantees this ADR demands): a cross-tenant isolation test (token for tenant A cannot read tenant B); a capability-gating test per regime (e.g. `MCP_ACCESS` without `VIEW_TRUST` → trust tool denied); a project-access test (non-member gets 404 on a matter they're not on); a read-only-registry assertion (no mutating tool exists). No live Claude in CI.
- Related: [ADR-303](ADR-303-mcp-authentication.md) (binds the identity these gates evaluate), [ADR-302](ADR-302-mcp-read-scope-model.md) (the firm-side paths whose gates are reused), [ADR-305](ADR-305-mcp-popia-consent-audit.md) (per-tenant enablement layered above `MCP_ACCESS`).
