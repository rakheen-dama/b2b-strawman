# Phase 73 — Matter Detail Page Redesign (Sidebar + Grouped Tabs)

## System Context

Kazi is a multi-tenant B2B practice-management platform. The **matter detail page** (`/org/[slug]/projects/[id]`) is the most-visited page in the product — every attorney lands here multiple times per day to check status, log time, review documents, and manage tasks.

The current layout has grown organically through 72 phases. It stacks everything vertically: header → custom fields → 21 flat tabs → tab content. The result:

- **Custom fields consume the viewport.** A matter with SA Legal field groups (Case Number, Court, Opposing Party, Opposing Attorney, Date of Instruction, Estimated Value, etc.) pushes the tab bar below the fold. Users scroll past fields they rarely edit to reach content they always need.
- **Long matter names and descriptions break the layout.** The header uses `flex-1` with `min-w-0` but no max-width or truncation. Combined with 6+ action buttons squeezing the right side, long titles like "Sipho Dlamini v Road Accident Fund" collapse into a single-word-width column where each word renders on its own line. The description does the same — eating 2+ screens of vertical space while the right 70% of the page is empty.
- **21 tabs create visual noise.** The tab bar (Overview, Documents, Members, Customers, Tasks, Time, Expenses, Budget, Financials, Staffing, Rates, Generated Docs, Requests, Client Comments, Court Dates, Adverse Parties, Trust, Disbursements, Statements, Activity, Audit) wraps or scrolls horizontally. Module-gated tabs help, but a typical legal-za tenant still sees 15+ tabs.
- **Activity feeds push content down.** The Overview tab crams health status, recent activity, budget, time, team, task status, and upcoming deadlines into a long scroll. Activity items compound the vertical sprawl.
- **Action buttons overflow.** Close Matter, Generate Statement, Complete Matter, Generate Document, New Engagement Letter, Save as Template, Edit, Delete — these wrap onto multiple rows or scroll horizontally.

### Predecessor systems this phase restructures (frontend only)

- **Main page component:** `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` (~965 lines, server component)
- **Tab orchestrator:** `frontend/components/projects/project-tabs.tsx` (Radix TabsPrimitive, 21 tab definitions)
- **Custom fields:** `frontend/components/field-definitions/CustomFieldSection.tsx` (client component, grouped field rendering)
- **Field group selector:** `frontend/components/field-definitions/FieldGroupSelector.tsx`
- **Activity feed:** `frontend/components/activity/activity-feed.tsx` (server component + ActivityFeedClient)
- **Overview tab:** `frontend/components/projects/overview-tab.tsx` (health, metrics, activity, budget, team, tasks, deadlines)
- **All panel components** imported by ProjectTabs (DocumentsPanel, TaskListPanel, TimeSummaryPanel, BudgetPanel, etc.)
- **Promoted fields:** `frontend/lib/constants/promoted-field-slugs.ts`

### What this phase does NOT change

- **No backend changes.** No new API endpoints, no entity changes, no migrations. All data fetching stays the same.
- **No new features.** This is a layout restructure. Every current capability (custom fields, tabs, activity, actions) remains — just reorganised.
- **No customer detail page.** The customer detail page has similar issues but is out of scope. It can follow the same pattern in a future phase.

## Objective

Redesign the matter detail page from a **vertical-stack layout** to a **sidebar + main content** layout that:

1. Puts matter identity and custom fields in a collapsible sidebar, freeing the main area for tabbed content.
2. Groups 21 flat tabs into 5–6 logical categories with dropdown sub-navigation.
3. Condenses the Overview tab into a single-screen KPI dashboard (no activity feed, no team roster).
4. Moves action buttons to the sidebar bottom + a compact overflow menu.
5. Truncates long matter names and descriptions with expand-on-demand.

## Constraints & Assumptions

- **Frontend-only phase.** No `backend/` changes. The server component in `page.tsx` continues to fetch all the same data; only the rendering structure changes.
- **Shadcn UI + Radix primitives.** Use existing design system components. New components (sidebar, grouped tabs, dropdown menus) should follow Shadcn patterns.
- **Progressive disclosure over information density.** The default view should show the minimum needed to orient + act. Details expand on demand (custom fields in sidebar scroll, description expands, grouped tabs reveal sub-tabs).
- **URL-based tab state preserved.** The current `?tab=<id>` URL parameter continues to work. Grouped tabs should support `?tab=time` resolving to the Finance group → Time sub-tab.
- **Module-gating preserved.** Court Dates, Adverse Parties, Trust, Disbursements, and Statements remain gated by their respective modules. If all items in a group are gated off, the group itself hides.
- **No breaking changes to data fetching.** The page.tsx server component fetches all panel data upfront. This phase restructures how that data flows to the new layout components, but does not change what is fetched.
- **Keyboard navigation preserved.** Tab groups must remain keyboard-navigable (arrow keys, Enter to expand group, Tab to move between groups).
- **Responsive behavior.** Sidebar collapses to a slide-out drawer on screens below `lg` breakpoint (1024px). Main content becomes full-width.

