"use client";

import { createContext, useContext, useEffect, useMemo, useState } from "react";
import type { AuthUser } from "@/lib/auth/types";
import { getTokenFromDocumentCookie } from "./cookie-util";

const MOCK_IDP_URL = process.env.NEXT_PUBLIC_MOCK_IDP_URL || "http://localhost:8090";

interface MockAuthContextValue {
  authUser: AuthUser | null;
  isLoaded: boolean;
  orgSlug: string | null;
  token: string | null;
}

export const MockAuthContext = createContext<MockAuthContextValue | null>(null);

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(base64));
  } catch {
    return null;
  }
}

export function MockAuthContextProvider({ children }: { children: React.ReactNode }) {
  // All state starts as null/false — identical on server and client.
  // Cookie is read in useEffect (client-only) to prevent hydration mismatch
  // since document.cookie is unavailable during SSR.
  const [token, setToken] = useState<string | null>(null);
  const [orgSlug, setOrgSlug] = useState<string | null>(null);
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  const [isLoaded, setIsLoaded] = useState(false);

  useEffect(() => {
    const rawToken = getTokenFromDocumentCookie();
    if (!rawToken) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- auth init must mark loaded synchronously to avoid stale UI; refactor tracked separately
      setIsLoaded(true);
      return;
    }

    const payload = decodeJwtPayload(rawToken);
    const userId = typeof payload?.sub === "string" ? payload.sub : null;
    const organization =
      Array.isArray(payload?.organization) &&
      payload.organization.every((v): v is string => typeof v === "string")
        ? payload.organization
        : undefined;

    setToken(rawToken);
    setOrgSlug(organization?.[0] ?? null);

    if (!userId) {
      setIsLoaded(true);
      return;
    }

    let cancelled = false;

    fetch(`${MOCK_IDP_URL}/userinfo/${encodeURIComponent(userId)}`, {
      signal: AbortSignal.timeout(3000),
    })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (!cancelled && data) {
          setAuthUser({
            firstName: data.firstName ?? null,
            lastName: data.lastName ?? null,
            email: data.email ?? "",
            imageUrl: data.imageUrl ?? null,
          });
        }
      })
      .catch((e) => {
        console.error("MockAuthContextProvider: failed to fetch userinfo", e);
      })
      .finally(() => {
        if (!cancelled) setIsLoaded(true);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const value = useMemo<MockAuthContextValue>(
    () => ({ authUser, isLoaded, orgSlug, token }),
    [authUser, isLoaded, orgSlug, token]
  );

  return <MockAuthContext.Provider value={value}>{children}</MockAuthContext.Provider>;
}

export function useMockAuthContext(): MockAuthContextValue {
  const ctx = useContext(MockAuthContext);
  if (!ctx) {
    throw new Error("useMockAuthContext must be used within MockAuthContextProvider");
  }
  return ctx;
}
