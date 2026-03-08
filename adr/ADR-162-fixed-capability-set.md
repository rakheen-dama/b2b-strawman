# ADR-162: Fixed Capability Set vs Dynamic Permissions

**Status**: Proposed
**Date**: 2026-03-08
**Phase**: 41 (Organisation Roles & Capability-Based Permissions)

## Context

Phase 41 introduces capability-based authorization to replace the coarse Owner/Admin/Member role model. The platform has ~50+ `@PreAuthorize` annotations gating admin-only endpoints across 7 functional domains: financials, invoicing, projects, team oversight, customers, automations, and resource planning. The question is how granular the permission model should be.

The target users are administrators of 10–50 person professional services firms — not IT administrators. They need to express policies like "this person handles billing but shouldn't see profitability" or "this person manages projects and approves time." The UI for role management must be simple enough that a non-technical office manager can configure it in under a minute.

Competitors in the professional services space:
- **Productive.io**: ~8 role-based permission areas (projects, financials, scheduling, etc.)
- **Scoro**: ~10 permission groups with toggle switches
- **Harvest**: ~5 permission levels (administrator, manager, member, etc.)
- **Accelo**: ~6 permission areas with granular toggles

## Options Considered

1. **Per-endpoint permissions (~100+ toggles)** — Define a permission for every admin-gated endpoint. E.g., `INVOICE_CREATE`, `INVOICE_APPROVE`, `INVOICE_SEND`, `INVOICE_DELETE`, `BILLING_RATE_VIEW`, `BILLING_RATE_EDIT`, etc. Roles are arbitrary sets of these fine-grained permissions.
   - Pros: Maximum flexibility — firms can create arbitrarily specific roles. No future "we need to split this capability" situations. Industry-standard RBAC granularity used by enterprise IAM systems.
   - Cons: UX disaster for target users — a role creation dialog with 100+ checkboxes is overwhelming for a non-technical office manager. Enforcement logic explodes — every endpoint needs a unique permission constant, every test must verify the specific permission. Permission names become a versioning problem (what happens when endpoints are renamed or split?). The sheer number of permissions makes it likely that roles are misconfigured — "I thought I gave them invoice access but missed INVOICE_APPROVE." Competitors with comparable target markets all chose 5–10 high-level permission areas, not per-endpoint toggles.

2. **Fixed curated capabilities — 7 domain areas (chosen)** — Define exactly 7 capabilities aligned with functional domains: `FINANCIAL_VISIBILITY`, `INVOICING`, `PROJECT_MANAGEMENT`, `TEAM_OVERSIGHT`, `CUSTOMER_MANAGEMENT`, `AUTOMATIONS`, `RESOURCE_PLANNING`. Each capability gates a cohesive set of related endpoints. The set is compiled into the application code — admins choose which capabilities a role gets, but cannot define new capability types.
   - Pros: Simple UX — 7 checkboxes with clear descriptions. Matches the mental model of the target user ("can this person do invoicing?"). Bounded enforcement logic — 7 constants to check, not 100+. Each capability aligns with a sidebar navigation section, making the UI gating natural. Matches competitor patterns (5–10 areas). No permission naming/versioning problem — capabilities are stable domain concepts, not endpoint names. Easy to expand later: adding an 8th capability is a one-line enum change + migration.
   - Cons: Cannot express "can create invoices but not approve them" — invoicing is all-or-nothing within the capability. If a future requirement demands sub-capability granularity, a capability must be split (e.g., `INVOICING` → `INVOICE_MANAGEMENT` + `INVOICE_APPROVAL`). This is a breaking change for existing roles.

3. **Hierarchical capability groups** — Define 7 top-level capability groups (same as Option 2), each with sub-capabilities. E.g., `INVOICING` contains `INVOICE_CREATE`, `INVOICE_APPROVE`, `INVOICE_SEND`. Admins can toggle at the group level (all-or-nothing) or expand to toggle individual sub-capabilities.
   - Pros: Combines the simplicity of Option 2 (7 top-level checkboxes for common cases) with the granularity of Option 1 (sub-toggles for edge cases). Progressive disclosure — most admins use group-level toggles, power users drill into sub-capabilities.
   - Cons: Significantly more complex to implement — two-level enforcement, UI for expandable toggle groups, resolution logic must handle group-level and sub-level overrides. The "progressive disclosure" argument is speculative — there's no evidence that DocTeams' target users need sub-capability granularity today. Adds conceptual overhead: "does the user have INVOICING, or INVOICING.APPROVE, or both?" The sub-capability definitions are harder to maintain than a flat enum. If sub-capabilities are needed later, Option 2 can be extended to Option 3 without breaking existing roles (the top-level capability becomes a group).

## Decision

Use **fixed curated capabilities — 7 domain areas** (Option 2). The `Capability` enum defines exactly 7 values. Each value gates a cohesive set of endpoints aligned with a functional domain. Admins compose roles by selecting capabilities from this fixed set. The set is part of the application code, not user-configurable.

## Rationale

The target user for role management is a non-technical administrator at a 10–50 person firm. A dialog with 7 labeled checkboxes and descriptions is immediately comprehensible. A dialog with 100+ per-endpoint permissions (Option 1) or a two-level expandable tree (Option 3) requires training and increases misconfiguration risk.

The 7 capabilities map directly to the platform's sidebar navigation sections and functional domains. This creates a natural correspondence: "if I uncheck Invoicing, the Invoices nav item disappears." This 1:1 mapping between capabilities and UI sections simplifies both the backend enforcement and the frontend gating logic.

The risk of needing sub-capability granularity is real but manageable. If a future requirement demands "can create invoices but not approve them," the `INVOICING` capability can be split into two capabilities without breaking the resolution algorithm — the migration would replace `INVOICING` rows in `org_role_capabilities` with two new capability values. This is a data migration, not an architecture change. Starting with 7 capabilities and splitting later is lower-risk than starting with 100+ and trying to simplify.

All competitors targeting the same market segment (Productive.io, Scoro, Harvest, Accelo) use 5–10 high-level permission areas. None offer per-endpoint toggles for their SMB plans. This validates the 7-capability approach as appropriate for the target market.

## Consequences

- **Positive**: Simple UX — role creation requires selecting from 7 labeled checkboxes, completable in under 30 seconds
- **Positive**: Bounded enforcement — 7 `@RequiresCapability` values to maintain, not 100+ per-endpoint permissions
- **Positive**: Natural sidebar mapping — each capability corresponds to a navigation section, simplifying frontend gating
- **Positive**: Easy expansion — adding an 8th capability (e.g., for a future vertical feature) is an enum value + migration, no architecture change
- **Negative**: Cannot express intra-domain granularity (e.g., "invoicing but not approval"). Mitigation: split capabilities if this need arises, or use per-user overrides as a workaround
- **Negative**: The 7 capabilities are a product opinion — if a vertical fork needs different groupings (e.g., legal firms wanting "trust accounting" separate from general invoicing), the enum must be extended
- **Neutral**: The fixed set means no admin-configurable permission types. This is intentional — it keeps the enforcement logic predictable and the UI simple
