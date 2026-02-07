"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { cn } from "@/lib/utils";
import { Separator } from "@/components/ui/separator";
import { NAV_ITEMS } from "@/lib/nav-items";

interface DesktopSidebarProps {
  slug: string;
}

export function DesktopSidebar({ slug }: DesktopSidebarProps) {
  const pathname = usePathname();

  return (
    <aside className="bg-sidebar hidden w-60 flex-col border-r md:flex">
      <div className="text-sidebar-foreground flex h-14 items-center px-4 font-semibold">
        DocTeams
      </div>
      <Separator />
      <nav className="flex flex-1 flex-col gap-1 p-2">
        {NAV_ITEMS.map((item) => {
          const href = item.href(slug);
          const isActive = item.exact ? pathname === href : pathname.startsWith(href);

          return (
            <Link
              key={item.label}
              href={href}
              className={cn(
                "flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors",
                isActive
                  ? "bg-sidebar-accent text-sidebar-accent-foreground font-medium"
                  : "text-sidebar-foreground/70 hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground"
              )}
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
