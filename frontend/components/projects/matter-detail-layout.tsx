"use client";

import { type ReactNode, useState } from "react";
import { PanelLeft } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { SidebarCollapseToggle } from "@/components/projects/sidebar-collapse-toggle";

const STORAGE_KEY = "kazi-matter-sidebar-collapsed";

/** Read persisted collapse state. Safe for SSR (returns fallback). */
function readStoredCollapsed(fallback: boolean): boolean {
  if (typeof window === "undefined") return fallback;
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored !== null) return stored === "true";
  } catch {
    // localStorage unavailable (private mode) — keep fallback
  }
  return fallback;
}

interface MatterDetailLayoutProps {
  sidebar: ReactNode;
  children: ReactNode;
  defaultCollapsed?: boolean;
  /** Optional sidebar content for mobile Sheet (defaults to `sidebar`). */
  mobileSidebar?: ReactNode;
}

export function MatterDetailLayout({
  sidebar,
  children,
  defaultCollapsed = false,
  mobileSidebar,
}: MatterDetailLayoutProps) {
  const [collapsed, setCollapsed] = useState(() => readStoredCollapsed(defaultCollapsed));

  const handleToggle = () => {
    const next = !collapsed;
    setCollapsed(next);
    try {
      localStorage.setItem(STORAGE_KEY, String(next));
    } catch {
      // localStorage unavailable — state still updates
    }
  };

  return (
    <div
      data-testid="matter-detail-layout"
      className={cn(
        "grid min-h-0 transition-[grid-template-columns] duration-200 ease-in-out",
        // Mobile: always single column; Desktop: two-column (respects collapse)
        "grid-cols-[1fr]",
        collapsed ? "lg:grid-cols-[0_1fr]" : "lg:grid-cols-[var(--sidebar-width)_1fr]"
      )}
    >
      {/* Sidebar slot — hidden below lg */}
      <div
        className={cn(
          "hidden overflow-x-hidden overflow-y-auto lg:block",
          !collapsed && "border-r border-slate-200 dark:border-slate-800"
        )}
        aria-hidden={collapsed}
        inert={collapsed ? true : undefined}
      >
        {!collapsed && (
          <div className="flex justify-end p-2">
            <SidebarCollapseToggle collapsed={false} onToggle={handleToggle} />
          </div>
        )}
        {sidebar}
      </div>

      {/* Main content slot */}
      <div className="relative min-w-0 overflow-y-auto">
        {/* Mobile sidebar Sheet trigger — visible below lg */}
        <Sheet>
          <SheetTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="absolute top-4 left-4 z-10 lg:hidden"
              data-testid="mobile-sidebar-trigger"
            >
              <PanelLeft className="size-5" />
              <span className="sr-only">Open sidebar</span>
            </Button>
          </SheetTrigger>
          <SheetContent side="left" className="w-[280px] overflow-y-auto p-0">
            <SheetTitle className="sr-only">Matter sidebar</SheetTitle>
            {mobileSidebar ?? sidebar}
          </SheetContent>
        </Sheet>

        {/* Desktop collapse toggle — visible at lg+ when sidebar is collapsed */}
        {collapsed && (
          <SidebarCollapseToggle
            collapsed={true}
            onToggle={handleToggle}
            className="absolute top-4 left-4 z-10 hidden lg:inline-flex"
          />
        )}
        {children}
      </div>
    </div>
  );
}
