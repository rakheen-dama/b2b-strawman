"use client";

import { useEffect, useOptimistic, useRef, useTransition, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

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

  // The new panel arrives with the server render, not with the click, so focus moves when the
  // selected `activeTab` commits (not in the handler, while the old panel is still mounted). A fast
  // second selection overwrites the ref, so the first one's commit does not steal focus.
  useEffect(() => {
    if (focusOnCommitRef.current !== activeTab) return;
    focusOnCommitRef.current = null;
    const panel = panelRef.current;
    if (!panel) return;
    (panel.querySelector<HTMLElement>(FOCUSABLE) ?? panel).focus();
  }, [activeTab]);

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
      {/* The strip scrolls on its own at narrow widths: the settings rail is desktop-only
          (settings/layout.tsx wraps it in `hidden md:block`; it has no mobile pill row — D11).
          `@container` (inline-size containment) keeps the `w-max` strip's width out of the page's
          min-content width — the app shell's flex column has no `min-w-0`, so without it the strip
          widened the whole page (1024px viewport: document 1250px wide) instead of scrolling in its
          own box. */}
      <div className="@container -mx-1 overflow-x-auto px-1 pb-1">
        <TabsList aria-label={label} className="w-max">
          {tabs.map((tab) => (
            <TabsTrigger key={tab.id} value={tab.id}>
              {tab.label}
            </TabsTrigger>
          ))}
        </TabsList>
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
