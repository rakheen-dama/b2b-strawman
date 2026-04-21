"use client";

import Link from "next/link";
import { Menu, LogOut, User } from "lucide-react";
import { DropdownMenu } from "radix-ui";
import { useAuth } from "@/hooks/use-auth";
import { useBranding } from "@/hooks/use-portal-context";
import { isSafeImageUrl } from "@/lib/utils";
import { Button } from "@/components/ui/button";

interface PortalTopbarProps {
  onHamburgerClick: () => void;
}

export function PortalTopbar({ onHamburgerClick }: PortalTopbarProps) {
  const { customer, logout } = useAuth();
  const { orgName, logoUrl } = useBranding();

  return (
    <header className="sticky top-0 z-30 flex h-12 items-center justify-between border-b border-slate-200 bg-white px-4">
      {/* Skip-to-content link — visually hidden until focused (keyboard users) */}
      <a
        href="#main-content"
        data-testid="skip-to-content"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-2 focus:z-50 focus:rounded-md focus:bg-white focus:px-3 focus:py-2 focus:text-sm focus:font-medium focus:text-slate-900 focus:shadow focus:outline focus:outline-2 focus:outline-teal-500"
      >
        Skip to content
      </a>
      <div className="flex items-center gap-3">
        <Button
          variant="ghost"
          size="icon"
          className="min-h-11 min-w-11 md:hidden"
          onClick={onHamburgerClick}
          aria-label="Open navigation menu"
        >
          <Menu className="size-5" />
        </Button>
        {logoUrl && isSafeImageUrl(logoUrl) ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={logoUrl}
            alt={`${orgName} logo`}
            className="h-6 w-auto"
          />
        ) : (
          <span className="font-display text-sm font-semibold text-slate-900">
            {orgName}
          </span>
        )}
      </div>

      <DropdownMenu.Root>
        <DropdownMenu.Trigger asChild>
          <button
            type="button"
            className="inline-flex items-center gap-2 rounded-md px-2 py-1 text-sm text-slate-700 outline-none hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-400"
            aria-label="User menu"
          >
            <span className="truncate max-w-[12rem]">
              {customer?.name ?? ""}
            </span>
          </button>
        </DropdownMenu.Trigger>
        <DropdownMenu.Portal>
          <DropdownMenu.Content
            align="end"
            sideOffset={4}
            className="z-50 min-w-[10rem] rounded-md border border-slate-200 bg-white p-1 shadow-md"
          >
            <DropdownMenu.Item asChild>
              <Link
                href="/profile"
                className="flex items-center gap-2 rounded px-2 py-1.5 text-sm text-slate-700 outline-none hover:bg-slate-50 focus:bg-slate-50"
              >
                <User className="size-4" />
                Profile
              </Link>
            </DropdownMenu.Item>
            <DropdownMenu.Item
              onSelect={logout}
              className="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-sm text-slate-700 outline-none hover:bg-slate-50 focus:bg-slate-50"
            >
              <LogOut className="size-4" />
              Logout
            </DropdownMenu.Item>
          </DropdownMenu.Content>
        </DropdownMenu.Portal>
      </DropdownMenu.Root>
    </header>
  );
}
