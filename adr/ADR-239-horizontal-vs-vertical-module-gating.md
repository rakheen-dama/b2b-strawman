# ADR-239: Horizontal vs. Vertical Module Gating

**Status**: Accepted

**Context**:

Phase 49 introduced a module gating system for vertical-specific features. The `VerticalModuleRegistry` maintains a static registry of module definitions, the `VerticalModuleGuard` enforces access at the service layer by checking `OrgSettings.enabled_modules` (a JSONB `List<String>`), and the frontend `ModuleGate` component conditionally renders UI based on the same list via `useOrgProfile().isModuleEnabled()`. Currently, all five registered modules — `trust_accounting`, `court_calendar`, `conflict_check`, `lssa_tariff`, and `regulatory_deadlines` — are vertical-specific. They are auto-assigned when an org selects a vertical profile (e.g., `legal-za` enables all four legal modules), and admins cannot toggle them independently in Settings. The `updateVerticalProfile()` method on `OrgSettingsService` overwrites `enabled_modules` with the profile's declared list each time a profile is selected.

Phase 62 extends this system to gate horizontal power-user features — `resource_planning`, `bulk_billing`, and `automation_builder` — that are not tied to any vertical profile. These features are complex enough that exposing them by default creates onboarding friction for small firms. Unlike vertical modules, horizontal modules are OFF by default for all profiles and are toggled individually by org admins via a new Settings > Features page. This means two categories of modules now coexist in the same system: ones managed by profile selection and ones managed by direct user action. The question is how to store, query, and toggle these two categories without introducing fragility or unnecessary infrastructure.

The key constraint is that the `VerticalModuleGuard` — and by extension every service-layer `requireModule()` call and every frontend `ModuleGate` wrapper — is category-agnostic. It checks whether a module ID exists in `enabled_modules`. It does not care whether the module was enabled by profile selection or by a Settings toggle. Any solution must preserve this single guard path to avoid duplicating enforcement logic across the backend and frontend.

**Options Considered**:

1. **Separate `enabled_horizontal_modules` column** -- Add a new JSONB column `enabled_horizontal_modules` on `OrgSettings` alongside the existing `enabled_modules`.
   - Pros:
     - Clear physical separation between vertical and horizontal module state. No risk of profile selection overwriting horizontal toggles.
     - Simple `updateVerticalProfile()` logic — it only touches `enabled_modules`, never the horizontal column.
     - Easy to query each category independently (e.g., "which horizontal modules has this org enabled?").
   - Cons:
     - Requires a new tenant migration to add the column and a GIN index. Phase 62 explicitly targets a no-migration approach since `enabled_modules` already supports arbitrary module IDs.
     - `VerticalModuleGuard` must now check two columns. Every `requireModule()` call and the `getEnabledModules()` method must merge both lists. This creates a hidden coupling — if a developer forgets to check the horizontal column, a module that is enabled in Settings will still be blocked at the service layer.
     - Frontend `OrgProfileProvider` must fetch and merge two arrays. The `isModuleEnabled()` function becomes a union check across two data sources.
     - Two sources of truth for "is this module enabled?" invites bugs. A future developer adding a new guard point might check only one column.

2. **Unified `enabled_modules` with category metadata** -- Keep the single `enabled_modules` JSONB column. Add a `category` field (`VERTICAL` or `HORIZONTAL`) to `ModuleDefinition` in `VerticalModuleRegistry`. The toggle API reads the registry to determine which modules are horizontal, validates that only horizontal IDs are being toggled, and merges the toggled set with the existing vertical modules before writing back to `enabled_modules`.
   - Pros:
     - Zero schema changes. The existing `enabled_modules` column, its GIN index, and every downstream consumer work unchanged. No migration needed.
     - Single guard path. `VerticalModuleGuard.requireModule()` continues to check one list. No conditional logic, no union merges. Frontend `isModuleEnabled()` checks one array. Every existing `ModuleGate` wrapper works without modification.
     - Category distinction lives in the registry (application code), not in the database schema. The registry is the source of truth for what a module is and how it behaves. This is the right layer for metadata.
     - The toggle API performs a partition-and-merge: read current `enabled_modules`, separate vertical IDs from horizontal IDs (using the registry), replace the horizontal subset with the request payload, concatenate with the vertical subset, write back. Profile selection does the inverse: replace the vertical subset, preserve the horizontal subset.
     - Consistent with how the codebase handles other categorical distinctions on flat data — `entity_type` and `work_type` are VARCHAR columns with service-layer validation per vertical (ADR-238), not separate columns per category.
   - Cons:
     - The merge logic in `updateVerticalProfile()` must be updated to preserve horizontal modules when a profile change occurs. If this merge is implemented incorrectly, a profile change could wipe horizontal toggles. This is a one-time implementation risk mitigated by a focused integration test.
     - Querying "which horizontal modules are enabled?" requires joining with the registry at the application layer. There is no database-level distinction. In practice this is never needed for a database query — the only consumer is the Settings UI, which already loads the registry.
     - A developer unfamiliar with the system might not realize that `enabled_modules` contains both categories. The `ModuleCategory` enum and clear naming in the registry mitigate this.

