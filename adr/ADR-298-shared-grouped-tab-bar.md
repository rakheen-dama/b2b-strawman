# ADR-298: Shared GroupedTabBar Pattern Across Entity Detail Pages

**Status**: Accepted

**Context**:

Phase 73 introduced `GroupedTabBar` (`frontend/components/projects/grouped-tab-bar.tsx`) to collapse 21 flat matter-detail tabs into 7 logical groups with dropdown sub-navigation. The component is entity-agnostic by design -- it accepts a `groups: TabGroup[]` prop and renders them with keyboard navigation, active-tab indication, and module-gating support. However, it lives in the `components/projects/` directory and its companion constant (`TAB_GROUPS` in `lib/constants/tab-groups.ts`) is matter-specific.

Phase 77 redesigns the customer detail page using the same grouped-tab pattern. The customer page currently has 11 flat tabs in `CustomerTabs`, and with the addition of Details, Fields, Tags, and Overview tabs in Phase 77, it will have 15 tab IDs across 6 groups. This exceeds the >8 visible tab threshold established in ADR-287 as the point where grouping becomes necessary.

The question is how to share the `GroupedTabBar` component and its tab-group configuration infrastructure across entity detail pages (matters, customers, and future entities like invoices or proposals).

**Options Considered**:

1. **Extract to shared location with parameterised constants (CHOSEN)** -- Move `GroupedTabBar` from `components/projects/` to `components/shared/`. Extract shared types (`TabGroup`, `TabDefinition`) and utility functions (`resolveTabFromUrl`, `getGroupForTab`, `buildTabIdToGroupMap`) to `lib/constants/tab-group-types.ts`. Rename `tab-groups.ts` to `project-tab-groups.ts` and create a parallel `customer-tab-groups.ts`. Each entity defines its own `TabGroup[]` constant following the shared shape. The `GroupedTabBar` component itself remains unchanged.
   - Pros:
     - **Single component, multiple configurations.** The rendering logic is shared -- only the group definitions differ per entity. Bug fixes and accessibility improvements to `GroupedTabBar` benefit all consumers automatically.
     - **Type safety.** Both entity configurations share the `TabGroup` and `TabDefinition` interfaces. A breaking change to the tab group shape is caught at compile time across all consumers.
     - **Clear ownership.** Shared component lives in `components/shared/`, entity-specific configurations live in `lib/constants/{entity}-tab-groups.ts`. No ambiguity about where to add a new entity's tab groups.
     - **Utility reuse.** `resolveTabFromUrl` is already parameterised by `groups` -- it works for any `TabGroup[]` without modification. `getGroupForTab` needs only to accept a group map parameter instead of using a module-level constant.
     - **Low migration cost.** One file move, one file rename, one new file. Two import path updates (one in `ProjectTabs`, one barrel re-export if desired).
   - Cons:
     - **Import churn.** Every file that imports from `components/projects/grouped-tab-bar` or `lib/constants/tab-groups` needs a path update. Currently there are only 2 consumers (`ProjectTabs` and the new `CustomerGroupedTabs`), so the churn is minimal.
     - **Shared component evolution risk.** A change requested by the customer page team (e.g., a different animation) could affect the matter page. Mitigated: the component is prop-driven with no entity-specific logic. Visual customisation (if ever needed) can be added via optional props.

2. **Copy-paste the component into customers domain** -- Duplicate `GroupedTabBar` into `components/customers/customer-grouped-tab-bar.tsx` with customer-specific modifications. Each entity owns its own copy.
   - Pros:
     - **Zero coupling between entities.** Changes to the customer tab bar cannot affect the matter tab bar. Each page evolves independently.
     - **No shared directory convention needed.** Components stay in their entity directories.
   - Cons:
     - **Duplication.** The `GroupedTabBar` component is ~120 lines of non-trivial UI logic (keyboard navigation, dropdown management, Framer Motion animation, active-tab indication). Duplicating it means bug fixes must be applied in multiple places. This is the exact anti-pattern that led to the original 11-flat-tab inconsistency between matters and customers.
     - **Divergence risk.** Over time, the copies will drift. One gets an accessibility fix, the other does not. One gets a visual refinement, the other does not. The user experience becomes inconsistent across entity detail pages.
     - **Scales poorly.** If invoices, proposals, or other entity detail pages adopt the pattern, each would need another copy. At 4+ copies, maintenance becomes untenable.

