"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { cn } from "@/lib/utils";
import { useCapabilities } from "@/lib/capabilities";
import { useOrgProfile } from "@/lib/org-profile";
import type { NavFilterAccess } from "@/lib/nav-filter";
import {
  SETTINGS_FAMILIES,
  resolveSettingsTab,
  settingsHref,
  visibleSettingsTabs,
} from "@/lib/settings-nav";

interface SettingsSidebarProps {
  slug: string;
}

/**
 * Settings rail (Phase 87 S9, ADR-341 §12.7, §12.7.3–§12.7.4): the five families and, under the
 * active family, its permitted tabs — the same SETTINGS_FAMILIES entries filtered by the same rule
 * (lib/nav-filter.ts via visibleSettingsTabs) as the family page's tab strip, so a tab cannot be in
 * one and not the other. Desktop only: settings/layout.tsx hides the rail below `md`, where the
 * family page's own tab strip scrolls horizontally. Links only — no `role="tab"`, no headings.
 */
export function SettingsSidebar({ slug }: SettingsSidebarProps) {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { hasCapability, isAdmin, isOwner } = useCapabilities();
  const { isModuleEnabled } = useOrgProfile();
  const access: NavFilterAccess = { hasCapability, isAdmin, isOwner, isModuleEnabled };

  // /org/{slug}/settings/{family}[/{sub-route}/…] → [family, sub-route]
  const prefix = `/org/${slug}/settings/`;
  const [activeFamilyId, subRoute] = pathname.startsWith(prefix)
    ? pathname.slice(prefix.length).split("/")
    : [];

  return (
    <nav
      aria-label="Settings navigation"
      className="sticky top-14 h-[calc(100vh-3.5rem)] overflow-y-auto py-4"
    >
      <ul className="space-y-0.5">
        {SETTINGS_FAMILIES.map((family) => {
          const tabs = visibleSettingsTabs(family, access);
          if (tabs.length === 0) return null; // never today: every first tab is ungated
          const isActive = family.id === activeFamilyId;
          // Family page: the tab the page renders (?tab= resolved exactly as the server does).
          // Kept sub-route (e.g. matters/checklists/[id]/edit): its first segment is its tab id.
          const activeTabId = !isActive
            ? null
            : subRoute
              ? (tabs.find((t) => t.id === subRoute)?.id ?? null)
              : resolveSettingsTab(family, searchParams.get("tab") ?? undefined, access).tab.id;
          return (
            <li key={family.id}>
              <Link
                href={family.href(slug)}
                aria-current={isActive ? "page" : undefined}
                className={cn(
                  "flex items-center gap-2 rounded-md px-3 py-1.5 text-sm transition-colors",
                  isActive
                    ? "font-medium text-slate-900 dark:text-white"
                    : "text-slate-600 hover:bg-slate-50 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-white"
                )}
              >
                <family.icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                {family.label}
              </Link>
              {isActive && (
                <ul className="mt-0.5 mb-2 space-y-0.5">
                  {tabs.map((tab) => {
                    const isCurrent = tab.id === activeTabId;
                    return (
                      <li key={tab.id}>
                        <Link
                          href={settingsHref(slug, family.id, tab.id)}
                          aria-current={isCurrent ? "true" : undefined}
                          className={cn(
                            "block rounded-r-md border-l-2 py-1.5 pr-3 pl-9 text-sm transition-colors",
                            isCurrent
                              ? "border-teal-600 bg-teal-50 font-medium text-slate-900 dark:bg-slate-800 dark:text-white"
                              : "border-transparent text-slate-600 hover:bg-slate-50 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-white"
                          )}
                        >
                          {tab.label}
                        </Link>
                      </li>
                    );
                  })}
                </ul>
              )}
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
