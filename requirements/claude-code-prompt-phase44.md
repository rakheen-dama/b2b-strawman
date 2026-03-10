# Phase 44 — Navigation Zones, Command Palette & Settings Modernization

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) for professional services firms. After 43 phases, the platform has grown to 77 pages with 15 top-level sidebar navigation items, 23 settings cards, and capability-based gating per role. The sidebar navigation is a flat, ungrouped list that grew organically — there is no visual hierarchy, no logical sectioning, and no progressive disclosure. The settings page uses a card grid layout that requires navigating away to each individual setting, with no persistent context of where you are within settings.

**The problem**: The flat sidebar is overwhelming (15 items exceeds the 5-7 cognitive grouping threshold). Items are ordered arbitrarily rather than by user mental model. Settings uses a dated card-grid pattern rather than the modern sidebar + content pane layout that users expect from tools like Claude Desktop, Linear, or Notion. There is no quick-navigation mechanism for power users who know where they want to go.

**The fix**: Three pillars — (1) group sidebar items into collapsible zones with sensible defaults, (2) add a command palette (⌘K) for instant navigation, (3) modernize settings into a sidebar + content pane layout with logical groupings.

## Objective

1. **Restructure sidebar navigation into logical zones** — replace the flat 15-item list with 5-6 collapsible groups that reflect how users think about the product (work, delivery, clients, finance, team). Zones collapse/expand on click. No persistence — sensible defaults based on capability profile.
2. **Add a command palette (⌘K)** — a keyboard-first quick-jump dialog that lets users navigate to any page, setting, or entity by typing. Built on Shadcn's `CommandDialog` (cmdk). Fuzzy search across all nav items, settings pages, and recent entities.
3. **Modernize settings layout** — replace the card grid with a persistent sidebar + content pane pattern. Settings sections render inline without full page navigation. The 23 settings are reorganized into 6 logical groups. Existing URL routes preserved for bookmarkability.

## Constraints & Assumptions

- **Frontend-only phase.** No backend changes. No new API endpoints. No database changes.
- **No state persistence for sidebar collapse.** Zones default to a sensible expanded/collapsed state. If a zone has zero visible items (all capability-gated), it is hidden entirely. No localStorage, no backend preference storage. If this becomes a complaint, localStorage persistence is a trivial follow-up.
- **Existing nav-items structure evolves.** The current `NavItem` interface in `lib/nav-items.ts` gains a `group` field. The flat `NAV_ITEMS` array is replaced with a `NAV_GROUPS` structure. Both `desktop-sidebar.tsx` and `mobile-sidebar.tsx` consume the new structure.
- **Command palette is global.** Mounted once in the org layout, triggered by ⌘K (Mac) / Ctrl+K (Windows). Searches nav items, settings pages, and optionally recent projects/customers (fetched on open, cached).
- **Settings layout uses nested routing.** The settings hub page becomes a layout with a sidebar. Individual settings pages render in the content area. Existing `/org/[slug]/settings/rates` URLs continue to work. The settings sidebar is always visible when on any settings page.
- **Shadcn components only.** `CommandDialog` for the palette, existing sidebar primitives for settings nav. No new UI library dependencies.
- **Design system compliance.** All new components follow Signal Deck conventions: slate palette, Sora headings, teal accents, Motion animations for expand/collapse. See `frontend/CLAUDE.md` for full design system spec.
- **Mobile responsive.** Sidebar zones work in the mobile sheet sidebar. Settings sidebar collapses to a dropdown or stacked layout on mobile. Command palette works on mobile (tap trigger in header, no keyboard shortcut).
- **Capability gating preserved.** Zones that contain only capability-gated items hidden from users without those capabilities. Individual item gating within zones unchanged.

---

## Section 1 — Sidebar Navigation Zones

### Zone Structure

Replace the flat `NAV_ITEMS` array with a grouped structure:

```typescript
interface NavGroup {
  id: string;
  label: string;          // Displayed as section header
  items: NavItem[];
  defaultExpanded: boolean; // Default collapse state
}
```

**Zone definitions (in order):**

| Zone ID | Label | Items | Default State |
|---------|-------|-------|---------------|
| `work` | Work | Dashboard, My Work, Calendar | Expanded |
| `delivery` | Delivery | Projects, Documents, Recurring Schedules | Expanded |
| `clients` | Clients | Customers, Retainers, Compliance | Expanded (if any items visible) |
| `finance` | Finance | Invoices, Profitability, Reports | Expanded (if any items visible) |
| `team` | Team & Resources | Team, Resources | Expanded |

