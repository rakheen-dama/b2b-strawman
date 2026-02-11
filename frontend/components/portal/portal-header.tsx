"use client";

import { useRouter, usePathname } from "next/navigation";
import { LogOut, FolderOpen, FileText } from "lucide-react";
import { Button } from "@/components/ui/button";
import { clearPortalAuth, getPortalCustomerName } from "@/lib/portal-api";
import { cn } from "@/lib/utils";
import Link from "next/link";

const NAV_ITEMS = [
  { label: "Projects", href: "/portal/projects", icon: FolderOpen },
  { label: "Documents", href: "/portal/documents", icon: FileText },
];

export function PortalHeader() {
  const router = useRouter();
  const pathname = usePathname();
  const customerName = getPortalCustomerName();

  const handleLogout = () => {
    clearPortalAuth();
    router.push("/portal");
  };

  return (
    <header className="border-b border-olive-200 bg-white dark:border-olive-800 dark:bg-olive-950">
      <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4 sm:px-6">
        {/* Left: Logo + nav */}
        <div className="flex items-center gap-6">
          <Link
            href="/portal/projects"
            className="font-display text-lg text-olive-950 dark:text-olive-50"
          >
            DocTeams Portal
          </Link>

          <nav className="hidden items-center gap-1 sm:flex">
            {NAV_ITEMS.map((item) => {
              const isActive =
                pathname === item.href || pathname.startsWith(item.href + "/");
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={cn(
                    "inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                    isActive
                      ? "bg-olive-100 text-olive-900 dark:bg-olive-800 dark:text-olive-100"
                      : "text-olive-600 hover:bg-olive-50 hover:text-olive-900 dark:text-olive-400 dark:hover:bg-olive-900 dark:hover:text-olive-100",
                  )}
                >
                  <item.icon className="size-4" />
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </div>

        {/* Right: Customer name + logout */}
        <div className="flex items-center gap-3">
          {customerName && (
            <span className="hidden text-sm text-olive-600 sm:block dark:text-olive-400">
              {customerName}
            </span>
          )}
          <Button variant="ghost" size="sm" onClick={handleLogout}>
            <LogOut className="size-4" />
            <span className="hidden sm:inline">Sign out</span>
          </Button>
        </div>
      </div>
    </header>
  );
}
