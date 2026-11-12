"use client";

import { useState, useRef, useCallback, useEffect, useMemo, type KeyboardEvent } from "react";
import { ChevronDown } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import { ScrollArea, ScrollBar } from "@/components/ui/scroll-area";
import type { TabGroup } from "@/lib/constants/tab-group-types";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface GroupedTabBarProps {
  groups: TabGroup[];
  activeTab: string;
  onTabChange: (tabId: string) => void;
}

// ---------------------------------------------------------------------------
// Styles (tokens only — the shadcn `line` tab variant in components/ui/tabs.tsx)
// ---------------------------------------------------------------------------

/**
 * The floating header is `sticky top-2` and `--app-header-height` tall, so its bottom edge is at
 * header height + 0.5rem (the DataTable sticky-head offset). Opaque page background; z-20 stays
 * under the header (z-30) and the portalled menus (z-50). The horizontal scroller lives *inside*
 * this element — an overflow ancestor would become the sticky scrollport and stop it sticking.
 */
const STICKY_BAR_CLASS =
  "bg-background border-border sticky top-[calc(var(--app-header-height)+0.5rem)] z-20 border-b";

/**
 * Active state keys on `aria-selected` — not `data-state`, which Radix's DropdownMenuTrigger sets
 * to `open|closed` on the multi-tab triggers. The 2px underline is an `after:` pseudo-element in
 * `foreground` (as the line variant); the focus ring is inset so the scroll viewport can't clip it.
 */
const TRIGGER_CLASS = cn(
  "relative shrink-0 cursor-pointer rounded-sm px-2 pt-2 pb-3 text-sm font-medium whitespace-nowrap outline-none transition-colors motion-reduce:transition-none",
  "text-muted-foreground hover:text-foreground aria-selected:text-foreground",
  "after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:rounded-full after:bg-foreground after:opacity-0 after:transition-opacity aria-selected:after:opacity-100 motion-reduce:after:transition-none",
  "focus-visible:ring-ring focus-visible:ring-2 focus-visible:ring-inset"
);

/** Edge fade overlays: shown only while there is tab content hidden past that edge. */
const FADE_CLASS =
  "from-background pointer-events-none absolute inset-y-0 w-8 opacity-0 transition-opacity data-[visible=true]:opacity-100 motion-reduce:transition-none";

interface EdgeFades {
  start: boolean;
  end: boolean;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function GroupedTabBar({ groups, activeTab, onTabChange }: GroupedTabBarProps) {
  const visibleGroups = useMemo(
    () =>
      groups
        .filter((g) => g.visible)
        .map((g) => ({
          ...g,
          tabs: g.tabs.filter((t) => t.visible !== false),
        }))
        .filter((g) => g.tabs.length > 0),
    [groups]
  );

  const groupRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const scrollAreaRef = useRef<HTMLDivElement>(null);
  const [openGroupId, setOpenGroupId] = useState<string | null>(null);
  const [fades, setFades] = useState<EdgeFades>({ start: false, end: false });

  // Determine which group the active tab belongs to
  const activeGroupId =
    visibleGroups.find((g) => g.tabs.some((t) => t.id === activeTab))?.id ?? null;

  // Edge fades follow the scroll viewport (scroll + resize of the viewport or the tab row).
  useEffect(() => {
    const viewport = scrollAreaRef.current?.querySelector<HTMLElement>(
      '[data-slot="scroll-area-viewport"]'
    );
    if (!viewport) return;

    const update = () => {
      const { scrollLeft, scrollWidth, clientWidth } = viewport;
      const next = {
        start: scrollLeft > 0,
        end: scrollLeft + clientWidth < scrollWidth - 1,
      };
      setFades((prev) => (prev.start === next.start && prev.end === next.end ? prev : next));
    };

    update();
    viewport.addEventListener("scroll", update, { passive: true });
    let observer: ResizeObserver | undefined;
    if (typeof ResizeObserver !== "undefined") {
      observer = new ResizeObserver(update);
      observer.observe(viewport);
      const row = viewport.querySelector('[role="tablist"]');
      if (row) observer.observe(row);
    }
    return () => {
      viewport.removeEventListener("scroll", update);
      observer?.disconnect();
    };
  }, []);

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
        e.key === "ArrowRight" ? (currentIndex + 1) % len : (currentIndex - 1 + len) % len;

      groupRefs.current[nextIndex]?.focus();
    },
    [visibleGroups.length]
  );

  return (
    <div data-slot="grouped-tab-bar-sticky" className={STICKY_BAR_CLASS}>
      <ScrollArea ref={scrollAreaRef} type="scroll" className="w-full">
        <div
          role="tablist"
          data-testid="grouped-tab-bar"
          className="flex w-max gap-2"
          onKeyDown={handleKeyDown}
        >
          {visibleGroups.map((group, idx) => {
            const isActive = activeGroupId === group.id;
            const activeSubTab = isActive ? group.tabs.find((t) => t.id === activeTab) : null;
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
                  ref={(el) => {
                    groupRefs.current[idx] = el;
                  }}
                  role="tab"
                  aria-selected={isActive}
                  data-testid={`tab-group-${group.id}`}
                  className={TRIGGER_CLASS}
                  onClick={() => onTabChange(group.tabs[0].id)}
                >
                  {label}
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
                    ref={(el) => {
                      groupRefs.current[idx] = el;
                    }}
                    role="tab"
                    aria-selected={isActive}
                    data-testid={`tab-group-${group.id}`}
                    className={cn(TRIGGER_CLASS, "inline-flex items-center gap-1")}
                    onClick={() => {
                      if (!isActive) {
                        // Navigate to first sub-tab; keep dropdown closed via controlled open prop
                        setOpenGroupId(null);
                        onTabChange(group.tabs[0].id);
                      }
                      // else: isActive — Radix opens via controlled open prop
                    }}
                  >
                    {label}
                    <ChevronDown className="size-3 opacity-50" />
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start">
                  {group.tabs.map((tab) => {
                    const isCurrent = activeTab === tab.id;
                    return (
                      <DropdownMenuItem
                        key={tab.id}
                        data-testid={`tab-item-${tab.id}`}
                        data-active={isCurrent ? "true" : undefined}
                        onSelect={() => {
                          onTabChange(tab.id);
                          setOpenGroupId(null);
                        }}
                        className={cn(isCurrent && "bg-muted font-medium")}
                      >
                        {tab.label}
                      </DropdownMenuItem>
                    );
                  })}
                </DropdownMenuContent>
              </DropdownMenu>
            );
          })}
        </div>
        <ScrollBar orientation="horizontal" className="h-1.5" />
      </ScrollArea>
      <div
        aria-hidden="true"
        data-slot="grouped-tab-bar-fade-start"
        data-visible={fades.start}
        className={cn(FADE_CLASS, "left-0 bg-linear-to-r")}
      />
      <div
        aria-hidden="true"
        data-slot="grouped-tab-bar-fade-end"
        data-visible={fades.end}
        className={cn(FADE_CLASS, "right-0 bg-linear-to-l")}
      />
    </div>
  );
}
