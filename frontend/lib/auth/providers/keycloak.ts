import { auth } from "@/auth";
import type { AuthContext } from "../types";

/**
 * Keycloak auth provider — wraps next-auth v5 session calls.
 * Used when NEXT_PUBLIC_AUTH_MODE=keycloak.
 *
 * The Keycloak access token contains org claims in the "o" map
 * (shaped by the OrgRoleProtocolMapper SPI):
 *   { sub, o: { id, rol, slg }, ... }
 */

function decodeJwtPayload(token: string): Record<string, unknown> {
  const parts = token.split(".");
  if (parts.length !== 3) {
    throw new Error("Invalid JWT format — expected 3 parts");
  }
  const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
  return JSON.parse(atob(base64));
}

export async function getAuthContext(): Promise<AuthContext> {
  const session = await auth();

  if (!session?.accessToken) {
    throw new Error("No active session — user may not be authenticated");
  }

  const payload = decodeJwtPayload(session.accessToken);
  const userId = payload.sub as string | undefined;
  const org = payload.o as
    | { id?: string; slg?: string; rol?: string }
    | undefined;

  if (!userId || !org?.id || !org?.slg || !org?.rol) {
    throw new Error(
      "No active organization — Keycloak JWT missing required org claims",
    );
  }

  return {
    userId,
    orgId: org.id,
    orgSlug: org.slg,
    orgRole: org.rol.startsWith("org:") ? org.rol : `org:${org.rol}`,
  };
}

export async function getAuthToken(): Promise<string> {
  const session = await auth();

  if (!session?.accessToken) {
    throw new Error(
      "No auth token available — user may not be authenticated",
    );
  }

  return session.accessToken;
}

export async function getCurrentUserEmail(): Promise<string | null> {
  const session = await auth();
  return session?.user?.email ?? null;
}

export async function hasPlan(_plan: string): Promise<boolean> {
  // Keycloak mode always returns true — plan enforcement not yet integrated
  return true;
}

export async function requireRole(
  role: "admin" | "owner" | "any",
): Promise<void> {
  const { orgRole } = await getAuthContext();

  if (role === "any") {
    return;
  }

  // orgRole is normalized to "org:" prefix format (matches Clerk convention)
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
