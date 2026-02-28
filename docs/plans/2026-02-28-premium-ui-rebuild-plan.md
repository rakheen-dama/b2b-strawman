# Premium UI Rebuild — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rebuild all 53 frontend pages with a new Workspace Zones navigation architecture (slim icon rail + contextual sub-nav + top bar), while keeping the same backend API.

**Architecture:** Replace the current 240px dark sidebar + flat nav with a 64px icon rail (7 zones) + 48px top bar + 40px contextual sub-nav. All pages follow one of 4 layout patterns (List, Detail, Dashboard, Settings). TanStack Table v8 replaces manual table markup. URL-driven state via `nuqs`.

**Tech Stack:** Next.js 16, React 19, Tailwind CSS v4, Shadcn UI (Radix), TanStack Table v8, React Hook Form + Zod, Framer Motion, nuqs, Sonner, cmdk, Recharts, Lucide React

**Design doc:** `docs/plans/2026-02-28-premium-ui-rebuild-design.md`

---

## Task 1: Install New Dependencies

**Files:**
- Modify: `frontend/package.json`

**Step 1: Install new deps**

Run:
```bash
cd frontend && pnpm add @tanstack/react-table react-hook-form @hookform/resolvers zod nuqs sonner
```

**Step 2: Verify installation**

Run: `cd frontend && pnpm build`
Expected: Build succeeds with no errors

**Step 3: Commit**

```bash
git add frontend/package.json frontend/pnpm-lock.yaml
git commit -m "chore: add TanStack Table, React Hook Form, Zod, nuqs, Sonner"
```

---

## Task 2: Update Design Tokens & Global Styles

**Files:**
- Modify: `frontend/app/globals.css`

Update the globals.css to add new tokens needed by the new shell. Keep ALL existing tokens — only add new ones.

**Step 1: Add new elevation & layout tokens**

Add after the existing `:root` block:

```css
/* === New shell layout tokens === */
:root {
  /* Icon rail */
  --rail-width: 64px;
  --rail-bg: var(--slate-950);

  /* Top bar */
  --topbar-height: 48px;

  /* Sub-nav */
  --subnav-height: 40px;
  --subnav-bg: var(--slate-50);

  /* Content */
  --content-max-width: 1440px;
}

.dark {
  --subnav-bg: var(--slate-800);
}
```

**Step 2: Add elevation utility classes**

```css
@utility elevation-0 {
  box-shadow: none;
}
@utility elevation-1 {
  box-shadow: 0 1px 2px 0 rgb(0 0 0 / 0.05);
}
@utility elevation-2 {
  box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1);
}
@utility elevation-3 {
  box-shadow: 0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1);
}
```

**Step 3: Verify build**

Run: `cd frontend && pnpm build`
Expected: Build succeeds

**Step 4: Commit**

```bash
git add frontend/app/globals.css
git commit -m "feat: add shell layout tokens and elevation utilities"
```

---

## Task 3: Navigation Data Model

**Files:**
- Create: `frontend/lib/navigation.ts`
- Modify: `frontend/lib/nav-items.ts` (keep for backward compat until full migration)

**Step 1: Create the new zone-based navigation model**

```typescript
// frontend/lib/navigation.ts
import {
  Home,
  FolderKanban,
  Users,
  Receipt,
  FileText,
  BarChart3,
  Settings,
  type LucideIcon,
} from "lucide-react";

export interface NavZone {
  id: string;
  icon: LucideIcon;
  label: string;
  /** Route prefixes that activate this zone */
  matchPrefixes: string[];
  /** Sub-nav items shown when this zone is active */
  subNav: SubNavItem[];
}

export interface SubNavItem {
  label: string;
  href: (slug: string) => string;
  /** Use exact pathname match instead of startsWith */
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
    matchPrefixes: ["/settings", "/team", "/compliance"],
    subNav: [], // Admin zone uses its own settings sidebar
  },
];

/**
 * Find the active zone based on the current pathname.
 * Strips the `/org/[slug]` prefix before matching.
 */
export function getActiveZone(pathname: string, slug: string): NavZone | undefined {
  const relative = pathname.replace(`/org/${slug}`, "");
  return NAV_ZONES.find((zone) =>
    zone.matchPrefixes.some((prefix) => relative.startsWith(prefix))
  );
}

/**
 * Check if a sub-nav item is active.
 */
export function isSubNavActive(item: SubNavItem, pathname: string, slug: string): boolean {
  const href = item.href(slug);
  return item.exact ? pathname === href : pathname.startsWith(href);
}
```

