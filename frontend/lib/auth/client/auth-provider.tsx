"use client";

import { ClerkProvider } from "@clerk/nextjs";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

interface AuthProviderProps {
  children: React.ReactNode;
}

/**
 * Conditional auth provider wrapper.
 * Renders ClerkProvider in clerk mode, placeholder in mock mode.
 * Mock mode will be replaced with a real context provider in Epic 142.
 */
export function AuthProvider({ children }: AuthProviderProps) {
  if (AUTH_MODE === "mock") {
    // TODO(Epic 142): Replace with MockAuthProvider context
    return <>{children}</>;
  }

  return (
    <ClerkProvider appearance={{ cssLayerName: "clerk" }}>
      {children}
    </ClerkProvider>
  );
}
