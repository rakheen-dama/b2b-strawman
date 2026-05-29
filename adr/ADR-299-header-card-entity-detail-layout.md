# ADR-299: Header Card + Grouped Tabs as the Standard Entity Detail Layout

**Status**: Accepted

**Context**:

Kazi's entity detail pages (matter, customer, and future entities like invoice, proposal) need a consistent layout pattern. The history of the matter detail page illustrates the problem of not having one:

- **Pre-Phase 73**: Single vertical column. Header, custom fields, tags, 21 flat tabs, tab content stacked top to bottom. Custom fields pushed the tab bar below the fold. 7+ action buttons in the header row caused squeezing and wrapping.
- **Phase 73 (ADR-286, ADR-287)**: Sidebar + grouped tabs. 280px collapsible sidebar for identity, metadata, custom fields, tags. Grouped tab bar in the main content area. Solved the below-the-fold and squeezing problems.
- **Post-Phase 73 refinement**: Sidebar replaced with a compact `MatterHeaderCard`. Custom fields and tags moved to dedicated Details and Fields tabs within the grouped tab bar. The sidebar's scroll independence advantage was traded for a simpler layout with less component complexity and better mobile behaviour.

The customer detail page currently uses the pre-Phase 73 pattern: vertical stack with inline metadata, flat tabs, and an action button row. Phase 77 will restructure it. The question is which layout pattern to adopt -- and whether to establish it as the standard for all primary entity detail pages.

**Options Considered**:

1. **Header card + grouped tabs (CHOSEN)** -- A compact `Card` component at the top containing entity identity (name, status badges, contact/context info, primary action, overflow menu). Below the card: the `GroupedTabBar` with entity-specific tab group configuration. Entity metadata (address, custom fields, tags) lives inside dedicated tabs (Details, Fields) rather than inline above the tab bar. One layout, one scroll context.
   - Pros:
     - **Tab bar is always above the fold.** The header card is compact (5 rows max, ~120px). The tab bar renders immediately below. No metadata wall pushing tabs down.
     - **Simple component tree.** No grid layout, no sidebar collapse state, no Sheet for mobile, no localStorage persistence. The page is a single vertical column: back link -> card -> tabs -> content. Server component renders everything inline.
     - **Excellent mobile behaviour.** A single column with a compact card adapts naturally to all viewport widths. No sidebar-to-sheet transition, no collapse toggle, no breakpoint-dependent layout changes.
     - **Smart primary action.** The card surfaces the single most relevant action for the entity's current state. This is more intentional than a row of 7 buttons where the user must scan to find the right one.
     - **Consistent pattern.** Both matters and customers use the same layout. Future entity detail pages (invoices, proposals) adopt the same pattern. Users learn one layout, not a different one per entity type.
     - **Metadata is not lost -- it's organised.** Address, custom fields, and tags move to dedicated tabs, not hidden behind a collapse. The Details tab is always one click away. This trades "always visible" for "logically grouped and never below the fold."
   - Cons:
     - **Metadata is not persistently visible.** In the sidebar layout, custom fields were visible alongside tab content. In the header card layout, viewing custom fields requires switching to the Details or Fields tab. A practitioner who frequently references custom fields (case number, court name) must navigate away from their current tab.
     - **One click to reach metadata.** Compared to the sidebar (zero clicks, always visible), the Details tab requires one click. Compared to the pre-Phase 73 layout (scroll up, zero clicks), it also requires one click.
     - **No scroll independence.** The header card, tab bar, and tab content share a single scroll context. Scrolling down in tab content scrolls the header card off screen. Mitigated: the header card is compact enough (~120px) that it scrolls off quickly, and the tab bar (sticky or near-top) remains accessible.

2. **Sidebar + grouped tabs** -- The Phase 73 original pattern. 280px fixed-width collapsible sidebar for identity, metadata, custom fields, tags. Grouped tab bar in the main content area. CSS Grid layout with independent scroll contexts.
   - Pros:
     - **Scroll independence.** Sidebar and main content scroll separately. Custom fields remain visible while scrolling tab content. This was the core UX win cited in ADR-286.
     - **Custom fields always visible.** No tab switching needed to see field values.
     - **Power-user density.** More information visible simultaneously on wide screens.
   - Cons:
     - **Component complexity.** CSS Grid shell, sidebar collapse state in localStorage, responsive Sheet on mobile, collapse toggle button, hydration layout shift. The matter detail page's post-73 refactoring removed the sidebar precisely because this complexity was not justified by the UX benefit for most users.
     - **280px constraint on custom fields.** Long dropdown values and textarea fields render poorly at 280px. Required expand-on-demand affordances for wider content.
     - **Mobile regression.** Below lg (1024px), the sidebar becomes a Sheet overlay. Users on tablets must tap a button to see metadata. The header card layout shows metadata (via Details tab) with the same number of taps but without the Sheet overlay cognitive load.
     - **Not suitable for all entities.** The sidebar pattern assumes rich, frequently-referenced metadata. Customers have less sidebar-worthy metadata than matters (no description, no due date, no priority). An invoice detail page would have even less. Forcing a sidebar on entities with sparse metadata creates awkward empty space.
     - **Lesson learned from post-73.** The matter detail page started with a sidebar and moved away from it. The sidebar's scroll independence benefit was outweighed by its complexity cost and mobile UX regression. Repeating the pattern for customers would repeat the same evolution cycle.