**Step 2: Write tests**

Create `frontend/__tests__/lib/navigation.test.ts`:

```typescript
import { describe, it, expect } from "vitest";
import { getActiveZone, isSubNavActive, NAV_ZONES } from "@/lib/navigation";

describe("getActiveZone", () => {
  it("returns home zone for dashboard", () => {
    expect(getActiveZone("/org/acme/dashboard", "acme")?.id).toBe("home");
  });

  it("returns home zone for my-work", () => {
    expect(getActiveZone("/org/acme/my-work", "acme")?.id).toBe("home");
  });

  it("returns work zone for projects", () => {
    expect(getActiveZone("/org/acme/projects", "acme")?.id).toBe("work");
  });

  it("returns work zone for project detail", () => {
    expect(getActiveZone("/org/acme/projects/123", "acme")?.id).toBe("work");
  });

  it("returns admin zone for settings", () => {
    expect(getActiveZone("/org/acme/settings/rates", "acme")?.id).toBe("admin");
  });

  it("returns undefined for unknown paths", () => {
    expect(getActiveZone("/org/acme/unknown", "acme")).toBeUndefined();
  });
});

describe("isSubNavActive", () => {
  const dashboardItem = NAV_ZONES[0].subNav[0]; // Dashboard, exact: true

  it("exact match works", () => {
    expect(isSubNavActive(dashboardItem, "/org/acme/dashboard", "acme")).toBe(true);
  });

  it("exact match rejects child paths", () => {
    expect(isSubNavActive(dashboardItem, "/org/acme/dashboard/sub", "acme")).toBe(false);
  });
});
```

**Step 3: Run tests**

Run: `cd frontend && pnpm test -- __tests__/lib/navigation.test.ts`
Expected: All tests pass

**Step 4: Commit**

```bash
git add frontend/lib/navigation.ts frontend/__tests__/lib/navigation.test.ts
git commit -m "feat: zone-based navigation model with tests"
```

---

## Task 4: Icon Rail Component

**Files:**
- Create: `frontend/components/shell/icon-rail.tsx`
- Create: `frontend/components/shell/icon-rail-mobile.tsx`

**Step 1: Build the desktop icon rail**

```typescript
// frontend/components/shell/icon-rail.tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { AnimatePresence, motion } from "motion/react";
import { cn } from "@/lib/utils";
import { NAV_ZONES, getActiveZone } from "@/lib/navigation";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

export function IconRail({ slug }: { slug: string }) {
  const pathname = usePathname();
  const activeZone = getActiveZone(pathname, slug);

  // Split zones: main (first 6) and bottom (Settings)
  const mainZones = NAV_ZONES.filter((z) => z.id !== "admin");
  const adminZone = NAV_ZONES.find((z) => z.id === "admin");

  return (
    <nav
      className="hidden md:flex flex-col items-center justify-between w-16 bg-slate-950 border-r border-slate-800 py-4 fixed inset-y-0 left-0 z-40"
      aria-label="Main navigation"
    >
      {/* Top: org avatar placeholder */}
      <div className="flex flex-col items-center gap-1">
        <div className="size-8 rounded-lg bg-teal-600 flex items-center justify-center text-white text-xs font-bold mb-4">
          {slug.charAt(0).toUpperCase()}
        </div>

        {/* Main zone icons */}
        {mainZones.map((zone) => {
          const isActive = activeZone?.id === zone.id;
          const Icon = zone.icon;
          const defaultHref = zone.subNav[0]?.href(slug) ?? `/org/${slug}/dashboard`;

          return (
            <Tooltip key={zone.id} delayDuration={0}>
              <TooltipTrigger asChild>
                <Link
                  href={defaultHref}
                  className={cn(
                    "relative flex items-center justify-center size-10 rounded-lg transition-colors",
                    isActive
                      ? "text-white bg-white/10"
                      : "text-slate-400 hover:text-white hover:bg-white/5"
                  )}
                  aria-current={isActive ? "page" : undefined}
                >
                  {isActive && (
                    <motion.div
                      layoutId="rail-indicator"
                      className="absolute left-0 top-1/2 -translate-y-1/2 w-[3px] h-5 rounded-r-full bg-teal-500"
                      transition={{ type: "spring", stiffness: 500, damping: 30 }}
                    />
                  )}
                  <Icon className="size-5" />
                </Link>
              </TooltipTrigger>
              <TooltipContent side="right" sideOffset={8}>
                {zone.label}
              </TooltipContent>
            </Tooltip>
          );
        })}
      </div>

      {/* Bottom: admin + user */}
      <div className="flex flex-col items-center gap-1">
        {adminZone && (() => {
          const isActive = activeZone?.id === "admin";
          const Icon = adminZone.icon;
          return (
            <Tooltip delayDuration={0}>
              <TooltipTrigger asChild>
                <Link
                  href={`/org/${slug}/settings`}
                  className={cn(
                    "relative flex items-center justify-center size-10 rounded-lg transition-colors",
                    isActive
                      ? "text-white bg-white/10"
                      : "text-slate-400 hover:text-white hover:bg-white/5"
                  )}
                  aria-current={isActive ? "page" : undefined}
                >
                  {isActive && (
                    <motion.div
                      layoutId="rail-indicator"
                      className="absolute left-0 top-1/2 -translate-y-1/2 w-[3px] h-5 rounded-r-full bg-teal-500"
                      transition={{ type: "spring", stiffness: 500, damping: 30 }}
                    />
                  )}
                  <Icon className="size-5" />
                </Link>
              </TooltipTrigger>
              <TooltipContent side="right" sideOffset={8}>
                {adminZone.label}
              </TooltipContent>
            </Tooltip>
          );
        })()}
      </div>
    </nav>
  );
}
```