3. **Feature flags table** -- Create a new `feature_flags` entity and table with columns like `(id, feature_key, enabled, created_at, updated_at)`. Horizontal modules become feature flags, separate from the module system entirely.
   - Pros:
     - Clean conceptual separation. Modules are vertical infrastructure; feature flags are org-level toggles. Different tables for different concerns.
     - Feature flags could extend beyond module gating in the future (A/B testing, gradual rollouts, per-plan feature tiers).
     - Standard pattern in SaaS products. Well-understood by developers.
   - Cons:
     - Requires a new tenant migration (`CREATE TABLE feature_flags`), a new entity, a new repository, a new service, and a new controller. This is significant infrastructure for 3 boolean toggles.
     - Introduces a second gating mechanism. Controllers and frontend components would need to check the module system for vertical features and the feature flag system for horizontal features. Every guard point must know which system to consult. This doubles the surface area for enforcement bugs.
     - The frontend would need a second provider/hook alongside `useOrgProfile().isModuleEnabled()`. Components wrapping horizontal features would use a different gating API than components wrapping vertical features, even though the UX is identical (show or hide).
     - Future modules that blur the line between vertical and horizontal (e.g., a module that is auto-enabled for one profile but toggleable for others) would require coordination between both systems.
     - Over-engineers the problem. The existing `enabled_modules` column is a JSONB list that already stores arbitrary string IDs. A feature flag table adds a new entity, migration, repository, service, and controller to achieve what one additional field on `ModuleDefinition` provides.

**Decision**: Option 2 -- Unified `enabled_modules` with category metadata on `ModuleDefinition`.

**Rationale**:

The decisive factor is the single guard path. `VerticalModuleGuard.requireModule("resource_planning")` and `VerticalModuleGuard.requireModule("trust_accounting")` must work identically — both check the same `enabled_modules` list. Splitting storage (Option 1) or mechanism (Option 3) means every guard point must be aware of the split, which is a maintenance burden that scales with the number of guarded endpoints. Today there are ~15 service methods behind module guards across trust accounting, court calendar, conflict check, and tariff services. Phase 62 adds guards to resource allocation, capacity, utilization, billing run, and automation controllers. Ensuring all ~25+ guard points correctly merge two data sources is strictly harder than ensuring they all check one list.

The no-migration constraint reinforces this choice. `OrgSettings.enabled_modules` is a JSONB `List<String>` with a GIN index, added by `V75__add_vertical_modules.sql`. It already stores arbitrary module IDs. Adding `"resource_planning"` to the list is indistinguishable from adding `"trust_accounting"` at the storage level. The category distinction is a metadata concern — it determines which UI renders the toggle and which code path writes the value — not a storage concern. Placing category metadata on `ModuleDefinition` in the `VerticalModuleRegistry` keeps it in the registry, which is already the single source of truth for module names, descriptions, status, default profiles, and nav items.

The merge logic in `updateVerticalProfile()` is the only new complexity. Today, profile selection overwrites `enabled_modules` entirely with the profile's declared list. After this change, it must partition the current list by category, replace only the vertical subset, and preserve the horizontal subset. Similarly, the new `PUT /api/settings/modules` endpoint must replace only the horizontal subset and preserve the vertical subset. Both operations use the registry to classify module IDs, making the registry the authoritative boundary between categories. A targeted integration test — "enable `resource_planning`, then change vertical profile, assert `resource_planning` is still enabled" — covers the critical merge path.

This approach is consistent with the codebase's preference for flat storage with service-layer categorization. `Invoice.paymentDestination` is a VARCHAR validated against vertical-specific allowed values (ADR-238 rationale). `Task.type` is a VARCHAR with per-vertical validation. In each case, the database stores a simple value and the application layer provides categorical semantics. `enabled_modules` follows the same pattern: the database stores a flat list of strings, and the registry provides the category for each.

**Consequences**:

- `ModuleDefinition` gains a `category` field of type `ModuleCategory` (`VERTICAL` or `HORIZONTAL`). Existing modules are `VERTICAL`. The three new modules are `HORIZONTAL`. The `ModuleCategory` enum lives in the `verticals` package alongside `VerticalModuleRegistry`.
- `VerticalModuleGuard` is unchanged. `requireModule()` and `isModuleEnabled()` continue to check `enabled_modules` without knowledge of categories. This is the key benefit — zero changes to the enforcement layer.
- `ModuleGate` (frontend) is unchanged. `useOrgProfile().isModuleEnabled()` checks the same `enabledModules` array. No new hooks, providers, or gating components.
- `OrgSettingsService.updateVerticalProfile()` must be updated to preserve horizontal modules during profile changes. The current implementation overwrites `enabled_modules` with the profile's list. After this change, it must: (1) read current `enabled_modules`, (2) filter to only horizontal IDs using the registry, (3) concatenate with the profile's vertical module list, (4) write the merged result. This is the highest-risk change and must be covered by an integration test.
- A new `PUT /api/settings/modules` endpoint accepts a list of horizontal module IDs and performs the inverse merge: preserve vertical IDs, replace horizontal IDs with the request payload. It validates that all submitted IDs exist in the registry with `category: HORIZONTAL`, rejecting any attempt to toggle vertical modules via this endpoint.
- No database migration is required. `enabled_modules` already supports storing any module ID strings. The GIN index covers lookups regardless of how many IDs are in the list.
- The Settings > Features page queries `GET /api/settings/modules`, which filters the registry to `HORIZONTAL` modules and joins with the org's `enabled_modules` to determine toggle state. Vertical modules are never shown on this page.
- Negative consequence: the `enabled_modules` column now contains a mix of profile-managed and user-managed entries. There is no database-level mechanism to prevent a raw SQL update from inserting a vertical module ID that contradicts the org's profile. This is acceptable because all writes go through the service layer, which enforces the partition. Direct database manipulation is not a supported operation.
- Related: [ADR-238](ADR-238-entity-type-varchar-vs-enum.md) (flat storage with service-layer categorization pattern)
