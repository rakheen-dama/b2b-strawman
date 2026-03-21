# Phase 53 — Dashboard Polish, Navigation Cleanup & Print-Accurate Previews

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with 52 phases of functionality serving professional services firms (accounting, legal, consulting). The platform has three main dashboard surfaces: an Org Dashboard (company overview), My Work (personal cockpit), and Project Detail (15-tab workspace). The frontend uses the "Signal Deck" design system — dark slate sidebar, cool slate/teal palette, Sora/IBM Plex Sans/JetBrains Mono font stack, Shadcn UI + Tailwind CSS v4.

**The existing surfaces this phase polishes:**

- **Org Dashboard** (`/org/[slug]/dashboard`): 3-column layout with KPI cards (active projects, hours logged, overdue tasks), project health widget, team workload/capacity widgets, my schedule, deadline widget, incomplete profiles widget, info requests widget, automations widget, and recent activity feed. Uses Recharts for charts. Getting Started card for new orgs.
- **My Work** (`/org/[slug]/my-work`): Personal KPIs (hours, billable %, overdue tasks), time breakdown pie chart, upcoming deadlines, assigned/unassigned task lists, weekly time summary, expenses widget. Date range defaults to current week.
- **Project Detail Overview tab** (`/org/[slug]/projects/[id]?tab=overview`): Setup progress cards, project health header, overview metrics strip, activity feed, member hours breakdown, task summary, budget status card, template readiness, unbilled time summary.
- **Shared dashboard components** (`components/dashboard/`): KPI cards, sparklines, chart wrappers, metric strips. Currently using basic Recharts bar/line/pie charts.
- **Sidebar navigation** (`lib/nav-items.ts`): 5 zone groups (Work, Delivery, Clients, Finance, Team & Resources) plus footer utility items (Notifications, Settings).
- **Document preview**: Template preview and generated document preview render HTML in an unconstrained container — font renders at normal size but page dimensions are not A4-proportioned, making the preview inaccurate compared to print/PDF output.
- **Org Documents page** (`/org/[slug]/documents`): An org-level document listing page under the "Delivery" sidebar zone. Originally a strawman concept for org-level compliance docs (BEE certificates, practice certificates). Not connected to any meaningful workflow — these docs are settings-tier items that should live under organization settings, not in the daily-use sidebar.

**The goal**: Make these surfaces screenshot-ready for the landing page. A prospect should look at the dashboard screenshots and immediately grasp "this product is powerful and polished." The aesthetic reference is **Scoro's dashboard layouts and chart styles** — data-dense, rich visualizations, professional. Applied to our existing dark/slate Signal Deck palette with less white space than current implementation.

## Objective

1. **Org Dashboard visual redesign** — Transform from a flat widget grid into a composed, visually rich company overview. Bold metrics strip with trend indicators and sparklines. Upgraded chart components — area charts with gradient fills, heatmaps or progress bars for team utilization, donut charts replacing basic pie charts. Tighter card composition with less white space. Admin-only widgets collapsed or tucked into a secondary section so the default view photographs well.

2. **My Work redesign** — Cockpit feel centered on "what do I need to do today." Today's agenda front-and-center (due tasks, scheduled time, meetings). Time tracking feeling effortless and integrated, not another widget to scan past. Weekly rhythm visible at a glance with a compact week strip. Less widget grid, more narrative flow.

3. **Project Detail Overview tab polish** — The hero tab that tells a project's story in one glance. Compact visual health indicator (not just text badges). Budget/time/team status as a unified visual strip. Recent activity condensed. Setup progress cards refined or collapsed once setup is complete.

4. **Chart component library upgrade** — Refresh the shared Recharts chart components across all dashboards. Area charts with gradient fills, consistent color palette (slate + teal accent tones), smooth animations, dark-mode-friendly styling. Donut charts, heatmap patterns, sparklines with micro-trends. These components power all three dashboards and the profitability page.

5. **Sidebar navigation cleanup** — Four changes:
   - Remove "Documents" from the sidebar entirely (org docs move to Settings)
   - Rename "Delivery" zone → "Projects"
   - Move "Proposals" from Finance zone → Clients zone
   - Rename "Team & Resources" zone → "Team"

6. **Org Documents relocated to Settings** — The org-level documents page content (BEE certs, compliance docs, practice certificates) moves to the Settings > Organization/General page as an "Org Documents" upload section. Remove the standalone `/org/[slug]/documents` page and its sidebar entry.

