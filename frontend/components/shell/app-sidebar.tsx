// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): default-layout/src/components/layout/Sidebar.tsx.
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { ChevronRight, Search, Shield } from "lucide-react";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarSeparator,
  useSidebar,
} from "@/components/ui/sidebar";
import { useCommandPalette } from "@/components/command-palette-provider";
import { SIDEBAR_ACTIVE_BAR } from "@/components/shell/sidebar-styles";
import { SidebarUtilityFooter } from "@/components/sidebar-utility-footer";
import { SidebarUserFooter } from "@/components/sidebar-user-footer";
import { useCapabilities } from "@/lib/capabilities";
import { useOrgProfile } from "@/lib/org-profile";
import { isNavEntryVisible } from "@/lib/nav-filter";
import { NAV_GROUPS, type NavGroup, type NavItem } from "@/lib/nav-items";

/**
 * The href of the single active item in a zone: the longest href among the visible items that
 * matches the location. An href's path matches the pathname (exact equality when `item.exact`,
 * else equality or a `/`-segment prefix); an href with a query string also needs every one of its
 * params to equal the current one. Longest-match keeps parent/child pairs in one zone unambiguous —
 * `/matters/new` highlights only "New matter", `/invoices/billing-runs/x` only "Fee runs",
 * `/settings/firm?tab=audit-trail` only "Audit trail" (not "Settings", `/settings/firm`) — so at
 * most one item per zone is active.
 */
function findActiveHref(
  items: NavItem[],
  slug: string,
  pathname: string,
  searchParams: URLSearchParams
): string | null {
  let active: string | null = null;
  for (const item of items) {
    const href = item.href(slug);
    const [path, query] = href.split("?");
    const pathMatches = pathname === path || (!item.exact && pathname.startsWith(`${path}/`));
    const queryMatches =
      !query ||
      [...new URLSearchParams(query)].every(([key, value]) => searchParams.get(key) === value);
    if (pathMatches && queryMatches && (active === null || href.length > active.length)) {
      active = href;
    }
  }
  return active;
}

interface AppSidebarZoneProps {
  zone: NavGroup;
  slug: string;
  /** Renders a separator above the zone (every zone but the first). */
  separated: boolean;
  onNavigate: () => void;
}

function AppSidebarZone({ zone, slug, separated, onNavigate }: AppSidebarZoneProps) {
  // Same default as the old NavZone; not persisted (persisting would be new behaviour, §15.3.1).
  const [expanded, setExpanded] = useState(zone.defaultExpanded ?? true);
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { hasCapability, isAdmin, isOwner } = useCapabilities();
  const { isModuleEnabled } = useOrgProfile();
  const { state, isMobile } = useSidebar();

  // One access rule for every nav surface (lib/nav-filter.ts — shared with the palette and the
  // settings rail).
  const visibleItems = zone.items.filter((item) =>
    isNavEntryVisible(item, { hasCapability, isAdmin, isOwner, isModuleEnabled })
  );
  if (visibleItems.length === 0) return null;

  const activeHref = findActiveHref(visibleItems, slug, pathname, searchParams);
  // Collapsed icon rail: every zone shows its items and the zone headers give way to the
  // separators, so a closed zone's items stay reachable. Expanded-mode state is untouched.
  const iconRail = state === "collapsed" && !isMobile;

  return (
    <>
      {separated && <SidebarSeparator className="my-1" />}
      <Collapsible open={expanded || iconRail} onOpenChange={setExpanded} className="group/zone">
        <SidebarGroup className="py-1">
          <SidebarGroupLabel
            asChild
            className="text-sidebar-foreground/50 hover:text-sidebar-foreground w-full justify-between text-[11px] tracking-widest uppercase group-data-[collapsible=icon]:hidden"
          >
            <CollapsibleTrigger>
              {zone.label}
              <ChevronRight
                aria-hidden="true"
                className="transition-transform duration-150 group-data-[state=open]/zone:rotate-90 motion-reduce:transition-none"
              />
            </CollapsibleTrigger>
          </SidebarGroupLabel>
          <CollapsibleContent>
            <SidebarGroupContent>
              <SidebarMenu>
                {visibleItems.map((item) => {
                  const href = item.href(slug);
                  const isActive = href === activeHref;
                  return (
                    <SidebarMenuItem key={href}>
                      <SidebarMenuButton
                        asChild
                        isActive={isActive}
                        tooltip={item.label}
                        className={SIDEBAR_ACTIVE_BAR}
                      >
                        <Link
                          href={href}
                          aria-current={isActive ? "page" : undefined}
                          onClick={onNavigate}
                        >
                          <item.icon />
                          <span className="group-data-[collapsible=icon]:sr-only">
                            {item.label}
                          </span>
                        </Link>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  );
                })}
              </SidebarMenu>
            </SidebarGroupContent>
          </CollapsibleContent>
        </SidebarGroup>
      </Collapsible>
    </>
  );
}

