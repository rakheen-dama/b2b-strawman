"use client";

import { useState, useRef, useEffect } from "react";
import { LogOut } from "lucide-react";
import { UserButton, OrganizationSwitcher } from "@clerk/nextjs";
import { useSession, signIn } from "next-auth/react";
import { usePathname } from "next/navigation";
import { useMockAuthContext } from "@/lib/auth/client/mock-context";
import { useAuthUser, useSignOut } from "@/lib/auth/client";
import { cn } from "@/lib/utils";
import type { AuthUser } from "@/lib/auth";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

const BACKEND_URL =
  process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

function getInitials(user: AuthUser | null): string {
  if (!user) return "?";
  const fi = user.firstName?.charAt(0) ?? "";
  const li = user.lastName?.charAt(0) ?? "";
  return (fi + li).toUpperCase() || user.email?.charAt(0).toUpperCase() || "?";
}

function MockOrgSwitcher() {
  const { orgSlug } = useMockAuthContext();
  return (
    <div className="flex items-center gap-2 rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-700 dark:border-slate-700 dark:text-slate-300">
      <span className="truncate font-medium">{orgSlug ?? "No org"}</span>
    </div>
  );
}

function MockUserButton() {
  const { authUser } = useMockAuthContext();
  const { signOut } = useSignOut();
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

  const initials = getInitials(authUser);

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
              {authUser?.firstName} {authUser?.lastName}
            </p>
            <p className="truncate text-xs text-slate-500 dark:text-slate-400">
              {authUser?.email}
            </p>
          </div>
          <button
            onClick={signOut}
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

function KeycloakUserButton() {
  const { user } = useAuthUser();
  const { signOut } = useSignOut();
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

  const initials = getInitials(user);

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
              {user?.firstName} {user?.lastName}
            </p>
            <p className="truncate text-xs text-slate-500 dark:text-slate-400">
              {user?.email}
            </p>
          </div>
          <button
            onClick={signOut}
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

interface UserOrg {
  id: string;
  name: string;
  slug: string;
  role: string;
}

function KeycloakOrgSwitcher() {
  const { data: session } = useSession();
  const pathname = usePathname();
  const [orgs, setOrgs] = useState<UserOrg[]>([]);
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const currentSlug = pathname?.match(/^\/org\/([^/]+)/)?.[1] ?? null;
  const currentOrg =
    orgs.find((o) => o.slug === currentSlug) ?? orgs[0] ?? null;

  useEffect(() => {
    const token = session?.accessToken;
    if (!token) return;

    let cancelled = false;

    fetch(`${BACKEND_URL}/api/orgs/mine`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => (res.ok ? res.json() : []))
      .then((data: UserOrg[]) => {
        if (!cancelled) setOrgs(Array.isArray(data) ? data : []);
      })
      .catch((err) => {
        console.error("KeycloakOrgSwitcher: failed to fetch orgs", err);
      });

    return () => {
      cancelled = true;
    };
  }, [session?.accessToken]);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    if (open) document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  function handleOrgSelect(org: UserOrg) {
    setOpen(false);
    if (org.slug === currentSlug) return;
    signIn("keycloak", { kc_org: org.slug });
  }

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-2 rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-700 transition-colors hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
      >
        <span className="truncate font-medium">
          {currentOrg?.name ?? "Select org"}
        </span>
      </button>
      {open && orgs.length > 0 && (
        <div className="absolute left-0 top-full mt-2 w-56 rounded-lg border border-slate-200 bg-white py-1 shadow-lg dark:border-slate-700 dark:bg-slate-900">
          {orgs.map((org) => (
            <button
              key={org.id}
              onClick={() => handleOrgSelect(org)}
              className={cn(
                "flex w-full items-center gap-2 px-3 py-2 text-sm hover:bg-slate-50 dark:hover:bg-slate-800",
                org.slug === currentSlug
                  ? "font-medium text-slate-900 dark:text-slate-100"
                  : "text-slate-700 dark:text-slate-300",
              )}
            >
              <span className="truncate">{org.name}</span>
              {org.slug === currentSlug && (
                <span className="ml-auto text-xs text-teal-600">Current</span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function ClerkHeaderControls() {
  return (
    <>
      <OrganizationSwitcher
        afterSelectOrganizationUrl="/org/:slug/dashboard"
        afterCreateOrganizationUrl="/org/:slug/dashboard"
        hidePersonal
      />
      <UserButton />
    </>
  );
}

/**
 * Auth-aware header controls — renders Clerk UI, Keycloak, or mock equivalents
 * based on build-time AUTH_MODE selection.
 */
export function AuthHeaderControls() {
  if (AUTH_MODE === "mock") {
    return (
      <>
        <MockOrgSwitcher />
        <MockUserButton />
      </>
    );
  }
  if (AUTH_MODE === "keycloak") {
    return (
      <>
        <KeycloakOrgSwitcher />
        <KeycloakUserButton />
      </>
    );
  }
  return <ClerkHeaderControls />;
}
