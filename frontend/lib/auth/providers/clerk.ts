import type { AuthContext } from "../types";

/**
 * Clerk auth provider — stub.
 * Full implementation will be added in Epic 138B when the 44-file migration happens.
 */

export async function getAuthContext(): Promise<AuthContext> {
  throw new Error("Clerk provider not implemented — will be added in 138B");
}

export async function getAuthToken(): Promise<string> {
  throw new Error("Clerk provider not implemented — will be added in 138B");
}

export async function getCurrentUserEmail(): Promise<string | null> {
  throw new Error("Clerk provider not implemented — will be added in 138B");
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export async function requireRole(
  _role: "admin" | "owner" | "any",
): Promise<void> {
  throw new Error("Clerk provider not implemented — will be added in 138B");
}
