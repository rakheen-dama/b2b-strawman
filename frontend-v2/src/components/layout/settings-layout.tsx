"use client";

import type { ReactNode } from "react";
import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChevronDown } from "lucide-react";

import { cn } from "@/lib/utils";

interface SettingsNavItem {
  label: string;
  href: string;
  icon?: ReactNode;
}

interface SettingsNavGroup {
  title: string;
  items: SettingsNavItem[];
}

interface SettingsLayoutProps {
  groups: SettingsNavGroup[];
  children: ReactNode;
  className?: string;
}

export function SettingsLayout({
  groups,
  children,
  className,
}: SettingsLayoutProps) {
  const pathname = usePathname();

  // Find the active item for the mobile dropdown label
  const activeItem = groups
    .flatMap((g) => g.items)
    .find((item) => pathname === item.href || pathname.startsWith(item.href + "/"));

  return (
    <div className={cn("flex gap-8", className)}>
      {/* Desktop sidebar */}
      <nav className="hidden w-56 shrink-0 lg:block" aria-label="Settings">
        <div className="flex flex-col gap-6">
          {groups.map((group) => (
            <div key={group.title} className="flex flex-col gap-1">
              <span className="px-3 text-xs font-medium uppercase tracking-wider text-slate-400">
                {group.title}
              </span>
              {group.items.map((item) => {
                const isActive =
                  pathname === item.href ||
                  pathname.startsWith(item.href + "/");
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={cn(
                      "flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                      isActive
                        ? "bg-slate-100 text-slate-900"
                        : "text-slate-600 hover:bg-slate-50 hover:text-slate-900"
                    )}
                  >
                    {item.icon && (
                      <span className="flex h-4 w-4 items-center justify-center text-slate-400">
                        {item.icon}
                      </span>
                    )}
                    {item.label}
                  </Link>
                );
              })}
            </div>
          ))}
        </div>
      </nav>

      {/* Mobile dropdown */}
      <MobileSettingsNav
        groups={groups}
        activeItem={activeItem}
        pathname={pathname}
      />

      {/* Content area */}
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}

function MobileSettingsNav({
  groups,
  activeItem,
  pathname,
}: {
  groups: SettingsNavGroup[];
  activeItem?: SettingsNavItem;
  pathname: string;
}) {
  const [open, setOpen] = useState(false);

  return (
    <div className="relative mb-4 lg:hidden">
      <button
        onClick={() => setOpen(!open)}
        className="flex w-full items-center justify-between rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 shadow-sm"
        aria-expanded={open}
      >
        <span>{activeItem?.label ?? "Settings"}</span>
        <ChevronDown
          className={cn(
            "h-4 w-4 text-slate-400 transition-transform",
            open && "rotate-180"
          )}
        />
      </button>
      {open && (
        <div className="absolute inset-x-0 top-full z-20 mt-1 rounded-md border border-slate-200 bg-white shadow-lg">
          {groups.map((group) => (
            <div key={group.title}>
              <span className="block px-3 pt-3 pb-1 text-xs font-medium uppercase tracking-wider text-slate-400">
                {group.title}
              </span>
              {group.items.map((item) => {
                const isActive =
                  pathname === item.href ||
                  pathname.startsWith(item.href + "/");
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={() => setOpen(false)}
                    className={cn(
                      "flex items-center gap-2 px-3 py-2 text-sm",
                      isActive
                        ? "bg-slate-50 font-medium text-slate-900"
                        : "text-slate-600 hover:bg-slate-50"
                    )}
                  >
                    {item.icon && (
                      <span className="flex h-4 w-4 items-center justify-center text-slate-400">
                        {item.icon}
                      </span>
                    )}
                    {item.label}
                  </Link>
                );
              })}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