**Utility items** (not in a collapsible zone, pinned to sidebar bottom):
- Notifications (with bell icon + unread badge)
- Settings

### Visual Design

- **Zone headers**: Uppercase, `text-[11px] font-medium tracking-widest text-slate-500` (muted, doesn't compete with items). Clickable to expand/collapse.
- **Collapse indicator**: Small chevron (`ChevronRight` rotated 90° when expanded) next to zone label. Subtle — not the focus.
- **Collapse animation**: Motion `AnimatePresence` with `height: auto` transition (~150ms, ease-out). Items fade + slide.
- **Divider**: Thin `border-t border-slate-800` between zones (sidebar is dark, so use slate-800).
- **Active state**: Unchanged — teal left-border indicator on active item.
- **Empty zones**: If all items in a zone are hidden by capability gating, the entire zone (header + items) is hidden.

### Mobile Sidebar

The mobile sheet sidebar (`mobile-sidebar.tsx`) uses the same zone structure. Zones are collapsible via tap. Same visual treatment adapted for light background (mobile sidebar uses sheet, which is light-themed).

### Nav Items File

Refactor `lib/nav-items.ts`:
- Export `NAV_GROUPS: NavGroup[]` instead of (or in addition to) `NAV_ITEMS`
- Each `NavItem` gains an optional `group` field for reverse-lookup
- Maintain backward compatibility: export a flat `NAV_ITEMS` derived from `NAV_GROUPS` for any code that needs the flat list (e.g., breadcrumbs, command palette indexing)

---

## Section 2 — Command Palette (⌘K)

### Trigger

- **Keyboard**: `⌘K` (Mac) / `Ctrl+K` (Windows). Global listener in org layout.
- **Click**: Small search icon or "Search..." pill in the sidebar header area (above zone list). Also works as mobile trigger.
- **Focus trap**: When open, palette captures all keyboard input. `Escape` closes.

### UI

Built on Shadcn `CommandDialog` (wraps cmdk):

```
┌──────────────────────────────────────┐
│ 🔍 Search pages, settings, items... │
├──────────────────────────────────────┤
│ Pages                                │
│   Dashboard                          │
│   My Work                            │
│   Projects                           │
│   ...                                │
│ Settings                             │
│   Rates & Currency                   │
│   Time Tracking                      │
│   Roles & Permissions                │
│   ...                                │
│ Recent                               │
│   Project: Acme Audit 2026           │
│   Customer: Smith & Partners         │
└──────────────────────────────────────┘
```

### Search Behavior

- **Fuzzy matching** via cmdk's built-in filter (no custom implementation needed)
- **Groups**: "Pages" (all nav items), "Settings" (all 23 settings pages), "Recent" (last 5 visited projects/customers from a lightweight in-memory cache — populated on palette open, not persisted)
- **Selection**: Arrow keys to navigate, Enter to go. Mouse click also works.
- **Navigation**: On select, close palette and router.push to the selected item's href.
- **Empty state**: "No results found" with suggestion text.

### Item Index

The palette indexes:
1. All `NavItem`s from `NAV_GROUPS` (label + icon, capability-filtered for current user)
2. All settings cards (title + description, admin-filtered)
3. Recent entities (optional — if implementation is straightforward, include; otherwise defer). These would be the last ~5 projects and customers the user visited, tracked in a simple React context or zustand store (in-memory only, resets on refresh).

### Keyboard Shortcut Hint

Show a subtle `⌘K` badge in the sidebar search area so users discover the shortcut. On the command palette itself, show "Esc to close" at the bottom.

---

## Section 3 — Settings Layout Modernization

### Layout Architecture

Convert the settings page from a card grid to a sidebar + content pane layout:

```
/org/[slug]/settings/layout.tsx    ← NEW: settings shell with sidebar + content area
/org/[slug]/settings/page.tsx      ← MODIFIED: becomes redirect to first settings page (or overview)
/org/[slug]/settings/[section]/page.tsx ← EXISTING: individual settings pages render in content pane
```

The settings layout renders:
- **Left sidebar** (~240px): Grouped navigation links to each settings section
- **Content area** (flex-1): The active settings page (rendered via `{children}` from the layout)

### Settings Groups

Reorganize the 23 settings into 6 groups:

| Group | Label | Settings (in order) |
|-------|-------|---------------------|
| `general` | General | Organization*, Billing, Notifications, Email†, Security* |
| `work` | Work | Time Tracking, Project Templates, Project Naming, Automations† |
| `documents` | Documents | Templates, Clauses, Checklists, Document Acceptance |
| `finance` | Finance | Rates & Currency, Tax, Batch Billing†, Capacity |
| `clients` | Clients | Custom Fields, Tags, Request Templates, Request Settings, Compliance |
| `access` | Access & Integrations | Roles & Permissions†, Integrations |

*\* = Coming soon (Organization, Security)*
*† = Admin-only (Email, Automations, Batch Billing, Roles & Permissions)*

### Settings Sidebar Visual Design

- **Background**: White/card background (not dark like the main sidebar)
- **Group headers**: Same style as main sidebar zone headers — uppercase, small, muted
- **Active item**: Teal left border or teal background tint, matching Signal Deck active states
- **Coming soon items**: Muted text + "Coming soon" badge, not clickable
- **Admin-only items**: Hidden for non-admin users (same behavior as current card grid)
- **Sticky**: Settings sidebar is sticky (`sticky top-0`) so it stays visible while scrolling content

### Settings Hub Page

When navigating to `/org/[slug]/settings` (no subsection), the content pane shows:
- **Option A (preferred)**: Redirect to the first available (non-coming-soon) settings page (likely "Billing")
- **Option B**: A minimal overview with the org name and a "Select a setting from the sidebar" prompt

Architect decides, but Option A is more useful — users always land somewhere actionable.

### Breadcrumbs

Update breadcrumb component to show `Settings > Rates & Currency` (two levels) when on a settings subpage. The "Settings" segment links back to the settings hub.

### Mobile Settings

On screens below `md` breakpoint:
- Settings sidebar becomes a horizontal scrollable tab bar at the top of the settings area, OR
- A dropdown/select that shows the current section and allows switching

Architect decides the mobile pattern. The key constraint: the user must always be able to switch settings sections without navigating back to a hub page.

---

## Section 4 — Sidebar Order & Polish

### Item Reordering

The current item order is arbitrary (grew organically). The new zone-based order reflects user mental models:

**Work zone** (daily drivers — what most users touch first):
1. Dashboard
2. My Work
3. Calendar

**Delivery zone** (the work itself):
4. Projects
5. Documents
6. Recurring Schedules

**Clients zone** (who you work for):
7. Customers
8. Retainers
9. Compliance

**Finance zone** (money):
10. Invoices
11. Profitability
12. Reports

**Team & Resources zone** (people):
13. Team
14. Resources

**Utility footer** (always visible, not collapsible):
15. Notifications
16. Settings

### Sidebar Header

The top of the sidebar currently shows the org name/switcher. Below that, add:
- A subtle search trigger (`⌘K` pill or magnifying glass icon) that opens the command palette
- This serves as both the discovery mechanism for the palette and the mobile trigger

---

## Out of Scope

- **Sidebar collapse persistence** — no localStorage, no backend preferences. Sensible defaults only.
- **Contextual completeness on detail pages** — auditing what actions are available on project/customer/invoice detail pages is a separate phase (likely fork-specific).
- **Backend changes** — this is entirely a frontend phase. No API endpoints, no database migrations.
- **i18n migration of nav labels** — the Phase 43 message catalog can adopt nav labels later. This phase uses hardcoded strings in the nav config (matching current pattern).
- **Sidebar resize/drag** — fixed-width sidebar. No user-adjustable width.
- **Favorites/pinned items** — no user-customizable nav items. If needed, this is a follow-up.
- **Deep-link search in command palette** — the palette searches pages and settings, not individual entities in the database (projects, customers, etc.) beyond a lightweight recent-items cache. Full search is a separate feature.
- **Settings page content changes** — this phase only changes the settings *layout and navigation*. The content of individual settings pages is unchanged.

## ADR Topics

1. **Sidebar zone structure** — fixed zones vs. user-configurable groups. Recommend fixed zones with capability-based visibility (simpler, consistent, aligns with product mental model).
2. **Command palette scope** — page-only search vs. entity search. Recommend page-only for v1 with optional recent-items cache. Full entity search requires backend search endpoint.
3. **Settings layout pattern** — nested layout with URL preservation vs. client-side tab switching. Recommend nested layout (SSR-friendly, bookmarkable, shareable URLs).

## Style & Boundaries

- Follow Signal Deck design system (slate palette, Sora display font, teal accents, Motion animations)
- All new components in `components/` following existing patterns
- Shadcn UI primitives only — `CommandDialog`, `Collapsible`, `ScrollArea`, `Tooltip`
- Mobile-first responsive design
- No new dependencies beyond what Shadcn's `CommandDialog` requires (cmdk is already a Shadcn dependency)
- Test coverage: component tests for sidebar zone rendering, capability filtering, command palette search, settings sidebar navigation
