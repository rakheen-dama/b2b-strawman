"use client";

import { useState, useRef, useEffect } from "react";
import { LogOut } from "lucide-react";

const GATEWAY_URL =
  process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8443";

interface BffUserInfo {
  name: string;
  email: string;
}

function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) {
    return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
  }
  return name.charAt(0).toUpperCase() || "?";
}

function useBffUser(): BffUserInfo | null {
  const [user, setUser] = useState<BffUserInfo | null>(null);

  useEffect(() => {
    let cancelled = false;

    fetch(`${GATEWAY_URL}/bff/me`, { credentials: "include" })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (!cancelled && data?.authenticated) {
          setUser({
            name: data.name || "User",
            email: data.email || "",
          });
        }
      })
      .catch(() => {
        // Silently fail — user menu will show fallback
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return user;
}

/**
 * Get the Keycloak login URL (gateway OAuth2 authorization endpoint).
 */
export function getKeycloakLoginUrl(): string {
  return `${GATEWAY_URL}/oauth2/authorization/keycloak`;
}

/**
 * Get the Keycloak logout URL (gateway logout endpoint).
 * The gateway's OidcClientInitiatedLogoutSuccessHandler handles Keycloak session termination.
 */
export function getKeycloakLogoutUrl(): string {
  return `${GATEWAY_URL}/logout`;
}

/**
 * BFF user menu component for Keycloak auth mode.
 * Displays user avatar initials + name with a sign-out dropdown.
 * Fetches user info client-side from the gateway /bff/me endpoint.
 */
export function UserMenuBff() {
  const user = useBffUser();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    if (open) document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  const initials = user ? getInitials(user.name) : "?";

  function handleSignOut() {
    // Full navigation to gateway logout endpoint — not a client-side route
    window.location.href = getKeycloakLogoutUrl();
  }

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-200 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-300 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
      >
        {initials}
      </button>
      {open && (
        <div className="absolute right-0 top-full mt-2 w-48 rounded-lg border border-slate-200 bg-white py-1 shadow-lg dark:border-slate-700 dark:bg-slate-900">
          <div className="border-b border-slate-100 px-3 py-2 dark:border-slate-800">
            <p className="truncate text-sm font-medium text-slate-900 dark:text-slate-100">
              {user?.name ?? "Loading..."}
            </p>
            <p className="truncate text-xs text-slate-500 dark:text-slate-400">
              {user?.email ?? ""}
            </p>
          </div>
          <button
            onClick={handleSignOut}
            className="flex w-full items-center gap-2 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800"
          >
            <LogOut className="h-3.5 w-3.5" />
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}
