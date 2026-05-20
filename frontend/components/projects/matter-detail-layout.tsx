"use client";

import { type ReactNode, useState } from "react";
import { cn } from "@/lib/utils";
import { SidebarCollapseToggle } from "@/components/projects/sidebar-collapse-toggle";

const STORAGE_KEY = "kazi-matter-sidebar-collapsed";

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
  const [collapsed, setCollapsed] = useState(() => {
    if (typeof window === "undefined") return defaultCollapsed;
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored !== null) return stored === "true";
    } catch {
      // localStorage unavailable (private mode) — keep default
    }
    return defaultCollapsed;
  });

  function handleToggle() {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem(STORAGE_KEY, String(next));
      } catch {
        // localStorage unavailable — ignore
      }
      return next;
    });
  }

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
      <div className="overflow-y-auto overflow-x-hidden border-r border-slate-200 dark:border-slate-800">
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
