"use client";

import { ClerkProvider } from "@clerk/nextjs";
import { KeycloakAuthProvider } from "./keycloak-context";
import { MockAuthContextProvider } from "./mock-context";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

interface AuthProviderProps {
  children: React.ReactNode;
}

/**
 * Conditional auth provider wrapper.
 * Renders the appropriate provider based on AUTH_MODE:
 * - "clerk": ClerkProvider (default)
 * - "keycloak": next-auth SessionProvider via KeycloakAuthProvider
 * - "mock": MockAuthContextProvider for E2E tests
 */
export function AuthProvider({ children }: AuthProviderProps) {
  if (AUTH_MODE === "mock") {
    return <MockAuthContextProvider>{children}</MockAuthContextProvider>;
  }

  if (AUTH_MODE === "keycloak") {
    return <KeycloakAuthProvider>{children}</KeycloakAuthProvider>;
  }

  return (
    <ClerkProvider appearance={{ cssLayerName: "clerk" }}>
      {children}
    </ClerkProvider>
  );
}
