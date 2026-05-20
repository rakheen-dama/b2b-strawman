# Phase 73 --- Matter Detail Page Redesign (Sidebar + Grouped Tabs)

Phase 73 restructures the matter detail page (`/org/[slug]/projects/[id]`) from a vertical-stack layout to a sidebar + main content layout. This is a **frontend-only** phase --- no backend changes, no migrations, no new API endpoints. The server component in `page.tsx` continues to fetch all the same data; only the rendering and component composition change.

The 21 flat tabs collapse into 6 logical groups with dropdown sub-navigation. Custom fields, tags, and matter identity move into a fixed-width collapsible sidebar. The Overview tab becomes a single-screen KPI dashboard. Action buttons relocate to the sidebar footer and a compact overflow menu.

**Architecture doc**: `architecture/phase73-matter-detail-redesign.md`

**ADRs**: [ADR-286](../adr/ADR-286-sidebar-layout-entity-detail.md) (sidebar layout), [ADR-287](../adr/ADR-287-grouped-tabs-dense-navigation.md) (grouped tabs)

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 532 | Layout Shell + Sidebar | Frontend | -- | L | 532A, 532B | |
| 533 | Grouped Tab Bar | Frontend | -- | M | 533A, 533B | |
| 534 | Overview Tab Redesign | Frontend | 532 | M | 534A | |
| 535 | Action Button Relocation | Frontend | 532 | M | 535A | |
| 536 | Responsive Behaviour + Polish | Frontend | 532, 533 | M | 536A | |
| 537 | QA Testplan Updates | Frontend | 532-536 | S | 537A | |

## Dependency Graph

```
532A (Layout Shell)          533A (GroupedTabBar Component)
     |                            |
     v                            v
532B (Sidebar Extraction)    533B (ProjectTabs Integration)
     |                            |
     +------+------+              |
     |      |      |              |
     v      v      |              |
  534A   535A      |              |
  (KPI   (Action   |              |
  Dash)  Reloc.)   |              |
     |      |      |              |
     +------+------+--------------+
                   |
                   v
                536A (Responsive + Polish)
                   |
                   v
                537A (QA Testplan Updates)
```

**Parallel tracks**:
- 532 (Layout Shell + Sidebar) and 533 (Grouped Tab Bar) have no shared dependencies --- can start immediately in parallel.
- After 532: slices 534A and 535A can run in parallel (both depend only on 532).
- 536A runs after 532 and 533 are both complete.
- 537A runs last after all other slices are complete.

## Implementation Order

### Stage 1: Foundation (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 532 | 532A | `MatterDetailLayout` CSS Grid shell + `SidebarCollapseToggle` + `--sidebar-width` CSS variable. Foundation for the two-column layout. | **Done** (PR #1339) |
| 1b | Epic 532 | 532B | `MatterSidebar` component. Extract matter identity, metadata, custom fields, tags from `page.tsx` header into sidebar. Wire `MatterDetailLayout` wrapper in `page.tsx`. Depends on 532A. |
| 1c | Epic 533 | 533A | `GroupedTabBar` component with dropdown sub-navigation, keyboard nav, URL state resolution. Fully independent of 532. |
| 1d | Epic 533 | 533B | Integrate `GroupedTabBar` into `ProjectTabs`, replacing flat tab triggers. Depends on 533A. |

### Stage 2: Content Restructure (Parallel, after 532B)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 534 | 534A | `KPIDashboard` replaces `OverviewTab` body. Depends on 532 (sidebar must exist to receive team roster and other relocated content). |
| 2b | Epic 535 | 535A | `OverflowActionsMenu` + lifecycle action in sidebar footer. Depends on 532 (sidebar footer is the target). |

### Stage 3: Polish (after Stage 1 + Stage 2)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 536 | 536A | Sheet on mobile, collapse transitions, dark mode, edge-case testing. Depends on all layout and content slices. |

### Stage 4: QA (after Stage 3)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 537 | 537A | Migrate all QA lifecycle scripts to grouped tab selectors. Re-capture screenshot baselines. Depends on all prior slices. |

### Timeline

```
Stage 1:  [532A -> 532B]  //  [533A -> 533B]      <- foundation (parallel tracks)
Stage 2:  [534A]  //  [535A]                       <- content restructure (parallel, after 532B)
Stage 3:  [536A]                                   <- responsive + polish (after all above)
Stage 4:  [537A]                                   <- QA testplan (after all above)
```

---

## Epic 532: Layout Shell + Sidebar

**Goal**: Create the two-column CSS Grid layout shell (`MatterDetailLayout`) and the sidebar component (`MatterSidebar`). Extract matter identity, metadata, custom fields, and tags from the current `page.tsx` header section into the sidebar. Implement sidebar collapse toggle with localStorage persistence.

**References**: Architecture doc Sections 11.2 (component architecture), 11.3 (layout system), 11.5 (sidebar design). [ADR-286](../adr/ADR-286-sidebar-layout-entity-detail.md).

