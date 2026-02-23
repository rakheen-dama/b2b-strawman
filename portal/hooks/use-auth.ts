"use client";

import { useCallback, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { getAuth, clearAuth, type CustomerInfo } from "@/lib/auth";

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

// Listeners for useSyncExternalStore
let listeners: Array<() => void> = [];

function subscribe(listener: () => void): () => void {
  listeners = [...listeners, listener];
  return () => {
    listeners = listeners.filter((l) => l !== listener);
  };
}

function emitChange(): void {
  for (const listener of listeners) {
    listener();
  }
}

function getSnapshot(): AuthSnapshot {
  const auth = getAuth();
  if (auth) {
    return { jwt: auth.jwt, customer: auth.customer };
  }
  return emptySnapshot;
}

function getServerSnapshot(): AuthSnapshot {
  return emptySnapshot;
}

/**
 * React hook for portal authentication state.
 * Uses useSyncExternalStore to read from localStorage without useEffect.
 */
export function useAuth(): UseAuthReturn {
  const router = useRouter();
  const snapshot = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  const logout = useCallback(() => {
    clearAuth();
    emitChange();
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
