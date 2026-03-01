"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { FileCheck, FileText, FolderOpen, LogOut } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

const portalNav = [
  { label: "Projects", href: "/portal/projects", icon: FolderOpen },
  { label: "Documents", href: "/portal/documents", icon: FileText },
  { label: "Proposals", href: "/portal/proposals", icon: FileCheck },
];

interface PortalHeaderProps {
  customerName?: string;
  onSignOut?: () => void;
}

export function PortalHeader({ customerName, onSignOut }: PortalHeaderProps) {
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/95 backdrop-blur supports-[backdrop-filter]:bg-white/80 dark:border-slate-800 dark:bg-slate-950/95">
      <div className="mx-auto flex h-14 max-w-5xl items-center gap-6 px-4 sm:px-6">
        {/* Logo / Brand */}
        <Link
          href="/portal/projects"
          className="flex items-center gap-2 font-display text-lg font-semibold tracking-tight text-slate-900 dark:text-slate-100"
        >
          <div className="flex h-7 w-7 items-center justify-center rounded-md bg-teal-600 text-xs font-bold text-white">
            D
          </div>
          <span className="hidden sm:inline">Client Portal</span>
        </Link>

        {/* Nav links */}
        <nav className="flex items-center gap-1">
          {portalNav.map((item) => {
            const isActive = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-slate-100 text-slate-900 dark:bg-slate-800 dark:text-slate-100"
                    : "text-slate-600 hover:bg-slate-50 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800/50 dark:hover:text-slate-100"
                )}
              >
                <item.icon className="size-4" />
                {item.label}
              </Link>
            );
          })}
        </nav>

        {/* Spacer */}
        <div className="flex-1" />

        {/* Customer name + sign out */}
        <div className="flex items-center gap-3">
          {customerName && (
            <span className="hidden text-sm text-slate-500 dark:text-slate-400 sm:inline">
              {customerName}
            </span>
          )}
          {onSignOut && (
            <Button
              variant="ghost"
              size="sm"
              onClick={onSignOut}
              className="gap-1.5 text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
            >
              <LogOut className="size-3.5" />
              <span className="hidden sm:inline">Sign Out</span>
            </Button>
          )}
        </div>
      </div>
    </header>
  );
}
