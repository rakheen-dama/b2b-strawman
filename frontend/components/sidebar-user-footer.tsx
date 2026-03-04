"use client";

import { useState, useEffect } from "react";
import { useUser } from "@clerk/nextjs";
import { useAuthUser } from "@/lib/auth/client";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

function getInitials(
  firstName: string | null,
  lastName: string | null,
  email: string | null,
): string {
  const fi = firstName?.charAt(0) ?? "";
  const li = lastName?.charAt(0) ?? "";
  const combined = (fi + li).toUpperCase();
  if (combined) return combined;
  return email?.charAt(0).toUpperCase() ?? "?";
}

function ClerkUserFooter() {
  const { user } = useUser();
  const initials = getInitials(
    user?.firstName ?? null,
    user?.lastName ?? null,
    user?.primaryEmailAddress?.emailAddress ?? null,
  );

  return (
    <UserFooterUI
      initials={initials}
      name={user?.fullName ?? "User"}
      email={user?.primaryEmailAddress?.emailAddress ?? ""}
    />
  );
}

function MockUserFooter() {
  const { user } = useAuthUser();
  const initials = getInitials(
    user?.firstName ?? null,
    user?.lastName ?? null,
    user?.email ?? null,
  );
  const name =
    user?.firstName && user?.lastName
      ? `${user.firstName} ${user.lastName}`
      : user?.firstName ?? "User";

  return <UserFooterUI initials={initials} name={name} email={user?.email ?? ""} />;
}

function UserFooterUI({
  initials,
  name,
  email,
}: {
  initials: string;
  name: string;
  email: string;
}) {
  return (
    <>
      <div className="mx-4 border-t border-white/10" />
      <div className="flex items-center gap-3 px-4 py-3">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-800 text-xs font-medium text-white">
          {initials}
        </div>
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-white">{name}</p>
          <p className="truncate text-xs text-white/60">{email}</p>
        </div>
      </div>
    </>
  );
}

const GATEWAY_URL =
  process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8443";

function KeycloakUserFooter() {
  const [user, setUser] = useState<{
    name: string;
    email: string;
  } | null>(null);

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
        // Silently fail — footer will show fallback
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const name = user?.name ?? "User";
  const email = user?.email ?? "";
  const parts = name.trim().split(/\s+/);
  const initials =
    parts.length >= 2
      ? (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase()
      : name.charAt(0).toUpperCase() || "?";

  return <UserFooterUI initials={initials} name={name} email={email} />;
}

/**
 * Auth-aware sidebar user footer — dispatches between Clerk, mock,
 * and Keycloak based on build-time AUTH_MODE selection.
 */
export function SidebarUserFooter() {
  if (AUTH_MODE === "mock") return <MockUserFooter />;
  if (AUTH_MODE === "keycloak") return <KeycloakUserFooter />;
  return <ClerkUserFooter />;
}