interface AppSidebarProps {
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

/**
 * The org shell's sidebar (Phase 90 E2A, §15.3.1): a shadcn `Sidebar` with `collapsible="icon"`
 * on desktop and a Sheet below 768px. Only the six NAV_GROUPS zones live inside the
 * "Main navigation" nav; the search pill, Platform Admin, the utility links and the user footer sit
 * outside it (sidebar-navigation.spec.ts asserts the nav's exact ordered link list).
 */
export function AppSidebar({
  slug,
  orgName,
  groups = [],
  userName,
  userEmail,
  brandColor,
  logoUrl,
}: AppSidebarProps) {
  const pathname = usePathname();
  const { setOpen } = useCommandPalette();
  const { isMobile, setOpenMobile } = useSidebar();
  // The old mobile sheet closed itself on every nav click; shadcn's Sheet does not.
  const closeMobile = () => {
    if (isMobile) setOpenMobile(false);
  };
  const platformAdminActive = pathname.startsWith("/platform-admin");

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
    <Sidebar
      collapsible="icon"
      className="border-sidebar-border"
      mobileTitle="Kazi"
      mobileDescription="Main navigation menu"
    >
      <SidebarHeader className="gap-0 p-0">
        {/* Firm logo (if branded) or product wordmark fallback */}
        <div className="flex h-14 items-center gap-2 px-4 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:px-0">
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
          <span className="text-sidebar-foreground text-base font-bold tracking-tight group-data-[collapsible=icon]:hidden">
            Kazi
          </span>
        </div>
        <SidebarSeparator className="mx-4 group-data-[collapsible=icon]:hidden" />
        {/* The brand colour is an accent bar, never the name's text colour: a dark brand colour
            (#1B3A4B) on the slate-950 rail was illegible (G-A3, Phase 88 628B). */}
        <div className="flex items-center gap-2 px-4 py-3 group-data-[collapsible=icon]:hidden">
          <span
            aria-hidden="true"
            data-testid="org-brand-accent"
            className="h-4 w-[3px] shrink-0 rounded-full"
            style={{ backgroundColor: "var(--brand-color)" }}
          />
          <span className="text-sidebar-foreground/80 truncate text-xs font-medium">
            {orgName ?? slug}
          </span>
        </div>
        <SidebarSeparator className="mx-4" />
        {/* Search pill — ⌘K trigger. Its name stays "Search, Command K": two specs click a
            page-wide button named exactly "Search" (client ledgers). */}
        <SidebarMenu className="p-2">
          <SidebarMenuItem>
            <SidebarMenuButton
              aria-label="Search, Command K"
              tooltip="Search..."
              onClick={() => setOpen(true)}
              className="border-sidebar-border bg-sidebar-accent text-sidebar-foreground/60 hover:text-sidebar-foreground border text-xs"
            >
              <Search />
              <span className="group-data-[collapsible=icon]:hidden">Search...</span>
              <kbd className="bg-sidebar-accent ml-auto rounded px-1 py-0.5 font-mono text-[10px] group-data-[collapsible=icon]:hidden">
                ⌘K
              </kbd>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      {/* The icon rail shows every zone's items, so it scrolls instead of clipping (the registry
          default is overflow-hidden in icon mode). */}
      <SidebarContent className="group-data-[collapsible=icon]:overflow-auto">
        {/* Only the six zones live in this nav (sidebar-navigation.spec.ts asserts the exact
            ordered link list). */}
        <nav aria-label="Main navigation" className="flex flex-col">
          {NAV_GROUPS.map((group, index) => (
            <AppSidebarZone
              key={group.id}
              zone={group}
              slug={slug}
              separated={index > 0}
              onNavigate={closeMobile}
            />
          ))}
        </nav>
      </SidebarContent>

      <SidebarFooter className="gap-0 p-0">
        {groups.includes("platform-admins") && (
          <>
            <SidebarSeparator className="mx-4" />
            <SidebarMenu className="p-2">
              <SidebarMenuItem>
                <SidebarMenuButton
                  asChild
                  isActive={platformAdminActive}
                  tooltip="Platform Admin"
                  className={SIDEBAR_ACTIVE_BAR}
                >
                  <Link
                    href="/platform-admin/access-requests"
                    aria-current={platformAdminActive ? "page" : undefined}
                    onClick={closeMobile}
                  >
                    <Shield />
                    <span className="group-data-[collapsible=icon]:sr-only">Platform Admin</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </>
        )}
        <SidebarUtilityFooter slug={slug} onNavItemClick={closeMobile} />
        <SidebarUserFooter userName={userName} userEmail={userEmail} />
      </SidebarFooter>
    </Sidebar>
  );
}