7. **Document preview print-accuracy** — Fix document preview containers (template preview, generated document preview) to render at proper A4 proportions (210mm × 297mm ratio). The preview should be a scaled-down but proportionally accurate representation of the printed/PDF output. Currently the container is unconstrained so the page appears half-size while fonts render at normal size, making it an inaccurate WYSIWYG preview.

## Constraints & Assumptions

- **Pure frontend phase.** No new backend endpoints, no new entities, no migrations. All data already exists — this phase changes how it's presented.
- **No new data fetching patterns.** Use the same server-component data fetching and SWR patterns already in place. Restructure the visual layout, not the data pipeline.
- **Preserve all existing functionality.** Every widget that currently exists must still be accessible — the goal is better composition and visual treatment, not removal. Admin-only widgets can be collapsed/grouped, not deleted.
- **Signal Deck design system.** All changes stay within the existing design language: slate OKLCH color scale, teal accents, Sora/IBM Plex Sans/JetBrains Mono font stack, motion animations via Framer Motion, Shadcn component foundation.
- **Scoro-inspired density.** Reference Scoro's dashboard aesthetic: data-dense, rich chart fills, professional/corporate feel. Not Linear/Notion (too sparse) or generic Bootstrap dashboards. Apply through our dark slate palette.
- **Screenshot-ready.** The primary success metric is "does this look great in a 16:9 landing page screenshot?" — design for a filled state with realistic data, not empty states.
- **Responsive but desktop-first for screenshots.** Dashboards should still work on tablet/mobile, but optimize the composition for 1440px+ desktop widths — that's what gets screenshotted.
- **Recharts stays.** Don't replace Recharts with a different chart library. Enhance the Recharts usage with better styling, gradient fills, custom tooltips, and consistent theming.
- **A4 print dimensions.** Document preview uses A4 paper size (210mm × 297mm). The preview container should maintain this aspect ratio and scale the rendered HTML proportionally.

---

## Section 1 — Org Dashboard Redesign

### 1.1 Metrics Strip

Replace the current 3-card KPI row with a full-width metrics strip across the top of the dashboard. Metrics:

- **Active Projects** — count with trend arrow (vs. previous period) and mini sparkline
- **Hours This Month** — total with billable/non-billable split shown as a micro stacked bar
- **Revenue This Month** — total invoiced amount with trend vs. previous month
- **Overdue Tasks** — count with severity coloring (amber if <5, red if ≥5)
- **Team Utilization** — percentage with a compact radial/gauge indicator
- **Budget Health** — count of projects on-track / at-risk / over-budget

Use JetBrains Mono for the primary numbers (tabular-nums). Subtle card backgrounds with left border accent color per metric category. Compact — the strip should be ~80-100px tall, not dominating the page.

### 1.2 Primary Dashboard Area (Two-Panel Hero)

Below the metrics strip, a two-panel layout:

**Left panel (~60% width) — Project Health Overview:**
- Clean table or card grid showing active projects with:
  - Project name + customer name
  - Health status indicator (colored dot or mini bar)
  - Budget consumption progress bar (thin, colored by threshold)
  - Hours logged vs. budget (compact text)
  - Task completion ratio (e.g., "12/18")
- Sortable by health status, budget %, name
- "View All Projects" link at bottom
- Filter tabs: All / At Risk / Over Budget

**Right panel (~40% width) — Team & Time:**
- **Team Utilization chart** — Horizontal bar chart or heatmap showing each team member's utilization for the current period. Color gradient from cool (under-utilized) to warm (over-utilized). Target line at 80%.
- **Hours by Project** — Donut chart with project breakdown, showing top 5 projects + "Other" slice. Gradient fills on slices. Center text shows total hours.

### 1.3 Secondary Dashboard Area

Below the hero panels, a three-column layout:

**Column 1 — Recent Activity:**
- Condensed activity feed (last 15 items)
- Compact rows: avatar/initials, action description, relative time
- Subtle alternating row backgrounds
- "View All" link to full activity

**Column 2 — Upcoming Deadlines:**
- Next 7-10 deadlines across all projects
- Date, project, task/milestone name
- Color-coded urgency (today = red, this week = amber, later = default)
- Module-gated: only shows if regulatory_deadlines module is active OR if projects have due dates

**Column 3 — Quick Stats / Admin Panel:**
- For admin/owner: Incomplete profiles count, pending info requests count, recent automation runs count — as compact stat cards, not full widgets
- For members: This column shows "My Week" summary — hours logged today, tasks completed this week, upcoming assigned tasks
- Keeps the admin widgets accessible without cluttering the main view

