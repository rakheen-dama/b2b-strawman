"use client";

import { useState, useRef, useEffect } from "react";
import { LogOut } from "lucide-react";
import { UserButton, OrganizationSwitcher } from "@clerk/nextjs";
import { useMockAuthContext } from "@/lib/auth/client/mock-context";
import { useSignOut } from "@/lib/auth/client";
import type { AuthUser } from "@/lib/auth";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

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
 * Auth-aware header controls -- renders Clerk UI or mock equivalents
 * based on build-time AUTH_MODE selection.
 */
export function AuthControls() {
  if (AUTH_MODE === "mock") {
    return (
      <>
        <MockOrgSwitcher />
        <MockUserButton />
      </>
    );
  }
  return <ClerkHeaderControls />;
}
