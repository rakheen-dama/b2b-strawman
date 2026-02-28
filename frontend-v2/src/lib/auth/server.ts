import "server-only";

import type { AuthContext } from "./types";
// TODO(138B): Switch to dynamic imports for tree-shaking — both providers are stubs now
import * as clerkProvider from "./providers/clerk";
import * as mockProvider from "./providers/mock/server";

/**
 * Build-time auth provider selection.
 * NEXT_PUBLIC_* vars are inlined by Next.js at build time,
 * so mock code is tree-shaken out of production builds.
 */
export const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

export async function getAuthContext(): Promise<AuthContext> {
  if (AUTH_MODE === "mock") return mockProvider.getAuthContext();
  return clerkProvider.getAuthContext();
}

export async function getAuthToken(): Promise<string> {
  if (AUTH_MODE === "mock") return mockProvider.getAuthToken();
  return clerkProvider.getAuthToken();
}

export async function getCurrentUserEmail(): Promise<string | null> {
  if (AUTH_MODE === "mock") return mockProvider.getCurrentUserEmail();
  return clerkProvider.getCurrentUserEmail();
}

export async function hasPlan(plan: string): Promise<boolean> {
  if (AUTH_MODE === "mock") return mockProvider.hasPlan(plan);
  return clerkProvider.hasPlan(plan);
}

export async function requireRole(
  role: "admin" | "owner" | "any",
): Promise<void> {
  if (AUTH_MODE === "mock") return mockProvider.requireRole(role);
  return clerkProvider.requireRole(role);
}
