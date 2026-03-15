import { cookies } from "next/headers";
import type { AuthContext, SessionIdentity } from "../../types";

const MOCK_IDP_URL = process.env.MOCK_IDP_URL || "http://mock-idp:8090";

/**
 * Mock auth provider — reads JWT from mock-auth-token cookie.
 * Used when NEXT_PUBLIC_AUTH_MODE=mock (E2E testing / agent automation).
 *
 * JWT payload format:
 *   { sub, o: { id, slg }, iss, aud, iat, exp, v }
 */

function getTokenFromCookie(
  cookieStore: Awaited<ReturnType<typeof cookies>>,
): string {
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

/**
 * Mock user groups — maps userId to group names.
 * Alice gets platform-admins so E2E tests can exercise the admin panel.
 */
const MOCK_USER_GROUPS: Record<string, string[]> = {
  user_e2e_alice: ["platform-admins"],
};

export async function getSessionIdentity(): Promise<SessionIdentity> {
  const cookieStore = await cookies();
  const token = getTokenFromCookie(cookieStore);
  const payload = decodeJwtPayload(token);

  const userId = payload.sub as string | undefined;
  if (!userId) {
    throw new Error("No userId in mock JWT");
  }

  return {
    userId,
    groups: MOCK_USER_GROUPS[userId] ?? [],
  };
}

export async function getAuthContext(): Promise<AuthContext> {
  const cookieStore = await cookies();
  const token = getTokenFromCookie(cookieStore);
  const payload = decodeJwtPayload(token);

  const userId = payload.sub as string | undefined;
  const org = payload.o as
    | { id?: string; slg?: string }
    | undefined;

  if (!userId || !org?.id || !org?.slg) {
    throw new Error(
      "No active organization — mock JWT missing required claims",
    );
  }

  return {
    userId,
    orgId: org.id,
    orgSlug: org.slg,
    groups: MOCK_USER_GROUPS[userId] ?? [],
  };
}

export async function getAuthToken(): Promise<string> {
  const cookieStore = await cookies();
  return getTokenFromCookie(cookieStore);
}

export async function getCurrentUserEmail(): Promise<string | null> {
  const cookieStore = await cookies();
  const token = getTokenFromCookie(cookieStore);
  const payload = decodeJwtPayload(token);
  const userId = payload.sub as string | undefined;

  if (!userId) {
    return null;
  }

  try {
    const response = await fetch(`${MOCK_IDP_URL}/userinfo/${userId}`, {
      signal: AbortSignal.timeout(3000),
    });
    if (!response.ok) {
      return null;
    }
    const data = await response.json();
    return data.email ?? null;
  } catch {
    return null;
  }
}

export async function hasPlan(_plan: string): Promise<boolean> {
  // Mock mode always returns true — E2E tests run with full Pro features
  return true;
}

