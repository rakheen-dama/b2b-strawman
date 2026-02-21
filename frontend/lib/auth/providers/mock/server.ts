import type { AuthContext } from "../../types";

/**
 * Mock auth provider — stub.
 * Full implementation will be added in Epic 138B.
 */

export async function getAuthContext(): Promise<AuthContext> {
  throw new Error("Mock provider not implemented — will be added in 138B");
}

export async function getAuthToken(): Promise<string> {
  throw new Error("Mock provider not implemented — will be added in 138B");
}

export async function getCurrentUserEmail(): Promise<string | null> {
  throw new Error("Mock provider not implemented — will be added in 138B");
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export async function requireRole(
  _role: "admin" | "owner" | "any",
): Promise<void> {
  throw new Error("Mock provider not implemented — will be added in 138B");
}
