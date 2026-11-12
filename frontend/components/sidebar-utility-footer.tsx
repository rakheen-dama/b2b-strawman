"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarSeparator,
} from "@/components/ui/sidebar";
import { SIDEBAR_ACTIVE_BAR } from "@/components/shell/sidebar-styles";
import { UTILITY_ITEMS } from "@/lib/nav-items";

interface SidebarUtilityFooterProps {
  slug: string;
  /** Called when an internal utility link is clicked (the mobile sheet closes itself). */
  onNavItemClick?: () => void;
}

/**
 * Utility footer (Notifications) inside AppSidebar's SidebarFooter, outside the Main navigation
 * nav (ADR-339). Needs a SidebarProvider ancestor (SidebarMenuButton reads useSidebar()).
 */
export function SidebarUtilityFooter({ slug, onNavItemClick }: SidebarUtilityFooterProps) {
  const pathname = usePathname();

  return (
    <>
      <SidebarSeparator className="mx-4" />
      <SidebarMenu className="p-2">
        {UTILITY_ITEMS.map((item) => {
          const href = item.href(slug);
          const isActive =
            !item.external && (item.exact ? pathname === href : pathname.startsWith(href));

          return (
            <SidebarMenuItem key={item.label}>
              <SidebarMenuButton
                asChild
                isActive={isActive}
                tooltip={item.label}
                className={SIDEBAR_ACTIVE_BAR}
              >
                {item.external ? (
                  <a href={href} target="_blank" rel="noopener noreferrer">
                    <item.icon />
                    <span className="group-data-[collapsible=icon]:sr-only">{item.label}</span>
                  </a>
                ) : (
                  <Link
                    href={href}
                    aria-current={isActive ? "page" : undefined}
                    onClick={onNavItemClick}
                  >
                    <item.icon />
                    <span className="group-data-[collapsible=icon]:sr-only">{item.label}</span>
                  </Link>
                )}
              </SidebarMenuButton>
            </SidebarMenuItem>
          );
        })}
      </SidebarMenu>
    </>
  );
}
