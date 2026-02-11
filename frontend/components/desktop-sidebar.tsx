"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { useUser } from "@clerk/nextjs";
import { motion } from "motion/react";
import { cn } from "@/lib/utils";
import { NAV_ITEMS } from "@/lib/nav-items";

interface DesktopSidebarProps {
  slug: string;
}

export function DesktopSidebar({ slug }: DesktopSidebarProps) {
  const pathname = usePathname();
  const { user } = useUser();

  const initials = user
    ? `${user.firstName?.charAt(0) ?? ""}${user.lastName?.charAt(0) ?? ""}`.toUpperCase() ||
      user.primaryEmailAddress?.emailAddress?.charAt(0).toUpperCase() ||
      "?"
    : "?";

  return (
    <aside className="hidden w-60 flex-col bg-olive-950 md:flex">
      {/* Header */}
      <div className="flex h-14 items-center px-4">
        <span className="text-base font-semibold text-white">DocTeams</span>
      </div>
      <div className="mx-4 border-t border-white/10" />
      <div className="flex items-center gap-2 px-4 py-3">
        <span className="truncate text-sm text-white/60">{slug}</span>
      </div>
      <div className="mx-4 border-t border-white/10" />

      {/* Nav body */}
      <nav aria-label="Main navigation" className="flex flex-1 flex-col gap-1 p-2">
        {NAV_ITEMS.map((item) => {
          const href = item.href(slug);
          const isActive = item.exact
            ? pathname === href
            : pathname.startsWith(href);

          return (
            <Link
              key={item.label}
              href={href}
              className={cn(
                "relative flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors",
                isActive
                  ? "bg-white/5 text-white"
                  : "text-white/60 hover:bg-olive-800 hover:text-white"
              )}
            >
              {isActive && (
                <motion.div
                  layoutId="sidebar-indicator"
                  aria-hidden="true"
                  className="absolute left-0 top-1 bottom-1 w-0.5 rounded-full bg-indigo-500"
                  transition={{ type: "spring", stiffness: 350, damping: 30 }}
                />
              )}
              <item.icon className="h-4 w-4" />
              {item.label}
            </Link>
          );
        })}
      </nav>

      {/* Footer */}
      <div className="mx-4 border-t border-white/10" />
      <div className="flex items-center gap-3 px-4 py-3">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-olive-800 text-xs font-medium text-white">
          {initials}
        </div>
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-white">
            {user?.fullName ?? "User"}
          </p>
          <p className="truncate text-xs text-white/60">
            {user?.primaryEmailAddress?.emailAddress ?? ""}
          </p>
        </div>
      </div>
    </aside>
  );
}
