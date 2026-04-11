"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useMockAuthContext } from "./mock-context";
import type { AuthUser, OrgMemberInfo } from "@/lib/auth/types";

/** Normalize role string to org:-prefixed lowercase format (e.g. "owner" → "org:owner"). */
function normalizeRole(role: string): string {
  const lower = role.toLowerCase();
  if (lower.startsWith("org:")) return lower;
  return `org:${lower}`;
}

// This URL is used for direct browser-to-backend calls in mock/E2E mode only.
// In production, Keycloak-authenticated requests go through the BFF gateway.
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
  // Read token + isLoaded from context to stay in sync with the provider
  const { token, isLoaded: contextLoaded } = useMockAuthContext();
  const [members, setMembers] = useState<OrgMemberInfo[]>([]);
  const [isLoaded, setIsLoaded] = useState(false);

  useEffect(() => {
    // Context hasn't loaded yet — wait for token to be available
    if (!contextLoaded) return;
    // Context loaded but no token — no members to fetch
    if (!token) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- auth init must mark loaded synchronously to avoid stale UI; refactor tracked separately
      setIsLoaded(true);
      return;
    }

    setIsLoaded(false);
    let cancelled = false;

    fetch(`${BACKEND_URL}/api/members`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => (res.ok ? res.json() : []))
      .then((data: Array<Record<string, unknown>>) => {
        if (!cancelled) {
          const mapped: OrgMemberInfo[] = (Array.isArray(data) ? data : []).map((m) => ({
            id: String(m.id ?? ""),
            email: String(m.email ?? ""),
            name: m.name ? String(m.name) : null,
            role: normalizeRole(String(m.orgRole ?? m.role ?? "member")),
          }));
          setMembers(mapped);
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
  }, [token, contextLoaded]);

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
