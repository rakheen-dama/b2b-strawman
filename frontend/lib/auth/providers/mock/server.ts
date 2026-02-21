import type { AuthContext } from "../../types";

/**
 * Mock auth provider — stub.
 * All functions throw until Epic 141 replaces this with the real mock implementation.
 */

export async function getAuthContext(): Promise<AuthContext> {
  throw new Error(
    "Mock auth provider not yet implemented — implement in Epic 141",
  );
}

export async function getAuthToken(): Promise<string> {
  throw new Error(
    "Mock auth provider not yet implemented — implement in Epic 141",
  );
}

export async function getCurrentUserEmail(): Promise<string | null> {
  throw new Error(
    "Mock auth provider not yet implemented — implement in Epic 141",
  );
}

export async function requireRole(
  role: "admin" | "owner" | "any",
): Promise<void> {
  throw new Error(
    `Mock auth provider not yet implemented (role=${role}) — implement in Epic 141`,
  );
}
