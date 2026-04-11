import { cookies } from "next/headers";
import type { AuthContext, SessionIdentity } from "../../types";

const MOCK_IDP_URL = process.env.MOCK_IDP_URL || "http://mock-idp:8090";

/**
 * Mock auth provider — reads JWT from mock-auth-token cookie.
 * Used when NEXT_PUBLIC_AUTH_MODE=mock (E2E testing / agent automation).
 *
 * JWT payload format (Keycloak):
 *   { sub, organization: [orgSlug], groups: [...], email, iss, aud, iat, exp }
 */

function getTokenFromCookie(cookieStore: Awaited<ReturnType<typeof cookies>>): string {
  const token = cookieStore.get("mock-auth-token")?.value;
  if (!token) {
    throw new Error("No mock-auth-token cookie — user is not authenticated");
  }
  return token;
}

function decodeJwtPayload(token: string): Record<string, unknown> {
  const parts = token.split(".");
  if (parts.length !== 3) {
    throw new Error("Invalid JWT format — expected 3 parts");
  }
  // Base64url decode: replace URL-safe chars, add padding
  const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
  return JSON.parse(atob(base64));
}

export async function getSessionIdentity(): Promise<SessionIdentity> {
  const cookieStore = await cookies();
  const token = getTokenFromCookie(cookieStore);
  const payload = decodeJwtPayload(token);

  const userId = payload.sub as string | undefined;
  if (!userId) {
    throw new Error("No userId in mock JWT");
  }

  const groups = (payload.groups as string[]) ?? [];

  return {
    userId,
    groups,
  };
}

export async function getAuthContext(): Promise<AuthContext> {
  const cookieStore = await cookies();
  const token = getTokenFromCookie(cookieStore);
  const payload = decodeJwtPayload(token);

  const userId = payload.sub as string | undefined;
  const organization = payload.organization as string[] | undefined;
  const groups = (payload.groups as string[]) ?? [];

  const orgSlug = organization?.[0];

  if (!userId || !orgSlug) {
    throw new Error("No active organization — mock JWT missing required claims");
  }

  return {
    userId,
    orgId: orgSlug, // In Keycloak format, orgId = orgSlug
    orgSlug,
    groups,
  };
}

export async function getAuthToken(): Promise<string> {
  const cookieStore = await cookies();
  return getTokenFromCookie(cookieStore);
}

export async function getCurrentUserEmail(): Promise<string | null> {
  const info = await getCurrentUserInfo();
  return info.email;
}

export async function getCurrentUserInfo(): Promise<{
  name: string | null;
  email: string | null;
}> {
  const cookieStore = await cookies();
  const token = getTokenFromCookie(cookieStore);
  const payload = decodeJwtPayload(token);

  const email = (payload.email as string | undefined) ?? null;
  const name = (payload.name as string | undefined) ?? null;

  if (email || name) return { name, email };

  // Fallback: fetch from userinfo endpoint
  const userId = payload.sub as string | undefined;
  if (!userId) return { name: null, email: null };

  try {
    const response = await fetch(`${MOCK_IDP_URL}/userinfo/${userId}`, {
      signal: AbortSignal.timeout(3000),
    });
    if (!response.ok) return { name: null, email: null };
    const data = await response.json();
    return { name: data.name ?? null, email: data.email ?? null };
  } catch {
    return { name: null, email: null };
  }
}
