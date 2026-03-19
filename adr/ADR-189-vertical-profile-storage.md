# ADR-189: Vertical Profile Storage

**Status**: Accepted
**Date**: 2026-03-18
**Phase**: 49 (Vertical Architecture)

## Context

Phase 49 introduces a vertical profile system that formalises the relationship between tenants and industry verticals. A vertical profile is a named configuration bundle that determines which modules a tenant can access, which packs to apply at provisioning, which terminology namespace to use, and what default settings (currency, tax rate) to seed. The phase defines three concrete profiles: IT Consulting (generic), SA Accounting (config-only), and SA Legal (config + module stubs for trust accounting, court calendar, and conflict check).

The question is where to store these profile definitions. The `OrgSettings` entity already has a `vertical_profile` VARCHAR(50) column (added in a prior phase but currently unused). Phase 49 extends this with `enabled_modules` (JSONB) and `terminology_namespace` (VARCHAR) columns. These per-tenant columns store the tenant's *applied* profile state. The question here is about the *profile definitions themselves* -- the templates that describe what "accounting-za" means in terms of modules, packs, and defaults.

The existing codebase already has a partial precedent: `backend/src/main/resources/vertical-profiles/accounting-za.json` defines pack selections, rate card defaults, tax defaults, and terminology overrides for the SA accounting profile. This JSON file is consumed by the pack seeder system at provisioning time. The profile definition question is whether to extend this JSON-on-classpath pattern, replace it with Java code, or promote profiles to a full database entity.

## Options Considered

### Option 1: Seed data with Java registry class

Define profiles as Java constants in a `VerticalProfileRegistry` class. Each profile is a record or method that returns the profile's configuration: enabled modules, pack references, terminology namespace, and default settings. The registry is a Spring `@Component` injected wherever profile data is needed. Profile application at provisioning time calls `registry.getProfile("accounting-za")` and applies the returned configuration to `OrgSettings`.

- **Pros:**
  - Type-safe: profile fields are Java records with compile-time validation, not stringly-typed JSON keys
  - Discoverable: IDE navigation, refactoring, and usage search all work naturally
  - Testable: unit tests construct profile records directly without parsing JSON
  - Follows the `CustomerLifecycleGuard` and `IntegrationGuardService` precedent of using Java components for configuration logic
  - No external file loading or JSON parsing at runtime -- profiles are in-memory constants
  - Profile metadata (display name, description) can be exposed via a `GET /api/profiles` endpoint by iterating the registry

- **Cons:**
  - Adding a new profile requires a code change and redeployment, not just a config file edit
  - Duplicates some information already in `accounting-za.json` (pack lists, terminology namespace)
  - Less accessible to non-Java contributors who might want to define profiles
  - The existing `accounting-za.json` file would need to be reconciled -- either migrated into the registry or kept as a parallel source of truth

### Option 2: Database entity (VerticalProfile table)

Create a `vertical_profiles` table in the public schema (not tenant-scoped) with columns for profile ID, display name, description, enabled modules, pack selections, terminology namespace, and default settings. Profiles are managed via a CRUD API accessible to platform admins. The provisioning flow reads from this table when applying a profile to a new tenant.

- **Pros:**
  - Profiles can be created, edited, and deleted at runtime without code changes or redeployment
  - Supports a future admin UI for managing profiles
  - Clean separation: profile definitions live in the database, profile application logic lives in Java
  - New verticals (e.g., "engineering-consulting-uk") can be added by platform admins without developer involvement

- **Cons:**
  - Over-engineered for 3-5 profiles: creates a new entity, repository, service, controller, migration, and global schema table for data that changes at most once per phase
  - Introduces a global-schema dependency -- profile definitions must be available before tenant schemas exist, adding a boot-order constraint
  - Requires seeding the table with initial profiles (a seeder that creates database rows), then maintaining consistency between the seeded data and whatever the admin has modified
  - A profile update does not retroactively update tenants that already applied the old version -- this creates version drift that is harder to reason about than immutable code constants
  - The existing `OrgSettings.verticalProfile` column stores a string reference to the profile -- if profiles are database entities, this becomes a foreign key relationship across schemas (public.vertical_profiles referenced from tenant.org_settings), which violates the project's schema isolation model

### Option 3: JSON config files on classpath

Extend the existing `vertical-profiles/*.json` pattern. Each profile gets a JSON file (e.g., `consulting-generic.json`, `legal-za.json`) alongside the existing `accounting-za.json`. A `VerticalProfileLoader` component reads all JSON files from the classpath directory at startup and exposes them via a `getProfile(String id)` method. Profile application at provisioning time reads from this in-memory cache.

- **Pros:**
  - Extends an existing, working pattern -- `accounting-za.json` already exists and is consumed by the pack seeder
  - JSON is human-readable and easy to diff in code review
  - Adding a new profile is a new JSON file, not a code change (though still requires redeployment)
  - Profile structure is self-documenting -- the JSON schema serves as documentation
  - Non-Java contributors can define profiles by editing JSON

