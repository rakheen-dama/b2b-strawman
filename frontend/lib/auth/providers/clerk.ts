import { auth, currentUser } from "@clerk/nextjs/server";
import type { AuthContext } from "../types";

/**
 * Clerk auth provider — wraps all Clerk SDK server calls.
 * This is the single location for all Clerk SDK usage in the codebase.
 */

export async function getAuthContext(): Promise<AuthContext> {
  const { orgId, orgSlug, orgRole, userId } = await auth();

  if (!orgId || !orgSlug || !orgRole || !userId) {
    throw new Error("No active organization — select an organization first");
  }

  return { orgId, orgSlug, orgRole, userId };
}

export async function getAuthToken(): Promise<string> {
  const { getToken } = await auth();
  const token = await getToken();
  if (!token)
    throw new Error(
      "No auth token available — user may not be authenticated",
    );
  return token;
}

export async function getCurrentUserEmail(): Promise<string | null> {
  const user = await currentUser();
  return user?.primaryEmailAddress?.emailAddress ?? null;
}

export async function requireRole(
  role: "admin" | "owner" | "any",
): Promise<void> {
  const { orgRole } = await auth();

  if (!orgRole) {
    throw new Error("No active organization — cannot check role");
  }

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
