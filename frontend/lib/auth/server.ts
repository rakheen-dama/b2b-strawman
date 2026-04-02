import "server-only";

import type { AuthContext, SessionIdentity } from "./types";
import * as mockProvider from "./providers/mock/server";
import * as keycloakBffProvider from "./providers/keycloak-bff";

/**
 * Build-time auth provider selection.
 * NEXT_PUBLIC_* vars are inlined by Next.js at build time,
 * so mock code is tree-shaken out of production builds.
 */
export const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";

export async function getSessionIdentity(): Promise<SessionIdentity> {
  if (AUTH_MODE === "mock") return mockProvider.getSessionIdentity();
  return keycloakBffProvider.getSessionIdentity();
}

export async function getAuthContext(): Promise<AuthContext> {
  if (AUTH_MODE === "mock") return mockProvider.getAuthContext();
  return keycloakBffProvider.getAuthContext();
}

export async function getAuthToken(): Promise<string> {
  if (AUTH_MODE === "mock") return mockProvider.getAuthToken();
  return keycloakBffProvider.getAuthToken();
}

export async function getCurrentUserEmail(): Promise<string | null> {
  if (AUTH_MODE === "mock") return mockProvider.getCurrentUserEmail();
  return keycloakBffProvider.getCurrentUserEmail();
}

export async function getCurrentUserInfo(): Promise<{
  name: string | null;
  email: string | null;
}> {
  if (AUTH_MODE === "mock") return mockProvider.getCurrentUserInfo();
  return keycloakBffProvider.getCurrentUserInfo();
}
