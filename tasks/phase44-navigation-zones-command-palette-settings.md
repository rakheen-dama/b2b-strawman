# Phase 44 — Navigation Zones, Command Palette & Settings Modernization

A **frontend-only** UX structural overhaul addressing accumulated navigation debt across 43 phases. The sidebar grew to 15 ungrouped items (exceeding the 5-7 cognitive limit), settings uses a dated card-grid that loses context on every navigation, and there is no quick-jump mechanism for power users. This phase closes all three gaps simultaneously.

**Architecture doc**: `architecture/phase44-navigation-zones-command-palette-settings.md`

**ADRs**: ADR-170 (sidebar zone structure), ADR-171 (command palette scope), ADR-172 (settings layout pattern)

**Dependencies on prior phases**:
- Phase 41 (Org Roles & Capabilities): `useCapabilities()` hook and `CapabilityProvider` — consumed by zone visibility logic
- Phase 43 (UX Quality Pass): `EmptyState`, `ErrorBoundary`, `HelpTip` patterns — established component conventions to follow

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 332 | Nav-Items Data Model Restructure | Frontend | -- | S | 332A | **Done** (PR #653) |
| 333 | Desktop Sidebar Zone Rendering | Frontend | 332 | M | 333A, 333B | **Done** (PRs #654, #655) |
| 334 | Mobile Sidebar Zone Rendering | Frontend | 332, 333 | S | 334A | **Done** (PR #656) |
| 335 | Command Palette (⌘K) | Frontend | 332 | M | 335A, 335B | **Done** (PRs #657, #658) |
| 336 | Settings Layout Shell & Sidebar | Frontend | -- | M | 336A, 336B | |
| 337 | Settings Hub Redirect & Breadcrumb Update | Frontend | 336 | S | 337A | |

---

## Dependency Graph

```
FOUNDATION
──────────────────────────────────────────────────────

[E332A Nav-items data model:
 NavGroup interface, NAV_GROUPS array,
 UTILITY_ITEMS constant, SETTINGS_ITEMS constant,
 backward-compat NAV_ITEMS re-export,
 unit tests]
        |
        +--------------------------+------------------------------+
        |                          |                              |
SIDEBAR TRACK                CMD PALETTE TRACK           SETTINGS TRACK (independent)
─────────────────            ──────────────────────      ──────────────────────────────

[E333A DesktopSidebar         [E335A CommandPaletteDialog:    [E336A settings/layout.tsx:
 zone rendering:               CommandDialog wrapper,          SettingsSidebar component,
 NavZone component,            NAV_GROUPS + SETTINGS_ITEMS     settings groups config,
 collapse/expand,              indexing, keyboard shortcut,    nested layout shell,
 Motion animation,             ⌘K trigger pill in sidebar,    sticky sidebar,
 capability filtering,         router.push on select,         mobile dropdown,
 unit tests]                   unit tests]                    unit tests]
        |                              |                              |
[E333B DesktopSidebar                 |                     [E336B Settings sidebar
 polish: search trigger               |                      visual polish:
 pill in sidebar header,              |                      active item highlight,
 utility footer items,         [E335B Recent items           group headers, Coming Soon
 empty zone hiding,            cache: RecentItemsContext,    badges, admin-only gating,
 integration tests]            pathname observation,         mobile scrollable tabs,
        |                      CommandPalette integration,   unit tests]
[E334A MobileSidebar          integration tests]                    |
 zone rendering:                                           [E337A Settings hub redirect
 same NavZone component,                                   + breadcrumb update:
 sheet context close-on-nav,                               settings/page.tsx → redirect,
 mobile capability filtering,                              breadcrumbs.tsx SEGMENT_LABELS
 unit tests]                                               + settings segments,
                                                           unit tests]
```

**Parallel opportunities**:
- E332A is the sole foundation — all sidebar and command palette epics depend on it.
- After E332A: E333A, E335A, and E336A can all run in parallel (3 concurrent builders).
- E333B depends on E333A (adds the search trigger pill that references the command palette).
- E334A depends on E332A and E333A (reuses `NavZone` component built in 333A).
- E335B depends on E335A (adds the recent-items cache layer on top of the working palette).
- E336B depends on E336A (polishes the layout shell built in 336A).
- E337A depends on E336A (redirects and breadcrumb update assume the layout shell exists).

---

## Implementation Order

### Stage 0: Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 332 | 332A | `NavGroup` and `NavItem` interfaces with optional `group` field, `NAV_GROUPS` grouped array (5 zones), `UTILITY_ITEMS` constant (Notifications + Settings), `SETTINGS_ITEMS` flat array for command palette indexing, backward-compat `NAV_ITEMS` derived re-export. ~3 modified/new files (~6 unit tests). Frontend only. | **Done** (PR #653) |

### Stage 1: Parallel Feature Tracks (3 concurrent)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 333 | 333A | `NavZone` collapsible component with Motion `AnimatePresence` expand/collapse, capability-filtered items, empty-zone auto-hide, active-item detection. `DesktopSidebar` refactored to render `NAV_GROUPS` via `NavZone` + utility footer from `UTILITY_ITEMS`. ~4 new/modified files (~5 tests). Frontend only. | **Done** (PR #654) |
| 1b (parallel) | 335 | 335A | `CommandPaletteDialog` built on `CommandDialog` from `components/ui/command.tsx`. Indexes `NAV_GROUPS` + `UTILITY_ITEMS` as "Pages" group and `SETTINGS_ITEMS` as "Settings" group. Capability-filtered. `useEffect` global `⌘K`/`Ctrl+K` listener in org layout. Empty state "No results". Router.push on select + close. ~3 new/modified files (~5 tests). Frontend only. | **Done** (PR #657) |
| 1c (parallel) | 336 | 336A | New `app/(app)/org/[slug]/settings/layout.tsx` rendering `SettingsSidebar` + `{children}`. `SettingsSidebar` is a new component with `SETTINGS_NAV_GROUPS` config (6 groups from architecture doc). Sticky sidebar. Mobile: dropdown `<select>`-style switcher below `md` breakpoint. ~3 new files (~4 tests). Frontend only. | |

### Stage 2: Dependent Second Slices (3 concurrent)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 333 | 333B | Search trigger `⌘K` pill added to `DesktopSidebar` header area (below org slug). Utility footer items (Notifications with unread badge passthrough, Settings) pinned below zone list with `border-t`. Empty zone hidden via `!items.length` guard. Integration tests confirming zone collapse, capability filter, utility footer render. ~2 modified files (~4 tests). Frontend only. | **Done** (PR #655) |
| 2b (parallel) | 334 | 334A | `MobileSidebar` refactored to render `NAV_GROUPS` via the same `NavZone` component from Epic 333A. Sheet close-on-nav preserved via `onClose` prop threading. Utility footer items rendered. Mobile-specific light-background zone header styling. ~1 modified file (~4 tests). Frontend only. | **Done** (PR #656) |
| 2c (parallel) | 335 | 335B | `RecentItemsContext` provider added to org layout — observes `pathname` changes to build a `RecentItem[]` list (max 5, project/customer pages only, extracts label from `<title>` or a pre-populated lookup). `CommandPaletteDialog` gains a "Recent" group rendered when cache is non-empty. Integration test: palette shows recent item after navigation. ~3 new/modified files (~4 tests). Frontend only. | **Done** (PR #658) |
| 2d (parallel) | 336 | 336B | `SettingsSidebar` visual polish: active item teal left-border (`border-l-2 border-teal-600`), group header uppercase muted labels, "Coming soon" `Badge` on Organization/Security items (non-clickable), admin-only item hiding via `orgRole` prop, mobile horizontal scrollable tab row alternative. Settings page content area padding/max-width alignment. ~2 modified files (~5 tests). Frontend only. | |

### Stage 3: Integration & Wiring

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 337 | 337A | `settings/page.tsx` converted to `redirect(\`/org/\${slug}/settings/billing\`)`. `breadcrumbs.tsx` gains `SEGMENT_LABELS` entries for all 23 settings segments (`rates`, `tax`, `time-tracking`, etc.) and a two-level display path when on settings subpages (`Settings > Rates & Currency`). `CommandPaletteDialog` mounted in org layout (wiring task). ~3 modified files (~5 tests). Frontend only. | |

### Timeline

```
Stage 0: [332A]                                                   (single, foundation)
Stage 1: [333A] // [335A] // [336A]                               (3 parallel tracks)
Stage 2: [333B] // [334A] // [335B] // [336B]                     (4 parallel tracks)
Stage 3: [337A]                                                    (single, integration)
```

**Critical path**: 332A → 333A → 333B → 337A (4 slices sequential at most).

**Fastest path with parallelism**: 10 slices total, 4 on the critical path. With 3 concurrent builders in Stage 1 and 4 in Stage 2, the phase completes in effectively 4 sequential rounds.

---

## Epic 332: Nav-Items Data Model Restructure

**Goal**: Extend `lib/nav-items.ts` with the `NavGroup` interface and `NAV_GROUPS` grouped array. Add `UTILITY_ITEMS` for the pinned footer. Add `SETTINGS_ITEMS` for command palette indexing. Maintain full backward compatibility by re-exporting a derived flat `NAV_ITEMS` array. This is the foundational change that all sidebar and command palette epics depend on.

**References**: Architecture doc Section 1 (Zone Structure), ADR-170.

**Dependencies**: None — changes a standalone data file.

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **332A** | 332.1–332.8 | `NavGroup` interface, `NAV_GROUPS` grouped array (5 zones × N items), `UTILITY_ITEMS` array (Notifications, Settings), `SETTINGS_ITEMS` flat array for command palette, backward-compat `NAV_ITEMS` re-export derived from `NAV_GROUPS`. ~3 modified/new files (~6 unit tests). Frontend only. | **Done** (PR #653) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 332.1 | Add `NavGroup` interface to `lib/nav-items.ts` | 332A | | Modify: `frontend/lib/nav-items.ts`. Add `interface NavGroup { id: string; label: string; items: NavItem[]; defaultExpanded: boolean; }`. Keep existing `NavItem` interface — add optional `group?: string` field for reverse-lookup. Do not remove any existing exports yet. |
| 332.2 | Define `NAV_GROUPS` array replacing flat `NAV_ITEMS` order | 332A | 332.1 | Modify: `frontend/lib/nav-items.ts`. Define `export const NAV_GROUPS: NavGroup[]` with exactly 5 zones in order: `work` (Dashboard, My Work, Calendar — `defaultExpanded: true`), `delivery` (Projects, Documents, Recurring Schedules — `defaultExpanded: true`), `clients` (Customers, Retainers, Compliance — `defaultExpanded: true`), `finance` (Invoices, Profitability, Reports — `defaultExpanded: true`), `team` (Team, Resources — `defaultExpanded: true`). Each `NavItem` gets a `group` field matching its zone `id`. All existing `requiredCapability` fields preserved unchanged. |
| 332.3 | Define `UTILITY_ITEMS` constant | 332A | 332.1 | Modify: `frontend/lib/nav-items.ts`. Add `export const UTILITY_ITEMS: NavItem[]` containing the two footer items: Notifications (`href: (slug) => \`/org/\${slug}/notifications\``, `icon: Bell`, `exact: true`) and Settings (`href: (slug) => \`/org/\${slug}/settings\``, `icon: Settings`). These items are NOT in any `NavGroup` — they are always visible, pinned to the sidebar bottom. |
| 332.4 | Define `SETTINGS_ITEMS` for command palette indexing | 332A | 332.1 | Modify: `frontend/lib/nav-items.ts`. Add `export interface SettingsItem { title: string; description: string; href: (slug: string) => string; adminOnly?: boolean; comingSoon?: boolean; }` and `export const SETTINGS_ITEMS: SettingsItem[]`. Populate with all 23 settings sections from the current `settings/page.tsx` `settingsCards` array: Billing, Notifications, Rates & Currency, Tax, Time Tracking, Custom Fields, Tags, Templates, Clauses, Checklists, Document Acceptance, Compliance, Project Templates, Project Naming, Request Templates, Request Settings, Batch Billing (adminOnly), Capacity, Email (adminOnly), Automations (adminOnly), Roles & Permissions (adminOnly), Organization (comingSoon), Security (comingSoon), Integrations. Use `href: (slug) => \`/org/\${slug}/settings/billing\`` pattern for each. |
| 332.5 | Maintain backward-compat `NAV_ITEMS` re-export | 332A | 332.2 | Modify: `frontend/lib/nav-items.ts`. Add `export const NAV_ITEMS: NavItem[] = NAV_GROUPS.flatMap((g) => g.items)` AFTER the `NAV_GROUPS` definition. This preserves compatibility for any code that still imports `NAV_ITEMS` (e.g., legacy references, breadcrumbs). The export remains but is now derived, not the source of truth. |
| 332.6 | Verify TypeScript compilation with updated types | 332A | 332.1–332.5 | No new file. Confirm that `DesktopSidebar` and `MobileSidebar` still compile against the updated `nav-items.ts` (they import `NAV_ITEMS` which is still exported). Also confirm `capabilities.tsx` still compiles. Run `pnpm run build --filter frontend` or `tsc --noEmit` in `frontend/`. |
| 332.7 | Write unit tests for `NAV_GROUPS` structure | 332A | 332.2 | New file: `frontend/lib/__tests__/nav-items.test.ts`. Tests (~6): (1) `NAV_GROUPS has exactly 5 zones`; (2) `NAV_GROUPS total items matches NAV_ITEMS count` (15 items total across all zones); (3) `each group has at least one item`; (4) `UTILITY_ITEMS has exactly 2 items (Notifications and Settings)`; (5) `backward-compat NAV_ITEMS is flat array of all group items`; (6) `SETTINGS_ITEMS contains at least 20 entries with href functions`. Pattern: see `frontend/lib/format.test.ts` for file-level test pattern. |
| 332.8 | Write unit tests for `SETTINGS_ITEMS` filtering | 332A | 332.4 | Add to `frontend/lib/__tests__/nav-items.test.ts`. Tests: (1) `adminOnly items are flagged correctly (Batch Billing, Email, Automations, Roles & Permissions)`; (2) `comingSoon items are flagged correctly (Organization, Security)`; (3) `all non-comingSoon items have valid href functions returning a string`. |

### Key Files

**Slice 332A — Modify:**
- `frontend/lib/nav-items.ts` — add `NavGroup`, `NAV_GROUPS`, `UTILITY_ITEMS`, `SETTINGS_ITEMS`, `SettingsItem`; keep `NAV_ITEMS` as derived export

**Slice 332A — Create:**
- `frontend/lib/__tests__/nav-items.test.ts` — 8 unit tests for structure correctness

**Slice 332A — Read for context:**
- `frontend/components/desktop-sidebar.tsx` — current consumer of `NAV_ITEMS`; verify backward compat
- `frontend/components/mobile-sidebar.tsx` — current consumer of `NAV_ITEMS`; verify backward compat
- `frontend/app/(app)/org/[slug]/settings/page.tsx` — source of truth for 23 settings card definitions to copy into `SETTINGS_ITEMS`
- `frontend/lib/capabilities.tsx` — `CapabilityName` type referenced in `NavItem.requiredCapability`

### Architecture Decisions

- **`NAV_GROUPS` as source of truth, `NAV_ITEMS` as derived**: Avoids duplication. The flat `NAV_ITEMS` array (used by breadcrumbs and any other legacy consumers) is always exactly the items in `NAV_GROUPS` concatenated — no drift possible.
- **`SETTINGS_ITEMS` co-located in `nav-items.ts`**: The settings item list is navigation metadata, not settings page logic. Co-locating it in `nav-items.ts` keeps the command palette's item index in one file. The settings layout (Epic 336) will define its own `SETTINGS_NAV_GROUPS` for visual grouping — a separate concern.
- **`UTILITY_ITEMS` separate from `NAV_GROUPS`**: Notifications and Settings are pinned footer items that are never inside a collapsible zone. Keeping them separate from `NAV_GROUPS` avoids edge cases where the utility footer items would need special-case handling in zone iteration.

---

## Epic 333: Desktop Sidebar Zone Rendering

**Goal**: Refactor `DesktopSidebar` to render navigation items grouped into collapsible zones using a new `NavZone` component. Add the `⌘K` search trigger pill to the sidebar header area. Pin utility footer items below the zone list. Auto-hide empty zones (all items capability-gated).

**References**: Architecture doc Sections 1 and 4, ADR-170.

**Dependencies**: Epic 332 (NAV_GROUPS, UTILITY_ITEMS must exist).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **333A** | 333.1–333.5 | New `NavZone` component (collapsible with Motion `AnimatePresence`, zone header, capability-filtered items, empty-zone auto-hide). `DesktopSidebar` refactored to render zones via `NavZone`. Utility footer items rendered. ~4 new/modified files (~5 component tests). Frontend only. | **Done** (PR #654) |
| **333B** | 333.6–333.9 | `⌘K` search trigger pill added to sidebar header below org slug. `NavZone` expand/collapse indicator (chevron). Integration tests confirming zone collapse state, capability filtering, utility footer presence, pill rendering. ~2 modified files (~4 tests). Frontend only. | **Done** (PR #655) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 333.1 | Create `NavZone` component | 333A | 332A | New file: `frontend/components/nav-zone.tsx`. Mark `"use client"`. Props: `interface NavZoneProps { zone: NavGroup; slug: string; defaultExpanded?: boolean; }`. State: `const [expanded, setExpanded] = useState(zone.defaultExpanded ?? true)`. Render: zone label header (uppercase `text-[11px] font-medium tracking-widest text-white/40`, click toggles expanded). `ChevronRight` icon from `lucide-react` rotated 90° when expanded (`className={cn("h-3 w-3 transition-transform", expanded && "rotate-90")}`). Items wrapped in Motion `AnimatePresence` + `motion.div` with `initial={{ height: 0, opacity: 0 }} animate={{ height: "auto", opacity: 1 }} exit={{ height: 0, opacity: 0 }} transition={{ duration: 0.15, ease: "easeOut" }}`. Import `motion` from `motion/react`. Each item is a `<Link>` using the same active-state classes as the current `DesktopSidebar`. Active item detection: same `pathname.startsWith(href)` / `exact` logic as existing sidebar. Motion `layoutId="sidebar-indicator"` preserved on the active teal bar. |
| 333.2 | Add capability filtering to `NavZone` | 333A | 333.1 | Modify: `frontend/components/nav-zone.tsx`. Import `useCapabilities` from `@/lib/capabilities`. Filter `zone.items` with `items.filter(item => !item.requiredCapability || hasCapability(item.requiredCapability))`. Store as `visibleItems`. If `visibleItems.length === 0`, return `null` (entire zone hidden). Render only `visibleItems` in the collapsible area. This mirrors the existing `visibleItems` filter in `DesktopSidebar`. |
| 333.3 | Refactor `DesktopSidebar` to use `NavZone` | 333A | 333.1, 333.2 | Modify: `frontend/components/desktop-sidebar.tsx`. Remove `NAV_ITEMS` import; add `NAV_GROUPS, UTILITY_ITEMS` imports from `@/lib/nav-items`. Remove the `visibleItems` filter and the flat `visibleItems.map(...)` render block. Replace nav body with `{NAV_GROUPS.map((group) => (<><NavZone key={group.id} zone={group} slug={slug} /><div className="mx-2 border-t border-white/5" /></>))}`. Keep the Platform Admin section and `SidebarUserFooter` unchanged. Remove the `useCapabilities` import (now handled inside `NavZone`). Keep `usePathname` for now (will be threaded as prop or kept at layout level). |
| 333.4 | Add utility footer items to `DesktopSidebar` | 333A | 333.3 | Modify: `frontend/components/desktop-sidebar.tsx`. Below the zone list nav, before `SidebarUserFooter`, add a pinned utility section: `<div className="mx-4 border-t border-white/10 mt-auto" />` followed by `<nav aria-label="Utility navigation" className="p-2">`. Render `UTILITY_ITEMS.map(...)` with the same active-link class pattern. This renders Notifications and Settings as always-visible footer items. The Notifications item will later get an unread badge in Epic 335B (wired via props or context). |
| 333.5 | Write component tests for `NavZone` | 333A | 333.1–333.4 | New file: `frontend/components/__tests__/nav-zone.test.tsx`. Tests (~5): (1) `renders zone label as section header`; (2) `renders visible items when expanded`; (3) `collapses items on zone header click`; (4) `hides zone entirely when all items are capability-gated (mock hasCapability returning false)`; (5) `shows active indicator on current path item`. Setup: mock `usePathname` from `next/navigation`, mock `useCapabilities` from `@/lib/capabilities`. Pattern: see `frontend/components/__tests__/` for existing test patterns (error-boundary, help-tip). |
| 333.6 | Add `⌘K` search trigger pill to sidebar header | 333B | 333.3 | Modify: `frontend/components/desktop-sidebar.tsx`. After the org slug display area, add a clickable pill: `<button onClick={onOpenCommandPalette} className="mx-4 mb-2 flex w-[calc(100%-2rem)] items-center gap-2 rounded-md border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-white/40 hover:bg-white/10 hover:text-white/60 transition-colors">`. Left: `<Search className="h-3.5 w-3.5" />` and text `"Search..."`. Right-aligned: `<kbd className="ml-auto rounded bg-white/10 px-1 py-0.5 text-[10px] font-mono">⌘K</kbd>`. Add `onOpenCommandPalette?: () => void` prop to `DesktopSidebarProps`. The prop is called on click; the actual command palette open logic is wired in the org layout (Epic 337A). |
| 333.7 | Add collapse chevron indicator to `NavZone` header | 333B | 333.1 | Modify: `frontend/components/nav-zone.tsx`. Wrap the zone header in a `<button>` (accessible click target). Render `<ChevronRight>` icon inside the header row, right-aligned using `flex justify-between items-center`. Apply `className={cn("h-3 w-3 transition-transform duration-150", expanded && "rotate-90")}`. Add `aria-expanded={expanded}` to the button. |
| 333.8 | Add zone dividers and spacing polish | 333B | 333.3 | Modify: `frontend/components/desktop-sidebar.tsx`. Between zones, render a thin `<div className="my-1 mx-2 border-t border-white/5" />` separator. Add `gap-0` to the nav container (remove `gap-1`). Adjust `NavZone` header padding: `px-3 py-1.5` for the zone header row vs `px-3 py-2` for items. This matches the architecture doc visual spec (`border-slate-800` equivalent using `white/5` for the dark sidebar). |
| 333.9 | Write integration tests for desktop sidebar zones | 333B | 333.6–333.8 | Add to `frontend/components/__tests__/nav-zone.test.tsx` or new file `desktop-sidebar.test.tsx`. Tests (~4): (1) `renders all 5 zone headers`; (2) `collapses a zone on header click, hides its items`; (3) `renders utility footer section with Notifications and Settings`; (4) `renders search pill with ⌘K badge`. Mock: `NAV_GROUPS` (import actual, mock `useCapabilities` to return all caps), `usePathname`. |

### Key Files

**Slice 333A — Create:**
- `frontend/components/nav-zone.tsx` — collapsible zone component with Motion animation, capability filtering, active-item detection

**Slice 333A — Modify:**
- `frontend/components/desktop-sidebar.tsx` — replace flat item list with `NavZone` iteration + utility footer

**Slice 333A — Create:**
- `frontend/components/__tests__/nav-zone.test.tsx` — 5 component tests

**Slice 333B — Modify:**
- `frontend/components/desktop-sidebar.tsx` — add `⌘K` search pill, zone dividers, spacing polish
- `frontend/components/nav-zone.tsx` — add collapse chevron indicator, `aria-expanded`

**Read for context:**
- `frontend/components/desktop-sidebar.tsx` — current implementation to refactor
- `frontend/lib/capabilities.tsx` — `useCapabilities` hook signature
- `frontend/components/page-transition.tsx` — Motion import pattern (from `motion/react`)
- `frontend/components/__tests__/` — existing test file patterns

### Architecture Decisions

- **`NavZone` as a standalone component (not inline in `DesktopSidebar`)**: The same component is reused by `MobileSidebar` (Epic 334A). Extracting it prevents duplication and ensures consistent behavior between desktop and mobile zones.
- **`useState` for collapse state (no persistence)**: Per ADR-170, collapse state is not persisted. `useState` with `defaultExpanded` from the zone config is the simplest correct solution. If localStorage persistence is requested later, the only change needed is `useState(localStorage.getItem(zone.id) ?? zone.defaultExpanded)`.
- **Motion `AnimatePresence` with height transition**: The architecture doc specifies `~150ms ease-out` collapse animation. `height: "auto"` via `motion.div` achieves this without measuring DOM elements. The `overflow: hidden` default on the motion wrapper ensures no content bleed during animation.
- **`onOpenCommandPalette` as prop on `DesktopSidebar`**: The command palette state (`open`/`setOpen`) lives in the org layout. Passing it down as a callback avoids prop drilling through the component tree and keeps `DesktopSidebar` testable in isolation. The search pill renders regardless of whether `onOpenCommandPalette` is provided.

---

## Epic 334: Mobile Sidebar Zone Rendering

**Goal**: Refactor `MobileSidebar` to render the same zone-grouped navigation as the desktop sidebar, reusing the `NavZone` component. Preserve all existing mobile behavior: sheet open/close state, close-on-navigation, Platform Admin section.

**References**: Architecture doc Section 1 (Mobile Sidebar), ADR-170.

**Dependencies**: Epic 332 (NAV_GROUPS), Epic 333A (NavZone component).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **334A** | 334.1–334.5 | `MobileSidebar` refactored to render `NAV_GROUPS` via `NavZone`. Sheet close-on-nav preserved. Utility footer items rendered. ~1 modified file, 1 test file (~4 tests). Frontend only. | **Done** (PR #656) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 334.1 | Import `NAV_GROUPS` and `UTILITY_ITEMS` in `MobileSidebar` | 334A | 332A | Modify: `frontend/components/mobile-sidebar.tsx`. Replace `import { NAV_ITEMS }` with `import { NAV_GROUPS, UTILITY_ITEMS }` from `@/lib/nav-items`. Add `import { NavZone } from "@/components/nav-zone"`. Remove the `useCapabilities` import (handled inside `NavZone`). Keep `usePathname` for the active-state detection within `NavZone` (it uses `usePathname` internally). |
| 334.2 | Replace flat item list with `NavZone` iteration | 334A | 334.1, 333A | Modify: `frontend/components/mobile-sidebar.tsx`. Remove the `visibleItems` filter block and the `visibleItems.map(...)` nav render. Replace the nav body with `{NAV_GROUPS.map((group) => (<NavZone key={group.id} zone={group} slug={slug} onNavItemClick={() => setOpen(false)} />))}`. Add `onNavItemClick?: () => void` prop to `NavZone` so the sheet closes on navigation. Thread this prop into each `<Link onClick={onNavItemClick}>` inside `NavZone`. |
| 334.3 | Add utility footer items to `MobileSidebar` | 334A | 334.2 | Modify: `frontend/components/mobile-sidebar.tsx`. Below the zone nav, before `SidebarUserFooter`, add a utility section with `UTILITY_ITEMS.map(...)` rendered as `<Link onClick={() => setOpen(false)} ...>` with the same active-link class pattern. Add a `border-t border-white/10 my-1 mx-4` separator above the utility items. |
| 334.4 | Handle mobile-specific zone header styling | 334A | 334.2 | Modify: `frontend/components/nav-zone.tsx` (or pass a `variant` prop). The mobile sidebar uses `bg-slate-950` (dark) matching the desktop — so no style change is needed. The `NavZone` component uses `white/40` text for zone headers which works on both dark backgrounds. Verify this is acceptable. If the mobile sidebar ever uses a light sheet background, a `variant="light"` prop can be added later. |
| 334.5 | Write component tests for mobile sidebar zones | 334A | 334.1–334.4 | New file: `frontend/components/__tests__/mobile-sidebar.test.tsx`. Tests (~4): (1) `renders hamburger menu trigger`; (2) `opens sheet and shows zone headers on trigger click`; (3) `renders utility footer items (Notifications, Settings)`; (4) `closes sheet on nav item click`. Setup: mock `NAV_GROUPS` (use actual), mock `useCapabilities`, mock `usePathname`. Pattern: see existing `desktop-sidebar.test.tsx` (if created in 333B) or `nav-zone.test.tsx`. |

### Key Files

**Slice 334A — Modify:**
- `frontend/components/mobile-sidebar.tsx` — replace flat item list with `NavZone` iteration, add utility footer

**Slice 334A — Modify (if needed):**
- `frontend/components/nav-zone.tsx` — add `onNavItemClick` prop for sheet-close threading

**Slice 334A — Create:**
- `frontend/components/__tests__/mobile-sidebar.test.tsx` — 4 component tests

**Read for context:**
- `frontend/components/mobile-sidebar.tsx` — full current implementation
- `frontend/components/nav-zone.tsx` — built in Epic 333A; must understand `onNavItemClick` threading

### Architecture Decisions

- **Reuse `NavZone` across desktop and mobile**: Both sidebars use the same dark (`bg-slate-950`) background, so the zone header styling works without a `variant` prop. The only behavioral difference is the `onNavItemClick` callback for sheet close. Threading this as a prop on `NavZone` is cleaner than having `MobileSidebar` wrap each item.
- **No mobile-specific collapse defaults**: The architecture doc does not specify different default collapse states for mobile. All zones default to expanded (`defaultExpanded: true`) on mobile as well. If the mobile sheet is visually crowded, zones can be collapsed by default in a follow-up by adjusting the `NAV_GROUPS` config in `nav-items.ts`.

---

## Epic 335: Command Palette (⌘K)

**Goal**: Add a global keyboard-first command palette triggered by `⌘K` / `Ctrl+K`. Built on the existing `CommandDialog` Shadcn component. Indexes all nav pages and settings items, capability-filtered. On selection, navigates via `router.push` and closes the palette. Includes a "Recent Items" cache as a stretch goal (slice 335B).

**References**: Architecture doc Section 2, ADR-171.

**Dependencies**: Epic 332 (NAV_GROUPS, UTILITY_ITEMS, SETTINGS_ITEMS must exist).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **335A** | 335.1–335.6 | `CommandPaletteDialog` component with "Pages" and "Settings" groups. Global `⌘K`/`Ctrl+K` listener. Capability and admin filtering. Router.push on select. Empty state. Mounted in org layout. ~3 new/modified files (~5 tests). Frontend only. | **Done** (PR #657) |
| **335B** | 335.7–335.10 | `RecentItemsContext` provider tracking last 5 visited project/customer pages. "Recent" group added to `CommandPaletteDialog` when cache non-empty. Provider mounted in org layout. ~3 new/modified files (~4 tests). Frontend only. | **Done** (PR #658) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 335.1 | Create `CommandPaletteDialog` component | 335A | 332A | New file: `frontend/components/command-palette-dialog.tsx`. Mark `"use client"`. Props: `interface CommandPaletteDialogProps { open: boolean; onOpenChange: (open: boolean) => void; slug: string; }`. Import `CommandDialog, CommandInput, CommandList, CommandEmpty, CommandGroup, CommandItem` from `@/components/ui/command`. Import `useRouter` from `next/navigation`. Import `useCapabilities, useCapabilityContext` from `@/lib/capabilities`. Import `NAV_GROUPS, UTILITY_ITEMS, SETTINGS_ITEMS` from `@/lib/nav-items`. |
| 335.2 | Implement "Pages" group in `CommandPaletteDialog` | 335A | 335.1 | Modify: `frontend/components/command-palette-dialog.tsx`. Build `pageItems` by calling `NAV_GROUPS.flatMap(g => g.items).concat(UTILITY_ITEMS)` then filtering by `!item.requiredCapability || hasCapability(item.requiredCapability)`. Render as `<CommandGroup heading="Pages">`. Each `<CommandItem>` renders `<item.icon className="mr-2 h-4 w-4" />` and `item.label`. On select: `router.push(item.href(slug))` then `onOpenChange(false)`. |
| 335.3 | Implement "Settings" group in `CommandPaletteDialog` | 335A | 335.1 | Modify: `frontend/components/command-palette-dialog.tsx`. Build `settingsItems` by filtering `SETTINGS_ITEMS` where `!item.comingSoon && (!item.adminOnly || isAdmin)`. Obtain `isAdmin` from `useCapabilityContext()`. Render as `<CommandGroup heading="Settings">`. Each `<CommandItem value={\`settings:\${item.title}\`}` to avoid key collisions with pages. On select: `router.push(item.href(slug))` then `onOpenChange(false)`. Render `item.title` as primary label. |
| 335.4 | Implement empty state in `CommandPaletteDialog` | 335A | 335.2, 335.3 | Modify: `frontend/components/command-palette-dialog.tsx`. After `CommandList` children, add `<CommandEmpty>No results found.</CommandEmpty>`. The cmdk library handles showing this when no items match the filter. No custom filtering logic needed — cmdk's built-in fuzzy filter handles it. |
| 335.5 | Mount `CommandPaletteDialog` in org layout with `⌘K` listener | 335A | 335.1–335.4 | Modify: `frontend/app/(app)/org/[slug]/layout.tsx`. This is a Server Component — the keyboard shortcut listener must be in a Client Component. Create new `frontend/components/command-palette-provider.tsx` (`"use client"`) that: (1) holds `const [open, setOpen] = useState(false)`, (2) adds `useEffect(() => { const handler = (e: KeyboardEvent) => { if ((e.metaKey || e.ctrlKey) && e.key === 'k') { e.preventDefault(); setOpen(true); } }; window.addEventListener('keydown', handler); return () => window.removeEventListener('keydown', handler); }, [])`, (3) renders `<CommandPaletteDialog open={open} onOpenChange={setOpen} slug={slug} />` + `{children}`. In `layout.tsx`, wrap the `CapabilityProvider > div` tree with `<CommandPaletteProvider slug={slug}>`. Pass `onOpenCommandPalette={() => setOpen(true)}` to `DesktopSidebar` via a Client wrapper component or context. Note: `DesktopSidebar` already accepts `onOpenCommandPalette?: () => void` (added in 333B). Use a `CommandPaletteContext` to expose `setOpen` to the search pill without prop drilling through the Server Component layout. |
| 335.6 | Write component tests for `CommandPaletteDialog` | 335A | 335.1–335.4 | New file: `frontend/components/__tests__/command-palette-dialog.test.tsx`. Tests (~5): (1) `renders CommandDialog when open=true`; (2) `filters items by typed query (mock cmdk behavior)`; (3) `hides capability-gated pages (mock hasCapability returning false for INVOICING)`; (4) `hides admin-only settings items when isAdmin=false`; (5) `calls router.push and onOpenChange(false) on item selection`. Mock `useRouter`, `useCapabilities`, `useCapabilityContext`. |
| 335.7 | Create `RecentItemsContext` and provider | 335B | 335A | New file: `frontend/components/recent-items-provider.tsx`. `"use client"`. Define `interface RecentItem { label: string; href: string; type: 'project' | 'customer'; }`. Create `RecentItemsContext` with `{ items: RecentItem[]; addItem: (item: RecentItem) => void }`. Provider holds `const [items, setItems] = useState<RecentItem[]>([])`. `addItem` prepends and deduplicates by `href`, keeping max 5 items: `setItems(prev => [item, ...prev.filter(i => i.href !== item.href)].slice(0, 5))`. Export `useRecentItems` hook. |
| 335.8 | Observe pathname changes to populate recent items cache | 335B | 335.7 | Modify: `frontend/components/recent-items-provider.tsx`. Add `usePathname` and `useParams`. In a `useEffect([pathname])`, detect if pathname matches `/org/[slug]/projects/[id]` or `/org/[slug]/customers/[id]`. If matched, call `addItem`. For the label: use `document.title` (the page `<title>` which Next.js sets from page metadata) stripped of the ` | DocTeams` suffix. This avoids any API calls. Pattern: `const title = document.title.replace(' | DocTeams', '').trim()`. |
| 335.9 | Integrate recent items into `CommandPaletteDialog` | 335B | 335.7, 335.8 | Modify: `frontend/components/command-palette-dialog.tsx`. Import `useRecentItems` from `@/components/recent-items-provider`. Add `<CommandGroup heading="Recent">` rendered only when `recentItems.length > 0`. Each item: `<CommandItem value={\`recent:\${item.href}\`}`. On select: `router.push(item.href)` then `onOpenChange(false)`. Place "Recent" group ABOVE "Pages" in the list for discoverability. |
| 335.10 | Write tests for recent items provider and integration | 335B | 335.7–335.9 | New file: `frontend/components/__tests__/recent-items-provider.test.tsx`. Tests (~4): (1) `starts with empty items list`; (2) `addItem prepends new item`; (3) `addItem deduplicates by href`; (4) `addItem keeps max 5 items`. Add to `command-palette-dialog.test.tsx`: (5) `shows Recent group when recentItems is non-empty`. |

### Key Files

**Slice 335A — Create:**
- `frontend/components/command-palette-dialog.tsx` — main palette component (Pages + Settings groups)
- `frontend/components/command-palette-provider.tsx` — client wrapper with keyboard listener, `CommandPaletteContext`

**Slice 335A — Modify:**
- `frontend/app/(app)/org/[slug]/layout.tsx` — mount `CommandPaletteProvider`

**Slice 335A — Create:**
- `frontend/components/__tests__/command-palette-dialog.test.tsx` — 5 component tests

**Slice 335B — Create:**
- `frontend/components/recent-items-provider.tsx` — in-memory recent items cache
- `frontend/components/__tests__/recent-items-provider.test.tsx` — 4 unit tests

**Slice 335B — Modify:**
- `frontend/components/command-palette-dialog.tsx` — add "Recent" group

**Read for context:**
- `frontend/components/ui/command.tsx` — existing `CommandDialog`, `CommandInput`, `CommandList`, `CommandGroup`, `CommandItem`, `CommandEmpty` exports
- `frontend/lib/capabilities.tsx` — `useCapabilities`, `useCapabilityContext` hooks and `CapabilityProvider`
- `frontend/app/(app)/org/[slug]/layout.tsx` — org layout structure; where to mount the provider
- `frontend/components/page-transition.tsx` — example of a `"use client"` provider wrapper in the layout

### Architecture Decisions

- **`CommandPaletteProvider` as a separate `"use client"` wrapper**: The org layout (`layout.tsx`) is a Server Component. The `⌘K` keyboard listener requires `useEffect` and therefore must run in a Client Component. Rather than marking the entire layout `"use client"` (which would disable SSR for all org pages), a thin `CommandPaletteProvider` wrapper holds all client state and mounts the dialog. This is the same pattern used by `PageTransition` in the existing layout.
- **`CommandPaletteContext` for exposing `setOpen` to the search pill**: The `DesktopSidebar` (also `"use client"`) needs to call `setOpen(true)` when the user clicks the `⌘K` pill. Rather than prop-drilling through the Server Component layout, a React context (`CommandPaletteContext`) exposes `{ open, setOpen }` to any client component in the tree. The sidebar pill calls `useCommandPalette().setOpen(true)`.
- **No debounce/loading state**: Per ADR-171, the palette is page-only search. All items are static arrays — cmdk filters synchronously. No async loading, no debounce needed. The palette opens and is immediately usable.
- **`document.title` for recent item labels**: Avoids any API calls. Next.js App Router sets `document.title` from page metadata before the component renders, so by the time a `useEffect` fires on pathname change, the title reflects the new page. The ` | DocTeams` suffix convention must be consistent in page metadata (which it already is across the platform).

---

## Epic 336: Settings Layout Shell & Sidebar

**Goal**: Convert the settings area from a card-grid hub to a persistent sidebar + content pane layout using a new `settings/layout.tsx`. Create a `SettingsSidebar` component with 6 grouped sections, active-item highlighting, Coming Soon badges, and admin-only item gating. Mobile-responsive: sidebar becomes a scrollable horizontal tab row below `md` breakpoint.

**References**: Architecture doc Section 3, ADR-172.

**Dependencies**: None — the settings layout is entirely independent of sidebar/command palette changes.

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **336A** | 336.1–336.5 | New `settings/layout.tsx` rendering `SettingsSidebar` + `{children}`. `SettingsSidebar` component with 6 setting groups config, sticky behavior, mobile dropdown. ~3 new files (~4 tests). Frontend only. | |
| **336B** | 336.6–336.9 | `SettingsSidebar` visual polish: active item teal highlight, group header labels, Coming Soon badges, admin-only gating, mobile horizontal scrollable tabs, content area max-width. ~2 modified files (~5 tests). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 336.1 | Define `SETTINGS_NAV_GROUPS` config constant | 336A | | New file: `frontend/components/settings/settings-nav-groups.ts`. (No `"use client"` — pure data.) Define `interface SettingsNavItem { label: string; href: string; adminOnly?: boolean; comingSoon?: boolean; }` and `interface SettingsNavGroup { id: string; label: string; items: SettingsNavItem[]; }`. Define and export `SETTINGS_NAV_GROUPS: SettingsNavGroup[]` with the 6 groups from the architecture doc: `general` (Billing `/settings/billing`, Notifications `/settings/notifications`, Email `/settings/email` (adminOnly), Security (comingSoon)), `work` (Time Tracking, Project Templates, Project Naming, Automations (adminOnly)), `documents` (Templates, Clauses, Checklists, Document Acceptance), `finance` (Rates & Currency, Tax, Batch Billing (adminOnly), Capacity), `clients` (Custom Fields, Tags, Request Templates, Request Settings, Compliance), `access` (Roles & Permissions (adminOnly), Integrations). All `href` values are relative slugs (e.g., `billing`, `rates`) — the `SettingsSidebar` component prepends `/org/${slug}/settings/`. |
| 336.2 | Create `SettingsSidebar` component (structure only) | 336A | 336.1 | New file: `frontend/components/settings/settings-sidebar.tsx`. `"use client"`. Props: `interface SettingsSidebarProps { slug: string; isAdmin: boolean; }`. Import `SETTINGS_NAV_GROUPS`. Import `usePathname` from `next/navigation`. Import `Link` from `next/link`. For each group: render group label + filtered items. Filter: `item => !item.adminOnly || isAdmin`. Render each item as `<Link href={\`/org/\${slug}/settings/\${item.href}\`}>`. Coming Soon items: render as non-clickable `<span>` with muted styling. Return a `<nav>` element. This slice focuses on structure; visual polish is in 336B. |
| 336.3 | Create `settings/layout.tsx` | 336A | 336.2 | New file: `frontend/app/(app)/org/[slug]/settings/layout.tsx`. This is a Server Component. Params: `{ params: Promise<{ slug: string }> }`. Fetch `isAdmin` via `const { orgRole } = await getAuthContext(); const isAdmin = orgRole === 'org:admin' || orgRole === 'org:owner'`. Render: `<div className="flex gap-6">`. Left: `<aside className="hidden w-60 shrink-0 md:block"><SettingsSidebar slug={slug} isAdmin={isAdmin} /></aside>`. Right: `<div className="flex-1 min-w-0">{children}</div>`. The `{children}` slot renders the active settings page (e.g., `settings/billing/page.tsx`). The existing `settings/page.tsx` card-grid continues to compile (it is replaced in Epic 337A). |
| 336.4 | Add mobile settings nav to `SettingsSidebar` | 336A | 336.2 | Modify: `frontend/components/settings/settings-sidebar.tsx`. Add a mobile nav below the `hidden md:block` desktop sidebar. Use a `<div className="md:hidden mb-4">` container. Inside, render a horizontally scrollable tab row: `<div className="flex gap-1 overflow-x-auto pb-2">`. Each visible item renders as a `<Link className={cn("shrink-0 rounded-full px-3 py-1.5 text-sm whitespace-nowrap transition-colors", isActive ? "bg-teal-600 text-white" : "bg-slate-100 text-slate-700 hover:bg-slate-200")}>`. Active detection: `usePathname().endsWith(\`/settings/\${item.href}\`)`. This replaces the `select` dropdown approach from the architecture doc with a pill-style tab row (more consistent with Signal Deck aesthetic). |
| 336.5 | Write tests for `SettingsSidebar` structure | 336A | 336.2–336.4 | New file: `frontend/components/__tests__/settings-sidebar.test.tsx`. Tests (~4): (1) `renders all 6 group headers`; (2) `hides adminOnly items when isAdmin=false`; (3) `shows adminOnly items when isAdmin=true`; (4) `renders Coming Soon items as non-clickable`. Mock `usePathname`. Setup: import actual `SETTINGS_NAV_GROUPS`. |
| 336.6 | Add active item highlighting to `SettingsSidebar` | 336B | 336.2 | Modify: `frontend/components/settings/settings-sidebar.tsx`. Active detection: `const isActive = pathname === \`/org/\${slug}/settings/\${item.href}\``. Active link class: `border-l-2 border-teal-600 bg-teal-50 text-slate-900 font-medium` (light background sidebar — settings sidebar is white/card, not dark like the main sidebar). Default link class: `border-l-2 border-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-50`. Wrap all items in `pl-3 py-1.5 text-sm rounded-r-md transition-colors` base class. |
| 336.7 | Add group header labels and visual structure to `SettingsSidebar` | 336B | 336.6 | Modify: `frontend/components/settings/settings-sidebar.tsx`. Group header label: `<div className="px-3 pb-1 pt-3 text-[11px] font-medium uppercase tracking-widest text-slate-400">`. Between groups: `<div className="my-2 border-t border-slate-100" />`. Sidebar container: `sticky top-0 h-[calc(100vh-3.5rem)] overflow-y-auto py-4`. (3.5rem = 56px header height.) This ensures the settings sidebar scrolls independently if there are many items. |
| 336.8 | Add Coming Soon badge and disabled state to `SettingsSidebar` | 336B | 336.6 | Modify: `frontend/components/settings/settings-sidebar.tsx`. For `comingSoon` items: render `<span>` instead of `<Link>`. Add `<Badge variant="neutral" className="ml-auto text-[10px] py-0">Coming soon</Badge>` to the right side. Apply `cursor-not-allowed opacity-50` to the wrapping element. Import `Badge` from `@/components/ui/badge`. |
| 336.9 | Write visual/behavior tests for polished `SettingsSidebar` | 336B | 336.6–336.8 | Add to `frontend/components/__tests__/settings-sidebar.test.tsx`. Tests (~5): (1) `applies active class to current settings section link`; (2) `does not apply active class to non-active links`; (3) `renders Coming Soon badge on Organization and Security items`; (4) `Coming Soon items are not rendered as anchor elements`; (5) `mobile tab row renders for all visible items`. Mock `usePathname` to return `/org/acme/settings/billing` for active-state tests. |

### Key Files

**Slice 336A — Create:**
- `frontend/components/settings/settings-nav-groups.ts` — pure data: 6 group definitions with all settings items
- `frontend/components/settings/settings-sidebar.tsx` — client component: grouped nav, mobile tabs
- `frontend/app/(app)/org/[slug]/settings/layout.tsx` — server component: settings shell

**Slice 336A — Create:**
- `frontend/components/__tests__/settings-sidebar.test.tsx` — 4 tests (structural)

**Slice 336B — Modify:**
- `frontend/components/settings/settings-sidebar.tsx` — active highlight, group headers, Coming Soon badges

**Read for context:**
- `frontend/app/(app)/org/[slug]/settings/page.tsx` — existing 23-card page; source for settings item definitions; will be replaced in 337A
- `frontend/app/(app)/org/[slug]/layout.tsx` — org layout pattern; mimic for settings layout shell
- `frontend/lib/auth.ts` (or `@/lib/auth`) — `getAuthContext()` for `isAdmin` resolution
- `frontend/components/ui/badge.tsx` — `Badge` component for "Coming soon" pills

### Architecture Decisions

- **New `components/settings/` subdirectory**: The settings layout components are grouped in their own subdirectory matching the existing convention (`components/billing/`, `components/projects/`). The `SETTINGS_NAV_GROUPS` data file is co-located here rather than in `lib/nav-items.ts` because it is layout-specific visual configuration, not navigation index metadata.
- **Separate `SETTINGS_NAV_GROUPS` from `SETTINGS_ITEMS`**: `SETTINGS_ITEMS` (in `lib/nav-items.ts`) is a flat list for command palette indexing — it includes descriptions and icons for search display. `SETTINGS_NAV_GROUPS` (in `components/settings/`) is a grouped visual structure for the settings sidebar — it has group headers, coming-soon flags, and admin gating but no descriptions. These are different concerns; merging them would create a large, hard-to-maintain data structure.
- **Settings layout as a Server Component**: Per ADR-172, the nested layout approach with `{children}` is the right pattern. The layout itself can be a Server Component (it only needs `getAuthContext()` for `isAdmin`), while `SettingsSidebar` is a `"use client"` component (needs `usePathname` for active detection). This is the same Server/Client boundary pattern used throughout the org area.
- **Horizontal scrollable tabs for mobile (not `<select>`)**: A pill-style tab row is more consistent with the Signal Deck aesthetic than a native `<select>` element. It allows multi-group settings to be visible without opening a dropdown. The `overflow-x-auto` ensures it works on small screens without wrapping.

---

## Epic 337: Settings Hub Redirect & Breadcrumb Update

**Goal**: Convert the settings hub page to a redirect to `settings/billing`. Update `breadcrumbs.tsx` with all 23 settings segment labels so settings subpages show `Settings > Rates & Currency` instead of raw segment names. Wire the `⌘K` search pill `onOpenCommandPalette` callback from `CommandPaletteProvider` to `DesktopSidebar` via context.

**References**: Architecture doc Sections 3 and 4, ADR-172.

**Dependencies**: Epic 336A (settings layout must exist before redirecting to it).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **337A** | 337.1–337.6 | `settings/page.tsx` converted to redirect. `breadcrumbs.tsx` gains all settings segment labels and two-level settings path rendering. `CommandPaletteProvider` wired to `DesktopSidebar` via context. ~3 modified files (~5 tests). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 337.1 | Convert `settings/page.tsx` to redirect | 337A | 336A | Modify: `frontend/app/(app)/org/[slug]/settings/page.tsx`. Replace the entire file content with: `import { redirect } from "next/navigation"; export default async function SettingsPage({ params }: { params: Promise<{ slug: string }> }) { const { slug } = await params; redirect(\`/org/\${slug}/settings/billing\`); }`. This is a Server Component — `redirect()` works without `"use client"`. The 23-card grid markup is removed. The `SettingsCard` interface and `settingsCards` array are deleted from this file (they are now superseded by `SETTINGS_NAV_GROUPS` in Epic 336 and `SETTINGS_ITEMS` in Epic 332). |
| 337.2 | Add all 23 settings segment labels to `breadcrumbs.tsx` | 337A | | Modify: `frontend/components/breadcrumbs.tsx`. Expand `SEGMENT_LABELS` with all settings sub-segments: `billing: "Billing"`, `notifications: "Notifications"`, `rates: "Rates & Currency"`, `tax: "Tax"`, `time-tracking: "Time Tracking"`, `custom-fields: "Custom Fields"`, `tags: "Tags"`, `templates: "Templates"`, `clauses: "Clauses"`, `checklists: "Checklists"`, `acceptance: "Document Acceptance"`, `compliance: "Compliance"`, `project-templates: "Project Templates"`, `project-naming: "Project Naming"`, `request-templates: "Request Templates"`, `request-settings: "Request Settings"`, `batch-billing: "Batch Billing"`, `capacity: "Capacity"`, `email: "Email"`, `automations: "Automations"`, `roles: "Roles & Permissions"`, `integrations: "Integrations"`. Also add `"my-work": "My Work"`, `"schedules": "Recurring Schedules"`, `"retainers": "Retainers"` which may be missing. |
| 337.3 | Add settings segment to `PARENT_SEGMENT_FALLBACKS` in `breadcrumbs.tsx` | 337A | 337.2 | Modify: `frontend/components/breadcrumbs.tsx`. Add `settings: "Settings"` to `PARENT_SEGMENT_FALLBACKS`. This ensures that when on a settings subpage (e.g., `/org/acme/settings/billing`), the breadcrumb shows `acme > Settings > Billing` with "Settings" as a clickable link back to the settings hub (which now redirects to billing). Also add `customers: "Customer"` and `invoices: "Invoice"` if missing from the fallbacks map. |
| 337.4 | Wire `CommandPaletteProvider` context to `DesktopSidebar` search pill | 337A | 335A, 333B | Modify: `frontend/components/command-palette-provider.tsx`. Export a `CommandPaletteContext` with `{ setOpen: (open: boolean) => void }`. In `DesktopSidebar`, import `useCommandPalette` from `@/components/command-palette-provider` and call `const { setOpen } = useCommandPalette()`. Use `setOpen(true)` as the `onOpenCommandPalette` callback for the `⌘K` pill click. Remove the `onOpenCommandPalette` prop from `DesktopSidebarProps` — the sidebar reads from context instead. Update `MobileSidebar` similarly if it needs the palette trigger. Note: this must be done carefully — `DesktopSidebar` is already `"use client"`, so importing a context is valid. |
| 337.5 | Verify no broken links from settings card removal | 337A | 337.1 | No new file. Audit: confirm the `settings/page.tsx` redirect does not break the `CommandPaletteDialog` (which uses `SETTINGS_ITEMS` from `lib/nav-items.ts` — independent of `settings/page.tsx`). Confirm the `SettingsSidebar` renders correctly now that `settings/page.tsx` no longer shows the card grid. Navigate to `settings/billing` in the E2E stack to verify the layout shell renders with the sidebar + billing content. |
| 337.6 | Write tests for redirect and breadcrumb updates | 337A | 337.1–337.3 | New file: `frontend/components/__tests__/breadcrumbs.test.tsx` (or add to existing if present). Tests (~5): (1) `renders Settings as a link segment when on settings subpage`; (2) `renders correct label for rates segment ("Rates & Currency")`; (3) `renders correct label for custom-fields segment ("Custom Fields")`; (4) `renders correct label for project-naming segment ("Project Naming")`; (5) `renders Settings > Billing two-level path correctly`. Mock `usePathname` to return `/org/acme/settings/billing` etc. |

### Key Files

**Slice 337A — Modify:**
- `frontend/app/(app)/org/[slug]/settings/page.tsx` — replace card grid with redirect to `settings/billing`
- `frontend/components/breadcrumbs.tsx` — add all 23 settings segment labels, `settings` parent fallback
- `frontend/components/command-palette-provider.tsx` — export `CommandPaletteContext`, `useCommandPalette` hook

**Slice 337A — Modify:**
- `frontend/components/desktop-sidebar.tsx` — replace `onOpenCommandPalette` prop with `useCommandPalette` context hook

**Slice 337A — Create:**
- `frontend/components/__tests__/breadcrumbs.test.tsx` — 5 tests

**Read for context:**
- `frontend/components/breadcrumbs.tsx` — current `SEGMENT_LABELS` structure to extend
- `frontend/components/desktop-sidebar.tsx` — current prop signature for `onOpenCommandPalette`
- `frontend/components/command-palette-provider.tsx` — built in Epic 335A; must export context

### Architecture Decisions

- **Breadcrumb labels as a static map (not derived from `NAV_GROUPS`)**: The `SEGMENT_LABELS` map in `breadcrumbs.tsx` maps URL segment strings to display labels. Deriving these from `NAV_GROUPS` or `SETTINGS_NAV_GROUPS` is tempting but fragile — the breadcrumb needs to handle URL segment casing/hyphenation (`custom-fields` → "Custom Fields") that doesn't map cleanly to the nav item `label` field. A static map is the simplest, most maintainable approach. The additional 23 entries are a one-time data entry task.
- **Context over prop for `CommandPalette` open state**: As noted in Epic 335, the `DesktopSidebar` is a `"use client"` component that sits inside a Server Component layout. Prop drilling `onOpenCommandPalette` from the layout through Server Component tree is not possible without making the layout a Client Component. The `CommandPaletteContext` approach keeps the layout as a Server Component while allowing any client component in the tree to open the palette.
- **Redirect to `billing` not to `overview`**: Per the architecture doc, Option A (redirect to first available settings page) is preferred. `billing` is chosen as the landing page because it is relevant to all users (non-admin users can view their plan), it is not admin-only, and it is not coming soon. This provides immediate value on settings navigation.

---

## Summary: Full Slice Listing

| Slice | Epic | Summary | Files Changed | Tests |
|-------|------|---------|---------------|-------|
| **332A** | 332 | Nav-items data model (`NavGroup`, `NAV_GROUPS`, `UTILITY_ITEMS`, `SETTINGS_ITEMS`, derived `NAV_ITEMS`) | `lib/nav-items.ts` (M), `lib/__tests__/nav-items.test.ts` (C) | 8 |
| **333A** | 333 | `NavZone` component + `DesktopSidebar` refactor | `components/nav-zone.tsx` (C), `components/desktop-sidebar.tsx` (M), `components/__tests__/nav-zone.test.tsx` (C) | 5 |
| **333B** | 333 | Search pill + chevron + zone polish for `DesktopSidebar` | `components/desktop-sidebar.tsx` (M), `components/nav-zone.tsx` (M), `components/__tests__/desktop-sidebar.test.tsx` (C) | 4 |
| **334A** | 334 | `MobileSidebar` zone refactor reusing `NavZone` | `components/mobile-sidebar.tsx` (M), `components/nav-zone.tsx` (M), `components/__tests__/mobile-sidebar.test.tsx` (C) | 4 |
| **335A** | 335 | `CommandPaletteDialog` + `CommandPaletteProvider` + org layout mount | `components/command-palette-dialog.tsx` (C), `components/command-palette-provider.tsx` (C), `app/(app)/org/[slug]/layout.tsx` (M), `components/__tests__/command-palette-dialog.test.tsx` (C) | 5 |
| **335B** | 335 | `RecentItemsContext` + Recent group in palette | `components/recent-items-provider.tsx` (C), `components/command-palette-dialog.tsx` (M), `components/__tests__/recent-items-provider.test.tsx` (C) | 4 |
| **336A** | 336 | `SETTINGS_NAV_GROUPS` + `SettingsSidebar` structure + `settings/layout.tsx` | `components/settings/settings-nav-groups.ts` (C), `components/settings/settings-sidebar.tsx` (C), `app/(app)/org/[slug]/settings/layout.tsx` (C), `components/__tests__/settings-sidebar.test.tsx` (C) | 4 |
| **336B** | 336 | `SettingsSidebar` visual polish (active highlight, group headers, Coming Soon, mobile tabs) | `components/settings/settings-sidebar.tsx` (M) | 5 |
| **337A** | 337 | Settings redirect + breadcrumb labels + command palette context wiring | `app/(app)/org/[slug]/settings/page.tsx` (M), `components/breadcrumbs.tsx` (M), `components/command-palette-provider.tsx` (M), `components/desktop-sidebar.tsx` (M), `components/__tests__/breadcrumbs.test.tsx` (C) | 5 |

**Total**: 10 slices, ~44 tests, ~15 new/modified files. Frontend only.

---

### Critical Files for Implementation

- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/lib/nav-items.ts` - Core data model to restructure; source of truth for NAV_GROUPS, UTILITY_ITEMS, SETTINGS_ITEMS; all sidebar and palette epics depend on this file
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/desktop-sidebar.tsx` - Primary sidebar component to refactor; establishes the zone rendering pattern that mobile-sidebar mirrors
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/settings/page.tsx` - Settings hub to replace with redirect; contains the full 23-card definitions that must be migrated to SETTINGS_ITEMS and SETTINGS_NAV_GROUPS
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/layout.tsx` - Org layout shell where CommandPaletteProvider mounts; must remain a Server Component throughout
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/breadcrumbs.tsx` - Breadcrumb component requiring 23+ new segment label entries for settings subpages; currently missing most settings paths
