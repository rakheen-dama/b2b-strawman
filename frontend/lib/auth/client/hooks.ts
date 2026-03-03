"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useSession, signOut as nextAuthSignOut } from "next-auth/react";
import { useMockAuthContext } from "./mock-context";
import type { AuthUser, OrgMemberInfo } from "@/lib/auth/types";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

// This URL is used for direct browser-to-backend calls in mock/keycloak mode.
// In production (Clerk), authenticated requests go through Next.js route handlers.
export const BACKEND_URL =
  process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

// --- useAuthUser ---

export function useAuthUser(): { user: AuthUser | null; isLoaded: boolean } {
  if (AUTH_MODE === "keycloak") {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    const { data: session, status } = useSession();
    const isLoaded = status !== "loading";
    const user: AuthUser | null = session?.user
      ? {
          firstName: session.user.name?.split(" ")[0] ?? null,
          lastName:
            session.user.name?.split(" ").slice(1).join(" ") || null,
          email: session.user.email ?? "",
          imageUrl: session.user.image ?? null,
        }
      : null;
    return { user, isLoaded };
  }

  // Mock mode (and fallback for clerk — clerk components use useUser() directly)
  // eslint-disable-next-line react-hooks/rules-of-hooks
  const { authUser, isLoaded } = useMockAuthContext();
  return { user: authUser, isLoaded };
}

// --- useOrgMembers ---

export function useOrgMembers(): {
  members: OrgMemberInfo[];
  isLoaded: boolean;
} {
  // Get token from the appropriate source
  let token: string | null = null;
  if (AUTH_MODE === "keycloak") {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    const { data: session } = useSession();
    token = session?.accessToken ?? null;
  } else {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    const ctx = useMockAuthContext();
    token = ctx.token;
  }

  const [members, setMembers] = useState<OrgMemberInfo[]>([]);
  const [isLoaded, setIsLoaded] = useState(!token);

  useEffect(() => {
    if (!token) return;

    let cancelled = false;

    fetch(`${BACKEND_URL}/api/members`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => (res.ok ? res.json() : []))
      .then((data: OrgMemberInfo[]) => {
        if (!cancelled) {
          setMembers(Array.isArray(data) ? data : []);
        }
      })
      .catch((err) => {
        console.error("useOrgMembers: failed to fetch members", err);
      })
      .finally(() => {
        if (!cancelled) setIsLoaded(true);
      });

    return () => {
      cancelled = true;
    };
  }, [token]);

  return { members, isLoaded };
}

// --- useSignOut ---

export function useSignOut(): { signOut: () => void } {
  if (AUTH_MODE === "keycloak") {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    const signOut = useCallback(() => {
      nextAuthSignOut({ callbackUrl: "/sign-in" });
    }, []);
    return { signOut };
  }

  // Mock mode
  // eslint-disable-next-line react-hooks/rules-of-hooks
  const router = useRouter();
  // eslint-disable-next-line react-hooks/rules-of-hooks
  const signOut = useCallback(() => {
    document.cookie = "mock-auth-token=; max-age=0; path=/";
    router.push("/mock-login");
  }, [router]);
  return { signOut };
}