**Step 2: Build the mobile bottom tab bar**

```typescript
// frontend/components/shell/icon-rail-mobile.tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { NAV_ZONES, getActiveZone } from "@/lib/navigation";

export function IconRailMobile({ slug }: { slug: string }) {
  const pathname = usePathname();
  const activeZone = getActiveZone(pathname, slug);

  return (
    <nav
      className="md:hidden fixed bottom-0 inset-x-0 z-40 bg-slate-950 border-t border-slate-800 pb-[env(safe-area-inset-bottom)]"
      aria-label="Main navigation"
    >
      <div className="flex items-center justify-around h-14">
        {NAV_ZONES.map((zone) => {
          const isActive = activeZone?.id === zone.id;
          const Icon = zone.icon;
          const defaultHref = zone.subNav[0]?.href(slug) ?? `/org/${slug}/settings`;

          return (
            <Link
              key={zone.id}
              href={defaultHref}
              className={cn(
                "flex flex-col items-center justify-center gap-0.5 px-3 py-1",
                isActive ? "text-teal-400" : "text-slate-500"
              )}
            >
              <Icon className="size-5" />
              <span className="text-[10px] font-medium">{zone.label}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
```

**Step 3: Commit**

```bash
git add frontend/components/shell/
git commit -m "feat: icon rail (desktop + mobile bottom tabs)"
```

---

## Task 5: Top Bar & Sub-Nav Components

**Files:**
- Create: `frontend/components/shell/top-bar.tsx`
- Create: `frontend/components/shell/sub-nav.tsx`
- Modify: `frontend/components/breadcrumbs.tsx` (simplify for new shell)

**Step 1: Build the top bar**

```typescript
// frontend/components/shell/top-bar.tsx
import { Breadcrumbs } from "@/components/breadcrumbs";
import { AuthHeaderControls } from "@/components/auth-header-controls";
import { NotificationBell } from "@/components/notifications/notification-bell";
import { CommandPaletteButton } from "@/components/shell/command-palette-button";

export function TopBar({ slug }: { slug: string }) {
  return (
    <header className="sticky top-0 z-30 flex h-12 items-center gap-4 border-b border-slate-200/60 bg-white/80 px-4 backdrop-blur-md md:px-6 dark:border-slate-800/60 dark:bg-slate-900/90">
      <Breadcrumbs slug={slug} />
      <div className="ml-auto flex items-center gap-2">
        <CommandPaletteButton />
        <NotificationBell orgSlug={slug} />
        <AuthHeaderControls />
      </div>
    </header>
  );
}
```

