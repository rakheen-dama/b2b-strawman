# ADR-181: Vertical Profile Structure

**Status**: Accepted
**Date**: 2026-03-15

## Context

DocTeams is a horizontal B2B SaaS platform with 46 phases of built-in functionality. To serve a specific vertical (e.g., SA accounting firms), the platform needs to be configured with industry-specific data: custom fields for SA company registration numbers and SARS tax references, FICA compliance checklists, engagement letter templates with SAICA-standard clauses, automation rules for tax deadlines, and request templates for year-end information gathering.

The existing infrastructure already supports all of these configuration types through pack seeders (Phase 11 field packs, Phase 14 compliance packs, Phase 12 template packs, Phase 31 clause packs, Phase 37 automation templates, Phase 38 request templates). Each seeder loads JSON files from a classpath directory, applies them to a tenant schema, and tracks application status in `OrgSettings` for idempotency. What does not exist is a way to say "this tenant is an accounting firm" and have all the relevant packs applied together as a coherent unit.

The question is how to define and apply a vertical profile — the bundle of packs that together configure the platform for a specific industry.

## Options Considered

### Option 1: Runtime VerticalProfileService

Build a `VerticalProfileService` that reads a profile manifest (JSON), resolves the referenced pack IDs, and orchestrates pack application in the correct order. The profile is applied at tenant provisioning time based on a `verticalProfile` field on the org registration request. A settings UI allows admins to view and modify the applied profile.

- **Pros**:
  - Fully automated: tenant provisioning applies the correct profile without manual steps
  - Single entry point: one API call applies all packs
  - Profile is a first-class concept in the system — queryable, auditable
- **Cons**:
  - New service, new API endpoint, new OrgSettings field — production code changes in a QA phase
  - Rate card and tax defaults require knowing which members exist, which is not available at provisioning time
  - Over-engineering for a single vertical — the first vertical is the only one that needs this
  - Profile modification UI adds frontend complexity

### Option 2: JSON manifest as documentation + existing seeders (chosen)

Create a JSON manifest file (`vertical-profiles/accounting-za.json`) that documents which packs belong to the accounting vertical, what rate card defaults to set, and what terminology overrides to apply. The manifest is a coordination artifact — it is not consumed by any runtime service. The individual pack files are placed in the standard classpath directories where existing seeders already discover them. Pack application happens through the existing `TenantProvisioningService` (which calls all seeders) or manually via Settings UI.

- **Pros**:
  - Zero production code changes — only new JSON data files
  - Leverages existing, proven seeder infrastructure
  - Profile manifest serves as documentation for what constitutes the vertical
  - Can be promoted to a runtime service later (Option 1) if multiple verticals justify the investment
  - Appropriate scope for a QA phase — no risk of introducing new bugs
- **Cons**:
  - No automated "apply accounting profile" button — packs are applied individually or all-at-once via provisioning
  - Rate card and tax defaults must be set manually (no seeder for rates/tax)
  - Profile is not a queryable concept — you cannot ask "which vertical is this tenant?"
  - Non-accounting packs (e.g., `common-customer`) are also applied during provisioning, so the tenant gets both generic and accounting-specific packs

### Option 3: Feature flag bundle

Define a vertical profile as a set of feature flags (e.g., `vertical.accounting-za.enabled=true`) that conditionally load pack data and toggle UI behaviour. Feature flags control which packs are discovered during seeding and which terminology overrides are loaded.

- **Pros**:
  - Runtime toggle: can enable/disable a vertical per tenant
  - Clean separation: non-accounting tenants never see accounting packs
  - Feature flags already exist conceptually (tier-based feature gating from Phase 2)
- **Cons**:
  - Feature flags for content packs is a category error — feature flags gate functionality, not data configuration
  - Adds conditional logic to every seeder (check flag before applying pack)
  - Complicates pack discovery — seeders currently load everything on the classpath
  - Removing a vertical after application is destructive (delete field definitions, templates, etc.)

## Decision

**Option 2: JSON manifest as documentation + existing seeders.** The vertical profile is defined as a JSON manifest file that documents the profile composition. Individual pack files are added to standard classpath directories. No new runtime services or API endpoints are created.

## Rationale

This phase is a QA and content phase, not a feature phase. The primary constraint is "no new platform features" — the output is pack data, a lifecycle script, and a gap report. Building a runtime `VerticalProfileService` would violate that constraint and introduce untested production code in a phase designed to find bugs, not create them.

The existing pack seeder infrastructure is mature and well-tested (6 seeders, used since Phase 11). Adding new JSON files to their classpath directories is the lowest-risk way to introduce vertical content. The seeders' idempotency checks (via `OrgSettings` pack status tracking) ensure that packs are applied exactly once, even if the seeder runs multiple times.

The manifest file serves a real purpose even without runtime consumption: it is the specification that a future `VerticalProfileService` would read, it documents the vertical for the team, and it provides the builder agent with a single source of truth for what packs to create. If Phase 48 or a future phase needs automated vertical application, the manifest becomes the input to Option 1 — no rework required.

The main trade-off is that non-accounting tenants will also receive accounting-specific packs during provisioning (since seeders discover all classpath packs). This is acceptable for now: extra field definitions and templates do not harm generic tenants — they simply appear as additional options in the UI. If pack isolation becomes important with multiple verticals, the seeder can be extended with a `targetProfile` filter in the pack JSON, which the seeder checks before applying.

## Consequences

### Positive
- Zero production code risk — only data files are added
- Existing seeder tests cover pack application correctness
- Profile manifest is immediately reusable if a runtime service is built later
- Clear separation: this phase creates content, future phases create infrastructure

### Negative
- No "one-click apply accounting profile" capability — packs are applied individually or all-at-once during provisioning
- Rate cards and tax rates must be configured manually via Settings UI
- Non-accounting tenants receive accounting-specific packs (benign but untidy)

### Neutral
- The manifest file (`vertical-profiles/accounting-za.json`) establishes a convention for future verticals — any new vertical follows the same pattern
- The `autoApply: false` flag on the field pack group means accounting fields do not automatically attach to every new customer — the admin must apply the field group (this is correct behaviour for an optional vertical)
