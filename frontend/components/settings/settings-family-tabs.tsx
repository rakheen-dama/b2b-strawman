"use client";

import { useOptimistic, useTransition, type ReactNode } from "react";
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

/**
 * Tab strip for a settings family page (Phase 87 S9, ADR-341 §12.7.4). The URL is the single
 * source of truth: `activeTab` is resolved on the server from `?tab=`, a click only calls
 * `router.replace` (`scroll: false`) and shows the clicked tab optimistically until the new server
 * render commits. No local tab state can outvote the URL (the MatterTabs "URL wins over click"
 * bug class), so in-page `?tab=` links and back/forward always land on the right tab. Switching
 * tabs drops the previous panel's own query params (e.g. the audit-trail filters).
 * 616E.2: focus the panel's first control on user-initiated change; `aria-current` on the rail.
 */
export function SettingsFamilyTabs({ label, tabs, activeTab, children }: SettingsFamilyTabsProps) {
  const router = useRouter();
  const pathname = usePathname();
  const [isPending, startTransition] = useTransition();
  const [shownTab, setShownTab] = useOptimistic(activeTab);

  function handleValueChange(next: string) {
    if (next === shownTab) return;
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
      {/* The strip scrolls on its own at narrow widths: the rail's mobile pill row never renders
          (settings/layout.tsx wraps the rail in `hidden md:block`). */}
      <div className="-mx-1 overflow-x-auto px-1 pb-1">
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
