"use client";

import {
  useState,
  useRef,
  useCallback,
  useEffect,
  useMemo,
  type FocusEvent,
  type KeyboardEvent,
} from "react";
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
import {
  TAB_UNDERLINE_SELECTED as TRIGGER_CLASS,
  TAB_STRIP_SCROLL_AREA_CLASS as SCROLL_AREA_CLASS,
  TAB_STRIP_SCROLLBAR_CLASS as SCROLLBAR_CLASS,
  TAB_EDGE_FADE_CLASS as FADE_CLASS,
  findScrollViewport as findViewport,
  scrollItemClearOfFades as scrollTriggerClearOfFades,
  type EdgeFades,
} from "@/components/shared/tab-underline";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface GroupedTabBarProps {
  groups: TabGroup[];
  activeTab: string;
  onTabChange: (tabId: string) => void;
}

// ---------------------------------------------------------------------------
// Styles (tokens only — the trigger underline, scroll area and edge fades: shared/tab-underline.ts)
// ---------------------------------------------------------------------------

/**
 * The floating header is `sticky top-2` and `--app-header-height` tall, so its bottom edge is at
 * header height + 0.5rem (the DataTable sticky-head offset). Opaque page background; z-20 stays
 * under the header (z-30) and the portalled menus (z-50). The horizontal scroller lives *inside*
 * this element — an overflow ancestor would become the sticky scrollport and stop it sticking.
 */
const STICKY_BAR_CLASS =
  "bg-background border-border sticky top-[calc(var(--app-header-height)+0.5rem)] z-20 border-b";

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
  const activeGroupIndex = visibleGroups.findIndex((g) => g.tabs.some((t) => t.id === activeTab));
  const activeGroupId = activeGroupIndex === -1 ? null : visibleGroups[activeGroupIndex].id;

  // Edge fades follow the scroll viewport (scroll + resize of the viewport or the tab row). When the
  // viewport itself changes width (window narrowed, device rotated), the active trigger is kept clear
  // of the fades first, or a narrowing could leave it hidden past the end edge. Only an actual
  // viewport width change does that: the user's own horizontal scroll (a `scroll` event) and a
  // tab-row resize at the same width (a label growing) only refresh the fades, so this never fights
  // the user, and the `scrollLeft` write itself cannot re-trigger it.
  useEffect(() => {
    const viewport = findViewport(scrollAreaRef.current);
    if (!viewport) return;

    const update = () => {
      const { scrollLeft, scrollWidth, clientWidth } = viewport;
      const next = {
        start: scrollLeft > 0,
        end: scrollLeft + clientWidth < scrollWidth - 1,
      };
      setFades((prev) => (prev.start === next.start && prev.end === next.end ? prev : next));
    };

    let lastViewportWidth = viewport.clientWidth;
    const handleResize = () => {
      const width = viewport.clientWidth;
      if (width !== lastViewportWidth) {
        lastViewportWidth = width;
        const active = viewport.querySelector<HTMLElement>(
          '[role="tablist"] [role="tab"][aria-selected="true"]'
        );
        if (active) scrollTriggerClearOfFades(viewport, active);
      }
      update();
    };

    update();
    viewport.addEventListener("scroll", update, { passive: true });
    let observer: ResizeObserver | undefined;
    if (typeof ResizeObserver !== "undefined") {
      observer = new ResizeObserver(handleResize);
      observer.observe(viewport);
      const row = viewport.querySelector('[role="tablist"]');
      if (row) observer.observe(row);
    }
    return () => {
      viewport.removeEventListener("scroll", update);
      observer?.disconnect();
    };
  }, []);

  // Keep the active trigger in view, e.g. on a deep link such as `?tab=disbursements` at a narrow
  // width, when the tab changes, or when the label grows ("Fees · Disbursements"). It runs in an
  // effect after hydration.
  useEffect(() => {
    const viewport = findViewport(scrollAreaRef.current);
    const trigger = activeGroupIndex === -1 ? null : groupRefs.current[activeGroupIndex];
    if (!viewport || !trigger) return;
    scrollTriggerClearOfFades(viewport, trigger);
  }, [activeTab, activeGroupIndex]);

  // Keep a keyboard-focused trigger in view too (arrow keys, Tab / Shift+Tab, focus returned from a
  // closed menu), so its focus ring is never hidden under a fade (WCAG 2.4.11). The browser's own
  // focus scroll skips a trigger that is already partly visible. Focus from a mouse click is left
  // alone (it isn't `:focus-visible`): scrolling the trigger away from under the pointer between
  // mousedown and mouseup would retarget the click to the row and lose it.
  const handleFocus = useCallback((e: FocusEvent<HTMLDivElement>) => {
    const trigger = e.target;
    if (trigger.getAttribute("role") !== "tab" || !trigger.matches(":focus-visible")) return;
    const viewport = findViewport(scrollAreaRef.current);
    if (viewport) scrollTriggerClearOfFades(viewport, trigger);
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
      <ScrollArea ref={scrollAreaRef} type="auto" className={SCROLL_AREA_CLASS}>
        <div
          role="tablist"
          data-testid="grouped-tab-bar"
          className="relative flex w-max gap-2"
          onKeyDown={handleKeyDown}
          onFocus={handleFocus}
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
        <ScrollBar orientation="horizontal" className={SCROLLBAR_CLASS} />
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
