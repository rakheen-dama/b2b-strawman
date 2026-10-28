"use client";

import { Fragment, useEffect } from "react";
import { usePathname } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import { Shield, Search } from "lucide-react";
import { cn } from "@/lib/utils";
import { NAV_GROUPS } from "@/lib/nav-items";
import { NavZone } from "@/components/nav-zone";
import { SidebarUtilityFooter } from "@/components/sidebar-utility-footer";
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
    <aside className="sticky top-0 hidden h-screen w-60 flex-col bg-slate-950 md:flex">
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
      {/* The brand colour is an accent bar, never the name's text colour: a dark brand colour
          (#1B3A4B) on the slate-950 rail was illegible (G-A3, Phase 88 628B). */}
      <div className="flex items-center gap-2 px-4 py-3">
        <span
          aria-hidden="true"
          data-testid="org-brand-accent"
          className="h-4 w-[3px] shrink-0 rounded-full"
          style={{ backgroundColor: "var(--brand-color)" }}
        />
        <span className="truncate text-xs font-medium text-white/80">{orgName ?? slug}</span>
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
      <nav
        aria-label="Main navigation"
        className="flex min-h-0 flex-1 flex-col gap-0 overflow-y-auto p-2"
      >
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

      <SidebarUtilityFooter slug={slug} indicatorLayoutId="sidebar-indicator" />

      {/* Footer */}
      <SidebarUserFooter userName={userName} userEmail={userEmail} />
    </aside>
  );
}
