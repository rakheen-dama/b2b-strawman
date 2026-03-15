# ADR-184: Vertical-Scoped Pack Filtering

**Status**: Accepted
**Date**: 2026-03-15

## Context

The platform's pack seeder system (field packs, compliance packs, template packs, clause packs, automation templates, request packs) applies **all packs on the classpath to every tenant**. This worked when all packs were universal (e.g., `common-customer`, `common-project`). Phase 47 introduces vertical-specific packs (e.g., `accounting-za-customer` for SA accounting firms) that should only be applied to tenants configured for that vertical.

Without filtering, provisioning a tenant creates field definitions, compliance checklists, document templates, automation rules, and request templates for **every vertical on the classpath** — an accounting firm would see law-specific FICA checklists, and a law firm would see accounting-specific engagement letter templates. This makes vertical QA testing unreliable and makes the platform feel generic rather than purpose-built.

The `AccessRequest` entity already captures an `industry` field (VARCHAR(100), required) during org registration. This field is stored but **not passed through** the approval → provisioning → seeding chain. The infrastructure to filter packs per-vertical is missing at three points: (1) `OrgSettings` has no vertical identifier, (2) `TenantProvisioningService` doesn't accept or store a vertical profile, and (3) `AbstractPackSeeder` has no filtering logic.

## Options Considered

### Option 1: Pack directory per vertical (classpath isolation)

Restructure pack directories from flat (`field-packs/*.json`) to vertical-scoped (`field-packs/universal/`, `field-packs/accounting-za/`, `field-packs/law-za/`). Each seeder's `getPackResourcePattern()` returns a pattern based on the tenant's vertical profile.

- **Pros**:
  - Clean directory separation — easy to see which packs belong to which vertical
  - No changes to pack JSON structure
- **Cons**:
  - Requires changing every seeder's `getPackResourcePattern()` method
  - Base class must know about vertical profiles to construct the glob pattern
  - Breaking change to existing pack directory structure (requires moving files)
  - Two-pass seeding needed: universal first, then vertical-specific
  - `PackReconciliationRunner` needs the same vertical-aware pattern logic

### Option 2: `verticalProfile` field on pack JSON + filter in `AbstractPackSeeder` (chosen)

Add an optional `"verticalProfile"` field to each pack's JSON definition. The `AbstractPackSeeder` reads `OrgSettings.verticalProfile` and skips packs where `pack.verticalProfile != null && !pack.verticalProfile.equals(orgSettings.verticalProfile)`. Packs without a `verticalProfile` field are universal and applied to all tenants.

- **Pros**:
  - Backward compatible — existing packs have no `verticalProfile` field and continue to apply universally
  - Single filtering point in `AbstractPackSeeder.doSeedPacks()` (~5 lines of code)
  - Pack JSON is self-describing — the vertical scope is declared alongside the content
  - No directory restructuring needed
  - Works with existing `PackReconciliationRunner` without changes (it calls the same seeders)
  - Naturally chains from `AccessRequest.industry` → `OrgSettings.verticalProfile`
- **Cons**:
  - Pack JSON gains an extra field (minimal impact)
  - Vertical profile must be set on `OrgSettings` before pack seeding runs (ordering dependency in provisioning)

### Option 3: Feature flags to gate pack application

Use the existing feature flag system (Phase 21) to control which packs are applied. Each pack declares required feature flags; the seeder checks flags before applying.

- **Pros**:
  - Reuses existing feature flag infrastructure
  - More granular than vertical profiles (can mix-and-match packs)
- **Cons**:
  - Feature flags are boolean — poor fit for "which vertical is this tenant?" (would need N flags per vertical)
  - Flag management complexity grows with each vertical
  - Packs become coupled to flag naming conventions
  - Flags are tenant-admin-managed; vertical profile should be set at provisioning time, not toggleable

## Decision

**Option 2: `verticalProfile` field on pack JSON + filter in `AbstractPackSeeder`.** The filtering happens at the seeder level with a single check, is fully backward compatible, and chains naturally from the existing `AccessRequest.industry` field through provisioning to `OrgSettings`.

## Rationale

Option 2 is the minimal change that enables vertical-scoped pack filtering. The `AccessRequest.industry` field was designed for exactly this purpose (captured during registration, stored on the request) — it just never made it through the provisioning chain. Threading it through requires:

1. **V70 migration**: `ALTER TABLE org_settings ADD COLUMN vertical_profile VARCHAR(50)` (tenant schema)
2. **`TenantProvisioningService`**: Accept `verticalProfile` parameter, set on `OrgSettings` after creation, before pack seeding
3. **`AccessRequestApprovalService.approve()`**: Map `AccessRequest.industry` to a vertical profile slug and pass it to `provisionTenant()`
4. **`AbstractPackSeeder.doSeedPacks()`**: Read `OrgSettings.verticalProfile`, skip non-matching packs
5. **Pack definition records**: Add optional `String verticalProfile()` to each pack definition DTO (defaults to `null`)

The industry-to-profile mapping is a simple function: `"Accounting" → "accounting-za"`, `"Legal" → "law-za"`, etc. Unmapped industries get `null` (universal packs only). This mapping can be a static map in the approval service initially, upgraded to a configurable lookup later if needed.

Option 1 was rejected because it requires restructuring existing pack directories and complicates the classpath scanning pattern. Option 3 was rejected because feature flags are the wrong abstraction for a categorical choice (which vertical) rather than a binary toggle (feature on/off).

## Consequences

### Positive
- Multiple verticals can coexist on the same classpath and in the same E2E stack
- Testing vertical profiles requires only provisioning orgs with different industry selections — no file swapping, no rebuilds
- Existing universal packs continue to work without any changes
- The `AccessRequest.industry` field, previously captured but unused, becomes load-bearing
- Pack JSON is self-documenting — `"verticalProfile": "accounting-za"` makes the intended audience explicit

### Negative
- Slight ordering dependency: `OrgSettings` must be created with `verticalProfile` set before pack seeding runs (this is the natural order in `TenantProvisioningService`, but must be maintained)
- Industry-to-profile mapping is initially hardcoded — adding new verticals requires a code change (acceptable for now; < 5 verticals in the near term)

### Neutral
- `PackReconciliationRunner` inherits the filtering automatically since it calls the same seeders
- The vertical profile manifest (`vertical-profiles/accounting-za.json`) remains a documentation artifact — the filtering is driven by the `verticalProfile` field on individual pack JSON files, not the manifest