**Step 2: Build the sub-nav bar**

```typescript
// frontend/components/shell/sub-nav.tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { getActiveZone, isSubNavActive, type SubNavItem } from "@/lib/navigation";

export function SubNav({ slug }: { slug: string }) {
  const pathname = usePathname();
  const activeZone = getActiveZone(pathname, slug);

  // Don't render sub-nav for admin zone (it has its own settings sidebar)
  // or if zone has only 1 sub-nav item
  if (!activeZone || activeZone.id === "admin" || activeZone.subNav.length <= 1) {
    return null;
  }

  return (
    <div className="flex h-10 items-center gap-1 border-b border-slate-200/60 bg-slate-50 px-4 md:px-6 dark:border-slate-800/60 dark:bg-slate-800 overflow-x-auto">
      {activeZone.subNav.map((item) => (
        <SubNavLink key={item.label} item={item} slug={slug} pathname={pathname} />
      ))}
    </div>
  );
}

function SubNavLink({
  item,
  slug,
  pathname,
}: {
  item: SubNavItem;
  slug: string;
  pathname: string;
}) {
  const active = isSubNavActive(item, pathname, slug);

  return (
    <Link
      href={item.href(slug)}
      className={cn(
        "inline-flex items-center rounded-md px-3 py-1.5 text-sm font-medium transition-colors whitespace-nowrap",
        active
          ? "text-teal-700 bg-teal-50 dark:text-teal-400 dark:bg-teal-950/50"
          : "text-slate-600 hover:text-slate-900 hover:bg-slate-100 dark:text-slate-400 dark:hover:text-slate-200 dark:hover:bg-slate-700"
      )}
    >
      {item.label}
    </Link>
  );
}
```

**Step 3: Create command palette button stub**

```typescript
// frontend/components/shell/command-palette-button.tsx
"use client";

import { Search } from "lucide-react";
import { Button } from "@/components/ui/button";

export function CommandPaletteButton() {
  return (
    <Button
      variant="ghost"
      size="sm"
      className="hidden md:inline-flex gap-2 text-slate-400 hover:text-slate-600"
      onClick={() => {
        // TODO: Wire up command palette in Task 14
        document.dispatchEvent(new KeyboardEvent("keydown", { key: "k", metaKey: true }));
      }}
    >
      <Search className="size-4" />
      <span className="text-xs">⌘K</span>
    </Button>
  );
}
```

**Step 4: Commit**

```bash
git add frontend/components/shell/ frontend/components/breadcrumbs.tsx
git commit -m "feat: top bar and contextual sub-nav components"
```

---

## Task 6: New App Shell Layout

**Files:**
- Modify: `frontend/app/(app)/org/[slug]/layout.tsx`
- Create: `frontend/components/shell/app-shell.tsx`

This is the critical task — replace the current sidebar+header layout with the new icon rail + top bar + sub-nav shell.

**Step 1: Create the shell wrapper component**

```typescript
// frontend/components/shell/app-shell.tsx
import { IconRail } from "@/components/shell/icon-rail";
import { IconRailMobile } from "@/components/shell/icon-rail-mobile";
import { TopBar } from "@/components/shell/top-bar";
import { SubNav } from "@/components/shell/sub-nav";
import { PageTransition } from "@/components/page-transition";
import { TooltipProvider } from "@/components/ui/tooltip";

export function AppShell({
  slug,
  children,
}: {
  slug: string;
  children: React.ReactNode;
}) {
  return (
    <TooltipProvider>
      <div className="flex min-h-screen">
        {/* Desktop icon rail */}
        <IconRail slug={slug} />

        {/* Main area — offset by rail width on desktop */}
        <div className="flex flex-1 flex-col md:ml-16">
          <TopBar slug={slug} />
          <SubNav slug={slug} />

          <main className="flex-1 bg-background dark:bg-slate-950 pb-16 md:pb-0">
            <div className="mx-auto max-w-[1440px] px-6 py-6 lg:px-8">
              <PageTransition>{children}</PageTransition>
            </div>
          </main>
        </div>

        {/* Mobile bottom tab bar */}
        <IconRailMobile slug={slug} />
      </div>
    </TooltipProvider>
  );
}
```

**Step 2: Update the org layout to use AppShell**

Replace the entire content of `frontend/app/(app)/org/[slug]/layout.tsx`:

