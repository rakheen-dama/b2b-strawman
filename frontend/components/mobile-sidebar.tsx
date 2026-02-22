"use client";

import { useState } from "react";
import { usePathname } from "next/navigation";
import Link from "next/link";
import { Menu } from "lucide-react";
import { motion } from "motion/react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { NAV_ITEMS } from "@/lib/nav-items";
import { SidebarUserFooter } from "@/components/sidebar-user-footer";

interface MobileSidebarProps {
  slug: string;
}

export function MobileSidebar({ slug }: MobileSidebarProps) {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="ghost" size="icon" className="md:hidden">
          <Menu className="h-5 w-5" />
          <span className="sr-only">Toggle menu</span>
        </Button>
      </SheetTrigger>
      <SheetContent
        side="left"
        className="w-60 gap-0 border-r-0 bg-slate-950 p-0 [&>button]:text-white/60 [&>button]:hover:text-white"
      >
        {/* Header */}
        <div className="flex h-14 items-center px-4">
          <SheetTitle className="text-base font-semibold text-white">
            DocTeams
          </SheetTitle>
          <SheetDescription className="sr-only">
            Main navigation menu
          </SheetDescription>
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
                onClick={() => setOpen(false)}
                className={cn(
                  "relative flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white",
                  isActive
                    ? "bg-white/5 text-white"
                    : "text-white/60 hover:bg-slate-800 hover:text-white"
                )}
              >
                {isActive && (
                  <motion.div
                    layoutId="mobile-sidebar-indicator"
                    aria-hidden="true"
                    className="absolute left-0 top-1 bottom-1 w-0.5 rounded-full bg-teal-500"
                    transition={{
                      type: "spring",
                      stiffness: 350,
                      damping: 30,
                    }}
                  />
                )}
                <item.icon className="h-4 w-4" />
                {item.label}
              </Link>
            );
          })}
        </nav>

        {/* Footer */}
        <SidebarUserFooter />
      </SheetContent>
    </Sheet>
  );
}