3. **Full-width stacked layout (current / pre-Phase 73)** -- Keep everything in a single vertical column. Metadata inline above tabs, flat tab bar, tab content below.
   - Pros:
     - **No migration needed.** The current layout already works this way.
     - **Simple.** No new components, no layout changes, no import restructuring.
   - Cons:
     - **The problems that triggered Phase 73.** Tab bar below the fold when custom fields expand. Action button row wraps and clips on narrow viewports. 11+ flat tabs overflow horizontally. No content hierarchy. These are the exact problems Phase 77 is designed to solve.
     - **Inconsistency.** The matter detail page has been redesigned. Keeping the customer detail page on the old pattern creates an inconsistent user experience between the two most-visited entity detail pages.

4. **Full-width card with collapsible metadata section** -- A full-width card at the top with entity identity. Metadata (address, custom fields, tags) in a collapsible accordion section within the card. Tabs below the card.
   - Pros:
     - **Metadata accessible without tab switching.** Click the accordion to expand. Collapse to see tabs.
     - **Simpler than a sidebar.** No grid layout, no Sheet on mobile, no collapse state persistence.
   - Cons:
     - **Accordion state management.** Users must remember to collapse the metadata section after checking a field value. If left expanded, it pushes tabs down -- the same problem as the current layout, just wrapped in an accordion.
     - **Visual noise.** An accordion trigger in the header card adds UI chrome. The card becomes taller and more complex. This works against the "compact header card" goal.
     - **Neither here nor there.** It is a compromise between "always visible" (sidebar) and "logically separated" (tabs) that achieves neither well. The metadata is not always visible (collapsed by default) and not logically separated (inline in the card, not in a dedicated tab).

**Decision**: Option 1 -- Header card + grouped tabs as the standard layout for all primary entity detail pages.

**Rationale**:

The core question is whether the benefit of persistently visible metadata (sidebar, Option 2) justifies the component complexity, mobile UX regression, and 280px constraint. The answer, validated by the post-Phase 73 matter detail page evolution, is no.

The sidebar layout solved the "tab bar below the fold" problem but introduced a new set of problems: CSS Grid complexity, localStorage-driven hydration layout shift, Sheet overlay on mobile, and 280px field rendering constraints. The matter detail page migrated away from the sidebar after discovering these costs in practice. Phase 77 benefits from that lesson learned and goes directly to the header card pattern.

The header card layout solves the same core problems as the sidebar -- tab bar above the fold, action button consolidation, content hierarchy -- with a simpler component tree. The trade-off is that custom fields require a tab switch to view. This is acceptable because:

1. **Custom field access is not the primary use case.** Practitioners visit the customer detail page to check status (header card), manage projects (Work tab), review compliance (Compliance tab), or handle finances (Finance tab). Custom field reference is a secondary activity that happens occasionally, not on every page visit.
2. **The Details tab is one click away.** The grouped tab bar puts Details as the first group (leftmost position). It is highly discoverable and fast to reach.
3. **Mobile users benefit significantly.** The header card layout requires no Sheet overlay, no collapse toggle, no breakpoint-dependent grid changes. It works identically on desktop and mobile.

The convention established by this ADR:

- **Entity detail pages** (matter, customer, invoice, proposal) use header card + grouped tabs.
- **Settings pages** (org settings, user settings) use sidebar navigation -- this is a different use case (page-level navigation, not content tabs) where a persistent sidebar is appropriate.
- **Dashboard pages** (org dashboard, reporting) use full-width layouts with card grids -- no entity identity to display, no tab-heavy content.

**Consequences**:

- Positive:
  - Consistent layout across all entity detail pages. Users learn one pattern.
  - Simpler component tree than the sidebar layout. No CSS Grid, no collapse state, no Sheet on mobile.
  - Tab bar is always above the fold. The compact header card (~120px) does not compete with tabs for viewport space.
  - Smart primary action in the header card surfaces the most relevant action for the entity's lifecycle state.
  - Mobile behaviour is natural -- single column, no breakpoint-dependent layout changes.

- Negative:
  - Custom fields, tags, and address metadata require a tab switch (Details or Fields tab) to view. They are not persistently visible. Practitioners who frequently reference custom fields will notice the extra click.
  - No scroll independence between header card and tab content. Scrolling down in a long tab panel scrolls the header card off screen. The compact card height (~120px) mitigates this -- it scrolls off quickly and does not consume significant viewport space.
  - The header card pattern is less information-dense than the sidebar on wide screens. A 1920px desktop with a sidebar shows metadata + tab content simultaneously. The header card uses the full width for tab content, with metadata in a separate tab. This is a conscious trade-off of density for simplicity.

- Neutral:
  - The `MatterHeaderCard` and `ClientHeaderCard` are separate components (not a shared abstraction) because entity identity content differs significantly. Matters show project name, status, work type, reference number, customer link. Customers show customer name, lifecycle badges, contact info, engagement count. Sharing a layout component would require so many conditional branches that it would be harder to maintain than two focused components.
  - The pattern does not preclude a future "pinned fields" feature where users can choose 2-3 custom fields to display in the header card. This would require backend persistence (user preference) and is out of scope for Phase 77, but the header card layout can accommodate it by adding a small field-value row below the badges.
  - Each entity's overflow menu (`OverflowActionsMenu` for matters, `ClientOverflowMenu` for customers) is entity-specific. The pattern (DropdownMenu with MoreHorizontal trigger, gated items, destructive items at bottom with red text) is consistent, but the menu contents differ per entity.

- Related: [ADR-286](ADR-286-sidebar-layout-entity-detail.md) (sidebar layout -- superseded by the header card pattern for entity detail pages; sidebar remains appropriate for settings pages), [ADR-287](ADR-287-grouped-tabs-dense-navigation.md) (grouped tabs -- complementary, used in both sidebar and header card layouts), [ADR-298](ADR-298-shared-grouped-tab-bar.md) (shared GroupedTabBar extraction)