```typescript
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

**Step 3: Verify build**

Run: `cd frontend && pnpm build`
Expected: Build succeeds. The old sidebar components (`desktop-sidebar.tsx`, `mobile-sidebar.tsx`) are no longer imported from the layout but can remain in the codebase until cleanup.

**Step 4: Commit**

```bash
git add frontend/components/shell/app-shell.tsx frontend/app/\(app\)/org/\[slug\]/layout.tsx
git commit -m "feat: replace sidebar layout with Workspace Zones shell"
```

---

## Task 7: Sonner Toast Provider & Setup

**Files:**
- Modify: `frontend/app/layout.tsx` (add Toaster)
- Create: `frontend/lib/toast.ts` (re-export for convenience)

**Step 1: Add Sonner's Toaster to root layout**

Add `<Toaster />` from `sonner` inside the root layout body, after the children. Use `richColors` and position `bottom-right`.

**Step 2: Create toast utility**

```typescript
// frontend/lib/toast.ts
export { toast } from "sonner";
```

**Step 3: Commit**

```bash
git add frontend/app/layout.tsx frontend/lib/toast.ts
git commit -m "feat: add Sonner toast provider"
```

---

## Task 8: Data Table Foundation Component

**Files:**
- Create: `frontend/components/ui/data-table.tsx`
- Create: `frontend/components/ui/data-table-toolbar.tsx`
- Create: `frontend/components/ui/data-table-pagination.tsx`
- Create: `frontend/components/ui/data-table-skeleton.tsx`
- Create: `frontend/components/ui/data-table-empty.tsx`

This is the most important new component — used by every list page.

**Step 1: Build the core DataTable component**

Build a generic `DataTable<T>` component using TanStack Table v8 that:
- Accepts `columns: ColumnDef<T>[]` and `data: T[]`
- Supports server-side sorting via `sortingState` + `onSortingChange` callbacks
- Supports row selection via checkbox column
- Renders sticky header
- Row hover highlight (`bg-slate-50`)
- Row click handler (`onRowClick?: (row: T) => void`)
- Row action menu slot (last column)
- Uses the existing Shadcn `Table` primitives for styling consistency

**Step 2: Build DataTableToolbar**

Generic toolbar component that accepts:
- `searchPlaceholder: string`
- `searchValue / onSearchChange` for controlled search
- `filters: ReactNode` slot for filter dropdowns
- `actions: ReactNode` slot for view toggles / bulk actions

**Step 3: Build DataTablePagination**

Component showing "Showing 1-25 of 142" + page buttons. Accepts `page: PageMetadata` from the Spring Data pagination response shape.

**Step 4: Build DataTableSkeleton**

5 shimmer rows matching column structure. Accepts `columnCount: number`.

**Step 5: Build DataTableEmpty**

Reuse pattern from existing `empty-state.tsx` but embedded inside the table area.

**Step 6: Write tests for DataTable**

Test: renders data, handles row click, shows empty state, shows skeleton when loading.

**Step 7: Commit**

```bash
git add frontend/components/ui/data-table*.tsx frontend/__tests__/
git commit -m "feat: DataTable component (TanStack Table v8)"
```

---

## Task 9: Page Layout Pattern Components

**Files:**
- Create: `frontend/components/layout/page-header.tsx`
- Create: `frontend/components/layout/list-page.tsx`
- Create: `frontend/components/layout/detail-page.tsx`
- Create: `frontend/components/layout/kpi-strip.tsx`
- Create: `frontend/components/layout/widget-grid.tsx`

**Step 1: PageHeader**

Reusable header with title (left) + actions slot (right). Accepts optional `description`, `badge` (for count), and `backLink`.

```typescript
interface PageHeaderProps {
  title: string;
  description?: string;
  badge?: React.ReactNode;
  backLink?: { href: string; label: string };
  actions?: React.ReactNode;
}
```

**Step 2: KpiStrip**

Horizontal row of KPI cards. Accepts `items: KpiItem[]` where:

```typescript
interface KpiItem {
  label: string;
  value: string;
  trend?: { direction: "up" | "down" | "neutral"; label: string };
  period?: string;
}
```

Uses JetBrains Mono for values, trend colors (emerald/red/slate).

**Step 3: WidgetGrid**

Simple 2-column responsive grid wrapper for dashboard widgets. Each widget is a card with title + "View all →" link + content slot.

```typescript
interface WidgetCardProps {
  title: string;
  viewAllHref?: string;
  children: React.ReactNode;
}
```

**Step 4: DetailPage layout**

Wrapper that renders: PageHeader (with back link) + horizontal tabs (URL-driven). Accepts `tabs: TabDef[]`:

```typescript
interface TabDef {
  id: string;
  label: string;
  content: React.ReactNode;
  count?: number;
}
```

Uses `nuqs` to persist `?tab=` in URL. Framer Motion animated underline.

**Step 5: Commit**

```bash
git add frontend/components/layout/
git commit -m "feat: page layout pattern components (PageHeader, KpiStrip, WidgetGrid, DetailPage)"
```

---

## Task 10: Status Badge System

**Files:**
- Create: `frontend/components/ui/status-badge.tsx`

Unified status badge that maps entity statuses to consistent colors:

```typescript
const STATUS_COLORS: Record<string, string> = {
  // Slate — neutral/pending
  DRAFT: "slate", PENDING: "slate", PROSPECT: "slate",
  // Blue — in progress
  IN_PROGRESS: "blue", ONBOARDING: "blue", SENT: "blue",
  // Emerald — positive/complete
  ACTIVE: "emerald", COMPLETED: "emerald", PAID: "emerald", ON_TRACK: "emerald", DONE: "emerald",
  // Amber — warning
  AT_RISK: "amber", OVERDUE: "amber", DORMANT: "amber",
  // Red — danger
  OVER_BUDGET: "red", CANCELLED: "red", VOID: "red", FAILED: "red",
  // Purple — archived
  ARCHIVED: "purple", OFFBOARDED: "purple", OFFBOARDING: "purple",
};
```

Usage: `<StatusBadge status="ACTIVE" />` — auto-maps to color + label.

**Step 1: Implement**
**Step 2: Write tests (snapshot or visual)**
**Step 3: Commit**

---

## Task 11: Zone 1 — Home Pages (Dashboard + My Work)

**Files:**
- Rewrite: `frontend/app/(app)/org/[slug]/dashboard/page.tsx`
- Rewrite: `frontend/app/(app)/org/[slug]/my-work/page.tsx`
- Rewrite: `frontend/components/dashboard/` (all widget components)
- Rewrite: `frontend/components/my-work/` (all components)

**Dashboard page:**
- Use `KpiStrip` for the top KPI cards
- Use `WidgetGrid` for the 2-column widget layout
- Keep the same API calls (`fetchDashboardKpis`, `fetchProjectHealth`, etc.)
- Keep role-based KPI visibility
- Remove the old page header pattern, use new `PageHeader`

**My Work page:**
- Personal time summary strip at top (using `KpiStrip`)
- Tasks grouped by project using a simple list (not DataTable — these are small sets)
- Today's time entries below with running total
- Floating "+ Log Time" button (FAB)

**Key pattern:** Both pages use server components for data fetching, pass data to client sub-components. No changes to API calls — just layout restructuring.

**Commit after each page works.**

---

## Task 12: Zone 2 — Work Pages (Projects + Schedules)

**Files:**
- Rewrite: `frontend/app/(app)/org/[slug]/projects/page.tsx`
- Rewrite: `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`
- Rewrite: `frontend/components/projects/` (20+ components)
- Rewrite: `frontend/app/(app)/org/[slug]/schedules/page.tsx`
- Rewrite: `frontend/app/(app)/org/[slug]/schedules/[id]/page.tsx`

**Projects list:**
- Use `DataTable` with columns: Name, Customer, Status, Tasks (done/total), Budget %, Due Date, Actions
- Use `DataTableToolbar` with search + status/customer/tag filter dropdowns
- Row click → navigate to project detail
- Same server actions for create/edit/delete

**Project detail:**
- Use `DetailPage` layout with 7 tabs: Overview, Tasks, Time, Budget, Documents, Team, Activity
- Keep existing 15+ data fetches (they work, just restructure layout)
- Overview tab: `KpiStrip` + task summary + team roster + comments
- Tasks tab: `DataTable` with inline create row
- Time tab: `DataTable` + summary sidebar
- Budget tab: gauge + chart (recharts)
- Team tab: member cards
- Activity tab: timeline feed

**Schedules:** Simple list page + detail page with 2 tabs.

**Commit after each page pair (list + detail) works.**

---

## Task 13: Zone 3 — Clients Pages (Customers)

**Files:**
- Rewrite: `frontend/app/(app)/org/[slug]/customers/page.tsx`
- Rewrite: `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`
- Rewrite: `frontend/components/customers/` (8 components)

**Customers list:**
- The sub-nav bar should show lifecycle status counts: [All (22)] [Prospects (4)] [Active (15)] etc.
- This requires a custom sub-nav override for the clients zone. Add lifecycle counts fetched from `GET /api/customers/lifecycle-summary`.
- Use `DataTable` for the customer list with lifecycle status filters
- Columns: Name, Lifecycle Status, Projects, Unbilled (R), Contact, Actions

**Customer detail:**
- Use `DetailPage` layout with 5 tabs: Overview, Projects, Lifecycle, Invoices, Documents
- Lifecycle tab: visual stepper (horizontal progress) + checklist items
- Keep existing server actions

**Commit after list + detail work.**

---

## Task 14: Zone 4 — Money Pages (Invoices + Retainers)

**Files:**
- Rewrite: `frontend/app/(app)/org/[slug]/invoices/page.tsx`
- Rewrite: `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx`
- Rewrite: `frontend/components/invoices/` (6 components)
- Rewrite: `frontend/app/(app)/org/[slug]/retainers/page.tsx`
- Rewrite: `frontend/app/(app)/org/[slug]/retainers/[id]/page.tsx`

**Invoices list:**
- `DataTable` with columns: Invoice # (monospace), Customer, Amount, Status, Issue Date, Due Date
- Overdue row tinting: `bg-red-50` for overdue invoices
- Status filters in toolbar

**Invoice detail:**
- 3 tabs: Line Items, Preview, Payments
- Line Items: editable table (inline add/edit/remove). Use React Hook Form for the line item form.
- Preview: rendered HTML iframe + template selector + PDF download
- Payments: timeline

**Retainers:** Similar pattern — list + detail (3 tabs: Overview, Consumption, Timeline).

**Commit after each entity pair works.**

---

## Task 15: Zone 5 — Docs Pages

**Files:**
- Rewrite: `frontend/app/(app)/org/[slug]/documents/page.tsx`
- Rewrite: `frontend/components/documents/` (8 components)

**Documents list:**
- `DataTable` with table ↔ card grid toggle
- Filters: scope, visibility, file type
- Upload button in PageHeader actions
- Card view: file type icon + name + metadata

**Commit when done.**

---

## Task 16: Zone 6 — Reports Pages

**Files:**
- Rewrite: `frontend/app/(app)/org/[slug]/profitability/page.tsx`
- Rewrite: `frontend/components/profitability/` (5 components)
- Rewrite: `frontend/app/(app)/org/[slug]/reports/page.tsx`
- Rewrite: `frontend/app/(app)/org/[slug]/reports/[reportSlug]/page.tsx`

**Profitability:**
- Toolbar: period selector + "View by" toggle (Project/Customer/Member)
- Data table with margin color-coding
- Utilization bar chart below (recharts)

**Reports hub:**
- Card grid of pre-built reports
- Each card links to report detail with filters

**Commit after each page.**

---

## Task 17: Zone 7 — Admin Pages (Settings Hub)

**Files:**
- Rewrite: `frontend/app/(app)/org/[slug]/settings/page.tsx` and all sub-pages
- Create: `frontend/components/shell/settings-sidebar.tsx`
- Rewrite: `frontend/app/(app)/org/[slug]/team/page.tsx`
- Rewrite: `frontend/app/(app)/org/[slug]/compliance/page.tsx` and sub-pages
- Rewrite: `frontend/app/(app)/org/[slug]/notifications/page.tsx`

**Settings layout:**
- Create `settings-sidebar.tsx` — secondary sidebar inside the content area
- Groups: General, Team, Work, Documents, Notifications, Compliance, Integrations
- On mobile: dropdown selector instead of sidebar

**Team page:** Move under admin zone. Use `DataTable` for member list.

**Compliance:** Move under admin zone. Data request list + detail pages.

**Notifications page:** Simple list with read/unread filter.

**Settings sub-pages:** Keep existing form components, just wrap in new settings layout.

**Commit after settings shell works, then per settings sub-page.**

---

## Task 18: Command Palette

**Files:**
- Create: `frontend/components/shell/command-palette.tsx`
- Modify: `frontend/components/shell/command-palette-button.tsx`
- Modify: `frontend/components/shell/app-shell.tsx` (add palette to shell)

**Implementation:**
- Use `cmdk` (already in deps)
- `⌘K` keyboard shortcut to open
- Search across: projects, customers, invoices, tasks (debounced 300ms)
- Quick actions: Create Project, Create Customer, Log Time, Go to Settings
- Results grouped by entity type with type badges
- Recent items section (persisted to localStorage)

**Commit when palette works.**

---

## Task 19: Floating Time Entry Button

**Files:**
- Create: `frontend/components/shell/time-entry-fab.tsx`
- Modify: `frontend/components/shell/app-shell.tsx` (add FAB)

**Implementation:**
- Fixed position bottom-right (above mobile tab bar on mobile)
- Teal pill button with clock icon + "Log Time"
- Opens the existing `LogTimeDialog` (from `components/tasks/log-time-dialog.tsx`)
- Pre-fills project context if on a project detail page

**Commit when done.**

---

## Task 20: Keyboard Shortcuts

**Files:**
- Create: `frontend/components/shell/keyboard-shortcuts.tsx`
- Create: `frontend/components/shell/shortcut-help-dialog.tsx`

**Implementation:**
- Global keyboard event listener (client component in AppShell)
- `⌘K` → command palette
- `?` → shortcut help overlay
- `c p` → create project dialog
- `c t` → create task dialog (if on project page)
- `c i` → create invoice dialog
- `j/k` → navigate table rows (when DataTable focused)
- `Enter` → open selected row
- `Escape` → close dialog/deselect

**Commit when done.**

---

## Task 21: Dark Mode Toggle

**Files:**
- Create: `frontend/components/shell/theme-toggle.tsx`
- Modify: `frontend/components/auth-header-controls.tsx` (add toggle to user menu)

**Implementation:**
- Toggle in user avatar dropdown menu
- Persists preference to localStorage
- Applies `.dark` class to `<html>` element
- Respects `prefers-color-scheme` as default

**Commit when done.**

---

## Task 22: Portal Pages (Customer Portal)

**Files:**
- Rewrite: `frontend/app/portal/` (all portal pages)
- Rewrite: `frontend/components/portal/` (3 components)

**Portal layout:**
- Simpler shell: no icon rail, just a top bar with logo + nav links
- Pages: Projects, Documents
- Magic link auth (existing pattern)

**Commit when done.**

---

## Task 23: Cleanup & Remove Old Components

**Files:**
- Delete: `frontend/components/desktop-sidebar.tsx`
- Delete: `frontend/components/mobile-sidebar.tsx`
- Delete: `frontend/components/sidebar-user-footer.tsx`
- Delete: `frontend/lib/nav-items.ts` (replaced by `navigation.ts`)
- Remove old imports from any remaining files

**Step 1: Search for any remaining imports of old components**

Run: `cd frontend && grep -r "desktop-sidebar\|mobile-sidebar\|sidebar-user-footer\|nav-items" --include="*.tsx" --include="*.ts" -l`

**Step 2: Update any remaining references**

**Step 3: Delete old files**

**Step 4: Run full build**

Run: `cd frontend && pnpm build`
Expected: Build succeeds with no import errors

**Step 5: Run all tests**

Run: `cd frontend && pnpm test`
Expected: All tests pass (some may need updates for removed components)

**Step 6: Commit**

```bash
git add -A
git commit -m "chore: remove old sidebar components and nav-items"
```

---

## Task 24: Final Verification & Polish

**Step 1: Full build**
Run: `cd frontend && pnpm build`

**Step 2: Full test suite**
Run: `cd frontend && pnpm test`

**Step 3: Lint**
Run: `cd frontend && pnpm lint`

**Step 4: Visual review**
Start dev server and manually verify:
- Icon rail active states
- Sub-nav appears/disappears per zone
- All pages render without errors
- Mobile layout (bottom tabs, stacked content)
- Dark mode toggle
- Command palette search

**Step 5: Commit any polish fixes**
