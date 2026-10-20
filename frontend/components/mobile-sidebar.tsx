"use client";

import { Fragment, useState } from "react";
import { usePathname } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import { Menu, Shield } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@b2mash/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { NAV_GROUPS } from "@/lib/nav-items";
import { NavZone } from "@/components/nav-zone";
import { SidebarUtilityFooter } from "@/components/sidebar-utility-footer";
import { SidebarUserFooter } from "@/components/sidebar-user-footer";

interface MobileSidebarProps {
  slug: string;
  orgName?: string | null;
  groups?: string[];
  userName?: string | null;
  userEmail?: string | null;
  brandColor?: string | null;
  logoUrl?: string | null;
}

export function MobileSidebar({
  slug,
  orgName,
  groups = [],
  userName,
  userEmail,
  logoUrl,
}: MobileSidebarProps) {
  // brandColor is injected by DesktopSidebar on document.documentElement; no
  // per-instance CSS variable work needed here (we share the root var).
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
        {/* Header — firm logo (if branded) or product wordmark fallback */}
        <div className="flex h-14 items-center gap-2 px-4">
          {logoUrl ? (
            <Image
              src={logoUrl}
              alt={orgName ? `${orgName} logo` : "Firm logo"}
              width={28}
              height={28}
              unoptimized
              className="size-7 rounded object-contain"
            />
          ) : null}
          <SheetTitle className="text-base font-semibold text-white">Kazi</SheetTitle>
          <SheetDescription className="sr-only">Main navigation menu</SheetDescription>
        </div>
        <div className="mx-4 border-t border-white/10" />
        <div className="flex items-center gap-2 px-4 py-3">
          <span className="truncate text-sm text-white/60">{orgName ?? slug}</span>
        </div>
        <div className="mx-4 border-t border-white/10" />

        {/* Nav body — zone-based */}
        <nav aria-label="Main navigation" className="flex flex-1 flex-col gap-0 p-2">
          {NAV_GROUPS.map((group, index) => (
            <Fragment key={group.id}>
              {index > 0 && <div className="mx-2 my-1 border-t border-white/5" />}
              <NavZone zone={group} slug={slug} onNavItemClick={() => setOpen(false)} />
            </Fragment>
          ))}
        </nav>

        {/* Platform Admin */}
        {groups.includes("platform-admins") && (
          <>
            <div className="mx-4 border-t border-white/10" />
            <div className="p-2">
              <Link
                href="/platform-admin/access-requests"
                onClick={() => setOpen(false)}
                className={cn(
                  "relative flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white",
                  pathname.startsWith("/platform-admin")
                    ? "bg-white/5 text-white"
                    : "text-white/60 hover:bg-slate-800 hover:text-white"
                )}
              >
                <Shield className="h-4 w-4" />
                Platform Admin
              </Link>
            </div>
          </>
        )}

        <SidebarUtilityFooter
          slug={slug}
          indicatorLayoutId="mobile-sidebar-indicator"
          onNavItemClick={() => setOpen(false)}
        />

        {/* Footer */}
        <SidebarUserFooter userName={userName} userEmail={userEmail} />
      </SheetContent>
    </Sheet>
  );
}
