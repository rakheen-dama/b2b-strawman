"use client";

import { Fragment, useEffect } from "react";
import { usePathname } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import { Shield, Search } from "lucide-react";
import { motion } from "motion/react";
import { cn } from "@/lib/utils";
import { NAV_GROUPS, UTILITY_ITEMS } from "@/lib/nav-items";
import { NavZone } from "@/components/nav-zone";
import { SidebarUserFooter } from "@/components/sidebar-user-footer";
import { useCommandPalette } from "@/components/command-palette-provider";

interface DesktopSidebarProps {
  slug: string;
  orgName?: string | null;
  groups?: string[];
  userName?: string | null;
  userEmail?: string | null;
  /** Firm brand colour (hex) applied as the `--brand-color` CSS custom property. */
  brandColor?: string | null;
  /** Presigned URL for the firm logo, rendered in the sidebar header when present. */
  logoUrl?: string | null;
}

export function DesktopSidebar({
  slug,
  orgName,
  groups = [],
  userName,
  userEmail,
  brandColor,
  logoUrl,
}: DesktopSidebarProps) {
  const pathname = usePathname();
  const { setOpen } = useCommandPalette();

  // Inject brand colour as a CSS custom property on the root element so any
  // child component can reference `var(--brand-color)`. Cleans up on unmount
  // so switching orgs doesn't leak colour into the next render. (GAP-L-26)
  useEffect(() => {
    if (!brandColor) return;
    const root = document.documentElement;
    const previous = root.style.getPropertyValue("--brand-color");
    root.style.setProperty("--brand-color", brandColor);
    return () => {
      if (previous) {
        root.style.setProperty("--brand-color", previous);
      } else {
        root.style.removeProperty("--brand-color");
      }
    };
  }, [brandColor]);

  return (
    <aside className="hidden w-60 flex-col bg-slate-950 md:flex">
      {/* Header — firm logo (if branded) or product wordmark fallback */}
      <div className="flex h-14 items-center gap-2 px-4">
        {logoUrl ? (
          <Image
            src={logoUrl}
            alt={orgName ? `${orgName} logo` : "Firm logo"}
            width={32}
            height={32}
            unoptimized
            className="size-8 rounded object-contain"
          />
        ) : null}
        <span className="text-base font-bold tracking-tight text-white">Kazi</span>
      </div>
      <div className="mx-4 border-t border-white/10" />
      <div className="flex items-center gap-2 px-4 py-3">
        <span className="truncate text-xs font-medium text-teal-500/80">{orgName ?? slug}</span>
      </div>
      <div className="mx-4 border-t border-white/10" />

      {/* Search pill — ⌘K trigger */}
      <button
        type="button"
        aria-label="Search, Command K"
        onClick={() => setOpen(true)}
        className="mx-4 mb-2 flex w-[calc(100%-2rem)] items-center gap-2 rounded-md border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-white/40 transition-colors hover:bg-white/10 hover:text-white/60"
      >
        <Search className="h-3.5 w-3.5" />
        Search...
        <kbd className="ml-auto rounded bg-white/10 px-1 py-0.5 font-mono text-[10px]">⌘K</kbd>
      </button>

      {/* Nav body — zone-based */}
      <nav aria-label="Main navigation" className="flex flex-1 flex-col gap-0 p-2">
        {NAV_GROUPS.map((group, index) => (
          <Fragment key={group.id}>
            {index > 0 && <div className="mx-2 my-1 border-t border-white/5" />}
            <NavZone zone={group} slug={slug} />
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
              className={cn(
                "relative flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white",
                isActive
                  ? "bg-white/5 text-white"
                  : "text-white/60 hover:bg-slate-800 hover:text-white"
              )}
            >
              {isActive && (
                <motion.div
                  layoutId="sidebar-indicator"
                  aria-hidden="true"
                  className="absolute top-1 bottom-1 left-0 w-0.5 rounded-full bg-teal-500"
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
    </aside>
  );
}
