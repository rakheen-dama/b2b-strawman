"use client";

import { useState } from "react";
import { usePathname } from "next/navigation";
import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { cn } from "@/lib/utils";
import { useCapabilities } from "@/lib/capabilities";
import type { NavGroup } from "@/lib/nav-items";

export interface NavZoneProps {
  zone: NavGroup;
  slug: string;
  defaultExpanded?: boolean;
}

export function NavZone({ zone, slug, defaultExpanded }: NavZoneProps) {
  const [expanded, setExpanded] = useState(zone.defaultExpanded ?? defaultExpanded ?? true);
  const pathname = usePathname();
  const { hasCapability } = useCapabilities();

  const visibleItems = zone.items.filter(
    (item) => !item.requiredCapability || hasCapability(item.requiredCapability),
  );

  if (visibleItems.length === 0) return null;

  return (
    <div>
      {/* Zone header */}
      <button
        type="button"
        onClick={() => setExpanded((prev) => !prev)}
        className="flex w-full items-center justify-between px-3 py-1.5 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
      >
        <span className="text-[11px] font-medium tracking-widest text-white/40 uppercase">
          {zone.label}
        </span>
        <ChevronRight
          className={cn(
            "h-3 w-3 text-white/40 transition-transform duration-150",
            expanded && "rotate-90",
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
              const isActive = item.exact
                ? pathname === href
                : pathname.startsWith(href);

              return (
                <Link
                  key={item.label}
                  href={href}
                  className={cn(
                    "relative flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white",
                    isActive
                      ? "bg-white/5 text-white"
                      : "text-white/60 hover:bg-slate-800 hover:text-white",
                  )}
                >
                  {isActive && (
                    <motion.div
                      layoutId="sidebar-indicator"
                      aria-hidden="true"
                      className="absolute left-0 top-1 bottom-1 w-0.5 rounded-full bg-teal-500"
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
