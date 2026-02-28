"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useMockAuthContext } from "./mock-context";
import type { AuthUser, OrgMemberInfo } from "@/lib/auth/types";

// This URL is used for direct browser-to-backend calls in mock/E2E mode only.
// In production, Clerk-authenticated requests go through Next.js route handlers.
// Since this module is only loaded when NEXT_PUBLIC_AUTH_PROVIDER=mock,
// there is no security concern with the browser calling the backend directly.
const BACKEND_URL =
  process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

// --- useAuthUser ---

export function useAuthUser(): { user: AuthUser | null; isLoaded: boolean } {
  const { authUser, isLoaded } = useMockAuthContext();
  return { user: authUser, isLoaded };
}

// --- useOrgMembers ---

export function useOrgMembers(): {
  members: OrgMemberInfo[];
  isLoaded: boolean;
} {
  // Read token from context to stay in sync with the provider
  const { token } = useMockAuthContext();
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
  const router = useRouter();

  const signOut = useCallback(() => {
    document.cookie = "mock-auth-token=; max-age=0; path=/";
    router.push("/mock-login");
  }, [router]);

  return { signOut };
}