---

## Section 1 — Sidebar Component

### 1.1 Layout shell

New component: `MatterDetailLayout` (or refactor in `page.tsx`).

```
+---[sidebar]---+---[main content]------------------+
|  280px fixed  |  fluid, min-w-0                    |
|  collapsible  |                                    |
|  overflow-y   |  breadcrumb + overflow actions      |
|  auto         |  grouped tab bar                   |
|               |  tab content                       |
+---------------+------------------------------------+
```

- Sidebar width: `280px` (collapsed: `0px` on mobile, icon-toggle on desktop).
- Sidebar has its own vertical scroll, independent of main content.
- A toggle button (chevron or hamburger) collapses/expands the sidebar. State persisted in `localStorage` per user preference.
- Below `lg` breakpoint: sidebar renders as a slide-out sheet (Shadcn `Sheet` component, side="left").

### 1.2 Sidebar content sections

The sidebar contains the following sections, top to bottom:

**A. Matter identity**
- Matter name: `text-lg font-semibold`, max 3 lines, truncated with ellipsis + tooltip for full name. Click to edit (inline, same as current edit flow).
- Status badge: inline with name (Active, Closed, Archived, Completed).
- Description: collapsed by default (show first 2 lines). "Show more" link expands. Full description wraps normally within the 280px sidebar width — no single-word-per-line problem because the column is fixed-width, not squeezed.

**B. Key metadata**
- Client: name (link to customer detail), or "Internal Matter" label.
- Reference number: displayed as a chip/badge.
- Matter type / work type: if set.
- Priority: if set (badge with colour).
- Due date: with overdue styling (red text + icon if past due).
- Created date.
- Assigned attorney / responsible member: if the concept exists.

Display as a compact key-value list (`text-sm`, `text-muted-foreground` for labels, regular weight for values). No card wrapper — just clean vertical list with `space-y-2`.

**C. Custom fields (scrollable)**
- Render existing `CustomFieldSection` within the sidebar, constrained to sidebar width.
- Each field group is a collapsible section (Shadcn `Collapsible` or `Accordion`). Default: first group expanded, rest collapsed.
- Field groups scroll independently within the sidebar's overflow area.
- The `FieldGroupSelector` (add/remove field groups) renders at the bottom of the custom fields section.
- "Save Custom Fields" button appears only when there are unsaved changes (sticky at bottom of custom fields area).

**D. Tags**
- Existing `TagInput` component, rendered at sidebar width.

**E. Actions (sidebar footer)**
- Primary lifecycle action: **Complete Matter** / **Close Matter** / **Reopen** / **Restore** (depending on current status). Full-width button, pinned to sidebar bottom.
- This section is sticky — always visible even when sidebar content scrolls.

### 1.3 Sidebar collapse behaviour

- **Desktop (>= lg):** Toggle button in sidebar header. Collapsed state hides sidebar entirely (main content expands to full width). No icon-rail intermediate state for v1 — keep it simple.
- **Mobile (< lg):** Sidebar is a `Sheet` triggered by a button in the breadcrumb area. Sheet slides in from left, overlays main content.
- Collapse preference stored in `localStorage` key `kazi-matter-sidebar-collapsed`.

---

## Section 2 — Grouped Tab Bar

### 2.1 Tab groups

Replace the flat 21-tab `ProjectTabs` with a grouped tab bar. Each group is a top-level item in the tab bar. Groups with multiple items show a dropdown on hover/click. Groups with a single visible item render as a plain tab (no dropdown).

| Group | Sub-tabs | Notes |
|-------|----------|-------|
| **Overview** | (none — standalone tab) | KPI dashboard, always first |
| **Work** | Tasks, Documents, Generated Docs, Staffing | Core operational content |
| **Finance** | Time, Expenses/Disbursements, Budget, Rates, Financials, Statements, Trust | Revenue + cost tracking. "Expenses" label when disbursements module is off; "Disbursements" when on. Trust & Statements only when trust_accounting module is on. |
| **Client** | Customers, Requests, Client Comments, Adverse Parties | Client-facing content. Adverse Parties only when conflict_check module is on. |
| **Schedule** | Court Dates | Only visible when court_calendar module is on. If this is the only module-gated group and it's off, the group hides entirely. |
| **Activity** | Activity Feed, Audit | Audit only visible with TEAM_OVERSIGHT capability. If only Activity Feed is visible, render as a plain tab (no dropdown). |

