// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): default-layout/src/components/layout/Header.tsx.
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
"use client";

import { Search } from "lucide-react";
import { Separator } from "@b2mash/ui/separator";
import { AuthHeaderControls } from "@/components/auth-header-controls";
import { Breadcrumbs } from "@/components/breadcrumbs";
import { useCommandPalette } from "@/components/command-palette-provider";
import { NotificationBell } from "@/components/notifications/notification-bell";
import { SidebarTrigger } from "@/components/ui/sidebar";

interface AppHeaderProps {
  slug: string;
  orgName: string | null;
}

/**
 * The org shell's floating header (§15.3.1 step 8): sidebar trigger ("Toggle menu"), breadcrumbs,
 * the ⌘K palette trigger, the notification bell and the user menu (which hosts the theme toggle).
 * Sticky inside SidebarInset, above — never inside — <main id="main-content">.
 */
export function AppHeader({ slug, orgName }: AppHeaderProps) {
  const { setOpen } = useCommandPalette();

  return (
    // mt-2 matches top-2 so the sticky bar does not overlap content at scroll 0; z-30 keeps the
    // hand-rolled popovers (bell, user menus) above page content; no overflow-hidden (it would clip them).
    <header className="bg-card/90 sticky top-2 z-30 mx-2 mt-2 flex h-(--app-header-height) items-center gap-2 rounded-xl border px-2 shadow-(--shadow-overlay) backdrop-blur-md md:mx-4 md:gap-3 md:px-3">
      <SidebarTrigger />
      <Separator
        orientation="vertical"
        className="hidden data-[orientation=vertical]:h-4 sm:block"
      />
      <Breadcrumbs slug={slug} orgName={orgName} />
      <div className="ml-auto flex shrink-0 items-center gap-2">
        {/* Named "Search, Command K" like the sidebar pill — never exactly "Search": two specs
            click the client-ledgers page's own "Search" button (day-07-14:226, day-30:240). */}
        <button
          type="button"
          aria-label="Search, Command K"
          aria-keyshortcuts="Meta+K Control+K"
          onClick={() => setOpen(true)}
          className="border-border bg-background/60 text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:ring-ring/50 inline-flex h-8 items-center gap-2 rounded-md border px-2 text-sm transition-colors outline-none focus-visible:ring-[3px] sm:px-3"
        >
          <Search aria-hidden="true" className="size-4" />
          <span className="hidden sm:inline">Search</span>
          <kbd
            aria-hidden="true"
            className="bg-muted text-muted-foreground pointer-events-none hidden h-5 items-center rounded px-1.5 font-mono text-[10px] font-medium md:inline-flex"
          >
            ⌘K
          </kbd>
        </button>
        <NotificationBell orgSlug={slug} />
        <AuthHeaderControls />
      </div>
    </header>
  );
}
