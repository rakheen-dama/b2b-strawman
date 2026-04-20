"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { User, LogOut } from "lucide-react";
import { Dialog } from "radix-ui";
import { cn } from "@/lib/utils";
import { useAuth } from "@/hooks/use-auth";
import {
  usePortalContext,
  useBranding,
} from "@/hooks/use-portal-context";
import {
  PORTAL_NAV_ITEMS,
  PORTAL_NAV_LABELS,
  filterNavItems,
} from "@/lib/nav-items";

function isItemActive(pathname: string, href: string): boolean {
  if (href === "/home") {
    return pathname === "/home";
  }
  return pathname === href || pathname.startsWith(href + "/");
}

function NavList({ onItemClick }: { onItemClick?: () => void }) {
  const pathname = usePathname();
  const { brandColor } = useBranding();
  const ctx = usePortalContext();
  const items = filterNavItems(PORTAL_NAV_ITEMS, {
    tenantProfile: ctx?.tenantProfile ?? undefined,
    enabledModules: ctx?.enabledModules ?? [],
  });

  return (
    <nav
      aria-label="Portal navigation"
      className="flex flex-1 flex-col gap-0.5 p-2"
    >
      {items.map((item) => {
        const Icon = item.icon;
        const isActive = isItemActive(pathname ?? "", item.href);
        return (
          <Link
            key={item.id}
            href={item.href}
            onClick={onItemClick}
            data-active={isActive ? "true" : undefined}
            aria-current={isActive ? "page" : undefined}
            className={cn(
              "relative flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors",
              "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-400",
              isActive
                ? "bg-slate-100 text-slate-900"
                : "text-slate-600 hover:bg-slate-50 hover:text-slate-900",
            )}
          >
            {isActive && (
              <span
                aria-hidden="true"
                data-testid="nav-active-indicator"
                className="absolute inset-y-1 left-0 w-0.5 rounded-full"
                style={{ backgroundColor: brandColor ?? "#0f172a" }}
              />
            )}
            <Icon className="size-4 shrink-0" />
            <span className="truncate">
              {PORTAL_NAV_LABELS[item.labelKey] ?? item.labelKey}
            </span>
          </Link>
        );
      })}
    </nav>
  );
}

function SidebarFooter({ onNavigate }: { onNavigate?: () => void }) {
  const { logout } = useAuth();
  return (
    <div className="border-t border-slate-200 p-2">
      <Link
        href="/profile"
        onClick={onNavigate}
        className="flex items-center gap-3 rounded-md px-3 py-2 text-sm text-slate-600 hover:bg-slate-50 hover:text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-400"
      >
        <User className="size-4" />
        Profile
      </Link>
      <button
        type="button"
        onClick={() => {
          onNavigate?.();
          logout();
        }}
        className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm text-slate-600 hover:bg-slate-50 hover:text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-400"
      >
        <LogOut className="size-4" />
        Logout
      </button>
    </div>
  );
}

export function PortalSidebar() {
  return (
    <aside
      aria-label="Portal sidebar"
      className="sticky top-0 hidden h-screen w-60 flex-col border-r border-slate-200 bg-white md:flex"
    >
      <div className="flex h-12 items-center px-4 font-display text-sm font-semibold text-slate-900">
        Portal
      </div>
      <NavList />
      <SidebarFooter />
    </aside>
  );
}

export function PortalSidebarMobile({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (next: boolean) => void;
}) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-slate-950/25 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=open]:fade-in data-[state=closed]:fade-out" />
        <Dialog.Content
          aria-label="Portal navigation"
          className="fixed inset-y-0 left-0 z-50 flex w-60 flex-col border-r border-slate-200 bg-white data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=open]:slide-in-from-left data-[state=closed]:slide-out-to-left md:hidden"
        >
          <Dialog.Title className="flex h-12 items-center px-4 font-display text-sm font-semibold text-slate-900">
            Portal
          </Dialog.Title>
          <Dialog.Description className="sr-only">
            Navigation menu
          </Dialog.Description>
          <NavList onItemClick={() => onOpenChange(false)} />
          <SidebarFooter onNavigate={() => onOpenChange(false)} />
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
