"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import { useOrgProfile } from "@/lib/org-profile";
import { useTerminology } from "@/lib/terminology";
import { SETTINGS_NAV_GROUPS } from "./settings-nav-groups";

/**
 * Translate a settings nav label using the active terminology profile.
 * Handles multi-word labels that contain a term (e.g. "Project Templates" → "Matter Templates").
 */
function translateNavLabel(label: string, t: (term: string) => string): string {
  // Map known terms that appear as a prefix in nav labels.
  const prefixTerms = ["Project", "Customer", "Client", "Proposal", "Invoice"];
  for (const term of prefixTerms) {
    if (label.startsWith(term + " ")) {
      const mapped = t(term);
      if (mapped !== term) {
        return `${mapped} ${label.slice(term.length + 1)}`;
      }
    }
  }
  return t(label);
}

interface SettingsSidebarProps {
  slug: string;
  isAdmin: boolean;
}

export function SettingsSidebar({ slug, isAdmin }: SettingsSidebarProps) {
  const pathname = usePathname();
  const { isModuleEnabled } = useOrgProfile();
  const { t } = useTerminology();

  // Flatten all visible items for mobile tab row (exclude comingSoon)
  const allVisibleItems = SETTINGS_NAV_GROUPS.flatMap((group) =>
    group.items.filter(
      (item) =>
        (!item.adminOnly || isAdmin) &&
        !item.comingSoon &&
        (!item.requiredModule || isModuleEnabled(item.requiredModule))
    )
  );

  return (
    <>
      {/* Mobile: horizontal scrollable pill tabs */}
      <div className="mb-4 md:hidden">
        <div className="flex gap-1 overflow-x-auto pb-2">
          {allVisibleItems.map((item) => {
            const isActive = pathname === `/org/${slug}/settings/${item.href}`;
            return (
              <Link
                key={item.href}
                href={`/org/${slug}/settings/${item.href}`}
                className={cn(
                  "shrink-0 rounded-full px-3 py-1.5 text-sm whitespace-nowrap transition-colors",
                  isActive
                    ? "bg-teal-600 text-white"
                    : "bg-muted text-muted-foreground hover:bg-muted/80"
                )}
              >
                {translateNavLabel(item.label, t)}
              </Link>
            );
          })}
        </div>
      </div>

      {/* Desktop: grouped nav */}
      <nav
        aria-label="Settings navigation"
        className="sticky top-14 h-[calc(100vh-3.5rem)] overflow-y-auto py-4"
      >
        {SETTINGS_NAV_GROUPS.map((group, index) => {
          const visibleItems = group.items.filter(
            (item) =>
              (!item.adminOnly || isAdmin) &&
              (!item.requiredModule || isModuleEnabled(item.requiredModule))
          );
          if (visibleItems.length === 0) return null;

          return (
            <div key={group.id}>
              {index > 0 && <div className="my-2 border-t border-slate-100" />}
              <div className="px-3 pt-3 pb-1 text-[11px] font-medium tracking-widest text-slate-400 uppercase dark:text-slate-500">
                {group.label}
              </div>
              {visibleItems.map((item) => {
                const isActive = pathname === `/org/${slug}/settings/${item.href}`;
                if (item.comingSoon) {
                  return (
                    <span
                      key={item.href || item.label}
                      aria-disabled="true"
                      className="flex cursor-not-allowed items-center rounded-r-md border-l-2 border-transparent py-1.5 pl-3 text-sm text-slate-600 opacity-50"
                    >
                      {translateNavLabel(item.label, t)}
                      <Badge variant="neutral" className="ml-auto py-0 text-[10px]">
                        Coming soon
                      </Badge>
                    </span>
                  );
                }
                return (
                  <Link
                    key={item.href}
                    href={`/org/${slug}/settings/${item.href}`}
                    className={cn(
                      "flex items-center rounded-r-md py-1.5 pl-3 text-sm transition-colors",
                      isActive
                        ? "border-l-2 border-teal-600 bg-teal-50 font-medium text-slate-900"
                        : "border-l-2 border-transparent text-slate-600 hover:bg-slate-50 hover:text-slate-900"
                    )}
                  >
                    {translateNavLabel(item.label, t)}
                  </Link>
                );
              })}
            </div>
          );
        })}
      </nav>
    </>
  );
}
