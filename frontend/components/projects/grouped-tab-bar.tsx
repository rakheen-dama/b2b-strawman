"use client";

import { useState, useRef, useCallback, type KeyboardEvent } from "react";
import { motion } from "motion/react";
import { ChevronDown } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import type { TabGroup } from "@/lib/constants/tab-groups";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface GroupedTabBarProps {
  groups: TabGroup[];
  activeTab: string;
  onTabChange: (tabId: string) => void;
}

// ---------------------------------------------------------------------------
// Trigger style (mirrors project-tabs.tsx TabsPrimitive.Trigger)
// ---------------------------------------------------------------------------

const triggerClassName = cn(
  "relative pb-3 text-sm font-medium transition-colors outline-none cursor-pointer",
  "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-200",
  "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-500",
);

const activeTriggerClassName = "text-slate-950 dark:text-slate-50";

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function GroupedTabBar({ groups, activeTab, onTabChange }: GroupedTabBarProps) {
  const visibleGroups = groups.filter(
    (g) => g.visible && g.tabs.length > 0,
  );

  const groupRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const [openGroupId, setOpenGroupId] = useState<string | null>(null);

  // Determine which group the active tab belongs to
  const activeGroupId = visibleGroups.find((g) =>
    g.tabs.some((t) => t.id === activeTab),
  )?.id ?? null;

  // Keyboard: ArrowLeft / ArrowRight between group triggers
  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLDivElement>) => {
      if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;

      e.preventDefault();
      const currentEl = document.activeElement as HTMLElement | null;
      const currentIndex = groupRefs.current.findIndex((ref) => ref === currentEl);
      if (currentIndex === -1) return;

      const len = visibleGroups.length;
      const nextIndex =
        e.key === "ArrowRight"
          ? (currentIndex + 1) % len
          : (currentIndex - 1 + len) % len;

      groupRefs.current[nextIndex]?.focus();
    },
    [visibleGroups.length],
  );

  return (
    <div
      role="tablist"
      data-testid="grouped-tab-bar"
      className="relative flex gap-6 border-b border-slate-200 dark:border-slate-800"
      onKeyDown={handleKeyDown}
    >
      {visibleGroups.map((group, idx) => {
        const isActive = activeGroupId === group.id;
        const activeSubTab = isActive
          ? group.tabs.find((t) => t.id === activeTab)
          : null;
        const isSingle = group.tabs.length === 1;

        // Build the label: "Finance · Time" when a sub-tab is active
        const label = (
          <>
            {group.label}
            {activeSubTab && !isSingle && (
              <span className="text-muted-foreground"> &middot; {activeSubTab.label}</span>
            )}
          </>
        );

        // Single-tab group: plain button (no dropdown)
        if (isSingle) {
          return (
            <button
              key={group.id}
              ref={(el) => { groupRefs.current[idx] = el; }}
              role="tab"
              aria-selected={isActive}
              data-testid={`tab-group-${group.id}`}
              className={cn(triggerClassName, isActive && activeTriggerClassName)}
              onClick={() => onTabChange(group.tabs[0].id)}
            >
              {label}
              {isActive && (
                <motion.span
                  className="absolute inset-x-0 bottom-0 h-0.5 bg-teal-500"
                  layoutId="grouped-tab-indicator"
                  transition={{ type: "spring", stiffness: 300, damping: 25 }}
                  aria-hidden="true"
                />
              )}
            </button>
          );
        }

        // Multi-tab group: DropdownMenu
        return (
          <DropdownMenu
            key={group.id}
            open={openGroupId === group.id}
            onOpenChange={(open) => setOpenGroupId(open ? group.id : null)}
          >
            <DropdownMenuTrigger asChild>
              <button
                ref={(el) => { groupRefs.current[idx] = el; }}
                role="tab"
                aria-selected={isActive}
                data-testid={`tab-group-${group.id}`}
                className={cn(
                  triggerClassName,
                  isActive && activeTriggerClassName,
                  "inline-flex items-center gap-1",
                )}
                onClick={(e) => {
                  // If no sub-tab in this group is active, navigate to first tab
                  if (!isActive) {
                    e.preventDefault();
                    onTabChange(group.tabs[0].id);
                  }
                  // else: let Radix open the dropdown
                }}
              >
                {label}
                <ChevronDown className="size-3 opacity-50" />
                {isActive && (
                  <motion.span
                    className="absolute inset-x-0 bottom-0 h-0.5 bg-teal-500"
                    layoutId="grouped-tab-indicator"
                    transition={{ type: "spring", stiffness: 300, damping: 25 }}
                    aria-hidden="true"
                  />
                )}
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start">
              {group.tabs.map((tab) => (
                <DropdownMenuItem
                  key={tab.id}
                  data-testid={`tab-item-${tab.id}`}
                  onSelect={() => {
                    onTabChange(tab.id);
                    setOpenGroupId(null);
                  }}
                  className={cn(
                    activeTab === tab.id &&
                      "bg-slate-100 dark:bg-slate-800 font-medium",
                  )}
                >
                  {tab.label}
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        );
      })}
    </div>
  );
}
