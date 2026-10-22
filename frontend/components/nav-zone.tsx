"use client";

import { useState } from "react";
import { usePathname, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { cn } from "@/lib/utils";
import { useCapabilities } from "@/lib/capabilities";
import { useOrgProfile } from "@/lib/org-profile";
import { canAccessNavItem, type NavGroup, type NavItem } from "@/lib/nav-items";

/**
 * The href of the single active item in a zone: the longest href among the visible items that
 * matches the location. An href's path matches the pathname (exact equality when `item.exact`,
 * else equality or a `/`-segment prefix); an href with a query string also needs every one of its
 * params to equal the current one. Longest-match keeps parent/child pairs in one zone unambiguous —
 * `/matters/new` highlights only "New matter", `/invoices/billing-runs/x` only "Fee runs",
 * `/settings/firm?tab=audit-trail` only "Audit trail" (not "Settings", `/settings/firm`) — so at
 * most one active indicator mounts per zone.
 */
function findActiveHref(
  items: NavItem[],
  slug: string,
  pathname: string,
  searchParams: URLSearchParams
): string | null {
  let active: string | null = null;
  for (const item of items) {
    const href = item.href(slug);
    const [path, query] = href.split("?");
    const pathMatches = pathname === path || (!item.exact && pathname.startsWith(`${path}/`));
    const queryMatches =
      !query ||
      [...new URLSearchParams(query)].every(([key, value]) => searchParams.get(key) === value);
    if (pathMatches && queryMatches && (active === null || href.length > active.length)) {
      active = href;
    }
  }
  return active;
}

export interface NavZoneProps {
  zone: NavGroup;
  slug: string;
  onNavItemClick?: () => void;
}

export function NavZone({ zone, slug, onNavItemClick }: NavZoneProps) {
  const [expanded, setExpanded] = useState(zone.defaultExpanded ?? true);
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const access = useCapabilities();
  const { isModuleEnabled } = useOrgProfile();

  const visibleItems = zone.items.filter(
    (item) =>
      canAccessNavItem(item, access) &&
      (!item.requiredModule || isModuleEnabled(item.requiredModule))
  );

  if (visibleItems.length === 0) return null;

  const activeHref = findActiveHref(visibleItems, slug, pathname, searchParams);

  return (
    <div>
      {/* Zone header */}
      <button
        type="button"
        aria-expanded={expanded}
        onClick={() => setExpanded((prev) => !prev)}
        className="flex w-full items-center justify-between px-3 py-1.5 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
      >
        <span className="text-[11px] font-medium tracking-widest text-white/40 uppercase">
          {zone.label}
        </span>
        <ChevronRight
          className={cn(
            "h-3 w-3 text-white/40 transition-transform duration-150",
            expanded && "rotate-90"
          )}
        />
      </button>

      {/* Zone items */}
      <AnimatePresence initial={false}>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.15, ease: "easeOut" }}
            className="overflow-hidden"
          >
            {visibleItems.map((item) => {
              const href = item.href(slug);
              const isActive = href === activeHref;

              return (
                <Link
                  key={href}
                  href={href}
                  onClick={onNavItemClick}
                  className={cn(
                    "relative flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white",
                    isActive
                      ? "bg-white/5 text-white"
                      : "text-white/60 hover:bg-slate-800 hover:text-white"
                  )}
                >
                  {isActive && (
                    <motion.div
                      layoutId={`sidebar-indicator-${zone.id}`}
                      aria-hidden="true"
                      className="absolute top-1 bottom-1 left-0 w-0.5 rounded-full"
                      style={{ backgroundColor: "var(--brand-color)" }}
                      transition={{ type: "spring", stiffness: 350, damping: 30 }}
                    />
                  )}
                  <item.icon className="h-4 w-4" />
                  {item.label}
                </Link>
              );
            })}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
