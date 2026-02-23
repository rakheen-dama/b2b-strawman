"use client";

import { useCallback, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import {
  getAuth,
  clearAuth,
  subscribeAuth,
  getAuthVersion,
  type CustomerInfo,
} from "@/lib/auth";

interface UseAuthReturn {
  isAuthenticated: boolean;
  isLoading: boolean;
  jwt: string | null;
  customer: CustomerInfo | null;
  logout: () => void;
}

interface AuthSnapshot {
  jwt: string | null;
  customer: CustomerInfo | null;
}

const emptySnapshot: AuthSnapshot = { jwt: null, customer: null };

// Cached snapshot — only recomputed when version changes.
let cachedSnapshot: AuthSnapshot = emptySnapshot;
let cachedVersion = -1;

function getSnapshot(): AuthSnapshot {
  const currentVersion = getAuthVersion();
  if (currentVersion !== cachedVersion) {
    cachedVersion = currentVersion;
    const auth = getAuth();
    cachedSnapshot = auth
      ? { jwt: auth.jwt, customer: auth.customer }
      : emptySnapshot;
  }
  return cachedSnapshot;
}

function getServerSnapshot(): AuthSnapshot {
  return emptySnapshot;
}

/**
 * React hook for portal authentication state.
 * Uses useSyncExternalStore to read from localStorage without useEffect.
 * Auth changes (storeAuth/clearAuth) emit notifications automatically.
 */
export function useAuth(): UseAuthReturn {
  const router = useRouter();
  const snapshot = useSyncExternalStore(
    subscribeAuth,
    getSnapshot,
    getServerSnapshot
  );

  const logout = useCallback(() => {
    clearAuth();
    router.push("/login");
  }, [router]);

  return {
    isAuthenticated: snapshot.jwt !== null,
    isLoading: false,
    jwt: snapshot.jwt,
    customer: snapshot.customer,
    logout,
  };
}
