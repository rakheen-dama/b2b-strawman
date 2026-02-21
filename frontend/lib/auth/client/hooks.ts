"use client";

import { useState, useEffect, useMemo } from "react";
import { useRouter } from "next/navigation";
import { useMockAuthContext } from "./mock-context";
import type { AuthUser, OrgMemberInfo } from "@/lib/auth/types";

const BACKEND_URL =
  process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

// --- Helper ---

function getTokenFromDocumentCookie(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(/(?:^|;\s*)mock-auth-token=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

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
  // Read cookie synchronously to determine if a fetch is needed
  const initialToken = useMemo(() => getTokenFromDocumentCookie(), []);
  const [members, setMembers] = useState<OrgMemberInfo[]>([]);
  const [isLoaded, setIsLoaded] = useState(!initialToken);

  useEffect(() => {
    if (!initialToken) return;

    let cancelled = false;

    fetch(`${BACKEND_URL}/api/members`, {
      headers: { Authorization: `Bearer ${initialToken}` },
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
  }, [initialToken]);

  return { members, isLoaded };
}

// --- useSignOut ---

export function useSignOut(): { signOut: () => void } {
  const router = useRouter();

  const signOut = () => {
    document.cookie = "mock-auth-token=; max-age=0; path=/";
    router.push("/mock-login");
  };

  return { signOut };
}