### 1.4 Getting Started Card

Keep the Getting Started card for new orgs but make it dismissible and move it to a banner position above the metrics strip (not inline with dashboard content). Once dismissed or once >3 projects exist, it auto-hides.

---

## Section 2 — My Work Redesign

### 2.1 Today's Agenda (Hero Section)

The top section should immediately answer "what should I work on right now?"

- **Today's Tasks** — Tasks due today or overdue, sorted by priority then due date. Each row shows task name, project, time estimate, and a quick "Log Time" action button. Compact list, not cards.
- **Time Logged Today** — A compact horizontal progress bar showing hours logged vs. target (from capacity settings). JetBrains Mono for the numbers. Shows "2h 15m / 8h" style.
- **Next Deadline** — The single most urgent upcoming deadline, prominently displayed with countdown.

### 2.2 Weekly Rhythm Strip

Below the agenda, a compact week strip (Mon–Sun) showing:
- Hours logged per day as small vertical bars (filled = logged, empty = remaining capacity)
- Current day highlighted
- Weekly total prominently displayed
- Clickable days to filter the task/time lists below

### 2.3 Work Panels (Two-Column)

**Left (~60%) — Tasks:**
- Tabs: My Tasks / Available (unassigned)
- Compact list with: task name, project, due date, priority indicator, time estimate
- Saved views selector (existing functionality, just better positioned)
- Inline quick actions: claim (for available), log time, mark complete

**Right (~40%) — Time & Activity:**
- **Today's Time Entries** — Compact list of time logged today with project, task, duration
- **This Week's Breakdown** — Small donut or stacked bar: hours by project, billable vs. non-billable
- **Recent Activity** — Last 5 items of personal activity (what you did, not what others did)

### 2.4 Expenses and Extended Widgets

Move the expenses widget and any secondary widgets below the main panels. These are accessible but not the first thing you see. Compact card summaries with "View All" links.

---

## Section 3 — Project Detail Overview Tab

### 3.1 Project Health Header

Replace the current text-heavy header area with a visual health indicator:
- **Health status** as a colored band or gradient bar across the top of the overview
- **Key metrics strip** below: Budget (spent/total with %), Hours (logged/estimated), Tasks (completed/total), Revenue (invoiced)
- Compact, ~60-80px tall, using JetBrains Mono for numbers

### 3.2 Setup Progress