**Dependencies**: None (first epic)

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **532A** | 532.1-532.4 | `MatterDetailLayout` CSS Grid shell, `SidebarCollapseToggle` button, `--sidebar-width` CSS variable, `ExpandableText` utility component, unit tests (~6 tests) | **Done** (PR #1339) |
| **532B** | 532.5-532.10 | `MatterSidebar` component with identity, metadata, custom fields (accordion), tags, sticky footer placeholder. Refactor `page.tsx` to wrap in `MatterDetailLayout` and distribute data to sidebar vs main. Unit tests (~6 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 532.1 | Add `--sidebar-width` CSS custom property | 532A | **Done** | Modify `frontend/app/globals.css`. Add `--sidebar-width: 280px;` inside `:root`. Place it near existing sidebar variables (lines ~124-131). Single-line addition. Pattern: follow existing `--sidebar` variable definitions in `globals.css`. |
| 532.2 | Create `SidebarCollapseToggle` component | 532A | **Done** | Create `frontend/components/projects/sidebar-collapse-toggle.tsx`. `"use client"`. Props: `collapsed: boolean`, `onToggle: () => void`, `className?: string`. Renders a `Button variant="ghost" size="icon"` with `ChevronLeft` (when expanded) or `ChevronRight` (when collapsed) from `lucide-react`. `data-testid="sidebar-collapse-toggle"`. ~30 lines. Pattern: follow `components/ui/button.tsx` for Shadcn Button usage. |
| 532.3 | Create `MatterDetailLayout` component | 532A | **Done** | Create `frontend/components/projects/matter-detail-layout.tsx`. `"use client"`. Props interface per architecture doc Section 11.2.4: `sidebar: ReactNode`, `children: ReactNode`, `defaultCollapsed?: boolean`. Internal state: `collapsed` boolean, initialised from `localStorage` key `kazi-matter-sidebar-collapsed` on mount (fallback to `defaultCollapsed ?? false`). CSS Grid: `grid min-h-0`, `collapsed ? "grid-cols-[0_1fr]" : "grid-cols-[var(--sidebar-width)_1fr]"`. Sidebar slot: `overflow-y-auto overflow-x-hidden border-r border-slate-200 dark:border-slate-800`, `transition-[grid-template-columns] duration-200 ease-in-out` on the grid container. Main slot: `min-w-0 overflow-y-auto`. When `collapsed === true`, render `SidebarCollapseToggle` as a floating button at `absolute top-4 left-4 z-10` inside main area. `data-testid="matter-detail-layout"`. ~80 lines. Pattern: follow `components/desktop-sidebar.tsx` for sidebar patterns, use `cn()` from `@/lib/utils`. |
| 532.4 | Create `ExpandableText` utility component + unit tests for 532A | 532A | **Done** | Create `frontend/components/ui/expandable-text.tsx`. `"use client"`. Props: `text: string | null | undefined`, `lineClamp: number (default 2)`, `className?: string`. Renders text with `line-clamp-{N}` by default, "Show more" / "Show less" toggle. `data-testid="expandable-text"`. ~40 lines. Tests: Create `frontend/__tests__/components/projects/matter-detail-layout.test.tsx` (~6 tests): (1) MatterDetailLayout renders sidebar and main slots, (2) MatterDetailLayout respects defaultCollapsed, (3) SidebarCollapseToggle renders correct icon for expanded state, (4) SidebarCollapseToggle renders correct icon for collapsed state, (5) ExpandableText truncates long text, (6) ExpandableText shows "Show more" toggle. Pattern: follow `__tests__/components/projects/overview-tab-setup.test.tsx` for test structure. Use `@testing-library/react` + `happy-dom`. |
| 532.5 | Create `MatterSidebar` component | 532B | | Create `frontend/components/projects/matter-sidebar.tsx`. `"use client"`. Props interface per architecture doc Section 11.2.4: `project`, `customers`, `slug`, `canEdit`, `canManage`, `isAdmin`, `isOwner`, `fieldDefinitions`, `fieldGroups`, `groupMembers`, `projectTags`, `allTags`, `collapsed`, `onCollapsedChange`. Renders 5 sections: (A) Matter identity --- `h1` with `line-clamp-3 text-lg font-semibold`, status `Badge` (reuse `PROJECT_STATUS_BADGE` map from page.tsx), `ExpandableText` for description. (B) Key metadata --- compact key-value list with `text-sm` labels: client name (Link), reference number (`code` chip), work type, priority (Badge), due date (red if overdue with AlertTriangle), created date. (C) Custom fields --- render `CustomFieldSection` within Shadcn `Accordion` per field group, first group expanded by default, `FieldGroupSelector` at bottom. (D) Tags --- render `TagInput` at sidebar width. (E) Sticky footer placeholder (div with `sticky bottom-0 border-t border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950`) --- lifecycle action will be wired in Epic 535. Collapse toggle at top-right of sidebar header calling `onCollapsedChange`. `data-testid="matter-sidebar"`. ~200 lines. Pattern: follow `CustomFieldSection.tsx` for field rendering, `TagInput` for tags, `page.tsx` lines 120-128 for `PROJECT_STATUS_BADGE` map (extract to shared constant). Import `Accordion, AccordionContent, AccordionItem, AccordionTrigger` from Shadcn. |
| 532.6 | Extract `PROJECT_STATUS_BADGE` to shared constant | 532B | | The `PROJECT_STATUS_BADGE` map is currently defined inline in `page.tsx` (lines ~120-128). Extract to `frontend/lib/constants/project-status.ts` so both `page.tsx` and `MatterSidebar` can import it. ~15 lines. Keep the existing usage in `page.tsx` via import. |
| 532.7 | Refactor `page.tsx` to use `MatterDetailLayout` wrapper | 532B | | Major refactor of `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`. Changes: (1) Import `MatterDetailLayout` and `MatterSidebar`. (2) Remove the header section that renders matter name, status badge, description, metadata, custom fields, and tags from the main flow. (3) Wrap the return value in `<MatterDetailLayout sidebar={<MatterSidebar ... />}>`. (4) Pass sidebar-bound data (project, customers, fieldDefinitions, fieldGroups, groupMembers, projectTags, allTags, canEdit, canManage, isAdmin, isOwner, slug) to `MatterSidebar`. (5) Keep `ProjectTabs` and all tab content panels in the main children slot. (6) Keep action buttons in the header for now (they move in Epic 535). (7) Breadcrumb moves to the top of the main content area. Verify all data passed to client components is serializable (no functions, no component references). ~100 lines changed in a 965-line file. Pattern: follow architecture doc Section 11.2.5 for data flow. Read `frontend/CLAUDE.md` RSC Serialization Boundary section. |
| 532.8 | Wire custom fields in sidebar with Accordion groups | 532B | | Inside `MatterSidebar`, render each field group as an `AccordionItem`. The `fieldGroups` array provides group names; `groupMembers[groupId]` provides field definitions within each group. Use Shadcn `Accordion type="single" collapsible defaultValue={fieldGroups[0]?.id}` to keep first group open. The `CustomFieldSection` component is already a client component and renders at container width. `FieldGroupSelector` renders below the accordion. "Save Custom Fields" button: wrap custom fields in a `relative` container, save button uses `sticky bottom-0` within that container. Pattern: follow existing `CustomFieldSection` usage in `page.tsx` (search for `CustomFieldSection` in the current render). |
| 532.9 | Handle promoted fields in sidebar metadata | 532B | | The current `page.tsx` renders promoted fields (from `PROMOTED_PROJECT_SLUGS`) in the header. These should render in the sidebar metadata section (Section B). In `MatterSidebar`, iterate `fieldDefinitions` filtered by `PROMOTED_PROJECT_SLUGS`, render each as a compact key-value row in the metadata section. Import from `@/lib/constants/promoted-field-slugs`. ~20 lines within `MatterSidebar`. |
| 532.10 | Add unit tests for `MatterSidebar` | 532B | | Create `frontend/__tests__/components/projects/matter-sidebar.test.tsx` (~6 tests): (1) MatterSidebar renders project name with line-clamp, (2) renders status badge, (3) renders client name as link, (4) renders custom fields in accordion, (5) renders tags section, (6) renders sticky footer placeholder. Mock `CustomFieldSection`, `FieldGroupSelector`, `TagInput` as simple divs. Pattern: follow `__tests__/components/projects/overview-tab-setup.test.tsx`. Use `@testing-library/react` + `happy-dom`. Always add `afterEach(() => cleanup())` per `frontend/CLAUDE.md`. |

### Key Files

**Slice 532A --- Create:**
- `frontend/components/projects/matter-detail-layout.tsx`
- `frontend/components/projects/sidebar-collapse-toggle.tsx`
- `frontend/components/ui/expandable-text.tsx`
- `frontend/__tests__/components/projects/matter-detail-layout.test.tsx`

**Slice 532A --- Modify:**
- `frontend/app/globals.css` --- Add `--sidebar-width: 280px` CSS custom property

**Slice 532A --- Read for context:**
- `frontend/components/desktop-sidebar.tsx` --- Existing sidebar pattern (nav sidebar, 256px)
- `frontend/app/globals.css` --- Existing sidebar CSS variables (lines 124-131)
- `frontend/CLAUDE.md` --- RSC serialization boundary, anti-patterns

**Slice 532B --- Create:**
- `frontend/components/projects/matter-sidebar.tsx`
- `frontend/lib/constants/project-status.ts`
- `frontend/__tests__/components/projects/matter-sidebar.test.tsx`

**Slice 532B --- Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` --- Wrap in MatterDetailLayout, distribute sidebar data
- (Import update only) `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` --- Replace inline PROJECT_STATUS_BADGE with import

**Slice 532B --- Read for context:**
- `frontend/components/field-definitions/CustomFieldSection.tsx` --- Custom field rendering (reused in sidebar)
- `frontend/components/field-definitions/FieldGroupSelector.tsx` --- Field group management (reused in sidebar)
- `frontend/components/tags/TagInput.tsx` --- Tag input (reused in sidebar)
- `frontend/lib/constants/promoted-field-slugs.ts` --- Promoted fields for metadata section
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` --- Current header section to extract from (lines ~200-400)
- `frontend/__tests__/components/projects/overview-tab-setup.test.tsx` --- Test pattern reference

### Architecture Decisions

- **`MatterDetailLayout` as a client component**: The layout must manage sidebar collapse state via `useState` and `localStorage`, which requires `"use client"`. The `page.tsx` server component passes serializable data only --- `sidebar` and `children` as `ReactNode` slots (JSX is serializable across the RSC boundary).
- **localStorage for collapse state, not cookie**: This is a per-device UI preference, not a user setting. localStorage is the simplest mechanism --- no server round-trip, no API endpoint. The brief layout shift on hydration (server always renders expanded, client may collapse) is acceptable for v1.
- **CSS Grid over flexbox**: CSS Grid with `grid-template-columns` provides cleaner control over the sidebar width transition and prevents content reflow issues that occur with flexbox `flex-basis` animations.
- **`--sidebar-width` as a CSS custom property**: Defined once in `globals.css`, referenced by both the grid template and the sidebar component. Changing the width is a single-line edit.
- **`PROJECT_STATUS_BADGE` extracted to shared constant**: Currently inline in `page.tsx`. Both `page.tsx` and `MatterSidebar` need the same badge mapping. Extract to avoid duplication.
- **Accordion for custom field groups**: At 280px width, multiple field groups open simultaneously would push tags and the lifecycle action far below the fold. Accordion (`type="single"`) ensures only one group is open at a time, preserving vertical space. First group open by default.

---

## Epic 533: Grouped Tab Bar

**Goal**: Create the `GroupedTabBar` component that replaces the flat 21-tab `TabsPrimitive.List` in `ProjectTabs` with grouped dropdown navigation. Implement keyboard navigation, URL state resolution, module gating, and backward-compatible tab ID resolution.

**References**: Architecture doc Sections 11.4 (grouped tab bar design), 11.2.4 (props interfaces). [ADR-287](../adr/ADR-287-grouped-tabs-dense-navigation.md).

**Dependencies**: None. `GroupedTabBar` replaces the tab trigger row inside `ProjectTabs` and can be developed independently of the layout shell. It composes with the existing `ProjectTabs` content panels regardless of whether the sidebar exists yet.

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **533A** | 533.1-533.5 | `GroupedTabBar` component with tab group definitions, dropdown sub-navigation, keyboard navigation, URL state resolution, `?tab=members` redirect, unit tests (~10 tests) | |
| **533B** | 533.6-533.8 | Integrate `GroupedTabBar` into `ProjectTabs`, replacing flat tab triggers. Keep all content panels. Update `ProjectTabs` props interface if needed. Integration tests (~4 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 533.1 | Define tab group configuration constant | 533A | | Create `frontend/lib/constants/tab-groups.ts`. Export `TAB_GROUPS: TabGroup[]` constant matching architecture doc Section 11.4.1. Six groups: `overview` (standalone), `work` (tasks, documents, generated, staffing), `finance` (time, expenses, budget, rates, financials, statements, trust), `client` (customers, requests, customer-comments, adverse-parties), `schedule` (court-dates), `activity` (activity, audit). Export `TAB_ID_TO_GROUP_MAP: Record<string, string>` for reverse lookup. Export `MEMBERS_TAB_REDIRECT = "staffing"` for backward compatibility. Export TypeScript interfaces: `TabDefinition { id: string; label: string }`, `TabGroup { id: string; label: string; tabs: TabDefinition[]; visible: boolean }`. ~70 lines. Pattern: follow `lib/constants/promoted-field-slugs.ts` for constant export structure. |
| 533.2 | Create URL state resolution utility | 533A | | Add to `frontend/lib/constants/tab-groups.ts` (or separate `frontend/lib/tab-navigation.ts`). Export function `resolveTabFromUrl(tabParam: string | null, groups: TabGroup[]): { groupId: string; tabId: string }`. Logic: (1) null -> `{ groupId: "overview", tabId: "overview" }`, (2) `tabParam === "members"` -> resolve to `staffing`, (3) find group containing `tabParam` -> return that group + tab, (4) check if `tabParam` is a group ID -> return group + first visible sub-tab, (5) fallback to overview. Also export `getGroupForTab(tabId: string): string | null` reverse lookup. ~40 lines. |
| 533.3 | Create `GroupedTabBar` component | 533A | | Create `frontend/components/projects/grouped-tab-bar.tsx`. `"use client"`. Props interface per architecture doc Section 11.2.4: `groups: TabGroup[]`, `activeTab: string`, `onTabChange: (tabId: string) => void`. Renders: `role="tablist"` container with `data-testid="grouped-tab-bar"`. Each group renders as: if single visible sub-tab -> plain tab button (`role="tab"`, click navigates directly); if multiple visible sub-tabs -> Shadcn `DropdownMenu` with trigger (`role="tab"`, `data-testid="tab-group-{id}"`) and menu items (`role="menuitem"`, `data-testid="tab-item-{tabId}"`). Active sub-tab indication: group label shows `"{GroupLabel} . {SubTabLabel}"` when a sub-tab in that group is active. Groups with zero visible sub-tabs are not rendered. Uses `motion` for active tab underline indicator (follow existing pattern in `project-tabs.tsx`). Dropdown opens on click (not hover). ~150 lines. Pattern: follow `project-tabs.tsx` for motion underline, import `DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem` from Shadcn. |
| 533.4 | Implement keyboard navigation in `GroupedTabBar` | 533A | | Within `grouped-tab-bar.tsx`. Add keyboard event handlers per architecture doc Section 11.4.3: `ArrowRight`/`ArrowLeft` move focus between groups, `Enter`/`Space` open dropdown (or activate standalone), `ArrowDown`/`ArrowUp` navigate within dropdown, `Enter` selects sub-tab, `Escape` closes dropdown, `Tab` moves focus out. Use `useRef` array for group trigger elements. Manage `focusedGroupIndex` state. `onKeyDown` handler on the tablist container delegates to appropriate logic. ~60 lines within the component (total component stays under 220 lines). Pattern: WAI-ARIA Tabs + Menu pattern. Radix `DropdownMenu` already handles Escape and ArrowUp/Down within the dropdown content; wire the group-level ArrowLeft/Right and Enter/Space manually. |
| 533.5 | Add unit tests for `GroupedTabBar` and URL resolution | 533A | | Create `frontend/__tests__/components/projects/grouped-tab-bar.test.tsx` (~10 tests): (1) renders all visible groups, (2) hides groups with zero visible sub-tabs, (3) renders standalone tab (no dropdown) for single-sub-tab group, (4) shows active sub-tab label in group trigger ("Finance . Time"), (5) resolveTabFromUrl returns overview for null param, (6) resolveTabFromUrl maps "members" to "staffing", (7) resolveTabFromUrl finds correct group for "time", (8) resolveTabFromUrl handles group-level param ("finance" -> first sub-tab), (9) keyboard ArrowRight moves focus to next group, (10) clicking group with multiple tabs opens dropdown. Pattern: follow `__tests__/components/projects/overview-tab-setup.test.tsx`. Use `@testing-library/react` + `happy-dom`. Always `afterEach(() => cleanup())`. |
| 533.6 | Integrate `GroupedTabBar` into `ProjectTabs` | 533B | | Modify `frontend/components/projects/project-tabs.tsx`. Changes: (1) Import `GroupedTabBar` and `TAB_GROUPS` from constants. (2) Replace the `TabsPrimitive.List` section (flat tab triggers with motion underline) with `<GroupedTabBar groups={visibleGroups} activeTab={activeTab} onTabChange={handleTabChange} />`. (3) Build `visibleGroups` by filtering `TAB_GROUPS` using existing module-gating logic (`useOrgProfile()`, `useAuditTabVisible()`, etc.). Map module flags to group/tab visibility: `trust_accounting` gates `trust` and `statements` in finance group, `disbursements` gates `expenses` label change, `conflict_check` gates `adverse-parties` in client group, `court_calendar` gates `court-dates` in schedule group, `TEAM_OVERSIGHT` capability gates `audit` in activity group. (4) Keep all `TabsPrimitive.Content` blocks unchanged. (5) The `onTabChange` handler updates the URL `?tab=` param (existing `handleTabChange` logic). (6) Apply terminology rewriting via `useTerminology()` to tab labels within group definitions. ~80 lines changed in existing file. Pattern: preserve existing `TabsPrimitive.Root` and `TabsPrimitive.Content` usage; only replace the `TabsPrimitive.List` section. |
| 533.7 | Handle `?tab=members` backward compatibility | 533B | | In the `ProjectTabs` component's tab resolution logic (or in `GroupedTabBar`'s initial render), check if `searchParams.get("tab") === "members"` and redirect to `?tab=staffing`. Use `router.replace()` (from `next/navigation`) to rewrite the URL without a navigation. This preserves bookmarks and deep links to the old members tab. ~10 lines. |
| 533.8 | Add integration tests for `ProjectTabs` with `GroupedTabBar` | 533B | | Create or update `frontend/__tests__/components/projects/project-tabs.test.tsx` (~4 tests): (1) ProjectTabs renders GroupedTabBar (not flat TabsPrimitive.List), (2) module-gated tabs are hidden (e.g., trust tab hidden when trust_accounting off), (3) schedule group hidden when court_calendar off, (4) activity group renders as standalone tab when audit is hidden. Mock `useOrgProfile`, `useSearchParams`, `useTerminology`. Pattern: follow `__tests__/components/projects/overview-tab-setup.test.tsx`. |

### Key Files

**Slice 533A --- Create:**
- `frontend/lib/constants/tab-groups.ts`
- `frontend/components/projects/grouped-tab-bar.tsx`
- `frontend/__tests__/components/projects/grouped-tab-bar.test.tsx`

**Slice 533A --- Read for context:**
- `frontend/components/projects/project-tabs.tsx` --- Current flat tab implementation (understand TabId union, module-gating logic, motion underline pattern)
- `frontend/lib/org-profile.ts` --- `useOrgProfile()` hook for module flags
- `frontend/components/audit/audit-timeline-tab.tsx` --- `useAuditTabVisible()` hook
- `frontend/lib/terminology.ts` --- `useTerminology()` for label rewriting
- `frontend/lib/terminology-map.ts` --- `auditTabLabel` and other term mappings
- `adr/ADR-287-grouped-tabs-dense-navigation.md` --- Grouped tabs decision and rationale

**Slice 533B --- Modify:**
- `frontend/components/projects/project-tabs.tsx` --- Replace flat TabsPrimitive.List with GroupedTabBar

**Slice 533B --- Create:**
- `frontend/__tests__/components/projects/project-tabs.test.tsx` (or update existing)

**Slice 533B --- Read for context:**
- `frontend/components/projects/grouped-tab-bar.tsx` --- The new component being integrated (from 533A)
- `frontend/components/projects/project-tabs.tsx` --- Full file to understand all module-gating conditionals

### Architecture Decisions

- **Tab group definitions as a TypeScript constant, not React context**: Group definitions are static configuration. A context wrapper would add unnecessary re-render surface. Components import the constant directly.
- **URL state preserved, not rewritten**: `GroupedTabBar` resolves `?tab=time` to the Finance group internally. The URL stays as `?tab=time`. This preserves all existing deep links and bookmarks without migration.
- **`?tab=members` redirected to `?tab=staffing`**: The Members panel content is accessible via Work > Staffing. The redirect uses `router.replace()` for a seamless URL update without adding a history entry.
- **Radix `DropdownMenu` for sub-tab dropdowns**: Standard Shadcn primitive that handles Escape, ArrowUp/Down, outside-click dismissal. Group-level ArrowLeft/Right navigation is custom because Radix `DropdownMenu` does not manage inter-trigger focus.
- **Single-tab groups render as plain tabs**: Groups with exactly one visible sub-tab (e.g., Schedule with only Court Dates) render as a direct navigation tab, not a dropdown with one item. This avoids the "dropdown with one item" anti-pattern.
- **Motion underline preserved**: The active tab underline animation from the existing `project-tabs.tsx` is carried forward to `GroupedTabBar` for visual continuity.

---

## Epic 534: Overview Tab Redesign

**Goal**: Replace the current `OverviewTab` content with a single-screen `KPIDashboard` component. Render metric cards, compact health header, and upcoming deadlines list. Remove activity feed, task breakdown, time breakdown, team roster, and budget detail from the overview.

**References**: Architecture doc Sections 11.6 (KPI dashboard), 11.2.4 (KPIDashboardProps interface).

**Dependencies**: Epic 532 (sidebar must exist because team roster and metadata relocate to sidebar; removing them from overview without the sidebar creates a content gap).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **534A** | 534.1-534.6 | `KPIDashboard` component with metric cards grid, compact health header, upcoming deadlines list, responsive grid layout, `OverviewTab` gutted and rewired. Unit tests (~6 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 534.1 | Create `MetricCard` sub-component | 534A | | Create within `frontend/components/projects/kpi-dashboard.tsx` (not a separate file). Internal component: `MetricCard` with props per architecture doc Section 11.6.1 `MetricCard` interface: `id`, `label`, `value`, `linkTab`, `icon` (ReactNode), `visible`. Renders as `<Link href="?tab={linkTab}">` wrapping a card. Layout: icon (top-left, `size-5 text-slate-400`), value (`font-mono text-2xl font-bold tabular-nums`), label (`text-xs text-muted-foreground uppercase tracking-wider`). `data-testid="metric-card-{id}"`. ~40 lines. Pattern: follow `components/dashboard/kpi-card.tsx` for card structure, but simpler (no trend indicator for v1). |
| 534.2 | Create `KPIDashboard` component | 534A | | Create `frontend/components/projects/kpi-dashboard.tsx`. **Server component** (no `"use client"` --- renders as Link-based navigation, not onClick handlers). Props interface per architecture doc Section 11.2.4: `projectId`, `projectName`, `projectStatus`, `slug`, `canManage`, `customerName`, `customerId`, `setupStatus`, `setupSteps`, `ficaStatus`, `retentionClockStartedAt`, `retentionEndsOn`. Internal async function fetches dashboard data via existing server actions: `fetchProjectHealthDetail`, `fetchProjectTaskSummary`, `fetchProjectMemberHours`, `fetchProjectUpcomingDeadlines`. Uses `Promise.allSettled` (same pattern as current `overview-tab.tsx`). Renders: (1) Setup bar (compact, auto-hides when complete --- reuse from Phase 53 `overview-tab.tsx` setup bar logic), (2) Compact health header with `HealthBadge` and colored `border-t-4` band, (3) Metric cards grid `grid gap-4 md:grid-cols-2 lg:grid-cols-3`, (4) `FicaStatusCard` and `RetentionCard` (when customer linked, same visibility gates as current overview), (5) `UpcomingDeadlinesTile` below grid (max 5 items, "View all" links to `?tab=court-dates`). ~180 lines. Pattern: follow current `overview-tab.tsx` for data fetching pattern; significantly reduce rendering. |
| 534.3 | Define metric card configurations | 534A | | Within `kpi-dashboard.tsx`, define the 6 metric cards per architecture doc Section 11.6.1: (1) Budget consumed % --- visible when budget is set, links to `?tab=budget`, (2) Hours logged --- always visible, links to `?tab=time`, (3) Task completion % --- always visible, links to `?tab=tasks`, (4) Days to deadline --- visible when due date is set, no link, (5) Trust balance --- visible when `trust_accounting` module on, links to `?tab=trust`, (6) Outstanding invoices --- visible when `disbursements` module on, links to `?tab=statements`. Icons from `lucide-react`: `DollarSign`, `Clock`, `CheckSquare`, `Calendar`, `Shield`, `Receipt`. Module gating: use props to determine visibility (budget data presence, due date presence). Trust and disbursements gating: KPIDashboard does not have module context directly --- pass `trustEnabled` and `disbursementsEnabled` as boolean props (add to interface). ~30 lines of configuration. |
| 534.4 | Refactor `OverviewTab` to render `KPIDashboard` | 534A | | Major refactor of `frontend/components/projects/overview-tab.tsx`. Changes: (1) Remove the activity feed section (recent activity items, `getEventIcon`, activity icon map). (2) Remove the task status breakdown card (MicroStackedBar of open/in-progress/done). (3) Remove the time breakdown section (DonutChart of billable vs non-billable by member). (4) Remove the team roster section (member avatars). (5) Remove the budget detail card (progress bar with threshold). (6) Remove the unbilled time callout CTA. (7) Keep the `OverviewTabProps` interface but update it: remove `tasks` prop (no longer used in overview), remove `unbilledSummary`, remove `templateReadiness`. (8) Render `<KPIDashboard {...relevantProps} />` as the sole body content. (9) Keep `ficaStatus`, `retentionClockStartedAt`, `retentionEndsOn` props (passed through to KPIDashboard). The overview tab becomes a thin wrapper. ~150 lines reduced to ~30 lines. |
| 534.5 | Update `page.tsx` to pass updated props to `OverviewTab` | 534A | | Modify `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`. Update the `overviewPanel` construction: remove props that `OverviewTab` no longer accepts (`tasks`, `unbilledSummary`, `templateReadiness` if they were passed). Add `trustEnabled` and `disbursementsEnabled` boolean props derived from org profile module checks (these may already be computed in page.tsx for tab visibility). ~10 lines changed. |
| 534.6 | Add unit tests for `KPIDashboard` | 534A | | Create `frontend/__tests__/components/projects/kpi-dashboard.test.tsx` (~6 tests): (1) renders health header with colored band, (2) renders metric cards for always-visible metrics (hours, tasks), (3) hides budget card when no budget data, (4) hides trust card when trustEnabled is false, (5) renders upcoming deadlines list, (6) setup bar hidden when all steps complete. Note: `KPIDashboard` is a server component with async data fetching --- mock the fetch functions (`fetchProjectHealthDetail`, etc.). Pattern: follow `__tests__/components/projects/overview-tab-setup.test.tsx` and `__tests__/dashboard/project-overview.test.tsx`. |

### Key Files

**Slice 534A --- Create:**
- `frontend/components/projects/kpi-dashboard.tsx`
- `frontend/__tests__/components/projects/kpi-dashboard.test.tsx`

**Slice 534A --- Modify:**
- `frontend/components/projects/overview-tab.tsx` --- Gut current content, replace with KPIDashboard
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` --- Update overviewPanel props

**Slice 534A --- Read for context:**
- `frontend/components/projects/overview-tab.tsx` --- Current full implementation (understand data flow, what to remove)
- `frontend/components/projects/overview-health-header.tsx` --- Health header pattern (compact in sidebar)
- `frontend/components/projects/overview-metrics-strip.tsx` --- Existing metrics strip (evolving into KPI cards)
- `frontend/components/projects/upcoming-deadlines-tile.tsx` --- Reused for deadlines list
- `frontend/components/dashboard/health-badge.tsx` --- Reused in health header
- `frontend/components/compliance/FicaStatusCard.tsx` --- Preserved in KPI dashboard
- `frontend/components/legal/retention-card.tsx` --- Preserved in KPI dashboard
- `frontend/lib/actions/dashboard.ts` --- `fetchProjectHealthDetail`, `fetchProjectTaskSummary`, etc.
- `frontend/__tests__/components/projects/overview-tab-setup.test.tsx` --- Existing test pattern
- `frontend/__tests__/dashboard/project-overview.test.tsx` --- Existing test pattern

### Architecture Decisions

- **`KPIDashboard` as a server component**: Metric cards navigate via `<Link href="?tab={tab}">`, not `onClick` handlers. This avoids the client boundary and allows the dashboard to fetch its own data via server actions. No `"use client"` needed.
- **Activity feed removed from overview, not relocated**: The Activity tab already exists as a standalone tab. The overview was duplicating it. Removing it from overview reduces scroll depth without losing any functionality.
- **Team roster removed from overview**: Team information is now in the sidebar metadata (client name, assigned members) and accessible via Work > Staffing tab. The overview was showing redundant information.
- **Metric cards use Link, not onTabChange callback**: Since `KPIDashboard` is a server component, it cannot use client-side callbacks. URL-based navigation (`<Link>`) triggers a re-render that `GroupedTabBar` picks up via its `searchParams` resolution.
- **Setup bar preserved**: The setup progress bar remains in the KPI dashboard (above metrics) because it guides new matter configuration. It auto-hides when all steps are complete.

---

## Epic 535: Action Button Relocation

**Goal**: Move the primary lifecycle action to the sidebar footer and create an `OverflowActionsMenu` for remaining actions. Remove the action button cluster from the page header.

**References**: Architecture doc Sections 11.7 (action button relocation), 11.2.4 (OverflowActionsMenuProps interface).

**Dependencies**: Epic 532 (sidebar footer is the target for lifecycle action).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **535A** | 535.1-535.5 | `OverflowActionsMenu` component, lifecycle action wired in sidebar footer, action cluster removed from `page.tsx` header. Unit tests (~5 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 535.1 | Create `OverflowActionsMenu` component | 535A | | Create `frontend/components/projects/overflow-actions-menu.tsx`. `"use client"`. Props interface per architecture doc Section 11.2.4: `slug`, `projectId`, `projectName`, `projectStatus`, `canEdit`, `canManage`, `isAdmin`, `isOwner`, `templates`, `primaryCustomer`, `projectTags`. Renders Shadcn `DropdownMenu` with `MoreHorizontal` icon trigger (`data-testid="overflow-actions-trigger"`). Menu items per architecture doc Section 11.7.2: (1) Generate Document --- gated `canManage && templates.length > 0`, renders `GenerateDocumentDropdown` inline or as sub-menu, (2) Generate Statement of Account --- module-gated (disbursements), renders `GenerateStatementOfAccountAction`, (3) New Engagement Letter --- gated `canManage && customer linked && customer not offboarded`, renders `CreateProposalDialog` trigger, (4) Save as Template --- gated `canManage`, renders `SaveAsTemplateDialog` trigger, (5) Edit Matter --- gated `canEdit`, renders `EditProjectDialog` trigger, (6) Archive Matter --- gated `isAdmin`, renders archive action, (7) Delete Matter --- gated `isOwner`, renders `DeleteProjectDialog` trigger with confirmation. All permission gates must match exactly the current `page.tsx` header action cluster (lines ~350-450 in current source). `data-testid="overflow-actions-menu"`. ~120 lines. Pattern: follow Dialog Trigger Composition pattern from `frontend/CLAUDE.md` --- dialogs own their buttons. Import existing dialog components (`EditProjectDialog`, `DeleteProjectDialog`, `SaveAsTemplateDialog`, `GenerateDocumentDropdown`, `GenerateStatementOfAccountAction`, `CreateProposalDialog`). |
| 535.2 | Wire lifecycle action in sidebar footer | 535A | | Modify `frontend/components/projects/matter-sidebar.tsx`. Replace the placeholder sticky footer (from 532.5) with the actual lifecycle action button. Logic per architecture doc Section 11.7.1: `ACTIVE` -> `ProjectLifecycleActions` (Complete) or `MatterClosureAction` (Close, when legal-za closure module on), `COMPLETED` -> `MatterReopenAction`, `CLOSED` -> `MatterReopenAction`, `ARCHIVED` -> `ProjectLifecycleActions` (Restore). Render full-width `Button` in the sticky footer. Add required props to `MatterSidebarProps`: `projectStatus: ProjectStatus`, and pass the existing action component props (slug, projectId, etc.). `data-testid="sidebar-lifecycle-action"`. ~40 lines added to `MatterSidebar`. Pattern: follow existing usage of `ProjectLifecycleActions`, `MatterClosureAction`, `MatterReopenAction` in `page.tsx`. |
| 535.3 | Add `OverflowActionsMenu` to main content area | 535A | | Modify `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`. Add `<OverflowActionsMenu {...actionProps} />` at the top-right of the main content area, same row as the breadcrumb. Pass all required props (slug, projectId, projectName, projectStatus, canEdit, canManage, isAdmin, isOwner, templates, primaryCustomer, projectTags). Position: `flex items-center justify-between` wrapper around breadcrumb (left) and overflow menu (right). ~15 lines. |
| 535.4 | Remove action button cluster from `page.tsx` header | 535A | | Modify `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`. Remove the header action cluster section that currently renders: `ProjectLifecycleActions` / `MatterClosureAction` / `MatterReopenAction`, `GenerateDocumentDropdown`, `GenerateStatementOfAccountAction`, `CreateProposalDialog`, `SaveAsTemplateDialog`, `EditProjectDialog`, `DeleteProjectDialog`, and `ArchivedProjectBanner`. The `ArchivedProjectBanner` should render above the breadcrumb in the main content area (not in the sidebar). ~80 lines removed. Verify no orphaned imports after removal. |
| 535.5 | Add unit tests for `OverflowActionsMenu` | 535A | | Create `frontend/__tests__/components/projects/overflow-actions-menu.test.tsx` (~5 tests): (1) renders MoreHorizontal trigger button, (2) shows Edit Matter when canEdit is true, (3) hides Delete Matter when isOwner is false, (4) hides Generate Document when no templates, (5) shows Generate Statement when disbursements module enabled. Mock all dialog components as simple buttons. Pattern: follow `__tests__/components/projects/overview-tab-setup.test.tsx`. Always `afterEach(() => cleanup())`. |

### Key Files

**Slice 535A --- Create:**
- `frontend/components/projects/overflow-actions-menu.tsx`
- `frontend/__tests__/components/projects/overflow-actions-menu.test.tsx`

**Slice 535A --- Modify:**
- `frontend/components/projects/matter-sidebar.tsx` --- Wire lifecycle action in sticky footer
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` --- Add OverflowActionsMenu, remove header action cluster

**Slice 535A --- Read for context:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` --- Current header action cluster (lines ~350-450, understand all permission gates)
- `frontend/components/projects/project-lifecycle-actions.tsx` --- Complete/Restore lifecycle actions (141 lines)
- `frontend/components/projects/matter-closure-action.tsx` --- Close Matter action (legal-za)
- `frontend/components/projects/matter-reopen-action.tsx` --- Reopen action
- `frontend/components/projects/generate-statement-action.tsx` --- Statement generation
- `frontend/components/templates/GenerateDocumentDropdown.tsx` --- Document generation dropdown
- `frontend/components/proposals/create-proposal-dialog.tsx` --- Engagement letter dialog
- `frontend/components/templates/SaveAsTemplateDialog.tsx` --- Save as template dialog
- `frontend/components/projects/edit-project-dialog.tsx` --- Edit matter dialog
- `frontend/components/projects/delete-project-dialog.tsx` --- Delete matter dialog
- `frontend/components/projects/archived-project-banner.tsx` --- Archived banner (relocate to main area)
- `frontend/CLAUDE.md` --- Dialog Trigger Composition pattern (critical for OverflowActionsMenu)

### Architecture Decisions

- **Dialog Trigger Composition pattern for overflow menu**: Per `frontend/CLAUDE.md`, dialogs must own their trigger buttons when multiple triggers are adjacent. The `OverflowActionsMenu` renders multiple dialog triggers within a dropdown. Each dialog component (Edit, Delete, Save as Template, etc.) should accept `triggerLabel` / `triggerVariant` props and render its own button, avoiding the `asChild` Slot collision (OBS-2103).
- **Primary lifecycle action in sidebar footer, not overflow menu**: The lifecycle action (Complete/Close/Reopen/Restore) is the most important action on the page. It deserves a full-width prominent button, not a dropdown menu item. The sidebar footer provides persistent visibility without consuming main content space.
- **OverflowActionsMenu at breadcrumb level**: Positioned at the top-right of the main content area, same row as breadcrumb. This is a standard pattern (GitHub, Linear) for secondary actions on detail pages.
- **All permission gates preserved**: Every conditional rendering check from the current header action cluster is preserved identically in the new locations. No permission logic changes.
- **ArchivedProjectBanner relocates to main area**: The banner (`ArchivedProjectBanner`) renders above the breadcrumb in the main content area, not in the sidebar. It is a page-level status message, not metadata.

---

## Epic 536: Responsive Behaviour + Polish

**Goal**: Implement the Sheet-based mobile sidebar, polish the sidebar collapse/expand transition, verify dark mode for all new components, and test edge cases (very long names, many field groups, no modules).

**References**: Architecture doc Sections 11.8 (responsive behaviour), 11.5.2 (collapse behaviour), 11.8.4 (testing matrix).

**Dependencies**: Epics 532 (layout shell), 533 (grouped tab bar), 534 (overview tab), 535 (actions). Polishes all prior work.

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **536A** | 536.1-536.6 | Sheet trigger for mobile sidebar, responsive breakpoint handling, collapse/expand transition animation, dark mode verification, edge-case styling fixes, Playwright viewport tests (~4 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 536.1 | Add Sheet wrapper for mobile sidebar | 536A | | Modify `frontend/components/projects/matter-detail-layout.tsx`. Below `lg` breakpoint: hide the grid sidebar column (`grid-cols-[1fr]`). Render a `Sheet` (Shadcn, `side="left"`) containing `MatterSidebar` with `collapsed={false} onCollapsedChange={() => {}}`. Sheet trigger: `Button variant="ghost" size="icon"` with `PanelLeft` icon (`lucide-react`), `className="lg:hidden"`, positioned in the main content area before the breadcrumb. `data-testid="mobile-sidebar-trigger"`. Sheet content: `w-[280px] overflow-y-auto p-0`. Per architecture doc Section 11.8.2. ~30 lines added. Pattern: follow `components/mobile-sidebar.tsx` for existing Sheet usage. Import `Sheet, SheetTrigger, SheetContent` from Shadcn. |
| 536.2 | Polish collapse/expand transition | 536A | | In `matter-detail-layout.tsx`, ensure the CSS Grid transition is smooth: `transition-[grid-template-columns] duration-200 ease-in-out` on the grid container. When collapsing: sidebar content fades out with `opacity-0` transition before the grid column shrinks to 0. When expanding: grid column expands first, then sidebar content fades in. Use `overflow-hidden` on the sidebar slot during transition to prevent content flash. Test: rapid toggle (click toggle multiple times quickly) should not break layout. ~20 lines refined. |
| 536.3 | Verify and fix dark mode for all new components | 536A | | Audit all new components from Epics 532-535 for dark mode support. Checklist: (1) `MatterDetailLayout` --- `border-slate-200 dark:border-slate-800` on sidebar border, (2) `MatterSidebar` --- `dark:bg-slate-950` on sticky footer, `dark:text-slate-50` on heading, (3) `GroupedTabBar` --- dropdown uses Shadcn `DropdownMenu` (inherits dark mode), verify active indicator color, (4) `KPIDashboard` --- metric cards use `bg-card` (inherits), health band colors work in dark, (5) `OverflowActionsMenu` --- uses Shadcn `DropdownMenu` (inherits). Fix any missing `dark:` variants. ~10-20 lines across 3-4 files. |
| 536.4 | Edge-case styling fixes | 536A | | Test and fix styling for edge cases per architecture doc Section 11.8.4 testing matrix: (1) Very long matter name ("Sipho Dlamini v Road Accident Fund --- High Court Johannesburg --- Case No 2026/12345") --- verify `line-clamp-3` works, tooltip shows full text. (2) No custom fields --- sidebar should not show empty accordion section. (3) 3+ field groups --- accordion scrolls within sidebar, does not push footer off screen. (4) All modules enabled (max tabs) --- GroupedTabBar fits on one line. (5) No modules enabled (min tabs) --- GroupedTabBar shows Overview + Work + Finance (core tabs only). (6) Very short name ("NDA Review") --- no excessive whitespace in sidebar header. Add conditional rendering: hide custom fields section when `fieldDefinitions.length === 0` and `fieldGroups.length === 0`. ~15 lines across 2-3 files. |
| 536.5 | Tab group dropdown z-index verification | 536A | | Verify `GroupedTabBar` dropdown z-index does not conflict with modal dialogs. Test: open Finance dropdown, then trigger Generate Document dialog from overflow menu. The dialog should render above the dropdown. Radix `DropdownMenu` uses Portal, which should handle this automatically. If z-index conflicts are found, add explicit `z-50` to dropdown content. Also verify dropdown renders correctly when sidebar is collapsed (full-width main area). ~5-10 lines if fixes needed. |
| 536.6 | Add responsive viewport tests | 536A | | Create `frontend/__tests__/components/projects/responsive-layout.test.tsx` (~4 tests): (1) Mobile sidebar trigger visible below lg breakpoint, (2) Sidebar grid column hidden below lg, (3) Collapse toggle hides sidebar on desktop, (4) Sheet opens on mobile trigger click. Note: viewport testing in Vitest/happy-dom is limited --- use `window.innerWidth` mocking or `matchMedia` mocking. Pattern: follow existing responsive test patterns in the codebase. Alternative: document Playwright E2E viewport tests in the test expectations instead of Vitest. |

### Key Files

**Slice 536A --- Modify:**
- `frontend/components/projects/matter-detail-layout.tsx` --- Sheet wrapper, transition polish
- `frontend/components/projects/matter-sidebar.tsx` --- Edge-case conditional rendering, dark mode fixes
- `frontend/components/projects/grouped-tab-bar.tsx` --- Dark mode verification, z-index fixes
- `frontend/components/projects/kpi-dashboard.tsx` --- Dark mode verification

**Slice 536A --- Create:**
- `frontend/__tests__/components/projects/responsive-layout.test.tsx`

**Slice 536A --- Read for context:**
- `frontend/components/mobile-sidebar.tsx` --- Existing Sheet-based mobile sidebar pattern
- `frontend/components/desktop-sidebar.tsx` --- Existing sidebar transition patterns
- `architecture/phase73-matter-detail-redesign.md` --- Section 11.8.4 testing matrix

### Architecture Decisions

- **Sheet on mobile, not a separate component**: The same `MatterSidebar` component renders inside the Sheet. No duplicate sidebar implementation. The Sheet receives the same props; it just wraps `MatterSidebar` in a slide-out overlay.
- **No icon-rail intermediate state for v1**: Collapsed sidebar goes to 0px. A future phase could add an icon-rail (showing section icons at ~48px width), but for v1 simplicity, collapsed means hidden.
- **Transition on grid-template-columns**: Animating the CSS Grid column width provides smooth, hardware-accelerated transitions. Content overflow is hidden during the transition to prevent visual artifacts.
- **matchMedia mocking for responsive tests**: Vitest/happy-dom does not have a real viewport. Mock `window.matchMedia` to simulate breakpoint changes. For true viewport testing, rely on Playwright E2E tests (covered in Epic 537).

---

## Epic 537: QA Testplan Updates

**Goal**: Migrate all QA lifecycle scripts to use grouped tab navigation selectors. Update Playwright selectors. Re-capture screenshot baselines. Verify all lifecycle scripts pass with the new layout.

**References**: Architecture doc Section 11.9 (QA testplan impact assessment).

**Dependencies**: Epics 532-536 (all layout changes must be complete before updating QA scripts).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **537A** | 537.1-537.5 | Update 6 QA lifecycle scripts, migrate Playwright selectors, update action button locators, re-capture screenshot baselines, verification pass | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 537.1 | Update `legal-za-full-lifecycle-keycloak.md` | 537A | | Modify `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`. Migrate all tab navigation steps per architecture doc Section 11.9.1: Step 3.5 (verify tab groups instead of flat tabs), Step 5.1 (Client > Requests), Step 10.8 (Finance > Trust), Step 21.1 (Work > Tasks), Step 21.6 (Finance > Disbursements), Step 21.10 (Schedule > Court Dates). Update action button locators: "Complete Matter" -> sidebar lifecycle action (`data-testid="sidebar-lifecycle-action"`), "Generate Document" -> overflow menu -> Generate Document. Update Playwright selectors per Section 11.9.2: `page.getByRole('tab', { name: 'Time' })` -> `page.getByTestId('tab-group-finance').click()` then `page.getByTestId('tab-item-time').click()`. ~30 selector changes. |
| 537.2 | Update `legal-za-90day-keycloak.md` | 537A | | Modify `qa/testplan/demos/legal-za-90day-keycloak.md`. Step 5.3 (verify tab loading): update to verify grouped tabs --- Work > Tasks, Finance > Time, Work > Documents, Client > Client Comments, Activity. Update all tab click selectors to use `data-testid` pattern. ~15 selector changes. |
| 537.3 | Update remaining QA lifecycle scripts | 537A | | Modify `qa/testplan/demos/consulting-agency-90day-keycloak.md`, `qa/testplan/demos/portal-client-90day-keycloak.md`, `qa/testplan/demos/admin-audit-30day-keycloak.md`, `qa/testplan/demos/accounting-za-90day-keycloak-v2.md`. For each: search for any step that navigates to the matter detail page and clicks a tab. Update to grouped tab navigation pattern. Update any action button references. Files that do not visit the matter detail page need no changes (verify by searching for "projects/[id]" or "matter detail" in each file). ~5-10 selector changes per file. |
| 537.4 | Create Playwright selector migration reference | 537A | | Add a comment block at the top of `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` (or a separate `qa/testplan/MIGRATION-NOTES.md` section) documenting the selector migration patterns from Section 11.9.2: old `page.getByRole('tab', ...)` -> new `page.getByTestId('tab-group-*')` + `page.getByTestId('tab-item-*')` two-step pattern. Old `page.getByText('Complete Matter')` -> new sidebar lifecycle action. Old `page.getByText('Generate Document')` -> new overflow menu two-step. ~20 lines of documentation. |
| 537.5 | Screenshot baseline re-capture checklist | 537A | | Update `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` with screenshot re-capture checklist per architecture doc Section 11.9.3: matter detail desktop (sidebar expanded), matter detail desktop (sidebar collapsed), matter detail mobile (Sheet closed), matter detail mobile (Sheet open), Overview tab KPI dashboard, tab group dropdown open, overflow actions menu open. Mark as TODO items that must be completed after E2E run with new layout. ~10 lines of checklist items. |

### Key Files

**Slice 537A --- Modify:**
- `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` --- Primary lifecycle script, most tab navigation steps
- `qa/testplan/demos/legal-za-90day-keycloak.md` --- Secondary lifecycle script
- `qa/testplan/demos/consulting-agency-90day-keycloak.md` --- Consulting vertical script
- `qa/testplan/demos/portal-client-90day-keycloak.md` --- Portal client script
- `qa/testplan/demos/admin-audit-30day-keycloak.md` --- Admin audit script
- `qa/testplan/demos/accounting-za-90day-keycloak-v2.md` --- Accounting vertical script

**Slice 537A --- Read for context:**
- `architecture/phase73-matter-detail-redesign.md` --- Section 11.9 (full QA impact assessment, selector migration guide, screenshot checklist)
- `frontend/components/projects/grouped-tab-bar.tsx` --- Verify data-testid values match what QA scripts will target
- `frontend/components/projects/overflow-actions-menu.tsx` --- Verify data-testid values match QA scripts

### Architecture Decisions

- **Two-step tab navigation in QA scripts**: Grouped tabs require two actions (click group, click sub-tab) instead of one (click flat tab). QA scripts must reflect this. The `data-testid` pattern (`tab-group-*` + `tab-item-*`) provides stable selectors that do not break on text or style changes.
- **Screenshot baselines are TODOs, not automated**: Re-capturing baselines requires the full E2E stack running with the new layout. This epic marks them as checklist items to be completed during the QA cycle, not as automated test assertions.
- **Selector migration documented inline**: The migration patterns are documented within the QA scripts (or a migration notes section) so future QA authors know the current selector conventions. This prevents regression to old selector patterns.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/projects/project-tabs.tsx`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/projects/overview-tab.tsx`
- `/Users/rakheendama/Projects/2026/b2b-strawman/architecture/phase73-matter-detail-redesign.md`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/CLAUDE.md`
