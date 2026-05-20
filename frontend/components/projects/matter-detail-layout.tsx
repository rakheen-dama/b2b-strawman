"use client";

import { type ReactNode, useState } from "react";
import { cn } from "@/lib/utils";
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
}

export function MatterDetailLayout({
  sidebar,
  children,
  defaultCollapsed = false,
}: MatterDetailLayoutProps) {
  const [collapsed, setCollapsed] = useState(() =>
    readStoredCollapsed(defaultCollapsed)
  );

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
        collapsed
          ? "grid-cols-[0_1fr]"
          : "grid-cols-[var(--sidebar-width)_1fr]"
      )}
    >
      {/* Sidebar slot */}
      <div
        className={cn(
          "overflow-y-auto overflow-x-hidden",
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
        {collapsed && (
          <SidebarCollapseToggle
            collapsed={true}
            onToggle={handleToggle}
            className="absolute top-4 left-4 z-10"
          />
        )}
        {children}
      </div>
    </div>
  );
}
