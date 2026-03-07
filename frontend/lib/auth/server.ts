import "server-only";

import type { AuthContext, SessionIdentity } from "./types";
// TODO(138B): Switch to dynamic imports for tree-shaking — both providers are stubs now
import * as clerkProvider from "./providers/clerk";
import * as mockProvider from "./providers/mock/server";
import * as keycloakBffProvider from "./providers/keycloak-bff";

/**
 * Build-time auth provider selection.
 * NEXT_PUBLIC_* vars are inlined by Next.js at build time,
 * so mock code is tree-shaken out of production builds.
 */
export const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

export async function getSessionIdentity(): Promise<SessionIdentity> {
  if (AUTH_MODE === "mock") return mockProvider.getSessionIdentity();
  if (AUTH_MODE === "keycloak") return keycloakBffProvider.getSessionIdentity();
  return clerkProvider.getSessionIdentity();
}

export async function getAuthContext(): Promise<AuthContext> {
  if (AUTH_MODE === "mock") return mockProvider.getAuthContext();
  if (AUTH_MODE === "keycloak") return keycloakBffProvider.getAuthContext();
  return clerkProvider.getAuthContext();
}

export async function getAuthToken(): Promise<string> {
  if (AUTH_MODE === "mock") return mockProvider.getAuthToken();
  if (AUTH_MODE === "keycloak") return keycloakBffProvider.getAuthToken();
  return clerkProvider.getAuthToken();
}

export async function getCurrentUserEmail(): Promise<string | null> {
  if (AUTH_MODE === "mock") return mockProvider.getCurrentUserEmail();
  if (AUTH_MODE === "keycloak") return keycloakBffProvider.getCurrentUserEmail();
  return clerkProvider.getCurrentUserEmail();
}

export async function hasPlan(plan: string): Promise<boolean> {
  if (AUTH_MODE === "mock") return mockProvider.hasPlan(plan);
  if (AUTH_MODE === "keycloak") return keycloakBffProvider.hasPlan(plan);
  return clerkProvider.hasPlan(plan);
}

export async function requireRole(
  role: "admin" | "owner" | "any",
): Promise<void> {
  if (AUTH_MODE === "mock") return mockProvider.requireRole(role);
  if (AUTH_MODE === "keycloak") return keycloakBffProvider.requireRole(role);
  return clerkProvider.requireRole(role);
}
