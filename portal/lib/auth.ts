/**
 * Portal authentication state stored in localStorage.
 *
 * Security note: Storing JWTs in localStorage is an accepted tradeoff for the
 * customer portal. The portal is a separate Next.js app (not the main SaaS
 * frontend) with limited scope. Customer portal JWTs are short-lived and
 * scoped to a single tenant. See ADR-077 for the full rationale.
 */
import { decodeJwt } from "jose";

const JWT_KEY = "portal_jwt";
const CUSTOMER_KEY = "portal_customer";

export interface CustomerInfo {
  id: string;
  name: string;
  email: string;
  orgId: string;
}

// --- External store change notification ---
// Used by useSyncExternalStore in useAuth hook.
let listeners: Array<() => void> = [];
let version = 0;

export function subscribeAuth(listener: () => void): () => void {
  listeners = [...listeners, listener];
  return () => {
    listeners = listeners.filter((l) => l !== listener);
  };
}

export function emitAuthChange(): void {
  version++;
  for (const listener of listeners) {
    listener();
  }
}

export function getAuthVersion(): number {
  return version;
}

/**
 * Stores the portal JWT and customer info in localStorage.
 * Called after successful token exchange.
 */
export function storeAuth(jwt: string, customer: CustomerInfo): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(JWT_KEY, jwt);
  localStorage.setItem(CUSTOMER_KEY, JSON.stringify(customer));
  emitAuthChange();
}

/**
 * Retrieves stored auth data. Returns null if no JWT stored,
 * or if the JWT has expired (also clears expired data).
 */
export function getAuth(): { jwt: string; customer: CustomerInfo } | null {
  if (typeof window === "undefined") return null;

  const jwt = localStorage.getItem(JWT_KEY);
  const customerJson = localStorage.getItem(CUSTOMER_KEY);

  if (!jwt || !customerJson) return null;

  // Check JWT expiry
  try {
    const claims = decodeJwt(jwt);
    if (claims.exp && claims.exp * 1000 < Date.now()) {
      // Token expired -- clear storage
      clearAuth();
      return null;
    }
  } catch {
    // Invalid JWT -- clear storage
    clearAuth();
    return null;
  }

  try {
    const customer: CustomerInfo = JSON.parse(customerJson);
    return { jwt, customer };
  } catch {
    clearAuth();
    return null;
  }
}

/**
 * Clears all auth data from localStorage.
 */
export function clearAuth(): void {
  if (typeof window === "undefined") return;
  localStorage.removeItem(JWT_KEY);
  localStorage.removeItem(CUSTOMER_KEY);
  emitAuthChange();
}

/**
 * Returns the stored JWT string, or null if not authenticated or expired.
 */
export function getJwt(): string | null {
  const auth = getAuth();
  return auth?.jwt ?? null;
}

/**
 * Returns the stored customer info, or null if not authenticated.
 */
export function getCustomer(): CustomerInfo | null {
  const auth = getAuth();
  return auth?.customer ?? null;
}

/**
 * Returns true if a valid (non-expired) JWT exists in storage.
 */
export function isAuthenticated(): boolean {
  return getAuth() !== null;
}
