"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion } from "motion/react";
import { cn } from "@/lib/utils";
import { UTILITY_ITEMS } from "@/lib/nav-items";

interface SidebarUtilityFooterProps {
  slug: string;
  /** Motion layoutId for the active indicator — distinct per sidebar instance (desktop vs mobile). */
  indicatorLayoutId: string;
  /** Called when an internal utility link is clicked (the mobile sheet closes itself). */
  onNavItemClick?: () => void;
}

/** Utility footer (Notifications) shared by the desktop sidebar and the mobile sheet (ADR-339). */
export function SidebarUtilityFooter({
  slug,
  indicatorLayoutId,
  onNavItemClick,
}: SidebarUtilityFooterProps) {
  const pathname = usePathname();

  return (
    <>
      <div className="mx-4 border-t border-white/10" />
      <div className="p-2">
        {UTILITY_ITEMS.map((item) => {
          const href = item.href(slug);

          if (item.external) {
            return (
              <a
                key={item.label}
                href={href}
                target="_blank"
                rel="noopener noreferrer"
                className="relative flex items-center gap-2 rounded-md px-3 py-2 text-sm text-white/60 transition-colors hover:bg-slate-800 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
              >
                <item.icon className="h-4 w-4" />
                {item.label}
              </a>
            );
          }

          const isActive = item.exact ? pathname === href : pathname.startsWith(href);

          return (
            <Link
              key={item.label}
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
                  layoutId={indicatorLayoutId}
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
      </div>
    </>
  );
}