**Behaviour:**
- Clicking a group label opens its first sub-tab (e.g., clicking "Work" opens Tasks).
- The dropdown appears on click (not hover — hover is unreliable on touch devices).
- Active sub-tab is indicated in the dropdown with a checkmark or highlight.
- The group label shows which sub-tab is active: e.g., "Finance · Time" or just bold the group when any of its sub-tabs is active.

### 2.2 URL state

- Tab URL parameter continues to work: `?tab=tasks` → Work group opens with Tasks selected.
- Group-level navigation: `?tab=finance` → opens Finance group's first sub-tab (Time).
- Backward compatibility: all existing tab IDs (`overview`, `documents`, `members`, `tasks`, `time`, `expenses`, `budget`, `financials`, `staffing`, `rates`, `generated-docs`, `requests`, `client-comments`, `court-dates`, `adverse-parties`, `trust`, `disbursements`, `statements`, `activity`, `audit`) continue to resolve correctly.

### 2.3 Keyboard navigation

- Left/Right arrow keys move between groups.
- Enter or Space opens a group's dropdown.
- Up/Down arrow keys navigate within a dropdown.
- Enter selects a sub-tab and closes the dropdown.
- Escape closes an open dropdown without changing selection.

### 2.4 Component structure

New component: `GroupedTabBar` (replaces `ProjectTabs`'s tab trigger section).

Props:
- `groups: TabGroup[]` where `TabGroup = { id: string, label: string, tabs: TabDefinition[], visible: boolean }`.
- `activeTab: string` (the sub-tab ID).
- `onTabChange: (tabId: string) => void`.

The tab content panels remain the same — each sub-tab renders its existing panel component. Only the navigation chrome changes.

---

## Section 3 — Condensed Overview Tab

### 3.1 KPI dashboard

The Overview tab becomes a **single-screen-height** dashboard. No scrolling needed for the default view.

Layout: CSS Grid, responsive.
- **Desktop (>= lg):** 3-column grid of metric cards + a deadlines list.
- **Tablet (md):** 2-column grid.
- **Mobile:** single column stack.

**Metric cards (4–6, depending on modules):**

| Metric | Source | Always visible? |
|--------|--------|-----------------|
| Budget consumed (%) | Budget entity | If budget is set |
| Hours logged (this period) | TimeEntry aggregation | Always |
| Task completion (%) | Task counts (done / total) | Always |
| Days to deadline | Project due date | If due date is set |
| Trust balance | Trust accounting module | If trust_accounting module on |
| Outstanding invoices | Statements module | If disbursements module on |

Each card: icon, metric value (large), label (small), trend indicator if applicable (up/down arrow + delta vs. prior period). Click navigates to the relevant tab.

**Health ring:** Existing project health visualization, compact version. Positioned as a header element above the grid, not a card.

**Upcoming deadlines list:** Below the metric grid. Max 5 items, sorted by date. Each item: date, description, link to relevant entity. "View all" link navigates to the Schedule tab (or a dedicated deadlines view if it exists).

### 3.2 Removed from Overview

These items move to their own tabs and are **not** in the Overview anymore:
- Recent Activity → Activity tab
- Team roster → Work > Staffing sub-tab (or remain accessible via Members)
- Task status breakdown → Work > Tasks sub-tab
- Time breakdown → Finance > Time sub-tab
- Budget details → Finance > Budget sub-tab

---

## Section 4 — Action Button Relocation

### 4.1 Primary action in sidebar

The primary lifecycle action (context-dependent) renders as a full-width button in the sidebar footer:

| Matter status | Primary action |
|---------------|---------------|
| ACTIVE | Complete Matter |
| ACTIVE (legal-za with closure module) | Close Matter |
| COMPLETED | Reopen (if permitted) |
| CLOSED | Reopen (if permitted) |
| ARCHIVED | Restore |

### 4.2 Overflow menu in main area

A `...` (MoreHorizontal icon) dropdown button, positioned at the top-right of the main content area (same row as breadcrumb or tab bar).

Menu items:
- Generate Document (with sub-menu for template selection)
- Generate Statement of Account (module-gated)
- New Engagement Letter
- Save as Template
- Edit Matter
- Archive Matter
- Delete Matter (owner only, with confirmation dialog)

Items are gated by the same permission/module checks as the current buttons.

---

## Section 5 — Title and Description Truncation

### 5.1 Matter name

- In the sidebar: `text-lg font-semibold`, `line-clamp-3` (3 lines max). Full text available via tooltip on hover and accessible via the Edit action.
- No more `h1` rendering in a squeezable flex column. The sidebar's fixed 280px width guarantees consistent wrapping.

### 5.2 Description

- In the sidebar: `text-sm text-muted-foreground`, `line-clamp-2` by default. "Show more" button expands to full description. "Show less" collapses back.
- The expand/collapse state is not persisted — defaults to collapsed on page load.

### 5.3 Breadcrumb

The breadcrumb in the main content area shows: `Org Name > Matters > [Matter Name (truncated)]`. The matter name in the breadcrumb is truncated with `truncate` (single line, ellipsis).

---

## Section 6 — Migration Path

### 6.1 Component refactoring strategy

This phase restructures existing components, not rewrites. The approach:

1. **Extract sidebar** from the current `page.tsx` header section. The metadata, custom fields, tags, and action buttons move from the main flow into the new `MatterSidebar` component.
2. **Wrap in layout shell.** `page.tsx` renders `MatterDetailLayout` which contains `MatterSidebar` + main content area.
3. **Replace tab triggers.** `ProjectTabs` keeps its content panels but replaces its trigger row with `GroupedTabBar`.
4. **Slim Overview.** `OverviewTab` drops activity feed, team, task breakdown, time breakdown, budget details. Retains health ring and adds KPI metric cards.
5. **Existing panel components unchanged.** `DocumentsPanel`, `TaskListPanel`, `TimeSummaryPanel`, etc. render identically — they just live inside the new main content area instead of the old full-width layout.

### 6.2 QA testplan / demo script audit

The layout changes affect every QA lifecycle script that interacts with the matter detail page. Before merging, audit and update:

- **`qa/testplan/demos/*.md`** — any step that clicks a specific tab by name (e.g., "click the Time tab") must be updated to reflect grouped tab navigation (e.g., "open Finance group → click Time").
- **Playwright selectors** — any selector targeting the flat tab bar (`[role="tablist"]`, tab trigger text content) will break. Update to match the new `GroupedTabBar` structure.
- **Screenshot baselines** — any checkpoint screenshot of the matter detail page will show the old layout. Re-capture after the redesign lands.
- **Action button locators** — scripts clicking "Complete Matter", "Generate Document", etc. in the header toolbar must be updated to find them in the sidebar footer or overflow menu.

This audit is a required task in the epic breakdown, not a post-merge afterthought.

### 6.3 No feature flags

This is a direct replacement, not an A/B test. The old layout is replaced. If a rollback is needed, it's via git revert.

---

## Out of Scope

- **Customer detail page redesign.** Same pattern could apply, but it's a separate phase.
- **Mobile-native layouts.** The sidebar collapses to a sheet — no native app-style bottom tabs or swipe navigation.
- **Drag-and-drop tab reordering.** Tab groups are fixed in code, not user-configurable.
- **User-configurable sidebar sections.** The sidebar content order is fixed. No "move Tags above Custom Fields" customisation.
- **New backend APIs.** Everything is frontend restructuring of existing data.
- **Dashboard analytics beyond KPI cards.** No charts, no trend graphs, no date-range pickers on the Overview. That's a reporting feature, not a layout fix.
- **Saved views or layout presets.** No "compact mode" vs. "full mode" toggle beyond sidebar collapse.

---

## ADR Topics

- **ADR-286: Sidebar vs. full-width layout for entity detail pages.** Documents the trade-off between sidebar layouts (better information architecture, sidebar scroll independence, consistent metadata location) and full-width stacked layouts (simpler component tree, no width constraints on fields). Records the decision to go sidebar-first for the matter page and when to apply the same pattern to customer/other entity pages.

- **ADR-287: Grouped tabs pattern for dense navigation.** Documents the grouped tab bar pattern (dropdown sub-navigation within tab groups), how it composes with URL state, keyboard accessibility requirements, and the threshold at which flat tabs should be grouped (guidance: >8 visible tabs → group).

---

## Style and Boundaries

- Read `frontend/CLAUDE.md` before touching any component.
- All new components use Shadcn UI primitives (Sheet, Collapsible, DropdownMenu, Tooltip, Badge).
- Tailwind v4 utility classes only — no custom CSS files.
- The sidebar width (280px) is a design token, not a magic number. Define it in the Tailwind config or as a CSS variable.
- Test the layout with: (a) short matter names ("NDA Review"), (b) long matter names ("Sipho Dlamini v Road Accident Fund — High Court Johannesburg — Case No 2026/12345"), (c) no custom fields, (d) 3+ custom field groups, (e) all modules enabled (max tabs), (f) no modules enabled (min tabs).
- The page must remain a server component at the top level (data fetching). Client components are used for interactive elements (sidebar toggle, grouped tab bar, collapsibles).
