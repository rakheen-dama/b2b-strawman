"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { cn } from "@/lib/utils";
import { SETTINGS_NAV_GROUPS } from "./settings-nav-groups";

interface SettingsSidebarProps {
  slug: string;
  isAdmin: boolean;
}

export function SettingsSidebar({ slug, isAdmin }: SettingsSidebarProps) {
  const pathname = usePathname();

  // Flatten all visible items for mobile tab row
  const allVisibleItems = SETTINGS_NAV_GROUPS.flatMap((group) =>
    group.items.filter((item) => !item.adminOnly || isAdmin),
  );

  return (
    <>
      {/* Mobile: horizontal scrollable pill tabs */}
      <div className="mb-4 md:hidden">
        <div className="flex gap-1 overflow-x-auto pb-2">
          {allVisibleItems.map((item) => {
            const isActive = pathname.endsWith(`/settings/${item.href}`);
            if (item.comingSoon) {
              return (
                <span
                  key={item.href}
                  className={cn(
                    "shrink-0 rounded-full px-3 py-1.5 text-sm whitespace-nowrap",
                    "bg-slate-100 text-slate-400 cursor-not-allowed opacity-50",
                  )}
                >
                  {item.label}
                </span>
              );
            }
            return (
              <Link
                key={item.href}
                href={`/org/${slug}/settings/${item.href}`}
                className={cn(
                  "shrink-0 rounded-full px-3 py-1.5 text-sm whitespace-nowrap transition-colors",
                  isActive
                    ? "bg-teal-600 text-white"
                    : "bg-slate-100 text-slate-700 hover:bg-slate-200",
                )}
              >
                {item.label}
              </Link>
            );
          })}
        </div>
      </div>

      {/* Desktop: grouped nav */}
      <nav aria-label="Settings navigation">
        {SETTINGS_NAV_GROUPS.map((group) => {
          const visibleItems = group.items.filter(
            (item) => !item.adminOnly || isAdmin,
          );
          if (visibleItems.length === 0) return null;

          return (
            <div key={group.id}>
              <div className="px-3 pb-1 pt-3 text-[11px] font-medium uppercase tracking-widest text-slate-400">
                {group.label}
              </div>
              {visibleItems.map((item) => {
                if (item.comingSoon) {
                  return (
                    <span
                      key={item.href}
                      className="flex cursor-not-allowed items-center py-1.5 pl-3 text-sm text-slate-400 opacity-50"
                    >
                      {item.label}
                    </span>
                  );
                }
                return (
                  <Link
                    key={item.href}
                    href={`/org/${slug}/settings/${item.href}`}
                    className={cn(
                      "flex items-center py-1.5 pl-3 text-sm transition-colors",
                      "text-slate-600 hover:text-slate-900 hover:bg-slate-50",
                    )}
                  >
                    {item.label}
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
