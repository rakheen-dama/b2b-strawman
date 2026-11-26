"use client";

import { useEffect, useOptimistic, useRef, useTransition, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { cn } from "@/lib/utils";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ScrollArea, ScrollBar } from "@/components/ui/scroll-area";
import {
  TAB_EDGE_FADE_CLASS,
  TAB_STRIP_SCROLLBAR_CLASS,
  TAB_STRIP_SCROLL_AREA_CLASS,
  TAB_UNDERLINE_SELECTED,
} from "@/components/shared/tab-underline";
import { useEdgeFades } from "@/components/shared/use-edge-fades";

export interface SettingsFamilyTabItem {
  id: string;
  label: string;
}

interface SettingsFamilyTabsProps {
  /** Accessible name of the tablist, e.g. "Firm settings". */
  label: string;
  /** Permitted tabs only — admin-only tabs are absent, never disabled (§12.7.4). */
  tabs: SettingsFamilyTabItem[];
  /** Tab resolved on the server from `?tab=`; its panel is `children`. */
  activeTab: string;
  children: ReactNode;
}

/** Tabbable descendants in DOM order — the panel's "first focusable control" (§12.7.4). */
const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * The shadcn `line` list with the shared 642C underline (664A, K-101). The trigger overrides drop
 * what the shadcn trigger brings for its own pill/line looks — `flex-1`, the fixed height, the
 * border and the outside focus outline (both would be clipped by the scroll viewport) and its own
 * underline at `bottom: -5px`, which the viewport would clip — so exactly one 2px underline,
 * keyed on `aria-selected`, sits at the bottom edge inside the scroll area.
 */
const LIST_CLASS = "w-max gap-2 p-0 group-data-[orientation=horizontal]/tabs:h-auto";
const TRIGGER_CLASS = cn(
  TAB_UNDERLINE_SELECTED,
  "h-auto flex-none border-0 focus-visible:outline-none group-data-[orientation=horizontal]/tabs:after:bottom-0"
);

/**
 * Tab strip for a settings family page (Phase 87 S9, ADR-341 §12.7.4). The URL is the single
 * source of truth: `activeTab` is resolved on the server from `?tab=`, a click only calls
 * `router.replace` (`scroll: false`) and shows the clicked tab optimistically until the new server
 * render commits. No local tab state can outvote the URL (the MatterTabs "URL wins over click"
 * bug class), so in-page `?tab=` links and back/forward always land on the right tab. Switching
 * tabs drops the previous panel's own query params (e.g. the audit-trail filters).
 * Focus (§12.7.4, 616E.2): when the user selects a tab in this strip, the new panel's first
 * focusable control (or the panel itself) takes focus once the server render commits it. Loads and
 * URL-driven changes (back/forward, the settings rail, in-page `?tab=` links) never move focus.
 */
export function SettingsFamilyTabs({ label, tabs, activeTab, children }: SettingsFamilyTabsProps) {
  const router = useRouter();
  const pathname = usePathname();
  const [isPending, startTransition] = useTransition();
  const [shownTab, setShownTab] = useOptimistic(activeTab);
  const panelRef = useRef<HTMLDivElement>(null);
  /** The tab the user just selected here; null on load and for URL-driven changes. */
  const focusOnCommitRef = useRef<string | null>(null);
  const scrollAreaRef = useRef<HTMLDivElement>(null);
  const { fades, onFocus } = useEdgeFades(scrollAreaRef, {
    activeSelector: '[role="tab"][aria-selected="true"]',
    itemSelector: '[role="tab"]',
    activeKey: shownTab,
  });

  // The new panel arrives with the server render, not with the click, so focus moves when the
  // selected `activeTab` commits (not in the handler, while the old panel is still mounted). A fast
  // second selection overwrites the ref, so the first one's commit does not steal focus. The
  // transition covers `router.replace` until its render commits, so a selection still unmatched
  // once it settles (`!isPending`) never committed (aborted, or superseded by a URL-driven change)
  // and is dropped — a later URL-driven change to that tab must not move focus.
  useEffect(() => {
    const selected = focusOnCommitRef.current;
    if (selected === null) return;
    if (selected !== activeTab) {
      if (!isPending) focusOnCommitRef.current = null;
      return;
    }
    focusOnCommitRef.current = null;
    const panel = panelRef.current;
    if (!panel) return;
    (panel.querySelector<HTMLElement>(FOCUSABLE) ?? panel).focus();
  }, [activeTab, isPending]);

  function handleValueChange(next: string) {
    if (next === shownTab) return;
    focusOnCommitRef.current = next;
    startTransition(() => {
      setShownTab(next);
      router.replace(`${pathname}?tab=${encodeURIComponent(next)}`, { scroll: false });
    });
  }

  return (
    // Manual activation (WAI-ARIA APG: use it when a panel cannot display instantly): arrow keys
    // move focus only and Enter/Space or a click selects, so arrowing across the strip does not
    // fire one server navigation per tab — and a click navigates once, not on mousedown + focus.
    <Tabs value={shownTab} onValueChange={handleValueChange} activationMode="manual">
      {/* The strip scrolls on its own in a horizontal ScrollArea with edge fades over the tabs
          hidden past either end (664A, K-101); the active tab is kept clear of the fades. The
          settings rail is desktop-only (settings/layout.tsx wraps it in `hidden md:block`); below
          `md` the family link bar switches families and this strip switches tabs (D11). The app
          shell's content column and <main> carry `min-w-0` (Phase 88 628B), so the `w-max` strip
          scrolls inside the viewport instead of widening the page. Not sticky (ADR-394). */}
      <div className="border-border relative border-b">
        <ScrollArea ref={scrollAreaRef} type="auto" className={TAB_STRIP_SCROLL_AREA_CLASS}>
          <TabsList aria-label={label} variant="line" className={LIST_CLASS} onFocus={onFocus}>
            {tabs.map((tab) => (
              <TabsTrigger key={tab.id} value={tab.id} className={TRIGGER_CLASS}>
                {tab.label}
              </TabsTrigger>
            ))}
          </TabsList>
          <ScrollBar orientation="horizontal" className={TAB_STRIP_SCROLLBAR_CLASS} />
        </ScrollArea>
        <div
          aria-hidden="true"
          data-slot="settings-family-tabs-fade-start"
          data-visible={fades.start}
          className={cn(TAB_EDGE_FADE_CLASS, "left-0 bg-linear-to-r")}
        />
        <div
          aria-hidden="true"
          data-slot="settings-family-tabs-fade-end"
          data-visible={fades.end}
          className={cn(TAB_EDGE_FADE_CLASS, "right-0 bg-linear-to-l")}
        />
      </div>
      {/* forceMount keeps the current panel visible while the next server render is pending. */}
      <TabsContent
        ref={panelRef}
        value={activeTab}
        forceMount
        className={isPending ? "pt-6 opacity-60 transition-opacity" : "pt-6"}
        aria-busy={isPending || undefined}
      >
        {children}
      </TabsContent>
    </Tabs>
  );
}
