# ADR-192: Enabled Modules Authority

**Status**: Accepted
**Date**: 2026-03-18
**Phase**: 49 (Vertical Architecture)

## Context

The `enabled_modules` JSONB column on `OrgSettings` controls which vertical-specific modules a tenant can access. This array is the backing data for `VerticalModuleGuard.requireModule()` (ADR-190) -- if a module ID is not in the array, the guard rejects all API requests to that module's endpoints with HTTP 403. This makes `enabled_modules` a security boundary: modifying it directly controls which features a tenant can use.

The question is who has authority to modify this field. The answer has implications for security (can a tenant self-enable modules they shouldn't have?), billing (are modules tied to paid tiers?), and UX (how easy is it for a tenant to start using a new module?). The existing platform has two relevant precedents. First, `OrgSettings` fields like `default_currency`, `tax_label`, and `accounting_enabled` are modified by org owners through the settings UI (`PUT /api/settings`). Second, the admin-approved org provisioning flow (Phase 39) demonstrates the pattern where certain operations require platform admin approval -- a tenant requests something, the admin reviews and approves, and the system applies the change.

The vertical profile system (ADR-189) introduces a related but distinct concept: the `vertical_profile` field on `OrgSettings` identifies which profile the tenant was provisioned with (e.g., "accounting-za", "legal-za"). The profile determines the *default* set of enabled modules at provisioning time. The question here is about *post-provisioning* modification of `enabled_modules`, independent of the profile. A legal firm provisioned with "legal-za" gets `["trust_accounting", "court_calendar", "conflict_check"]` by default. Can they later add or remove modules from this list?

## Options Considered

### Option 1: Platform admin only

The `enabled_modules` field can only be set during provisioning (when the admin approves a new org and selects a vertical profile) or by a dedicated platform admin API endpoint. Org owners can view their enabled modules in settings but cannot modify the list. The `PUT /api/settings` endpoint ignores any `enabled_modules` field in the request body for non-admin callers. A separate `PUT /internal/orgs/{orgId}/modules` endpoint (API-key authenticated, like other internal endpoints) allows the platform admin to modify enabled modules.

- **Pros:**
  - Security boundary is controlled by the platform operator, not by tenants -- prevents self-enablement of modules the tenant hasn't paid for or isn't qualified to use
  - Supports future paid module tiers: when module-level billing is implemented, the admin enables modules after payment is confirmed, and the tenant cannot bypass the billing gate
  - Consistent with the Phase 39 admin-approved provisioning pattern where security-sensitive org configuration is controlled by the platform operator
  - Simple implementation: `PUT /api/settings` continues to ignore `enabled_modules`, and the internal endpoint is a straightforward extension of the existing `/internal/` API pattern
  - Audit trail is clear: only platform admin actions modify `enabled_modules`, so the audit log shows exactly who enabled or disabled a module and when

- **Cons:**
  - Manual process: if a tenant wants to try a new module, they must contact the platform admin and wait for the change -- no self-service
  - Increases admin workload: every module enablement request requires admin action (acceptable for a solo-founder project with few tenants initially, but may not scale)
  - No in-app mechanism for tenants to request module changes -- they must use out-of-band communication (email, support ticket)
  - If the admin is unavailable, tenants are blocked from accessing new modules until the admin returns

### Option 2: Org owner self-service

Org owners can modify `enabled_modules` directly from the settings UI. The `PUT /api/settings` endpoint accepts an `enabledModules` array field when the caller has the `OWNER` role. The frontend shows a "Modules" section in settings with toggles for each available module.

- **Pros:**
  - Instant self-service: tenants can enable or disable modules without waiting for admin approval
  - Better UX: a tenant exploring the platform can immediately try trust accounting by toggling it on
  - Reduces admin workload to zero for module management
  - Simpler implementation: no internal API endpoint needed, just extend the existing settings update with a new field

- **Cons:**
  - No billing gate: if modules are ever tied to paid tiers, self-service enablement bypasses the payment requirement -- the billing system would need to be integrated with the settings update
  - Security risk: an org owner could enable modules they shouldn't have access to (e.g., a consulting firm enabling trust accounting, which has compliance implications in the legal domain)
  - No opportunity for the platform to guide or validate module selection -- a tenant could enable an arbitrary combination of modules that don't make sense together
  - Undoing a self-service enablement (e.g., for compliance reasons) requires either a platform admin override or trust that the tenant will self-disable
  - Divergence from the admin-approved provisioning pattern: org creation requires admin approval, but module enablement doesn't -- inconsistent security model

### Option 3: Profile-driven only -- no independent modification

The `enabled_modules` field is always derived from the `vertical_profile` field. When a tenant changes their vertical profile (e.g., from "consulting-generic" to "legal-za"), the `enabled_modules` array is recomputed from the profile definition in the `VerticalProfileRegistry` (ADR-189). There is no way to modify `enabled_modules` independently of the profile. A legal firm always gets exactly `["trust_accounting", "court_calendar", "conflict_check"]` -- no more, no less.

- **Pros:**
  - Simplest model: `enabled_modules` is a computed field, not an independent configuration -- one fewer thing to manage
  - No authority question: nobody modifies `enabled_modules` directly, so there's no debate about who can
  - Profile consistency: a tenant's enabled modules always match their stated vertical, preventing nonsensical combinations (e.g., a consulting firm with trust accounting)
  - Reduces the OrgSettings API surface: `enabled_modules` is read-only in all contexts

- **Cons:**
  - No flexibility for cross-vertical module use: a consulting firm that also handles trust accounting for clients cannot enable just that one module without switching their entire profile to "legal-za"
  - No way to partially adopt a vertical: a legal firm that wants court calendar but not trust accounting (because they use an external trust accounting system) is forced to take all or nothing
  - Adding a new module to a profile retroactively affects all tenants on that profile (if `enabled_modules` is recomputed on profile read) or creates drift (if it's only computed at provisioning time)
  - Limits future monetization: module-level pricing requires the ability to enable individual modules, which the profile-only approach does not support
  - A tenant that wants a custom module combination must either get a custom profile defined in the registry (doesn't scale) or accept a standard profile that doesn't match their needs

### Option 4: Hybrid -- profile sets defaults, platform admin can override

The vertical profile determines the default `enabled_modules` at provisioning time. Post-provisioning, only the platform admin can override the list via the internal API. Org owners can request changes through an in-app mechanism (a "Request Module" button that creates a notification or audit event for the admin). The admin reviews the request and applies the change if appropriate.

- **Pros:**
  - Best of Options 1 and 3: profiles provide sensible defaults, admin provides flexibility for exceptions
  - Supports the "consulting firm that also does trust accounting" case without requiring a custom profile
  - The request mechanism gives tenants agency without giving them direct control over a security boundary
  - Admin can enforce business rules (billing, compliance, qualification) before enabling modules
  - Audit trail captures both the tenant's request and the admin's action

- **Cons:**
  - Most complex implementation: requires the provisioning profile defaults (Option 3), the admin override API (Option 1), and a request/notification mechanism
  - The request workflow is a new pattern that doesn't exist in the codebase -- it requires a notification type, a UI component for the request, and admin UI to review/approve
  - Over-engineered for the current state: with 3 profiles and <10 expected tenants in the near term, a formal request workflow is premature
  - The admin override creates a divergence from the profile: a tenant's `enabled_modules` no longer matches their `vertical_profile`, requiring explanation in the UI ("You're on the Legal profile with custom module configuration")

## Decision

**Option 1 -- Platform admin only, with profile-driven defaults at provisioning.**

## Rationale

Module enablement is a security boundary. The `VerticalModuleGuard` enforces access control based on `enabled_modules`, and future phases will build domain modules (trust accounting with fiduciary compliance requirements, conflict checks with legal ethical obligations) where incorrect enablement has real consequences beyond feature access. Keeping this boundary under platform admin control ensures that module enablement can be paired with billing verification, compliance checks, or qualification validation as the platform matures.

The solo-founder project context makes Option 1 practical. With a small number of tenants in the near term, the admin (the founder) can handle module enablement requests manually. The internal API endpoint (`PUT /internal/orgs/{orgId}/modules`) is the same infrastructure pattern used for tenant provisioning (Phase 39) and schema management -- it's an authenticated server-to-server call, not a user-facing API. Building a self-service module toggle (Option 2) or a formal request workflow (Option 4) is premature when the expected volume is single-digit tenants.

The profile-driven-only approach (Option 3) is too rigid. The existing `accounting-za.json` profile shows that profiles bundle multiple concerns (packs, rates, terminology, modules). Locking `enabled_modules` to the profile prevents the granular module management that real tenants will need: a legal firm might use trust accounting but outsource court calendar management, or a consulting firm might want conflict checking without the full legal vertical. The `enabled_modules` field exists precisely to decouple module access from the higher-level profile concept.

The provisioning flow applies profile defaults: when the admin approves a new org with the "legal-za" profile, `enabled_modules` is set to `["trust_accounting", "court_calendar", "conflict_check"]` from the `VerticalProfileRegistry` (ADR-189). Post-provisioning, the admin can modify this list via the internal API. The profile is the starting point, not a permanent constraint. This approach mirrors how `OrgSettings.defaultCurrency` is set by the profile at provisioning time but can be changed independently afterward.

When module-level billing is implemented in a future phase, the admin-only approach provides the natural integration point: the billing system confirms payment, then calls the internal API to enable the module. Self-service enablement (Option 2) would require building the billing gate simultaneously, which is out of scope for Phase 49.

## Consequences

- **Positive:**
  - Module enablement is a controlled, auditable operation performed by the platform admin
  - Future billing integration has a clean hook: payment confirmed -> admin API enables module -> guard allows access
  - The `PUT /api/settings` endpoint does not need modification for `enabled_modules` -- it continues to handle tenant-editable settings only
  - Consistent with the Phase 39 admin-approved provisioning pattern: security-sensitive configuration is admin-controlled
  - Profile defaults at provisioning reduce admin work for new tenants: approving a "legal-za" org automatically sets the correct modules

- **Negative:**
  - No self-service module enablement: tenants must contact the platform admin for module changes (acceptable for current scale, may need revisiting at 50+ tenants)
  - No in-app request mechanism: tenants use out-of-band communication to request module changes (a future phase could add a "Request Module" button that creates a notification)
  - The internal API for module management must be built and maintained alongside the provisioning API
  - If the admin is unavailable, module enablement requests are blocked until the admin returns

- **Neutral:**
  - Org owners can view their `enabled_modules` in the settings response (`GET /api/settings`) but cannot modify them -- the field is read-only in the tenant-facing API
  - Changing `vertical_profile` via the settings UI triggers profile re-application (packs, terminology, defaults) but does NOT automatically change `enabled_modules` -- the admin must explicitly update modules if a profile change warrants it
  - The `GET /api/modules` endpoint (which returns module metadata with enabled/disabled status per tenant) reads from `OrgSettings.enabled_modules` and does not require admin authentication -- it is informational, not mutational
  - This decision can be revisited when the platform scales beyond the solo-admin model, at which point Option 4 (hybrid with request workflow) becomes the natural evolution
