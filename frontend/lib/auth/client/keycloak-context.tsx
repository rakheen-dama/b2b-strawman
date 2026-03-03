"use client";

import { SessionProvider } from "next-auth/react";

/**
 * Keycloak auth provider — wraps children in next-auth's SessionProvider.
 * SessionProvider handles session state, auto-refresh, and provides the
 * useSession() hook context for client components.
 */
export function KeycloakAuthProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  return <SessionProvider>{children}</SessionProvider>;
}
