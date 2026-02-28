# Premium UI Rebuild — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a fresh `frontend-v2/` Next.js app with the Workspace Zones navigation architecture, consuming the same backend API as `frontend/`.

**Architecture:** New Next.js 16 app in `frontend-v2/`. 64px icon rail (7 zones) + 48px top bar + 40px contextual sub-nav. All pages follow one of 4 layout patterns (List, Detail, Dashboard, Settings). TanStack Table v8. URL-driven state via `nuqs`.

**Tech Stack:** Next.js 16, React 19, Tailwind CSS v4, Shadcn UI (Radix), TanStack Table v8, React Hook Form + Zod, Framer Motion, nuqs, Sonner, cmdk, Recharts, Lucide React

**Design doc:** `docs/plans/2026-02-28-premium-ui-rebuild-design.md`

**Location:** `frontend-v2/` at project root (parallel to existing `frontend/`)

**Shared from `frontend/`:** Copy (not symlink) these files into `frontend-v2/` to bootstrap:
- `lib/api.ts` — server-side API client (JWT auth, error handling)
- `lib/internal-api.ts` — internal endpoint client
- `lib/types.ts` — all TypeScript types (1400+ lines)
- `lib/utils.ts` — `cn()` utility
- `lib/format.ts` — date/number formatting
- `lib/date-utils.ts` — date calculations
- `lib/auth/` — entire auth abstraction directory
- `lib/api/` — specialized API modules (email, integrations, reports, etc.)
- `lib/actions/` — shared server actions
- `hooks/use-notification-polling.ts` — notification polling hook
- `app/api/` — webhook route handlers + invoice preview

**Do NOT copy:** Components, layouts, pages, nav-items, sidebar — all rebuilt from scratch.

---

## Task 0: Scaffold frontend-v2

**Files:**
- Create: `frontend-v2/` (entire new Next.js project)

**Step 1: Create the Next.js project**

Run from project root:
```bash
pnpm create next-app frontend-v2 --typescript --tailwind --eslint --app --src-dir --no-import-alias --use-pnpm
```

Then configure the `@/*` path alias in `tsconfig.json` → `"@/*": ["./src/*"]`

**Step 2: Install all dependencies**

```bash
cd frontend-v2 && pnpm add \
  @clerk/nextjs \
  class-variance-authority clsx tailwind-merge \
  radix-ui \
  lucide-react \
  motion \
  recharts \
  cmdk \
  server-only \
  @tanstack/react-table \
  react-hook-form @hookform/resolvers zod \
  nuqs \
  sonner
```

Dev deps:
```bash
pnpm add -D \
  @tailwindcss/postcss \
  @testing-library/jest-dom @testing-library/react @testing-library/user-event \
  @vitest/coverage-v8 vitest happy-dom \
  @playwright/test \
  prettier prettier-plugin-tailwindcss \
  tw-animate-css \
  @types/node @types/react @types/react-dom
```

