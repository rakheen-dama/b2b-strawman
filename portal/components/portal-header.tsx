"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Menu, X, LogOut, User } from "lucide-react";
import { useAuth } from "@/hooks/use-auth";
import { useBranding } from "@/hooks/use-branding";
import { isSafeImageUrl } from "@/lib/utils";
import { Button } from "@/components/ui/button";

const NAV_LINKS = [
  { href: "/projects", label: "Projects" },
  { href: "/invoices", label: "Invoices" },
];

export function PortalHeader() {
  const { customer, logout } = useAuth();
  const { orgName, logoUrl, brandColor } = useBranding();
  const pathname = usePathname();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return (
    <header className="sticky top-0 z-50 border-b border-slate-200 bg-white">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6 lg:px-8">
        {/* Left: Logo / Org Name */}
        <div className="flex items-center gap-3">
          {logoUrl && isSafeImageUrl(logoUrl) ? (
            <img
              src={logoUrl}
              alt={`${orgName} logo`}
              className="h-8 w-auto"
            />
          ) : (
            <span className="font-display text-lg font-semibold text-slate-900">
              {orgName}
            </span>
          )}
        </div>

        {/* Center: Desktop Navigation */}
        <nav className="hidden items-center gap-1 md:flex">
          {NAV_LINKS.map((link) => {
            const isActive = pathname.startsWith(link.href);
            return (
              <Link
                key={link.href}
                href={link.href}
                className="relative px-4 py-2 text-sm font-medium transition-colors"
                style={{
                  color: isActive ? brandColor : undefined,
                }}
              >
                {link.label}
                {isActive && (
                  <span
                    className="absolute inset-x-4 -bottom-[17px] h-0.5"
                    style={{ backgroundColor: brandColor }}
                  />
                )}
              </Link>
            );
          })}
        </nav>

        {/* Right: User info + Logout (Desktop) */}
        <div className="hidden items-center gap-3 md:flex">
          <span className="text-sm text-slate-600">{customer?.name}</span>
          <Link
            href="/profile"
            className="text-slate-500 transition-colors hover:text-slate-700"
          >
            <User className="size-4" />
          </Link>
          <Button variant="ghost" size="sm" onClick={logout}>
            <LogOut className="size-4" />
            <span className="sr-only">Logout</span>
          </Button>
        </div>

        {/* Mobile: Hamburger */}
        <Button
          variant="ghost"
          size="icon"
          className="md:hidden"
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          aria-label={mobileMenuOpen ? "Close menu" : "Open menu"}
        >
          {mobileMenuOpen ? (
            <X className="size-5" />
          ) : (
            <Menu className="size-5" />
          )}
        </Button>
      </div>

      {/* Mobile Menu */}
      {mobileMenuOpen && (
        <div className="border-t border-slate-200 bg-white px-4 pb-4 pt-2 md:hidden">
          <nav className="flex flex-col gap-1">
            {NAV_LINKS.map((link) => {
              const isActive = pathname.startsWith(link.href);
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  className="rounded-md px-3 py-2 text-sm font-medium"
                  style={{
                    color: isActive ? brandColor : undefined,
                    backgroundColor: isActive
                      ? `color-mix(in srgb, ${brandColor} 10%, transparent)`
                      : undefined,
                  }}
                  onClick={() => setMobileMenuOpen(false)}
                >
                  {link.label}
                </Link>
              );
            })}
          </nav>
          <div className="mt-3 flex items-center justify-between border-t border-slate-100 pt-3">
            <div className="flex items-center gap-2">
              <span className="text-sm text-slate-600">{customer?.name}</span>
              <Link
                href="/profile"
                className="text-sm text-slate-500 hover:text-slate-700"
              >
                Profile
              </Link>
            </div>
            <Button variant="ghost" size="sm" onClick={logout}>
              <LogOut className="size-4" />
              Logout
            </Button>
          </div>
        </div>
      )}
    </header>
  );
}
