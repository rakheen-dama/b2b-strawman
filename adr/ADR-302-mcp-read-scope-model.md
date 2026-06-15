# ADR-302: MCP Read-Scope Model — Firm-Side RBAC vs the Customer-Scoped Portal Read-Model

**Status**: Accepted

**Context**:

Kazi has two distinct read surfaces. The **firm-side** services (`ProjectService`, `CustomerService`, `InvoiceService`, `ClientLedgerService`, `DocumentService`, `ActivityService`, `AuditService`, `AiFirmProfileService`) serve firm staff and scope data by the member's RBAC + project access — a member sees the matters they are on, owner/admin see everything firm-wide. The **portal-side** `customerbackend.PortalReadModelService` and its 15+ denormalized `Portal*View` projections serve the *external client portal* and are filtered by a single `customerId`: a portal user sees exactly one client's data.

The MCP server exposes data to **firm staff's** own Claude. The danger is reuse-by-proximity: the portal read-model already has clean, token-efficient denormalized views that look like an ideal MCP source. But routing MCP through `PortalReadModelService` would apply the portal's customer-id filter and incorrectly restrict a firm member to a single client's data — semantically wrong and a silent under-exposure bug that would make MCP useless for firm staff.

The decision: which read path backs the MCP tools, and how is project-level access enforced per tool.

**Options Considered**:

1. **Firm-side service layer, RBAC + project-access scoped; portal views as shape reference only (CHOSEN)** — Every MCP tool delegates to the firm-side service named in the catalogue, passing an `ActorContext` built from the resolved member. `ProjectAccessService.requireViewAccess` and `@RequiresCapability` fire exactly as in the web app. The `Portal*View` projections are consulted only as a *shape* inspiration for token-efficient DTOs, never invoked.
   - Pros: A member sees via MCP exactly what they see in the web app — no more (no cross-client leak), no less (no single-client over-restriction). Reuses the audited, access-controlled firm-side paths. No new query logic. Correct by construction.
   - Cons: Firm-side return types are uneven (some return entities, some controller-private DTOs, pagination is inconsistent) so MCP must define its own thin DTO + pagination-normalisation layer ([ADR-306](ADR-306-mcp-tool-contract-design.md), [Section 11.4](../architecture/phase78-mcp-server.md)).

2. **Reuse `PortalReadModelService` / `Portal*View` projections** — Route MCP reads through the existing denormalized portal read-model.
   - Pros: Views are already denormalized and token-efficient; less DTO work.
   - Cons: **Semantically wrong.** The portal filter is customer-scoped; a firm member would be restricted to one client (or, if the filter were stripped, would bypass project-access control entirely and leak every matter). It is built for a different actor with a different scoping rule. Reusing it forks the meaning of "what can this principal see."

3. **A new MCP-specific read-model / denormalized projections** — Build fresh firm-wide denormalized views tuned for MCP.
   - Pros: Could be perfectly token-shaped and firm-scoped.
   - Cons: Duplicates query logic the requirements forbid ("no new query logic that duplicates existing read paths"); a second source of truth for access decisions that can drift from `ProjectAccessService`; large build cost; high risk of an access-control divergence bug. Over-engineered for v1.

**Decision**: Option 1 — MCP tools delegate exclusively to the firm-side service layer with an `ActorContext`-propagated member identity; project access is enforced per tool via `ProjectAccessService.requireViewAccess` (404 for non-members) and per-domain `@RequiresCapability`; portal projections inform DTO *shape* only.

**Rationale**:

1. **Access semantics must be identical to the web app.** The only way to guarantee "a member sees via MCP exactly what they'd see in the browser" is to reuse the same services, the same `ActorContext`, and the same access guards. Any second read path is a second place for the access rule to be wrong.
2. **The portal filter is the wrong filter.** Customer-scoping is correct for an external client and catastrophic for a firm member — it either hides their other matters or, stripped, removes project access control. There is no safe way to repurpose it.
3. **No duplicate query logic.** Delegating to existing services honours the requirement and keeps a single source of truth for what each principal may read.
4. **The cost (a thin MCP DTO layer) is the right cost.** Normalising uneven return types and pagination at the MCP boundary is exactly where that concern belongs, and is far cheaper and safer than a parallel read-model.

**Consequences**:
- Positive: Zero cross-tenant or cross-client leakage by construction; MCP access tracks web-app access automatically as RBAC evolves.
- Positive: No duplicated read logic; single source of truth for access decisions.
- Negative: MCP must own a DTO + pagination-normalisation layer because firm-side contracts are uneven (entities, controller-private records, unbounded lists) — see [ADR-306](ADR-306-mcp-tool-contract-design.md).
- Negative: MCP inherits firm-side quirks it must document and tolerate — e.g. trust reads are module-gated (`moduleGuard.requireModule`) and fail for non-legal tenants; `CustomerService` returns entities the controller enriches with tags/member-names that MCP must replicate or omit.
- Neutral: `Portal*View` projections remain a useful design reference for token-efficient shaping without being a dependency.
- Related: [ADR-303](ADR-303-mcp-authentication.md) (how the member identity that scopes these reads is established), [ADR-304](ADR-304-mcp-tenant-isolation-capability-gating.md) (the gating regimes per tool), [ADR-306](ADR-306-mcp-tool-contract-design.md) (the DTO layer this decision necessitates).