- **Cons:**
  - No compile-time validation: a typo in a module ID or pack reference is only caught at runtime (or in integration tests)
  - JSON parsing adds a small amount of startup complexity and error handling (malformed JSON, missing required fields)
  - The profile data is stringly-typed -- `"enabled_modules": ["trust_accounting"]` has no relationship to the `VerticalModuleGuard`'s module ID constants
  - Two sources of truth for profile metadata: JSON files define pack lists and defaults, but the Java code must still know about module IDs for guard logic
  - The `accounting-za.json` file currently mixes profile definition (pack references, terminology) with operational data (rate card defaults with specific ZAR amounts) -- this conflation would be replicated across all profile files

### Option 4: Hybrid -- JSON files for pack/default data, Java registry for module/guard data

Keep the existing `vertical-profiles/*.json` files for pack selections, rate card defaults, tax defaults, and terminology overrides (operational configuration). Add a Java `VerticalProfileRegistry` that defines the profile metadata (ID, display name, description) and enabled modules (the security-sensitive part). The registry loads the corresponding JSON file for each profile to assemble the complete profile definition. Profile application reads from the registry (which combines both sources).

- **Pros:**
  - Security-sensitive data (enabled modules) is in type-safe Java code, not editable JSON
  - Operational data (pack lists, rate card amounts, tax rates) stays in JSON where it's already defined and easy to adjust
  - No duplication: the existing `accounting-za.json` is reused as-is, and the registry adds the module and metadata layer
  - Module IDs in the registry can reference constants shared with `VerticalModuleGuard`, ensuring consistency
  - The registry is the single entry point -- consumers don't need to know about the JSON files

- **Cons:**
  - Two locations for profile data (Java class + JSON files) -- developers must understand which data lives where
  - Slightly more complex than a pure Java registry: the registry must load and parse JSON at startup
  - If the JSON file for a profile is missing or malformed, the error is a runtime startup failure rather than a compile error
  - The split between "security-sensitive" and "operational" data may not always be clear-cut for future profile fields

## Decision

**Option 4 -- Hybrid approach with Java registry for module/guard metadata and JSON files for pack/operational data.**

## Rationale

The hybrid approach respects two competing constraints. First, module identifiers are a security boundary -- they gate access to backend endpoints via `VerticalModuleGuard` (see ADR-190). Defining these in type-safe Java code with shared constants prevents a category of bugs where a JSON typo silently disables or enables a module. Second, pack selections and rate defaults are operational configuration that changes more frequently than module definitions and benefits from the human-readable, diffable JSON format that the codebase already uses.

The existing `accounting-za.json` file is already consumed by the pack seeder and contains exactly the operational data that should stay in JSON: pack references, rate card defaults, tax defaults, and terminology namespace. This file was created in Phase 47 and has proven to work. Duplicating its contents into a Java class would create a maintenance burden without adding safety -- pack IDs are validated at application time against the actual pack seeder, not at compile time.

A pure database entity (Option 2) is over-engineered for 3-5 profiles that change at most once per phase. The project is a solo-founder platform with no admin UI for profile management, and the global-schema foreign key relationship would violate the schema isolation model established in ADR-064. A pure Java registry (Option 1) would require migrating the existing JSON file and losing the readability benefits of the JSON format for operational data. A pure JSON approach (Option 3) would put security-sensitive module definitions in a format with no compile-time validation.

The registry class also serves as the backing data for the `GET /api/profiles` endpoint, which the admin provisioning UI and frontend dropdown need. It returns profile metadata (ID, name, description, modules) while the JSON files provide the provisioning-time operational defaults that are applied once and then belong to the tenant's `OrgSettings`.

## Consequences

- **Positive:**
  - The Java side is split into two focused registries: `VerticalProfileRegistry` (reads classpath JSON, returns profile metadata for `GET /api/profiles` and provisioning) and `VerticalModuleRegistry` (defines known module IDs with name/description/status for `GET /api/modules` and guard validation). This separation keeps module security metadata (used by the guard) independent of operational profile data
  - Module identifiers are defined as Java constants shared between `VerticalModuleRegistry` and `VerticalModuleGuard`, eliminating stringly-typed mismatches
  - The existing `accounting-za.json` is reused without modification; new profiles add corresponding JSON files
  - The `GET /api/profiles` endpoint reads from the in-memory registry with zero database access
  - Profile definitions are immutable at runtime -- no concern about admin edits creating version drift across tenants
  - Adding a new profile requires one JSON file + one registry entry, with a redeployment (appropriate for a change that introduces new security boundaries)

- **Negative:**
  - Profile data lives in two locations (Java registry + JSON files), requiring developers to understand the split
  - A missing or malformed JSON file causes a startup failure rather than a compile error (mitigated by integration tests that verify all registered profiles have valid JSON files)
  - Adding a new vertical requires both a code change (registry entry) and a config file (JSON), not just one or the other

- **Neutral:**
  - The `OrgSettings.vertical_profile` column continues to store a string ID (e.g., "accounting-za") that references the registry, not a database foreign key
  - Per-tenant state (`enabled_modules`, `terminology_namespace`) is stored in `OrgSettings` as decided in the Phase 49 requirements -- the registry only defines defaults, not live tenant state
  - The approach is consistent with ADR-091's principle of keeping per-tenant configuration on `OrgSettings` while using purpose-built components for the configuration logic
