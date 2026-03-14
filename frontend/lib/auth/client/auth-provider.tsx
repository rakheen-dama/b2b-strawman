"use client";

import { MockAuthContextProvider } from "./mock-context";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";

interface AuthProviderProps {
  children: React.ReactNode;
}

/**
 * Conditional auth provider wrapper.
 * Renders MockAuthContextProvider in mock mode,
 * or a passthrough wrapper in keycloak mode (no client-side auth provider needed).
 */
export function AuthProvider({ children }: AuthProviderProps) {
  if (AUTH_MODE === "mock") {
    return <MockAuthContextProvider>{children}</MockAuthContextProvider>;
  }

  // Keycloak BFF mode — no client-side auth provider needed.
  // Auth is handled server-side via SESSION cookie + gateway /bff/me.
  return <>{children}</>;
}