**Step 3: Copy shared code from frontend/**

```bash
# From project root
mkdir -p frontend-v2/src/lib/auth frontend-v2/src/lib/api frontend-v2/src/lib/actions frontend-v2/src/hooks

# Core utilities
cp frontend/src/lib/api.ts frontend-v2/src/lib/
cp frontend/src/lib/internal-api.ts frontend-v2/src/lib/
cp frontend/src/lib/types.ts frontend-v2/src/lib/
cp frontend/src/lib/utils.ts frontend-v2/src/lib/
cp frontend/src/lib/format.ts frontend-v2/src/lib/
cp frontend/src/lib/date-utils.ts frontend-v2/src/lib/

# Auth abstraction
cp -r frontend/src/lib/auth/* frontend-v2/src/lib/auth/

# API modules
cp -r frontend/src/lib/api/* frontend-v2/src/lib/api/

# Shared actions
cp -r frontend/src/lib/actions/* frontend-v2/src/lib/actions/

# Hooks
cp frontend/src/hooks/use-notification-polling.ts frontend-v2/src/hooks/

# API routes
mkdir -p frontend-v2/src/app/api/webhooks/clerk frontend-v2/src/app/api/invoices/\[id\]/preview
cp frontend/src/app/api/webhooks/clerk/route.ts frontend-v2/src/app/api/webhooks/clerk/
cp frontend/src/app/api/invoices/\[id\]/route.ts frontend-v2/src/app/api/invoices/\[id\]/ 2>/dev/null || true
cp frontend/src/app/api/invoices/\[id\]/preview/route.ts frontend-v2/src/app/api/invoices/\[id\]/preview/ 2>/dev/null || true
```

**Step 4: Copy and adapt config files**

```bash
cp frontend/next.config.ts frontend-v2/
cp frontend/vitest.config.ts frontend-v2/
cp frontend/postcss.config.mjs frontend-v2/
cp frontend/components.json frontend-v2/
cp frontend/eslint.config.mjs frontend-v2/
cp frontend/.prettierrc* frontend-v2/ 2>/dev/null || true
cp frontend/proxy.ts frontend-v2/ 2>/dev/null || true
```

**Step 5: Copy globals.css and fonts setup from layout.tsx**

Copy `frontend/src/app/globals.css` to `frontend-v2/src/app/globals.css`.
Copy the font imports and `<html>` setup from `frontend/src/app/layout.tsx` into `frontend-v2/src/app/layout.tsx`.

**Step 6: Verify build**

```bash
cd frontend-v2 && pnpm build
```

Expected: Build succeeds (pages may be empty but no import errors on shared code).

**Step 7: Commit**

```bash
git add frontend-v2/
git commit -m "feat: scaffold frontend-v2 with shared code from frontend"
```

---

## Task 1: Design Tokens & Global Styles

**Files:**
- Modify: `frontend-v2/src/app/globals.css`

**Step 1: Set up the full design token system**

Start from the copied `globals.css`. Add the new shell tokens:

```css
:root {
  /* ... existing tokens ... */

  /* Shell layout */
  --rail-width: 64px;
  --topbar-height: 48px;
  --subnav-height: 40px;
  --content-max-width: 1440px;
}
```

Add elevation utilities:

```css
@utility elevation-0 { box-shadow: none; }
@utility elevation-1 { box-shadow: 0 1px 2px 0 rgb(0 0 0 / 0.05); }
@utility elevation-2 { box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1); }
@utility elevation-3 { box-shadow: 0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1); }
```

**Step 2: Verify build**

Run: `cd frontend-v2 && pnpm build`

**Step 3: Commit**

```bash
git add frontend-v2/src/app/globals.css
git commit -m "feat(v2): design tokens with shell layout variables and elevation utilities"
```

---

## Task 2: Shadcn UI Primitives

**Files:**
- Create: `frontend-v2/src/components/ui/` (all 23 primitives)

**Step 1: Copy Shadcn UI components from frontend/**

```bash
mkdir -p frontend-v2/src/components/ui
cp frontend/src/components/ui/*.tsx frontend-v2/src/components/ui/
```

These are already customized with slate + teal. Keep them as-is — they're the foundation.

**Step 2: Update button.tsx variants**

Simplify to 4 primary variants per the design doc:
- `primary` → `bg-teal-600 text-white` (was the `accent` variant)
- `secondary` → `bg-slate-100 text-slate-700`
- `ghost` → `text-slate-600 hover:bg-slate-100`
- `destructive` → `bg-red-600 text-white`

Keep `outline`, `plain`, `link` as utility variants. Remove `soft` and `default` (the old dark primary).

**Step 3: Verify all imports compile**

Run: `cd frontend-v2 && pnpm build`

**Step 4: Commit**

```bash
git add frontend-v2/src/components/ui/
git commit -m "feat(v2): Shadcn UI primitives with updated button variants"
```

---

## Task 3: Navigation Data Model

**Files:**
- Create: `frontend-v2/src/lib/navigation.ts`
- Create: `frontend-v2/src/__tests__/lib/navigation.test.ts`

**Step 1: Create the zone-based navigation model**

```typescript
// frontend-v2/src/lib/navigation.ts
import {
  Home, FolderKanban, Users, Receipt, FileText, BarChart3, Settings,
  type LucideIcon,
} from "lucide-react";

export interface NavZone {
  id: string;
  icon: LucideIcon;
  label: string;
  matchPrefixes: string[];
  subNav: SubNavItem[];
}

export interface SubNavItem {
  label: string;
  href: (slug: string) => string;
  exact?: boolean;
}

export const NAV_ZONES: NavZone[] = [
  {
    id: "home",
    icon: Home,
    label: "Home",
    matchPrefixes: ["/dashboard", "/my-work"],
    subNav: [
      { label: "Dashboard", href: (s) => `/org/${s}/dashboard`, exact: true },
      { label: "My Work", href: (s) => `/org/${s}/my-work`, exact: true },
    ],
  },
  {
    id: "work",
    icon: FolderKanban,
    label: "Work",
    matchPrefixes: ["/projects", "/schedules"],
    subNav: [
      { label: "Projects", href: (s) => `/org/${s}/projects` },
      { label: "Recurring Schedules", href: (s) => `/org/${s}/schedules` },
    ],
  },
  {
    id: "clients",
    icon: Users,
    label: "Clients",
    matchPrefixes: ["/customers"],
    subNav: [
      { label: "Customers", href: (s) => `/org/${s}/customers` },
    ],
  },
  {
    id: "money",
    icon: Receipt,
    label: "Money",
    matchPrefixes: ["/invoices", "/retainers"],
    subNav: [
      { label: "Invoices", href: (s) => `/org/${s}/invoices` },
      { label: "Retainers", href: (s) => `/org/${s}/retainers` },
    ],
  },
  {
    id: "docs",
    icon: FileText,
    label: "Docs",
    matchPrefixes: ["/documents"],
    subNav: [
      { label: "Documents", href: (s) => `/org/${s}/documents` },
    ],
  },
  {
    id: "reports",
    icon: BarChart3,
    label: "Reports",
    matchPrefixes: ["/profitability", "/reports"],
    subNav: [
      { label: "Profitability", href: (s) => `/org/${s}/profitability` },
      { label: "Reports", href: (s) => `/org/${s}/reports` },
    ],
  },
  {
    id: "admin",
    icon: Settings,
    label: "Admin",
    matchPrefixes: ["/settings", "/team", "/compliance", "/notifications"],
    subNav: [],
  },
];

export function getActiveZone(pathname: string, slug: string): NavZone | undefined {
  const relative = pathname.replace(`/org/${slug}`, "");
  return NAV_ZONES.find((zone) =>
    zone.matchPrefixes.some((prefix) => relative.startsWith(prefix))
  );
}

export function isSubNavActive(item: SubNavItem, pathname: string, slug: string): boolean {
  const href = item.href(slug);
  return item.exact ? pathname === href : pathname.startsWith(href);
}
```

**Step 2: Write tests**

Test `getActiveZone` for each zone, test `isSubNavActive` for exact and prefix matching.

**Step 3: Run tests**

Run: `cd frontend-v2 && pnpm test`

**Step 4: Commit**

```bash
git add frontend-v2/src/lib/navigation.ts frontend-v2/src/__tests__/
git commit -m "feat(v2): zone-based navigation model with tests"
```

---

## Task 4: App Shell Components

**Files:**
- Create: `frontend-v2/src/components/shell/icon-rail.tsx`
- Create: `frontend-v2/src/components/shell/icon-rail-mobile.tsx`
- Create: `frontend-v2/src/components/shell/top-bar.tsx`
- Create: `frontend-v2/src/components/shell/sub-nav.tsx`
- Create: `frontend-v2/src/components/shell/command-palette-button.tsx`
- Create: `frontend-v2/src/components/shell/app-shell.tsx`

**Step 1: Icon Rail (desktop)**

64px wide, fixed left, `bg-slate-950`. Org initial at top, 6 zone icons, Settings at bottom. Framer Motion `layoutId="rail-indicator"` for the teal active bar. Lucide icon tooltips on hover. Hidden on mobile (`hidden md:flex`).

**Step 2: Icon Rail Mobile (bottom tabs)**

7 zone icons in a horizontal bar, fixed bottom, `bg-slate-950`. `pb-[env(safe-area-inset-bottom)]`. Visible only `md:hidden`.

**Step 3: Top Bar**

48px tall, sticky. Contains: Breadcrumbs (reuse/adapt from `frontend/`), spacer, ⌘K button stub, NotificationBell, AuthHeaderControls.

**Step 4: Sub-Nav**

40px tall, below top bar. Reads active zone from pathname, renders zone's `subNav` items as horizontal pill links. Hides if zone has ≤1 sub-nav item or if zone is "admin".

**Step 5: AppShell wrapper**

Composes: IconRail + main area (TopBar + SubNav + content with `max-w-[1440px]`) + IconRailMobile. Content has `pb-16 md:pb-0` to account for mobile bottom tabs.

**Step 6: Verify build**

Run: `cd frontend-v2 && pnpm build`

**Step 7: Commit**

```bash
git add frontend-v2/src/components/shell/
git commit -m "feat(v2): complete app shell — icon rail, top bar, sub-nav, mobile tabs"
```

---

## Task 5: Root Layout & Org Layout

**Files:**
- Modify: `frontend-v2/src/app/layout.tsx` (root layout with fonts + providers)
- Create: `frontend-v2/src/app/(app)/layout.tsx`
- Create: `frontend-v2/src/app/(app)/org/[slug]/layout.tsx`
- Create: `frontend-v2/src/app/(auth)/layout.tsx`
- Create: `frontend-v2/src/app/(auth)/sign-in/[[...sign-in]]/page.tsx`
- Create: `frontend-v2/src/app/(auth)/sign-up/[[...sign-up]]/page.tsx`

**Step 1: Root layout**

Set up fonts (Sora, IBM Plex Sans, JetBrains Mono via `next/font/google`), ClerkProvider (with `cssLayerName: "clerk"`), Sonner `<Toaster />`, and the `<html>` shell.

Copy font setup from `frontend/src/app/layout.tsx`.

**Step 2: Auth layouts**

Copy auth pages from `frontend/src/app/(auth)/` — these are just Clerk components, no changes needed.

**Step 3: Org layout**

```typescript
// frontend-v2/src/app/(app)/org/[slug]/layout.tsx
import { redirect } from "next/navigation";
import { getAuthContext } from "@/lib/auth";
import { AppShell } from "@/components/shell/app-shell";

export default async function OrgLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let orgSlug: string;
  try {
    const ctx = await getAuthContext();
    orgSlug = ctx.orgSlug;
  } catch {
    redirect("/dashboard");
  }

  if (orgSlug !== slug) {
    redirect(`/org/${orgSlug}/dashboard`);
  }

  return <AppShell slug={slug}>{children}</AppShell>;
}
```

**Step 4: Verify build**

Run: `cd frontend-v2 && pnpm build`

**Step 5: Commit**

```bash
git add frontend-v2/src/app/
git commit -m "feat(v2): root layout, auth pages, org layout with AppShell"
```

---

## Task 6: Sonner Toasts + Toast Utility

**Files:**
- Verify: `frontend-v2/src/app/layout.tsx` has `<Toaster />` (done in Task 5)
- Create: `frontend-v2/src/lib/toast.ts`

**Step 1: Create convenience re-export**

```typescript
export { toast } from "sonner";
```

**Step 2: Commit**

```bash
git add frontend-v2/src/lib/toast.ts
git commit -m "feat(v2): Sonner toast utility"
```

---

## Task 7: DataTable Foundation

**Files:**
- Create: `frontend-v2/src/components/ui/data-table.tsx`
- Create: `frontend-v2/src/components/ui/data-table-toolbar.tsx`
- Create: `frontend-v2/src/components/ui/data-table-pagination.tsx`
- Create: `frontend-v2/src/components/ui/data-table-skeleton.tsx`
- Create: `frontend-v2/src/components/ui/data-table-empty.tsx`
- Create: `frontend-v2/src/__tests__/components/ui/data-table.test.tsx`

**DataTable<T>** — Generic component using `@tanstack/react-table`:
- Props: `columns`, `data`, `onRowClick?`, `isLoading?`, `emptyState?`
- Supports: server-side sorting (`sortingState` + `onSortingChange`), row selection (checkbox column), sticky header
- Row hover: `bg-slate-50`. Clickable rows get `cursor-pointer`.
- Uses existing Shadcn `Table`, `TableHeader`, `TableRow`, `TableCell` components.

**DataTableToolbar** — Generic toolbar:
- Props: `searchPlaceholder`, `searchValue`, `onSearchChange`, `filters` (ReactNode slot), `actions` (ReactNode slot)
- Layout: search input (left) + filter dropdowns (center) + actions (right)

**DataTablePagination** — Pagination component:
- Props: `page` (matches Spring Data `{ totalElements, totalPages, size, number }`)
- Renders: "Showing 1-25 of 142" + page buttons

**DataTableSkeleton** — Loading state:
- Props: `columnCount`, `rowCount` (default 5)
- Shimmer rows using existing Shadcn `Skeleton` component

**DataTableEmpty** — Empty state:
- Props: `icon`, `title`, `description`, `action?`
- Centered within table area

**Tests:**
- Renders columns and data
- Calls onRowClick when row clicked
- Shows skeleton when loading
- Shows empty state when no data

**Commit:**

```bash
git add frontend-v2/src/components/ui/data-table* frontend-v2/src/__tests__/
git commit -m "feat(v2): DataTable component with toolbar, pagination, skeleton, empty state"
```

---

## Task 8: Page Layout Pattern Components

**Files:**
- Create: `frontend-v2/src/components/layout/page-header.tsx`
- Create: `frontend-v2/src/components/layout/kpi-strip.tsx`
- Create: `frontend-v2/src/components/layout/widget-grid.tsx`
- Create: `frontend-v2/src/components/layout/detail-page.tsx`
- Create: `frontend-v2/src/components/layout/settings-layout.tsx`

**PageHeader** — Title + actions + optional back link, description, count badge.

**KpiStrip** — Horizontal row of metric cards. Value in JetBrains Mono. Trend arrows with emerald/red/slate coloring.

**WidgetGrid** — 2-col responsive grid. `WidgetCard` sub-component: title + "View all →" + content slot.

**DetailPage** — Header area + horizontal tabs (URL-driven via `nuqs`). Framer Motion underline. Tabs defined as `{ id, label, content, count? }[]`.

**SettingsLayout** — Secondary sidebar (grouped nav) + content area. Mobile: sidebar becomes dropdown.

**Commit:**

```bash
git add frontend-v2/src/components/layout/
git commit -m "feat(v2): page layout patterns — PageHeader, KpiStrip, WidgetGrid, DetailPage, SettingsLayout"
```

---

## Task 9: Status Badge System

**Files:**
- Create: `frontend-v2/src/components/ui/status-badge.tsx`
- Create: `frontend-v2/src/__tests__/components/ui/status-badge.test.tsx`

Unified `<StatusBadge status="ACTIVE" />` that maps statuses to consistent colors:
- Slate: Draft, Pending, Prospect
- Blue: In Progress, Onboarding, Sent
- Emerald: Active, Completed, Paid, On Track, Done
- Amber: At Risk, Overdue, Dormant
- Red: Over Budget, Cancelled, Void, Failed
- Purple: Archived, Offboarded

**Commit:**

```bash
git add frontend-v2/src/components/ui/status-badge* frontend-v2/src/__tests__/
git commit -m "feat(v2): unified StatusBadge component with color mapping"
```

---

## Task 10: Empty State Component

**Files:**
- Create: `frontend-v2/src/components/empty-state.tsx`

Copy and adapt from `frontend/src/components/empty-state.tsx`. Same API: `icon`, `title`, `description`, `actionLabel`, `actionHref`.

**Commit:**

```bash
git add frontend-v2/src/components/empty-state.tsx
git commit -m "feat(v2): empty state component"
```

---

## Tasks 11-17: Zone Pages

Each zone is an independent task that can run in parallel. They all follow the same approach:

1. Create route files (`page.tsx`, `actions.ts`, `loading.tsx`)
2. Create feature components in `components/{feature}/`
3. Copy server actions from `frontend/` (API calls don't change)
4. Rebuild the UI using the pattern components (DataTable, PageHeader, DetailPage, etc.)
5. Verify build
6. Commit

### Task 11: Zone 1 — Home (Dashboard + My Work)

**Route files:**
- `frontend-v2/src/app/(app)/org/[slug]/dashboard/page.tsx`
- `frontend-v2/src/app/(app)/org/[slug]/my-work/page.tsx`

**Components:**
- `frontend-v2/src/components/dashboard/` — KPI cards, widgets (activity, project health, team workload)
- `frontend-v2/src/components/my-work/` — task groups, time entries, personal KPIs
- `frontend-v2/src/components/shell/time-entry-fab.tsx` — floating Log Time button

**Dashboard:** KpiStrip at top, WidgetGrid below. Copy data fetching from existing `dashboard/page.tsx`.
**My Work:** Personal KpiStrip + task list grouped by project + today's time entries.
**FAB:** Fixed bottom-right teal pill button, opens LogTimeDialog.

### Task 12: Zone 2 — Work (Projects + Schedules)

**Route files:**
- `frontend-v2/src/app/(app)/org/[slug]/projects/page.tsx` + `actions.ts`
- `frontend-v2/src/app/(app)/org/[slug]/projects/[id]/page.tsx` + `actions.ts` + all sub-actions
- `frontend-v2/src/app/(app)/org/[slug]/schedules/page.tsx` + `[id]/page.tsx`

**Components:**
- `frontend-v2/src/components/projects/` — all project components rebuilt with DataTable + DetailPage patterns
- `frontend-v2/src/components/tasks/` — task list, create/edit dialogs, log time
- `frontend-v2/src/components/schedules/` — schedule list + detail

**Projects list:** DataTable (Name, Customer, Status, Tasks, Budget %, Due Date). Toolbar filters.
**Project detail:** DetailPage with 7 tabs (Overview, Tasks, Time, Budget, Docs, Team, Activity). Copy all 15+ data fetches from existing page.

### Task 13: Zone 3 — Clients (Customers)

**Route files:**
- `frontend-v2/src/app/(app)/org/[slug]/customers/page.tsx` + `actions.ts`
- `frontend-v2/src/app/(app)/org/[slug]/customers/[id]/page.tsx` + sub-actions

**Components:**
- `frontend-v2/src/components/customers/` — lifecycle management, checklist, billing info

**Customers list:** DataTable with lifecycle filters. Fetch lifecycle summary for sub-nav counts.
**Customer detail:** DetailPage with 5 tabs (Overview, Projects, Lifecycle, Invoices, Docs). Visual lifecycle stepper.

### Task 14: Zone 4 — Money (Invoices + Retainers)

**Route files:**
- `frontend-v2/src/app/(app)/org/[slug]/invoices/page.tsx` + `[id]/page.tsx`
- `frontend-v2/src/app/(app)/org/[slug]/retainers/page.tsx` + `[id]/page.tsx`

**Components:**
- `frontend-v2/src/components/invoices/` — line item editor, preview, payment history
- `frontend-v2/src/components/retainers/` — progress gauge, consumption table

**Invoice list:** DataTable with overdue row tinting. Status filters.
**Invoice detail:** DetailPage with 3 tabs (Lines, Preview, Payments). React Hook Form for line items.

### Task 15: Zone 5 — Docs (Documents)

**Route files:**
- `frontend-v2/src/app/(app)/org/[slug]/documents/page.tsx` + `actions.ts`

**Components:**
- `frontend-v2/src/components/documents/` — upload zone, file grid, visibility toggle

**Documents list:** DataTable with table ↔ card grid toggle. Scope/visibility filters.

### Task 16: Zone 6 — Reports (Profitability + Reports)

**Route files:**
- `frontend-v2/src/app/(app)/org/[slug]/profitability/page.tsx`
- `frontend-v2/src/app/(app)/org/[slug]/reports/page.tsx` + `[reportSlug]/page.tsx`

**Components:**
- `frontend-v2/src/components/profitability/` — margin tables, utilization chart
- `frontend-v2/src/components/reports/` — report browser, parameter forms

**Profitability:** Period selector + View by toggle + DataTable with margin color-coding + Recharts bar chart.
**Reports:** Card grid hub → individual report detail.

### Task 17: Zone 7 — Admin (Settings + Team + Compliance + Notifications)

**Route files:**
- `frontend-v2/src/app/(app)/org/[slug]/settings/page.tsx` + ALL sub-pages
- `frontend-v2/src/app/(app)/org/[slug]/team/page.tsx`
- `frontend-v2/src/app/(app)/org/[slug]/compliance/page.tsx` + sub-pages
- `frontend-v2/src/app/(app)/org/[slug]/notifications/page.tsx`

**Components:**
- `frontend-v2/src/components/shell/settings-sidebar.tsx` — secondary nav for settings
- `frontend-v2/src/components/team/` — member list, invite form
- `frontend-v2/src/components/compliance/` — data requests, retention, checklists
- `frontend-v2/src/components/notifications/` — notification list, preferences
- `frontend-v2/src/components/settings/` — tax, rates, custom fields, templates, clauses, etc.
- `frontend-v2/src/components/rates/` — billing/cost rate tables
- `frontend-v2/src/components/templates/` — template editor, preview
- `frontend-v2/src/components/integrations/` — integration cards
- `frontend-v2/src/components/billing/` — plan management
- Copy all existing settings form components, adapt to SettingsLayout pattern.

---

## Task 18: Command Palette

**Files:**
- Create: `frontend-v2/src/components/shell/command-palette.tsx`
- Modify: `frontend-v2/src/components/shell/app-shell.tsx`

**Implementation:**
- `cmdk` dialog, opens on `⌘K`
- Search: projects, customers, invoices, tasks (debounced 300ms to backend)
- Quick actions: Create Project, Create Customer, Log Time, Go to Settings
- Recent items (localStorage)
- Results grouped by entity type with badges

---

## Task 19: Keyboard Shortcuts

**Files:**
- Create: `frontend-v2/src/components/shell/keyboard-shortcuts.tsx`
- Create: `frontend-v2/src/components/shell/shortcut-help-dialog.tsx`

Global listener: `⌘K` palette, `?` help overlay, `c p` create project, `c t` create task, `c i` create invoice, `j/k` table rows, `Enter` open, `Escape` close.

---

## Task 20: Dark Mode Toggle

**Files:**
- Create: `frontend-v2/src/components/shell/theme-toggle.tsx`
- Modify: `frontend-v2/src/components/auth-header-controls.tsx`

Toggle in user dropdown. Persists to localStorage. Applies `.dark` to `<html>`. Respects `prefers-color-scheme`.

---

## Task 21: Portal Pages

**Files:**
- Create: `frontend-v2/src/app/portal/` (all portal routes)
- Create: `frontend-v2/src/components/portal/` (header, project cards, doc table)

Simpler shell (no icon rail, just top bar with logo + nav links). Magic link auth. Pages: Projects, Documents.

---

## Task 22: Landing Page

**Files:**
- Create: `frontend-v2/src/app/page.tsx`
- Create: `frontend-v2/src/components/marketing/` (hero, features, pricing, footer)

Copy and adapt landing page components from `frontend/`. Minimal changes — just ensure they work in the new project.

---

## Task 23: Pre-org Pages

**Files:**
- Create: `frontend-v2/src/app/(app)/dashboard/page.tsx` (org selector)
- Create: `frontend-v2/src/app/(app)/create-org/page.tsx`

Copy from `frontend/` — these are simple Clerk-powered pages.

---

## Task 24: Final Verification

**Step 1:** `cd frontend-v2 && pnpm build` — zero errors
**Step 2:** `cd frontend-v2 && pnpm test` — all tests pass
**Step 3:** `cd frontend-v2 && pnpm lint` — clean
**Step 4:** Manual visual review — dev server on a different port (`pnpm dev -p 3002`)
**Step 5:** Commit any polish fixes
