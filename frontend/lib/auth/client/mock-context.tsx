"use client";

import { createContext, useContext, useEffect, useMemo, useState } from "react";
import type { AuthUser } from "@/lib/auth/types";
import { getTokenFromDocumentCookie } from "./cookie-util";

const MOCK_IDP_URL =
  process.env.NEXT_PUBLIC_MOCK_IDP_URL || "http://localhost:8090";

interface MockAuthContextValue {
  authUser: AuthUser | null;
  isLoaded: boolean;
  orgSlug: string | null;
  token: string | null;
}

export const MockAuthContext = createContext<MockAuthContextValue | null>(null);

/**
 * Decode the payload segment of a JWT token.
 * The token value is already URL-decoded when read from the cookie via
 * `decodeURIComponent` in `getTokenFromDocumentCookie`, so the input
 * here is a standard dot-separated JWT string.
 */
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

/** Parse cookie synchronously to derive initial state values. */
function parseInitialState(): {
  token: string | null;
  orgSlug: string | null;
  userId: string | null;
} {
  const rawToken = getTokenFromDocumentCookie();
  if (!rawToken) return { token: null, orgSlug: null, userId: null };

  const payload = decodeJwtPayload(rawToken);
  const userId = (payload?.sub as string) ?? null;
  const org = payload?.o as { slg?: string } | undefined;

  return {
    token: rawToken,
    orgSlug: org?.slg ?? null,
    userId,
  };
}

export function MockAuthContextProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  // Synchronously read cookie on first render — no setState in effect needed
  const initial = useMemo(() => parseInitialState(), []);

  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  const [isLoaded, setIsLoaded] = useState(!initial.userId);

  useEffect(() => {
    if (!initial.userId) return;

    let cancelled = false;

    fetch(`${MOCK_IDP_URL}/userinfo/${initial.userId}`, {
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
  }, [initial.userId]);

  const value = useMemo<MockAuthContextValue>(
    () => ({
      authUser,
      isLoaded,
      orgSlug: initial.orgSlug,
      token: initial.token,
    }),
    [authUser, isLoaded, initial.orgSlug, initial.token],
  );

  return (
    <MockAuthContext.Provider value={value}>{children}</MockAuthContext.Provider>
  );
}

export function useMockAuthContext(): MockAuthContextValue {
  const ctx = useContext(MockAuthContext);
  if (!ctx) {
    throw new Error(
      "useMockAuthContext must be used within MockAuthContextProvider",
    );
  }
  return ctx;
}