- If project setup is incomplete, show a compact checklist bar (not individual cards) — "3/6 setup steps complete" with a progress bar and expandable detail
- Once all setup steps are done, hide this section entirely (don't show 6/6 forever)

### 3.3 Overview Body (Two-Panel)

**Left (~60%) — Activity & Tasks:**
- Recent project activity (last 10 items, compact)
- Task status summary — small stacked horizontal bar showing Open/In Progress/Done counts
- Upcoming task deadlines (next 5)

**Right (~40%) — Financial & Team:**
- Budget consumption — progress bar with threshold coloring
- Time breakdown — small donut: billable vs. non-billable, by member
- Team roster — compact avatar row with hours-this-period tooltip on hover
- Unbilled time callout (if >0, shows amount with "Generate Invoice" CTA)

---

## Section 4 — Chart Component Library Upgrade

### 4.1 Shared Chart Theme

Create a consistent chart theme configuration used across all Recharts instances:
- **Color palette**: Slate-based with teal accent. Primary series: `slate-400`, `teal-500`, `slate-600`. Secondary: `slate-300`, `teal-400`, `slate-500`. Use opacity for fills.
- **Area charts**: Gradient fills from color at top to transparent at bottom. Smooth curves (`type="monotone"`). No dots by default, dots on hover.
- **Donut charts**: Inner radius ~60%, outer radius ~80%. Rounded corners on segments. Center content (total/label).
- **Bar charts**: Rounded top corners (`radius={[4, 4, 0, 0]}`). Subtle hover state with brightness increase.
- **Tooltips**: Dark background (`slate-900`), white text, rounded, shadow. Consistent formatting across all charts.
- **Grid lines**: Subtle dashed lines (`stroke-dasharray="3 3"`), `slate-200` in light mode, `slate-700` in dark.
- **Axes**: `slate-500` text, no axis line (tick marks only).
- **Responsive containers**: All charts in `ResponsiveContainer` with appropriate aspect ratios.

### 4.2 New Chart Patterns

- **Sparkline component**: Tiny inline line/area chart (~80×24px) for use in metric strips. No axes, no labels, just the trend shape with gradient fill.
- **Radial gauge**: For utilization/percentage metrics. Arc from 0–100%, colored by threshold. Center text shows value.
- **Micro stacked bar**: Tiny horizontal stacked bar (~120×8px) for inline billable/non-billable or status breakdowns.

---

## Section 5 — Sidebar Navigation Cleanup

### 5.1 Changes to `nav-items.ts`

1. **Remove Documents** from the "Delivery" group entirely.
2. **Rename** the "Delivery" group to **"Projects"** (`id: "projects"`, `label: "Projects"`).
3. **Move Proposals** from the "Finance" group to the **"Clients"** group. Position after Customers (before Retainers).
4. **Rename** the "Team & Resources" group to **"Team"** (`label: "Team"`).

### 5.2 Resulting Structure

```
Work (expanded)     → Dashboard, My Work, Calendar, Court Calendar*
Projects (expanded) → Projects, Recurring Schedules
Clients (collapsed) → Customers, Proposals, Retainers, Compliance, Deadlines*
Finance (collapsed) → Invoices, Profitability, Reports, Trust Accounting*
Team (expanded)     → Team, Resources
─── footer ───
Notifications, Settings
```

Items marked * are module-gated and only appear when the relevant module is active.

---

## Section 6 — Org Documents → Settings

### 6.1 Remove Standalone Page

Delete or repurpose the `/org/[slug]/documents` page. Remove the sidebar navigation entry. Any routes or imports referencing this page should be cleaned up.

### 6.2 Add to Settings > Organization

Add an "Org Documents" section to the Organization/General settings page. This is a simple upload area for org-level compliance documents (BEE certificates, practice certificates, insurance docs, etc.):
- File upload using existing S3/Blob upload patterns
- Simple list: document name, upload date, file size, download link, delete action
- No categorization, no workflow — just a file drawer
- Admin/owner only

If the Settings > Organization page is currently a "coming soon" stub, implement a minimal version: org name display, logo display, and the documents section.

---

## Section 7 — Document Preview Print-Accuracy

### 7.1 Preview Container

Fix document preview rendering in:
- Template preview (Settings > Templates editor)
- Generated document preview (project-level generate document dialog)
- Any other location where document HTML is rendered for preview

### 7.2 A4 Proportioned Container

- Container aspect ratio: 210:297 (A4)
- Render the document HTML at full A4 width (210mm / 794px at 96dpi) inside the container
- Apply a CSS `transform: scale()` to fit the full-width render into the available preview width
- The preview should look like a miniature page — complete with margins, headers, footers as they'd appear in the PDF
- The font size should appear proportionally smaller (because the whole page is scaled down), NOT at full browser size with a squeezed layout

### 7.3 Visual Treatment

- Light paper background (`white` or `slate-50`) for the page area
- Subtle drop shadow around the page to create the "floating paper" effect
- Gray/dark background behind the page (like a PDF viewer)
- Page margins visible and proportional to print output

---

## Out of Scope

- **No new backend endpoints.** All data for dashboard polish already exists in current APIs.
- **No new entities or migrations.** Pure frontend.
- **No mobile-first redesign.** Responsive support maintained but desktop layout optimized for screenshots.
- **No dark mode rework.** The app sidebar is already dark; dashboard content area uses light mode. Keep as-is.
- **No Recharts replacement.** Enhance styling within Recharts, don't swap libraries.
- **No profitability page redesign.** The profitability page benefits from the chart upgrades but doesn't get a layout overhaul in this phase.
- **No customer detail page polish.** Scope is the three main dashboards + sidebar + doc preview.

## Section 8 — Test Impact Analysis

This section highlights UI changes that may affect existing test packs (Playwright E2E, Vitest unit/component tests). The automation engineer should review each area and update selectors, assertions, and screenshots as needed.

### 8.1 Sidebar Navigation Changes

**What changed**: Nav group labels renamed, items moved/removed, group IDs changed.

| Change | Impact | Affected Tests |
|--------|--------|----------------|
| "Delivery" group renamed to "Projects" | Tests selecting by group label or `id="delivery"` will break | Sidebar rendering tests, navigation E2E tests |
| "Documents" sidebar item removed | Tests clicking "Documents" in sidebar will fail | Any E2E flow that navigates to `/org/[slug]/documents` via sidebar |
| "Proposals" moved from Finance → Clients group | Tests asserting Proposals is in Finance group will fail | Sidebar structure tests, Finance section E2E |
| "Team & Resources" renamed to "Team" | Tests selecting by group label `id="team"` (id unchanged) — label assertions break | Sidebar label assertion tests |
| `/org/[slug]/documents` page removed | Direct navigation tests to this route will 404 | Document list E2E tests, any fixture navigating to org documents |

**Action**: Update sidebar snapshot tests, update any E2E selectors that reference group labels or the Documents nav item. Update navigation smoke tests.

### 8.2 Dashboard Layout Restructure

**What changed**: Org Dashboard, My Work, and Project Detail Overview tab have new component structures, different widget ordering, and new chart components.

| Surface | Layout Change | Test Impact |
|---------|--------------|-------------|
| Org Dashboard | 3-card KPI row → full-width metrics strip (6 metrics) | Tests asserting KPI card count, card content, or card ordering |
| Org Dashboard | Widget grid → two-panel hero + three-column secondary | Tests locating widgets by position or parent container |
| Org Dashboard | Getting Started card moves to banner above metrics | Tests targeting Getting Started card location |
| Org Dashboard | Admin widgets consolidated into compact stat cards | Tests asserting admin widget visibility, content, or structure |
| My Work | Widget layout restructured to agenda-first | Tests asserting widget order, presence of specific widget containers |
| My Work | New weekly rhythm strip component | No existing tests to break, but new component needs coverage |
| Project Detail | Overview tab health header redesigned | Tests checking health status display, metric values |
| Project Detail | Setup progress cards → compact checklist bar (hides when complete) | Tests asserting setup card visibility when all steps done |

**Action**: Dashboard and My Work page tests likely need significant selector updates. The data being displayed is the same — API responses unchanged — but DOM structure, component hierarchy, and text formatting may differ. Prefer `data-testid` selectors over structural selectors during updates.

### 8.3 Chart Component Changes

**What changed**: Shared chart components restyled with new theme. Chart types may change (pie → donut, bar → area).

| Change | Test Impact |
|--------|-------------|
| Pie charts → donut charts | Tests checking for SVG `<path>` elements in pie charts may need selector updates |
| New sparkline components in metrics strip | New components, no existing tests to break |
| Recharts tooltip/legend styling changes | Visual regression tests may flag differences |
| New radial gauge components | New components, need new test coverage |

**Action**: Chart tests are typically visual — update snapshot/screenshot baselines after the redesign. Functional chart tests (data values, tooltips) should still pass if they use Recharts data attributes.

### 8.4 Document Preview Changes

**What changed**: Preview container now uses A4-proportioned scaling with CSS transform.

| Change | Test Impact |
|--------|-------------|
| Preview container has new CSS (transform, aspect ratio, wrapper div) | Tests measuring preview dimensions or taking screenshots will see different sizes |
| "Floating paper" visual treatment (shadow, dark background) | Visual regression tests will flag the new styling |
| Scale transform may affect element coordinate calculations | Playwright click/hover actions within the preview may need coordinate adjustments |

**Action**: Update document preview screenshot baselines. If tests interact with elements inside the scaled preview, verify that Playwright's coordinate calculations account for the CSS transform.

### 8.5 Org Documents Page Removal / Settings Addition

**What changed**: `/org/[slug]/documents` page deleted. New "Org Documents" section added to Settings > Organization page.

| Change | Test Impact |
|--------|-------------|
| `/org/[slug]/documents` route removed | Direct navigation returns 404 — any E2E test visiting this URL fails |
| New upload section on Settings > Organization page | Needs new test coverage (upload, list, delete) |
| Settings > Organization page may go from "coming soon" stub to functional | Existing tests asserting "coming soon" state will break |

**Action**: Delete org documents page tests. Add new tests for the Settings > Organization documents section. Update any test that checks the Settings page structure.

### 8.6 No Backend Changes

All API responses, endpoints, and data shapes remain identical. Backend test packs (integration tests, API tests) are **not affected** by this phase. Only frontend test packs need review.

---

## ADR Topics

- **ADR-205: Chart theming strategy** — Centralized chart theme vs. per-component styling. Decision: shared theme config consumed by all chart instances for visual consistency.

## Style and Boundaries

- All frontend work. No backend changes.
- Stay within the Signal Deck design system: slate palette, teal accents, Sora/IBM Plex Sans/JetBrains Mono.
- Scoro-inspired density and chart richness, applied through the existing aesthetic.
- Recharts for all charts (already a dependency).
- Framer Motion for any new animations (already a dependency).
- JetBrains Mono (tabular-nums) for all dashboard numbers and statistics.
- Minimize new dependencies — use existing Shadcn components, Recharts, and Framer Motion.
