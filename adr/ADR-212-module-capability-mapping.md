# ADR-212: Module Capability Mapping -- Module-Specific Pair (VIEW_LEGAL, MANAGE_LEGAL)

**Status**: Proposed
**Date**: 2026-03-31
**Phase**: 55 (Legal Foundations: Court Calendar, Conflict Check & LSSA Tariff)

## Context

Phase 55 introduces three legal modules (`court_calendar`, `conflict_check`, `lssa_tariff`) that need authorization controls. The platform has an existing RBAC capability system (Phase 41/46) with capabilities like `PROJECT_MANAGEMENT`, `FINANCIAL_VISIBILITY`, `MEMBER_MANAGEMENT`, etc. Custom roles can be configured with any combination of capabilities.

The question is how to map legal module access to capabilities: should legal modules use the existing capabilities, define per-module capabilities, or use a shared pair of legal-specific capabilities?

The platform already has two gating mechanisms:
1. **Module guard** (`VerticalModuleGuard`): checks whether a module is enabled for the tenant. This is a tenant-level gate -- either the org has the module or it does not.
2. **Capability check** (`@RequiresCapability`): checks whether the current user's role has the required capability. This is a user-level gate -- the org has the module, but does this specific user have permission to use it?

These are complementary, not redundant. Module guard answers "does this org have court calendar?" Capability check answers "can this user create court dates?"

## Options Considered

### Option A: Reuse Existing Shared Capabilities

Map legal modules to existing capabilities: court dates use `PROJECT_MANAGEMENT` (since they are linked to matters), conflict checks use `PROJECT_MANAGEMENT`, tariff schedules use `FINANCIAL_VISIBILITY`.

- **Pros:** No new capabilities. Existing role configurations automatically grant legal access. Simple.
- **Cons:** Overly broad. A user with `PROJECT_MANAGEMENT` in an accounting firm should not automatically have `MANAGE_LEGAL` permissions if the firm later enables legal modules. No way to grant legal access without also granting project management access (or vice versa). The semantic mapping is weak -- conflict checks are not "project management," they are a regulatory compliance function.

### Option B: Per-Module Capabilities

Define separate capability pairs for each module: `VIEW_COURT_CALENDAR`, `MANAGE_COURT_CALENDAR`, `VIEW_CONFLICT_CHECK`, `MANAGE_CONFLICT_CHECK`, `VIEW_TARIFF`, `MANAGE_TARIFF`.

- **Pros:** Maximum granularity. A firm can grant court calendar access without conflict check access at the user level. Each module's permissions are independently configurable.
- **Cons:** Six new capabilities for three modules. Each new legal module adds two more. The role configuration UI becomes cluttered with capabilities that most firms will either all-enable or all-disable. In practice, legal staff who can view court dates will almost always also need to view conflict checks and tariffs -- the per-module split adds configuration overhead without practical access control benefit.

### Option C: Module-Specific Pair (VIEW_LEGAL, MANAGE_LEGAL) (Selected)

Define two new capabilities: `VIEW_LEGAL` (read access to all legal modules) and `MANAGE_LEGAL` (write access to all legal modules). All three legal modules use the same capability pair. Module guard still controls which modules are available at the tenant level.

- **Pros:** Two capabilities cover all legal modules. Simple role configuration: a firm grants VIEW_LEGAL to all staff and MANAGE_LEGAL to senior staff. Consistent with how legal firms actually work -- access to legal tools is role-based (partner vs. candidate attorney), not module-based. Module guard provides per-module tenant-level gating, so per-module user-level gating is redundant. Adding a new legal module does not require new capabilities.
- **Cons:** Cannot restrict a user to "court calendar only, no conflict check" at the capability level. However, this is an unlikely access control requirement -- if a firm enables conflict check, all authorized legal staff should be able to use it.

## Decision

**Option C -- Module-specific pair (VIEW_LEGAL, MANAGE_LEGAL).**

## Rationale

1. **Two-tier gating is sufficient:** Module guard handles "does this org use court calendar?" (tenant-level). Capability check handles "can this user manage legal data?" (user-level). Adding a third level (per-module per-user) creates configuration complexity without corresponding access control value.

2. **Matches real-world access patterns:** In SA law firms, access to practice management tools is determined by role (partner, associate, candidate attorney, secretary), not by feature. A partner who can see court dates will also need to see conflict checks and tariff schedules. Forcing per-module capability configuration does not match how firms actually manage permissions.

3. **Avoids capability explosion:** The platform already has 15+ capabilities. Adding 6 more for three legal modules (and 2 more for each future module) would make the role configuration UI unwieldy. Two capabilities (VIEW_LEGAL, MANAGE_LEGAL) scale regardless of how many legal modules are added.

4. **Consistent with accounting precedent:** The `regulatory_deadlines` module (Phase 51) could have had its own `VIEW_DEADLINES` / `MANAGE_DEADLINES` capabilities but instead uses existing accounting-relevant capabilities. The principle is the same: vertical-specific modules share vertical-specific capabilities.

5. **Default role mapping is clear:** VIEW_LEGAL maps to Owner + Admin + Member (all staff can see legal data). MANAGE_LEGAL maps to Owner + Admin (only senior staff can create/modify). This matches the firm hierarchy.

## Consequences

- **Positive:** Simple capability model -- two new entries in the capability registry
- **Positive:** Role configuration is straightforward -- one toggle for "can see legal data," one for "can manage legal data"
- **Positive:** Future legal modules automatically inherit the same capability pair
- **Positive:** Module guard provides per-module gating at the tenant level, making per-module user gating redundant
- **Negative:** Cannot restrict individual legal modules at the user level (e.g., "this user can manage court dates but not conflict checks")
- **Mitigations:** The module guard handles the more common case (tenant does not have conflict check enabled). If per-module user restriction is needed in the future, new capabilities can be added without breaking existing role configurations -- they would be checked in addition to VIEW_LEGAL/MANAGE_LEGAL.
