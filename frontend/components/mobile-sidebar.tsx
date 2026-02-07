"use client";

import { useState } from "react";
import { usePathname } from "next/navigation";
import Link from "next/link";
import { Menu } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { NAV_ITEMS } from "@/lib/nav-items";

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
      <SheetContent side="left" className="w-60 gap-0 p-0">
        <div className="flex h-14 items-center px-4">
          <SheetTitle className="font-semibold">DocTeams</SheetTitle>
          <SheetDescription className="sr-only">Main navigation menu</SheetDescription>
        </div>
        <Separator />
        <nav className="flex flex-col gap-1 p-2">
          {NAV_ITEMS.map((item) => {
            const href = item.href(slug);
            const isActive = item.exact ? pathname === href : pathname.startsWith(href);

            return (
              <Link
                key={item.label}
                href={href}
                onClick={() => setOpen(false)}
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
      </SheetContent>
    </Sheet>
  );
}
