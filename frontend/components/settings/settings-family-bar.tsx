"use client";

import { useRef } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { ScrollArea, ScrollBar } from "@/components/ui/scroll-area";
import { SETTINGS_FAMILIES } from "@/lib/settings-nav";
import {
  TAB_EDGE_FADE_CLASS,
  TAB_STRIP_SCROLLBAR_CLASS,
  TAB_STRIP_SCROLL_AREA_CLASS,
  TAB_UNDERLINE_CURRENT,
} from "@/components/shared/tab-underline";
import { useEdgeFades } from "@/components/shared/use-edge-fades";

interface SettingsFamilyBarProps {
  slug: string;
}

/**
 * Settings family switch below `md` (Phase 91 664A, K-02, ADR-394): the five families from
 * SETTINGS_FAMILIES as plain links — real URLs, one tap to switch, no client navigation code, no
 * Select and no `role="tab"`. All five always show (§18.9; every family's first tab is ungated).
 * settings/layout.tsx renders it in a `md:hidden` wrapper and the rail in `hidden md:block`, so
 * exactly one "Settings navigation" landmark is in the accessibility tree at any width.
 */
export function SettingsFamilyBar({ slug }: SettingsFamilyBarProps) {
  const pathname = usePathname();
  const scrollAreaRef = useRef<HTMLDivElement>(null);

  // /org/{slug}/settings/{family}[/{sub-route}/…] → family (the rail's parse, settings-sidebar.tsx)
  const prefix = `/org/${slug}/settings/`;
  const activeFamilyId = pathname.startsWith(prefix)
    ? pathname.slice(prefix.length).split("/")[0]
    : null;

  const { fades, onFocus } = useEdgeFades(scrollAreaRef, {
    activeSelector: 'a[aria-current="page"]',
    itemSelector: "a[href]",
    activeKey: activeFamilyId,
  });

  return (
    <nav aria-label="Settings navigation" className="border-border relative border-b">
      <ScrollArea ref={scrollAreaRef} type="auto" className={TAB_STRIP_SCROLL_AREA_CLASS}>
        <ul className="flex w-max gap-2" onFocus={onFocus}>
          {SETTINGS_FAMILIES.map((family) => {
            const isActive = family.id === activeFamilyId;
            return (
              <li key={family.id} className="flex shrink-0">
                <Link
                  href={family.href(slug)}
                  aria-current={isActive ? "page" : undefined}
                  className={cn(TAB_UNDERLINE_CURRENT, "inline-flex items-center gap-1.5")}
                >
                  <family.icon className="size-4 shrink-0" aria-hidden="true" />
                  {family.label}
                </Link>
              </li>
            );
          })}
        </ul>
        <ScrollBar orientation="horizontal" className={TAB_STRIP_SCROLLBAR_CLASS} />
      </ScrollArea>
      <div
        aria-hidden="true"
        data-slot="settings-family-bar-fade-start"
        data-visible={fades.start}
        className={cn(TAB_EDGE_FADE_CLASS, "left-0 bg-linear-to-r")}
      />
      <div
        aria-hidden="true"
        data-slot="settings-family-bar-fade-end"
        data-visible={fades.end}
        className={cn(TAB_EDGE_FADE_CLASS, "right-0 bg-linear-to-l")}
      />
    </nav>
  );
}