3. **Keep in projects directory, import cross-domain** -- Leave `GroupedTabBar` in `components/projects/` and import it from `components/customers/customer-grouped-tabs.tsx`. No file moves.
   - Pros:
     - **Zero code changes.** No file moves, no import updates, no new directories.
     - **Pragmatic.** Gets the job done with minimal churn.
   - Cons:
     - **Misleading ownership.** A component in `components/projects/` is semantically owned by the projects domain. Having the customers domain import from projects creates a confusing dependency direction -- customers depending on projects for shared UI infrastructure.
     - **Discovery problem.** A developer looking for shared UI components would not look in `components/projects/`. The component is effectively "hidden" in a domain-specific directory despite being domain-agnostic.
     - **Sets a bad precedent.** Other shared patterns (e.g., entity header cards, overflow menus) would also accumulate in `components/projects/` simply because matters was the first entity to need them. The projects directory becomes a de-facto "shared" dump.

4. **Publish as a Shadcn-style UI component** -- Move to `components/ui/grouped-tab-bar.tsx`, alongside Shadcn primitives like `button.tsx`, `dropdown-menu.tsx`, etc.
   - Pros:
     - **Consistent with Shadcn conventions.** UI components live in `components/ui/`.
     - **Easy discovery.** Developers know to look in `components/ui/` for reusable primitives.
   - Cons:
     - **Shadcn `components/ui/` is for design-system primitives.** Files in that directory are typically generated by `npx shadcn add` and follow a specific pattern (forwardRef wrappers around Radix primitives, minimal business logic). `GroupedTabBar` has business logic (group visibility filtering, active-tab resolution, keyboard navigation) that goes beyond a design-system primitive. Placing it alongside `button.tsx` and `input.tsx` blurs the line between generic UI atoms and application-level UI patterns.
     - **Risk of accidental overwrite.** Running `npx shadcn add` or updating Shadcn components could conflict with custom files in the `ui/` directory. Shadcn's CLI expects to own that directory.

**Decision**: Option 1 -- Extract to `components/shared/` with parameterised constants in `lib/constants/`.

**Rationale**:

The `GroupedTabBar` component is entity-agnostic infrastructure that happens to have been first implemented for the matter detail page. Its API -- `groups: TabGroup[]`, `activeTab: string`, `onTabChange: (tabId: string) => void` -- has no matter-specific logic. Moving it to `components/shared/` makes its cross-domain nature explicit and prevents the misleading dependency direction of Option 3.

The copy-paste approach (Option 2) was rejected because `GroupedTabBar` contains non-trivial interaction logic (keyboard navigation, dropdown state management, Framer Motion animation) that is costly to maintain in duplicate. The matters and customers pages should have identical tab-bar behaviour -- inconsistency would be a regression, not a feature.

The Shadcn UI directory (Option 4) was rejected because `GroupedTabBar` is an application-level pattern, not a design-system atom. It composes Shadcn primitives (`DropdownMenu`, `Badge`) but is itself a higher-level abstraction with business-relevant concepts (tab groups, module gating, URL resolution). `components/shared/` is the appropriate home for such patterns.

The convention established by this ADR: **entity detail pages with >6 visible tabs should use `GroupedTabBar` with an entity-specific `TabGroup[]` configuration**. This threshold is lower than the >8 threshold in ADR-287 because the addition of Details, Fields, and Overview tabs to any entity pushes the count past 6 quickly. The component is available in `components/shared/` for any future entity that needs it.

**Consequences**:

- Positive:
  - Single source of truth for grouped-tab rendering logic. Bug fixes and accessibility improvements benefit all entity detail pages.
  - Clear directory structure: `components/shared/` for cross-domain UI patterns, `lib/constants/{entity}-tab-groups.ts` for entity-specific configurations.
  - `resolveTabFromUrl` and `getGroupForTab` are fully parameterised -- any entity's tab groups work without modification.
  - Future entity detail pages (invoices, proposals) can adopt the pattern by defining a `TabGroup[]` constant and wiring `GroupedTabBar`.

- Negative:
  - Import path changes for existing `ProjectTabs` consumer. This is a one-time cost affecting one file.
  - `components/shared/` is a new directory convention. It must not become a dumping ground -- only components with genuine cross-domain usage belong here. `GroupedTabBar` qualifies because two entity detail pages (matters, customers) use it.

- Neutral:
  - The `GroupedTabBar` component source is unchanged. Only its file location moves. No functional changes, no new props, no visual changes.
  - The `TabGroup` and `TabDefinition` interfaces are extracted to `tab-group-types.ts`. Both `project-tab-groups.ts` and `customer-tab-groups.ts` import from the same shared types file.
  - The Framer Motion `layoutId="grouped-tab-indicator"` animation is shared across all `GroupedTabBar` instances. If two `GroupedTabBar` components rendered on the same page (unlikely but theoretically possible), they would share the animation -- this is harmless since only one entity detail page renders at a time.

- Related: [ADR-287](ADR-287-grouped-tabs-dense-navigation.md) (original decision to use grouped tabs for dense navigation), [ADR-299](ADR-299-header-card-entity-detail-layout.md) (header card + grouped tabs as the standard entity detail layout)
