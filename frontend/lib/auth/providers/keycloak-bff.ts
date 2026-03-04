import "server-only";

import { cookies } from "next/headers";
import type { AuthContext } from "../types";

const GATEWAY_URL = process.env.GATEWAY_URL || "http://localhost:8443";

/** Response shape from the gateway's /bff/me endpoint. */
interface BffUserInfo {
  authenticated: boolean;
  userId: string | null;
  email: string | null;
  name: string | null;
  picture: string | null;
  orgId: string | null;
  orgSlug: string | null;
  orgRole: string | null;
}

/**
 * Fetch the current user's identity from the gateway BFF endpoint.
 * Forwards the SESSION cookie from the incoming Next.js request.
 */
async function fetchBffMe(): Promise<BffUserInfo> {
  const cookieStore = await cookies();
  const sessionCookie = cookieStore.get("SESSION");

  const headers: Record<string, string> = {};
  if (sessionCookie) {
    headers["cookie"] = `SESSION=${sessionCookie.value}`;
  }

  const response = await fetch(`${GATEWAY_URL}/bff/me`, {
    headers,
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error(
      `BFF /bff/me request failed with status ${response.status}`,
    );
  }

  return response.json() as Promise<BffUserInfo>;
}

export async function getAuthContext(): Promise<AuthContext> {
  const info = await fetchBffMe();

  if (!info.authenticated || !info.userId || !info.orgId || !info.orgSlug || !info.orgRole) {
    throw new Error("No active organization — user is not authenticated via BFF");
  }

  return {
    userId: info.userId,
    orgId: info.orgId,
    orgSlug: info.orgSlug,
    orgRole: info.orgRole.startsWith("org:") ? info.orgRole : `org:${info.orgRole}`,
  };
}

export async function getAuthToken(): Promise<string> {
  throw new Error(
    "getAuthToken() is not available in BFF mode. API calls should route through the gateway.",
  );
}

export async function getCurrentUserEmail(): Promise<string | null> {
  try {
    const info = await fetchBffMe();
    return info.email ?? null;
  } catch {
    return null;
  }
}

export async function hasPlan(_plan: string): Promise<boolean> {
  // Keycloak BFF mode always returns true — billing not yet wired for Keycloak
  return true;
}

export async function requireRole(
  role: "admin" | "owner" | "any",
): Promise<void> {
  const { orgRole } = await getAuthContext();

  if (role === "any") {
    return;
  }

  if (role === "owner" && orgRole !== "org:owner") {
    throw new Error("Insufficient permissions — owner role required");
  }

  if (
    role === "admin" &&
    orgRole !== "org:admin" &&
    orgRole !== "org:owner"
  ) {
    throw new Error("Insufficient permissions — admin role required");
  }
}
