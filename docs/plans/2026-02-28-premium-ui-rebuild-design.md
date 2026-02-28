# Premium UI Rebuild — Design Document

**Date:** 2026-02-28
**Status:** Approved
**Scope:** Full rebuild of all 53 pages, new app shell, same backend API

## Design Decisions

| Decision | Choice |
|----------|--------|
| Motivation | Navigation/workflow friction in current UI |
| Aesthetic | Clio/Xero — traditional professional SaaS |
| Navigation | Hybrid: 64px icon rail + contextual top bar + sub-nav |
| Colors | Keep slate + teal (OKLCH) |
| Scope | Full rebuild in one pass |
| Detail pages | Full page with horizontal tabs (URL-driven) |
| Dark mode | Supported, user-toggleable |

---

## 1. App Shell & Navigation Architecture

### Global Layout

```
┌─────────────────────────────────────────────────────────────────┐
│┌──────┐┌───────────────────────────────────────────────────────┐│
││      ││ Top Bar (48px, sticky)                                ││
││      ││ Breadcrumbs              ⌘K  🔔  Avatar              ││
││ Icon ││───────────────────────────────────────────────────────││
││ Rail ││ Sub-nav bar (40px, contextual per zone)               ││
││ 64px ││───────────────────────────────────────────────────────││
││      ││                                                       ││
││      ││  Content Area (max-w: 1440px, centered, p-6 / p-8)   ││
││      ││                                                       ││
│└──────┘└───────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### Icon Rail

| Property | Value |
|----------|-------|
| Width | 64px |
| Background | `slate-950` (always dark) |
| Active indicator | 3px teal left bar + `bg-white/5` highlight |
| Position | Fixed, full height |
| Mobile (<768px) | Transforms to bottom tab bar |

**Zone icons:**

| # | Lucide Icon | Label | Route Prefix |
|---|-------------|-------|-------------|
| 1 | `Home` | Home | `/dashboard`, `/my-work` |
| 2 | `FolderKanban` | Work | `/projects`, `/schedules` |
| 3 | `Users` | Clients | `/customers` |
| 4 | `Receipt` | Money | `/invoices`, `/retainers` |
| 5 | `FileText` | Docs | `/documents` |
| 6 | `BarChart3` | Reports | `/profitability`, `/reports` |
| — | spacer | — | — |
| 7 | `Settings` | Admin | `/settings/*` |

Bottom of rail: Org avatar (click → org switcher popover).

### Top Bar (48px)

Contents (left → right):
1. Breadcrumbs (clickable, truncated on mobile)
2. Spacer
3. ⌘K search button
4. Notification bell with unread badge
5. User avatar dropdown (profile, org switch, dark mode toggle, sign out)

### Sub-nav Bar (40px, contextual)

| Property | Value |
|----------|-------|
| Background | `slate-50` (light) / `slate-800` (dark) |
| Items | Horizontal pill tabs |
| Active | Teal text + teal underline |

**Sub-nav per zone:**

| Zone | Items |
|------|-------|
| 🏠 Home | Dashboard, My Work |
| 📋 Work | Projects, Recurring Schedules |
| 👥 Clients | Customers |
| 💰 Money | Invoices, Retainers |
| 📄 Docs | Documents, Templates |
| 📊 Reports | Profitability, Reports |
| ⚙ Admin | Settings hub (own secondary sidebar) |

### Mobile (<768px)

- Icon rail → bottom tab bar (7 icons, safe-area padding)
- Sub-nav → horizontally scrollable pills
- Tables → card lists
- Dialogs → full-screen bottom sheets
- Settings sidebar → dropdown selector

---

## 2. Page Layout Patterns

### Pattern 1: List Page

Used for: Projects, Customers, Invoices, Documents, Retainers, Schedules, Team, Notifications

Structure:
1. **Page header** — title (left) + primary CTA (right)
2. **Toolbar** — search input, filter dropdowns, view toggles (table/card)
3. **Data table** — checkbox column, sortable headers, row actions (⋯ menu), clickable rows
4. **Pagination** — "Showing 1-25 of 142" + page buttons
5. **Bulk actions bar** — slides up from bottom when rows selected

Rules:
- Server-side sorting/filtering via query params
- Sticky table header
- Row hover: `bg-slate-50`
- Empty state: centered icon + heading + CTA
- Loading: 5 skeleton rows with shimmer

### Pattern 2: Detail Page (Tabbed)

Used for: Project, Customer, Invoice, Retainer, Schedule detail

Structure:
1. **Detail header** — back link, entity name, status badge, metadata, Edit + overflow menu
2. **Horizontal tabs** — teal underline (Framer Motion animated), URL-driven (`?tab=tasks`)
3. **Tab content** — full width, scrolls independently

Rules:
- Tabs are sticky below the sub-nav on scroll
- Tab selection persisted in URL for shareability
- Each tab can contain its own list-pattern table

### Pattern 3: Dashboard / Overview

Used for: Dashboard, My Work, Profitability

Structure:
1. **Welcome header** or **period selector**
2. **KPI strip** — 3-5 metric cards in a row
3. **Widget grid** — 2-column card grid (1-column on mobile)

Rules:
- KPI cards: value in JetBrains Mono, trend arrow, comparison period
- Widgets: title + "View all →" + content
- Not drag-and-drop configurable (YAGNI)
- Role-based visibility (Members see personal KPIs only)

### Pattern 4: Settings Page

Structure:
1. **Secondary sidebar** (inside content area) with grouped navigation
2. **Settings content** — form or list per section
3. **Sticky save button** at bottom of form sections

Settings sidebar groups: General, Team, Work, Documents, Notifications, Compliance, Integrations.

---

## 3. Page Specifications

### Zone 1: Home

#### Dashboard

**KPI strip (Owner/Admin):** Revenue, Utilization %, Unbilled Hours, Overdue Tasks
**KPI strip (Member):** My Hours, My Tasks, Billable %, Active Projects
**Widgets:** Recent Activity, My Tasks Today, Project Health, Team Workload, Unbilled Time, Setup Checklist (conditional)

#### My Work

**Top:** Personal time summary strip (hours today, this week, billable %)
**Main:** Tasks grouped by project (priority/due sort) + today's time entries with running total
**CTA:** Floating "+ Log Time" button

### Zone 2: Work

#### Projects List

**Filters:** Search, Status, Customer, Tags, Budget Status
**Columns:** Name, Customer, Status, Tasks (done/total), Budget %, Due Date, Actions
**Sort:** Updated date descending

#### Project Detail — 7 tabs

| Tab | Content |
|-----|---------|
| Overview | KPI cards + unfinished tasks + team roster + comments |
| Tasks | List-pattern table with inline create row |
| Time | Time entries table + summary sidebar |
| Budget | Gauge + consumption chart + threshold config |
| Documents | Grid/list toggle + upload |
| Team | Member cards with hours + tasks + add member |
| Activity | Timeline feed, filterable, infinite scroll |

#### Recurring Schedules

**Columns:** Name, Frequency, Next Run, Project, Status, Actions

### Zone 3: Clients

#### Customers List

**Sub-nav as lifecycle filter:** [All] [Prospects (4)] [Onboarding (2)] [Active (15)] [Dormant (1)] [Offboarding (0)]
**Columns:** Name, Lifecycle Status, Projects, Unbilled (R), Contact Email, Actions

#### Customer Detail — 5 tabs

| Tab | Content |
|-----|---------|
| Overview | Contact details + billing info + unbilled summary + custom fields |
| Projects | Linked projects table + link button |
| Lifecycle | Visual stepper + checklist items for current stage |
| Invoices | Customer-filtered invoice list |
| Documents | Customer-scoped docs + generated docs |

### Zone 4: Money

#### Invoices List

**Sub-nav as status filter:** [All] [Draft] [Approved] [Sent] [Overdue] [Paid] — with counts
**Columns:** Invoice # (monospace), Customer, Amount (right-aligned), Status, Issue Date, Due Date, Actions
**Special:** Overdue rows get subtle `bg-red-50` tint

#### Invoice Detail — 3 tabs

| Tab | Content |
|-----|---------|
| Line Items | Editable table (add/edit/remove). Subtotal/tax/total footer. |
| Preview | Rendered HTML via Thymeleaf. Template selector. PDF download. |
| Payments | Payment history timeline. Payment link status. |

**Header actions change per status:**
- Draft: Edit, Approve
- Approved: Send, Void
- Sent: Record Payment, Void
- Paid: read-only

#### Retainers List

**Columns:** Name, Customer, Type, Consumed %, Amount/Hours, Status, Actions

#### Retainer Detail — 3 tabs: Overview, Consumption, Timeline

### Zone 5: Docs

#### Documents List

**View toggle:** Table ↔ Card grid
**Filters:** Search, Scope, Visibility, Type
**Columns:** Name, Scope badge, Visibility, Uploaded by, Date, Size, Actions

#### Templates

Accessible from Docs sub-nav as quick link → opens in Admin/Settings zone.
List → click → full-page split-pane editor (code left, preview right).

### Zone 6: Reports

#### Profitability

**Toolbar:** Period selector, View by (Project/Customer/Member)
**Content:** Data table with margin color-coding (green >30%, amber 10-30%, red <10%)
**Below table:** Utilization bar chart

#### Reports Hub

Card grid of pre-built reports. Click → report detail with filters + visualization.

### Zone 7: Admin (Settings)

Settings sidebar groups:

```
GENERAL: Organization, Branding
TEAM: Members, Billing & Plan
WORK: Rates, Tax, Custom Fields, Project Templates, Checklists
DOCUMENTS: Templates, Clauses
NOTIFICATIONS: Preferences, Email
COMPLIANCE: Settings, Data Requests, Retention Policies
INTEGRATIONS: Connected Apps
```

Key pages:
- **Branding:** Logo upload, brand color picker, footer text, live preview
- **Rates:** Three-section page (Org Default, Project Overrides, Customer Overrides)
- **Templates:** List → split-pane editor (code + preview)

---

## 4. Component Design System

### Elevation

| Level | Use | CSS |
|-------|-----|-----|
| 0 | Flat backgrounds | No shadow |
| 1 | Cards, dropdowns | `shadow-sm` |
| 2 | Floating panels, popovers | `shadow-md` |
| 3 | Modals, command palette | `shadow-lg` + backdrop |

### Buttons (4 variants)

| Variant | Style | Use |
|---------|-------|-----|
| Primary | `bg-teal-600 text-white` | Main CTA (1 per page max) |
| Secondary | `bg-slate-100 text-slate-700` | Supporting actions |
| Ghost | `text-slate-600` hover `bg-slate-100` | Toolbar, inline actions |
| Destructive | `bg-red-600 text-white` | Delete (in confirmation dialogs only) |

Sizes: `sm` (28px), `default` (36px), `lg` (44px).

### Status Badges

| Color | Statuses |
|-------|----------|
| Slate | Draft, Pending, Prospect |
| Blue | In Progress, Onboarding, Sent |
| Emerald | Active, Complete, Paid, On Track |
| Amber | At Risk, Overdue, Dormant |
| Red | Over Budget, Cancelled, Void, Failed |
| Purple | Archived, Offboarded |

Style: `rounded-full px-2.5 py-0.5 text-xs font-medium` with tinted background.

### KPI Cards

- Background: `card` token
- Border: 1px `border` token
- Value: JetBrains Mono, `text-2xl font-semibold tabular-nums`
- Trend: `text-emerald-600` (up), `text-red-600` (down), `text-slate-500` (neutral)

### Data Tables (TanStack Table v8)

- Server-side sorting/filtering
- Sticky header
- Row hover: `bg-slate-50`
- Row actions: `⋯` dropdown
- Bulk select: checkbox column + floating action bar
- Column visibility: configurable via toolbar icon
- Loading: 5 shimmer skeleton rows
- Empty state: centered icon + heading + CTA

### Dialogs

- Sizes: `sm` (400px), `md` (560px), `lg` (720px), `xl` (900px)
- Backdrop: `bg-black/50 backdrop-blur-sm`
- Animation: Framer Motion scale-in (95% → 100%) + fade
- Footer: `[Cancel] [Primary Action]`, right-aligned
- Max 6 form fields — use full page for more

### Command Palette (⌘K)

- `cmdk` library
- Search hits backend endpoints (debounced 300ms)
- Results grouped by entity type with badges
- Quick actions section (Create Project, Create Customer, Log Time)
- Keyboard: arrow keys + enter

### Toasts (Sonner)

- Position: bottom-right
- Auto-dismiss: 5s success, persistent errors
- Max 3 stacked
- Variants: success (emerald), error (red), info (teal), warning (amber)

---

## 5. Interaction Patterns

### Inline Create
Tasks table has persistent "+ Add task..." row at top. Click expands inline form. Enter submits, Escape cancels.

### Optimistic Updates
- Checkbox toggles: immediate UI update, revert on error
- Status transitions: brief loading on button, then update
- Deletes: always require confirmation dialog

### Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `⌘K` | Command palette |
| `c p` | Create project |
| `c t` | Create task |
| `c i` | Create invoice |
| `?` | Shortcut help overlay |
| `← →` | Navigate tabs |
| `j k` | Navigate table rows |
| `Enter` | Open selected row |
| `Escape` | Close / deselect |

### Floating Time Log
FAB-style button (bottom-right, teal) on all pages. Opens compact time entry dialog. Pre-fills project context if on a project page.

---

## 6. Responsive Breakpoints

| Breakpoint | Width | Changes |
|------------|-------|---------|
| `sm` | <640px | Single column, bottom tabs, tables → cards |
| `md` | 640-1023px | Icon rail visible, 2-col grids, table horizontal scroll |
| `lg` | 1024-1279px | Full layout, all columns |
| `xl` | 1280-1535px | max-w-1440px centered, wider padding |
| `2xl` | 1536px+ | Same as xl, more space |

---

## 7. Dark Mode

- User-toggleable (avatar dropdown)
- Icon rail: always dark (no change)
- Top bar: `slate-900`
- Sub-nav: `slate-800`
- Content: `slate-950`
- Cards: `slate-900` + `slate-700` border
- Tables: `slate-900` rows, `slate-800` hover
- Teal accent: unchanged (high contrast on dark)

---

## 8. Technology

| Layer | Choice |
|-------|--------|
| Components | Shadcn UI (Radix primitives) |
| Styling | Tailwind CSS v4 |
| Data tables | TanStack Table v8 |
| Charts | Recharts |
| Forms | React Hook Form + Zod |
| Animations | Framer Motion (`motion`) |
| Command palette | `cmdk` |
| Icons | Lucide React |
| Data fetching | Server Components + fetch + Server Actions |
| URL state | `nuqs` |
| Toasts | Sonner |
| Fonts | Sora (display), IBM Plex Sans (body), JetBrains Mono (code/stats) |

**New deps:** `@tanstack/react-table`, `react-hook-form`, `@hookform/resolvers`, `zod`, `nuqs`, `sonner`
