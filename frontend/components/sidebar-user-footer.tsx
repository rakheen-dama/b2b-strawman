"use client";

import { useAuthUser } from "@/lib/auth/client";
import { getInitials as getBffInitials } from "@/components/auth/user-menu-bff";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";

interface SidebarUserFooterProps {
  userName?: string | null;
  userEmail?: string | null;
}

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

function MockUserFooter() {
  const { user, isLoaded } = useAuthUser();
  if (!isLoaded) return null;
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

function KeycloakUserFooter({
  userName,
  userEmail,
}: {
  userName?: string | null;
  userEmail?: string | null;
}) {
  // Use server-provided user info (passed from layout via getAuthContext/getCurrentUserInfo)
  // instead of client-side /bff/me fetch (which fails due to SameSite cookie restrictions)
  const name = userName ?? "User";
  const email = userEmail ?? "";
  const initials = getBffInitials(name);

  return <UserFooterUI initials={initials} name={name} email={email} />;
}

/**
 * Auth-aware sidebar user footer — dispatches between mock
 * and Keycloak based on build-time AUTH_MODE selection.
 * In Keycloak mode, accepts server-provided userName/userEmail props
 * to avoid cross-origin client-side fetch issues.
 */
export function SidebarUserFooter({ userName, userEmail }: SidebarUserFooterProps) {
  if (AUTH_MODE === "mock") return <MockUserFooter />;
  return <KeycloakUserFooter userName={userName} userEmail={userEmail} />;
}
