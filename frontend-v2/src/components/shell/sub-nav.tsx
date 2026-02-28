"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { getActiveZone, isSubNavActive } from "@/lib/navigation";
import { cn } from "@/lib/utils";

interface SubNavProps {
  slug: string;
}

export function SubNav({ slug }: SubNavProps) {
  const pathname = usePathname();
  const activeZone = getActiveZone(pathname, slug);

  // Hide if no active zone, zone is admin (has its own settings sidebar), or <= 1 sub-nav item
  if (!activeZone || activeZone.id === "admin" || activeZone.subNav.length <= 1) {
    return null;
  }

  return (
    <div className="flex h-[var(--subnav-height)] items-center gap-1 border-b border-slate-200/60 bg-slate-50 px-4 dark:border-slate-800/60 dark:bg-slate-800">
      {activeZone.subNav.map((item) => {
        const href = item.href(slug);
        const isActive = isSubNavActive(item, pathname, slug);

        return (
          <Link
            key={item.label}
            href={href}
            className={cn(
              "rounded-full px-3 py-1 text-sm font-medium transition-colors",
              isActive
                ? "bg-white text-teal-600 shadow-sm dark:bg-slate-700 dark:text-teal-400"
                : "text-slate-600 hover:bg-white/60 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-700/60 dark:hover:text-slate-200",
            )}
          >
            {item.label}
          </Link>
        );
      })}
    </div>
  );
}
