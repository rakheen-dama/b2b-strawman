"use client";

import { Fragment, useState } from "react";
import { usePathname } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import { Menu, Shield } from "lucide-react";
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
import { NAV_GROUPS, UTILITY_ITEMS } from "@/lib/nav-items";
import { NavZone } from "@/components/nav-zone";
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

        {/* Utility footer — Notifications + Settings + Help */}
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

        {/* Footer */}
        <SidebarUserFooter userName={userName} userEmail={userEmail} />
      </SheetContent>
    </Sheet>
  );
}
